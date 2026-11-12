package com.tikitoki.downloader.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileDiffTest {
    @Test fun firstScanIsBaseline() {
        val result = ProfileDiff.compare(emptySet(), setOf("1", "2"))
        assertEquals(emptySet<String>(), result.newIds)
        assertEquals(setOf("1", "2"), result.knownIds)
    }

    @Test fun partialScanPreservesOldIds() {
        val result = ProfileDiff.compare(setOf("1", "2"), setOf("2", "3"))
        assertEquals(setOf("3"), result.newIds)
        assertEquals(setOf("1", "2", "3"), result.knownIds)
    }

    @Test fun emptyScanDoesNotEraseBaseline() {
        val result = ProfileDiff.compare(setOf("1"), emptySet())
        assertEquals(emptySet<String>(), result.newIds)
        assertEquals(setOf("1"), result.knownIds)
    }
}
