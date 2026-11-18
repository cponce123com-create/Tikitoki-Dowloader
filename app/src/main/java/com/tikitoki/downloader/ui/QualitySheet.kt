package com.tikitoki.downloader.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Modifier
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tikitoki.downloader.R
import com.tikitoki.downloader.data.FormatChoice
import com.tikitoki.downloader.data.FormatOptions
import com.tikitoki.downloader.data.FormatAvailability
import com.tikitoki.downloader.data.TikTokVideoSource
import com.tikitoki.downloader.data.UniversalMediaSource
import com.tikitoki.downloader.data.VideoEntry
import com.tikitoki.downloader.domain.DownloadFormatPreference
import com.tikitoki.downloader.domain.MediaLinkParser
import com.tikitoki.downloader.domain.MediaPlatform
import kotlinx.coroutines.CancellationException

@Composable
fun QualitySheet(
    context: Context,
    selectedVideoIds: Map<String, Boolean>,
    videos: List<VideoEntry>,
    defaultFormat: FormatChoice,
    onFormatSelected: (FormatChoice) -> Unit,
    onDismissRequest: () -> Unit,
    onDownloadClick: (FormatChoice, Boolean) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
    settings: android.content.SharedPreferences,
    selectedSize: Int,
    universalSource: UniversalMediaSource,
    source: com.tikitoki.downloader.data.TikTokVideoSource,
    thumbnailResolver: com.tikitoki.downloader.data.VideoThumbnailResolver
) {
    val preview = videos.firstOrNull { selectedSize == 1 && it.id == selectedVideoIds.keys.first() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var formats by remember { mutableStateOf(listOf(defaultFormat)) }
    var chosenFormat by remember(defaultFormat) { mutableStateOf(defaultFormat) }
    var formatsBusy by remember { mutableStateOf(false) }
    var formatError by remember { mutableStateOf(false) }
    var rememberFormat by remember { mutableStateOf(false) }
    var batchChecking by remember { mutableStateOf(false) }
    var batchCheckJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var batchGeneration by remember { mutableStateOf(0) }
    var batchChecked by remember { mutableStateOf(0) }
    var batchFailed by remember { mutableStateOf(0) }
    var batchTotal by remember { mutableStateOf(0) }
    var batchCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    ModalBottomSheet(
        onDismissRequest = {
            batchGeneration++
            batchCheckJob?.cancel()
            batchChecking = false
            onDismissRequest()
        },
        sheetState = sheetState,
        containerColor = TechBackground,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(PaddingValues(horizontal = 20.dp, vertical = 8.dp)),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Opciones de descarga",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                if (preview != null) {
                    androidx.compose.foundation.layout.Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        VideoThumbnail(
                            preview,
                            thumbnailResolver,
                            Modifier.width(144.dp).aspectRatio(
                                if (MediaLinkParser.parse(preview.url)?.platform == MediaPlatform.TIKTOK) {
                                    9f / 16f
                                } else {
                                    16f / 9f
                                }
                            ),
                        )
                        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                            Text(
                                preview.title,
                                maxLines = 3,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text("${preview.durationSeconds}s", color = TechMuted)
                        }
                    }
                }
                Text("Formato disponible", style = MaterialTheme.typography.titleMedium)
                if (selectedSize > 1) {
                    Text(stringResource(R.string.format_batch_note), color = TechMuted)
                    OutlinedButton(onClick = {
                        if (batchChecking) {
                            batchGeneration++
                            batchCheckJob?.cancel()
                            batchChecking = false
                        } else {
                            val items = videos.filter { it.id in selectedVideoIds }
                            var presets = formats.toList()
                            val checkedItems = mutableListOf<List<FormatChoice>>()
                            val knownHeights = mutableSetOf<Int>()
                            batchChecked = 0
                            batchFailed = 0
                            batchTotal = items.size
                            batchCounts = emptyMap()
                            batchChecking = true
                            val generation = ++batchGeneration
                            batchCheckJob = scope.launch {
                                try {
                                    for (item in items) {
                                        try {
                                            val found =
                                                if (item.id.contains(":")) universalSource.formats(item) else source.formats(item)
                                            if (found.isEmpty()) {
                                                batchFailed++
                                                continue
                                            }
                                            checkedItems.add(found)
                                            val discovered = found.mapNotNull { it.height }.filter { it in 1..16384 }
                                            val heightsChanged = knownHeights.addAll(discovered)
                                            if (heightsChanged) {
                                                presets = FormatOptions.batch(context, knownHeights)
                                                formats = presets
                                                batchCounts =
                                                    presets.associate { preset ->
                                                        preset.id to
                                                            checkedItems.count { FormatAvailability.matches(preset.id, it) }
                                                    }
                                            } else {
                                                batchCounts =
                                                    batchCounts.toMutableMap().apply {
                                                        presets.forEach { preset ->
                                                            if (FormatAvailability.matches(preset.id, found)) {
                                                                this[preset.id] =
                                                                    (this[preset.id] ?: 0) + 1
                                                            }
                                                        }
                                                    }
                                            }
                                            batchChecked++
                                        } catch (
                                            e: CancellationException,
                                        ) {
                                            throw e
                                        } catch (e: Exception) {
                                            if (e is CancellationException) throw e
                                            com.tikitoki.downloader.data.DiagnosticHistory.record(context, e.message)
                                            batchFailed++
                                        }
                                    }
                                } finally {
                                    if (generation == batchGeneration) batchChecking = false
                                }
                            }
                        }
                    }) { Text(stringResource(if (batchChecking) R.string.format_check_cancel else R.string.format_check_batch)) }
                    if (batchTotal > 0) {
                        Text(
                            stringResource(
                                R.string.format_check_status,
                                batchChecked + batchFailed,
                                batchTotal,
                                batchFailed + (batchTotal - batchChecked - batchFailed),
                            ),
                            color = TechMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (formatsBusy) CircularProgressIndicator(color = TechPurple)
                if (formatError) Text(stringResource(R.string.format_check_failed), color = MaterialTheme.colorScheme.error)
                Column {
                    formats.forEach { option ->
                        androidx.compose.foundation.layout.Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { chosenFormat = option }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = chosenFormat.id == option.id,
                                onClick = { chosenFormat = option },
                            )
                            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                                Text(option.label)
                                if (selectedSize > 1 &&
                                    batchChecked > 0
                                ) {
                                    Text(
                                        stringResource(R.string.format_check_matches, batchCounts[option.id] ?: 0, batchChecked),
                                        color = TechMuted,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = rememberFormat, onCheckedChange = { rememberFormat = it })
                Text("Usar como opción predeterminada")
            }
            TechPrimaryButton(
                "Descargar",
                icon = Icons.Outlined.Download.imageVector,
                enabled = !formatsBusy && !formatError,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    batchGeneration++
                    batchCheckJob?.cancel()
                    batchChecking = false
                    onDownloadClick(chosenFormat, rememberFormat)
                },
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(18.dp))
        }
    }
}