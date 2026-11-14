package com.tikitoki.downloader.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileAvatarResolverTest {
    @Test fun onlyUsesAvatarForTheRequestedAccountOnAllowedCdn() {
        val html = """<script id="SIGI_STATE" type="application/json">{"users":{"a":{"uniqueId":"carlos","nickname":"Carlos Ponce","avatarLarger":"https://p16.tiktokcdn.com/avatar.jpg"},"b":{"uniqueId":"otro","avatarLarger":"https://p16.tiktokcdn.com/other.jpg"}}}</script>"""
        val resolver = ProfileAvatarResolver()
        assertEquals("https://p16.tiktokcdn.com/avatar.jpg", resolver.findAvatar(html, "Carlos"))
        assertEquals("Carlos Ponce", resolver.findIdentity(html, "carlos")?.displayName)
        assertNull(resolver.findAvatar(html, "desconocido"))
        val unsafe = html.replace("p16.tiktokcdn.com", "example.com")
        assertNull(resolver.findAvatar(unsafe, "carlos"))
    }
}
