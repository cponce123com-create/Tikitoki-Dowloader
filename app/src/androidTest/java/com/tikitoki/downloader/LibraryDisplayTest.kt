package com.tikitoki.downloader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tikitoki.downloader.data.DownloadDatabase
import com.tikitoki.downloader.data.DownloadRecord
import com.tikitoki.downloader.data.DownloadState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryDisplayTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val videoId = "library-test-video"
    private val audioId = "library-test-audio"

    @Before fun setUp() {
        compose.acceptFirstRunNoticeIfShown()
        runBlocking {
            val dao = DownloadDatabase.get(compose.activity).downloads()
            dao.upsert(
                DownloadRecord(
                    videoId = videoId,
                    videoUrl = "https://example.com/video",
                    title = "Video de prueba",
                    thumbnail = null,
                    formatId = "bestvideo+bestaudio",
                    status = DownloadState.COMPLETED,
                    savedUri = "file:///tmp/test-video.mp4",
                ),
            )
            dao.upsert(
                DownloadRecord(
                    videoId = audioId,
                    videoUrl = "https://example.com/audio",
                    title = "Audio de prueba",
                    thumbnail = null,
                    formatId = "bajamp3-128",
                    status = DownloadState.COMPLETED,
                    savedUri = "file:///tmp/test-audio.mp3",
                ),
            )
        }
    }

    @After fun tearDown() {
        runBlocking {
            val dao = DownloadDatabase.get(compose.activity).downloads()
            dao.deleteCompleted(videoId)
            dao.deleteCompleted(audioId)
        }
    }

    @Test fun completedVideoAndAudioAppearInTheirTabs() {
        compose.onNodeWithText("Biblioteca").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Video de prueba").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Video de prueba").assertIsDisplayed()
        compose.onNodeWithText("Música").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Audio de prueba").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Audio de prueba").assertIsDisplayed()
    }
}
