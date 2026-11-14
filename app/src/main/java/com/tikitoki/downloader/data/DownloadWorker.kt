package com.tikitoki.downloader.data

import com.tikitoki.downloader.domain.DownloadInputPolicy
import com.tikitoki.downloader.domain.MetadataSanitizer
import com.tikitoki.downloader.domain.DestinationNaming
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
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
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
    companion object { private val documentFolderLock = Any() }
    private val dao = DownloadDatabase.get(context).downloads()
    private val source = TikTokVideoSource(context.applicationContext)
    private val progressScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val lastDbUpdates = java.util.concurrent.ConcurrentHashMap<String, Long>()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Recover tasks interrupted by process death, never resume manually paused tasks.
            dao.requeueInterrupted()
            setForeground(notification("Preparando descargas"))
            val parallel = applicationContext.getSharedPreferences("tikitoki_settings", Context.MODE_PRIVATE)
                .getInt("parallel_downloads", 3).coerceIn(1, 4)
            drainParallelQueue(parallel, take = {
                var claimed: DownloadRecord? = null
                while (!isStopped && claimed == null) {
                    val item = dao.nextQueued() ?: break
                    if (dao.claimQueued(item.videoId) != 0) claimed = item
                }
                claimed
            }, process = { downloadOne(it) })
            Result.success()
        } catch (_: CancellationException) {
            Result.success()
        } finally {
            progressScope.cancel()
        }
    }

    private suspend fun downloadOne(item: DownloadRecord) {
                activeIds.add(item.videoId)
                setForeground(notification("Descargando ${activeIds.size} videos"))
                val stopMonitor = progressScope.launch {
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
                try {
                    val file = source.downloadToCache(
                        VideoEntry(item.videoId, DownloadInputPolicy.canonicalUrl(item.videoUrl), item.title, item.thumbnail),
                        DownloadInputPolicy.safeFormatId(item.formatId)
                    ) { percent ->
                        if (!isStopped) {
                            val timestamp = System.currentTimeMillis()
                            if (timestamp - (lastDbUpdates[item.videoId] ?: 0L) > 1000 || percent == 100) {
                                lastDbUpdates[item.videoId] = timestamp
                                progressScope.launch { dao.setProgress(item.videoId, percent) }
                            }
                        }
                    }
                    if (isStopped) {
                        dao.pauseIfDownloading(item.videoId)
                        file.delete()
                        return
                    }
                    if (dao.find(item.videoId)?.status != DownloadState.DOWNLOADING) {
                        file.delete()
                        return
                    }
                    val uri = try { publishVideo(applicationContext, file, item) }
                        finally { file.delete() }
                    // A pause can occur during MediaStore publishing. Keep the saved file
                    // but only mark it completed when the record is still downloading.
                    if (dao.markCompleted(item.videoId, uri.toString()) == 0) {
                        // Avoid leaving an orphaned file if the user paused the task.
                        if (uri.scheme == "content") applicationContext.contentResolver.delete(uri, null, null)
                        else if (uri.scheme == "file") uri.path?.let { File(it).delete() }
                    }
                } catch (e: CancellationException) {
                    // Cancellation can race with a newly resumed worker; do not overwrite its state.
                    dao.pauseIfDownloading(item.videoId)
                    throw e
                } catch (e: Exception) {
                    if (isStopped) dao.pauseIfDownloading(item.videoId)
                    else dao.markFailed(item.videoId, MetadataSanitizer.error(e.message).ifBlank { "Error al descargar" })
                } finally {
                    activeIds.remove(item.videoId)
                    lastDbUpdates.remove(item.videoId)
                    stopMonitor.cancel()
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

    private fun publishVideo(context: Context, file: File, item: DownloadRecord): Uri {
        val extension = file.extension.lowercase().ifBlank { "mp4" }
        val account = DestinationNaming.account(item.videoUrl)
        val displayName = DestinationNaming.filename(item.title, item.videoId, extension)
        val mime = when (extension) {
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "m4a" -> "audio/mp4"
            "mp3" -> "audio/mpeg"
            "opus" -> "audio/ogg"
            "ogg" -> "audio/ogg"
            "aac" -> "audio/aac"
            else -> "application/octet-stream"
        }
        val folderUri = context.getSharedPreferences("tikitoki_settings", Context.MODE_PRIVATE)
            .getString("download_tree", null)
        if (folderUri != null) {
            val folder = DocumentFile.fromTreeUri(context, Uri.parse(folderUri))
                ?: throw IllegalStateException("La carpeta seleccionada ya no está disponible.")
            val target = synchronized(documentFolderLock) {
                val accountFolder = folder.findFile(account)?.takeIf { it.isDirectory }
                    ?: folder.createDirectory(account)
                    ?: throw IllegalStateException("No se pudo crear la carpeta de la cuenta.")
                accountFolder.createFile(mime, displayName)
                    ?: throw IllegalStateException("No se pudo crear el archivo en la carpeta seleccionada.")
            }
            try {
                context.contentResolver.openOutputStream(target.uri, "w")?.use { output ->
                    file.inputStream().use { input -> input.copyTo(output) }
                } ?: error("No se pudo escribir en la carpeta seleccionada.")
                return target.uri
            } catch (e: Exception) {
                target.delete()
                throw e
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/TikitokiDownloader/" + account)
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
            "TikitokiDownloader/$account"
        )
        if (!folder.exists() && !folder.mkdirs()) throw IllegalStateException("No se pudo crear Descargas.")
        val destination = File(folder, displayName)
        file.copyTo(destination, overwrite = false)
        return Uri.fromFile(destination)
    }
}

