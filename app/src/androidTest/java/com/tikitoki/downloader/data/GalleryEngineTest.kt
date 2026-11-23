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

    @Test
    fun facebookExtractorIsAvailableInTheBundledEngine() {
        val lines = mutableListOf<String>()
        val result =
            GalleryEngine(ApplicationProvider.getApplicationContext()).run(
                listOf("--config-ignore", "--list-extractors", "facebook"),
                lines::add,
            )
        assertEquals(0, result)
        assertTrue(lines.any { it.contains("FacebookPhotosExtractor") })
    }

    @Test
    fun downloadsImageFromLocalFixture() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val folder = java.io.File(context.cacheDir, "gallery-test-" + java.util.UUID.randomUUID())
        folder.mkdirs()
        val bytes = "fixture-image-data".toByteArray()
        java.net.ServerSocket(0).use { server ->
            server.soTimeout = 20000
            val serving =
                Thread {
                    server.accept().use { socket ->
                        val reader = socket.getInputStream().bufferedReader()
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                        }
                        val output = socket.getOutputStream()
                        output.write(
                            "HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                                .toByteArray(),
                        )
                        output.write(bytes)
                        output.flush()
                    }
                }
            serving.start()
            try {
                val result =
                    GalleryEngine(context).run(
                        listOf(
                            "--config-ignore",
                            "--no-input",
                            "-D",
                            folder.absolutePath,
                            "http://127.0.0.1:${server.localPort}/fixture.png",
                        ),
                    )
                serving.join(20000)
                assertEquals(0, result)
                assertTrue(folder.walkTopDown().any { it.isFile && it.readBytes().contentEquals(bytes) })
            } finally {
                folder.deleteRecursively()
            }
        }
    }
}
