package com.wexpa.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes

/**
 * #118: LocalGlassAdaptiveQuality rebuilds the theme's glass when it steps the render scale. A tab bar under
 * the theme must keep answering a press and hold afterwards (on the Pixel 7 a hold did nothing after a step
 * back up, while taps still worked).
 */
@OptIn(ExperimentalTestApi::class)
class GlassThemeRebuildGestureTest {
    @Test fun aHoldStillWorksAfterTheThemeRebuildsItsGlass() = runComposeUiTest(testTimeout = 4.minutes) {
        var scale by mutableFloatStateOf(0.25f)
        var selected by mutableIntStateOf(0)
        val calls = ArrayList<Int>()
        setContent {
            LiquidGlassTheme(dark = true, background = Color.Black, renderScale = scale) {
                Box(Modifier.size(500.dp, 200.dp).background(Color.DarkGray)) {
                    GlassTabBar(3, selected, { calls += it; selected = it }, Modifier.testTag("bar").width(500.dp),
                        style = GlassTabBarStyle.Calm()) { _, _ -> Box(Modifier.size(20.dp)) }
                }
            }
        }
        val bar = onNodeWithTag("bar")
        fun held(): Boolean? = bar.fetchSemanticsNode().config.let { if (it.contains(GlassTabBarSemantics.Held)) it[GlassTabBarSemantics.Held] else null }
        val w = bar.fetchSemanticsNode().size.width.toFloat(); val h = bar.fetchSemanticsNode().size.height / 2f
        fun hold(x: Float): Boolean? {
            bar.performTouchInput { down(Offset(x, h)); advanceEventTime(400); moveTo(Offset(x + 2f, h)) }
            mainClock.advanceTimeBy(400); waitForIdle()
            val during = held()
            bar.performTouchInput { advanceEventTime(100); up() }
            mainClock.advanceTimeBy(2000); waitForIdle()
            return during
        }
        hold(w * 0.5f)
        assertEquals(listOf(1), calls, "a hold before any rebuild selects on release")
        scale = 0.5f; waitForIdle(); mainClock.advanceTimeBy(500); waitForIdle()
        scale = 0.25f; waitForIdle(); mainClock.advanceTimeBy(500); waitForIdle()
        hold(w * 0.85f)
        assertEquals(listOf(1, 2), calls, "a hold after two rebuilds selects on release")
    }
}
