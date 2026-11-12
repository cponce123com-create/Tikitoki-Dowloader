package com.tikitoki.downloader.domain

import org.junit.Assert.*
import org.junit.Test

class DownloadInputPolicyTest {
    @Test fun canonicalizesExternalUrlsBeforePersistence() {
        assertEquals(
            "https://www.youtube.com/watch?v=a%2Bb",
            DownloadInputPolicy.canonicalUrl("https://WWW.YOUTUBE.COM/watch?v=a%2Bb")
        )
        assertEquals(
            "https://www.tiktok.com/@test/video/123",
            DownloadInputPolicy.canonicalUrl("https://www.tiktok.com/@test/video/123")
        )
    }

    @Test fun blocksHostileUrlsAtQueueBoundary() {
        listOf(
            "http://youtube.com/watch?v=1",
            "https://youtube.com.evil.test/watch",
            "https://youtube.com/--exec",
            "https://youtube.com/x\n--output=evil",
            "https://user@youtube.com/watch?v=1",
            "file:///tmp/private",
            "https://youtube.com/x\u202e"
        ).forEach { raw ->
            assertThrows(IllegalArgumentException::class.java) {
                DownloadInputPolicy.canonicalUrl(raw)
            }
        }
    }

    @Test fun allowsOnlySafeFormatSelectors() {
        listOf(
            "best", "bestaudio/best", "137+bestaudio/best",
            "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best",
            "mp4", "249", "bestvideo[height<=720]/best"
        ).forEach { assertEquals(it, DownloadInputPolicy.safeFormatId(it)) }
    }

    @Test fun rejectsCommandLineSwitchesAndUntrustedFormatValues() {
        listOf(
            "", " ", "--exec", "--netrc-cmd=evil", "--config-location=x",
            "-o", "-P", "best --exec=evil", "best\n--output=x",
            "best;rm", "best$(whoami)", "best|cat", "a".repeat(161)
        ).forEach { raw ->
            assertThrows(IllegalArgumentException::class.java) {
                DownloadInputPolicy.safeFormatId(raw)
            }
        }
    }
}
