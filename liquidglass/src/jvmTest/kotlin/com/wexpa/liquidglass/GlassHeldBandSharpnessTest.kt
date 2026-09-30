package com.wexpa.liquidglass

import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Sharpness **inside the compressed held connection band**, which the identity-map tests cannot
 * reach.
 *
 * `heldSourceContinuous(u) = u + w(u) (0.3 u - 0.29)` fades its outward term out across
 * `0.45 <= u <= 0.55`. Source depths `0.295 W` to `0.55 W` are squeezed into screen depths
 * `0.45 W` to `0.55 W`: a compression of about 2.6, so the failure mode is **aliasing**, not blur,
 * and an identity-map test at the same nominal band cannot see any of it.
 *
 * The reference is **the same current map**, rendered supersampled and box-filtered down, so this
 * compares the sampler against a correctly averaged version of itself - never against a different
 * map, a fitted profile or a second model. Both polarities and four fractional phases are scored.
 *
 * Two numbers, because they mean different things:
 *
 *  - **extra edge spread** over the reference, gated at one physical pixel. A stroke whose
 *    transition cannot be localised is **counted and reported with its depth**, never treated as
 *    a pass.
 *  - **level disagreement** with the correctly averaged reference inside the band, recorded but
 *    **not gated**. Around a 240-level step, a rendering whose transition is half a pixel
 *    narrower than a box-filtered one differs by most of that step at the transition pixel, so a
 *    large number here is what a sharper edge looks like and is not by itself evidence of
 *    aliasing. A single step edge cannot separate the two; saying so is the honest result.
 *
 * Measured: the band renders about **0.45 px sharper** than its own supersampled reference at
 * every polarity and phase, so the one-pixel extra-spread gate passes with margin and the sampler
 * is left alone.
 *
 * The body is large enough that the band is several physical pixels wide on screen; at the
 * selector's own size it is about two, and an edge cannot be localised inside two pixels at all.
 */
class GlassHeldBandSharpnessTest {

