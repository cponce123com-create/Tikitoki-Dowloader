package com.tikitoki.downloader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun acknowledgeNotice() { compose.acceptFirstRunNoticeIfShown() }

    @Test fun downloadActionsHaveEqualWidthAndLibrarySeparatesMedia() {
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Inicio").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Inicio").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Pegar").fetchSemanticsNodes().isNotEmpty() }
        val paste = compose.onNodeWithText("Pegar").fetchSemanticsNode().boundsInRoot.width
        val analyze = compose.onNodeWithText("Analizar").fetchSemanticsNode().boundsInRoot.width
        assertEquals(paste, analyze, 2f)
        compose.onNodeWithText("Biblioteca").performClick()
        compose.onNodeWithText("Videos").assertIsDisplayed()
        compose.onNodeWithText("Música").performClick()
        compose.onNodeWithText("Reproductor").assertIsDisplayed()
    }

    @Test fun toolsPlayerOffersVideoAndMusic() {
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Herramientas").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Herramientas").performClick()
        compose.onNodeWithText("Abrir reproductor").assertIsDisplayed().performClick()
        compose.onNodeWithText("Videos").assertIsDisplayed()
        compose.onNodeWithText("Música").assertIsDisplayed()
    }
}
