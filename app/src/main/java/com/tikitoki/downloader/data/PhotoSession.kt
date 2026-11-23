package com.tikitoki.downloader.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.Locale

/** Session files remain in app-private, non-backed-up storage. */
@Suppress("MagicNumber")
internal object PhotoSession {
    private const val MAX_BYTES = 1024 * 1024
    private val domains = listOf("facebook.com", "instagram.com", "tiktok.com")

    fun isPresent(context: Context): Boolean = File(context.noBackupFilesDir, PhotoDownloadWorker.COOKIE_FILE).isFile

    /** Only checks whether the two Facebook authentication cookies are present locally.
     * The server may still reject an expired or revoked session.
     */
    fun hasFacebookLogin(context: Context): Boolean {
        val file = File(context.noBackupFilesDir, PhotoDownloadWorker.COOKIE_FILE)
        if (!file.isFile || file.length() > MAX_BYTES) return false
        return runCatching { hasFacebookLoginCookies(file.readText()) }.getOrDefault(false)
    }

    internal fun hasFacebookLoginCookies(contents: String): Boolean {
        val names =
            sanitize(contents)
                .asSequence()
                .map { it.split('\t') }
                .filter { fields ->
                    val host = fields[0].removePrefix("#HttpOnly_").trimStart('.').lowercase(Locale.ROOT)
                    host == "facebook.com" || host.endsWith(".facebook.com")
                }.map { it[5] }
                .toSet()
        return "c_user" in names && "xs" in names
    }

    fun delete(context: Context) {
        File(context.noBackupFilesDir, PhotoDownloadWorker.COOKIE_FILE).delete()
    }

    fun import(
        context: Context,
        uri: Uri,
    ): Int {
        val bytes =
            requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (output.size() <= MAX_BYTES) {
                    val length = input.read(buffer, 0, minOf(buffer.size, MAX_BYTES + 1 - output.size()))
                    if (length < 0) break
                    output.write(buffer, 0, length)
                }
                output.toByteArray()
            }
        require(bytes.size <= MAX_BYTES) { "El archivo de sesión es demasiado grande." }
        val entries = sanitize(bytes.toString(Charsets.UTF_8))
        require(entries.isNotEmpty()) { "El archivo no contiene cookies compatibles." }
        val target = File(context.noBackupFilesDir, PhotoDownloadWorker.COOKIE_FILE)
        val temp = File(context.noBackupFilesDir, "photo-session.tmp")
        try {
            temp.writeText("# Netscape HTTP Cookie File\n" + entries.joinToString("\n") + "\n")
            check(temp.renameTo(target))
        } finally {
            temp.delete()
        }
        return entries.size
    }

    internal fun sanitize(contents: String): List<String> =
        contents
            .lineSequence()
            .mapNotNull { raw ->
                val line = raw.trimEnd('\r')
                if (line.isBlank() || (line.startsWith("#") && !line.startsWith("#HttpOnly_"))) return@mapNotNull null
                val fields = line.split('\t')
                if (fields.size != 7) return@mapNotNull null
                val host = fields[0].removePrefix("#HttpOnly_").trimStart('.').lowercase(Locale.ROOT)
                if (domains.none { host == it || host.endsWith(".$it") }) return@mapNotNull null
                if (fields.any { field -> field.any { it.isISOControl() } }) return@mapNotNull null
                if (fields[1] !in setOf("TRUE", "FALSE") || fields[3] !in setOf("TRUE", "FALSE")) return@mapNotNull null
                if (!fields[2].startsWith("/") || fields[5].isBlank()) return@mapNotNull null
                if (fields[4].isNotEmpty() && fields[4].any { !it.isDigit() }) return@mapNotNull null
                line
            }.toList()
}
