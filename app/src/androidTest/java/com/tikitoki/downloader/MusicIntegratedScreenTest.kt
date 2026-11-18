package com.tikitoki.downloader

import android.graphics.Bitmap
import android.content.ContentValues
import android.provider.MediaStore
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MusicIntegratedScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun acknowledgeNotice() { compose.acceptFirstRunNoticeIfShown() }

    @Test fun musicUsesAppNavigationAndFunctionalControls() {
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Biblioteca").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Biblioteca").performClick()
        compose.onNodeWithText("Música").performClick()
        compose.onNodeWithText("Reproductor").performClick()
        compose.onNodeWithText("Lista de reproducción").assertIsDisplayed()
        compose.onNodeWithContentDescription("Aleatorio").assertIsDisplayed()
        compose.onNodeWithContentDescription("Anterior").assertIsDisplayed()
        compose.onNodeWithContentDescription("Reproducir").assertIsDisplayed()
        compose.onNodeWithContentDescription("Siguiente").assertIsDisplayed()
        compose.onNodeWithContentDescription("Repetir").assertIsDisplayed()
        compose.onNodeWithText("Biblioteca").assertIsDisplayed()

        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val values =
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "music-layout.png")
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/")
                }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
            context.contentResolver.openOutputStream(uri)!!.use {
                compose
                    .onRoot()
                    .captureToImage()
                    .asAndroidBitmap()
                    .compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
