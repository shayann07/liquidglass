package com.wexpa.liquidglass

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Degenerate layouts the bar can pass through on its way somewhere else. */
class GlassRobustnessTest {
    @Test fun aBarNarrowerThanItsInsetsHasOrderedCentresAndNeverThrows() {
        for (width in listOf(0f, 4f, 11.9f)) {
            val g = GlassTabBarGeometry(barWidth = width, inset = 6f, count = 4)
            assertTrue(g.firstCentre <= g.lastCentre, "centres crossed at width $width")
            // The drag path clamps the finger between the first and last centre.
            100f.coerceIn(g.firstCentre, g.lastCentre)
            assertTrue(g.nearestIndex(50f) in 0..3)
            assertEquals(g.projected(3, 9_999f, 1f), 3)
        }
    }
}
