package com.tikitoki.downloader.data

/** Counts only metadata that the extractor actually returned for each item. */
internal object FormatAvailability {
    fun matches(preset: String, available: List<FormatChoice>): Boolean {
        if (preset == "bestaudio/best" || FormatOptions.mp3Bitrate(preset) != null) return available.any { it.isAudio }
        val ceiling = Regex("height<=([0-9]+)").find(preset)?.groupValues?.get(1)?.toIntOrNull()
        return available.any { option -> option.height?.let { ceiling == null || it <= ceiling } == true }
    }
}
