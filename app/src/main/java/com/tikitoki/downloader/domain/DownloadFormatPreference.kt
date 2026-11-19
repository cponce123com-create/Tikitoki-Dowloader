package com.tikitoki.downloader.domain

/** Stores reusable preferences, never a format ID tied to a particular video. */
internal object DownloadFormatPreference {
    fun fromChoice(
        id: String,
        label: String,
        height: Int? = null,
    ): String =
        when {
            Mp3Preset.bitrate(id) != null -> id
            id.startsWith("bestaudio") || id.startsWith("bajaaudio-") -> "audio"
            else -> (height?.toString() ?: parseResolution(id, label))?.let { "height:$it" } ?: "video"
        }

    fun matches(
        preference: String?,
        id: String,
        label: String,
        defaultVideoId: String,
        height: Int? = null,
    ): Boolean =
        when {
            preference?.startsWith("bajamp3-") == true -> id == preference
            preference == "audio" -> id.startsWith("bestaudio") || id.startsWith("bajaaudio-")
            preference?.startsWith("height:") == true -> fromChoice(id, label, height) == preference
            else -> id == defaultVideoId
        }

    fun prefersAudio(preference: String?): Boolean {
        val bitrate = preference?.let(Mp3Preset::bitrate)
        return preference == "audio" || bitrate != null
    }

    private fun parseResolution(
        id: String,
        label: String,
    ): String? =
        Regex("height<=([0-9]+)").find(id)?.groupValues?.get(1)
            ?: Regex("^(\\d+)p", RegexOption.IGNORE_CASE).find(label)?.groupValues?.get(1)
}
