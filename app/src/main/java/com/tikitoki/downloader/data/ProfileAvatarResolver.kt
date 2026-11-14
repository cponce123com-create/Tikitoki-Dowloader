package com.tikitoki.downloader.data

import com.tikitoki.downloader.domain.LinkParseResult
import com.tikitoki.downloader.domain.TikTokLinkParser
import com.tikitoki.downloader.domain.TikTokLinkType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.ArrayDeque
import java.util.Locale

/** Best-effort public profile metadata lookup; no login or private-profile access. */
data class ProfileIdentity(val displayName: String?, val avatar: String?)

class ProfileAvatarResolver {
    suspend fun resolve(profileUrl: String): String? = resolveIdentity(profileUrl)?.avatar

    suspend fun resolveIdentity(profileUrl: String): ProfileIdentity? = withContext(Dispatchers.IO) {
        val parsed = TikTokLinkParser.parse(profileUrl) as? LinkParseResult.Success ?: return@withContext null
        if (parsed.link.type != TikTokLinkType.PROFILE) return@withContext null
        val username = parsed.link.username ?: return@withContext null
        val connection = URL("https://www.tiktok.com/@$username").openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
            if (connection.responseCode != 200) return@withContext null
            val bytes = connection.inputStream.use { it.readNBytes(2 * 1024 * 1024 + 1) }
            if (bytes.size > 2 * 1024 * 1024) return@withContext null
            findIdentity(bytes.toString(Charsets.UTF_8), username)
        } catch (_: Exception) { null }
        finally { connection.disconnect() }
    }

    internal fun findAvatar(html: String, username: String): String? = findIdentity(html, username)?.avatar

    internal fun findIdentity(html: String, username: String): ProfileIdentity? {
        val script = Regex("<script\\b[^>]*\\bid=[\"'](?:SIGI_STATE|__UNIVERSAL_DATA_FOR_REHYDRATION__)[\"'][^>]*>(.*?)</script>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        for (match in script.findAll(html)) {
            val root = runCatching { JSONObject(match.groupValues[1]) }.getOrNull() ?: continue
            val pending = ArrayDeque<Any>()
            pending.add(root)
            var visited = 0
            while (pending.isNotEmpty() && visited++ < 50000) {
                when (val node = pending.removeFirst()) {
                    is JSONObject -> {
                        if (node.optString("uniqueId").equals(username, ignoreCase = true)) {
                            val avatar = listOf("avatarLarger", "avatarMedium", "avatarThumb")
                                .firstNotNullOfOrNull { node.optString(it).takeIf(::safeAvatarUrl) }
                            val name = node.optString("nickname").takeIf { it.isNotBlank() && it != "null" }
                                ?.let { com.tikitoki.downloader.domain.MetadataSanitizer.title(it) }
                            if (avatar != null || name != null) return ProfileIdentity(name, avatar)
                        }
                        val keys = node.keys()
                        while (keys.hasNext()) {
                            val child = node.opt(keys.next())
                            if (child is JSONObject || child is JSONArray) pending.add(child)
                        }
                    }
                    is JSONArray -> for (i in 0 until node.length()) {
                        val child = node.opt(i)
                        if (child is JSONObject || child is JSONArray) pending.add(child)
                    }
                }
            }
        }
        return null
    }

    private fun safeAvatarUrl(raw: String): Boolean = runCatching {
        val uri = URI(raw)
        val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
        uri.scheme == "https" && uri.userInfo == null && uri.port == -1 &&
            (host.endsWith(".tiktokcdn.com") || host.endsWith(".tiktokcdn-us.com") ||
             host.endsWith(".tiktokcdn-eu.com") || host.endsWith(".ibytedtos.com") ||
             host.endsWith(".byteoversea.com"))
    }.getOrDefault(false)
}
