package com.wexpa.liquidglass

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Owner rejection: test visible extent and end recovery, not just the number of admitted items. */
class GlassSelectorOwnerRegressionTest {
    private val density = 2.625f
    private val style = GlassTabBarStyle.V3().copy(height = 62.dp)
    private val bar = glassSelectorBarOf(Density(density), 1006.5f, 62f * density, style, 5, 0f, style.selector!!)

    private fun controller(start: Int, grasp: Float = 0f): GlassSelectorController = GlassSelectorController(style.selector!!).also { c ->
        val centres = FloatArray(5) { bar.centreOf(it) }
        val bounds = FloatArray(10) { j -> centres[j / 2] + (if (j % 2 == 0) -1 else 1) * 25f * density }
        c.attach(bar, bounds, centres, 0.62f * bar.baseHalfHeight)
        c.snapToRest(start)
        val finger = bar.centreOf(start) + grasp * bar.baseHalfWidth
        c.pointerDown(finger, bar.centreY, 0f, true)
        c.beginDrag(finger)
        for (i in 1..96) c.advanceTo(i / 240f)
    }

    @Test fun accommodationAndVelocityTogetherCannotRecruitThreeIconCentres() {
        for (grasp in listOf(-0.75f, 0f, 0.75f)) {
            val c = controller(2, grasp)
            var t = 0.4f
            var peak = 0f
            for (i in 0..240) {
                t += 1f / 240f
                val x = bar.centreOf(0) + (bar.centreOf(4) - bar.centreOf(0)) * i / 240f
                c.pointerMove(x, t)
                c.advanceTo(t)
                val b = c.body
                val width = b.length + 2f * b.radius
                peak = maxOf(peak, width / bar.slotWidth)
                assertTrue(width <= 1.9f * bar.slotWidth + 0.25f, "visible width=$width, slots=${width / bar.slotWidth}")
                assertTrue((0..4).count { bar.centreOf(it) > b.left && bar.centreOf(it) < b.right } <= 2,
                    "three icon centres under $b")
                assertTrue(!c.solverFailed && c.selectorValid)
            }
            println("OWNER width ceiling: peak=$peak slots")
        }
    }

    @Test fun bothOutermostTabsRemainReachableWithAnAccommodatedBody() {
        val c = controller(2)
        var t = 0.4f
        val between = (bar.centreOf(2) + bar.centreOf(3)) / 2f
        c.pointerMove(between, t)
        repeat(120) { t += 1f / 240f; c.advanceTo(t) }
        assertEquals(bar.firstCentre, c.grabCentreFor(-bar.slotWidth), 0.01f)
        assertEquals(bar.lastCentre, c.grabCentreFor(bar.width + bar.slotWidth), 0.01f)
    }

    @Test fun stoppedEndReleaseShrinksAndTranslatesTogetherInBothDirections() {
        for (end in listOf(0, 4)) {
            val c = controller(4 - end)
            var t = 0.4f
            val start = bar.centreOf(4 - end)
            val finish = if (end == 0) 0f else bar.width
            repeat(40) { i ->
                t += 1f / 240f
                c.pointerMove(start + (finish - start) * (i + 1) / 40f, t)
                c.advanceTo(t)
            }
            repeat(120) { t += 1f / 240f; c.advanceTo(t) }
            val before = c.body
            c.pointerUp(end)
            assertEquals(before, c.body, "UP must not move the contour")
            var previousWidth = before.length + 2f * before.radius
            var previousError = abs(before.cx - bar.centreOf(end))
            repeat(240) {
                t += 1f / 240f
                c.advanceTo(t)
                val b = c.body
                val width = b.length + 2f * b.radius
                val error = abs(b.cx - bar.centreOf(end))
                assertTrue(width <= previousWidth + 0.25f, "release pumps width: $previousWidth -> $width")
                assertTrue(error <= previousError + 0.25f, "release moves away: $previousError -> $error")
                assertTrue(!c.solverFailed && c.selectorValid, "release solver failure")
                previousWidth = width
                previousError = error
            }
            assertTrue(previousError < 0.5f, "end=$end centre error=$previousError")
            assertTrue(abs(previousWidth - 2f * bar.baseHalfWidth) < 0.5f)
        }
    }

