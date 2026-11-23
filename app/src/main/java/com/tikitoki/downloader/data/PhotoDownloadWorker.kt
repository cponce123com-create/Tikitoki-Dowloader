package com.tikitoki.downloader.data

import android.content.ContentValues
import android.content.Context
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.tikitoki.downloader.domain.DestinationNaming
import com.tikitoki.downloader.domain.FacebookShareResolver
import com.tikitoki.downloader.domain.IncomingLinkValidator
import com.tikitoki.downloader.domain.MediaPlatform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Downloads public or explicitly authenticated image collections in a separate queue. */
internal class PhotoDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    companion object {
        const val UNIQUE = "bajape_photo_download"
        const val URL_KEY = "url"
        const val RESULT_KEY = "message"
        const val COUNT_KEY = "count"
        const val COOKIE_FILE = "photo-session-cookies.txt"
        private const val NOTIFICATION_ID = 903
        private const val CHANNEL = "bajape_photo_download"
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod", "TooGenericExceptionCaught")
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val parsed = IncomingLinkValidator.validate(inputData.getString(URL_KEY))
            val supported = setOf(MediaPlatform.FACEBOOK, MediaPlatform.INSTAGRAM, MediaPlatform.TIKTOK)
            if (parsed == null || parsed.platform !in supported) {
                return@withContext Result.failure(workDataOf(RESULT_KEY to "El enlace no es compatible con fotos."))
            }
            try {
                setForeground(foreground("Preparando fotos"))
            } catch (_: IllegalStateException) {
                return@withContext Result.retry()
            }
            val temp = File(applicationContext.cacheDir, "photo-job-$id")
            temp.mkdirs()
            var count = 0
            var skipped = 0
            var errors = 0
            var loginNeeded = false
            try {
                val url = if (parsed.platform == MediaPlatform.FACEBOOK) resolveShare(parsed.url) else parsed.url
                val account = DestinationNaming.account(url)
                val directory = "Pictures/Baja.pe/${parsed.platform.name.lowercase()}/$account/"
                val cookies = File(applicationContext.noBackupFilesDir, COOKIE_FILE)
                val arguments =
                    mutableListOf(
                        "--config-ignore",
                        "--no-input",
                        "--no-part",
                        "--no-mtime",
                        "--restrict-filenames",
                        "windows",
                        "--sleep-request",
                        "0.5",
                        "--filesize-max",
                        "60M",
                        "--whitelist",
                        "facebook,instagram,tiktok",
                        "-o",
                        "extractor.facebook.videos=false",
                        "-o",
                        "extractor.instagram.videos=false",
                        "-o",
                        "extractor.tiktok.videos=false",
                        "--filter",
                        "extension.lower() in ('jpg','jpeg','png','webp','gif')",
                        "-D",
                        temp.absolutePath,
                    )
                if (cookies.isFile) arguments.addAll(listOf("--cookies", cookies.absolutePath))
                arguments.add("--")
                arguments.add(url)
                val root = temp.canonicalPath + File.separator
                val exitCode =
                    runInterruptible {
                        GalleryEngine(applicationContext).run(arguments) { line ->
                            if (line.contains("AuthRequired", true) || line.contains("login required", true)) {
                                loginNeeded = true
                            }
                            val file = File(line)
                            if (file.isFile &&
                                file.canonicalPath.startsWith(root) &&
                                file.extension.lowercase() in IMAGE_EXTENSIONS
                            ) {
                                try {
                                    if (publish(file, directory)) count++ else skipped++
                                } catch (_: Exception) {
                                    errors++
                                } finally {
                                    file.delete()
                                }
                                setProgressAsync(workDataOf(COUNT_KEY to count))
                            }
                        }
                    }
                if (isStopped) return@withContext Result.failure()
                val message =
                    when {
                        loginNeeded -> "La plataforma requiere sesión. Importa tus cookies y vuelve a intentar."
                        exitCode != 0 -> "Se guardaron $count fotos; no se pudo completar la consulta."
                        errors > 0 -> "Se guardaron $count fotos; $errors no se pudieron guardar."
                        count == 0 && skipped == 0 -> "No se encontraron fotos accesibles para este enlace."
                        else -> "Se guardaron $count fotos. Ya existentes: $skipped."
                    }
                if (exitCode != 0 || errors > 0 || loginNeeded) {
                    Result.failure(workDataOf(RESULT_KEY to message))
                } else {
                    Result.success(workDataOf(RESULT_KEY to message))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                Result.failure()
            } catch (_: Exception) {
                val message = "No se pudo descargar la galería. Revisa la sesión y vuelve a intentar."
                Result.failure(workDataOf(RESULT_KEY to message))
            } finally {
                temp.deleteRecursively()
            }
        }

    @Suppress("MagicNumber")
    private fun resolveShare(url: String): String =
        FacebookShareResolver.resolve(url) { current ->
            val connection = URL(current).openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                if (connection.responseCode in 300..399) connection.getHeaderField("Location") else null
            } finally {
                connection.disconnect()
            }
        } ?: url

    @Suppress("MagicNumber", "ReturnCount", "TooGenericExceptionCaught")
    private fun publish(
        file: File,
        directory: String,
    ): Boolean {
        val extension = file.extension.lowercase()
        val name = file.nameWithoutExtension.take(130) + "." + extension
        val mime =
            when (extension) {
                "png" -> "image/png"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                else -> "image/jpeg"
            }
        if (Build.VERSION.SDK_INT < 29) {
            val folder =
                File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    directory.removePrefix("Pictures/"),
                )
            check(folder.isDirectory || folder.mkdirs())
            val dest = File(folder, name)
            if (dest.exists()) return false
            file.copyTo(dest)
            MediaScannerConnection.scanFile(applicationContext, arrayOf(dest.absolutePath), arrayOf(mime), null)
            return true
        }
        val resolver = applicationContext.contentResolver
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        resolver
            .query(
                collection,
                arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media.RELATIVE_PATH}=? AND ${MediaStore.Images.Media.DISPLAY_NAME}=?",
                arrayOf(directory, name),
                null,
            )?.use {
                if (it.moveToFirst()) return false
            }
        val values =
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, directory)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        val target = checkNotNull(resolver.insert(collection, values))
        try {
            checkNotNull(resolver.openOutputStream(target)).use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(target, values, null, null)
            return true
        } catch (error: Exception) {
            resolver.delete(target, null, null)
            throw error
        }
    }

    @Suppress("MagicNumber")
    private fun foreground(message: String): ForegroundInfo {
        val manager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.createNotificationChannel(
            android.app.NotificationChannel(
                CHANNEL,
                "Descarga de fotos",
                android.app.NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val notification =
            NotificationCompat
                .Builder(applicationContext, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Baja.pe")
                .setContentText(message)
                .setOngoing(true)
                .build()
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }
}

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif")
