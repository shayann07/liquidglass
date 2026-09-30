package com.wexpa.liquidglass

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Full-display excursions exposed bugs that the former 128dp probes missed. These are
 * robustness requirements, not inferred Apple finger trajectories. */
class GlassExtremeInteractionTest {
    private val bar = GlassSelectorBar(
        width = 1006f, height = 162f, inset = 18f, count = 5, cornerRadius = 81f,
        baseHalfWidth = 96f, baseHalfHeight = 69f, allowedHalfHeight = 69f,
        heldHalfHeight = 96f, heldHalfWidth = 128f, density = 3f, maxBodyWidth = 600f,
    )

    @Test fun anchoredWideBarsNeverTranslateOrTiltAtAnyPullAngle() {
        for (degrees in 0..359) {
            val p = glassAnchoredMaterialPull(GlassPullDeformation(1.4f, .8f,
                degrees.toFloat(), 5000f, -5000f), 1000f, 140f)
            assertEquals(0f, p.translationX); assertEquals(0f, p.translationY)
            val a = p.angleDegrees * kotlin.math.PI.toFloat() / 180f
            assertEquals(0f, (p.along - p.across) * cos(a) * sin(a), .00001f,
                "wide bar sheared at $degrees")
            assertTrue(p.along >= p.across && p.across >= .799f)
        }
    }

    @Test fun anchoredRoundControlsRetainDiagonalStretch() {
        val p = glassAnchoredMaterialPull(GlassPullDeformation(1.3f, .9f, 45f, 50f, 50f), 100f, 100f)
        assertEquals(1.3f, p.along, .00001f); assertEquals(.9f, p.across, .00001f)
        assertEquals(45f, p.angleDegrees, .00001f)
        assertEquals(0f, p.translationX); assertEquals(0f, p.translationY)
    }

    @Test fun farEdgesAndCornersStayBoundedAndRecoverWithoutStoredTravel() {
        for (signX in listOf(-1f, 0f, 1f)) for (signY in listOf(-1f, 0f, 1f)) {
            if (signX == 0f && signY == 0f) continue
            val c = GlassPoseController()
            c.attach(bar, null, null); c.snapToRest(2)
            val x = bar.centreOf(2); val y = bar.centreY
            c.pointerDown(x, y, 1_825_000.0, true); c.beginDrag(x, y)
            var t = 0f
            fun advance(seconds: Float) {
                repeat((seconds * 240).toInt()) { t += 1f / 240f; c.advanceTo(t) }
            }
            advance(.5f)
            val farX = x + signX * 10_000f
            c.pointerMove(farX, y + signY * 10_000f, 1_825_000.6)
            advance(.6f)
            val cap = c.spec.endTravelCapRatio * bar.height
            assertTrue(c.centreX in (bar.firstCentre - cap - .2f)..(bar.lastCentre + cap + .2f),
                "unbounded centre ${c.centreX} for $signX,$signY")
            assertTrue(abs(c.centreY - y) <= c.spec.pullFollowCapRatio * bar.height + .2f)
            assertTrue(!c.solverFailed)
            // Raw intent is not truncated by the resisted visual centre.
            if (signX != 0f) assertEquals(farX, c.grabCentreFor(farX, y), .2f)
            c.pointerMove(x, y, 1_825_001.2)
            advance(.3f)
            assertEquals(x, c.centreX, 1f, "overpull delayed the return")
            c.cancel(2); advance(1.5f)
            assertTrue(c.isIdle, "cancel did not settle after $signX,$signY")
            assertTrue(c.protrusion() <= .1f)
        }
    }

    @Test fun resistanceHasNoDeadZoneOrDiscontinuityAtEitherAnchor() {
        for (edge in listOf(0f, 100f)) {
            val below = glassAnchoredTravel(edge - .001f, 0f, 100f, 8f)
            val above = glassAnchoredTravel(edge + .001f, 0f, 100f, 8f)
            assertEquals(.002f, above - below, .00002f)
        }
        assertEquals(-8f, glassAnchoredTravel(-100_000f, 0f, 100f, 8f), .001f)
        assertEquals(108f, glassAnchoredTravel(100_000f, 0f, 100f, 8f), .001f)
    }

    @Test fun wideGlassDoesNotMultiplyCrossAxisPixelLossByItsAspectRatio() {
        val s = GlassInteraction.Pullable
        for (angle in 0..359 step 15) {
            val a = angle * kotlin.math.PI.toFloat() / 180f
            val x = 120f * cos(a); val y = 120f * sin(a)
            val along = glassPullExtent(1000f, 140f, 70f, 1f, 1f, x, y)
            val across = glassPullExtent(1000f, 140f, 70f, 1f, 1f, -y, x)
            val d = glassPullDeformation(x, y, along, s.pullElongation,
                s.pullWidthRatio, s.pullFollow, acrossExtentPx = across)
            assertEquals((d.along - 1f) * along, (1f - d.across) * across, .001f,
                "different absolute stretch/squeeze at $angle degrees")
        }
        val vertical = glassPullDeformation(0f, 200f, 140f, s.pullElongation,
            s.pullWidthRatio, s.pullFollow, acrossExtentPx = 1000f)
        assertTrue(vertical.across > .95f, "vertical pull still crushes the whole long pill")
    }

    @Test fun wideGlassContourStaysInTheWindowForEveryExtremePullAngle() {
        val width = 1006f; val height = 116f; val radius = 58f
        val cx = 540f; val cy = 2027f
        val sx = glassPressScaleInViewport(1.06f, width, cx - width / 2f, 1080f)
        val sy = 1.26f
        for (degrees in 0..359 step 5) {
            val angle = degrees * kotlin.math.PI.toFloat() / 180f
            val px = cos(angle) * 200f; val py = sin(angle) * 200f
            val along = glassPullExtent(width, height, radius, sx, sy, px, py)
            val across = glassPullExtent(width, height, radius, sx, sy, -py, px)
            val raw = glassPullDeformation(px, py, along, .217f, 1f, .35f, acrossExtentPx = across)
            val d = glassPullInViewport(raw, width, height, radius, sx, sy, cx, cy, 1080f, 2400f)
            // Independent contour samples, transformed in the same order as Compose's layers.
            val c = cos(angle); val s = sin(angle)
            repeat(360) { n ->
                val t = n * kotlin.math.PI.toFloat() / 180f
                val nx = cos(t); val ny = sin(t)
                val x = ((if (nx < 0) -1 else 1) * (width / 2f - radius) + radius * nx) * sx
                val y = ((if (ny < 0) -1 else 1) * (height / 2f - radius) + radius * ny) * sy
                val a = (c * x + s * y) * d.along
                val b = (-s * x + c * y) * d.across
                val outX = cx + d.translationX + c * a - s * b
                val outY = cy + d.translationY + s * a + c * b
                assertTrue(outX in -.01f..1080.01f && outY in -.01f..2400.01f,
                    "contour left the window at pull $degrees / sample $n: $outX,$outY")
            }
        }
    }

    @Test fun viewportLeavesRoomyControlsAndIntentionallyPartialLayoutsAlone() {
        val raw = glassPullDeformation(40f, 60f, 234f, .217f, 1f, .35f)
        assertEquals(raw, glassPullInViewport(raw, 186f, 186f, 93f,
            1.26f, 1.26f, 500f, 1000f, 1080f, 2400f))
        assertEquals(raw, glassPullInViewport(raw, 186f, 186f, 93f,
            1.26f, 1.26f, -20f, 1000f, 1080f, 2400f))
        assertEquals(1.08f, glassPressScaleInViewport(1.15f, 1000f, 40f, 1080f), 1e-5f)
    }
}
