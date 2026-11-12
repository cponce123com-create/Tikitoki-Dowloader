package com.tikitoki.downloader.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TikTokLinkParserTest {
    @Test fun recognizesProfile() {
        val result = TikTokLinkParser.parse("https://www.tiktok.com/@demo.user")
        assertTrue(result is LinkParseResult.Success)
        val link = (result as LinkParseResult.Success).link
        assertEquals(TikTokLinkType.PROFILE, link.type)
        assertEquals("demo.user", link.username)
    }

    @Test fun recognizesVideo() {
        val result = TikTokLinkParser.parse("https://www.tiktok.com/@demo/video/123456789?lang=es")
        assertTrue(result is LinkParseResult.Success)
        assertEquals("123456789", (result as LinkParseResult.Success).link.videoId)
        assertEquals(TikTokLinkType.VIDEO, result.link.type)
    }

    @Test fun recognizesShortLink() {
        val result = TikTokLinkParser.parse("https://vt.tiktok.com/ZExample/")
        assertTrue(result is LinkParseResult.Success)
        assertEquals(TikTokLinkType.SHORT_LINK, (result as LinkParseResult.Success).link.type)
    }

    @Test fun rejectsHostSpoofing() {
        assertTrue(TikTokLinkParser.parse("https://tiktok.com.evil.example/@demo") is LinkParseResult.Error)
    }

    @Test fun rejectsInsecureHttp() {
        assertTrue(TikTokLinkParser.parse("http://www.tiktok.com/@demo") is LinkParseResult.Error)
    }

    @Test fun acceptsUrlWithoutScheme() {
        val result = TikTokLinkParser.parse("www.tiktok.com/@demo")
        assertTrue(result is LinkParseResult.Success)
    }
}
