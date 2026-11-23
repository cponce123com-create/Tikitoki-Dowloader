package com.tikitoki.downloader.data

import android.content.Context
import com.tikitoki.downloader.domain.MetadataSanitizer
import com.tikitoki.downloader.domain.Mp3Preset
import com.tikitoki.downloader.domain.TikTokLink
import com.tikitoki.downloader.domain.TikTokLinkType
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class VideoEntry(
    val id: String,
    val url: String,
    val title: String,
    val thumbnail: String? = null,
    val durationSeconds: Int = 0,
    val uploader: String? = null,
    val viewCount: Long = 0L,
)

data class FormatChoice(
    val id: String,
    val label: String,
    val height: Int? = null,
    val isAudio: Boolean = false,
    val shortLabel: String = label,
)

/**
 * Calls the bundled yt-dlp extractor. No generated/fake results: all entries must
 * originate in a returned TikTok extractor payload. TikTok might reject queries.
 */
class TikTokVideoSource(
    private val context: Context,
) {
    private val photoSource = TikTokPhotoSource(context.applicationContext)
    private var initialized = false
    private var updateAttempted = false
    private val profileCache = context.applicationContext.getSharedPreferences("profile_cache_v1", Context.MODE_PRIVATE)

    @Synchronized
    private fun ensureReady() {
        if (!initialized) {
            ExtractorAccess.useEngine {
                YoutubeDL.getInstance().init(context.applicationContext)
                FFmpeg.getInstance().init(context.applicationContext)
            }
            initialized = true
        }
        // Do not run a potentially slow yt-dlp update on the critical scan path.
        // Updates must be triggered separately so the first page can appear promptly.
    }

    suspend fun inspect(
        link: TikTokLink,
        forceRefresh: Boolean = false,
    ): List<VideoEntry> =
        withContext(Dispatchers.IO) {
            if (link.type != TikTokLinkType.PROFILE && !link.isPhoto) ensureReady()
            when (link.type) {
                TikTokLinkType.PROFILE -> {
                    if (!forceRefresh) cachedProfile(link.url)?.let { return@withContext it }
                    ensureReady()
                    inspectProfile(link).also { if (it.isNotEmpty()) saveProfile(link.url, it) }
                }
                TikTokLinkType.VIDEO -> if (link.isPhoto) photoSource.inspect(link.url) else listOf(fetchSingle(link.url))
                TikTokLinkType.SHORT_LINK ->
                    try {
                        listOf(fetchSingle(link.url))
                    } catch (error: Exception) {
                        if (error is kotlinx.coroutines.CancellationException) throw error
                        photoSource.inspectIfPhoto(link.url) ?: throw error
                    }
            }
        }

    private fun cachedProfile(url: String): List<VideoEntry>? {
        val storedAt = profileCache.getLong("time:$url", 0L)
        if (System.currentTimeMillis() - storedAt > 15 * 60 * 1000L) return null
        return try {
            val array = JSONArray(profileCache.getString("data:$url", "[]"))
            if (array.length() == 0) return null
            (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                VideoEntry(
                    id = item.getString("id"),
                    url = item.getString("url"),
                    title = MetadataSanitizer.title(item.optString("title")),
                    thumbnail = item.optString("thumbnail").takeIf { it.isNotBlank() },
                    durationSeconds = item.optInt("duration"),
                )
            }
        } catch (
            e: Exception,
        ) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            DiagnosticHistory.record(context, e.message)
            null
        }
    }

    private fun saveProfile(
        url: String,
        videos: List<VideoEntry>,
    ) {
        val array = JSONArray()
        videos.forEach { video ->
            array.put(
                JSONObject().apply {
                    put("id", video.id)
                    put("url", video.url)
                    put("title", video.title)
                    put("thumbnail", video.thumbnail ?: "")
                    put("duration", video.durationSeconds)
                },
            )
        }
        profileCache
            .edit()
            .putString("data:$url", array.toString())
            .putLong("time:$url", System.currentTimeMillis())
            .apply()
    }

    private fun fetchSingle(url: String): VideoEntry {
        val info =
            try {
                ExtractorAccess.useEngine {
                    YoutubeDL.getInstance().getInfo(
                        com.tikitoki.downloader.domain.DownloadInputPolicy
                            .canonicalUrl(url),
                    )
                }
            } catch (
                e: Exception,
            ) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                throw readableError(e)
            }
        val id =
            info.id?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("El extractor no devolvió un ID de video.")
        return VideoEntry(
            id = id,
            url = info.webpageUrl?.takeIf { it.startsWith("https://") } ?: url,
            title = MetadataSanitizer.title(info.title?.takeIf { it.isNotBlank() } ?: "Video $id"),
            thumbnail = info.thumbnail,
            durationSeconds = info.duration,
        )
    }

    /** Request a bounded page directly from yt-dlp, not from a full fetched list. */
    suspend fun inspectPage(
        link: TikTokLink,
        page: Int,
        pageSize: Int = 60,
    ): List<VideoEntry> = inspectPageWithStatus(link, page, pageSize).items

    suspend fun inspectPageWithStatus(
        link: TikTokLink,
        page: Int,
        pageSize: Int = 60,
    ): ScanPage =
        withContext(Dispatchers.IO) {
            require(link.type == TikTokLinkType.PROFILE)
            require(page >= 1 && pageSize in 1..100)
            ensureReady()
            inspectProfileWithStatus(link, (page - 1) * pageSize + 1, page * pageSize)
        }

    private fun inspectProfile(link: TikTokLink): List<VideoEntry> = inspectProfileWithStatus(link).items

    private fun inspectProfileWithStatus(
        link: TikTokLink,
        start: Int? = null,
        end: Int? = null,
    ): ScanPage {
        val request =
            YoutubeDLRequest(
                com.tikitoki.downloader.domain.DownloadInputPolicy
                    .canonicalUrl(link.url),
            ).addOption("--flat-playlist")
                .addOption("--dump-single-json")
                .addOption("--ignore-errors")
                .addOption("--no-warnings")

        if (start != null && end != null) {
            request.addOption("--playlist-start", start)
            request.addOption("--playlist-end", end)
        }
        val response =
            try {
                ExtractorAccess.useEngine { YoutubeDL.getInstance().execute(request, "tikitoki_scan") }
            } catch (
                e: Exception,
            ) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                throw readableError(e)
            }
        if (response.exitCode != 0 && response.out.isBlank()) {
            throw readableError(IllegalStateException(response.err))
        }
        val json =
            try {
                JSONObject(response.out.trim())
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                DiagnosticHistory.record(context, e.message)
                throw IllegalStateException("TikTok no devolvió un catálogo válido de publicaciones.")
            }
        val entries =
            json.optJSONArray("entries")
                ?: throw IllegalStateException("No se pudo enumerar el perfil. Puede estar restringido.")
        val username = link.username ?: throw IllegalStateException("No se identificó el usuario.")

        val seen = mutableSetOf<String>()
        val results =
            buildList {
                for (index in 0 until entries.length()) {
                    val entry = entries.optJSONObject(index) ?: continue
                    val id = entry.optString("id").takeIf { it.matches(Regex("[0-9]{5,30}")) } ?: continue
                    if (!seen.add(id)) continue
                    add(
                        VideoEntry(
                            id = id,
                            url = "https://www.tiktok.com/@$username/video/$id",
                            title = MetadataSanitizer.title(entry.optString("title").ifBlank { "Video $id" }),
                            thumbnail =
                                entry.optString("thumbnail").takeIf { it.startsWith("https://") }
                                    ?: entry.optJSONArray("thumbnails")?.let { thumbs ->
                                        (0 until thumbs.length())
                                            .mapNotNull { i ->
                                                thumbs.optJSONObject(i)?.optString("url")?.takeIf { it.startsWith("https://") }
                                            }.lastOrNull()
                                    },
                            durationSeconds = entry.optInt("duration"),
                        ),
                    )
                }
            }
        val incomplete = response.exitCode != 0 || response.err.contains("ERROR:", ignoreCase = true) || results.size < entries.length()
        val avatar =
            listOf("uploader_thumbnail", "channel_thumbnail", "avatar")
                .firstNotNullOfOrNull { key -> json.optString(key).takeIf { it.startsWith("https://") } }
        return ScanPage(results, entries.length(), incomplete, avatar)
    }

    suspend fun formats(
        video: VideoEntry,
        compact: Boolean = false,
    ): List<FormatChoice> =
        withContext(Dispatchers.IO) {
            if (TikTokPhotoSource.isPhotoEntry(video)) {
                return@withContext listOf(
                    FormatChoice(
                        id = TikTokPhotoSource.PHOTO_FORMAT_ID,
                        label = "Imagen original",
                        shortLabel = "Foto",
                    ),
                )
            }
            ensureReady()
            val info =
                try {
                    ExtractorAccess.useEngine {
                        YoutubeDL.getInstance().getInfo(
                            com.tikitoki.downloader.domain.DownloadInputPolicy
                                .canonicalUrl(video.url),
                        )
                    }
                } catch (
                    e: Exception,
                ) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    throw readableError(e)
                }
            val duration = video.durationSeconds.takeIf { it > 0 } ?: info.duration
            if (compact) {
                CompactDownloadFormats.options(context, info, duration)
            } else {
                FormatOptions.individual(context, info.formats.orEmpty(), duration)
            }
        }

    suspend fun downloadToCache(
        video: VideoEntry,
        formatId: String,
        onSpeed: (String) -> Unit = {},
        onProgress: (Int) -> Unit,
    ): File =
        withContext(Dispatchers.IO) {
            val safeFormatId =
                com.tikitoki.downloader.domain.DownloadInputPolicy
                    .safeFormatId(formatId)
            if (TikTokPhotoSource.isPhotoEntry(video)) {
                require(TikTokPhotoSource.isPhotoFormat(safeFormatId)) { "La foto no tiene un formato de descarga compatible." }
                val imageUrl = video.thumbnail ?: throw IllegalArgumentException("No se encontró la URL de la imagen.")
                return@withContext photoSource.downloadToCache(imageUrl, video.id, onProgress)
            }
            require(!TikTokPhotoSource.isPhotoFormat(safeFormatId)) { "El formato de imagen requiere una entrada de foto TikTok." }
            ensureReady()
            val folder = File(context.cacheDir, "tikitoki")
            if (!folder.exists() && !folder.mkdirs()) {
                throw IllegalStateException("No se pudo crear la carpeta temporal.")
            }
            val safeId = video.id.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
            if (safeId.isBlank()) throw IllegalArgumentException("ID de video inválido.")
            // Remove completed remnants from an earlier run; keep .part files for --continue.
            folder
                .listFiles()
                ?.filter {
                    it.isFile &&
                        it.nameWithoutExtension == safeId &&
                        it.extension.lowercase() in setOf("mp4", "mkv", "webm", "mov", "m4a", "mp3", "opus", "ogg", "aac")
                }?.forEach { it.delete() }
            val mp3Bitrate =
                FormatOptions.mp3Bitrate(
                    com.tikitoki.downloader.domain.DownloadInputPolicy
                        .safeFormatId(safeFormatId),
                )
            val selector =
                when (mp3Bitrate) {
                    // The source metadata does not reliably expose `abr` for TikTok
                    // streams. Filtering on it can leave yt-dlp with no candidate,
                    // even though a valid audio stream is available. The requested
                    // MP3 bitrate is applied by the audio postprocessor below.
                    in Mp3Preset.averageRates -> "bestaudio/best"
                    null -> formatId.removePrefix("bajaaudio-")
                    else -> "bestaudio/best"
                }
            val request =
                YoutubeDLRequest(
                    com.tikitoki.downloader.domain.DownloadInputPolicy
                        .canonicalUrl(video.url),
                ).addOption("--no-playlist")
                    .addOption("--newline")
                    .addOption("--continue")
                    // Parallel fragments accelerate DASH/HLS streams where the host allows it.
                    // Limit concurrency to avoid saturating mobile networks and HTTP 429.
                    .addOption("--concurrent-fragments", if (DownloadSettings.currentLimit(context) > 4) "2" else "4")
                    .addOption("--fragment-retries", "3")
                    .addOption("--retries", "3")
                    .addOption("--retry-sleep", "fragment:exp=1:8")
                    .addOption("--socket-timeout", "20")
                    .addOption("-f", selector)
                    .addOption("--merge-output-format", "mp4")
                    .addOption("-o", File(folder, "$safeId.%(ext)s").absolutePath)

            if (mp3Bitrate != null) {
                request
                    .addOption("--extract-audio")
                    .addOption("--audio-format", "mp3")
                    .addOption("--audio-quality", "${mp3Bitrate}K")
            }
            val rate = DownloadSettings.rateKiB(context)
            if (rate > 0) request.addOption("--limit-rate", "${rate}K")

            val response =
                ExtractorAccess.useEngine {
                    YoutubeDL.getInstance().execute(
                        request,
                        processId(video.id),
                    ) { _, _, line ->
                        DownloadProgressParser.percent(line)?.let(onProgress)
                        DownloadProgressParser.speed(line)?.let(onSpeed)
                    }
                }

            if (response.exitCode != 0) {
                throw readableError(IllegalStateException(response.err.ifBlank { "El extractor rechazó la descarga." }))
            }
            folder
                .listFiles()
                ?.filter {
                    it.isFile &&
                        it.length() > 0L &&
                        it.nameWithoutExtension == safeId &&
                        it.extension.lowercase() in setOf("mp4", "mkv", "webm", "mov", "m4a", "mp3", "opus", "ogg", "aac")
                }?.maxByOrNull { it.length() } ?: throw IllegalStateException("No se encontró el archivo descargado.")
        }

    private fun readableError(cause: Exception): IllegalStateException {
        val detail = cause.message.orEmpty()
        // yt-dlp often writes warnings before the actual error. Do not mistake
        // "attempting impersonation" or an outdated-version warning for a fatal cause.
        val errorLine =
            detail
                .lines()
                .lastOrNull {
                    it.contains("ERROR:", ignoreCase = true) ||
                        it.contains("HTTP Error", ignoreCase = true)
                }?.substringAfter("ERROR:", missingDelimiterValue = detail)
                .orEmpty()
                .trim()
        val fatal = errorLine.ifBlank { detail }
        val message =
            when {
                fatal.contains("HTTP Error 429", ignoreCase = true) ->
                    "TikTok limitó temporalmente las solicitudes (429). Espera unos minutos y reintenta."
                fatal.contains("HTTP Error 403", ignoreCase = true) ->
                    "TikTok rechazó el acceso al archivo (403). Puede requerir sesión autorizada."
                fatal.contains("login", ignoreCase = true) ||
                    fatal.contains("private video", ignoreCase = true) ->
                    "El video requiere autenticación o no es público."
                fatal.contains("Unable to extract webpage video data", ignoreCase = true) ||
                    fatal.contains("secondary user ID", ignoreCase = true) ->
                    "TikTok cambió la respuesta del sitio. Actualiza yt-dlp y vuelve a intentar."
                fatal.contains("impersonat", ignoreCase = true) &&
                    (
                        fatal.contains("required", ignoreCase = true) ||
                            fatal.contains("not available", ignoreCase = true)
                    ) ->
                    "El extractor requiere compatibilidad de navegador no disponible."
                else -> {
                    val concise = fatal.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
                    "Falló la descarga: " + MetadataSanitizer.error(concise).take(210)
                }
            }
        return IllegalStateException(message, cause)
    }

    fun cancel(videoId: String) {
        photoSource.cancel(videoId)
        if (!videoId.startsWith(TikTokPhotoSource.PHOTO_ENTRY_PREFIX)) {
            YoutubeDL.getInstance().destroyProcessById(processId(videoId))
        }
    }

    private fun processId(id: String) =
        "tikitoki_${java.security.MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).take(12).joinToString("") {
            "%02x".format(
                it.toInt() and 0xff,
            )
        }}"

    companion object {
        const val DEFAULT_FORMAT =
            "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best"
    }
}
