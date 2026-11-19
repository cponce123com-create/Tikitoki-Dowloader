package com.tikitoki.downloader.ui

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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tikitoki.downloader.VideoThumbnail
import com.tikitoki.downloader.data.UniversalMediaSource
import com.tikitoki.downloader.data.VideoEntry
import com.tikitoki.downloader.data.VideoThumbnailResolver
import kotlinx.coroutines.launch

private val rockolaGenres = listOf(
    "Salsa", "Cumbia", "Reguetón", "Rock", "Pop", "Bachata", "Criolla", "Huayno", "Electrónica",
)

@Composable
internal fun RockolaScreen(
    source: UniversalMediaSource,
    thumbnailResolver: VideoThumbnailResolver,
    onDownload: (VideoEntry) -> Unit,
    onOpenYouTube: (VideoEntry) -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var selectedGenre by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf(emptyList<VideoEntry>()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    fun search(term: String) {
        val clean = term.trim()
        if (clean.isBlank()) return
        query = clean
        keyboard?.hide()
        loading = true
        message = ""
        scope.launch {
            try {
                results = source.searchYouTube(clean, 30)
                if (results.isEmpty()) message = "No se encontraron resultados."
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                results = emptyList()
                message = "No se pudo buscar en YouTube. Comprueba tu conexión."
            } finally {
                loading = false
            }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Rockola", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
                Text("Busca música en YouTube y elige qué reproducir o descargar.", color = TechMuted)
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    rockolaGenres.take(4).forEach { genre ->
                        FilterChip(
                            selected = selectedGenre == genre,
                            onClick = {
                                selectedGenre = genre
                                search(genre)
                            },
                            label = { Text(genre, maxLines = 1) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    rockolaGenres.drop(4).forEach { genre ->
                        FilterChip(
                            selected = selectedGenre == genre,
                            onClick = {
                                selectedGenre = genre
                                search(genre)
                            },
                            label = { Text(genre, maxLines = 1) },
                        )
                    }
                }
            }
        }
        if (loading) {
            item {
                Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = TechPurple)
                }
            }
        }
        if (message.isNotBlank() && !loading) {
            item { Text(message, color = TechMuted, modifier = Modifier.padding(vertical = 12.dp)) }
        }
        items(results, key = { it.id }) { video ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VideoThumbnail(video, thumbnailResolver, Modifier.size(width = 128.dp, height = 74.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(video.title, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text(if (video.durationSeconds > 0) formatDuration(video.durationSeconds) else "YouTube", color = TechMuted, style = MaterialTheme.typography.labelSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = { onOpenYouTube(video) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.OpenInNew, contentDescription = "Abrir en YouTube", tint = TechPink)
                            }
                            IconButton(onClick = { onDownload(video) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Download, contentDescription = "Descargar", tint = TechPurple)
                            }
                        }
                    }
                }
            }
        }
        if (!loading && results.isEmpty() && message.isBlank()) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Elige un género o busca un artista para comenzar.", color = TechMuted)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { search("salsa música popular") }) { Text("Explorar música") }
                }
            }
        }
    }
}

private fun formatDuration(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)
