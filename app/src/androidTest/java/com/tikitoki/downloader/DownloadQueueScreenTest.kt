package com.tikitoki.downloader

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tikitoki.downloader.data.DownloadRecord
import com.tikitoki.downloader.data.DownloadState
import com.tikitoki.downloader.data.VideoThumbnailResolver
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadQueueScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun queueActionsCallTheirCallbacks() {
        var pauseCalls = 0
        var resumeCalls = 0
        var cancelledId: String? = null
        val records =
            listOf(
                DownloadRecord(
                    videoId = "test-active",
                    videoUrl = "https://www.youtube.com/watch?v=active",
                    title = "Video activo",
                    thumbnail = "file:///missing-thumbnail.jpg",
                    formatId = "best",
                    status = DownloadState.DOWNLOADING,
                    progress = 35,
                ),
                DownloadRecord(
                    videoId = "test-paused",
                    videoUrl = "https://www.youtube.com/watch?v=paused",
                    title = "Video pausado",
                    thumbnail = "file:///missing-thumbnail.jpg",
                    formatId = "best",
                    status = DownloadState.PAUSED,
                ),
            )

        compose.setContent {
            val context = androidx.compose.ui.platform.LocalContext.current
            DownloadQueueScreen(
                records = records,
                themeChoice = "Oscuro",
                insets = PaddingValues(0.dp),
                message = "",
                thumbnailResolver = VideoThumbnailResolver(context.applicationContext),
                downloadSpeeds = emptyMap(),
                onSettings = {},
                onPause = { pauseCalls++ },
                onResume = { resumeCalls++ },
                onCancel = { cancelledId = it },
            )
        }

        compose.onNodeWithText("Pausar").performClick()
        compose.onNodeWithText("Continuar").performClick()
        compose.onNodeWithContentDescription("Cancelar descarga de Video activo").performClick()
        assertEquals(1, pauseCalls)
        assertEquals(1, resumeCalls)
        assertEquals("test-active", cancelledId)
    }
}
