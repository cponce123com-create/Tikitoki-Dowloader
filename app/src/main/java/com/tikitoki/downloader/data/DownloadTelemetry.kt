package com.tikitoki.downloader.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Only validated transfer rates, never raw extractor output or URLs. */
internal object DownloadTelemetry {
    private val mutable = MutableStateFlow<Map<String, String>>(emptyMap())
    val speeds: StateFlow<Map<String, String>> = mutable
    private val last = mutableMapOf<String, Long>()

    @Synchronized fun update(id: String, speed: String) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - (last[id] ?: -1000L) < 500) return
        last[id] = now
        mutable.value = mutable.value + (id to speed)
    }
    @Synchronized fun remove(id: String) {
        last.remove(id)
        if (id in mutable.value) mutable.value = mutable.value - id
    }
}
