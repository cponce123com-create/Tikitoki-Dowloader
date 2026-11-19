package com.tikitoki.downloader.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadObservationQueryTest {
    private lateinit var db: DownloadDatabase

    @Before
    fun openDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, DownloadDatabase::class.java).build()
    }

    @After
    fun closeDatabase() {
        db.close()
    }

    @Test
    fun observationFlowsReturnOnlyRelevantStatuses() {
        runBlocking {
            val dao = db.downloads()
            dao.upsertAll(
                listOf(
                    downloadRecord("queued", DownloadState.QUEUED, 1L),
                    downloadRecord("downloading", DownloadState.DOWNLOADING, 2L),
                    downloadRecord("paused", DownloadState.PAUSED, 3L),
                    downloadRecord("completed", DownloadState.COMPLETED, 4L),
                    downloadRecord("failed", DownloadState.FAILED, 5L),
                    downloadRecord("cancelled", DownloadState.CANCELLED, 6L),
                ),
            )

            assertEquals(
                listOf("paused", "downloading", "queued"),
                dao.observeQueue().first().map { it.videoId },
            )
            assertEquals(listOf("completed"), dao.observeCompleted().first().map { it.videoId })
            assertEquals(1, dao.observeFailedCount().first())
        }
    }

    private fun downloadRecord(
        id: String,
        status: String,
        createdAt: Long,
    ): DownloadRecord =
        DownloadRecord(
            videoId = id,
            videoUrl = "https://www.youtube.com/watch?v=$id",
            title = id,
            thumbnail = null,
            formatId = "best",
            status = status,
            createdAt = createdAt,
            updatedAt = createdAt,
        )
}
