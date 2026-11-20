package com.tikitoki.downloader.domain

/**
 * Test utilities for core:security module.
 * Provides sample URLs shared by validation tests.
 */
object TestUtils {
    const val TIKTOK_URL_VIDEO = "https://www.tiktok.com/@user/video/1234567890"
    const val TIKTOK_URL_PROFILE = "https://www.tiktok.com/@user"
    const val TIKTOK_URL_SHARE = "https://vm.tiktok.com/abc123/"

    const val YOUTUBE_URL_VIDEO = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
    const val YOUTUBE_URL_SHORT = "https://youtu.be/dQw4w9WgXcQ"

    const val INVALID_URL_NOT_HTTP = "not-a-url"
    const val INVALID_URL_EMPTY = ""
    const val INVALID_URL_FTP = "ftp://example.com/file"

    fun createTestMediaLink(
        url: String = TIKTOK_URL_VIDEO,
        platform: MediaPlatform = MediaPlatform.TIKTOK,
        title: String = "Test Video",
    ): MediaLink = MediaLink(
        url = url,
        platform = platform,
        title = title,
    )
}
