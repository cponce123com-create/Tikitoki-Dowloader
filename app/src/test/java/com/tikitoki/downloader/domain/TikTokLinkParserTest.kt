package com.tikitoki.downloader.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test fun recognizesPhotoPostWithoutTreatingItAsAProfile() {
        val url = "https://www.tiktok.com/@demo/photo/123456789?lang=es"
        val result = TikTokLinkParser.parse(url) as LinkParseResult.Success
        assertEquals(TikTokLinkType.VIDEO, result.link.type)
        assertEquals("123456789", result.link.videoId)
        assertEquals("demo", result.link.username)
        assertNull(TikTokLinkParser.canonicalProfile(url))
    }

    @Test fun recognizesShortLink() {
        val result = TikTokLinkParser.parse("https://vt.tiktok.com/ZExample/")
        assertTrue(result is LinkParseResult.Success)
        assertEquals(TikTokLinkType.SHORT_LINK, (result as LinkParseResult.Success).link.type)
    }

    @Test fun recognizesVmShortLink() {
        val result = TikTokLinkParser.parse("https://vm.tiktok.com/ZExample/")
        assertTrue(result is LinkParseResult.Success)
        assertEquals(TikTokLinkType.SHORT_LINK, (result as LinkParseResult.Success).link.type)
    }

    @Test fun recognizesTikTokRedirectPath() {
        val result = TikTokLinkParser.parse("https://www.tiktok.com/t/ZExample/")
        assertTrue(result is LinkParseResult.Success)
        assertEquals(TikTokLinkType.SHORT_LINK, (result as LinkParseResult.Success).link.type)
    }

    @Test fun recognizesDirectVideoPath() {
        val result = TikTokLinkParser.parse("https://www.tiktok.com/video/123456789")
        assertTrue(result is LinkParseResult.Success)
        val link = (result as LinkParseResult.Success).link
        assertEquals(TikTokLinkType.VIDEO, link.type)
        assertEquals("123456789", link.videoId)
        assertNull(link.username)
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

    @Test fun rejectsEmptyOrWhitespaceInput() {
        assertTrue(TikTokLinkParser.parse("") is LinkParseResult.Error)
        assertTrue(TikTokLinkParser.parse("   ") is LinkParseResult.Error)
    }

    @Test fun rejectsMalformedUri() {
        assertTrue(TikTokLinkParser.parse("https://[") is LinkParseResult.Error)
    }

    @Test fun rejectsUriWithoutRecognizableHost() {
        assertTrue(TikTokLinkParser.parse("https://?query=value") is LinkParseResult.Error)
    }

    @Test fun rejectsCredentialsAndExplicitPort() {
        assertTrue(
            TikTokLinkParser.parse("https://user:pass@www.tiktok.com/@demo") is LinkParseResult.Error,
        )
        assertTrue(TikTokLinkParser.parse("https://www.tiktok.com:443/@demo") is LinkParseResult.Error)
    }

    @Test fun rejectsUnsupportedTikTokPath() {
        assertTrue(TikTokLinkParser.parse("https://www.tiktok.com/@demo/favorites") is LinkParseResult.Error)
    }

    @Test fun canonicalizesOnlyProfiles() {
        assertEquals(
            "https://www.tiktok.com/@Demo.User",
            TikTokLinkParser.canonicalProfile("https://www.tiktok.com/@Demo.User?lang=es"),
        )
        assertNull(TikTokLinkParser.canonicalProfile("https://www.tiktok.com/@Demo/video/123"))
        assertNull(TikTokLinkParser.canonicalProfile("not a TikTok link"))
    }

    @Test fun comparesCanonicalProfilesIgnoringCase() {
        assertTrue(TikTokLinkParser.sameProfile("tiktok.com/@Demo", "www.tiktok.com/@demo"))
        assertTrue(!TikTokLinkParser.sameProfile("tiktok.com/@Demo", "tiktok.com/video/123"))
        assertTrue(!TikTokLinkParser.sameProfile("invalid", "tiktok.com/@demo"))
    }

    @Test fun acceptsCaseInsensitiveSchemeAndTrailingHostDot() {
        val result = TikTokLinkParser.parse("HTTPS://WWW.TIKTOK.COM./@demo")
        assertTrue(result is LinkParseResult.Success)
    }

    @Test fun recognizesSharedProfileWithTrackingParameters() {
        val url = "https://www.tiktok.com/@usuario123?_r=1&_t=ZS-example"
        val media = MediaLinkParser.parse(url)
        assertEquals(MediaPlatform.TIKTOK, media?.platform)
        assertEquals(true, media?.collection)
        val result = TikTokLinkParser.parse(url) as LinkParseResult.Success
        assertEquals(TikTokLinkType.PROFILE, result.link.type)
        assertEquals("usuario123", result.link.username)
    }
}
