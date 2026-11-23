package com.tikitoki.downloader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoSessionTest {
    @Test
    fun importsOnlySupportedSocialDomainsAndRejectsLookalikes() {
        val input =
            listOf(
                "# Netscape HTTP Cookie File",
                "#HttpOnly_.facebook.com\tTRUE\t/\tTRUE\t0\tc_user\t123",
                ".instagram.com\tTRUE\t/\tTRUE\t0\tsessionid\t456",
                ".facebook.com.evil.example\tTRUE\t/\tTRUE\t0\tbad\tvalue",
                ".example.org\tTRUE\t/\tTRUE\t0\tbad\tvalue",
                ".facebook.com\tTRUE\t/\tTRUE\tnan\tbad\tvalue",
                ".facebook.com\tTRUE\t/\tTRUE\t0\t\tvalue",
                "invalid",
            ).joinToString("\n")
        assertEquals(2, PhotoSession.sanitize(input).size)
    }
    @Test
    fun detectsFacebookSessionOnlyWithBothAuthenticationCookies() {
        val cUser = "#HttpOnly_.facebook.com\tTRUE\t/\tTRUE\t0\tc_user\t123"
        val xs = "#HttpOnly_.facebook.com\tTRUE\t/\tTRUE\t0\txs\tsession"
        val instagram = ".instagram.com\tTRUE\t/\tTRUE\t0\tsessionid\t456"
        val lookalike = ".facebook.com.evil.example\tTRUE\t/\tTRUE\t0\txs\tbad"
        assertFalse(PhotoSession.hasFacebookLoginCookies(cUser))
        assertFalse(PhotoSession.hasFacebookLoginCookies("$cUser\\n$instagram"))
        assertFalse(PhotoSession.hasFacebookLoginCookies("$cUser\\n$lookalike"))
        assertTrue(PhotoSession.hasFacebookLoginCookies("$cUser\\n$xs"))
    }

}
