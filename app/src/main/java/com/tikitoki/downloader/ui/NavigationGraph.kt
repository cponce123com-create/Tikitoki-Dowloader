package com.tikitoki.downloader.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.tikitoki.downloader.PlayerActivity
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.Density
import androidx.compose.ui.LayoutDirection
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
import com.tikitoki.downloader.ui.AboutDialog
import com.tikitoki.downloader.ui.AnalysisAnimation
import com.tikitoki.downloader.ui.AnalysisScreen
import com.tikitoki.downloader.ui.ClearTempDialog
import com.tikitoki.downloader.ui.DeleteConfirmDialog
import com.tikitoki.downloader.ui.DownloadQueueScreen
import com.tikitoki.downloader.ui.FilesScreen
import com.tikitoki.downloader.ui.FinishedBatchDialog
import com.tikitoki.downloader.ui.HomeLinkInput
import com.tikitoki.downloader.ui.LibraryScreen
import com.tikitoki.downloader.ui.MusicScreen
import com.tikitoki.downloader.ui.PlayerChoiceDialog
import com.tikitoki.downloader.ui.ProfileViewModel
import com.tikitoki.downloader.ui.SettingsAppearanceSection
import com.tikitoki.downloader.ui.SettingsGeneralSection
import com.tikitoki.downloader.ui.SettingsStorageSection
import com.tikitoki.downloader.ui.DownloadSettingsSection
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
import com.tikitoki.downloader.CollectionsScreen
import com.tikitoki.downloader.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Navigation routes for the app.
 */
object NavRoutes {
    const val HOME = "home"
    const val DOWNLOADS = "downloads"
    const val LIBRARY = "library"
    const val TOOLS = "tools"
    const val FILES = "files"
    const val SETTINGS = "settings"
    const val MUSIC = "music"
    const val COLLECTIONS = "collections"
}

