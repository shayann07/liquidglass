package com.wexpa.liquidglass

import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import kotlin.math.*
import kotlin.test.*

class GlassRefinementTest {
    @Test fun pressPeakAndSteadySizeMatchAnIndependentKeypadPress() {
        val s = GlassInteraction.Pullable
        val settled = 234f * glassPressScale(234f, s.pressGrowth.value * 3f, s.pressScale)
        assertEquals(282f, settled, 1f)
        val animation = TargetBasedAnimation(GlassMotion.BalloonDown, Float.VectorConverter, 0f, 1f)
        // K01 width, original native PTS, aligned to last resting frame. This key was not
        // used to fit the S01 search-button spring. ±4 px includes its 16.7 ms sampling.
        for ((seconds, width) in listOf(.033334f to 244f, .066667f to 262f,
            .1f to 276f, .15f to 286f, .25f to 282f, .35f to 282f)) {
            val actual = 234f + (settled - 234f) * animation.getValueFromNanos((seconds * 1e9).toLong())
            assertEquals(width, actual, 4f, "holdout at $seconds seconds")
        }
    }

    private val bar = GlassSelectorBar(
        width = 1006f, height = 162f, inset = 18f, count = 5, cornerRadius = 81f,
        baseHalfWidth = 96f, baseHalfHeight = 69f, allowedHalfHeight = 69f,
        heldHalfHeight = 96f, heldHalfWidth = 128f, density = 3f, maxBodyWidth = 600f,
    )

    private fun held() = GlassPoseController().also {
        it.attach(bar, null, null); it.snapToRest(2)
        it.pointerDown(bar.centreOf(2), bar.centreY, 1_825_000.0, true)
        it.beginDrag(bar.centreOf(2), bar.centreY)
        it.advanceTo(.5f); it.advanceTo(1f)
        it.pointerMove(bar.centreOf(2), bar.centreY, 1_825_001.0)
    }

    @Test fun sustainedOffBarOverpullCannotKeepCollapsingTheBody() {
        fun extent(dp: Float): GlassPoseExtents {
            val c = held()
            c.pointerMove(bar.centreOf(2), bar.centreY - dp * bar.density, 1_825_001.1)
            repeat(180) { c.advanceTo(1f + (it + 1) / 120f) }
            assertTrue(c.isHeld)
            return GlassPoseExtents().also { c.extents(it) }
        }
        val calm = extent(0f); val small = extent(2f); val extreme = extent(128f); val beyond = extent(512f)
        assertTrue(abs(small.height - calm.height) < 2f, "tiny travel overreacted")
        assertTrue(abs(extreme.height - beyond.height) < 2f, "hidden overpull keeps changing height")
        assertTrue(abs(extreme.width - beyond.width) < 2f, "hidden overpull keeps shrinking width")
        assertTrue(extreme.width > calm.width * .7f, "off-bar body collapsed")
    }

    @Test fun theSamePhysicalVelocityIsStableAcrossInputRates() {
        fun sample(hz: Int): FloatArray {
            val c = held(); val e = GlassPoseExtents(); val result = FloatArray(12)
            for (i in 1..hz) {
                val time = i.toDouble() / hz
                c.pointerMove(bar.centreOf(2) + (240 * time).toFloat(),
                    bar.centreY - (48 * time).toFloat(), 1_825_001.0 + time)
                c.advanceTo(1f + time.toFloat())
                if (i % (hz / 4) == 0) {
                    c.extents(e); val index = (i / (hz / 4) - 1) * 3
                    result[index] = e.width; result[index + 1] = e.height; result[index + 2] = c.centreX
                }
            }
            return result
        }
        val reference = sample(240)
        for (hz in listOf(60, 120)) {
            val actual = sample(hz)
            // Presentation phase differs by at most one 60 Hz event (4 px at 240 px/s).
            assertTrue(actual.indices.maxOf { abs(actual[it] - reference[it]) } < 4.5f,
                "$hz Hz samples changed the actual contour or tracking")
        }
    }

    @Test fun geometricPressDoesNotWaitForTheGlowOrItsFade() {
        val s = GlassInteraction.Pullable
        assertTrue(glassPressIsDown(s, GlassPress(0f, 0f, 0f, pulling = true)))
        assertFalse(glassPressIsDown(s, GlassPress(0f, 0f, 1f, pulling = false)))
        assertFalse(glassPressIsDown(GlassInteraction.Default, GlassPress(0f, 0f, 0f, pulling = true)))
        assertTrue(glassPressIsDown(GlassInteraction.Default, GlassPress(0f, 0f, 1f)))
    }

    @Test fun circularGlassHasTheSameStrainInEveryDirection() {
        val s = GlassInteraction.Pullable
        for (degrees in 0..359) {
            val a = degrees * PI.toFloat() / 180f
            val x = 80f * cos(a); val y = 80f * sin(a)
            val e = glassPullExtent(186f, 186f, 93f, 1.28f, 1.28f, x, y)
            assertEquals(238.08f, e, 1e-3f)
            val d = glassPullDeformation(x, y, e, s.pullElongation, s.pullWidthRatio, s.pullFollow)
            assertEquals(1f + s.pullElongation * 80f / 238.08f, d.along, 1e-4f)
        }
        // A pill's diagonal is its rectangle core plus round caps, not its bounding box.
        assertEquals(200f / sqrt(2f) + 100f,
            glassPullExtent(300f, 100f, 50f, 1f, 1f, 1f, 1f), 1e-3f)
    }

    @Test fun increasingPullRemainsResponsiveButCannotStoreUnboundedTravel() {
        val s = GlassInteraction.Pullable
        var previous = 0f
        var previousIncrement = Float.POSITIVE_INFINITY
        for (i in 1..1000) {
            val input = i.toFloat()
            val centre = input * glassPullTargetScale(input, s.pullFollow, 40f) * s.pullFollow
            assertTrue(centre >= previous - 1e-5f && centre <= 40.001f)
            val increment = centre - previous
            // Four subtractions/products around 40 px accumulate a few Float ulps.
            assertTrue(increment <= previousIncrement + 3e-5f, "resistance must increase smoothly")
            previous = centre; previousIncrement = increment
        }
        assertTrue(glassPullTargetScale(0.1f, s.pullFollow, 40f) > 0.999f)
        assertEquals(1f, glassPullTargetScale(10000f, 0.78f, Float.POSITIVE_INFINITY))
    }
}
