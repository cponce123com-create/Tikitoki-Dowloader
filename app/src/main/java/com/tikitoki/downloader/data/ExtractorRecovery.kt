package com.tikitoki.downloader.data

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import java.io.File

/** Snapshot the updater's metadata before publishing the binary journal. */
internal object ExtractorRecovery {
    private const val JOURNAL = "extractor_recovery"
    private const val UPDATER = "youtubedl-android"
    private val keys = listOf("dlpVersion", "dlpVersionName")

    fun binary(context: Context): File = File(context.noBackupFilesDir, "${YoutubeDL.baseName}/${YoutubeDL.ytdlpDirName}/${YoutubeDL.ytdlpBin}")

    fun snapshot(context: Context) {
        val source = context.getSharedPreferences(UPDATER, Context.MODE_PRIVATE)
        val journal = context.getSharedPreferences(JOURNAL, Context.MODE_PRIVATE).edit().clear()
        keys.forEach { key ->
            journal.putBoolean("present_$key", source.contains(key))
            journal.putString(key, source.getString(key, null))
        }
        check(journal.commit()) { "Cannot persist extractor metadata" }
    }

    fun restoreMetadata(context: Context) {
        val journal = context.getSharedPreferences(JOURNAL, Context.MODE_PRIVATE)
        val target = context.getSharedPreferences(UPDATER, Context.MODE_PRIVATE).edit()
        keys.forEach { key ->
            if (journal.getBoolean("present_$key", false)) target.putString(key, journal.getString(key, null))
            else target.remove(key)
        }
        check(target.commit()) { "Cannot restore extractor metadata" }
    }

    fun recover(context: Context): Boolean = ExtractorRollback.recover(binary(context)) { restoreMetadata(context) }
}
