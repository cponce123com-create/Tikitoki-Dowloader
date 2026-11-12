package com.tikitoki.downloader.domain

import org.junit.Assert.*
import org.junit.Test

class MediaLinkParserTest {
    @Test fun supportsFourServices() {
        assertEquals(MediaPlatform.YOUTUBE, MediaLinkParser.parse("https://youtu.be/abc123")?.platform)
        assertEquals(MediaPlatform.YOUTUBE, MediaLinkParser.parse("https://www.youtube.com/@creator/videos")?.platform)
        assertEquals(MediaPlatform.FACEBOOK, MediaLinkParser.parse("https://www.facebook.com/watch/?v=123")?.platform)
        assertEquals(MediaPlatform.INSTAGRAM, MediaLinkParser.parse("https://www.instagram.com/reel/ABC/")?.platform)
        assertEquals(MediaPlatform.TIKTOK, MediaLinkParser.parse("https://www.tiktok.com/@creator/video/123456789")?.platform)
    }

    @Test fun rejectsUnsafeHostsAndSchemes() {
        assertNull(MediaLinkParser.parse("http://youtube.com/watch?v=123"))
        assertNull(MediaLinkParser.parse("https://youtube.com.evil.example/watch?v=123"))
        assertNull(MediaLinkParser.parse("https://user:pass@youtube.com/watch?v=123"))
        assertNull(MediaLinkParser.parse("https://youtube.com:8080/watch?v=123"))
        assertNull(MediaLinkParser.parse("file:///etc/passwd"))
    }

    @Test fun distinguishesCollections() {
        assertTrue(MediaLinkParser.parse("https://www.youtube.com/@creator/videos")!!.collection)
        assertTrue(MediaLinkParser.parse("https://www.tiktok.com/@creator")!!.collection)
        assertFalse(MediaLinkParser.parse("https://www.youtube.com/watch?v=123")!!.collection)
    }
}
