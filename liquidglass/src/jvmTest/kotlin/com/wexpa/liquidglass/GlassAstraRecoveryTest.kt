package com.wexpa.liquidglass

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** Regression checks against independent observables, not another copy of the controller. */
class GlassAstraRecoveryTest {
    private val bar = GlassSelectorBar(
        width = 1006f, height = 162f, inset = 18f, count = 5, cornerRadius = 81f,
        baseHalfWidth = 96f, baseHalfHeight = 69f, allowedHalfHeight = 69f,
        heldHalfHeight = 96f, heldHalfWidth = 128f, density = 3f, maxBodyWidth = 600f,
    )
    private fun held(): GlassPoseController = GlassPoseController().also {
        it.attach(bar, null, null)
        it.snapToRest(2)
        it.pointerDown(bar.centreOf(2), bar.centreY - 25f, 1_825_000.0, true)
        it.beginDrag(bar.centreOf(2), bar.centreY - 25f)
        it.advanceTo(0.5f)
        it.advanceTo(1f)
    }

    @Test fun releaseToADistantTabDoesNotReexciteStrainFromReturnSpeed() {
        val c = held()
        c.pointerUp(0)
        val stationary = held()
        stationary.pointerUp(2)
        val a = GlassPoseExtents(); val b = GlassPoseExtents()
        var maxDifference = 0f
        var maxCentreSpeed = 0f
        // While both released bodies are free of resting contact, a different navigation
        // destination must not create additional shape energy. Compare observable contours,
        // not strainMagnitude whose normalization changes as the pressure subsides.
        repeat(20) {
            val time = 1f + (it + 1) / 120f
            c.advanceTo(time); stationary.advanceTo(time)
            c.extents(a); stationary.extents(b)
            maxDifference = maxOf(maxDifference, abs(a.width - b.width), abs(a.height - b.height))
            maxCentreSpeed = maxOf(maxCentreSpeed, abs(c.centreVelocityX))
        }
        assertTrue(maxCentreSpeed > 1000f, "test never exercised rapid return")
        assertTrue(maxDifference < 0.25f, "return speed changed the recovered contour: $maxDifference")

    }

    @Test fun everyRenderedContourPointIsContinuousAtAnOffCentreRelease() {
        val c = held()
        c.pointerMove(bar.centreOf(2) + 80f, bar.centreY - 55f, 1_825_001.05)
        c.advanceTo(1.05f)
        val before = FloatArray(512); val after = FloatArray(512)
        c.frame.contour(256, before)
        val vx = c.centreVelocityX; val vy = c.centreVelocityY
        c.pointerUp(3)
        c.frame.contour(256, after)
        assertTrue(before.indices.maxOf { abs(before[it] - after[it]) } < 0.01f)
        assertTrue(abs(vx - c.centreVelocityX) < 0.01f && abs(vy - c.centreVelocityY) < 0.01f)
    }

    @Test fun anOffCentreStationaryPointStaysAttachedAtEveryPresentationRate() {
        for (hz in listOf(60, 90, 120)) {
            val c = held()
            val point = FloatArray(2)
            var maxError = 0f
            repeat(hz) {
                c.advanceTo(1f + (it + 1).toFloat() / hz)
                c.graspRendered(point)
                maxError = maxOf(maxError, abs(point[0] - bar.centreOf(2)),
                    abs(point[1] - (bar.centreY - 25f)))
            }
            assertTrue(maxError <= 0.1f, "$hz Hz attachment error $maxError")
        }
    }
}
