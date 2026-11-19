package com.tikitoki.downloader.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadProgressPersistenceTest {
    @Test fun persistsOnlyChangedProgressAndAlwaysPersistsCompletion() {
        assertTrue(shouldPersistDownloadProgress(null, 0, intervalElapsed = true))
        assertFalse(shouldPersistDownloadProgress(42, 42, intervalElapsed = true))
        assertFalse(shouldPersistDownloadProgress(42, 43, intervalElapsed = false))
        assertTrue(shouldPersistDownloadProgress(42, 43, intervalElapsed = true))
        assertTrue(shouldPersistDownloadProgress(99, 100, intervalElapsed = false))
    }
}
