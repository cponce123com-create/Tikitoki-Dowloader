package com.tikitoki.downloader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tikitoki.downloader.data.DownloadSelection
import com.tikitoki.downloader.data.FormatChoice
import com.tikitoki.downloader.data.VideoEntry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadFormatSheetTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun playlistOffersBothTabsAndConfirmsOnlyCompatibleItems() {
        val first = VideoEntry("one", "https://www.youtube.com/watch?v=one", "Uno")
        val second = VideoEntry("two", "https://www.youtube.com/watch?v=two", "Dos")
        var confirmed = emptyList<DownloadSelection>()
        compose.setContent {
            DownloadFormatSheetContent(
                videos = listOf(first, second),
                saved = null,
                readFormats = { item ->
                    if (item.id == first.id) {
                        listOf(FormatChoice("bajamp3-128", "MP3", isAudio = true))
                    } else {
                        listOf(FormatChoice("b720+bestaudio", "HD (720p)", height = 720))
                    }
                },
                preview = {},
                onConfirm = { plan, _ -> confirmed = plan.downloads },
            )
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Revisados 2 de 2 elementos").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Video").performClick()
        compose.onNodeWithText("HD (720p)").assertIsDisplayed()
        compose.onNodeWithText("Audio").performClick()
        compose.onNodeWithText("MP3").assertIsDisplayed()
        compose.onNodeWithText("Se añadirán 1 de 2 elementos. Los demás seguirán seleccionados.").assertIsDisplayed()
        compose.onNodeWithText("Descargar").performClick()
        compose.runOnIdle {
            assertEquals(listOf(DownloadSelection(first, "bajamp3-128")), confirmed)
        }
    }
}
