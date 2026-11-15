package com.tikitoki.downloader.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThumbnailUrlsTest {
    @Test fun derivesYoutubeThumbnailsForCurrentAndLegacyIds() {
        val url = "https://www.youtube.com/watch?v=ab--cdefghi"
        val expected = "https://i.ytimg.com/vi/ab--cdefghi/hqdefault.jpg"
        assertEquals(expected, ThumbnailUrls.youtube("youtube:ab--cdefghi", url))
        assertEquals(expected, ThumbnailUrls.youtube("newpipe:ab--cdefghi", url))
        assertEquals(expected, ThumbnailUrls.youtube("ab--cdefghi", url))
    }

    @Test fun rejectsInvalidIdsAndNonYoutubeHosts() {
        assertNull(ThumbnailUrls.youtube("youtube:../unsafe", "https://youtube.com/watch?v=valid"))
        assertNull(ThumbnailUrls.youtube("youtube:ab--cdefghi", "https://youtube.com.evil.test/watch?v=ab--cdefghi"))
        assertNull(ThumbnailUrls.youtube("youtube:ab--cdefghi", "https://www.tiktok.com/@user/video/123"))
        assertNull(ThumbnailUrls.youtube("youtube:ab--cdefghi", "http://youtube.com/watch?v=ab--cdefghi"))
    }
}
