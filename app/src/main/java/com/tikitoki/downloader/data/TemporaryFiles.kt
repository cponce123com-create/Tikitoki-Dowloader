package com.tikitoki.downloader.data

import java.io.File

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
