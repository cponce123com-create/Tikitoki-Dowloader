package com.tikitoki.downloader

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MusicActivityTest {
    @Test fun opensMusicScreenWithQueueAndControls() {
        ActivityScenario.launch(MusicActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                fun descendants(view: View): List<View> =
                    listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
                val views = descendants(activity.window.decorView)
                val labels = views.filterIsInstance<TextView>().map { it.text.toString() }
                val buttons = views.filterIsInstance<Button>().map { it.text.toString() }
                assertTrue(labels.contains("Mi música"))
                assertTrue(labels.contains("Lista de reproducción"))
                assertTrue(buttons.contains("+ Agregar"))
                assertTrue(buttons.contains("Aleatorio"))
                assertTrue(buttons.contains("Repetir"))
            }
        }
    }
}
