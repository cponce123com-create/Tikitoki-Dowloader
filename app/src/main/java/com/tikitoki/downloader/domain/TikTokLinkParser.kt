package com.tikitoki.downloader.domain

import java.net.URI
import java.util.Locale

enum class TikTokLinkType { PROFILE, VIDEO, SHORT_LINK }

data class TikTokLink(
    val url: String,
    val type: TikTokLinkType,
    val username: String? = null,
    val videoId: String? = null,
)

sealed interface LinkParseResult {
    data class Success(val link: TikTokLink) : LinkParseResult
    data class Error(val message: String) : LinkParseResult
}

/**
 * Only validates and classifies links; it does NOT retrieve TikTok content.
 * Avoid claiming a profile is downloadable until an authorized source has responded.
 */
object TikTokLinkParser {
    private val profileOrVideo = Regex("^/@([A-Za-z0-9._]{1,50})(?:/video/([0-9]+))?/?$")
    private val directVideo = Regex("^/video/([0-9]+)/?$")

    fun parse(rawInput: String): LinkParseResult {
        val raw = rawInput.trim()
        if (raw.isEmpty()) return LinkParseResult.Error("Pega un enlace de TikTok.")

        val normalized = if (raw.startsWith("https://", ignoreCase = true) ||
            raw.startsWith("http://", ignoreCase = true)
        ) raw else "https://$raw"

        val uri = try {
            URI(normalized)
        } catch (_: Exception) {
            return LinkParseResult.Error("El enlace no tiene un formato válido.")
        }

        if (uri.scheme?.lowercase(Locale.ROOT) != "https") {
            return LinkParseResult.Error("Por seguridad, utiliza un enlace HTTPS.")
        }
        if (uri.userInfo != null || uri.port != -1) {
            return LinkParseResult.Error("El enlace tiene credenciales o un puerto no permitido.")
        }

        val host = uri.host?.lowercase(Locale.ROOT)?.trimEnd('.')
            ?: return LinkParseResult.Error("No se pudo reconocer el dominio.")
        if (host != "tiktok.com" && !host.endsWith(".tiktok.com")) {
            return LinkParseResult.Error("El enlace debe pertenecer a TikTok.")
        }
        val path = uri.path ?: ""

        if (host == "vm.tiktok.com" || host == "vt.tiktok.com" ||
            path.startsWith("/t/")
        ) {
            return LinkParseResult.Success(TikTokLink(normalized, TikTokLinkType.SHORT_LINK))
        }

        profileOrVideo.matchEntire(path)?.let { match ->
            val username = match.groupValues[1]
            val id = match.groupValues[2].ifEmpty { null }
            return LinkParseResult.Success(
                TikTokLink(
                    normalized,
                    if (id == null) TikTokLinkType.PROFILE else TikTokLinkType.VIDEO,
                    username,
                    id,
                )
            )
        }
        directVideo.matchEntire(path)?.let { match ->
            return LinkParseResult.Success(
                TikTokLink(normalized, TikTokLinkType.VIDEO, videoId = match.groupValues[1])
            )
        }

        return LinkParseResult.Error(
            "Escribe el enlace de un perfil (@usuario) o de un video de TikTok."
        )
    }
}
