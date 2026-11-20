package com.tikitoki.downloader.data

import android.content.Context
import com.tikitoki.downloader.R
import com.tikitoki.downloader.domain.MetadataSanitizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

internal data class CatalogPlaylist(
    val title: String,
    val url: String,
)

internal data class CatalogSection(
    val nameRes: Int,
    val playlists: List<CatalogPlaylist>,
)

internal data class CatalogResult(
    val sections: List<CatalogSection>,
    val cached: Boolean,
    val capped: Boolean,
)

internal object CollectionsCatalog {
    const val MAX_PLAYLISTS = 1000
    private const val MAX_BYTES = 2 * 1024 * 1024
    private const val MAX_TITLE_LENGTH = 150
    private val playlistId = Regex("PL[A-Za-z0-9_-]{5,198}")
    private const val PREFS = "collections_catalog"

    suspend fun load(context: Context): CatalogResult =
        withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            try {
                val raw = UniversalMediaSource(context).channelPlaylistsJson()
                val lists = parse(raw)
                val serialized =
                    JSONArray()
                        .apply {
                            lists.forEach { put(JSONObject().put("title", it.title).put("url", it.url)) }
                        }.toString()
                prefs.edit().putString("last_valid", serialized).apply()
                classify(lists, false, lists.size >= MAX_PLAYLISTS)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                val cached = prefs.getString("last_valid", null)
                val lists = cached?.let { runCatching { parseCache(it) }.getOrNull() }
                if (lists != null) {
                    classify(lists, true, lists.size >= MAX_PLAYLISTS)
                } else {
                    error("catalog_unavailable")
                }
            }
        }

    internal fun parse(raw: String): List<CatalogPlaylist> {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
        val items = JSONObject(raw).getJSONArray("entries")
        require(items.length() <= MAX_PLAYLISTS)
        return (0 until items.length())
            .mapNotNull { index -> items.optJSONObject(index)?.let(::parsePlaylist) }
            .distinctBy { it.url }
    }

    private fun parsePlaylist(entry: JSONObject): CatalogPlaylist? {
        val id = entry.optString("id")
        val title = entry.optString("title")
        if (!playlistId.matches(id) || title.isBlank() || title == "null") return null
        return CatalogPlaylist(
            title = MetadataSanitizer.title(title).take(MAX_TITLE_LENGTH),
            url = "https://www.youtube.com/playlist?list=$id",
        )
    }

    private fun parseCache(raw: String): List<CatalogPlaylist> {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
        val json = JSONArray(raw)
        require(json.length() <= MAX_PLAYLISTS)
        return (0 until json.length()).map { i ->
            val entry = json.getJSONObject(i)
            val url = entry.getString("url")
            require(url.matches(Regex("https://www\\.youtube\\.com/playlist\\?list=PL[A-Za-z0-9_-]{5,198}")))
            CatalogPlaylist(entry.getString("title"), url)
        }
    }

    internal fun classify(
        lists: List<CatalogPlaylist>,
        cached: Boolean,
        capped: Boolean,
    ): CatalogResult {
        val sections =
            listOf(
                CatalogSection(R.string.collections_all, lists),
                CatalogSection(
                    R.string.collections_music,
                    lists.filter {
                        matches(it, "musica", "salsa", "cumbia", "rock", "baladas", "artista", "autor")
                    },
                ),
                CatalogSection(
                    R.string.collections_christian,
                    lists.filter {
                        matches(it, "cristian", "devocional", "alabanza", "biblia", "evangelio")
                    },
                ),
                CatalogSection(
                    R.string.collections_reflection,
                    lists.filter {
                        matches(it, "reflexion", "mensaje", "motivacion", "amistad", "amor")
                    },
                ),
                CatalogSection(
                    R.string.collections_news,
                    lists.filter { matches(it, "noticia", "actualidad", "informativo") },
                ),
            )
        return CatalogResult(sections, cached, capped)
    }

    private fun matches(
        item: CatalogPlaylist,
        vararg terms: String,
    ): Boolean {
        val title =
            Normalizer
                .normalize(item.title.lowercase(Locale.ROOT), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
        return terms.any { title.contains(it) }
    }
}
