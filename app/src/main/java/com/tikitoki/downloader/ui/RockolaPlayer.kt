package com.tikitoki.downloader.ui

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tikitoki.downloader.data.VideoEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

private const val VIDEO_HEIGHT_RATIO = 9f / 16f
private const val PLAYER_POLL_MS = 500L
private const val PLAYER_TIMEOUT_POLLS = 30
private const val YOUTUBE_PLAYING = 1
private const val YOUTUBE_PAUSED = 2
private const val YOUTUBE_ENDED = 0
private const val SECONDS_PER_MINUTE = 60

private val youtubeVideoIdPattern = Regex("[A-Za-z0-9_-]{11}")

internal fun VideoEntry.rockolaVideoId(): String? = id.removePrefix("youtube:").takeIf(youtubeVideoIdPattern::matches)

internal data class RockolaSelection(
    val track: VideoEntry,
    val videoId: String,
    val requestId: Int,
)

private data class PlayerReading(
    val requestId: Int,
    val ready: Boolean,
    val failed: Boolean,
    val phase: Int,
    val autoplayBlocked: Boolean,
    val position: Float,
    val duration: Float,
) {
    val isPlaying get() = phase == YOUTUBE_PLAYING
    val isEnded get() = phase == YOUTUBE_ENDED

    val label: String
        get() =
            when {
                isPlaying -> "En reproducción"
                isEnded -> "Terminó"
                phase == YOUTUBE_PAUSED || autoplayBlocked -> "En pausa"
                else -> "Cargando video…"
            }
}

@Suppress("ktlint:standard:function-naming", "LongMethod", "LongParameterList")
@Composable
internal fun RockolaPlayer(
    selection: RockolaSelection?,
    hasNext: Boolean,
    onNext: () -> Unit,
    onEnded: () -> Unit,
    onStarted: (VideoEntry) -> Unit,
    onRetry: () -> Unit,
    onOpenInYouTube: () -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var webView by remember { mutableStateOf<RockolaPlayerView?>(null) }
    var reading by remember(selection) { mutableStateOf<PlayerReading?>(null) }
    var timedOut by remember(selection) { mutableStateOf(false) }
    val latestEnded by rememberUpdatedState(onEnded)
    val latestStarted by rememberUpdatedState(onStarted)
    val ended = reading?.isEnded == true
    val failed = reading?.failed == true || timedOut

    RockolaPlayerLifecycle(lifecycle, webView)
    LaunchedEffect(webView, selection) {
        val view = webView ?: return@LaunchedEffect
        val target = selection ?: return@LaunchedEffect
        var pollsUntilReady = PLAYER_TIMEOUT_POLLS
        while (true) {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                view.read { current ->
                    if (current?.requestId == target.requestId) {
                        reading = current
                        if (current.ready) timedOut = false
                    }
                }
                if (reading?.ready != true && pollsUntilReady-- <= 0) timedOut = true
            }
            delay(PLAYER_POLL_MS)
        }
    }
    LaunchedEffect(selection) {
        if (selection == null) return@LaunchedEffect
        snapshotFlow { reading?.isPlaying == true }.first { it }
        latestStarted(selection.track)
    }
    LaunchedEffect(selection, ended) {
        if (ended) latestEnded()
    }

    Surface(
        color = RockolaColors.Surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, RockolaColors.Edge),
    ) {
        Column {
            RockolaVideoSurface(selection) { webView = it }
            RockolaPlayerControls(
                track = selection?.track,
                reading = reading,
                failed = failed,
                hasNext = hasNext,
                onToggle = { webView?.runCommand("rockolaToggle()") },
                onNext = onNext,
                onSeek = { webView?.runCommand("rockolaSeek($it)") },
            )
            if (failed) {
                Row(
                    Modifier.padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = onRetry) { Text("Reintentar", color = RockolaColors.Green) }
                    TextButton(onClick = onOpenInYouTube) { Text("Abrir en YouTube", color = RockolaColors.Green) }
                }
            }
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun RockolaVideoSurface(
    selection: RockolaSelection?,
    onViewChanged: (RockolaPlayerView?) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Keep the embedded player at its minimum height on compact phones.
        val videoHeight = (maxWidth * VIDEO_HEIGHT_RATIO).coerceAtLeast(200.dp)
        Box(
            modifier = Modifier.fillMaxWidth().height(videoHeight),
            contentAlignment = Alignment.Center,
        ) {
            if (selection == null) {
                RockolaPlayerPlaceholder()
            } else {
                AndroidView(
                    factory = { RockolaPlayerView(it).also(onViewChanged) },
                    modifier = Modifier.fillMaxSize(),
                    onRelease = { view ->
                        view.stopLoading()
                        view.destroy()
                        onViewChanged(null)
                    },
                    update = { view -> view.select(selection) },
                )
            }
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun RockolaPlayerLifecycle(
    lifecycle: Lifecycle,
    view: RockolaPlayerView?,
) {
    DisposableEffect(lifecycle, view) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> view?.setActive(true)
                    Lifecycle.Event.ON_PAUSE -> view?.setActive(false)
                    else -> Unit
                }
            }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun RockolaPlayerPlaceholder() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(RockolaColors.Panel, RockolaColors.Background))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.GraphicEq,
            contentDescription = null,
            tint = RockolaColors.Green,
            modifier = Modifier.size(48.dp),
        )
    }
}

