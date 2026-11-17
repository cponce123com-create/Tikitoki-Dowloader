package com.tikitoki.downloader

import android.app.Application
import android.content.Context
import com.tikitoki.downloader.data.ExtractorAccess
import com.tikitoki.downloader.data.ExtractorRecovery
import com.tikitoki.downloader.di.initializeKoin

class BajaApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // Runs before providers and workers can initialize the extractor.
        ExtractorAccess.setRecoveryReady(runCatching { ExtractorRecovery.recover(base); true }.getOrDefault(false))
    }

    override fun onCreate() {
        super.onCreate()
        // Initialize Koin dependency injection
        initializeKoin()
    }
}
