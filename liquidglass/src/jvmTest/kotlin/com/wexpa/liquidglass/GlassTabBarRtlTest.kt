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

/** #161: in RTL, item 0 is drawn rightmost, a tap selects the tab drawn under the finger, and the selector rests on it. */
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
                    GlassTabBar(state, 5, selected, { calls += it; selected = it }, Modifier.testTag("bar").width(500.dp), style = style) { index, _ ->
                        Box(Modifier.size(20.dp).background(Color.White).testTag("item$index"))
                    }
                }
            }
        }
        waitForIdle()
        body(calls)
    }

    private fun check(style: GlassTabBarStyle) = run(style) { calls ->
        // Items by their logical index, as the app numbers them.
        val centres = (0 until 5).map { onNodeWithTag("item$it", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center }
        // RTL: the first item is drawn rightmost.
        assertEquals(0, centres.indices.maxBy { centres[it].x }, "item 0 is not drawn rightmost in RTL")
        val target = 3
        onNodeWithTag(GlassTabBarSemantics.TAG, useUnmergedTree = true).performTouchInput {
            down(Offset(centres[target].x, centerY)); advanceEventTime(60); up()
        }
        waitForIdle()
        assertEquals(listOf(target), calls, "the tap selected another tab than the one drawn under it")
        val x = onNodeWithTag(GlassTabBarSemantics.TAG, useUnmergedTree = true).fetchSemanticsNode().config[GlassTabBarSemantics.SelectorX]
        assertTrue(abs(x - centres[target].x) < 30f, "selector rests at $x, the tapped tab is at ${centres[target].x}")
    }

    @Test fun calmTapSelectsTheDrawnTab() = check(GlassTabBarStyle.Calm())
    @Test fun measuredTapSelectsTheDrawnTab() = check(GlassTabBarStyle.Measured(dark = true))
}
