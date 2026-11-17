package com.wexpa.liquidglass

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A resting capsule on the measured resting map ([GlassRestMap.Measured]) shows that map all the way round its outline.
 * Over the Safari lab pill's stripes (16 November stills) iOS displaces the straight run's outer band as it does the round
 * end's, and nothing changes where the end begins. The library used to lens only the corner arcs and hand over to a
 * near-identity run map over 0.2 of the radius; near the rim the two maps show rows about 0.7 of the band apart, so
 * text lines hooked at each cap start (the shown row moved by about 3.5 rows per pixel there).
 *
 * The backdrop is a vertical ramp, so a pixel's level says which row it shows. Near each cap start the shown row must
 * move gently along the run (under one row per pixel), and mid-run it must show what the cap's tip shows at the same
 * depth (within two rows: 8-bit levels and half-pixel sampling where the map is steep; the near-identity run map
 * the library used to draw sits about 40 rows off the tip there), so the run carries the measured map, not a second one.
 */
class GlassCapJunctionTest {

    @Test
    fun aCapsuleRestsOnTheMeasuredMapAlongItsRuns() {
        val pad = 40
        val r = 93f // a 31 dp cap at 3x
        val w = 660
        val h = (2 * r).toInt()
        val lh = h + 2 * pad
        val band = GlassMaterial.BEVEL_RATIO * r
        // The ramp spans the padded layer, so a level difference reads directly in rows.
        val levelPerRow = 255.0 / (lh - 1)
        val px = GlassRender.render(
            width = w, height = h, pad = pad, refractBand = band,
            radii = FloatArray(4) { r }, profile = 1f, formation = 0f, restMap = 1f,
            edgeLight = 0f, counterLight = 0f, bevel = 0f,
            backdrop = { _, y -> (y * 255 / (lh - 1)).let { (it shl 16) or (it shl 8) or it } },
        )
        val lw = w + 2 * pad
        fun shownRow(x: Int, y: Int) = GlassRender.luma(px[(pad + y) * lw + pad + x]) / levelPerRow
        // The same map at the left tip, read with a horizontal ramp of one level per column (the tip's inward normal is +x;
        // everything it reads lies well inside the first 255 columns).
        val pxH = GlassRender.render(
            width = w, height = h, pad = pad, refractBand = band,
            radii = FloatArray(4) { r }, profile = 1f, formation = 0f, restMap = 1f,
            edgeLight = 0f, counterLight = 0f, bevel = 0f,
            backdrop = { x, _ -> minOf(x, 255).let { (it shl 16) or (it shl 8) or it } },
        )
        // How far inward the pixel at depth d looks: mid-run along +y from the top, at the tip along +x.
        fun runOffset(d: Int) = shownRow(w / 2, d) - (pad + d)
        fun tipOffset(d: Int) = GlassRender.luma(pxH[(pad + h / 2) * lw + pad + d]) - (pad + d)
        fun depth(x: Int, y: Int): Float {
            val dx = maxOf(abs(x + .5f - w / 2f) - (w / 2f - r), 0f)
            val dy = abs(y + .5f - h / 2f)
            return r - sqrt(dx * dx + dy * dy)
        }
        val step = 6
        val ri = r.toInt()
        var worst = 0.0
        var at = ""
        // The run side of both cap starts, over the band the resting lens acts in.
        for ((from, until) in listOf(ri - 2 to 3 * ri, w - 3 * ri to w - ri + 2)) {
            for (y in 0 until h) for (x in from until until - step) {
                val d0 = depth(x, y)
                val d1 = depth(x + step, y)
                if (minOf(d0, d1) < 4f || maxOf(d0, d1) > band) continue
                val slope = abs(shownRow(x + step, y) - shownRow(x, y)) / step
                if (slope > worst) { worst = slope; at = "x=$x y=$y" }
            }
        }
        // Mid-run against the cap's tip (the outline's left-most point), depth by depth over the band.
        var apart = 0.0
        for (d in 4 until band.toInt()) apart = maxOf(apart, abs(runOffset(d) - tipOffset(d)))
        println("cap junction: worst ${"%.3f".format(worst)} rows/px at $at; run vs tip ${"%.2f".format(apart)} rows")
        assertTrue(worst < 1.0, "the shown row moves ${"%.2f".format(worst)} rows per pixel along the run at $at: a hook")
        assertTrue(apart <= 2.0, "mid-run shows ${"%.2f".format(apart)} rows away from what the cap's tip shows at the same depth")
    }

    /** A card (corners short of the half-height) keeps the corner-only lens: what the dock and a resting sheet show. */
    @Test
    fun aCardKeepsItsStraightRunsInPlace() {
        val pad = 40
        val r = 30f
        val w = 660
        val h = 186
        val lh = h + 2 * pad
        val band = GlassMaterial.BEVEL_RATIO * r
        val levelPerRow = 255.0 / (lh - 1)
        val px = GlassRender.render(
            width = w, height = h, pad = pad, refractBand = band,
            radii = FloatArray(4) { r }, profile = 1f, formation = 0f, restMap = 1f,
            edgeLight = 0f, counterLight = 0f, bevel = 0f,
            backdrop = { _, y -> (y * 255 / (lh - 1)).let { (it shl 16) or (it shl 8) or it } },
        )
        val lw = w + 2 * pad
        var worst = 0.0
        for (d in 2 until band.toInt()) {
            val shown = GlassRender.luma(px[(pad + d) * lw + pad + w / 2]) / levelPerRow
            worst = maxOf(worst, abs(shown - (pad + d)))
        }
        println("card run: worst inward offset ${"%.2f".format(worst)} rows (band ${"%.1f".format(band)})")
        // The straight-run term moves a row by at most 0.06 of the band (1.1 px here); the corner map would move it ~0.7.
        assertTrue(worst <= 2.0, "a card's straight run moved content ${"%.2f".format(worst)} rows: it took the capsule rule")
    }
}
