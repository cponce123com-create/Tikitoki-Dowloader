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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.tikitoki.downloader.data.DeleteCompletedResult
import com.tikitoki.downloader.data.DiagnosticHistory
import com.tikitoki.downloader.data.DownloadDatabase
import com.tikitoki.downloader.data.DownloadQueue
import com.tikitoki.downloader.data.DownloadRecord
import com.tikitoki.downloader.data.DownloadSettings
import com.tikitoki.downloader.data.DownloadState
import com.tikitoki.downloader.data.DownloadTelemetry
import com.tikitoki.downloader.data.ErrorCategory
import com.tikitoki.downloader.data.ExtractorUpdateWorker
import com.tikitoki.downloader.data.ExtractorVersion
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
import com.tikitoki.downloader.ui.DownloadsViewModel
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
    BackHandler(enabled = tab == 6) { tab = 2 }
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
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { destination ->
            if (destination != null) {
                scope.launch {
                    try {
                        val text =
                            withContext(Dispatchers.IO) {
                                val status = context.getSharedPreferences("extractor_status", android.content.Context.MODE_PRIVATE)
                                val unknown = context.getString(R.string.diagnostic_unknown)
                                val version =
                                    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
                                        ?: unknown
                                val engineVersion = ExtractorVersion.read(context) ?: unknown
                                val last = status.getLong("last_success", 0L)
                                val updated =
                                    if (last > 0) {
                                        java.text.DateFormat
                                            .getDateTimeInstance()
                                            .format(java.util.Date(last))
                                    } else {
                                        unknown
                                    }
                                val errors = DiagnosticHistory.read(context)
                                buildString {
                                    appendLine(context.getString(R.string.diagnostic_heading))
                                    appendLine(context.getString(R.string.diagnostic_app, version))
                                    appendLine(context.getString(R.string.diagnostic_engine, engineVersion))
                                    appendLine(context.getString(R.string.diagnostic_last_update, updated))
                                    appendLine(context.getString(R.string.diagnostic_android, Build.VERSION.RELEASE, Build.VERSION.SDK_INT))
                                    appendLine(context.getString(R.string.diagnostic_abi, Build.SUPPORTED_ABIS.joinToString(", ")))
                                    appendLine(context.getString(R.string.diagnostic_errors))
                                    if (errors.isEmpty()) appendLine(context.getString(R.string.diagnostic_none))
                                    errors.forEach { error ->
                                        val date =
                                            java.text.DateFormat
                                                .getDateTimeInstance()
                                                .format(java.util.Date(error.time))
                                        val category =
                                            when (error.category) {
                                                ErrorCategory.RATE_LIMIT -> R.string.diagnostic_rate_limit
                                                ErrorCategory.TIMEOUT -> R.string.diagnostic_timeout
                                                ErrorCategory.NETWORK -> R.string.diagnostic_network
                                                ErrorCategory.PERMISSION -> R.string.diagnostic_permission
                                                ErrorCategory.OTHER -> R.string.diagnostic_other
                                            }
                                        appendLine(context.getString(R.string.diagnostic_error_entry, date, context.getString(category)))
                                    }
                                }
                            }
                        withContext(Dispatchers.IO) {
                            val stream = context.contentResolver.openOutputStream(destination) ?: error("output unavailable")
                            stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                        }
                        val send =
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_STREAM, destination)
                                clipData = android.content.ClipData.newRawUri("diagnóstico", destination)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                        context.startActivity(Intent.createChooser(send, context.getString(R.string.diagnostic_share)))
                    } catch (
                        e: kotlinx.coroutines.CancellationException,
                    ) {
                        throw e
                    } catch (
                        e: Exception,
                    ) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        DiagnosticHistory.record(context, e.message)
                        message =
                            context.getString(R.string.diagnostic_error)
                    }
                }
            }
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
        delay(500)
        if (!profileViewModel.isCurrentLink(link)) profileViewModel.inspect(link, false)
    }

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
                    if (selected.isEmpty()) tab = 1
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
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
    }

    MaterialTheme(colorScheme = TikitokiColors) {
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
                        tab = 2
                    }) { Text("Videos") }
                },
                dismissButton = {
                    OutlinedButton(onClick = {
                        showPlayerChoice = false
                        tab = 6
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
                    showQualitySheet =
                        false
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
                                        if (MediaLinkParser.parse(preview.url)?.platform ==
                                            MediaPlatform.TIKTOK
                                        ) {
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
                            OutlinedButton(onClick = {
                                if (batchChecking) {
                                    batchGeneration++
                                    batchCheckJob?.cancel()
                                    batchChecking = false
                                } else {
                                    val items = videos.filter { it.id in selected }
                                    var presets = formats.toList()
                                    val checkedItems = mutableListOf<List<FormatChoice>>()
                                    val knownHeights = mutableSetOf<Int>()
                                    batchChecked = 0
                                    batchFailed = 0
                                    batchTotal = items.size
                                    batchCounts = emptyMap()
                                    batchChecking = true
                                    val generation = ++batchGeneration
                                    batchCheckJob =
                                        scope.launch {
                                            try {
                                                for (item in items) {
                                                    try {
                                                        val found =
                                                            if (item.id.contains(
                                                                    ":",
                                                                )
                                                            ) {
                                                                universalSource.formats(item)
                                                            } else {
                                                                source.formats(item)
                                                            }
                                                        if (found.isEmpty()) {
                                                            batchFailed++
                                                            continue
                                                        }
                                                        checkedItems.add(found)
                                                        val discovered = found.mapNotNull { it.height }.filter { it in 1..16384 }
                                                        val heightsChanged = knownHeights.addAll(discovered)
                                                        if (heightsChanged) {
                                                            presets = FormatOptions.batch(context, knownHeights)
                                                            formats = presets
                                                            batchCounts =
                                                                presets.associate { preset ->
                                                                    preset.id to
                                                                        checkedItems.count { FormatAvailability.matches(preset.id, it) }
                                                                }
                                                        } else {
                                                            batchCounts =
                                                                batchCounts.toMutableMap().apply {
                                                                    presets.forEach { preset ->
                                                                        if (FormatAvailability.matches(preset.id, found)) {
                                                                            this[preset.id] =
                                                                                (this[preset.id] ?: 0) + 1
                                                                        }
                                                                    }
                                                                }
                                                        }
                                                        batchChecked++
                                                    } catch (
                                                        e: kotlinx.coroutines.CancellationException,
                                                    ) {
                                                        throw e
                                                    } catch (
                                                        e: Exception,
                                                    ) {
                                                        if (e is kotlinx.coroutines.CancellationException) throw e
                                                        DiagnosticHistory.record(context, e.message)
                                                        batchFailed++
                                                    }
                                                }
                                            } finally {
                                                if (generation == batchGeneration) batchChecking = false
                                            }
                                        }
                                }
                            }) { Text(stringResource(if (batchChecking) R.string.format_check_cancel else R.string.format_check_batch)) }
                            if (batchTotal >
                                0
                            ) {
                                Text(
                                    stringResource(
                                        R.string.format_check_status,
                                        batchChecked + batchFailed,
                                        batchTotal,
                                        batchFailed + (batchTotal - batchChecked - batchFailed),
                                    ),
                                    color = TechMuted,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        if (formatsBusy) CircularProgressIndicator(color = TechPurple)
                        if (formatError) Text(stringResource(R.string.format_check_failed), color = MaterialTheme.colorScheme.error)
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
                                        if (selected.size > 1 &&
                                            batchChecked > 0
                                        ) {
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
                        Checkbox(checked = rememberFormat, onCheckedChange = { rememberFormat = it })
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
                    Spacer(Modifier.height(18.dp))
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
                        if (batch.cancelled >
                            0
                        ) {
                            Text("Cancelados: ${batch.cancelled}", color = TechMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        if (batch.duplicates >
                            0
                        ) {
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
                                message =
                                    "No se puede limpiar mientras haya descargas en curso, pendientes o pausadas."
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
        Scaffold(
            modifier = Modifier.imePadding(),
            contentWindowInsets = WindowInsets.safeDrawing,
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
                            TechHeader(onSettings = { tab = 5 })

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
                                    formats = listOf(defaultFormat)
                                    chosenFormat = defaultFormat
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
                                        formats = listOf(defaultFormat)
                                        chosenFormat = defaultFormat
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
                                        formats = listOf(defaultFormat)
                                        chosenFormat = defaultFormat
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
                                formats = listOf(defaultFormat)
                                chosenFormat = defaultFormat
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
                    onOpen = { openPlayback(it) },
                    onDelete = { deleteCandidate = it },
                )
            } else {
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
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    TechHeader(onSettings = { tab = 5 })
                    when (tab) {
                        6 ->
                            MusicScreen(
                                requestedUri = musicRequestUri,
                                requestedTitle = musicRequestTitle,
                                onRequestConsumed = { musicRequestUri = null },
                            )
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
                        5 -> {
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
                                    Text("Carpeta de descarga", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                                                    setDataAndType(folder, android.provider.DocumentsContract.Document.MIME_TYPE_DIR)
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
}
