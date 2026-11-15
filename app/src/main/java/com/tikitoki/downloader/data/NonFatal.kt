package com.tikitoki.downloader.data

import kotlinx.coroutines.CancellationException

/** Preserve cancellation, interruption and fatal VM failures across best-effort boundaries. */
internal fun rethrowIfFatal(error: Throwable) {
    when (error) {
        is CancellationException, is VirtualMachineError, is ThreadDeath -> throw error
        is InterruptedException -> { Thread.currentThread().interrupt(); throw error }
    }
}
