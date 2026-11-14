package com.tikitoki.downloader.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tikitoki.downloader.domain.ProfileDiff
import com.tikitoki.downloader.domain.LinkParseResult
import com.tikitoki.downloader.domain.TikTokLinkParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import androidx.work.Constraints
import androidx.work.NetworkType

/** Only monitors explicitly saved public profiles. No credentials or private-content discovery. */
object ProfileMonitor {
    private const val PREFS = "tikitoki_monitored_profiles"
    fun profiles(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet("urls", emptySet()).orEmpty().toSet()

    fun add(context: Context, url: String): Boolean {
        val parsed = TikTokLinkParser.parse(url)
        if (parsed !is LinkParseResult.Success || parsed.link.type != com.tikitoki.downloader.domain.TikTokLinkType.PROFILE) return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putStringSet("urls", profiles(context) + parsed.link.url).apply()
        schedule(context)
        return true
    }

    fun remove(context: Context, url: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putStringSet("urls", profiles(context) - url)
            .remove("ids:$url").remove("new:$url").remove("checked:$url").remove("error:$url").apply()
        if (profiles(context).isEmpty()) {
            WorkManager.getInstance(context).cancelUniqueWork("tikitoki_public_profile_monitor")
        }
    }

    fun lastChecked(context: Context, url: String): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("checked:$url", 0L)

    fun lastError(context: Context, url: String): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("error:$url", null)

    fun newCount(context: Context, url: String): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("new:$url", 0)

    fun schedule(context: Context) {
        if (profiles(context).isEmpty()) return
        val request = PeriodicWorkRequestBuilder<ProfileMonitorWorker>(1, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "tikitoki_public_profile_monitor",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}

class ProfileMonitorWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences("tikitoki_monitored_profiles", Context.MODE_PRIVATE)
        val source = TikTokVideoSource(applicationContext)
        for (url in ProfileMonitor.profiles(applicationContext)) {
            if (isStopped) return@withContext Result.success()
            val parsed = TikTokLinkParser.parse(url)
            if (parsed !is LinkParseResult.Success) continue
            try {
                // Inspect only the newest page instead of loading the entire catalogue.
                val videos = source.inspectPage(parsed.link, page = 1, pageSize = 100)
                if (videos.isEmpty()) continue // Never erase the baseline on an empty/blocked response.
                val ids = videos.map { it.id }.toSet()
                val old = prefs.getStringSet("ids:$url", emptySet()).orEmpty()
                // A partial catalogue must not replace a previously larger baseline.
                val diff = ProfileDiff.compare(old, ids)
                prefs.edit().putStringSet("ids:$url", diff.knownIds).putInt("new:$url", diff.newIds.size)
                    .putLong("checked:$url", System.currentTimeMillis())
                    .remove("error:$url").apply()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                prefs.edit().putString("error:$url", e.message?.take(180) ?: "Error temporal de consulta").apply()
            }
        }
        // Avoid immediate retry storms across large public profiles.
        Result.success()
    }
}
