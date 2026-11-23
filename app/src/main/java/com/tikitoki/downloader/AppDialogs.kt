package com.tikitoki.downloader

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tikitoki.downloader.data.BatchSummary
import com.tikitoki.downloader.data.DiagnosticHistory
import com.tikitoki.downloader.data.DownloadRecord
import com.tikitoki.downloader.data.DownloadSelection
import com.tikitoki.downloader.data.TikTokPhotoSource
import com.tikitoki.downloader.data.TikTokVideoSource
import com.tikitoki.downloader.data.UniversalMediaSource
import com.tikitoki.downloader.data.VideoEntry
import com.tikitoki.downloader.data.VideoThumbnailResolver
import com.tikitoki.downloader.data.rethrowIfFatal
import com.tikitoki.downloader.domain.MediaLinkParser
import com.tikitoki.downloader.domain.MediaPlatform
import com.tikitoki.downloader.ui.TechBackground
import com.tikitoki.downloader.ui.TechMuted

internal const val PORTRAIT_VIDEO_RATIO = 9f / 16f
internal const val LANDSCAPE_VIDEO_RATIO = 16f / 9f

@OptIn(ExperimentalMaterial3Api::class)
@Suppress(
    "ktlint:standard:function-naming",
    "LongMethod",
    "LongParameterList",
    "TooGenericExceptionCaught",
)
@Composable
internal fun AppDialogs(
    context: Context,
    settings: SharedPreferences,
    showAbout: Boolean,
    onDismissAbout: () -> Unit,
    onOpenSources: () -> Unit,
    showPlayerChoice: Boolean,
    onDismissPlayerChoice: () -> Unit,
    onChooseVideos: () -> Unit,
    onChooseMusic: () -> Unit,
    qualityVideos: List<VideoEntry>,
    source: TikTokVideoSource,
    universalSource: UniversalMediaSource,
    thumbnailResolver: VideoThumbnailResolver,
    onDismissFormatSheet: () -> Unit,
    onSaveFormat: (id: String, label: String, height: Int?) -> Unit,
    onRequestDownload: (List<DownloadSelection>) -> Unit,
    finishedBatch: BatchSummary?,
    onAcknowledgeBatch: (String) -> Unit,
    showClearTemp: Boolean,
    onDismissClearTemp: () -> Unit,
    onClearTemporary: () -> Unit,
    deleteCandidate: DownloadRecord?,
    onDismissDelete: () -> Unit,
    onDelete: (DownloadRecord) -> Unit,
) {
    if (showAbout) {
        AlertDialog(
            onDismissRequest = onDismissAbout,
            title = { Text(stringResource(R.string.about_title)) },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.about_description))
                    Text(stringResource(R.string.about_license))
                    Text(stringResource(R.string.about_credits))
                    Text(stringResource(R.string.about_privacy))
                }
            },
            confirmButton = { Button(onClick = onDismissAbout) { Text(stringResource(R.string.about_close)) } },
            dismissButton = {
                OutlinedButton(onClick = onOpenSources) { Text(stringResource(R.string.about_sources)) }
            },
        )
    }
    if (showPlayerChoice) {
        AlertDialog(
            onDismissRequest = onDismissPlayerChoice,
            title = { Text("Reproductor") },
            text = { Text("Elige qué quieres reproducir.") },
            confirmButton = {
                Button(onClick = onChooseVideos) { Text("Videos") }
            },
            dismissButton = {
                OutlinedButton(onClick = onChooseMusic) { Text("Música") }
            },
        )
    }
    if (qualityVideos.isNotEmpty()) {
        val preview = qualityVideos.singleOrNull()
        ModalBottomSheet(
            onDismissRequest = onDismissFormatSheet,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = TechBackground,
        ) {
            DownloadFormatSheetContent(
                videos = qualityVideos,
                saved = settings.getString("default_download_format", "video"),
                readFormats = { item ->
                    try {
                        if (item.id.contains(":")) {
                            universalSource.formats(item, compact = true)
                        } else {
                            source.formats(item, compact = true)
                        }
                    } catch (e: Exception) {
                        rethrowIfFatal(e)
                        DiagnosticHistory.record(context, e.message)
                        null
                    }
                },
                preview = {
                    if (preview != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            VideoThumbnail(
                                preview,
                                thumbnailResolver,
                                Modifier.width(144.dp).aspectRatio(
                                    if (TikTokPhotoSource.isPhotoEntry(preview)) {
                                        1f
                                    } else if (MediaLinkParser.parse(preview.url)?.platform ==
                                        MediaPlatform.TIKTOK
                                    ) {
                                        PORTRAIT_VIDEO_RATIO
                                    } else {
                                        LANDSCAPE_VIDEO_RATIO
                                    },
                                ),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    preview.title,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    if (TikTokPhotoSource.isPhotoEntry(preview)) "Imagen original" else "${preview.durationSeconds}s",
                                    color = TechMuted,
                                )
                            }
                        }
                    }
                },
                onConfirm = { plan, rememberFormat ->
                    if (rememberFormat) {
                        val choice = plan.choice
                        onSaveFormat(choice.id, choice.label, choice.height)
                    }
                    onDismissFormatSheet()
                    onRequestDownload(plan.downloads)
                },
            )
        }
    }
    finishedBatch?.let { batch ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Descargas terminadas") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SummaryTile("Total", batch.total, Modifier.weight(1f))
                        SummaryTile("Éxitos", batch.completed, Modifier.weight(1f))
                        SummaryTile("Errores", batch.failed, Modifier.weight(1f))
                    }
                    if (batch.cancelled > 0) {
                        Text(
                            "Cancelados: ${batch.cancelled}",
                            color = TechMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (batch.duplicates > 0) {
                        Text(
                            "Duplicados omitidos: ${batch.duplicates}",
                            color = TechMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = { Button(onClick = { onAcknowledgeBatch(batch.batchId) }) { Text("Aceptar") } },
        )
    }
    if (showClearTemp) {
        AlertDialog(
            onDismissRequest = onDismissClearTemp,
            title = { Text("Limpiar archivos temporales") },
            text = {
                Text(
                    "Se borrarán solo los archivos temporales de Baja.pe. " +
                        "Los fragmentos guardados para reanudar podrían perderse. Los videos descargados se conservan.",
                )
            },
            confirmButton = { Button(onClick = onClearTemporary) { Text("Limpiar") } },
            dismissButton = { OutlinedButton(onClick = onDismissClearTemp) { Text("Cancelar") } },
        )
    }
    deleteCandidate?.let { item ->
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text(if (TikTokPhotoSource.isPhotoFormat(item.formatId)) "Eliminar foto" else "Eliminar video") },
            text = {
                Text(
                    "¿Eliminar este elemento del dispositivo y del historial de Baja.pe? " +
                        "Esta acción no se puede deshacer.",
                )
            },
            confirmButton = { Button(onClick = { onDelete(item) }) { Text("Eliminar") } },
            dismissButton = { OutlinedButton(onClick = onDismissDelete) { Text("Cancelar") } },
        )
    }
}
