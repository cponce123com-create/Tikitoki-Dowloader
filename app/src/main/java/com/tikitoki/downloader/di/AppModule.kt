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
import android.content.Context

/**
 * Koin module for dependency injection.
 * Declares all ViewModels, repositories, and data sources.
 * Objects (true Kotlin singletons) are bound with single { ObjectName } (no parameters).
 * Classes with constructor dependencies use single { ClassName(get(), ...) }.
 * Top-level function files (AdaptiveQueue, BoundedInput, ParallelQueue, NotificationIntents,
 * TemporaryFiles, SavedFileDeletion, NonFatal) are NOT included - they have no DI needs.
 */
val appModule = module {
    // ViewModels
    viewModel { DownloadsViewModel(get()) }
    viewModel { ProfileViewModel(get()) }

    // Repository / Data layer - Objects (true Kotlin singletons, no constructor params)
    single { DownloadDatabase.get(androidContext()) } // Uses companion object get(Context)
    single { DownloadQueue } // object
    single { EngineRegistry } // object
    single { ExtractorAccess } // object
    single { ExtractorRecovery } // object
    single { DownloadSettings } // object
    single { LibraryStats } // object (LibraryAnalytics in code, bound as LibraryStats interface)
    single { LibraryExport } // object
    single { DiagnosticHistory } // object
    single { DiagnosticReport } // object
    single { LibraryVisibility } // object
    single { FormatAvailability } // object
    single { DownloadTelemetry } // object
    single { CompletionNotifier } // object
    single { MonitorPolicy } // object
    single { ProfileMonitor } // object
    single { VideoThumbnailResolver(get()) } // class with Context
    single { ProfileAvatarResolver(get()) } // class with Context
    single { ScanPage(get()) } // class with Context
    single { DownloadProgressParser } // object
    single { EngineFallback(get()) } // class with Context
    single { ExtractorVersion } // object
    single { ExtractorRollback } // object
    single { FormatOptions } // object
    single { NewPipeSource(get()) } // class with Context
    single { TikTokVideoSource(get()) } // class with Context
    single { UniversalMediaSource(get()) } // class with Context
    single { DestinationNaming } // object (domain)
    single { DownloadFormatPreference } // object (domain)
    single { DownloadIdentity } // object (domain)
    single { IncomingIntentReader } // object (domain)
    single { PlaybackKind } // object (domain)
    single { ProfileDiff } // object (domain)
    single { ProfileSnapshot } // object (domain)
    single { ThumbnailUrls } // object (domain)
    single { TikTokLinkParser } // object (domain)
    single { VideoPagination } // object (domain)
    single { AnalysisFailureClassifier } // object (domain)
    // DownloadQueueActions interface bound to implementation
    single<DownloadQueueActions> { AndroidDownloadQueueActions(get()) }

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