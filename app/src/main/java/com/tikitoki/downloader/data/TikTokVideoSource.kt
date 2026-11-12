package com.tikitoki.downloader.data

import android.content.Context
import com.tikitoki.downloader.domain.TikTokLink
import com.tikitoki.downloader.domain.TikTokLinkType
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.json.JSONObject
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class VideoEntry(
    val id: String,
    val url: String,
    val title: String,
    val thumbnail: String? = null,
    val durationSeconds: Int = 0,
)

data class FormatChoice(val id: String, val label: String)

/**
 * Calls the bundled yt-dlp extractor. No generated/fake results: all entries must
 * originate in a returned TikTok extractor payload. TikTok might reject queries.
 */
class TikTokVideoSource(private val context: Context) {
    private var initialized = false

    @Synchronized
    private fun ensureReady() {
        if (!initialized) {
            YoutubeDL.getInstance().init(context.applicationContext)
            FFmpeg.getInstance().init(context.applicationContext)
            initialized = true
        }
    }

    suspend fun inspect(link: TikTokLink): List<VideoEntry> = withContext(Dispatchers.IO) {
        ensureReady()
        when (link.type) {
            TikTokLinkType.PROFILE -> inspectProfile(link)
            TikTokLinkType.VIDEO, TikTokLinkType.SHORT_LINK -> listOf(fetchSingle(link.url))
        }
    }

    private fun fetchSingle(url: String): VideoEntry {
        val info = YoutubeDL.getInstance().getInfo(url)
        val id = info.id?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("El extractor no devolvió un ID de video.")
        return VideoEntry(
            id = id,
            url = info.webpageUrl?.takeIf { it.startsWith("https://") } ?: url,
            title = info.title?.takeIf { it.isNotBlank() } ?: "Video $id",
            thumbnail = info.thumbnail,
            durationSeconds = info.duration,
        )
    }

    private fun inspectProfile(link: TikTokLink): List<VideoEntry> {
        val request = YoutubeDLRequest(link.url)
            .addOption("--flat-playlist")
            .addOption("--dump-single-json")
            .addOption("--ignore-errors")
            .addOption("--no-warnings")

        val response = YoutubeDL.getInstance().execute(request, "tikitoki_scan")
        if (response.exitCode != 0 && response.out.isBlank()) {
            throw IllegalStateException("Error del extractor: ${response.err.take(240)}")
        }
        val json = try {
            JSONObject(response.out.trim())
        } catch (_: Exception) {
            throw IllegalStateException("TikTok no devolvió un catálogo válido de publicaciones.")
        }
        val entries = json.optJSONArray("entries")
            ?: throw IllegalStateException("No se pudo enumerar el perfil. Puede estar restringido.")
        val username = link.username ?: throw IllegalStateException("No se identificó el usuario.")

        val seen = mutableSetOf<String>()
        return buildList {
            for (index in 0 until entries.length()) {
                val entry = entries.optJSONObject(index) ?: continue
                val id = entry.optString("id").takeIf { it.matches(Regex("[0-9]{5,30}")) } ?: continue
                if (!seen.add(id)) continue
                add(
                    VideoEntry(
                        id = id,
                        url = "https://www.tiktok.com/@$username/video/$id",
                        title = entry.optString("title").ifBlank { "Video $id" },
                        thumbnail = entry.optString("thumbnail").takeIf { it.startsWith("https://") },
                        durationSeconds = entry.optInt("duration"),
                    )
                )
            }
        }
    }

    suspend fun formats(video: VideoEntry): List<FormatChoice> = withContext(Dispatchers.IO) {
        ensureReady()
        val info = YoutubeDL.getInstance().getInfo(video.url)
        val available = info.formats.orEmpty()
            .filter {
                !it.formatId.isNullOrBlank() &&
                    it.height > 0 && it.vcodec != "none"
            }
            .distinctBy { it.formatId }
            .sortedByDescending { it.height }

        listOf(FormatChoice(DEFAULT_FORMAT, "Mejor calidad disponible")) +
            available.map { f ->
                val id = f.formatId.orEmpty()
                val selector = if (f.acodec == "none") "$id+bestaudio/best" else id
                FormatChoice(selector, "${f.height}p · ${f.ext?.uppercase() ?: "Video"} · $id")
            }
    }

    suspend fun downloadToCache(
        video: VideoEntry,
        formatId: String,
        onProgress: (Int) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        ensureReady()
        val folder = File(context.cacheDir, "tikitoki")
        if (!folder.exists() && !folder.mkdirs()) {
            throw IllegalStateException("No se pudo crear la carpeta temporal.")
        }
        val safeId = video.id.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
        if (safeId.isBlank()) throw IllegalArgumentException("ID de video inválido.")
        val request = YoutubeDLRequest(video.url)
            .addOption("--no-playlist")
            .addOption("--newline")
            .addOption("--continue")
            .addOption("-f", formatId)
            .addOption("--merge-output-format", "mp4")
            .addOption("-o", File(folder, "$safeId.%(ext)s").absolutePath)

        val response = YoutubeDL.getInstance().execute(
            request,
            processId(video.id),
        ) { progress, _, _ -> onProgress(progress.toInt().coerceIn(0, 100)) }

        if (response.exitCode != 0) {
            throw IllegalStateException(
                response.err.takeLast(400).ifBlank { "El extractor rechazó la descarga." }
            )
        }
        folder.listFiles()?.firstOrNull {
            it.isFile && it.nameWithoutExtension == safeId &&
                it.extension.lowercase() in setOf("mp4", "mkv", "webm", "mov")
        } ?: throw IllegalStateException("No se encontró el archivo descargado.")
    }

    fun cancel(videoId: String) {
        YoutubeDL.getInstance().destroyProcessById(processId(videoId))
    }

    private fun processId(id: String) = "tikitoki_${id.take(35)}"

    companion object {
        const val DEFAULT_FORMAT =
            "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best"
    }
}
