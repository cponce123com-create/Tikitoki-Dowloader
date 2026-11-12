package com.tikitoki.downloader.domain

object MetadataSanitizer {
    fun title(raw: String?): String = clean(raw, 180).ifBlank { "Video sin título" }
    fun description(raw: String?): String = clean(raw, 2000)
    fun error(raw: String?): String = clean(raw, 350)
    private fun clean(raw: String?, limit: Int): String =
        raw.orEmpty().asSequence()
            .filterNot { it.isISOControl() || it == '\u202e' || it == '\u202d' || it == '\u2066' || it == '\u2067' || it == '\u2068' || it == '\u2069' }
            .take(limit).joinToString("").trim()
}
