package com.tikitoki.downloader.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DestinationNamingTest {
    @Test fun accountComesFromCanonicalTikTokVideo() {
        assertEquals("@carlos.123", DestinationNaming.account("https://www.tiktok.com/@carlos.123/video/123456"))
        assertEquals("Sin cuenta", DestinationNaming.account("https://youtu.be/abc"))
    }
    @Test fun accountUsesOnlyChannelInformationPresentInUrl() {
        assertEquals("@autor", DestinationNaming.account("https://youtube.com/@autor/videos"))
        assertEquals("UC123", DestinationNaming.account("https://youtube.com/channel/UC123"))
        assertEquals("pagina", DestinationNaming.account("https://facebook.com/pagina/videos/123"))
        assertEquals("creador", DestinationNaming.account("https://instagram.com/creador/"))
        assertEquals("Sin cuenta", DestinationNaming.account("https://youtube.com/watch?v=abc"))
        assertEquals("Sin cuenta", DestinationNaming.account("https://facebook.com/watch/?v=123"))
        assertEquals("Sin cuenta", DestinationNaming.account("https://facebook.com/videos/123"))
        assertEquals("Sin cuenta", DestinationNaming.account("https://instagram.com/reel/abc"))
    }

    @Test fun fileNameCannotIntroduceDirectoriesAndKeepsDistinctIds() {
        val first = DestinationNaming.filename("../../Fiesta: en casa", "tiktok:111111", "mp4")
        val second = DestinationNaming.filename("../../Fiesta: en casa", "tiktok:222222", "mp4")
        assertFalse(first.contains('/'))
        assertFalse(first.contains('\\'))
        assertFalse(first.startsWith('.'))
        assertNotEquals(first, second)
        assertEquals("Video - 42.mp4", DestinationNaming.filename("../", "42", "mp4"))
    }
}

