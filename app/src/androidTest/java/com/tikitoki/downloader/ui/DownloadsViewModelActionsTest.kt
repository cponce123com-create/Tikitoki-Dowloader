package com.tikitoki.downloader.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tikitoki.downloader.domain.DownloadQueueActions
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadsViewModelActionsTest {
    @Test
    fun forwardsQueueActionsToInjectedContract() {
        runBlocking {
            val calls = mutableListOf<String>()
            val actions =
                object : DownloadQueueActions {
                    override suspend fun pause() {
                        calls.add("pause")
                    }

                    override suspend fun resume() {
                        calls.add("resume")
                    }

                    override suspend fun cancel(id: String) {
                        calls.add("cancel:$id")
                    }

                    override suspend fun retryFailed() {
                        calls.add("retry")
                    }
                }
            val application = ApplicationProvider.getApplicationContext<Application>()
            val viewModel = DownloadsViewModel(application, actions)

            viewModel.pauseQueue()
            viewModel.resumeQueue()
            viewModel.cancelDownload("video-123")

            assertEquals(listOf("pause", "resume", "cancel:video-123"), calls)
        }
    }
}
