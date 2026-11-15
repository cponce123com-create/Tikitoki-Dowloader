package com.tikitoki.downloader.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticReportTest {
    @Test fun categorizesWithoutReturningSensitiveDetails() {
        assertEquals(ErrorCategory.RATE_LIMIT, DiagnosticReport.errorCategory("HTTP 429 https://private.example/video"))
        assertEquals(ErrorCategory.NETWORK, DiagnosticReport.errorCategory("connection failed /storage/private/file"))
        assertEquals(ErrorCategory.TIMEOUT, DiagnosticReport.errorCategory("timeout for https://example.com/id"))
        assertEquals(ErrorCategory.OTHER, DiagnosticReport.errorCategory("https://example.com/secret"))
    }
}
