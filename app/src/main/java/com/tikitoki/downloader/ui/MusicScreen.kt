package com.tikitoki.downloader.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.tikitoki.downloader.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private data class MusicTrack(val uri: String, val title: String)
private val Mint = Color(0xFF43E6A1)
private val Muted = Color(0xFFADC8BC)
private val Metal = Brush.verticalGradient(listOf(Color(0xFF56645E), Color(0xFF222E2A), Color(0xFF111B18)))

@Composable
fun MusicScreen(requestedUri: String? = null, requestedTitle: String = "", onRequestConsumed: () -> Unit = {}) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("music_player", Context.MODE_PRIVATE) }
    val tracks = remember {
        mutableStateListOf<MusicTrack>().apply {
            runCatching {
                val a = JSONArray(prefs.getString("tracks", "[]"))
                for (i in 0 until a.length()) { val o = a.getJSONObject(i); add(MusicTrack(o.getString("uri"), o.getString("title"))) }
            }
        }
    }
    var favorites by remember { mutableStateOf(prefs.getStringSet("favorites", emptySet()).orEmpty().toSet()) }
    var favoritesOnly by remember { mutableStateOf(false) }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var currentUri by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("Selecciona una canción") }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var shuffle by remember { mutableStateOf(false) }
    var repeat by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    var seeking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var volume by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    val maxVolume = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1).toFloat() }
    fun save() {
        val a = JSONArray(); tracks.forEach { a.put(JSONObject().put("uri", it.uri).put("title", it.title)) }
        prefs.edit().putString("tracks", a.toString()).putStringSet("favorites", favorites).apply()
    }
    fun item(track: MusicTrack) = MediaItem.Builder().setUri(track.uri)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).build()).build()
    fun start(index: Int) {
        val player = controller
        if (player == null) { message = "Conectando el reproductor…"; return }
        try { player.setMediaItems(tracks.map(::item), index, 0L); player.prepare(); player.play(); message = "" }
        catch (_: Exception) { message = "No se pudo abrir el audio. Comprueba que el archivo esté disponible." }
    }
    LaunchedEffect(requestedUri, controller) {
        val uri = requestedUri ?: return@LaunchedEffect
        if (Uri.parse(uri).scheme !in setOf("content", "file")) {
            message = "No se pudo abrir la ubicación del audio."
            onRequestConsumed()
            return@LaunchedEffect
        }
        if (tracks.none { it.uri == uri }) {
            tracks.add(MusicTrack(uri, requestedTitle.ifBlank { "Canción" }))
            save()
        }
        if (controller != null) {
            val index = tracks.indexOfFirst { it.uri == uri }
            if (index >= 0) start(index)
            onRequestConsumed()
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val existing = tracks.map { it.uri }.toSet()
        val added = mutableListOf<MusicTrack>()
        uris.forEach { uri ->
            if (tracks.none { it.uri == uri.toString() }) {
                try {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    val name = runCatching { context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } }.getOrNull() ?: "Canción"
                    val track = MusicTrack(uri.toString(), name); tracks.add(track); added.add(track)
                } catch (_: SecurityException) { message = "No se pudo guardar el acceso a una canción." }
            }
        }
        controller?.let { p -> if (p.currentMediaItem?.localConfiguration?.uri?.toString() in existing) p.addMediaItems(added.map(::item)) }
        save()
    }
    DisposableEffect(context) {
        var disposed = false
        val future = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
        future.addListener({ if (!disposed) {
            try { controller = future.get().also { p -> p.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) { message = "No se pudo reproducir el audio. Comprueba que el archivo siga disponible." }
            }) } } catch (_: Exception) { message = "No se pudo conectar el reproductor." }
        } }, ContextCompat.getMainExecutor(context))
        onDispose { disposed = true; controller = null; MediaController.releaseFuture(future) }
    }
    LaunchedEffect(controller) {
        while (true) {
            controller?.let { p ->
                currentUri = p.currentMediaItem?.localConfiguration?.uri?.toString()
                title = p.mediaMetadata.title?.toString() ?: "Selecciona una canción"
                if (!seeking) position = p.currentPosition.coerceAtLeast(0L)
                duration = p.duration.coerceAtLeast(0L)
                playing = p.isPlaying; shuffle = p.shuffleModeEnabled; repeat = p.repeatMode
            }
            delay(500)
        }
    }
    val artwork by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, currentUri) {
        val uri = currentUri ?: return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching { MediaMetadataRetriever().let { r -> try { r.setDataSource(context, Uri.parse(uri)); r.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } } finally { r.release() } } }.getOrNull()
        }
    }
    fun clock(ms: Long) = "%02d:%02d".format(ms / 60000, ms / 1000 % 60)
    val shape = RoundedCornerShape(18.dp)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text("Mi música", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = Color.White)
        Column(Modifier.fillMaxWidth().background(Metal, shape).border(1.dp, Color(0xFF798D83), shape).padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth().background(Color(0xFF041C13), RoundedCornerShape(12.dp)).border(1.dp, Color(0xFF13784D), RoundedCornerShape(12.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(68.dp).background(Color(0xFF164D38), RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
                    if (artwork != null) Image(artwork!!, contentDescription = "Carátula", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Icon(Icons.Default.MusicNote, contentDescription = null, tint = Mint, modifier = Modifier.size(34.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("Música local", color = Muted, fontSize = 12.sp)
                    Text("${clock(position)} / ${clock(duration)}", color = Mint, fontSize = 13.sp)
                }
            }
            Slider(value = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                onValueChange = { seeking = true; position = (it * duration).toLong() }, onValueChangeFinished = { if (duration > 0) controller?.seekTo(position); seeking = false },
                enabled = duration > 0, colors = SliderDefaults.colors(thumbColor = Mint, activeTrackColor = Mint, inactiveTrackColor = Color(0xFF112C20)))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                DeckButton(Icons.Default.Shuffle, "Aleatorio", shuffle, Modifier.weight(1f)) { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled; shuffle = it.shuffleModeEnabled } }
                DeckButton(Icons.Default.SkipPrevious, "Anterior", false, Modifier.weight(1f)) { controller?.seekToPreviousMediaItem() }
                DeckButton(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "Pausar" else "Reproducir", true, Modifier.weight(1.2f)) {
                    val p = controller
                    when {
                        p == null -> message = "Conectando el reproductor…"
                        p.currentMediaItem?.localConfiguration?.uri?.toString()?.let { uri -> tracks.any { it.uri == uri } } != true -> if (tracks.isNotEmpty()) start(0) else { message = "Agrega canciones para comenzar."; picker.launch(arrayOf("audio/*")) }
                        p.isPlaying -> p.pause()
                        else -> p.play()
                    }
                }
                DeckButton(Icons.Default.SkipNext, "Siguiente", false, Modifier.weight(1f)) { controller?.seekToNextMediaItem() }
                DeckButton(if (repeat == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat, "Repetir", repeat != Player.REPEAT_MODE_OFF, Modifier.weight(1f)) {
                    controller?.let { p -> p.repeatMode = when (p.repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF }; repeat = p.repeatMode }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.VolumeDown, contentDescription = "Volumen", tint = Muted)
                Slider(value = volume.coerceIn(0f, maxVolume), onValueChange = { volume = it; audio.setStreamVolume(AudioManager.STREAM_MUSIC, it.toInt(), 0) }, valueRange = 0f..maxVolume, modifier = Modifier.weight(1f), colors = SliderDefaults.colors(thumbColor = Mint, activeTrackColor = Mint))
                Icon(Icons.Default.VolumeUp, contentDescription = null, tint = Muted)
            }
        }
        Column(Modifier.fillMaxWidth().background(Color(0xFF0B1D17), RoundedCornerShape(14.dp)).border(1.dp, Color(0xFF526B5E), RoundedCornerShape(14.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.QueueMusic, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp)); Text("Lista de reproducción", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { picker.launch(arrayOf("audio/*")) }, contentPadding = PaddingValues(horizontal = 10.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Mint)) { Text("+ Agregar", color = Mint, fontSize = 12.sp) }
            }
            TextButton(onClick = { favoritesOnly = !favoritesOnly }) { Text(if (favoritesOnly) "Ver todas" else "Ver favoritos", color = Mint) }
            val visible = tracks.filter { !favoritesOnly || it.uri in favorites }
            if (visible.isEmpty()) Text(if (favoritesOnly) "Aún no tienes favoritos." else "Agrega canciones para empezar.", color = Muted, modifier = Modifier.padding(8.dp))
            visible.forEach { track ->
                val selected = currentUri == track.uri
                Row(Modifier.fillMaxWidth().background(if (selected) Color(0xFF064B31) else Color.Transparent, RoundedCornerShape(8.dp)).clickable { start(tracks.indexOf(track)) }.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).background(Color(0xFF16563C), RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.MusicNote, contentDescription = null, tint = Mint) }
                    Spacer(Modifier.width(10.dp))
                    Text(track.title, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    IconButton(onClick = { favorites = if (track.uri in favorites) favorites - track.uri else favorites + track.uri; save() }) { Icon(if (track.uri in favorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "Favorito", tint = Mint) }
                    var menu by remember(track.uri) { mutableStateOf(false) }
                    Box { IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Opciones de canción", tint = Color.White) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Quitar de la lista") }, onClick = {
                                menu = false; tracks.remove(track)
                                controller?.let { p -> for (i in p.mediaItemCount - 1 downTo 0) if (p.getMediaItemAt(i).localConfiguration?.uri?.toString() == track.uri) p.removeMediaItem(i) }
                                save()
                            })
                        }
                    }
                }
            }
        }
        if (message.isNotBlank()) Text(message, color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DeckButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(modifier.height(52.dp).background(if (active) Brush.verticalGradient(listOf(Color(0xFF168D59), Color(0xFF073A24))) else Metal, shape)
        .border(1.dp, if (active) Mint else Color(0xFF60766B), shape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = label, tint = if (active) Mint else Color.White, modifier = Modifier.size(26.dp))
    }
}
