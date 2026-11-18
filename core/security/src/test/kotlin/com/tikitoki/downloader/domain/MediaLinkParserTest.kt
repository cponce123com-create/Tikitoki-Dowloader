package com.tikitoki.downloader.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [MediaLinkParser].
 * Validates URL parsing, platform detection, and normalization.
 */
class MediaLinkParserTest {

    @Test
    fun `parses TikTok video URL`() {
        val result = MediaLinkParser.parse("https://www.tiktok.com/@user/video/1234567890")
        assertNotNull(result)
        assertEquals(MediaPlatform.TIKTOK, result.platform)
        assertEquals(false, result.collection)
    }

    @Test
    fun `parses TikTok profile URL`() {
        val result = MediaLinkParser.parse("https://www.tiktok.com/@user")
        assertNotNull(result)
        assertEquals(MediaPlatform.TIKTOK, result.platform)
        assertEquals(true, result.collection)
    }

    @Test
    fun `parses TikTok share URL`() {
        val result = MediaLinkParser.parse("https://vm.tiktok.com/abc123/")
        assertNotNull(result)
        assertEquals(MediaPlatform.TIKTOK, result.platform)
    }

    @Test
    fun `parses YouTube video URL`() {
        val result = MediaLinkParser.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertNotNull(result)
        assertEquals(MediaPlatform.YOUTUBE, result.platform)
        assertEquals(false, result.collection)
    }

    @Test
    fun `parses YouTube short URL`() {
        val result = MediaLinkParser.parse("https://youtu.be/dQw4w9WgXcQ")
        assertNotNull(result)
        assertEquals(MediaPlatform.YOUTUBE, result.platform)
    }

    @Test
    fun `parses YouTube channel URL`() {
        val result = MediaLinkParser.parse("https://www.youtube.com/@channel")
        assertNotNull(result)
        assertEquals(MediaPlatform.YOUTUBE, result.platform)
        assertEquals(true, result.collection)
    }

    @Test
    fun `parses YouTube playlist URL`() {
        val result = MediaLinkParser.parse("https://www.youtube.com/playlist")
        assertNotNull(result)
        assertEquals(MediaPlatform.YOUTUBE, result.platform)
        assertEquals(true, result.collection)
    }

    @Test
    fun `parses Facebook URL`() {
        val result = MediaLinkParser.parse("https://www.facebook.com/user/videos/123")
        assertNotNull(result)
        assertEquals(MediaPlatform.FACEBOOK, result.platform)
        assertEquals(true, result.collection)
    }

    @Test
    fun `parses Instagram URL`() {
        val result = MediaLinkParser.parse("https://www.instagram.com/user/")
        assertNotNull(result)
        assertEquals(MediaPlatform.INSTAGRAM, result.platform)
        assertEquals(true, result.collection)
    }

    @Test
    fun `parses Direct Media URLs`() {
        val vimeo = MediaLinkParser.parse("https://vimeo.com/123456")
        assertNotNull(vimeo)
        assertEquals(MediaPlatform.DIRECT_MEDIA, vimeo.platform)

        val dailymotion = MediaLinkParser.parse("https://dailymotion.com/video/x123")
        assertNotNull(dailymotion)
        assertEquals(MediaPlatform.DIRECT_MEDIA, dailymotion.platform)

        val soundcloud = MediaLinkParser.parse("https://soundcloud.com/user/track")
        assertNotNull(soundcloud)
        assertEquals(MediaPlatform.DIRECT_MEDIA, soundcloud.platform)
    }

    @Test
    fun `rejects HTTP scheme`() {
        val result = MediaLinkParser.parse("http://www.tiktok.com/@user/video/123")
        assertNull(result)
    }

    @Test
    fun `rejects user info in URL`() {
        val result = MediaLinkParser.parse("https://user:pass@tiktok.com/@user/video/123")
        assertNull(result)
    }

    @Test
    fun `rejects explicit port`() {
        val result = MediaLinkParser.parse("https://www.tiktok.com:443/@user/video/123")
        assertNull(result)
    }

    @Test
    fun `rejects fragment in URL`() {
        val result = MediaLinkParser.parse("https://www.tiktok.com/@user/video/123#fragment")
        assertNull(result)
    }

    @Test
    fun `rejects unknown platform`() {
        val result = MediaLinkParser.parse("https://www.unknown-platform.com/video/123")
        assertNull(result)
    }

    @Test
    fun `rejects empty string`() {
        val result = MediaLinkParser.parse("")
        assertNull(result)
    }

    @Test
    fun `rejects whitespace`() {
        val result = MediaLinkParser.parse("  https://www.tiktok.com/@user/video/123  ")
        assertNull(result)
    }

    @Test
    fun `rejects control characters`() {
        val result = MediaLinkParser.parse("https://www.tiktok.com/@user/video/123\u0000")
        assertNull(result)
    }

    @Test
    fun `rejects bidi override characters`() {
        val result = MediaLinkParser.parse("https://www.tiktok.com/@user/video/‮123")
        assertNull(result)
    }

    @Test
    fun `normalizes URL with tracking params`() {
        val input = "https://www.tiktok.com/@user/video/123?utm_source=share&utm_medium=copy"
        val result = MediaLinkParser.parse(input)
        assertNotNull(result)
        // Should strip tracking parameters and normalize
        assertTrue(!result.url.contains("utm_source"))
        assertTrue(!result.url.contains("utm_medium"))
    }

    @Test
    fun `preserves valid query parameters`() {
        // YouTube video IDs with extra params should be preserved if valid
        val input = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PL123"
        val result = MediaLinkParser.parse(input)
        assertNotNull(result)
        assertTrue(result.url.contains("v=dQw4w9WgXcQ"))
    }

    @Test
    fun `normalizes host to lowercase`() {
        val input = "https://WWW.TIKTOK.COM/@user/video/123"
        val result = MediaLinkParser.parse(input)
        assertNotNull(result)
        assertTrue(result.url.startsWith("https://www.tiktok.com"))
    }

    @Test
    fun `handles subdomains correctly`() {
        val result = MediaLinkParser.parse("https://vm.tiktok.com/abc123/")
        assertNotNull(result)
        assertEquals(MediaPlatform.TIKTOK, result.platform)

        val result2 = MediaLinkParser.parse("https://youtube-nocookie.com/embed/123")
        assertNotNull(result2)
        assertEquals(MediaPlatform.YOUTUBE, result2.platform)
    }
}