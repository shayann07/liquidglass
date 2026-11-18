package com.wexpa.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * [GlassInteraction.Fluid], the default of [Glass.interactive] (#196): the search button's response, falling with
 * size. The search button (62 dp) must keep every value; surfaces of 160 dp and more keep today's anchored response.
 */
class GlassFluidInteractionTest {
    private val density = 3f
    private val fluid = GlassInteraction.Fluid

    /** The deformation Modifier.liquidGlass draws for a fully pressed control pulled by (pullX, pullY) px, before landing. */
    private fun landed(w: Float, h: Float, pullX: Float, pullY: Float): GlassPullDeformation {
        val mix = glassSizeMix(w, h, density)
        val growth = (8f * density) + (2f * density - 8f * density) * mix
        val sx = glassPressScale(w, growth, fluid.pressScale)
        val sy = glassPressScale(h, growth, fluid.pressScale)
        val radius = minOf(w, h) / 2f
        val limit = minOf(w * sx, h * sy) * fluid.pullLimit
        val k = glassPullTargetScale(kotlin.math.hypot(pullX, pullY), fluid.pullFollow, limit)
        val raw = glassMaterialPull(w, h, radius, density, sx, sy, pullX * k, pullY * k, limit, fluid)
        return glassFreeSizeBlend(glassUprightPull(raw), fluid, w, h, density, sx, sy, radius, pullX * k, pullY * k)
    }

    @Test fun fluidIsTheSearchButtonPresetAndTheDefault() {
        assertEquals(GlassInteraction.Pullable.copy(pullShape = GlassPullShape.Free, pullLiftShare = 0.65f), fluid)
        assertEquals(fluid, Glass.Regular.interactive().interaction)
        assertEquals(GlassInteraction.Calm, Glass.Regular.interactive(response = GlassInteraction.Calm).interaction)
    }

    @Test fun aControlUpTo80DpIsTheFreeButtonSampleForSample() {
        for (side in listOf(44f, 56f, 62f, 80f)) for (degrees in 0..359 step 15) {
            val a = degrees * kotlin.math.PI.toFloat() / 180f
            val x = cos(a) * 40f * density; val y = sin(a) * 40f * density
            val s = side * density
            val pressed = glassPressScale(s, 8f * density, fluid.pressScale)
            val radius = s / 2f
            val limit = s * pressed * fluid.pullLimit
            val k = glassPullTargetScale(kotlin.math.hypot(x, y), fluid.pullFollow, limit)
            val raw = glassMaterialPull(s, s, radius, density, pressed, pressed, x * k, y * k, limit, fluid)
            val free = glassUprightPull(raw)
            assertEquals(free, glassFreeSizeBlend(free, fluid, s, s, density, pressed, pressed, radius, x * k, y * k))
            assertTrue(free.translationX != 0f || free.translationY != 0f, "the body leaves its place")
        }
    }

    @Test fun surfacesFrom160DpDoNotMoveAndStretchAtMostTwoDp() {
        for ((w, h) in listOf(160f to 160f, 300f to 300f, 350f to 220f, 220f to 350f)) for (degrees in 0..359 step 15) {
            val a = degrees * kotlin.math.PI.toFloat() / 180f
            val x = cos(a) * 200f * density; val y = sin(a) * 200f * density
            val d = landed(w * density, h * density, x, y)
            assertEquals(0f, d.translationX, 1e-4f); assertEquals(0f, d.translationY, 1e-4f)
            // Upright: along is the vertical scale, across the horizontal one. The press growth (2 dp a side) is separate.
            assertTrue((d.across - 1f) * w * density <= 2.01f * density, "$w x $h at $degrees across ${d.across}")
            assertTrue((d.along - 1f) * h * density <= 2.01f * density, "$w x $h at $degrees along ${d.along}")
        }
    }

    @Test fun theBlendIsContinuousBetween80And160Dp() {
        var previous: GlassPullDeformation? = null
        var side = 78f
        while (side <= 162f) {
            val d = landed(side * density, side * density, 0f, -120f * density)
            previous?.let {
                assertEquals(it.translationY, d.translationY, 25f)
                assertEquals(it.along, d.along, 0.02f)
            }
            previous = d
            side += 1f
        }
        val at80 = landed(80f * density, 80f * density, 0f, -120f * density)
        val above = landed(80.001f * density, 80.001f * density, 0f, -120f * density)
        assertEquals(at80.translationY, above.translationY, .5f)
    }

    @Test fun sizeMixIsZeroToEightyAndOneFromOneSixty() {
        assertEquals(0f, glassSizeMix(80f * density, 40f * density, density))
        assertEquals(1f, glassSizeMix(160f * density, 40f * density, density))
        assertEquals(.5f, glassSizeMix(120f * density, 40f * density, density), 1e-6f)
    }

    @Test fun reduceMotionReplacesAnyResponse() {
        for (r in listOf(fluid, GlassInteraction.Calm, GlassInteraction.Pullable)) {
            assertEquals(GlassInteraction.ReducedMotion, glassResponseFor(true, r))
            assertEquals(r, glassResponseFor(false, r))
        }
        assertFalse(GlassInteraction.ReducedMotion.pull)
        assertEquals(1f, GlassInteraction.ReducedMotion.pressScale)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun aScrollThatTakesTheGestureCancelsThePressAndALiftDoesNot() = runComposeUiTest(testTimeout = 4.minutes) {
        var press = GlassPress.None
        setContent {
            val (p, modifier) = rememberGlassPress(enabled = true, source = null, pullLiftShare = .65f,
                freeGlow = true, moveThreshold = 4f)
            press = p
            Column(Modifier.size(200.dp).verticalScroll(rememberScrollState())) {
                Box(Modifier.size(100.dp).testTag("button").then(modifier).background(Color.Red))
                Box(Modifier.height(1000.dp))
            }
        }
        onNodeWithTag("button").performTouchInput { down(center); repeat(6) { moveBy(Offset(0f, -30f)) }; up() }
        waitForIdle()
        assertTrue(press.cancelled, "the scroll took the gesture")
        onNodeWithTag("button").performScrollTo().performTouchInput { down(center); up() }
        waitForIdle()
        assertFalse(press.cancelled, "a plain lift keeps its ring")
    }
}
