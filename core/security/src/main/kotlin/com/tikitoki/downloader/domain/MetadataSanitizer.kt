package com.tikitoki.downloader.domain

import java.text.Normalizer

/**
 * Plain-text metadata boundary for persistence and display.
 * Compose Text does not interpret HTML, so do not HTML-escape ordinary text.
 */
object MetadataSanitizer {
    fun title(raw: String?): String = clean(raw, 180).ifBlank { "Video sin título" }
    fun description(raw: String?): String = clean(raw, 2000)
    fun error(raw: String?): String = clean(raw, 350)

    private fun clean(raw: String?, limit: Int): String {
        val normalized = Normalizer.normalize(raw.orEmpty(), Normalizer.Form.NFC)
        val result = StringBuilder(limit)
        var offset = 0
        var count = 0
        while (offset < normalized.length && count < limit) {
            val codePoint = normalized.codePointAt(offset)
            offset += Character.charCount(codePoint)
            val type = Character.getType(codePoint)
            val forbidden = Character.isISOControl(codePoint) ||
                type == Character.FORMAT.toInt() ||
                type == Character.LINE_SEPARATOR.toInt() ||
                type == Character.PARAGRAPH_SEPARATOR.toInt() ||
                type == Character.SURROGATE.toInt() ||
                codePoint in 0x202A..0x202E ||
                codePoint in 0x2066..0x2069
            if (!forbidden) {
                result.appendCodePoint(codePoint)
                count++
            }
        }
        // Removing invisible format characters can join combining marks with letters.
        // Normalize again after filtering/truncation to guarantee NFC output.
        return Normalizer.normalize(result.toString().trim(), Normalizer.Form.NFC)
    }
}
