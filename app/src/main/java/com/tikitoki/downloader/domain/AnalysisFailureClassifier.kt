package com.tikitoki.downloader.domain

import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.UnknownHostException
import java.util.Locale

/** Classify failures without exposing extractor output, URLs, or local paths to the UI. */
internal enum class AnalysisFailureKind {
    RATE_LIMIT,
    ACCESS,
    NETWORK,
    EXTRACTOR_RESPONSE,
    UNKNOWN,
}

internal object AnalysisFailureClassifier {
    fun classify(error: Throwable): AnalysisFailureKind {
        val causes = generateSequence(error) { it.cause }.take(6).toList()
        val details = causes.mapNotNull { it.message }.joinToString(" ").lowercase(Locale.ROOT)
        return when {
            "http error 429" in details || "(429)" in details || "too many requests" in details || "limitó temporalmente" in details ->
                AnalysisFailureKind.RATE_LIMIT
            "http error 403" in details || "(403)" in details || "http error 401" in details || "login required" in details ||
                "private video" in details || "requires authentication" in details ||
                "no es público" in details || "autenticación" in details ->
                AnalysisFailureKind.ACCESS
            causes.any { it is UnknownHostException || it is ConnectException || it is InterruptedIOException } ||
                "timed out" in details || "timeout" in details || "connection" in details ||
                "unable to resolve host" in details || "network is unreachable" in details ->
                AnalysisFailureKind.NETWORK
            "unable to extract webpage video data" in details ||
                "unexpected response from webpage" in details ||
                "secondary user id" in details ||
                "failed to parse json" in details ||
                "tiktok cambió la respuesta" in details ->
                AnalysisFailureKind.EXTRACTOR_RESPONSE
            else -> AnalysisFailureKind.UNKNOWN
        }
    }
}
