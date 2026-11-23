package com.tikitoki.downloader.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/** Publishes only fully downloaded files and removes incomplete MediaStore entries on error. */
internal object PhotoStorage {
    @Suppress("MagicNumber", "ReturnCount", "TooGenericExceptionCaught")
    fun publish(
        context: Context,
        file: File,
        directory: String,
    ): Boolean {
        val extension = file.extension.lowercase()
        val name = file.nameWithoutExtension.take(130) + "." + extension
        val mime =
            when (extension) {
                "png" -> "image/png"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                else -> "image/jpeg"
            }
        if (Build.VERSION.SDK_INT < 29) {
            val folder =
                File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    directory.removePrefix("Pictures/"),
                )
            check(folder.isDirectory || folder.mkdirs())
            val dest = File(folder, name)
            if (dest.exists()) return false
            file.copyTo(dest)
            MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), arrayOf(mime), null)
            return true
        }
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        resolver
            .query(
                collection,
                arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media.RELATIVE_PATH}=? AND ${MediaStore.Images.Media.DISPLAY_NAME}=?",
                arrayOf(directory, name),
                null,
            )?.use {
                if (it.moveToFirst()) return false
            }
        val values =
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, directory)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        val target = checkNotNull(resolver.insert(collection, values))
        try {
            checkNotNull(resolver.openOutputStream(target)).use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(target, values, null, null)
            return true
        } catch (error: Exception) {
            resolver.delete(target, null, null)
            throw error
        }
    }
}
