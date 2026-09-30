package com.wexpa.liquidglass

import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The compositor gates of `V3-MODEL.md` section 15.1.
 *
 * The claim under test is that replacing complete endpoints is what the material actually does,
 * not that the expression in the shader equals itself. So the two endpoints are taken from the
 * passes' own outputs — `B1` from the material emitting its opaque endpoint, `(c1, a1)` from the
 * ink pass emitting premultiplied unmasked ink — and the composition is scored against
 *
 * ```
 * C1 = c1 + (1 - a1) B1
 * C  = (1 - m) C0 + m C1
 * ```
 *
 * computed independently here in double precision. The naive alternative is computed alongside,
 * so the cross term `m (1 - m) (a1 - a0) (B1 - B0)` is shown to be a real difference on these
 * inputs rather than asserted to be one.
 */
class GlassEndpointCompositorTest {

    private fun a(p: Int) = (p ushr 24) and 0xFF
    private fun r(p: Int) = (p ushr 16) and 0xFF
    private fun g(p: Int) = (p ushr 8) and 0xFF
    private fun b(p: Int) = p and 0xFF

    private val width = 180
    private val height = 90
    private val pad = 24

    /** A saturated, high-contrast page: every channel pair is different somewhere. */
    private fun page(x: Int, y: Int): Int {
        val band = (x / 18) % 4
        val row = (y / 15) % 3
        val rr = if (band == 0 || band == 3) 250 else 6
        val gg = if (row == 1) 240 else 12
        val bb = if (band == 2 || row == 2) 230 else 20
        return (rr shl 16) or (gg shl 8) or bb
    }

    /**
     * Premultiplied ink: hard glyph edges, a soft antialiased edge and a translucent block,
     * spanning the whole padded layer so that ink really is present where the aperture is
     * partial. Ink only in the interior would make the cross term identically zero and the
     * comparison below vacuous.
     */
    private fun ink(x: Int, y: Int): Int = when ((x / 13) % 5) {
        0 -> premul(255, 255, 255, 255)                     // opaque white
        1 -> premul(0, 0, 0, 255)                           // opaque black
        2 -> premul(255, 40, 40, 128)                       // translucent red
        3 -> premul(40, 255, 90, ((x * 7 + y * 3) % 256))   // a ramp of alpha
        else -> 0
    }

    private fun premul(r: Int, g: Int, b: Int, a: Int): Int =
        (a shl 24) or ((r * a / 255) shl 16) or ((g * a / 255) shl 8) or (b * a / 255)

