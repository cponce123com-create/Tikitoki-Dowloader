package com.tikitoki.downloader.data

import android.content.Context
import com.tikitoki.downloader.domain.DownloadQueueActions

/** Android adapter for the queue operations used by the downloads ViewModel. */
internal class AndroidDownloadQueueActions(context: Context) : DownloadQueueActions {
    private val appContext = context.applicationContext

    override suspend fun pause() {
        DownloadQueue.pause(appContext)
    }

    override suspend fun resume() {
        DownloadQueue.resume(appContext)
    }

    override suspend fun cancel(id: String) {
        DownloadQueue.cancel(appContext, id)
    }

    override suspend fun retryFailed() {
        DownloadQueue.retryFailed(appContext)
    }
}
