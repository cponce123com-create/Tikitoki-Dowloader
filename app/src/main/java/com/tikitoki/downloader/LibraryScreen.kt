package com.tikitoki.downloader

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.request.videoFrameMillis
import com.tikitoki.downloader.data.DownloadRecord
import com.tikitoki.downloader.data.LibraryVisibility
import com.tikitoki.downloader.data.VideoEntry
import com.tikitoki.downloader.data.VideoThumbnailResolver
import com.tikitoki.downloader.domain.DownloadIdentity
import com.tikitoki.downloader.domain.PlaybackKind
import com.tikitoki.downloader.domain.ThumbnailUrls
import com.tikitoki.downloader.ui.TechBackground
import com.tikitoki.downloader.ui.TechHeader
import com.tikitoki.downloader.ui.TechMuted
import com.tikitoki.downloader.ui.TechPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.lazy.grid.items as gridItems

@OptIn(ExperimentalFoundationApi::class)
@Suppress("ktlint:standard:function-naming")
@Composable
internal fun LibraryScreen(
    records: List<DownloadRecord>,
    themeChoice: String,
    insets: PaddingValues,
    libraryKind: String,
    message: String,
    thumbnailResolver: VideoThumbnailResolver,
    galleryImageLoader: ImageLoader,
    onSettings: () -> Unit,
    onKindChange: (String) -> Unit,
    onMusic: () -> Unit,
    onCollections: () -> Unit,
    onOpen: (DownloadRecord) -> Unit,
    onDelete: (DownloadRecord) -> Unit,
) {
    val context = LocalContext.current
    val allCompleted = remember(records) { records.filter { LibraryVisibility.inLibrary(it.status, it.savedUri) } }
    // Show known downloads immediately; resolve older records one at a time in the background.
    val audioKinds = remember { mutableStateMapOf<String, Boolean>() }

    fun libraryKey(record: DownloadRecord) = "${record.videoId}|${record.savedUri}|${record.formatId}"
    LaunchedEffect(allCompleted) {
        for (record in allCompleted) {
            val key = libraryKey(record)
            if (audioKinds.containsKey(key)) continue
            val audio =
                withContext(Dispatchers.IO) {
                    val uri = record.savedUri?.let(Uri::parse)
                    val mime =
                        if (uri?.scheme ==
                            "content"
                        ) {
                            runCatching { context.contentResolver.getType(uri) }.getOrNull()
                        } else {
                            null
                        }
                    val name =
                        if (uri?.scheme == "content") {
                            runCatching {
                                context.contentResolver
                                    .query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                                    ?.use { if (it.moveToFirst()) it.getString(0) else null }
                            }.getOrNull()
                        } else {
                            uri?.lastPathSegment
                        }
                    PlaybackKind.isAudio(record.formatId, mime, name)
                }
            audioKinds[key] = audio
        }
    }
    val completed =
        allCompleted.filter { record ->
            val audio =
                audioKinds[libraryKey(record)]
                    ?: PlaybackKind.isAudio(record.formatId, null, record.savedUri)
            audio == (libraryKind == "Música")
        }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 105.dp),
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    if (themeChoice ==
                        "Negro AMOLED"
                    ) {
                        Color.Black
                    } else {
                        TechBackground
                    },
                ).padding(insets)
                .consumeWindowInsets(insets),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TechHeader(onSettings = onSettings)
                Text("Biblioteca", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Tus descargas, listas para reproducir", color = TechMuted, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = onCollections, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.collections_explore))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Videos", "Música").forEach { kind ->
                        FilterChip(selected = libraryKind == kind, onClick = { onKindChange(kind) }, label = { Text(kind) })
                    }
                    if (libraryKind == "Música") OutlinedButton(onClick = onMusic) { Text("Reproductor") }
                }
                if (completed.isEmpty()) {
                    Text(
                        if (libraryKind ==
                            "Música"
                        ) {
                            "Aún no hay música descargada."
                        } else {
                            "Aún no hay videos descargados."
                        },
                        color = TechMuted,
                    )
                } else {
                    Text(
                        "Toca para reproducir · Mantén pulsado para eliminar",
                        color = TechMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (message.startsWith("No se") ||
                    message.startsWith("El archivo ya no existía")
                ) {
                    Text(message, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        gridItems(completed, key = { it.videoId }) { record ->
            Surface(
                modifier =
                    Modifier.fillMaxWidth().aspectRatio(0.78f).combinedClickable(
                        onClick = { onOpen(record) },
                        onLongClick = { onDelete(record) },
                    ),
                shape = RoundedCornerShape(8.dp),
                color = TechPanel,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val entry =
                        remember(record.videoId, record.thumbnail) {
                            VideoEntry(
                                DownloadIdentity.sourceId(record.videoId),
                                record.videoUrl,
                                record.title,
                                record.thumbnail,
                            )
                        }

                    // The producer assigns value; this Compose lint check does not recognize it with the current Kotlin version.
                    @android.annotation.SuppressLint("ProduceStateDoesNotAssignValue")
                    val remote by produceState<String?>(
                        initialValue =
                            record.thumbnail ?: ThumbnailUrls.youtube(entry.id, entry.url),
                        key1 = entry.url,
                        key2 = entry.thumbnail,
                    ) {
                        value =
                            entry.thumbnail?.takeIf { it.startsWith("https://") } ?: ThumbnailUrls.youtube(entry.id, entry.url)
                                ?: thumbnailResolver.resolve(entry)
                    }

                    @Composable fun LocalVideoFrame() {
                        if (!(audioKinds[libraryKey(record)] ?: PlaybackKind.isAudio(record.formatId, null, record.savedUri))) {
                            coil.compose.SubcomposeAsyncImage(
                                model =
                                    coil.request.ImageRequest
                                        .Builder(
                                            LocalContext.current,
                                        ).data(record.savedUri?.let(Uri::parse))
                                        .videoFrameMillis(1000)
                                        .build(),
                                imageLoader = galleryImageLoader,
                                contentDescription = "Miniatura de ${record.title}",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                error = { Text("▶", color = TechMuted) },
                                loading = { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) },
                            )
                        } else {
                            Text("♫", color = TechMuted, style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                    if (!remote.isNullOrBlank()) {
                        coil.compose.SubcomposeAsyncImage(
                            model =
                                coil.request.ImageRequest
                                    .Builder(LocalContext.current)
                                    .data(remote)
                                    .apply {
                                        if (entry.url.contains(
                                                "tiktok.com",
                                            )
                                        ) {
                                            addHeader("Referer", "https://www.tiktok.com/")
                                        }
                                    }.build(),
                            contentDescription = "Miniatura de ${record.title}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            error = { LocalVideoFrame() },
                            loading = { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) },
                        )
                    } else {
                        LocalVideoFrame()
                    }
                    Text(
                        record.title,
                        modifier =
                            Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .9f))))
                                .padding(start = 7.dp, end = 7.dp, top = 18.dp, bottom = 7.dp),
                        color = Color.White,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
