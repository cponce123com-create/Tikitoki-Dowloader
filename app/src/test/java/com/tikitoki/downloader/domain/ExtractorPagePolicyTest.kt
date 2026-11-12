package com.tikitoki.downloader.domain

import org.junit.Assert.*
import org.junit.Test

class ExtractorPagePolicyTest {
    @Test fun rejectsCrossPlatformAndUntrustedExtractorUrls() {
        val source = "https://www.youtube.com/watch?v=abc"
        listOf(
            "https://evil.example/watch?v=abc",
            "http://www.youtube.com/watch?v=abc",
            "https://youtube.com.evil.example/x",
            "https://www.facebook.com/watch/?v=abc",
            "https://www.youtube.com/x\n--exec=evil",
            "file:///etc/passwd",
            "https://user@www.youtube.com/x"
        ).forEach {
            assertNull(it, DownloadInputPolicy.extractorPage(MediaPlatform.YOUTUBE, "abc", it, source, false))
        }
    }

    @Test fun synthesizesOnlySafeYoutubeIds() {
        val source = "https://www.youtube.com/playlist?list=PL123"
        assertEquals(
            "https://www.youtube.com/watch?v=Ab_12-Z",
            DownloadInputPolicy.extractorPage(MediaPlatform.YOUTUBE, "Ab_12-Z", null, source, true)
        )
        listOf("", "../evil", "abc&--exec=x", "a b", "abc\n", "a".repeat(129))
            .forEach { assertNull(DownloadInputPolicy.extractorPage(MediaPlatform.YOUTUBE, it, null, source, true)) }
    }

    @Test fun retainsSamePlatformCanonicalUrls() {
        assertEquals(
            "https://www.instagram.com/reel/ABC/",
            DownloadInputPolicy.extractorPage(
                MediaPlatform.INSTAGRAM, "ABC",
                "https://WWW.INSTAGRAM.COM/reel/ABC/",
                "https://www.instagram.com/profile/", true
            )
        )
        assertEquals(
            "https://www.facebook.com/watch/?v=123",
            DownloadInputPolicy.extractorPage(
                MediaPlatform.FACEBOOK, "123", null,
                "https://www.facebook.com/watch/?v=123", false
            )
        )
        assertNull(
            DownloadInputPolicy.extractorPage(
                MediaPlatform.FACEBOOK, "123", null,
                "https://www.facebook.com/profile/videos", true
            )
        )
    }
}
