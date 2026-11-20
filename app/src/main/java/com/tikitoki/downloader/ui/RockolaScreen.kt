package com.tikitoki.downloader.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tikitoki.downloader.VideoThumbnail
import com.tikitoki.downloader.data.DiagnosticHistory
import com.tikitoki.downloader.data.UniversalMediaSource
import com.tikitoki.downloader.data.VideoEntry
import com.tikitoki.downloader.data.VideoThumbnailResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private const val MAX_HISTORY_ITEMS = 50
private const val GENRES_IN_FIRST_ROW = 5
private const val SECONDS_PER_MINUTE = 60
private const val THOUSAND_VIEWS = 1_000.0
private const val MILLION_VIEWS = 1_000_000.0

private val rockolaGenres =
    listOf(
        "Salsa",
        "Cumbia",
        "Reguetón",
        "Rock",
        "Pop",
        "Bachata",
        "Criolla",
        "Huayno",
        "Electrónica",
    )

private enum class RockolaSection { GENRES, ARTISTS, SONGS }

private data class ArtistSummary(
    val name: String,
    val sample: VideoEntry,
    val songCount: Int,
)

@Suppress(
    "ktlint:standard:function-naming",
    "LongMethod",
    "CyclomaticComplexMethod",
    "TooGenericExceptionCaught",
)
@Composable
internal fun RockolaScreen(
    source: UniversalMediaSource,
    thumbnailResolver: VideoThumbnailResolver,
    onDownload: (VideoEntry) -> Unit,
    onOpenYouTube: (VideoEntry) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("rockola", Context.MODE_PRIVATE) }
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    var section by remember { mutableStateOf(RockolaSection.GENRES) }
    var query by remember { mutableStateOf("") }
    var activeArtist by remember { mutableStateOf<String?>(null) }
    var selectedGenre by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf(emptyList<VideoEntry>()) }
    var artists by remember { mutableStateOf(emptyList<ArtistSummary>()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var favoritesOnly by remember { mutableStateOf(false) }
    var favoriteIds by remember { mutableStateOf(prefs.getStringSet("favorites", emptySet()).orEmpty().toSet()) }
    var favoriteItems by remember { mutableStateOf(loadTracks(prefs, "favorite_items")) }
    var historyItems by remember { mutableStateOf(loadTracks(prefs, "history_items")) }
    var pendingDownload by remember { mutableStateOf<VideoEntry?>(null) }
    var downloadConsent by remember { mutableStateOf(false) }

    fun updateSet(
        key: String,
        values: Set<String>,
    ) {
        prefs.edit().putStringSet(key, values).apply()
    }

    fun updateItems(
        key: String,
        values: List<VideoEntry>,
    ) {
        val array = JSONArray()
        values.takeLast(MAX_HISTORY_ITEMS).forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("url", item.url)
                    put("title", item.title)
                    put("thumbnail", item.thumbnail ?: "")
                    put("duration", item.durationSeconds)
                    put("uploader", item.uploader ?: "")
                    put("views", item.viewCount)
                },
            )
        }
        prefs.edit().putString(key, array.toString()).apply()
    }

    fun toggleFavorite(video: VideoEntry) {
        favoriteIds = if (video.id in favoriteIds) favoriteIds - video.id else favoriteIds + video.id
        updateSet("favorites", favoriteIds)
        favoriteItems =
            if (video.id in
                favoriteIds
            ) {
                favoriteItems.filterNot { it.id == video.id } + video
            } else {
                favoriteItems.filterNot { it.id == video.id }
            }
        updateItems("favorite_items", favoriteItems)
    }

    fun rememberHistory(video: VideoEntry) {
        historyItems = (historyItems.filterNot { it.id == video.id } + video).takeLast(MAX_HISTORY_ITEMS)
        updateSet("history", historyItems.map { it.id }.toSet())
        updateItems("history_items", historyItems)
    }

    fun search(
        term: String,
        artist: String? = null,
        genre: String? = null,
    ) {
        val clean = term.trim()
        if (clean.isBlank() || loading) return
        query = clean
        activeArtist = artist
        selectedGenre = genre
        favoritesOnly = false
        keyboard?.hide()
        loading = true
        message = ""
        scope.launch {
            try {
                val found = source.searchYouTube(clean, sortByViews = artist != null)
                results = found
                if (artist == null) {
                    artists =
                        found
                            .mapNotNull { item ->
                                item.uploader?.takeIf { it.isNotBlank() }?.let { name -> name to item }
                            }.groupBy { it.first }
                            .map { (name, entries) ->
                                ArtistSummary(name, entries.first().second, entries.size)
                            }.sortedByDescending { it.songCount }
                }
                section = if (artist == null && artists.isNotEmpty()) RockolaSection.ARTISTS else RockolaSection.SONGS
                if (found.isEmpty()) message = "No se encontraron resultados."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DiagnosticHistory.record(context, e.message)
                results = emptyList()
                if (artist == null) artists = emptyList()
                message = "No se pudo consultar YouTube. Comprueba tu conexión."
            } finally {
                loading = false
            }
        }
    }

    fun openSongList(title: String) {
        search(title, artist = title, genre = selectedGenre)
    }

    fun requestDownload(video: VideoEntry) {
        if (prefs.getBoolean("authorized_downloads_ack", false)) {
            onDownload(video)
        } else {
            downloadConsent = false
            pendingDownload = video
        }
    }

    val visibleResults = results.filter { !favoritesOnly || it.id in favoriteIds }
    pendingDownload?.let { video ->
        AlertDialog(
            onDismissRequest = { pendingDownload = null },
            title = { Text("Descarga autorizada") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Descarga solo contenido propio, de dominio público o para el que tengas autorización. " +
                            "La aplicación no puede verificar los derechos de cada video de YouTube.",
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = downloadConsent, onCheckedChange = { downloadConsent = it })
                        Text("Confirmo que tengo permiso para descargarlo.")
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = downloadConsent,
                    onClick = {
                        prefs.edit().putBoolean("authorized_downloads_ack", true).apply()
                        pendingDownload = null
                        onDownload(video)
                    },
                ) { Text("Continuar") }
            },
            dismissButton = { TextButton(onClick = { pendingDownload = null }) { Text("Cancelar") } },
        )
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (section != RockolaSection.GENRES) {
                        IconButton(onClick = {
                            section =
                                if (section == RockolaSection.SONGS) RockolaSection.ARTISTS else RockolaSection.GENRES
                            if (section == RockolaSection.GENRES) {
                                artists = emptyList()
                                results = emptyList()
                                activeArtist = null
                            }
                        }) { Icon(Icons.Default.ArrowBack, contentDescription = "Atrás", tint = Color.White) }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Rockola",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            when (section) {
                                RockolaSection.GENRES -> "Elige un género para descubrir artistas."
                                RockolaSection.ARTISTS -> "Artistas encontrados en ${selectedGenre ?: "YouTube"}."
                                RockolaSection.SONGS -> {
                                    val artist = activeArtist ?: "tu búsqueda"
                                    "Resultados de $artist"
                                }
                            },
                            color = TechMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Buscar canción, cantante o grupo") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { search(query) }, enabled = !loading) {
                            Icon(Icons.Default.Search, contentDescription = "Buscar")
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search(query) }),
                )
                if (section == RockolaSection.GENRES) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        rockolaGenres.take(GENRES_IN_FIRST_ROW).forEach { genre ->
                            FilterChip(selected = selectedGenre == genre, onClick = {
                                search("$genre música popular", genre = genre)
                            }, label = { Text(genre, maxLines = 1) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        rockolaGenres.drop(GENRES_IN_FIRST_ROW).forEach { genre ->
                            FilterChip(selected = selectedGenre == genre, onClick = {
                                search("$genre música popular", genre = genre)
                            }, label = { Text(genre, maxLines = 1) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        FilterChip(
                            selected = false,
                            onClick = {
                                results = historyItems.asReversed()
                                section = RockolaSection.SONGS
                                activeArtist = "Historial"
                                favoritesOnly = false
                            },
                            label = { Text("Historial") },
                            enabled = historyItems.isNotEmpty(),
                        )
                        FilterChip(
                            selected = false,
                            onClick = {
                                results = favoriteItems.asReversed()
                                section = RockolaSection.SONGS
                                activeArtist = "Favoritos"
                                favoritesOnly = true
                            },
                            label = { Text("Favoritos") },
                            enabled = favoriteItems.isNotEmpty(),
                        )
                    }
                } else if (section == RockolaSection.SONGS) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(
                            selected = favoritesOnly,
                            onClick = { favoritesOnly = !favoritesOnly },
                            label = { Text("Favoritos") },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${visibleResults.size} resultados",
                            color = TechMuted,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
        if (loading) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator(color = TechPurple) }
            }
        }
        if (message.isNotBlank() && !loading) {
            item { Text(message, color = TechMuted, modifier = Modifier.padding(vertical = 12.dp)) }
        }
        if (!loading && section == RockolaSection.ARTISTS) {
            items(artists, key = { it.name }) { artist ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        VideoThumbnail(artist.sample, thumbnailResolver, Modifier.size(width = 96.dp, height = 64.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                artist.name,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${artist.songCount} resultados encontrados",
                                color = TechMuted,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        Button(onClick = { openSongList(artist.name) }) { Text("Canciones") }
                    }
                }
            }
            if (artists.isEmpty() && results.isNotEmpty()) {
                item { Text("No se pudo identificar el canal; mostrando canciones encontradas.", color = TechMuted) }
            }
        }
        if (!loading && section == RockolaSection.SONGS) {
            items(visibleResults, key = { it.id }) { video ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        VideoThumbnail(video, thumbnailResolver, Modifier.size(width = 128.dp, height = 74.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                video.title,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(songMeta(video), color = TechMuted, style = MaterialTheme.typography.labelSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                IconButton(
                                    onClick = {
                                        rememberHistory(video)
                                        onOpenYouTube(video)
                                    },
                                    modifier =
                                        Modifier.size(
                                            36.dp,
                                        ),
                                ) {
                                    Icon(
                                        Icons.Default.OpenInNew,
                                        contentDescription = "Abrir en YouTube",
                                        tint = TechPink,
                                    )
                                }
                                IconButton(onClick = { toggleFavorite(video) }, modifier = Modifier.size(36.dp)) {
                                    Icon(
                                        if (video.id in
                                            favoriteIds
                                        ) {
                                            Icons.Default.Favorite
                                        } else {
                                            Icons.Default.FavoriteBorder
                                        },
                                        contentDescription = "Favorito",
                                        tint = TechPink,
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        requestDownload(video)
                                    },
                                    modifier =
                                        Modifier.size(
                                            36.dp,
                                        ),
                                ) { Icon(Icons.Default.Download, contentDescription = "Descargar", tint = TechPurple) }
                            }
                        }
                    }
                }
            }
        }
        if (!loading && section == RockolaSection.GENRES && results.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Elige un género o busca un artista para comenzar.", color = TechMuted)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { search("salsa música popular", genre = "Salsa") }) { Text("Explorar música") }
                }
            }
        }
        val hasUncategorizedSongs = results.isNotEmpty() && artists.isEmpty()
        if (!loading && section == RockolaSection.ARTISTS && hasUncategorizedSongs) {
            items(results, key = { it.id }) { video ->
                SimpleSongResult(
                    video,
                    thumbnailResolver,
                    favoriteIds,
                    onOpenYouTube,
                    ::requestDownload,
                    ::toggleFavorite,
                    ::rememberHistory,
                )
            }
        }
    }
}

