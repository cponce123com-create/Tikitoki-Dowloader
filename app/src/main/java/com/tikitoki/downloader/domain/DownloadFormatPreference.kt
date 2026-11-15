package com.tikitoki.downloader.domain

/** Stores reusable preferences, never a format ID tied to a particular video. */
internal object DownloadFormatPreference {
    fun fromChoice(id: String, label: String): String {
        if (id.matches(Regex("bajamp3-(128|192|256|320)"))) return id
        if (id.startsWith("bestaudio") || id.startsWith("bajaaudio-")) return "audio"
        val height = Regex("height<=([0-9]+)").find(id)?.groupValues?.get(1) ?: Regex("^(\\d+)p", RegexOption.IGNORE_CASE).find(label)?.groupValues?.get(1)
        return if (height != null) "height:$height" else "video"
    }

    fun matches(preference: String?, id: String, label: String, defaultVideoId: String): Boolean = when {
        preference?.startsWith("bajamp3-") == true -> id == preference
        preference == "audio" -> id.startsWith("bestaudio")
        preference?.startsWith("height:") == true -> (label.startsWith(preference.removePrefix("height:") + "p", ignoreCase = true) || id.contains("height<=${preference.removePrefix("height:")}"))
        else -> id == defaultVideoId
    }
}

