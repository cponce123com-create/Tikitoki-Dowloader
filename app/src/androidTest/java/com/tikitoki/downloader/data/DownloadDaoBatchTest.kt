package com.tikitoki.downloader.data

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import android.database.sqlite.SQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadDaoBatchTest {
    private lateinit var db: DownloadDatabase

    @Before fun openDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, DownloadDatabase::class.java).build()
    }

    @After fun closeDatabase() { db.close() }

    @Test fun bulkInsertAndRollbackAreAtomic() = runBlocking {
        val dao = db.downloads()
        val records = (1..3).map { DownloadRecord("id$it", "https://www.youtube.com/watch?v=$it", "Video $it", null, "best") }
        try {
            db.withTransaction {
                dao.upsertAll(records)
                throw IllegalStateException("simulate interrupted enqueue")
            }
        } catch (_: IllegalStateException) { }
        assertNull(dao.find("id1"))
        db.withTransaction { dao.upsertAll(records) }
        assertEquals(3, dao.findMany(listOf("id1", "id2", "id3")).size)
        val nextId = dao.nextQueuedExcluding(listOf("id1"))?.videoId.orEmpty()
        assertTrue(nextId == "id2" || nextId == "id3")
    }

    @Test fun summaryCountsEachTerminalOutcomeOnce() = runBlocking {
        val dao = db.downloads()
        val records = (1..3).map { DownloadRecord("v$it", "https://www.youtube.com/watch?v=$it", "Video", null, "best") }
        db.withTransaction {
            dao.upsertAll(records)
            dao.insertBatch(DownloadBatch("batch", 1L, 2))
            dao.insertBatchItems(records.map { DownloadBatchItem("batch", it.videoId) })
        }
        assertEquals(0, dao.observeFinishedBatches().first().size)
        assertEquals(listOf("batch"), dao.pendingBatchIds("v1"))
        dao.claimQueued("v1"); dao.claimQueued("v2")
        dao.markCompleted("v1", "content://example/1")
        dao.markFailed("v2", "failed")
        assertEquals(0, dao.observeFinishedBatches().first().size)
        assertEquals(0, dao.finishedBatches(listOf("batch")).size)
        dao.markCancelled("v3")
        val result = dao.observeFinishedBatches().first().single()
        assertEquals(3, result.total)
        assertEquals(1, result.completed)
        assertEquals(1, result.failed)
        assertEquals(1, result.cancelled)
        assertEquals(2, result.duplicates)
        assertEquals(result, dao.finishedBatches(listOf("batch")).single())
        dao.acknowledgeBatch("batch")
        assertEquals(0, dao.observeFinishedBatches().first().size)
        // The notification query must remain valid even if the user already closed the dialog.
        assertEquals(result, dao.finishedBatches(listOf("batch")).single())
    }

    @Test fun migrationPreservesPendingDownloads() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "download-migration-test.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name)
        path.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            old.execSQL("CREATE TABLE downloads (videoId TEXT NOT NULL PRIMARY KEY, videoUrl TEXT NOT NULL, title TEXT NOT NULL, thumbnail TEXT, formatId TEXT NOT NULL, status TEXT NOT NULL, progress INTEGER NOT NULL, savedUri TEXT, error TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
            old.execSQL("INSERT INTO downloads VALUES ('old', 'https://www.youtube.com/watch?v=1', 'Video', NULL, 'best', 'QUEUED', 0, NULL, NULL, 1, 1)")
            old.version = 1
        }
        val migrated = Room.databaseBuilder(context, DownloadDatabase::class.java, name)
            .addMigrations(DownloadDatabase.MIGRATION_1_2).build()
        try {
            val dao = migrated.downloads()
            assertEquals("QUEUED", dao.find("old")?.status)
            dao.claimQueued("old")
            dao.markCompleted("old", "content://example/old")
            assertEquals(1, dao.observeFinishedBatches().first().single().completed)
        } finally { migrated.close(); context.deleteDatabase(name) }
    }
}
