package com.tikitoki.downloader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadProgressParserTest {
    @Test fun readsDownloadProgressWithOrWithoutEta() {
        assertEquals(42, DownloadProgressParser.percent("[download]  42.7% of 10MiB at 1MiB/s ETA 00:06"))
        assertEquals(100, DownloadProgressParser.percent("[download] 100% of 10MiB in 00:10"))
        assertEquals(5, DownloadProgressParser.percent("[download]   5.1% of ~30MiB (frag 5/100)"))
        assertEquals(37, DownloadProgressParser.percent("[#abc123 2.0MiB/5.4MiB(37%) CN:4 DL:1MiB]"))
    }

    @Test fun ignoresMetadataAndPostProcessing() {
        assertNull(DownloadProgressParser.percent("[Merger] Merging formats into file"))
        assertNull(DownloadProgressParser.percent("[youtube] Extracting URL"))
    }
}
