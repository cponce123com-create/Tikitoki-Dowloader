package com.tikitoki.downloader.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun PlayerChoiceDialog(
    onDismissRequest: () -> Unit,
    onChooseVideos: () -> Unit,
    onChooseMusic: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Reproductor") },
        text = { Text("Elige qué quieres reproducir.") },
        confirmButton = {
            Button(onClick = { onChooseVideos(); onDismissRequest() }) { Text("Videos") }
        },
        dismissButton = {
            OutlinedButton(onClick = { onChooseMusic(); onDismissRequest() }) { Text("Música") }
        }
    )
}