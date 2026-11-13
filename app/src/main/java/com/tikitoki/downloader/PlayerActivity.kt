package com.tikitoki.downloader

import android.content.ComponentName
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.data
        if (uri == null || uri.scheme !in setOf("content", "file")) { finish(); return }
        val title = intent.getStringExtra("title").orEmpty().ifBlank { "Reproducción" }
        playerView = PlayerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            setBackgroundColor(android.graphics.Color.BLACK)
            useController = true
        }
        val label = TextView(this).apply {
            text = title; textSize = 18f; setTextColor(android.graphics.Color.WHITE)
            setPadding(24, 28, 24, 20)
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.BLACK)
            addView(label); addView(playerView)
        })
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            if (!isFinishing && !isDestroyed) {
                try {
                    val controller = future.get()
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
                        controller.play()
                    }
                } catch (_: Exception) {
                    Toast.makeText(this, "No se pudo abrir el reproductor.", Toast.LENGTH_LONG).show()
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        playerView.player = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onDestroy()
    }
}
