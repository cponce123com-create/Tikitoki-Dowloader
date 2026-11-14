package com.tikitoki.downloader.data

/** Count the extractor's raw entries separately from entries we can display. */
data class ScanPage(val items: List<VideoEntry>, val rawCount: Int, val incomplete: Boolean, val profileImage: String? = null)

internal enum class ScanPageState { CONTINUE, END, PARTIAL }

internal fun scanPageState(page: ScanPage, pageSize: Int, freshCount: Int): ScanPageState = when {
    page.incomplete -> ScanPageState.PARTIAL
    page.rawCount == 0 -> ScanPageState.END
    freshCount == 0 -> ScanPageState.PARTIAL // repeated pages must not look like a clean ending
    page.rawCount < pageSize -> ScanPageState.END
    else -> ScanPageState.CONTINUE
}
