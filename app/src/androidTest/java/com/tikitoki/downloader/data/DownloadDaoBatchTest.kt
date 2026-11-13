package com.tikitoki.downloader.data

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    }
}
