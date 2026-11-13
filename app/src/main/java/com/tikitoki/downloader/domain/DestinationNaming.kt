package com.tikitoki.downloader.domain

/** Names derived from remote metadata must never escape the chosen folder. */
object DestinationNaming {
    fun account(url: String): String {
        val parsed = TikTokLinkParser.parse(url)
        val username = (parsed as? LinkParseResult.Success)?.link?.username
        return username?.let { safeComponent("@$it", "Sin cuenta") } ?: "Sin cuenta"
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
