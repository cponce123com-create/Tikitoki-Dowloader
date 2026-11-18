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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProgressIndicatorDefaults
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
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
import com.tikitoki.downloader.ui.MusicScreen
import com.tikitoki.downloader.ui.ProfileViewModel
import com.tikitoki.downloader.ui.ProfileUiState
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
import com.tikitoki.downloader.ui.VideoThumbnail
import com.tikitoki.downloader.MainNavigationBar
import com.tikitoki.downloader.COLLECTIONS_TAB
import com.tikitoki.downloader.ui.AppNavHost
import com.tikitoki.downloader.ui.NavRoutes
import com.tikitoki.downloader.ui.rememberAppNavController
import com.tikitoki.downloader.rememberDiagnosticPicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import androidx.compose.foundation.lazy.grid.items as gridItems

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
                    androidx.compose.foundation.Image(
                        painter = painterResource(R.drawable.bajape_logo),
                        contentDescription = "Baja.pe",
                        modifier = Modifier.size(280.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
            } else {
                MainNavHost(sharedLink, shareRevision)
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
private fun MainNavHost(
    incomingLink: String,
    shareRevision: Int,
) {
    val context = LocalContext.current
    val settings = remember(context) { context.getSharedPreferences("tikitoki_settings", android.content.Context.MODE_PRIVATE) }
    FirstRunNotice(settings)

    // State variables
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

    val defaultFormat =
        remember(preferredQuality) {
            val selector =
                when (preferredQuality) {
                    "720p" -> "bestvideo[height<=720]+bestaudio/best[height<=720]/best"
                    "480p" -> "bestvideo[height<=480]+bestaudio/best[height<=480]/best"
                    else -> TikTokVideoSource.DEFAULT_FORMAT
                }
            FormatChoice(selector, preferredQuality)
        }
    var tab by rememberSaveable { mutableStateOf(0) }
    var musicRequestUri by rememberSaveable { mutableStateOf<String?>(null) }
    var musicRequestTitle by rememberSaveable { mutableStateOf("") }

    // Navigation state
    val navController = rememberAppNavController()
    var gridMode by rememberSaveable { mutableStateOf(true) }
    var selectedPlatform by remember { mutableStateOf<String?>("Auto") }
    var deleteCandidate by remember { mutableStateOf<DownloadRecord?>(null) }
    var monitorRevision by remember { mutableStateOf(0) }
    var url by rememberSaveable { mutableStateOf(incomingLink) }
    var search by rememberSaveable { mutableStateOf("") }
    var onlyUndownloaded by rememberSaveable { mutableStateOf(false) }
    var historyFilter by rememberSaveable { mutableStateOf("TODOS") }
    var libraryKind by rememberSaveable { mutableStateOf("Videos") }
    var message by remember { mutableStateOf("") }
    var inspecting by remember { mutableStateOf(false) }
    var forceRefresh by rememberSaveable { mutableStateOf(false) }
    var formatsBusy by remember { mutableStateOf(false) }
    var queueBusy by remember { mutableStateOf(false) }
    var showQualitySheet by remember { mutableStateOf(false) }
    var formatError by remember { mutableStateOf(false) }
    var batchCheckJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var batchGeneration by remember { mutableStateOf(0) }
    var batchChecking by remember { mutableStateOf(false) }
    var batchChecked by remember { mutableStateOf(0) }
    var batchFailed by remember { mutableStateOf(0) }
    var batchTotal by remember { mutableStateOf(0) }
    var batchCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var rememberFormat by remember { mutableStateOf(false) }
    val videos = profileState.videos
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    var formats by remember { mutableStateOf(listOf(defaultFormat)) }
    var chosenFormat by remember(defaultFormat) { mutableStateOf(defaultFormat) }

    val diagnosticPicker =
        rememberDiagnosticPicker {
            message = context.getString(R.string.diagnostic_error)
        }

    // Navigation callbacks
    val onNavigateToDownloads = { navController.navigate(NavRoutes.DOWNLOADS) }
    val onNavigateToSettings = { navController.navigate(NavRoutes.SETTINGS) }
    val onShowClearTemp = { showClearTemp = true }
    val onShowAbout = { showAbout = true }

    // Permission launchers
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

    // Download actions
    val addToQueue: () -> Unit = {
        if (!queueBusy && selected.isNotEmpty()) {
            queueBusy = true
            val batch = videos.filter { it.id in selected }
            val formatId = chosenFormat.id
            scope.launch {
                try {
                    val result = DownloadQueue.enqueue(context, batch, formatId)
                    message = "${result.added} videos añadidos. Duplicados omitidos: ${result.duplicates}."
                    if (result.added > 0 || result.duplicates > 0) batch.forEach { selected.remove(it.id) }
                    if (selected.isEmpty()) navController.navigate(NavRoutes.DOWNLOADS)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    message = "No se pudo crear la cola. Inténtalo otra vez."
                } finally {
                    queueBusy = false
                }
            }
        }
    }

    val requestDownload: () -> Unit = {
        focusManager.clearFocus()
        keyboard?.hide()
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
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
            formats = if (selected.size == 1) listOf(defaultFormat) else FormatOptions.batch(context)
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
                    e: kotlinx.coroutines.CancellationException,
                ) {
                    throw e
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    runCatching { DiagnosticHistory.record(context, e.message) }
                    message = "No se pudieron cargar las opciones de calidad. Inténtalo otra vez."
                    formatError = true
                } finally {
                    formatsBusy = false
                }
            }
        }
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
                        navController.navigate(NavRoutes.MUSIC)
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
    }

    // Incoming link handling
    LaunchedEffect(incomingLink, shareRevision) {
        if (incomingLink.isNotBlank() && (shareRevision > 0 || !profileViewModel.isCurrentLink(incomingLink))) {
            url = incomingLink
            profileViewModel.reset()
            selected.clear()
            formats = listOf(defaultFormat)
            chosenFormat = defaultFormat
            message = ""
            navController.navigate(NavRoutes.HOME)
        }
    }
    LaunchedEffect(url) {
        val link = IncomingLinkValidator.validate(url)?.url ?: return@LaunchedEffect
        delay(500)
        if (!profileViewModel.isCurrentLink(link)) profileViewModel.inspect(link, false)
    }

    // Navigation back handler
    BackHandler(enabled = navController.currentBackStackEntryAsState().value?.destination?.route?.let { it == NavRoutes.MUSIC || it == NavRoutes.COLLECTIONS } == true) {
        navController.popBackStack()
    }

    // Map routes to tab indices for MainNavigationBar
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route ?: NavRoutes.HOME
    val selectedTab = when {
        currentRoute.startsWith(NavRoutes.HOME) -> 0
        currentRoute.startsWith(NavRoutes.DOWNLOADS) -> 1
        currentRoute.startsWith(NavRoutes.LIBRARY) -> 2
        currentRoute.startsWith(NavRoutes.TOOLS) -> 3
        currentRoute.startsWith(NavRoutes.FILES) -> 4
        currentRoute.startsWith(NavRoutes.SETTINGS) -> 5
        currentRoute.startsWith(NavRoutes.MUSIC) -> 6
        currentRoute.startsWith(NavRoutes.COLLECTIONS) -> COLLECTIONS_TAB
        else -> 0
    }

    // Render the navigation host with Scaffold
    MaterialTheme(colorScheme = TikitokiColors) {
        Scaffold(
            modifier = Modifier.imePadding(),
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets.safeDrawing,
            topBar = { SafeAreaTopBar { } },
            bottomBar = {
                MainNavigationBar(
                    selectedTab = selectedTab,
                    onMusic = { navController.navigate(NavRoutes.MUSIC) },
                    onNavigate = { target ->
                        focusManager.clearFocus()
                        keyboard?.hide()
                        when (target) {
                            0 -> navController.navigate(NavRoutes.HOME) { popUpTo(NavRoutes.HOME) { inclusive = false }; launchSingleTop = true; restoreState = true }
                            1 -> navController.navigate(NavRoutes.DOWNLOADS) { popUpTo(NavRoutes.DOWNLOADS) { inclusive = false }; launchSingleTop = true; restoreState = true }
                            2 -> navController.navigate(NavRoutes.LIBRARY) { popUpTo(NavRoutes.LIBRARY) { inclusive = false }; launchSingleTop = true; restoreState = true }
                            3 -> navController.navigate(NavRoutes.TOOLS) { popUpTo(NavRoutes.TOOLS) { inclusive = false }; launchSingleTop = true; restoreState = true }
                            4 -> navController.navigate(NavRoutes.FILES) { popUpTo(NavRoutes.FILES) { inclusive = false }; launchSingleTop = true; restoreState = true }
                            5 -> navController.navigate(NavRoutes.SETTINGS) { popUpTo(NavRoutes.SETTINGS) { inclusive = false }; launchSingleTop = true; restoreState = true }
                            6 -> navController.navigate(NavRoutes.MUSIC)
                            COLLECTIONS_TAB -> navController.navigate(NavRoutes.COLLECTIONS)
                        }
                    },
                )
            },
        ) { insets ->
            AppNavHost(
                navController = navController,
                startDestination = NavRoutes.HOME,
                onNavigateToDownloads = onNavigateToDownloads,
                onNavigateToSettings = onNavigateToSettings,
                onShowClearTemp = onShowClearTemp,
                onShowAbout = onShowAbout,
                themeChoice = themeChoice,
                preferredQuality = preferredQuality,
                focusManager = focusManager,
                keyboard = keyboard,
                clipboard = clipboard,
                incomingLink = incomingLink,
                shareRevision = shareRevision,
                context = context,
            settings = settings,
            source = source,
            thumbnailResolver = thumbnailResolver,
            avatarResolver = avatarResolver,
            galleryImageLoader = galleryImageLoader,
            universalSource = universalSource,
            profileViewModel = profileViewModel,
            profileState = profileState,
            dao = dao,
            downloadsViewModel = downloadsViewModel,
            records = records,
            downloadSpeeds = downloadSpeeds,
            finishedBatches = finishedBatches,
            scope = scope,
            folderDescription = folderDescription,
            downloadFolder = downloadFolder,
            folderPicker = folderPicker,
            networkSettingsBusy = networkSettingsBusy,
            wifiParallel = wifiParallel,
            meteredParallel = meteredParallel,
            allowMetered = allowMetered,
            rateKiB = rateKiB,
            showProgressNotice = showProgressNotice,
            completionNotice = completionNotice,
            monitorHours = monitorHours,
            monitorPages = monitorPages,
            libraryKind = libraryKind,
            url = url,
            monitorRevision = monitorRevision,
            message = message,
            inspecting = inspecting,
            storagePermission = storagePermission,
            notificationsPermission = notificationsPermission,
            diagnosticPicker = diagnosticPicker,
            deleteCandidate = deleteCandidate,
            showClearTemp = showClearTemp,
            showPlayerChoice = showPlayerChoice,
            showAbout = showAbout,
            musicRequestUri = musicRequestUri,
            musicRequestTitle = musicRequestTitle,
            onMusicRequestConsumed = { musicRequestUri = null },
            onOpenFile = openPlayback,
            onDelete = { deleteCandidate = it },
            onKindChange = { libraryKind = it },
            onRetryFailed = {
                scope.launch {
                    DownloadQueue.retryFailed(context)
                    navController.navigate(NavRoutes.DOWNLOADS)
                    message = "Reintentando descargas."
                }
            },
            onClearErrors = { scope.launch { dao.clearFailed() } },
            onFollowProfile = {
                if (ProfileMonitor.add(context, url.trim())) {
                    if (Build.VERSION.SDK_INT >= 33 &&
                        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
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
            onUpdateExtractor = {
                ExtractorUpdateWorker.updateNow(context)
                message = "Actualización solicitada. Se ejecutará sin descargas pendientes."
            },
            onChooseFolder = { folderPicker.launch(null) },
            onResetFolder = {
                settings.edit().remove("download_tree").apply()
                downloadFolder = null
            },
            onMonitorHoursChange = { hours ->
                monitorHours = hours
                ProfileMonitor.setInterval(context, hours)
            },
            onMonitorPagesChange = { pages ->
                monitorPages = pages
                ProfileMonitor.setPageLimit(context, pages)
            },
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
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onCompletionNoticeChange = {
                completionNotice = it
                settings.edit().putBoolean("notify_completion", it).apply()
                if (it &&
                    Build.VERSION.SDK_INT >= 33 &&
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onThemeChange = { option ->
                themeChoice = option
                settings.edit().putString("theme", option).apply()
            },
            onQualityChange = { option ->
                preferredQuality = option
                settings.edit().putString("quality", option).apply()
            },
            onSettings = { navController.navigate(NavRoutes.SETTINGS) },
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
            onOpenPlayer = { showPlayerChoice = true },
        )

        // Dialogs rendered at the top level (inside Scaffold content)
        if (showAbout) {
            AlertDialog(
                onDismissRequest = { showAbout = false },
                title = { Text(stringResource(R.string.about_title)) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.about_description))
                        Text(stringResource(R.string.about_license))
                        Text(stringResource(R.string.about_credits))
                        Text(stringResource(R.string.about_privacy))
                    }
                },
                confirmButton = { Button(onClick = { showAbout = false }) { Text(stringResource(R.string.about_close)) } },
                dismissButton = {
                    OutlinedButton(onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/cponce123com-create/Tikitoki-Dowloader")),
                            )
                        }
                    }) { Text(stringResource(R.string.about_sources)) }
                },
            )
        }
        if (showPlayerChoice) {
            AlertDialog(
                onDismissRequest = { showPlayerChoice = false },
                title = { Text("Reproductor") },
                text = { Text("Elige qué quieres reproducir.") },
                confirmButton = {
                    Button(onClick = {
                        showPlayerChoice = false
                        libraryKind = "Videos"
                        navController.navigate(NavRoutes.LIBRARY)
                    }) { Text("Videos") }
                },
                dismissButton = {
                    OutlinedButton(onClick = {
                        showPlayerChoice = false
                        navController.navigate(NavRoutes.MUSIC)
                    }) { Text("Música") }
                },
            )
        }
        if (showQualitySheet && selected.isNotEmpty()) {
            val preview = videos.firstOrNull { selected.size == 1 && it.id == selected.keys.first() }
            ModalBottomSheet(
                onDismissRequest = {
                    batchGeneration++
                    batchCheckJob?.cancel()
                    batchChecking = false
                    showQualitySheet = false
                },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = TechBackground,
            ) {
                Column(
                    Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Opciones de descarga",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        if (preview != null) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                VideoThumbnail(
                                    preview,
                                    thumbnailResolver,
                                    Modifier.width(144.dp).aspectRatio(
                                        if (MediaLinkParser.parse(preview.url)?.platform == MediaPlatform.TIKTOK) {
                                            9f / 16f
                                        } else {
                                            16f / 9f
                                        },
                                    ),
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        preview.title,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text("${preview.durationSeconds}s", color = TechMuted)
                                }
                            }
                        }
                        Text("Formato disponible", style = MaterialTheme.typography.titleMedium)
                        if (selected.size > 1) {
                            Text(stringResource(R.string.format_batch_note), color = TechMuted)
                        }
                        if (batchChecking) {
                            CircularProgressIndicator(color = TechPink)
                        }
                        if (formatError) {
                            Text(stringResource(R.string.format_check_failed), color = MaterialTheme.colorScheme.error)
                        }
                        Column {
                            formats.forEach { option ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { chosenFormat = option }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = chosenFormat.id == option.id,
                                        onClick = { chosenFormat = option },
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(option.label)
                                        if (selected.size > 1 && batchChecked > 0) {
                                            Text(
                                                stringResource(R.string.format_check_matches, batchCounts[option.id] ?: 0, batchChecked),
                                                color = TechMuted,
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(checked = rememberFormat, onCheckedChange = { rememberFormat = it })
                        Text("Usar como opción predeterminada")
                    }
                    TechPrimaryButton(
                        "Descargar",
                        icon = Icons.Outlined.Download,
                        enabled = !queueBusy && !formatsBusy && !formatError,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            batchGeneration++
                            batchCheckJob?.cancel()
                            batchChecking = false
                            if (rememberFormat) {
                                val preference = DownloadFormatPreference.fromChoice(chosenFormat.id, chosenFormat.label)
                                settings.edit().putString("default_download_format", preference).apply()
                            }
                            showQualitySheet = false
                            requestDownload()
                        },
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.height(18.dp))
                }
            }
        }
        finishedBatches.firstOrNull()?.let { batch ->
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Descargas terminadas") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SummaryTile("Total", batch.total, Modifier.weight(1f))
                            SummaryTile("Éxitos", batch.completed, Modifier.weight(1f))
                            SummaryTile("Errores", batch.failed, Modifier.weight(1f))
                        }
                        if (batch.cancelled > 0) {
                            Text("Cancelados: ${batch.cancelled}", color = TechMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        if (batch.duplicates > 0) {
                            Text("Duplicados omitidos: ${batch.duplicates}", color = TechMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = { Button(onClick = { scope.launch { dao.acknowledgeBatch(batch.batchId) } }) { Text("Aceptar") } },
            )
        }
        if (showClearTemp) {
            AlertDialog(
                onDismissRequest = { showClearTemp = false },
                title = { Text("Limpiar archivos temporales") },
                text = {
                    Text(
                        "Se borrarán solo los archivos temporales de Baja.pe. Los fragmentos guardados para reanudar podrían perderse. Los videos descargados se conservan.",
                    )
                },
                confirmButton = {
                    Button(onClick = {
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
                                message = "No se puede limpiar mientras haya descargas en curso, pendientes o pausadas."
                            }
                        }
                    }) { Text("Limpiar") }
                },
                dismissButton = { OutlinedButton(onClick = { showClearTemp = false }) { Text("Cancelar") } },
            )
        }
        deleteCandidate?.let { item ->
            AlertDialog(
                onDismissRequest = { deleteCandidate = null },
                title = { Text("Eliminar video") },
                text = { Text("¿Eliminar este video del dispositivo y del historial de Baja.pe? Esta acción no se puede deshacer.") },
                confirmButton = {
                    Button(onClick = {
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
                    }) { Text("Eliminar") }
                },
                dismissButton = { OutlinedButton(onClick = { deleteCandidate = null }) { Text("Cancelar") } },
            )
        }
    }
    }
}

@Composable
private fun FirstRunNotice(settings: android.content.SharedPreferences) {
    val hasRun = settings.getBoolean("has_run", false)
    if (!hasRun) {
        LaunchedEffect(Unit) {
            settings.edit().putBoolean("has_run", true).apply()
        }
    }
}

@Composable
private fun SummaryTile(label: String, value: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
            .background(TechPanel),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, TechBorder),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label, color = TechMuted, style = MaterialTheme.typography.labelSmall)
            Text(value.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
}