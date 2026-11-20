package com.tikitoki.downloader.ui

import com.tikitoki.downloader.data.VideoEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RockolaTrackJsonTest {
    @Test
    fun queueRoundTripPreservesOrderAndTrackMetadata() {
        val first =
            VideoEntry(
                id = "youtube:abcdefghijk",
                url = "https://www.youtube.com/watch?v=abcdefghijk",
                title = "Canción",
                thumbnail = "https://i.ytimg.com/vi/abcdefghijk/hqdefault.jpg",
                durationSeconds = 280,
                uploader = "Artista",
                viewCount = 300,
            )
        val second =
            VideoEntry(
                id = "youtube:klmnopqrstu",
                url = "https://www.youtube.com/watch?v=klmnopqrstu",
                title = "Otro video",
            )
        val queue = listOf(second, first)
        assertEquals(queue, RockolaTrackJson.decode(RockolaTrackJson.encode(queue).toString()))
    }

    @Test
    fun restoresLegacyHistoryAndIgnoresDuplicateOrDamagedEntries() {
        val previousHistory =
            """
            [
                {"id":"youtube:abcdefghijk","url":"https://www.youtube.com/watch?v=abcdefghijk",
                 "title":"Canción","duration":240,"thumbnail":"","uploader":"Canal","views":12},
                {"id":"youtube:abcdefghijk","url":"https://www.youtube.com/watch?v=abcdefghijk","title":"Duplicado"},
                {"id":"bad-record","title":"Sin enlace"}
            ]
            """.trimIndent()
        val track = RockolaTrackJson.decode(previousHistory).single()
        assertEquals("Canción", track.title)
        assertEquals("Canal", track.uploader)
        assertEquals(240, track.durationSeconds)
        assertEquals(12L, track.viewCount)
        assertTrue(RockolaTrackJson.decode("{").isEmpty())
    }
}
