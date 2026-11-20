package com.tikitoki.downloader

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.tikitoki.downloader.data.DiagnosticHistory
import com.tikitoki.downloader.data.DownloadDao
import com.tikitoki.downloader.data.DownloadQueue
import com.tikitoki.downloader.data.DownloadRecord
import com.tikitoki.downloader.data.DownloadSelection
import com.tikitoki.downloader.data.DownloadState
import com.tikitoki.downloader.data.ExtractorUpdateWorker
import com.tikitoki.downloader.data.ProfileAvatarResolver
import com.tikitoki.downloader.data.ProfileMonitor
import com.tikitoki.downloader.data.UniversalMediaSource
import com.tikitoki.downloader.data.VideoEntry
import com.tikitoki.downloader.data.VideoThumbnailResolver
import com.tikitoki.downloader.data.rethrowIfFatal
import com.tikitoki.downloader.domain.DownloadIdentity
import com.tikitoki.downloader.domain.IncomingLinkValidator
import com.tikitoki.downloader.domain.MediaLinkParser
import com.tikitoki.downloader.domain.MediaPlatform
import com.tikitoki.downloader.domain.PlaybackKind
import com.tikitoki.downloader.ui.AnalysisAnimation
import com.tikitoki.downloader.ui.DownloadsViewModel
import com.tikitoki.downloader.ui.MainViewModel
import com.tikitoki.downloader.ui.MusicScreen
import com.tikitoki.downloader.ui.ProfileUiState
import com.tikitoki.downloader.ui.ProfileViewModel
import com.tikitoki.downloader.ui.RockolaScreen
import com.tikitoki.downloader.ui.SafeAreaTopBar
import com.tikitoki.downloader.ui.TechBackground
import com.tikitoki.downloader.ui.TechBorder
import com.tikitoki.downloader.ui.TechGradient
import com.tikitoki.downloader.ui.TechHeader
import com.tikitoki.downloader.ui.TechMuted
import com.tikitoki.downloader.ui.TechPanel
import com.tikitoki.downloader.ui.TechPink
import com.tikitoki.downloader.ui.TechPurple
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.lifecycle.viewmodel.compose.viewModel

