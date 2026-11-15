package com.tikitoki.downloader.domain

/** Derive a public thumbnail only for a verified YouTube video and a valid video ID. */
internal object ThumbnailUrls {
    private val youtubeId = Regex("^[A-Za-z0-9_-]{11}$")

    fun youtube(videoId: String, videoUrl: String): String? {
        if (MediaLinkParser.parse(videoUrl)?.platform != MediaPlatform.YOUTUBE) return null
        val id = videoId.substringAfterLast(':')
        if (!youtubeId.matches(id)) return null
        return "https://i.ytimg.com/vi/$id/hqdefault.jpg"
    }
}
