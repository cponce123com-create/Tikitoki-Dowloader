package com.tikitoki.downloader.domain

/** A failed, empty, or incomplete catalog must never remove a known video. */
object ProfileSnapshot {
    data class Result(val snapshot: Set<String>, val pending: Set<String>, val possiblyUnavailable: Set<String>)
    fun compare(previous: Set<String>, pending: Set<String>, current: Set<String>, complete: Boolean): Result {
        if (!complete || current.isEmpty()) return Result(previous, pending, emptySet())
        val missing = previous - current
        val confirmed = missing intersect pending
        return Result(current + (missing - confirmed), missing - confirmed, confirmed)
    }
}
