package com.tikitoki.downloader.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryVisibilityTest {
    @Test fun queueContainsOnlyUnfinishedDownloads() {
        assertTrue(LibraryVisibility.inQueue(DownloadState.DOWNLOADING))
        assertTrue(LibraryVisibility.inQueue(DownloadState.QUEUED))
        assertTrue(LibraryVisibility.inQueue(DownloadState.PAUSED))
        assertFalse(LibraryVisibility.inQueue(DownloadState.COMPLETED))
        assertFalse(LibraryVisibility.inQueue(DownloadState.FAILED))
        assertFalse(LibraryVisibility.inQueue(DownloadState.CANCELLED))
    }

    @Test fun libraryRequiresACompletedSavedFile() {
        assertTrue(LibraryVisibility.inLibrary(DownloadState.COMPLETED, "content://video/1"))
        assertFalse(LibraryVisibility.inLibrary(DownloadState.COMPLETED, null))
        assertFalse(LibraryVisibility.inLibrary(DownloadState.DOWNLOADING, "content://video/1"))
    }
}
