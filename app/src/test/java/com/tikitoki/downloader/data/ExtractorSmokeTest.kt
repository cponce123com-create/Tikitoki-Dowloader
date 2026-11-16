package com.tikitoki.downloader.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractorSmokeTest {
    @Test fun acceptsSuccessfulListingContainingYoutube() {
        assertTrue(extractorSmokePassed(0, "generic\nyoutube\nvimeo\n"))
    }

    @Test fun rejectsFailedOrUnrelatedListings() {
        assertFalse(extractorSmokePassed(1, "youtube\n"))
        assertFalse(extractorSmokePassed(0, "vimeo\n"))
        assertFalse(extractorSmokePassed(0, ""))
    }
}
