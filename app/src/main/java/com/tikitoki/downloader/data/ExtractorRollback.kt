package com.tikitoki.downloader.data

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** A published backup is a journal: it remains until binary and metadata are consistent. */
internal object ExtractorRollback {
    private fun backupFor(binary: File): File = File(binary.parentFile.parentFile, "yt-dlp-rollback.bin")

    private fun copyAndSync(source: File, destination: File) {
        source.inputStream().use { input ->
            FileOutputStream(destination).use { output -> input.copyTo(output); output.fd.sync() }
        }
    }

    fun recover(binary: File, restoreMetadata: () -> Unit = {}): Boolean {
        val backup = backupFor(binary)
        if (!backup.exists()) return false
        require(backup.isFile && backup.length() > 0) { "Invalid extractor backup" }
        check(binary.parentFile.isDirectory || binary.parentFile.mkdirs()) { "Cannot create extractor directory" }
        val temp = File.createTempFile("yt-dlp-restore-", ".tmp", binary.parentFile)
        try {
            copyAndSync(backup, temp)
            Files.move(temp.toPath(), binary.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            restoreMetadata()
            check(backup.delete()) { "Cannot clear extractor journal" }
        } finally { temp.delete() }
        return true
    }

    fun <T> run(binary: File, restoreMetadata: () -> Unit = {}, update: () -> T): T {
        require(binary.isFile && binary.length() > 0) { "Extractor unavailable" }
        val backup = backupFor(binary)
        check(!backup.exists()) { "Extractor recovery required" }
        val temp = File.createTempFile("yt-dlp-snapshot-", ".tmp", backup.parentFile)
        try {
            copyAndSync(binary, temp)
            Files.move(temp.toPath(), backup.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } finally { temp.delete() }
        try {
            val result = update()
            check(backup.delete()) { "Cannot clear extractor journal" }
            return result
        } catch (failure: Exception) {
            try { recover(binary, restoreMetadata) }
            catch (restoreFailure: Exception) {
                failure.addSuppressed(restoreFailure)
                throw RestoreFailedException(backup, failure)
            }
            throw failure
        }
    }
}

internal class RestoreFailedException(val backup: File, cause: Throwable) : Exception(cause)
