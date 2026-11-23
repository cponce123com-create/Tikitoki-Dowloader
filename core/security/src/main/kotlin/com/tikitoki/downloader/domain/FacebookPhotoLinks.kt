package com.tikitoki.downloader.domain

import java.net.URI
import java.util.Locale

/**
 * Routes Facebook profiles, photo pages and generic share links to the gallery engine.
 * Explicit video/reel links must continue to use the existing video analyzer.
 */
object FacebookPhotoLinks {
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
