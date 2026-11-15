package com.tikitoki.downloader.domain

/** Stores reusable preferences, never a format ID tied to a particular video. */
internal object DownloadFormatPreference {
    fun fromChoice(id: String, label: String): String {
        if (id.startsWith("bestaudio")) return "audio"
        val height = Regex("^(\\d+)p", RegexOption.IGNORE_CASE).find(label)?.groupValues?.get(1)
        return if (height != null) "height:$height" else "video"
    }

    fun matches(preference: String?, id: String, label: String, defaultVideoId: String): Boolean = when {
        preference == "audio" -> id.startsWith("bestaudio")
        preference?.startsWith("height:") == true -> label.startsWith(preference.removePrefix("height:") + "p", ignoreCase = true)
        else -> id == defaultVideoId
    }
}
