package com.tikitoki.downloader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import com.tikitoki.downloader.data.DeleteCompletedResult
import com.tikitoki.downloader.data.DiagnosticHistory
import com.tikitoki.downloader.data.DownloadDatabase
import com.tikitoki.downloader.data.DownloadQueue
import com.tikitoki.downloader.data.DownloadRecord
import com.tikitoki.downloader.data.DownloadSelection
import com.tikitoki.downloader.data.DownloadSettings
import com.tikitoki.downloader.data.DownloadTelemetry
import com.tikitoki.downloader.data.ProfileAvatarResolver
import com.tikitoki.downloader.data.ProfileMonitor
import com.tikitoki.downloader.data.TikTokVideoSource
import com.tikitoki.downloader.data.UniversalMediaSource
import com.tikitoki.downloader.data.VideoEntry
import com.tikitoki.downloader.data.VideoThumbnailResolver
import com.tikitoki.downloader.data.rethrowIfFatal
import com.tikitoki.downloader.domain.DownloadFormatPreference
import com.tikitoki.downloader.domain.FacebookPhotoLinks
import com.tikitoki.downloader.domain.IncomingLinkValidator
import com.tikitoki.downloader.domain.PlaybackKind
import com.tikitoki.downloader.ui.DownloadsViewModel
import com.tikitoki.downloader.ui.ProfileViewModel
import com.tikitoki.downloader.ui.TechBackground
import com.tikitoki.downloader.ui.TechPanel
import com.tikitoki.downloader.ui.TechPink
import com.tikitoki.downloader.ui.TechPurple
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CLIPBOARD_DEBOUNCE_MS = 500L
private const val ON_SURFACE_COLOR = 0xFFF4F4F7

