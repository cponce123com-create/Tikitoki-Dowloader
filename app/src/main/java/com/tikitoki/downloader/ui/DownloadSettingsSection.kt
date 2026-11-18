package com.tikitoki.downloader.ui

import android.Manifest
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
import com.tikitoki.downloader.data.DownloadQueue
import com.tikitoki.downloader.data.DownloadSettings
import com.tikitoki.downloader.ui.TechBackground
import com.tikitoki.downloader.ui.TechBorder
import com.tikitoki.downloader.ui.TechMuted
import com.tikitoki.downloader.ui.TechPanel
import com.tikitoki.downloader.ui.TechPrimaryButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun DownloadSettingsSection(
    wifiParallel: Int,
    meteredParallel: Int,
    allowMetered: Boolean,
    networkSettingsBusy: Boolean,
    rateKiB: Int,
    showProgressNotice: Boolean,
    completionNotice: Boolean,
    onWifiChange: (Int) -> Unit,
    onMeteredChange: (Int) -> Unit,
    onAllowMeteredChange: (Boolean) -> Unit,
    onRateChange: (Int) -> Unit,
    onProgressNoticeChange: (Boolean) -> Unit,
    onCompletionNoticeChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = TechPanel),
        border = BorderStroke(1.dp, TechBorder),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Descargas y red", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Descargas simultáneas (Wi-Fi)", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    Text("Máximo de descargas paralelas", color = TechMuted, style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onWifiChange((wifiParallel - 1).coerceIn(1, 8)) }) { Text("−") }
                    Text(wifiParallel.toString(), style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    OutlinedButton(onClick = { onWifiChange((wifiParallel + 1).coerceIn(1, 8)) }) { Text("+") }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Descargas simultáneas (Datos)", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    Text("Máximo en conexión medida", color = TechMuted, style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onMeteredChange((meteredParallel - 1).coerceIn(1, 8)) }) { Text("−") }
                    Text(meteredParallel.toString(), style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    OutlinedButton(onClick = { onMeteredChange((meteredParallel + 1).coerceIn(1, 8)) }) { Text("+") }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Permitir datos móviles", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    Text("Descargar en conexiones medidas", color = TechMuted, style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = allowMetered,
                    onCheckedChange = { allowed ->
                        if (!networkSettingsBusy) onAllowMeteredChange(allowed)
                    },
                    enabled = !networkSettingsBusy,
                )
            }

            if (networkSettingsBusy) CircularProgressIndicator()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Límite de velocidad (KiB/s)", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    Text("0 = sin límite", color = TechMuted, style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onRateChange((rateKiB - 1024).coerceIn(0, 10240)) }) { Text("−") }
                    Text(rateKiB.toString(), style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    OutlinedButton(onClick = { onRateChange((rateKiB + 1024).coerceIn(0, 10240)) }) { Text("+") }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Notificar progreso detallado", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    Text("Notificaciones por fragmento", color = TechMuted, style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = showProgressNotice,
                    onCheckedChange = onProgressNoticeChange,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Notificar al completar", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    Text("Notificación cuando termine la descarga", color = TechMuted, style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = completionNotice,
                    onCheckedChange = onCompletionNoticeChange,
                )
            }
        }
    }
}