package com.tikitoki.downloader.data

import android.content.Context
import android.net.ConnectivityManager
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Validates an offline extractor listing without relying on network content. */
internal fun extractorSmokePassed(
    exitCode: Int,
    output: String,
): Boolean = exitCode == 0 && output.lineSequence().any { it.trim().equals("youtube", ignoreCase = true) }

/** Updates the Python extractor off the scan/download critical path. */
class ExtractorUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            try {
                // Existing scheduled work may still have the old CONNECTED constraint.
                val network = applicationContext.getSystemService(ConnectivityManager::class.java)
                if (network == null || network.isActiveNetworkMetered) {
                    return@withContext Result.retry()
                }
                // Never replace the extractor while a download queue is active.
                if (DownloadDatabase.get(applicationContext).downloads().pendingRecoveryCount() > 0) {
                    return@withContext Result.retry()
                }
                var updated: YoutubeDL.UpdateStatus? = null
                var effectiveVersion: String? = null
                val acquired =
                    ExtractorAccess.tryUpdateEngine {
                        val engine = YoutubeDL.getInstance()
                        engine.init(applicationContext)
                        val binary = ExtractorRecovery.binary(applicationContext)
                        ExtractorRecovery.snapshot(applicationContext)
                        ExtractorRollback.run(binary, restoreMetadata = { ExtractorRecovery.restoreMetadata(applicationContext) }) {
                            updated = engine.updateYoutubeDL(applicationContext, YoutubeDL.UpdateChannel.STABLE)
                            if (!ExtractorVerifier.verifyAndLog(applicationContext)) {
                                throw SecurityException("Versión de yt-dlp sin hash aprobado")
                            }
                            val response = engine.execute(YoutubeDLRequest(emptyList()).addOption("--version"))
                            check(response.exitCode == 0) { "Cannot verify extractor version" }
                            effectiveVersion = ExtractorVersion.parse(response.out)
                                ?: throw IllegalStateException("Cannot verify extractor version")
                            val smoke = engine.execute(YoutubeDLRequest(emptyList()).addOption("--list-extractors"))
                            check(extractorSmokePassed(smoke.exitCode, smoke.out)) { "Extractor smoke test failed" }
                        }
                    }
                if (!acquired) return@withContext Result.retry()
                val prefs = applicationContext.getSharedPreferences("extractor_status", Context.MODE_PRIVATE)
                prefs
                    .edit()
                    .putLong("last_success", System.currentTimeMillis())
                    .putString("last_result", "Actualización ejecutada: $updated")
                    .apply { effectiveVersion?.let { putString("effective_version", it) } }
                    .apply()
                Result.success()
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                recordFailure(e)
                Result.failure()
            } catch (e: RestoreFailedException) {
                ExtractorAccess.setRecoveryReady(false)
                recordFailure(e)
                Result.retry()
            } catch (e: Exception) {
                recordFailure(e)
                Result.retry()
            }
        }

    private fun recordFailure(error: Exception) {
        DiagnosticHistory.record(applicationContext, error.message)
        applicationContext
            .getSharedPreferences("extractor_status", Context.MODE_PRIVATE)
            .edit()
            .putString("last_result", "Falló: ${error.message.orEmpty().take(120)}")
            .apply()
    }

    companion object {
        private const val WORK = "tikitoki_extractor_update"

        fun updateNow(context: Context) {
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                "tikitoki_extractor_update_now",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<ExtractorUpdateWorker>()
                    .setConstraints(
                        Constraints
                            .Builder()
                            .setRequiredNetworkType(NetworkType.UNMETERED)
                            .build(),
                    ).build(),
            )
        }

        fun schedule(context: Context) {
            val request =
                PeriodicWorkRequestBuilder<ExtractorUpdateWorker>(24, TimeUnit.HOURS)
                    .setConstraints(
                        Constraints
                            .Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build(),
                    ).build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