private val TikitokiColors =
    darkColorScheme(
        primary = TechPurple,
        onPrimary = Color.White,
        secondary = TechPink,
        background = TechBackground,
        surface = TechPanel,
        onSurface = Color(ON_SURFACE_COLOR),
    )

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Suppress(
    "ktlint:standard:function-naming",
    "LongMethod",
    "CyclomaticComplexMethod",
    "TooGenericExceptionCaught",
)
@Composable
internal fun AppContent(
    incomingLink: String,
    shareRevision: Int,
) {
    val context = LocalContext.current
    val settings =
        remember(context) { context.getSharedPreferences("tikitoki_settings", android.content.Context.MODE_PRIVATE) }
    FirstRunNotice(settings)
    var preferredQuality by remember { mutableStateOf(settings.getString("quality", "Máxima") ?: "Máxima") }
    var themeChoice by remember { mutableStateOf(settings.getString("theme", "Oscuro") ?: "Oscuro") }
    var wifiParallel by remember { mutableStateOf(DownloadSettings.wifiLimit(context)) }
    var meteredParallel by remember { mutableStateOf(DownloadSettings.meteredLimit(context)) }
    var allowMetered by remember { mutableStateOf(DownloadSettings.allowMetered(context)) }
    var showProgressNotice by remember { mutableStateOf(DownloadSettings.showProgress(context)) }
    var completionNotice by remember { mutableStateOf(DownloadSettings.notifyCompletion(context)) }
    var rateKiB by remember { mutableStateOf(DownloadSettings.rateKiB(context)) }
    var monitorHours by remember { mutableStateOf(ProfileMonitor.interval(context)) }
    var monitorPages by remember { mutableStateOf(ProfileMonitor.pageLimit(context)) }
    var networkSettingsBusy by remember { mutableStateOf(false) }
    var showClearTemp by remember { mutableStateOf(false) }
    var showPlayerChoice by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    val invalidLinkMessage = stringResource(R.string.media_invalid_link)
    var downloadFolder by remember { mutableStateOf(settings.getString("download_tree", null)) }

    // The producer assigns value; this Compose lint check does not recognize it with the current Kotlin version.
    @android.annotation.SuppressLint("ProduceStateDoesNotAssignValue")
    val folderDescription by produceState(initialValue = "Comprobando carpeta…", key1 = downloadFolder) {
        val tree = downloadFolder
        value =
            if (tree == null) {
                "Descargas/TikitokiDownloader"
            } else {
                withContext(Dispatchers.IO) {
                    runCatching {
                        val folder = DocumentFile.fromTreeUri(context, Uri.parse(tree))
                        if (folder != null && folder.exists() && folder.canWrite()) {
                            folder.name ?: "Carpeta personalizada"
                        } else {
                            "Carpeta no disponible. Elige otra ubicación."
                        }
                    }.getOrElse { "Carpeta no disponible. Elige otra ubicación." }
                }
            }
    }
    val folderPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                    settings.edit().putString("download_tree", uri.toString()).apply()
                    downloadFolder = uri.toString()
                } catch (
                    e: Exception,
                ) {
                    rethrowIfFatal(e)
                    DiagnosticHistory.record(context, e.message)
                    settings.edit().remove("download_tree").apply()
                }
            }
        }

    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val source = remember(context) { TikTokVideoSource(context.applicationContext) }
    val thumbnailResolver = remember(context) { VideoThumbnailResolver(context.applicationContext) }
    val avatarResolver = remember { ProfileAvatarResolver() }
    val galleryImageLoader =
        remember(context) {
            ImageLoader.Builder(context).components { add(VideoFrameDecoder.Factory()) }.build()
        }
    val universalSource = remember(context) { UniversalMediaSource(context.applicationContext) }
    val profileViewModel: ProfileViewModel = viewModel()
    val profileState by profileViewModel.state.collectAsState()
    val dao = remember(context) { DownloadDatabase.get(context).downloads() }
    val downloadsViewModel: DownloadsViewModel = viewModel()
    val records by downloadsViewModel.records.collectAsState()
    val downloadSpeeds by DownloadTelemetry.speeds.collectAsState()
    val finishedBatches by remember(dao) { dao.observeFinishedBatches() }.collectAsState(initial = emptyList())

    var tab by rememberSaveable { mutableStateOf(0) }
    var musicRequestUri by rememberSaveable { mutableStateOf<String?>(null) }
    var musicRequestTitle by rememberSaveable { mutableStateOf("") }
    BackHandler(enabled = tab == 6 || tab == COLLECTIONS_TAB || tab == ROCKOLA_TAB) { tab = 2 }
    var gridMode by rememberSaveable { mutableStateOf(true) }
    var selectedPlatform by remember { mutableStateOf<String?>("Auto") }
    var deleteCandidate by remember { mutableStateOf<DownloadRecord?>(null) }
    var monitorRevision by remember { mutableStateOf(0) }
    var url by rememberSaveable { mutableStateOf(incomingLink) }
    var search by rememberSaveable { mutableStateOf("") }
    var onlyUndownloaded by rememberSaveable { mutableStateOf(false) }
    var libraryKind by rememberSaveable { mutableStateOf("Videos") }
    var message by remember { mutableStateOf("") }
    var inspecting by remember { mutableStateOf(false) }
    var forceRefresh by rememberSaveable { mutableStateOf(false) }
    var queueBusy by remember { mutableStateOf(false) }
    var qualityVideos by remember { mutableStateOf(emptyList<VideoEntry>()) }
    var pendingDownloads by remember { mutableStateOf(emptyList<DownloadSelection>()) }
    val videos = profileState.videos
    val selected = remember { mutableStateMapOf<String, Boolean>() }

    val diagnosticPicker =
        rememberDiagnosticPicker {
            message = context.getString(R.string.diagnostic_error)
        }

    var analysisRevision by remember { mutableStateOf(0) }
    LaunchedEffect(incomingLink, shareRevision) {
        if (incomingLink.isNotBlank() && (shareRevision > 0 || !profileViewModel.isCurrentLink(incomingLink))) {
            url = incomingLink
            profileViewModel.reset()
            selected.clear()
            tab = 0
            message = ""
            analysisRevision++
        }
    }
    LaunchedEffect(url, analysisRevision) {
        val link = IncomingLinkValidator.validate(url)?.url ?: return@LaunchedEffect
        // Facebook profiles and photo share links belong to gallery-dl, not the video extractor.
        if (FacebookPhotoLinks.isPhotoCollection(link)) return@LaunchedEffect
        delay(CLIPBOARD_DEBOUNCE_MS)
        if (!profileViewModel.isCurrentLink(link)) profileViewModel.inspect(link, false)
    }

    val addToQueue: (List<DownloadSelection>) -> Unit = { items ->
        if (!queueBusy && items.isNotEmpty()) {
            queueBusy = true
            scope.launch {
                try {
                    val result = DownloadQueue.enqueue(context, items)
                    message = "${result.added} videos añadidos. Duplicados omitidos: ${result.duplicates}."
                    if (result.added > 0 || result.duplicates > 0) items.forEach { selected.remove(it.video.id) }
                    if (selected.isEmpty()) tab = 1
                } catch (e: Exception) {
                    rethrowIfFatal(e)
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
            val items = pendingDownloads
            pendingDownloads = emptyList()
            if (granted) {
                addToQueue(items)
            } else {
                message = "Android necesita permiso para guardar en Descargas."
            }
        }
    val notificationsPermission =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { /* Notificaciones opcionales: las descargas siguen aunque se denieguen. */ }

    val requestDownload: (List<DownloadSelection>) -> Unit = { items ->
        focusManager.clearFocus()
        keyboard?.hide()
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingDownloads = items
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            addToQueue(items)
        }
    }
    val chooseQuality: () -> Unit = {
        focusManager.clearFocus()
        keyboard?.hide()
        if (!queueBusy) qualityVideos = videos.filter { it.id in selected }
    }

    val openPlayback: (DownloadRecord) -> Unit = { record ->
        val saved = record.savedUri
        if (saved.isNullOrBlank()) {
            message = "No se encontró la ubicación del archivo."
        } else {
            scope.launch {
                try {
                    val uri = Uri.parse(saved)
                    val (mime, name) =
                        withContext(Dispatchers.IO) {
                            if (uri.scheme == "content") {
                                val type = runCatching { context.contentResolver.getType(uri) }.getOrNull()
                                val displayName =
                                    runCatching {
                                        context.contentResolver
                                            .query(
                                                uri,
                                                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                                                null,
                                                null,
                                                null,
                                            )?.use { if (it.moveToFirst()) it.getString(0) else null }
                                    }.getOrNull()
                                type to (displayName ?: uri.lastPathSegment)
                            } else {
                                null to uri.lastPathSegment
                            }
                        }
                    if (PlaybackKind.isAudio(record.formatId, mime, name)) {
                        musicRequestUri = saved
                        musicRequestTitle = record.title
                        tab = MUSIC_TAB
                    } else {
                        context.startActivity(
                            Intent(context, PlayerActivity::class.java).apply {
                                data = uri
                                putExtra("title", record.title)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            },
                        )
                    }
                } catch (e: Exception) {
                    rethrowIfFatal(e)
                    runCatching { DiagnosticHistory.record(context, e.message) }
                    message = "No se pudo abrir el reproductor."
                }
            }
        }
    }

    MaterialTheme(colorScheme = TikitokiColors) {
        AppDialogs(
            context = context,
            settings = settings,
            showAbout = showAbout,
            onDismissAbout = { showAbout = false },
            onOpenSources = {
                runCatching {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/cponce123com-create/Tikitoki-Dowloader"),
                        ),
                    )
                }
            },
            showPlayerChoice = showPlayerChoice,
            onDismissPlayerChoice = { showPlayerChoice = false },
            onChooseVideos = {
                showPlayerChoice = false
                libraryKind = "Videos"
                tab = 2
            },
            onChooseMusic = {
                showPlayerChoice = false
                tab = MUSIC_TAB
            },
            qualityVideos = qualityVideos,
            source = source,
            universalSource = universalSource,
            thumbnailResolver = thumbnailResolver,
            onDismissFormatSheet = { qualityVideos = emptyList() },
            onSaveFormat = { id, label, height ->
                val preference = DownloadFormatPreference.fromChoice(id, label, height)
                settings.edit().putString("default_download_format", preference).apply()
            },
            onRequestDownload = requestDownload,
            finishedBatch = finishedBatches.firstOrNull(),
            onAcknowledgeBatch = { batchId -> scope.launch { dao.acknowledgeBatch(batchId) } },
            showClearTemp = showClearTemp,
            onDismissClearTemp = { showClearTemp = false },
            onClearTemporary = {
                showClearTemp = false
                scope.launch {
                    try {
                        val deleted = DownloadQueue.clearTemporary(context)
                        message = "Archivos temporales eliminados: $deleted."
                    } catch (e: Exception) {
                        rethrowIfFatal(e)
                        DiagnosticHistory.record(context, e.message)
                        message = "No se puede limpiar mientras haya descargas en curso, pendientes o pausadas."
                    }
                }
            },
            deleteCandidate = deleteCandidate,
            onDismissDelete = { deleteCandidate = null },
            onDelete = { item ->
                deleteCandidate = null
                scope.launch {
                    try {
                        message =
                            when (DownloadQueue.deleteCompleted(context, item.videoId)) {
                                DeleteCompletedResult.DELETED -> "Video eliminado del dispositivo y del historial."
                                DeleteCompletedResult.FILE_MISSING ->
                                    "El archivo ya no existía. Se eliminó del historial."
                                DeleteCompletedResult.FAILED ->
                                    "No se pudo eliminar el archivo. El registro se conserva."
                            }
                    } catch (e: Exception) {
                        rethrowIfFatal(e)
                        message = "No se pudo eliminar el archivo. Comprueba que siga disponible."
                    }
                }
            },
        )
        AppNavigationContent(
            state =
                AppNavigationState(
                    context = context,
                    settings = settings,
                    tab = AppMutableValue({ tab }, { tab = it }),
                    musicRequestUri = AppMutableValue({ musicRequestUri }, { musicRequestUri = it }),
                    musicRequestTitle = AppMutableValue({ musicRequestTitle }, { musicRequestTitle = it }),
                    gridMode = AppMutableValue({ gridMode }, { gridMode = it }),
                    selectedPlatform = AppMutableValue({ selectedPlatform }, { selectedPlatform = it }),
                    deleteCandidate = AppMutableValue({ deleteCandidate }, { deleteCandidate = it }),
                    monitorRevision = AppMutableValue({ monitorRevision }, { monitorRevision = it }),
                    url = AppMutableValue({ url }, { url = it }),
                    search = AppMutableValue({ search }, { search = it }),
                    onlyUndownloaded = AppMutableValue({ onlyUndownloaded }, { onlyUndownloaded = it }),
                    libraryKind = AppMutableValue({ libraryKind }, { libraryKind = it }),
                    message = AppMutableValue({ message }, { message = it }),
                    forceRefresh = AppMutableValue({ forceRefresh }, { forceRefresh = it }),
                    qualityVideos = AppMutableValue({ qualityVideos }, { qualityVideos = it }),
                    showAbout = AppMutableValue({ showAbout }, { showAbout = it }),
                    showPlayerChoice = AppMutableValue({ showPlayerChoice }, { showPlayerChoice = it }),
                    monitorHours = AppMutableValue({ monitorHours }, { monitorHours = it }),
                    monitorPages = AppMutableValue({ monitorPages }, { monitorPages = it }),
                    preferredQuality = AppMutableValue({ preferredQuality }, { preferredQuality = it }),
                    themeChoice = AppMutableValue({ themeChoice }, { themeChoice = it }),
                    downloadFolder = AppMutableValue({ downloadFolder }, { downloadFolder = it }),
                    wifiParallel = AppMutableValue({ wifiParallel }, { wifiParallel = it }),
                    meteredParallel = AppMutableValue({ meteredParallel }, { meteredParallel = it }),
                    allowMetered = AppMutableValue({ allowMetered }, { allowMetered = it }),
                    networkSettingsBusy = AppMutableValue({ networkSettingsBusy }, { networkSettingsBusy = it }),
                    rateKiB = AppMutableValue({ rateKiB }, { rateKiB = it }),
                    showProgressNotice = AppMutableValue({ showProgressNotice }, { showProgressNotice = it }),
                    completionNotice = AppMutableValue({ completionNotice }, { completionNotice = it }),
                    showClearTemp = AppMutableValue({ showClearTemp }, { showClearTemp = it }),
                    analysisRevision = AppMutableValue({ analysisRevision }, { analysisRevision = it }),
                    focusManager = focusManager,
                    keyboard = keyboard,
                    clipboard = clipboard,
                    scope = scope,
                    profileViewModel = profileViewModel,
                    profileState = profileState,
                    dao = dao,
                    downloadsViewModel = downloadsViewModel,
                    records = records,
                    downloadSpeeds = downloadSpeeds,
                    galleryImageLoader = galleryImageLoader,
                    thumbnailResolver = thumbnailResolver,
                    universalSource = universalSource,
                    avatarResolver = avatarResolver,
                    folderDescription = folderDescription,
                    invalidLinkMessage = invalidLinkMessage,
                    inspecting = inspecting,
                    queueBusy = queueBusy,
                    videos = videos,
                    selected = selected,
                    chooseQuality = chooseQuality,
                    openPlayback = openPlayback,
                    onExportDiagnostic = { diagnosticPicker.launch(it) },
                    onChooseFolder = { folderPicker.launch(null) },
                    onRequestNotificationPermission = {
                        notificationsPermission.launch(
                            Manifest.permission.POST_NOTIFICATIONS,
                        )
                    },
                ),
        )
    }
}
