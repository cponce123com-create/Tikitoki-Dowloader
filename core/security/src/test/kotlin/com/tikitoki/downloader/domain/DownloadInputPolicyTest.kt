package com.tikitoki.downloader.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [DownloadInputPolicy].
 * Validates canonical URL enforcement, extractor page handling, and format ID sanitization.
 */
class DownloadInputPolicyTest {

    @Test
    fun `canonicalUrl validates and returns normalized URL`() {
        val result = DownloadInputPolicy.canonicalUrl(TestUtils.TIKTOK_URL_VIDEO)
        assertNotNull(result)
        assertTrue(result.startsWith("https://www.tiktok.com"))
    }

    @Test
    fun `canonicalUrl rejects invalid URL`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            DownloadInputPolicy.canonicalUrl(TestUtils.INVALID_URL_EMPTY)
        }
        assertEquals("URL de descarga no permitida", exception.message)
    }

    @Test
    fun `extractorPage returns valid URL for YouTube`() {
        val result = DownloadInputPolicy.extractorPage(
            platform = MediaPlatform.YOUTUBE,
            id = "dQw4w9WgXcQ",
            original = null,
            sourceUrl = TestUtils.YOUTUBE_URL_VIDEO,
            collection = false,
        )
        assertNotNull(result)
        assertTrue(result.contains("dQw4w9WgXcQ"))
    }

    @Test
    fun `extractorPage validates platform match`() {
        // Trying to use YouTube ID for TikTok should fail
        val result = DownloadInputPolicy.extractorPage(
            platform = MediaPlatform.TIKTOK,
            id = "dQw4w9WgXcQ",
            original = null,
            sourceUrl = TestUtils.YOUTUBE_URL_VIDEO,
            collection = false,
        )
        assertNull(result)
    }

    @Test
    fun `extractorPage rejects cross-platform redirect for DIRECT_MEDIA`() {
        val result = DownloadInputPolicy.extractorPage(
            platform = MediaPlatform.DIRECT_MEDIA,
            id = "123456",
            original = "https://vimeo.com/123456",
            sourceUrl = "https://dailymotion.com/video/123456",
            collection = false,
        )
        // Different hosts for DIRECT_MEDIA should fail
        assertNull(result)
    }

    @Test
    fun `extractorPage allows same host for DIRECT_MEDIA`() {
        val result = DownloadInputPolicy.extractorPage(
            platform = MediaPlatform.DIRECT_MEDIA,
            id = "123456",
            original = "https://vimeo.com/123456",
            sourceUrl = "https://vimeo.com/123456",
            collection = false,
        )
        assertNotNull(result)
    }

    @Test
    fun `extractorPage returns null for collection without original`() {
        val result = DownloadInputPolicy.extractorPage(
            platform = MediaPlatform.TIKTOK,
            id = "user",
            original = null,
            sourceUrl = TestUtils.TIKTOK_URL_PROFILE,
            collection = true,
        )
        assertNull(result)
    }

    @Test
    fun `safeFormatId accepts valid format IDs`() {
        val validFormats = listOf(
            "bestvideo+bestaudio/best",
            "best[height<=720]",
            "137+140",
            "bestvideo[ext=mp4]+bestaudio[ext=m4a]",
        )
        validFormats.forEach { format ->
            val result = DownloadInputPolicy.safeFormatId(format)
            assertEquals(format, result)
        }
    }

    @Test
    fun `safeFormatId rejects empty string`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            DownloadInputPolicy.safeFormatId("")
        }
        assertEquals("Formato de descarga no permitido", exception.message)
    }

    @Test
    fun `safeFormatId rejects leading dash`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            DownloadInputPolicy.safeFormatId("-best")
        }
        assertEquals("Formato de descarga no permitido", exception.message)
    }

    @Test
    fun `safeFormatId rejects double dash`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            DownloadInputPolicy.safeFormatId("best--format")
        }
        assertEquals("Formato de descarga no permitido", exception.message)
    }

    @Test
    fun `safeFormatId rejects control characters`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            DownloadInputPolicy.safeFormatId("best\u0000format")
        }
        assertEquals("Formato de descarga no permitido", exception.message)
    }

    @Test
    fun `safeFormatId rejects whitespace`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            DownloadInputPolicy.safeFormatId("best format")
        }
        assertEquals("Formato de descarga no permitido", exception.message)
    }

    @Test
    fun `safeFormatId rejects long string`() {
        val longFormat = "a".repeat(161)
        val exception = assertThrows(IllegalArgumentException::class.java) {
            DownloadInputPolicy.safeFormatId(longFormat)
        }
        assertEquals("Formato de descarga no permitido", exception.message)
    }

    @Test
    fun `safeFormatId rejects invalid characters`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            DownloadInputPolicy.safeFormatId("best@format")
        }
        assertEquals("Formato de descarga no permitido", exception.message)
    }

    @Test
    fun `safeFormatId accepts boundary length`() {
        val maxFormat = "a".repeat(160)
        val result = DownloadInputPolicy.safeFormatId(maxFormat)
        assertEquals(maxFormat, result)
    }
}