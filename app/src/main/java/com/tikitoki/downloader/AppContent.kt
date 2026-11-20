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
import com.tikitoki.downloader.data.ProfileAvatarResolver
import com.tikitoki.downloader.data.ProfileMonitor
import com.tikitoki.downloader.data.TikTokVideoSource
import com.tikitoki.downloader.data.UniversalMediaSource
import com.tikitoki.downloader.data.VideoEntry
import com.tikitoki.downloader.data.VideoThumbnailResolver
import com.tikitoki.downloader.data.rethrowIfFatal
import com.tikitoki.downloader.domain.DownloadFormatPreference
import com.tikitoki.downloader.domain.IncomingLinkValidator
import com.tikitoki.downloader.domain.PlaybackKind
import com.tikitoki.downloader.ui.DownloadsViewModel
import com.tikitoki.downloader.ui.MainViewModel
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
    val downloadsViewModel: DownloadsViewModel = viewModel()
    val dao = remember(context) { DownloadDatabase.get(context).downloads() }

    val mainViewModel = remember {
        MainViewModel(
            application = context.applicationContext as android.app.Application,
            profileViewModel = profileViewModel,
            downloadsViewModel = downloadsViewModel,
            source = source,
            universalSource = universalSource,
            thumbnailResolver = thumbnailResolver,
            avatarResolver = avatarResolver,
            galleryImageLoader = galleryImageLoader,
            dao = dao,
            settings = settings,
        )
    }

    // Observe incoming link and share revision
    LaunchedEffect(incomingLink, shareRevision) {
        mainViewModel.handleIncomingLink(incomingLink, shareRevision)
    }

    // Observe URL changes for clipboard debounce
    val url by mainViewModel.url.collectAsState()
    val analysisRevision by mainViewModel.analysisRevision.collectAsState()
    LaunchedEffect(url, analysisRevision) {
        val link = IncomingLinkValidator.validate(url)?.url ?: return@LaunchedEffect
        delay(CLIPBOARD_DEBOUNCE_MS)
        if (!profileViewModel.isCurrentLink(link)) profileViewModel.inspect(link, false)
    }

    // Permission launchers
    val storagePermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            mainViewModel.onStoragePermissionGranted(granted)
        }

    val notificationsPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { /* Optional */ }

    val folderPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                    mainViewModel.setDownloadFolder(uri.toString())
                } catch (e: Exception) {
                    rethrowIfFatal(e)
                    DiagnosticHistory.record(context, e.message)
                    mainViewModel.setDownloadFolder(null)
                }
            }
        }

    val diagnosticPicker = rememberDiagnosticPicker { mainViewModel.setMessage(context.getString(R.string.diagnostic_error)) }

    MaterialTheme(colorScheme = TikitokiColors) {
        // Dialogs - pass ViewModel callbacks
        AppDialogs(
            context = context,
            settings = settings,
            showAbout = mainViewModel.showAbout.collectAsState().value,
            onDismissAbout = { mainViewModel.onAboutDismiss() },
            onOpenSources = { mainViewModel.onOpenSources() },
            showPlayerChoice = mainViewModel.showPlayerChoice.collectAsState().value,
            onDismissPlayerChoice = { mainViewModel.onPlayerChoiceDismiss() },
            onChooseVideos = { mainViewModel.onChooseVideos() },
            onChooseMusic = { mainViewModel.onChooseMusic() },
            qualityVideos = mainViewModel.qualityVideos.collectAsState().value,
            source = source,
            universalSource = universalSource,
            thumbnailResolver = thumbnailResolver,
            onDismissFormatSheet = { mainViewModel.dismissFormatSheet() },
            onSaveFormat = { id, label, height -> mainViewModel.saveDefaultFormat(id, label, height) },
            onRequestDownload = { items -> mainViewModel.requestDownload(items) },
            finishedBatch = mainViewModel.finishedBatches.collectAsState().value.firstOrNull(),
            onAcknowledgeBatch = { batchId -> mainViewModel.acknowledgeBatch(batchId) },
            showClearTemp = mainViewModel.showClearTemp.collectAsState().value,
            onDismissClearTemp = { mainViewModel.setShowClearTemp(false) },
            onClearTemporary = { mainViewModel.clearTemporaryFiles() },
            deleteCandidate = mainViewModel.deleteCandidate.collectAsState().value,
            onDismissDelete = { mainViewModel.dismissDelete() },
            onDelete = { item -> mainViewModel.confirmDelete(item) },
        )

        // Navigation content - pass MainViewModel
        AppNavigationContent(
            viewModel = mainViewModel,
            context = context,
            settings = settings,
            focusManager = focusManager,
            keyboard = keyboard,
            clipboard = clipboard,
            scope = scope,
            folderPicker = folderPicker,
            storagePermission = storagePermission,
            notificationsPermission = notificationsPermission,
            diagnosticPicker = diagnosticPicker,
        )
    }
}