package com.tikitoki.downloader.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

object DownloadState {
    const val QUEUED = "QUEUED"
    const val DOWNLOADING = "DOWNLOADING"
    const val PAUSED = "PAUSED"
    const val COMPLETED = "COMPLETED"
    const val FAILED = "FAILED"
    const val CANCELLED = "CANCELLED"
}

@Entity(tableName = "downloads")
data class DownloadRecord(
    @PrimaryKey val videoId: String,
    val videoUrl: String,
    val title: String,
    val thumbnail: String?,
    val formatId: String,
    val status: String = DownloadState.QUEUED,
    val progress: Int = 0,
    val savedUri: String? = null,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "download_batches")
data class DownloadBatch(@PrimaryKey val batchId: String, val createdAt: Long, val duplicates: Int, val acknowledged: Boolean = false)

@Entity(tableName = "download_batch_items", primaryKeys = ["batchId", "videoId"], indices = [androidx.room.Index(value = ["videoId"])])
data class DownloadBatchItem(val batchId: String, val videoId: String, val outcome: String? = null)

data class BatchSummary(val batchId: String, val total: Int, val completed: Int, val failed: Int, val cancelled: Int, val duplicates: Int)

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadRecord>>

    @Query("SELECT * FROM downloads WHERE videoId = :id LIMIT 1")
    suspend fun find(id: String): DownloadRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: DownloadRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<DownloadRecord>)

    @Query("SELECT * FROM downloads WHERE videoId IN (:ids)")
    suspend fun findMany(ids: List<String>): List<DownloadRecord>

    @Insert suspend fun insertBatch(batch: DownloadBatch)
    @Insert suspend fun insertBatchItems(items: List<DownloadBatchItem>)
    @Query("SELECT * FROM downloads WHERE status = 'FAILED'") suspend fun failedRecords(): List<DownloadRecord>
    @Query("UPDATE download_batch_items SET outcome = :outcome WHERE videoId = :id AND outcome IS NULL")
    suspend fun finishBatchItem(id: String, outcome: String)
    @Query("UPDATE download_batches SET acknowledged = 1 WHERE batchId = :id") suspend fun acknowledgeBatch(id: String)
    @Query("""SELECT b.batchId AS batchId, COUNT(i.videoId) AS total,
        SUM(CASE WHEN i.outcome = 'COMPLETED' THEN 1 ELSE 0 END) AS completed,
        SUM(CASE WHEN i.outcome = 'FAILED' THEN 1 ELSE 0 END) AS failed,
        SUM(CASE WHEN i.outcome = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled,
        b.duplicates AS duplicates FROM download_batches b JOIN download_batch_items i ON i.batchId = b.batchId
        WHERE b.acknowledged = 0 GROUP BY b.batchId HAVING COUNT(i.videoId) = COUNT(i.outcome)
        ORDER BY b.createdAt ASC""")
    fun observeFinishedBatches(): Flow<List<BatchSummary>>

    @Transaction suspend fun markCompleted(id: String, uri: String): Int {
        val changed = completeIfDownloading(id, uri)
        if (changed > 0) finishBatchItem(id, DownloadState.COMPLETED)
        return changed
    }

    @Transaction suspend fun markFailed(id: String, error: String): Int {
        val changed = failIfDownloading(id, error)
        if (changed > 0) finishBatchItem(id, DownloadState.FAILED)
        return changed
    }

    @Transaction suspend fun markCancelled(id: String): Int {
        val changed = cancelPending(id)
        if (changed > 0) finishBatchItem(id, DownloadState.CANCELLED)
        return changed
    }

    @Query("SELECT COUNT(*) FROM downloads WHERE status IN ('QUEUED', 'DOWNLOADING')")
    suspend fun pendingRecoveryCount(): Int

    @Query("SELECT * FROM downloads WHERE status = 'QUEUED' ORDER BY createdAt ASC LIMIT 1")
    suspend fun nextQueued(): DownloadRecord?

    @Query("UPDATE downloads SET status = 'DOWNLOADING', updatedAt = :now WHERE videoId = :id AND status = 'QUEUED'")
    suspend fun claimQueued(id: String, now: Long = System.currentTimeMillis()): Int

    @Query("UPDATE downloads SET status = 'COMPLETED', progress = 100, savedUri = :uri, error = NULL, updatedAt = :now WHERE videoId = :id AND status = 'DOWNLOADING'")
    suspend fun completeIfDownloading(id: String, uri: String, now: Long = System.currentTimeMillis()): Int

    @Query("UPDATE downloads SET status = 'PAUSED', updatedAt = :now WHERE videoId = :id AND status = 'DOWNLOADING'")
    suspend fun pauseIfDownloading(id: String, now: Long = System.currentTimeMillis()): Int

    @Query("UPDATE downloads SET status = 'FAILED', error = :error, updatedAt = :now WHERE videoId = :id AND status = 'DOWNLOADING'")
    suspend fun failIfDownloading(id: String, error: String, now: Long = System.currentTimeMillis()): Int

    @Query("UPDATE downloads SET status = 'CANCELLED', error = NULL, updatedAt = :now WHERE videoId = :id AND status IN ('QUEUED', 'DOWNLOADING', 'PAUSED')")
    suspend fun cancelPending(id: String, now: Long = System.currentTimeMillis()): Int

    @Query("UPDATE downloads SET status = :status, updatedAt = :now, error = :error WHERE videoId = :id")
    suspend fun setStatus(
        id: String,
        status: String,
        now: Long = System.currentTimeMillis(),
        error: String? = null
    )

    @Query("UPDATE downloads SET progress = :progress, updatedAt = :now WHERE videoId = :id AND status = 'DOWNLOADING'")
    suspend fun setProgress(id: String, progress: Int, now: Long = System.currentTimeMillis())

    @Query("UPDATE downloads SET status = 'COMPLETED', progress = 100, savedUri = :uri, error = NULL, updatedAt = :now WHERE videoId = :id")
    suspend fun completed(id: String, uri: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE downloads SET status = 'PAUSED', updatedAt = :now WHERE status IN ('QUEUED', 'DOWNLOADING')")
    suspend fun pausePending(now: Long = System.currentTimeMillis())

    @Query("UPDATE downloads SET status = 'QUEUED', updatedAt = :now WHERE status IN ('PAUSED', 'DOWNLOADING')")
    suspend fun resumePending(now: Long = System.currentTimeMillis())

    @Query("UPDATE downloads SET status = 'QUEUED', updatedAt = :now WHERE status = 'DOWNLOADING'")
    suspend fun requeueInterrupted(now: Long = System.currentTimeMillis())

    @Query("UPDATE downloads SET status = 'QUEUED', progress = 0, error = NULL, updatedAt = :now WHERE status = 'FAILED'")
    suspend fun retryFailed(now: Long = System.currentTimeMillis())

    @Query("DELETE FROM downloads WHERE videoId = :id AND status = 'COMPLETED'")
    suspend fun deleteCompleted(id: String): Int

    @Query("DELETE FROM downloads WHERE status = 'FAILED'")
    suspend fun clearFailed()
}

