package com.wexpa.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.down
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * [GlassInteraction.QuickAction] and [Glass.ControlCenterTile] (the press of iOS quick-action round buttons), scored against
 * the lock-screen flashlight button of `IMG_6667.MP4` (58 pt, one tap and one 1.9 s hold, HDR decode, display colours;
 * `review/atlas/flashlight-press-2026-11-18` and `review/atlas/quick-action-press-2026-11-19`). The button is drawn through the
 * production modifier and read back frame by frame at 60 Hz. Every expected number below is the measured one; none is fitted
 * to this renderer, and the tolerances are the measurement's own spread (about two frames in time, a few percent in size).
 *
 * The rest body of the Control Center tile (about 60 over black) is not the lock-screen button's (18), so the lightening is scored
 * as the share of the way from the rest body to white, which is the measured quantity (0.502 over the black band, 0.525 over the
 * 125 stripe band); the absolute levels are printed beside the measured ones.
 */
@OptIn(ExperimentalTestApi::class)
class GlassQuickActionPressTest {
    private val failures = mutableListOf<String>()

    private fun check(ok: Boolean, message: String) {
        println((if (ok) "CCPRESS ok   " else "CCPRESS FAIL ") + message)
        if (!ok) failures += message
    }

    /** One frame of the button: seconds since the touch went down or since the lift, its diameter in dp and its body level. */
    private class Frame(val t: Double, val diameter: Double, val level: Double)

    private class Trace(val rest: Frame, val down: List<Frame>, val up: List<Frame>)

