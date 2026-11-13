package com.tikitoki.downloader.data

import android.content.Context
import com.tikitoki.downloader.domain.MetadataSanitizer
import com.tikitoki.downloader.domain.DownloadInputPolicy
import com.tikitoki.downloader.domain.IncomingLinkValidator
import com.tikitoki.downloader.domain.MediaLink
import com.tikitoki.downloader.domain.MediaPlatform
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Platform adapters share the yt-dlp engine. Site-specific authentication and
 * DRM bypass are intentionally not attempted.
 */
interface PlatformExtractor {
    suspend fun inspect(link: MediaLink, start: Int = 1, count: Int = 60): List<VideoEntry>
    suspend fun formats(video: VideoEntry): List<FormatChoice>
}

class UniversalMediaSource(context: Context) : PlatformExtractor {
    private val app = context.applicationContext
    private val tiktok = TikTokVideoSource(app)

    private fun ready() {
        synchronized(UniversalMediaSource::class.java) {
            if (!initialized) {
                YoutubeDL.getInstance().init(app)
                FFmpeg.getInstance().init(app)
                initialized = true
            }
        }
    }

    companion object {
        @Volatile private var initialized = false
    }

    override suspend fun inspect(link: MediaLink, start: Int, count: Int): List<VideoEntry> =
        withContext(Dispatchers.IO) {
            require(start >= 1 && count in 1..100)
            val safeLink = requireNotNull(IncomingLinkValidator.validate(link.url)) { "Enlace no permitido" }
            if (link.platform == MediaPlatform.TIKTOK) {
                val parsed = com.tikitoki.downloader.domain.TikTokLinkParser.parse(link.url)
                require(parsed is com.tikitoki.downloader.domain.LinkParseResult.Success)
                if (link.collection) tiktok.inspectPage(parsed.link, (start - 1) / count + 1, count)
                else tiktok.inspect(parsed.link)
            } else {
                ready()
                val request = YoutubeDLRequest(safeLink.url)
                    .addOption("--flat-playlist")
                    .addOption("--dump-single-json")
                    .addOption("--ignore-errors")
                    .addOption("--no-warnings")
                if (link.collection) {
                    request.addOption("--playlist-start", start)
                    request.addOption("--playlist-end", start + count - 1)
                } else request.addOption("--no-playlist")
                val response = YoutubeDL.getInstance().execute(request, "universal_inspect")
                if (response.exitCode != 0 && response.out.isBlank()) {
                    throw IllegalStateException(response.err.take(300).ifBlank { "No se pudo consultar el enlace público." })
                }
                val root = JSONObject(response.out.trim())
                val entries = root.optJSONArray("entries")
                val results = mutableListOf<VideoEntry>()
                if (entries == null) {
                    parseEntry(root, link)?.let(results::add)
                } else for (i in 0 until entries.length()) {
                    entries.optJSONObject(i)?.let { parseEntry(it, link)?.let(results::add) }
                }
                results.distinctBy { it.url }
            }
        }

    private fun parseEntry(json: JSONObject, link: MediaLink): VideoEntry? {
        val id = json.optString("id").takeIf { it.isNotBlank() } ?: return null
        val original = json.optString("webpage_url").takeIf { it.isNotBlank() }
            ?: json.optString("url").takeIf { it.startsWith("https://") }
        val safePage = DownloadInputPolicy.extractorPage(
            link.platform, id, original, link.url, link.collection
        ) ?: return null
        val title = MetadataSanitizer.title(json.optString("title").ifBlank { "Video $id" })
        return VideoEntry(
            id = "${link.platform.name.lowercase()}:$id",
            url = safePage,
            title = title,
            thumbnail = json.optString("thumbnail").takeIf { it.startsWith("https://") },
            durationSeconds = json.optInt("duration")
        )
    }

    /** Metadata-only count: a missing count must never be treated as zero. */
    suspend fun declaredCollectionCount(link: MediaLink): Int? = withContext(Dispatchers.IO) {
        if (!link.collection || link.platform == MediaPlatform.TIKTOK) return@withContext null
        ready()
        val safeUrl = DownloadInputPolicy.canonicalUrl(link.url)
        val request = YoutubeDLRequest(safeUrl)
            .addOption("--flat-playlist")
            .addOption("--dump-single-json")
            .addOption("--playlist-items", "1")
            .addOption("--ignore-errors")
            .addOption("--no-warnings")
        val response = YoutubeDL.getInstance().execute(request, "collection_count")
        if (response.out.isBlank()) return@withContext null
        val root = runCatching { JSONObject(response.out.trim()) }.getOrNull()
            ?: return@withContext null
        root.optInt("playlist_count", -1).takeIf { it >= 0 }
    }

    override suspend fun formats(video: VideoEntry): List<FormatChoice> = withContext(Dispatchers.IO) {
        ready()
        val info = YoutubeDL.getInstance().getInfo(DownloadInputPolicy.canonicalUrl(video.url))
        val options = info.formats.orEmpty()
            .filter { !it.formatId.isNullOrBlank() && it.height > 0 && it.vcodec != "none" }
            .distinctBy { it.formatId }
            .sortedByDescending { it.height }
             .mapNotNull { f ->
                val selector = if (f.acodec == "none") "${f.formatId}+bestaudio/best" else f.formatId.orEmpty()
                val safeSelector = runCatching { DownloadInputPolicy.safeFormatId(selector) }.getOrNull()
                    ?: return@mapNotNull null
                FormatChoice(safeSelector, "${f.height}p · ${f.ext ?: "video"}")
            }
        listOf(
            FormatChoice(TikTokVideoSource.DEFAULT_FORMAT, "Video: máxima calidad"),
            FormatChoice("bestaudio/best", "Solo audio: mejor calidad disponible")
        ) + options
    }
}