@Suppress("ktlint:standard:function-naming", "LongParameterList")
@Composable
private fun RockolaPlayerControls(
    track: VideoEntry?,
    reading: PlayerReading?,
    failed: Boolean,
    hasNext: Boolean,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Float) -> Unit,
) {
    val playing = reading?.isPlaying == true
    Column(Modifier.padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = track?.title ?: "Elige un video",
                    color = RockolaColors.Text,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (track != null) {
                    val status = if (failed) "Video no disponible aquí" else reading?.label ?: "Cargando video…"
                    Text(
                        listOfNotNull(track.uploader, status).joinToString(" · "),
                        color = RockolaColors.Muted,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            val canPlay = reading?.ready == true && !failed
            IconButton(
                onClick = onToggle,
                enabled = canPlay,
                modifier = Modifier.background(if (canPlay) RockolaColors.Green else RockolaColors.Panel, CircleShape),
            ) {
                Icon(
                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playing) "Pausar" else "Reproducir",
                    tint = if (canPlay) RockolaColors.Ink else RockolaColors.Muted,
                )
            }
            IconButton(onClick = onNext, enabled = hasNext) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = "Siguiente",
                    tint = if (hasNext) RockolaColors.Text else RockolaColors.Muted,
                )
            }
        }

        if (track != null) {
            RockolaSeekBar(track, reading, onSeek)
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun RockolaSeekBar(
    track: VideoEntry,
    reading: PlayerReading?,
    onSeek: (Float) -> Unit,
) {
    var scrubPosition by remember(track.id) { mutableStateOf<Float?>(null) }
    val duration = reading?.duration?.takeIf { it > 0f } ?: track.durationSeconds.toFloat()
    val position = (scrubPosition ?: reading?.position ?: 0f).coerceIn(0f, duration.coerceAtLeast(0f))
    Slider(
        value = position,
        onValueChange = { scrubPosition = it },
        onValueChangeFinished = {
            scrubPosition?.let(onSeek)
            scrubPosition = null
        },
        enabled = reading?.ready == true && reading.duration > 0f && !reading.failed,
        valueRange = 0f..duration.coerceAtLeast(1f),
        colors =
            SliderDefaults.colors(
                thumbColor = RockolaColors.Green,
                activeTrackColor = RockolaColors.Green,
                inactiveTrackColor = RockolaColors.Edge,
            ),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatRockolaDuration(position.toInt()), color = RockolaColors.Muted, fontSize = 11.sp)
        Text(formatRockolaDuration(duration.toInt()), color = RockolaColors.Muted, fontSize = 11.sp)
    }
}

internal fun formatRockolaDuration(seconds: Int): String =
    "${seconds / SECONDS_PER_MINUTE}:${(seconds % SECONDS_PER_MINUTE).toString().padStart(2, '0')}"

@SuppressLint("SetJavaScriptEnabled")
private class RockolaPlayerView(
    context: Context,
) : WebView(context) {
    private var pageReady = false
    private var active = false
    private var selection: RockolaSelection? = null

    init {
        setBackgroundColor(android.graphics.Color.BLACK)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowContentAccess = false
        settings.allowFileAccess = false
        settings.mediaPlaybackRequiresUserGesture = false
        webViewClient =
            object : WebViewClient() {
                override fun onPageFinished(
                    view: WebView,
                    url: String,
                ) {
                    pageReady = true
                    runCommand("rockolaSetActive($active)")
                    selection?.let(::sendSelection)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    val uri = request.url
                    val host = uri.host.orEmpty()
                    return request.isForMainFrame ||
                        uri.scheme != "https" ||
                        !(host == "youtube.com" || host.endsWith(".youtube.com"))
                }
            }
        val html =
            context.assets
                .open("rockola-player.html")
                .bufferedReader()
                .use { it.readText() }
        loadDataWithBaseURL("https://${context.packageName}/", html, "text/html", "UTF-8", null)
    }

    fun select(target: RockolaSelection) {
        if (selection == target) return
        selection = target
        if (pageReady) sendSelection(target)
    }

    fun setActive(value: Boolean) {
        active = value
        if (value) onResume()
        runCommand("rockolaSetActive($value)")
        if (!value) onPause()
    }

    fun runCommand(script: String) {
        if (pageReady) evaluateJavascript("if (window.rockolaRead) { $script }", null)
    }

    fun read(onReading: (PlayerReading?) -> Unit) {
        if (pageReady) evaluateJavascript("window.rockolaRead && rockolaRead()") { onReading(decodeReading(it)) }
    }

    private fun sendSelection(target: RockolaSelection) {
        runCommand("rockolaLoad(${JSONObject.quote(target.videoId)}, ${target.requestId})")
    }
}

private fun decodeReading(value: String): PlayerReading? =
    try {
        val result = (JSONTokener(value).nextValue() as? String)?.let(::JSONObject)
        result?.let {
            PlayerReading(
                requestId = it.getInt("id"),
                ready = it.getBoolean("ready"),
                failed = it.getBoolean("failed"),
                phase = it.getInt("state"),
                autoplayBlocked = it.getBoolean("autoplayBlocked"),
                position = it.getDouble("time").toFloat(),
                duration = it.getDouble("duration").toFloat(),
            )
        }
    } catch (_: JSONException) {
        null
    }
