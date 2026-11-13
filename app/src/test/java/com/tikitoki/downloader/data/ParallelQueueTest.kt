package com.tikitoki.downloader.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class ParallelQueueTest {
    @Test fun startsNextItemBeforeSlowSlotFinishes() = runBlocking {
        val queue = ArrayDeque(listOf(1, 2, 3))
        val releaseSlow = CompletableDeferred<Unit>()
        val thirdStarted = CompletableDeferred<Unit>()
        val finished = mutableListOf<Int>()
        val job = launch {
            drainParallelQueue(2, take = { if (queue.isEmpty()) null else queue.removeFirst() }, process = {
                if (it == 1) releaseSlow.await()
                if (it == 3) thirdStarted.complete(Unit)
                finished.add(it)
            })
        }
        try {
            withTimeout(3000) { thirdStarted.await() }
            releaseSlow.complete(Unit)
            withTimeout(3000) { job.join() }
            assertEquals(listOf(2, 3, 1), finished)
        } finally {
            releaseSlow.complete(Unit)
            job.cancel()
        }
    }
}