private const val PLAYLIST_PREVIEW_LIMIT = 35
private const val MAX_PARALLEL_DOWNLOADS = 8
// Tab constants from MainNavigationBar
private const val TOOLS_TAB = 3
private const val SETTINGS_TAB = 5
private const val MUSIC_TAB = 6
private const val COLLECTIONS_TAB = 7
private const val ROCKOLA_TAB = 8

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Suppress(
    "ktlint:standard:function-naming",
    "LongMethod",
    "CyclomaticComplexMethod",
    "TooGenericExceptionCaught",
)
@Composable
internal fun AppNavigationContent(
    viewModel: MainViewModel,
    context: Context,
    settings: SharedPreferences,
    focusManager: FocusManager,
    keyboard: androidx.compose.ui.platform.SoftwareKeyboardController?,
    clipboard: ClipboardManager,
    scope: CoroutineScope,
    folderPicker: androidx.activity.result.ActivityResultLauncher<Intent>,
    storagePermission: androidx.activity.result.ActivityResultLauncher<String>,
    notificationsPermission: androidx.activity.result.ActivityResultLauncher<String>,
    diagnosticPicker: androidx.activity.compose.ManagedActivityResultLauncher<String, Uri?>,
) {
    // Collect all state from ViewModel
    var tab by viewModel.tab.collectAsState()
    var musicRequestUri by viewModel.musicRequestUri.collectAsState()
    var musicRequestTitle by viewModel.musicRequestTitle.collectAsState()
    var gridMode by viewModel.gridMode.collectAsState()
    var selectedPlatform by viewModel.selectedPlatform.collectAsState()
    var deleteCandidate by viewModel.deleteCandidate.collectAsState()
    val monitorRevision = viewModel.monitorRevision.collectAsState().value
    var url by viewModel.url.collectAsState()
    var search by viewModel.search.collectAsState()
    var onlyUndownloaded by viewModel.onlyUndownloaded.collectAsState()
    var libraryKind by viewModel.libraryKind.collectAsState()
    var message by viewModel.message.collectAsState()
    var forceRefresh by viewModel.forceRefresh.collectAsState()
    var qualityVideos by viewModel.qualityVideos.collectAsState()
    var showAbout by viewModel.showAbout.collectAsState()
    var showPlayerChoice by viewModel.showPlayerChoice.collectAsState()
    var monitorHours by viewModel.monitorHours.collectAsState()
    var monitorPages by viewModel.monitorPages.collectAsState()
    var preferredQuality by viewModel.preferredQuality.collectAsState()
    var themeChoice by viewModel.themeChoice.collectAsState()
    val downloadFolder = viewModel.downloadFolder.collectAsState().value
    var wifiParallel by viewModel.wifiParallel.collectAsState()
    var meteredParallel by viewModel.meteredParallel.collectAsState()
    var allowMetered by viewModel.allowMetered.collectAsState()
    var networkSettingsBusy by viewModel.networkSettingsBusy.collectAsState()
    var rateKiB by viewModel.rateKiB.collectAsState()
    var showProgressNotice by viewModel.showProgressNotice.collectAsState()
    var completionNotice by viewModel.completionNotice.collectAsState()
    var showClearTemp by viewModel.showClearTemp.collectAsState()
    val analysisRevision = viewModel.analysisRevision.collectAsState().value

    val profileViewModel = viewModel.profileViewModel
    val profileState = viewModel.profileState.collectAsState()
    val dao = viewModel.dao
    val downloadsViewModel = viewModel.downloadsViewModel
    val records = viewModel.records.collectAsState()
    val downloadSpeeds = viewModel.downloadSpeeds.collectAsState()
    val galleryImageLoader = viewModel.galleryImageLoader
    val thumbnailResolver = viewModel.thumbnailResolver
    val universalSource = viewModel.universalSource
    val avatarResolver = viewModel.avatarResolver
    val folderDescription = viewModel.folderDescription
    val invalidLinkMessage = viewModel.invalidLinkMessage
    val inspecting = viewModel.inspecting.collectAsState().value
    val queueBusy = viewModel.queueBusy.collectAsState().value
    val videos = profileState.value.videos
    val selected = remember { mutableStateMapOf<String, Boolean>() }

    // Keep selected in sync with ViewModel
    val viewModelSelected = viewModel.selected.collectAsState()
    androidx.compose.runtime.LaunchedEffect(viewModelSelected.value) {
        selected.clear()
        selected.putAll(viewModelSelected.value)
    }

    val chooseQuality = { viewModel.chooseQuality() }
    val openPlayback = { record: DownloadRecord -> viewModel.openPlayback(record) }
    val onExportDiagnostic = { filename: String -> diagnosticPicker.launch(filename) }
    val onChooseFolder = { folderPicker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)) }
    val onRequestNotificationPermission = { notificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }

    Scaffold(
        modifier = Modifier.imePadding(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { SafeAreaTopBar { } },
        bottomBar = {
            MainNavigationBar(
                selectedTab = tab,
                onMusic = { tab = MUSIC_TAB; viewModel.setTab(MUSIC_TAB) },
                onRockola = { tab = ROCKOLA_TAB; viewModel.setTab(ROCKOLA_TAB) },
                onNavigate = { target ->
                    focusManager.clearFocus()
                    keyboard?.hide()
                    tab = target
                    viewModel.setTab(target)
                },
            )
        },
    ) { insets ->
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
        val visibleVideos =
            remember(videos, search, onlyUndownloaded, downloadedIds) {
                videos.filter {
                    (search.isBlank() || it.title.contains(search, ignoreCase = true) || it.id.contains(search)) &&
                        (!onlyUndownloaded || it.id !in downloadedIds)
                }
            }

        if (tab == 0) {
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
                        .padding(insets)
                        .consumeWindowInsets(insets),
                contentPadding =
                    androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        TechHeader(onSettings = { tab = SETTINGS_TAB; viewModel.setTab(SETTINGS_TAB) })

                        HomeLinkInput(
                            selectedPlatform = selectedPlatform,
                            url = url,
                            loading = profileState.value.loading,
                            loadingMore = profileState.value.loadingMore,
                            onPlatformChange = { selectedPlatform = it; viewModel.setSelectedPlatform(it) },
                            onUrlChange = { newUrl ->
                                val extracted =
                                    if (newUrl.length - url.length > 1) {
                                        IncomingLinkValidator.extractFirstHttps(newUrl)
                                    } else {
                                        null
                                    }
                                url = extracted?.url ?: newUrl
                                viewModel.setUrl(url)
                                profileViewModel.reset()
                                selected.clear()
                                viewModel.clearSelected()
                                message = ""
                                viewModel.setMessage("")
                            },
                            onKeyboardDone = {
                                focusManager.clearFocus()
                                keyboard?.hide()
                            },
                            onPaste = {
                                focusManager.clearFocus()
                                keyboard?.hide()
                                val pasted = clipboard.getText()?.text.orEmpty()
                                val link =
                                    IncomingLinkValidator.validate(pasted)
                                        ?: IncomingLinkValidator.extractFirstHttps(pasted)
                                if (link != null) {
                                    url = link.url
                                    viewModel.setUrl(url)
                                    profileViewModel.reset()
                                    selected.clear()
                                    viewModel.clearSelected()
                                    message = ""
                                    viewModel.setMessage("")
                                    viewModel.incrementAnalysisRevision()
                                } else {
                                    message = "El portapapeles no contiene un enlace válido."
                                    viewModel.setMessage(message)
                                }
                            },
                            onAnalyze = {
                                focusManager.clearFocus()
                                keyboard?.hide()
                                when (IncomingLinkValidator.validate(url)) {
                                    null -> {
                                        message = invalidLinkMessage
                                        viewModel.setMessage(message)
                                    }
                                    else -> {
                                        selected.clear()
                                        viewModel.clearSelected()
                                        profileViewModel.inspect(url, forceRefresh)
                                        forceRefresh = false
                                        viewModel.setForceRefresh(false)
                                        message = ""
                                        viewModel.setMessage("")
                                    }
                                }
                            },
                        )

                        if (profileState.value.loading || profileState.value.loadingMore || profileState.value.countingTotal) {
                            AnalysisAnimation(
                                found = videos.size,
                                scanning = profileState.value.countingTotal || profileState.value.loadingMore,
                            )
                        }
                        if (queueBusy) CircularProgressIndicator()
                        profileState.value.error?.let { error ->
                            Column {
                                Text(error, color = MaterialTheme.colorScheme.error)
                                if (profileState.value.hasMore && !profileState.value.countingTotal) {
                                    OutlinedButton(onClick = { profileViewModel.resumeScan() }) {
                                        Text("↻ Reanudar escaneo")
                                    }
                                }
                                if (profileState.value.incompletePage && !profileState.value.countingTotal) {
                                    OutlinedButton(onClick = { profileViewModel.skipIncompletePage() }) {
                                        Text("Continuar con la siguiente parte")
                                    }
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
                                            Modifier
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(17.dp))
                                                .background(TechGradient),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (profileState.value.profileImage != null) {
                                                SubcomposeAsyncImage(
                                                    model =
                                                        ImageRequest.Builder(LocalContext.current)
                                                            .data(profileState.value.profileImage)
                                                            .addHeader("Referer", "https://www.tiktok.com/")
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
                                                    .take(PLAYLIST_PREVIEW_LIMIT)
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
                                            enabled = !profileState.value.countingTotal && profileState.value.hasMore,
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
                                                (
                                                    profileState.value.totalVideos?.toString()
                                                        ?: if (profileState.value.countingTotal) "…" else "—"
                                                ),
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
                                    viewModel.clearSelected()
                                    if (!allSelected) videos.forEach { selected[it.id] = true; viewModel.toggleSelected(it.id, true) }
                                }) {
                                    Text(
                                        if (allSelected) "Quitar selección" else "Seleccionar todo",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Button(
                                    modifier = Modifier.weight(1f),
                                    enabled = selected.isNotEmpty() && !queueBusy && !inspecting,
                                    onClick = chooseQuality,
                                ) {
                                    Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Descargar ${selected.size}", maxLines = 1)
                                }
                            }

                            OutlinedTextField(
                                value = search,
                                onValueChange = { search = it; viewModel.setSearch(it) },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Buscar videos") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    focusManager.clearFocus()
                                    keyboard?.hide()
                                }),
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FilterChip(
                                    selected = onlyUndownloaded,
                                    onClick = { onlyUndownloaded = !onlyUndownloaded; viewModel.setOnlyUndownloaded(!onlyUndownloaded) },
                                    label = { Text("Pendientes") },
                                )
                                FilterChip(
                                    selected = gridMode,
                                    onClick = { gridMode = true; viewModel.setGridMode(true) },
                                    label = { Text("Cuadrícula") },
                                )
                                FilterChip(
                                    selected = !gridMode,
                                    onClick = { gridMode = false; viewModel.setGridMode(false) },
                                    label = { Text("Lista") },
                                )
                            }
                        }
                    }
                }
                gridItems(visibleVideos, key = { it.id }) { video ->
                    val checked = video.id in selected
                    Card(
                        onClick = {
                            if (checked) {
                                selected.remove(video.id)
                                viewModel.toggleSelected(video.id, false)
                            } else {
                                selected[video.id] = true
                                viewModel.toggleSelected(video.id, true)
                            }
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
                                        if (MediaLinkParser.parse(video.url)?.platform == MediaPlatform.TIKTOK) {
                                            PORTRAIT_VIDEO_RATIO
                                        } else {
                                            LANDSCAPE_VIDEO_RATIO
                                        },
                                    ),
                                ) {
                                    VideoThumbnail(
                                        video,
                                        thumbnailResolver,
                                        Modifier.fillMaxSize(),
                                        deferLookup = profileState.value.countingTotal,
                                    )
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = null,
                                        modifier =
                                            Modifier
                                                .align(Alignment.TopEnd)
                                                .background(Color.Black.copy(alpha = .65f), RoundedCornerShape(8.dp))
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
                                        if (MediaLinkParser.parse(video.url)?.platform == MediaPlatform.TIKTOK) {
                                            PORTRAIT_VIDEO_RATIO
                                        } else {
                                            LANDSCAPE_VIDEO_RATIO
                                        },
                                    ),
                                ) {
                                    VideoThumbnail(
                                        video,
                                        thumbnailResolver,
                                        Modifier.fillMaxSize(),
                                        deferLookup = profileState.value.countingTotal,
                                    )
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = null,
                                        modifier =
                                            Modifier
                                                .align(Alignment.TopEnd)
                                                .background(Color.Black.copy(alpha = .65f), RoundedCornerShape(8.dp))
                                                .size(36.dp),
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        if (video.id in downloadedIds) {
                                            "✓ Audio y video"
                                        } else if (video.id in completedKinds) {
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
        } else if (tab == 1) {
            DownloadQueueScreen(
                records = records.value,
                themeChoice = themeChoice,
                insets = insets,
                message = message,
                thumbnailResolver = thumbnailResolver,
                downloadSpeeds = downloadSpeeds.value,
                onSettings = { tab = SETTINGS_TAB; viewModel.setTab(SETTINGS_TAB) },
                onPause = {
                    scope.launch {
                        downloadsViewModel.pauseQueue()
                        message = "Cola pausada."
                        viewModel.setMessage(message)
                    }
                },
                onResume = {
                    scope.launch {
                        downloadsViewModel.resumeQueue()
                        message = "Cola reanudada."
                        viewModel.setMessage(message)
                    }
                },
                onCancel = { id ->
                    scope.launch {
                        downloadsViewModel.cancelDownload(id)
                        message = "Descarga cancelada."
                        viewModel.setMessage(message)
                    }
                },
            )
        } else if (tab == 2) {
            LibraryScreen(
                records = records.value,
                themeChoice = themeChoice,
                insets = insets,
                libraryKind = libraryKind,
                message = message,
                thumbnailResolver = thumbnailResolver,
                galleryImageLoader = galleryImageLoader,
                onSettings = { tab = SETTINGS_TAB; viewModel.setTab(SETTINGS_TAB) },
                onKindChange = { libraryKind = it; viewModel.setLibraryKind(it) },
                onMusic = { tab = MUSIC_TAB; viewModel.setTab(MUSIC_TAB) },
                onCollections = { tab = COLLECTIONS_TAB; viewModel.setTab(COLLECTIONS_TAB) },
                onOpen = { viewModel.openPlayback(it) },
                onDelete = { deleteCandidate = it; viewModel.setDeleteCandidate(it) },
            )
        } else if (tab == COLLECTIONS_TAB) {
            CollectionsScreen(
                modifier = Modifier.padding(insets).consumeWindowInsets(insets),
                onBack = { tab = 2; viewModel.setTab(2) },
                onOpenPlaylist = { link ->
                    url = link
                    viewModel.setUrl(link)
                    profileViewModel.reset()
                    selected.clear()
                    viewModel.clearSelected()
                    message = ""
                    viewModel.setMessage("")
                    tab = 0
                    viewModel.setTab(0)
                    viewModel.incrementAnalysisRevision()
                },
            )
        } else if (tab == ROCKOLA_TAB) {
            RockolaScreen(
                source = universalSource,
                thumbnailResolver = thumbnailResolver,
                onDownload = { video -> qualityVideos = listOf(video); viewModel.setQualityVideos(listOf(video)) },
                onOpenYouTube = { video ->
                    video.rockolaVideoId()?.let { videoId ->
                        val watchUri = Uri.parse("https://www.youtube.com/watch?v=$videoId")
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, watchUri))
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(context, "No hay una aplicación para abrir YouTube.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.padding(insets).consumeWindowInsets(insets),
            )
        } else {
            val scrollState = rememberScrollState()
            Column(
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
                        .padding(insets)
                        .consumeWindowInsets(insets)
                        .verticalScroll(scrollState)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                TechHeader(onSettings = { tab = SETTINGS_TAB; viewModel.setTab(SETTINGS_TAB) })
                when (tab) {
                    MUSIC_TAB -> MusicScreen(
                        requestedUri = musicRequestUri,
                        requestedTitle = musicRequestTitle,
                        onRequestConsumed = { musicRequestUri = null; viewModel.setMusicRequestUri(null) },
                    )
                    TOOLS_TAB -> {
                        val monitored = remember(monitorRevision) { ProfileMonitor.profiles(context).sorted() }
                        ToolsScreen(
                            monitored = monitored,
                            failedCount = records.value.count { it.status == DownloadState.FAILED },
                            currentUrl = url,
                            profileImage = profileState.value.profileImage,
                            avatarResolver = avatarResolver,
                            message = message,
                            onRetryFailed = {
                                scope.launch {
                                    DownloadQueue.retryFailed(context)
                                    tab = 1
                                    viewModel.setTab(1)
                                    message = "Reintentando descargas."
                                    viewModel.setMessage(message)
                                }
                            },
                            onClearErrors = { scope.launch { dao.clearFailed() } },
                            onOpenPlayer = { showPlayerChoice = true; viewModel.setShowPlayerChoice(true) },
                            onFollowProfile = {
                                viewModel.onFollowProfile(url)
                            },
                            onUnfollowProfile = { profile ->
                                viewModel.onUnfollowProfile(profile)
                            },
                        )
                    }
                    SETTINGS_TAB -> {
                        SettingsGeneralSection(
                            monitorHours = monitorHours,
                            monitorPages = monitorPages,
                            folderDescription = folderDescription,
                            hasCustomFolder = downloadFolder != null,
                            message = message,
                            onAbout = { showAbout = true; viewModel.setShowAbout(true) },
                            onExportDiagnostic = {
                                onExportDiagnostic(context.getString(R.string.diagnostic_filename))
                            },
                            onMonitorHoursChange = { hours ->
                                monitorHours = hours
                                viewModel.setMonitorHours(hours)
                            },
                            onMonitorPagesChange = { pages ->
                                monitorPages = pages
                                viewModel.setMonitorPages(pages)
                            },
                            onUpdateExtractor = {
                                ExtractorUpdateWorker.updateNow(context)
                                message = "Actualización solicitada. Se ejecutará sin descargas pendientes."
                                viewModel.setMessage(message)
                            },
                            onChooseFolder = { onChooseFolder() },
                            onResetFolder = {
                                viewModel.onResetFolder()
                            },
                        )
                        SettingsAppearanceSection(
                            themeChoice = themeChoice,
                            preferredQuality = preferredQuality,
                            onThemeChange = { option ->
                                themeChoice = option
                                viewModel.setThemeChoice(option)
                            },
                            onQualityChange = { option ->
                                preferredQuality = option
                                viewModel.setPreferredQuality(option)
                            },
                        )
                        DownloadSettingsSection(
                            wifiParallel = wifiParallel,
                            meteredParallel = meteredParallel,
                            allowMetered = allowMetered,
                            networkSettingsBusy = networkSettingsBusy,
                            rateKiB = rateKiB,
                            showProgressNotice = showProgressNotice,
                            completionNotice = completionNotice,
                            onWifiChange = {
                                wifiParallel = it.roundToInt().coerceIn(1, MAX_PARALLEL_DOWNLOADS)
                                viewModel.setWifiParallel(wifiParallel)
                            },
                            onMeteredChange = {
                                meteredParallel = it.roundToInt().coerceIn(1, MAX_PARALLEL_DOWNLOADS)
                                viewModel.setMeteredParallel(meteredParallel)
                            },
                            onAllowMeteredChange = { allowed ->
                                networkSettingsBusy = true
                                viewModel.setNetworkSettingsBusy(true)
                                viewModel.setAllowMetered(allowed)
                            },
                            onRateChange = { rate ->
                                rateKiB = rate
                                viewModel.setRateKiB(rate)
                            },
                            onProgressNoticeChange = {
                                showProgressNotice = it
                                viewModel.setShowProgressNotice(it)
                                if (it &&
                                    Build.VERSION.SDK_INT >= 33 &&
                                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                                        PackageManager.PERMISSION_GRANTED
                                ) {
                                    onRequestNotificationPermission()
                                }
                            },
                            onCompletionNoticeChange = {
                                completionNotice = it
                                viewModel.setCompletionNotice(it)
                                if (it &&
                                    Build.VERSION.SDK_INT >= 33 &&
                                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                                        PackageManager.PERMISSION_GRANTED
                                ) {
                                    onRequestNotificationPermission()
                                }
                            },
                        )
                        SettingsStorageSection(
                            message = message,
                            onClearTemporary = { showClearTemp = true; viewModel.setShowClearTemp(true) },
                        )
                    }
                    else -> {
                        Text("Archivos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "Administra el contenido guardado en tu dispositivo",
                            color = TechMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(8.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors =
                                androidx.compose.material3.CardDefaults
                                    .cardColors(containerColor = TechPanel),
                            border = BorderStroke(1.dp, TechBorder),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "Carpeta de descarga",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(folderDescription, color = TechMuted)
                                OutlinedButton(onClick = {
                                    viewModel.openDownloadFolder()
                                }) { Text("Abrir carpeta de Baja.pe") }
                            }
                        )
                        Text(
                            "${records.value.count { it.status == DownloadState.COMPLETED }} archivos guardados",
                            color = TechMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (message.startsWith("No se pudo abrir")) Text(message, color = TechMuted)
                    }
                }
            }
        }
    }
}