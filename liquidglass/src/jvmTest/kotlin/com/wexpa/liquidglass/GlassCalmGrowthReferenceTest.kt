package com.wexpa.liquidglass

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Independent Phone T01 geometry; no pointer gain or rendered-Astra target is fitted here. */
class GlassCalmGrowthReferenceTest {
    @Test fun aCalmPhoneHoldDoesNotMagnifyTheWholeRestingCapsule() {
        // IMG_6756: resting bar186px, selected capsule284x162px. The settled held
        // capsule is approximately340–360x225px; a uniformly enlarged capsule is ~394px.
        // ±10px width admits edge visibility/held-frame variation, not the old model.
        val bar = GlassSelectorBar(
            width = 834f, height = 186f, inset = 18f, count = 3, cornerRadius = 93f,
            baseHalfWidth = 142f, baseHalfHeight = 81f, allowedHalfHeight = 81f,
            heldHalfWidth = 177f, heldHalfHeight = 112.5f, density = 3f, maxBodyWidth = 700f,
        )
        val c = GlassPoseController()
        c.attach(bar, null, null); c.snapToRest(1)
        c.pointerDown(bar.centreOf(1), bar.centreY, 1_825_000.0, true)
        repeat(180) { c.advanceTo((it + 1) / 120f) }
        val e = GlassPoseExtents(); c.extents(e)
        assertTrue(c.isHeld)
        assertEquals(225f, e.height, 4f, "Phone calm height")
        assertEquals(350f, e.width, 10f, "Phone calm width: pressure must not magnify the spine")
    }
}