/**
 * Navigation graph builder with all screens.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: String = NavRoutes.HOME,
    // Shared dependencies passed from MainActivity
    onNavigateToDownloads: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onShowClearTemp: () -> Unit,
    onShowAbout: () -> Unit,
    themeChoice: String,
    preferredQuality: String,
    // Additional dependencies
    focusManager: androidx.compose.ui.focus.FocusManager,
    keyboard: androidx.compose.ui.platform.SoftwareKeyboardController?,
    clipboard: androidx.compose.ui.platform.ClipboardManager,
    incomingLink: String,
    shareRevision: Int,
    context: Context,
    settings: android.content.SharedPreferences,
    source: TikTokVideoSource,
    thumbnailResolver: VideoThumbnailResolver,
    avatarResolver: ProfileAvatarResolver,
    galleryImageLoader: coil.ImageLoader,
    universalSource: UniversalMediaSource,
    profileViewModel: ProfileViewModel,
    profileState: com.tikitoki.downloader.ui.ProfileUiState,
    dao: com.tikitoki.downloader.data.DownloadDao,
    downloadsViewModel: DownloadsViewModel,
    records: androidx.compose.runtime.State<java.util.List<DownloadRecord>>,
    downloadSpeeds: androidx.compose.runtime.State<java.util.Map<String, String>>,
    finishedBatches: androidx.compose.runtime.State<java.util.List<com.tikitoki.downloader.data.BatchSummary>>,
    scope: kotlinx.coroutines.CoroutineScope,
    folderDescription: String,
    downloadFolder: String?,
    folderPicker: androidx.activity.result.ActivityResultLauncher<android.content.Intent>,
    networkSettingsBusy: androidx.compose.runtime.MutableState<Boolean>,
    wifiParallel: androidx.compose.runtime.MutableState<Int>,
    meteredParallel: androidx.compose.runtime.MutableState<Int>,
    allowMetered: androidx.compose.runtime.MutableState<Boolean>,
    rateKiB: androidx.compose.runtime.MutableState<Int>,
    showProgressNotice: androidx.compose.runtime.MutableState<Boolean>,
    completionNotice: androidx.compose.runtime.MutableState<Boolean>,
    monitorHours: androidx.compose.runtime.MutableState<Int>,
    monitorPages: androidx.compose.runtime.MutableState<Int>,
    libraryKind: androidx.compose.runtime.MutableState<String>,
    url: androidx.compose.runtime.MutableState<String>,
    monitorRevision: androidx.compose.runtime.MutableState<Int>,
    message: androidx.compose.runtime.MutableState<String>,
    inspecting: androidx.compose.runtime.MutableState<Boolean>,
    storagePermission: androidx.activity.result.ActivityResultLauncher<String>,
    notificationsPermission: androidx.activity.result.ActivityResultLauncher<String>,
    diagnosticPicker: androidx.activity.result.ActivityResultLauncher<String>,
    deleteCandidate: androidx.compose.runtime.MutableState<DownloadRecord?>,
    showClearTemp: androidx.compose.runtime.MutableState<Boolean>,
    showPlayerChoice: androidx.compose.runtime.MutableState<Boolean>,
    showAbout: androidx.compose.runtime.MutableState<Boolean>,
    musicRequestUri: androidx.compose.runtime.MutableState<String?>,
    musicRequestTitle: androidx.compose.runtime.MutableState<String>,
    onMusicRequestConsumed: () -> Unit,
    onOpenFile: (DownloadRecord) -> Unit,
    onDelete: (DownloadRecord) -> Unit,
    onKindChange: (String) -> Unit,
    onRetryFailed: () -> Unit,
    onClearErrors: () -> Unit,
    onFollowProfile: () -> Unit,
    onUnfollowProfile: (String) -> Unit,
    onUpdateExtractor: () -> Unit,
    onChooseFolder: () -> Unit,
    onResetFolder: () -> Unit,
    onMonitorHoursChange: (Int) -> Unit,
    onMonitorPagesChange: (Int) -> Unit,
    onWifiChange: (Int) -> Unit,
    onMeteredChange: (Int) -> Unit,
    onAllowMeteredChange: (Boolean) -> Unit,
    onRateChange: (Int) -> Unit,
    onProgressNoticeChange: (Boolean) -> Unit,
    onCompletionNoticeChange: (Boolean) -> Unit,
    onThemeChange: (String) -> Unit,
    onQualityChange: (String) -> Unit,
    onSettings: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: (String) -> Unit,
    onOpenPlayer: () -> Unit,
) {
    // ZeroWindowInsets workaround for Compose 1.6.x compatibility (WindowInsets.Companion.zero doesn't exist)
    val zeroInsets = object : WindowInsets {
        override fun getBottom(density: Density): Int = 0
        override fun getLeft(density: Density, layoutDirection: LayoutDirection): Int = 0
        override fun getRight(density: Density, layoutDirection: LayoutDirection): Int = 0
        override fun getTop(density: Density): Int = 0
    }

    NavHost(navController, startDestination) {
        composable(NavRoutes.HOME) {
            AnalysisScreen(
                incomingLink = incomingLink,
                shareRevision = shareRevision,
                onNavigateToDownloads = onNavigateToDownloads,
                onNavigateToSettings = onNavigateToSettings,
                onShowQualitySheet = { /* handled internally */ },
                onShowClearTemp = onShowClearTemp,
                onShowAbout = onShowAbout,
                themeChoice = themeChoice,
                preferredQuality = preferredQuality,
                onSettingsChanged = { /* settings are shared via SharedPreferences */ },
                insets = zeroInsets,
            )
        }
        composable(NavRoutes.DOWNLOADS) {
            DownloadQueueScreen(
                records = records.value,
                themeChoice = themeChoice,
                insets = zeroInsets,
                message = message.value,
                thumbnailResolver = thumbnailResolver,
                downloadSpeeds = downloadSpeeds.value,
                onSettings = onSettings,
                onPause = onPause,
                onResume = onResume,
                onCancel = onCancel,
            )
        }
        composable(NavRoutes.LIBRARY) {
            LibraryScreen(
                records = records.value,
                themeChoice = themeChoice,
                insets = zeroInsets,
                libraryKind = libraryKind.value,
                message = message.value,
                thumbnailResolver = thumbnailResolver,
                galleryImageLoader = galleryImageLoader,
                onSettings = onSettings,
                onKindChange = onKindChange,
                onMusic = { navController.navigate(NavRoutes.MUSIC) },
                onCollections = { navController.navigate(NavRoutes.COLLECTIONS) },
                onOpen = onOpenFile,
                onDelete = onDelete,
            )
        }
        composable(NavRoutes.TOOLS) {
            val monitored = remember(monitorRevision.value) { ProfileMonitor.profiles(context).sorted() }
            ToolsScreen(
                monitored = monitored,
                failedCount = records.value.count { it.status == DownloadState.FAILED },
                currentUrl = url.value,
                profileImage = profileState.profileImage,
                avatarResolver = avatarResolver,
                message = message.value,
                onRetryFailed = onRetryFailed,
                onClearErrors = onClearErrors,
                onOpenPlayer = onOpenPlayer,
                onFollowProfile = onFollowProfile,
                onUnfollowProfile = onUnfollowProfile,
            )
        }
        composable(NavRoutes.FILES) {
            FilesScreen(
                themeChoice = themeChoice,
                folderDescription = folderDescription,
                downloadFolder = downloadFolder,
                message = message.value,
                onSettingsClick = onSettings,
                onChooseFolder = onChooseFolder,
                onResetFolder = onResetFolder,
                onOpenFolder = { /* handled by FilesScreen internally */ },
                completedCount = records.value.count { it.status == DownloadState.COMPLETED },
                settings = settings,
            )
        }
        composable(NavRoutes.SETTINGS) {
            SettingsScreen(
                themeChoice = themeChoice,
                preferredQuality = preferredQuality,
                folderDescription = folderDescription,
                downloadFolder = downloadFolder,
                message = message,
                monitorHours = monitorHours,
                monitorPages = monitorPages,
                wifiParallel = wifiParallel,
                meteredParallel = meteredParallel,
                allowMetered = allowMetered,
                networkSettingsBusy = networkSettingsBusy,
                rateKiB = rateKiB,
                showProgressNotice = showProgressNotice,
                completionNotice = completionNotice,
                hasCustomFolder = downloadFolder != null,
                onAbout = { showAbout.value = true },
                onExportDiagnostic = { diagnosticPicker.launch(context.getString(R.string.diagnostic_filename)) },
                onMonitorHoursChange = onMonitorHoursChange,
                onMonitorPagesChange = onMonitorPagesChange,
                onUpdateExtractor = onUpdateExtractor,
                onChooseFolder = onChooseFolder,
                onResetFolder = onResetFolder,
                onThemeChange = onThemeChange,
                onQualityChange = onQualityChange,
                onWifiChange = onWifiChange,
                onMeteredChange = onMeteredChange,
                onAllowMeteredChange = onAllowMeteredChange,
                onRateChange = onRateChange,
                onProgressNoticeChange = onProgressNoticeChange,
                onCompletionNoticeChange = onCompletionNoticeChange,
                onClearTemporary = { showClearTemp.value = true },
                settings = settings,
                folderPicker = folderPicker,
                context = context,
                scope = scope,
            )
        }
        composable(NavRoutes.MUSIC) {
            MusicScreen(
                requestedUri = musicRequestUri.value,
                requestedTitle = musicRequestTitle.value,
                onRequestConsumed = onMusicRequestConsumed,
            )
        }
        composable(NavRoutes.COLLECTIONS) {
            CollectionsScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .background(TechBackground)
                    .padding(androidx.compose.foundation.layout.WindowInsets.safeDrawing)
                    .consumeWindowInsets(androidx.compose.foundation.layout.WindowInsets.safeDrawing),
                onBack = { navController.popBackStack() },
                onOpenPlaylist = { link ->
                    url.value = link
                    profileViewModel.reset()
                    // Clear selected videos
                    message.value = ""
                    navController.navigate(NavRoutes.HOME) {
                        popUpTo(NavRoutes.HOME) { inclusive = false }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
    }
}

/**
 * Settings screen extracted from MainActivity.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SettingsScreen(
    themeChoice: String,
    preferredQuality: String,
    folderDescription: String,
    downloadFolder: String?,
    message: androidx.compose.runtime.MutableState<String>,
    monitorHours: androidx.compose.runtime.MutableState<Int>,
    monitorPages: androidx.compose.runtime.MutableState<Int>,
    wifiParallel: androidx.compose.runtime.MutableState<Int>,
    meteredParallel: androidx.compose.runtime.MutableState<Int>,
    allowMetered: androidx.compose.runtime.MutableState<Boolean>,
    networkSettingsBusy: androidx.compose.runtime.MutableState<Boolean>,
    rateKiB: androidx.compose.runtime.MutableState<Int>,
    showProgressNotice: androidx.compose.runtime.MutableState<Boolean>,
    completionNotice: androidx.compose.runtime.MutableState<Boolean>,
    hasCustomFolder: Boolean,
    onAbout: () -> Unit,
    onExportDiagnostic: () -> Unit,
    onMonitorHoursChange: (Int) -> Unit,
    onMonitorPagesChange: (Int) -> Unit,
    onUpdateExtractor: () -> Unit,
    onChooseFolder: () -> Unit,
    onResetFolder: () -> Unit,
    onThemeChange: (String) -> Unit,
    onQualityChange: (String) -> Unit,
    onWifiChange: (Int) -> Unit,
    onMeteredChange: (Int) -> Unit,
    onAllowMeteredChange: (Boolean) -> Unit,
    onRateChange: (Int) -> Unit,
    onProgressNoticeChange: (Boolean) -> Unit,
    onCompletionNoticeChange: (Boolean) -> Unit,
    onClearTemporary: () -> Unit,
    settings: android.content.SharedPreferences,
    folderPicker: androidx.activity.result.ActivityResultLauncher<android.content.Intent>,
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
) {
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
                .padding(androidx.compose.foundation.layout.WindowInsets.safeDrawing)
                .consumeWindowInsets(androidx.compose.foundation.layout.WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        TechHeader(onSettings = { /* already in settings */ })
        SettingsGeneralSection(
            monitorHours = monitorHours.value,
            monitorPages = monitorPages.value,
            folderDescription = folderDescription,
            hasCustomFolder = hasCustomFolder,
            message = message.value,
            onAbout = onAbout,
            onExportDiagnostic = onExportDiagnostic,
            onMonitorHoursChange = onMonitorHoursChange,
            onMonitorPagesChange = onMonitorPagesChange,
            onUpdateExtractor = onUpdateExtractor,
            onChooseFolder = onChooseFolder,
            onResetFolder = onResetFolder,
        )
        SettingsAppearanceSection(
            themeChoice = themeChoice,
            preferredQuality = preferredQuality,
            onThemeChange = onThemeChange,
            onQualityChange = onQualityChange,
        )
        DownloadSettingsSection(
            wifiParallel = wifiParallel.value,
            meteredParallel = meteredParallel.value,
            allowMetered = allowMetered.value,
            networkSettingsBusy = networkSettingsBusy.value,
            rateKiB = rateKiB.value,
            showProgressNotice = showProgressNotice.value,
            completionNotice = completionNotice.value,
            onWifiChange = onWifiChange,
            onMeteredChange = onMeteredChange,
            onAllowMeteredChange = onAllowMeteredChange,
            onRateChange = onRateChange,
            onProgressNoticeChange = onProgressNoticeChange,
            onCompletionNoticeChange = onCompletionNoticeChange,
        )
        SettingsStorageSection(
            message = message.value,
            onClearTemporary = onClearTemporary,
        )
    }
}

/**
 * Remember NavController with proper configuration.
 */
@Composable
fun rememberAppNavController(): NavHostController = rememberNavController()