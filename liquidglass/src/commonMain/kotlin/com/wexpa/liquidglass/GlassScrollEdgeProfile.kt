package com.wexpa.liquidglass

import androidx.compose.ui.graphics.Color

/**
 * The scroll edge effect's numbers (#207), measured on iOS 27 Safari's top edge under the status bar
 * (`Scr-safari-scroll-{dark,light}`, iPhone, 3 px per point, 60 fps, HDR10 decoded). Distances are dp from the
 * edge the content scrolls under. Each constant says whether it is measured, fitted or authored.
 *
 * - Tint, dark appearance (measured): a blend toward grey 124 at alpha 0.41, flat over the first 10 dp and gone
 *   by 34 dp, `0.41 (1 - smoothstep(10, 34, d))` (0.8 level rms against the measured median profile over 427
 *   frames; the earlier quadratic ramp missed by 8). The same grey for red, green, purple and blue content.
 * - Tint, light appearance (measured): none. Tile colours moved by 1 to 3 levels with no consistent sign.
 * - Blur (measured, both appearances): needed. Edges stay smeared to the end of the band, so a fade or a tint
 *   alone leaves text sharp. The measure is the rms edge width over +-10 rows around isolated strong edges:
 *   5.7 px at 5 dp, 5.2 at 18, 4.4 at 22, 3.9 at 25, 3.0 at 28, 1.5 at 32, 0.4 at 35 (3x pixels). Inside +-10 rows
 *   the estimator saturates at 5.8 px, so the values above 20 dp are lower bounds; the kernel's shape and sigma
 *   there are not measured.
 * - Blur masks (fitted, to those widths): two blurred copies of the content, sigma 3.33 dp (A) and 1 dp (B), laid
 *   over the sharp content with the masks below, give the measured edge widths at 22, 25, 28, 32 and 35 dp. A is
 *   at full strength to 5 dp, B to 28 dp.
 * - Intensity ramp, 24 dp of scroll (authored) and the bottom edge, a mirror of the top (authored): neither was
 *   measurable (the take's bottom edge is under the glass toolbar; at rest the page header hides the effect).
 */
internal object GlassScrollEdgeProfile {
    /** Measured: the dark tint's strength at the edge. */
    const val DarkTintAlpha = 0.41f
    /** Measured: the grey the dark tint blends toward (display sRGB). */
    val DarkTintColor: Color = Color(124, 124, 124)
    /** Measured: the dark tint is flat to here and gone at [TintEndDp]. */
    const val TintFlatDp = 10f
    const val TintEndDp = 34f
    /** Measured: the blur is gone by here (0.4 px edge width at 35 dp). */
    const val BandDp = 35f
    /** Fitted: sigma of the two blurred copies, in dp. */
    const val SigmaADp = 10f / 3f
    const val SigmaBDp = 1f
    /** Authored: scroll distance over which the effect fades in, in dp. */
    const val RampDp = 24f
    /** Authored, the earlier composable's: the Hard style's uniform strength, light and dark. */
    const val HardAlpha = 0.90f
    const val HardAlphaInverted = 0.18f

    private fun smoothstep(a: Float, b: Float, x: Float): Float {
        val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** The dark tint's alpha at [dp] from the edge, for a band scaled by [scale] (1 is the measured band). */
    fun tintAlpha(dp: Float, scale: Float = 1f): Float = DarkTintAlpha * (1f - smoothstep(TintFlatDp * scale, TintEndDp * scale, dp))

    private fun piecewise(x: Float, knots: FloatArray): Float {
        if (x <= knots[0]) return knots[1]
        var i = 0
        while (i + 2 < knots.size && knots[i + 2] < x) i += 2
        if (i + 2 >= knots.size) return knots[knots.size - 1]
        val t = (x - knots[i]) / (knots[i + 2] - knots[i])
        return knots[i + 1] + (knots[i + 3] - knots[i + 1]) * t
    }

    /** Position (dp), strength pairs, fitted: copy A's mask, from full strength to none by 28.3 dp. */
    private val maskA = floatArrayOf(0f, 1f, 5f, 1f, 15f, .957f, 18.33f, .848f, 21.67f, .543f, 25f, .347f, 28.33f, 0f)
    /** Copy B's mask: full to 28.3 dp, then to none by 35 dp. */
    private val maskB = floatArrayOf(0f, 1f, 28.33f, .998f, 31.67f, .228f, 35f, 0f)

    fun maskA(dp: Float, scale: Float = 1f): Float = piecewise(dp / scale, maskA)
    fun maskB(dp: Float, scale: Float = 1f): Float = piecewise(dp / scale, maskB)

    /** The weights the three layers (sharp, B, A) end with at [dp]: A's mask, then B's under it, then the rest sharp. */
    fun weights(dp: Float, scale: Float = 1f): Triple<Float, Float, Float> {
        val a = maskA(dp, scale)
        val b = (1f - a) * maskB(dp, scale)
        return Triple(1f - a - b, b, a)
    }

    /** The unit-variance-weighted edge width the weights give, for tests: sqrt of the mix's variance in dp. */
    fun mixSigmaDp(dp: Float, scale: Float = 1f): Float {
        val (_, b, a) = weights(dp, scale)
        return kotlin.math.sqrt(a * SigmaADp * SigmaADp + b * SigmaBDp * SigmaBDp)
    }
}
