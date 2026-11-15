package com.tikitoki.downloader.data

import org.junit.Assert.*
import org.junit.Test

class MonitorPolicyTest {
    @Test fun migratesIntervalsAndLimitsPages() {
        assertEquals(6, MonitorPolicy.interval(1))
        assertEquals(6, MonitorPolicy.interval(5))
        assertEquals(12, MonitorPolicy.interval(12))
        assertEquals(24, MonitorPolicy.interval(24))
        assertEquals(1, MonitorPolicy.pageLimit(-2))
        assertEquals(100, MonitorPolicy.pageLimit(1000))
    }
    @Test fun enforcesWeeklyInterval() {
        assertTrue(MonitorPolicy.fullDue(MonitorPolicy.WEEK_MS, 0))
        assertFalse(MonitorPolicy.fullDue(MonitorPolicy.WEEK_MS, 1))
        assertTrue(MonitorPolicy.fullDue(MonitorPolicy.WEEK_MS + 1, 1))
        assertFalse(MonitorPolicy.fullDue(1, 2))
    }
    @Test fun recognizesRateLimitAcrossCauses() {
        assertTrue(MonitorPolicy.rateLimited(IllegalStateException("failed", RuntimeException("HTTP 429"))))
        assertFalse(MonitorPolicy.rateLimited(IllegalStateException("HTTP 4290")))
    }
}
