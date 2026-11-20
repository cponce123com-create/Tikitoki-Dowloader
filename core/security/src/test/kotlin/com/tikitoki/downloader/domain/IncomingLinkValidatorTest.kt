package com.tikitoki.downloader.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [IncomingLinkValidator].
 * Validates URL parsing, platform detection, and input sanitization.
 */
class IncomingLinkValidatorTest {

    @Test
    fun `validates TikTok video URL`() {
        val result = IncomingLinkValidator.validate(TestUtils.TIKTOK_URL_VIDEO)
        assertNotNull(result)
        assertEquals(MediaPlatform.TIKTOK, result.platform)
        assertEquals(TestUtils.TIKTOK_URL_VIDEO, result.url)
    }

    @Test
    fun `validates TikTok profile URL`() {
        val result = IncomingLinkValidator.validate(TestUtils.TIKTOK_URL_PROFILE)
        assertNotNull(result)
        assertEquals(MediaPlatform.TIKTOK, result.platform)
        assertEquals(TestUtils.TIKTOK_URL_PROFILE, result.url)
    }

    @Test
    fun `validates TikTok share URL`() {
        val result = IncomingLinkValidator.validate(TestUtils.TIKTOK_URL_SHARE)
        assertNotNull(result)
        assertEquals(MediaPlatform.TIKTOK, result.platform)
    }

    @Test
    fun `validates YouTube video URL`() {
        val result = IncomingLinkValidator.validate(TestUtils.YOUTUBE_URL_VIDEO)
        assertNotNull(result)
        assertEquals(MediaPlatform.YOUTUBE, result.platform)
        assertEquals(TestUtils.YOUTUBE_URL_VIDEO, result.url)
    }

    @Test
    fun `validates YouTube short URL`() {
        val result = IncomingLinkValidator.validate(TestUtils.YOUTUBE_URL_SHORT)
        assertNotNull(result)
        assertEquals(MediaPlatform.YOUTUBE, result.platform)
    }

    @Test
    fun `rejects empty URL`() {
        val result = IncomingLinkValidator.validate(TestUtils.INVALID_URL_EMPTY)
        assertNull(result)
    }

    @Test
    fun `rejects non-HTTP URL`() {
        val result = IncomingLinkValidator.validate(TestUtils.INVALID_URL_NOT_HTTP)
        assertNull(result)
    }

    @Test
    fun `rejects FTP URL`() {
        val result = IncomingLinkValidator.validate(TestUtils.INVALID_URL_FTP)
        assertNull(result)
    }

    @Test
    fun `extracts first HTTPS from text`() {
        val text = "Check this out ${TestUtils.TIKTOK_URL_VIDEO} and also ${TestUtils.YOUTUBE_URL_VIDEO}"
        val result = IncomingLinkValidator.extractFirstHttps(text)
        assertNotNull(result)
        assertEquals(TestUtils.TIKTOK_URL_VIDEO, result.url)
    }

    @Test
    fun `extractFirstHttps returns null for no URLs`() {
        val result = IncomingLinkValidator.extractFirstHttps("No URLs here")
        assertNull(result)
    }

    @Test
    fun `sanitizes URL by removing tracking params`() {
        val urlWithTracking = "${TestUtils.TIKTOK_URL_VIDEO}?utm_source=share&utm_medium=copy_link"
        val result = IncomingLinkValidator.validate(urlWithTracking)
        assertNotNull(result)
        // Should strip tracking parameters
        assertTrue(!result.url.contains("utm_source"))
    }
}
