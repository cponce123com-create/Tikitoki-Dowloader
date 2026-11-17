package com.tikitoki.downloader.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.UnknownHostException

class AnalysisFailureClassifierTest {
    @Test
    fun classifiesNestedExtractorErrorsWithoutReturningTheirContents() {
        val cases =
            listOf(
                IllegalStateException("wrapper", RuntimeException("HTTP Error 429")) to AnalysisFailureKind.RATE_LIMIT,
                IllegalStateException("wrapper", RuntimeException("Unable to extract webpage video data")) to
                    AnalysisFailureKind.EXTRACTOR_RESPONSE,
                IllegalStateException("wrapper", RuntimeException("Unexpected response from webpage request")) to
                    AnalysisFailureKind.EXTRACTOR_RESPONSE,
                IllegalStateException("wrapper", RuntimeException("Unable to extract secondary user ID")) to
                    AnalysisFailureKind.EXTRACTOR_RESPONSE,
                IllegalStateException("wrapper", RuntimeException("HTTP Error 403")) to AnalysisFailureKind.ACCESS,
                IllegalStateException("wrapper", UnknownHostException("host")) to AnalysisFailureKind.NETWORK,
                IllegalStateException("unexpected") to AnalysisFailureKind.UNKNOWN,
            )
        cases.forEach { (error, expected) -> assertEquals(expected, AnalysisFailureClassifier.classify(error)) }
    }
}
