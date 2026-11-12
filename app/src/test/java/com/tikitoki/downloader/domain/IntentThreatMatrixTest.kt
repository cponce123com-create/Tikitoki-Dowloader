package com.tikitoki.downloader.domain

import org.junit.Assert.*
import org.junit.Test

class IntentThreatMatrixTest {
    @Test fun rejectsAtLeastThirtyHostileLinks() {
        val base = "https://www.youtube.com/watch?v=abc"
        val hostile = listOf(
            "http://youtube.com/watch?v=abc", "ftp://youtube.com/x", "file:///tmp/a",
            "javascript:alert(1)", "content://youtube.com/a", "https://youtube.com.evil.test/x",
            "https://notyoutube.com/x", "https://youtube.com@evil.test/x",
            "https://user:pass@youtube.com/x", "https://youtube.com:444/x",
            "https://youtube.com./x", "https://youtube.com/#fragment",
            "https://youtube.com/x\n--exec=evil", "https://youtube.com/x\r",
            "https://youtube.com/x\t", "https://youtube.com/x\u0000",
            "https://youtube.com/x\u202e", "https://youtube.com/x\u2066",
            "https://youtube.com/x --exec=evil", "https://youtube.com/x --netrc-cmd=evil",
            "https://youtube.com/x --config-location=/tmp/a",
            "https://youtube.com/x --batch-file=/tmp/a",
            "https://youtube.com/x --load-info-json=/tmp/a",
            "https://youtube.com/x --output=/tmp/a",
            "https://youtube.com/x -o /tmp/a", "https://youtube.com/x -P /tmp/a",
            "https://youtube.com/x --cookies=/tmp/a",
            "https://youtube.com/x --plugin-dirs=/tmp/a",
            "https://youtube.com/x --arbitrary-flag=1",
            "https://youtube.com/x" + "a".repeat(4100),
            " https://youtube.com/x", "https://youtube.com/x ",
            "https://youtube.com/x\\evil", "https://youtube.com/x\u007f"
        )
        assertTrue(hostile.size >= 30)
        hostile.forEach { assertNull("Unexpectedly accepted: $it", IncomingLinkValidator.validate(it)) }
    }

    @Test fun exercisesValidationBoundaryBranches() {
        assertNull(IncomingLinkValidator.validate(null))
        assertNull(IncomingLinkValidator.validate(""))
        assertNull(IncomingLinkValidator.validate("https://youtube.com/--exec"))
        assertNull(IncomingLinkValidator.validate("https://youtube.com/-o"))
        assertNull(IncomingLinkValidator.validate("https://youtube.com/-P"))
        assertNull(IncomingLinkValidator.validate("https://youtube.com/--load-info-json"))
        assertNull(IncomingLinkValidator.validate("https://youtube.com/--batch-file"))
        assertNull(IncomingLinkValidator.validate("https://youtube.com/\u202a"))
        assertNull(IncomingLinkValidator.validate("https://youtube.com/\u202b"))
        assertNull(IncomingLinkValidator.validate("https://youtube.com/\u202c"))
        assertNull(IncomingLinkValidator.validate("https://youtube.com/\u2069"))
        assertNotNull(IncomingLinkValidator.validate("https://www.facebook.com/watch/?v=123"))
        assertNotNull(IncomingLinkValidator.validate("https://www.instagram.com/reel/abc/"))
        assertNotNull(IncomingLinkValidator.validate("https://youtu.be/abc"))
        assertNull(IncomingLinkValidator.validate("https://example.org/watch?v=1"))
    }

    @Test fun canonicalizesAndKeepsSupportedUrls() {
        val link = IncomingLinkValidator.validate("https://WWW.YOUTUBE.COM/watch?v=abc%2Bdef")
        assertNotNull(link)
        assertEquals(MediaPlatform.YOUTUBE, link!!.platform)
        assertTrue(link.url.startsWith("https://www.youtube.com/"))
        assertNotNull(IncomingLinkValidator.validate("https://vm.tiktok.com/ZM123/"))
    }
}
