package com.tikitoki.downloader.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VideoThumbnailResolverTest {
    @Test fun extractsThumbnailFromPlaylistMetadata() {
        val json = org.json.JSONObject("""{"thumbnails":[{"url":"https://i.ytimg.com/vi/ab--cdefghi/default.jpg"},{"url":"https://i.ytimg.com/vi/ab--cdefghi/hqdefault.jpg"}]}""")
        assertEquals("https://i.ytimg.com/vi/ab--cdefghi/hqdefault.jpg", thumbnailFromInfo(json))
        assertNull(thumbnailFromInfo(org.json.JSONObject("""{"thumbnails":[{"url":"http://unsafe.test/image"}]}""")))
    }
    @Test fun missingYoutubeThumbnailDoesNotRequireExtractor() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val resolver = VideoThumbnailResolver(context)
        val video = VideoEntry("youtube:ab--cdefghi", "https://www.youtube.com/watch?v=ab--cdefghi", "Audio", null)
        assertEquals("https://i.ytimg.com/vi/ab--cdefghi/hqdefault.jpg", resolver.resolve(video))
    }

    @Test fun rejectsUntrustedVideoUrlBeforeQueryingExtractor() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val resolver = VideoThumbnailResolver(context)
            val video = VideoEntry("test", "https://untrusted.example/video", "Video", null)
            assertNull(resolver.resolve(video))
        }
    }
}
