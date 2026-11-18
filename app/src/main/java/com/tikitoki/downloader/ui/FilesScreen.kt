package com.tikitoki.downloader.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tikitoki.downloader.data.DownloadState
import com.tikitoki.downloader.ui.TechBackground
import com.tikitoki.downloader.ui.TechBorder
import com.tikitoki.downloader.ui.TechHeader
import com.tikitoki.downloader.ui.TechMuted
import com.tikitoki.downloader.ui.TechPanel
import com.tikitoki.downloader.R

/**
 * FilesScreen - Screen for managing downloaded files and download folder.
 *
 * Handles:
 * - Displaying download folder information
 * - Opening download folder in file manager
 * - Showing count of saved files
 * - Error messages for folder access
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FilesScreen(
    themeChoice: String,
    folderDescription: String,
    downloadFolder: String?,
    message: String,
    onSettingsClick: () -> Unit,
    onChooseFolder: () -> Unit,
    onResetFolder: () -> Unit,
    onOpenFolder: () -> Unit,
    completedCount: Int,
    settings: android.content.SharedPreferences,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    if (themeChoice == "Negro AMOLED") {
                        Color.Black
                    } else {
                        TechBackground
                    },
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        TechHeader(onSettings = onSettingsClick)
        Text("Archivos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Administra el contenido guardado en tu dispositivo",
            color = TechMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                androidx.compose.material3.CardDefaults
                    .cardColors(containerColor = TechPanel),
            border = androidx.compose.foundation.BorderStroke(1.dp, TechBorder),
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Carpeta de descarga", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(folderDescription, color = TechMuted)
                OutlinedButton(onClick = onOpenFolder) { Text("Abrir carpeta de Baja.pe") }
            }
        }
        Text(
            "$completedCount archivos guardados",
            color = TechMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        if (message.startsWith("No se pudo abrir")) Text(message, color = TechMuted)
    }
}