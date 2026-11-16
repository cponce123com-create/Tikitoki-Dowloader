package com.tikitoki.downloader

import android.content.Context
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirstRunNoticeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun noticeIsShownOnceAfterAcceptance() {
        val prefs = compose.activity.getSharedPreferences("tikitoki_settings", Context.MODE_PRIVATE)
        val existed = prefs.contains("first_run_notice_accepted")
        val previous = prefs.getBoolean("first_run_notice_accepted", false)
        try {
            prefs.edit().remove("first_run_notice_accepted").commit()
            compose.activityRule.scenario.recreate()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Entendido").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Antes de empezar").assertIsDisplayed()
            compose.onNodeWithText("Entendido").performClick()
            compose.activityRule.scenario.recreate()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Inicio").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Antes de empezar").assertDoesNotExist()
        } finally {
            val edit = prefs.edit()
            if (existed) edit.putBoolean("first_run_notice_accepted", previous) else edit.remove("first_run_notice_accepted")
            edit.commit()
        }
    }
}
