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

    /** Local presence and expiry only; Facebook may still revoke any cookie at any time. */
    fun hasFacebookLogin(context: Context): Boolean = facebookCookieState(context) == FacebookCookieState.PRESENT

    fun facebookCookieState(context: Context): FacebookCookieState {
        val file = File(context.noBackupFilesDir, PhotoDownloadWorker.COOKIE_FILE)
        if (!file.isFile) return FacebookCookieState.MISSING
        if (file.length() > MAX_BYTES) return FacebookCookieState.INCOMPLETE
        return runCatching { facebookCookieStateFromText(file.readText()) }
            .getOrDefault(FacebookCookieState.INCOMPLETE)
    }

    internal fun hasFacebookLoginCookies(contents: String): Boolean =
        facebookCookieStateFromText(contents) == FacebookCookieState.PRESENT

    internal fun facebookCookieStateFromText(
        contents: String,
        nowSeconds: Long = System.currentTimeMillis() / 1000L,
    ): FacebookCookieState {
        val present = mutableSetOf<String>()
        val expired = mutableSetOf<String>()
        sanitize(contents).forEach { line ->
            val fields = line.split('\t')
            val host = fields[0].removePrefix("#HttpOnly_").trimStart('.').lowercase(Locale.ROOT)
            if (host != "facebook.com" && !host.endsWith(".facebook.com")) return@forEach
            val name = fields[5]
            if (name !in setOf("c_user", "xs")) return@forEach
            val expiry = fields[4].toLongOrNull() ?: return@forEach
            if (expiry == 0L || expiry > nowSeconds) present.add(name) else expired.add(name)
        }
        val required = setOf("c_user", "xs")
        return when {
            present.containsAll(required) -> FacebookCookieState.PRESENT
            (present + expired).containsAll(required) -> FacebookCookieState.EXPIRED
            else -> FacebookCookieState.INCOMPLETE
        }
    }

    fun delete(context: Context) {
        File(context.noBackupFilesDir, PhotoDownloadWorker.COOKIE_FILE).delete()
        FacebookSessionHealth.reset(context)
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
            FacebookSessionHealth.reset(context)
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

internal enum class FacebookCookieState {
    MISSING,
    INCOMPLETE,
    EXPIRED,
    PRESENT,
}
