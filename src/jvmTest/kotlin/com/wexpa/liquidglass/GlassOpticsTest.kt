package com.wexpa.liquidglass

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Optics asserted on rendered pixels rather than on the shader's text.
 *
 * Every other test here checks that the shader compiles and declares what the host binds. These
 * check what it draws, which is the only way to catch a regression in the geometry that still
 * compiles perfectly.
 */
class GlassOpticsTest {

    /** A hard horizontal boundary: white above [edgeY], black below. */
    private fun halfPlane(edgeY: Int): (Int, Int) -> Int =
        { _, y -> if (y < edgeY) 0xFFFFFF else 0x000000 }

    @Test
    fun testTheRimSamplesOutward() {
        // The claim this library differs from every other implementation on. A panel is placed
        // wholly inside the black half, its top edge 20px below a white/black boundary. Sampling
        // outward pulls what is beyond the rim inward, so white must appear inside a panel that
        // covers only black. Sampling inward could never bring it in.
        val pad = 40
        val w = 240
        val h = 120
        // The gap has to be smaller than the displacement or the test proves nothing either
        // way; 8px of black above the rim against 30px of displacement is comfortably outward.
        val boundary = pad - 8
        val px = GlassRender.render(
            width = w, height = h, pad = pad,
            refractBand = 30f, refractDepth = 30f,
            backdrop = halfPlane(boundary),
        )
        val lw = w + pad * 2
        val column = pad + w / 2
        // Rows strictly inside the panel, below its top edge.
        val inside = (pad + 1 until pad + 24).map { GlassRender.luma(px[it * lw + column]) }
        assertTrue(
            inside.any { it > 40.0 },
            "outward sampling must pull the white beyond the rim into the panel; got $inside",
        )
    }

    @Test
    fun testTheClosedFormNormalIsSymmetricInBothAxes() {
        // The closed form picks its quadrant with two sign branches and its axis with a step,
        // which is exactly where a gradient bug hides: get one branch wrong and the shape still
        // renders, just asymmetrically. A symmetric shape over a symmetric backdrop must produce
        // a symmetric image, so mirroring the render must reproduce it.
        val pad = 40
        val w = 260
        val h = 160
        val lw = w + pad * 2
        val lh = h + pad * 2
        // Symmetric about the panel's own centre lines, not the layer's pixel indices: pixel i
        // has its centre at i + 0.5, so the axis of symmetry falls between two pixels. Building
        // the pattern from the distance to that axis makes it exactly mirror-symmetric under
        // x -> lw - 1 - x, which is what the assertion below compares.
        val rings = { x: Int, y: Int ->
            val dx = abs(x + 0.5 - lw / 2.0)
            val dy = abs(y + 0.5 - lh / 2.0)
            val r = kotlin.math.sqrt(dx * dx + dy * dy)
            if ((r / 24.0).toInt() % 2 == 0) 0xFFFFFF else 0x101010
        }
        val px = GlassRender.render(
            width = w, height = h, pad = pad,
            refractBand = 20f, // inradius 80, so the closed form is taken
            refractDepth = 16f, radii = FloatArray(4) { 40f },
            backdrop = rings,
        )
        var worstX = 0.0
        var worstY = 0.0
        for (y in pad until pad + h) {
            for (x in pad until pad + w) {
                val here = GlassRender.luma(px[y * lw + x])
                val mirroredX = GlassRender.luma(px[y * lw + (lw - 1 - x)])
                val mirroredY = GlassRender.luma(px[(lh - 1 - y) * lw + x])
                worstX = maxOf(worstX, abs(here - mirroredX))
                worstY = maxOf(worstY, abs(here - mirroredY))
            }
        }
        assertTrue(worstX < 6.0, "left and right must mirror; worst difference $worstX")
        assertTrue(worstY < 6.0, "top and bottom must mirror; worst difference $worstY")
    }

    @Test
    fun testRefractionIsContinuousAcrossTheMedialAxisOfAPill() {
        // The seam this library's wide-epsilon gradient exists to prevent. A capsule whose
        // refraction band reaches its own centre line is the worst case: the distance field's
        // gradient flips there, and a naive normal leaves a one-pixel stripe of unrefracted
        // backdrop down the middle. Over a vertical stripe pattern that stripe is plainly
        // visible as a row whose luminance departs from its neighbours.
        val pad = 30
        val w = 400
        val h = 80
        val px = GlassRender.render(
            width = w, height = h, pad = pad,
            refractBand = 40f, // inradius 40: the band reaches the axis exactly
            refractDepth = 18f,
            backdrop = { x, _ -> if ((x / 6) % 2 == 0) 0xFFFFFF else 0x000000 },
        )
        val lw = w + pad * 2
        val axis = pad + h / 2
        val rowMean = { y: Int ->
            (pad + 120 until pad + w - 120).map { GlassRender.luma(px[y * lw + it]) }.average()
        }
        val onAxis = rowMean(axis)
        val above = rowMean(axis - 6)
        val below = rowMean(axis + 6)
        val neighbourMean = (above + below) / 2.0
        assertTrue(
            abs(onAxis - neighbourMean) < 12.0,
            "no seam down the medial axis: axis $onAxis vs neighbours $neighbourMean",
        )
    }

