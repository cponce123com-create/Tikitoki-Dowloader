package com.tikitoki.downloader.domain

import org.junit.Assert.*
import org.junit.Test

class IncomingSecurityTest {
    @Test fun acceptsPublicMediaLinks() {
        assertNotNull(IncomingLinkValidator.validate("https://www.tiktok.com/@creator/video/123456789"))
        assertNotNull(IncomingLinkValidator.validate("https://www.youtube.com/watch?v=abc123"))
    }
    @Test fun extractsTheFirstHttpsLinkFromSharedText() {
        assertEquals("https://www.youtube.com/watch?v=ab--cdefghi",
            IncomingLinkValidator.extractFirstHttps("Mira esto https://www.youtube.com/watch?v=ab--cdefghi por favor")?.url)
        assertNull(IncomingLinkValidator.extractFirstHttps("https://evil.test/x https://youtube.com/watch?v=abc"))
        assertNull(IncomingLinkValidator.extractFirstHttps("https://youtube.com/watch?v=abc#fragment"))
        assertNull(IncomingLinkValidator.extractFirstHttps("https://youtube.com/watch?v=abc\u202e"))
        assertNull(IncomingLinkValidator.extractFirstHttps(null))
        assertNull(IncomingLinkValidator.extractFirstHttps(""))
        assertNull(IncomingLinkValidator.extractFirstHttps("sin enlaces"))
        assertNull(IncomingLinkValidator.extractFirstHttps("x".repeat(4097)))
        assertNull(IncomingLinkValidator.extractFirstHttps("texto\nhttps://youtube.com/watch?v=abc"))
        assertNull(IncomingLinkValidator.extractFirstHttps("https://usuario@youtube.com/watch?v=abc"))
        assertNull(IncomingLinkValidator.extractFirstHttps("https://youtube.com:443/watch?v=abc"))
    }
    @Test fun acceptsHyphensInsideYoutubeIdentifiers() {
        assertNotNull(IncomingLinkValidator.validate("https://youtube.com/watch?v=ab--cdefghi"))
    }
    @Test fun rejectsMaliciousPayloads() {
        for (value in listOf(
            "https://www.tiktok.com/@creator/video/123\n--exec=evil",
            "https://youtube.com/watch?v=1 --netrc-cmd=evil",
            "https://youtube.com/watch?v=1 --config-location=/tmp/x",
            "https://youtube.com.evil.example/watch?v=1",
            "http://youtube.com/watch?v=1",
            "https://youtube.com/watch?v=1\u0000",
            "https://youtube.com/" + "a".repeat(5000)
        )) assertNull(value, IncomingLinkValidator.validate(value))
    }
    @Test fun metadataIsBoundedAndRemovesControls() {
        assertEquals("hola mundo", MetadataSanitizer.title("hola\n mundo"))
        assertTrue(MetadataSanitizer.title("a".repeat(1000)).length <= 180)
        assertEquals("Video sin título", MetadataSanitizer.title("\u0000"))
    }
}

