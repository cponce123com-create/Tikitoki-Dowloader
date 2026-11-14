package com.tikitoki.downloader.data

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Keeps the number of active transfers within the current network setting. */
internal suspend fun <T> drainAdaptiveQueue(
    limit: () -> Int,
    take: suspend () -> T?,
    process: suspend (T) -> Unit,
) = coroutineScope {
    val lock = Mutex()
    var active = 0
    var ended = false
    repeat(8) {
        launch {
            while (true) {
                var item: T? = null
                val done = lock.withLock {
                    if (ended) true
                    else if (active >= limit().coerceIn(0, 8)) false
                    else {
                        item = take()
                        if (item != null) { active++; false }
                        else if (active == 0) { ended = true; true }
                        else false
                    }
                }
                if (done) break
                val claimed = item
                if (claimed == null) { delay(300); continue }
                try { process(claimed) }
                finally { lock.withLock { active-- } }
            }
        }
    }
}
