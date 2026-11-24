package com.tikitoki.downloader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeAndGeneralSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun linkInputForwardsTextAndActions() {
        var input = ""
        var pastes = 0
        var analyses = 0
        compose.setContent {
            Column {
                HomeLinkInput(
                    selectedPlatform = "Auto",
                    url = "https://youtube.com/watch?v=abc",
                    loading = false,
                    loadingMore = false,
                    onPlatformChange = {},
                    onUrlChange = { input = it },
                    onKeyboardDone = {},
                    onPaste = { pastes++ },
                    onAnalyze = { analyses++ },
                )
            }
        }

        compose.onNode(hasSetTextAction()).performTextReplacement("https://youtube.com/watch?v=xyz")
        compose.onNodeWithText("Pegar").performClick()
        compose.onNodeWithText("Analizar").performClick()
        assertEquals("https://youtube.com/watch?v=xyz", input)
        assertEquals(1, pastes)
        assertEquals(1, analyses)
    }

    @Test
    fun photoControlsOnlyOpenOnRequest() {
        compose.setContent {
            PhotoToolsEntry("https://www.facebook.com/photo/?fbid=123")
        }

        compose.onNodeWithText("Descargar fotos").assertDoesNotExist()
        compose.onNodeWithText("Importar sesión").assertDoesNotExist()
        compose.onNodeWithText("Opciones de fotos").assertIsDisplayed().performClick()
        compose.onNodeWithText("Descargar fotos").assertIsDisplayed()
        compose.onNodeWithText("Gestionar cuenta de Facebook").performScrollTo().performClick()
        compose.onNodeWithText("Cuenta de Facebook").assertIsDisplayed()
        compose.onNodeWithText("Volver a fotos").performClick()
        compose.onNodeWithText("Descargar fotos").assertIsDisplayed()
    }

    @Test
    fun generalSettingsForwardSelectedActions() {
        var interval = 0
        var exports = 0
        var folderSelections = 0
        compose.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SettingsGeneralSection(
                    monitorHours = 6,
                    monitorPages = 10,
                    folderDescription = "Descargas/TikitokiDownloader",
                    hasCustomFolder = false,
                    message = "",
                    onAbout = {},
                    onExportDiagnostic = { exports++ },
                    onMonitorHoursChange = { interval = it },
                    onMonitorPagesChange = {},
                    onUpdateExtractor = {},
                    onChooseFolder = { folderSelections++ },
                    onResetFolder = {},
                )
            }
        }

        compose.onNodeWithText("Exportar diagnóstico").performClick()
        compose.onNodeWithText("12 h").performScrollTo().performClick()
        compose.onNodeWithText("📁 Elegir carpeta o memoria SD").performScrollTo().performClick()
        assertEquals(1, exports)
        assertEquals(12, interval)
        assertEquals(1, folderSelections)
    }
}