@Suppress(
    "ktlint:standard:function-naming",
    "LongParameterList",
)
@Composable
private fun SimpleSongResult(
    video: VideoEntry,
    resolver: VideoThumbnailResolver,
    favoriteIds: Set<String>,
    onOpen: (VideoEntry) -> Unit,
    onDownload: (VideoEntry) -> Unit,
    onFavorite: (VideoEntry) -> Unit,
    onHistory: (VideoEntry) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            VideoThumbnail(video, resolver, Modifier.size(width = 128.dp, height = 74.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(video.title, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row {
                    IconButton(
                        onClick = {
                            onHistory(video)
                            onOpen(video)
                        },
                        modifier =
                            Modifier.size(
                                36.dp,
                            ),
                    ) { Icon(Icons.Default.OpenInNew, contentDescription = "Abrir en YouTube", tint = TechPink) }
                    IconButton(onClick = { onFavorite(video) }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            if (video.id in
                                favoriteIds
                            ) {
                                Icons.Default.Favorite
                            } else {
                                Icons.Default.FavoriteBorder
                            },
                            contentDescription = "Favorito",
                            tint = TechPink,
                        )
                    }
                    IconButton(
                        onClick = {
                            onDownload(video)
                        },
                        modifier =
                            Modifier.size(
                                36.dp,
                            ),
                    ) { Icon(Icons.Default.Download, contentDescription = "Descargar", tint = TechPurple) }
                }
            }
        }
    }
}

