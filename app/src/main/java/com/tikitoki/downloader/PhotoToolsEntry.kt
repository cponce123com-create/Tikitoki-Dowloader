package com.tikitoki.downloader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tikitoki.downloader.ui.TechBackground

@Composable
@Suppress("ktlint:standard:function-naming")
internal fun PhotoToolsEntry(url: String) {
    var showPhotos by rememberSaveable { mutableStateOf(false) }
    var manageFacebook by rememberSaveable { mutableStateOf(false) }

    TextButton(onClick = { showPhotos = true }) { Text("Opciones de fotos") }
    if (showPhotos) {
        PhotoToolsSheet(onDismiss = {
            showPhotos = false
            manageFacebook = false
        }) {
            if (manageFacebook) {
                TextButton(onClick = { manageFacebook = false }) { Text("Volver a fotos") }
                FacebookAccountPanel()
            } else {
                Text("Fotos", style = MaterialTheme.typography.titleLarge)
                PhotoDownloadPanel(url, onManageFacebook = { manageFacebook = true })
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
internal fun FacebookAccountEntry() {
    var showAccount by rememberSaveable { mutableStateOf(false) }

    TextButton(onClick = { showAccount = true }) { Text("Gestionar cuenta de Facebook") }
    if (showAccount) {
        PhotoToolsSheet(onDismiss = { showAccount = false }) {
            FacebookAccountPanel()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("ktlint:standard:function-naming")
private fun PhotoToolsSheet(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = TechBackground,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}
