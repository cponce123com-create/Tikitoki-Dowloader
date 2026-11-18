package com.tikitoki.downloader.ui

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tikitoki.downloader.data.DownloadQueue
import com.tikitoki.downloader.data.DownloadRecord
import com.tikitoki.downloader.data.DownloadState
import com.tikitoki.downloader.data.VideoThumbnailResolver
import com.tikitoki.downloader.ui.TechBackground
import com.tikitoki.downloader.ui.TechBorder
import com.tikitoki.downloader.ui.TechGradient
import com.tikitoki.downloader.ui.TechHeader
import com.tikitoki.downloader.ui.TechMuted
import com.tikitoki.downloader.ui.TechPanel
import com.tikitoki.downloader.ui.TechPink
import com.tikitoki.downloader.ui.TechPrimaryButton
import com.tikitoki.downloader.ui.VideoThumbnail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * DownloadQueueScreen - Screen for managing the download queue.
 *
 * Handles:
 * - Displaying active, pending, paused, and completed downloads
 * - Pause/resume queue operations
 * - Cancel individual downloads
 * - Progress indicators with download speeds
 * - Retry failed downloads
 * - Navigate to settings
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DownloadQueueScreen(
    records: List<DownloadRecord>,
    themeChoice: String,
    insets: androidx.compose.foundation.layout.WindowInsets,
    message: String,
    thumbnailResolver: VideoThumbnailResolver,
    downloadSpeeds: Map<String, String>,
    onSettings: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val showRetryAll = records.any { it.status == DownloadState.FAILED }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (themeChoice == "Negro AMOLED") Color.Black else TechBackground
            )
            .windowInsetsPadding(insets)
            .consumeWindowInsets(insets)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        TechHeader(onSettings = onSettings)

        if (records.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Default.Download.imageVector, contentDescription = null, tint = TechMuted, modifier = Modifier.size(64.dp))
                Text("La cola está vacía", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Añade videos desde Buscar o analiza un perfil.", color = TechMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                TechPrimaryButton("Ir a Buscar", icon = Icons.Default.Search.imageVector) {
                    // Navigation handled by parent
                }
            }
            if (message.isNotBlank()) Text(message, color = TechMuted, style = MaterialTheme.typography.bodySmall)
            return@Column
        }

        val activeRecords = records.filter { it.status != DownloadState.COMPLETED }
        val completedRecords = records.filter { it.status == DownloadState.COMPLETED }

        if (activeRecords.isNotEmpty()) {
            Text("En cola", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                activeRecords.forEach { record ->
                    DownloadQueueItem(
                        record = record,
                        themeChoice = themeChoice,
                        thumbnailResolver = thumbnailResolver,
                        speed = downloadSpeeds[record.videoId] ?: "",
                        onPause = { scope.launch { DownloadQueue.pauseQueue(context) } },
                        onResume = { scope.launch { DownloadQueue.resumeQueue(context) } },
                        onCancel = { onCancel(record.videoId) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (completedRecords.isNotEmpty()) {
            Text("Completados", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                completedRecords.take(10).forEach { record ->
                    CompletedQueueItem(
                        record = record,
                        themeChoice = themeChoice,
                        thumbnailResolver = thumbnailResolver,
                    )
                }
            }
        }

        if (message.isNotBlank()) {
            Text(message, color = TechMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
        }

        // Bottom controls for queue
        if (activeRecords.isNotEmpty() || showRetryAll) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (records.any { it.status == DownloadState.DOWNLOADING }) {
                        TechPrimaryButton("Pausar cola", icon = Icons.Default.Pause.imageVector, modifier = Modifier.weight(1f), onClick = onPause)
                    }
                    if (records.any { it.status == DownloadState.PAUSED }) {
                        TechPrimaryButton("Reanudar cola", icon = Icons.Default.PlayArrow.imageVector, modifier = Modifier.weight(1f), onClick = onResume)
                    }
                    if (showRetryAll) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = { scope.launch { DownloadQueue.retryFailed(context) } },
                        ) {
                            Icon(Icons.Default.Refresh.imageVector, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Reintentar fallidos")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadQueueItem(
    record: DownloadRecord,
    themeChoice: String,
    thumbnailResolver: VideoThumbnailResolver,
    speed: String,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
) {
    val platform = record.formatId.takeIf { it.contains(":") }?.substringBefore(":")
    val isTikTok = platform == "tiktok"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = TechPanel),
        border = androidx.compose.foundation.BorderStroke(1.dp, TechBorder),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .width(96.dp)
                        .aspectRatio(if (isTikTok) 9f / 16f else 16f / 9f)
                ) {
                    VideoThumbnail(record, thumbnailResolver, Modifier.fillMaxSize())
                    when (record.status) {
                        DownloadState.DOWNLOADING -> {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                                color = TechPink,
                                strokeWidth = 3.dp,
                            )
                        }
                        DownloadState.PAUSED -> {
                            Icon(Icons.Default.PauseCircle.imageVector, contentDescription = "Pausado", tint = TechPink, modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .align(Alignment.Center)
                                .size(48.dp))
                        }
                        DownloadState.FAILED -> {
                            Icon(Icons.Default.Error.imageVector, contentDescription = "Error", tint = MaterialTheme.colorScheme.error, modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .align(Alignment.Center)
                                .size(48.dp))
                        }
                        DownloadState.PENDING -> {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                                color = TechMuted,
                                strokeWidth = 3.dp,
                            )
                        }
                        DownloadState.COMPLETED -> {
                            Icon(Icons.Default.CheckCircle.imageVector, contentDescription = "Completado", tint = Color(0xFF43E6A1), modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .align(Alignment.Center)
                                .size(48.dp))
                        }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(record.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(record.formatId, color = TechMuted, style = MaterialTheme.typography.labelSmall)
                    if (speed.isNotBlank()) {
                        Text(speed, color = TechPink, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    when (record.status) {
                        DownloadState.DOWNLOADING -> {
                            IconButton(onClick = onPause) {
                                Icon(Icons.Default.Pause.imageVector, contentDescription = "Pausar", tint = TechPink)
                            }
                        }
                        DownloadState.PAUSED -> {
                            IconButton(onClick = onResume) {
                                Icon(Icons.Default.PlayArrow.imageVector, contentDescription = "Reanudar", tint = TechPink)
                            }
                        }
                        DownloadState.FAILED -> {
                            OutlinedButton(onClick = onResume) { Text("Reintentar") }
                        }
                    }
                    if (record.status != DownloadState.COMPLETED) {
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Delete.imageVector, contentDescription = "Cancelar", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            if (record.status == DownloadState.DOWNLOADING || record.status == DownloadState.PAUSED) {
                val progress = record.progress.coerceIn(0f, 1f)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ProgressIndicatorDefaults.LinearProgressIndicatorColors(
                            indicatorColor = TechPink,
                            trackColor = TechBorder,
                        ),
                    )
                    Text(
                        "${(progress * 100).roundToInt()}%",
                        color = TechMuted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun CompletedQueueItem(
    record: DownloadRecord,
    themeChoice: String,
    thumbnailResolver: VideoThumbnailResolver,
) {
    val platform = record.formatId.takeIf { it.contains(":") }?.substringBefore(":")
    val isTikTok = platform == "tiktok"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = TechPanel),
        border = androidx.compose.foundation.BorderStroke(1.dp, TechBorder),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(96.dp)
                    .aspectRatio(if (isTikTok) 9f / 16f else 16f / 9f)
            ) {
                VideoThumbnail(record, thumbnailResolver, Modifier.fillMaxSize())
                Icon(Icons.Default.CheckCircle.imageVector, contentDescription = "Completado", tint = Color(0xFF43E6A1), modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .align(Alignment.Center)
                    .size(48.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(record.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(record.formatId, color = TechMuted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}