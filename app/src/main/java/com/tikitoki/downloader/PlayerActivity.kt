package com.tikitoki.downloader

import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.widget.Button
import android.view.View
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
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
        val label = TextView(this).apply {
            text = title; textSize = 18f; setTextColor(android.graphics.Color.WHITE)
            setPadding(24, 28, 24, 20)
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
                text = if (isFull) "Pantalla completa" else "Salir de pantalla completa"
            }
        }
        val controls = LinearLayout(this).apply { addView(speed, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); addView(rotate, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); addView(fullScreen, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.BLACK)
            addView(label); addView(playerView); addView(controls)
        })
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
                    if (controller.currentMediaItem?.localConfiguration?.uri != uri) {
                        val metadata = MediaMetadata.Builder().setTitle(title).build()
                        controller.setMediaItem(MediaItem.Builder().setUri(uri).setMediaMetadata(metadata).build())
                        controller.prepare()
                        val position = getSharedPreferences("playback_positions", MODE_PRIVATE).getLong(uri.toString(), 0L)
                        if (position > 0) controller.seekTo(position)
                        controller.play()
                    }
                } catch (_: Exception) {
                    Toast.makeText(this, "No se pudo abrir el reproductor.", Toast.LENGTH_LONG).show()
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onStop() {
        val player = activeController
        val key = currentUri
        if (player != null && key != null && player.currentMediaItem?.localConfiguration?.uri?.toString() == key) {
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
