package com.wexpa.liquidglass

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Where a capsule's straight run meets its round end, the resting lens hands over from the run's
 * shallow offset to the measured resting-corner map ([GlassRestMap]). The two maps disagree by about
 * 0.7 of the band near the rim, so a short handover made text lines hook and break right where the
 * cap begins. The handover along the run is authored, not measured; this asserts it is gradual.
 *
 * The backdrop is a vertical ramp, so a pixel's level says which row it shows. Along a straight run
 * the rows stay put; across the handover the shown row may move, but only gently: less than one row
 * per pixel along the run.
 */
class GlassCapJunctionTest {

    @Test
    fun theRunHandsOverToTheCapGently() {
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
        println("cap junction: worst ${"%.3f".format(worst)} rows/px at $at")
        assertTrue(worst < 1.0, "the shown row moves ${"%.2f".format(worst)} rows per pixel along the run at $at: a hook")
    }
}
