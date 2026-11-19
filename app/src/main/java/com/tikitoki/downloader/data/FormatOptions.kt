package com.tikitoki.downloader.data

import android.content.Context
import android.text.format.Formatter
import com.tikitoki.downloader.R
import com.tikitoki.downloader.domain.DownloadFormatPreference
import com.tikitoki.downloader.domain.DownloadInputPolicy
import com.tikitoki.downloader.domain.Mp3Preset
import com.yausername.youtubedl_android.mapper.VideoFormat
import java.util.Locale

/** Builds options from extractor metadata; batch presets are requests, not guarantees. */
internal object FormatOptions {
    fun mp3Bitrate(id: String): Int? = Mp3Preset.bitrate(id)

    fun mp3Options(
        context: Context,
        durationSeconds: Int,
    ): List<FormatChoice> =
        Mp3Preset.legacyRates.map { rate ->
            val bytes = if (durationSeconds > 0) durationSeconds.toLong() * rate * 1000L / 8L else null
            val size =
                if (bytes != null) {
                    val readableSize = Formatter.formatShortFileSize(context, bytes)
                    context.getString(R.string.format_size_approx, readableSize)
                } else {
                    context.getString(R.string.format_size_unknown)
                }
            FormatChoice("bajamp3-$rate", context.getString(R.string.format_mp3, rate, size), isAudio = true)
        }

    fun batch(context: Context, extraHeights: Set<Int> = emptySet()): List<FormatChoice> {
        val heights = (listOf(240, 360, 480, 720, 1080, 1440, 2160) + extraHeights.filter { it in 1..16384 }).distinct().sorted()
        return listOf(FormatChoice(TikTokVideoSource.DEFAULT_FORMAT, context.getString(R.string.format_video_best))) +
            heights.map { height -> FormatChoice("bestvideo[height<=$height]+bestaudio/best[height<=$height]", context.getString(R.string.format_video_upto, height)) } +
            listOf(FormatChoice("bestaudio", context.getString(R.string.format_audio_original), isAudio = true)) + mp3Options(context, 0)
    }

    fun individual(context: Context, formats: List<VideoFormat>, durationSeconds: Int): List<FormatChoice> {
        val hasAudio = formats.any { !it.acodec.isNullOrBlank() && it.acodec != "none" }
        val hasAudioOnly = formats.any { !it.formatId.isNullOrBlank() && !it.acodec.isNullOrBlank() && it.acodec != "none" && it.vcodec == "none" }
        val videoOptions = formats.asSequence()
            .filter { !it.formatId.isNullOrBlank() && it.height > 0 && !it.vcodec.isNullOrBlank() && it.vcodec != "none" }
            .distinctBy { it.formatId }
            .sortedByDescending { it.height }
            .mapNotNull { f ->
                val id = f.formatId ?: return@mapNotNull null
                val videoOnly = f.acodec.isNullOrBlank() || f.acodec == "none"
                if (videoOnly && !hasAudioOnly) return@mapNotNull null
                val selector = if (videoOnly) "$id+bestaudio" else id
                val safe = runCatching { DownloadInputPolicy.safeFormatId(selector) }.getOrNull() ?: return@mapNotNull null
                // A video-only stream needs a separate audio track, so video bytes alone are not the final size.
                val size = when {
                    videoOnly -> context.getString(R.string.format_size_unknown)
                    f.fileSize > 0 -> Formatter.formatShortFileSize(context, f.fileSize)
                    f.fileSizeApproximate > 0 -> context.getString(R.string.format_size_approx, Formatter.formatShortFileSize(context, f.fileSizeApproximate))
                    else -> context.getString(R.string.format_size_unknown)
                }
                val ext = f.ext?.uppercase(Locale.ROOT) ?: context.getString(R.string.format_video_type)
                FormatChoice(safe, context.getString(R.string.format_video_option, f.height, ext, size), height = f.height)
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
                FormatChoice(id, context.getString(R.string.format_audio_option, ext, bitrate, size), isAudio = true)
            }.toList()
        val bestVideo = if (videoOptions.isNotEmpty()) listOf(FormatChoice(TikTokVideoSource.DEFAULT_FORMAT, context.getString(R.string.format_video_best))) else emptyList()
        val originalAudio = if (hasAudioOnly) listOf(FormatChoice("bestaudio", context.getString(R.string.format_audio_original), isAudio = true)) + audioOptions else emptyList()
        val audio = originalAudio + if (hasAudio) mp3Options(context, durationSeconds) else emptyList()
        return bestVideo + videoOptions + audio
    }

    fun preferred(
        options: List<FormatChoice>,
        saved: String?,
        compact: Boolean,
    ): FormatChoice {
        val exact =
            options.firstOrNull {
                DownloadFormatPreference.matches(saved, it.id, it.label, TikTokVideoSource.DEFAULT_FORMAT, it.height)
            }
        if (exact != null) return exact
        val fallback =
            when {
                !compact -> null
                DownloadFormatPreference.prefersAudio(saved) -> options.firstOrNull { it.isAudio }
                else -> options.lastOrNull { !it.isAudio }
            }
        return fallback ?: options.first()
    }
}
