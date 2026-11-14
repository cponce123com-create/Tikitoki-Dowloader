package com.tikitoki.downloader.domain

import org.junit.Assert.*
import org.junit.Test

class VideoIdMigrationPlanTest {
    @Test fun canonicalizesLegacyAndExtractorIds() {
        assertEquals("tiktok:12345", VideoIdMigrationPlan.canonicalId("12345", "https://www.tiktok.com/@user/video/12345"))
        assertEquals("youtube:abc", VideoIdMigrationPlan.canonicalId("newpipe:abc", "https://youtube.com/watch?v=abc"))
        assertEquals("youtube:abc", VideoIdMigrationPlan.canonicalId("youtube:abc", "https://youtube.com/watch?v=abc"))
        assertNull(VideoIdMigrationPlan.canonicalId("facebook:abc", "https://youtube.com/watch?v=abc"))
        assertNull(VideoIdMigrationPlan.canonicalId("123", "https://unknown.example/video"))
    }

    @Test fun identifiesCollisionsInsteadOfLosingRecords() {
        val plan = VideoIdMigrationPlan.build(listOf(
            VideoIdMigrationPlan.OldRecord("newpipe:abc", "https://youtube.com/watch?v=abc"),
            VideoIdMigrationPlan.OldRecord("youtube:abc", "https://youtube.com/watch?v=abc"),
            VideoIdMigrationPlan.OldRecord("unknown", "https://unknown.example/"),
        ))
        assertEquals(setOf("newpipe:abc", "youtube:abc"), plan.collisions["youtube:abc"]?.toSet())
        assertEquals(listOf("unknown"), plan.unresolved)
    }
}
