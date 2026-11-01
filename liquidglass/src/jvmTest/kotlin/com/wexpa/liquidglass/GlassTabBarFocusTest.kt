package com.wexpa.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    private fun ComposeUiTest.ringPixels(): Int {
        val map = onNodeWithTag("scene").captureToImage().toPixelMap()
        var n = 0
        for (y in 0 until map.height) for (x in 0 until map.width) {
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
                GlassTabBar(state, 5, 0, {}, Modifier.offset(0.dp, 29.dp).width(500.dp)) { _, _ ->
                    Box(Modifier.size(20.dp).background(Color.White))
                }
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

    @Test fun aClickedTabShowsNoRing() = runComposeUiTest(testTimeout = 4.minutes) {
        bar(); waitForIdle()
        onAllNodes(tab)[3].performMouseInput { click(center) }
        waitForIdle()
        assertEquals(0, ringPixels(), "a mouse click left a focus ring")
    }
}
