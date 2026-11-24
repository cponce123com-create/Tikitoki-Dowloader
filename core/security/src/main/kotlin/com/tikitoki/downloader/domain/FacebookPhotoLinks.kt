package com.tikitoki.downloader.domain

import java.net.URI
import java.util.Locale

/**
 * Identifies where the optional gallery tool is offered and which links target photos explicitly.
 */
object FacebookPhotoLinks {
    fun isPhotoOnly(raw: String): Boolean {
        val link = MediaLinkParser.parse(raw) ?: return false
        if (link.platform != MediaPlatform.FACEBOOK) return false
        val uri = URI(link.url)
        val host = uri.host.lowercase(Locale.ROOT)
        if (host == "fb.watch" || host.endsWith(".fb.watch")) return false
        val path = uri.path.orEmpty().lowercase(Locale.ROOT)
        return path == "/photo.php" ||
            path == "/photo" ||
            path.startsWith("/photo/") ||
            path.split('/').any { it == "photos" || it == "albums" }
    }


    fun isPhotoCollection(raw: String): Boolean {
        val link = MediaLinkParser.parse(raw) ?: return false
        if (link.platform != MediaPlatform.FACEBOOK) return false
        val uri = URI(link.url)
        val host = uri.host.lowercase(Locale.ROOT)
        if (host == "fb.watch" || host.endsWith(".fb.watch")) return false
        val path = uri.path.orEmpty().lowercase(Locale.ROOT)
        return !(
            path == "/watch" ||
                path.startsWith("/watch/") ||
                path == "/reel" ||
                path.startsWith("/reel/") ||
                path.startsWith("/reels/") ||
                path.startsWith("/video/") ||
                path.startsWith("/videos/") ||
                path.contains("/videos/") ||
                path.endsWith("/videos") ||
                path.startsWith("/share/v/") ||
                path.startsWith("/share/r/")
        )
    }
}
