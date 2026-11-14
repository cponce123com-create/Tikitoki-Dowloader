package com.tikitoki.downloader.domain

import org.junit.Assert.*
import org.junit.Test

class CanonicalProfileTest {
    @Test fun canonicalizesOnlyProfiles() {
        assertEquals("https://www.tiktok.com/@Carlos", TikTokLinkParser.canonicalProfile("https://tiktok.com/@Carlos?lang=es#top"))
        assertNull(TikTokLinkParser.canonicalProfile("https://tiktok.com/@Carlos/video/12345"))
        assertNull(TikTokLinkParser.canonicalProfile("https://evil.example/@Carlos"))
    }

    @Test fun recognizesPreviouslyStoredVariants() {
        assertTrue(TikTokLinkParser.sameProfile("https://tiktok.com/@carlos?lang=es", "https://www.tiktok.com/@Carlos"))
        assertFalse(TikTokLinkParser.sameProfile("https://tiktok.com/@uno", "https://tiktok.com/@otro"))
    }
}
