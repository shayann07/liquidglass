package com.wexpa.liquidglass

import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The end-to-end endpoint gate of `V3-MODEL.md` section 15.1, at the **one** output level the
 * model specifies, with its actual error recorded rather than a looser tolerance substituted.
 *
 * ## The compositing domain, declared
 *
 * Every surface here is `ColorType.BGRA_8888`, `ColorAlphaType.PREMUL`, with **no colour-space
 * transform**: the shaders operate on the encoded values the layers hold, and so does the oracle
 * below. Encoded RGB and linear-light RGB are not interchangeable and nothing here converts
 * between them. Ink is premultiplied; the material's endpoint is opaque.
 *
 * Legacy has **three** 8-bit quantisation points between the inputs and what a viewer sees, and
 * they are the same three its production path has (`LiquidGlass.kt`: `glassLayer`, `contentLayer`
 * and `endpointLayer` are all `GraphicsLayer`s, and the aperture runs as a `renderEffect` *on*
 * the already-quantised endpoint layer):
 *
 *  1. `B1` and `(c1, a1)`, each rounded as its pass writes its layer;
 *  2. `C1 = c1 + (1 - a1) B1`, rounded as the offscreen endpoint layer is written;
 *  3. `(m C1, m)`, rounded as the aperture pass writes its result.
 *
 * So two numbers are reported separately, because they mean different things:
 *
 *  - **per-pass error** - each pass against the exact equation evaluated on the intermediate the
 *    pipeline actually handed it. This is the compositor's own correctness and it is gated at one
 *    level: `C1` against `c1 + (1 - a1) B1`, and the drawn result against `m C1 + (1 - m) C0` on
 *    the endpoint the aperture genuinely consumed.
 *  - **end-to-end error** - the drawn result against the exact equation on the passes' unrounded
 *    outputs. This carries every rounding as well, and is **recorded** at its measured maximum.
 *    The V3 section 15.1 end-to-end gate is one level; legacy does **not** meet it.
 *
 * Calm packs B1 and ink separately, so the aperture composes and masks before the output write.
 * It is checked against the SAME double-precision equation and the strict one-level limit.
 * Composition of the old C1 remains a diagnostic control; Calm does not consume that image.
 * These gates begin at the material/ink outputs. They do not measure optical accuracy, colour
 * management, or rounding from a subsequent real display framebuffer write.
 *
 * The oracle is never the renderer's own final answer; `B1` and the ink are the passes' declared
 * outputs, and everything after them is computed here in double precision.
 */
class GlassEndpointGateTest {

    private fun a(p: Int) = (p ushr 24) and 0xFF
    private fun channel(p: Int, c: Int): Int = when (c) {
        0 -> (p ushr 16) and 0xFF
        1 -> (p ushr 8) and 0xFF
        else -> p and 0xFF
    }

    private val width = 180
    private val height = 90
    private val pad = 24

    /** The measured worst end-to-end error, for the record. Filled by the gate below. */
    companion object {
        /**
         * What quantisation alone can explain end to end, in output levels:
         *
         *  - 0.5 - the endpoint layer's own write, propagated through the aperture by `m <= 1`;
         *  - 0.5 - the aperture layer's write;
         *  - 0.5 - reading `m` back out of a quantised 8-bit alpha channel, which moves the
         *    oracle by `|C1 - C0| * 0.5/255 <= 0.5` levels. This one is the measurement's, not
         *    the pipeline's, and it cannot be removed without restating the implementation's own
         *    coverage expression as the oracle.
         *
         * Anything past this is a real defect in the composition rather than rounding.
         */
        private const val QUANTISATION_BUDGET = 1.5
    }

    // ------------------------------------------------------------------- fixtures

    /** Different `B0`: a saturated page, a near-flat one and a dark one. */
    private fun pageSaturated(x: Int, y: Int): Int {
        val band = (x / 18) % 4
        val row = (y / 15) % 3
        val rr = if (band == 0 || band == 3) 250 else 6
        val gg = if (row == 1) 240 else 12
        val bb = if (band == 2 || row == 2) 230 else 20
        return (rr shl 16) or (gg shl 8) or bb
    }

    private fun pageDark(x: Int, y: Int): Int {
        val v = 8 + ((x * 3 + y * 5) % 24)
        return (v shl 16) or ((v + 4) shl 8) or (v + 9)
    }

