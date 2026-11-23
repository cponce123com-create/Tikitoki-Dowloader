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

    internal const val LEGACY_FILE = "photo-session-cookies.txt"
    internal const val IMPORT_NOTICE =
        "Importa solo tu propia sesión. Baja.pe cifra las cookies en este dispositivo y las usa al " +
            "descargar fotos. Pueden dar acceso a contenido privado. El archivo original se borra por separado."
    private const val HEADER = "# Netscape HTTP Cookie File\n"
    private const val MAX_STORED_BYTES = MAX_BYTES + 64

    fun isPresent(context: Context): Boolean =
        EncryptedPhotoSession.exists(context) || legacy(context).exists()

    /** Local presence and expiry only; Facebook may revoke cookies at any time. */
    fun hasFacebookLogin(context: Context): Boolean = facebookCookieState(context) == FacebookCookieState.PRESENT

    fun facebookCookieState(context: Context): FacebookCookieState =
        runCatching {
            read(context)?.let { facebookCookieStateFromText(it) } ?: FacebookCookieState.MISSING
        }.getOrDefault(FacebookCookieState.INCOMPLETE)

    @Synchronized
    fun read(context: Context): String? {
        val old = legacy(context)
        if (!EncryptedPhotoSession.exists(context)) {
            if (!old.isFile) return null
            require(old.length() <= MAX_STORED_BYTES)
            EncryptedPhotoSession.write(context, old.readText())
        }
        val contents = EncryptedPhotoSession.read(context, MAX_STORED_BYTES)
        removeLegacy(context)
        return contents
    }

    private fun legacy(context: Context): File = File(context.noBackupFilesDir, LEGACY_FILE)

    private fun removeLegacy(context: Context) {
        val old = legacy(context)
        check(!old.exists() || old.delete())
        val temporary = File(context.noBackupFilesDir, "photo-session.tmp")
        check(!temporary.exists() || temporary.delete())
    }

    internal fun hasFacebookLoginCookies(contents: String): Boolean {
        val state = facebookCookieStateFromText(contents)
        return state == FacebookCookieState.PRESENT
    }

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

    @Synchronized
    fun delete(context: Context) {
        removeLegacy(context)
        EncryptedPhotoSession.delete(context)
        FacebookSessionHealth.reset(context)
    }

    @Synchronized
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
        EncryptedPhotoSession.write(context, HEADER + entries.joinToString("\n") + "\n")
        removeLegacy(context)
        FacebookSessionHealth.reset(context)
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
