package com.tikitoki.downloader.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SafeAreaTopBarTest {
    @get:Rule val compose = createComposeRule()

    @Test fun headerAndActionsStayBelowSystemAreaAndSideCutouts() {
        val selected = mutableStateOf(false)
        val fakeInsets = WindowInsets(left = 24, top = 96, right = 24, bottom = 0)
        compose.setContent {
            Scaffold(
                contentWindowInsets = fakeInsets,
                topBar = {
                    SafeAreaTopBar(insets = fakeInsets) {
                        if (selected.value) Box(Modifier.fillMaxWidth().height(48.dp).testTag("actions"))
                    }
                },
            ) { padding ->
                Box(Modifier.padding(padding).fillMaxWidth().height(40.dp).testTag("header"))
            }
        }
        val withoutSelection = compose.onNodeWithTag("header").fetchSemanticsNode().boundsInRoot
        assertTrue("Header must clear the status bar without a selection", withoutSelection.top >= 96f)
        assertTrue("Header must clear a side cutout", withoutSelection.left >= 24f)

        compose.runOnIdle { selected.value = true }
        val actions = compose.onNodeWithTag("actions").fetchSemanticsNode().boundsInRoot
        val header = compose.onNodeWithTag("header").fetchSemanticsNode().boundsInRoot
        assertTrue("Actions must clear the status bar", actions.top >= 96f)
        assertTrue("Actions must clear a side cutout", actions.left >= 24f)
        assertTrue("Header must remain below the action bar", header.top >= actions.bottom)
    }
}
