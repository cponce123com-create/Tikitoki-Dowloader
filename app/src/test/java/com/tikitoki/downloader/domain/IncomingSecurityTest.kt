package com.tikitoki.downloader.domain

import org.junit.Assert.*
import org.junit.Test

class IncomingSecurityTest {
    @Test fun acceptsPublicMediaLinks() {
        assertNotNull(IncomingLinkValidator.validate("https://www.tiktok.com/@creator/video/123456789"))
        assertNotNull(IncomingLinkValidator.validate("https://www.youtube.com/watch?v=abc123"))
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
