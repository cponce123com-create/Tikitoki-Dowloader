package com.tikitoki.downloader.di

import com.tikitoki.downloader.data.*
import com.tikitoki.downloader.ui.DownloadsViewModel
import com.tikitoki.downloader.ui.ProfileViewModel
import org.koin.android.logger.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.core.viewmodel.dsl.viewModel
import org.koin.core.scope.scope

/**
 * Koin module for dependency injection.
 * Declares all ViewModels, repositories, and data sources.
 */
val appModule = module {
    // ViewModels
    viewModel { DownloadsViewModel(get()) }
    viewModel { ProfileViewModel(get()) }

    // Repository / Data layer
    single { DownloadDatabase(get()) }
    single { DownloadQueue(get(), get(), get()) }
    single { EngineRegistry(get(), get()) }
    single { ExtractorAccess(get()) }
    single { ExtractorRecovery(get()) }
    single { DownloadSettings(get()) }
    single { LibraryStats(get()) }
    single { LibraryExport(get()) }
    single { DiagnosticHistory(get()) }
    single { DiagnosticReport(get()) }
    single { LibraryVisibility(get()) }
    single { FormatAvailability(get()) }
    single { DownloadTelemetry(get()) }
    single { CompletionNotifier(get()) }
    single { MonitorPolicy(get()) }
    single { ProfileMonitor(get()) }
    single { NotificationIntents(get()) }
    single { VideoThumbnailResolver(get()) }
    single { ProfileAvatarResolver(get()) }
    single { TemporaryFiles(get()) }
    single { ScanPage(get()) }
    single { SavedFileDeletion(get()) }
    single { DownloadProgressParser(get()) }
    single { AdaptiveQueue(get()) }
    single { ParallelQueue(get()) }
    single { BoundedInput(get()) }
    single { EngineFallback(get()) }
    single { ExtractorVersion(get()) }
    single { ExtractorRollback(get()) }
    single { FormatOptions(get()) }
    single { NonFatal(get()) }
    single { NewPipeSource(get()) }
    single { TikTokVideoSource(get()) }
    single { UniversalMediaSource(get()) }
    single { DestinationNaming(get()) }
    single { DownloadFormatPreference(get()) }
    single { DownloadIdentity(get()) }
    single { IncomingIntentReader(get()) }
    single { PlaybackKind(get()) }
    single { ProfileDiff(get()) }
    single { ProfileSnapshot(get()) }
    single { ThumbnailUrls(get()) }
    single { TikTokLinkParser(get()) }
    single { VideoPagination(get()) }
    single { AnalysisFailureClassifier(get()) }
    single { DownloadQueueActions(get()) }
    single { AndroidDownloadQueueActions(get()) }

    // Workers (scoped to work context)
    scope<androidx.work.Worker> {
        scoped { DownloadWorker(get(), get(), get(), get(), get(), get()) }
        scoped { ExtractorUpdateWorker(get(), get(), get()) }
    }

    // Extractor access is initialized early
    factory(named("extractorRecoveryReady")) { ExtractorAccess.isRecoveryReady }
}

/**
 * Initializes Koin with all modules.
 * Called from [BajaApplication.onCreate] or test setup.
 */
fun initializeKoin(): org.koin.core.context.GlobalContext = startKoin {
    androidLogger()
    modules(appModule)
}