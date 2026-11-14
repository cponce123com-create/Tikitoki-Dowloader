package com.tikitoki.downloader.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import java.io.File
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import java.util.concurrent.TimeUnit
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.tikitoki.downloader.domain.DownloadInputPolicy
import com.tikitoki.downloader.domain.MetadataSanitizer
import androidx.room.withTransaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class EnqueueSummary(val added: Int, val duplicates: Int)

/** Persistent, serial background queue. Only authorized/available TikTok media. */
object DownloadQueue {
    const val UNIQUE_WORK = "tikitoki_download_queue"
    private val mutationLock = Mutex()

    suspend fun enqueue(
        context: Context,
        videos: List<VideoEntry>,
        formatId: String = TikTokVideoSource.DEFAULT_FORMAT,
    ): EnqueueSummary = withContext(Dispatchers.IO) { mutationLock.withLock {
        val db = DownloadDatabase.get(context)
        val dao = db.downloads()
        val safeFormat = DownloadInputPolicy.safeFormatId(formatId)
        // Validate the whole selection before changing persistent state.
        val safeVideos = videos.distinctBy { it.id }.map { video ->
            video.copy(url = DownloadInputPolicy.canonicalUrl(video.url), title = MetadataSanitizer.title(video.title))
        }
        val summary = db.withTransaction {
            val existing = safeVideos.map { it.id }.chunked(400)
                .flatMap { dao.findMany(it) }.associateBy { it.videoId }
            var duplicates = 0
            val records = safeVideos.mapNotNull { video ->
                val previous = existing[video.id]
                if (previous?.status in listOf(DownloadState.COMPLETED, DownloadState.QUEUED,
                        DownloadState.DOWNLOADING, DownloadState.PAUSED)) {
                    duplicates++
                    null
                } else DownloadRecord(
                    videoId = video.id, videoUrl = video.url, title = video.title,
                    thumbnail = video.thumbnail, formatId = safeFormat,
                    createdAt = previous?.createdAt ?: System.currentTimeMillis(),
                )
            }
            if (records.isNotEmpty()) {
                dao.upsertAll(records)
                val batchId = java.util.UUID.randomUUID().toString()
                dao.insertBatch(DownloadBatch(batchId, System.currentTimeMillis(), duplicates))
                dao.insertBatchItems(records.map { DownloadBatchItem(batchId, it.videoId) })
            }
            EnqueueSummary(records.size, duplicates)
        }
        // Also kick an existing queued selection after a previous interrupted enqueue.
        if (summary.added > 0 || dao.pendingRecoveryCount() > 0) start(context)
        summary
    } }

    /** Preserve history on permission or provider errors; remove it if the file is already gone. */
    suspend fun deleteCompleted(context: Context, id: String): DeleteCompletedResult = withContext(Dispatchers.IO) {
        val dao = DownloadDatabase.get(context).downloads()
        val record = dao.find(id) ?: return@withContext DeleteCompletedResult.FAILED
        if (record.status != DownloadState.COMPLETED) return@withContext DeleteCompletedResult.FAILED
        val outcome = deleteSavedFile(context, record.savedUri?.let(Uri::parse))
        if (outcome != DeleteCompletedResult.FAILED && dao.deleteCompleted(id) == 0)
            DeleteCompletedResult.FAILED else outcome
    }

    private fun request(context: Context) = OneTimeWorkRequestBuilder<DownloadWorker>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(if (DownloadSettings.allowMetered(context)) NetworkType.CONNECTED else NetworkType.UNMETERED).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .build()

    fun start(context: Context) {
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(
                UNIQUE_WORK,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request(context),
            )
    }

    suspend fun pause(context: Context) = withContext(Dispatchers.IO) { mutationLock.withLock {
        val dao = DownloadDatabase.get(context).downloads()
        dao.pausePending()
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK).result.get()
    } }

    /** Atomically pause pending work before changing the constraints for the next request. */
    suspend fun changeMeteredPolicy(context: Context, allowed: Boolean): Boolean = withContext(Dispatchers.IO) { mutationLock.withLock {
        val dao = DownloadDatabase.get(context).downloads()
        val hadPending = dao.pendingRecoveryCount() > 0
        dao.pausePending()
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK).result.get()
        check(context.getSharedPreferences("tikitoki_settings", Context.MODE_PRIVATE).edit().putBoolean("allow_metered", allowed).commit())
        hadPending
    } }

    suspend fun resume(context: Context) = withContext(Dispatchers.IO) { mutationLock.withLock {
        // Wait for the old worker to stop before starting a replacement.
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK).result.get()
        DownloadDatabase.get(context).downloads().resumePending()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            UNIQUE_WORK,
            ExistingWorkPolicy.REPLACE,
            request(context),
        )
    } }

    suspend fun retryFailed(context: Context) = withContext(Dispatchers.IO) { mutationLock.withLock {
        val db = DownloadDatabase.get(context)
        val dao = db.downloads()
        val count = db.withTransaction {
            val failed = dao.failedRecords()
            if (failed.isNotEmpty()) {
                val id = java.util.UUID.randomUUID().toString()
                dao.insertBatch(DownloadBatch(id, System.currentTimeMillis(), 0))
                dao.insertBatchItems(failed.map { DownloadBatchItem(id, it.videoId) })
                dao.retryFailed()
            }
            failed.size
        }
        if (count > 0) start(context)
    } }

    /** Deletes only this app's temporary cache after the user confirms, never saved downloads. */
    suspend fun clearTemporary(context: Context): Int = withContext(Dispatchers.IO) { mutationLock.withLock {
        val dao = DownloadDatabase.get(context).downloads()
        val running = ActiveDownloadProcesses.ids.isNotEmpty() || WorkManager.getInstance(context.applicationContext).getWorkInfosForUniqueWork(UNIQUE_WORK)
            .get().any { it.state == androidx.work.WorkInfo.State.RUNNING }
        check(!running && dao.activeOrPausedCount() == 0) { "Espera a que terminen o cancela las descargas pendientes y pausadas." }
        clearOwnTemporaryFiles(safeTemporaryDirectory(context.cacheDir))
    } }

    suspend fun cancel(context: Context, id: String) = withContext(Dispatchers.IO) {
        val dao = DownloadDatabase.get(context).downloads()
        val batches = dao.pendingBatchIds(id)
        if (dao.markCancelled(id) > 0) {
            runCatching { TikTokVideoSource(context.applicationContext).cancel(id) }
            if (id !in ActiveDownloadProcesses.ids)
                runCatching { clearVideoTemporaryFiles(safeTemporaryDirectory(context.cacheDir), id) }
            runCatching { CompletionNotifier.notifyFinished(context, batches) }
        }
    }
}


