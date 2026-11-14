package com.tikitoki.downloader

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MusicIntegratedScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun musicUsesAppNavigationAndFunctionalControls() {
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Biblioteca").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Biblioteca").performClick()
        compose.onNodeWithText("Mi música").performClick()
        compose.onNodeWithText("Lista de reproducción").assertIsDisplayed()
        compose.onNodeWithContentDescription("Aleatorio").assertIsDisplayed()
        compose.onNodeWithContentDescription("Anterior").assertIsDisplayed()
        compose.onNodeWithContentDescription("Reproducir").assertIsDisplayed()
        compose.onNodeWithContentDescription("Siguiente").assertIsDisplayed()
        compose.onNodeWithContentDescription("Repetir").assertIsDisplayed()
        compose.onNodeWithText("Biblioteca").assertIsDisplayed()

        val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!
        File(dir, "music-layout.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
