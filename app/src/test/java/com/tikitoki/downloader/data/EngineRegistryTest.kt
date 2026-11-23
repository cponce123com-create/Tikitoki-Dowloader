package com.tikitoki.downloader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineRegistryTest {
    @Test
    fun listsOnlyInstalledEnginesForEachCapability() {
        assertEquals(listOf("yt-dlp", "tiktok-photos", "newpipe"), EngineRegistry.available(EngineCapability.EXTRACT).map { it.id })
        assertEquals(listOf("yt-dlp", "tiktok-photos", "ffmpeg"), EngineRegistry.available(EngineCapability.TRANSFER).map { it.id })
        assertEquals(listOf("ffmpeg"), EngineRegistry.available(EngineCapability.PROCESS).map { it.id })
    }

    @Test
    fun excludesTheSelectedEngineFromFallbacks() {
        assertEquals(listOf("tiktok-photos", "newpipe"), EngineRegistry.fallbackExtractors().map { it.id })
        assertEquals(listOf("yt-dlp", "tiktok-photos"), EngineRegistry.fallbackExtractors("newpipe").map { it.id })
        assertTrue(EngineRegistry.statusMessage().contains("NewPipe Extractor"))
    }
}
