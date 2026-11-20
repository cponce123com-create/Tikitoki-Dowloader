package com.tikitoki.downloader.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tikitoki.downloader.data.AndroidDownloadQueueActions
import com.tikitoki.downloader.data.DownloadDatabase
import com.tikitoki.downloader.data.DownloadRecord
import com.tikitoki.downloader.domain.DownloadQueueActions
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Room is the source of truth; Compose only observes the download state. */
class DownloadsViewModel(
    application: Application,
    private val queueActions: DownloadQueueActions,
) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, AndroidDownloadQueueActions(application))

    private val dao = DownloadDatabase.get(application).downloads()

    val records =
        dao
            .observeAll()
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<DownloadRecord>())

    val queueRecords =
        dao
            .observeQueue()
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<DownloadRecord>())

    val completedRecords =
        dao
            .observeCompleted()
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<DownloadRecord>())

    val failedCount =
        dao
            .observeFailedCount()
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    suspend fun pauseQueue() = queueActions.pause()

    suspend fun resumeQueue() = queueActions.resume()

    suspend fun cancelDownload(id: String) = queueActions.cancel(id)

    fun pause() = viewModelScope.launch { queueActions.pause() }

    fun resume() = viewModelScope.launch { queueActions.resume() }

    fun retryFailed() = viewModelScope.launch { queueActions.retryFailed() }
}
