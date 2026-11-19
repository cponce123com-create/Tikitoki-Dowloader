package com.tikitoki.downloader.data

import android.content.Context
import com.tikitoki.downloader.domain.ThumbnailUrls
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Flat playlist entries often omit thumbnails. Resolve only cards that are actually
 * composed/visible; never turn a large profile scan into thousands of detail requests.
 * A small semaphore protects the extractor and the remote platform from bursts.
 */
class VideoThumbnailResolver(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val slots = Semaphore(2)
    private val cache = android.util.LruCache<String, CachedThumbnail>(MAX_CACHED_THUMBNAILS)

    suspend fun resolve(video: VideoEntry): String? {
        if (!video.thumbnail.isNullOrBlank()) return video.thumbnail
        ThumbnailUrls.youtube(video.id, video.url)?.let { return it }
        cache.get(video.url)?.let { return it.url }
        return slots.withPermit {
            cache.get(video.url)?.let { return@withPermit it.url }
            withContext(Dispatchers.IO) {
                val result =
                    runCatching {
                        val safeUrl =
                            com.tikitoki.downloader.domain.DownloadInputPolicy
                                .canonicalUrl(video.url)
                        ExtractorAccess.useEngine {
                            YoutubeDL.getInstance().init(appContext)
                            YoutubeDL.getInstance().getInfo(safeUrl).thumbnail
                        }
                    }.getOrNull()?.takeIf { it.startsWith("https://") }
                cache.put(video.url, CachedThumbnail(result))
                result
            }
        }
    }
}

private const val MAX_CACHED_THUMBNAILS = 512

private data class CachedThumbnail(
    val url: String?,
)
