package com.wexpa.liquidglass

import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * #211: the parts of the standalone material the container shader once lacked, each against a lone piece of the same
 * style at the same place: the legibility term, the rim band's colour-fringe branch, the absorbing tint, the unrotated
 * soft scatter and the container's [Glass] light. Every part is checked over flat grey 128, flat white and a text page
 * (a 30 px black stem on the off-white page of the #190 probe), dark and light; the interior must agree within 2 levels
 * and, for the light, the rim ring within 3.5 levels rms.
 */
class GlassContainerPartsTest {
    private val failures = mutableListOf<String>()

    private fun check(ok: Boolean, message: String) {
        println((if (ok) "PARTS ok   " else "PARTS FAIL ") + message)
        if (!ok) failures += message
    }

    private fun GlassLabScene.interiorMean(argb: Int): Double {
        val out = render(IntArray(width * height) { argb })
        return zoneRows.flatMap { y -> zoneColumns.flatMap { xs -> xs.map { x -> GlassRender.luma(out[y * width + x]) } } }.average()
    }

    /** Luma rms over a ring 1 to 12 px inside the outline, on a page that is white left of the pill's centre and black right of it. */
    private fun GlassLabScene.ringFrame(): DoubleArray {
        val out = render(IntArray(width * height) { i -> if ((i % width) < pillX + pillW / 2) 0xFFF3F0E8.toInt() else GlassLabScene.grey(20) })
        val r = pillH / 2f
        val ring = ArrayList<Double>()
        for (y in pillY until pillY + pillH) for (x in pillX until pillX + pillW) {
            val cx = x.coerceIn(pillX + r.toInt(), pillX + pillW - r.toInt()).toFloat()
            val d = r - sqrt((x - cx) * (x - cx) + (y - (pillY + r)) * (y - (pillY + r)))
            if (d in 1f..12f) ring += GlassRender.luma(out[y * width + x])
        }
        return ring.toDoubleArray()
    }

    private fun rms(a: DoubleArray, b: DoubleArray) = sqrt(a.indices.sumOf { (a[it] - b[it]) * (a[it] - b[it]) } / a.size)

    private fun parity(part: String, style: GlassStyle, light: GlassLight = GlassLight.Default, ring: Boolean = false): Pair<Double, Double> {
        var worstInterior = 0.0
        var worstRing = 0.0
        GlassLabScene(style, light = light).use { alone ->
            GlassLabScene(style, container = true, light = light).use { merged ->
                for ((name, page) in listOf("grey 128" to GlassLabScene.grey(128), "white" to GlassLabScene.grey(255))) {
                    val d = abs(alone.interiorMean(page) - merged.interiorMean(page))
                    worstInterior = maxOf(worstInterior, d)
                    check(d <= 2.0, "$part over $name: interior differs by %.2f levels".format(d))
                }
                val a = alone.stemProfile(); val m = merged.stemProfile()
                val d = a.indices.maxOf { abs(a[it] - m[it]) }
                worstInterior = maxOf(worstInterior, d)
                check(d <= 2.0, "$part over the text page: stem profile differs by up to %.2f levels".format(d))
                if (ring) {
                    val e = rms(alone.ringFrame(), merged.ringFrame())
                    worstRing = e
                    check(e <= 3.5, "$part: the rim ring differs by %.2f levels rms".format(e))
                }
            }
        }
        return worstInterior to worstRing
    }

    @Test
    fun legibilityRaisesTheTintUnderTextInAContainerToo() {
        for (dark in listOf(true, false)) {
            val app = if (dark) "dark" else "light"
            val base = GlassStyle.inApp(dark = dark)
            val on = base.copy(legibility = 0.9f)
            parity("$app legibility 0.9", on)
            // The term has to matter, or the parity above proves nothing.
            val offProfile = GlassLabScene(base.copy(legibility = 0f), container = true).use { it.stemProfile() }
            val onProfile = GlassLabScene(on, container = true).use { it.stemProfile() }
            val effect = onProfile.indices.maxOf { abs(onProfile[it] - offProfile[it]) }
            check(effect >= 3.0, "$app: legibility 0.9 changes the container's stem profile by %.1f levels".format(effect))
        }
        assertTrue(failures.isEmpty(), failures.joinToString("; "))
    }

    @Test
    fun theAbsorbingTintAndTheEarlierFitMatchALonePiece() {
        for (dark in listOf(true, false)) {
            val app = if (dark) "dark" else "light"
            parity("$app absorbing tint", GlassStyle.inApp(dark = dark).copy(tintAbsorption = 0.8f))
            // The earlier fit at Tint 0 carries legibility 0.6: the case the text-stem check used to exempt.
            parity("$app Tint 0", GlassStyle.inApp(dark = dark, tintAmount = 0f))
        }
        assertTrue(failures.isEmpty(), failures.joinToString("; "))
    }

    @Test
    fun theSoftScatterIsRotatedOrNotAsTheStyleSays() {
        // A reach above the four-tap lab reach takes the scatter kernel, whose rotation softDetail switches.
        for (dark in listOf(true, false)) for (soft in listOf(0f, 1f)) {
            val app = "${if (dark) "dark" else "light"} softDetail ${soft.toInt()}"
            parity(app, GlassStyle.inApp(dark = dark).copy(blurRadius = 4.dp, softDetail = soft))
        }
        assertTrue(failures.isEmpty(), failures.joinToString("; "))
    }

    @Test
    fun aContainerTakesTheGlassLight() {
        val light = GlassLight(0.85f, 0.15f)
        for (dark in listOf(true, false)) {
            val app = if (dark) "dark" else "light"
            val style = GlassStyle.inApp(dark = dark)
            parity("$app light from the right", style, light, ring = true)
            val moved = GlassLabScene(style, container = true, light = light).use { it.ringFrame() }
            val home = GlassLabScene(style, container = true).use { it.ringFrame() }
            check(rms(moved, home) >= 1.0, "$app: moving the light changes the container's rim by %.2f levels rms".format(rms(moved, home)))
        }
        assertTrue(failures.isEmpty(), failures.joinToString("; "))
    }
}
