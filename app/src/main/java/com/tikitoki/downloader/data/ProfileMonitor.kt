package com.tikitoki.downloader.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tikitoki.downloader.domain.LinkParseResult
import com.tikitoki.downloader.domain.TikTokLinkParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/** Only monitors explicitly saved public profiles. No credentials or private-content discovery. */
object ProfileMonitor {
    private const val PREFS = "tikitoki_monitored_profiles"
    fun profiles(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet("urls", emptySet()).orEmpty().toSet()

    fun add(context: Context, url: String): Boolean {
        val parsed = TikTokLinkParser.parse(url)
        if (parsed !is LinkParseResult.Success || !url.contains("tiktok.com/@")) return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putStringSet("urls", profiles(context) + parsed.link.url).apply()
        schedule(context)
        return true
    }

    fun remove(context: Context, url: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putStringSet("urls", profiles(context) - url).apply()
    }

    fun newCount(context: Context, url: String): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("new:$url", 0)

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<ProfileMonitorWorker>(12, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "tikitoki_public_profile_monitor",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}

class ProfileMonitorWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences("tikitoki_monitored_profiles", Context.MODE_PRIVATE)
        val source = TikTokVideoSource(applicationContext)
        var failures = 0
        for (url in ProfileMonitor.profiles(applicationContext)) {
            if (isStopped) break
            val parsed = TikTokLinkParser.parse(url)
            if (parsed !is LinkParseResult.Success) continue
            try {
                val videos = source.inspect(parsed.link, forceRefresh = true)
                val ids = videos.map { it.id }.toSet()
                val old = prefs.getStringSet("ids:$url", emptySet()).orEmpty()
                val newCount = if (old.isEmpty()) 0 else (ids - old).size
                prefs.edit().putStringSet("ids:$url", ids).putInt("new:$url", newCount)
                    .putLong("checked:$url", System.currentTimeMillis()).apply()
            } catch (_: Exception) { failures++ }
        }
        if (failures > 0) Result.retry() else Result.success()
    }
}
