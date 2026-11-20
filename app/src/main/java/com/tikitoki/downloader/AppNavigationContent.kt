package com.tikitoki.downloader

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import com.tikitoki.downloader.data.DiagnosticHistory
import com.tikitoki.downloader.data.DownloadDao
import com.tikitoki.downloader.data.DownloadQueue
import com.tikitoki.downloader.data.DownloadRecord
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import androidx.compose.foundation.lazy.grid.items as gridItems

private const val PLAYLIST_PREVIEW_LIMIT = 35
private const val MAX_PARALLEL_DOWNLOADS = 8

internal class AppMutableValue<T>(
    private val read: () -> T,
    private val write: (T) -> Unit,
) : ReadWriteProperty<Any?, T> {
    override fun getValue(
        thisRef: Any?,
        property: KProperty<*>,
    ): T = read()

    override fun setValue(
        thisRef: Any?,
        property: KProperty<*>,
        value: T,
    ) {
        write(value)
    }
}

internal data class AppNavigationState(
    val context: Context,
    val settings: SharedPreferences,
    val tab: AppMutableValue<Int>,
    val musicRequestUri: AppMutableValue<String?>,
    val musicRequestTitle: AppMutableValue<String>,
    val gridMode: AppMutableValue<Boolean>,
    val selectedPlatform: AppMutableValue<String?>,
    val deleteCandidate: AppMutableValue<DownloadRecord?>,
    val monitorRevision: AppMutableValue<Int>,
    val url: AppMutableValue<String>,
    val search: AppMutableValue<String>,
    val onlyUndownloaded: AppMutableValue<Boolean>,
    val libraryKind: AppMutableValue<String>,
    val message: AppMutableValue<String>,
    val forceRefresh: AppMutableValue<Boolean>,
    val qualityVideos: AppMutableValue<List<VideoEntry>>,
    val showAbout: AppMutableValue<Boolean>,
    val showPlayerChoice: AppMutableValue<Boolean>,
    val monitorHours: AppMutableValue<Int>,
    val monitorPages: AppMutableValue<Int>,
    val preferredQuality: AppMutableValue<String>,
    val themeChoice: AppMutableValue<String>,
    val downloadFolder: AppMutableValue<String?>,
    val wifiParallel: AppMutableValue<Int>,
    val meteredParallel: AppMutableValue<Int>,
    val allowMetered: AppMutableValue<Boolean>,
    val networkSettingsBusy: AppMutableValue<Boolean>,
    val rateKiB: AppMutableValue<Int>,
    val showProgressNotice: AppMutableValue<Boolean>,
    val completionNotice: AppMutableValue<Boolean>,
    val showClearTemp: AppMutableValue<Boolean>,
    val analysisRevision: AppMutableValue<Int>,
    val focusManager: FocusManager,
    val keyboard: SoftwareKeyboardController?,
    val clipboard: ClipboardManager,
    val scope: CoroutineScope,
    val profileViewModel: ProfileViewModel,
    val profileState: ProfileUiState,
    val dao: DownloadDao,
    val downloadsViewModel: DownloadsViewModel,
    val records: List<DownloadRecord>,
    val downloadSpeeds: Map<String, String>,
    val galleryImageLoader: ImageLoader,
    val thumbnailResolver: VideoThumbnailResolver,
    val universalSource: UniversalMediaSource,
    val avatarResolver: ProfileAvatarResolver,
    val folderDescription: String,
    val invalidLinkMessage: String,
    val inspecting: Boolean,
    val queueBusy: Boolean,
    val videos: List<VideoEntry>,
    val selected: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Boolean>,
    val chooseQuality: () -> Unit,
    val openPlayback: (DownloadRecord) -> Unit,
    val onExportDiagnostic: (String) -> Unit,
    val onChooseFolder: () -> Unit,
    val onRequestNotificationPermission: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Suppress(
    "ktlint:standard:function-naming",
    "LongMethod",
    "CyclomaticComplexMethod",
    "TooGenericExceptionCaught",
)
@Composable
internal fun AppNavigationContent(state: AppNavigationState) {
    var tab by state.tab
    var musicRequestUri by state.musicRequestUri
    var musicRequestTitle by state.musicRequestTitle
    var gridMode by state.gridMode
    var selectedPlatform by state.selectedPlatform
    var deleteCandidate by state.deleteCandidate
    var monitorRevision by state.monitorRevision
    var url by state.url
    var search by state.search
    var onlyUndownloaded by state.onlyUndownloaded
    var libraryKind by state.libraryKind
    var message by state.message
    var forceRefresh by state.forceRefresh
    var qualityVideos by state.qualityVideos
    var showAbout by state.showAbout
    var showPlayerChoice by state.showPlayerChoice
    var monitorHours by state.monitorHours
    var monitorPages by state.monitorPages
    var preferredQuality by state.preferredQuality
    var themeChoice by state.themeChoice
    var downloadFolder by state.downloadFolder
    var wifiParallel by state.wifiParallel
    var meteredParallel by state.meteredParallel
    var allowMetered by state.allowMetered
    var networkSettingsBusy by state.networkSettingsBusy
    var rateKiB by state.rateKiB
    var showProgressNotice by state.showProgressNotice
    var completionNotice by state.completionNotice
    var showClearTemp by state.showClearTemp
    var analysisRevision by state.analysisRevision
    val context = state.context
    val settings = state.settings
    val focusManager = state.focusManager
    val keyboard = state.keyboard
    val clipboard = state.clipboard
    val scope = state.scope
    val profileViewModel = state.profileViewModel
    val profileState = state.profileState
    val dao = state.dao
    val downloadsViewModel = state.downloadsViewModel
    val records = state.records
    val downloadSpeeds = state.downloadSpeeds
    val galleryImageLoader = state.galleryImageLoader
    val thumbnailResolver = state.thumbnailResolver
    val universalSource = state.universalSource
    val avatarResolver = state.avatarResolver
    val folderDescription = state.folderDescription
    val invalidLinkMessage = state.invalidLinkMessage
    val inspecting = state.inspecting
    val queueBusy = state.queueBusy
    val videos = state.videos
    val selected = state.selected
    val chooseQuality = state.chooseQuality
    val openPlayback = state.openPlayback
    val onExportDiagnostic = state.onExportDiagnostic
    val onChooseFolder = state.onChooseFolder
    val onRequestNotificationPermission = state.onRequestNotificationPermission
    Scaffold(
        modifier = Modifier.imePadding(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { SafeAreaTopBar { } },
        bottomBar = {
            MainNavigationBar(
                selectedTab = tab,
                onMusic = { tab = 6 },
                onRockola = { tab = ROCKOLA_TAB },
                onNavigate = { target ->
                    focusManager.clearFocus()
                    keyboard?.hide()
                    tab = target
                },
            )
        },
    ) { insets ->
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
        if (tab == 0) {
            LazyVerticalGrid(
                columns = if (gridMode) GridCells.Adaptive(minSize = 112.dp) else GridCells.Fixed(1),
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            if (themeChoice ==
                                "Negro AMOLED"
                            ) {
                                Color.Black
                            } else {
                                TechBackground
                            },
                        ).padding(insets)
                        .consumeWindowInsets(insets),
                contentPadding =
                    androidx.compose.foundation.layout
                        .PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        TechHeader(onSettings = { tab = SETTINGS_TAB })

                        HomeLinkInput(
                            selectedPlatform = selectedPlatform,
                            url = url,
                            loading = profileState.loading,
                            loadingMore = profileState.loadingMore,
                            onPlatformChange = { selectedPlatform = it },
                            onUrlChange = {
                                val extracted =
                                    if (it.length - url.length >
                                        1
                                    ) {
                                        IncomingLinkValidator.extractFirstHttps(it)
                                    } else {
                                        null
                                    }
                                url = extracted?.url ?: it
                                profileViewModel.reset()
                                selected.clear()
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
                                val link =
                                    IncomingLinkValidator.validate(pasted)
                                        ?: IncomingLinkValidator.extractFirstHttps(pasted)
                                if (link != null) {
                                    url = link.url
                                    profileViewModel.reset()
                                    selected.clear()
                                    message = ""
                                    analysisRevision++
                                } else {
                                    message = "El portapapeles no contiene un enlace válido."
                                }
                            },
                            onAnalyze = {
                                focusManager.clearFocus()
                                keyboard?.hide()
                                when (IncomingLinkValidator.validate(url)) {
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
                            AnalysisAnimation(
                                found = videos.size,
                                scanning =
                                    profileState.countingTotal || profileState.loadingMore,
                            )
                        }
                        if (queueBusy) CircularProgressIndicator()
                        profileState.error?.let { error ->
                            Column {
                                Text(error, color = MaterialTheme.colorScheme.error)
                                if (profileState.hasMore && !profileState.countingTotal) {
                                    OutlinedButton(
                                        onClick = { profileViewModel.resumeScan() },
                                    ) { Text("↻ Reanudar escaneo") }
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
                                            Modifier
                                                .size(
                                                    34.dp,
                                                ).clip(RoundedCornerShape(17.dp))
                                                .background(TechGradient),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (profileState.profileImage != null) {
                                                coil.compose.SubcomposeAsyncImage(
                                                    model =
                                                        coil.request.ImageRequest
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
                                                Text(
                                                    "@",
                                                    fontSize = 18.sp,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                )
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
                                                (
                                                    profileState.totalVideos?.toString()
                                                        ?: if (profileState.countingTotal) "…" else "—"
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
                                    if (!allSelected) videos.forEach { selected[it.id] = true }
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
                                    Icon(
                                        Icons.Outlined.Download,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
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
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FilterChip(
                                    selected = onlyUndownloaded,
                                    onClick = { onlyUndownloaded = !onlyUndownloaded },
                                    label = { Text("Pendientes") },
                                )
                                FilterChip(
                                    selected = gridMode,
                                    onClick = { gridMode = true },
                                    label = { Text("Cuadrícula") },
                                )
                                FilterChip(
                                    selected = !gridMode,
                                    onClick = { gridMode = false },
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
                            if (checked) selected.remove(video.id) else selected[video.id] = true
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
                                        deferLookup = profileState.countingTotal,
                                    )
                                    Checkbox(
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
                                        deferLookup = profileState.countingTotal,
                                    )
                                    Checkbox(
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
        } else if (tab == 1) {
            DownloadQueueScreen(
                records = records,
                themeChoice = themeChoice,
                insets = insets,
                message = message,
                thumbnailResolver = thumbnailResolver,
                downloadSpeeds = downloadSpeeds,
                onSettings = { tab = 5 },
                onPause = {
                    scope.launch {
                        downloadsViewModel.pauseQueue()
                        message = "Cola pausada."
                    }
                },
                onResume = {
                    scope.launch {
                        downloadsViewModel.resumeQueue()
                        message = "Cola reanudada."
                    }
                },
                onCancel = { id ->
                    scope.launch {
                        downloadsViewModel.cancelDownload(id)
                        message = "Descarga cancelada."
                    }
                },
            )
        } else if (tab == 2) {
            LibraryScreen(
                records = records,
                themeChoice = themeChoice,
                insets = insets,
                libraryKind = libraryKind,
                message = message,
                thumbnailResolver = thumbnailResolver,
                galleryImageLoader = galleryImageLoader,
                onSettings = { tab = 5 },
                onKindChange = { libraryKind = it },
                onMusic = { tab = 6 },
                onCollections = { tab = COLLECTIONS_TAB },
                onOpen = { openPlayback(it) },
                onDelete = { deleteCandidate = it },
            )
        } else if (tab == COLLECTIONS_TAB) {
            CollectionsScreen(
                modifier = Modifier.padding(insets).consumeWindowInsets(insets),
                onBack = { tab = 2 },
                onOpenPlaylist = { link ->
                    url = link
                    profileViewModel.reset()
                    selected.clear()
                    message = ""
                    tab = 0
                    analysisRevision++
                },
            )
        } else {
            val scrollState = rememberScrollState()
            val scrollModifier = if (tab == ROCKOLA_TAB) Modifier else Modifier.verticalScroll(scrollState)
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            if (themeChoice ==
                                "Negro AMOLED"
                            ) {
                                Color.Black
                            } else {
                                TechBackground
                            },
                        ).padding(insets)
                        .consumeWindowInsets(insets)
                        .then(scrollModifier)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                TechHeader(onSettings = { tab = SETTINGS_TAB })
                when (tab) {
                    MUSIC_TAB ->
                        MusicScreen(
                            requestedUri = musicRequestUri,
                            requestedTitle = musicRequestTitle,
                            onRequestConsumed = { musicRequestUri = null },
                        )
                    ROCKOLA_TAB ->
                        RockolaScreen(
                            source = universalSource,
                            thumbnailResolver = thumbnailResolver,
                            onDownload = { video -> qualityVideos = listOf(video) },
                            onOpenYouTube = { video ->
                                val videoId = video.id.removePrefix("youtube:")
                                if (videoId.isBlank()) {
                                    message = "No se identificó el video de YouTube."
                                } else {
                                    context.startActivity(
                                        Intent(context, YouTubePlayerActivity::class.java)
                                            .putExtra(YouTubePlayerActivity.EXTRA_VIDEO_ID, videoId)
                                            .putExtra(YouTubePlayerActivity.EXTRA_TITLE, video.title),
                                    )
                                }
                            },
                        )
                    TOOLS_TAB -> {
                        val monitored = remember(monitorRevision) { ProfileMonitor.profiles(context).sorted() }
                        ToolsScreen(
                            monitored = monitored,
                            failedCount = records.count { it.status == DownloadState.FAILED },
                            currentUrl = url,
                            profileImage = profileState.profileImage,
                            avatarResolver = avatarResolver,
                            message = message,
                            onRetryFailed = {
                                scope.launch {
                                    DownloadQueue.retryFailed(context)
                                    tab = 1
                                    message = "Reintentando descargas."
                                }
                            },
                            onClearErrors = { scope.launch { dao.clearFailed() } },
                            onOpenPlayer = { showPlayerChoice = true },
                            onFollowProfile = {
                                if (ProfileMonitor.add(context, url.trim())) {
                                    if (Build.VERSION.SDK_INT >= 33 &&
                                        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                                        PackageManager.PERMISSION_GRANTED
                                    ) {
                                        onRequestNotificationPermission()
                                    }
                                    monitorRevision++
                                    message = "Perfil agregado al monitoreo."
                                } else {
                                    message = "Pega un enlace válido de perfil en Buscar."
                                }
                            },
                            onUnfollowProfile = { profile ->
                                ProfileMonitor.remove(context, profile)
                                monitorRevision++
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
                            onAbout = { showAbout = true },
                            onExportDiagnostic = {
                                onExportDiagnostic(
                                    context.getString(R.string.diagnostic_filename),
                                )
                            },
                            onMonitorHoursChange = { hours ->
                                monitorHours = hours
                                ProfileMonitor.setInterval(context, hours)
                            },
                            onMonitorPagesChange = { pages ->
                                monitorPages = pages
                                ProfileMonitor.setPageLimit(context, pages)
                            },
                            onUpdateExtractor = {
                                ExtractorUpdateWorker.updateNow(context)
                                message = "Actualización solicitada. Se ejecutará sin descargas pendientes."
                            },
                            onChooseFolder = { onChooseFolder() },
                            onResetFolder = {
                                settings.edit().remove("download_tree").apply()
                                downloadFolder = null
                            },
                        )
                        SettingsAppearanceSection(
                            themeChoice = themeChoice,
                            preferredQuality = preferredQuality,
                            onThemeChange = { option ->
                                themeChoice = option
                                settings.edit().putString("theme", option).apply()
                            },
                            onQualityChange = { option ->
                                preferredQuality = option
                                settings.edit().putString("quality", option).apply()
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
                                settings.edit().putInt("wifi_parallel", wifiParallel).apply()
                            },
                            onMeteredChange = {
                                meteredParallel = it.roundToInt().coerceIn(1, MAX_PARALLEL_DOWNLOADS)
                                settings.edit().putInt("metered_parallel", meteredParallel).apply()
                            },
                            onAllowMeteredChange = { allowed ->
                                networkSettingsBusy = true
                                scope.launch {
                                    try {
                                        val pending = DownloadQueue.changeMeteredPolicy(context, allowed)
                                        allowMetered = allowed
                                        message =
                                            if (pending) {
                                                "Cola pausada para cambiar la red. Pulsa Continuar en Descargas."
                                            } else {
                                                "Preferencia de red actualizada."
                                            }
                                    } catch (e: Exception) {
                                        rethrowIfFatal(e)
                                        DiagnosticHistory.record(context, e.message)
                                        message = "No se pudo cambiar la preferencia de red."
                                    } finally {
                                        networkSettingsBusy = false
                                    }
                                }
                            },
                            onRateChange = { rate ->
                                rateKiB = rate
                                settings.edit().putInt("rate_kib", rate).apply()
                            },
                            onProgressNoticeChange = {
                                showProgressNotice = it
                                settings.edit().putBoolean("notify_progress_detail", it).apply()
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
                                settings.edit().putBoolean("notify_completion", it).apply()
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
                            onClearTemporary = { showClearTemp = true },
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
                                    try {
                                        val folder =
                                            downloadFolder?.let { tree ->
                                                val treeUri = Uri.parse(tree)
                                                android.provider.DocumentsContract.buildDocumentUriUsingTree(
                                                    treeUri,
                                                    android.provider.DocumentsContract.getTreeDocumentId(treeUri),
                                                )
                                            }
                                                ?: android.provider.DocumentsContract.buildDocumentUri(
                                                    "com.android.externalstorage.documents",
                                                    "primary:Download/TikitokiDownloader",
                                                )
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(
                                                    folder,
                                                    android.provider.DocumentsContract.Document.MIME_TYPE_DIR,
                                                )
                                                addFlags(
                                                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                                                )
                                            },
                                        )
                                    } catch (
                                        e: Exception,
                                    ) {
                                        rethrowIfFatal(e)
                                        DiagnosticHistory.record(context, e.message)
                                        message =
                                            "No se pudo abrir la carpeta. " +
                                            "Busca $folderDescription en tu administrador de archivos."
                                    }
                                }) { Text("Abrir carpeta de Baja.pe") }
                            }
                        }
                        Text(
                            "${records.count { it.status == DownloadState.COMPLETED }} archivos guardados",
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
