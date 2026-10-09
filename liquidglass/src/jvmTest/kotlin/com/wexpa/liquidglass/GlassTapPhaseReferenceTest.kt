package com.wexpa.liquidglass

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.io.File

/** Compare shape at observed travel phase; the original stills have no input timestamps. */
class GlassTapPhaseReferenceTest {
    @Test fun arrivalCompressionExchangesWidthForHeight() {
        val bar = referenceBar()
        val controller = GlassPoseController(GlassPoseSpec.Calm)
        controller.attach(bar, null, null)
        controller.snapToRest(0)
        controller.retarget(4)
        val extent = GlassPoseExtents()
        var minimumWidth = Float.POSITIVE_INFINITY
        var matchedWidth = 0f
        var pairedHeight = 0f
        var best = Float.POSITIVE_INFINITY
        val rows = arrayListOf("seconds,phase,width,height")
        for (i in 0..480) {
            controller.advanceTo(i / 480f)
            controller.extents(extent)
            val phase = (controller.centreX - bar.centreOf(0)) / (bar.centreOf(4) - bar.centreOf(0))
            rows += "${i / 480f},$phase,${extent.width},${extent.height}"
            if (phase > .95f) {
                minimumWidth = minOf(minimumWidth, extent.width)
                val error = abs(extent.width / 230f - 227.5f / 235f) +
                    abs(extent.height / 162f - 174f / 164f)
                if (error < best) {
                    best = error
                    matchedWidth = extent.width
                    pairedHeight = extent.height
                }
            }
            assertFalse(controller.solverFailed)
            assertTrue(extent.height <= bar.height * 1.23f)
        }
        val widthRatio = matchedWidth / extent.width
        val heightRatio = pairedHeight / extent.height
        File("build/reports/atlas").apply { mkdirs() }.resolve("tap-arrival-trace.csv")
            .writeText(rows.joinToString("\n") + "\n")
        println("ARRIVAL_RECOIL minimumWidth=$minimumWidth matchedWidth=$matchedWidth height=$pairedHeight ratios=$widthRatio,$heightRatio")
        // IMG6694 vs6701: about 227.5x174 vs 235x164. Untimed stills do not establish
        // the peak or its time; find a paired spatial state, not independent axis peaks.
        // This constrains an authored recovery envelope, with
        // ~4px horizontal and 3px vertical uncertainty admitted after normalization.
        assertEquals(227.5f / 235f, widthRatio, .02f, "arrival width compression is excessive")
        assertEquals(174f / 164f, heightRatio, .02f, "arrival lost the paired vertical recoil")
        assertTrue(minimumWidth / extent.width >= 227.5f / 235f - .02f,
            "recoil exceeds the selected conservative width bound")
    }

    @Test fun recoilSurvivesReversedTravelAndPresentationCadence() {
        val bar = referenceBar()
        fun trace(start: Int, target: Int, hz: Int, motion: Boolean = true): List<FloatArray> {
            val controller = GlassPoseController(GlassPoseSpec.Calm)
            controller.attach(bar, null, null)
            controller.motionEnabled = motion
            controller.snapToRest(start)
            controller.retarget(target)
            val extent = GlassPoseExtents()
            val samples = arrayListOf<FloatArray>()
            for (i in 0..hz) {
                controller.advanceFrameTo(i.toFloat() / hz)
                controller.extents(extent)
                assertFalse(controller.solverFailed)
                assertTrue(extent.height <= bar.height * 1.23f)
                if (!motion) {
                    assertEquals(230f, extent.width, .1f, "reduced motion grew a recoil")
                    assertEquals(162f, extent.height, .1f, "reduced motion grew a recoil")
                }
                if (i % (hz / 30) == 0) samples += floatArrayOf(extent.width, extent.height)
            }
            assertEquals(bar.centreOf(target), controller.centreX, .1f)
            assertEquals(230f, extent.width, .1f)
            assertEquals(162f, extent.height, .1f)
            return samples
        }
        for (distance in 1..4) {
            val reference = trace(0, distance, 120)
            for (hz in listOf(30, 60, 90, 120)) {
                val candidate = trace(4, 4 - distance, hz)
                for (i in reference.indices) for (axis in 0..1) {
                    assertEquals(reference[i][axis], candidate[i][axis], .75f,
                        "reversed $distance-slot tap at $hz Hz, sample $i, axis $axis")
                }
            }
        }
        trace(0, 4, 60, motion = false)
    }

    private fun referenceBar() = GlassSelectorBar(1044f, 186f, 27f, 5, 93f, 115f, 81f, 90f,
        150f, 112.5f, 3f, 700f)

    @Test fun longTapHeightHasAlreadyChangedAtTheOriginalMidpoint() {
        // Original 1170px crop: bar x63..1107, y76..260. Resting endpoint centres
        // approximately189 and981; settled inset height162. This fixture uses that geometry,
        // not Atlas's five-slot example with its different width and labels.
        val bar = GlassSelectorBar(1044f, 186f, 27f, 5, 93f, 115f, 81f, 90f,
            150f, 112.5f, 3f, 700f)
        val controller = GlassPoseController(GlassPoseSpec.Calm)
        controller.attach(bar, null, null); controller.snapToRest(0); controller.retarget(4)
        val extent = GlassPoseExtents()
        var best = Float.POSITIVE_INFINITY
        var matchedWidth = 0f
        var matchedHeight = 0f
        var matchedPhase = 0f
        for (i in 0..480) {
            controller.advanceTo(i / 480f); controller.extents(extent)
            assertFalse(controller.isHeld)
            assertFalse(controller.solverFailed)
            assertTrue(extent.height <= bar.height * 1.23f)
            val phase = (controller.centreX - bar.centreOf(0)) / (bar.centreOf(4) - bar.centreOf(0))
            if (abs(phase - .487f) < best) {
                best = abs(phase - .487f)
                matchedWidth = extent.width; matchedHeight = extent.height; matchedPhase = phase
            }
        }
        println("ORIGINAL_MIDPOINT phase=$matchedPhase width=$matchedWidth height=$matchedHeight")
        // IMG_6698: six clear central columns give171..173px from the strongest inward/outward
        // contour gradients. +/-4px admits edge convention and the186px fixture's rounded
        // outer height. Compare spatial phase, not a fabricated time for an untimed still.
        assertEquals(172f, matchedHeight, 4f, "transit height at the original midpoint")
        assertEquals(330f, matchedWidth, 12f, "midpoint width including partial side-edge uncertainty")
        assertEquals(162f, extent.height, .5f, "arrival must recover to the resting inset")
    }
}
