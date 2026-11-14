package com.tikitoki.downloader.data

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemporaryFilesTest {
    @Test fun cleanupDoesNotFollowLinksOutsideTheCache() {
        val root = Files.createTempDirectory("tikitoki-clean-test").toFile()
        try {
            val cache = root.resolve("cache").apply { mkdirs() }
            val outside = root.resolve("saved-video.mp4").apply { writeText("keep") }
            cache.resolve("temporary.part").writeText("partial")
            Files.createSymbolicLink(cache.resolve("link").toPath(), outside.toPath())
            assertEquals(1, clearOwnTemporaryFiles(cache))
            assertTrue(outside.exists())
        } finally { root.deleteRecursively() }
    }
}
