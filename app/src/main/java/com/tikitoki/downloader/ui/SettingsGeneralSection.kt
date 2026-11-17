package com.tikitoki.downloader.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tikitoki.downloader.R
import com.tikitoki.downloader.data.ExtractorUpdateWorker
import com.tikitoki.downloader.data.ProfileMonitor
import com.tikitoki.downloader.ui.TechBackground
import com.tikitoki.downloader.ui.TechBorder
import com.tikitoki.downloader.ui.TechMuted
import com.tikitoki.downloader.ui.TechPanel
import com.tikitoki.downloader.ui.TechPrimaryButton

@Composable
fun SettingsGeneralSection(
    monitorHours: Int,
    monitorPages: Int,
    folderDescription: String,
    hasCustomFolder: Boolean,
    message: String,
    onAbout: () -> Unit,
    onExportDiagnostic: () -> Unit,
    onMonitorHoursChange: (Int) -> Unit,
    onMonitorPagesChange: (Int) -> Unit,
    onUpdateExtractor: () -> Unit,
    onChooseFolder: () -> Unit,
    onResetFolder: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = TechPanel),
        border = BorderStroke(1.dp, TechBorder),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("General", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Monitoreo de perfiles", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    Text("Intervalo y profundidad de escaneo", color = TechMuted, style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onMonitorHoursChange((monitorHours - 1).coerceIn(1, 24)) }) { Text("−") }
                    Text(monitorHours.toString(), style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    OutlinedButton(onClick = { onMonitorHoursChange((monitorHours + 1).coerceIn(1, 24)) }) { Text("+") }
                    Text("h", color = TechMuted)
                    OutlinedButton(onClick = { onMonitorPagesChange((monitorPages - 50).coerceIn(1, 500)) }) { Text("−") }
                    Text(monitorPages.toString(), style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    OutlinedButton(onClick = { onMonitorPagesChange((monitorPages + 50).coerceIn(1, 500)) }) { Text("+") }
                    Text("pág", color = TechMuted)
                }
            }

            Text("Carpeta de descarga: $folderDescription", color = TechMuted, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onChooseFolder) { Text("Elegir carpeta") }
                if (hasCustomFolder) OutlinedButton(onClick = onResetFolder) { Text("Restablecer") }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onUpdateExtractor) { Text("Actualizar extractores") }
                OutlinedButton(onClick = onExportDiagnostic) { Text("Exportar diagnóstico") }
            }

            OutlinedButton(onClick = onAbout) { Text("Acerca de Baja.pe") }
        }
    }
}