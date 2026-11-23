package com.tikitoki.downloader

import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tikitoki.downloader.ui.TechHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
@Suppress("ktlint:standard:function-naming", "LongMethod", "MagicNumber")
internal fun PhotoLibrary(
    insets: PaddingValues,
    onSettings: () -> Unit,
    onKindChange: (String) -> Unit,
) {
    val context = LocalContext.current
    val photos by produceState(initialValue = emptyList<Uri>()) {
        value =
            withContext(Dispatchers.IO) {
                runCatching {
                    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    val pathColumn =
                        if (Build.VERSION.SDK_INT >=
                            29
                        ) {
                            MediaStore.Images.Media.RELATIVE_PATH
                        } else {
                            MediaStore.Images.Media.DATA
                        }
                    val pattern = if (Build.VERSION.SDK_INT >= 29) "Pictures/Baja.pe/%" else "%/Pictures/Baja.pe/%"
                    val result = mutableListOf<Uri>()
                    context.contentResolver
                        .query(
                            collection,
                            arrayOf(MediaStore.Images.Media._ID),
                            "$pathColumn LIKE ?",
                            arrayOf(pattern),
                            "${MediaStore.Images.Media.DATE_ADDED} DESC",
                        )?.use { cursor ->
                            while (cursor.moveToNext()) {
                                result.add(ContentUris.withAppendedId(collection, cursor.getLong(0)))
                            }
                        }
                    result
                }.getOrDefault(emptyList())
            }
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(105.dp),
        modifier = Modifier.fillMaxSize().padding(insets),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                TechHeader(onSettings = onSettings)
                Text("Biblioteca")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Videos", "Música", "Fotos").forEach { kind ->
                        FilterChip(selected = kind == "Fotos", onClick = { onKindChange(kind) }, label = { Text(kind) })
                    }
                }
                if (photos.isEmpty()) Text("Aún no hay fotos descargadas.") else Text("${photos.size} fotos")
            }
        }
        items(photos, key = { it.toString() }) { uri ->
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).clickable {
                    val intent =
                        Intent(Intent.ACTION_VIEW)
                            .setDataAndType(uri, "image/*")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    runCatching { context.startActivity(intent) }
                },
            ) {
                AsyncImage(
                    model = uri,
                    contentDescription = "Foto descargada",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
