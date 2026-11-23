package com.tikitoki.downloader.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PhotoStorageTest {
    @Test
    fun publishesToGalleryAndSkipsAnExistingName() {
        assumeTrue(Build.VERSION.SDK_INT >= 29)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "photo-test-${UUID.randomUUID()}.jpg")
        val directory = "Pictures/Baja.pe/test/"
        file.writeBytes(byteArrayOf(1, 2, 3))
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        try {
            assertTrue(PhotoStorage.publish(context, file, directory))
            assertFalse(PhotoStorage.publish(context, file, directory))
            val selection = "${MediaStore.Images.Media.RELATIVE_PATH}=? AND ${MediaStore.Images.Media.DISPLAY_NAME}=?"
            val args = arrayOf(directory, file.name)
            context.contentResolver.query(collection, arrayOf(MediaStore.Images.Media._ID), selection, args, null)?.use {
                assertTrue(it.moveToFirst())
                val uri = ContentUris.withAppendedId(collection, it.getLong(0))
                assertTrue(
                    context.contentResolver.openInputStream(uri)?.use { stream -> stream.readBytes().contentEquals(file.readBytes()) } ==
                        true,
                )
            } ?: error("MediaStore query returned no cursor")
        } finally {
            context.contentResolver.delete(collection, "${MediaStore.Images.Media.DISPLAY_NAME}=?", arrayOf(file.name))
            file.delete()
        }
    }
}
