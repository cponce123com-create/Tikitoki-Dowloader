package com.tikitoki.downloader.data

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import java.io.File
import java.security.MessageDigest

/** Runs a pinned gallery-dl archive using the Python runtime already shipped with yt-dlp. */
internal class GalleryEngine(
    private val context: Context,
) {
    companion object {
        private const val ENGINE_ASSET = "gallery-engine.pyz"
        private const val ENGINE_SHA256 = "960030368700187a0bb4714c2c63215b7fb349c01c4f856a7a54c2293770a28a"
        private val installLock = Any()
    }

    fun run(
        arguments: List<String>,
        onLine: (String) -> Unit = {},
    ): Int {
        val app = context.applicationContext
        val archive =
            synchronized(installLock) {
                ExtractorAccess.useEngine { YoutubeDL.getInstance().init(app) }
                installArchive(app)
            }
        val executable = File(app.applicationInfo.nativeLibraryDir, "libpython.so")
        val base = File(app.noBackupFilesDir, "youtubedl-android/packages")
        val pythonHome = File(base, "python/usr")
        val command = listOf(executable.absolutePath, archive.absolutePath) + arguments
        val process =
            ProcessBuilder(command)
                .redirectErrorStream(true)
                .apply {
                    environment()["PYTHONHOME"] = pythonHome.absolutePath
                    environment()["HOME"] = app.filesDir.absolutePath
                    environment()["TMPDIR"] = app.cacheDir.absolutePath
                    environment()["SSL_CERT_FILE"] = File(pythonHome, "etc/tls/cert.pem").absolutePath
                    environment()["LD_LIBRARY_PATH"] =
                        listOf(
                            File(pythonHome, "lib"),
                            File(base, "ffmpeg/usr/lib"),
                            File(base, "aria2c/usr/lib"),
                        ).joinToString(":") { it.absolutePath }
                    environment()["PYTHONUNBUFFERED"] = "1"
                }.start()
        val readerFailure =
            java.util.concurrent.atomic
                .AtomicReference<Exception?>()
        val reader =
            Thread {
                try {
                    process.inputStream.bufferedReader().useLines { lines -> lines.forEach(onLine) }
                } catch (error: Exception) {
                    readerFailure.set(error)
                    process.destroy()
                }
            }
        reader.start()
        try {
            val code = process.waitFor()
            reader.join()
            readerFailure.get()?.let { throw it }
            return code
        } finally {
            process.destroy()
            reader.interrupt()
        }
    }

    private fun installArchive(app: Context): File {
        val target = File(app.noBackupFilesDir, ENGINE_ASSET)
        if (target.isFile && sha256(target) == ENGINE_SHA256) return target
        val temporary = File(app.noBackupFilesDir, "$ENGINE_ASSET.tmp")
        try {
            app.assets.open(ENGINE_ASSET).use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            }
            check(sha256(temporary) == ENGINE_SHA256) { "Invalid photo engine" }
            check(temporary.renameTo(target)) { "Unable to install photo engine" }
        } finally {
            temporary.delete()
        }
        return target
    }

    @Suppress("MagicNumber")
    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val bytes = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = stream.read(bytes)
                if (read < 0) break
                digest.update(bytes, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
