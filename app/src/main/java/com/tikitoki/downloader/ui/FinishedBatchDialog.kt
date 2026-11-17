package com.tikitoki.downloader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Modifier
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tikitoki.downloader.R
import com.tikitoki.downloader.data.DeleteCompletedResult
import com.tikitoki.downloader.data.DownloadDatabase

@Composable
fun FinishedBatchDialog(
    batch: com.tikitoki.downloader.data.DownloadQueue.BatchResult,
    onDismiss: () -> Unit,
    dao: DownloadDatabase.DownloadsDao
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Descargas terminadas") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SummaryTile("Total", batch.total, Modifier.weight(1f))
                    SummaryTile("Éxitos", batch.completed, Modifier.weight(1f))
                    SummaryTile("Errores", batch.failed, Modifier.weight(1f))
                }
                if (batch.cancelled > 0) {
                    Text("Cancelados: ${batch.cancelled}", color = TechMuted, style = MaterialTheme.typography.bodySmall)
                }
                if (batch.duplicates > 0) {
                    Text("Duplicados omitidos: ${batch.duplicates}", color = TechMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { Button(onClick = { scope.launch { dao.acknowledgeBatch(batch.batchId) }; onDismiss() }) { Text("Aceptar") } },
    )
}

@Composable
private fun SummaryTile(label: String, value: Int, modifier: Modifier) {
    Card(
        modifier = modifier,
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = TechPanel),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TechMuted)
            Text(value.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }
    }
}