package com.tikitoki.downloader.data

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Keep a separate copy until an update and its version check have both succeeded. */
internal object ExtractorRollback {
    fun <T> run(binary: File, update: () -> T): T {
        require(binary.isFile && binary.length() > 0) { "Extractor unavailable" }
        val backup = File.createTempFile("extractor-backup-", ".bin", binary.parentFile.parentFile)
        var keepBackup = false
        try {
            binary.copyTo(backup, overwrite = true)
            try {
                return update()
            } catch (failure: Exception) {
                try {
                    binary.parentFile.mkdirs()
                    val replacement = File.createTempFile("extractor-restore-", ".tmp", binary.parentFile)
                    try {
                        backup.copyTo(replacement, overwrite = true)
                        Files.move(replacement.toPath(), binary.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    } finally { replacement.delete() }
                } catch (restoreFailure: Exception) {
                    failure.addSuppressed(restoreFailure)
                    // Keep the backup for manual recovery if restore itself failed.
                    keepBackup = true
                    throw RestoreFailedException(backup, failure)
                }
                throw failure
            }
        } finally {
            // Keep backups only when restoration itself did not succeed.
            if (!keepBackup) backup.delete()
        }
    }
}

internal class RestoreFailedException(val backup: File, cause: Throwable) : Exception(cause)
