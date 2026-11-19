package com.tikitoki.downloader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.FilterChip
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tikitoki.downloader.data.FormatChoice

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
internal fun CompactDownloadFormatPicker(
    options: List<FormatChoice>,
    selected: FormatChoice,
    onSelect: (FormatChoice) -> Unit,
) {
    val video = options.filterNot { it.isAudio }
    val audio = options.filter { it.isAudio }
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            video.lastOrNull()?.let { bestVideo ->
                FilterChip(
                    selected = !selected.isAudio,
                    onClick = { if (selected.isAudio) onSelect(bestVideo) },
                    label = { Text(stringResource(R.string.format_video_type)) },
                )
            }
            audio.firstOrNull()?.let { mp3 ->
                FilterChip(
                    selected = selected.isAudio,
                    onClick = { if (!selected.isAudio) onSelect(mp3) },
                    label = { Text(stringResource(R.string.format_audio_type)) },
                )
            }
        }
        val visible = if (selected.isAudio) audio else video
        for (option in visible) {
            val checked = option.id == selected.id
            Row(
                modifier =
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                        selected = checked,
                        onClick = { onSelect(option) },
                        role = Role.RadioButton,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = checked, onClick = null, modifier = Modifier.padding(end = 10.dp))
                Text(option.label)
            }
        }
    }
}
