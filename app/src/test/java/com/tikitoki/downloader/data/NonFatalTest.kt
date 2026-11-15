package com.tikitoki.downloader.data

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class NonFatalTest {
    @Test fun allowsNonfatalLinkageFailure() { rethrowIfFatal(LinkageError("test")) }
    @Test fun preservesCancellation() {
        val original = CancellationException("test")
        try { rethrowIfFatal(original); fail() } catch (actual: CancellationException) { assertSame(original, actual) }
    }
    @Test fun preservesFatalVmFailure() {
        val original = object : VirtualMachineError("test") {}
        try { rethrowIfFatal(original); fail() } catch (actual: VirtualMachineError) { assertSame(original, actual) }
    }
}