    @Test fun endRecoveryHasOneShapeAndPositionPhaseRatherThanASecondStretch() {
        val c = controller(2)
        var t = 0.4f
        c.pointerMove(bar.width, t)
        repeat(240) { t += 1f / 240f; c.advanceTo(t) }
        val start = c.body
        val width0 = start.halfWidth * 2f
        val widthR = bar.baseHalfWidth * 2f
        c.pointerUp(4)
        var at90 = Float.NaN
        var at10 = Float.NaN
        for (i in 1..240) {
            t += 1f / 240f; c.advanceTo(t)
            val positionRemaining = (bar.lastCentre - c.body.cx) / (bar.lastCentre - start.cx)
            val widthRemaining = (c.body.halfWidth * 2f - widthR) / (width0 - widthR)
            if (i in 12..96) assertTrue(abs(positionRemaining - widthRemaining) < 0.045f,
                "release separates translation=$positionRemaining from shrink=$widthRemaining at ${i / 240f}s")
            if (at90.isNaN() && widthRemaining <= 0.9f) at90 = i / 240f
            if (at10.isNaN() && widthRemaining <= 0.1f) at10 = i / 240f
        }
        val span = at10 - at90
        assertTrue(span in 0.28f..0.39f, "main recovery blinked or dragged: $span seconds")
        println("OWNER end recovery width T90-10=$span s")
    }

    @Test fun aQuickTapDoesNotUseTheSlowerHeldRecovery() {
        fun fresh() = GlassSelectorController(style.selector!!).also {
            it.attach(bar, null, null); it.snapToRest(0)
        }
        val direct = fresh()
        val tapped = fresh()
        tapped.pointerDown(bar.lastCentre, bar.centreY, 0f, false)
        tapped.pointerUp(0)
        direct.retarget(4); tapped.retarget(4)
        repeat(120) { i ->
            val t = (i + 1) / 240f
            direct.advanceTo(t); tapped.advanceTo(t)
            assertEquals(direct.body.cx, tapped.body.cx, 0.01f, "tap acquired held recovery")
            assertEquals(direct.body.halfWidth, tapped.body.halfWidth, 0.01f)
        }
    }

    @Test fun regrabbingDuringRecoveryReplacesItsShrinkingWalls() {
        val c = controller(2)
        c.pointerUp(2)
        var t = 0.4f
        repeat(24) { t += 1f / 240f; c.advanceTo(t) }
        c.pointerDown(c.body.cx, bar.centreY, t, true)
        c.beginDrag(c.body.cx)
        repeat(240) { t += 1f / 240f; c.advanceTo(t) }
        assertTrue(c.isHeld && c.formation > 0.99f)
        assertTrue(c.body.radius + abs(c.body.skew) > bar.allowedHalfHeight + 0.8f * (bar.heldHalfHeight - bar.allowedHalfHeight),
            "old release walls still crush a newly acquired hold")
        assertTrue(!c.solverFailed && c.selectorValid)
    }

    @Test fun weeksOfDeviceUptimeCannotQuantizeDragVelocityOrDeformation() {
        val low = GlassSelectorController(style.selector!!)
        val high = GlassSelectorController(style.selector!!)
        val offset = 1_758_000.0 // seconds: the order of the physical Pixel's actual uptime
        for ((c, origin) in listOf(low to 0.0, high to offset)) {
            c.attach(bar, null, null); c.snapToRest(1)
            c.pointerDown(bar.centreOf(1), bar.centreY, origin, true)
            c.beginDrag(bar.centreOf(1))
        }
        // Identical 8ms input intervals and 1000px/s speed; only the clock origin differs.
        for (i in 1..80) {
            val elapsed = i * 0.008
            val x = bar.centreOf(1) + i * 8f
            low.pointerMove(x, elapsed); high.pointerMove(x, offset + elapsed)
            low.advanceTo(elapsed.toFloat()); high.advanceTo(elapsed.toFloat())
            assertEquals(low.centreVelocity, high.centreVelocity, 0.02f, "uptime changed velocity at sample $i")
            assertEquals(low.body.cx, high.body.cx, 0.01f)
            assertEquals(low.body.radius, high.body.radius, 0.01f)
            assertEquals(low.body.length, high.body.length, 0.01f)
            assertEquals(low.body.skew, high.body.skew, 0.01f)
        }
    }
}
