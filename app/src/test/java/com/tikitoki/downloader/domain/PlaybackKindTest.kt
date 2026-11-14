package com.tikitoki.downloader.domain

import org.junit.Assert.*
import org.junit.Test

class PlaybackKindTest {
    @Test fun recognizesAudioDownloads() {
        assertTrue(PlaybackKind.isAudio("bestaudio/best", "video/webm", "45"))
        assertTrue(PlaybackKind.isAudio("best", "audio/mp4", "45"))
        assertTrue(PlaybackKind.isAudio(null, null, "mi canción.MP3"))
        assertTrue(PlaybackKind.isAudio(null, null, "track.opus"))
    }

    @Test fun keepsVideosInVideoPlayer() {
        assertFalse(PlaybackKind.isAudio("bestvideo+bestaudio/best", "video/mp4", "video.mp4"))
        assertFalse(PlaybackKind.isAudio(null, null, "unknown"))
    }
}