    @Test
    fun theCompositionIsTheCompleteEndpointEquationToWithinOneCodeLevel() {
        val result = GlassEndpointRender.render(
            width = width,
            height = height,
            pad = pad,
            refractBand = 24f,
            refractDepth = 10f,
            profile = 2f,
            heldLens = 1f,
            tintAlpha = 0.02f,
            specular = 0.3f,
            edgeLight = 0f,
            edgeShadow = 0.12f,
            bevel = 2f,
            counterLight = 1f,
            backdrop = ::page,
            ink1 = ::ink,
        )
        var worstEndpoint = 0.0
        var worstMasked = 0.0
        var worstFinal = 0.0
        var worstNaiveGap = 0.0
        var worstChain = 0.0
        var edgePixels = 0
        val w = result.layerWidth
        for (i in result.b1.indices) {
            val m = a(result.masked[i]) / 255.0
            if (m <= 0.0) continue
            if (m < 0.999) edgePixels++
            val a1 = a(result.ink[i]) / 255.0
            for (c in 0..2) {
                val b1c = channel(result.b1[i], c).toDouble()
                val c1Ink = channel(result.ink[i], c).toDouble()   // premultiplied
                val expectedC1 = c1Ink + (1.0 - a1) * b1c
                worstEndpoint = max(worstEndpoint, abs(channel(result.endpoint[i], c) - expectedC1))
                // Each step is scored against the 8-bit image the pipeline actually hands it, so
                // the figure is that step's own error rather than three quantisations summed.
                worstMasked = max(
                    worstMasked,
                    abs(channel(result.masked[i], c) - m * channel(result.endpoint[i], c)),
                )

                // What a viewer sees, with C0 the page itself (no ordinary ink under this rim).
                val x = i % w
                val y = i / w
                val c0 = channel(page(x, y), c).toDouble()
                val final = m * channel(result.endpoint[i], c) + (1.0 - m) * c0
                val drawn = channel(result.masked[i], c) + (1.0 - m) * c0
                worstFinal = max(worstFinal, abs(drawn - final))
                val exact = m * expectedC1 + (1.0 - m) * c0

                // The shortcut this replaces, and the size of its error on these inputs.
                val naive = (1.0 - m) * 0.0 + m * c1Ink +
                    (1.0 - m * a1) * ((1.0 - m) * c0 + m * b1c)
                worstNaiveGap = max(worstNaiveGap, abs(naive - exact))
                worstChain = max(worstChain, abs(drawn - exact))
            }
        }
        assertTrue(edgePixels > 200, "the sweep must include antialiased rim pixels: $edgePixels")
        assertTrue(worstEndpoint <= 1.0, "C1 is off the endpoint equation by $worstEndpoint levels")
        assertTrue(worstMasked <= 1.0, "(m C1, m) is off by $worstMasked levels")
        assertTrue(worstFinal <= 1.0, "the composed result is off by $worstFinal levels")
        println(
            "ENDPOINT worstEndpoint=$worstEndpoint worstMasked=$worstMasked " +
                "worstFinal=$worstFinal worstChain=$worstChain",
        )
        // End to end, three 8-bit intermediates deep, against the equation in exact arithmetic.
        //
        // This number used to be asserted at two levels while the method name and V3 section 15.1
        // both say one. It is NOT one - it is measured at about 1.24 on this fixture and 1.43 over
        // the wider sweep in GlassEndpointGateTest - and the gate is carried as a PARTIAL with its
        // actual maximum rather than renamed. What is gated at one level is each pass against the
        // intermediate the pipeline genuinely handed it, which is the compositor's own
        // correctness; the rest is the three 8-bit layer writes the production path also has.
        assertTrue(
            worstChain <= 1.5,
            "the whole chain is off the exact equation by $worstChain levels, past what its three " +
                "8-bit intermediates can explain",
        )
        assertTrue(
            worstNaiveGap > 8.0,
            "the naive path would differ by only $worstNaiveGap levels here, so this fixture does " +
                "not actually exercise the cross term",
        )
    }

    @Test
    fun aCoverageSweepStaysExactAtEveryPartialAperture() {
        // Aperture coverage is swept by moving a body across a fixed glyph, so m takes every
        // value between 0 and 1 across the rim rather than only 0 and 1.
        var worst = 0.0
        var samples = 0
        for (shift in listOf(-60f, -30f, 0f, 18f, 46f)) {
            val body = GlassBody(cx = width / 2f + shift, cy = height / 2f, length = 70f, radius = 34f, skew = 9f)
            val result = GlassEndpointRender.render(
                width = width, height = height, pad = pad,
                refractBand = glassBodyBand(body), refractDepth = 10f,
                profile = 2f, heldLens = 1f, tintAlpha = 0.02f, specular = 0.3f,
                edgeShadow = 0.12f, bevel = 2f, counterLight = 1f,
                body = body, backdrop = ::page, ink1 = ::ink,
            )
            for (i in result.b1.indices) {
                val m = a(result.masked[i]) / 255.0
                if (m <= 0.0) continue
                val a1 = a(result.ink[i]) / 255.0
                for (c in 0..2) {
                    // Each pass is scored against the 8-bit image the pipeline actually hands
                    // the next one, so the number is that step's own error and not three
                    // quantisations added together.
                    val c1 = channel(result.ink[i], c) + (1.0 - a1) * channel(result.b1[i], c)
                    worst = max(worst, abs(channel(result.endpoint[i], c) - c1))
                    worst = max(worst, abs(channel(result.masked[i], c) - m * channel(result.endpoint[i], c)))
                }
                samples++
            }
        }
        assertTrue(samples > 20000, "coverage sweep covered only $samples pixels")
        assertTrue(worst <= 1.0, "partial coverage is off by $worst levels")
    }