@Database(entities = [DownloadRecord::class, DownloadBatch::class, DownloadBatchItem::class], version = 2, exportSchema = false)
abstract class DownloadDatabase : RoomDatabase() {
    abstract fun downloads(): DownloadDao

    companion object {
        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS download_batches (batchId TEXT NOT NULL PRIMARY KEY, createdAt INTEGER NOT NULL, duplicates INTEGER NOT NULL, acknowledged INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS download_batch_items (batchId TEXT NOT NULL, videoId TEXT NOT NULL, outcome TEXT, PRIMARY KEY(batchId, videoId))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_download_batch_items_videoId ON download_batch_items (videoId)")
                // Preserve the outstanding queue as one batch across upgrades.
                db.execSQL("INSERT INTO download_batches(batchId, createdAt, duplicates, acknowledged) SELECT 'migrated-pending', 0, 0, 0 WHERE EXISTS (SELECT 1 FROM downloads WHERE status IN ('QUEUED','DOWNLOADING','PAUSED'))")
                db.execSQL("INSERT INTO download_batch_items(batchId, videoId, outcome) SELECT 'migrated-pending', videoId, NULL FROM downloads WHERE status IN ('QUEUED','DOWNLOADING','PAUSED')")
            }
        }
        @Volatile private var INSTANCE: DownloadDatabase? = null

        fun get(context: Context): DownloadDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DownloadDatabase::class.java,
                    "tikitoki_downloads.db",
                ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
            }
    }
}

