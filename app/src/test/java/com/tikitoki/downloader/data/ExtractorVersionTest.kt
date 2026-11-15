package com.tikitoki.downloader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExtractorVersionTest {
    @Test fun parsesVersionWithoutAcceptingExtraOrSensitiveOutput() {
        assertEquals("2026.01.02", ExtractorVersion.parse("2026.01.02\n"))
        assertEquals("2026.01.02-nightly.1", ExtractorVersion.parse("2026.01.02-nightly.1"))
        assertNull(ExtractorVersion.parse("https://example.com/secret"))
        assertNull(ExtractorVersion.parse("ERROR"))
        assertNull(ExtractorVersion.parse("2026.01.02\nhttps://example.com/secret"))
    }
}
