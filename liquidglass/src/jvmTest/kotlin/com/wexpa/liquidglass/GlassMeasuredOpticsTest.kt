package com.wexpa.liquidglass

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The measured profile, asserted on rendered pixels against the numbers in
 * `docs/research/measured-model.md`. Each test is one claim the calibration captures made.
 */
class GlassMeasuredOpticsTest {

    private val W = 30f // bevel width in px; the sheet's is 90 px at 3x, an icon's about 30

    @Test
    fun testTheMeasuredRimSamplesInwardOnly() {
        // On iOS 27 nothing outside the edge is ever sampled (FINDINGS 1b): the stripes right
        // below a cover sheet's edge are crisp and unshifted in every frame. A panel wholly
        // inside the black half of a boundary must therefore stay black, however close the white
        // is. The legacy profile does the opposite, which GlassOpticsTest asserts.
        val pad = 40
        val w = 240
        val h = 160
        val boundary = pad - 6
        val px = GlassRender.render(
            width = w, height = h, pad = pad, refractBand = W, profile = 1f,
            radii = FloatArray(4) { 50f },
            backdrop = { _, y -> if (y < boundary) 0xFFFFFF else 0x000000 },
        )
        val lw = w + pad * 2
        val column = pad + w / 2
        val inside = (pad + 1 until pad + 40).map { GlassRender.luma(px[it * lw + column]) }
        assertTrue(inside.all { it < 20.0 }, "inward sampling must never bring the white in; got $inside")
    }

    @Test
    fun testTheFoldShowsContentTwiceAndHidesTheInnermostBand() {
        // A one-pixel white line 0.8 W inside the top edge, on black. The measured mapping shows it
        // upright in the stretched zone at d = 0.70 W, again upside down in the mirrored zone at
        // d = 0.21 W, and not at its own depth, because content 0-0.69 W inside is never shown
        // and 0.8 W is displayed at 0.70 W, not 0.8 W.
        val pad = 40
        val w = 300
        val h = 200
        val lineY = pad + (0.8f * W).toInt()
        val px = GlassRender.render(
            width = w, height = h, pad = pad, refractBand = W, profile = 1f,
            radii = FloatArray(4) { 50f },
            backdrop = { _, y -> if (y == lineY) 0xFFFFFF else 0x000000 },
        )
        val lw = w + pad * 2
        val column = pad + w / 2
        fun lumaAtDepth(d: Int) = GlassRender.luma(px[(pad + d) * lw + column])
        fun brightNear(d: Int, min: Double) = (d - 2..d + 2).any { lumaAtDepth(it) > min }
        val profile = (0 until 32).map { it to lumaAtDepth(it).toInt() }
        // The stretched zone magnifies 2x, so the line lands at full brightness over two rows;
        // the mirrored zone compresses 1.3x and bilinear sampling spreads the line thin.
        assertTrue(brightNear((0.70f * W).toInt(), 120.0), "the line must appear upright at 0.70 W; profile $profile")
        assertTrue(brightNear((0.21f * W).toInt(), 90.0), "the line must appear mirrored at 0.21 W; profile $profile")
        assertTrue(
            !((0.77f * W).toInt()..(0.85f * W).toInt()).any { lumaAtDepth(it) > 120.0 },
            "the line must not appear at its own depth of 0.8 W",
        )
    }

    @Test
    fun testTheCommittedLensIsOnlyAnInwardOffset() {
        // With formation 0 the fold is gone: chrome at rest and a fast pull show a shallow ring
        // that pulls content inward by 0.4 W (1 - u)^2. The same line must then appear once,
        // at or just inside its own depth, and nowhere near the rim.
        val pad = 40
        val w = 300
        val h = 200
        val lineY = pad + (0.8f * W).toInt()
        val px = GlassRender.render(
            width = w, height = h, pad = pad, refractBand = W, profile = 1f, formation = 0f,
            radii = FloatArray(4) { 50f },
            backdrop = { _, y -> if (y == lineY) 0xFFFFFF else 0x000000 },
        )
        val lw = w + pad * 2
        val column = pad + w / 2
        fun lumaAtDepth(d: Int) = GlassRender.luma(px[(pad + d) * lw + column])
        val own = (0.8f * W).toInt()
        assertTrue((own - 5..own).any { lumaAtDepth(it) > 120.0 }, "the line must sit at or just inside its depth")
        assertTrue((0 until (0.4f * W).toInt()).none { lumaAtDepth(it) > 120.0 }, "no mirrored copy on a committed pull")
    }

