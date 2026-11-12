package com.tikitoki.downloader.domain

import java.net.URI
import java.util.Locale

enum class MediaPlatform { TIKTOK, YOUTUBE, FACEBOOK, INSTAGRAM, DIRECT_MEDIA }

data class MediaLink(val url: String, val platform: MediaPlatform, val collection: Boolean)

/** Strict allowlist: no arbitrary host or embedded credentials. */
object MediaLinkParser {
    fun parse(raw: String): MediaLink? {
        val text = raw
        if (text.length > 4096 || text.isEmpty() || text != text.trim()) return null
        if (text.any { Character.isISOControl(it) || it.isWhitespace() || it in "\\u202a\\u202b\\u202c\\u202d\\u202e\\u2066\\u2067\\u2068\\u2069" }) return null
        val uri = try { URI(text) } catch (_: Exception) { return null }
        if (uri.scheme?.lowercase(Locale.ROOT) != "https" || uri.userInfo != null || uri.port != -1) return null
        if (uri.isOpaque || uri.fragment != null || uri.rawAuthority?.contains('\\') == true) return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        if (host.endsWith('.') || !host.matches(Regex("[a-z0-9.-]+"))) return null
        fun domain(root: String) = host == root || host.endsWith(".$root")
        val path = uri.path.orEmpty()
        val platform = when {
            domain("tiktok.com") -> MediaPlatform.TIKTOK
            domain("youtube.com") || domain("youtu.be") || domain("youtube-nocookie.com") -> MediaPlatform.YOUTUBE
            domain("facebook.com") || domain("fb.watch") -> MediaPlatform.FACEBOOK
            domain("instagram.com") -> MediaPlatform.INSTAGRAM
            else -> return null
        }
        val collection = when (platform) {
            MediaPlatform.TIKTOK -> Regex("^/@[A-Za-z0-9._]+/?$").matches(path)
            MediaPlatform.YOUTUBE -> path.startsWith("/@") || path.startsWith("/channel/") ||
                path.startsWith("/c/") || path.startsWith("/user/") || path == "/playlist"
            MediaPlatform.FACEBOOK -> path.endsWith("/videos") || path.contains("/videos/")
            MediaPlatform.INSTAGRAM -> path.matches(Regex("^/[A-Za-z0-9._]+/?$"))
            else -> false
        }
        val normalized = try {
            URI("https", null, host, -1, uri.path.ifEmpty { "/" }, uri.rawQuery?.let { java.net.URLDecoder.decode(it, "UTF-8") }, null).toASCIIString()
        } catch (_: Exception) { return null }
        return MediaLink(normalized, platform, collection)
    }
}
