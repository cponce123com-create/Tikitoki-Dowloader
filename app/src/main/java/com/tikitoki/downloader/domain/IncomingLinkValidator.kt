package com.tikitoki.downloader.domain

/** Pure validation shared by Android Intent receiver and pasted URLs. */
object IncomingLinkValidator {
    private const val MAX_LENGTH = 4096
    private val forbiddenOptions = Regex("(?i)(?:^|\\s)--(?:exec|netrc-cmd|config-location|config|batch-file|plugin-dirs|cookies-from-browser|cookies)(?:\\s|=|$)")

    fun validate(text: String?): MediaLink? {
        val value = text?.trim() ?: return null
        if (value.isEmpty() || value.length > MAX_LENGTH) return null
        if (value.any { Character.isISOControl(it) } || forbiddenOptions.containsMatchIn(value)) return null
        if (value.any { it.isWhitespace() }) return null
        return MediaLinkParser.parse(value)
    }
}
