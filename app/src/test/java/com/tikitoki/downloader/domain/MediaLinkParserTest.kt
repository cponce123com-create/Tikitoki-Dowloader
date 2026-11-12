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

    @Test fun coversAllCollectionBranchesAndCanonicalization() {
        assertTrue(MediaLinkParser.parse("https://www.facebook.com/creator/videos")!!.collection)
        assertFalse(MediaLinkParser.parse("https://www.facebook.com/watch/?v=123")!!.collection)
        assertTrue(MediaLinkParser.parse("https://www.instagram.com/creator/")!!.collection)
        assertFalse(MediaLinkParser.parse("https://www.instagram.com/reel/abc/")!!.collection)
        assertTrue(MediaLinkParser.parse("https://www.youtube.com/channel/UC123")!!.collection)
        assertTrue(MediaLinkParser.parse("https://www.youtube.com/c/creator")!!.collection)
        assertTrue(MediaLinkParser.parse("https://www.youtube.com/user/creator")!!.collection)
        assertTrue(MediaLinkParser.parse("https://www.youtube.com/playlist?list=PL123")!!.collection)
        assertFalse(MediaLinkParser.parse("https://www.tiktok.com/@creator/video/123")!!.collection)
        assertNull(MediaLinkParser.parse(""))
        assertNull(MediaLinkParser.parse(" https://youtube.com/watch?v=1"))
        assertNull(MediaLinkParser.parse("https://youtube.com/watch?v=1\\u202e"))
        assertNull(MediaLinkParser.parse("https://youtube.com/path#fragment"))
        assertNull(MediaLinkParser.parse("https://youtube.com./watch"))
        assertNull(MediaLinkParser.parse("https://youtube.com\\\\@evil.test/watch"))
        assertNull(MediaLinkParser.parse("https://youtube.com/" + "a".repeat(4100)))
        assertNull(MediaLinkParser.parse("https://youtube.com:123/watch"))
        assertNull(MediaLinkParser.parse("https://youtube.com/space here"))
        assertEquals("https://www.youtube.com/watch?v=hello%2Bworld",
            MediaLinkParser.parse("https://WWW.YOUTUBE.COM/watch?v=hello%2Bworld")!!.url)
    }

    @Test fun coversRemainingUrlParserPaths() {
        val accepted = listOf(
            "https://tiktok.com/@user", "https://vt.tiktok.com/a",
            "https://youtu.be/abc", "https://youtube-nocookie.com/embed/abc",
            "https://fb.watch/abc", "https://m.facebook.com/user/videos/123",
            "https://instagram.com/user", "https://instagram.com/p/abc",
            "https://www.youtube.com/", "https://www.youtube.com/watch",
            "https://www.youtube.com/@user", "https://www.youtube.com/c/user",
            "https://www.youtube.com/user/user", "https://www.youtube.com/channel/id",
            "https://www.youtube.com/playlist", "https://www.facebook.com/user/videos",
            "https://www.facebook.com/user/videos/123",
            "https://www.instagram.com/user/", "https://www.instagram.com/reels/abc"
        )
        accepted.forEach { assertNotNull(it, MediaLinkParser.parse(it)) }
        val rejected = listOf(
            "https://", "https:/youtube.com/x", "https:///youtube.com/x",
            "https://youtube.com:443/x", "https://name@youtube.com/x",
            "https://youtube.com./x", "https://youtube.com/#x",
            "https://youtube.com/x\\\\y", "https://youtube.com/x?x=hello world",
            "https://youtube.com/x?x=%ZZ", "https://[::1]/x",
            "https://youtube.com/x\u202d", "https://youtube.com/x\u2067",
            "https://youtube.com/x\u2068", "https://youtube.com/x\u2069",
            "https://youtube.com/x\u202a", "https://youtube.com/x\u202b",
            "https://youtube.com/x\u202c", "https://youtube.com/x\u2066",
            "https://youtube.com/x\u0001", "https://youtube.com/x\t",
            "https://youtube.com/x\n", "https://youtube.com/x\r",
            "https://example.com/x", "https://fakeinstagram.com/x",
            "https://instagram.com.evil.test/x", "https://facebook.com.evil.test/x"
        )
        rejected.forEach { assertNull(it, MediaLinkParser.parse(it)) }
    }

    @Test fun distinguishesCollections() {
        assertTrue(MediaLinkParser.parse("https://www.youtube.com/@creator/videos")!!.collection)
        assertTrue(MediaLinkParser.parse("https://www.tiktok.com/@creator")!!.collection)
        assertFalse(MediaLinkParser.parse("https://www.youtube.com/watch?v=123")!!.collection)
    }
}
