package com.tikitoki.downloader.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tikitoki.downloader.data.DownloadDatabase
import com.tikitoki.downloader.data.DownloadQueue
import com.tikitoki.downloader.data.DownloadRecord
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Room is the source of truth; Compose only observes the download state. */
class DownloadsViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    val records = DownloadDatabase.get(application).downloads().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<DownloadRecord>())

    fun pause() = viewModelScope.launch { DownloadQueue.pause(appContext) }
    fun resume() = viewModelScope.launch { DownloadQueue.resume(appContext) }
    fun retryFailed() = viewModelScope.launch { DownloadQueue.retryFailed(appContext) }
}
