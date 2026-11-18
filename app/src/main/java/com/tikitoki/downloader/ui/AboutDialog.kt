package com.tikitoki.downloader.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tikitoki.downloader.R

@Composable
fun AboutDialog(
    onDismissRequest: () -> Unit,
    onOpenSources: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.about_title)) },
        text = {
            androidx.compose.foundation.layout.Column(
                modifier = androidx.compose.foundation.layout.Modifier
                    .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.about_description))
                Text(stringResource(R.string.about_license))
                Text(stringResource(R.string.about_credits))
                Text(stringResource(R.string.about_privacy))
            }
        },
        confirmButton = { Button(onClick = onDismissRequest) { Text(stringResource(R.string.about_close)) } },
        dismissButton = {
            OutlinedButton(onClick = onOpenSources) { Text(stringResource(R.string.about_sources)) }
        }
    )
}