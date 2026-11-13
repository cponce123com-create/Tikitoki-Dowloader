package com.tikitoki.downloader.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ScanPageTest {
    private fun page(raw: Int, incomplete: Boolean) = ScanPage(emptyList(), raw, incomplete)

    @Test fun incompleteOrRepeatedPageIsNotMarkedComplete() {
        assertEquals(ScanPageState.PARTIAL, scanPageState(page(50, true), 100, 49))
        assertEquals(ScanPageState.PARTIAL, scanPageState(page(100, false), 100, 0))
        assertEquals(ScanPageState.PARTIAL, scanPageState(page(0, true), 100, 0))
    }

    @Test fun cleanPagesContinueOrEndUsingRawCount() {
        assertEquals(ScanPageState.CONTINUE, scanPageState(page(100, false), 100, 100))
        assertEquals(ScanPageState.END, scanPageState(page(25, false), 100, 25))
        assertEquals(ScanPageState.END, scanPageState(page(0, false), 100, 0))
    }
}
