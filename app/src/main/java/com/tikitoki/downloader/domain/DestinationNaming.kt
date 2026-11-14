package com.tikitoki.downloader.domain

import java.net.URI

/** Names derived from remote metadata must never escape the chosen folder. */
object DestinationNaming {
    fun account(url: String): String {
        val link = MediaLinkParser.parse(url) ?: return "Sin cuenta"
        val parts = runCatching { URI(link.url).path.split('/').filter { it.isNotBlank() } }.getOrDefault(emptyList())
        val first = parts.firstOrNull() ?: return "Sin cuenta"
        val account = when (link.platform) {
            MediaPlatform.TIKTOK -> (TikTokLinkParser.parse(link.url) as? LinkParseResult.Success)?.link?.username?.let { "@$it" }
            MediaPlatform.YOUTUBE -> when {
                first.startsWith("@") && first.length > 1 -> first
                first in setOf("channel", "c", "user") -> parts.getOrNull(1)
                else -> null
            }
            MediaPlatform.FACEBOOK -> first.takeUnless {
                it.lowercase() in setOf("watch", "reel", "reels", "videos", "share", "video.php", "profile.php", "groups", "pages")
            }
            MediaPlatform.INSTAGRAM -> first.takeUnless {
                it.lowercase() in setOf("p", "reel", "reels", "stories", "explore", "accounts", "tv")
            }
            else -> null
        }
        return account?.let { safeComponent(it, "Sin cuenta") } ?: "Sin cuenta"
    }

    fun filename(title: String, id: String, extension: String): String {
        val stem = safeComponent(title, "Video").take(90).trimEnd(' ', '.')
        val suffix = id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.takeLast(24)
        val safeExt = extension.lowercase().filter { it in 'a'..'z' || it in '0'..'9' }.take(8).ifBlank { "mp4" }
        return (if (suffix.isBlank()) stem else "$stem - $suffix") + ".$safeExt"
    }

    private fun safeComponent(value: String, fallback: String): String {
        val cleaned = value.map { c ->
            if (c.isISOControl() || Character.getType(c) == Character.FORMAT.toInt() || c in "\\/:*?\"<>|") ' ' else c
        }.joinToString("").replace(Regex("\\s+"), " ").trim { it == '.' || it.isWhitespace() }
        return cleaned.take(100).trim().ifBlank { fallback }
    }
}

