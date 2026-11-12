package com.tikitoki.downloader.domain

/** Comparison of public video IDs, preserving prior observations after partial scans. */
object ProfileDiff {
    data class Result(val newIds: Set<String>, val knownIds: Set<String>)

    fun compare(previous: Set<String>, current: Set<String>): Result {
        if (current.isEmpty()) return Result(emptySet(), previous)
        return Result(
            newIds = if (previous.isEmpty()) emptySet() else current - previous,
            knownIds = previous + current
        )
    }
}
