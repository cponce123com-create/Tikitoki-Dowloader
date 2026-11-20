package com.tikitoki.downloader.data

import com.tikitoki.downloader.R
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionsCatalogUnitTest {
    @Test
    fun parsesPublicPlaylistsAndKeepsTheFirstDuplicate() {
        val raw =
            """
            {"entries":[
                null,
                {"id":"PL123456789","title":"  Salsa peruana  "},
                {"id":"PL123456789","title":"Duplicada"},
                {"id":"abc12345678","title":"Video"},
                {"id":"PL987654321","title":""}
            ]}
            """.trimIndent()

        assertEquals(
            listOf(CatalogPlaylist("Salsa peruana", "https://www.youtube.com/playlist?list=PL123456789")),
            CollectionsCatalog.parse(raw),
        )
        assertTrue(CollectionsCatalog.parse("""{"entries":[]}""").isEmpty())
    }

    @Test(expected = JSONException::class)
    fun rejectsResponsesWithoutPlaylistEntries() {
        CollectionsCatalog.parse("""{"error":"network"}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsChannelsThatExceedTheEntryLimit() {
        val entries = JSONArray()
        repeat(CollectionsCatalog.MAX_PLAYLISTS + 1) { entries.put(JSONObject.NULL) }
        CollectionsCatalog.parse(JSONObject().put("entries", entries).toString())
    }

    @Test
    fun groupsTitlesWithAccentsAndPreservesCatalogStatus() {
        val music = CatalogPlaylist("MÚSICA y Salsa", "https://www.youtube.com/playlist?list=PL111111111")
        val christian = CatalogPlaylist("Evangelio", "https://www.youtube.com/playlist?list=PL222222222")
        val reflection = CatalogPlaylist("Reflexión y amistad", "https://www.youtube.com/playlist?list=PL333333333")
        val news = CatalogPlaylist("Noticias de hoy", "https://www.youtube.com/playlist?list=PL444444444")
        val playlists = listOf(music, christian, reflection, news)

        val result = CollectionsCatalog.classify(playlists, cached = true, capped = false)

        assertEquals(
            mapOf(
                R.string.collections_all to playlists,
                R.string.collections_music to listOf(music),
                R.string.collections_christian to listOf(christian),
                R.string.collections_reflection to listOf(reflection),
                R.string.collections_news to listOf(news),
            ),
            result.sections.associate { it.nameRes to it.playlists },
        )
        assertTrue(result.cached)
        assertFalse(result.capped)
    }
}
