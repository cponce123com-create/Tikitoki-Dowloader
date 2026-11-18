package com.tikitoki.downloader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tikitoki.downloader.data.DownloadRecord
import com.tikitoki.downloader.data.DownloadState
import com.tikitoki.downloader.data.LibraryVisibility
import com.tikitoki.downloader.data.VideoThumbnailResolver
import com.tikitoki.downloader.ui.TechBackground
import com.tikitoki.downloader.ui.TechHeader
import com.tikitoki.downloader.ui.TechMuted

@Suppress("ktlint:standard:function-naming")
@Composable
internal fun DownloadQueueScreen(
    records: List<DownloadRecord>,
    themeChoice: String,
    insets: PaddingValues,
    message: String,
    thumbnailResolver: VideoThumbnailResolver,
    downloadSpeeds: Map<String, String>,
    onSettings: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: (String) -> Unit,
) {
    val queue =
        records
            .filter { LibraryVisibility.inQueue(it.status) }
            .sortedBy {
                if (it.status == DownloadState.DOWNLOADING) {
                    0
                } else if (it.status == DownloadState.QUEUED) {
                    1
                } else {
                    2
                }
            }
    val active = queue.count { it.status == DownloadState.DOWNLOADING || it.status == DownloadState.QUEUED }
    val paused = queue.count { it.status == DownloadState.PAUSED }
    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    if (themeChoice ==
                        "Negro AMOLED"
                    ) {
                        Color.Black
                    } else {
                        TechBackground
                    },
                ).padding(insets)
                .consumeWindowInsets(insets),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TechHeader(onSettings = onSettings)
                Text("Cola de descargas", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (queue.isEmpty()) {
                    Text("No hay descargas en curso.", color = TechMuted)
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SummaryTile("Activas", queue.count { it.status == DownloadState.DOWNLOADING }, Modifier.weight(1f))
                        SummaryTile("En espera", queue.count { it.status == DownloadState.QUEUED }, Modifier.weight(1f))
                        SummaryTile("Pausadas", paused, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = active > 0, onClick = onPause) { Text("Pausar") }
                        Button(enabled = paused > 0, onClick = onResume) { Text("Continuar") }
                    }
                }
                if (queue.isNotEmpty() && message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
            }
        }
        items(queue, key = { it.videoId }) { record ->
            DownloadHistoryCard(record, resolver = thumbnailResolver, speed = downloadSpeeds[record.videoId], onDelete = {
            }, onCancel = { onCancel(record.videoId) }, onOpen = {})
        }
    }
}
