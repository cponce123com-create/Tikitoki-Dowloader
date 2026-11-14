package com.tikitoki.downloader.data

import java.io.File

internal fun retainVideoTemporaryFiles(status: String?, stopped: Boolean): Boolean =
    status == DownloadState.PAUSED || status == DownloadState.QUEUED ||
        (status == DownloadState.DOWNLOADING && stopped)

internal fun safeTemporaryDirectory(cacheRoot: File): File {
    val folder = File(cacheRoot.canonicalFile, "tikitoki").absoluteFile
    require(folder.canonicalFile == folder) { "La carpeta temporal no es válida." }
    return folder
}

/** Only direct files under the app's private temp directory may be removed. */
internal fun clearOwnTemporaryFiles(folder: File): Int {
    val directory = folder.canonicalFile
    return directory.listFiles().orEmpty().count { file ->
        runCatching { file.isFile && file.canonicalFile.parentFile == directory && file.delete() }.getOrDefault(false)
    }
}

/** yt-dlp names all files for one job with the sanitized ID followed by a dot. */
internal fun clearVideoTemporaryFiles(folder: File, videoId: String): Int {
    val safeId = videoId.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
    if (safeId.isBlank()) return 0
    val directory = folder.canonicalFile
    return directory.listFiles().orEmpty().count { file ->
        runCatching {
            file.name.startsWith("$safeId.") && file.isFile &&
                file.canonicalFile == file.absoluteFile && file.canonicalFile.parentFile == directory && file.delete()
        }.getOrDefault(false)
    }
}

/** Reserve a new path atomically so concurrent downloads never overwrite a prior file. */
internal fun reserveUniqueFile(folder: File, displayName: String): File {
    val dot = displayName.lastIndexOf('.').takeIf { it > 0 } ?: displayName.length
    val stem = displayName.substring(0, dot)
    val suffix = displayName.substring(dot)
    for (number in 0..100_000) {
        val name = if (number == 0) displayName else "$stem ($number)$suffix"
        val candidate = File(folder, name)
        if (candidate.createNewFile()) return candidate
    }
    throw IllegalStateException("No se encontró un nombre disponible.")
}

