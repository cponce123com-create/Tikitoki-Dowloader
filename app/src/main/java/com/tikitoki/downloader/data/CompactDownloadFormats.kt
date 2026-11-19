package com.tikitoki.downloader.data

import android.content.Context
import android.text.format.Formatter
import com.tikitoki.downloader.R
import com.tikitoki.downloader.domain.DownloadInputPolicy
import com.tikitoki.downloader.domain.Mp3Preset
import com.yausername.youtubedl_android.mapper.VideoFormat
import com.yausername.youtubedl_android.mapper.VideoInfo

/** User-facing qualities available for an inspected item. */
internal object CompactDownloadFormats {
    private const val LOW_QUALITY = 240
    private const val STANDARD_QUALITY = 360
    private const val HD = 720
    private const val FHD = 1080
    private const val ORIGINAL_AUDIO_RATE = 360
    private const val BYTES_PER_KILOBIT = 125L
    private val videoHeights = listOf(LOW_QUALITY, STANDARD_QUALITY, HD, FHD)

    fun options(
        context: Context,
        info: VideoInfo,
        duration: Int,
    ): List<FormatChoice> {
        val formats = info.formats.orEmpty().ifEmpty { info.requestedFormats.orEmpty() }
        val streams = formats.filter { it.safeId() != null }
        return videoChoices(context, streams) + audioChoices(context, streams, duration, info.ext in audioExtensions)
    }

    internal fun videoTracks(streams: List<VideoFormat>): Map<Int, VideoFormat> {
        val hasSeparateAudio = streams.any { it.hasAudio() && (it.vcodec == "none" || it.ext in audioExtensions) }
        val candidates = streams.filter { it.hasVideo() && (it.hasAudio() || hasSeparateAudio) }
        val ranking = compareBy<VideoFormat>({ it.ext == "mp4" }, { it.hasAudio() }, { it.tbr })
        return videoHeights
            .mapNotNull { height ->
                candidates
                    .filter { it.shortSide() == height }
                    .maxWithOrNull(ranking)
                    ?.let { height to it }
            }.toMap()
    }

    private fun videoChoices(
        context: Context,
        streams: List<VideoFormat>,
    ): List<FormatChoice> =
        videoTracks(streams).mapNotNull { (height, video) ->
            val id = video.safeId() ?: return@mapNotNull null
            val label =
                when (height) {
                    HD -> context.getString(R.string.format_video_hd)
                    FHD -> context.getString(R.string.format_video_fhd)
                    else -> context.getString(R.string.format_video_resolution, height)
                }
            val selector = safeSelector(if (video.hasAudio()) id else "$id+bestaudio") ?: return@mapNotNull null
            FormatChoice(
                selector,
                withSize(label, if (video.hasAudio()) streamSize(context, video) else null),
                height = height,
                shortLabel = label,
            )
        }

    internal fun mp3Rates(streams: List<VideoFormat>): List<Int> {
        val sourceRate = streams.filter { it.hasAudio() }.maxOfOrNull { it.abr } ?: return emptyList()
        return Mp3Preset.averageRates.filter { it <= sourceRate }
    }

    private fun audioChoices(
        context: Context,
        streams: List<VideoFormat>,
        duration: Int,
        directAudio: Boolean,
    ): List<FormatChoice> {
        if (!directAudio && streams.none { it.hasAudio() }) return emptyList()
        return buildList {
            add(FormatChoice(Mp3Preset.DEFAULT_ID, context.getString(R.string.format_audio_mp3), isAudio = true))
            for (rate in mp3Rates(streams)) {
                val label = context.getString(R.string.format_audio_mp3_rate, rate)
                val bytes = duration.toLong() * rate * BYTES_PER_KILOBIT
                val size = bytes.takeIf { it > 0 }?.let { approximateSize(context, it) }
                add(FormatChoice("bajamp3-$rate", withSize(label, size), isAudio = true, shortLabel = label))
            }
            originalTrack(streams)?.let { track ->
                val label = context.getString(R.string.format_audio_original_rate, ORIGINAL_AUDIO_RATE)
                val id = safeSelector("bajaaudio-${track.formatId}") ?: return@let
                add(FormatChoice(id, withSize(label, streamSize(context, track)), isAudio = true, shortLabel = label))
            }
        }
    }

    internal fun originalTrack(streams: List<VideoFormat>): VideoFormat? =
        streams
            .filter {
                it.vcodec == "none" && it.hasAudio() && it.abr == ORIGINAL_AUDIO_RATE && it.ext != "mp3"
            }.maxByOrNull { it.ext == "m4a" }

    private fun VideoFormat.safeId(): String? = formatId?.let(::safeSelector)

    private fun streamSize(
        context: Context,
        track: VideoFormat,
    ): String? =
        when {
            track.fileSize > 0 -> Formatter.formatShortFileSize(context, track.fileSize)
            track.fileSizeApproximate > 0 -> approximateSize(context, track.fileSizeApproximate)
            else -> null
        }

    private fun approximateSize(
        context: Context,
        bytes: Long,
    ): String = context.getString(R.string.format_size_approx, Formatter.formatShortFileSize(context, bytes))

    private fun withSize(
        label: String,
        size: String?,
    ): String = if (size == null) label else "$label · $size"
}

private fun VideoFormat.hasVideo(): Boolean = height > 0 && !vcodec.isNullOrBlank() && vcodec != "none"

private val audioExtensions = setOf("aac", "flac", "m4a", "mp3", "ogg", "opus", "wav")

internal fun VideoFormat.hasAudio(): Boolean =
    when {
        acodec == "none" -> false
        !acodec.isNullOrBlank() -> true
        else -> abr > 0 || ext in audioExtensions
    }

private fun VideoFormat.shortSide(): Int = minOf(width.takeIf { it > 0 } ?: height, height)

private fun safeSelector(value: String): String? =
    runCatching {
        DownloadInputPolicy.safeFormatId(value)
    }.getOrNull()
