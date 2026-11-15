package com.tikitoki.downloader.data

import java.util.Locale

/** Restrict output to fixed labels; never return raw extractor errors or file locations. */
internal enum class ErrorCategory { RATE_LIMIT, TIMEOUT, NETWORK, PERMISSION, OTHER }

internal object DiagnosticReport {
    fun errorCategory(raw: String?): ErrorCategory {
        val text = raw.orEmpty().lowercase(Locale.ROOT)
        return when {
            "429" in text || "too many requests" in text -> ErrorCategory.RATE_LIMIT
            "timeout" in text || "timed out" in text -> ErrorCategory.TIMEOUT
            "network" in text || "connection" in text -> ErrorCategory.NETWORK
            "permission" in text || "permiso" in text -> ErrorCategory.PERMISSION
            else -> ErrorCategory.OTHER
        }
    }
}
