package com.tikitoki.downloader.domain

import java.net.URI

/** Resolve Facebook share links without ever following a redirect to another host. */
object FacebookShareResolver {
    private const val MAX_HOPS = 5

    fun resolve(raw: String, location: (String) -> String?): String? {
        var current = facebookUrl(raw) ?: return null
        val seen = mutableSetOf<String>()
        repeat(MAX_HOPS) {
            if (!seen.add(current)) return null
            val uri = URI(current)
            if (!uri.path.orEmpty().startsWith("/share/")) return current
            val next = location(current) ?: return null
            val absolute = runCatching { uri.resolve(next).toASCIIString() }.getOrNull() ?: return null
            current = facebookUrl(absolute) ?: return null
        }
        return current.takeIf { !URI(it).path.orEmpty().startsWith("/share/") }
    }

    private fun facebookUrl(url: String): String? {
        val parsed = IncomingLinkValidator.validate(url) ?: return null
        val host = URI(parsed.url).host.lowercase()
        return parsed.url.takeIf {
            parsed.platform == MediaPlatform.FACEBOOK &&
                (host == "facebook.com" || host.endsWith(".facebook.com"))
        }
    }
}
