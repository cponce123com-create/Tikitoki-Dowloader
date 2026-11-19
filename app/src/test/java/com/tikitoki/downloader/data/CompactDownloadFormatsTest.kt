package com.tikitoki.downloader.data

import com.tikitoki.downloader.domain.DownloadFormatPreference
import com.tikitoki.downloader.domain.PlaybackKind
import com.yausername.youtubedl_android.mapper.VideoFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompactDownloadFormatsTest {
    @Test
    fun groupsRealQualitiesAndOmitsUnrequestedHeights() {
        val formats =
            listOf(
                video("small", 426, 240, "mp4"),
                video("duplicate", 1280, 720, "webm"),
                video("preferred", 1280, 720, "mp4"),
                video("vertical", 1080, 1920, "mp4"),
                video("hidden", 2560, 1440, "mp4"),
                audio("aac", 180),
            )
        val tracks = CompactDownloadFormats.videoTracks(formats)
        assertEquals(listOf(240, 720, 1080), tracks.keys.toList())
        assertEquals("preferred", tracks[720]?.formatId)
        assertEquals("vertical", tracks[1080]?.formatId)
        assertTrue(CompactDownloadFormats.videoTracks(formats.take(5)).isEmpty())
    }

    @Test
    fun offersHigherMp3PresetsOnlyWithMatchingSourceQuality() {
        assertTrue(CompactDownloadFormats.mp3Rates(listOf(audio("low", 130))).isEmpty())
        assertEquals(listOf(180), CompactDownloadFormats.mp3Rates(listOf(audio("mid", 192))))
        assertEquals(listOf(180, 240), CompactDownloadFormats.mp3Rates(listOf(audio("high", 360))))
        assertNull(CompactDownloadFormats.originalTrack(listOf(audio("middle", 240))))
        assertEquals("native", CompactDownloadFormats.originalTrack(listOf(audio("native", 360)))?.formatId)
        assertEquals(180, FormatOptions.mp3Bitrate("bajamp3-180"))
        assertEquals(240, FormatOptions.mp3Bitrate("bajamp3-240"))
        assertNull(FormatOptions.mp3Bitrate("bajamp3-360"))
        assertTrue(PlaybackKind.isAudio("bajamp3-240", null, null))
    }

    @Test
    fun remembersRealResolutionAndPrefersAvailableVideoOrAudio() {
        val hd = FormatChoice("hd-id+bestaudio", "HD (720p)", height = 720)
        val fhd = FormatChoice("fhd-id+bestaudio", "FHD (1080p)", height = 1080)
        val mp3 = FormatChoice("bajamp3-128", "MP3", isAudio = true)
        val options = listOf(hd, fhd, mp3)
        val saved = DownloadFormatPreference.fromChoice(hd.id, hd.label, hd.height)
        assertEquals("height:720", saved)
        assertEquals(hd, FormatOptions.preferred(options, saved, compact = true))
        assertEquals(fhd, FormatOptions.preferred(options, "video", compact = true))
        assertEquals(mp3, FormatOptions.preferred(options, "bajamp3-240", compact = true))
    }

    @Test
    fun recognizesAudioWithoutCodecNameWhenMetadataIdentifiesATrack() {
        assertTrue(format("ext" to "m4a").hasAudio())
        assertTrue(format("abr" to 180).hasAudio())
        assertFalse(format("acodec" to "none", "ext" to "mp4").hasAudio())
        assertFalse(format("ext" to "mp4").hasAudio())
    }

    private fun video(
        id: String,
        width: Int,
        height: Int,
        ext: String,
    ) = format(
        "formatId" to id,
        "width" to width,
        "height" to height,
        "ext" to ext,
        "acodec" to "none",
        "vcodec" to "h264",
    )

    private fun audio(
        id: String,
        rate: Int,
    ) = format(
        "formatId" to id,
        "ext" to "m4a",
        "acodec" to "aac",
        "vcodec" to "none",
        "abr" to rate,
    )

    private fun format(vararg values: Pair<String, Any>): VideoFormat =
        VideoFormat().apply {
            for ((name, value) in values) {
                javaClass.getDeclaredField(name).apply { isAccessible = true }.set(this, value)
            }
        }
}
