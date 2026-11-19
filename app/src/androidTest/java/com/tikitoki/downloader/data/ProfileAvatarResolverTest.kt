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

    @Test fun findsAvatarAfterNameOnlyRecord() {
        val html = """<script id="SIGI_STATE">{"a":{"uniqueId":"carlos","nickname":"Carlos"},"b":{"uniqueId":"carlos","avatarThumb":"https://p16.tiktokcdn.com/avatar.jpg"}}</script>"""
        val result = ProfileAvatarResolver().findIdentity(html, "carlos")
        assertEquals("Carlos", result?.displayName)
        assertEquals("https://p16.tiktokcdn.com/avatar.jpg", result?.avatar)
    }

    @Test fun resolvesOnlyAValidatedPublicProfileId() {
        val id = "MS4wLjABAAAA" + "A".repeat(64)
        val html =
            """
            <script id="__UNIVERSAL_DATA_FOR_REHYDRATION__">
            {"__DEFAULT_SCOPE__":{"webapp.user-detail":{"statusCode":0,"userInfo":{"user":
              {"uniqueId":"carlos","secUid":"$id","privateAccount":false}
            }}}}
            </script>
            """.trimIndent()
        val resolver = ProfileAvatarResolver()
        assertEquals(id, resolver.findIdentity(html, "Carlos")?.extractorProfileId)
        assertNull(resolver.findIdentity(html, "otro")?.extractorProfileId)
        assertNull(resolver.findIdentity(html.replace("false", "true"), "carlos")?.extractorProfileId)
        assertNull(resolver.findIdentity(html.replace("false", "null"), "carlos")?.extractorProfileId)
        assertNull(resolver.findIdentity(html.replace("statusCode\":0", "statusCode\":10222"), "carlos")?.extractorProfileId)
        assertNull(resolver.findIdentity(html.replace(id, "--config-locations"), "carlos")?.extractorProfileId)
    }
}
