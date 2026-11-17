package com.tikitoki.downloader.domain

/** Operations requested by the download UI; implementations manage persistence and workers. */
interface DownloadQueueActions {
    suspend fun pause()

    suspend fun resume()

    suspend fun cancel(id: String)

    suspend fun retryFailed()
}
