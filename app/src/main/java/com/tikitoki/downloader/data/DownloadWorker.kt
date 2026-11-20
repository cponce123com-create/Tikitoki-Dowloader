package com.tikitoki.downloader.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.tikitoki.downloader.domain.DestinationNaming
import com.tikitoki.downloader.domain.DownloadInputPolicy
import com.tikitoki.downloader.domain.MetadataSanitizer
import com.tikitoki.downloader.domain.PlaybackKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

private const val COMPLETED_DOWNLOAD_PROGRESS = 100

internal object ActiveDownloadProcesses {
    val ids =
        java.util.concurrent.ConcurrentHashMap
            .newKeySet<String>()
    val workerLock = Mutex()
}

private data class DownloadFileMetadata(
    val account: String,
    val displayName: String,
    val mime: String,
)

/** Persist only changed percentages, except that completion bypasses the time interval. */
internal fun shouldPersistDownloadProgress(
    lastPersistedProgress: Int?,
    progress: Int,
    intervalElapsed: Boolean,
): Boolean = lastPersistedProgress != progress && (intervalElapsed || progress == COMPLETED_DOWNLOAD_PROGRESS)

class DownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    companion object {
        private val documentFolderLock = Any()
    }

    private val dao = DownloadDatabase.get(context).downloads()
    private val source = TikTokVideoSource(context.applicationContext)
    private val progressScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeIds = ActiveDownloadProcesses.ids
    private val lastDbUpdates = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val lastDbProgress = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val activeProgress = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val lastNotification =
        java.util.concurrent.atomic
            .AtomicLong(0)

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            try {
                // Android may reject starting a dataSync foreground service while its
                // time quota is exhausted. Keep the queue intact and let WorkManager
                // retry with the request's exponential backoff instead of failing it.
                try {
                    setForeground(notification("Preparando descargas"))
                } catch (e: IllegalStateException) {
                    DiagnosticHistory.record(applicationContext, e.message)
                    return@withContext Result.retry()
                }
                ActiveDownloadProcesses.workerLock.withLock {
                    // A cancelled worker may still be stopping yt-dlp; never overlap replacements.
                    while (activeIds.isNotEmpty() && !isStopped) delay(250)
                    if (isStopped) return@withLock Result.success()
                    dao.requeueInterrupted()
                    try {
                        drainAdaptiveQueue(limit = { DownloadSettings.currentLimit(applicationContext) }, take = {
                            var claimed: DownloadRecord? = null
                            while (!isStopped && claimed == null) {
                                val item = dao.nextQueuedExcluding(activeIds.toList().ifEmpty { listOf("") }) ?: break
                                if (dao.claimQueued(item.videoId) != 0) claimed = item
                            }
                            claimed
                        }, process = { downloadOne(it) })
                        Result.success()
                    } finally {
                        // Network constraints may stop work without the user pausing it.
                        // Keep such records queued; explicit PAUSED/CANCELLED states are untouched.
                        withContext(NonCancellable) { dao.requeueInterrupted() }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                progressScope.cancel()
            }
        }

    private suspend fun downloadOne(item: DownloadRecord) {
        activeIds.add(item.videoId)
        var stopMonitor: Job? = null
        val progressUpdates = Channel<Int>(Channel.CONFLATED)
        val progressWriter =
            progressScope.launch {
                for (progress in progressUpdates) {
                    try {
                        dao.setProgress(item.videoId, progress)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        lastDbProgress.remove(item.videoId, progress)
                        DiagnosticHistory.record(applicationContext, e.message)
                    }
                }
            }
        try {
            activeProgress[item.videoId] = 0
            setForeground(notification("Descargando ${activeIds.size} videos"))
            stopMonitor =
                progressScope.launch {
                    // CoroutineWorker.onStopped is final. Observe cancellation from a
                    // separate scope and stop the blocking yt-dlp process directly.
                    while (item.videoId in activeIds) {
                        if (isStopped) {
                            source.cancel(item.videoId)
                            break
                        }
                        // Cancellation may arrive while the extractor is waiting to start.
                        // Keep checking until the task exits so a late process is stopped too.
                        if (dao.find(item.videoId)?.status == DownloadState.CANCELLED) {
                            runCatching { source.cancel(item.videoId) }
                        }
                        delay(1000)
                    }
                }
            val file =
                source.downloadToCache(
                    VideoEntry(item.videoId, DownloadInputPolicy.canonicalUrl(item.videoUrl), item.title, item.thumbnail),
                    DownloadInputPolicy.safeFormatId(item.formatId),
                    onSpeed = { speed -> if (!isStopped) DownloadTelemetry.update(item.videoId, speed) },
                ) { percent ->
                    if (!isStopped) {
                        activeProgress[item.videoId] = percent
                        val timestamp = System.currentTimeMillis()
                        val intervalElapsed =
                            timestamp - (lastDbUpdates[item.videoId] ?: 0L) >= PROGRESS_DB_INTERVAL_MS
                        if (
                            shouldPersistDownloadProgress(
                                lastDbProgress[item.videoId],
                                percent,
                                intervalElapsed,
                            )
                        ) {
                            lastDbUpdates[item.videoId] = timestamp
                            lastDbProgress[item.videoId] = percent
                            // A single consumer persists progress without blocking the extractor callback.
                            progressUpdates.trySend(percent)
                        }
                        val last = lastNotification.get()
                        if (timestamp - last >= 1000 && lastNotification.compareAndSet(last, timestamp)) {
                            runCatching {
                                NotificationManagerCompat
                                    .from(
                                        applicationContext,
                                    ).notify(901, notification("Descargando ${activeIds.size} videos").notification)
                            }
                        }
                    }
                }
            if (isStopped) {
                file.delete()
                return
            }
            if (dao.find(item.videoId)?.status != DownloadState.DOWNLOADING) {
                file.delete()
                return
            }
            DownloadTelemetry.remove(item.videoId)
            val uri =
                try {
                    publishVideo(applicationContext, file, item)
                } finally {
                    file.delete()
                }
            // A pause can occur during MediaStore publishing. Keep the saved file
            // but only mark it completed when the record is still downloading.
            val batches = dao.pendingBatchIds(item.videoId)
            if (dao.markCompleted(item.videoId, uri.toString()) == 0) {
                // Avoid leaving an orphaned file if the user paused the task.
                runCatching { deleteSavedFile(applicationContext, uri) }
            } else {
                runCatching { CompletionNotifier.notifyFinished(applicationContext, batches) }
            }
        } catch (e: CancellationException) {
            // Leave DOWNLOADING untouched: the next worker requeues interrupted work.
            throw e
        } catch (e: Exception) {
            if (!isStopped) {
                DiagnosticHistory.record(applicationContext, e.message)
                val batches = dao.pendingBatchIds(item.videoId)
                if (dao.markFailed(item.videoId, MetadataSanitizer.error(e.message).ifBlank { "Error al descargar" }) > 0) {
                    runCatching { CompletionNotifier.notifyFinished(applicationContext, batches) }
                }
            }
        } finally {
            withContext(NonCancellable) {
                progressUpdates.close()
                progressWriter.join()
                val status = runCatching { dao.find(item.videoId)?.status }.getOrNull()
                // A stopped DOWNLOADING task is requeued by doWork's finally block.
                val resumable = retainVideoTemporaryFiles(status, isStopped)
                if (!resumable && status != null) {
                    runCatching { clearVideoTemporaryFiles(safeTemporaryDirectory(applicationContext.cacheDir), item.videoId) }
                }
            }
            activeIds.remove(item.videoId)
            activeProgress.remove(item.videoId)
            DownloadTelemetry.remove(item.videoId)
            if (activeIds.isNotEmpty() &&
                (
                    Build.VERSION.SDK_INT < 33 ||
                        ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED
                )
            ) {
                try {
                    NotificationManagerCompat
                        .from(
                            applicationContext,
                        ).notify(901, notification("Descargando ${activeIds.size} videos").notification)
                } catch (_: SecurityException) {
                    // Notification permission can be revoked at any time.
                }
            }
            lastDbUpdates.remove(item.videoId)
            lastDbProgress.remove(item.videoId)
            stopMonitor?.cancel()
        }
    }

    private fun notification(message: String): ForegroundInfo {
        val channelId = "tikitoki_downloads"
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(channelId, "Descargas de Baja.pe", NotificationManager.IMPORTANCE_LOW),
        )
        val showDetails = DownloadSettings.showProgress(applicationContext)
        val progress = activeProgress.values.toList().let { if (it.isEmpty()) null else it.average().toInt().coerceIn(0, 100) }
        val notification =
            NotificationCompat
                .Builder(applicationContext, channelId)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Baja.pe")
                .setContentText(
                    if (showDetails &&
                        progress != null
                    ) {
                        "$message · avance aproximado $progress%"
                    } else if (showDetails) {
                        message
                    } else {
                        "Descargas en curso"
                    },
                ).setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(
                    if (showDetails &&
                        progress != null
                    ) {
                        100
                    } else {
                        0
                    },
                    if (showDetails) progress ?: 0 else 0,
                    showDetails && progress == null,
                ).build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(901, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(901, notification)
        }
    }

    private fun publishVideo(
        context: Context,
        file: File,
        item: DownloadRecord,
    ): Uri {
        val extension = file.extension.lowercase().ifBlank { "mp4" }
        val account = DestinationNaming.account(item.videoUrl)
        val displayName = DestinationNaming.filename(item.title, item.videoId, extension)
        val mime = mimeType(extension, item.formatId)
        val folderUri =
            context
                .getSharedPreferences("tikitoki_settings", Context.MODE_PRIVATE)
                .getString("download_tree", null)
        val metadata = DownloadFileMetadata(account, displayName, mime)
        return when {
            folderUri != null -> publishToSelectedFolder(context, file, folderUri, metadata)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> publishToMediaStore(context, file, metadata)
            else -> publishToLegacyDownloads(context, file, metadata)
        }
    }

    private fun mimeType(
        extension: String,
        formatId: String,
    ): String =
        when (extension) {
            "mp4" -> "video/mp4"
            "webm" -> if (PlaybackKind.isAudio(formatId, null, null)) "audio/webm" else "video/webm"
            "mkv" -> "video/x-matroska"
            "m4a" -> "audio/mp4"
            "mp3" -> "audio/mpeg"
            "opus" -> "audio/ogg"
            "ogg" -> "audio/ogg"
            "aac" -> "audio/aac"
            else -> "application/octet-stream"
        }

    private fun publishToSelectedFolder(
        context: Context,
        file: File,
        folderUri: String,
        metadata: DownloadFileMetadata,
    ): Uri {
        val folder =
            DocumentFile.fromTreeUri(context, Uri.parse(folderUri))
                ?: error("La carpeta seleccionada ya no está disponible.")
        val target =
            synchronized(documentFolderLock) {
                val accountFolder =
                    folder.findFile(metadata.account)?.takeIf { it.isDirectory }
                        ?: folder.createDirectory(metadata.account)
                        ?: error("No se pudo crear la carpeta de la cuenta.")
                accountFolder.createFile(metadata.mime, metadata.displayName)
                    ?: error("No se pudo crear el archivo en la carpeta seleccionada.")
            }
        try {
            context.contentResolver.openOutputStream(target.uri, "w")?.use { output ->
                file.copyToOutput(output)
            } ?: error("No se pudo escribir en la carpeta seleccionada.")
            return target.uri
        } catch (e: Exception) {
            target.delete()
            throw e
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun publishToMediaStore(
        context: Context,
        file: File,
        metadata: DownloadFileMetadata,
    ): Uri {
        val values =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, metadata.displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, metadata.mime)
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/TikitokiDownloader/" + metadata.account,
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        val resolver = context.contentResolver
        val uri =
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("No se pudo crear el archivo en Descargas.")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                file.copyToOutput(output)
            } ?: error("No se pudo escribir el archivo.")
            resolver.update(
                uri,
                ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                },
                null,
                null,
            )
            return uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    private fun publishToLegacyDownloads(
        context: Context,
        file: File,
        metadata: DownloadFileMetadata,
    ): Uri {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            error("Android necesita permiso para guardar en Descargas.")
        }
        @Suppress("DEPRECATION")
        val folder =
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "TikitokiDownloader/${metadata.account}",
            )
        if (!folder.exists() && !folder.mkdirs()) error("No se pudo crear Descargas.")
        val destination = reserveUniqueFile(folder, metadata.displayName)
        try {
            destination.outputStream().use { output -> file.copyToOutput(output) }
        } catch (e: Exception) {
            destination.delete()
            throw e
        }
        runCatching {
            MediaScannerConnection.scanFile(context, arrayOf(destination.absolutePath), arrayOf(metadata.mime), null)
        }
        return Uri.fromFile(destination)
    }
}

private const val DOWNLOAD_COPY_BUFFER_SIZE_BYTES = 64 * 1024

private fun File.copyToOutput(output: java.io.OutputStream) {
    inputStream().use { input ->
        input.copyTo(output, bufferSize = DOWNLOAD_COPY_BUFFER_SIZE_BYTES)
    }
}

private const val PROGRESS_DB_INTERVAL_MS = 1_000L
