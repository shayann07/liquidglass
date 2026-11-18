package com.wexpa.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * #114: a pressed, still body is brighter toward the light and darker away from it, and the grade is gone once the finger
 * has moved past the drag threshold or lifted. Over a flat backdrop the only top-left to bottom-right difference of a
 * rest body is the rim; the held difference minus the rest difference is the grade (iOS: 11 to 13 levels at full
 * illumination on the search button, which is [GlassPressGrade] x 255 x the patch positions).
 */
@OptIn(ExperimentalTestApi::class)
class GlassPressGradeRenderTest {
    private fun gradeOf(response: GlassInteraction, full: Boolean = true): List<Float> {
        val out = mutableListOf<Float>()
        runComposeUiTest(testTimeout = 12.minutes) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    LiquidGlassTheme(dark = true) {
                        Box(Modifier.size(120.dp).background(Color(0.4f, 0.4f, 0.4f)).testTag("scene")) {
                            Box(Modifier.offset(20.dp, 20.dp).size(80.dp)
                                .glassEffect(Glass.Regular.interactive(response = response), CircleShape).testTag("button"))
                        }
                    }
                }
            }
            waitForIdle()
            // Patches half a radius toward the top left and the bottom right of the centre (60, 60).
            fun tlMinusBr(): Float {
                val map = onNodeWithTag("scene").captureToImage().toPixelMap()
                fun patch(cx: Int, cy: Int): Float {
                    var s = 0f
                    for (dx in -2..2) for (dy in -2..2) s += map[cx + dx, cy + dy].red * 255f
                    return s / 25f
                }
                return patch(40, 40) - patch(80, 80)
            }
            out += tlMinusBr()
            mainClock.autoAdvance = false
            onNodeWithTag("button").performTouchInput { down(center) }
            mainClock.advanceTimeBy(400); out += tlMinusBr()
            if (!full) { mainClock.autoAdvance = true; onNodeWithTag("button").performTouchInput { up() }; waitForIdle(); return@runComposeUiTest }
            onNodeWithTag("button").performTouchInput { moveBy(Offset(0f, -6f)) }
            mainClock.advanceTimeBy(600); out += tlMinusBr()
            onNodeWithTag("button").performTouchInput { up() }
            mainClock.advanceTimeBy(1200); out += tlMinusBr()
            mainClock.autoAdvance = true
            waitForIdle()
        }
        return out
    }

    @Test fun aStillPressIsGradedTowardTheLightAndTheGradeGoesOnceDragged() {
        val fluid = gradeOf(GlassInteraction.Fluid)
        val (rest, held, moved, released) = fluid
        assertTrue(held - rest > 6f, "a still press is brighter toward the light: rest $rest held $held")
        assertTrue(abs(moved - rest) < 3f, "the grade is gone once dragged: rest $rest moved $moved")
        assertEquals(rest, released, 2f, "released, flat again")
    }

    @Test fun glassWithoutIlluminationIsNotGraded() {
        val flat = gradeOf(GlassInteraction.Fluid.copy(illumination = 0f), full = false)
        assertEquals(flat[0], flat[1], 2.5f, "illumination 0 grades nothing: $flat")
    }
}