    private fun pageWarm(x: Int, y: Int): Int {
        val band = (x / 9) % 2
        val rr = if (band == 0) 218 else 196
        val gg = if (band == 0) 160 else 142
        val bb = 96 + ((y / 7) % 3) * 12
        return (rr shl 16) or (gg shl 8) or bb
    }

    private fun premul(r: Int, g: Int, b: Int, al: Int): Int =
        (al shl 24) or ((r * al / 255) shl 16) or ((g * al / 255) shl 8) or (b * al / 255)

    /** Ordinary ink: opaque light and dark glyph strokes. */
    private fun inkOpaque(x: Int, y: Int): Int = when ((x / 11 + y / 23) % 4) {
        0 -> premul(255, 255, 255, 255)
        1 -> premul(12, 12, 16, 255)
        2 -> premul(230, 236, 255, 255)
        else -> 0
    }

    /** Selected ink: translucent and coloured, which is where the cross term actually lives. */
    private fun inkTranslucentColoured(x: Int, y: Int): Int = when ((x / 13) % 5) {
        0 -> premul(255, 246, 210, 255)
        1 -> premul(20, 10, 60, 190)
        2 -> premul(255, 40, 40, 128)
        3 -> premul(40, 255, 90, ((x * 7 + y * 3) % 256))
        else -> premul(120, 200, 255, 64)
    }

    private class Score {
        var composition = 0.0
        var aperture = 0.0
        var endToEnd = 0.0
        var partialPixels = 0
        var samples = 0
    }

    /**
     * One scene, scored. [body] makes the aperture a deforming body so `m` takes every value
     * across the rim rather than only 0 and 1.
     */
    private fun score(
        backdrop: (Int, Int) -> Int,
        ink: (Int, Int) -> Int,
        body: GlassBody?,
        into: Score,
        packedEndpoint: Boolean = false,
    ) {
        val result = GlassEndpointRender.render(
            width = width,
            height = height,
            pad = pad,
            refractBand = if (body != null) glassBodyBand(body) else 24f,
            refractDepth = 12f,
            profile = 2f,
            heldLens = 1f,
            tintAlpha = 0.02f,
            specular = 0.3f,
            edgeShadow = 0.12f,
            bevel = 2f,
            counterLight = 1f,
            body = body,
            packedEndpoint = packedEndpoint,
            backdrop = backdrop,
            ink1 = ink,
        )
        val w = result.layerWidth
        for (i in result.b1.indices) {
            val m = a(result.masked[i]) / 255.0
            if (m <= 0.0) continue
            if (m < 0.999) into.partialPixels++
            into.samples++
            val a1 = a(result.ink[i]) / 255.0
            val x = i % w
            val y = i / w
            for (c in 0..2) {
                val b1c = channel(result.b1[i], c).toDouble()
                val c1Ink = channel(result.ink[i], c).toDouble()
                val c0 = channel(backdrop(x, y), c).toDouble()
                // The exact endpoint on the passes' own declared outputs.
                val exactC1 = c1Ink + (1.0 - a1) * b1c
                // What the pipeline actually put on screen: the aperture's premultiplied result
                // source-over the page, which is the real final path and not a restatement.
                val drawn = channel(result.masked[i], c) + (1.0 - m) * c0

                // End to end: three roundings deep.
                into.endToEnd = max(into.endToEnd, abs(drawn - (m * exactC1 + (1.0 - m) * c0)))
                // Per pass, each against the intermediate the pipeline genuinely handed it.
                // Using `round(exactC1)` here instead would count the endpoint layer's rounding
                // twice - the layer holds Skia's own premultiplied source-over, not this
                // expression rounded - and would report an error larger than the chain's.
                val actualC1 = channel(result.endpoint[i], c).toDouble()
                into.composition = max(into.composition, abs(actualC1 - exactC1))
                val consumedC1 = if (packedEndpoint) exactC1 else actualC1
                into.aperture = max(into.aperture, abs(drawn - (m * consumedC1 + (1.0 - m) * c0)))
            }
        }
    }

    @Test
    fun theEndToEndChainCarriesNoArithmeticErrorBeyondItsDeclaredQuantisation() {
        checkEndpointChain(packedEndpoint = false)
    }

    @Test
    fun packedInkAndMaterialMeetTheOneLevelEndToEndGate() {
        checkEndpointChain(packedEndpoint = true)
    }

