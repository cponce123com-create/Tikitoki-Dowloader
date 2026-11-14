package com.tikitoki.downloader.domain

/** Pure validation: both ACTION_SEND and ACTION_VIEW must pass this gate. */
object IncomingLinkValidator {
    private const val MAX_LENGTH = 4096
    private val firstHttps = Regex("(?i)https://[^\\s]+")
    private val bidi = setOf('\u202a', '\u202b', '\u202c', '\u202d', '\u202e', '\u2066', '\u2067', '\u2068', '\u2069')

    fun validate(text: String?): MediaLink? {
        val value = text ?: return null
        if (value.isEmpty() || value.length > MAX_LENGTH || value != value.trim()) return null
        if (value.any { Character.isISOControl(it) || it.isWhitespace() || it in bidi }) return null
        return MediaLinkParser.parse(value)
    }

    /** Shared text may contain prose; validate the first HTTPS URL, never a later one. */
    fun extractFirstHttps(text: String?): MediaLink? {
        val value = text ?: return null
        if (value.length > MAX_LENGTH || value.any { Character.isISOControl(it) || it in bidi }) return null
        val candidate = firstHttps.find(value)?.value ?: return null
        return validate(candidate)
    }
}

