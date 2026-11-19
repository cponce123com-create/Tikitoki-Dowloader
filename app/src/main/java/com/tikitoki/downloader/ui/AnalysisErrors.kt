package com.tikitoki.downloader.ui

import android.app.Application
import com.tikitoki.downloader.R
import com.tikitoki.downloader.data.DiagnosticHistory
import com.tikitoki.downloader.data.ExtractorUpdateWorker
import com.tikitoki.downloader.domain.AnalysisFailureClassifier
import com.tikitoki.downloader.domain.AnalysisFailureKind
import com.tikitoki.downloader.domain.MediaPlatform
import kotlinx.coroutines.CancellationException

internal object AnalysisErrors {
    fun message(
        app: Application,
        error: Exception,
        platform: MediaPlatform,
    ): String {
        val resource =
            when (AnalysisFailureClassifier.classify(error)) {
                AnalysisFailureKind.RATE_LIMIT -> R.string.analysis_error_rate_limit
                AnalysisFailureKind.ACCESS -> R.string.analysis_error_access
                AnalysisFailureKind.NETWORK -> R.string.analysis_error_network
                AnalysisFailureKind.TIKTOK_PROFILE_ID -> R.string.analysis_error_tiktok_profile_id
                AnalysisFailureKind.EXTRACTOR_RESPONSE ->
                    if (platform == MediaPlatform.TIKTOK) {
                        requestUpdate(app)
                    } else {
                        R.string.analysis_error_generic
                    }
                AnalysisFailureKind.UNKNOWN ->
                    if (platform == MediaPlatform.TIKTOK) {
                        R.string.analysis_error_tiktok
                    } else {
                        R.string.analysis_error_generic
                    }
            }
        return app.getString(resource)
    }

    private fun requestUpdate(app: Application): Int =
        try {
            ExtractorUpdateWorker.updateNow(app)
            R.string.analysis_error_update_requested
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            DiagnosticHistory.record(app, failure.message)
            R.string.analysis_error_update_manual
        }
}
