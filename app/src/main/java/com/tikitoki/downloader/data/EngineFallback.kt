package com.tikitoki.downloader.data

import kotlinx.coroutines.CancellationException

/**
 * Executes a primary extractor, then registered *installed* fallback extractors.
 * No fallback is attempted for cancellation, authentication or access denial.
 * Register adapters only after they are packaged and verified on Android.
 */
class EngineFallback<T>(
    private val primary: suspend () -> T,
    private val alternatives: List<Pair<String, suspend () -> T>>,
) {
    suspend fun execute(): T {
        try {
            return primary()
        } catch (e: CancellationException) {
            throw e
        } catch (first: Exception) {
            if (isAccessDenied(first)) throw first
            val failures = mutableListOf(first)
            for ((_, fallback) in alternatives) {
                try {
                    return fallback()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failures.add(e)
                    if (isAccessDenied(e)) throw e
                }
            }
            throw first.apply { failures.drop(1).forEach(::addSuppressed) }
        }
    }

    companion object {
        fun isAccessDenied(error: Throwable): Boolean {
            val detail = generateSequence(error) { it.cause }
                .take(4).mapNotNull { it.message }.joinToString(" ").lowercase()
            return listOf("login required", "login to", "subscription required",
                "private video", "not authorized", "unauthorized", "forbidden",
                "http error 401", "http error 403", "drm").any { it in detail }
        }
    }
}
