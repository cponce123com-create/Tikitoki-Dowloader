package com.tikitoki.downloader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Suppress("ktlint:standard:function-naming")
@Composable
internal fun SettingsAppearanceSection(
    themeChoice: String,
    preferredQuality: String,
    onThemeChange: (String) -> Unit,
    onQualityChange: (String) -> Unit,
) {
    Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            "Oscuro" to stringResource(R.string.settings_theme_dark),
            "Negro AMOLED" to stringResource(R.string.settings_theme_amoled),
        ).forEach { (value, label) ->
            FilterChip(selected = themeChoice == value, onClick = { onThemeChange(value) }, label = { Text(label) })
        }
    }
    Text(stringResource(R.string.settings_default_quality), style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            "Máxima" to stringResource(R.string.settings_quality_maximum),
            "720p" to stringResource(R.string.settings_quality_720),
            "480p" to stringResource(R.string.settings_quality_480),
        ).forEach { (value, label) ->
            FilterChip(selected = preferredQuality == value, onClick = { onQualityChange(value) }, label = { Text(label) })
        }
    }
}
