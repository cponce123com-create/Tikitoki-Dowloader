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
 * Navigation graph builder with all screens - now using MainViewModel for state.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AppNavHost(
    navController: NavHostController,
    viewModel: MainViewModel,
    startDestination: String = NavRoutes.HOME,
    // UI callbacks that need Activity context
    folderPicker: androidx.activity.result.ActivityResultLauncher<android.content.Intent>,
    storagePermission: androidx.activity.result.ActivityResultLauncher<String>,
    notificationsPermission: androidx.activity.result.ActivityResultLauncher<String>,
    diagnosticPicker: androidx.activity.result.ActivityResultLauncher<String>,
    focusManager: androidx.compose.ui.focus.FocusManager,
    keyboard: androidx.compose.ui.platform.SoftwareKeyboardController?,
    clipboard: androidx.compose.ui.platform.ClipboardManager,
    context: Context,
    settings: android.content.SharedPreferences,
    incomingLink: String,
    shareRevision: Int,
    galleryImageLoader: coil.ImageLoader,
    thumbnailResolver: VideoThumbnailResolver,
    avatarResolver: ProfileAvatarResolver,
) {
    // ZeroWindowInsets workaround for Compose 1.6.x compatibility (WindowInsets.Companion.zero doesn't exist)
    val zeroInsets = object : WindowInsets {
        override fun getBottom(density: Density): Int = 0
        override fun getLeft(density: Density, layoutDirection: LayoutDirection): Int = 0
        override fun getRight(density: Density, layoutDirection: LayoutDirection): Int = 0
        override fun getTop(density: Density): Int = 0
    }

    // Observe ViewModel state for screens that need derived data
    val records by viewModel.records.collectAsState()
    val downloadSpeeds by viewModel.downloadSpeeds.collectAsState()
    val finishedBatches by viewModel.finishedBatches.collectAsState()
    val profileState by viewModel.profileState.collectAsState()
    val message by viewModel.message.collectAsState()
    val themeChoice by viewModel.themeChoice.collectAsState()
    val preferredQuality by viewModel.preferredQuality.collectAsState()
    val libraryKind by viewModel.libraryKind.collectAsState()
    val url by viewModel.url.collectAsState()
    val monitorRevision by viewModel.monitorRevision.collectAsState()
    val monitorHours by viewModel.monitorHours.collectAsState()
    val monitorPages by viewModel.monitorPages.collectAsState()
    val wifiParallel by viewModel.wifiParallel.collectAsState()
    val meteredParallel by viewModel.meteredParallel.collectAsState()
    val allowMetered by viewModel.allowMetered.collectAsState()
    val networkSettingsBusy by viewModel.networkSettingsBusy.collectAsState()
    val rateKiB by viewModel.rateKiB.collectAsState()
    val showProgressNotice by viewModel.showProgressNotice.collectAsState()
    val completionNotice by viewModel.completionNotice.collectAsState()
    val downloadFolder by viewModel.downloadFolder.collectAsState()
    val showClearTemp by viewModel.showClearTemp.collectAsState()
    val showPlayerChoice by viewModel.showPlayerChoice.collectAsState()
    val showAbout by viewModel.showAbout.collectAsState()
    val musicRequestUri by viewModel.musicRequestUri.collectAsState()
    val musicRequestTitle by viewModel.musicRequestTitle.collectAsState()
    val queueBusy by viewModel.queueBusy.collectAsState()
    val formatsBusy by viewModel.formatsBusy.collectAsState()
    val formatError by viewModel.formatError.collectAsState()
    val showQualitySheet by viewModel.showQualitySheet.collectAsState()
    val formats by viewModel.formats.collectAsState()
    val chosenFormat by viewModel.chosenFormat.collectAsState()
    val rememberFormat by viewModel.rememberFormat.collectAsState()
    val batchChecking by viewModel.batchChecking.collectAsState()
    val batchChecked by viewModel.batchChecked.collectAsState()
    val batchFailed by viewModel.batchFailed.collectAsState()
    val batchTotal by viewModel.batchTotal.collectAsState()
    val batchCounts by viewModel.batchCounts.collectAsState()
    val batchGeneration by viewModel.batchGeneration.collectAsState()
    val inspecting by viewModel.inspecting.collectAsState()
    val search by viewModel.search.collectAsState()
    val onlyUndownloaded by viewModel.onlyUndownloaded.collectAsState()
    val forceRefresh by viewModel.forceRefresh.collectAsState()
    val selectedVideos by viewModel.selectedVideos.collectAsState()
    val defaultFormat by viewModel.defaultFormat.collectAsState()

    NavHost(navController, startDestination) {
        composable(NavRoutes.HOME) {
            AnalysisScreen(
                incomingLink = incomingLink,
                shareRevision = shareRevision,
                onNavigateToDownloads = viewModel.onNavigateToDownloads,
                onNavigateToSettings = viewModel.onNavigateToSettings,
                onShowQualitySheet = { /* handled internally */ },
                onShowClearTemp = viewModel.onShowClearTemp,
                onShowAbout = viewModel.onShowAbout,
                themeChoice = themeChoice,
                preferredQuality = preferredQuality,
                onSettingsChanged = { /* settings are shared via SharedPreferences */ },
                insets = zeroInsets,
                // ViewModel-driven state
                url = url,
                search = search,
                onlyUndownloaded = onlyUndownloaded,
                forceRefresh = forceRefresh,
                inspecting = inspecting,
                formatsBusy = formatsBusy,
                formats = formats,
                chosenFormat = chosenFormat,
                rememberFormat = rememberFormat,
                formatError = formatError,
                showQualitySheet = showQualitySheet,
                batchChecking = batchChecking,
                batchChecked = batchChecked,
                batchFailed = batchFailed,
                batchTotal = batchTotal,
                batchCounts = batchCounts,
                batchGeneration = batchGeneration,
                selectedVideos = selectedVideos,
                defaultFormat = defaultFormat,
                profileState = profileState,
                // Additional state from ViewModel
                records = records,
                downloadSpeeds = downloadSpeeds,
                finishedBatches = finishedBatches,
                thumbnailResolver = thumbnailResolver,
                onUrlChange = viewModel::onUrlChange,
                onSearchChange = viewModel::onSearchChange,
                onOnlyUndownloadedChange = viewModel::onOnlyUndownloadedChange,
                onForceRefreshChange = viewModel::onForceRefreshChange,
                onInspectLink = viewModel::onInspectLink,
                onResumeScan = viewModel::onResumeScan,
                onSkipIncompletePage = viewModel::onSkipIncompletePage,
                onChooseQuality = { ids -> viewModel.chooseQuality(ids) },
                onFormatSelected = viewModel::onFormatSelected,
                onRememberFormatChange = viewModel::onRememberFormatChange,
                onDownloadSelected = viewModel::onDownloadSelected,
                onNavigateToHome = viewModel.onNavigateToHome,
            )
        }
        composable(NavRoutes.DOWNLOADS) {
            DownloadQueueScreen(
                records = records,
                themeChoice = themeChoice,
                insets = zeroInsets,
                message = message,
                thumbnailResolver = viewModel.thumbnailResolver,
                downloadSpeeds = downloadSpeeds,
                onSettings = viewModel.onNavigateToSettings,
                onPause = viewModel.onPauseQueue,
                onResume = viewModel.onResumeQueue,
                onCancel = viewModel::onCancelDownload,
            )
        }
        composable(NavRoutes.LIBRARY) {
            LibraryScreen(
                records = records,
                themeChoice = themeChoice,
                insets = zeroInsets,
                libraryKind = libraryKind,
                message = message,
                thumbnailResolver = viewModel.thumbnailResolver,
                galleryImageLoader = galleryImageLoader,
                onSettings = viewModel.onNavigateToSettings,
                onKindChange = viewModel::onLibraryKindChange,
                onMusic = { navController.navigate(NavRoutes.MUSIC) },
                onCollections = { navController.navigate(NavRoutes.COLLECTIONS) },
                onOpen = viewModel::onOpenFile,
                onDelete = viewModel::onDeleteVideo,
                onHistoryFilterChange = viewModel::onHistoryFilterChange,
                historyFilter = viewModel.historyFilter.collectAsState(),
            )
        }
        composable(NavRoutes.TOOLS) {
            val monitored = remember(monitorRevision) { ProfileMonitor.profiles(context).sorted() }
            ToolsScreen(
                monitored = monitored,
                failedCount = records.count { it.status == DownloadState.FAILED },
                currentUrl = url,
                profileImage = profileState.profileImage,
                avatarResolver = avatarResolver,
                message = message,
                onRetryFailed = viewModel.onRetryFailed,
                onClearErrors = viewModel.onClearErrors,
                onOpenPlayer = viewModel.onOpenPlayer,
                onFollowProfile = { viewModel.onFollowProfile(url) },
                onUnfollowProfile = viewModel::onUnfollowProfile,
                onUpdateExtractor = viewModel.onUpdateExtractor,
            )
        }
        composable(NavRoutes.FILES) {
            val folderDescription = remember(downloadFolder) {
                androidx.compose.runtime.produceState(initialValue = "Comprobando carpeta…", key1 = downloadFolder) {
                    val tree = downloadFolder
                    value =
                        if (tree == null) {
                            "Descargas/TikitokiDownloader"
                        } else {
                            withContext(Dispatchers.IO) {
                                kotlinx.coroutines.runCatching {
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
            }
            FilesScreen(
                themeChoice = themeChoice,
                folderDescription = folderDescription.value,
                downloadFolder = downloadFolder,
                message = message,
                onSettingsClick = viewModel.onNavigateToSettings,
                onChooseFolder = { viewModel.onChooseFolder(folderPicker) },
                onResetFolder = viewModel.onResetFolder,
                onOpenFolder = { /* handled by FilesScreen internally */ },
                completedCount = records.count { it.status == DownloadState.COMPLETED },
                settings = settings,
            )
        }
        composable(NavRoutes.SETTINGS) {
            SettingsScreen(
                themeChoice = themeChoice,
                preferredQuality = preferredQuality,
                folderDescription = "", // computed in FilesScreen now
                downloadFolder = downloadFolder,
                message = viewModel.message,
                monitorHours = viewModel.monitorHours,
                monitorPages = viewModel.monitorPages,
                wifiParallel = viewModel.wifiParallel,
                meteredParallel = viewModel.meteredParallel,
                allowMetered = viewModel.allowMetered,
                networkSettingsBusy = viewModel.networkSettingsBusy,
                rateKiB = viewModel.rateKiB,
                showProgressNotice = viewModel.showProgressNotice,
                completionNotice = viewModel.completionNotice,
                hasCustomFolder = downloadFolder != null,
                onAbout = viewModel.onShowAbout,
                onExportDiagnostic = { diagnosticPicker.launch(context.getString(R.string.diagnostic_filename)) },
                onMonitorHoursChange = viewModel::onMonitorHoursChange,
                onMonitorPagesChange = viewModel::onMonitorPagesChange,
                onUpdateExtractor = viewModel.onUpdateExtractor,
                onChooseFolder = { viewModel.onChooseFolder(folderPicker) },
                onResetFolder = viewModel.onResetFolder,
                onThemeChange = viewModel::onThemeChange,
                onQualityChange = viewModel::onQualityChange,
                onWifiChange = viewModel::onWifiChange,
                onMeteredChange = viewModel::onMeteredChange,
                onAllowMeteredChange = viewModel::onAllowMeteredChange,
                onRateChange = viewModel::onRateChange,
                onProgressNoticeChange = viewModel::onProgressNoticeChange,
                onCompletionNoticeChange = viewModel::onCompletionNoticeChange,
                onClearTemporary = viewModel.onShowClearTemp,
                settings = settings,
                folderPicker = folderPicker,
                context = context,
                scope = viewModel.viewModelScope,
            )
        }
        composable(NavRoutes.MUSIC) {
            MusicScreen(
                requestedUri = musicRequestUri,
                requestedTitle = musicRequestTitle,
                onRequestConsumed = viewModel.onMusicRequestConsumed,
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
                    viewModel.handleIncomingLink(link, 1)
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
    message: String,
    monitorHours: kotlinx.coroutines.flow.StateFlow<Int>,
    monitorPages: kotlinx.coroutines.flow.StateFlow<Int>,
    wifiParallel: kotlinx.coroutines.flow.StateFlow<Int>,
    meteredParallel: kotlinx.coroutines.flow.StateFlow<Int>,
    allowMetered: kotlinx.coroutines.flow.StateFlow<Boolean>,
    networkSettingsBusy: kotlinx.coroutines.flow.StateFlow<Boolean>,
    rateKiB: kotlinx.coroutines.flow.StateFlow<Int>,
    showProgressNotice: kotlinx.coroutines.flow.StateFlow<Boolean>,
    completionNotice: kotlinx.coroutines.flow.StateFlow<Boolean>,
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
    val monitorHoursValue by monitorHours.collectAsState()
    val monitorPagesValue by monitorPages.collectAsState()
    val wifiParallelValue by wifiParallel.collectAsState()
    val meteredParallelValue by meteredParallel.collectAsState()
    val allowMeteredValue by allowMetered.collectAsState()
    val networkSettingsBusyValue by networkSettingsBusy.collectAsState()
    val rateKiBValue by rateKiB.collectAsState()
    val showProgressNoticeValue by showProgressNotice.collectAsState()
    val completionNoticeValue by completionNotice.collectAsState()

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
            monitorHours = monitorHoursValue,
            monitorPages = monitorPagesValue,
            folderDescription = folderDescription,
            hasCustomFolder = hasCustomFolder,
            message = message,
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
            wifiParallel = wifiParallelValue,
            meteredParallel = meteredParallelValue,
            allowMetered = allowMeteredValue,
            networkSettingsBusy = networkSettingsBusyValue,
            rateKiB = rateKiBValue,
            showProgressNotice = showProgressNoticeValue,
            completionNotice = completionNoticeValue,
            onWifiChange = onWifiChange,
            onMeteredChange = onMeteredChange,
            onAllowMeteredChange = onAllowMeteredChange,
            onRateChange = onRateChange,
            onProgressNoticeChange = onProgressNoticeChange,
            onCompletionNoticeChange = onCompletionNoticeChange,
        )
        SettingsStorageSection(
            message = message,
            onClearTemporary = onClearTemporary,
        )
    }
}

/**
 * Remember NavController with proper configuration.
 */
@Composable
fun rememberAppNavController(): NavHostController = rememberNavController()