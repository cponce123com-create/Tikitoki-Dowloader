package com.tikitoki.downloader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.documentfile.provider.DocumentFile
import kotlin.math.roundToInt
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.IconButton
import coil.request.videoFrameMillis
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RadioButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tikitoki.downloader.data.DiagnosticHistory
import com.tikitoki.downloader.data.ExtractorVersion
import com.tikitoki.downloader.data.ErrorCategory
import com.tikitoki.downloader.data.DownloadTelemetry
import com.tikitoki.downloader.data.DownloadDatabase
import com.tikitoki.downloader.data.DownloadQueue
import com.tikitoki.downloader.data.DeleteCompletedResult
import com.tikitoki.downloader.data.DownloadSettings
import com.tikitoki.downloader.data.ExtractorUpdateWorker
import com.tikitoki.downloader.data.DownloadRecord
import com.tikitoki.downloader.data.DownloadState
import com.tikitoki.downloader.data.LibraryVisibility
import com.tikitoki.downloader.data.LibraryExport
import com.tikitoki.downloader.data.ProfileMonitor
import com.tikitoki.downloader.data.ProfileAvatarResolver
import com.tikitoki.downloader.data.ProfileIdentity
import com.tikitoki.downloader.data.FormatChoice
import com.tikitoki.downloader.data.FormatOptions
import com.tikitoki.downloader.data.FormatAvailability
import com.tikitoki.downloader.data.TikTokVideoSource
import com.tikitoki.downloader.data.VideoEntry
import com.tikitoki.downloader.data.VideoThumbnailResolver
import com.tikitoki.downloader.domain.LinkParseResult
import com.tikitoki.downloader.domain.VideoPagination
import com.tikitoki.downloader.domain.TikTokLinkParser
import com.tikitoki.downloader.domain.MediaPlatform
import com.tikitoki.downloader.domain.PlaybackKind
import com.tikitoki.downloader.domain.DownloadIdentity
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.rememberModalBottomSheetState
import com.tikitoki.downloader.domain.ThumbnailUrls
import com.tikitoki.downloader.domain.DownloadFormatPreference
import com.tikitoki.downloader.domain.MediaLinkParser
import com.tikitoki.downloader.domain.IncomingLinkValidator
import com.tikitoki.downloader.domain.IncomingIntentReader
import com.tikitoki.downloader.data.UniversalMediaSource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.lifecycleScope
import com.tikitoki.downloader.ui.AnalysisAnimation
import com.tikitoki.downloader.ui.TechHeader
import com.tikitoki.downloader.ui.MusicScreen
import com.tikitoki.downloader.ui.SafeAreaTopBar
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import com.tikitoki.downloader.ui.TechPlatformStrip
import com.tikitoki.downloader.ui.TechShortcut
import com.tikitoki.downloader.ui.TechPrimaryButton
import com.tikitoki.downloader.ui.TechPurple
import com.tikitoki.downloader.ui.TechPink
import com.tikitoki.downloader.ui.TechBackground
import com.tikitoki.downloader.ui.TechPanel
import com.tikitoki.downloader.ui.TechBorder
import com.tikitoki.downloader.ui.TechMuted
import com.tikitoki.downloader.ui.TechGradient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items as videoGridItems
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import com.tikitoki.downloader.ui.ProfileViewModel
import com.tikitoki.downloader.ui.DownloadsViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

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
            val pending = withContext(Dispatchers.IO) {
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

private val TikitokiColors = darkColorScheme(
    primary = TechPurple,
    onPrimary = Color.White,
    secondary = TechPink,
    background = TechBackground,
    surface = TechPanel,
    onSurface = Color(0xFFF4F4F7),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun TikitokiApp(incomingLink: String, shareRevision: Int) {
    val context = LocalContext.current
    val settings = remember(context) { context.getSharedPreferences("tikitoki_settings", android.content.Context.MODE_PRIVATE) }
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
        value = if (tree == null) "Descargas/TikitokiDownloader" else withContext(Dispatchers.IO) {
            runCatching {
                val folder = DocumentFile.fromTreeUri(context, Uri.parse(tree))
                if (folder != null && folder.exists() && folder.canWrite()) folder.name ?: "Carpeta personalizada"
                else "Carpeta no disponible. Elige otra ubicación."
            }.getOrElse { "Carpeta no disponible. Elige otra ubicación." }
        }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                settings.edit().putString("download_tree", uri.toString()).apply()
                downloadFolder = uri.toString()
            } catch (_: Exception) { settings.edit().remove("download_tree").apply() }
        }
    }

    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val source = remember(context) { TikTokVideoSource(context.applicationContext) }
    val thumbnailResolver = remember(context) { VideoThumbnailResolver(context.applicationContext) }
    val avatarResolver = remember { ProfileAvatarResolver() }
    val galleryImageLoader = remember(context) {
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

    val defaultFormat = remember(preferredQuality) {
        val selector = when (preferredQuality) {
            "720p" -> "bestvideo[height<=720]+bestaudio/best[height<=720]/best"
            "480p" -> "bestvideo[height<=480]+bestaudio/best[height<=480]/best"
            else -> TikTokVideoSource.DEFAULT_FORMAT
        }
        FormatChoice(selector, preferredQuality)
    }
    var tab by rememberSaveable { mutableStateOf(0) }
    var musicRequestUri by rememberSaveable { mutableStateOf<String?>(null) }
    var musicRequestTitle by rememberSaveable { mutableStateOf("") }
    BackHandler(enabled = tab == 6 || tab == 7) { tab = 2 }
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

    val diagnosticPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { destination ->
        if (destination != null) scope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    val status = context.getSharedPreferences("extractor_status", android.content.Context.MODE_PRIVATE)
                    val unknown = context.getString(R.string.diagnostic_unknown)
                    val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: unknown
                    val engineVersion = ExtractorVersion.read(context) ?: unknown
                    val last = status.getLong("last_success", 0L)
                    val updated = if (last > 0) java.text.DateFormat.getDateTimeInstance().format(java.util.Date(last)) else unknown
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
                            val date = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(error.time))
                            val category = when (error.category) {
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
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, destination)
                    clipData = android.content.ClipData.newRawUri("diagnóstico", destination)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, context.getString(R.string.diagnostic_share)))
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { message = context.getString(R.string.diagnostic_error) }
        }
    }

    var analysisRevision by remember { mutableStateOf(0) }
    LaunchedEffect(incomingLink, shareRevision) {
        if (incomingLink.isNotBlank()) {
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
        profileViewModel.inspect(link, false)
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
                    message = "No se pudo crear la cola. Inténtalo otra vez."
                } finally {
                    queueBusy = false
                }
            }
        }
    }

    val storagePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) addToQueue()
        else message = "Android necesita permiso para guardar en Descargas."
    }
    val notificationsPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Notificaciones opcionales: las descargas siguen aunque se denieguen. */ }

    val requestDownload: () -> Unit = {
        focusManager.clearFocus()
        keyboard?.hide()
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        else {
            if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                notificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            addToQueue()
        }
    }
    val chooseQuality: () -> Unit = {
        focusManager.clearFocus()
        keyboard?.hide()
        val video = selected.keys.firstOrNull()?.let { id -> videos.firstOrNull { it.id == id } }
        if (video != null) {
            batchGeneration++; batchCheckJob?.cancel()
            batchChecking = false; batchChecked = 0; batchFailed = 0; batchTotal = 0; batchCounts = emptyMap()
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
                    chosenFormat = formats.firstOrNull { DownloadFormatPreference.matches(saved, it.id, it.label, TikTokVideoSource.DEFAULT_FORMAT) } ?: formats.first()
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) {
                    runCatching { DiagnosticHistory.record(context, e.message) }
                    message = "No se pudieron cargar las opciones de calidad. Inténtalo otra vez."
                    formatError = true
                } finally { formatsBusy = false }
            }
        }
    }

    val openPlayback: (DownloadRecord) -> Unit = { record ->
        val saved = record.savedUri
        if (saved.isNullOrBlank()) message = "No se encontró la ubicación del archivo."
        else scope.launch {
            try {
                val uri = Uri.parse(saved)
                val (mime, name) = withContext(Dispatchers.IO) {
                    if (uri.scheme == "content") {
                        val type = runCatching { context.contentResolver.getType(uri) }.getOrNull()
                        val displayName = runCatching {
                            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                                ?.use { if (it.moveToFirst()) it.getString(0) else null }
                        }.getOrNull()
                        type to (displayName ?: uri.lastPathSegment)
                    } else null to uri.lastPathSegment
                }
                if (PlaybackKind.isAudio(record.formatId, mime, name)) {
                    musicRequestUri = saved
                    musicRequestTitle = record.title
                    tab = 6
                } else context.startActivity(Intent(context, PlayerActivity::class.java).apply {
                    data = uri
                    putExtra("title", record.title)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                runCatching { DiagnosticHistory.record(context, e.message) }
                message = "No se pudo abrir el reproductor."
            }
        }
    }

    MaterialTheme(colorScheme = TikitokiColors) {
        if (showAbout) {
            AlertDialog(
                onDismissRequest = { showAbout = false },
                title = { Text(stringResource(R.string.about_title)) },
                text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.about_description))
                    Text(stringResource(R.string.about_license))
                    Text(stringResource(R.string.about_credits))
                    Text(stringResource(R.string.about_privacy))
                } },
                confirmButton = { Button(onClick = { showAbout = false }) { Text(stringResource(R.string.about_close)) } },
                dismissButton = { OutlinedButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/cponce123com-create/Tikitoki-Dowloader"))) }
                }) { Text(stringResource(R.string.about_sources)) } },
            )
        }
        if (showPlayerChoice) {
            AlertDialog(
                onDismissRequest = { showPlayerChoice = false },
                title = { Text("Reproductor") },
                text = { Text("Elige qué quieres reproducir.") },
                confirmButton = { Button(onClick = { showPlayerChoice = false; libraryKind = "Videos"; tab = 2 }) { Text("Videos") } },
                dismissButton = { OutlinedButton(onClick = { showPlayerChoice = false; tab = 6 }) { Text("Música") } },
            )
        }
        if (showQualitySheet && selected.isNotEmpty()) {
            val preview = videos.firstOrNull { selected.size == 1 && it.id == selected.keys.first() }
            ModalBottomSheet(onDismissRequest = { batchGeneration++; batchCheckJob?.cancel(); batchChecking = false; showQualitySheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = TechBackground) {
                Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Opciones de descarga", style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold, color = Color.White)
                    if (preview != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            VideoThumbnail(preview, thumbnailResolver,
                                Modifier.width(144.dp).aspectRatio(if (MediaLinkParser.parse(preview.url)?.platform == MediaPlatform.TIKTOK) 9f / 16f else 16f / 9f))
                            Column(Modifier.weight(1f)) {
                                Text(preview.title, maxLines = 3, overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.SemiBold)
                                Text("${preview.durationSeconds}s", color = TechMuted)
                            }
                        }
                    }
                    Text("Formato disponible", style = MaterialTheme.typography.titleMedium)
                    if (selected.size > 1) {
                        Text(stringResource(R.string.format_batch_note), color = TechMuted)
                        OutlinedButton(onClick = {
                            if (batchChecking) {
                                batchGeneration++; batchCheckJob?.cancel(); batchChecking = false
                            } else {
                                val items = videos.filter { it.id in selected }
                                var presets = formats.toList()
                                val checkedItems = mutableListOf<List<FormatChoice>>()
                                val knownHeights = mutableSetOf<Int>()
                                batchChecked = 0; batchFailed = 0; batchTotal = items.size; batchCounts = emptyMap()
                                batchChecking = true
                                val generation = ++batchGeneration
                                batchCheckJob = scope.launch {
                                    try {
                                        for (item in items) {
                                            try {
                                                val found = if (item.id.contains(":")) universalSource.formats(item) else source.formats(item)
                                                if (found.isEmpty()) { batchFailed++; continue }
                                                checkedItems.add(found)
                                                val discovered = found.mapNotNull { it.height }.filter { it in 1..16384 }
                                                val heightsChanged = knownHeights.addAll(discovered)
                                                if (heightsChanged) {
                                                    presets = FormatOptions.batch(context, knownHeights)
                                                    formats = presets
                                                    batchCounts = presets.associate { preset -> preset.id to checkedItems.count { FormatAvailability.matches(preset.id, it) } }
                                                } else {
                                                    batchCounts = batchCounts.toMutableMap().apply {
                                                        presets.forEach { preset -> if (FormatAvailability.matches(preset.id, found)) this[preset.id] = (this[preset.id] ?: 0) + 1 }
                                                    }
                                                }
                                                batchChecked++
                                            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                                            catch (_: Exception) { batchFailed++ }
                                        }
                                    } finally { if (generation == batchGeneration) batchChecking = false }
                                }
                            }
                        }) { Text(stringResource(if (batchChecking) R.string.format_check_cancel else R.string.format_check_batch)) }
                        if (batchTotal > 0) Text(stringResource(R.string.format_check_status, batchChecked + batchFailed, batchTotal, batchFailed + (batchTotal - batchChecked - batchFailed)), color = TechMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    if (formatsBusy) CircularProgressIndicator(color = TechPurple)
                    if (formatError) Text(stringResource(R.string.format_check_failed), color = MaterialTheme.colorScheme.error)
                    Column {
                        formats.forEach { option ->
                            Row(Modifier.fillMaxWidth().clickable { chosenFormat = option }
                                .padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = chosenFormat.id == option.id,
                                    onClick = { chosenFormat = option })
                                Column(Modifier.weight(1f)) {
                                    Text(option.label)
                                    if (selected.size > 1 && batchChecked > 0) Text(stringResource(R.string.format_check_matches, batchCounts[option.id] ?: 0, batchChecked), color = TechMuted, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = rememberFormat, onCheckedChange = { rememberFormat = it })
                        Text("Usar como opción predeterminada")
                    }
                    TechPrimaryButton("Descargar", icon = Icons.Outlined.Download, enabled = !queueBusy && !formatsBusy && !formatError,
                        modifier = Modifier.fillMaxWidth(), onClick = {
                            batchGeneration++; batchCheckJob?.cancel(); batchChecking = false
                            if (rememberFormat) {
                                val preference = DownloadFormatPreference.fromChoice(chosenFormat.id, chosenFormat.label)
                                settings.edit().putString("default_download_format", preference).apply()
                            }
                            showQualitySheet = false
                            requestDownload()
                        })
                    Spacer(Modifier.height(18.dp))
                }
            }
        }
        finishedBatches.firstOrNull()?.let { batch ->
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Descargas terminadas") },
                text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SummaryTile("Total", batch.total, Modifier.weight(1f))
                        SummaryTile("Éxitos", batch.completed, Modifier.weight(1f))
                        SummaryTile("Errores", batch.failed, Modifier.weight(1f))
                    }
                    if (batch.cancelled > 0) Text("Cancelados: ${batch.cancelled}", color = TechMuted, style = MaterialTheme.typography.bodySmall)
                    if (batch.duplicates > 0) Text("Duplicados omitidos: ${batch.duplicates}", color = TechMuted, style = MaterialTheme.typography.bodySmall)
                } },
                confirmButton = { Button(onClick = { scope.launch { dao.acknowledgeBatch(batch.batchId) } }) { Text("Aceptar") } }
            )
        }
        if (showClearTemp) {
            AlertDialog(
                onDismissRequest = { showClearTemp = false },
                title = { Text("Limpiar archivos temporales") },
                text = { Text("Se borrarán solo los archivos temporales de Baja.pe. Los fragmentos guardados para reanudar podrían perderse. Los videos descargados se conservan.") },
                confirmButton = { Button(onClick = {
                    showClearTemp = false
                    scope.launch {
                        try { val deleted = DownloadQueue.clearTemporary(context); message = "Archivos temporales eliminados: $deleted." }
                        catch (_: Exception) { message = "No se puede limpiar mientras haya descargas en curso, pendientes o pausadas." }
                    }
                }) { Text("Limpiar") } },
                dismissButton = { OutlinedButton(onClick = { showClearTemp = false }) { Text("Cancelar") } }
            )
        }
        deleteCandidate?.let { item ->
            AlertDialog(
                onDismissRequest = { deleteCandidate = null },
                title = { Text("Eliminar video") },
                text = { Text("¿Eliminar este video del dispositivo y del historial de Baja.pe? Esta acción no se puede deshacer.") },
                confirmButton = { Button(onClick = {
                    deleteCandidate = null
                    scope.launch {
                        try {
                            message = when (DownloadQueue.deleteCompleted(context, item.videoId)) {
                                DeleteCompletedResult.DELETED -> "Video eliminado del dispositivo y del historial."
                                DeleteCompletedResult.FILE_MISSING -> "El archivo ya no existía. Se eliminó del historial."
                                DeleteCompletedResult.FAILED -> "No se pudo eliminar el archivo. El registro se conserva."
                            }
                        } catch (e: Exception) {
                            message = "No se pudo eliminar el archivo. Comprueba que siga disponible."
                        }
                    }
                }) { Text("Eliminar") } },
                dismissButton = { OutlinedButton(onClick = { deleteCandidate = null }) { Text("Cancelar") } }
            )
        }
        Scaffold(
            modifier = Modifier.imePadding(),
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = { SafeAreaTopBar { } },
            bottomBar = {
                Column(Modifier.fillMaxWidth().background(Color(0xFF09231B))) {
                MiniPlayer(musicScreenOpen = tab == 6, onMusic = { tab = 6 })
                NavigationBar(containerColor = Color(0xFF09231B)) {
                    listOf("Inicio", "Descargas", "Biblioteca", "Herramientas", "Archivos").forEachIndexed { index, name ->
                        val target = listOf(0, 1, 2, 3, 4)[index]
                        NavigationBarItem(
                            selected = tab == target || (tab == 6 && target == 3) || (tab == 7 && target == 2),
                            onClick = { focusManager.clearFocus(); keyboard?.hide(); tab = target },
                            icon = { Icon(listOf(Icons.Outlined.Home, Icons.Outlined.Download, Icons.Outlined.VideoLibrary, Icons.Outlined.Build, Icons.Outlined.Folder)[index], contentDescription = name) },
                            label = { Text(name, maxLines = 1, fontSize = if (name == "Herramientas" || name == "Biblioteca") 10.sp else 12.sp) },
                            colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White, selectedTextColor = Color.White,
                                indicatorColor = TechPurple, unselectedIconColor = TechMuted,
                                unselectedTextColor = TechMuted)
                        )
                    }
                }
                }
            }
        ) { insets ->
            val completedKinds = remember(records) { records.filter { it.status == DownloadState.COMPLETED }
                .groupBy { DownloadIdentity.sourceId(it.videoId) } }
            val downloadedIds = remember(completedKinds) { completedKinds.filterValues { items ->
                items.any { PlaybackKind.isAudio(it.formatId, null, it.savedUri) } &&
                    items.any { !PlaybackKind.isAudio(it.formatId, null, it.savedUri) }
            }.keys }
            val visibleVideos = remember(videos, search, onlyUndownloaded, downloadedIds) { videos.filter {
                (search.isBlank() || it.title.contains(search, ignoreCase = true) || it.id.contains(search)) &&
                    (!onlyUndownloaded || it.id !in downloadedIds)
            } }
            if (tab == 0) {
                LazyVerticalGrid(
                    columns = if (gridMode) GridCells.Adaptive(minSize = 112.dp) else GridCells.Fixed(1),
                    modifier = Modifier.fillMaxSize().background(if (themeChoice == "Negro AMOLED") Color.Black else TechBackground).padding(insets).consumeWindowInsets(insets),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            TechHeader(onSettings = { tab = 5 })

                        TechPlatformStrip(selectedPlatform) { platform -> selectedPlatform = platform }
                        OutlinedTextField(
                            value = url,
                            onValueChange = {
                                val extracted = if (it.length - url.length > 1) IncomingLinkValidator.extractFirstHttps(it) else null
                                url = extracted?.url ?: it
                                profileViewModel.reset()
                                selected.clear()
                                formats = listOf(defaultFormat)
                                chosenFormat = defaultFormat
                                message = ""
                            },
                            placeholder = { Text("Pega un enlace") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); keyboard?.hide() }),
                            shape = RoundedCornerShape(12.dp),
                        )

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                modifier = Modifier.weight(1f).height(44.dp),
                                onClick = {
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
                                    } else message = "El portapapeles no contiene un enlace válido."
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, TechBorder)
                            ) {
                                Icon(Icons.Outlined.ContentPaste, contentDescription = null, tint = TechPink, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Pegar")
                            }
                        TechPrimaryButton(
                            modifier = Modifier.weight(1f).height(44.dp),
                            enabled = !profileState.loading && !profileState.loadingMore && url.isNotBlank(),
                            onClick = {
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
                            icon = Icons.Outlined.Search,
                            text = if (profileState.loading) "Analizando…" else "Analizar",
                        )
                        }

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
                                    OutlinedButton(onClick = { profileViewModel.skipIncompletePage() }) { Text("Continuar con la siguiente parte") }
                                }
                            }
                        }
                        if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)


                        if (videos.isNotEmpty()) {
                            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                                colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = TechPanel),
                                border = BorderStroke(1.dp, TechBorder)) {
                                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(26.dp)).background(TechGradient),
                                        contentAlignment = Alignment.Center) {
                                        if (profileState.profileImage != null) {
                                            coil.compose.SubcomposeAsyncImage(
                                                model = coil.request.ImageRequest.Builder(LocalContext.current)
                                                    .data(profileState.profileImage).addHeader("Referer", "https://www.tiktok.com/").build(),
                                                contentDescription = "Foto del perfil", modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop,
                                                error = { Text("@", fontSize = 26.sp, color = Color.White) },
                                                loading = { Text("@", fontSize = 26.sp, color = Color.White) },
                                            )
                                        } else Text("@", fontSize = 26.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(url.substringAfter("tiktok.com/").substringBefore("?").substringBefore("/video/").take(35)
                                            .ifBlank { selectedPlatform ?: "Perfil" }, fontWeight = FontWeight.Bold,
                                            color = Color.White, maxLines = 1)
                                        Text("${videos.size} videos detectados", color = TechMuted, fontSize = 12.sp)
                                    }
                                    OutlinedButton(onClick = { profileViewModel.resumeScan() },
                                        enabled = !profileState.countingTotal && profileState.hasMore) { Text("↻") }
                                }
                            }
                            val allSelected = videos.isNotEmpty() && videos.all { it.id in selected }
                            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = TechPanel), border = BorderStroke(1.dp, TechBorder)) {
                                Row(Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    listOf(
                                        "Detectados" to (profileState.totalVideos?.toString() ?: if (profileState.countingTotal) "…" else "—"),
                                        "Cargados" to videos.size.toString(),
                                        "Seleccionados" to selected.size.toString()
                                    ).forEach { (label, value) ->
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(label, fontSize = 11.sp, color = TechMuted)
                                            Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                                        }
                                    }
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(modifier = Modifier.weight(1f), onClick = {
                                    focusManager.clearFocus(); keyboard?.hide()
                                    selected.clear()
                                    if (!allSelected) videos.forEach { selected[it.id] = true }
                                    formats = listOf(defaultFormat)
                                    chosenFormat = defaultFormat
                                }) { Text(if (allSelected) "Quitar selección" else "Seleccionar todo", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                Button(modifier = Modifier.weight(1f), enabled = selected.isNotEmpty() && !queueBusy && !inspecting,
                                    onClick = chooseQuality) { Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Descargar ${selected.size}", maxLines = 1) }
                            }

                            OutlinedTextField(
                                value = search,
                                onValueChange = { search = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Buscar videos") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); keyboard?.hide() }),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                FilterChip(selected = onlyUndownloaded, onClick = { onlyUndownloaded = !onlyUndownloaded }, label = { Text("Pendientes") })
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
                            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = TechPanel),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (checked) TechPink else TechBorder),
                        ) {
                            if (gridMode) {
                                Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(Modifier.fillMaxWidth().aspectRatio(if (MediaLinkParser.parse(video.url)?.platform == MediaPlatform.TIKTOK) 9f / 16f else 16f / 9f)) {
                                        VideoThumbnail(video, thumbnailResolver, Modifier.fillMaxSize(), deferLookup = profileState.countingTotal)
                                        Checkbox(checked = checked, onCheckedChange = null,
                                            modifier = Modifier.align(Alignment.TopEnd).background(Color.Black.copy(alpha = .65f), RoundedCornerShape(8.dp)).size(36.dp))
                                    }
                                    Text(video.title, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            } else {
                                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.width(116.dp).aspectRatio(if (MediaLinkParser.parse(video.url)?.platform == MediaPlatform.TIKTOK) 9f / 16f else 16f / 9f)) {
                                        VideoThumbnail(video, thumbnailResolver, Modifier.fillMaxSize(), deferLookup = profileState.countingTotal)
                                        Checkbox(checked = checked, onCheckedChange = null,
                                            modifier = Modifier.align(Alignment.TopEnd).background(Color.Black.copy(alpha = .65f), RoundedCornerShape(8.dp)).size(36.dp))
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text(if (video.id in downloadedIds) "✓ Audio y video" else if (video.id in completedKinds) "✓ Una versión guardada" else "Disponible", color = TechMuted, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (tab == 7) {
                CollectionsPrototype(Modifier.padding(insets).consumeWindowInsets(insets), onBack = { tab = 2 })
            } else if (tab == 1) {
                val queue = records.filter { LibraryVisibility.inQueue(it.status) }
                    .sortedBy { if (it.status == DownloadState.DOWNLOADING) 0 else if (it.status == DownloadState.QUEUED) 1 else 2 }
                val active = queue.count { it.status == DownloadState.DOWNLOADING || it.status == DownloadState.QUEUED }
                val paused = queue.count { it.status == DownloadState.PAUSED }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().background(if (themeChoice == "Negro AMOLED") Color.Black else TechBackground).padding(insets).consumeWindowInsets(insets),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TechHeader(onSettings = { tab = 5 })
                            Text("Cola de descargas", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            if (queue.isEmpty()) Text("No hay descargas en curso.", color = TechMuted)
                            else {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    SummaryTile("Activas", queue.count { it.status == DownloadState.DOWNLOADING }, Modifier.weight(1f))
                                    SummaryTile("En espera", queue.count { it.status == DownloadState.QUEUED }, Modifier.weight(1f))
                                    SummaryTile("Pausadas", paused, Modifier.weight(1f))
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(enabled = active > 0, onClick = { scope.launch { DownloadQueue.pause(context); message = "Cola pausada." } }) { Text("Pausar") }
                                    Button(enabled = paused > 0, onClick = { scope.launch { DownloadQueue.resume(context); message = "Cola reanudada." } }) { Text("Continuar") }
                                }
                            }
                            if (queue.isNotEmpty() && message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    items(queue, key = { it.videoId }) { record ->
                        DownloadHistoryCard(record, resolver = thumbnailResolver, speed = downloadSpeeds[record.videoId], onDelete = {}, onCancel = {
                            scope.launch { DownloadQueue.cancel(context, record.videoId); message = "Descarga cancelada." }
                        }, onOpen = {})
                    }
                }
            } else if (tab == 2) {
                val allCompleted = remember(records) { records.filter { LibraryVisibility.inLibrary(it.status, it.savedUri) } }
                // The producer assigns value; this Compose lint check does not recognize it with the current Kotlin version.
                @android.annotation.SuppressLint("ProduceStateDoesNotAssignValue")
                val audioIds by produceState<Set<String>?>(initialValue = null, key1 = allCompleted) {
                    value = null
                    value = withContext(Dispatchers.IO) {
                        allCompleted.filter { record ->
                            val uri = record.savedUri?.let(Uri::parse)
                            val mime = if (uri?.scheme == "content") runCatching { context.contentResolver.getType(uri) }.getOrNull() else null
                            val name = if (uri?.scheme == "content") runCatching {
                                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                                    ?.use { if (it.moveToFirst()) it.getString(0) else null }
                            }.getOrNull() else uri?.lastPathSegment
                            PlaybackKind.isAudio(record.formatId, mime, name)
                        }.map { it.videoId }.toSet()
                    }
                }
                val completed = audioIds?.let { ids -> allCompleted.filter { (it.videoId in ids) == (libraryKind == "Música") } }.orEmpty()
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 105.dp),
                    modifier = Modifier.fillMaxSize().background(if (themeChoice == "Negro AMOLED") Color.Black else TechBackground).padding(insets).consumeWindowInsets(insets),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(Modifier.padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            TechHeader(onSettings = { tab = 5 })
                            Text("Biblioteca", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("Tus descargas, listas para reproducir", color = TechMuted, style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = { tab = 7 }, modifier = Modifier.fillMaxWidth()) { Text("Explorar colecciones") }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Videos", "Música").forEach { kind ->
                                    FilterChip(selected = libraryKind == kind, onClick = { libraryKind = kind }, label = { Text(kind) })
                                }
                                if (libraryKind == "Música") OutlinedButton(onClick = { tab = 6 }) { Text("Reproductor") }
                            }
                            if (audioIds == null) Text("Organizando biblioteca…", color = TechMuted)
                            else if (completed.isEmpty()) Text(if (libraryKind == "Música") "Aún no hay música descargada." else "Aún no hay videos descargados.", color = TechMuted)
                            else Text("Toca para reproducir · Mantén pulsado para eliminar", color = TechMuted, style = MaterialTheme.typography.bodySmall)
                            if (message.startsWith("No se") || message.startsWith("El archivo ya no existía")) Text(message, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    gridItems(completed, key = { it.videoId }) { record ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().aspectRatio(0.78f).combinedClickable(
                                onClick = { openPlayback(record) }, onLongClick = { deleteCandidate = record }),
                            shape = RoundedCornerShape(8.dp), color = TechPanel,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                val entry = remember(record.videoId, record.thumbnail) {
                                    VideoEntry(DownloadIdentity.sourceId(record.videoId), record.videoUrl, record.title, record.thumbnail)
                                }
                                // The producer assigns value; this Compose lint check does not recognize it with the current Kotlin version.
                                @android.annotation.SuppressLint("ProduceStateDoesNotAssignValue")
                                val remote by produceState<String?>(initialValue = record.thumbnail ?: ThumbnailUrls.youtube(entry.id, entry.url), key1 = entry.url, key2 = entry.thumbnail) {
                                    value = entry.thumbnail?.takeIf { it.startsWith("https://") } ?: ThumbnailUrls.youtube(entry.id, entry.url) ?: thumbnailResolver.resolve(entry)
                                }
                                @Composable fun LocalVideoFrame() {
                                    if (record.videoId !in audioIds.orEmpty()) coil.compose.SubcomposeAsyncImage(
                                        model = coil.request.ImageRequest.Builder(LocalContext.current).data(record.savedUri?.let(Uri::parse)).videoFrameMillis(1000).build(),
                                        imageLoader = galleryImageLoader, contentDescription = "Miniatura de ${record.title}",
                                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                                        error = { Text("▶", color = TechMuted) },
                                        loading = { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) },
                                    ) else Text("♫", color = TechMuted, style = MaterialTheme.typography.headlineMedium)
                                }
                                if (!remote.isNullOrBlank()) coil.compose.SubcomposeAsyncImage(
                                    model = coil.request.ImageRequest.Builder(LocalContext.current).data(remote)
                                        .apply { if (entry.url.contains("tiktok.com")) addHeader("Referer", "https://www.tiktok.com/") }.build(),
                                    contentDescription = "Miniatura de ${record.title}", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                                    error = { LocalVideoFrame() },
                                    loading = { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) },
                                ) else LocalVideoFrame()
                                Text(record.title, modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .9f))))
                                    .padding(start = 7.dp, end = 7.dp, top = 18.dp, bottom = 7.dp),
                                    color = Color.White, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            } else Column(
                modifier = Modifier.fillMaxSize().background(if (themeChoice == "Negro AMOLED") Color.Black else TechBackground).padding(insets).consumeWindowInsets(insets)
                    .verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                TechHeader(onSettings = { tab = 5 })
                when (tab) {
                    6 -> MusicScreen(
                        requestedUri = musicRequestUri,
                        requestedTitle = musicRequestTitle,
                        onRequestConsumed = { musicRequestUri = null },
                    )
                    3 -> {
                        val monitored = remember(monitorRevision) { ProfileMonitor.profiles(context).sorted() }
                        val failedCount = records.count { it.status == DownloadState.FAILED }
                        Text("Herramientas", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Reproducción y seguimiento en un solo lugar", color = TechMuted, style = MaterialTheme.typography.bodySmall)
                        if (failedCount > 0) {
                            Text("Descargas con errores: $failedCount", style = MaterialTheme.typography.titleMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { scope.launch { DownloadQueue.retryFailed(context); tab = 1; message = "Reintentando descargas." } }) { Text("Reintentar") }
                                OutlinedButton(onClick = { scope.launch { dao.clearFailed() } }) { Text("Limpiar errores") }
                            }
                        }
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = TechPanel), border = BorderStroke(1.dp, TechBorder), shape = RoundedCornerShape(18.dp)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Reproductor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Abre tu música o biblioteca de videos.", color = TechMuted, style = MaterialTheme.typography.bodySmall)
                                OutlinedButton(onClick = { showPlayerChoice = true }) { Text("Abrir reproductor") }
                            }
                        }
                        Text("Monitoreo de perfiles", style = MaterialTheme.typography.titleLarge)
                        
                        OutlinedButton(onClick = {
                            if (ProfileMonitor.add(context, url.trim())) {
                                if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                monitorRevision++
                                message = "Perfil agregado al monitoreo."
                            } else message = "Pega un enlace válido de perfil en Buscar."
                        }) { Text("Seguir perfil del enlace actual") }
                        monitored.forEach { profile ->
                            val parsedProfile = TikTokLinkParser.parse(profile) as? LinkParseResult.Success
                            val username = parsedProfile?.link?.username.orEmpty()
                            val activeUsername = (TikTokLinkParser.parse(url) as? LinkParseResult.Success)?.link?.username
                            val initialAvatar = profileState.profileImage.takeIf { username.isNotBlank() && username.equals(activeUsername, ignoreCase = true) }
                            // The producer assigns value; this Compose lint check does not recognize it with the current Kotlin version.
                            @android.annotation.SuppressLint("ProduceStateDoesNotAssignValue")
                            val identity by produceState<ProfileIdentity?>(initialValue = ProfileIdentity(null, initialAvatar), key1 = profile) {
                                val found = avatarResolver.resolveIdentity(profile)
                                if (found != null) value = found.copy(avatar = found.avatar ?: value?.avatar)
                            }
                            Card {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(52.dp).clip(RoundedCornerShape(26.dp)).background(TechGradient), contentAlignment = Alignment.Center) {
                                            if (identity?.avatar != null) coil.compose.SubcomposeAsyncImage(
                                                model = coil.request.ImageRequest.Builder(LocalContext.current)
                                                    .data(identity?.avatar).addHeader("Referer", "https://www.tiktok.com/").build(),
                                                contentDescription = "Foto de perfil", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                                                error = { Text("@", color = Color.White) }, loading = { Text("@", color = Color.White) },
                                            ) else Text("@", color = Color.White)
                                        }
                                        Column(Modifier.weight(1f)) {
                                            Text(identity?.displayName ?: "@$username", fontWeight = FontWeight.Bold, maxLines = 1)
                                            if (identity?.displayName != null) Text("@$username", color = TechMuted, maxLines = 1)
                                        }
                                    }
                                    OutlinedButton(onClick = { ProfileMonitor.remove(context, profile); monitorRevision++ }) { Text("Dejar de seguir") }
                                }
                            }
                        }
                        if (monitored.isEmpty()) Text("Todavía no sigues perfiles.")
                        if (message.contains("perfil", ignoreCase = true)) Text(message, style = MaterialTheme.typography.bodySmall, color = TechMuted)
                    }
                    5 -> {
                        Image(painter = painterResource(R.drawable.bajape_logo), contentDescription = null, modifier = Modifier.size(76.dp))
                        Text("Configuración", style = MaterialTheme.typography.titleLarge)
                        OutlinedButton(onClick = { showAbout = true }) { Text(stringResource(R.string.about_open)) }
                        val diagnosticFilename = stringResource(R.string.diagnostic_filename)
                        OutlinedButton(onClick = { diagnosticPicker.launch(diagnosticFilename) }) { Text(stringResource(R.string.diagnostic_export)) }
                        Text("Frecuencia de monitoreo de perfiles", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(6, 12, 24).forEach { hours ->
                                FilterChip(selected = monitorHours == hours, onClick = { monitorHours = hours; ProfileMonitor.setInterval(context, hours) }, label = { Text(stringResource(R.string.monitor_hours, hours)) })
                            }
                        }
                        Text(stringResource(R.string.monitor_weekly), color = TechMuted, style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.monitor_page_limit), style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(5, 10, 25, 50).forEach { pages ->
                                FilterChip(selected = monitorPages == pages, onClick = { monitorPages = pages; ProfileMonitor.setPageLimit(context, pages) }, label = { Text(pages.toString()) })
                            }
                        }
                        Text("Baja.pe · v0.5")
                        Text("Actualización de la app", style = MaterialTheme.typography.titleMedium)
                        val updateStatus = remember { context.getSharedPreferences("extractor_status", android.content.Context.MODE_PRIVATE) }
                        val lastResult = updateStatus.getString("last_result", "Actualización aún no ejecutada.")
                        Text(if (lastResult?.startsWith("Falló") == true) "No se pudo actualizar. Inténtalo otra vez." else if (lastResult?.startsWith("Actualización ejecutada") == true) "Actualización completada." else "Actualización pendiente.", color = TechMuted)
                        val lastSuccess = updateStatus.getLong("last_success", 0L)
                        if (lastSuccess > 0L) Text("Última actualización: " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(lastSuccess)))
                        OutlinedButton(onClick = {
                            ExtractorUpdateWorker.updateNow(context)
                            message = "Actualización solicitada. Se ejecutará sin descargas pendientes."
                        }) { Text("↻ Buscar actualización") }
                        Text(message, style = MaterialTheme.typography.bodySmall, color = TechMuted)
                        Text("Destino de las descargas", style = MaterialTheme.typography.titleMedium)
                        Text(folderDescription, color = TechMuted)
                        OutlinedButton(onClick = { folderPicker.launch(null) }) { Text("📁 Elegir carpeta o memoria SD") }
                        if (downloadFolder != null) OutlinedButton(onClick = {
                            settings.edit().remove("download_tree").apply()
                            downloadFolder = null
                        }) { Text("Restablecer carpeta predeterminada") }
                        Text("Tema", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Oscuro", "Negro AMOLED").forEach { option ->
                                FilterChip(selected = themeChoice == option, onClick = {
                                    themeChoice = option
                                    settings.edit().putString("theme", option).apply()
                                }, label = { Text(option) })
                            }
                        }
                        Text("Calidad predeterminada", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Máxima", "720p", "480p").forEach { option ->
                                FilterChip(selected = preferredQuality == option, onClick = {
                                    preferredQuality = option
                                    settings.edit().putString("quality", option).apply()
                                }, label = { Text(option) })
                            }
                        }
                        Text("Descargas simultáneas", style = MaterialTheme.typography.titleMedium)
                        Text("Wi-Fi y redes sin medición: $wifiParallel", color = TechMuted)
                        Slider(value = wifiParallel.toFloat(), onValueChange = {
                            wifiParallel = it.roundToInt().coerceIn(1, 8)
                            settings.edit().putInt("wifi_parallel", wifiParallel).apply()
                        }, valueRange = 1f..8f, steps = 6)
                        Text("Datos y redes medidas: $meteredParallel", color = TechMuted)
                        Slider(value = meteredParallel.toFloat(), onValueChange = {
                            meteredParallel = it.roundToInt().coerceIn(1, 8)
                            settings.edit().putInt("metered_parallel", meteredParallel).apply()
                        }, valueRange = 1f..8f, steps = 6)
                        Text("Entre 1 y 8 por red. Al reducir el límite, los videos activos terminan antes de iniciar otros.", color = TechMuted, style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Permitir redes medidas")
                                Text("Incluye datos móviles y Wi-Fi con medición.", color = TechMuted, style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = allowMetered, enabled = !networkSettingsBusy, onCheckedChange = { allowed ->
                                networkSettingsBusy = true
                                scope.launch {
                                    try {
                                        val pending = DownloadQueue.changeMeteredPolicy(context, allowed)
                                        allowMetered = allowed
                                        message = if (pending) "Cola pausada para cambiar la red. Pulsa Continuar en Descargas." else "Preferencia de red actualizada."
                                    } catch (_: Exception) { message = "No se pudo cambiar la preferencia de red." }
                                    finally { networkSettingsBusy = false }
                                }
                            })
                        }
                        Text("Límite aproximado por descarga", style = MaterialTheme.typography.titleMedium)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0 to "Sin límite", 256 to "256 KiB/s", 512 to "512 KiB/s", 1024 to "1 MiB/s", 2048 to "2 MiB/s").forEach { (rate, label) ->
                                FilterChip(selected = rateKiB == rate, onClick = { rateKiB = rate; settings.edit().putInt("rate_kib", rate).apply() }, label = { Text(label) })
                            }
                        }
                        Text("Se aplica a las siguientes descargas. El total puede variar por los fragmentos y la cantidad de videos activos.", color = TechMuted, style = MaterialTheme.typography.bodySmall)
                        Text("Notificaciones", style = MaterialTheme.typography.titleMedium)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Mostrar avance aproximado", modifier = Modifier.weight(1f))
                            Switch(checked = showProgressNotice, onCheckedChange = {
                                showProgressNotice = it; settings.edit().putBoolean("notify_progress_detail", it).apply()
                                if (it && Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            })
                        }
                        Text("Android puede mantener una notificación general mientras se descarga.", color = TechMuted, style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Avisar al terminar cada grupo", modifier = Modifier.weight(1f))
                            Switch(checked = completionNotice, onCheckedChange = {
                                completionNotice = it; settings.edit().putBoolean("notify_completion", it).apply()
                                if (it && Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            })
                        }
                        Text("Almacenamiento temporal", style = MaterialTheme.typography.titleMedium)
                        Text("Limpia solo archivos temporales de Baja.pe cuando no haya descargas pendientes ni pausadas.", color = TechMuted, style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { showClearTemp = true }) { Text("Limpiar temporales") }
                        if (message.isNotBlank()) Text(message, color = TechMuted, style = MaterialTheme.typography.bodySmall)
                        
                        Text("Uso de contenido autorizado · GPL-3.0")
                    }
                    else -> {
                        Text("Archivos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Administra el contenido guardado en tu dispositivo", color = TechMuted, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        Card(modifier = Modifier.fillMaxWidth(), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = TechPanel), border = BorderStroke(1.dp, TechBorder), shape = RoundedCornerShape(18.dp)) {
                          Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Carpeta de descarga", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(folderDescription, color = TechMuted)
                        OutlinedButton(onClick = {
                            try {
                                val folder = downloadFolder?.let { tree ->
                                    val treeUri = Uri.parse(tree)
                                    android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, android.provider.DocumentsContract.getTreeDocumentId(treeUri))
                                } ?: android.provider.DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Download/TikitokiDownloader")
                                context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(folder, android.provider.DocumentsContract.Document.MIME_TYPE_DIR)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                                })
                            } catch (_: Exception) { message = "No se pudo abrir la carpeta. Busca $folderDescription en tu administrador de archivos." }
                        }) { Text("Abrir carpeta de Baja.pe") }
                          }
                        }
                        Text("${records.count { it.status == DownloadState.COMPLETED }} archivos guardados", color = TechMuted, style = MaterialTheme.typography.bodySmall)
                        if (message.startsWith("No se pudo abrir")) Text(message, color = TechMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryTile(label: String, count: Int, modifier: Modifier = Modifier) {
    Column(modifier.background(TechPanel, RoundedCornerShape(12.dp)).padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count.toString(), fontWeight = FontWeight.Bold, color = Color.White, style = MaterialTheme.typography.titleMedium)
        Text(label, color = TechMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun DownloadHistoryCard(record: DownloadRecord, resolver: VideoThumbnailResolver, speed: String? = null, onDelete: () -> Unit, onCancel: () -> Unit = {}, onOpen: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = TechPanel), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, TechBorder)) {
        Row(Modifier.fillMaxWidth().padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
            VideoThumbnail(VideoEntry(DownloadIdentity.sourceId(record.videoId), record.videoUrl, record.title, record.thumbnail), resolver,
                Modifier.width(86.dp).aspectRatio(16f / 9f))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(record.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val status = when (record.status) {
                    DownloadState.QUEUED -> "En espera"
                    DownloadState.DOWNLOADING -> when {
                        record.progress <= 0 -> "Preparando descarga…"
                        record.progress >= 100 -> "Procesando archivo…"
                        else -> "Descargando ${record.progress}%"
                    }
                    DownloadState.PAUSED -> "Pausada"
                    DownloadState.COMPLETED -> "Completada"
                    DownloadState.FAILED -> "Error"
                    DownloadState.CANCELLED -> "Cancelada"
                    else -> record.status
                }
                Text(status, color = TechMuted, style = MaterialTheme.typography.labelSmall)
                if (record.status == DownloadState.DOWNLOADING && speed != null) Text(stringResource(R.string.download_speed, speed), color = TechMuted, style = MaterialTheme.typography.labelSmall)
                if (record.status == DownloadState.DOWNLOADING) {
                    if (record.progress in 1..99) LinearProgressIndicator(progress = { record.progress / 100f }, modifier = Modifier.fillMaxWidth().height(5.dp), color = TechPurple)
                    else LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(5.dp), color = TechPurple)
                }
                if (!record.error.isNullOrBlank()) Text("No se pudo completar. Revisa la conexión.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                if (record.status == DownloadState.COMPLETED && record.savedUri != null) Row {
                    OutlinedButton(onClick = onOpen) { Text("Ver") }
                    OutlinedButton(onClick = onDelete) { Text("Eliminar") }
                }
            }
            if (record.status in listOf(DownloadState.QUEUED, DownloadState.DOWNLOADING, DownloadState.PAUSED)) {
                IconButton(onClick = onCancel, modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.Close, contentDescription = "Cancelar descarga de ${record.title}", tint = TechMuted) }
            }
        }
    }
}

