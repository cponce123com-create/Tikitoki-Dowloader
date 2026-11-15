package com.tikitoki.downloader.domain

import org.junit.Assert.*
import org.junit.Test

class DownloadFormatPreferenceTest {
    @Test fun resolutionIsReusableAcrossDifferentVideoIds() {
        val preference = DownloadFormatPreference.fromChoice("old-id+bestaudio/best", "720p · MP4")
        assertEquals("height:720", preference)
        assertTrue(DownloadFormatPreference.matches(preference, "new-id+bestaudio/best", "720p · WEBM", "default"))
        assertFalse(DownloadFormatPreference.matches(preference, "old-id", "1080p · MP4", "default"))
    }

    @Test fun audioAndVideoPreferencesMatchTheirOwnOptions() {
        assertEquals("audio", DownloadFormatPreference.fromChoice("bestaudio/best", "Solo audio"))
        assertTrue(DownloadFormatPreference.matches("audio", "bestaudio/best", "Solo audio", "default"))
        assertFalse(DownloadFormatPreference.matches("audio", "default", "Video", "default"))
        assertTrue(DownloadFormatPreference.matches("video", "default", "Video", "default"))
    }
}
