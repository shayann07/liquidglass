package com.wexpa.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

/** #145: at accessibility text sizes a held tab shows itself magnified in a centred viewer; a tap, or normal text, does not. */
@OptIn(ExperimentalTestApi::class)
class GlassLargeContentViewerTest {
    private val geometry = GlassTabBarGeometry(barWidth = 500f, inset = 6f, count = 5)

    private fun ComposeUiTest.bar(fontScale: Float) = setContent {
        CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
            val state = rememberLiquidGlassState(Color.Black, renderScale = .25f)
            Box(Modifier.size(500.dp, 200.dp)) {
                Box(Modifier.fillMaxSize().background(Color.DarkGray).liquidGlassSource(state))
                GlassTabBar(state, 5, 0, {}, Modifier.testTag("bar").width(500.dp), style = GlassTabBarStyle.Calm()) { _, _ ->
                    Box(Modifier.size(20.dp).background(Color.White))
                }
            }
        }
    }

    private fun ComposeUiTest.viewer() = onAllNodesWithTag(GlassLargeContentViewerTag, useUnmergedTree = true)

    private fun ComposeUiTest.hold(): SemanticsNodeInteraction {
        val node = onNodeWithTag(GlassTabBarSemantics.TAG, useUnmergedTree = true)
        node.performTouchInput { down(Offset(geometry.centreOf(2), 31f)); advanceEventTime(300); moveTo(Offset(geometry.centreOf(2), 31f)) }
        mainClock.advanceTimeBy(400)
        return node
    }

    @Test fun aHeldTabShowsTheViewerAtLargeText() = runComposeUiTest(testTimeout = 4.minutes) {
        bar(fontScale = 2f); waitForIdle()
        viewer().assertCountEquals(0)
        val node = hold()
        viewer().assertCountEquals(1)
        node.performTouchInput { up() }
        waitForIdle()
        viewer().assertCountEquals(0)
    }

    @Test fun aTapShowsNoViewer() = runComposeUiTest(testTimeout = 4.minutes) {
        bar(fontScale = 2f); waitForIdle()
        onNodeWithTag(GlassTabBarSemantics.TAG, useUnmergedTree = true)
            .performTouchInput { down(Offset(geometry.centreOf(3), 31f)); advanceEventTime(60); up() }
        mainClock.advanceTimeBy(30)
        viewer().assertCountEquals(0)
    }

    @Test fun normalTextShowsNoViewer() = runComposeUiTest(testTimeout = 4.minutes) {
        bar(fontScale = 1f); waitForIdle()
        val node = hold()
        viewer().assertCountEquals(0)
        node.performTouchInput { up() }
    }
}
