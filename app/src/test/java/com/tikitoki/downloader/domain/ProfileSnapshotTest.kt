package com.tikitoki.downloader.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileSnapshotTest {
    @Test fun partialResultsDoNotCauseFalseMissingNotices() {
        val old = setOf("one", "two")
        val result = ProfileSnapshot.compare(old, emptySet(), setOf("one"), complete = false)
        assertEquals(old, result.snapshot)
        assertEquals(emptySet<String>(), result.possiblyUnavailable)
    }
    @Test fun missingNeedsTwoCompleteScansAndRecoveryCancelsIt() {
        val first = ProfileSnapshot.compare(setOf("one", "two"), emptySet(), setOf("one"), true)
        assertEquals(setOf("two"), first.pending)
        assertEquals(emptySet<String>(), first.possiblyUnavailable)
        val second = ProfileSnapshot.compare(first.snapshot, first.pending, setOf("one"), true)
        assertEquals(setOf("two"), second.possiblyUnavailable)
        val restored = ProfileSnapshot.compare(first.snapshot, first.pending, setOf("one", "two"), true)
        assertEquals(emptySet<String>(), restored.possiblyUnavailable)
        assertEquals(emptySet<String>(), restored.pending)
    }
    @Test fun emptyCatalogDoesNotWipeBaseline() {
        val result = ProfileSnapshot.compare(setOf("one"), emptySet(), emptySet(), true)
        assertEquals(setOf("one"), result.snapshot)
    }
}
