package com.wexpa.liquidglass

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Regular glass against the measured model of #199 (`tools/glass_model.py`), with no private data: the
 * toolbar style at Tint 50 drawn through the production modifier over synthetic backdrops, read in the
 * interior zones the lab stills were scored in, and compared with the model over the same backdrop.
 * Gates from #200. Greys only (colour is #201); the rim band (#202) is outside the zones.
 */
class GlassLabModelTest {
    private class Case(val name: String, val values: (Int, Int) -> Int)

    private val failures = mutableListOf<String>()

    private fun check(ok: Boolean, message: String) {
        println((if (ok) "LAB ok   " else "LAB FAIL ") + message)
        if (!ok) failures += message
    }

    private fun GlassLabScene.run(dark: Boolean, case: Case): Pair<DoubleArray, DoubleArray> {
        val greys = DoubleArray(width * height) { i -> case.values(i % width, i / width).toDouble() }
        val out = render(IntArray(width * height) { i -> GlassLabScene.grey(greys[i].toInt()) })
        val ours = DoubleArray(out.size) { i -> ((out[i] ushr 8) and 0xFF).toDouble() }
        return ours to GlassLabModel.predict(greys, width, height, dark)
    }

    private fun GlassLabScene.zone(): List<Int> =
        zoneRows.flatMap { y -> zoneColumns.flatMap { xs -> xs.map { x -> y * width + x } } }

    private fun stripes(period: Int, lo: Int, hi: Int, vertical: Boolean) = Case(
        "${if (vertical) "v" else "h"}stripes-$period $lo-$hi",
    ) { x, y -> if (((if (vertical) x else y) / (period / 2)) % 2 == 0) lo else hi }

    @Test
    fun regularGlassFollowsTheMeasuredModel() {
        for (dark in listOf(true, false)) {
            val app = if (dark) "dark" else "light"
            GlassLabScene(GlassStyle.toolbar(dark = dark)).use { s ->
                val zone = s.zone()
                // Flat greys: the measured tone table within 2 levels.
                for (v in GlassLabModel.greyIn) {
                    val (ours, _) = s.run(dark, Case("grey-$v") { _, _ -> v })
                    val got = zone.map { ours[it] }.average()
                    val want = GlassLabModel.tone(v.toDouble(), dark)
                    check(abs(got - want) <= 2.0, "$app grey $v: ours %.1f, measured %.1f".format(got, want))
                }
                // Stripes: both plateaus within 3 levels of the model; horizontal and vertical within 1.5.
                for ((lo, hi) in listOf(0 to 255, 64 to 192, 0 to 64)) for (period in listOf(4, 6, 8, 12, 16, 64)) {
                    val plateaus = listOf(true, false).map { vertical ->
                        val case = stripes(period, lo, hi, vertical)
                        val (ours, model) = s.run(dark, case)
                        // A plateau is the stripe centres: half a period wide, less a pixel either side.
                        val half = period / 2
                        fun centre(i: Int): Boolean {
                            val u = if (vertical) i % s.width else i / s.width
                            val inStripe = u % half
                            return half <= 2 || (inStripe >= 1 && inStripe < half - 1)
                        }
                        val low = zone.filter { centre(it) && case.values(it % s.width, it / s.width) == lo }
                        val high = zone.filter { centre(it) && case.values(it % s.width, it / s.width) == hi }
                        val r = doubleArrayOf(
                            low.map { ours[it] }.average(), high.map { ours[it] }.average(),
                            low.map { model[it] }.average(), high.map { model[it] }.average(),
                        )
                        check(
                            abs(r[0] - r[2]) <= 3.0 && abs(r[1] - r[3]) <= 3.0,
                            "$app ${case.name}: low %.1f (model %.1f), high %.1f (model %.1f)".format(r[0], r[2], r[1], r[3]),
                        )
                        r
                    }
                    check(
                        abs(plateaus[0][0] - plateaus[1][0]) <= 1.5 && abs(plateaus[0][1] - plateaus[1][1]) <= 1.5,
                        "$app stripes $period $lo-$hi: vertical minus horizontal %.1f / %.1f".format(
                            plateaus[0][0] - plateaus[1][0], plateaus[0][1] - plateaus[1][1],
                        ),
                    )
                }
                // Step edges through the middle of the pill: the limits 2 pt (6 px) either side within 4 levels of
                // the model, and, across a vertical edge, the zones far from it at their side's flat value within 2.
                for ((a, b) in listOf(0 to 255, 64 to 192)) for (vertical in listOf(true, false)) {
                    val mid = if (vertical) s.pillX + s.pillW / 2 else s.pillY + s.pillH / 2
                    val case = Case("${if (vertical) "v" else "h"}edge $a|$b") { x, y -> if ((if (vertical) x else y) < mid) a else b }
                    val (ours, model) = s.run(dark, case)
                    val probes = if (vertical) {
                        listOf(mid - 6, mid + 5).map { x -> s.zoneRows.map { y -> y * s.width + x } }
                    } else {
                        listOf(mid - 6, mid + 5).map { y -> s.zoneColumns.flatMap { xs -> xs.map { x -> y * s.width + x } } }
                    }
                    for ((side, idx) in probes.withIndex()) {
                        val got = idx.map { ours[it] }.average()
                        val want = idx.map { model[it] }.average()
                        check(
                            abs(got - want) <= 4.0,
                            "$app ${case.name} limit ${if (side == 0) "before" else "after"}: ours %.1f, model %.1f".format(got, want),
                        )
                    }
                    if (vertical) for ((side, xs) in s.zoneColumns.withIndex()) {
                        val got = s.zoneRows.flatMap { y -> xs.map { x -> ours[y * s.width + x] } }.average()
                        val want = GlassLabModel.tone((if (side == 0) a else b).toDouble(), dark)
                        check(
                            abs(got - want) <= 2.0,
                            "$app ${case.name} far ${if (side == 0) "left" else "right"}: ours %.1f, flat %.1f".format(got, want),
                        )
                    }
                }
                // Bars and checkers: within 3 levels rms of the model.
                val centreX = s.pillX + s.pillW / 2
                val area = listOf(
                    Case("white bar 2") { x, _ -> if (x - centreX in 0..1) 255 else 0 },
                    Case("black bar 2") { x, _ -> if (x - centreX in 0..1) 0 else 255 },
                    Case("white bar 24") { x, _ -> if (x - centreX in -12..11) 255 else 0 },
                    Case("black bar 24") { x, _ -> if (x - centreX in -12..11) 0 else 255 },
                    Case("checker 10") { x, y -> if ((x / 10 + y / 10) % 2 == 0) 0 else 255 },
                    Case("checker 30") { x, y -> if ((x / 30 + y / 30) % 2 == 0) 0 else 255 },
                )
                for (case in area) {
                    val (ours, model) = s.run(dark, case)
                    // A bar at the pill's centre crosses the zones only through its halo: score the whole interior band.
                    val idx = if (case.name.startsWith("checker")) zone
                        else s.zoneRows.flatMap { y -> (s.pillX + 104 until s.pillX + 537).map { x -> y * s.width + x } }
                    val rms = sqrt(idx.sumOf { (ours[it] - model[it]) * (ours[it] - model[it]) } / idx.size)
                    check(rms <= 3.0, "$app ${case.name}: rms %.2f".format(rms))
                }
                // A text stem on a plain page: one soft copy, not stepped ghosts (#190).
                val turns = s.stemTurningPoints()
                check(turns <= 7, "$app text stem: $turns turning points")
            }
        }
        assertTrue(failures.isEmpty(), "${failures.size} lab gates failed:\n" + failures.joinToString("\n"))
    }

