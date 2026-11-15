package com.tikitoki.downloader.data

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractorAccessTest {
    @Test fun pendingUpdateDoesNotBlockNewQueries() {
        val executor = Executors.newFixedThreadPool(2)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        try {
            val first = executor.submit {
                ExtractorAccess.useEngine { started.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
            }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            assertFalse(ExtractorAccess.tryUpdateEngine { error("must not update during a read") })
            val second = executor.submit<Boolean> { ExtractorAccess.useEngine { true } }
            assertTrue(second.get(5, TimeUnit.SECONDS))
            release.countDown()
            first.get(5, TimeUnit.SECONDS)
            var ran = false
            assertTrue(ExtractorAccess.tryUpdateEngine { ran = true })
            assertTrue(ran)
        } finally { release.countDown(); executor.shutdownNow() }
    }

    @Test fun updateWaitsForActiveExtraction() {
        val executor = Executors.newFixedThreadPool(2)
        val extractionStarted = CountDownLatch(1)
        val releaseExtraction = CountDownLatch(1)
        val updateStarted = CountDownLatch(1)
        try {
            val extraction = executor.submit {
                ExtractorAccess.useEngine {
                    extractionStarted.countDown()
                    check(releaseExtraction.await(5, TimeUnit.SECONDS))
                }
            }
            assertTrue(extractionStarted.await(5, TimeUnit.SECONDS))
            val update = executor.submit { ExtractorAccess.updateEngine { updateStarted.countDown() } }
            assertFalse(updateStarted.await(200, TimeUnit.MILLISECONDS))
            releaseExtraction.countDown()
            extraction.get(5, TimeUnit.SECONDS)
            update.get(5, TimeUnit.SECONDS)
            assertTrue(updateStarted.await(5, TimeUnit.SECONDS))
        } finally {
            releaseExtraction.countDown()
            executor.shutdownNow()
        }
    }
}

