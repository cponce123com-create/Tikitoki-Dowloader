package com.tikitoki.downloader.data

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/** Allow simultaneous extraction, but never update yt-dlp while it is in use. */
internal object ExtractorAccess {
    private val lock = ReentrantReadWriteLock(true)

    fun <T> useEngine(block: () -> T): T = lock.read(block)
    fun <T> updateEngine(block: () -> T): T = lock.write(block)

    /** Defer maintenance rather than queuing a writer ahead of new readers. */
    fun tryUpdateEngine(block: () -> Unit): Boolean {
        val write = lock.writeLock()
        if (!write.tryLock()) return false
        try { block(); return true } finally { write.unlock() }
    }
}

