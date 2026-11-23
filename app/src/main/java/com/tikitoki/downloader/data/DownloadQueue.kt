package com.tikitoki.downloader.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.tikitoki.downloader.domain.DownloadIdentity
import com.tikitoki.downloader.domain.DownloadInputPolicy
import com.tikitoki.downloader.domain.MetadataSanitizer
import com.tikitoki.downloader.domain.PlaybackKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

data class EnqueueSummary(
    val added: Int,
    val duplicates: Int,
)

/** Persistent, serial background queue. Only authorized/available TikTok media. */
object DownloadQueue {
    const val UNIQUE_WORK = "tikitoki_download_queue"
    private val mutationLock = Mutex()

    suspend fun enqueue(
        context: Context,
        videos: List<VideoEntry>,
        formatId: String = TikTokVideoSource.DEFAULT_FORMAT,
    ): EnqueueSummary = enqueue(context, videos.map { DownloadSelection(it, formatId) })

    internal suspend fun enqueue(
        context: Context,
        selections: List<DownloadSelection>,
    ): EnqueueSummary =
        withContext(Dispatchers.IO) {
            mutationLock.withLock {
                val db = DownloadDatabase.get(context)
                val dao = db.downloads()
                // Validate the whole selection before changing persistent state.
                val safe = selections.map { it.validated() }.distinctBy { it.queueKey() }
                val summary =
                    db.withTransaction {
                        val keys = safe.flatMap { listOf(it.video.id, it.queueKey()) }
                        val existing = keys.chunked(400).flatMap { dao.findMany(it) }.associateBy { it.videoId }
                        val activeStatuses =
                            setOf(
                                DownloadState.COMPLETED,
                                DownloadState.QUEUED,
                                DownloadState.DOWNLOADING,
                                DownloadState.PAUSED,
                            )
                        val records =
                            safe.mapNotNull { item ->
                                val video = item.video
                                val key = item.queueKey()
                                val legacy =
                                    existing[video.id]?.takeIf {
                                        PlaybackKind.isAudio(it.formatId, null, it.savedUri) == item.isAudio()
                                    }
                                val current = existing[key]
                                if (current?.status in activeStatuses || legacy?.status in activeStatuses) {
                                    null
                                } else {
                                    DownloadRecord(
                                        videoId = key,
                                        videoUrl = video.url,
                                        title = video.title,
                                        thumbnail = video.thumbnail,
                                        formatId = item.formatId,
                                        createdAt = (current ?: legacy)?.createdAt ?: System.currentTimeMillis(),
                                    )
                                }
                            }
                        val duplicates = safe.size - records.size
                        if (records.isNotEmpty()) {
                            dao.upsertAll(records)
                            val batchId =
                                java.util.UUID
                                    .randomUUID()
                                    .toString()
                            dao.insertBatch(DownloadBatch(batchId, System.currentTimeMillis(), duplicates))
                            dao.insertBatchItems(records.map { DownloadBatchItem(batchId, it.videoId) })
                        }
                        EnqueueSummary(records.size, duplicates)
                    }
                // Also kick an existing queued selection after a previous interrupted enqueue.
                if (summary.added > 0 || dao.pendingRecoveryCount() > 0) start(context)
                summary
            }
        }

    /** Preserve history on permission or provider errors; remove it if the file is already gone. */
    suspend fun deleteCompleted(
        context: Context,
        id: String,
    ): DeleteCompletedResult =
        withContext(Dispatchers.IO) {
            val dao = DownloadDatabase.get(context).downloads()
            val record = dao.find(id) ?: return@withContext DeleteCompletedResult.FAILED
            if (record.status != DownloadState.COMPLETED) return@withContext DeleteCompletedResult.FAILED
            val outcome = deleteSavedFile(context, record.savedUri?.let(Uri::parse))
            if (outcome != DeleteCompletedResult.FAILED && dao.deleteCompleted(id) == 0) {
                DeleteCompletedResult.FAILED
            } else {
                outcome
            }
        }

    fun start(context: Context) {
        WorkManager
            .getInstance(context.applicationContext)
            .enqueueUniqueWork(
                UNIQUE_WORK,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                downloadWorkRequest(context),
            )
    }

    suspend fun pause(context: Context) =
        withContext(Dispatchers.IO) {
            mutationLock.withLock {
                val dao = DownloadDatabase.get(context).downloads()
                dao.pausePending()
                WorkManager
                    .getInstance(context.applicationContext)
                    .cancelUniqueWork(UNIQUE_WORK)
                    .result
                    .get()
            }
        }

