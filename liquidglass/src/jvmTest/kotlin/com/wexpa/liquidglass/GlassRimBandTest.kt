package com.wexpa.liquidglass

import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * #202, gate M of the plan, as a public render test: the outer ~6 px of regular glass show content from deeper inside than
 * the rest map had it. A horizontal edge (grey 128 above, black below) is placed at four source depths on the lab pill
 * (632 x 143 px at 3x, capsule); where the step shows is read off the row-to-row luma jump, and compared with where the lab
 * pill measured it (moving-edge sweeps, 18 November; the source depth s at each displayed depth d, run mean: 61.0, 53.1,
 * 48.2, 44.3, 41.8, 39.4 px at d = 0.5 ... 5.5 and 38.4 at 6, 33.8 at 8.5, 28.5 at 14.5, 27.6 at 19.5, 32.0 at 31.5, 34.5 at 34.5).
 * The outline pixel row (d under 1 px, the anti-aliased coverage row carrying the bead) is left out of the reading.
 * Depths 13 and 24 are never displayed anywhere in the pill (the fold's minimum is 27.6 px); 33 shows twice, 43 at
 * its first branch and at its true row.
 */
class GlassRimBandTest {
    private val failures = mutableListOf<String>()

    private fun check(ok: Boolean, message: String) {
        println((if (ok) "RIM ok   " else "RIM FAIL ") + message)
        if (!ok) failures += message
    }

    /**
     * How much darker each displayed row is than over an all-grey page, 0 at the outline, over the pill's centre column
     * span: the lighting (bead, contour, tone) is the same on both pages and cancels (mid-grey, not white, so the light glass's additive bead does not clip on one page only), what is left is the black the row shows.
     */
    private fun rows(scene: GlassLabScene, edgeDepth: Int, bottom: Boolean): DoubleArray {
        val edgeRow = if (bottom) scene.pillY + scene.pillH - edgeDepth else scene.pillY + edgeDepth
        // Grey on the outline's side of the edge, black beyond it: the same polarity at the top and at the bottom.
        val page = IntArray(scene.width * scene.height) { i ->
            val y = i / scene.width
            val outside = if (bottom) y >= edgeRow else y < edgeRow
            if (outside) GlassLabScene.grey(128) else GlassLabScene.grey(0)
        }
        val edge = scene.render(page)
        val white = scene.render(IntArray(scene.width * scene.height) { GlassLabScene.grey(128) })
        val xs = scene.pillX + 250 until scene.pillX + 380
        return DoubleArray(60) { d ->
            val y = if (bottom) scene.pillY + scene.pillH - 1 - d else scene.pillY + d
            xs.sumOf { x -> GlassRender.luma(white[y * scene.width + x]) - GlassRender.luma(edge[y * scene.width + x]) } / xs.count()
        }
    }

    /**
     * Displayed depths (the boundary below pixel row d is d + 1) of every local maximum of the row-to-row jump above [level],
     * from displayed depth 4 on. Both the level and the first depth are read off the lab's own profiles (the moving-edge
     * sweeps, run through this same detector with the same grey-128-against-black scaling, 19 November):
     * - [level] 4.0 (was 8.0). The lab's single-row step at the edges this test pins is 13.9 to 28.2 levels in the dark
     *   glass but only 5.2 to 9.0 in the light glass (33 px edge: 5.2 / 7.0 at the top, 5.6 / 6.9 at the bottom; 43 px edge:
     *   6.5 / 6.8 at the top, 8.2 / 9.0 at the bottom), so 8.0 was above the lab's own step. 4.0 is 0.75 of the weakest.
     * - From depth 4 on (was 2): the lab's own rim ramp (the bead's falloff over the outer 3 px) makes a jump at depth 2 in the
     *   dark glass and at depth 3 in the light glass under every edge page, 11 to 15 levels, on its own flat-grey-minus-edge reading,
     *   and the light glass's bead clips on the grey page there. It is lighting, not content shown from deeper inside.
     * The outline row's own boundary stays left out.
     */
    private fun jumps(r: DoubleArray, level: Double = 4.0): List<Double> {
        val step = DoubleArray(r.size - 1) { abs(r[it + 1] - r[it]) }
        return (3 until step.size - 1).filter { step[it] > level && step[it] >= step[it - 1] && step[it] > step[it + 1] }.map { it + 1.0 }
    }

    @Test
    fun theRimShowsTheEdgeWhereTheLabPillMeasuredIt() {
        // Where each source depth is displayed, from the measured table above (linear between its rows).
        val expected = mapOf(43 to listOf(4.0, 43.0), 33 to listOf(9.2, 32.7), 24 to emptyList(), 13 to emptyList())
        // Lone and merged: the container takes the same rim-band branch (#211). The old blend there mixed the crisp copy
        // back in whole, which displays the 13 and 24 px source edges at their true rows inside the band and fails the
        // negative cases below.
        for (container in listOf(false, true)) for (dark in listOf(true, false)) {
            val app = (if (dark) "dark" else "light") + if (container) " merged" else ""
            val style = GlassStyle.inApp(dark = dark)
            GlassLabScene(style, container = container).use { scene ->
                for (bottom in listOf(false, true)) for ((depth, at) in expected) {
                    val side = if (bottom) "bottom" else "top"
                    val j = jumps(rows(scene, depth, bottom))
                    val what = "$app $side edge at source depth $depth: jumps at ${j.map { "%.1f".format(it) }}"
                    if (at.isEmpty()) {
                        check(j.none { it < 40.0 }, "$what (never displayed inside the band: none expected)")
                    } else {
                        // One to one: each expected depth has its own jump within 2 px, and there is no other jump.
                        check(j.size == at.size, "$what, expected exactly ${at.size}")
                        for (d in at) check(j.count { abs(it - d) <= 2.0 } == 1, "$what, expected one near %.1f".format(d))
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun aMergedRimBandShowsTheEdgeRowByRowAsALonePieceDoes() {
        // The container's rim-band branch (#211) against a lone piece, row by row over the band (d 0 to 39), for each
        // source edge: the same profile, not only the same jump positions. Gate 1.5 levels: the current branch reads 1.0 in
        // every case; with the container switched back to the old blend (crisp copy mixed in whole) the scatter case reads
        // up to 5 (dark) and 3 (light) levels at the 33 px edge and fails, while the lab case reads 1.0 either way.
        // Two materials: the lab material (its soft copy is the four-tap detail at the lab reach, so near the rim the soft
        // and sharp copies nearly agree and the branch barely changes a pixel) and a scatter reach (blurRadius 4 dp), where
        // the soft copy is a real blur and the old blend would mix the crisp copy back in at the rim.
        for (scatter in listOf(false, true)) for (dark in listOf(true, false)) {
            val app = (if (dark) "dark" else "light") + if (scatter) " scatter" else " lab"
            val style = GlassStyle.inApp(dark = dark).let { if (scatter) it.copy(blurRadius = 4.dp) else it }
            GlassLabScene(style).use { alone ->
                GlassLabScene(style, container = true).use { merged ->
                    for (bottom in listOf(false, true)) for (depth in listOf(13, 24, 33, 43)) {
                        val a = rows(alone, depth, bottom); val m = rows(merged, depth, bottom)
                        val worst = (0 until 40).maxOf { abs(a[it] - m[it]) }
                        val side = if (bottom) "bottom" else "top"
                        println("RIM merged-vs-lone $app $side depth $depth: worst %.2f levels".format(worst))
                        check(worst <= 1.5, "$app $side edge at source depth $depth: merged differs from a lone piece by up to %.2f levels over the band".format(worst))
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun theRimBandSamplesFromInsideTheShape() {
        // The deepest source of the rim, 1.41 W at 0.5 px, is 61 px inside a 72 px radius: never outward, never past the far side.
        for (i in GlassRestMapTable.U.indices) {
            val u = GlassRestMapTable.U[i]
            assertTrue(GlassRestMapTable.source(u) * 43.2f < 72f, "knot $i samples from inside the pill")
            assertTrue(GlassRestMapTable.source(u) >= u, "knot $i never samples outward")
        }
    }
}
