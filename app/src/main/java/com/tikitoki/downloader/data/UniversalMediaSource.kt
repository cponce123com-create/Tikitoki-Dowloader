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

internal fun thumbnailFromInfo(json: JSONObject): String? =
    json.optString("thumbnail").takeIf { it.startsWith("https://") }
        ?: json.optJSONArray("thumbnails")?.let { thumbnails ->
            (thumbnails.length() - 1 downTo 0).firstNotNullOfOrNull { index ->
                thumbnails.optJSONObject(index)?.optString("url")?.takeIf { it.startsWith("https://") }
            }
        }

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
                ExtractorAccess.useEngine {
                    YoutubeDL.getInstance().init(app)
                    FFmpeg.getInstance().init(app)
                }
                initialized = true
            }
        }
    }

    companion object {
        @Volatile private var initialized = false
    }

    override suspend fun inspect(link: MediaLink, start: Int, count: Int): List<VideoEntry> =
        inspectWithStatus(link, start, count).items

    /** Searches public YouTube results through the bundled extractor. */
    suspend fun searchYouTube(query: String, limit: Int = 30): List<VideoEntry> =
        withContext(Dispatchers.IO) {
            val cleanQuery = query.trim().replace(Regex("\\s+"), " ").take(180)
            require(cleanQuery.isNotBlank()) { "Escribe algo para buscar." }
            require(limit in 1..30)
            ready()
            val response = ExtractorAccess.useEngine {
                YoutubeDL.getInstance().execute(
                    YoutubeDLRequest("ytsearch$limit:$cleanQuery")
                        .addOption("--flat-playlist")
                        .addOption("--dump-single-json")
                        .addOption("--ignore-errors")
                        .addOption("--no-warnings")
                        .addOption("--skip-download"),
                    "rockola_search",
                )
            }
            if (response.out.isBlank()) {
                throw IllegalStateException(response.err.take(300).ifBlank { "YouTube no devolvió resultados." })
            }
            val root = JSONObject(response.out.trim())
            val entries = root.optJSONArray("entries") ?: return@withContext emptyList()
            buildList {
                for (index in 0 until entries.length()) {
                    val entry = entries.optJSONObject(index) ?: continue
                    val id = entry.optString("id").takeIf { it.isNotBlank() } ?: continue
                    add(
                        VideoEntry(
                            id = "youtube:$id",
                            url = "https://www.youtube.com/watch?v=$id",
                            title = MetadataSanitizer.title(entry.optString("title").ifBlank { "Video de YouTube" }),
                            thumbnail = thumbnailFromInfo(entry),
                            durationSeconds = entry.optInt("duration").coerceAtLeast(0),
                        ),
                    )
                }
            }.distinctBy { it.url }
        }

    suspend fun inspectWithStatus(link: MediaLink, start: Int = 1, count: Int = 60): ScanPage =
        withContext(Dispatchers.IO) {
            require(start >= 1 && count in 1..100)
            val safeLink = requireNotNull(IncomingLinkValidator.validate(link.url)) { "Enlace no permitido" }
            if (link.platform == MediaPlatform.TIKTOK) {
                val parsed = com.tikitoki.downloader.domain.TikTokLinkParser.parse(link.url)
                require(parsed is com.tikitoki.downloader.domain.LinkParseResult.Success)
                if (link.collection) tiktok.inspectPageWithStatus(parsed.link, (start - 1) / count + 1, count)
                else tiktok.inspect(parsed.link).let { ScanPage(it, it.size, false) }
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
                val response = ExtractorAccess.useEngine { YoutubeDL.getInstance().execute(request, "universal_inspect") }
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
                val distinct = results.distinctBy { it.url }
                val rawCount = entries?.length() ?: 1
                val incomplete = response.exitCode != 0 || response.err.contains("ERROR:", ignoreCase = true) || distinct.size < rawCount
                val avatar = listOf("uploader_thumbnail", "channel_thumbnail", "avatar")
                    .firstNotNullOfOrNull { key -> root.optString(key).takeIf { it.startsWith("https://") } }
                ScanPage(distinct, rawCount, incomplete, avatar)
            }
        }

    private fun parseEntry(json: JSONObject, link: MediaLink): VideoEntry? {
        val id = json.optString("id").takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) } ?: return null
        val original = json.optString("webpage_url").takeIf { it.isNotBlank() }
            ?: json.optString("url").takeIf { it.startsWith("https://") }
        val safePage = DownloadInputPolicy.extractorPage(
            link.platform, id, original, link.url, link.collection
        ) ?: return null
        val rawTitle = json.optString("title").takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        val title = MetadataSanitizer.title(rawTitle ?: "Video $id")
        return VideoEntry(
            id = if (link.platform == MediaPlatform.DIRECT_MEDIA &&
                !java.net.URI(link.url).host.orEmpty().let { it == "xvideos.com" || it.endsWith(".xvideos.com") })
                "${link.platform.name.lowercase()}:${java.net.URI(link.url).host}:$id"
            else "${link.platform.name.lowercase()}:$id",
            url = safePage,
            title = title,
            thumbnail = thumbnailFromInfo(json) ?: com.tikitoki.downloader.domain.ThumbnailUrls.youtube(id, safePage),
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
        val response = ExtractorAccess.useEngine { YoutubeDL.getInstance().execute(request, "collection_count") }
        if (response.out.isBlank()) return@withContext null
        val root = runCatching { JSONObject(response.out.trim()) }.getOrNull()
            ?: return@withContext null
        root.optInt("playlist_count", -1).takeIf { it >= 0 }
    }

    override suspend fun formats(video: VideoEntry): List<FormatChoice> = formats(video, compact = false)

    suspend fun formats(
        video: VideoEntry,
        compact: Boolean,
    ): List<FormatChoice> =
        withContext(Dispatchers.IO) {
            ready()
            val info = ExtractorAccess.useEngine { YoutubeDL.getInstance().getInfo(DownloadInputPolicy.canonicalUrl(video.url)) }
            val duration = video.durationSeconds.takeIf { it > 0 } ?: info.duration
            if (compact) {
                CompactDownloadFormats.options(app, info, duration)
            } else {
                FormatOptions.individual(app, info.formats.orEmpty(), duration)
            }
        }

    /** Consults the public playlist tab; entries are playlists, not individual videos. */
    internal suspend fun channelPlaylistsJson(): String =
        withContext(Dispatchers.IO) {
            ready()
            val request =
                YoutubeDLRequest("https://www.youtube.com/channel/UCb5Wa57eqZv48hcRNCZL1fw/playlists")
                    .addOption("--flat-playlist")
                    .addOption("--dump-single-json")
                    .addOption("--ignore-errors")
                    .addOption("--no-warnings")
                    .addOption("--playlist-end", CollectionsCatalog.MAX_PLAYLISTS)
            val response =
                ExtractorAccess.useEngine {
                    YoutubeDL.getInstance().execute(request, "bajape_channel_playlists")
                }
            check(response.exitCode == 0 && response.out.isNotBlank())
            response.out
        }
}
