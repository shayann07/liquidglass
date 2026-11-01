package com.wexpa.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/** #161: in RTL, a tap selects the tab drawn under the finger, and the selector rests on that tab. */
@OptIn(ExperimentalTestApi::class)
class GlassTabBarRtlTest {
    private val tab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private fun run(style: GlassTabBarStyle, body: ComposeUiTest.(calls: MutableList<Int>) -> Unit) = runComposeUiTest(testTimeout = 4.minutes) {
        val calls = mutableListOf<Int>()
        var selected by mutableIntStateOf(0)
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f), LocalLayoutDirection provides LayoutDirection.Rtl) {
                val state = rememberLiquidGlassState(Color.Black, renderScale = .25f)
                Box(Modifier.size(500.dp, 200.dp)) {
                    Box(Modifier.fillMaxSize().background(Color.DarkGray).liquidGlassSource(state))
                    GlassTabBar(state, 5, selected, { calls += it; selected = it }, Modifier.testTag("bar").width(500.dp), style = style) { _, _ ->
                        Box(Modifier.size(20.dp).background(Color.White))
                    }
                }
            }
        }
        waitForIdle()
        body(calls)
    }

    private fun check(style: GlassTabBarStyle) = run(style) { calls ->
        // The tab drawn rightmost, whichever logical index it carries.
        val tabs = onAllNodes(tab).fetchSemanticsNodes()
        val rightmost = tabs.indices.maxBy { tabs[it].boundsInRoot.center.x }
        val target = tabs[rightmost].boundsInRoot.center
        onNodeWithTag(GlassTabBarSemantics.TAG, useUnmergedTree = true).performTouchInput {
            down(Offset(target.x, centerY)); advanceEventTime(60); up()
        }
        waitForIdle()
        assertEquals(listOf(rightmost), calls, "the tap selected another tab than the one drawn under it")
        val x = onNodeWithTag(GlassTabBarSemantics.TAG, useUnmergedTree = true).fetchSemanticsNode().config[GlassTabBarSemantics.SelectorX]
        assertTrue(abs(x - target.x) < 30f, "selector rests at $x, the tapped tab is at ${target.x}")
    }

    @Test fun calmTapSelectsTheDrawnTab() = check(GlassTabBarStyle.Calm())
    @Test fun measuredTapSelectsTheDrawnTab() = check(GlassTabBarStyle.Measured(dark = true))
}
