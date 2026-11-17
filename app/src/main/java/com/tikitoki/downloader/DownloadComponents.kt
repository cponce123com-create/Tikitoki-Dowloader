package com.tikitoki.downloader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tikitoki.downloader.data.DownloadRecord
import com.tikitoki.downloader.data.DownloadState
import com.tikitoki.downloader.data.VideoEntry
import com.tikitoki.downloader.data.VideoThumbnailResolver
import com.tikitoki.downloader.domain.DownloadIdentity
import com.tikitoki.downloader.domain.ThumbnailUrls
import com.tikitoki.downloader.ui.TechBorder
import com.tikitoki.downloader.ui.TechMuted
import com.tikitoki.downloader.ui.TechPanel
import com.tikitoki.downloader.ui.TechPurple

@Suppress("ktlint:standard:function-naming")
@Composable
internal fun SummaryTile(
    label: String,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.background(TechPanel, RoundedCornerShape(12.dp)).padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(count.toString(), fontWeight = FontWeight.Bold, color = Color.White, style = MaterialTheme.typography.titleMedium)
        Text(label, color = TechMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
internal fun DownloadHistoryCard(
    record: DownloadRecord,
    resolver: VideoThumbnailResolver,
    speed: String? = null,
    onDelete: () -> Unit,
    onCancel: () -> Unit = {},
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            androidx.compose.material3.CardDefaults
                .cardColors(containerColor = TechPanel),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, TechBorder),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VideoThumbnail(
                VideoEntry(DownloadIdentity.sourceId(record.videoId), record.videoUrl, record.title, record.thumbnail),
                resolver,
                Modifier.width(86.dp).aspectRatio(16f / 9f),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    record.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val status =
                    when (record.status) {
                        DownloadState.QUEUED -> "En espera"
                        DownloadState.DOWNLOADING ->
                            when {
                                record.progress <= 0 -> "Preparando descarga…"
                                record.progress >= 100 -> "Procesando archivo…"
                                else -> "Descargando ${record.progress}%"
                            }
                        DownloadState.PAUSED -> "Pausada"
                        DownloadState.COMPLETED -> "Completada"
                        DownloadState.FAILED -> "Error"
                        DownloadState.CANCELLED -> "Cancelada"
                        else -> record.status
                    }
                Text(status, color = TechMuted, style = MaterialTheme.typography.labelSmall)
                if (record.status == DownloadState.DOWNLOADING &&
                    speed != null
                ) {
                    Text(stringResource(R.string.download_speed, speed), color = TechMuted, style = MaterialTheme.typography.labelSmall)
                }
                if (record.status == DownloadState.DOWNLOADING) {
                    if (record.progress in
                        1..99
                    ) {
                        LinearProgressIndicator(
                            progress = { record.progress / 100f },
                            modifier = Modifier.fillMaxWidth().height(5.dp),
                            color = TechPurple,
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(5.dp), color = TechPurple)
                    }
                }
                if (!record.error.isNullOrBlank()) {
                    Text(
                        "No se pudo completar. Revisa la conexión.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (record.status == DownloadState.COMPLETED && record.savedUri != null) {
                    Row {
                        OutlinedButton(onClick = onOpen) { Text("Ver") }
                        OutlinedButton(onClick = onDelete) { Text("Eliminar") }
                    }
                }
            }
            if (record.status in listOf(DownloadState.QUEUED, DownloadState.DOWNLOADING, DownloadState.PAUSED)) {
                IconButton(onClick = onCancel, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = "Cancelar descarga de ${record.title}", tint = TechMuted)
                }
            }
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
internal fun VideoThumbnail(
    video: VideoEntry,
    resolver: VideoThumbnailResolver,
    modifier: Modifier = Modifier,
    deferLookup: Boolean = false,
) {
    val directThumbnail = video.thumbnail?.takeIf { it.isNotBlank() } ?: ThumbnailUrls.youtube(video.id, video.url)

    // The producer assigns value; this Compose lint check does not recognize it with the current Kotlin version.
    @android.annotation.SuppressLint("ProduceStateDoesNotAssignValue")
    val resolved by produceState<String?>(initialValue = directThumbnail, key1 = video.url, key2 = directThumbnail, key3 = deferLookup) {
        value = directThumbnail ?: if (deferLookup) null else resolver.resolve(video)
    }
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (!resolved.isNullOrBlank()) {
                coil.compose.SubcomposeAsyncImage(
                    model =
                        coil.request.ImageRequest
                            .Builder(LocalContext.current)
                            .data(resolved)
                            .crossfade(true)
                            .apply { if (video.url.contains("tiktok.com")) addHeader("Referer", "https://www.tiktok.com/") }
                            .build(),
                    contentDescription = "Miniatura del video: ${video.title}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    loading = {
                        CircularProgressIndicator(
                            modifier = Modifier.size(26.dp),
                            color = TechPurple,
                            strokeWidth = 2.dp,
                        )
                    },
                    error = {
                        val fallback = ThumbnailUrls.youtube(video.id, video.url)
                        if (fallback != null && fallback != resolved) {
                            coil.compose.SubcomposeAsyncImage(
                                model = fallback,
                                contentDescription = "Miniatura del video: ${video.title}",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                error = {
                                    Text(
                                        "▶",
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                            )
                        } else {
                            Text(
                                "▶",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            } else {
                Text(
                    "▶",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
