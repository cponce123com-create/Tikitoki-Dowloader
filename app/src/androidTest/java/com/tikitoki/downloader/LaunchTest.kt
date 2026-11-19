package com.tikitoki.downloader

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LaunchTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun acknowledgeNotice() {
        compose.acceptFirstRunNoticeIfShown()
    }

    @Test fun applicationStartsAndDisplaysItsHeader() {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Baja.pe").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Baja.pe").assertIsDisplayed()
    }

    @Test
    fun consecutiveSharesReachTheOpenActivityInOneTask() {
        val openActivity = compose.activity
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val component = ComponentName(context, MainActivity::class.java)
        val activityManager = checkNotNull(context.getSystemService(ActivityManager::class.java))

        for (videoId in 1..3) {
            // HTTP links exercise intent delivery without starting a network analysis.
            val link = "http://www.tiktok.com/@example/video/$videoId"
            val shareIntent =
                Intent(Intent.ACTION_SEND)
                    .setComponent(component)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, link)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(shareIntent)

            compose.waitUntil(timeoutMillis = 5_000) {
                compose.runOnUiThread { openActivity.intent.getStringExtra(Intent.EXTRA_TEXT) == link }
            }
        }

        val mainTasks = activityManager.appTasks.count { it.taskInfo?.baseIntent?.component == component }
        assertEquals(1, mainTasks)
    }
}
