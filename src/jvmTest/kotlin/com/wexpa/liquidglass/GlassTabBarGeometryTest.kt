package com.wexpa.liquidglass

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The item geometry a [GlassTabBar] drags against, pinned on the JVM.
 *
 * Two of these already shipped as bugs in the app before the component existed: a drag that
 * anchored at the wrong point could not reach the last item, and an index mapping off by a slot
 * would have looked identical from the outside. Neither needs a device to catch.
 */
class GlassTabBarGeometryTest {

    private val g = GlassTabBarGeometry(barWidth = 1342f, inset = 21f, count = 5)

    @Test
    fun testSlotsShareTheBarInsideTheInset() {
        assertEquals((1342f - 42f) / 5f, g.slotWidth, 0.001f)
        assertEquals(21f + g.slotWidth / 2f, g.firstCentre, 0.001f)
        assertEquals(1342f - 21f - g.slotWidth / 2f, g.lastCentre, 0.001f)
    }

    @Test
    fun testCentresAreSymmetricAboutTheBar() {
        for (i in 0 until 5) {
            assertEquals(1342f - g.centreOf(i), g.centreOf(4 - i), 0.001f, "centre $i")
        }
    }

    @Test
    fun testNearestIndexRoundTripsEveryCentreAndClampsTheEnds() {
        for (i in 0 until 5) assertEquals(i, g.nearestIndex(g.centreOf(i)))
        assertEquals(0, g.nearestIndex(-500f))
        assertEquals(4, g.nearestIndex(5000f))
        // Exactly on a boundary belongs to the item that starts there.
        assertEquals(1, g.nearestIndex(21f + g.slotWidth))
    }

    @Test
    fun testAFlickCarriesOneItemAndNeverPastTheEnds() {
        assertEquals(3, g.projected(lastIndex = 2, releasedVelocity = 900f, flingVelocity = 420f))
        assertEquals(1, g.projected(lastIndex = 2, releasedVelocity = -900f, flingVelocity = 420f))
        assertEquals(2, g.projected(lastIndex = 2, releasedVelocity = 300f, flingVelocity = 420f))
        assertEquals(4, g.projected(lastIndex = 4, releasedVelocity = 5000f, flingVelocity = 420f))
        assertEquals(0, g.projected(lastIndex = 0, releasedVelocity = -5000f, flingVelocity = 420f))
    }
}
