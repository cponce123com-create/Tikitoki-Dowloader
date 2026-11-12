package com.tikitoki.downloader.domain

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IncomingIntentReaderTest {
    @Test fun validSharedLink() {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "https://www.tiktok.com/@creator/video/123")
        assertTrue(IncomingIntentReader.read(intent).startsWith("https://www.tiktok.com/"))
    }

    @Test fun maliciousSharedPayloadsAreRejected() {
        val payloads = listOf(
            "https://www.tiktok.com/@x/video/1\n--exec=evil",
            "https://youtube.com.evil.example/watch?v=1",
            "https://youtube.com/watch?v=1 --output=/tmp/a",
            "file:///etc/passwd"
        )
        payloads.forEach {
            val intent = Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, it)
            assertEquals("", IncomingIntentReader.read(intent))
        }
    }

    @Test fun actionAndMimeAreChecked() {
        assertEquals("", IncomingIntentReader.read(Intent(Intent.ACTION_SEND).setType("image/png")
            .putExtra(Intent.EXTRA_TEXT, "https://youtube.com/watch?v=1")))
        assertEquals("", IncomingIntentReader.read(Intent(Intent.ACTION_SEND).setType("text/plain")))
        assertEquals("", IncomingIntentReader.read(Intent("custom.action")
            .putExtra(Intent.EXTRA_TEXT, "https://youtube.com/watch?v=1")))
    }

    @Test fun appLinkRequiresHttpsAndAllowlistedHost() {
        assertTrue(IncomingIntentReader.read(Intent(Intent.ACTION_VIEW,
            Uri.parse("https://vm.tiktok.com/ZM123/"))).startsWith("https://vm.tiktok.com/"))
        assertEquals("", IncomingIntentReader.read(Intent(Intent.ACTION_VIEW,
            Uri.parse("http://vm.tiktok.com/ZM123/"))))
        assertEquals("", IncomingIntentReader.read(Intent(Intent.ACTION_VIEW,
            Uri.parse("https://vm.tiktok.com.evil.test/ZM123/"))))
    }
}
