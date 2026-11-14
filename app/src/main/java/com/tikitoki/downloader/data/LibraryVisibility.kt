package com.tikitoki.downloader.data

/** Keep the live queue separate from the local playback library. */
object LibraryVisibility {
    fun inQueue(status: String): Boolean = status in setOf(DownloadState.DOWNLOADING, DownloadState.QUEUED, DownloadState.PAUSED)
    fun inLibrary(status: String, savedUri: String?): Boolean = status == DownloadState.COMPLETED && !savedUri.isNullOrBlank()
}
