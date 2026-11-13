package com.tikitoki.downloader.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Updates the Python extractor off the scan/download critical path. */
class ExtractorUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Never replace the extractor while a download queue is active.
            if (DownloadDatabase.get(applicationContext).downloads().pendingRecoveryCount() > 0)
                return@withContext Result.retry()
            val engine = YoutubeDL.getInstance()
            engine.init(applicationContext)
            engine.updateYoutubeDL(applicationContext, YoutubeDL.UpdateChannel.STABLE)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK = "tikitoki_extractor_update"
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
