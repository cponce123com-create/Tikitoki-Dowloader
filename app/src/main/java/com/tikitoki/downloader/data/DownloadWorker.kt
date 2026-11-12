package com.tikitoki.downloader.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val dao = DownloadDatabase.get(context).downloads()
    private val source = TikTokVideoSource(context.applicationContext)
    private val progressScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var currentId: String? = null
    @Volatile private var lastDbUpdate = 0L

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Recover tasks interrupted by process death, never resume manually paused tasks.
            dao.requeueInterrupted()
            setForeground(notification("Preparando descargas"))
            while (!isStopped) {
                val item = dao.nextQueued() ?: break
                // Claim the queued record atomically: pause must not be overwritten.
                if (dao.claimQueued(item.videoId) == 0) continue
                currentId = item.videoId
                setForeground(notification("Descargando: ${item.title}"))
                val stopMonitor = progressScope.launch {
                    // CoroutineWorker.onStopped is final. Observe cancellation from a
                    // separate scope and stop the blocking yt-dlp process directly.
                    while (currentId == item.videoId) {
                        if (isStopped) {
                            source.cancel(item.videoId)
                            break
                        }
                        delay(350)
                    }
                }
                try {
                    val file = source.downloadToCache(
                        VideoEntry(item.videoId, item.videoUrl, item.title, item.thumbnail),
                        item.formatId
                    ) { percent ->
                        if (!isStopped) {
                            setProgressAsync(workDataOf("videoId" to item.videoId, "percent" to percent))
                            val timestamp = System.currentTimeMillis()
                            if (timestamp - lastDbUpdate > 1000 || percent == 100) {
                                lastDbUpdate = timestamp
                                progressScope.launch { dao.setProgress(item.videoId, percent) }
                            }
                        }
                    }
                    if (isStopped) {
                        dao.setStatus(item.videoId, DownloadState.PAUSED)
                        file.delete()
                        break
                    }
                    if (dao.find(item.videoId)?.status == DownloadState.PAUSED) {
                        file.delete()
                        break
                    }
                    val uri = publishVideo(applicationContext, file)
                    file.delete()
                    dao.completeIfDownloading(item.videoId, uri.toString())
                } catch (e: CancellationException) {
                    // Cancellation can race with a newly resumed worker; do not overwrite its state.
                    dao.pauseIfDownloading(item.videoId)
                    throw e
                } catch (e: Exception) {
                    if (isStopped) dao.pauseIfDownloading(item.videoId)
                    else dao.failIfDownloading(item.videoId, e.message?.take(500) ?: "Error al descargar")
                } finally {
                    currentId = null
                    stopMonitor.cancel()
                }
            }
            Result.success()
        } catch (_: CancellationException) {
            Result.success()
        } finally {
            progressScope.cancel()
        }
    }

    private fun notification(message: String): ForegroundInfo {
        val channelId = "tikitoki_downloads"
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(channelId, "Descargas de Tikitoki", NotificationManager.IMPORTANCE_LOW)
        )
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Tikitoki Downloader")
            .setContentText(message)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(901, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else ForegroundInfo(901, notification)
    }

    private fun publishVideo(context: Context, file: File): Uri {
        val extension = file.extension.lowercase().ifBlank { "mp4" }
        val mime = when (extension) {
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            else -> "application/octet-stream"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/TikitokiDownloader")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("No se pudo crear el archivo en Descargas.")
            try {
                resolver.openOutputStream(uri)?.use { output ->
                    file.inputStream().use { input -> input.copyTo(output) }
                } ?: throw IllegalStateException("No se pudo escribir el archivo.")
                resolver.update(uri, ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }, null, null)
                return uri
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) throw IllegalStateException("Android necesita permiso para guardar en Descargas.")
        @Suppress("DEPRECATION")
        val folder = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "TikitokiDownloader"
        )
        if (!folder.exists() && !folder.mkdirs()) throw IllegalStateException("No se pudo crear Descargas.")
        val destination = File(folder, file.name)
        file.copyTo(destination, overwrite = false)
        return Uri.fromFile(destination)
    }
}
