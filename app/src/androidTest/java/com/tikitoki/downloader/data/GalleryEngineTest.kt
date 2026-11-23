package com.tikitoki.downloader.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GalleryEngineTest {
    @Test
    fun bundledPhotoEngineStartsWithTheAndroidPythonRuntime() {
        val lines = mutableListOf<String>()
        val result = GalleryEngine(ApplicationProvider.getApplicationContext()).run(listOf("--version"), lines::add)
        assertEquals(0, result)
        assertTrue(lines.any { it.trim() == "1.32.21" })
    }
}
