package com.tikitoki.downloader.ui

import android.content.SharedPreferences
import com.tikitoki.downloader.data.VideoEntry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

class RockolaLibraryTest {
    @Test
    fun queueAndFavoritesPersistWithoutDuplicates() {
        val preferences = inMemoryPreferences()
        val library = RockolaLibrary(preferences)
        val first = track("youtube:abcdefghijk", "Primera")
        val second = track("youtube:klmnopqrstuv", "Segunda")

        library.enqueue(first)
        library.enqueue(second)
        library.enqueue(first)
        assertEquals(listOf(first, second), library.queue)

        library.moveUp(second)
        library.moveUp(second)
        assertEquals(listOf(second, first), library.queue)
        library.remove(first)
        library.remove(first)
        assertEquals(listOf(second), library.queue)

        library.toggleFavorite(first)
        library.toggleFavorite(first)
        library.toggleFavorite(first)
        assertEquals(listOf(first), library.favorites)
        assertEquals(setOf(first.id), library.favoriteIds)

        val restored = RockolaLibrary(preferences)
        assertEquals(listOf(second), restored.queue)
        assertEquals(listOf(first), restored.favorites)
    }

    @Test
    fun historyAndNamedPlaylistsPreserveOrderAndIgnoreDuplicateTracks() {
        val preferences = inMemoryPreferences()
        val library = RockolaLibrary(preferences)
        val first = track("youtube:abcdefghijk", "Primera")
        val second = track("youtube:klmnopqrstuv", "Segunda")

        library.recordPlayed(first)
        library.recordPlayed(second)
        library.recordPlayed(first)
        assertEquals(listOf(first, second), library.history)

        library.saveToPlaylist("Favoritas", first)
        library.saveToPlaylist("favoritas", second)
        library.saveToPlaylist("FAVORITAS", second)
        assertEquals("Favoritas", library.playlists.single().name)
        assertEquals(listOf(first, second), library.playlists.single().tracks)

        val restored = RockolaLibrary(preferences)
        assertEquals(listOf(first, second), restored.history)
        assertEquals(listOf(first, second), restored.playlists.single().tracks)
    }

    private fun track(
        id: String,
        title: String,
    ) = VideoEntry(
        id = id,
        url = "https://www.youtube.com/watch?v=${id.removePrefix("youtube:")}",
        title = title,
    )

    private fun inMemoryPreferences(): SharedPreferences {
        val values = mutableMapOf<String, Any>()
        val editor = inMemoryEditor(values)
        return Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
            InvocationHandler { _, method, args ->
                val key = args?.getOrNull(0) as? String
                when (method.name) {
                    "getString" -> values[key] as? String ?: args?.getOrNull(1) as? String
                    "getStringSet" -> values[key] as? Set<*> ?: args?.getOrNull(1) as? Set<*>
                    "edit" -> editor
                    else -> error("Unexpected preferences method: ${method.name}")
                }
            },
        ) as SharedPreferences
    }

    private fun inMemoryEditor(values: MutableMap<String, Any>): SharedPreferences.Editor {
        lateinit var editor: SharedPreferences.Editor
        editor =
            Proxy.newProxyInstance(
                SharedPreferences.Editor::class.java.classLoader,
                arrayOf(SharedPreferences.Editor::class.java),
                InvocationHandler { _, method, args ->
                    handleEditorMethod(method.name, args, values, editor)
                },
            ) as SharedPreferences.Editor
        return editor
    }

    private fun handleEditorMethod(
        name: String,
        args: Array<out Any?>?,
        values: MutableMap<String, Any>,
        editor: SharedPreferences.Editor,
    ): Any? =
        when (name) {
            "putString", "putStringSet" -> {
                val key = args?.getOrNull(0) as? String ?: error("Preference key is required")
                val value = args.getOrNull(1)
                if (value == null) values.remove(key) else values[key] = value
                editor
            }
            "remove" -> {
                val key = args?.getOrNull(0) as? String ?: error("Preference key is required")
                values.remove(key)
                editor
            }
            "clear" -> {
                values.clear()
                editor
            }
            "apply" -> null
            "commit" -> true
            else -> error("Unexpected editor method: $name")
        }
}
