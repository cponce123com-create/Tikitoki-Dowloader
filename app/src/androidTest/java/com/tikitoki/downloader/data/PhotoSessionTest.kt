package com.tikitoki.downloader.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PhotoSessionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val legacy = File(context.noBackupFilesDir, PhotoSession.LEGACY_FILE)
    private val encrypted = File(context.noBackupFilesDir, "photo-session-v1.enc")
    private val sample = ".facebook.com\tTRUE\t/\tTRUE\t0\tc_user\tfixture\n" +
        ".facebook.com\tTRUE\t/\tTRUE\t0\txs\tsynthetic-session\n"

    @Before
    @After
    fun clearSession() {
        PhotoSession.delete(context)
    }

    @Test
    fun migratesLegacySessionAndDeletesStoredCredentials() {
        legacy.writeText(sample)

        assertEquals(FacebookCookieState.PRESENT, PhotoSession.facebookCookieState(context))
        assertFalse(legacy.exists())
        assertTrue(encrypted.isFile)
        assertFalse(encrypted.readBytes().toString(Charsets.UTF_8).contains("synthetic-session"))
        assertEquals(sample, PhotoSession.read(context))

        PhotoSession.delete(context)
        assertFalse(encrypted.exists())
        assertNull(PhotoSession.read(context))
    }

    @Test
    fun rejectsModifiedCiphertext() {
        legacy.writeText(sample)
        assertEquals(sample, PhotoSession.read(context))
        val bytes = encrypted.readBytes()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        encrypted.writeBytes(bytes)

        assertEquals(FacebookCookieState.INCOMPLETE, PhotoSession.facebookCookieState(context))
    }
}
