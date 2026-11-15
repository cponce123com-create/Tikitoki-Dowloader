package com.tikitoki.downloader.data

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest

/** Read the executable itself rather than assuming the updater's cached tag is current. */
internal object ExtractorVersion {
    internal fun parse(output: String): String? = output.trim().takeIf {
        it.matches(Regex("""\d{4}\.\d{2}\.\d{2}(?:[._-][0-9A-Za-z.-]+)?"""))
    }

    fun read(context: Context): String? {
        try {
            return ExtractorAccess.useEngine {
                val app = context.applicationContext
                val engine = YoutubeDL.getInstance()
                engine.init(app)
                val response = engine.execute(YoutubeDLRequest(emptyList()).addOption("--version"))
                val version = if (response.exitCode == 0) parse(response.out) else null
                version?.let { app.getSharedPreferences("extractor_status", Context.MODE_PRIVATE).edit().putString("effective_version", it).apply() }
                version
            }
        } catch (error: Throwable) {
            rethrowIfFatal(error)
            return null
        }
    }
}
