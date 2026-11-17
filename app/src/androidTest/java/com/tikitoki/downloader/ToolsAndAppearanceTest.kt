package com.tikitoki.downloader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tikitoki.downloader.data.ProfileAvatarResolver
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ToolsAndAppearanceTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun toolsButtonsCallTheirCallbacks() {
        var retries = 0
        var clears = 0
        var playerOpens = 0
        var follows = 0

        compose.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ToolsScreen(
                    monitored = emptyList(),
                    failedCount = 2,
                    currentUrl = "",
                    profileImage = null,
                    avatarResolver = ProfileAvatarResolver(),
                    message = "",
                    onRetryFailed = { retries++ },
                    onClearErrors = { clears++ },
                    onOpenPlayer = { playerOpens++ },
                    onFollowProfile = { follows++ },
                    onUnfollowProfile = {},
                )
            }
        }

        compose.onNodeWithText("Reintentar").performClick()
        compose.onNodeWithText("Limpiar errores").performClick()
        compose.onNodeWithText("Abrir reproductor").performScrollTo().performClick()
        compose.onNodeWithText("Seguir perfil del enlace actual").performScrollTo().performClick()
        assertEquals(1, retries)
        assertEquals(1, clears)
        assertEquals(1, playerOpens)
        assertEquals(1, follows)
    }

    @Test
    fun appearanceOptionsReturnStoredValues() {
        var theme: String? = null
        var quality: String? = null

        compose.setContent {
            Column {
                SettingsAppearanceSection(
                    themeChoice = "Oscuro",
                    preferredQuality = "Máxima",
                    onThemeChange = { theme = it },
                    onQualityChange = { quality = it },
                )
            }
        }

        compose.onNodeWithText("Negro AMOLED").performClick()
        compose.onNodeWithText("720p").performClick()
        assertEquals("Negro AMOLED", theme)
        assertEquals("720p", quality)
    }
}
