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
import com.tikitoki.downloader.ui.FinishedBatchDialog
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
import com.tikitoki.downloader.ui.MainViewModel
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

    // Create and observe MainViewModel
    val viewModel: MainViewModel = viewModel()

    // Observe ViewModel state for values needed in MainActivity
    val themeChoice by viewModel.themeChoice.collectAsState()
    val preferredQuality by viewModel.preferredQuality.collectAsState()
    val message by viewModel.message.collectAsState()
    val showClearTemp by viewModel.showClearTemp.collectAsState()
    val showPlayerChoice by viewModel.showPlayerChoice.collectAsState()
    val showAbout by viewModel.showAbout.collectAsState()
    val musicRequestUri by viewModel.musicRequestUri.collectAsState()
    val musicRequestTitle by viewModel.musicRequestTitle.collectAsState()

    // Folder picker for custom download directory
    val folderPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                    viewModel.onChooseFolder(uri)
                } catch (
                    e: Exception,
                ) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    DiagnosticHistory.record(context, e.message)
                    viewModel.onResetFolder()
                }
            }
        }

    // Permission launchers
    val storagePermission =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (granted) {
                viewModel.onDownloadSelected(viewModel.selectedVideos.value.keys.toList())
            } else {
                viewModel.onMessage("Android necesita permiso para guardar en Descargas.")
            }
        }
    val notificationsPermission =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { /* Notificaciones opcionales: las descargas siguen aunque se denieguen. */ }

    val diagnosticPicker =
        rememberDiagnosticPicker {
            viewModel.onMessage(context.getString(R.string.diagnostic_error))
        }

    // Navigation
    val navController = rememberAppNavController()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboardManager.current

    // Handle incoming links from MainActivity state
    LaunchedEffect(incomingLink, shareRevision) {
        if (incomingLink.isNotBlank() && (shareRevision > 0 || !viewModel.isCurrentLink(incomingLink))) {
            viewModel.handleIncomingLink(incomingLink, shareRevision)
        }
    }

    // Back handler for MUSIC and COLLECTIONS routes
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
                viewModel = viewModel,
                startDestination = NavRoutes.HOME,
                // UI callbacks that need Activity context
                folderPicker = folderPicker,
                storagePermission = storagePermission,
                notificationsPermission = notificationsPermission,
                diagnosticPicker = diagnosticPicker,
                focusManager = focusManager,
                keyboard = keyboard,
                clipboard = clipboard,
                context = context,
                settings = settings,
                incomingLink = incomingLink,
                shareRevision = shareRevision,
                galleryImageLoader = ImageLoader.Builder(context).components { add(VideoFrameDecoder.Factory()) }.build(),
                thumbnailResolver = thumbnailResolver,
                avatarResolver = avatarResolver,
            )
        }

        // Dialogs rendered at the top level (inside Scaffold content)
        if (showAbout) {
            AlertDialog(
                onDismissRequest = { viewModel.onShowAboutDismiss() },
                title = { Text(stringResource(R.string.about_title)) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.about_description))
                        Text(stringResource(R.string.about_license))
                        Text(stringResource(R.string.about_credits))
                        Text(stringResource(R.string.about_privacy))
                    }
                },
                confirmButton = { Button(onClick = { viewModel.onShowAboutDismiss() }) { Text(stringResource(R.string.about_close)) } },
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
                onDismissRequest = { viewModel.onShowPlayerChoiceDismiss() },
                title = { Text("Reproductor") },
                text = { Text("Elige qué quieres reproducir.") },
                confirmButton = {
                    Button(onClick = {
                        viewModel.onShowPlayerChoiceDismiss()
                        viewModel.onLibraryKindChange("Videos")
                        navController.navigate(NavRoutes.LIBRARY)
                    }) { Text("Videos") }
                },
                dismissButton = {
                    OutlinedButton(onClick = {
                        viewModel.onShowPlayerChoiceDismiss()
                        navController.navigate(NavRoutes.MUSIC)
                    }) { Text("Música") }
                },
            )
        }
        if (showClearTemp) {
            AlertDialog(
                onDismissRequest = { viewModel.onShowClearTempDismiss() },
                title = { Text("Limpiar archivos temporales") },
                text = {
                    Text(
                        "Se borrarán solo los archivos temporales de Baja.pe. Los fragmentos guardados para reanudar podrían perderse. Los videos descargados se conservan.",
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        viewModel.onShowClearTempDismiss()
                        viewModel.onClearTemporary()
                    }) { Text("Limpiar") }
                },
                dismissButton = { OutlinedButton(onClick = { viewModel.onShowClearTempDismiss() }) { Text("Cancelar") } },
            )
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