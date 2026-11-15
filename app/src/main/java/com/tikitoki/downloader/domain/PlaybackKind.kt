package com.tikitoki.downloader.domain

import java.util.Locale

/** Classifies downloads using the requested format, MIME type and saved filename. */
internal object PlaybackKind {
    private val audioExtensions = setOf("aac", "flac", "m4a", "m4b", "mp3", "oga", "ogg", "opus", "wav")

    fun isAudio(format: String?, mime: String?, filename: String?): Boolean {
        val selector = format.orEmpty().lowercase(Locale.ROOT).trim()
        if (selector.startsWith("bestaudio") || selector.startsWith("worstaudio") || selector.matches(Regex("bajamp3-(128|192|256|320)"))) return true
        if (mime.orEmpty().lowercase(Locale.ROOT).startsWith("audio/")) return true
        val extension = filename.orEmpty().substringAfterLast('.', "").lowercase(Locale.ROOT)
        return extension in audioExtensions
    }
}

