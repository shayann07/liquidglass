package com.wexpa.liquidglass

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Scores the outer contour (#202, m202d) against the iOS 27 numbers it was fitted to: the Safari lab pill's unlit side
 * and top pole, in levels over flat greys 0 / 128 / 255, at the first three pixel centres outside the outline
 * (d = 0.5, 1.5, 2.5). The tolerance (2 levels) is authored: the fit's worst error is 1.1 levels, plus 8-bit rounding.
 */
class GlassContourFitTest {
    private val pad = 8
    private val size = 132
    private val lw = size + pad * 2
    private val c = pad + size / 2

    /** The level of the pixel [k] rows (0 = first wholly outside) from the outline, over grey [g], composited as the host does. */
    private fun level(contour: Float, floor: Float, g: Int, side: Boolean, k: Int): Double {
        val rgb = (g shl 16) or (g shl 8) or g
        val shot = GlassRender.render(width = size, height = size, pad = pad, refractBand = 0f,
            outerContour = contour, outerContourFloor = floor, backdrop = { _, _ -> rgb })
        val px = if (side) shot[c * lw + pad + size + k] else shot[(pad - 1 - k) * lw + c]
        val a = ((px ushr 24) and 0xFF) / 255.0
        return GlassRender.luma(px) + g * (1.0 - a)
    }

    private fun score(name: String, contour: Float, floor: Float, side: Boolean, ios: List<List<Double>>) {
        val greys = listOf(0, 128, 255)
        var worst = 0.0
        for (k in 0..2) for ((i, g) in greys.withIndex()) {
            val got = level(contour, floor, g, side, k)
            val err = abs(got - ios[k][i])
            worst = maxOf(worst, err)
            println("GLASS_CONTOUR_FIT $name d=${k + 0.5} over $g: got ${"%.1f".format(got)} ios ${ios[k][i]}")
            assertTrue(err <= 2.0, "$name d=${k + 0.5} over $g: got $got, iOS ${ios[k][i]}")
        }
        println("GLASS_CONTOUR_FIT $name worst error ${"%.2f".format(worst)} levels")
    }

    @Test
    fun darkContourMatchesIos27() {
        score("dark side", 0.61f, 0f, true, listOf(listOf(0.0, 51.0, 99.5), listOf(0.0, 89.0, 178.0), listOf(0.0, 128.0, 255.0)))
        score("dark pole", 0.61f, 0f, false, listOf(listOf(0.0, 128.0, 255.0), listOf(0.0, 128.0, 255.0), listOf(0.0, 128.0, 255.0)))
    }

    @Test
    fun lightContourMatchesIos27() {
        score("light side", -0.30f, 0.13f, true, listOf(listOf(0.0, 52.0, 178.0), listOf(0.0, 89.0, 216.0), listOf(0.0, 128.0, 255.0)))
        score("light pole", -0.30f, 0.13f, false, listOf(listOf(0.0, 118.0, 245.0), listOf(0.0, 122.0, 250.0), listOf(0.0, 128.0, 255.0)))
    }
}
