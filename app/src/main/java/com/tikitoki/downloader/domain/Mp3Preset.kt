package com.tikitoki.downloader.domain

/** Keeps existing queued and saved rates valid as new choices are added. */
internal object Mp3Preset {
    @Suppress("MagicNumber")
    val legacyRates = listOf(128, 192, 256, 320)

    @Suppress("MagicNumber")
    val averageRates = listOf(180, 240)
    const val DEFAULT_ID = "bajamp3-128"
    private const val PREFIX = "bajamp3-"

    fun bitrate(id: String): Int? =
        id
            .takeIf { it.startsWith(PREFIX) }
            ?.removePrefix(PREFIX)
            ?.toIntOrNull()
            ?.takeIf { it in legacyRates || it in averageRates }
}
