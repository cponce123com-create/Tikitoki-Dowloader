package com.tikitoki.downloader

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.delay

@Composable
fun MiniPlayer(musicScreenOpen: Boolean, onMusic: () -> Unit) {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var title by remember { mutableStateOf("") }
    var uri by remember { mutableStateOf("") }
    var video by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    DisposableEffect(context) {
        var disposed = false
        val future = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
        future.addListener({ if (!disposed) controller = runCatching { future.get() }.getOrNull() }, ContextCompat.getMainExecutor(context))
        onDispose { disposed = true; controller = null; MediaController.releaseFuture(future) }
    }
    LaunchedEffect(controller) {
        while (true) {
            controller?.let { p ->
                val item = p.currentMediaItem
                val id = item?.mediaId.orEmpty()
                video = id.startsWith("video:")
                uri = if (video) id.removePrefix("video:") else id
                title = item?.mediaMetadata?.title?.toString().orEmpty()
                playing = p.isPlaying
                progress = if (p.duration > 0) (p.currentPosition.toFloat() / p.duration).coerceIn(0f, 1f) else 0f
            }
            delay(500)
        }
    }
    if ((!musicScreenOpen || video) && uri.isNotBlank() && (video || uri.startsWith("content:") || uri.startsWith("file:"))) {
        val mint = Color(0xFF43E6A1)
        val shape = RoundedCornerShape(14.dp)
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp).background(Color(0xFF102A20), shape).border(1.dp, Color(0xFF43866A), shape)) {
            Row(Modifier.fillMaxWidth().clickable {
                if (video) context.startActivity(Intent(context, PlayerActivity::class.java).apply {
                    data = Uri.parse(uri); putExtra("title", title); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }) else onMusic()
            }.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (video) Icons.Default.Videocam else Icons.Default.MusicNote, null, tint = mint)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(title.ifBlank { if (video) "Video" else "Música" }, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(if (video) "Video · Toca para abrir" else "Música · Toca para abrir", color = Color(0xFFADC8BC), fontSize = 11.sp)
                }
                IconButton(onClick = { controller?.let { if (it.isPlaying) it.pause() else it.play() } }) {
                    Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "Pausar" else "Reproducir", tint = mint)
                }
                IconButton(onClick = { controller?.stop(); controller?.clearMediaItems() }) {
                    Icon(Icons.Default.Close, "Cerrar reproductor", tint = Color.White)
                }
            }
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(2.dp), color = mint, trackColor = Color(0xFF24483A))
        }
    }
}