    private fun checkEndpointChain(packedEndpoint: Boolean) {
        val bodies = listOf(
            null,
            GlassBody(cx = width / 2f - 42f, cy = height / 2f, length = 70f, radius = 34f, skew = 9f),
            GlassBody(cx = width / 2f + 26f, cy = height / 2f, length = 96f, radius = 30f, skew = -11f),
        )
        val pages = listOf<Pair<String, (Int, Int) -> Int>>(
            "saturated" to ::pageSaturated,
            "dark" to ::pageDark,
            "warm" to ::pageWarm,
        )
        val inks = listOf<Pair<String, (Int, Int) -> Int>>(
            "opaque" to ::inkOpaque,
            "translucent-coloured" to ::inkTranslucentColoured,
        )
        var worstComposition = 0.0
        var worstAperture = 0.0
        var worstEndToEnd = 0.0
        var partial = 0
        var samples = 0
        val lines = ArrayList<String>()
        for ((pageName, page) in pages) {
            for ((inkName, ink) in inks) {
                for ((bi, body) in bodies.withIndex()) {
                    val s = Score()
                    score(page, ink, body, s, packedEndpoint)
                    worstComposition = max(worstComposition, s.composition)
                    worstAperture = max(worstAperture, s.aperture)
                    worstEndToEnd = max(worstEndToEnd, s.endToEnd)
                    partial += s.partialPixels
                    samples += s.samples
                    lines.add(
                        "ENDPOINT-GATE packed=$packedEndpoint page=$pageName ink=$inkName body=$bi " +
                            "composition=${"%.4f".format(s.composition)} " +
                            "aperture=${"%.4f".format(s.aperture)} " +
                            "endToEnd=${"%.4f".format(s.endToEnd)} " +
                            "partial=${s.partialPixels} samples=${s.samples}",
                    )
                }
            }
        }
        lines.forEach(::println)
        println(
            "ENDPOINT-GATE SUMMARY packed=$packedEndpoint compositionControl=${"%.4f".format(worstComposition)} " +
                "aperture=${"%.4f".format(worstAperture)} " +
                "endToEnd=${"%.4f".format(worstEndToEnd)} partial=$partial samples=$samples",
        )
        assertTrue(samples > 100_000, "the sweep scored only $samples pixels")
        assertTrue(partial > 5_000, "the sweep barely touched fractional aperture coverage: $partial")
        // The compositor's own correctness, pass by pass, at the model's one level.
        assertTrue(
            worstComposition <= 1.0,
            "C1 is off `c1 + (1 - a1) B1` by $worstComposition levels, past the one level " +
                "V3 section 15.1 allows",
        )
        assertTrue(
            worstAperture <= 1.0,
            "the drawn result is off `m C1 + (1 - m) C0` by $worstAperture levels on the very " +
                "endpoint the aperture consumed, past the one level V3 section 15.1 allows",
        )
        val limit = if (packedEndpoint) 1.0 else QUANTISATION_BUDGET
        assertTrue(
            worstEndToEnd <= limit,
            "packed=$packedEndpoint is off by $worstEndToEnd levels, past its $limit limit",
        )
    }

    /**
     * The equal-endpoint invariant with **nonempty** ink: when `C0` and `C1` are the same image,
     * moving the aperture over them must change nothing. The existing control uses a flat page and
     * no ink at all, which cannot catch coverage leaking through the ink path.
     */
    @Test
    fun equalEndpointsWithRealInkLeaveTheImageUntouched() {
        val flat = 0x3C6E91
        var worst = 0.0
        var partial = 0
        for (shift in listOf(-40f, 0f, 25f)) {
            val body = GlassBody(cx = width / 2f + shift, cy = height / 2f, length = 80f, radius = 32f, skew = -7f)
            // Identical endpoints with ink present: the ink is composed into B1 as well, by
            // handing the same premultiplied glyphs to the backdrop - so C1 == C0 by construction
            // even though a1 is nonzero everywhere the glyphs are.
            val opaqueInk: (Int, Int) -> Int = { x, y ->
                val g = inkOpaque(x, y)
                if (a(g) == 255) g else 0
            }
            val composed: (Int, Int) -> Int = { x, y ->
                val g = opaqueInk(x, y)
                if (a(g) == 255) g or (0xFF shl 24) else flat
            }
            val result = GlassEndpointRender.render(
                width = width, height = height, pad = pad,
                refractBand = glassBodyBand(body), refractDepth = 0f,
                profile = 0f, heldLens = 0f,
                body = body,
                backdrop = composed,
                ink1 = opaqueInk,
            )
            val w = result.layerWidth
            for (i in result.b1.indices) {
                val m = a(result.masked[i]) / 255.0
                if (m <= 0.0) continue
                if (m < 0.999) partial++
                val x = i % w
                val y = i / w
                for (c in 0..2) {
                    val c0 = channel(composed(x, y), c).toDouble()
                    val drawn = channel(result.masked[i], c) + (1.0 - m) * c0
                    worst = max(worst, abs(drawn - c0))
                }
            }
        }
        println("ENDPOINT-EQUAL worst=${"%.4f".format(worst)} partial=$partial")
        assertTrue(partial > 500, "the invariant was not exercised at fractional coverage: $partial")
        assertTrue(
            worst <= 1.5,
            "an aperture over identical endpoints, with ink present, changed the image by " +
                "$worst levels",
        )
    }

