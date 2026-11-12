package com.tikitoki.downloader.domain

import android.content.Intent

/** Only trusted, normalized URLs may cross the exported Activity boundary. */
object IncomingIntentReader {
    fun read(intent: Intent?): String {
        val candidate = when {
            intent?.action == Intent.ACTION_SEND && intent.type == "text/plain" ->
                intent.getStringExtra(Intent.EXTRA_TEXT)
            intent?.action == Intent.ACTION_VIEW && intent.data?.scheme == "https" ->
                intent.dataString
            else -> null
        }
        return IncomingLinkValidator.validate(candidate)?.url.orEmpty()
    }
}
