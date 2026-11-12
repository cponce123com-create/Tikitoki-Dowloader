package com.tikitoki.downloader.data

data class LibraryStats(
    val total: Int,
    val completed: Int,
    val failed: Int,
    val queued: Int,
    val paused: Int,
    val downloading: Int
)

object LibraryAnalytics {
    fun summarize(statuses: List<String>): LibraryStats = LibraryStats(
        total = statuses.size,
        completed = statuses.count { it == DownloadState.COMPLETED },
        failed = statuses.count { it == DownloadState.FAILED },
        queued = statuses.count { it == DownloadState.QUEUED },
        paused = statuses.count { it == DownloadState.PAUSED },
        downloading = statuses.count { it == DownloadState.DOWNLOADING }
    )
}
