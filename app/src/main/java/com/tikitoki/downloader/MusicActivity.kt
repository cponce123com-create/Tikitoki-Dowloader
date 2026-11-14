package com.tikitoki.downloader

import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.widget.ImageView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.net.Uri
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import org.json.JSONArray
import org.json.JSONObject

/** Local music queue; URIs are granted by Android's document picker. */
class MusicActivity : ComponentActivity() {
    private data class Track(val uri: String, val title: String)
    private val tracks = mutableListOf<Track>()
    private val favorites = mutableSetOf<String>()
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var trackTitle: TextView
    private lateinit var artwork: ImageView
    private var artworkUri: String? = null
    private lateinit var time: TextView
    private lateinit var seek: SeekBar
    private lateinit var play: Button
    private lateinit var favorite: Button
    private lateinit var shuffle: Button
    private lateinit var repeat: Button
    private lateinit var list: LinearLayout
    private var seeking = false
    private var favoritesOnly = false
    private val prefs by lazy { getSharedPreferences("music_player", MODE_PRIVATE) }
    private val picker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val existing = tracks.map { it.uri }.toSet()
        val added = mutableListOf<Track>()
        uris.forEach { uri ->
            if (tracks.none { it.uri == uri.toString() }) {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    val track = Track(uri.toString(), displayName(uri))
                    tracks.add(track)
                    added.add(track)
                } catch (_: SecurityException) {
                    Toast.makeText(this, "No se pudo guardar el acceso a una canción.", Toast.LENGTH_LONG).show()
                }
            }
        }
        controller?.let { player ->
            if (player.currentMediaItem?.localConfiguration?.uri?.toString() in existing) {
                player.addMediaItems(added.map(::mediaItem))
            }
        }
        save(); renderList()
    }
    private val refresh = object : Runnable {
        override fun run() { updateUi(); handler.postDelayed(this, 1000) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        load()
        val dp = resources.displayMetrics.density
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding((16*dp).toInt(), (16*dp).toInt(), (16*dp).toInt(), (12*dp).toInt()); setBackgroundColor(Color.rgb(3, 25, 20)) }
        fun label(size: Float) = TextView(this).apply { textSize = size; setTextColor(Color.WHITE) }
        fun button(text: String, action: () -> Unit) = Button(this).apply {
            this.text = text; isAllCaps = false; setTextColor(Color.WHITE)
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.rgb(37, 56, 52), Color.rgb(9, 31, 26))).apply { cornerRadius = 18f; setStroke(2, Color.rgb(28, 158, 112)) }
            setOnClickListener { action() }
        }
        root.addView(label(22f).apply { text = "Baja.pe"; setTextColor(Color.rgb(180, 249, 220)) })
        root.addView(label(26f).apply { text = "Mi música"; setPadding(0, (8*dp).toInt(), 0, (8*dp).toInt()) })
        val playerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((14*dp).toInt(), (14*dp).toInt(), (14*dp).toInt(), (14*dp).toInt())
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.rgb(26, 48, 42), Color.rgb(5, 31, 24))).apply {
                cornerRadius = 22*dp
                setStroke((1*dp).toInt().coerceAtLeast(1), Color.rgb(66, 122, 101))
            }
        }
        root.addView(playerCard)
        artwork = ImageView(this).apply {
            setBackgroundColor(Color.rgb(0, 70, 49))
            setImageResource(android.R.drawable.ic_media_play)
            contentDescription = "Carátula de la canción"
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        val nowPlaying = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        nowPlaying.addView(artwork, LinearLayout.LayoutParams((76*dp).toInt(), (76*dp).toInt()))
        val nowText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding((12*dp).toInt(), 0, 0, 0) }
        trackTitle = label(19f).apply { text = "Selecciona una canción"; maxLines = 2 }; nowText.addView(trackTitle)
        time = label(14f).apply { text = "00:00 / 00:00"; setTextColor(Color.rgb(92, 232, 172)) }; nowText.addView(time)
        nowPlaying.addView(nowText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        playerCard.addView(nowPlaying)
        seek = SeekBar(this).apply {
            max = 1000
            progressTintList = ColorStateList.valueOf(Color.rgb(40, 231, 157))
            thumbTintList = ColorStateList.valueOf(Color.rgb(40, 231, 157))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(bar: SeekBar?) { seeking = true }
                override fun onStopTrackingTouch(bar: SeekBar?) { val p = controller; if (p != null && p.duration > 0) p.seekTo(p.duration * progress / 1000); seeking = false }
                override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) { }
            })
        }; playerCard.addView(seek)
        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL }
        controls.addView(button("⏮") { controller?.seekToPreviousMediaItem() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        play = button("▶") { controller?.let { p -> if (p.currentMediaItem?.localConfiguration?.uri?.toString()?.let { uri -> tracks.any { it.uri == uri } } != true) { if (tracks.isNotEmpty()) startAt(0) else Toast.makeText(this, "Agrega una canción para reproducir.", Toast.LENGTH_SHORT).show() } else if (p.isPlaying) p.pause() else if (p.mediaItemCount > 0) p.play() else if (tracks.isNotEmpty()) startAt(0) else Toast.makeText(this, "Agrega una canción para reproducir.", Toast.LENGTH_SHORT).show() } }
        play.contentDescription = "Reproducir o pausar"
        controls.addView(play, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        controls.addView(button("⏭") { controller?.seekToNextMediaItem() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        playerCard.addView(controls)
        val modes = LinearLayout(this).apply { gravity = Gravity.CENTER }
        shuffle = button("Aleatorio") { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }; updateUi() }; modes.addView(shuffle)
        repeat = button("Repetir") { controller?.let { it.repeatMode = when (it.repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF } }; updateUi() }; modes.addView(repeat)
        favorite = button("♡") { val uri = controller?.currentMediaItem?.localConfiguration?.uri?.toString() ?: return@button; if (!favorites.add(uri)) favorites.remove(uri); save(); updateUi(); renderList() }; modes.addView(favorite)
        playerCard.addView(modes)
        playerCard.addView(label(14f).apply { text = "Volumen" })
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        playerCard.addView(SeekBar(this).apply {
            progressTintList = ColorStateList.valueOf(Color.rgb(40, 231, 157))
            thumbTintList = ColorStateList.valueOf(Color.rgb(40, 231, 157))
            max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            progress = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
                override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) { if (fromUser) audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0) }
            })
        })
        val listHeader = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        listHeader.addView(label(20f).apply { text = "Lista de reproducción" }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        listHeader.addView(button("+ Agregar") { picker.launch(arrayOf("audio/*")) })
        root.addView(listHeader)
        root.addView(button("Mostrar favoritos") { favoritesOnly = !favoritesOnly; renderList() })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        renderList()
        val pending = MediaController.Builder(this, SessionToken(this, ComponentName(this, PlaybackService::class.java))).buildAsync()
        future = pending
        pending.addListener({ if (!isDestroyed && !isFinishing) try {
            controller = pending.get().also { player ->
                player.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        Toast.makeText(this@MusicActivity, "No se pudo reproducir el audio. Comprueba que el archivo siga disponible.", Toast.LENGTH_LONG).show()
                    }
                })
            }
            updateUi()
        } catch (_: Exception) { Toast.makeText(this, "No se pudo conectar el reproductor.", Toast.LENGTH_LONG).show() } }, ContextCompat.getMainExecutor(this))
        handler.post(refresh)
    }
    private fun displayName(uri: Uri): String = try {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "Canción"
    } catch (_: Exception) { "Canción" }
    private fun renderList() {
        list.removeAllViews()
        tracks.forEachIndexed { index, track ->
            if (favoritesOnly && track.uri !in favorites) return@forEachIndexed
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply { setColor(Color.rgb(10, 43, 32)); cornerRadius = 12 * resources.displayMetrics.density }
            }
            row.addView(Button(this).apply {
                text = track.title; isAllCaps = false; setTextColor(Color.WHITE); setBackgroundColor(Color.TRANSPARENT)
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                maxLines = 1
                setOnClickListener { startAt(index) }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(Button(this).apply {
                text = "Quitar"; isAllCaps = false; setTextColor(Color.rgb(132, 235, 188)); setBackgroundColor(Color.TRANSPARENT)
                setOnClickListener { val removed = tracks.removeAt(index); controller?.let { p -> for (i in p.mediaItemCount - 1 downTo 0) if (p.getMediaItemAt(i).localConfiguration?.uri?.toString() == removed.uri) p.removeMediaItem(i) }; save(); renderList() }
            })
            list.addView(row)
        }
    }
    private fun startAt(index: Int) {
        val player = controller ?: return
        val items = tracks.map(::mediaItem)
        try { player.setMediaItems(items, index, 0L); player.prepare(); player.play() }
        catch (_: Exception) { Toast.makeText(this, "No se pudo abrir el audio.", Toast.LENGTH_LONG).show() }
    }
    private fun mediaItem(track: Track): MediaItem = MediaItem.Builder().setUri(track.uri)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).build()).build()
    private fun updateUi() {
        val player = controller ?: return
        trackTitle.text = player.mediaMetadata.title?.toString() ?: "Selecciona una canción"
        val current = player.currentMediaItem?.localConfiguration?.uri?.toString()
        if (current != artworkUri) {
            artworkUri = current
            artwork.setImageResource(android.R.drawable.ic_media_play)
            if (current != null) lifecycleScope.launch {
                val bytes = withContext(Dispatchers.IO) {
                    try { MediaMetadataRetriever().let { retriever -> try { retriever.setDataSource(this@MusicActivity, Uri.parse(current)); retriever.embeddedPicture } finally { retriever.release() } } } catch (_: Exception) { null }
                }
                if (current == artworkUri && bytes != null) artwork.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
            }
        }
        val duration = player.duration.coerceAtLeast(0L)
        fun clock(ms: Long): String = "%02d:%02d".format(ms / 60000, ms / 1000 % 60)
        time.text = "${clock(player.currentPosition.coerceAtLeast(0L))} / ${clock(duration)}"
        if (!seeking) seek.progress = if (duration > 0) (player.currentPosition * 1000 / duration).toInt().coerceIn(0, 1000) else 0
        play.text = if (player.isPlaying) "⏸" else "▶"
        shuffle.text = if (player.shuffleModeEnabled) "Aleatorio ✓" else "Aleatorio"
        repeat.text = when (player.repeatMode) { Player.REPEAT_MODE_ONE -> "Repetir 1"; Player.REPEAT_MODE_ALL -> "Repetir ✓"; else -> "Repetir" }
        favorite.text = if (player.currentMediaItem?.localConfiguration?.uri?.toString()?.let { it in favorites } == true) "♥" else "♡"
    }
    private fun save() {
        val array = JSONArray(); tracks.forEach { array.put(JSONObject().put("uri", it.uri).put("title", it.title)) }
        prefs.edit().putString("tracks", array.toString()).putStringSet("favorites", favorites.toSet()).apply()
    }
    private fun load() {
        favorites.addAll(prefs.getStringSet("favorites", emptySet()).orEmpty())
        try { val array = JSONArray(prefs.getString("tracks", "[]")); for (i in 0 until array.length()) { val t = array.getJSONObject(i); tracks.add(Track(t.getString("uri"), t.getString("title"))) } } catch (_: Exception) { }
    }
    override fun onDestroy() { handler.removeCallbacks(refresh); future?.let { MediaController.releaseFuture(it) }; controller = null; super.onDestroy() }
}

