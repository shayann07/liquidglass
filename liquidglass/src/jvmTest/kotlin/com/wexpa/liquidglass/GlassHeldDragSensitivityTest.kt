package com.wexpa.liquidglass

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Owner's r9.1 rejection plus 6735's approximately .79 bar-height extreme.
 * Pointer speeds/compliance below are authored probes, not recovered Apple input data. */
class GlassHeldDragSensitivityTest {
    private val bar = GlassSelectorBar(
        width = 1006f, height = 162f, inset = 18f, count = 5, cornerRadius = 81f,
        baseHalfWidth = 96f, baseHalfHeight = 69f, allowedHalfHeight = 69f,
        heldHalfHeight = 96f, heldHalfWidth = 128f, density = 3f, maxBodyWidth = 600f,
    )
    private val previous = GlassPoseSpec().copy(
        heldReferenceSpeed = 14f, elongationSlots = .85f, squeeze = .55f,
        pullStrainPerDp = .0058f, pullPressurePerDp = -.0011f,
        pullOnsetDp = 8f, pullMaxStrain = .22f,
    )

    private fun held(spec: GlassPoseSpec): GlassPoseController = GlassPoseController(spec).also {
        it.attach(bar, null, null); it.snapToRest(2)
        it.pointerDown(bar.centreOf(2), bar.centreY, 1_825_000.0, true)
        it.beginDrag(bar.centreOf(2), bar.centreY)
        repeat(120) { frame -> it.advanceTo((frame + 1) / 120f) }
        it.pointerMove(bar.centreOf(2), bar.centreY, 1_825_001.0)
    }

    private fun speed(spec: GlassPoseSpec, slotsPerSecond: Float): GlassPoseExtents {
        val c = held(spec)
        val slot = bar.centreOf(3) - bar.centreOf(2)
        repeat(120) {
            val t = (it + 1) / 240.0
            c.pointerMove(bar.centreOf(2) + (slot * slotsPerSecond * t).toFloat(),
                bar.centreY, 1_825_001.0 + t)
            c.advanceTo(1f + t.toFloat())
        }
        assertTrue(c.isHeld)
        return GlassPoseExtents().also { c.extents(it) }
    }

    private fun pull(spec: GlassPoseSpec, dp: Float): GlassPoseExtents {
        val c = held(spec)
        c.pointerMove(bar.centreOf(2), bar.centreY - dp * bar.density, 1_825_001.1)
        repeat(240) { c.advanceTo(1f + (it + 1) / 120f) }
        return GlassPoseExtents().also { c.extents(it) }
    }

    @Test fun extremeHorizontalPullStillFlattensWithoutTheRejectedCollapse() {
        val calm = speed(GlassPoseSpec(), 0f)
        val extreme = speed(GlassPoseSpec(), 60f)
        println("HELD-EXTREME calm=${calm.width},${calm.height} extreme=${extreme.width},${extreme.height}")
        assertTrue(extreme.height < 2f * bar.baseHalfHeight, "hard drag must still flatten")
        assertTrue(extreme.height >= .79f * bar.height, "more squeezed than the reference envelope")
        assertTrue(extreme.width < calm.width * 1.75f, "maximum spine stretch remains excessive")
    }

    @Test fun moderateHorizontalInputHasLessSqueezeAndStretch() {
        val calm = speed(GlassPoseSpec(), 0f)
        for (v in listOf(3f, 10f, 20f)) {
            val now = speed(GlassPoseSpec(), v); val old = speed(previous, v)
            println("HELD-SPEED $v old=${old.width},${old.height} now=${now.width},${now.height}")
            assertTrue(now.height > old.height, "squeeze sensitivity unchanged at $v")
            assertTrue(now.width < old.width, "stretch sensitivity unchanged at $v")
            assertTrue(now.height < calm.height, "small movement stopped responding")
        }
    }

    @Test fun offAxisInputStaysContinuousWithAMuchSmallerMaximum() {
        val calm = pull(GlassPoseSpec(), 0f)
        var lastAspect = calm.height / calm.width
        for (dp in listOf(1f, 2f, 4f, 8f, 16f, 32f, 64f, 128f)) {
            val now = pull(GlassPoseSpec(), dp); val old = pull(previous, dp)
            val aspect = now.height / now.width
            println("HELD-PULL $dp old=${old.width},${old.height} now=${now.width},${now.height}")
            assertTrue(aspect > lastAspect, "dead zone or reversal at $dp dp")
            assertTrue(aspect < old.height / old.width, "off-axis sensitivity unchanged at $dp dp")
            lastAspect = aspect
        }
        val extreme = pull(GlassPoseSpec(), 512f)
        assertTrue(extreme.height <= calm.height * 1.12f, "off-axis length remains excessive")
        assertTrue(extreme.width >= calm.width * .84f, "off-axis width remains too pinched")
    }

    @Test fun tapContoursAreUnaffectedByHeldSensitivity() {
        for (destination in listOf(1, 4)) {
            fun c(spec: GlassPoseSpec) = GlassPoseController(spec).also {
                it.attach(bar, null, null); it.snapToRest(0); it.retarget(destination)
            }
            val now = c(GlassPoseSpec()); val old = c(previous)
            val a = GlassPoseExtents(); val b = GlassPoseExtents()
            repeat(180) {
                val t = (it + 1) / 120f
                now.advanceTo(t); old.advanceTo(t); now.extents(a); old.extents(b)
                assertEquals(b.width, a.width, 1e-4f); assertEquals(b.height, a.height, 1e-4f)
                assertTrue(abs(now.centreX - old.centreX) < 1e-4f)
            }
        }
    }
}
