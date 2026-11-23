package com.tikitoki.downloader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tikitoki.downloader.data.DownloadFormatPlan
import com.tikitoki.downloader.data.FormatChoice
import com.tikitoki.downloader.data.FormatInspection
import com.tikitoki.downloader.data.FormatOptions
import com.tikitoki.downloader.data.TikTokPhotoSource
import com.tikitoki.downloader.data.VideoEntry
import com.tikitoki.downloader.data.checkDownloadFormats
import com.tikitoki.downloader.data.downloadFormatPlans
import com.tikitoki.downloader.domain.DownloadFormatPreference
import com.tikitoki.downloader.ui.TechMuted
import com.tikitoki.downloader.ui.TechPrimaryButton
import com.tikitoki.downloader.ui.TechPurple

private const val SHEET_HEIGHT_FRACTION = 0.92f

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
internal fun DownloadFormatSheetContent(
    videos: List<VideoEntry>,
    saved: String?,
    readFormats: suspend (VideoEntry) -> List<FormatChoice>?,
    preview: @Composable () -> Unit,
    onConfirm: (DownloadFormatPlan, Boolean) -> Unit,
) {
    var inspected by remember(videos) { mutableStateOf(emptyList<FormatInspection>()) }
    LaunchedEffect(videos) {
        checkDownloadFormats(videos, readFormats) { inspected = it }
    }
    val batch = videos.size > 1
    val photosOnly = videos.all(TikTokPhotoSource::isPhotoEntry)
    val plans = remember(inspected, batch) { downloadFormatPlans(inspected, batch) }
    var tabIsAudio by remember { mutableStateOf<Boolean?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var rememberFormat by remember { mutableStateOf(false) }
    val audio = if (photosOnly) false else tabIsAudio ?: defaultAudioTab(saved, plans)
    val visible = plans.filter { it.choice.isAudio == audio }
    val selected = selectDownloadPlan(visible, selectedId, saved)

    Column(
        Modifier.fillMaxWidth().fillMaxHeight(SHEET_HEIGHT_FRACTION).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.format_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            preview()
            if (!photosOnly) FormatTabs(audio, onTabSelect = { tabIsAudio = it })
            FormatStatus(visible, audio, inspected, videos.size, photosOnly)
            FormatRows(visible, selected, onSelect = { selectedId = it.choice.id })
        }
        DownloadFormatActions(selected, videos.size, rememberFormat, { rememberFormat = it }, onConfirm, allowRemember = !photosOnly)
    }
}

private fun defaultAudioTab(
    saved: String?,
    plans: List<DownloadFormatPlan>,
): Boolean = DownloadFormatPreference.prefersAudio(saved) || (plans.isNotEmpty() && plans.all { it.choice.isAudio })

private fun selectDownloadPlan(
    plans: List<DownloadFormatPlan>,
    selectedId: String?,
    saved: String?,
): DownloadFormatPlan? {
    if (plans.isEmpty()) return null
    val id =
        selectedId?.takeIf { chosen -> plans.any { it.choice.id == chosen } }
            ?: FormatOptions.preferred(plans.map { it.choice }, saved, compact = true).id
    return plans.first { it.choice.id == id }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun FormatTabs(
    audio: Boolean,
    onTabSelect: (Boolean) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = !audio,
            onClick = { onTabSelect(false) },
            label = { Text(stringResource(R.string.format_video_type)) },
        )
        FilterChip(
            selected = audio,
            onClick = { onTabSelect(true) },
            label = { Text(stringResource(R.string.format_audio_type)) },
        )
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun FormatStatus(
    visible: List<DownloadFormatPlan>,
    audio: Boolean,
    inspected: List<FormatInspection>,
    total: Int,
    photosOnly: Boolean,
) {
    val checking = inspected.size < total
    val failed = inspected.count { it.choices == null }
    if (checking) CircularProgressIndicator(color = TechPurple)
    if (total > 1) {
        Text(stringResource(R.string.format_check_progress, inspected.size, total), color = TechMuted)
        if (failed > 0) Text(stringResource(R.string.format_check_unverified, failed), color = TechMuted)
    }
    if (!checking && visible.isEmpty()) {
        val empty =
            when {
                failed == total -> R.string.format_check_failed
                audio -> R.string.format_no_audio
                photosOnly -> R.string.format_no_photo
                else -> R.string.format_no_video
            }
        Text(stringResource(empty), color = TechMuted)
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun FormatRows(
    options: List<DownloadFormatPlan>,
    selected: DownloadFormatPlan?,
    onSelect: (DownloadFormatPlan) -> Unit,
) {
    for (plan in options) {
        val checked = plan.choice.id == selected?.choice?.id
        Row(
            modifier =
                Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                    selected = checked,
                    onClick = { onSelect(plan) },
                    role = Role.RadioButton,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = checked, onClick = null, modifier = Modifier.padding(end = 10.dp))
            Text(plan.choice.label)
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun DownloadFormatActions(
    selected: DownloadFormatPlan?,
    total: Int,
    rememberFormat: Boolean,
    onRemember: (Boolean) -> Unit,
    onConfirm: (DownloadFormatPlan, Boolean) -> Unit,
    allowRemember: Boolean,
) {
    if (selected != null && selected.downloads.size < total) {
        Text(stringResource(R.string.format_partial_download, selected.downloads.size, total), color = TechMuted)
    }
    if (allowRemember) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = rememberFormat, onCheckedChange = onRemember, enabled = selected != null)
            Text(stringResource(R.string.format_remember))
        }
    }
    TechPrimaryButton(
        stringResource(R.string.format_download),
        icon = Icons.Outlined.Download,
        enabled = selected != null,
        modifier = Modifier.fillMaxWidth(),
        onClick = { selected?.let { onConfirm(it, rememberFormat) } },
    )
    Spacer(Modifier.height(18.dp))
}