@Composable
private fun VideoThumbnail(
    video: VideoEntry,
    resolver: VideoThumbnailResolver,
    modifier: Modifier = Modifier,
    deferLookup: Boolean = false,
) {
    val directThumbnail = video.thumbnail?.takeIf { it.isNotBlank() } ?: ThumbnailUrls.youtube(video.id, video.url)
    // The producer assigns value; this Compose lint check does not recognize it with the current Kotlin version.
    @android.annotation.SuppressLint("ProduceStateDoesNotAssignValue")
    val resolved by produceState<String?>(initialValue = directThumbnail, key1 = video.url, key2 = directThumbnail, key3 = deferLookup) {
        value = directThumbnail ?: if (deferLookup) null else resolver.resolve(video)
    }
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (!resolved.isNullOrBlank()) {
                    coil.compose.SubcomposeAsyncImage(
                        model = coil.request.ImageRequest.Builder(LocalContext.current)
                            .data(resolved)
                            .crossfade(true)
                            .apply { if (video.url.contains("tiktok.com")) addHeader("Referer", "https://www.tiktok.com/") }
                            .build(),
                        contentDescription = "Miniatura del video: ${video.title}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        loading = {
                            CircularProgressIndicator(
                                modifier = Modifier.size(26.dp),
                                color = TechPurple,
                                strokeWidth = 2.dp
                            )
                        },
                        error = {
                            val fallback = ThumbnailUrls.youtube(video.id, video.url)
                            if (fallback != null && fallback != resolved) {
                                coil.compose.SubcomposeAsyncImage(
                                    model = fallback,
                                    contentDescription = "Miniatura del video: ${video.title}",
                                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                                    error = { Text("▶", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                )
                            } else Text("▶", style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    )
            } else {
                Text("▶", style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

















private data class CollectionSection(val name: String, val collections: List<String>)

@Composable
private fun CollectionsPrototype(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val sections = remember {
        listOf(
            CollectionSection("Música", listOf("Rock en español", "Salsa", "Cumbia", "Baladas")),
            CollectionSection("Cristiana", listOf("Devocionales de la mañana", "Alabanzas", "Reflexiones bíblicas")),
            CollectionSection("Reflexiones y mensajes", listOf("Motivación", "Amor", "Amistad", "Bienestar")),
            CollectionSection("Noticias", listOf("Perú", "Internacionales", "Tecnología"))
        )
    }
    var section by rememberSaveable { mutableStateOf<String?>(null) }
    var collection by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = section != null) {
        if (collection != null) collection = null else section = null
    }
    LazyColumn(
        modifier = modifier.fillMaxSize().background(TechBackground),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            OutlinedButton(onClick = {
                if (collection != null) collection = null
                else if (section != null) section = null
                else onBack()
            }) { Text("Volver") }
        }
        item {
            Text(collection ?: section ?: "Colecciones", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Vista preliminar", color = TechMuted)
        }
        if (section == null) {
            items(sections) { entry ->
                Card(onClick = { section = entry.name }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(entry.name, style = MaterialTheme.typography.titleMedium)
                        Text(entry.collections.joinToString(" · "), color = TechMuted)
                    }
                }
            }
        } else if (collection == null) {
            val entries = sections.firstOrNull { it.name == section }?.collections.orEmpty()
            items(entries) { entry ->
                Card(onClick = { collection = entry }, modifier = Modifier.fillMaxWidth()) {
                    Text(entry, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                }
            }
        } else {
            item {
                Text("El catálogo y la búsqueda automática aún no están conectados. Aquí aparecerán los contenidos disponibles.", color = TechMuted)
            }
        }
    }
}
