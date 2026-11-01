package com.wexpa.liquidglass

import androidx.compose.foundation.focusable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/** #117: a keyboard user can see which tab has focus; a pointer click does not leave a ring. */
@OptIn(ExperimentalTestApi::class)
class GlassTabBarFocusTest {
    /** Ring-coloured pixels in the scene, or only within [within] (scene px) when given. */
    private fun ComposeUiTest.ringPixels(within: androidx.compose.ui.geometry.Rect? = null): Int {
        val map = onNodeWithTag("scene").captureToImage().toPixelMap()
        var n = 0
        val xs = within?.let { it.left.toInt().coerceAtLeast(0) until it.right.toInt().coerceAtMost(map.width) } ?: 0 until map.width
        val ys = within?.let { it.top.toInt().coerceAtLeast(0) until it.bottom.toInt().coerceAtMost(map.height) } ?: 0 until map.height
        for (y in ys) for (x in xs) {
            val c = map[x, y]
            if (abs(c.red - GlassFocusRingColor.red) < .03f && abs(c.green - GlassFocusRingColor.green) < .03f &&
                abs(c.blue - GlassFocusRingColor.blue) < .03f) n++
        }
        return n
    }

    private fun ComposeUiTest.bar() = setContent {
        CompositionLocalProvider(LocalDensity provides Density(1f)) {
            val state = rememberLiquidGlassState(Color.Black, renderScale = .25f)
            Box(Modifier.size(500.dp, 120.dp).testTag("scene")) {
                Box(Modifier.fillMaxSize().background(Color.DarkGray).liquidGlassSource(state))
                // The public overload's default style.
                GlassTabBar(state, 5, 0, {}, Modifier.offset(0.dp, 29.dp).width(500.dp), style = GlassTabBarStyle.Calm()) { _, _ ->
                    Box(Modifier.size(20.dp).background(Color.White))
                }
                // A focusable sibling outside the bar, as a text field beside it would be.
                Box(Modifier.size(10.dp).focusable().testTag("other"))
            }
        }
    }

    private val tab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    @Test fun aKeyboardFocusedTabShowsARing() = runComposeUiTest(testTimeout = 4.minutes) {
        bar(); waitForIdle()
        assertEquals(0, ringPixels(), "no ring before focus")
        onAllNodes(tab)[2].requestFocus()
        waitForIdle()
        assertTrue(ringPixels() > 200, "focused tab draws no ring: ${ringPixels()}")
    }

    /** The selected tab sits under the selector; its ring is drawn above it, not under it. */
    @Test fun theSelectedTabsRingIsDrawnOverTheSelector() = runComposeUiTest(testTimeout = 4.minutes) {
        bar(); waitForIdle()
        onAllNodes(tab)[0].requestFocus()
        waitForIdle()
        // Counted within the selected tab's own bounds, so a ring round another tab cannot pass.
        val cell = onAllNodes(tab)[0].fetchSemanticsNode().boundsInRoot
        val inside = ringPixels(cell)
        assertTrue(inside > 200, "the selected tab's ring is hidden: $inside of ${ringPixels()}")
        assertEquals(ringPixels(), inside, "ring pixels outside the selected tab")
        // Evidence for review/atlas/tab-focus-ring-2026-11-01 (desktop JVM software render, 500 x 120 dp at density 1).
        val image = onNodeWithTag("scene").captureToImage().toAwtImage()
        java.io.File("build/reports/atlas").apply { mkdirs() }.resolve("tab-focus-ring.png")
            .let { javax.imageio.ImageIO.write(image, "png", it) }
    }

    /** Keyboard focus, then a mouse click on another tab: no ring stays behind. */
    @Test fun aClickAfterKeyboardFocusClearsTheRing() = runComposeUiTest(testTimeout = 4.minutes) {
        bar(); waitForIdle()
        onAllNodes(tab)[2].requestFocus()
        waitForIdle()
        assertTrue(ringPixels() > 200, "no ring after keyboard focus")
        onAllNodes(tab)[3].performMouseInput { click(center) }
        waitForIdle()
        assertEquals(0, ringPixels(), "a ring stayed after the click")
    }

    @Test fun aClickedTabShowsNoRing() = runComposeUiTest(testTimeout = 4.minutes) {
        bar(); waitForIdle()
        onAllNodes(tab)[3].performMouseInput { click(center) }
        waitForIdle()
        assertEquals(0, ringPixels(), "a mouse click left a focus ring")
    }

    /** A touch press focuses the tab on the desktop, where the input mode stays Keyboard: no ring (seen in every
     * desktop touch replay after #143). */
    @Test fun aTouchedTabShowsNoRing() = runComposeUiTest(testTimeout = 4.minutes) {
        bar(); waitForIdle()
        onAllNodes(tab)[3].performTouchInput { click(center) }
        waitForIdle()
        assertEquals(0, ringPixels(), "a touch left a focus ring")
    }

    /** Touch a tab, move focus out of the bar, then back without a pointer (a Tab from a text field, whose key the
     * bar never sees): the ring shows again, however soon it happens. */
    @Test fun focusReturningWithoutAPointerShowsTheRing() = runComposeUiTest(testTimeout = 4.minutes) {
        bar(); waitForIdle()
        onAllNodes(tab)[3].performTouchInput { click(center) }
        waitForIdle()
        assertEquals(0, ringPixels(), "a touch left a focus ring")
        onNodeWithTag("other").requestFocus()
        waitForIdle()
        onAllNodes(tab)[1].requestFocus()
        waitForIdle()
        assertTrue(ringPixels() > 200, "focus returning without a pointer showed no ring: ${ringPixels()}")
    }
}
