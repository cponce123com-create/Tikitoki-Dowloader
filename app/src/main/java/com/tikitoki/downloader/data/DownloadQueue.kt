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

data class EnqueueSummary(val added: Int, val duplicates: Int)

/** Persistent, serial background queue. Only authorized/available TikTok media. */
object DownloadQueue {
    const val UNIQUE_WORK = "tikitoki_download_queue"

    suspend fun enqueue(
        context: Context,
        videos: List<VideoEntry>,
        formatId: String = TikTokVideoSource.DEFAULT_FORMAT,
    ): EnqueueSummary = withContext(Dispatchers.IO) {
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
            if (records.isNotEmpty()) dao.upsertAll(records)
            EnqueueSummary(records.size, duplicates)
        }
        // Also kick an existing queued selection after a previous interrupted enqueue.
        if (summary.added > 0 || dao.pendingRecoveryCount() > 0) start(context)
        summary
    }

    /** Delete the saved file first; only remove the history entry on success. */
    suspend fun deleteCompleted(context: Context, id: String): Boolean = withContext(Dispatchers.IO) {
        val dao = DownloadDatabase.get(context).downloads()
        val record = dao.find(id) ?: return@withContext false
        if (record.status != DownloadState.COMPLETED) return@withContext false
        val saved = record.savedUri ?: return@withContext false
        val uri = Uri.parse(saved)
        val deleted = when (uri.scheme) {
            "content" -> context.contentResolver.delete(uri, null, null) > 0
            "file" -> {
                @Suppress("DEPRECATION")
                val root = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "TikitokiDownloader").canonicalFile
                val target = File(uri.path ?: "").canonicalFile
                target.path.startsWith(root.path + File.separator) && target.exists() && target.delete()
            }
            else -> false
        }
        if (deleted) dao.deleteCompleted(id) > 0 else false
    }

    private fun request() = OneTimeWorkRequestBuilder<DownloadWorker>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .build()

    fun start(context: Context) {
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(
                UNIQUE_WORK,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request(),
            )
    }

    suspend fun pause(context: Context) = withContext(Dispatchers.IO) {
        val dao = DownloadDatabase.get(context).downloads()
        dao.pausePending()
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK)
    }

    suspend fun resume(context: Context) = withContext(Dispatchers.IO) {
        // Wait for the old worker to stop before starting a replacement.
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK).result.get()
        DownloadDatabase.get(context).downloads().resumePending()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            UNIQUE_WORK,
            ExistingWorkPolicy.REPLACE,
            request(),
        )
    }

    suspend fun retryFailed(context: Context) = withContext(Dispatchers.IO) {
        DownloadDatabase.get(context).downloads().retryFailed()
        start(context)
    }

    suspend fun cancel(context: Context, id: String) = withContext(Dispatchers.IO) {
        val dao = DownloadDatabase.get(context).downloads()
        if (dao.cancelPending(id) > 0) runCatching { TikTokVideoSource(context.applicationContext).cancel(id) }
    }
}

