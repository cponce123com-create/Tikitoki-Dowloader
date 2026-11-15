package com.tikitoki.downloader.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.ExistingWorkPolicy
import androidx.work.workDataOf
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.io.File

/** Updates the Python extractor off the scan/download critical path. */
class ExtractorUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Never replace the extractor while a download queue is active.
            if (DownloadDatabase.get(applicationContext).downloads().pendingRecoveryCount() > 0)
                return@withContext Result.retry()
            var updated: YoutubeDL.UpdateStatus? = null
            var effectiveVersion: String? = null
            val acquired = ExtractorAccess.tryUpdateEngine {
                val engine = YoutubeDL.getInstance()
                engine.init(applicationContext)
                val binary = File(applicationContext.noBackupFilesDir, "${YoutubeDL.baseName}/${YoutubeDL.ytdlpDirName}/${YoutubeDL.ytdlpBin}")
                val enginePrefs = applicationContext.getSharedPreferences("youtubedl-android", Context.MODE_PRIVATE)
                val previousTag = enginePrefs.getString("dlpVersion", null)
                val previousName = enginePrefs.getString("dlpVersionName", null)
                try {
                    ExtractorRollback.run(binary) {
                        updated = engine.updateYoutubeDL(applicationContext, YoutubeDL.UpdateChannel.STABLE)
                        val response = engine.execute(YoutubeDLRequest(emptyList()).addOption("--version"))
                        effectiveVersion = response.out.trim().takeIf { it.matches(Regex("[0-9A-Za-z._-]{1,80}")) }
                            ?: throw IllegalStateException("Cannot verify extractor version")
                    }
                } catch (e: Exception) {
                    enginePrefs.edit().putString("dlpVersion", previousTag).putString("dlpVersionName", previousName).commit()
                    throw e
                }
            }
            if (!acquired) return@withContext Result.retry()
            val prefs = applicationContext.getSharedPreferences("extractor_status", Context.MODE_PRIVATE)
            prefs.edit().putLong("last_success", System.currentTimeMillis())
                .putString("last_result", "Actualización ejecutada: $updated")
                .apply { effectiveVersion?.let { putString("effective_version", it) } }.apply()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            applicationContext.getSharedPreferences("extractor_status", Context.MODE_PRIVATE)
                .edit().putString("last_result", "Falló: ${e.message.orEmpty().take(120)}").apply()
            Result.retry()
        }
    }

    companion object {
        private const val WORK = "tikitoki_extractor_update"
        fun updateNow(context: Context) {
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                "tikitoki_extractor_update_now", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<ExtractorUpdateWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            )
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ExtractorUpdateWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                WORK, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}



