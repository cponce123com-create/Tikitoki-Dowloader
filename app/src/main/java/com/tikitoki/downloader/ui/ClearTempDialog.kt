package com.tikitoki.downloader.ui

import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.coroutineScope
import com.tikitoki.downloader.data.DownloadQueue
import kotlinx.coroutines.launch

@Composable
fun ClearTempDialog(
    context: Context,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Limpiar archivos temporales") },
        text = {
            Text(
                "Se borrarán solo los archivos temporales de Baja.pe. Los fragmentos guardados para reanudar podrían perderse. Los videos descargados se conservan.",
            )
        },
        confirmButton = {
            Button(onClick = {
                onDismissRequest()
                scope.launch {
                    try {
                        val deleted = DownloadQueue.clearTemporary(context)
                        onConfirm(deleted)
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        com.tikitoki.downloader.data.DiagnosticHistory.record(context, e.message)
                        // Error handled by caller
                    }
                }
            }) { Text("Limpiar") }
        },
        dismissButton = { OutlinedButton(onClick = onDismissRequest) { Text("Cancelar") } },
    )
}