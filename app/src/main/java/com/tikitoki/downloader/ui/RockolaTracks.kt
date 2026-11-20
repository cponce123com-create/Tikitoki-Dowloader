package com.tikitoki.downloader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tikitoki.downloader.VideoThumbnail
import com.tikitoki.downloader.data.VideoEntry
import com.tikitoki.downloader.data.VideoThumbnailResolver

@Suppress("ktlint:standard:function-naming")
@Composable
internal fun RockolaSearchResults(
    loading: Boolean,
    message: String,
    results: List<VideoEntry>,
    onRetry: () -> Unit,
    trackRow: @Composable (VideoEntry) -> Unit,
) {
    when {
        loading ->
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp), color = RockolaColors.Green, strokeWidth = 2.dp)
            }
        message.isNotBlank() -> {
            RockolaMessage(message)
            TextButton(onClick = onRetry) { Text("Reintentar", color = RockolaColors.Green) }
        }
        else ->
            results.forEach { track ->
                key(track.id) { trackRow(track) }
            }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
internal fun RockolaTrackRow(
    video: VideoEntry,
    resolver: VideoThumbnailResolver,
    onPlay: () -> Unit,
    actions: @Composable () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) {
        Row(
            Modifier
                .weight(1f)
                .clickable(role = Role.Button, onClickLabel = "Reproducir ${video.title}", onClick = onPlay)
                .padding(vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            VideoThumbnail(video, resolver, Modifier.size(width = 70.dp, height = 40.dp), deferLookup = true)
            Column(Modifier.weight(1f)) {
                Text(
                    video.title,
                    color = RockolaColors.Text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val duration = video.durationSeconds.takeIf { it > 0 }?.let(::formatRockolaDuration)
                Text(
                    listOfNotNull(video.uploader ?: "YouTube", duration).joinToString(" · "),
                    color = RockolaColors.Muted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        actions()
    }
    HorizontalDivider(color = RockolaColors.Edge)
}

@Suppress("ktlint:standard:function-naming")
@Composable
internal fun RockolaAddButton(
    track: VideoEntry,
    library: RockolaLibrary,
    selection: RockolaSelection?,
) {
    val queued = library.queue.any { it.id == track.id }
    val current = selection?.track?.id == track.id
    IconButton(onClick = { library.enqueue(track) }, enabled = !queued && !current) {
        Icon(
            if (queued || current) Icons.Default.Check else Icons.AutoMirrored.Filled.PlaylistAdd,
            contentDescription =
                when {
                    queued -> "En cola: ${track.title}"
                    current -> "Video actual: ${track.title}"
                    else -> "Añadir a la cola: ${track.title}"
                },
            tint = if (current) RockolaColors.Muted else RockolaColors.Green,
        )
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
internal fun RockolaMoreButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Default.MoreVert, contentDescription = "Más opciones", tint = RockolaColors.Muted)
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
internal fun RockolaLists(
    library: RockolaLibrary,
    openedList: String?,
    onOpen: (String?) -> Unit,
    trackRow: @Composable (VideoEntry) -> Unit,
) {
    // Private keys for the built-in lists avoid collisions with user-chosen names.
    val favorites = RockolaPlaylist("", library.favorites.asReversed())
    val history = RockolaPlaylist("", library.history)
    val lists =
        listOf("favorites" to favorites, "history" to history) + library.playlists.map { "custom:${it.name}" to it }
    if (openedList == null) {
        lists.forEach { (key, list) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .clickable { onOpen(key) }
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, tint = RockolaColors.Green)
                Column {
                    Text(
                        rockolaListName(key, list),
                        color = RockolaColors.Text,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                    )
                    Text("${list.tracks.size} videos", color = RockolaColors.Muted, fontSize = 11.sp)
                }
            }
            HorizontalDivider(color = RockolaColors.Edge)
        }
    } else {
        val list = lists.firstOrNull { it.first == openedList }?.second ?: return
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onOpen(null) }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver a Mis listas",
                    tint = RockolaColors.Green,
                )
            }
            Text(rockolaListName(openedList, list), color = RockolaColors.Text, fontWeight = FontWeight.SemiBold)
        }
        if (list.tracks.isEmpty()) RockolaMessage("Todavía no hay videos en esta lista.")
        list.tracks.forEach { track -> key(track.id) { trackRow(track) } }
    }
}

private fun rockolaListName(
    key: String,
    list: RockolaPlaylist,
): String =
    when (key) {
        "favorites" -> "Favoritos"
        "history" -> "Historial"
        else -> list.name
    }

@Suppress("ktlint:standard:function-naming")
@Composable
internal fun RockolaMessage(message: String) {
    Text(message, Modifier.padding(vertical = 12.dp), color = RockolaColors.Muted, fontSize = 13.sp)
}
