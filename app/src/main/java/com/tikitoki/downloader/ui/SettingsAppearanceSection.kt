package com.tikitoki.downloader.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tikitoki.downloader.R
import com.tikitoki.downloader.ui.TechBackground
import com.tikitoki.downloader.ui.TechBorder
import com.tikitoki.downloader.ui.TechMuted
import com.tikitoki.downloader.ui.TechPanel
import com.tikitoki.downloader.ui.TechPink

@Composable
fun SettingsAppearanceSection(
    themeChoice: String,
    preferredQuality: String,
    onThemeChange: (String) -> Unit,
    onQualityChange: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = TechPanel),
        border = BorderStroke(1.dp, TechBorder),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Apariencia", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Text("Tema", style = MaterialTheme.typography.bodyMedium, color = Color.White)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = themeChoice == "Oscuro",
                    onClick = { onThemeChange("Oscuro") },
                    label = { Text("Oscuro") },
                )
                FilterChip(
                    selected = themeChoice == "Claro",
                    onClick = { onThemeChange("Claro") },
                    label = { Text("Claro") },
                )
                FilterChip(
                    selected = themeChoice == "Negro AMOLED",
                    onClick = { onThemeChange("Negro AMOLED") },
                    label = { Text("Negro AMOLED") },
                )
            }

            Text("Calidad preferida", style = MaterialTheme.typography.bodyMedium, color = Color.White)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = preferredQuality == "Máxima",
                    onClick = { onQualityChange("Máxima") },
                    label = { Text("Máxima") },
                )
                FilterChip(
                    selected = preferredQuality == "720p",
                    onClick = { onQualityChange("720p") },
                    label = { Text("720p") },
                )
                FilterChip(
                    selected = preferredQuality == "480p",
                    onClick = { onQualityChange("480p") },
                    label = { Text("480p") },
                )
            }
        }
    }
}