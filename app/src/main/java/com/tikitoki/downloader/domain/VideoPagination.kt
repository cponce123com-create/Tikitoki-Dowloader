package com.tikitoki.downloader.domain

/** UI pagination over an already extracted catalogue. Does not change yt-dlp network requests. */
object VideoPagination {
    const val PAGE_SIZE = 60

    fun visibleCount(total: Int, pages: Int): Int =
        minOf(total.coerceAtLeast(0), pages.coerceAtLeast(1).toLong().times(PAGE_SIZE).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())

    fun totalPages(total: Int): Int =
        if (total <= 0) 0 else 1 + (total - 1) / PAGE_SIZE
}
