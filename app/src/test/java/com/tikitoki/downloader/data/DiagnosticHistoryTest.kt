package com.tikitoki.downloader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DiagnosticHistoryTest {
    @Test fun keepsOnlyTwentySafeCategories() {
        val input = (1L..25L).map { DiagnosticEntry(it, ErrorCategory.NETWORK) }
        val encoded = DiagnosticHistory.encode(input)
        assertEquals(input.take(20), DiagnosticHistory.decode(encoded))
        assertFalse(encoded.contains("https"))
        assertFalse(encoded.contains("/storage/"))
    }
    @Test fun rejectsMalformedOrUnknownEntries() {
        assertEquals(listOf(DiagnosticEntry(1, ErrorCategory.OTHER)), DiagnosticHistory.decode("1|OTHER\n0|NETWORK\n2|https://example.com\nwrong"))
    }
}
