package com.tikitoki.downloader.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoPaginationTest {
    @Test fun largeProfile() {
        assertEquals(60, VideoPagination.visibleCount(1642, 1))
        assertEquals(120, VideoPagination.visibleCount(1642, 2))
        assertEquals(1642, VideoPagination.visibleCount(1642, 100))
        assertEquals(28, VideoPagination.totalPages(1642))
    }

    @Test fun emptyAndBoundary() {
        assertEquals(0, VideoPagination.visibleCount(0, 1))
        assertEquals(1, VideoPagination.totalPages(1))
        assertEquals(1, VideoPagination.totalPages(60))
        assertEquals(2, VideoPagination.totalPages(61))
        assertEquals(60, VideoPagination.visibleCount(120, 0))
    }
}
