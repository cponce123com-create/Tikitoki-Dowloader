package com.tikitoki.downloader.data

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveQueueTest {
    @Test fun changingLimitDoesNotStartExtraTransfers() = runBlocking {
        val queue = ArrayDeque(listOf(1, 2, 3, 4))
        val started = Channel<Int>(Channel.UNLIMITED)
        val release = Channel<Unit>(Channel.UNLIMITED)
        var limit = 2
        val job = launch {
            drainAdaptiveQueue(limit = { limit }, take = { if (queue.isEmpty()) null else queue.removeFirst() }, process = {
                started.send(it)
                release.receive()
            })
        }
        try {
            withTimeout(3000) { started.receive(); started.receive() }
            delay(500)
            assertTrue(started.tryReceive().isFailure)
            limit = 1
            release.send(Unit)
            delay(500)
            assertTrue(started.tryReceive().isFailure)
            release.send(Unit)
            withTimeout(3000) { started.receive() }
            delay(500)
            assertTrue(started.tryReceive().isFailure)
            release.send(Unit)
            withTimeout(3000) { started.receive() }
            release.send(Unit)
            withTimeout(3000) { job.join() }
            assertEquals(0, queue.size)
        } finally { job.cancel(); release.close(); started.close() }
    }

    @Test fun supportsEightSimultaneousTransfers() = runBlocking {
        val queue = ArrayDeque((1..8).toList())
        val started = Channel<Int>(Channel.UNLIMITED)
        val release = Channel<Unit>(Channel.UNLIMITED)
        val job = launch {
            drainAdaptiveQueue(limit = { 8 }, take = { if (queue.isEmpty()) null else queue.removeFirst() }, process = {
                started.send(it); release.receive()
            })
        }
        try {
            withTimeout(3000) { repeat(8) { started.receive() } }
            repeat(8) { release.send(Unit) }
            withTimeout(3000) { job.join() }
        } finally { job.cancel(); release.close(); started.close() }
    }

    @Test fun blockedNetworkDoesNotClaimWorkUntilAllowed() = runBlocking {
        var limit = 0
        var pending = true
        val started = Channel<Unit>(Channel.UNLIMITED)
        val job = launch {
            drainAdaptiveQueue(limit = { limit }, take = {
                if (pending) { pending = false; 1 } else null
            }, process = { started.send(Unit) })
        }
        try {
            delay(500)
            assertTrue(started.tryReceive().isFailure)
            assertTrue(pending)
            limit = 1
            withTimeout(3000) { started.receive(); job.join() }
        } finally { job.cancel(); started.close() }
    }
}
