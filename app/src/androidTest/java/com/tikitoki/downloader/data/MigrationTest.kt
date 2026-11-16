package com.tikitoki.downloader.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), DownloadDatabase::class.java)

    @Test fun pendingDownloadsArePreservedInMigratedBatch() {
        val name = "migration-pending"
        helper.createDatabase(name, 1).apply {
            listOf("QUEUED", "DOWNLOADING", "PAUSED", "COMPLETED", "FAILED").forEachIndexed { index, status ->
                execSQL(
                    "INSERT INTO downloads (videoId, videoUrl, title, thumbnail, formatId, status, progress, savedUri, error, createdAt, updatedAt) VALUES (?, ?, ?, NULL, ?, ?, ?, NULL, NULL, ?, ?)",
                    arrayOf("id$index", "https://example.com/$index", "Título $index", "audio", status, 20, index.toLong(), index.toLong())
                )
            }
            close()
        }
        helper.runMigrationsAndValidate(name, 2, true, DownloadDatabase.MIGRATION_1_2).use { db ->
            db.query("SELECT batchId, duplicates, acknowledged FROM download_batches").use { cursor ->
                assertEquals(1, cursor.count)
                assertTrue(cursor.moveToFirst())
                assertEquals("migrated-pending", cursor.getString(0))
                assertEquals(0, cursor.getInt(1))
                assertEquals(0, cursor.getInt(2))
            }
            db.query("SELECT videoId, outcome FROM download_batch_items WHERE batchId = 'migrated-pending' ORDER BY videoId").use { cursor ->
                assertEquals(3, cursor.count)
                listOf("id0", "id1", "id2").forEach { expected ->
                    assertTrue(cursor.moveToNext())
                    assertEquals(expected, cursor.getString(0))
                    assertNull(cursor.getString(1))
                }
            }
            db.query("SELECT status, progress FROM downloads ORDER BY videoId").use { cursor ->
                listOf("QUEUED", "DOWNLOADING", "PAUSED", "COMPLETED", "FAILED").forEach { status ->
                    assertTrue(cursor.moveToNext())
                    assertEquals(status, cursor.getString(0))
                    assertEquals(20, cursor.getInt(1))
                }
            }
        }
    }

    @Test fun noPendingDownloadsDoesNotCreateBatch() {
        val name = "migration-empty"
        helper.createDatabase(name, 1).close()
        helper.runMigrationsAndValidate(name, 2, true, DownloadDatabase.MIGRATION_1_2).use { db ->
            db.query("SELECT COUNT(*) FROM download_batches").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
    }
}
