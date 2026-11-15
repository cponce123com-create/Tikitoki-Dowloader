package com.tikitoki.downloader.data

import android.content.Context

internal data class DiagnosticEntry(val time: Long, val category: ErrorCategory)

/** Stores only a timestamp and an enum; raw messages and URLs are never persisted here. */
internal object DiagnosticHistory {
    private const val PREFS = "diagnostic_history"
    private const val KEY = "entries"
    private const val LIMIT = 20

    internal fun decode(raw: String): List<DiagnosticEntry> = raw.lineSequence().mapNotNull { line ->
        val parts = line.split('|')
        if (parts.size != 2) return@mapNotNull null
        val time = parts[0].toLongOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
        val category = ErrorCategory.entries.firstOrNull { it.name == parts[1] } ?: return@mapNotNull null
        DiagnosticEntry(time, category)
    }.take(LIMIT).toList()

    internal fun encode(entries: List<DiagnosticEntry>): String = entries.take(LIMIT).joinToString("\n") { "${it.time}|${it.category.name}" }

    @Synchronized fun record(context: Context, message: String?) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val entries = listOf(DiagnosticEntry(System.currentTimeMillis(), DiagnosticReport.errorCategory(message))) + decode(prefs.getString(KEY, "").orEmpty())
        prefs.edit().putString(KEY, encode(entries)).apply()
    }

    @Synchronized fun read(context: Context): List<DiagnosticEntry> =
        decode(context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty())
}
