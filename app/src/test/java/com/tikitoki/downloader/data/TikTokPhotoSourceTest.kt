package com.tikitoki.downloader.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TikTokPhotoSourceTest {
    @Test
    fun extractsEveryImageFromTheEmbeddedPhotoPost() {
        val html =
            pageHtml(
                photoPost(
                    images =
                        listOf(
                            "https://p16-sign-sg.tiktokcdn.com/obj/first.jpg",
                            "https://p16-sign-sg.tiktokcdn.com/obj/second.webp",
                        ),
                ),
            )

        val entries = TikTokPhotoSource.parseTikTokPhotoGallery(html, PHOTO_URL)

        assertEquals(2, entries.size)
        assertEquals("tiktok-photo-$POST_ID-1", entries[0].id)
        assertEquals("https://p16-sign-sg.tiktokcdn.com/obj/first.jpg", entries[0].thumbnail)
        assertEquals("tiktok-photo-$POST_ID-2", entries[1].id)
        assertTrue(entries[1].title.contains("foto 02"))
        assertEquals(PHOTO_URL, entries[0].url)
    }

    @Test
    fun skipsUntrustedImageUrlsAndRejectsWhenNoTrustedImagesRemain() {
        val html =
            pageHtml(
                photoPost(images = listOf("https://tiktokcdn.com.evil.example/file.jpg", "http://p16.tiktokcdn.com/file.jpg")),
            )

        assertTrue(runCatching { TikTokPhotoSource.parseTikTokPhotoGallery(html, PHOTO_URL) }.isFailure)
    }

    @Test
    fun rejectsMalformedEmbeddedJsonAndNonPhotoPosts() {
        val malformed = "<script id=\"__UNIVERSAL_DATA_FOR_REHYDRATION__\">{</script>"
        assertTrue(runCatching { TikTokPhotoSource.parseTikTokPhotoGallery(malformed, PHOTO_URL) }.isFailure)

        val withoutImages = pageHtml(JSONObject().put("id", POST_ID).put("desc", "not a photo"))
        assertTrue(runCatching { TikTokPhotoSource.parseTikTokPhotoGallery(withoutImages, PHOTO_URL) }.isFailure)
    }

    @Test
    fun onlyAcceptsHttpsImageUrlsOnTikTokCdnHosts() {
        assertEquals(
            "https://p16.tiktokcdn.com/image.jpg",
            TikTokPhotoSource.canonicalImageUrl("https://p16.tiktokcdn.com/image.jpg"),
        )
        listOf(
            "http://p16.tiktokcdn.com/image.jpg",
            "https://tiktokcdn.com.evil.example/image.jpg",
            "https://user@p16.tiktokcdn.com/image.jpg",
            "https://example.com/image.jpg",
        ).forEach { url ->
            assertTrue("Expected rejection for $url", runCatching { TikTokPhotoSource.canonicalImageUrl(url) }.isFailure)
        }
    }

    @Test
    fun recognizesImageFileSignaturesAndRejectsUnknownFormats() {
        assertEquals(
            "jpg",
            TikTokPhotoSource.detectImageExtension(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00)),
        )
        assertEquals("png", TikTokPhotoSource.detectImageExtension(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)))
        assertEquals("webp", TikTokPhotoSource.detectImageExtension("RIFF0000WEBP".toByteArray(Charsets.US_ASCII)))
        assertFalse(runCatching { TikTokPhotoSource.detectImageExtension("notimage".toByteArray()) }.isSuccess)
    }

    @Test
    fun identifiesPhotoPageUrlsWithoutAcceptingRegularVideoUrls() {
        assertTrue(TikTokPhotoSource.isPhotoPageUrl(PHOTO_URL))
        assertFalse(TikTokPhotoSource.isPhotoPageUrl("https://www.tiktok.com/@creator/video/$POST_ID"))
    }

    @Test
    fun validatesPhotoQueueSelectionAndRejectsMismatchedFormat() {
        val entry =
            VideoEntry(
                id = "${TikTokPhotoSource.PHOTO_ENTRY_PREFIX}$POST_ID-1",
                url = PHOTO_URL,
                title = "Foto de prueba",
                thumbnail = "https://p16.tiktokcdn.com/image.jpg",
            )

        assertEquals(TikTokPhotoSource.PHOTO_FORMAT_ID, DownloadSelection(entry, TikTokPhotoSource.PHOTO_FORMAT_ID).validated().formatId)
        assertTrue(runCatching { DownloadSelection(entry, "bestvideo").validated() }.isFailure)
        assertTrue(
            runCatching {
                DownloadSelection(entry.copy(thumbnail = "https://example.com/image.jpg"), TikTokPhotoSource.PHOTO_FORMAT_ID).validated()
            }.isFailure,
        )
    }

    private fun pageHtml(post: JSONObject): String =
        """<html><script id="__UNIVERSAL_DATA_FOR_REHYDRATION__">${JSONObject().put("item", post)}</script></html>"""

    private fun photoPost(images: List<String>): JSONObject =
        JSONObject()
            .put("id", POST_ID)
            .put("desc", "Carrusel de prueba")
            .put("author", JSONObject().put("uniqueId", "creator"))
            .put(
                "imagePost",
                JSONObject().put(
                    "images",
                    JSONArray(
                        images.map { url ->
                            JSONObject().put("imageURL", JSONObject().put("urlList", JSONArray().put(url)))
                        },
                    ),
                ),
            )

    private companion object {
        const val POST_ID = "1234567890123456789"
        const val PHOTO_URL = "https://www.tiktok.com/@creator/photo/$POST_ID"
    }
}
