package com.tikitoki.downloader.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Paste
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tikitoki.downloader.R
import com.tikitoki.downloader.data.DownloadQueue
import com.tikitoki.downloader.data.DownloadState
import com.tikitoki.downloader.data.FormatChoice
import com.tikitoki.downloader.data.FormatOptions
import com.tikitoki.downloader.data.ProfileMonitor
import com.tikitoki.downloader.data.VideoThumbnail
import com.tikitoki.downloader.data.VideoThumbnailResolver
import com.tikitoki.downloader.domain.DownloadFormatPreference
import com.tikitoki.downloader.domain.IncomingIntentReader
import com.tikitoki.downloader.domain.IncomingLinkValidator
import com.tikitoki.downloader.domain.MediaLinkParser
import com.tikitoki.downloader.domain.MediaPlatform
import com.tikitoki.downloader.domain.TikTokLinkParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeLinkInput(
    selectedPlatform: String?,
    url: String,
    loading: Boolean,
    loadingMore: Boolean,
    onPlatformChange: (String?) -> Unit,
    onUrlChange: (String) -> Unit,
    onKeyboardDone: () -> Unit,
    onPaste: () -> Unit,
    onAnalyze: () -> Unit,
    forceRefresh: Boolean,
    onForceRefreshChange: (Boolean) -> Unit,
    invalidLinkMessage: String,
    TechBackground: androidx.compose.ui.graphics.Color,
    TechPanel: androidx.compose.ui.graphics.Color,
    TechBorder: androidx.compose.ui.graphics.Color,
    TechPink: androidx.compose.ui.graphics.Color,
    TechMuted: androidx.compose.ui.graphics.Color,
    TechGradient: androidx.compose.ui.graphics.Color,
    TechPrimaryButton: @Composable (String, androidx.compose.material3.Icon?, Boolean, Modifier, () -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboardManager.current

    OutlinedTextField(
        value = url,
        onValueChange = onUrlChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Pega un enlace o escribe para buscar") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = onKeyboardDone),
    )

    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        // Platform selector would go here if needed
        Spacer(Modifier.weight(1f))

        IconButton(onClick = onPaste) {
            Icon(Icons.Outlined.Paste, contentDescription = stringResource(R.string.paste), tint = Color.White)
        }
    }

    if (loading || loadingMore) {
        CircularProgressIndicator()
    }

    // Force refresh checkbox
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = forceRefresh, onCheckedChange = onForceRefreshChange)
        Text("Forzar reescaneo", color = TechMuted, style = MaterialTheme.typography.bodySmall)
    }
}