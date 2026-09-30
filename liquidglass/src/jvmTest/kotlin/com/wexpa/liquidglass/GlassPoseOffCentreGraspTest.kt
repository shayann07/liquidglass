package com.wexpa.liquidglass

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** A still image cannot establish the finger's vertical position. Astra restores the
 * requested material attachment in both axes; ink alignment is a source-coordinate concern.
 * Supersedes the inherited test that enforced vertical sliding under an off-centre finger.
 */
class GlassPoseOffCentreGraspTest {

    private fun bar() = GlassSelectorBar(
        width = 1006f, height = 162f, inset = 18f, count = 5, cornerRadius = 81f,
        baseHalfWidth = 96f, baseHalfHeight = 69f, allowedHalfHeight = 69f,
        heldHalfHeight = 96f, heldHalfWidth = 128f, density = 3f, maxBodyWidth = 600f,
    )

    private fun boundsOf(b: GlassSelectorBar, width: Float = 150f): Pair<FloatArray, FloatArray> {
        val bounds = FloatArray(b.count * 2)
        val centres = FloatArray(b.count)
        for (i in 0 until b.count) {
            val c = b.centreOf(i)
            centres[i] = c
            bounds[i * 2] = c - width / 2f
            bounds[i * 2 + 1] = c + width / 2f
        }
        return bounds to centres
    }

    private fun controller(b: GlassSelectorBar, startAt: Int): GlassPoseController {
        val c = GlassPoseController()
        val (bounds, centres) = boundsOf(b)
        c.attach(b, bounds, centres, inkHalfHeight = b.baseHalfHeight * 0.62f)
        c.snapToRest(startAt)
        return c
    }

    /** The press row the device pass actually uses: a quarter of the way down the bar. */
    private fun pressRow(b: GlassSelectorBar) = b.centreY - b.height / 4f

    @Test
    fun anOffCentreHoldPreservesTheAttachedPoint() {
        val b = bar()
        val c = controller(b, startAt = 2)
        val y = pressRow(b)
        c.pointerDown(b.centreOf(2), y, 0.0, eligible = true)
        val (bounds, centres) = boundsOf(b)
        var t = 0f
        while (t < 0.6f) {
            t += 1f / 240f
            c.attach(b, bounds, centres, inkHalfHeight = b.baseHalfHeight * 0.62f)
            c.advanceTo(t)
        }
        assertTrue(c.isHeld, "the eligible off-centre press never became a hold")
        val e = GlassPoseExtents()
        c.extents(e)
        val offset = (e.top + e.bottom) / 2f - b.centreY
        println(
            "OFFCENTRE-HOLD pressRow=%.1f centreY=%.1f bodyCentre=%.1f offset=%.1f height=%.1f"
                .format(y, b.centreY, (e.top + e.bottom) / 2f, offset, e.height),
        )
        assertTrue(
            abs(c.graspRendered(FloatArray(2))[1] - y) <= 0.1f,
            "an off-centre hold moved the body's centre $offset px off the bar's: the selected " +
                "ink no longer covers the label row it belongs to",
        )
    }

    @Test
    fun anOffCentreHorizontalDragPreservesVerticalAttachment() {
        val b = bar()
        val c = controller(b, startAt = 0)
        val y = pressRow(b)
        var x = b.centreOf(0)
        c.pointerDown(x, y, 0.0, eligible = true)
        c.beginDrag(x, y)
        val (bounds, centres) = boundsOf(b)
        val e = GlassPoseExtents()
        var worst = 0f
        var t = 0.0
        var f = 0f
        val span = b.centreOf(4) - b.centreOf(0)
        while (f < 1.0f) {
            f += 1f / 240f
            t += 1.0 / 240.0
            x = (b.centreOf(0) + span * (f / 1.0f)).coerceAtMost(b.centreOf(4))
            c.pointerMove(x, y, t)
            c.attach(b, bounds, centres, inkHalfHeight = b.baseHalfHeight * 0.62f)
            c.advanceTo(f)
            c.extents(e)
            val offset = c.graspRendered(FloatArray(2))[1] - y
            if (abs(offset) > abs(worst)) worst = offset
        }
        println("OFFCENTRE-DRAG worstOffset=%.1f".format(worst))
        assertTrue(
            abs(worst) <= 0.1f,
            "a horizontal drag pressed $y px from the bar's centre lifted the body $worst px off " +
                "the bar's centre line",
        )
    }
}
