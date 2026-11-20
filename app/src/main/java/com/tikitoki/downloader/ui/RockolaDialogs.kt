package com.tikitoki.downloader.ui

import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tikitoki.downloader.data.VideoEntry

private const val DOWNLOAD_CONSENT = "authorized_downloads_ack"
private const val PLAYLIST_NAME_LIMIT = 60

internal sealed interface RockolaPopup {
    data class TrackActions(
        val track: VideoEntry,
    ) : RockolaPopup

    data class SaveToPlaylist(
        val track: VideoEntry,
    ) : RockolaPopup

    data class DownloadConsent(
        val track: VideoEntry,
    ) : RockolaPopup
}

@Suppress("ktlint:standard:function-naming", "LongParameterList")
@Composable
internal fun RockolaDialogs(
    popup: RockolaPopup?,
    preferences: SharedPreferences,
    library: RockolaLibrary,
    onPopup: (RockolaPopup?) -> Unit,
    onOpenYouTube: (VideoEntry) -> Unit,
    onDownload: (VideoEntry) -> Unit,
) {
    val dismiss = { onPopup(null) }
    when (popup) {
        is RockolaPopup.TrackActions ->
            TrackActionsDialog(
                title = popup.track.title,
                favorite = popup.track.id in library.favoriteIds,
                onDismiss = dismiss,
                onFavorite = {
                    library.toggleFavorite(popup.track)
                    dismiss()
                },
                onSave = { onPopup(RockolaPopup.SaveToPlaylist(popup.track)) },
                onOpenYouTube = {
                    dismiss()
                    onOpenYouTube(popup.track)
                },
                onDownload = {
                    if (preferences.getBoolean(DOWNLOAD_CONSENT, false)) {
                        dismiss()
                        onDownload(popup.track)
                    } else {
                        onPopup(RockolaPopup.DownloadConsent(popup.track))
                    }
                },
            )
        is RockolaPopup.SaveToPlaylist ->
            SaveToPlaylistDialog(
                names = library.playlists.map { it.name },
                onDismiss = dismiss,
                onSave = {
                    library.saveToPlaylist(it, popup.track)
                    dismiss()
                },
            )
        is RockolaPopup.DownloadConsent ->
            DownloadConsentDialog(
                onDismiss = dismiss,
                onDownload = {
                    preferences.edit().putBoolean(DOWNLOAD_CONSENT, true).apply()
                    dismiss()
                    onDownload(popup.track)
                },
            )
        null -> Unit
    }
}

@Suppress("ktlint:standard:function-naming", "LongParameterList")
@Composable
private fun TrackActionsDialog(
    title: String,
    favorite: Boolean,
    onDismiss: () -> Unit,
    onFavorite: () -> Unit,
    onSave: () -> Unit,
    onOpenYouTube: () -> Unit,
    onDownload: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                RockolaDialogOption(if (favorite) "Quitar de Favoritos" else "Añadir a Favoritos", onFavorite)
                RockolaDialogOption("Guardar en una lista", onSave)
                RockolaDialogOption("Abrir en YouTube", onOpenYouTube)
                RockolaDialogOption("Descargar", onDownload)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
        containerColor = RockolaColors.Surface,
        titleContentColor = RockolaColors.Text,
        textContentColor = RockolaColors.Text,
    )
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun SaveToPlaylistDialog(
    names: List<String>,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Guardar en una lista") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (names.isNotEmpty()) {
                    Column(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
                        names.forEach { label -> RockolaDialogOption(label) { onSave(label) } }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(PLAYLIST_NAME_LIMIT) },
                    label = { Text("Nombre de la lista nueva") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        containerColor = RockolaColors.Surface,
        titleContentColor = RockolaColors.Text,
        textContentColor = RockolaColors.Text,
    )
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun DownloadConsentDialog(
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
) {
    var agreed by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Descarga autorizada") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Descarga solo contenido propio, de dominio público o para el que tengas autorización. " +
                        "La aplicación no puede verificar los derechos de cada video de YouTube.",
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = agreed, onCheckedChange = { agreed = it })
                    Text("Confirmo que tengo permiso para descargarlo.")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDownload, enabled = agreed) { Text("Continuar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun RockolaDialogOption(
    label: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = RockolaColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