    private fun trace(ground: Float, glass: Glass, holdFrames: Int = 45, releaseFrames: Int = 40): Trace {
        val density = 2f
        lateinit var rest: Frame
        val down = mutableListOf<Frame>()
        val up = mutableListOf<Frame>()
        runComposeUiTest(testTimeout = 12.minutes) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(density)) {
                    LiquidGlassTheme(dark = true) {
                        Box(Modifier.size(170.dp).background(Color(ground, ground, ground)).testTag("scene")) {
                            Box(Modifier.offset(56.dp, 56.dp).size(58.dp)
                                .glassEffect(glass, CircleShape).testTag("button"))
                        }
                    }
                }
            }
            waitForIdle()
            fun read(t: Double): Frame {
                val map = onNodeWithTag("scene").captureToImage().toPixelMap()
                val cy = (56f + 29f) * density
                val cx = (56f + 29f) * density
                // The body: the mean of a patch of 7 dp radius at the centre, inside the rest disc and the press alike.
                var sum = 0.0
                var n = 0
                val r = 7f * density
                for (y in (cy - r).toInt()..(cy + r).toInt()) for (x in (cx - r).toInt()..(cx + r).toInt()) {
                    if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r) { sum += map[x, y].red * 255.0; n++ }
                }
                // The diameter: the row through the centre, the first and last pixel above 20 levels over the ground
                // (the rim glow sits at the very edge; over a black ground the contour shows nothing), interpolated.
                val thr = ground * 255.0 + 20.0
                fun v(x: Int) = map[x.coerceIn(0, map.width - 1), cy.toInt()].red * 255.0
                var left = 0.0
                var right = map.width - 1.0
                for (x in 1 until map.width) if (v(x) > thr) { left = x - 1 + (thr - v(x - 1)) / (v(x) - v(x - 1)); break }
                for (x in map.width - 2 downTo 0) if (v(x) > thr) { right = x + 1 - (thr - v(x + 1)) / (v(x) - v(x + 1)); break }
                return Frame(t, (right - left) / density, sum / n)
            }
            rest = read(0.0)
            mainClock.autoAdvance = false
            onNodeWithTag("button").performTouchInput { down(center) }
            for (i in 1..holdFrames) { mainClock.advanceTimeBy(16); down += read(i * 0.016) }
            onNodeWithTag("button").performTouchInput { up() }
            for (i in 1..releaseFrames) { mainClock.advanceTimeBy(16); up += read(i * 0.016) }
            mainClock.autoAdvance = true
            waitForIdle()
        }
        return Trace(rest, down, up)
    }

    private data class Band(val name: String, val trace: Trace, val iosShare: Double, val iosRest: Double, val iosPressed: Double)

    @Test
    fun theQuickActionPressMatchesTheLockScreenFlashlight() {
        val black = trace(0f, Glass.ControlCenterTile)
        val rest = black.rest.diameter
        val scale = black.down.map { it.diameter / rest }
        val held = scale.takeLast(8).average()
        val peak = scale.max()
        val first = black.down.first { it.diameter / rest > 1.01 }
        val peakAt = black.down[scale.indexOf(peak)].t - first.t
        println("CCPRESS rest ${"%.1f".format(rest)} dp, held %.3f, peak %.3f %.3f s after the first growth frame".format(held, peak, peakAt))
        // Measured (radius over rest, edge fit about 1 px): held 1.605 (settled by 0.33 s), the hold peak 1.694 about 0.19 s after the
        // first growth frame (2.483 to 2.667-2.683 s). Tolerances: 3 percent of size, about two frames in time.
        check(abs(held - 1.605) <= 0.03, "held scale %.3f (measured 1.605 +- 0.03)".format(held))
        check(abs(peak - 1.694) <= 0.05, "peak scale %.3f (measured 1.694 +- 0.05)".format(peak))
        check(abs(peakAt - 0.19) <= 0.04, "peak %.3f s after the first growth frame (measured 0.19 +- 0.04)".format(peakAt))

        // The release: it falls from 1.605 through 1.0 to 0.900 (the tap 0.889) and settles at 1.0 (1.012 for a few frames, the
        // edge fit own 1 px). Its minimum comes 0.15 to 0.17 s after the first shrinking frame (4.317 to 4.467-4.483 s).
        val after = black.up.map { it.diameter / rest }
        val low = after.min()
        val firstShrink = black.up.first { it.diameter / rest < held - 0.01 }
        val lowAt = black.up[after.indexOf(low)].t - firstShrink.t
        val end = after.takeLast(5).average()
        println("CCPRESS release minimum %.3f %.3f s after the first shrinking frame, settled %.3f".format(low, lowAt, end))
        check(abs(low - 0.90) <= 0.04, "release minimum %.3f (measured 0.900, tap 0.889, +- 0.04)".format(low))
        check(abs(lowAt - 0.16) <= 0.04, "minimum %.3f s after the first shrinking frame (measured 0.16 +- 0.04)".format(lowAt))
        check(abs(end - 1.0) <= 0.02, "settled at %.3f of rest (measured 1.0 to 1.012)".format(end))

        // The white: the body rise over its room to white, over the black band and over the 125 stripe band.
        val grey = trace(125f / 255f, Glass.ControlCenterTile, holdFrames = 45, releaseFrames = 6)
        for (b in listOf(Band("black band", black, 0.502, 18.0, 137.0), Band("stripe band (flat 125)", grey, 0.525, 133.0, 197.0))) {
            val restLevel = b.trace.rest.level
            val pressed = b.trace.down.takeLast(8).map { it.level }.average()
            val share = (pressed - restLevel) / (255.0 - restLevel)
            println("CCPRESS ${b.name}: rest %.1f, pressed %.1f (+%.1f; measured %.0f to %.0f, +%.0f), share of the way to white %.3f (measured %.3f)"
                .format(restLevel, pressed, pressed - restLevel, b.iosRest, b.iosPressed, b.iosPressed - b.iosRest, share, b.iosShare))
            check(abs(share - b.iosShare) <= 0.03, "${b.name}: share of the way to white %.3f (measured %.3f +- 0.03)".format(share, b.iosShare))
        }

        // The level rise: a straight line over 125 ms, so from 5 to 95 percent in about 0.113 s (measured 0.117 +- 0.035).
        val pressedLevel = black.down.takeLast(8).map { it.level }.average()
        val rise = black.down.map { (it.level - black.rest.level) / (pressedLevel - black.rest.level) }
        val t05 = black.down[rise.indexOfFirst { it > 0.05 }].t
        val t95 = black.down[rise.indexOfFirst { it >= 0.95 }].t
        check(abs((t95 - t05) - 0.117) <= 0.035, "level rises from 5 to 95 percent in %.3f s (measured 0.117 +- 0.035)".format(t95 - t05))
        // And drops at the lift: the measured body is at rest again on the first frame after it, whatever the size is doing.
        val dropped = black.up.take(3).minOf { it.level }
        check(dropped - black.rest.level <= 0.10 * (pressedLevel - black.rest.level),
            "level within 3 frames of the lift %.1f (rest %.1f, pressed %.1f): back at rest".format(dropped, black.rest.level, pressedLevel))
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun onlyTheQuickActionPressReachesTheNewTerm() {
        // The shader term is gated on uPressWhite > 0, which the host sets to GlassQuickActionWhite x the glow for a quick-action
        // press and to the literal 0f otherwise: so every other interaction must not be recognised, whatever it is copied from.
        for (other in listOf(
            GlassInteraction.Default, GlassInteraction.Calm, GlassInteraction.Pullable, GlassInteraction.Fluid,
            GlassInteraction.ReducedMotion, GlassInteraction.Fluid.copy(pressScale = GlassQuickActionScale),
            GlassInteraction(pressScale = GlassQuickActionScale),
            GlassInteraction(pressScale = 1.2f, releaseStiffness = GlassQuickActionReleaseStiffness),
        )) assertFalse(other.quickAction, "$other must not be a quick action")
        assertTrue(GlassInteraction.QuickAction.quickAction)
        assertTrue(GlassInteraction.QuickAction.copy(illumination = 0.3f).quickAction, "a copy of the preset stays one")
        // The preset is additive: the default response of every Glass is still Fluid, and the tile carries the preset.
        assertEquals(GlassInteraction.QuickAction, Glass.ControlCenterTile.interaction)
        assertTrue(Glass.ControlCenterTile.isInteractive)
        assertEquals(GlassInteraction.Fluid, Glass.Regular.interactive().interaction)
        assertFalse(Glass.ControlCenterTile.interactive(false).isInteractive)
        assertEquals(GlassStyle.controlCenterTile(), Glass.ControlCenterTile.style(true))
        // The white share is the measured pair: 0.51 puts both backdrops within 2 levels.
        val errs = listOf(18.0 + GlassQuickActionWhite * (255 - 18) - 137.0, 133.0 + GlassQuickActionWhite * (255 - 133) - 197.0)
        assertTrue(sqrt(errs.sumOf { it * it } / 2) < 2.0, "the shared share fits both backdrops within 2 levels: $errs")
    }
}
