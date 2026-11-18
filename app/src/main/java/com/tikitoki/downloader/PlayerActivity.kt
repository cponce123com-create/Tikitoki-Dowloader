package com.tikitoki.downloader

import com.tikitoki.downloader.data.DiagnosticHistory
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.widget.Button
import android.view.View
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture

class PlayerActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private lateinit var playerView: PlayerView
    private var currentUri: String? = null
    private var activeController: MediaController? = null

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
                val player = activeController ?: return@setOnClickListener
                val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
                val index = speeds.indexOf(player.playbackParameters.speed).let { if (it < 0) 2 else it }
                val next = speeds[(index + 1) % speeds.size]
                player.setPlaybackSpeed(next)
                text = "Velocidad ${next}x"
            }
        }
        val rotate = Button(this).apply {
            text = "Girar pantalla"
            setOnClickListener {
                requestedOrientation = if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
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
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            if (!isFinishing && !isDestroyed) {
                try {
                    val controller = future.get()
                    activeController = controller
                    speed.text = "Velocidad ${controller.playbackParameters.speed}x"
                    playerView.player = controller
                    controller.addListener(object : Player.Listener {
                        override fun onPlayerError(error: PlaybackException) {
                            Toast.makeText(this@PlayerActivity, "No se pudo reproducir el archivo.", Toast.LENGTH_LONG).show()
                        }
                    })
                    if (controller.currentMediaItem?.mediaId != "video:$uri") {
                        val metadata = MediaMetadata.Builder().setTitle(title).build()
                        controller.setMediaItem(MediaItem.Builder().setMediaId("video:$uri").setUri(uri).setMediaMetadata(metadata).build())
                        controller.prepare()
                        val position = getSharedPreferences("playback_positions", MODE_PRIVATE).getLong(uri.toString(), 0L)
                        if (position > 0) controller.seekTo(position)
                        controller.play()
                    }
                } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; DiagnosticHistory.record(this@PlayerActivity, e.message);
                    Toast.makeText(this, "No se pudo abrir el reproductor.", Toast.LENGTH_LONG).show()
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onStop() {
        val player = activeController
        val key = currentUri
        if (player != null && key != null && player.currentMediaItem?.mediaId == "video:$key") {
            val remaining = if (player.duration > 0) player.duration - player.currentPosition else Long.MAX_VALUE
            val position = if (remaining < 3000) 0L else player.currentPosition.coerceAtLeast(0L)
            getSharedPreferences("playback_positions", MODE_PRIVATE).edit().putLong(key, position).apply()
        }
        super.onStop()
    }

    override fun onDestroy() {
        activeController = null
        playerView.player = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onDestroy()
    }
}


