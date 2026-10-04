package com.wexpa.liquidglass

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Compare shape at observed travel phase; the original stills have no input timestamps. */
class GlassTapPhaseReferenceTest {
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
            assertTrue(controller.protrusion() < .1f)
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
