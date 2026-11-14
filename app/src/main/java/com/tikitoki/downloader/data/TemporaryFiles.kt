package com.tikitoki.downloader.data

import java.io.File

/** Only direct files under the app's private temp directory may be removed. */
internal fun clearOwnTemporaryFiles(folder: File): Int {
    val directory = folder.canonicalFile
    return directory.listFiles().orEmpty().count { file ->
        runCatching { file.isFile && file.canonicalFile.parentFile == directory && file.delete() }.getOrDefault(false)
    }
}
