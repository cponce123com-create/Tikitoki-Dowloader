package com.tikitoki.downloader.ui

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.layout.maxLineSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.tikitoki.downloader.data.DownloadDatabase
import com.tikitoki.downloader.data.DownloadQueue
import com.tikitoki.downloader.data.DownloadState
import com.tikitoki.downloader.data.DownloadTelemetry
import com.tikitoki.downloader.data.FormatChoice
import com.tikitoki.downloader.data.FormatOptions
import com.tikitoki.downloader.data.ProfileMonitor
import com.tikitoki.downloader.data.TikTokVideoSource
import com.tikitoki.downloader.data.UniversalMediaSource
import com.tikitoki.downloader.data.VideoThumbnailResolver
import com.tikitoki.downloader.domain.DownloadFormatPreference
import com.tikitoki.downloader.domain.DownloadIdentity
import com.tikitoki.downloader.domain.IncomingIntentReader
import com.tikitoki.downloader.domain.IncomingLinkValidator
import com.tikitoki.downloader.domain.MediaLinkParser
import com.tikitoki.downloader.domain.MediaPlatform
import com.tikitoki.downloader.domain.PlaybackKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import androidx.compose.foundation.lazy.grid.items as gridItems
import com.tikitoki.downloader.ui.AnalysisAnimation
import com.tikitoki.downloader.ui.HomeLinkInput
import com.tikitoki.downloader.ui.TechBackground
import com.tikitoki.downloader.ui.TechBorder
import com.tikitoki.downloader.ui.TechGradient
import com.tikitoki.downloader.ui.TechHeader
import com.tikitoki.downloader.ui.TechMuted
import com.tikitoki.downloader.ui.TechPanel
import com.tikitoki.downloader.ui.TechPink
import com.tikitoki.downloader.ui.TechPrimaryButton
import com.tikitoki.downloader.ui.TechPurple
import com.tikitoki.downloader.ui.VideoThumbnail
import com.tikitoki.downloader.DiagnosticExport
import com.tikitoki.downloader.R

