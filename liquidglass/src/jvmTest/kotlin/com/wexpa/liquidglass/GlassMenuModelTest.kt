package com.wexpa.liquidglass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `GlassStyle.menu(dark)` against the measured Safari `<select>` panel of #204 (`M-select-{dark,light}`),
 * with no private data: the style drawn through the production modifier over synthetic backdrops and read in
 * the interior zones, against the measured model `out = tone(blur(backdrop))`, a pure blur then the tone table.
 * The panel's rim, shadow and corner are not measured and not scored.
 *
 * Held out, not a gate: the Photos context menu (`COSG3073`, dark, 281-283 s) reads 10 to 18 levels lighter than
 * this fit predicts over plain chart patches. [theHeldOutContextMenuStaysAnOpenKnownDifference] pins the
 * prediction half of that table so the difference stays visible; the fit was not changed to hide it (#204).
 */
class GlassMenuModelTest {
    private val failures = mutableListOf<String>()

    private fun check(ok: Boolean, message: String) {
        println((if (ok) "MENU ok   " else "MENU FAIL ") + message)
        if (!ok) failures += message
    }

    private val greyIn = intArrayOf(0, 16, 32, 48, 64, 80, 96, 112, 128, 144, 160, 176, 192, 208, 224, 240, 255)
    /** Measured on the `<select>` panel, flat greys, G channel (204-measured.md, "Tone"). */
    private val darkTone = doubleArrayOf(32.3, 44.4, 57.0, 69.1, 81.0, 92.0, 101.0, 109.0, 116.0, 121.0, 125.0, 128.0, 129.0, 129.0, 128.0, 125.0, 122.0)
    private val lightTone = doubleArrayOf(137.0, 145.0, 154.0, 163.0, 172.0, 181.0, 189.0, 197.1, 205.0, 214.0, 222.0, 229.0, 236.0, 243.0, 248.6, 255.0, 255.0)
    /** Fitted sigma of the blur, in px at 3x. */
    private fun sigmaPx(dark: Boolean) = if (dark) 29.7 else 35.4

    private fun tone(v: Double, dark: Boolean): Double {
        val table = if (dark) darkTone else lightTone
        val x = v.coerceIn(0.0, 255.0)
        var i = 0
        while (i < greyIn.size - 2 && greyIn[i + 1] < x) i++
        val f = (x - greyIn[i]) / (greyIn[i + 1] - greyIn[i])
        return table[i] + (table[i + 1] - table[i]) * f
    }

    private fun blur(src: DoubleArray, w: Int, h: Int, sigma: Double): DoubleArray {
        val r = ceil(3.5 * sigma).toInt()
        val kernel = DoubleArray(2 * r + 1) { exp(-0.5 * ((it - r) / sigma) * ((it - r) / sigma)) }
        val sum = kernel.sum()
        for (j in kernel.indices) kernel[j] /= sum
        val tmp = DoubleArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0.0
            for (j in kernel.indices) s += kernel[j] * src[y * w + (x + j - r).coerceIn(0, w - 1)]
            tmp[y * w + x] = s
        }
        val out = DoubleArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0.0
            for (j in kernel.indices) s += kernel[j] * tmp[(y + j - r).coerceIn(0, h - 1) * w + x]
            out[y * w + x] = s
        }
        return out
    }

    private fun GlassLabScene.zone(): List<Int> =
        zoneRows.flatMap { y -> zoneColumns.flatMap { xs -> xs.map { x -> y * width + x } } }

    private fun GlassLabScene.ours(values: (Int, Int) -> Int): DoubleArray {
        val out = render(IntArray(width * height) { i -> GlassLabScene.grey(values(i % width, i / width)) })
        return DoubleArray(out.size) { i -> ((out[i] ushr 8) and 0xFF).toDouble() }
    }

    @Test
    fun theMenuToneFollowsTheMeasuredPanelOverFlatGreys() {
        for (dark in listOf(true, false)) {
            val app = if (dark) "dark" else "light"
            GlassLabScene(GlassStyle.menu(dark)).use { s ->
                val zone = s.zone()
                for ((i, v) in greyIn.withIndex()) {
                    val ours = s.ours { _, _ -> v }
                    val got = zone.map { ours[it] }.average()
                    val want = (if (dark) darkTone else lightTone)[i]
                    check(abs(got - want) <= 3.0, "$app grey $v: ours %.1f, measured %.1f".format(got, want))
                }
            }
        }
        assertTrue(failures.isEmpty(), "${failures.size} menu tone gates failed:\n" + failures.joinToString("\n"))
    }

    @Test
    fun stripesAndEdgesFollowABlurThenTone() {
        // Stripes of 16 and 32 px at two level pairs, vertical and horizontal: the interior mean within 4 levels of
        // tone(blur) over the same backdrop (the issue's gate; the source is video). Pooled rms is reported.
        for (dark in listOf(true, false)) {
            val app = if (dark) "dark" else "light"
            GlassLabScene(GlassStyle.menu(dark)).use { s ->
                val zone = s.zone()
                var sum = 0.0
                var n = 0
                for (period in listOf(16, 32)) for ((lo, hi) in listOf(0 to 255, 64 to 192)) for (vertical in listOf(true, false)) {
                    val values = { x: Int, y: Int -> if ((((if (vertical) x else y)) / (period / 2)) % 2 == 0) lo else hi }
                    val ours = s.ours(values)
                    val grey = DoubleArray(s.width * s.height) { i -> values(i % s.width, i / s.width).toDouble() }
                    val b = blur(grey, s.width, s.height, sigmaPx(dark))
                    val model = DoubleArray(b.size) { tone(b[it], dark) }
                    val rms = sqrt(zone.sumOf { (ours[it] - model[it]) * (ours[it] - model[it]) } / zone.size)
                    sum += rms * rms; n++
                    check(rms <= 4.0, "$app ${if (vertical) "v" else "h"}stripes $period $lo-$hi: %.2f levels rms".format(rms))
                }
                println("MENU pooled $app: %.2f levels rms over $n stripe cases".format(sqrt(sum / n)))
            }
        }
        assertTrue(failures.isEmpty(), "${failures.size} menu stripe gates failed:\n" + failures.joinToString("\n"))
    }

    @Test
    fun theMenuIsMoreFrostedThanRegularByHavingNoDetailAndAMoreBowedTone() {
        for (dark in listOf(true, false)) {
            val menu = GlassStyle.menu(dark)
            val regular = GlassStyle.inApp(dark)
            assertEquals(0f, menu.fineShare, "no detail share")
            assertEquals(0f, menu.detailLift, "no one-sided detail term")
            assertTrue(menu.fineShare < regular.fineShare, "the menu keeps less fine detail than Regular")
            assertTrue(menu.toneCurve > 0f && (!dark || menu.toneCurve > regular.toneCurve * 1.5f), "dark bow is 1.54 times Regular's")
            // The wide kernel is smaller than Regular's 15.5 dp: "frosted" here is the missing detail term and the compressed tone.
            assertEquals(if (dark) 9.9f else 11.8f, menu.wideKernel.value, 1e-4f)
            assertTrue(menu.wideKernel < regular.wideKernel)
        }
    }

    @Test
    fun menuWithoutAnArgumentIsTheOctoberFitUnchanged() {
        val earlier = GlassStyle(
            contactShadow = 0f, backdropSigma = 9.5.dp, wideKernel = 0.dp, fineShare = 1f,
            tint = Color.Black.copy(alpha = 0.76f), tintLift = 48f / 255f, bevel = 1.dp, specular = 0.15f,
            edgeLight = 1.5f, counterLight = 1f, invertsWithBackdrop = false,
        )
        assertEquals(earlier, GlassStyle.menu(), "menu() changed: existing callers must see the October fit")
    }

    @Test
    fun theHeldOutContextMenuStaysAnOpenKnownDifference() {
        // 204-measured.md, held-out check: blurred backdrop B over plain chart patches, the dark fit's prediction, and what
        // the Photos menu showed. The fit predicts 129 / 129 / 123 / 129 / 125 / 125 / 117; iOS showed 139 / 140 / 138 /
        // 147 / 157 / 143 / 143: 10 to 32 levels lighter, 10 to 18 against the plateau (gate 5 rms, none over 10: not met). Not re-fitted.
        val b = doubleArrayOf(193.0, 212.0, 151.0, 205.0, 159.0, 161.0, 130.0)
        val predicted = doubleArrayOf(129.0, 129.0, 123.0, 129.0, 125.0, 125.0, 117.0)
        val observed = doubleArrayOf(139.0, 140.0, 138.0, 147.0, 157.0, 143.0, 143.0)
        for (i in b.indices) {
            assertEquals(predicted[i], tone(b[i], dark = true), 4.0, "prediction at B ${b[i]}")
            assertTrue(observed[i] - predicted[i] > 10.0 - 4.0, "the recorded difference at B ${b[i]}")
        }
    }
}
