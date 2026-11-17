package com.tikitoki.downloader

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tikitoki.downloader.ui.TechMuted

@Suppress("ktlint:standard:function-naming")
@Composable
internal fun SettingsGeneralSection(
    monitorHours: Int,
    monitorPages: Int,
    folderDescription: String,
    hasCustomFolder: Boolean,
    message: String,
    onAbout: () -> Unit,
    onExportDiagnostic: () -> Unit,
    onMonitorHoursChange: (Int) -> Unit,
    onMonitorPagesChange: (Int) -> Unit,
    onUpdateExtractor: () -> Unit,
    onChooseFolder: () -> Unit,
    onResetFolder: () -> Unit,
) {
    Image(
        painter = painterResource(R.drawable.bajape_logo),
        contentDescription = null,
        modifier = Modifier.size(76.dp),
    )
    Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)
    OutlinedButton(onClick = onAbout) { Text(stringResource(R.string.about_open)) }
    OutlinedButton(onClick = onExportDiagnostic) { Text(stringResource(R.string.diagnostic_export)) }
    Text(stringResource(R.string.settings_monitor_frequency), style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(6, 12, 24).forEach { hours ->
            FilterChip(
                selected = monitorHours == hours,
                onClick = { onMonitorHoursChange(hours) },
                label = { Text(stringResource(R.string.monitor_hours, hours)) },
            )
        }
    }
    Text(stringResource(R.string.monitor_weekly), color = TechMuted, style = MaterialTheme.typography.bodySmall)
    Text(stringResource(R.string.monitor_page_limit), style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(5, 10, 25, 50).forEach { pages ->
            FilterChip(selected = monitorPages == pages, onClick = { onMonitorPagesChange(pages) }, label = { Text(pages.toString()) })
        }
    }
    Text(stringResource(R.string.settings_version_label))
    Text(stringResource(R.string.settings_app_update), style = MaterialTheme.typography.titleMedium)
    val context = LocalContext.current
    val updateStatus = remember { context.getSharedPreferences("extractor_status", android.content.Context.MODE_PRIVATE) }
    val lastResult = updateStatus.getString("last_result", "Actualización aún no ejecutada.")
    Text(
        stringResource(
            if (lastResult?.startsWith("Falló") == true) {
                R.string.settings_update_failed
            } else if (lastResult?.startsWith("Actualización ejecutada") == true) {
                R.string.settings_update_complete
            } else {
                R.string.settings_update_pending
            },
        ),
        color = TechMuted,
    )
    val lastSuccess = updateStatus.getLong("last_success", 0L)
    if (lastSuccess > 0L) {
        Text(
            stringResource(
                R.string.settings_last_update,
                java.text.DateFormat.getDateTimeInstance().format(java.util.Date(lastSuccess)),
            ),
        )
    }
    OutlinedButton(onClick = onUpdateExtractor) { Text(stringResource(R.string.settings_update_search)) }
    Text(message, style = MaterialTheme.typography.bodySmall, color = TechMuted)
    Text(stringResource(R.string.settings_destination), style = MaterialTheme.typography.titleMedium)
    Text(folderDescription, color = TechMuted)
    OutlinedButton(onClick = onChooseFolder) { Text(stringResource(R.string.settings_choose_folder)) }
    if (hasCustomFolder) {
        OutlinedButton(onClick = onResetFolder) { Text(stringResource(R.string.settings_reset_folder)) }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
internal fun SettingsStorageSection(
    message: String,
    onClearTemporary: () -> Unit,
) {
    Text(stringResource(R.string.settings_temporary_storage), style = MaterialTheme.typography.titleMedium)
    Text(
        stringResource(R.string.settings_temporary_explanation),
        color = TechMuted,
        style = MaterialTheme.typography.bodySmall,
    )
    OutlinedButton(onClick = onClearTemporary) { Text(stringResource(R.string.settings_clear_temporary)) }
    if (message.isNotBlank()) Text(message, color = TechMuted, style = MaterialTheme.typography.bodySmall)
    Text(stringResource(R.string.settings_authorized_gpl))
}
