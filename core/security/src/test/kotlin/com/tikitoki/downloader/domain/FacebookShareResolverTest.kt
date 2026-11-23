package com.tikitoki.downloader.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FacebookShareResolverTest {
    @Test
    fun followsShareRedirectToFacebookProfile() {
        val result = FacebookShareResolver.resolve("https://www.facebook.com/share/token/") {
            "https://www.facebook.com/example?fbclid=tracking"
        }
        assertEquals("https://www.facebook.com/example", result)
    }

    @Test
    fun rejectsExternalAndNonHttpsRedirects() {
        assertNull(FacebookShareResolver.resolve("https://www.facebook.com/share/token/") { "https://evil.example/" })
        assertNull(FacebookShareResolver.resolve("https://www.facebook.com/share/token/") { "http://www.facebook.com/example" })
    }

    @Test
    fun resolvesRelativeLocationsAndDetectsLoops() {
        assertEquals("https://www.facebook.com/example", FacebookShareResolver.resolve("https://www.facebook.com/share/token/") { "/example" })
        assertNull(FacebookShareResolver.resolve("https://www.facebook.com/share/token/") { "/share/token/" })
    }

    @Test
    fun doesNotRequestNonShareLinks() {
        assertEquals("https://www.facebook.com/example", FacebookShareResolver.resolve("https://www.facebook.com/example") { error("unexpected request") })
    }
}