    @Test
    fun testTheMeasuredToneOverFlatBlackAndWhite() {
        // Light in-app glass at Tint 50: black -> 136, white -> 247. Dark: black -> 39 (35 lift),
        // white -> 175. The interior, away from the rim and the lit edge.
        val pad = 24
        val w = 200
        val h = 120
        val lw = w + pad * 2
        fun centre(px: IntArray) = GlassRender.luma(px[(pad + h / 2) * lw + pad + w / 2])
        val lightA = 0.565f
        val darkA = 0.451f
        val t = 241f / 255f
        val lightOverBlack = centre(GlassRender.render(width = w, height = h, pad = pad, profile = 1f, tintAlpha = lightA, tint = Triple(t, t, t), backdrop = { _, _ -> 0x000000 }))
        val lightOverWhite = centre(GlassRender.render(width = w, height = h, pad = pad, profile = 1f, tintAlpha = lightA, tint = Triple(t, t, t), backdrop = { _, _ -> 0xFFFFFF }))
        val darkOverBlack = centre(GlassRender.render(width = w, height = h, pad = pad, profile = 1f, tintAlpha = darkA, tint = Triple(0f, 0f, 0f), lift = 35f / 255f, backdrop = { _, _ -> 0x000000 }))
        val darkOverWhite = centre(GlassRender.render(width = w, height = h, pad = pad, profile = 1f, tintAlpha = darkA, tint = Triple(0f, 0f, 0f), lift = 35f / 255f, backdrop = { _, _ -> 0xFFFFFF }))
        assertTrue(abs(lightOverBlack - 136.0) < 6.0, "light over black: expected 136, got $lightOverBlack")
        assertTrue(abs(lightOverWhite - 247.0) < 6.0, "light over white: expected 247, got $lightOverWhite")
        assertTrue(abs(darkOverBlack - 35.0) < 6.0, "dark over black: expected 35, got $darkOverBlack")
        assertTrue(abs(darkOverWhite - 175.0) < 6.0, "dark over white: expected 175, got $darkOverWhite")
    }

    @Test
    fun testTheAdaptiveLiftReadsLightOverDarkContent() {
        // The system backdrop: gain 0.32, lift 142/255 - 0.864 x the wide-kernel luma. Over a
        // flat backdrop of luma 95/255 (the dark test image's mean) that is a lift of 60; over
        // 153/255 (the light one's) a lift of 10.
        val pad = 40
        val w = 220
        val h = 160
        val lw = w + pad * 2
        fun centre(px: IntArray) = GlassRender.luma(px[(pad + h / 2) * lw + pad + w / 2])
        fun grey(v: Int) = (v shl 16) or (v shl 8) or v
        val overDark = centre(GlassRender.render(width = w, height = h, pad = pad, profile = 1f, refractBand = 0f, tintAlpha = 0.68f, tint = Triple(0f, 0f, 0f), lift = 142f / 255f, liftAdapt = 0.864f, wide = 12f, fineShare = 0f, backdrop = { _, _ -> grey(95) }))
        val overLight = centre(GlassRender.render(width = w, height = h, pad = pad, profile = 1f, refractBand = 0f, tintAlpha = 0.68f, tint = Triple(0f, 0f, 0f), lift = 142f / 255f, liftAdapt = 0.864f, wide = 12f, fineShare = 0f, backdrop = { _, _ -> grey(153) }))
        // expected: 0.32 * 95 + 60 = 90; 0.32 * 153 + 10 = 59
        assertTrue(abs(overDark - 90.0) < 6.0, "over dark content: expected 90, got $overDark")
        assertTrue(abs(overLight - 59.0) < 6.0, "over light content: expected 59, got $overLight")
    }

    @Test
    fun testTheWideKernelSetsToneWithoutDestroyingStripes() {
        // Two kernels: 16 px stripes under a Tint-50 pill keep about 36% of their contrast while
        // a hard edge's tone spreads over 25 pt. With fine share 0.36 the stripes must survive
        // at roughly that fraction of the flat gain.
        val pad = 60
        val w = 300
        val h = 160
        val lw = w + pad * 2
        val a = 0.565f
        val t = 241f / 255f
        val px = GlassRender.render(
            width = w, height = h, pad = pad, profile = 1f, refractBand = 0f,
            tintAlpha = a, tint = Triple(t, t, t), wide = 40f, fineShare = 0.36f,
            backdrop = { x, _ -> if ((x / 8) % 2 == 0) 0xFFFFFF else 0x000000 },
        )
        val row = pad + h / 2
        val vals = (pad + 60 until pad + w - 60).map { GlassRender.luma(px[row * lw + it]) }
        val contrast = vals.max() - vals.min()
        val flatGain = (1 - a) * 255
        val kept = contrast / flatGain
        assertTrue(kept in 0.25..0.5, "stripes must keep about 36% of the flat gain; kept $kept")
    }
}