    private fun a(p: Int) = (p ushr 24) and 0xFF
    private fun luma(p: Int): Double {
        val r = (p ushr 16) and 0xFF
        val g = (p ushr 8) and 0xFF
        val b = p and 0xFF
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private val width = 300
    private val height = 200
    private val pad = 30
    private val bodyLength = 90f
    private val bodyRadius = 84f

    /** Supersampling factor for the reference. */
    private val ss = 5

    private fun bodyAt(s: Int) = GlassBody(
        cx = width / 2f * s,
        cy = height / 2f * s,
        length = bodyLength * s,
        radius = bodyRadius * s,
        skew = 0f,
    )

    /** `W`, the refraction band, in unscaled px. */
    private val band: Float get() = glassBodyBand(bodyAt(1))

    /** The body's left boundary on its centre row, in unscaled **unpadded** px. */
    private val leftEdge: Float get() = width / 2f - (bodyLength / 2f + bodyRadius)

    /**
     * One render of a vertical step edge in the semantic ink, through the held continuous map, at
     * scale [s]. Every length scales together, so the geometry is identical at every scale.
     *
     * [edgeAt] is the edge's position in unscaled **padded-layer** px, [bright] its polarity.
     */
    private fun renderEdge(s: Int, edgeAt: Float, bright: Boolean): Pair<IntArray, Int> {
        val w = (width + pad * 2) * s
        val body = bodyAt(s)
        val edge = edgeAt * s
        val hi = 0xFFFFFFFF.toInt()
        val lo = (0xFF shl 24) or 0x101014
        val ink: (Int, Int) -> Int = { x, _ ->
            val on = if (bright) x.toFloat() >= edge else x.toFloat() < edge
            if (on) hi else lo
        }
        val result = GlassEndpointRender.render(
            width = width * s,
            height = height * s,
            pad = pad * s,
            refractBand = glassBodyBand(body),
            refractDepth = 14f * s,
            profile = 2f,
            heldLens = 1f,
            heldInk = 1f,
            inkSplit = 0f,
            body = body,
            backdrop = { _, _ -> (0xFF shl 24) or 0x202430 },
            ink1 = ink,
        )
        return result.endpoint to w
    }

    /** Box-filter [src] (row length [w]) down by [s]: the correctly area-averaged reference. */
    private fun downsample(src: IntArray, w: Int, s: Int): Pair<IntArray, Int> {
        val h = src.size / w
        val ow = w / s
        val oh = h / s
        val out = IntArray(ow * oh)
        for (y in 0 until oh) {
            for (x in 0 until ow) {
                var r = 0
                var g = 0
                var b = 0
                for (dy in 0 until s) {
                    for (dx in 0 until s) {
                        val p = src[(y * s + dy) * w + (x * s + dx)]
                        r += (p ushr 16) and 0xFF
                        g += (p ushr 8) and 0xFF
                        b += p and 0xFF
                    }
                }
                val n = s * s
                out[y * ow + x] = (0xFF shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
            }
        }
        return out to ow
    }

    private class Edge(val position: Double, val spread: Double, val contrast: Double)

    /**
     * Locate the single steepest transition in `row[from until to]` and measure its 10-90 % width
     * in px. Returns null when there is no transition of sufficient contrast: that is an
     * unlocalizable stroke and the caller reports it.
     *
     * The crossings are interpolated across whichever adjacent sample pair brackets each level, so
     * a transition narrower than one sample gives a width below one rather than failing.
     */
    private fun locateEdge(img: IntArray, w: Int, y: Int, from: Int, to: Int): Edge? {
        val n = to - from
        if (n < 8) return null
        val row = DoubleArray(n) { luma(img[y * w + from + it]) }
        var steepest = -1
        var steepestGap = 0.0
        for (i in 0 until n - 1) {
            val gap = abs(row[i + 1] - row[i])
            if (gap > steepestGap) { steepestGap = gap; steepest = i }
        }
        if (steepest < 0) return null
        // The plateaus either side of that transition: the extreme value reached before the next
        // transition of the opposite sense, which keeps a second edge in the window out of it.
        val rising = row[steepest + 1] > row[steepest]
        var lowIndex = steepest
        while (lowIndex > 0 && (if (rising) row[lowIndex - 1] <= row[lowIndex] else row[lowIndex - 1] >= row[lowIndex])) {
            lowIndex--
        }
        var highIndex = steepest + 1
        while (highIndex < n - 1 && (if (rising) row[highIndex + 1] >= row[highIndex] else row[highIndex + 1] <= row[highIndex])) {
            highIndex++
        }
        val lowValue = row[lowIndex]
        val highValue = row[highIndex]
        val contrast = abs(highValue - lowValue)
        if (contrast < 30.0) return null
        val p10 = lowValue + (highValue - lowValue) * 0.10
        val p90 = lowValue + (highValue - lowValue) * 0.90
        fun cross(level: Double): Double {
            for (d in 0 until n) {
                for (i in intArrayOf(steepest - d, steepest + d)) {
                    if (i < 0 || i >= n - 1) continue
                    val here = row[i]
                    val next = row[i + 1]
                    if (here == next) continue
                    if ((level - here) * (level - next) <= 0.0) {
                        return i + (level - here) / (next - here)
                    }
                }
            }
            return Double.NaN
        }
        val x10 = cross(p10)
        val x90 = cross(p90)
        if (x10.isNaN() || x90.isNaN()) return null
        val mid = cross(lowValue + (highValue - lowValue) * 0.5)
        if (mid.isNaN()) return null
        return Edge(position = from + mid, spread = abs(x90 - x10), contrast = contrast)
    }

    /** The covered stripe on row [y], inset so the rim itself is never the transition measured. */
    private fun coveredWindow(masked: IntArray, w: Int, y: Int, inset: Int): Pair<Int, Int>? {
        var lo = -1
        var hi = -1
        for (x in 0 until w) {
            if (a(masked[y * w + x]) > 240) {
                if (lo < 0) lo = x
                hi = x
            }
        }
        if (lo < 0 || hi - lo < 2 * inset + 8) return null
        return (lo + inset) to (hi - inset)
    }

    @Test
    fun theCompressedHeldBandIsNoMoreThanOnePixelSofterThanItsSupersampledReference() {
        val y = height / 2 + pad
        val w0 = width + 2 * pad
        // The aperture's own covered stripe, so nothing here is measured against the page.
        val coverage = GlassEndpointRender.render(
            width = width, height = height, pad = pad,
            refractBand = band, refractDepth = 14f,
            profile = 2f, heldLens = 1f, heldInk = 1f, inkSplit = 0f, body = bodyAt(1),
            backdrop = { _, _ -> (0xFF shl 24) or 0x202430 },
            ink1 = { _, _ -> 0xFFFFFFFF.toInt() },
        )
        val window = coveredWindow(coverage.masked, w0, y, inset = 3)
            ?: error("the aperture covered nothing on the scanned row")
        val (scanFrom, scanTo) = window

        // Source depths 0.295 W .. 0.55 W are what lands in the band, so the edge is swept there.
        val bandSourceLo = 0.295f * band
        val bandSourceHi = 0.55f * band
        val lines = ArrayList<String>()
        var worstExtra = -Double.MAX_VALUE
        var worstCase = ""
        var worstLevel = 0.0
        var inBand = 0
        var unlocalizable = 0
        val unlocalizableAt = ArrayList<String>()

        for (bright in listOf(true, false)) {
            for (phase in listOf(0f, 0.25f, 0.5f, 0.75f)) {
                // Mid-band, plus the fractional phase.
                val sourceDepth = (bandSourceLo + bandSourceHi) / 2f
                val edgeAt = pad + leftEdge + sourceDepth + phase
                val (native, nw) = renderEdge(1, edgeAt, bright)
                val (superRaw, sw) = renderEdge(ss, edgeAt, bright)
                val (reference, rw) = downsample(superRaw, sw, ss)
                check(rw == nw && rw == w0) { "the reference downsampled to $rw, not $w0" }

                val n = locateEdge(native, nw, y, scanFrom, scanTo)
                val r = locateEdge(reference, rw, y, scanFrom, scanTo)
                if (n == null || r == null) {
                    unlocalizable++
                    unlocalizableAt.add("bright=$bright phase=$phase native=${n != null} reference=${r != null}")
                    lines.add("HELD-BAND bright=$bright phase=$phase UNLOCALIZABLE")
                    continue
                }
                // Where the transition actually landed, as a depth into the body.
                val depth = r.position - pad - leftEdge
                val u = depth / band
                val insideBand = u in 0.40..0.60
                if (insideBand) inBand++
                val extra = n.spread - r.spread
                if (insideBand && extra > worstExtra) {
                    worstExtra = extra
                    worstCase = "bright=$bright phase=$phase u=${"%.3f".format(u)}"
                }
                var level = 0.0
                val lo = max(scanFrom, (r.position - 6).toInt())
                val hi = minOf(scanTo, (r.position + 6).toInt())
                for (x in lo until hi) {
                    level = max(level, abs(luma(native[y * nw + x]) - luma(reference[y * rw + x])))
                }
                if (insideBand) worstLevel = max(worstLevel, level)
                lines.add(
                    "HELD-BAND bright=$bright phase=$phase u=${"%.3f".format(u)} " +
                        "inBand=$insideBand native=${"%.3f".format(n.spread)} " +
                        "reference=${"%.3f".format(r.spread)} extra=${"%.3f".format(extra)} " +
                        "level=${"%.1f".format(level)}",
                )
            }
        }
        lines.forEach(::println)
        println(
            "HELD-BAND SUMMARY band=$band inBand=$inBand unlocalizable=$unlocalizable " +
                "worstExtra=${if (inBand > 0) "%.3f".format(worstExtra) else "n/a"} at $worstCase " +
                "worstLevel=${"%.1f".format(worstLevel)}",
        )
        unlocalizableAt.forEach { println("HELD-BAND UNLOCALIZABLE $it") }

        assertTrue(
            inBand >= 4,
            "only $inBand of 8 strokes landed inside the connection band, so the sweep did not " +
                "actually test it ($unlocalizable were unlocalizable)",
        )
        assertTrue(
            unlocalizable == 0,
            "$unlocalizable strokes could not be localised in the compressed band: " +
                unlocalizableAt.joinToString("; "),
        )
        assertTrue(
            worstExtra <= 1.0,
            "the compressed held band spreads an edge ${"%.3f".format(worstExtra)} px more than " +
                "its own supersampled reference ($worstCase); the gate is one physical pixel",
        )
    }

    /**
     * The identity region of the same map, as the paired control: a regression in the sampler has
     * to show up somewhere other than the band it was measured in.
     */
    @Test
    fun theIdentityRegionStaysAtLeastAsSharpAsItsReference() {
        val y = height / 2 + pad
        val w0 = width + 2 * pad
        val coverage = GlassEndpointRender.render(
            width = width, height = height, pad = pad,
            refractBand = band, refractDepth = 14f,
            profile = 2f, heldLens = 1f, heldInk = 1f, inkSplit = 0f, body = bodyAt(1),
            backdrop = { _, _ -> (0xFF shl 24) or 0x202430 },
            ink1 = { _, _ -> 0xFFFFFFFF.toInt() },
        )
        val (scanFrom, scanTo) = coveredWindow(coverage.masked, w0, y, inset = 3)
            ?: error("the aperture covered nothing")
        var worstExtra = -Double.MAX_VALUE
        var unlocalizable = 0
        for (bright in listOf(true, false)) {
            // Deep inside, where the held map is identity.
            val edgeAt = pad + width / 2f
            val (native, nw) = renderEdge(1, edgeAt, bright)
            val (superRaw, sw) = renderEdge(ss, edgeAt, bright)
            val (reference, rw) = downsample(superRaw, sw, ss)
            val n = locateEdge(native, nw, y, scanFrom, scanTo)
            val r = locateEdge(reference, rw, y, scanFrom, scanTo)
            if (n == null || r == null) { unlocalizable++; continue }
            worstExtra = max(worstExtra, n.spread - r.spread)
            println(
                "HELD-IDENTITY bright=$bright native=${"%.3f".format(n.spread)} " +
                    "reference=${"%.3f".format(r.spread)}",
            )
        }
        assertTrue(unlocalizable == 0, "$unlocalizable identity strokes could not be localised")
        assertTrue(
            worstExtra <= 1.0,
            "identity ink spreads ${"%.3f".format(worstExtra)} px more than its supersampled reference",
        )
    }
}
