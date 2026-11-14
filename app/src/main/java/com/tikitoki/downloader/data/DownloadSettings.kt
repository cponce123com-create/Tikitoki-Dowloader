package com.tikitoki.downloader.data

import android.content.Context
import android.net.ConnectivityManager

/** A metered Wi-Fi hotspot is treated like mobile data to avoid unexpected charges. */
object DownloadSettings {
    private fun prefs(context: Context) = context.getSharedPreferences("tikitoki_settings", Context.MODE_PRIVATE)
    fun wifiLimit(context: Context): Int = prefs(context).getInt("wifi_parallel", prefs(context).getInt("parallel_downloads", 3)).coerceIn(1, 8)
    fun meteredLimit(context: Context): Int = prefs(context).getInt("metered_parallel", 2).coerceIn(1, 8)
    fun allowMetered(context: Context): Boolean = prefs(context).getBoolean("allow_metered", true)
    fun showProgress(context: Context): Boolean = prefs(context).getBoolean("notify_progress_detail", true)
    fun notifyCompletion(context: Context): Boolean = prefs(context).getBoolean("notify_completion", true)
    fun rateKiB(context: Context): Int = prefs(context).getInt("rate_kib", 0).coerceIn(0, 8192)
    fun isMetered(context: Context): Boolean = (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).isActiveNetworkMetered
    fun currentLimit(context: Context): Int = if (isMetered(context)) {
        if (allowMetered(context)) meteredLimit(context) else 0
    } else wifiLimit(context)
}
