package com.tikitoki.downloader.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.tikitoki.downloader.MainActivity
import com.tikitoki.downloader.domain.LinkParseResult
import com.tikitoki.downloader.domain.TikTokLinkParser
import com.tikitoki.downloader.domain.ProfileSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/** Monitoring is limited to profiles explicitly followed by the user. */
object ProfileMonitor {
    internal const val PREFS = "tikitoki_monitored_profiles"
    private const val RECENT = "tikitoki_public_profile_monitor"
    private const val FULL = "tikitoki_full_profile_monitor"
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun profiles(context: Context): Set<String> = prefs(context).getStringSet("urls", emptySet()).orEmpty().toSet()
    fun interval(context: Context): Int = prefs(context).getInt("interval_hours", 5).takeIf { it in listOf(1, 5, 12) } ?: 5
    fun setInterval(context: Context, hours: Int) {
        require(hours in listOf(1, 5, 12))
        prefs(context).edit().putInt("interval_hours", hours).apply()
        schedule(context)
    }
    fun add(context: Context, url: String): Boolean {
        val parsed = TikTokLinkParser.parse(url)
        if (parsed !is LinkParseResult.Success || parsed.link.type != com.tikitoki.downloader.domain.TikTokLinkType.PROFILE) return false
        prefs(context).edit().putStringSet("urls", profiles(context) + parsed.link.url).apply()
        schedule(context)
        return true
    }
    fun remove(context: Context, url: String) {
        prefs(context).edit().putStringSet("urls", profiles(context) - url)
            .remove("ids:$url").remove("new:$url").remove("checked:$url").remove("error:$url")
            .remove("snapshot:$url").remove("missing:$url").remove("count:$url").apply()
        if (profiles(context).isEmpty()) {
            WorkManager.getInstance(context).cancelUniqueWork(RECENT)
            WorkManager.getInstance(context).cancelUniqueWork(FULL)
        }
    }
    fun lastChecked(context: Context, url: String): Long = prefs(context).getLong("checked:$url", 0L)
    fun lastError(context: Context, url: String): String? = prefs(context).getString("error:$url", null)
    fun newCount(context: Context, url: String): Int = prefs(context).getInt("new:$url", 0)
    fun schedule(context: Context) {
        if (profiles(context).isEmpty()) return
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val recent = PeriodicWorkRequestBuilder<ProfileMonitorWorker>(interval(context).toLong(), TimeUnit.HOURS)
            .setConstraints(constraints).build()
        val full = PeriodicWorkRequestBuilder<ProfileFullMonitorWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(15, TimeUnit.MINUTES).setConstraints(constraints).build()
        val manager = WorkManager.getInstance(context)
        manager.enqueueUniquePeriodicWork(RECENT, ExistingPeriodicWorkPolicy.UPDATE, recent)
        manager.enqueueUniquePeriodicWork(FULL, ExistingPeriodicWorkPolicy.KEEP, full)
    }
}

private object ProfileNotice {
    fun send(context: Context, url: String, message: String) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val channel = "profile_monitor"
        if (Build.VERSION.SDK_INT >= 26) {
            val system = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            system.createNotificationChannel(NotificationChannel(channel, "Monitoreo de perfiles", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val intent = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notice = NotificationCompat.Builder(context, channel).setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("Baja.pe · perfiles").setContentText(message).setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(intent).setAutoCancel(true).build()
        try { manager.notify("profile:$url:$message", 0, notice) } catch (_: SecurityException) { }
    }
}

private val recentLock = Mutex()

/** Shared by both workers so the daily pass checks the newest entries first. */
private suspend fun checkRecent(context: Context, url: String, source: TikTokVideoSource): Boolean = recentLock.withLock {
    val prefs = context.getSharedPreferences(ProfileMonitor.PREFS, Context.MODE_PRIVATE)
    val parsed = TikTokLinkParser.parse(url) as? LinkParseResult.Success ?: return@withLock false
    try {
        val page = source.inspectPageWithStatus(parsed.link, page = 1, pageSize = 10)
        if (page.incomplete || page.items.isEmpty()) throw IllegalStateException("No se pudo completar la consulta del perfil.")
        if (url !in ProfileMonitor.profiles(context)) return@withLock false
        val ids = page.items.map { it.id }.toSet()
        val old = prefs.getStringSet("ids:$url", emptySet()).orEmpty().toSet()
        val newIds = if (old.isEmpty()) emptySet() else ids - old
        prefs.edit().putStringSet("ids:$url", old + ids).putInt("new:$url", newIds.size)
            .putLong("checked:$url", System.currentTimeMillis()).remove("error:$url").apply()
        if (newIds.isNotEmpty()) ProfileNotice.send(context, url, "@${parsed.link.username}: ${newIds.size} video(s) nuevo(s).")
        true
    } catch (e: CancellationException) { throw e }
      catch (e: Exception) {
        prefs.edit().putString("error:$url", e.message?.take(180) ?: "Error temporal de consulta").apply()
        false
    }
}

class ProfileMonitorWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val source = TikTokVideoSource(applicationContext)
        for (url in ProfileMonitor.profiles(applicationContext)) {
            if (isStopped) return@withContext Result.success()
            checkRecent(applicationContext, url, source)
        }
        Result.success()
    }
}

/** A disappearance is only a candidate: extraction can be incomplete even without an error. */
class ProfileFullMonitorWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences(ProfileMonitor.PREFS, Context.MODE_PRIVATE)
        val source = TikTokVideoSource(applicationContext)
        for (url in ProfileMonitor.profiles(applicationContext)) {
            if (isStopped) return@withContext Result.success()
            val parsed = TikTokLinkParser.parse(url) as? LinkParseResult.Success ?: continue
            if (!checkRecent(applicationContext, url, source)) continue
            try {
                val all = mutableSetOf<String>()
                var complete = false
                for (number in 1..100) {
                    if (isStopped) return@withContext Result.success()
                    val page = source.inspectPageWithStatus(parsed.link, number, 100)
                    val fresh = page.items.count { all.add(it.id) }
                    when (scanPageState(page, 100, fresh)) {
                        ScanPageState.PARTIAL -> break
                        ScanPageState.END -> { complete = true; break }
                        ScanPageState.CONTINUE -> Unit
                    }
                }
                if (!complete || all.isEmpty() || url !in ProfileMonitor.profiles(applicationContext)) continue
                recentLock.withLock {
                if (url !in ProfileMonitor.profiles(applicationContext)) return@withLock
                val previous = prefs.getStringSet("snapshot:$url", emptySet()).orEmpty().toSet()
                val missingOnce = prefs.getStringSet("missing:$url", emptySet()).orEmpty().toSet()
                val result = ProfileSnapshot.compare(previous, missingOnce, all, complete = true)
                val confirmed = result.possiblyUnavailable
                val edit = prefs.edit().putStringSet("snapshot:$url", result.snapshot).putStringSet("missing:$url", result.pending)
                    .putInt("count:$url", all.size)
                if (previous.isEmpty()) {
                    val known = prefs.getStringSet("ids:$url", emptySet()).orEmpty().toSet()
                    edit.putStringSet("ids:$url", known + all)
                }
                edit.apply()
                if (confirmed.isNotEmpty()) ProfileNotice.send(applicationContext, url, "@${parsed.link.username}: ${confirmed.size} video(s) podrían no estar disponibles.")
                }
            } catch (e: CancellationException) { throw e }
              catch (_: Exception) { /* Never report disappearance on a failed scan. */ }
        }
        Result.success()
    }
}