    /** Atomically pause pending work before changing the constraints for the next request. */
    suspend fun changeMeteredPolicy(
        context: Context,
        allowed: Boolean,
    ): Boolean =
        withContext(Dispatchers.IO) {
            mutationLock.withLock {
                val dao = DownloadDatabase.get(context).downloads()
                val hadPending = dao.pendingRecoveryCount() > 0
                dao.pausePending()
                WorkManager
                    .getInstance(context.applicationContext)
                    .cancelUniqueWork(UNIQUE_WORK)
                    .result
                    .get()
                check(
                    context
                        .getSharedPreferences("tikitoki_settings", Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("allow_metered", allowed)
                        .commit(),
                )
                hadPending
            }
        }

    suspend fun resume(context: Context) =
        withContext(Dispatchers.IO) {
            mutationLock.withLock {
                // Wait for the old worker to stop before starting a replacement.
                WorkManager
                    .getInstance(context.applicationContext)
                    .cancelUniqueWork(UNIQUE_WORK)
                    .result
                    .get()
                DownloadDatabase.get(context).downloads().resumePending()
                WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                    UNIQUE_WORK,
                    ExistingWorkPolicy.REPLACE,
                    downloadWorkRequest(context),
                )
            }
        }

    suspend fun retryFailed(context: Context) =
        withContext(Dispatchers.IO) {
            mutationLock.withLock {
                val db = DownloadDatabase.get(context)
                val dao = db.downloads()
                val count =
                    db.withTransaction {
                        val failed = dao.failedRecords()
                        if (failed.isNotEmpty()) {
                            val id =
                                java.util.UUID
                                    .randomUUID()
                                    .toString()
                            dao.insertBatch(DownloadBatch(id, System.currentTimeMillis(), 0))
                            dao.insertBatchItems(failed.map { DownloadBatchItem(id, it.videoId) })
                            dao.retryFailed()
                        }
                        failed.size
                    }
                if (count > 0) start(context)
            }
        }

    /** Deletes only this app's temporary cache after the user confirms, never saved downloads. */
    suspend fun clearTemporary(context: Context): Int =
        withContext(Dispatchers.IO) {
            mutationLock.withLock {
                val dao = DownloadDatabase.get(context).downloads()
                val running =
                    ActiveDownloadProcesses.ids.isNotEmpty() ||
                        WorkManager
                            .getInstance(context.applicationContext)
                            .getWorkInfosForUniqueWork(UNIQUE_WORK)
                            .get()
                            .any { it.state == androidx.work.WorkInfo.State.RUNNING }
                check(!running && dao.activeOrPausedCount() == 0) { "Espera a que terminen o cancela las descargas pendientes y pausadas." }
                clearOwnTemporaryFiles(safeTemporaryDirectory(context.cacheDir))
            }
        }

    suspend fun cancel(
        context: Context,
        id: String,
    ) = withContext(Dispatchers.IO) {
        val dao = DownloadDatabase.get(context).downloads()
        val batches = dao.pendingBatchIds(id)
        if (dao.markCancelled(id) > 0) {
            runCatching { TikTokVideoSource(context.applicationContext).cancel(id) }
            if (id !in ActiveDownloadProcesses.ids) {
                runCatching { clearVideoTemporaryFiles(safeTemporaryDirectory(context.cacheDir), id) }
            }
            runCatching { CompletionNotifier.notifyFinished(context, batches) }
        }
    }
}

internal fun DownloadSelection.validated(): DownloadSelection {
    val isPhoto = TikTokPhotoSource.isPhotoEntry(video)
    val safeFormat = DownloadInputPolicy.safeFormatId(formatId)
    val safeUrl = DownloadInputPolicy.canonicalUrl(video.url)
    val safeThumbnail =
        if (isPhoto) {
            require(TikTokPhotoSource.isPhotoFormat(safeFormat)) { "La foto no tiene un formato de descarga compatible." }
            require(TikTokPhotoSource.isPhotoPageUrl(safeUrl)) { "La foto no corresponde a una publicación TikTok /photo/." }
            TikTokPhotoSource.canonicalImageUrl(video.thumbnail ?: "")
        } else {
            require(!TikTokPhotoSource.isPhotoFormat(safeFormat)) { "El formato de imagen requiere una entrada de foto TikTok." }
            video.thumbnail
        }
    return DownloadSelection(
        video.copy(
            url = safeUrl,
            title = MetadataSanitizer.title(video.title),
            thumbnail = safeThumbnail,
        ),
        safeFormat,
    )
}

private fun DownloadSelection.isAudio(): Boolean = PlaybackKind.isAudio(formatId, null, null)

private fun DownloadSelection.queueKey(): String = DownloadIdentity.key(video.id, isAudio())

private const val RETRY_BACKOFF_SECONDS = 30L

private fun downloadWorkRequest(context: Context) =
    OneTimeWorkRequestBuilder<DownloadWorker>()
        .setConstraints(
            Constraints
                .Builder()
                .setRequiredNetworkType(
                    if (DownloadSettings.allowMetered(context)) NetworkType.CONNECTED else NetworkType.UNMETERED,
                ).build(),
        ).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, RETRY_BACKOFF_SECONDS, TimeUnit.SECONDS)
        .build()
