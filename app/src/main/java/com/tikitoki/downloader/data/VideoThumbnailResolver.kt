package com.tikitoki.downloader.data

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Flat playlist entries often omit thumbnails. Resolve only cards that are actually
 * composed/visible; never turn a large profile scan into thousands of detail requests.
 * A small semaphore protects the extractor and the remote platform from bursts.
 */
class VideoThumbnailResolver(context: Context) {
    private val appContext = context.applicationContext
    private val slots = Semaphore(2)
    private val cache = ConcurrentHashMap<String, String>()
    private val failed = ConcurrentHashMap.newKeySet<String>()

    suspend fun resolve(video: VideoEntry): String? {
        if (!video.thumbnail.isNullOrBlank()) return video.thumbnail
        cache[video.url]?.let { return it }
        if (video.url in failed) return null
        return slots.withPermit {
            cache[video.url]?.let { return@withPermit it }
            if (video.url in failed) return@withPermit null
            withContext(Dispatchers.IO) {
                val result = runCatching {
                    ExtractorAccess.useEngine {
                        YoutubeDL.getInstance().init(appContext)
                        YoutubeDL.getInstance().getInfo(video.url).thumbnail
                    }
                }.getOrNull()?.takeIf { it.startsWith("https://") }
                if (result == null) failed.add(video.url) else cache[video.url] = result
                result
            }
        }
    }
}