/**
 * AnalysisScreen - Main home screen for video analysis and selection.
 *
 * Now observes state from MainViewModel instead of managing local state.
 * Handles:
 * - Link input and platform selection
 * - Profile/video analysis via ProfileViewModel
 * - Video grid display with thumbnails
 * - Video selection (single/multi)
 * - Search and filter functionality
 * - Download quality selection trigger
 * - Download queue integration
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AnalysisScreen(
    // Incoming shared link from intent
    incomingLink: String,
    shareRevision: Int,
    // Callbacks to MainActivity for cross-tab navigation and shared state
    onNavigateToDownloads: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onShowQualitySheet: () -> Unit,
    onShowClearTemp: () -> Unit,
    onShowAbout: () -> Unit,
    // Shared state that lives in MainActivity
    themeChoice: String,
    preferredQuality: String,
    // Settings callbacks
    onSettingsChanged: () -> Unit,
    // Window insets from Scaffold
    insets: androidx.compose.foundation.layout.WindowInsets,
    // ViewModel-driven state
    url: kotlinx.coroutines.flow.MutableStateFlow<String>,
    search: kotlinx.coroutines.flow.MutableStateFlow<String>,
    onlyUndownloaded: kotlinx.coroutines.flow.MutableStateFlow<Boolean>,
    forceRefresh: kotlinx.coroutines.flow.MutableStateFlow<Boolean>,
    inspecting: kotlinx.coroutines.flow.MutableStateFlow<Boolean>,
    formatsBusy: kotlinx.coroutines.flow.MutableStateFlow<Boolean>,
    formats: kotlinx.coroutines.flow.MutableStateFlow<List<FormatChoice>>,
    chosenFormat: kotlinx.coroutines.flow.MutableStateFlow<FormatChoice?>,
    rememberFormat: kotlinx.coroutines.flow.MutableStateFlow<Boolean>,
    formatError: kotlinx.coroutines.flow.MutableStateFlow<Boolean>,
    showQualitySheet: kotlinx.coroutines.flow.MutableStateFlow<Boolean>,
    batchChecking: kotlinx.coroutines.flow.MutableStateFlow<Boolean>,
    batchChecked: kotlinx.coroutines.flow.MutableStateFlow<Int>,
    batchFailed: kotlinx.coroutines.flow.MutableStateFlow<Int>,
    batchTotal: kotlinx.coroutines.flow.MutableStateFlow<Int>,
    batchCounts: kotlinx.coroutines.flow.MutableStateFlow<Map<String, Int>>,
    batchGeneration: kotlinx.coroutines.flow.MutableStateFlow<Int>,
    selectedVideos: kotlinx.coroutines.flow.MutableStateFlow<Map<String, Boolean>>,
    defaultFormat: kotlinx.coroutines.flow.MutableStateFlow<FormatChoice?>,
    profileState: com.tikitoki.downloader.ui.ProfileUiState,
    // Additional state from ViewModel
    records: androidx.compose.runtime.State<List<com.tikitoki.downloader.data.DownloadRecord>>,
    downloadSpeeds: androidx.compose.runtime.State<Map<String, Long>>,
    finishedBatches: androidx.compose.runtime.State<List<com.tikitoki.downloader.data.BatchSummary>>,
    thumbnailResolver: com.tikitoki.downloader.data.VideoThumbnailResolver,
    // ViewModel action callbacks
    onUrlChange: (String) -> Unit,
    onSearchChange: (String) -> Unit,
    onOnlyUndownloadedChange: (Boolean) -> Unit,
    onForceRefreshChange: (Boolean) -> Unit,
    onInspectLink: () -> Unit,
    onResumeScan: () -> Unit,
    onSkipIncompletePage: () -> Unit,
    onChooseQuality: (List<String>) -> Unit,
    onFormatSelected: (FormatChoice) -> Unit,
    onRememberFormatChange: (Boolean) -> Unit,
    onDownloadSelected: (List<String>) -> Unit,
    onNavigateToHome: () -> Unit,
) {
    val context = LocalContext.current
    val settings = remember(context) { context.getSharedPreferences("tikitoki_settings", android.content.Context.MODE_PRIVATE) }
    val invalidLinkMessage = stringResource(R.string.media_invalid_link)

    // Collect ViewModel state
    val urlValue by url.collectAsState()
    val searchValue by search.collectAsState()
    val onlyUndownloadedValue by onlyUndownloaded.collectAsState()
    val forceRefreshValue by forceRefresh.collectAsState()
    val inspectingValue by inspecting.collectAsState()
    val formatsBusyValue by formatsBusy.collectAsState()
    val formatsValue by formats.collectAsState()
    val chosenFormatValue by chosenFormat.collectAsState()
    val rememberFormatValue by rememberFormat.collectAsState()
    val formatErrorValue by formatError.collectAsState()
    val showQualitySheetValue by showQualitySheet.collectAsState()
    val batchCheckingValue by batchChecking.collectAsState()
    val batchCheckedValue by batchChecked.collectAsState()
    val batchFailedValue by batchFailed.collectAsState()
    val batchTotalValue by batchTotal.collectAsState()
    val batchCountsValue by batchCounts.collectAsState()
    val batchGenerationValue by batchGeneration.collectAsState()
    val selectedVideosValue by selectedVideos.collectAsState()
    val defaultFormatValue by defaultFormat.collectAsState()

    // Local UI state
    var selectedPlatform by rememberSaveable { mutableStateOf<String?>("Auto") }
    var gridMode by rememberSaveable { mutableStateOf(true) }
    var message by remember { mutableStateOf("") }
    var queueBusy by remember { mutableStateOf(false) }
    var showClearTemp by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showPlayerChoice by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<com.tikitoki.downloader.data.DownloadRecord?>(null) }

    // Repositories and services
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val diagnosticPicker =
        DiagnosticExport.rememberDiagnosticPicker {
            message = context.getString(R.string.diagnostic_error)
        }

    // Compute completed kinds and downloaded IDs for UI
    val completedKinds =
        remember(records.value) {
            records.value
                .filter { it.status == DownloadState.COMPLETED }
                .groupBy { DownloadIdentity.sourceId(it.videoId) }
        }
    val downloadedIds =
        remember(completedKinds) {
            completedKinds
                .filterValues { items ->
                    items.any { PlaybackKind.isAudio(it.formatId, null, it.savedUri) } &&
                        items.any { !PlaybackKind.isAudio(it.formatId, null, it.savedUri) }
                }.keys
        }
    val videos = profileState.videos
    val visibleVideos =
        remember(videos, searchValue, onlyUndownloadedValue, downloadedIds) {
            videos.filter {
                (searchValue.isBlank() || it.title.contains(searchValue, ignoreCase = true) || it.id.contains(searchValue)) &&
                    (!onlyUndownloadedValue || it.id !in downloadedIds)
            }
        }

    // Sync ViewModel state to local when needed (for incoming link handling)
    LaunchedEffect(incomingLink, shareRevision) {
        if (incomingLink.isNotBlank() && (shareRevision > 0)) {
            url.value = incomingLink
            selectedVideos.value = emptyMap()
            forceRefresh.value = false
            message = ""
        }
    }

    // Trigger analysis when URL changes - handled by ViewModel's handleUrlChange
    LaunchedEffect(urlValue) {
        val link = IncomingLinkValidator.validate(urlValue)?.url ?: return@LaunchedEffect
        delay(500)
        onInspectLink()
    }

    // Download handling
    val addToQueue: () -> Unit = {
        if (!queueBusy && selectedVideosValue.isNotEmpty()) {
            queueBusy = true
            val batch = videos.filter { it.id in selectedVideosValue }
            val formatId = chosenFormatValue?.id ?: defaultFormatValue?.id ?: TikTokVideoSource.DEFAULT_FORMAT
            scope.launch {
                try {
                    val result = DownloadQueue.enqueue(context, batch, formatId)
                    message = "${result.added} videos añadidos. Duplicados omitidos: ${result.duplicates}."
                    if (result.added > 0 || result.duplicates > 0) {
                        selectedVideos.value = emptyMap()
                    }
                    if (selectedVideosValue.isEmpty()) onNavigateToDownloads()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    message = "No se pudo crear la cola. Inténtalo otra vez."
                } finally {
                    queueBusy = false
                }
            }
        }
    }

    val storagePermission =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (granted) {
                addToQueue()
            } else {
                message = "Android necesita permiso para guardar en Descargas."
            }
        }
    val notificationsPermission =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { /* Notificaciones opcionales: las descargas siguen aunque se denieguen. */ }

    val requestDownload: () -> Unit = {
        focusManager.clearFocus()
        keyboard?.hide()
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            storagePermission.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notificationsPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            addToQueue()
        }
    }

    val chooseQualityAction: () -> Unit = {
        focusManager.clearFocus()
        keyboard?.hide()
        val selectedIds = selectedVideosValue.keys.toList()
        onChooseQuality(selectedIds)
    }

    // Main content
    LazyVerticalGrid(
        columns = if (gridMode) GridCells.Adaptive(minSize = 112.dp) else GridCells.Fixed(1),
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    if (themeChoice == "Negro AMOLED") {
                        Color.Black
                    } else {
                        TechBackground
                    },
                )
                .windowInsetsPadding(insets)
                .consumeWindowInsets(insets),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                TechHeader(onSettings = onNavigateToSettings)

                HomeLinkInput(
                    selectedPlatform = selectedPlatform,
                    url = urlValue,
                    loading = profileState.loading,
                    loadingMore = profileState.loadingMore,
                    onPlatformChange = { selectedPlatform = it },
                    onUrlChange = { newUrl ->
                        val extracted = if (newUrl.length - urlValue.length > 1) IncomingLinkValidator.extractFirstHttps(newUrl) else null
                        val finalUrl = extracted?.url ?: newUrl
                        url.value = finalUrl
                        profileViewModel.reset()
                        selectedVideos.value = emptyMap()
                        val df = defaultFormatValue!!
                        // Note: formats/chosenFormat are now in ViewModel, but we update local for immediate UI
                        message = ""
                    },
                    onKeyboardDone = {
                        focusManager.clearFocus()
                        keyboard?.hide()
                    },
                    onPaste = {
                        focusManager.clearFocus()
                        keyboard?.hide()
                        val pasted = clipboard.getText()?.text.orEmpty()
                        val link = IncomingLinkValidator.validate(pasted) ?: IncomingLinkValidator.extractFirstHttps(pasted)
                        if (link != null) {
                            url.value = link.url
                            selectedVideos.value = emptyMap()
                            val df = defaultFormatValue!!
                            message = ""
                        } else {
                            message = "El portapapeles no contiene un enlace válido."
                        }
                    },
                    onAnalyze = {
                        focusManager.clearFocus()
                        keyboard?.hide()
                        when (val parsed = IncomingLinkValidator.validate(urlValue)) {
                            null -> message = invalidLinkMessage
                            else -> {
                                selectedVideos.value = emptyMap()
                                onInspectLink()
                                forceRefresh.value = false
                                message = ""
                            }
                        }
                    },
                )

                // Force refresh remains available from the analysis action on repeat requests.
                if (profileState.loading || profileState.loadingMore || profileState.countingTotal) {
                    AnalysisAnimation(found = videos.size, scanning = profileState.countingTotal || profileState.loadingMore)
                }
                if (queueBusy) CircularProgressIndicator()
                profileState.error?.let { error ->
                    Column {
                        Text(error, color = MaterialTheme.colorScheme.error)
                        if (profileState.hasMore && !profileState.countingTotal) {
                            OutlinedButton(onClick = onResumeScan) { Text("↻ Reanudar escaneo") }
                        }
                        if (profileState.incompletePage && !profileState.countingTotal) {
                            OutlinedButton(
                                onClick = onSkipIncompletePage,
                            ) { Text("Continuar con la siguiente parte") }
                        }
                    }
                }
                if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)

                if (videos.isNotEmpty()) {
                    val allSelected = videos.all { it.id in selectedVideosValue }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors =
                            androidx.compose.material3.CardDefaults
                                .cardColors(containerColor = TechPanel),
                        border = BorderStroke(1.dp, TechBorder),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    Modifier.size(34.dp).clip(RoundedCornerShape(17.dp)).background(TechGradient),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (profileState.profileImage != null) {
                                        SubcomposeAsyncImage(
                                            model =
                                                ImageRequest
                                                    .Builder(LocalContext.current)
                                                    .data(
                                                        profileState.profileImage,
                                                    ).addHeader("Referer", "https://www.tiktok.com/")
                                                    .build(),
                                            contentDescription = stringResource(R.string.home_profile_photo),
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                            error = { Text("@", fontSize = 18.sp, color = Color.White) },
                                            loading = { Text("@", fontSize = 18.sp, color = Color.White) },
                                        )
                                    } else {
                                        Text("@", fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        urlValue
                                            .substringAfter("tiktok.com/")
                                            .substringBefore("?")
                                            .substringBefore("/video/")
                                            .take(35)
                                            .ifBlank { selectedPlatform ?: "Perfil" },
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        fontSize = 13.sp,
                                    )
                                    Text(
                                        stringResource(R.string.home_videos_found, videos.size),
                                        color = TechMuted,
                                        fontSize = 11.sp,
                                    )
                                }
                                IconButton(
                                    onClick = { profileViewModel.resumeScan() },
                                    enabled = !profileState.countingTotal && profileState.hasMore,
                                ) {
                                    Text("↻", fontSize = 20.sp)
                                }
                            }
                            Row(
                                Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                            ) {
                                listOf(
                                    stringResource(R.string.home_detected) to
                                        (profileState.totalVideos?.toString() ?: if (profileState.countingTotal) "…" else "—"),
                                    stringResource(R.string.home_loaded) to videos.size.toString(),
                                    stringResource(R.string.home_selected) to selectedVideosValue.size.toString(),
                                ).forEach { (label, value) ->
                                    Text("$label: $value", fontSize = 11.sp, color = TechMuted)
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(modifier = Modifier.weight(1f), onClick = {
                            focusManager.clearFocus()
                            keyboard?.hide()
                            selectedVideos.value = if (allSelected) emptyMap() else videos.associate { it.id to true }
                            val df = defaultFormatValue!!
                        }) {
                            Text(
                                if (allSelected) "Quitar selección" else "Seleccionar todo",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = selectedVideosValue.isNotEmpty() && !queueBusy && !profileState.loading,
                            onClick = chooseQualityAction,
                        ) {
                            Icon(Icons.Outlined.Download.imageVector, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Descargar ${selectedVideosValue.size}", maxLines = 1)
                        }
                    }

                    OutlinedTextField(
                        value = searchValue,
                        onValueChange = { search.value = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Buscar videos") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions =
                            KeyboardActions(onDone = {
                                focusManager.clearFocus()
                                keyboard?.hide()
                            }),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(
                            selected = onlyUndownloadedValue,
                            onClick = { onlyUndownloaded.value = !onlyUndownloadedValue },
                            label = { Text("Pendientes") },
                        )
                        FilterChip(selected = gridMode, onClick = { gridMode = true }, label = { Text("Cuadrícula") })
                        FilterChip(selected = !gridMode, onClick = { gridMode = false }, label = { Text("Lista") })
                    }
                }
            }
        }
        gridItems(visibleVideos, key = { it.id }) { video ->
            val checked = video.id in selectedVideosValue
            Card(
                onClick = {
                    val newSelection = selectedVideosValue.toMutableMap().also { map ->
                        if (map.containsKey(video.id)) map.remove(video.id) else map[video.id] = true
                    }
                    selectedVideos.value = newSelection
                    val df = defaultFormatValue!!
                },
                colors =
                    androidx.compose.material3.CardDefaults
                        .cardColors(containerColor = TechPanel),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (checked) TechPink else TechBorder),
            ) {
                if (gridMode) {
                    Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(
                                if (MediaLinkParser.parse(video.url)?.platform ==
                                    MediaPlatform.TIKTOK
                                ) {
                                    9f / 16f
                                } else {
                                    16f / 9f
                                },
                            ),
                        ) {
                            VideoThumbnail(
                                video,
                                thumbnailResolver,
                                Modifier.fillMaxSize(),
                                deferLookup = profileState.countingTotal,
                            )
                            androidx.compose.material3.Checkbox(
                                checked = checked,
                                onCheckedChange = null,
                                modifier =
                                    Modifier
                                        .align(
                                            Alignment.TopEnd,
                                        ).background(Color.Black.copy(alpha = .65f), RoundedCornerShape(8.dp))
                                        .size(36.dp),
                            )
                        }
                        Text(
                            video.title,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.width(116.dp).aspectRatio(
                                if (MediaLinkParser.parse(video.url)?.platform ==
                                    MediaPlatform.TIKTOK
                                ) {
                                    9f / 16f
                                } else {
                                    16f / 9f
                                },
                            ),
                        ) {
                            VideoThumbnail(
                                video,
                                thumbnailResolver,
                                Modifier.fillMaxSize(),
                                deferLookup = profileState.countingTotal,
                            )
                            androidx.compose.material3.Checkbox(
                                checked = checked,
                                onCheckedChange = null,
                                modifier =
                                    Modifier
                                        .align(
                                            Alignment.TopEnd,
                                        ).background(Color.Black.copy(alpha = .65f), RoundedCornerShape(8.dp))
                                        .size(36.dp),
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (video.id in
                                    downloadedIds
                                ) {
                                    "✓ Audio y video"
                                } else if (video.id in
                                    completedKinds
                                ) {
                                    "✓ Una versión guardada"
                                } else {
                                    "Disponible"
                                },
                                color = TechMuted,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
    }
}