package com.tikitoki.downloader.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThumbnailMetadataTest {
    @Test
    fun prefersThePrimaryThumbnailWhenItUsesHttps() {
        val info =
            JSONObject(
                """
                {
                  "thumbnail":"https://i.ytimg.com/primary.jpg",
                  "thumbnails":[{"url":"https://i.ytimg.com/fallback.jpg"}]
                }
                """.trimIndent(),
            )

        assertEquals("https://i.ytimg.com/primary.jpg", thumbnailFromInfo(info))
    }

    @Test
    fun skipsEntriesWithoutHttpsAndReturnsTheLastValidCandidate() {
        val info =
            JSONObject(
                """
                {
                  "thumbnail":"http://example.test/low.jpg",
                  "thumbnails":[
                    {"url":"https://i.ytimg.com/low.jpg"},
                    {"url":"https://i.ytimg.com/high.jpg"},
                    null,
                    {"url":"http://example.test/high.jpg"}
                  ]
                }
                """.trimIndent(),
            )

        assertEquals("https://i.ytimg.com/high.jpg", thumbnailFromInfo(info))
        assertNull(thumbnailFromInfo(JSONObject("""{"thumbnail":"http://example.test/only.jpg"}""")))
    }
}