    @Test
    fun equalEndpointsLeaveTheImageUntouchedAtEveryCoverage() {
        // With a flat page there is nothing for the optics to displace, so B1 is B0 whatever the
        // material does; with no ink at all C1 is C0. Moving the aperture across that must then
        // change no pixel: any silhouette appearing here would be coverage leaking into the
        // result on its own.
        val flat = 0x3C6E91
        var worst = 0.0
        for (shift in listOf(-40f, 0f, 25f)) {
            val body = GlassBody(cx = width / 2f + shift, cy = height / 2f, length = 80f, radius = 32f, skew = -7f)
            val result = GlassEndpointRender.render(
                width = width, height = height, pad = pad,
                // No optics and no rim: the material's held glow is a real term, so an
                // invariant about the *compositor* must not be run with it switched on.
                refractBand = glassBodyBand(body), refractDepth = 0f,
                profile = 0f, heldLens = 0f,
                body = body,
                backdrop = { _, _ -> flat },
                ink1 = { _, _ -> 0 },
            )
            val w = result.layerWidth
            for (i in result.b1.indices) {
                val m = a(result.masked[i]) / 255.0
                if (m <= 0.0) continue
                for (c in 0..2) {
                    val c0 = channel(flat, c).toDouble()
                    val drawn = channel(result.masked[i], c) + (1.0 - m) * c0
                    worst = max(worst, abs(drawn - c0))
                }
                @Suppress("UNUSED_EXPRESSION") w
            }
        }
        assertTrue(worst <= 1.0, "an aperture over identical endpoints changed the image by $worst levels")
    }

    @Test
    fun inkKeepsItsOwnAlphaWhenTheSplitIsOff() {
        // A maximum over three channels' alphas is not correct coloured transmission: a white
        // glyph found only by the red ray would block green and blue too. With the split off the
        // pass takes one sample, so the alpha it emits is exactly the glyph's own at the source
        // coordinate, and a black glyph over a saturated page stays black rather than picking up
        // a channel that has drifted off it.
        val body = GlassBody(cx = width / 2f, cy = height / 2f, length = 90f, radius = 36f, skew = 0f)
        val sharp = GlassEndpointRender.render(
            width = width, height = height, pad = pad,
            refractBand = glassBodyBand(body), refractDepth = 14f,
            profile = 2f, heldLens = 1f, inkSplit = 0f, body = body,
            backdrop = ::page, ink1 = ::ink,
        )
        val split = GlassEndpointRender.render(
            width = width, height = height, pad = pad,
            refractBand = glassBodyBand(body), refractDepth = 14f,
            profile = 2f, heldLens = 1f, inkSplit = 0.6f, body = body,
            backdrop = ::page, ink1 = ::ink,
        )
        // The structural claim, exactly: the split's alpha is `max(aR, aG, aB)` over three rays,
        // and the middle ray is the same sample the sharp path takes. So the split's alpha is
        // **never below** the sharp path's, and wherever a side ray finds a glyph the middle ray
        // does not, the split invents ink there. Neither fact is a restatement of the shader's
        // expression: both are checked against the rendered output.
        //
        // The previous version of this test ended in `sharpWorst >= 0 && splitWorst >= 0`, which
        // is true of any two nonnegative integers, plus "the split differs". That establishes a
        // difference, not correct single-ray alpha. What the single ray emits is now checked
        // against the glyph's own premultiplied colour and alpha at an independently recovered
        // source coordinate, in
        // GlassEndpointGateTest.theSingleRaySamplesTheGlyphsOwnPremultipliedColourAndAlpha.
        var below = 0
        var invented = 0
        var mismatched = 0
        var scored = 0
        for (i in sharp.ink.indices) {
            if (a(sharp.masked[i]) == 0) continue
            scored++
            val aSharp = a(sharp.ink[i])
            val aSplit = a(split.ink[i])
            if (aSplit < aSharp - 1) below++
            if (aSharp == 0 && aSplit > 8) invented++
            if (aSharp != aSplit) mismatched++
        }
        println("INK-SPLIT scored=$scored below=$below invented=$invented mismatched=$mismatched")
        assertTrue(scored > 5000, "only $scored pixels were under the aperture at all")
        assertTrue(
            below == 0,
            "$below pixels had a split alpha below the single ray's, so the split is not the " +
                "union of three rays one of which is that ray",
        )
        assertTrue(
            invented > 50,
            "the split invented ink at only $invented pixels the single ray found none at, so " +
                "this fixture does not exercise the union alpha at all",
        )
        assertTrue(
            mismatched > 0,
            "the split fixture did not actually change any alpha, so this comparison is vacuous",
        )
    }

    private fun channel(p: Int, c: Int): Int = when (c) {
        0 -> r(p)
        1 -> g(p)
        else -> b(p)
    }
}
