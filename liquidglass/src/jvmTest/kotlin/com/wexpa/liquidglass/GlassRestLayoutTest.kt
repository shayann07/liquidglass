package com.wexpa.liquidglass

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Layout feasibility, independent of any proposed Apple motion constants. */
class GlassRestLayoutTest {
    private fun bar(widthDp: Float, density: Float = 3f, count: Int = 5) = glassSelectorBarOf(
        Density(density), widthDp * density, 62f * density,
        GlassTabBarStyle.V3(true).copy(height = 62.dp), count, 0f, GlassSelectorSpec(),
    )

    @Test fun firstPublishedRestIsContainedAtEveryAnchorAcrossCompactLayouts() {
        for (density in listOf(1f, 2.625f, 3f, 3.5f)) {
            for (width in listOf(280f, 320f, 348f, 383.3333f, 440f)) {
                for (count in 3..5) {
                    val b = bar(width, density, count)
                    val c = GlassPoseController()
                    c.attach(b, null, null)
                    for (index in 0 until count) {
                        c.snapToRest(index)
                        assertTrue(c.protrusion() < .05f,
                            "first rest protrudes: width=$width density=$density count=$count index=$index by ${c.protrusion()}px")
                        assertEquals(b.centreOf(index), c.centreX, .01f, "layout must not move navigation anchors")
                        assertEquals(54f * density, c.extents(GlassPoseExtents()).height, .05f)
                        assertTrue(c.isIdle, "feasible rest must not need solver frames")
                    }
                }
            }
        }
    }

    @Test fun compactEndToEndTapsRecoverToTheDeclaredAnchors() {
        for (width in listOf(280f, 320f, 348f)) for (start in listOf(0, 4)) {
            val b = bar(width)
            val c = GlassPoseController()
            c.attach(b, null, null); c.snapToRest(start)
            c.retarget(4 - start)
            for (i in 0..300) {
                c.advanceTo(i / 240f)
                assertTrue(c.protrusion() < .1f, "tap escaped compact bar at $width/$i")
                assertFalse(c.solverFailed, "infeasible tap contact at $width/$i")
            }
            assertEquals(b.centreOf(4 - start), c.centreX, .25f)
            assertEquals(2f * b.baseHalfWidth, c.extents(GlassPoseExtents()).width, .5f)
            assertTrue(c.isIdle, "compact tap did not settle")
        }
    }

    @Test fun resizeFromHeldRebasesToAFeasibleRestWithoutChangingSelection() {
        val wide = bar(420f)
        val narrow = bar(320f)
        for (index in listOf(0, 4)) {
            val c = GlassPoseController()
            c.attach(wide, null, null); c.snapToRest(index)
            c.pointerDown(wide.centreOf(index), wide.centreY, 2_000_000.0, true)
            for (i in 0..90) c.advanceTo(i / 120f)
            assertTrue(c.isHeld)
            assertTrue(c.protrusion() > 1f, "held body must retain free protrusion")
            c.attach(narrow, null, null)
            assertFalse(c.isHeld)
            assertTrue(c.protrusion() < .05f)
            assertEquals(narrow.centreOf(index), c.centreX, .01f)
            c.cancel(index)
            for (i in 91..240) c.advanceTo(i / 120f)
            assertEquals(narrow.centreOf(index), c.centreX, .25f)
            assertTrue(c.protrusion() < .05f)
            assertTrue(c.isIdle)
        }
    }

    @Test fun layoutsWithEnoughSpaceKeepTheirDeclaredRestSize() {
        val pixel = bar(383.3333f)
        assertEquals(252f, 2f * pixel.baseHalfWidth, .01f)
        val phone = glassSelectorBarOf(
            Density(3f), 834f, 186f,
            GlassTabBarStyle.V3(true).copy(height = 62.dp, pillWidth = (284f / 3f).dp),
            3, 0f, GlassSelectorSpec(),
        )
        assertEquals(284f, 2f * phone.baseHalfWidth, .01f)
        val previousController = glassSelectorBarOf(
            Density(3f), 1044f, 186f, GlassTabBarStyle.V3(true).copy(height = 62.dp),
            5, 0f, GlassSelectorSpec(poseMotion = false),
        )
        assertEquals(252f, 2f * previousController.baseHalfWidth, .01f)
    }
}
