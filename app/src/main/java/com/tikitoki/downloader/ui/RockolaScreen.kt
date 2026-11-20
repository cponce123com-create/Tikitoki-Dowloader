@file:Suppress("ktlint:standard:function-naming")

package com.tikitoki.downloader.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tikitoki.downloader.VideoThumbnail
import com.tikitoki.downloader.data.UniversalMediaSource
import com.tikitoki.downloader.data.VideoEntry
import com.tikitoki.downloader.data.VideoThumbnailResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

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

private enum class RockolaShelf { DISCOVER, FAVORITES, HISTORY }

private data class ArtistSummary(
    val name: String,
    val sample: VideoEntry,
    val songCount: Int,
)

@Composable
internal fun RockolaScreen(
    modifier: Modifier = Modifier,
    source: UniversalMediaSource,
    thumbnailResolver: VideoThumbnailResolver,
    onDownload: (VideoEntry) -> Unit,
    onOpenYouTube: (VideoEntry) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("rockola", Context.MODE_PRIVATE) }
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    var section by remember { mutableStateOf(RockolaSection.GENRES) }
    var shelf by remember { mutableStateOf(RockolaShelf.DISCOVER) }
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
        values.takeLast(50).forEach { item ->
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
            if (video.id in favoriteIds) {
                favoriteItems.filterNot { it.id == video.id } + video
            } else {
                favoriteItems.filterNot { it.id == video.id }
            }
        updateItems("favorite_items", favoriteItems)
    }

    fun rememberHistory(video: VideoEntry) {
        historyItems = (historyItems.filterNot { it.id == video.id } + video).takeLast(50)
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
        shelf = RockolaShelf.DISCOVER
        keyboard?.hide()
        loading = true
        message = ""
        scope.launch {
            try {
                val found = source.searchYouTube(clean, 30, sortByViews = artist != null)
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
                if (found.isEmpty()) message = "No se encontraron resultados. Prueba otro término."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                results = emptyList()
                if (artist == null) artists = emptyList()
                message = "No se pudo consultar YouTube. Comprueba tu conexión."
            } finally {
                loading = false
            }
        }
    }

    fun openSongList(artist: String) {
        search("$artist canciones", artist = artist, genre = selectedGenre)
    }

    fun openSavedShelf(target: RockolaShelf) {
        shelf = target
        section = RockolaSection.SONGS
        activeArtist = if (target == RockolaShelf.FAVORITES) "Tus favoritos" else "Tu historial"
        favoritesOnly = false
        results = if (target == RockolaShelf.FAVORITES) favoriteItems.asReversed() else historyItems.asReversed()
        artists = emptyList()
        message = ""
    }

    fun requestDownload(video: VideoEntry) {
        if (prefs.getBoolean("authorized_downloads_ack", false)) {
            onDownload(video)
        } else {
            downloadConsent = false
            pendingDownload = video
        }
    }

    fun navigateBack() {
        if (section == RockolaSection.SONGS && shelf == RockolaShelf.DISCOVER && artists.isNotEmpty()) {
            section = RockolaSection.ARTISTS
        } else {
            section = RockolaSection.GENRES
            activeArtist = null
            if (shelf != RockolaShelf.DISCOVER) {
                shelf = RockolaShelf.DISCOVER
                results = emptyList()
            }
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
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "rockola-title") {
            RockolaHero(
                section = section,
                activeArtist = activeArtist,
                selectedGenre = selectedGenre,
                onBack = ::navigateBack,
            )
        }
        item(key = "rockola-search") {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(180) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !loading,
                label = { Text("Canción, artista o grupo") },
                placeholder = { Text("Ej. Grupo 5, salsa clásica…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { search(query) }, enabled = !loading && query.isNotBlank()) {
                        if (loading) {
                            CircularProgressIndicator(Modifier.size(22.dp), color = TechPink, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Search, contentDescription = "Buscar música", tint = TechPink)
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search(query) }),
                shape = RoundedCornerShape(18.dp),
            )
        }
        item(key = "rockola-library-shortcuts") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ShelfCard(
                    label = "Favoritos",
                    count = favoriteItems.size,
                    icon = Icons.Default.Favorite,
                    selected = shelf == RockolaShelf.FAVORITES,
                    onClick = { openSavedShelf(RockolaShelf.FAVORITES) },
                    modifier = Modifier.weight(1f),
                )
                ShelfCard(
                    label = "Historial",
                    count = historyItems.size,
                    icon = Icons.Default.History,
                    selected = shelf == RockolaShelf.HISTORY,
                    onClick = { openSavedShelf(RockolaShelf.HISTORY) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (section == RockolaSection.GENRES) {
            item(key = "rockola-genres-heading") {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "Explora por género",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Elige un estilo y descubre canales públicos de YouTube.",
                        color = TechMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            item(key = "rockola-genres") {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rockolaGenres.forEach { genre ->
                        FilterChip(
                            selected = selectedGenre == genre,
                            onClick = { search("$genre música popular", genre = genre) },
                            enabled = !loading,
                            label = { Text(genre, maxLines = 1) },
                        )
                    }
                }
            }
        }
        if (section == RockolaSection.SONGS && shelf == RockolaShelf.DISCOVER) {
            item(key = "rockola-song-tools") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = favoritesOnly,
                        onClick = { favoritesOnly = !favoritesOnly },
                        label = { Text("Solo favoritos") },
                        leadingIcon = { Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    )
                    Text("${visibleResults.size} temas", color = TechMuted, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        if (loading) {
            item(key = "rockola-loading") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(color = TechPink, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Buscando música…", color = TechMuted)
                }
            }
        }
        if (message.isNotBlank() && !loading) {
            item(key = "rockola-message") { RockolaMessage(message) }
        }
        if (!loading && section == RockolaSection.ARTISTS) {
            item(key = "rockola-artists-heading") {
                Text(
                    "Canales y artistas sugeridos",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            items(artists, key = { "artist-${it.name}" }) { artist ->
                ArtistCard(
                    artist = artist,
                    resolver = thumbnailResolver,
                    onClick = { openSongList(artist.name) },
                )
            }
            if (artists.isEmpty() && results.isNotEmpty()) {
                item(key = "rockola-no-artists") {
                    RockolaMessage("No se pudo agrupar por canal; aquí tienes las canciones encontradas.")
                }
            }
            if (artists.isEmpty() && results.isEmpty() && message.isBlank()) {
                item(key = "rockola-empty-artists") { RockolaMessage("No se encontraron canales para esta búsqueda.") }
            }
        }
        if (!loading && section == RockolaSection.SONGS) {
            if (visibleResults.isEmpty() && message.isBlank()) {
                item(key = "rockola-empty-songs") {
                    RockolaMessage(
                        when {
                            shelf == RockolaShelf.FAVORITES -> "Aún no tienes favoritos. Marca una canción con el corazón y aparecerá aquí."
                            shelf == RockolaShelf.HISTORY -> "Tu historial está vacío. Reproduce una canción para guardarla aquí."
                            favoritesOnly -> "No hay favoritos entre estos resultados."
                            else -> "No se encontraron canciones. Prueba otra búsqueda o género."
                        },
                    )
                }
            }
            items(visibleResults, key = { "song-${it.id}" }) { video ->
                SongCard(
                    video = video,
                    resolver = thumbnailResolver,
                    isFavorite = video.id in favoriteIds,
                    onOpen = {
                        rememberHistory(video)
                        onOpenYouTube(video)
                    },
                    onFavorite = { toggleFavorite(video) },
                    onDownload = { requestDownload(video) },
                )
            }
        }
        if (!loading && section == RockolaSection.GENRES && results.isEmpty() && message.isBlank()) {
            item(key = "rockola-empty-home") {
                RockolaMessage("Elige un género o escribe una búsqueda para empezar tu próxima sesión.")
            }
        }
    }
}

@Composable
private fun RockolaHero(
    section: RockolaSection,
    activeArtist: String?,
    selectedGenre: String?,
    onBack: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(22.dp), color = TechPanel) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(listOf(Color(0xFF15523C), TechPanel, Color(0xFF172A3D))))
                    .padding(horizontal = 18.dp, vertical = 17.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (section != RockolaSection.GENRES) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
                }
                Spacer(Modifier.width(4.dp))
            } else {
                Surface(shape = RoundedCornerShape(16.dp), color = TechPink.copy(alpha = .14f)) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = TechPink,
                        modifier = Modifier.padding(10.dp).size(28.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    when (section) {
                        RockolaSection.GENRES -> "ROCKOLA · DESCUBRIR"
                        RockolaSection.ARTISTS -> "CANALES · ${selectedGenre ?: "YOUTUBE"}"
                        RockolaSection.SONGS -> activeArtist ?: "CANCIONES"
                    },
                    color = TechPink,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when (section) {
                        RockolaSection.GENRES -> "¿Qué quieres escuchar?"
                        RockolaSection.ARTISTS -> "Elige un canal para ver sus temas"
                        RockolaSection.SONGS -> "Tu siguiente canción está aquí"
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ShelfCard(
    label: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) TechPurple.copy(alpha = .42f) else TechPanel),
        border = BorderStroke(1.dp, if (selected) TechPink.copy(alpha = .8f) else TechBorder.copy(alpha = .55f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, contentDescription = null, tint = TechPink, modifier = Modifier.size(21.dp))
            Column(Modifier.weight(1f)) {
                Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text("$count guardadas", color = TechMuted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ArtistCard(
    artist: ArtistSummary,
    resolver: VideoThumbnailResolver,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = TechPanel),
        border = BorderStroke(1.dp, TechBorder.copy(alpha = .48f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            VideoThumbnail(artist.sample, resolver, Modifier.size(width = 104.dp, height = 72.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(artist.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${artist.songCount} temas encontrados", color = TechMuted, style = MaterialTheme.typography.labelSmall)
                Text("Ver canciones  →", color = TechPink, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SongCard(
    video: VideoEntry,
    resolver: VideoThumbnailResolver,
    isFavorite: Boolean,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    onDownload: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = TechPanel),
        border = BorderStroke(1.dp, TechBorder.copy(alpha = .48f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                VideoThumbnail(video, resolver, Modifier.size(width = 112.dp, height = 76.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(video.title, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(
                        songMeta(video),
                        color = TechMuted,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onOpen, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(17.dp), tint = TechPink)
                    Spacer(Modifier.width(4.dp))
                    Text("Reproducir")
                }
                IconButton(onClick = onFavorite, modifier = Modifier.size(48.dp)) {
                    Icon(
                        if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isFavorite) "Quitar ${video.title} de favoritos" else "Añadir ${video.title} a favoritos",
                        tint = if (isFavorite) TechPink else TechMuted,
                    )
                }
                IconButton(onClick = onDownload, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Download, contentDescription = "Descargar ${video.title}", tint = TechPink)
                }
            }
        }
    }
}

@Composable
private fun RockolaMessage(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = TechPanel.copy(alpha = .85f),
        border = BorderStroke(1.dp, TechBorder.copy(alpha = .45f)),
    ) {
        Text(message, modifier = Modifier.padding(16.dp), color = TechMuted, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun songMeta(video: VideoEntry): String {
    val artist = video.uploader?.takeIf { it.isNotBlank() }
    val duration =
        if (video.durationSeconds > 0) {
            "%d:%02d".format(video.durationSeconds / 60, video.durationSeconds % 60)
        } else {
            "YouTube"
        }
    val views =
        video.viewCount
            .takeIf { it > 0 }
            ?.let { " · ${compactCount(it)} vistas" }
            .orEmpty()
    return listOfNotNull(artist, duration).joinToString(" · ") + views
}

private fun compactCount(value: Long): String =
    when {
        value >= 1_000_000 -> "%.1f M".format(value / 1_000_000.0)
        value >= 1_000 -> "%.1f mil".format(value / 1_000.0)
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
