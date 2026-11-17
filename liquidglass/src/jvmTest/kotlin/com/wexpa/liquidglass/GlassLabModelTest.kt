package com.wexpa.liquidglass

import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Regular glass against the measured model of #199 (`tools/glass_model.py`), with no private data: the
 * toolbar style at Tint 50 drawn through the production modifier over synthetic backdrops, read in the
 * interior zones the lab stills were scored in, and compared with the model over the same backdrop.
 * Gates from #200, and the colour transfer of #201 over flat colour; the rim band (#202) is outside the zones.
 */
class GlassLabModelTest {
    private class Case(val name: String, val values: (Int, Int) -> Int)

    private val failures = mutableListOf<String>()

    private fun check(ok: Boolean, message: String) {
        println((if (ok) "LAB ok   " else "LAB FAIL ") + message)
        if (!ok) failures += message
    }

    /** Our frame over [case], green channel. */
    private fun GlassLabScene.ours(case: Case): DoubleArray {
        val out = render(IntArray(width * height) { i -> GlassLabScene.grey(case.values(i % width, i / width)) })
        return DoubleArray(out.size) { i -> ((out[i] ushr 8) and 0xFF).toDouble() }
    }

    /** Our frame over [case] and the model's prediction over the same backdrop. */
    private fun GlassLabScene.run(dark: Boolean, case: Case): Pair<DoubleArray, DoubleArray> {
        val greys = DoubleArray(width * height) { i -> case.values(i % width, i / width).toDouble() }
        return ours(case) to GlassLabModel.predict(greys, width, height, dark)
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
                    val ours = s.ours(Case("grey-$v") { _, _ -> v })
                    val got = zone.map { ours[it] }.average()
                    val want = GlassLabModel.tone(v.toDouble(), dark)
                    check(abs(got - want) <= 2.0, "$app grey $v: ours %.1f, measured %.1f".format(got, want))
                }
                // Stripes: both plateaus within 3 levels of the model (6 below 8 px); horizontal and vertical within 1.5.
                // Below 8 px the shape of the detail softening is not pinned: the fit's 0.83 px Gaussian keeps 43 and
                // 68 % of 4 and 6 px stripes, the shader's four taps 50 and 75 %, iOS 49 and 73 % (#199), and the two
                // score alike on the 89 lab stills (1.31 against 1.43 levels rms, dark).
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
                        val tolerance = if (period < 8) 6.0 else 3.0
                        check(
                            abs(r[0] - r[2]) <= tolerance && abs(r[1] - r[3]) <= tolerance,
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

    @Test
    fun regularGlassKeepsAndBoostsColour() {
        // The 27 points of the RGB cube at 0, 128 and 255 (#201): every channel of the interior within 2 levels of the
        // model's colour transfer, the greys among them at the grey tone. The model itself is within 0.99 / 0.83 levels
        // rms (at most 7.1 / 5.3) of the measured 125-point cube in sRGB, dark / light (tools/glass_model.py).
        val levels = listOf(0, 128, 255)
        for (dark in listOf(true, false)) {
            val app = if (dark) "dark" else "light"
            GlassLabScene(GlassStyle.toolbar(dark = dark)).use { s ->
                val zone = s.zone()
                for (r in levels) for (g in levels) for (b in levels) {
                    val argb = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                    val out = s.render(IntArray(s.width * s.height) { argb })
                    val got = DoubleArray(3) { ch -> zone.map { ((out[it] ushr (16 - 8 * ch)) and 0xFF).toDouble() }.average() }
                    val want = GlassLabModel.colour(doubleArrayOf(r.toDouble(), g.toDouble(), b.toDouble()), dark)
                    val worst = (0..2).maxOf { abs(got[it] - want[it]) }
                    check(
                        worst <= 2.0,
                        "$app colour $r,$g,$b: ours %.1f %.1f %.1f, model %.1f %.1f %.1f".format(got[0], got[1], got[2], want[0], want[1], want[2]),
                    )
                }
            }
        }
        assertTrue(failures.isEmpty(), "${failures.size} colour gates failed:\n" + failures.joinToString("\n"))
    }

    @Test
    fun colourTransferFollowsTheLabWeight() {
        // Derived from detailLift (no style field): in full at Tint 50, none at 0 and 100, and none on the tab bar's
        // earlier fit, which moves to the lab material in #203.
        for (dark in listOf(true, false)) {
            val full = GlassMaterial.chromaOf(GlassStyle.toolbar(dark = dark).detailLift)
            check(full[6] == 1f && full[0] == GlassMaterial.labChromaGain(dark)[0], "${if (dark) "dark" else "light"} Tint 50: ${full.toList()}")
            check(abs(GlassMaterial.chromaOf(GlassStyle.inApp(dark = dark, tintAmount = 75f).detailLift)[6] - 0.5f) < 1e-5f, "Tint 75 weight")
            for (tint in listOf(0f, 100f)) {
                check(GlassMaterial.chromaOf(GlassStyle.toolbar(dark = dark, tintAmount = tint).detailLift)[6] == 0f, "Tint $tint weight")
            }
            check(GlassMaterial.chromaOf(GlassStyle.toolbarEarlier(dark).detailLift).all { it == 0f }, "tab bar earlier fit")
        }
        check(GlassMaterial.chromaOf(GlassStyle().detailLift).all { it == 0f }, "default constructor")
        assertTrue(failures.isEmpty(), failures.joinToString("; "))
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

    @Test
    fun morphBetweenLabStylesKeepsTheDetailKernel() {
        // A Dp lerp between two equal reaches can land one float step above 1/3 dp; the kernel must not flip.
        val pairs = listOf(
            GlassStyle.toolbar(dark = false) to GlassStyle.toolbar(dark = true),
            GlassStyle.Regular to GlassStyle.toolbar(dark = true),
            GlassStyle.inApp(dark = false, tintAmount = 0f) to GlassStyle.inApp(dark = true, tintAmount = 100f),
        )
        for ((a, b) in pairs) for (i in 0..100) {
            val reach = lerpGlassStyle(a, b, i / 100f).blurRadius
            check(GlassMaterial.usesDetailCross(reach), "morph fraction ${i / 100f}: reach ${reach.value} dp leaves the detail kernel")
        }
        assertTrue(failures.isEmpty(), failures.joinToString("; "))
    }

    @Test
    fun morphKeepsOneDetailCopy() {
        // GlassMorph lerps the reach with the rest of the style; past the lab reach the host must leave the four-tap
        // kernel, or a morph toward a wider-reach style draws four displaced copies (#190's ghosts) halfway.
        for (dark in listOf(true, false)) for (fraction in listOf(0.25f, 0.5f, 0.75f)) {
            val start = GlassStyle.toolbar(dark = dark)
            val stop = start.copy(blurRadius = 1.5.dp, detailLift = 0f, toneCurve = 0f)
            GlassLabScene(lerpGlassStyle(start, stop, fraction)).use { s ->
                val turns = s.stemTurningPoints()
                check(turns <= 7, "${if (dark) "dark" else "light"} morph $fraction text stem: $turns turning points")
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("; "))
    }

    @Test
    fun tintStepsAreContinuous() {
        // Every adjacent pair of whole tints, 0 to 100, within 3 levels rms over the interior zones, on the page that
        // steps most: black stems on grey 128 and 0/255 stripes. The detail kernel and the legibility term it feeds
        // fade out near Tint 100 (authored); the hard switch where the share fell below 0.02 drew 12.8 (dark) and 14.4
        // (light) levels over the 6 px stripes at 97 to 98.
        val pages = listOf(
            Case("stems on grey 128") { x, _ -> if (x % 90 < 30) 0 else 128 },
            stripes(6, 0, 255, true),
        )
        for (dark in listOf(true, false)) {
            val app = if (dark) "dark" else "light"
            GlassLabScene(GlassStyle.toolbar(dark = dark, tintAmount = 0f)).use { s ->
                val zone = s.zone()
                fun frames(tint: Int): List<List<Double>> {
                    s.style = GlassStyle.toolbar(dark = dark, tintAmount = tint.toFloat())
                    return pages.map { case -> s.ours(case).let { o -> zone.map { o[it] } } }
                }
                fun rms(a: List<Double>, b: List<Double>) = sqrt(a.zip(b) { x, y -> (x - y) * (x - y) }.average())
                var previous = frames(0)
                var worst = 0.0
                var worstAt = 0
                for (tint in 1..100) {
                    val current = frames(tint)
                    val steps = pages.indices.map { rms(previous[it], current[it]) }
                    val i = steps.indices.maxBy { steps[it] }
                    check(steps[i] <= 3.0, "$app tint ${tint - 1} to $tint: %.2f levels rms (${pages[i].name})".format(steps[i]))
                    if (steps[i] > worst) { worst = steps[i]; worstAt = tint }
                    previous = current
                }
                println("LAB worst $app step: tint ${worstAt - 1} to $worstAt, %.2f levels rms".format(worst))
                // The sweep re-draws one scene; a fresh scene at the last tint must draw the same frames.
                val fresh = GlassLabScene(GlassStyle.toolbar(dark = dark, tintAmount = 100f)).use { f ->
                    pages.map { case -> f.ours(case).let { o -> zone.map { o[it] } } }
                }
                val drift = pages.indices.maxOf { rms(previous[it], fresh[it]) }
                check(drift <= 0.1, "$app sweep scene against a fresh scene at tint 100: %.2f levels rms".format(drift))
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("; "))
    }

    @Test
    fun tintEndsDoNotStep() {
        // The measured material is blended toward the earlier fit at Tint 0 and 100 (authored), but the detail
        // reach and kernel switch as soon as the blend weight leaves 0. A step there must stay no larger than a
        // tint step elsewhere would be: within 3 levels rms of the next tint, over fine and coarse content.
        val cases = listOf(
            stripes(6, 0, 255, true),
            stripes(12, 0, 255, false),
            Case("bars-2 0-255") { x, _ -> if (x % 24 < 2) 255 else 0 },
            Case("checker-10 64-192") { x, y -> if ((x / 5 + y / 5) % 2 == 0) 64 else 192 },
        )
        for (dark in listOf(true, false)) {
            val app = if (dark) "dark" else "light"
            for ((a, b) in listOf(0f to 1f, 1f to 2f, 98f to 99f, 99f to 100f)) {
                val frames = listOf(a, b).map { tint ->
                    GlassLabScene(GlassStyle.toolbar(dark = dark, tintAmount = tint)).use { s ->
                        val zone = s.zone()
                        cases.map { case -> s.ours(case).let { o -> zone.map { o[it] } } }
                    }
                }
                cases.forEachIndexed { i, case ->
                    val d = frames[0][i].zip(frames[1][i]) { x, y -> (x - y) * (x - y) }.average().let { sqrt(it) }
                    check(d <= 3.0, "$app tint ${a.toInt()} to ${b.toInt()} ${case.name}: %.2f levels rms".format(d))
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("; "))
    }
    @Test
    fun appearanceMorphMidpointColour() {
        // The colour transfer is derived from detailLift (#201), and a dark-to-light morph (GlassMorph between
        // toolbar(dark) and toolbar(light), Tint 50) passes c = 0 halfway, where the glass draws the per-channel
        // grey tone with no transfer. Each channel's chroma (the channel minus the output's Rec. 709 luma) at the
        // midpoint is reported against both ends. Gate: no channel overshoots both ends (its chroma further from zero
        // than either end's by more than 0.5). Falling below both, a dip toward the grey tone, is only reported; the
        // CHANGELOG states it (20 of these 24 channels on 17 November).
        val pages = listOf(
            "red" to intArrayOf(255, 0, 0), "green" to intArrayOf(0, 255, 0), "blue" to intArrayOf(0, 0, 255),
            "magenta" to intArrayOf(255, 0, 255), "yellow" to intArrayOf(255, 255, 0), "cyan" to intArrayOf(0, 255, 255),
            "dark magenta" to intArrayOf(128, 0, 128), "orange" to intArrayOf(255, 128, 0),
        )
        val dark = GlassStyle.toolbar(dark = true)
        val light = GlassStyle.toolbar(dark = false)
        val fractions = listOf(0f, 0.5f, 1f)
        fun chroma(c: DoubleArray): DoubleArray {
            val y = 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]
            return DoubleArray(3) { c[it] - y }
        }
        var below = 0
        GlassLabScene(dark).use { s ->
            val zone = s.zone()
            for ((name, rgb) in pages) {
                val argb = (0xFF shl 24) or (rgb[0] shl 16) or (rgb[1] shl 8) or rgb[2]
                val colours = fractions.map { f ->
                    s.style = lerpGlassStyle(dark, light, f)
                    val out = s.render(IntArray(s.width * s.height) { argb })
                    DoubleArray(3) { ch -> zone.map { ((out[it] ushr (16 - 8 * ch)) and 0xFF).toDouble() }.average() }
                }
                val (d, m, l) = colours.map { chroma(it) }
                for (ch in 0..2) {
                    val lo = minOf(d[ch], l[ch])
                    val hi = maxOf(d[ch], l[ch])
                    val outside = m[ch] < lo - 0.5 || m[ch] > hi + 0.5
                    // An overshoot: the midpoint's chroma further from zero than both ends, on the same side.
                    val overshoot = abs(m[ch]) > maxOf(abs(d[ch]), abs(l[ch])) + 0.5
                    if (outside && !overshoot) below++
                    check(
                        !overshoot,
                        "morph midpoint $name channel ${"RGB"[ch]}: chroma dark %.1f, mid %.1f, light %.1f (colours %.1f / %.1f / %.1f)%s"
                            .format(d[ch], m[ch], l[ch], colours[0][ch], colours[1][ch], colours[2][ch], if (outside) " outside both" else ""),
                    )
                }
            }
        }
        println("LAB morph midpoint: $below channel(s) below both ends, ${failures.size} overshoot(s)")
        assertTrue(failures.isEmpty(), failures.joinToString("; "))
    }
}
