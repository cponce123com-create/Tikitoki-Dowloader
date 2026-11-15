package com.tikitoki.downloader.data

internal object MonitorPolicy {
    const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
    fun interval(stored: Int): Int = when (stored) {
        1, 5, 6 -> 6
        12 -> 12
        24 -> 24
        else -> 6
    }
    fun pageLimit(stored: Int): Int = stored.coerceIn(1, 100)
    fun fullDue(now: Long, last: Long): Boolean = last <= 0 || (now >= last && now - last >= WEEK_MS)
    fun rateLimited(error: Throwable): Boolean {
        var current: Throwable? = error
        repeat(8) {
            val item = current ?: return false
            if (Regex("(?<![0-9])429(?![0-9])").containsMatchIn(item.message.orEmpty()) || item.message.orEmpty().contains("too many requests", true)) return true
            val next = item.cause
            if (next === item) return false
            current = next
        }
        return false
    }
}
