package com.tikitoki.downloader.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CollectionsCatalogTest {
    @Test fun publicPlaylistsAreDeduplicatedAndVideoIdsIgnored() {
        val raw = """{"entries":[{"id":"PL123456789","title":"Salsa peruana"},{"id":"PL123456789","title":"Duplicada"},{"id":"abc12345678","title":"Video"}]}"""
        val lists = CollectionsCatalog.parse(raw)
        assertEquals(1, lists.size)
        assertEquals("Salsa peruana", lists.single().title)
        assertEquals("https://www.youtube.com/playlist?list=PL123456789", lists.single().url)
    }

    @Test fun emptyChannelHasNoPlaylists() {
        assertTrue(CollectionsCatalog.parse("""{"entries":[]}""").isEmpty())
    }

    @Test(expected = Exception::class) fun invalidResponseIsNotTreatedAsEmptyChannel() {
        CollectionsCatalog.parse("""{"error":"network"}""")
    }
}