private fun songMeta(video: VideoEntry): String {
    val duration =
        if (video.durationSeconds >
            0
        ) {
            "%d:%02d".format(video.durationSeconds / SECONDS_PER_MINUTE, video.durationSeconds % SECONDS_PER_MINUTE)
        } else {
            "YouTube"
        }
    val views =
        video.viewCount
            .takeIf { it > 0 }
            ?.let { " · ${compactCount(it)} vistas" }
            .orEmpty()
    return duration + views
}

private fun compactCount(value: Long): String =
    when {
        value >= MILLION_VIEWS -> "%.1f M".format(value / MILLION_VIEWS)
        value >= THOUSAND_VIEWS -> "%.1f mil".format(value / THOUSAND_VIEWS)
        else -> value.toString()
    }

private fun loadTracks(
    prefs: android.content.SharedPreferences,
    key: String,
): List<VideoEntry> =
    runCatching {
        val array = JSONArray(prefs.getString(key, "[]"))
        (0 until array.length())
            .mapNotNull { index ->
                array.optJSONObject(index)?.let { item ->
                    VideoEntry(
                        id = item.optString("id"),
                        url = item.optString("url"),
                        title = item.optString("title").ifBlank { "Video de YouTube" },
                        thumbnail = item.optString("thumbnail").takeIf { it.isNotBlank() },
                        durationSeconds = item.optInt("duration").coerceAtLeast(0),
                        uploader = item.optString("uploader").takeIf { it.isNotBlank() },
                        viewCount = item.optLong("views", 0L).coerceAtLeast(0L),
                    )
                }
            }.filter { it.id.isNotBlank() && it.url.isNotBlank() }
    }.getOrDefault(emptyList())
