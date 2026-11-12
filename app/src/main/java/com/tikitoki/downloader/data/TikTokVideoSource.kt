package com.tikitoki.downloader.data

import android.content.Context
import android.os.Environment
import com.tikitoki.downloader.domain.TikTokLink
import com.tikitoki.downloader.domain.TikTokLinkType
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

data class VideoEntry(
    val id: String,
    val url: String,
    val title: String,
    val thumbnail: String? = null,
    val durationSeconds: Int = 0,
)

data class FormatChoice(
    val id: String,
    val label: String,
)

/**
 * Uses yt-dlp to request REAL content. TikTok may reject profile enumeration
 * or individual videos. The UI must surface such failures, never use fake data.
 */
class TikTokVideoSource(private val context: Context) {
    private var initialized = false

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
            TikTokLinkType.VIDEO -> listOf(fetchSingle(link.url))
            TikTokLinkType.SHORT_LINK -> listOf(fetchSingle(link.url))
        }
    }

    private fun fetchSingle(url: String): VideoEntry {
        val info = YoutubeDL.getInstance().getInfo(url)
        val id = info.id?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("El extractor no devolvió un identificador de video.")
        return VideoEntry(
            id = id,
            url = info.webpageUrl?.takeIf { it.startsWith("https://") } ?: url,
            title = info.title?.takeIf { it.isNotBlank() } ?: "Video $id",
            thumbnail = info.thumbnail,
            durationSeconds = info.duration,
        )
    }

    private fun inspectProfile(link: TikTokLink): List<VideoEntry> {
        // No arbitrary cap: yt-dlp does the paging where the site allows it.
        val request = YoutubeDLRequest(link.url)
            .addOption("--flat-playlist")
            .addOption("--dump-single-json")
            .addOption("--ignore-errors")
            .addOption("--no-warnings")

        val response = YoutubeDL.getInstance().execute(request, "profile_scan", { _, _, _ -> })
        val body = response.out.trim()
        if (body.isBlank()) {
            throw IllegalStateException("TikTok no devolvió publicaciones del perfil.")
        }
        val json = try { JSONObject(body) } catch (_: Exception) {
            throw IllegalStateException("Respuesta inesperada del extractor: el perfil no pudo analizarse.")
        }
        val entries = json.optJSONArray("entries")
            ?: throw IllegalStateException("El extractor no pudo enumerar los videos de este perfil.")
        val username = link.username
            ?: throw IllegalStateException("No se pudo identificar el perfil.")

        val seen = mutableSetOf<String>()
        return buildList {
            for (index in 0 until entries.length()) {
                val entry = entries.optJSONObject(index) ?: continue
                val id = entry.optString("id").takeIf { it.matches(Regex("[0-9]{5,30}")) } ?: continue
                if (!seen.add(id)) continue
                // Canonical URL synthesized from the extractor-confirmed ID and profile.
                val videoUrl = "https://www.tiktok.com/@$username/video/$id"
                add(
                    VideoEntry(
                        id = id,
                        url = videoUrl,
                        title = entry.optString("title").ifBlank { "Video $id" },
                        thumbnail = entry.optString("thumbnail").takeIf { it.startsWith("https://") },
                        durationSeconds = entry.optInt("duration", 0),
                    )
                )
            }
        }
    }

    suspend fun formats(video: VideoEntry): List<FormatChoice> = withContext(Dispatchers.IO) {
        ensureReady()
        val info: VideoInfo = YoutubeDL.getInstance().getInfo(video.url)
        val available = info.formats.orEmpty()
            .filter { format ->
                !format.formatId.isNullOrBlank() &&
                    format.ext == "mp4" &&
                    format.height > 0 &&
                    format.vcodec != "none" &&
                    format.acodec != "none"
            }
            .distinctBy { it.formatId }
            .sortedWith(compareByDescending<com.yausername.youtubedl_android.mapper.VideoFormat> { it.height }.thenByDescending { it.tbr })

        listOf(FormatChoice("best[ext=mp4]/best", "Máxima disponible (automático)")) +
            available.map { FormatChoice(it.formatId.orEmpty(), "${it.height}p · MP4 · ${it.formatId}") }
    }

    suspend fun download(video: VideoEntry, formatId: String, progress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            ensureReady()
            val folder = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "TikitokiDownloader"
            )
            if (!folder.exists() && !folder.mkdirs()) {
                throw IllegalStateException("No se pudo crear la carpeta de descargas.")
            }
            val safeId = video.id.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
            if (safeId.isEmpty()) throw IllegalStateException("Identificador de video inválido.")
            val request = YoutubeDLRequest(video.url)
                .addOption("--no-playlist")
                .addOption("--no-overwrites")
                .addOption("-f", formatId)
                .addOption("-o", File(folder, "$safeId.%(ext)s").absolutePath)

            // Progress callback runs on an extractor thread; callers must switch to UI thread.
            val response = YoutubeDL.getInstance().execute(
                request,
                "download_$safeId",
                { value, _, _ -> progress(value) },
            )
            if (response.exitCode != 0) {
                throw IllegalStateException("yt-dlp terminó con error: ${response.err.take(280)}")
            }
            val output = folder.listFiles()?.firstOrNull {
                it.nameWithoutExtension == safeId && it.isFile
            }
            output ?: throw IllegalStateException("La descarga finalizó, pero no se encontró el archivo.")
        }
}
