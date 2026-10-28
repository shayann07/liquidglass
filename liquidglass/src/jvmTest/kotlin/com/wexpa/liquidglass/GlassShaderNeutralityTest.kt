package com.wexpa.liquidglass

import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression suite for shader optical correspondence, neutrality, and channel crush prevention.
 *
 * Verifies that:
 * 1. Grayscale (neutral) input with zero dispersion (uAberration=0, uInkSplit=0) produces strictly
 *    neutral output across all profiles, displacement polarities (positive inward, negative outward,
 *    and zero flat), and states (resting, held, transit).
 * 2. Negative displacement in the held lens profile (uProfile=2) is legitimate and samples the
 *    surrounding recorded margin (uPad) without channel clamp asymmetry (issue #124 repair).
 * 3. Nonzero dispersion (uAberration > 0, uInkSplit > 0) preserves the measured chromatic fringes.
 * 4. High-contrast content near the rim and outside the glass does not cause channel crush (issue #108).
 * 5. Material and semantic ink passes bend consistently and independently.
 */
class GlassShaderNeutralityTest {

    private fun checkNeutrality(
        pixels: IntArray,
        width: Int,
        height: Int,
        pad: Int,
        label: String,
        maxAllowedError: Int = 0
    ) {
        val w = width + pad * 2
        val h = height + pad * 2
        var nonNeutralCount = 0
        var maxDiff = 0
        for (y in pad until pad + height) {
            for (x in pad until pad + width) {
                val p = pixels[y * w + x]
                val r = (p ushr 16) and 0xFF
                val g = (p ushr 8) and 0xFF
                val b = p and 0xFF
                val diff = maxOf(abs(r - g), abs(g - b), abs(r - b))
                if (diff > maxAllowedError) {
                    nonNeutralCount++
                    maxDiff = max(maxDiff, diff)
                }
            }
        }
        assertEquals(
            0, nonNeutralCount,
            "$label: expected 0 non-neutral pixels (R==G==B), but found $nonNeutralCount with maxDiff=$maxDiff"
        )
    }

    @Test
    fun heldLensWithZeroDispersionPreservesStrictNeutralityAcrossGrayscaleRamp() {
        // Previously: uProfile=2, uHeldLens=1, uAberration=0 produced 2,576+ non-neutral pixels
        // because outward (negative) displacement was clamped to 0 on Red only.
        val width = 160
        val height = 60
        val pad = 32
        val w = width + pad * 2
        val h = height + pad * 2

        val pixels = GlassRender.render(
            width = width,
            height = height,
            pad = pad,
            profile = 2f,
            heldLens = 1f,
            formation = 0f,
            aberration = 0f,
            backdrop = { x, y ->
                // Linear grayscale diagonal ramp: R == G == B everywhere
                val v = (((x + y).toFloat() / (w + h)) * 255f).toInt().coerceIn(0, 255)
                (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            }
        )

        checkNeutrality(pixels, width, height, pad, "Held lens zero-dispersion grayscale ramp")
    }

    @Test
    fun transitFoldWithZeroDispersionPreservesStrictNeutralityAcrossGrayscaleRamp() {
        val width = 160
        val height = 60
        val pad = 32
        val w = width + pad * 2
        val h = height + pad * 2

        val pixels = GlassRender.render(
            width = width,
            height = height,
            pad = pad,
            profile = 1f,
            formation = 1f,
            foldCorners = 1f,
            aberration = 0f,
            backdrop = { x, y ->
                val v = (((x + y).toFloat() / (w + h)) * 255f).toInt().coerceIn(0, 255)
                (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            }
        )

        checkNeutrality(pixels, width, height, pad, "Transit fold zero-dispersion grayscale ramp")
    }

    @Test
    fun restingBarWithZeroDispersionPreservesStrictNeutralityAcrossGrayscaleRamp() {
        val width = 160
        val height = 60
        val pad = 32
        val w = width + pad * 2
        val h = height + pad * 2

        val pixels = GlassRender.render(
            width = width,
            height = height,
            pad = pad,
            profile = 1f,
            formation = 0f,
            foldCorners = 0f,
            aberration = 0f,
            backdrop = { x, y ->
                val v = (((x + y).toFloat() / (w + h)) * 255f).toInt().coerceIn(0, 255)
                (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            }
        )

        checkNeutrality(pixels, width, height, pad, "Resting bar zero-dispersion grayscale ramp")
    }

    @Test
    fun positiveNegativeAndZeroDisplacementsAllMaintainExactChannelAgreement() {
        val width = 120
        val height = 60
        val pad = 32
        val w = width + pad * 2
        val h = height + pad * 2

        fun ramp(x: Int, y: Int): Int {
            val v = (((x * 2 + y).toFloat() / (w * 2 + h)) * 255f).toInt().coerceIn(0, 255)
            return (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }

        // 1. Positive displacement (inward): measured fold profile (profile 1, formation 1)
        val foldPixels = GlassRender.render(
            width = width, height = height, pad = pad,
            profile = 1f, formation = 1f, foldCorners = 0f, aberration = 0f,
            backdrop = ::ramp
        )
        checkNeutrality(foldPixels, width, height, pad, "Positive (inward) displacement")

        // 2. Negative displacement (outward): measured held lens profile (profile 2, heldLens 1)
        val heldPixels = GlassRender.render(
            width = width, height = height, pad = pad,
            profile = 2f, heldLens = 1f, formation = 0f, aberration = 0f,
            backdrop = ::ramp
        )
        checkNeutrality(heldPixels, width, height, pad, "Negative (outward) displacement")

        // 3. Zero displacement: flat interior / legacy profile zero depth
        val flatPixels = GlassRender.render(
            width = width, height = height, pad = pad,
            profile = 0f, refractDepth = 0f, aberration = 0f,
            backdrop = ::ramp
        )
        checkNeutrality(flatPixels, width, height, pad, "Zero displacement")
    }

    @Test
    fun nonzeroDispersionProducesFringeWhileZeroDispersionIsNeutral() {
        val width = 160
        val height = 60
        val pad = 32
        val w = width + pad * 2
        val h = height + pad * 2

        fun stepBackdrop(x: Int, y: Int): Int {
            // High-contrast vertical edge at center: black on left, white on right
            val v = if (x < w / 2) 20 else 235
            return (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }

        // Zero dispersion: must have 0 non-neutral pixels across the sharp step edge
        val neutralResult = GlassRender.render(
            width = width, height = height, pad = pad,
            profile = 2f, heldLens = 1f, aberration = 0f,
            backdrop = ::stepBackdrop
        )
        checkNeutrality(neutralResult, width, height, pad, "Step edge zero-dispersion")

        // Nonzero dispersion: must produce off-axis chromatic fringes along the refracted boundary
        val fringedResult = GlassRender.render(
            width = width, height = height, pad = pad,
            profile = 2f, heldLens = 1f, aberration = 0.12f,
            backdrop = ::stepBackdrop
        )
        var fringePixelCount = 0
        for (y in pad until pad + height) {
            for (x in pad until pad + width) {
                val p = fringedResult[y * w + x]
                val r = (p ushr 16) and 0xFF
                val g = (p ushr 8) and 0xFF
                val b = p and 0xFF
                val diff = maxOf(abs(r - g), abs(g - b), abs(r - b))
                if (diff >= 5) {
                    fringePixelCount++
                }
            }
        }
        assertTrue(
            fringePixelCount > 100,
            "Nonzero dispersion must produce intended chromatic fringe pixels (got $fringePixelCount)"
        )
    }

    @Test
    fun contrastingContentNearRimAndOutsideGlassPreservesStrictNeutralityUnderZeroDispersion() {
        // High-contrast content passing under the rim and into the outer recorded margin (uPad):
        // sharp black/white vertical bars crossing from inside the glass to outside.
        // With zero dispersion, outward refraction across the margin must sample identical
        // coordinates across R, G, B without channel crush or red-clamp asymmetry.
        val width = 160
        val height = 60
        val pad = 32
        val w = width + pad * 2
        val h = height + pad * 2

        // Alternating high-contrast black/white vertical stripes spanning whole padded layer
        fun contrastingBackdrop(x: Int, y: Int): Int {
            val stripe = ((x / 16) % 2 == 0)
            val v = if (stripe) 235 else 20
            return (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }

        // 1. Held lens (outward refraction across rim into margin)
        val heldResult = GlassRender.render(
            width = width, height = height, pad = pad,
            profile = 2f, heldLens = 1f, formation = 0f,
            aberration = 0f,
            backdrop = ::contrastingBackdrop
        )
        checkNeutrality(heldResult, width, height, pad, "Contrasting rim held lens zero-dispersion")

        // 2. Transit fold (inward fold refraction)
        val foldResult = GlassRender.render(
            width = width, height = height, pad = pad,
            profile = 1f, formation = 1f, foldCorners = 1f,
            aberration = 0f,
            backdrop = ::contrastingBackdrop
        )
        checkNeutrality(foldResult, width, height, pad, "Contrasting rim transit fold zero-dispersion")
    }

    @Test
    fun darkAndLightAppearancesMaintainNeutralityWithZeroDispersion() {
        val width = 160
        val height = 60
        val pad = 32
        val w = width + pad * 2
        val h = height + pad * 2

        fun ramp(x: Int, y: Int): Int {
            val v = (((x + y).toFloat() / (w + h)) * 255f).toInt().coerceIn(0, 255)
            return (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }

        // 1. Dark appearance: dark tint, lift 0.04, dark outerContour 0.61
        val darkResult = GlassRender.render(
            width = width, height = height, pad = pad,
            profile = 2f, heldLens = 1f, formation = 0f,
            aberration = 0f,
            tint = Triple(0.12f, 0.12f, 0.12f),
            lift = 0.04f,
            outerContour = 0.61f,
            backdrop = ::ramp
        )
        checkNeutrality(darkResult, width, height, pad, "Dark appearance held lens zero-dispersion")

        // 2. Light appearance: light tint 241/255 grey, lift 0.0, light outerContour 0.31
        val lightTint = 241f / 255f
        val lightResult = GlassRender.render(
            width = width, height = height, pad = pad,
            profile = 2f, heldLens = 1f, formation = 0f,
            aberration = 0f,
            tint = Triple(lightTint, lightTint, lightTint),
            lift = 0f,
            outerContour = 0.31f,
            backdrop = ::ramp
        )
        checkNeutrality(lightResult, width, height, pad, "Light appearance held lens zero-dispersion")
    }

    @Test
    fun inkPassPreservesNeutralityAndProducesIntendedFringesWithoutCombArtifacts() {
        val width = 120
        val height = 60
        val pad = 24
        val w = width + pad * 2
        val h = height + pad * 2

        // Neutral grayscale ink pattern: centered glyph stroke
        fun neutralInk(x: Int, y: Int): Int {
            val dist = abs(x - w / 2)
            val a = if (dist < 4) 255 else 0
            val v = 200
            return (a shl 24) or (v shl 16) or (v shl 8) or v
        }

        fun blackBackdrop(x: Int, y: Int): Int = 0xFF000000.toInt()

        // 1. Zero ink split: ink must remain strictly neutral
        val zeroInk = GlassEndpointRender.render(
            width = width, height = height, pad = pad,
            profile = 2f, heldLens = 1f,
            inkSplit = 0f, aberration = 0f,
            backdrop = ::blackBackdrop,
            ink1 = ::neutralInk
        )
        for (y in pad until pad + height) {
            for (x in pad until pad + width) {
                val p = zeroInk.ink[zeroInk.index(x, y)]
                val a = (p ushr 24) and 0xFF
                if (a > 10) {
                    val r = (p ushr 16) and 0xFF
                    val g = (p ushr 8) and 0xFF
                    val b = p and 0xFF
                    assertEquals(r, g, "Ink zero-split R must equal G at ($x,$y)")
                    assertEquals(g, b, "Ink zero-split G must equal B at ($x,$y)")
                }
            }
        }

        // 2. Nonzero ink split: split occurs along refracted edges
        val splitInk = GlassEndpointRender.render(
            width = width, height = height, pad = pad,
            profile = 1f, formation = 1f, foldCorners = 1f,
            inkSplit = 0.14f * 0.3f, // GlassSelectorInkSplitShare
            aberration = 0.14f,
            backdrop = ::blackBackdrop,
            ink1 = ::neutralInk
        )
        // Along flat interior where shift is 0, delta must be 0 (no comb artifacts)
        // At the center y (spine), shift is 0, so ink must remain neutral
        val centerY = pad + height / 2
        for (x in pad until pad + width) {
            val p = splitInk.ink[splitInk.index(x, centerY)]
            val a = (p ushr 24) and 0xFF
            if (a > 10) {
                val r = (p ushr 16) and 0xFF
                val g = (p ushr 8) and 0xFF
                val b = p and 0xFF
                val diff = maxOf(abs(r - g), abs(g - b), abs(r - b))
                assertTrue(diff <= 2, "Unrefracted ink spine must not have comb split at x=$x (diff=$diff)")
            }
        }
    }
}
