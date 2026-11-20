package com.tikitoki.downloader.ui

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tikitoki.downloader.data.VideoEntry
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

private const val HISTORY_LIMIT = 50

internal data class RockolaPlaylist(
    val name: String,
    val tracks: List<VideoEntry>,
)

internal class RockolaLibrary(
    private val preferences: SharedPreferences,
) {
    var queue by mutableStateOf(readTracks("playback_queue"))
        private set
    var favorites by mutableStateOf(readTracks("favorite_items"))
        private set
    var favoriteIds by mutableStateOf(
        preferences.getStringSet("favorites", emptySet()).orEmpty() + favorites.map { it.id },
    )
        private set
    var history by mutableStateOf(readTracks("history_items").asReversed())
        private set
    var playlists by mutableStateOf(readPlaylists())
        private set

    fun enqueue(track: VideoEntry) {
        if (queue.none { it.id == track.id }) updateQueue(queue + track)
    }

    fun remove(track: VideoEntry) {
        if (queue.any { it.id == track.id }) updateQueue(queue.filterNot { it.id == track.id })
    }

    fun moveUp(track: VideoEntry) {
        val index = queue.indexOfFirst { it.id == track.id }
        if (index <= 0) return
        val reordered = queue.toMutableList()
        val previous = reordered[index - 1]
        reordered[index - 1] = track
        reordered[index] = previous
        updateQueue(reordered)
    }

    fun recordPlayed(track: VideoEntry) {
        history = (listOf(track) + history.filterNot { it.id == track.id }).take(HISTORY_LIMIT)
        preferences
            .edit()
            .putStringSet("history", history.map { it.id }.toSet())
            .putString("history_items", RockolaTrackJson.encode(history.asReversed()).toString())
            .apply()
    }

    fun toggleFavorite(track: VideoEntry) {
        if (track.id in favoriteIds) {
            favoriteIds = favoriteIds - track.id
            favorites = favorites.filterNot { it.id == track.id }
        } else {
            favoriteIds = favoriteIds + track.id
            favorites = favorites + track
        }
        preferences
            .edit()
            .putStringSet("favorites", favoriteIds)
            .putString("favorite_items", RockolaTrackJson.encode(favorites).toString())
            .apply()
    }

    fun saveToPlaylist(
        name: String,
        track: VideoEntry,
    ) {
        val existing = playlists.firstOrNull { it.name.equals(name, ignoreCase = true) }
        playlists =
            if (existing == null) {
                playlists + RockolaPlaylist(name, listOf(track))
            } else {
                playlists.map {
                    if (it === existing && it.tracks.none { saved -> saved.id == track.id }) {
                        it.copy(tracks = it.tracks + track)
                    } else {
                        it
                    }
                }
            }
        val data = JSONObject()
        playlists.forEach { data.put(it.name, RockolaTrackJson.encode(it.tracks)) }
        preferences.edit().putString("playlists", data.toString()).apply()
    }

    private fun updateQueue(tracks: List<VideoEntry>) {
        queue = tracks
        preferences.edit().putString("playback_queue", RockolaTrackJson.encode(tracks).toString()).apply()
    }

    private fun readTracks(key: String) = RockolaTrackJson.decode(preferences.getString(key, "[]").orEmpty())

    private fun readPlaylists(): List<RockolaPlaylist> =
        try {
            val data = JSONObject(preferences.getString("playlists", "{}").orEmpty())
            data
                .keys()
                .asSequence()
                .mapNotNull { name ->
                    data.optJSONArray(name)?.let { RockolaPlaylist(name, RockolaTrackJson.decode(it.toString())) }
                }.toList()
        } catch (_: JSONException) {
            emptyList()
        }
}

internal object RockolaTrackJson {
    fun encode(tracks: List<VideoEntry>): JSONArray =
        JSONArray().apply {
            tracks.forEach { item ->
                put(
                    JSONObject().apply {
                        put("id", item.id)
                        put("url", item.url)
                        put("title", item.title)
                        put("thumbnail", item.thumbnail.orEmpty())
                        put("duration", item.durationSeconds)
                        put("uploader", item.uploader.orEmpty())
                        put("views", item.viewCount)
                    },
                )
            }
        }

    fun decode(value: String): List<VideoEntry> =
        try {
            val items = JSONArray(value)
            (0 until items.length())
                .mapNotNull { items.optJSONObject(it) }
                .mapNotNull(::decodeTrack)
                .distinctBy { it.id }
        } catch (_: JSONException) {
            emptyList()
        }

    private fun decodeTrack(item: JSONObject): VideoEntry? {
        val id = item.optString("id")
        val url = item.optString("url")
        if (id.isBlank() || url.isBlank()) return null
        return VideoEntry(
            id = id,
            url = url,
            title = item.optString("title").ifBlank { "Video de YouTube" },
            thumbnail = item.optString("thumbnail").takeIf { it.isNotBlank() },
            durationSeconds = item.optInt("duration").coerceAtLeast(0),
            uploader = item.optString("uploader").takeIf { it.isNotBlank() },
            viewCount = item.optLong("views").coerceAtLeast(0L),
        )
    }
}
