package com.tikitoki.downloader

import android.content.Context
import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tikitoki.downloader.ui.MusicScreen
import com.tikitoki.downloader.audio.PlaybackSpectrum
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class MusicIntegratedPlaybackTest {
    @get:Rule val compose = createComposeRule()

    @Test fun openingDownloadedAudioAddsAndStartsIt() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("music_player", Context.MODE_PRIVATE)
        val before = prefs.getString("tracks", "[]")
        val file = File(context.cacheDir, "music-integrated-test.wav")
        val samples = 8000 * 30 * 2
        val wav = ByteBuffer.allocate(44 + samples).order(ByteOrder.LITTLE_ENDIAN)
        wav.put("RIFF".toByteArray()).putInt(36 + samples).put("WAVEfmt ".toByteArray())
        wav.putInt(16).putShort(1).putShort(1).putInt(8000).putInt(16000).putShort(2).putShort(16)
        wav.put("data".toByteArray()).putInt(samples)
        repeat(8000 * 30) { index ->
            wav.putShort((sin(2.0 * PI * 440.0 * index / 8000.0) * 16000).toInt().toShort())
        }
        file.writeBytes(wav.array())
        val consumed = AtomicBoolean(false)
        try {
            compose.setContent {
                MaterialTheme { MusicScreen(Uri.fromFile(file).toString(), "Audio de prueba") { consumed.set(true) } }
            }
            compose.waitUntil(30_000) {
                consumed.get() && PlaybackSpectrum.levels.value.any { it > 0.05f } &&
                    compose.onAllNodesWithContentDescription("Pausar").fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue(prefs.getString("tracks", "").orEmpty().contains("music-integrated-test.wav"))
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "music-playing.png")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/")
            }
            val screenshot = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
            context.contentResolver.openOutputStream(screenshot)!!.use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            compose.onNodeWithContentDescription("Pausar").performClick()
        } finally {
            prefs.edit().putString("tracks", before).commit()
            file.delete()
        }
    }
}

