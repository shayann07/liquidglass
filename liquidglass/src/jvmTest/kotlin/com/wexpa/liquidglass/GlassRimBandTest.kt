package com.wexpa.liquidglass

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

    /** Displayed depths (the boundary below pixel row d is d + 1) of every local maximum of the row-to-row jump above [level]; the outline row's own boundary is left out. */
    private fun jumps(r: DoubleArray, level: Double = 8.0): List<Double> {
        val step = DoubleArray(r.size - 1) { abs(r[it + 1] - r[it]) }
        return (1 until step.size - 1).filter { step[it] > level && step[it] >= step[it - 1] && step[it] > step[it + 1] }.map { it + 1.0 }
    }

    @Test
    fun theRimShowsTheEdgeWhereTheLabPillMeasuredIt() {
        // Where each source depth is displayed, from the measured table above (linear between its rows).
        val expected = mapOf(43 to listOf(4.0, 43.0), 33 to listOf(9.2, 32.7), 24 to emptyList(), 13 to emptyList())
        for (dark in listOf(true, false)) {
            val app = if (dark) "dark" else "light"
            val style = GlassStyle.inApp(dark = dark)
            GlassLabScene(style).use { scene ->
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
    fun theRimBandSamplesFromInsideTheShape() {
        // The deepest source of the rim, 1.41 W at 0.5 px, is 61 px inside a 72 px radius: never outward, never past the far side.
        for (i in GlassRestMapTable.U.indices) {
            val u = GlassRestMapTable.U[i]
            assertTrue(GlassRestMapTable.source(u) * 43.2f < 72f, "knot $i samples from inside the pill")
            assertTrue(GlassRestMapTable.source(u) >= u, "knot $i never samples outward")
        }
    }
}
