package com.tikitoki.downloader

import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.net.Uri
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
    private lateinit var time: TextView
    private lateinit var seek: SeekBar
    private lateinit var play: Button
    private lateinit var favorite: Button
    private lateinit var shuffle: Button
    private lateinit var repeat: Button
    private lateinit var list: LinearLayout
    private var seeking = false
    private val prefs by lazy { getSharedPreferences("music_player", MODE_PRIVATE) }
    private val picker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: SecurityException) { }
            if (tracks.none { it.uri == uri.toString() }) tracks.add(Track(uri.toString(), displayName(uri)))
        }
        save(); renderList()
    }
    private val refresh = object : Runnable {
        override fun run() { updateUi(); handler.postDelayed(this, 1000) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        load()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 20, 20, 20); setBackgroundColor(Color.rgb(0, 28, 20)) }
        fun label(size: Float) = TextView(this).apply { textSize = size; setTextColor(Color.WHITE) }
        fun button(text: String, action: () -> Unit) = Button(this).apply { this.text = text; setOnClickListener { action() } }
        root.addView(label(26f).apply { text = "Baja.pe · Mi música" })
        trackTitle = label(20f).apply { text = "Selecciona una canción" }; root.addView(trackTitle)
        time = label(14f).apply { text = "00:00 / 00:00" }; root.addView(time)
        seek = SeekBar(this).apply {
            max = 1000
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(bar: SeekBar?) { seeking = true }
                override fun onStopTrackingTouch(bar: SeekBar?) { val p = controller; if (p != null && p.duration > 0) p.seekTo(p.duration * progress / 1000); seeking = false }
                override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) { }
            })
        }; root.addView(seek)
        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL }
        controls.addView(button("⏮") { controller?.seekToPreviousMediaItem() })
        play = button("▶") { controller?.let { if (it.isPlaying) it.pause() else it.play() } }; controls.addView(play)
        controls.addView(button("⏭") { controller?.seekToNextMediaItem() })
        root.addView(controls)
        val modes = LinearLayout(this).apply { gravity = Gravity.CENTER }
        shuffle = button("Aleatorio") { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }; updateUi() }; modes.addView(shuffle)
        repeat = button("Repetir") { controller?.let { it.repeatMode = when (it.repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF } }; updateUi() }; modes.addView(repeat)
        favorite = button("♡") { val uri = controller?.currentMediaItem?.localConfiguration?.uri?.toString() ?: return@button; if (!favorites.add(uri)) favorites.remove(uri); save(); updateUi() }; modes.addView(favorite)
        root.addView(modes)
        root.addView(button("Agregar canciones") { picker.launch(arrayOf("audio/*")) })
        root.addView(label(20f).apply { text = "Lista de reproducción" })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        renderList()
        val pending = MediaController.Builder(this, SessionToken(this, ComponentName(this, PlaybackService::class.java))).buildAsync()
        future = pending
        pending.addListener({ if (!isDestroyed && !isFinishing) try { controller = pending.get(); updateUi() } catch (_: Exception) { Toast.makeText(this, "No se pudo conectar el reproductor.", Toast.LENGTH_LONG).show() } }, ContextCompat.getMainExecutor(this))
        handler.post(refresh)
    }
    private fun displayName(uri: Uri): String = try {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "Canción"
    } catch (_: Exception) { "Canción" }
    private fun renderList() {
        list.removeAllViews()
        tracks.forEachIndexed { index, track ->
            val row = LinearLayout(this)
            row.addView(Button(this).apply { text = track.title; isAllCaps = false; setOnClickListener { startAt(index) } }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(Button(this).apply { text = "Quitar"; setOnClickListener { val removed = tracks.removeAt(index); controller?.let { p -> for (i in p.mediaItemCount - 1 downTo 0) if (p.getMediaItemAt(i).localConfiguration?.uri?.toString() == removed.uri) p.removeMediaItem(i) }; save(); renderList() } })
            list.addView(row)
        }
    }
    private fun startAt(index: Int) {
        val player = controller ?: return
        val items = tracks.map { track -> MediaItem.Builder().setUri(track.uri).setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).build()).build() }
        try { player.setMediaItems(items, index, 0L); player.prepare(); player.play() }
        catch (_: Exception) { Toast.makeText(this, "No se pudo abrir el audio.", Toast.LENGTH_LONG).show() }
    }
    private fun updateUi() {
        val player = controller ?: return
        trackTitle.text = player.mediaMetadata.title?.toString() ?: "Selecciona una canción"
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
