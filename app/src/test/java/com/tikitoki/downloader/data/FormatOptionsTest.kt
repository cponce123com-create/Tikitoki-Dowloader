package com.tikitoki.downloader.data

import com.tikitoki.downloader.domain.PlaybackKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatOptionsTest {
    @Test fun recognizesOnlySupportedMp3Presets() {
        assertEquals(192, FormatOptions.mp3Bitrate("bajamp3-192"))
        assertNull(FormatOptions.mp3Bitrate("bajamp3-999"))
        assertNull(FormatOptions.mp3Bitrate("bajamp3-192;rm"))
        assertTrue(PlaybackKind.isAudio("bajamp3-128", null, null))
        assertFalse(PlaybackKind.isAudio("bajamp3-999", null, null))
    }
}
