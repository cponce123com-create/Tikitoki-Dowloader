package com.tikitoki.downloader.domain

import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest

/**
 * Test utilities for core:security module.
 * Provides common test helpers for validation logic testing.
 */
object TestUtils {

    /**
     * Runs a coroutine test with virtual time.
     * Usage: `test { ... }`
     */
    @JvmStatic
    fun test(body: suspend () -> Unit) = runTest { body() }

    /**
     * Creates a mock for interface/class with default relaxed behavior.
     */
    @JvmStatic
    inline fun <reified T : Any> mock(): T = mockk(relaxed = true)

    /**
     * Asserts that a flow emits expected values using Turbine.
     * Usage: `flow.test { awaitItem().shouldEqual(expected) }`
     */
    @JvmStatic
    fun <T> kotlinx.coroutines.flow.Flow<T>.test(
        body: app.cash.turbine.TestScope<T>.() -> Unit
    ) = this.test(body)

    /**
     * Sample valid TikTok URLs for testing.
     */
    const val TIKTOK_URL_VIDEO = "https://www.tiktok.com/@user/video/1234567890"
    const val TIKTOK_URL_PROFILE = "https://www.tiktok.com/@user"
    const val TIKTOK_URL_SHARE = "https://vm.tiktok.com/abc123/"

    /**
     * Sample valid YouTube URLs for testing.
     */
    const val YOUTUBE_URL_VIDEO = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
    const val YOUTUBE_URL_SHORT = "https://youtu.be/dQw4w9WgXcQ"

    /**
     * Sample invalid URLs for testing.
     */
    const val INVALID_URL_NOT_HTTP = "not-a-url"
    const val INVALID_URL_EMPTY = ""
    const val INVALID_URL_FTP = "ftp://example.com/file"

    /**
     * Creates a valid MediaLink for testing.
     */
    fun createTestMediaLink(
        url: String = TIKTOK_URL_VIDEO,
        platform: MediaPlatform = MediaPlatform.TIKTOK,
        title: String = "Test Video",
    ): MediaLink {
        return MediaLink(
            url = url,
            platform = platform,
            title = title,
        )
    }
}

/**
 * Turbine test extension for flows.
 */
@Suppress("UNUSED_PARAMETER")
inline fun <T> kotlinx.coroutines.flow.Flow<T>.test(
    crossinline body: app.cash.turbine.TestScope<T>.() -> Unit
) = app.cash.turbine.test(body)