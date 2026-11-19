package com.tikitoki.downloader.data

import com.tikitoki.downloader.domain.DownloadFormatPreference
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal data class FormatInspection(
    val video: VideoEntry,
    val choices: List<FormatChoice>?,
)

internal data class DownloadSelection(
    val video: VideoEntry,
    val formatId: String,
)

internal data class DownloadFormatPlan(
    val choice: FormatChoice,
    val downloads: List<DownloadSelection>,
)

private const val FORMAT_CHECK_PARALLELISM = 3

internal suspend fun checkDownloadFormats(
    videos: List<VideoEntry>,
    readFormats: suspend (VideoEntry) -> List<FormatChoice>?,
    onProgress: (List<FormatInspection>) -> Unit,
) {
    val inspected = mutableListOf<FormatInspection>()
    for (group in videos.chunked(FORMAT_CHECK_PARALLELISM)) {
        val results =
            coroutineScope {
                group.map { video -> async { FormatInspection(video, readFormats(video)) } }.awaitAll()
            }
        inspected.addAll(results)
        onProgress(inspected.toList())
    }
}

internal fun downloadFormatPlans(
    inspected: List<FormatInspection>,
    batch: Boolean,
): List<DownloadFormatPlan> {
    val grouped = linkedMapOf<String, MutableList<Pair<FormatChoice, DownloadSelection>>>()
    for ((video, choices) in inspected) {
        for (choice in choices.orEmpty().distinctBy(::qualityKey)) {
            val key = if (batch) qualityKey(choice) else choice.id
            grouped.getOrPut(key) { mutableListOf() }.add(choice to DownloadSelection(video, choice.id))
        }
    }
    return grouped
        .map { (key, entries) ->
            val first = entries.first().first
            val option = if (batch) first.copy(id = key, label = first.shortLabel) else first
            DownloadFormatPlan(option, entries.map { it.second })
        }.sortedWith(compareBy({ it.choice.isAudio }, { qualityOrder(it.choice) }))
}

private fun qualityKey(choice: FormatChoice): String {
    if (choice.id.startsWith("bajaaudio-")) return "bajaaudio-original"
    return DownloadFormatPreference.fromChoice(choice.id, choice.label, choice.height)
}

private fun qualityOrder(choice: FormatChoice): Int {
    val bitrate = FormatOptions.mp3Bitrate(choice.id)
    return choice.height ?: bitrate ?: Int.MAX_VALUE
}
