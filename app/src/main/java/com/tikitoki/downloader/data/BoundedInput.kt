package com.tikitoki.downloader.data

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Reads at most [limit] bytes without requiring InputStream.readNBytes (API 33). */
internal fun InputStream.readUpTo(limit: Int): ByteArray {
    require(limit >= 0)
    val output = ByteArrayOutputStream(minOf(limit, 8192))
    val buffer = ByteArray(minOf(limit, 8192).coerceAtLeast(1))
    var remaining = limit
    while (remaining > 0) {
        val read = read(buffer, 0, minOf(buffer.size, remaining))
        if (read < 0) break
        if (read == 0) {
            val byte = read()
            if (byte < 0) break
            output.write(byte)
            remaining--
        } else {
            output.write(buffer, 0, read)
            remaining -= read
        }
    }
    return output.toByteArray()
}
