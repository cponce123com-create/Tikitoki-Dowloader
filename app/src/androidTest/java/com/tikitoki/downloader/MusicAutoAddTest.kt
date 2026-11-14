package com.tikitoki.downloader

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MusicAutoAddTest {
    @Test fun openingAnAudioAddsItOnceToMusic() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("music_player", Context.MODE_PRIVATE)
        val previous = preferences.getString("tracks", "[]")
        val audio = File(context.cacheDir, "auto-add-test.mp3")
        audio.writeBytes(byteArrayOf(0))
        val uri = Uri.fromFile(audio)
        try {
            preferences.edit().putString("tracks", "[]").commit()
            val intent = Intent(context, MusicActivity::class.java).setData(uri).putExtra("title", "Canción de prueba")
            repeat(2) {
                ActivityScenario.launch<MusicActivity>(intent).use { scenario ->
                    scenario.onActivity {
                        val tracks = JSONArray(preferences.getString("tracks", "[]"))
                        assertEquals(1, tracks.length())
                        assertEquals(uri.toString(), tracks.getJSONObject(0).getString("uri"))
                        assertEquals("Canción de prueba", tracks.getJSONObject(0).getString("title"))
                    }
                }
            }
        } finally {
            preferences.edit().putString("tracks", previous).commit()
            audio.delete()
        }
    }
}
