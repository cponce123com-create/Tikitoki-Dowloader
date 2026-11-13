package com.tikitoki.downloader.data

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Refill each slot as soon as its item completes. */
internal suspend fun <T> drainParallelQueue(
    parallelism: Int,
    take: suspend () -> T?,
    process: suspend (T) -> Unit,
) = coroutineScope {
    repeat(parallelism.coerceIn(1, 4)) {
        launch {
            while (true) {
                val item = take() ?: break
                process(item)
            }
        }
    }
}
