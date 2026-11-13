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

@Database(entities = [DownloadRecord::class], version = 1, exportSchema = false)
abstract class DownloadDatabase : RoomDatabase() {
    abstract fun downloads(): DownloadDao

    companion object {
        @Volatile private var INSTANCE: DownloadDatabase? = null

        fun get(context: Context): DownloadDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DownloadDatabase::class.java,
                    "tikitoki_downloads.db",
                ).build().also { INSTANCE = it }
            }
    }
}

