package com.tikitoki.downloader.data

import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class BoundedInputTest {
    @Test fun readsShortAndExactInputs() {
        val bytes = byteArrayOf(1, 2, 3)
        assertArrayEquals(bytes, ByteArrayInputStream(bytes).readUpTo(10))
        assertArrayEquals(bytes, ByteArrayInputStream(bytes).readUpTo(3))
        assertEquals(0, ByteArrayInputStream(bytes).readUpTo(0).size)
    }

    @Test fun capsLongInputsWithoutConsumingTheRemainder() {
        val stream = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4))
        assertArrayEquals(byteArrayOf(1, 2, 3), stream.readUpTo(3))
        assertEquals(4, stream.read())
    }

    @Test fun toleratesPartialAndZeroLengthReads() {
        val stream = object : InputStream() {
            var next = 0
            var returnedZero = false
            override fun read(): Int = if (next < 3) ++next else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (!returnedZero) { returnedZero = true; return 0 }
                val value = read()
                if (value < 0) return -1
                b[off] = value.toByte()
                return 1
            }
        }
        assertArrayEquals(byteArrayOf(1, 2, 3), stream.readUpTo(4))
    }
}
