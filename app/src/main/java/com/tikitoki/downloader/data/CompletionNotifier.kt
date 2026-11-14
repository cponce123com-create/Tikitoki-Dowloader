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
import com.tikitoki.downloader.MainActivity

/** Posts at most one notice for each completed batch. The in-app summary remains independent. */
object CompletionNotifier {
    private val lock = Any()
    suspend fun notifyFinished(context: Context, batchIds: List<String>) {
        if (batchIds.isEmpty() || !DownloadSettings.notifyCompletion(context)) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val batches = DownloadDatabase.get(context).downloads().finishedBatches(batchIds)
        if (batches.isEmpty()) return
        val prefs = context.getSharedPreferences("tikitoki_completion_notices", Context.MODE_PRIVATE)
        synchronized(lock) {
            val sent = prefs.getStringSet("sent", emptySet()).orEmpty().toMutableSet()
            val channel = "tikitoki_completions"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val system = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                system.createNotificationChannel(NotificationChannel(channel, "Descargas terminadas", NotificationManager.IMPORTANCE_DEFAULT))
            }
            batches.filter { it.batchId !in sent }.forEach { batch ->
                val intent = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val notice = NotificationCompat.Builder(context, channel)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle("Resumen de descargas")
                    .setContentText("${batch.completed} descargados · ${batch.failed} errores · ${batch.cancelled} cancelados")
                    .setStyle(NotificationCompat.BigTextStyle().bigText("${batch.total} videos: ${batch.completed} descargados, ${batch.failed} errores, ${batch.cancelled} cancelados. Duplicados omitidos: ${batch.duplicates}."))
                    .setContentIntent(intent).setAutoCancel(true).build()
                try {
                    manager.notify("tikitoki_batch_${batch.batchId}", 0, notice)
                    sent.add(batch.batchId)
                } catch (_: SecurityException) { }
            }
            prefs.edit().putStringSet("sent", sent).apply()
        }
    }
}
