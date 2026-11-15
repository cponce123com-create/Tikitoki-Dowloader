package com.tikitoki.downloader.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadIdentityTest {
    @Test fun audioAndVideoForTheSameSourceStayDistinct() {
        val original = "youtube:abc123"
        val audio = DownloadIdentity.key(original, true)
        val video = DownloadIdentity.key(original, false)
        assertNotEquals(audio, video)
        assertEquals(original, DownloadIdentity.sourceId(audio))
        assertEquals(original, DownloadIdentity.sourceId(video))
        assertEquals(original, DownloadIdentity.sourceId(original))
        assertTrue(PlaybackKind.isAudio("bestaudio/best", null, null))
    }
}
