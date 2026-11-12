package com.tikitoki.downloader.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryStatsTest {
    @Test fun countsEachStatus() {
        val stats = LibraryAnalytics.summarize(listOf(
            DownloadState.COMPLETED, DownloadState.COMPLETED,
            DownloadState.FAILED, DownloadState.QUEUED, DownloadState.PAUSED
        ))
        assertEquals(5, stats.total)
        assertEquals(2, stats.completed)
        assertEquals(1, stats.failed)
        assertEquals(1, stats.queued)
        assertEquals(1, stats.paused)
        assertEquals(0, stats.downloading)
    }

    @Test fun emptyLibrary() {
        assertEquals(0, LibraryAnalytics.summarize(emptyList()).total)
    }
}