    @Test
    fun testRimSofteningSpreadsTheCompressionLine() {
        // Where the rim compresses a hard boundary it lands as one bright line, which reads as a
        // drawn stroke. Softening averages across the direction the compression runs, so the
        // steepest step in the rim band must get shallower while the band still carries the
        // boundary. Measured as the largest single-pixel jump down a column through the rim.
        val pad = 40
        val w = 240
        val h = 140
        val lw = w + pad * 2
        val half = { _: Int, y: Int -> if (y < pad - 6) 0xFFFFFF else 0x000000 }
        fun steepest(soft: Float): Double {
            val px = GlassRender.render(
                width = w, height = h, pad = pad,
                refractBand = 30f, refractDepth = 26f, rimSoft = soft,
                backdrop = half,
            )
            val col = (pad + 1 until pad + 34).map { GlassRender.luma(px[it * lw + pad + w / 2]) }
            return col.zipWithNext { a, b -> abs(b - a) }.max()
        }
        val hard = steepest(0f)
        val soft = steepest(6f)
        assertTrue(soft < hard, "softening must reduce the steepest step; $soft against $hard")
    }

    @Test
    fun testTintAsAMediumKeepsMoreOfTheBackdropStructure() {
        // A blend pulls every pixel the same distance toward one colour, which flattens whatever
        // light and shade the backdrop had. A medium multiplies, so the structure survives. Over
        // a textured backdrop the medium must leave more variation behind than the blend does.
        val pad = 24
        val w = 220
        val h = 160
        val lw = w + pad * 2
        val texture = { x: Int, y: Int ->
            val v = ((x * 7 + y * 13) % 200) + 30
            (v shl 16) or (v shl 8) or v
        }
        fun spread(absorb: Float): Double {
            val px = GlassRender.render(
                width = w, height = h, pad = pad,
                refractBand = 20f, refractDepth = 8f,
                tintAbsorb = absorb, tintAlpha = 0.55f, tint = Triple(0.2f, 0.5f, 1f),
                backdrop = texture,
            )
            val vals = (pad + 40 until pad + h - 40).flatMap { y ->
                (pad + 40 until pad + w - 40).map { x -> GlassRender.luma(px[y * lw + x]) }
            }
            val mean = vals.average()
            return kotlin.math.sqrt(vals.sumOf { (it - mean) * (it - mean) } / vals.size)
        }
        val blended = spread(0f)
        val medium = spread(1f)
        assertTrue(medium > blended, "the medium must preserve more structure; $medium against $blended")
    }

    @Test
    fun testTheDomeFieldKeepsRefractionAtTheSpineOfAWidePill() {
        // In a long pill whose band reaches its own centre line, distance-to-edge collapses along
        // that line and the lens has no direction to bend in. The dome field replaces it there.
        // Over vertical stripes, a row on the axis must still differ from the raw backdrop; if
        // refraction had died it would be pixel-identical to what lies behind it.
        val pad = 30
        val w = 480
        val h = 80
        val lw = w + pad * 2
        val stripes = { x: Int, _: Int -> if ((x / 7) % 2 == 0) 0xFFFFFF else 0x000000 }
        val px = GlassRender.render(
            width = w, height = h, pad = pad,
            refractBand = 40f, // inradius is 40: the band reaches the axis
            refractDepth = 20f,
            backdrop = stripes,
        )
        val axis = pad + h / 2
        var differing = 0
        var total = 0
        for (x in pad + 40 until pad + w - 40) {
            val rendered = GlassRender.luma(px[axis * lw + x])
            val raw = GlassRender.luma(stripes(x, axis) or (0xFF shl 24))
            if (abs(rendered - raw) > 20.0) differing++
            total++
        }
        assertTrue(
            differing > total / 10,
            "refraction must survive on the spine; only $differing of $total pixels moved",
        )
    }

    @Test
    fun testTheDarkContourSitsInsideTheCoverageRamp() {
        // edgeShadow drawn on the outermost pixel is multiplied by a partial alpha and
        // composited against whatever lies outside, so over a dark ground it does nothing. It
        // has to land where coverage is 1. Rendered over a mid grey, raising it must darken a
        // pixel just inside the edge, not merely the anti-aliased boundary.
        val pad = 24
        val w = 200
        val h = 100
        val grey = { _: Int, _: Int -> 0x808080 }
        val without = GlassRender.render(width = w, height = h, pad = pad, edgeShadow = 0f, backdrop = grey)
        val with = GlassRender.render(width = w, height = h, pad = pad, edgeShadow = 0.25f, backdrop = grey)
        val lw = w + pad * 2
        val y = pad + h / 2
        val x = pad + 2 // two pixels inside the left edge, clear of the coverage ramp
        val drop = GlassRender.luma(without[y * lw + x]) - GlassRender.luma(with[y * lw + x])
        assertTrue(drop > 8.0, "the contour must darken a fully covered pixel; drop was $drop")
    }
}