    /**
     * A 30 px black stem (the stem of 110 pt bold text at 3x) on the #190 probe's off-white page at the pill's centre:
     * turning points of the row-averaged profile across the interior. A single soft stem with the detail term's own
     * halo has at most 7; main's five-tap along-axis average gave 15 (fix/190-axis-comb).
     */
    private fun GlassLabScene.stemTurningPoints(): Int {
        val centreX = pillX + pillW / 2
        val out = render(IntArray(width * height) { i ->
            if ((i % width) - centreX in -15..14) 0xFF000000.toInt() else 0xFFF3F0E8.toInt()
        })
        val profile = DoubleArray(pillW - 200) { i ->
            val x = pillX + 100 + i
            zoneRows.sumOf { y -> GlassRender.luma(out[y * width + x]) } / zoneRows.count()
        }
        return turningPoints(profile)
    }

    @Test
    fun noGhostCopiesAtOtherTints() {
        // Between the measured Tint 50 and the earlier fit at 0 and 100 the material is blended (authored); the
        // detail copy must stay one soft copy throughout.
        for (dark in listOf(true, false)) for (tint in listOf(25f, 75f)) {
            GlassLabScene(GlassStyle.toolbar(dark = dark, tintAmount = tint)).use { s ->
                val turns = s.stemTurningPoints()
                check(turns <= 7, "${if (dark) "dark" else "light"} tint $tint text stem: $turns turning points")
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("; "))
    }

    /** Turning points of at least [amplitude] levels (the probe of `GlassAxisDetailRenderTest`, abandoned #190 branch). */
    private fun turningPoints(p: DoubleArray, amplitude: Double = 4.0): Int {
        var count = 0
        var last = p[0]
        var direction = 0
        for (v in p) {
            if (direction >= 0 && v < last - amplitude) {
                if (direction > 0) count++
                direction = -1; last = v
            } else if (direction <= 0 && v > last + amplitude) {
                if (direction < 0) count++
                direction = 1; last = v
            } else if ((direction > 0 && v > last) || (direction < 0 && v < last)) {
                last = v
            }
        }
        return count
    }
}
