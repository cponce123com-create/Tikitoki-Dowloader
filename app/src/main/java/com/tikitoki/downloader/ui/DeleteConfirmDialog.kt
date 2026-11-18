package com.tikitoki.downloader.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.coroutineScope
import com.tikitoki.downloader.data.DownloadQueue
import com.tikitoki.downloader.data.DeleteCompletedResult
import kotlinx.coroutines.launch

@Composable
fun DeleteConfirmDialog(
    item: com.tikitoki.downloader.data.DownloadRecord,
    onDismissRequest: () -> Unit,
    onConfirm: (DeleteCompletedResult) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Eliminar video") },
        text = { Text("¿Eliminar este video del dispositivo y del historial de Baja.pe? Esta acción no se puede deshacer.") },
        confirmButton = {
            Button(onClick = {
                onDismissRequest()
                scope.launch {
                    try {
                        val result = DownloadQueue.deleteCompleted(item.videoId)
                        onConfirm(result)
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        com.tikitoki.downloader.data.DiagnosticHistory.record(item.videoId, e.message)
                    }
                }
            }) { Text("Eliminar") }
        },
        dismissButton = { OutlinedButton(onClick = onDismissRequest) { Text("Cancelar") } },
    )
}