package com.tikitoki.downloader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadSpeedParserTest {
    @Test fun readsOnlyTheNumericRate() {
        assertEquals("2.10MiB/s", DownloadProgressParser.speed("[download] 20.1% of 30MiB at 2.10MiB/s ETA 00:10"))
        assertEquals("500KiB/s", DownloadProgressParser.speed("[download] 90% at 500KiB/s"))
        assertNull(DownloadProgressParser.speed("at https://private.example/secret"))
    }
}
