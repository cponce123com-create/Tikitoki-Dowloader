package com.tikitoki.downloader.domain

/** Separate queue identities for audio and video while recognizing pre-existing records. */
internal object DownloadIdentity {
    private const val AUDIO = "--baja-audio"
    private const val VIDEO = "--baja-video"
    fun key(sourceId: String, audio: Boolean) = sourceId + if (audio) AUDIO else VIDEO
    fun sourceId(key: String) = when {
        key.endsWith(AUDIO) -> key.removeSuffix(AUDIO)
        key.endsWith(VIDEO) -> key.removeSuffix(VIDEO)
        else -> key
    }
}
