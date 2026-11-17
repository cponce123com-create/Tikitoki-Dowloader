package com.tikitoki.downloader

import android.content.SharedPreferences
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource

@Suppress("ktlint:standard:function-naming")
@Composable
internal fun FirstRunNotice(settings: SharedPreferences) {
    var showFirstRunNotice by remember { mutableStateOf(!settings.getBoolean("first_run_notice_accepted", false)) }
    if (showFirstRunNotice) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.first_run_title)) },
            text = { Text(stringResource(R.string.first_run_body)) },
            confirmButton = {
                Button(onClick = {
                    settings.edit().putBoolean("first_run_notice_accepted", true).apply()
                    showFirstRunNotice = false
                }) { Text(stringResource(R.string.first_run_accept)) }
            },
        )
    }
}
