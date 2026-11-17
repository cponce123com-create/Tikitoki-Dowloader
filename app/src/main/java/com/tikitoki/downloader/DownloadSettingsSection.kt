package com.tikitoki.downloader

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tikitoki.downloader.ui.TechMuted

@Suppress("ktlint:standard:function-naming")
@Composable
internal fun DownloadSettingsSection(
    wifiParallel: Int,
    meteredParallel: Int,
    allowMetered: Boolean,
    networkSettingsBusy: Boolean,
    rateKiB: Int,
    showProgressNotice: Boolean,
    completionNotice: Boolean,
    onWifiChange: (Float) -> Unit,
    onMeteredChange: (Float) -> Unit,
    onAllowMeteredChange: (Boolean) -> Unit,
    onRateChange: (Int) -> Unit,
    onProgressNoticeChange: (Boolean) -> Unit,
    onCompletionNoticeChange: (Boolean) -> Unit,
) {
    Text(stringResource(R.string.settings_parallel), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.settings_wifi_count, wifiParallel), color = TechMuted)
    Slider(value = wifiParallel.toFloat(), onValueChange = onWifiChange, valueRange = 1f..8f, steps = 6)
    Text(stringResource(R.string.settings_metered_count, meteredParallel), color = TechMuted)
    Slider(value = meteredParallel.toFloat(), onValueChange = onMeteredChange, valueRange = 1f..8f, steps = 6)
    Text(
        stringResource(R.string.settings_parallel_explanation),
        color = TechMuted,
        style = MaterialTheme.typography.bodySmall,
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_allow_metered))
            Text(
                stringResource(R.string.settings_metered_explanation),
                color = TechMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(checked = allowMetered, enabled = !networkSettingsBusy, onCheckedChange = onAllowMeteredChange)
    }
    Text(stringResource(R.string.settings_rate_limit), style = MaterialTheme.typography.titleMedium)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            0 to stringResource(R.string.settings_rate_unlimited),
            256 to stringResource(R.string.settings_rate_256),
            512 to stringResource(R.string.settings_rate_512),
            1024 to stringResource(R.string.settings_rate_1024),
            2048 to stringResource(R.string.settings_rate_2048),
        ).forEach { (rate, label) ->
            FilterChip(selected = rateKiB == rate, onClick = { onRateChange(rate) }, label = { Text(label) })
        }
    }
    Text(
        stringResource(R.string.settings_rate_explanation),
        color = TechMuted,
        style = MaterialTheme.typography.bodySmall,
    )
    Text(stringResource(R.string.settings_notifications), style = MaterialTheme.typography.titleMedium)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.settings_show_progress), modifier = Modifier.weight(1f))
        Switch(checked = showProgressNotice, onCheckedChange = onProgressNoticeChange)
    }
    Text(
        stringResource(R.string.settings_notification_explanation),
        color = TechMuted,
        style = MaterialTheme.typography.bodySmall,
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.settings_notify_completion), modifier = Modifier.weight(1f))
        Switch(checked = completionNotice, onCheckedChange = onCompletionNoticeChange)
    }
}
