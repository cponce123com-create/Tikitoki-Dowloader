package com.tikitoki.downloader.data

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/** Allow simultaneous extraction, but never update yt-dlp while it is in use. */
internal object ExtractorAccess {
    private val lock = ReentrantReadWriteLock(true)
    @Volatile private var recoveryReady = true
    fun setRecoveryReady(ready: Boolean) { recoveryReady = ready }
    private fun requireRecovery() { check(recoveryReady) { "Extractor recovery required" } }

    fun <T> useEngine(block: () -> T): T = lock.read { requireRecovery(); block() }
    fun <T> updateEngine(block: () -> T): T = lock.write { requireRecovery(); block() }

    /** Defer maintenance rather than queuing a writer ahead of new readers. */
    fun tryUpdateEngine(block: () -> Unit): Boolean {
        val write = lock.writeLock()
        if (!write.tryLock()) return false
        try { requireRecovery(); block(); return true } finally { write.unlock() }
    }
}


