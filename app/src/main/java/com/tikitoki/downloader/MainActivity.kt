package com.tikitoki.downloader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.maxLineSpan
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.tikitoki.downloader.data.DeleteCompletedResult
import com.tikitoki.downloader.data.DiagnosticHistory
import com.tikitoki.downloader.data.DownloadDatabase
import com.tikitoki.downloader.data.DownloadQueue
import com.tikitoki.downloader.data.DownloadRecord
import com.tikitoki.downloader.data.DownloadSettings
import com.tikitoki.downloader.data.DownloadState
import com.tikitoki.downloader.data.DownloadTelemetry
import com.tikitoki.downloader.data.ExtractorUpdateWorker
import com.tikitoki.downloader.data.FormatAvailability
import com.tikitoki.downloader.data.FormatChoice
import com.tikitoki.downloader.data.FormatOptions
import com.tikitoki.downloader.data.ProfileAvatarResolver
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
import com.tikitoki.downloader.ui.AnalysisAnimation
import com.tikitoki.downloader.ui.AnalysisScreen
import com.tikitoki.downloader.ui.DownloadsViewModel
import com.tikitoki.downloader.ui.FilesScreen
import com.tikitoki.downloader.ui.HomeLinkInput
import com.tikitoki.downloader.ui.MusicScreen
import com.tikitoki.downloader.ui.ProfileViewModel
import com.tikitoki.downloader.ui.SafeAreaTopBar
import com.tikitoki.downloader.ui.TechBackground
import com.tikitoki.downloader.ui.TechBorder
import com.tikitoki.downloader.ui.TechGradient
import com.tikitoki.downloader.ui.TechHeader
import com.tikitoki.downloader.ui.TechMuted
import com.tikitoki.downloader.ui.TechPanel
import com.tikitoki.downloader.ui.TechPink
import com.tikitoki.downloader.ui.TechPrimaryButton
import com.tikitoki.downloader.ui.TechPurple
import com.tikitoki.downloader.ui.ToolsScreen
import com.tikitoki.downloader.ui.VideoThumbnail
import com.tikitoki.downloader.ui.LibraryScreen
import com.tikitoki.downloader.ui.DownloadQueueScreen
import com.tikitoki.downloader.ui.SettingsGeneralSection
import com.tikitoki.downloader.ui.SettingsAppearanceSection
import com.tikitoki.downloader.ui.SettingsStorageSection
import com.tikitoki.downloader.ui.DownloadSettingsSection
import com.tikitoki.downloader.ui.AboutDialog
import com.tikitoki.downloader.ui.ClearTempDialog
import com.tikitoki.downloader.ui.DeleteConfirmDialog
import com.tikitoki.downloader.ui.FinishedBatchDialog
import com.tikitoki.downloader.ui.PlayerChoiceDialog
import com.tikitoki.downloader.MainNavigationBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private var sharedLink by mutableStateOf("")
    private var shareRevision by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        sharedLink = receivedText(intent)
        ExtractorUpdateWorker.schedule(applicationContext)
        ProfileMonitor.schedule(applicationContext)
        lifecycleScope.launch {
            val pending =
                withContext(Dispatchers.IO) {
                    DownloadDatabase.get(applicationContext).downloads().pendingRecoveryCount()
                }
            if (pending > 0) DownloadQueue.start(applicationContext)
        }
        setContent {
            var showBrand by rememberSaveable { mutableStateOf(savedInstanceState == null) }
            if (showBrand) {
                LaunchedEffect(Unit) {
                    delay(1000)
                    showBrand = false
                }
                Box(Modifier.fillMaxSize().background(TechBackground), contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(R.drawable.bajape_logo),
                        contentDescription = "Baja.pe",
                        modifier = Modifier.size(280.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
            } else {
                TikitokiApp(sharedLink, shareRevision)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedLink = receivedText(intent)
        shareRevision++
    }

    private fun receivedText(intent: Intent?): String = IncomingIntentReader.read(intent)
}

private val TikitokiColors =
    darkColorScheme(
        primary = TechPurple,
        onPrimary = Color.White,
        secondary = TechPink,
        background = TechBackground,
        surface = TechPanel,
        onSurface = Color(0xFFF4F4F7),
    )

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Suppress("ktlint:standard:function-naming")
@Composable
private fun TikitokiApp(
    incomingLink: String,
    shareRevision: Int,
) {
    val context = LocalContext.current
    val settings = remember(context) { context.getSharedPreferences("tikitoki_settings", android.content.Context.MODE_PRIVATE) }
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
    var downloadFolder by remember { mutableStateOf(settings.getString("download_tree", null)) }
    var libraryKind by rememberSaveable { mutableStateOf("Videos") }
    var url by rememberSaveable { mutableStateOf(incomingLink) }

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
                    if (e is kotlinx.coroutines.CancellationException) throw e
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
    BackHandler(enabled = tab == 6) { tab = 2 }
    var deleteCandidate by remember { mutableStateOf<DownloadRecord?>(null) }
    var monitorRevision by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf("") }
    var inspecting by remember { mutableStateOf(false) }

    val storagePermission =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            // This will be handled by the AnalysisScreen internally
        }
    val notificationsPermission =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { /* Notificaciones opcionales: las descargas siguen aunque se denieguen. */ }

    val diagnosticPicker =
        rememberDiagnosticPicker {
            message = context.getString(R.string.diagnostic_error)
        }

    MaterialTheme(colorScheme = TikitokiColors) {
        if (showAbout) {
            AboutDialog(onDismiss = { showAbout = false })
        }
        if (showPlayerChoice) {
            PlayerChoiceDialog(
                onDismiss = { showPlayerChoice = false },
                onVideosClick = { showPlayerChoice = false; libraryKind = "Videos"; tab = 2 },
                onMusicClick = { showPlayerChoice = false; tab = 6 },
            )
        }
        // Finished batches dialog
        finishedBatches.firstOrNull()?.let { batch ->
            FinishedBatchDialog(
                batch = batch,
                onDismiss = { scope.launch { dao.acknowledgeBatch(batch.batchId) } },
            )
        }
        if (showClearTemp) {
            ClearTempDialog(
                onDismiss = { showClearTemp = false },
                onConfirm = {
                    showClearTemp = false
                    scope.launch {
                        try {
                            val deleted = DownloadQueue.clearTemporary(context)
                            message = "Archivos temporales eliminados: $deleted."
                        } catch (
                            e: Exception,
                        ) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            DiagnosticHistory.record(context, e.message)
                            message =
                                "No se puede limpiar mientras haya descargas en curso, pendientes o pausadas."
                        }
                    }
                },
            )
        }
        deleteCandidate?.let { item ->
            DeleteConfirmDialog(
                item = item,
                onDismiss = { deleteCandidate = null },
                onConfirm = {
                    deleteCandidate = null
                    scope.launch {
                        try {
                            message =
                                when (DownloadQueue.deleteCompleted(context, item.videoId)) {
                                    DeleteCompletedResult.DELETED -> "Video eliminado del dispositivo y del historial."
                                    DeleteCompletedResult.FILE_MISSING -> "El archivo ya no existía. Se eliminó del historial."
                                    DeleteCompletedResult.FAILED -> "No se pudo eliminar el archivo. El registro se conserva."
                                }
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            message = "No se pudo eliminar el archivo. Comprueba que siga disponible."
                        }
                    }
                },
            )
        }
        Scaffold(
            modifier = Modifier.imePadding(),
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets.safeDrawing,
            topBar = { SafeAreaTopBar { } },
            bottomBar = {
                MainNavigationBar(
                    selectedTab = tab,
                    onMusic = { tab = 6 },
                    onNavigate = { target ->
                        focusManager.clearFocus()
                        keyboard?.hide()
                        tab = target
                    },
                )
            },
        ) { insets ->
            when (tab) {
                0 -> {
                    AnalysisScreen(
                        incomingLink = incomingLink,
                        shareRevision = shareRevision,
                        onNavigateToDownloads = { tab = 1 },
                        onNavigateToSettings = { tab = 5 },
                        onShowQualitySheet = { /* handled internally */ },
                        onShowClearTemp = { showClearTemp = true },
                        onShowAbout = { showAbout = true },
                        themeChoice = themeChoice,
                        preferredQuality = preferredQuality,
                        onSettingsChanged = { /* settings are shared via SharedPreferences */ },
                        insets = insets,
                    )
                }
                1 -> {
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
                }
                2 -> {
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
                        onOpen = { record ->
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
                                                                .query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                                                                ?.use { if (it.moveToFirst()) it.getString(0) else null }
                                                        }.getOrNull()
                                                    type to (displayName ?: uri.lastPathSegment)
                                                } else {
                                                    null to uri.lastPathSegment
                                                }
                                            }
                                        if (PlaybackKind.isAudio(record.formatId, mime, name)) {
                                            musicRequestUri = saved
                                            musicRequestTitle = record.title
                                            tab = 6
                                        } else {
                                            context.startActivity(
                                                Intent(context, PlayerActivity::class.java).apply {
                                                    data = uri
                                                    putExtra("title", record.title)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                },
                                            )
                                        }
                                    } catch (
                                        e: kotlinx.coroutines.CancellationException,
                                    ) {
                                        throw e
                                    } catch (e: Exception) {
                                        if (e is kotlinx.coroutines.CancellationException) throw e
                                        runCatching { DiagnosticHistory.record(context, e.message) }
                                        message = "No se pudo abrir el reproductor."
                                    }
                                }
                            }
                        },
                        onDelete = { deleteCandidate = it },
                    )
                }
                3 -> {
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
                                    notificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
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
                4 -> {
                    FilesScreen(
                        themeChoice = themeChoice,
                        folderDescription = folderDescription,
                        downloadFolder = downloadFolder,
                        message = message,
                        onSettingsClick = { tab = 5 },
                        onChooseFolder = { folderPicker.launch(null) },
                        onResetFolder = {
                            settings.edit().remove("download_tree").apply()
                            downloadFolder = null
                        },
                        onOpenFolder = {
                            try {
                                val folder =
                                    downloadFolder?.let { tree ->
                                        val treeUri = Uri.parse(tree)
                                        DocumentsContract.buildDocumentUriUsingTree(
                                            treeUri,
                                            DocumentsContract.getTreeDocumentId(treeUri),
                                        )
                                    }
                                        ?: DocumentsContract.buildDocumentUri(
                                            "com.android.externalstorage.documents",
                                            "primary:Download/TikitokiDownloader",
                                        )
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(folder, DocumentsContract.Document.MIME_TYPE_DIR)
                                        addFlags(
                                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                                        )
                                    },
                                )
                            } catch (
                                e: Exception,
                            ) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                DiagnosticHistory.record(context, e.message)
                                message =
                                    "No se pudo abrir la carpeta. Busca $folderDescription en tu administrador de archivos."
                            }
                        },
                        completedCount = records.count { it.status == DownloadState.COMPLETED },
                        settings = settings,
                    )
                }
                5 -> {
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
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        TechHeader(onSettings = { tab = 5 })
                        SettingsGeneralSection(
                            monitorHours = monitorHours,
                            monitorPages = monitorPages,
                            folderDescription = folderDescription,
                            hasCustomFolder = downloadFolder != null,
                            message = message,
                            onAbout = { showAbout = true },
                            onExportDiagnostic = { diagnosticPicker.launch(context.getString(R.string.diagnostic_filename)) },
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
                            onChooseFolder = { folderPicker.launch(null) },
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
                                wifiParallel = it.roundToInt().coerceIn(1, 8)
                                settings.edit().putInt("wifi_parallel", wifiParallel).apply()
                            },
                            onMeteredChange = {
                                meteredParallel = it.roundToInt().coerceIn(1, 8)
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
                                        if (e is kotlinx.coroutines.CancellationException) throw e
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
                                    notificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
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
                                    notificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                        )
                        SettingsStorageSection(
                            message = message,
                            onClearTemporary = { showClearTemp = true },
                        )
                    }
                }
                6 -> {
                    MusicScreen(
                        requestedUri = musicRequestUri,
                        requestedTitle = musicRequestTitle,
                        onRequestConsumed = { musicRequestUri = null },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun FirstRunNotice(settings: android.content.SharedPreferences) {
    val hasRun = settings.getBoolean("has_run_before", false)
    if (!hasRun) {
        val scope = rememberCoroutineScope()
        AlertDialog(
            onDismissRequest = {
                settings.edit().putBoolean("has_run_before", true).apply()
            },
            title = { Text(stringResource(R.string.first_run_title)) },
            text = { Text(stringResource(R.string.first_run_description)) },
            confirmButton = {
                Button(onClick = {
                    settings.edit().putBoolean("has_run_before", true).apply()
                }) { Text(stringResource(R.string.first_run_accept)) }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    scope.launch {
                        val uri = Uri.parse("https://github.com/cponce123com-create/Tikitoki-Dowloader/blob/main/PRIVACY.md")
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        try {
                            LocalContext.current.startActivity(intent)
                        } catch (e: Exception) {
                            // Ignore
                        }
                    }
                }) { Text(stringResource(R.string.first_run_privacy)) }
            },
        )
    }
}

@Composable
private fun SummaryTile(title: String, value: Int, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(title, style = MaterialTheme.typography.bodySmall, color = TechMuted)
    }
}