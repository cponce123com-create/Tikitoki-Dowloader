package com.tikitoki.downloader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineFallbackTest {
    @Test fun usesPrimaryWhenSuccessful() = kotlinx.coroutines.runBlocking {
        var called = false
        val value = EngineFallback(
            primary = { "primary" },
            alternatives = listOf("backup" to suspend { called = true; "backup" })
        ).execute()
        assertEquals("primary", value)
        assertTrue(!called)
    }

    @Test fun usesFallbackWhenPrimaryFails() = kotlinx.coroutines.runBlocking {
        val value = EngineFallback(
            primary = { throw IllegalStateException("Extractor unavailable") },
            alternatives = listOf("backup" to suspend { "backup" })
        ).execute()
        assertEquals("backup", value)
    }

    @Test fun doesNotBypassAccessRestrictions() = kotlinx.coroutines.runBlocking {
        var called = false
        try {
            EngineFallback(
                primary = { throw IllegalStateException("HTTP Error 403") },
                alternatives = listOf("backup" to suspend { called = true; "backup" })
            ).execute()
            throw AssertionError("Expected denial")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("403"))
            assertTrue(!called)
        }
    }
}
