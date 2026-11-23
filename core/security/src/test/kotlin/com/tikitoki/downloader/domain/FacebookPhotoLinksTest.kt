package com.tikitoki.downloader.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FacebookPhotoLinksTest {
    @Test
    fun facebookProfilesAndPhotoPagesUseGalleryEngine() {
        listOf(
            "https://www.facebook.com/share/1AbNn9bpny/",
            "https://www.facebook.com/example.user",
            "https://www.facebook.com/example.user/photos",
            "https://www.facebook.com/profile.php?id=1234",
            "https://www.facebook.com/photo/?fbid=9876",
            "https://www.facebook.com/share/p/ABC/",
        ).forEach { assertTrue(it, FacebookPhotoLinks.isPhotoCollection(it)) }
    }

    @Test
    fun videosAndReelsUseExistingVideoAnalyzer() {
        listOf(
            "https://www.facebook.com/watch/?v=123",
            "https://www.facebook.com/example/videos/123",
            "https://www.facebook.com/reel/123",
            "https://www.facebook.com/share/v/ABC/",
            "https://www.facebook.com/share/r/ABC/",
            "https://fb.watch/ABC123/",
            "https://www.youtube.com/watch?v=123",
            "https://facebook.com.evil.example/photo",
        ).forEach { assertFalse(it, FacebookPhotoLinks.isPhotoCollection(it)) }
    }
}
