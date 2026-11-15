package com.tikitoki.downloader.data

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class ExtractorRollbackTest {
    @Test fun restoresPreviousBinaryAfterFailedUpdate() {
        val dir = Files.createTempDirectory("rollback-test").toFile()
        try {
            val binary = dir.resolve("engine").apply { mkdirs() }.resolve("yt-dlp")
            binary.writeText("previous")
            try { ExtractorRollback.run(binary) { binary.writeText("broken"); throw IllegalStateException("failed") }; fail() }
            catch (_: IllegalStateException) { }
            assertEquals("previous", binary.readText())
        } finally { dir.deleteRecursively() }
    }
    @Test fun keepsNewBinaryWhenUpdateSucceeds() {
        val dir = Files.createTempDirectory("rollback-test").toFile()
        try {
            val binary = dir.resolve("engine").apply { mkdirs() }.resolve("yt-dlp")
            binary.writeText("previous")
            ExtractorRollback.run(binary) { binary.writeText("new") }
            assertEquals("new", binary.readText())
        } finally { dir.deleteRecursively() }
    }
}
