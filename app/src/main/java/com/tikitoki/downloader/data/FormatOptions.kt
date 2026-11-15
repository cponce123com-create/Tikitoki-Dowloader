package com.tikitoki.downloader.data

import android.content.Context
import android.text.format.Formatter
import com.tikitoki.downloader.R
import com.tikitoki.downloader.domain.DownloadInputPolicy
import com.yausername.youtubedl_android.mapper.VideoFormat
import java.util.Locale

/** Builds options from extractor metadata; batch presets are requests, not guarantees. */
internal object FormatOptions {
    val bitrates = listOf(128, 192, 256, 320)
    fun mp3Bitrate(id: String): Int? = id.removePrefix("bajamp3-").takeIf { id.startsWith("bajamp3-") }
        ?.toIntOrNull()?.takeIf { it in bitrates }

    fun mp3Options(context: Context, durationSeconds: Int): List<FormatChoice> = bitrates.map { rate ->
        val bytes = if (durationSeconds > 0) durationSeconds.toLong() * rate * 1000L / 8L else null
        val size = bytes?.let { context.getString(R.string.format_size_approx, Formatter.formatShortFileSize(context, it)) }
            ?: context.getString(R.string.format_size_unknown)
        FormatChoice("bajamp3-$rate", context.getString(R.string.format_mp3, rate, size))
    }

    fun batch(context: Context): List<FormatChoice> {
        val heights = listOf(240, 360, 480, 720, 1080, 1440, 2160)
        return listOf(FormatChoice(TikTokVideoSource.DEFAULT_FORMAT, context.getString(R.string.format_video_best))) +
            heights.map { height -> FormatChoice("bestvideo[height<=$height]+bestaudio/best[height<=$height]", context.getString(R.string.format_video_upto, height)) } +
            listOf(FormatChoice("bestaudio/best", context.getString(R.string.format_audio_original))) + mp3Options(context, 0)
    }

    fun individual(context: Context, formats: List<VideoFormat>, durationSeconds: Int): List<FormatChoice> {
        val videoOptions = formats.asSequence()
            .filter { !it.formatId.isNullOrBlank() && it.height > 0 && !it.vcodec.isNullOrBlank() && it.vcodec != "none" }
            .distinctBy { it.formatId }
            .sortedByDescending { it.height }
            .mapNotNull { f ->
                val id = f.formatId ?: return@mapNotNull null
                val videoOnly = f.acodec.isNullOrBlank() || f.acodec == "none"
                val selector = if (videoOnly) "$id+bestaudio/best" else id
                val safe = runCatching { DownloadInputPolicy.safeFormatId(selector) }.getOrNull() ?: return@mapNotNull null
                // A video-only stream needs a separate audio track, so video bytes alone are not the final size.
                val size = when {
                    videoOnly -> context.getString(R.string.format_size_unknown)
                    f.fileSize > 0 -> Formatter.formatShortFileSize(context, f.fileSize)
                    f.fileSizeApproximate > 0 -> context.getString(R.string.format_size_approx, Formatter.formatShortFileSize(context, f.fileSizeApproximate))
                    else -> context.getString(R.string.format_size_unknown)
                }
                val ext = f.ext?.uppercase(Locale.ROOT) ?: context.getString(R.string.format_video_type)
                FormatChoice(safe, context.getString(R.string.format_video_option, f.height, ext, size))
            }.toList()
        val audioOptions = formats.asSequence()
            .filter { !it.formatId.isNullOrBlank() && !it.acodec.isNullOrBlank() && it.acodec != "none" && (it.vcodec.isNullOrBlank() || it.vcodec == "none") }
            .distinctBy { it.formatId }
            .sortedByDescending { it.abr }
            .mapNotNull { f ->
                val sourceId = f.formatId ?: return@mapNotNull null
                val id = "bajaaudio-$sourceId"
                if (runCatching { DownloadInputPolicy.safeFormatId(id) }.isFailure) return@mapNotNull null
                val size = when {
                    f.fileSize > 0 -> Formatter.formatShortFileSize(context, f.fileSize)
                    f.fileSizeApproximate > 0 -> context.getString(R.string.format_size_approx, Formatter.formatShortFileSize(context, f.fileSizeApproximate))
                    else -> context.getString(R.string.format_size_unknown)
                }
                val ext = f.ext?.uppercase(Locale.ROOT) ?: context.getString(R.string.format_audio_type)
                val bitrate = if (f.abr > 0) context.getString(R.string.format_audio_bitrate, f.abr) else context.getString(R.string.format_audio_bitrate_unknown)
                FormatChoice(id, context.getString(R.string.format_audio_option, ext, bitrate, size))
            }.toList()
        return listOf(FormatChoice(TikTokVideoSource.DEFAULT_FORMAT, context.getString(R.string.format_video_best))) +
            videoOptions + listOf(FormatChoice("bestaudio/best", context.getString(R.string.format_audio_original))) +
            audioOptions + mp3Options(context, durationSeconds)
    }
}