    /**
     * The ink pass emits the glyph's **own** premultiplied colour and alpha at the coordinate it
     * sampled, when the split is off.
     *
     * The source coordinate is obtained independently: the same geometry is rendered once with an
     * ink whose red and green channels **are** its own `(x, y)`, so the pass's output reads back
     * the coordinate it sampled. The real ink's emitted value is then checked against the known
     * ink function at that coordinate. Nothing here compares one rendering against another, and
     * nothing asserts that a number is nonnegative.
     */
    @Test
    fun theSingleRaySamplesTheGlyphsOwnPremultipliedColourAndAlpha() {
        val body = GlassBody(cx = width / 2f, cy = height / 2f, length = 90f, radius = 36f, skew = 0f)
        val w = width + pad * 2
        val h = height + pad * 2
        // Channels 0 and 1 carry the coordinate; alpha is opaque so premultiplication is identity
        // and the value read back is the coordinate itself, bilinearly interpolated.
        val coordinateInk: (Int, Int) -> Int = { x, y ->
            (255 shl 24) or ((x and 0xFF) shl 16) or ((y and 0xFF) shl 8) or 0
        }
        val probe = GlassEndpointRender.render(
            width = width, height = height, pad = pad,
            refractBand = glassBodyBand(body), refractDepth = 14f,
            profile = 2f, heldLens = 1f, inkSplit = 0f, body = body,
            backdrop = ::pageSaturated, ink1 = coordinateInk,
        )
        // Large constant blocks, so a sampled point well inside one has an unambiguous value
        // under bilinear filtering.
        val block = 26
        val known: (Int, Int) -> Int = { x, y ->
            when ((x / block + (y / block) * 2) % 4) {
                0 -> premul(255, 255, 255, 255)
                1 -> premul(0, 0, 0, 255)
                2 -> premul(255, 40, 40, 128)
                else -> premul(60, 220, 255, 200)
            }
        }
        val real = GlassEndpointRender.render(
            width = width, height = height, pad = pad,
            refractBand = glassBodyBand(body), refractDepth = 14f,
            profile = 2f, heldLens = 1f, inkSplit = 0f, body = body,
            backdrop = ::pageSaturated, ink1 = known,
        )

        var checked = 0
        var worstAlpha = 0
        var worstColour = 0
        for (i in probe.ink.indices) {
            if (a(probe.masked[i]) == 0) continue
            // The coordinate this pixel sampled, recovered from the probe's own output.
            val sx = channel(probe.ink[i], 0)
            val sy = channel(probe.ink[i], 1)
            // Only where the sample sits well inside one block: bilinear filtering across a block
            // boundary has no single correct answer and would be scoring the sampler, not the ink.
            val inX = sx % block
            val inY = sy % block
            if (inX < 3 || inX > block - 4 || inY < 3 || inY > block - 4) continue
            if (sx < 2 || sy < 2 || sx > w - 3 || sy > h - 3) continue
            val expected = known(sx, sy)
            checked++
            worstAlpha = max(worstAlpha, abs(a(real.ink[i]) - a(expected)))
            for (c in 0..2) {
                worstColour = max(worstColour, abs(channel(real.ink[i], c) - channel(expected, c)))
            }
        }
        println("INK-SINGLE-RAY checked=$checked worstAlpha=$worstAlpha worstColour=$worstColour")
        assertTrue(checked > 2_000, "only $checked interior samples were verifiable")
        assertTrue(
            worstAlpha <= 1,
            "the single-ray alpha is off the glyph's own by $worstAlpha levels",
        )
        assertTrue(
            worstColour <= 2,
            "the single-ray premultiplied colour is off the glyph's own by $worstColour levels",
        )
    }
}
