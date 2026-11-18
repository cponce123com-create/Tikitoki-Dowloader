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
) {
    val context = LocalContext.current
    val settings = remember(context) { context.getSharedPreferences("tikitoki_settings", android.content.Context.MODE_PRIVATE) }
    val invalidLinkMessage = stringResource(R.string.media_invalid_link)

    // Analysis-specific state
    var selectedPlatform by rememberSaveable { mutableStateOf<String?>("Auto") }
    var url by rememberSaveable { mutableStateOf(incomingLink) }
    var search by rememberSaveable { mutableStateOf("") }
    var onlyUndownloaded by rememberSaveable { mutableStateOf(false) }
    var gridMode by rememberSaveable { mutableStateOf(true) }
    var forceRefresh by rememberSaveable { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var analysisRevision by remember { mutableStateOf(0) }

    // Format selection state
    var formatsBusy by remember { mutableStateOf(false) }
    var formatError by remember { mutableStateOf(false) }
    var formats by remember { mutableStateOf(listOf<FormatChoice>()) }
    var chosenFormat by remember { mutableStateOf<FormatChoice?>(null) }
    var defaultFormat by remember { mutableStateOf<FormatChoice?>(null) }
    var rememberFormat by remember { mutableStateOf(false) }

    // Batch checking state
    var batchChecking by remember { mutableStateOf(false) }
    var batchChecked by remember { mutableStateOf(0) }
    var batchFailed by remember { mutableStateOf(0) }
    var batchTotal by remember { mutableStateOf(0) }
    var batchCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var batchGeneration by remember { mutableStateOf(0) }
    var batchCheckJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // Selected videos (mutableStateMapOf for selection tracking)
    val selected = remember { mutableStateMapOf<String, Boolean>() }

    // Repositories and services
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val source = remember(context) { TikTokVideoSource(context.applicationContext) }
    val thumbnailResolver = remember(context) { VideoThumbnailResolver(context.applicationContext) }
    val universalSource = remember(context) { UniversalMediaSource(context.applicationContext) }
    val profileViewModel: ProfileViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val profileState by profileViewModel.state.collectAsState()
    val dao = remember(context) { DownloadDatabase.get(context).downloads() }
    val downloadsViewModel: DownloadsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val records by downloadsViewModel.records.collectAsState()
    val downloadSpeeds by DownloadTelemetry.speeds.collectAsState()
    val finishedBatches by remember(dao) { dao.observeFinishedBatches() }.collectAsState(initial = emptyList())

    var queueBusy by remember { mutableStateOf(false) }
    var showQualitySheet by remember { mutableStateOf(false) }
    var showClearTemp by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showPlayerChoice by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<com.tikitoki.downloader.data.DownloadRecord?>(null) }

    // Initialize default format based on preferredQuality
    LaunchedEffect(preferredQuality) {
        val selector = when (preferredQuality) {
            "720p" -> "bestvideo[height<=720]+bestaudio/best[height<=720]/best"
            "480p" -> "bestvideo[height<=480]+bestaudio/best[height<=480]/best"
            else -> TikTokVideoSource.DEFAULT_FORMAT
        }
        val df = FormatChoice(selector, preferredQuality)
        defaultFormat = df
        if (formats.isEmpty()) {
            formats = listOf(df)
        }
        if (chosenFormat == null) {
            chosenFormat = df
        }
    }

    val videos = profileState.videos

    // Handle incoming link changes
    LaunchedEffect(incomingLink, shareRevision) {
        if (incomingLink.isNotBlank() && (shareRevision > 0 || !profileViewModel.isCurrentLink(incomingLink))) {
            url = incomingLink
            profileViewModel.reset()
            selected.clear()
            forceRefresh = false
            message = ""
            analysisRevision++
        }
    }

    // Trigger analysis when URL changes
    LaunchedEffect(url, analysisRevision) {
        val link = IncomingLinkValidator.validate(url)?.url ?: return@LaunchedEffect
        delay(500)
        if (!profileViewModel.isCurrentLink(link)) profileViewModel.inspect(link, forceRefresh)
    }

    // Download handling
    val addToQueue: () -> Unit = {
        if (!queueBusy && selected.isNotEmpty()) {
            queueBusy = true
            val batch = videos.filter { it.id in selected }
            val formatId = chosenFormat?.id ?: defaultFormat?.id ?: TikTokVideoSource.DEFAULT_FORMAT
            scope.launch {
                try {
                    val result = DownloadQueue.enqueue(context, batch, formatId)
                    message = "${result.added} videos añadidos. Duplicados omitidos: ${result.duplicates}."
                    if (result.added > 0 || result.duplicates > 0) batch.forEach { selected.remove(it.id) }
                    if (selected.isEmpty()) onNavigateToDownloads()
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

    val chooseQuality: () -> Unit = {
        focusManager.clearFocus()
        keyboard?.hide()
        val video = selected.keys.firstOrNull()?.let { id -> videos.firstOrNull { it.id == id } }
        if (video != null) {
            batchGeneration++
            batchCheckJob?.cancel()
            batchChecking = false
            batchChecked = 0
            batchFailed = 0
            batchTotal = 0
            batchCounts = emptyMap()
            formatError = false
            showQualitySheet = true
            formatsBusy = selected.size == 1
            formats = if (selected.size == 1) listOf(defaultFormat!!) else FormatOptions.batch(context)
            chosenFormat = formats.first()
            rememberFormat = false
            scope.launch {
                try {
                    if (selected.size == 1) {
                        val found = if (video.id.contains(":")) universalSource.formats(video) else source.formats(video)
                        if (found.isEmpty()) throw IllegalStateException("No formats")
                        formats = found
                    }
                    val saved = settings.getString("default_download_format", "video")
                    chosenFormat =
                        formats.firstOrNull { DownloadFormatPreference.matches(saved, it.id, it.label, TikTokVideoSource.DEFAULT_FORMAT) }
                            ?: formats.first()
                } catch (
                    e: CancellationException,
                ) {
                    throw e
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    runCatching { com.tikitoki.downloader.data.DiagnosticHistory.record(context, e.message) }
                    message = "No se pudieron cargar las opciones de calidad. Inténtalo otra vez."
                    formatError = true
                } finally {
                    formatsBusy = false
                }
            }
        }
    }

    val diagnosticPicker =
        DiagnosticExport.rememberDiagnosticPicker {
            message = context.getString(R.string.diagnostic_error)
        }

    // Compute completed kinds and downloaded IDs for UI
    val completedKinds =
        remember(records) {
            records
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
    val visibleVideos =
        remember(videos, search, onlyUndownloaded, downloadedIds) {
            videos.filter {
                (search.isBlank() || it.title.contains(search, ignoreCase = true) || it.id.contains(search)) &&
                    (!onlyUndownloaded || it.id !in downloadedIds)
            }
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
                    url = url,
                    loading = profileState.loading,
                    loadingMore = profileState.loadingMore,
                    onPlatformChange = { selectedPlatform = it },
                    onUrlChange = {
                        val extracted = if (it.length - url.length > 1) IncomingLinkValidator.extractFirstHttps(it) else null
                        url = extracted?.url ?: it
                        profileViewModel.reset()
                        selected.clear()
                        val df = defaultFormat!!
                        formats = listOf(df)
                        chosenFormat = df
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
                            url = link.url
                            profileViewModel.reset()
                            selected.clear()
                            val df = defaultFormat!!
                            formats = listOf(df)
                            chosenFormat = df
                            message = ""
                            analysisRevision++
                        } else {
                            message = "El portapapeles no contiene un enlace válido."
                        }
                    },
                    onAnalyze = {
                        focusManager.clearFocus()
                        keyboard?.hide()
                        when (val parsed = IncomingLinkValidator.validate(url)) {
                            null -> message = invalidLinkMessage
                            else -> {
                                selected.clear()
                                profileViewModel.inspect(url, forceRefresh)
                                forceRefresh = false
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
                            OutlinedButton(onClick = { profileViewModel.resumeScan() }) { Text("↻ Reanudar escaneo") }
                        }
                        if (profileState.incompletePage && !profileState.countingTotal) {
                            OutlinedButton(
                                onClick = { profileViewModel.skipIncompletePage() },
                            ) { Text("Continuar con la siguiente parte") }
                        }
                    }
                }
                if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)

                if (videos.isNotEmpty()) {
                    val allSelected = videos.all { it.id in selected }
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
                                        url
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
                                    stringResource(R.string.home_selected) to selected.size.toString(),
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
                            selected.clear()
                            if (!allSelected) videos.forEach { selected[it.id] = true }
                            val df = defaultFormat!!
                            formats = listOf(df)
                            chosenFormat = df
                        }) {
                            Text(
                                if (allSelected) "Quitar selección" else "Seleccionar todo",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = selected.isNotEmpty() && !queueBusy && !profileState.loading,
                            onClick = chooseQuality,
                        ) {
                            Icon(Icons.Outlined.Download.imageVector, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Descargar ${selected.size}", maxLines = 1)
                        }
                    }

                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
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
                            selected = onlyUndownloaded,
                            onClick = { onlyUndownloaded = !onlyUndownloaded },
                            label = { Text("Pendientes") },
                        )
                        FilterChip(selected = gridMode, onClick = { gridMode = true }, label = { Text("Cuadrícula") })
                        FilterChip(selected = !gridMode, onClick = { gridMode = false }, label = { Text("Lista") })
                    }
                }
            }
        }
        gridItems(visibleVideos, key = { it.id }) { video ->
            val checked = video.id in selected
            Card(
                onClick = {
                    if (checked) selected.remove(video.id) else selected[video.id] = true
                    val df = defaultFormat!!
                    formats = listOf(df)
                    chosenFormat = df
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