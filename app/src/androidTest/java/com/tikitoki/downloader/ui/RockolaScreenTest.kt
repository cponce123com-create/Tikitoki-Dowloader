package com.tikitoki.downloader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tikitoki.downloader.data.UniversalMediaSource
import com.tikitoki.downloader.data.VideoThumbnailResolver
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RockolaScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context by lazy { InstrumentationRegistry.getInstrumentation().targetContext }

    @Before
    fun clearRockolaPreferences() {
        context
            .getSharedPreferences("rockola", 0)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun tabsAreVisibleAndListsCanOpenAnEmptyFavoriteList() {
        compose.setContent {
            MaterialTheme {
                RockolaScreen(
                    source = UniversalMediaSource(context),
                    thumbnailResolver = VideoThumbnailResolver(context),
                    onDownload = {},
                    onOpenYouTube = {},
                )
            }
        }

        compose.onNodeWithText("Rockola").assertIsDisplayed()
        compose.onNodeWithText("Explorar").assertIsDisplayed()
        compose.onNodeWithText("En cola · 0").assertIsDisplayed()
        compose.onNodeWithText("Mis listas").performClick()
        compose.onNodeWithText("Favoritos").performClick()
        compose.onNodeWithText("Todavía no hay videos en esta lista.").assertIsDisplayed()
    }
}
