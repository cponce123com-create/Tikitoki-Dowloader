package com.tikitoki.downloader.data

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Assert.assertFalse
import org.junit.Test

class TemporaryFilesTest {
    @Test fun cleanupTargetsOnlyTheExactVideoAndNeverFollowsSymlinks() {
        val root = Files.createTempDirectory("video-clean-test").toFile()
        try {
            val cache = root.resolve("tikitoki").apply { mkdirs() }
            val outside = root.resolve("outside").apply { writeText("keep") }
            listOf("123.part", "123.mp4.part", "123.ytdl", "123.f137.mp4").forEach { cache.resolve(it).writeText("temp") }
            cache.resolve("1234.part").writeText("other")
            Files.createSymbolicLink(cache.resolve("123.link").toPath(), outside.toPath())
            assertEquals(4, clearVideoTemporaryFiles(cache, "123"))
            assertTrue(cache.resolve("1234.part").exists())
            assertTrue(outside.exists())
        } finally { root.deleteRecursively() }
    }

    @Test fun partialFilesAreKeptOnlyForResumableStates() {
        assertTrue(retainVideoTemporaryFiles(DownloadState.PAUSED, false))
        assertTrue(retainVideoTemporaryFiles(DownloadState.QUEUED, false))
        assertTrue(retainVideoTemporaryFiles(DownloadState.DOWNLOADING, true))
        assertFalse(retainVideoTemporaryFiles(DownloadState.FAILED, false))
        assertFalse(retainVideoTemporaryFiles(DownloadState.CANCELLED, false))
    }

    @Test fun reservesDistinctNamesWithoutOverwritingExistingFile() {
        val root = Files.createTempDirectory("unique-file-test").toFile()
        try {
            root.resolve("video.mp4").writeText("existing")
            val first = reserveUniqueFile(root, "video.mp4")
            val second = reserveUniqueFile(root, "video.mp4")
            assertEquals("video (1).mp4", first.name)
            assertEquals("video (2).mp4", second.name)
            assertEquals("existing", root.resolve("video.mp4").readText())
        } finally { root.deleteRecursively() }
    }
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

    @Test fun cacheDirectoryItselfCannotRedirectOutside() {
        val root = Files.createTempDirectory("tikitoki-root-test").toFile()
        try {
            val outside = root.resolve("other").apply { mkdirs() }
            Files.createSymbolicLink(root.resolve("tikitoki").toPath(), outside.toPath())
            assertThrows(IllegalArgumentException::class.java) { safeTemporaryDirectory(root) }
        } finally { root.deleteRecursively() }
    }
}

