package com.tikitoki.downloader

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick

internal fun ComposeTestRule.acceptFirstRunNoticeIfShown() {
    waitUntil(timeoutMillis = 10_000) {
        onAllNodesWithText("Entendido").fetchSemanticsNodes().isNotEmpty() ||
            onAllNodesWithText("Inicio").fetchSemanticsNodes().isNotEmpty()
    }
    if (onAllNodesWithText("Entendido").fetchSemanticsNodes().isNotEmpty()) {
        onNodeWithText("Entendido").performClick()
    }
}
