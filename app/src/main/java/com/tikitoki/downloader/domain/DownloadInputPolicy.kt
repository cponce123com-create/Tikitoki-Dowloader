package com.tikitoki.downloader.domain

/**
 * Revalidate at the persistence/execution boundary, not only in the UI.
 * Never forward raw external URLs or arbitrary command-line switches to yt-dlp.
 */
object DownloadInputPolicy {
    fun canonicalUrl(raw: String): String =
        requireNotNull(IncomingLinkValidator.validate(raw)) { "URL de descarga no permitida" }.url

    /**
     * Extractor metadata is untrusted. Never synthesize a URL from an arbitrary ID,
     * and never allow a cross-platform webpage URL to enter the download queue.
     */
    fun extractorPage(
        platform: MediaPlatform,
        id: String,
        original: String?,
        sourceUrl: String,
        collection: Boolean,
    ): String? {
        val validId = id.length in 1..128 &&
            id.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '_' || it == '-' }
        val candidate = original?.takeIf { it.isNotBlank() } ?: when {
            platform == MediaPlatform.YOUTUBE && validId ->
                "https://www.youtube.com/watch?v=$id"
            !collection -> sourceUrl
            else -> return null
        }
        val parsed = IncomingLinkValidator.validate(candidate) ?: return null
        return parsed.url.takeIf { parsed.platform == platform }
    }

    fun safeFormatId(value: String): String {
        require(value.isNotBlank() && value.length <= 160) { "Formato de descarga no permitido" }
        require(value.none { Character.isISOControl(it) || it.isWhitespace() }) {
            "Formato de descarga no permitido"
        }
        require(!value.startsWith("-") && !value.contains("--")) {
            "Formato de descarga no permitido"
        }
        require(value.all { it.isLetterOrDigit() || it in ".+_,:/[]()=!?*<>-" }) {
            "Formato de descarga no permitido"
        }
        return value
    }
}
