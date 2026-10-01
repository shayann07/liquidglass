package com.wexpa.liquidglass

import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlassSurfacePullTest {
    @Test fun settingsAndOriginalSmallControlsKeepTheirExistingResponse() {
        for (density in listOf(1f, 2.75f, 3f)) for (side in listOf(38f, 56f, 62f, 78f, 80f)) {
            val p = glassPullDeformation(200f, -300f, side * density, .217f, 1f, .35f)
            assertEquals(p, glassSurfacePull(p, side * density, side * density, density, side * density))
        }
    }

    @Test fun largeSurfaceDragAddsAtMostOneDpPerEdgeInEveryDirection() {
        for (density in listOf(1f, 2.75f, 3f)) for ((w, h) in listOf(350f to 44f, 350f to 220f, 220f to 350f)) {
            for (degrees in 0..359 step 5) for (distance in listOf(.01f, 10f, 100f, 10000f)) {
                val a = degrees * kotlin.math.PI.toFloat() / 180f
                val x = cos(a) * distance * density; val y = sin(a) * distance * density
                val along = glassPullExtent(w * density, h * density, 20f * density, 1f, 1f, x, y)
                val across = glassPullExtent(w * density, h * density, 20f * density, 1f, 1f, -y, x)
                val raw = glassPullDeformation(x, y, along, .217f, 1f, .35f, acrossExtentPx = across)
                val p = glassSurfacePull(raw, w * density, h * density, density, along)
                assertTrue((p.along - 1f) * along <= 2.001f * density)
                assertTrue((1f - p.across) * across <= 2.001f * density)
                assertTrue(p.along <= raw.along && p.across >= raw.across)
            }
        }
    }

    @Test fun sizeTransitionAndTinyDragAreContinuousAndIdleRemainsUnchanged() {
        val raw = glassPullDeformation(30f, 0f, 100f, .217f, 1f, .35f)
        for (boundary in listOf(80f, 160f)) {
            val a = glassSurfacePull(raw, boundary - .001f, 44f, 1f, 100f)
            val b = glassSurfacePull(raw, boundary + .001f, 44f, 1f, 100f)
            assertEquals(a.along, b.along, .00001f)
        }
        val tiny = glassPullDeformation(.1f, 0f, 200f, .217f, 1f, .35f)
        assertTrue(glassSurfacePull(tiny, 350f, 200f, 1f, 200f).along > 1f)
        assertEquals(GlassPullDeformation.None, glassSurfacePull(GlassPullDeformation.None, 350f, 200f, 1f, 200f))
    }
}
