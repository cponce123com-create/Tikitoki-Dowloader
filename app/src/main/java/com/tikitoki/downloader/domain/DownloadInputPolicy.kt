package com.tikitoki.downloader.domain

/**
 * Revalidate at the persistence/execution boundary, not only in the UI.
 * Never forward raw external URLs or arbitrary command-line switches to yt-dlp.
 */
object DownloadInputPolicy {
    fun canonicalUrl(raw: String): String =
        requireNotNull(IncomingLinkValidator.validate(raw)) { "URL de descarga no permitida" }.url

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
