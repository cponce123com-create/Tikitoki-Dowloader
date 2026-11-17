package com.tikitoki.downloader.domain

import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.UnknownHostException
import java.util.Locale

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
        fun containsAny(vararg terms: String): Boolean = terms.any(details::contains)
        return when {
            containsAny("http error 429", "(429)", "too many requests", "limitó temporalmente") ->
                AnalysisFailureKind.RATE_LIMIT
            containsAny(
                "http error 403",
                "(403)",
                "http error 401",
                "login required",
                "private video",
                "requires authentication",
                "no es público",
                "autenticación",
            ) -> AnalysisFailureKind.ACCESS
            causes.any { it is UnknownHostException || it is ConnectException || it is InterruptedIOException } ||
                containsAny("timed out", "timeout", "connection", "unable to resolve host", "network is unreachable") ->
                AnalysisFailureKind.NETWORK
            containsAny(
                "unable to extract webpage video data",
                "unexpected response from webpage",
                "secondary user id",
                "failed to parse json",
                "tiktok cambió la respuesta",
            ) -> AnalysisFailureKind.EXTRACTOR_RESPONSE
            else -> AnalysisFailureKind.UNKNOWN
        }
    }
}
