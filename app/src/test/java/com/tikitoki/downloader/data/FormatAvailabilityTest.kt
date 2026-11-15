package com.tikitoki.downloader.data

import org.junit.Assert.*
import org.junit.Test

class FormatAvailabilityTest {
    @Test fun checksEachRequestedMaximumAndAudio() {
        val options = listOf(FormatChoice("video", "", height = 1080), FormatChoice("audio", "", isAudio = true))
        assertFalse(FormatAvailability.matches("bestvideo[height<=720]+bestaudio/best[height<=720]", options))
        assertTrue(FormatAvailability.matches("bestvideo[height<=1080]+bestaudio/best[height<=1080]", options))
        assertTrue(FormatAvailability.matches("bajamp3-128", options))
        assertFalse(FormatAvailability.matches("bestaudio", options))
        assertTrue(FormatAvailability.matches("bestaudio", listOf(FormatChoice("bajaaudio-140", "", isAudio = true))))
        assertFalse(FormatAvailability.matches("bajamp3-128", listOf(FormatChoice("video", "", height = 720))))
        assertFalse(FormatAvailability.matches("best", emptyList()))
    }
}
