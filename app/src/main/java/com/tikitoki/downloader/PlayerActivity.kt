package com.tikitoki.downloader

import com.tikitoki.downloader.data.DiagnosticHistory
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Video player activity using local ExoPlayer for proper video rendering.
 * Uses its own player instance instead of the audio-only PlaybackService.
 */
@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {
    private lateinit var playerView: PlayerView
    private var player: ExoPlayer? = null
    private var currentUri: String? = null
    private var playbackJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.data
        if (uri == null || uri.scheme !in setOf("content", "file")) { finish(); return }
        val title = intent.getStringExtra("title").orEmpty().ifBlank { "Reproducción" }
        currentUri = uri.toString()

        playerView = PlayerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            setBackgroundColor(android.graphics.Color.BLACK)
            useController = true
            // Ensure video surface is used
            surfaceType = PlayerView.SURFACE_TYPE_SURFACE_VIEW
        }

        val mint = Color.rgb(67, 230, 161)
        fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
        fun panel(colors: IntArray, border: Int, radius: Int) = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors).apply {
            cornerRadius = dp(radius).toFloat(); setStroke(dp(1), border)
        }
        val label = TextView(this).apply {
            text = title; textSize = 16f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
            maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(16), dp(15), dp(16), dp(15))
            background = panel(intArrayOf(Color.rgb(15, 54, 39), Color.rgb(5, 27, 19)), Color.rgb(42, 115, 78), 12)
        }
        val speed = Button(this).apply {
            text = "Velocidad 1x"
            setOnClickListener {
                val p = player ?: return@setOnClickListener
                val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
                val index = speeds.indexOf(p.playbackParameters.speed).let { if (it < 0) 2 else it }
                val next = speeds[(index + 1) % speeds.size]
                p.setPlaybackSpeed(next)
                text = "Velocidad ${next}x"
            }
        }
        val rotate = Button(this).apply {
            text = "Girar pantalla"
            setOnClickListener {
                requestedOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        }
        val fullScreen = Button(this).apply {
            text = "Pantalla completa"
            setOnClickListener {
                val decor = window.decorView
                val isFull = decor.systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN != 0
                decor.systemUiVisibility = if (isFull) 0 else (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
                label.visibility = if (isFull) View.VISIBLE else View.GONE
                text = if (isFull) "Pantalla completa" else "Salir de pantalla completa"
            }
        }
        listOf(speed, rotate, fullScreen).forEach { button ->
            button.isAllCaps = false; button.textSize = 12f; button.setTextColor(mint)
            button.gravity = Gravity.CENTER
            button.background = panel(intArrayOf(Color.rgb(75, 91, 81), Color.rgb(32, 49, 40), Color.rgb(13, 28, 20)), Color.rgb(93, 132, 109), 10)
            button.setPadding(dp(3), dp(4), dp(3), dp(4))
        }
        val controls = LinearLayout(this).apply {
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = panel(intArrayOf(Color.rgb(49, 65, 56), Color.rgb(16, 29, 22)), Color.rgb(81, 115, 93), 14)
            listOf(speed, rotate, fullScreen).forEach { button ->
                addView(button, LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
            }
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundColor(Color.rgb(3, 21, 15))
            addView(label, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            addView(playerView)
            addView(controls, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safeArea = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(dp(10) + safeArea.left, dp(8) + safeArea.top,
                dp(10) + safeArea.right, dp(8) + safeArea.bottom)
            insets
        }
        setContentView(root)
        ViewCompat.requestApplyInsets(root)

        // Initialize ExoPlayer with video renderers
        val renderersFactory = DefaultRenderersFactory(this)
        player = ExoPlayer.Builder(this, renderersFactory)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build().apply {
                setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(), true)
                setHandleAudioBecomingNoisy(true)
            }

        playerView.player = player

        player?.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e("PlayerActivity", "Playback error", error)
                runOnUiThread { Toast.makeText(this@PlayerActivity, "No se pudo reproducir el archivo.", Toast.LENGTH_LONG).show() }
            }
        })

        // Prepare and play
        val mediaItem = MediaItem.Builder()
            .setMediaId("video:$uri")
            .setUri(uri)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
            .build()
        player?.setMediaItem(mediaItem)
        player?.prepare()

        // Restore position
        val position = getSharedPreferences("playback_positions", MODE_PRIVATE).getLong(uri.toString(), 0L)
        if (position > 0) {
            player?.seekTo(position)
        }
        player?.playWhenReady = true
    }

    override fun onStop() {
        super.onStop()
        val p = player
        val key = currentUri
        if (p != null && key != null) {
            val remaining = if (p.duration > 0) p.duration - p.currentPosition else Long.MAX_VALUE
            val position = if (remaining < 3000) 0L else p.currentPosition.coerceAtLeast(0L)
            getSharedPreferences("playback_positions", MODE_PRIVATE).edit().putLong(key, position).apply()
        }
    }

    override fun onDestroy() {
        playbackJob?.cancel()
        scope.coroutineContext.cancel()
        player?.run {
            playerView.player = null
            release()
        }
        player = null
        super.onDestroy()
    }
}


