package com.tikitoki.downloader.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadFormatPlansTest {
    @Test
    fun groupsPlaylistByVerifiedQualityAndKeepsTheFormatIdOfEachVideo() {
        val first = video("one")
        val second = video("two")
        val third = video("three")
        val mp3 = FormatChoice("bajamp3-128", "MP3", isAudio = true)
        val inspected =
            listOf(
                FormatInspection(
                    first,
                    listOf(FormatChoice("a720", "HD · 12 MB", height = 720, shortLabel = "HD"), mp3),
                ),
                FormatInspection(
                    second,
                    listOf(
                        FormatChoice("b720+bestaudio", "HD", height = 720),
                        FormatChoice("b360", "360p", height = 360),
                    ),
                ),
                FormatInspection(third, null),
            )
        val plans = downloadFormatPlans(inspected, batch = true)
        assertEquals(listOf("height:360", "height:720", "bajamp3-128"), plans.map { it.choice.id })
        assertEquals("HD", plans[1].choice.label)
        assertEquals(
            listOf(DownloadSelection(first, "a720"), DownloadSelection(second, "b720+bestaudio")),
            plans[1].downloads,
        )
        assertEquals(listOf(DownloadSelection(first, "bajamp3-128")), plans.last().downloads)
    }

    @Test
    fun keepsSizeForASingleVideo() {
        val video = video("single")
        val option = FormatChoice("a720", "HD · 12 MB", height = 720, shortLabel = "HD")
        val plan = downloadFormatPlans(listOf(FormatInspection(video, listOf(option))), batch = false).single()
        assertEquals(option, plan.choice)
        assertEquals(listOf(DownloadSelection(video, "a720")), plan.downloads)
    }

    private fun video(id: String): VideoEntry = VideoEntry(id, "https://www.youtube.com/watch?v=$id", id)
}
