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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material.Slider as ClassicSlider
import androidx.compose.material.SliderDefaults as ClassicSliderDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
import com.tikitoki.downloader.audio.PlaybackSpectrum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject

private data class MusicTrack(val uri: String, val title: String)
private val Mint = Color(0xFF43E6A1)
private val Muted = Color(0xFFADC8BC)
private val Metal = Brush.verticalGradient(listOf(Color(0xFF7C8982), Color(0xFF3D4A44), Color(0xFF1A2922), Color(0xFF101A16)))
private val artworkSlots = Semaphore(3)
private data class ArtworkInfo(val bitmap: androidx.compose.ui.graphics.ImageBitmap?, val artist: String?)

private suspend fun readArtwork(context: Context, uri: String): ArtworkInfo = withContext(Dispatchers.IO) {
    artworkSlots.withPermit {
        runCatching {
            MediaMetadataRetriever().let { r ->
                try {
                    r.setDataSource(context, Uri.parse(uri))
                    val bytes = r.embeddedPicture?.takeIf { it.size <= 5 * 1024 * 1024 }
                    val bitmap = bytes?.let {
                        val options = BitmapFactory.Options().apply { inSampleSize = 4 }
                        BitmapFactory.decodeByteArray(it, 0, it.size, options)?.asImageBitmap()
                    }
                    val artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                        ?.filterNot { it.isISOControl() }?.trim()?.take(100)?.takeIf { it.isNotBlank() }
                    ArtworkInfo(bitmap, artist)
                } finally { r.release() }
            }
        }.getOrElse { ArtworkInfo(null, null) }
    }
}

@Composable
fun MusicScreen(requestedUri: String? = null, requestedTitle: String = "", onRequestConsumed: () -> Unit = {}) {
    val context = LocalContext.current
    val compact = LocalConfiguration.current.screenWidthDp < 360
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
    val spectrum by PlaybackSpectrum.levels.collectAsState()
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
                val uri = p.currentMediaItem?.localConfiguration?.uri?.toString()
                val isMusic = uri != null && tracks.any { it.uri == uri }
                currentUri = uri.takeIf { isMusic }
                title = if (isMusic) p.mediaMetadata.title?.toString() ?: "Canción" else "Selecciona una canción"
                if (!seeking) position = if (isMusic) p.currentPosition.coerceAtLeast(0L) else 0L
                duration = if (isMusic) p.duration.coerceAtLeast(0L) else 0L
                playing = isMusic && p.isPlaying; shuffle = p.shuffleModeEnabled; repeat = p.repeatMode
            }
            delay(500)
        }
    }
    val artwork by produceState(ArtworkInfo(null, null), currentUri) {
        value = ArtworkInfo(null, null)
        val uri = currentUri ?: return@produceState
        value = readArtwork(context, uri)
    }
    fun clock(ms: Long) = "%02d:%02d".format(ms / 60000, ms / 1000 % 60)
    val shape = RoundedCornerShape(18.dp)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text("Mi música", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = Color.White)
        Column(Modifier.fillMaxWidth().background(Metal, shape).border(1.dp, Color(0xFF798D83), shape).padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth().background(Color(0xFF041C13), RoundedCornerShape(12.dp)).border(1.dp, Color(0xFF13784D), RoundedCornerShape(12.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(68.dp).background(Color(0xFF164D38), RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
                    if (artwork.bitmap != null) Image(artwork.bitmap!!, contentDescription = "Carátula", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Icon(Icons.Default.MusicNote, contentDescription = null, tint = Mint, modifier = Modifier.size(34.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(artwork.artist ?: "Música local", color = Muted, fontSize = 12.sp, maxLines = 1)
                    Text("${clock(position)} / ${clock(duration)}", color = Mint, fontSize = 13.sp)
                }
                Canvas(Modifier.width(if (compact) 58.dp else 76.dp).height(44.dp)
                    .semantics { contentDescription = "Visualizador de audio" }) {
                    val gap = 2.dp.toPx()
                    val barWidth = ((size.width - gap * (spectrum.size - 1)) / spectrum.size).coerceAtLeast(1f)
                    spectrum.forEachIndexed { index, level ->
                        val height = if (playing) (level.coerceIn(0f, 1f) * size.height) else 0f
                        if (height > 0f) drawRect(
                            color = Mint,
                            topLeft = Offset(index * (barWidth + gap), size.height - height),
                            size = Size(barWidth, height),
                        )
                    }
                }
            }
            ClassicSlider(value = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                onValueChange = { seeking = true; position = (it * duration).toLong() }, onValueChangeFinished = { if (duration > 0) controller?.seekTo(position); seeking = false },
                enabled = duration > 0, colors = ClassicSliderDefaults.colors(thumbColor = Mint, activeTrackColor = Mint, inactiveTrackColor = Color(0xFF112C20)))
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
                ClassicSlider(value = volume.coerceIn(0f, maxVolume), onValueChange = { volume = it; audio.setStreamVolume(AudioManager.STREAM_MUSIC, it.toInt(), 0) }, valueRange = 0f..maxVolume, modifier = Modifier.weight(1f), colors = ClassicSliderDefaults.colors(thumbColor = Mint, activeTrackColor = Mint, inactiveTrackColor = Color(0xFF112C20)))
                Icon(Icons.Default.VolumeUp, contentDescription = null, tint = Muted)
            }
        }
        Column(Modifier.fillMaxWidth().background(Color(0xFF0B1D17), RoundedCornerShape(14.dp)).border(1.dp, Color(0xFF526B5E), RoundedCornerShape(14.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (!compact) {
                    Icon(Icons.Default.QueueMusic, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(6.dp))
                }
                Text("Lista de reproducción", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                IconButton(onClick = { favoritesOnly = !favoritesOnly }, modifier = Modifier.size(40.dp)) {
                    Icon(if (favoritesOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (favoritesOnly) "Ver todas" else "Ver favoritos", tint = Mint)
                }
                OutlinedButton(onClick = { picker.launch(arrayOf("audio/*")) }, contentPadding = PaddingValues(horizontal = 6.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Mint)) { Text("+ Agregar", color = Mint, fontSize = 12.sp, maxLines = 1) }
            }
            val visible = tracks.filter { !favoritesOnly || it.uri in favorites }
            if (visible.isEmpty()) Text(if (favoritesOnly) "Aún no tienes favoritos." else "Agrega canciones para empezar.", color = Muted, modifier = Modifier.padding(8.dp))
            visible.forEach { track ->
                val selected = currentUri == track.uri
                val trackArtwork by produceState(ArtworkInfo(null, null), track.uri) {
                    value = ArtworkInfo(null, null)
                    value = readArtwork(context, track.uri)
                }
                Row(Modifier.fillMaxWidth().background(if (selected) Color(0xFF064B31) else Color.Transparent, RoundedCornerShape(8.dp)).clickable { start(tracks.indexOf(track)) }.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).background(Color(0xFF16563C), RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) {
                        if (trackArtwork.bitmap != null) Image(trackArtwork.bitmap!!, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Icon(Icons.Default.MusicNote, contentDescription = null, tint = Mint)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(track.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        trackArtwork.artist?.let { Text(it, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                    var menu by remember(track.uri) { mutableStateOf(false) }
                    Box { IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Opciones de canción", tint = Color.White) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(if (track.uri in favorites) "Quitar de favoritos" else "Añadir a favoritos") }, onClick = {
                                menu = false; favorites = if (track.uri in favorites) favorites - track.uri else favorites + track.uri; save()
                            })
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

