package com.tikitoki.downloader.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VideoThumbnailResolverTest {
    @Test fun missingYoutubeThumbnailDoesNotRequireExtractor() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val resolver = VideoThumbnailResolver(context)
        val video = VideoEntry("youtube:ab--cdefghi", "https://www.youtube.com/watch?v=ab--cdefghi", "Audio", null)
        assertEquals("https://i.ytimg.com/vi/ab--cdefghi/hqdefault.jpg", resolver.resolve(video))
    }
}
