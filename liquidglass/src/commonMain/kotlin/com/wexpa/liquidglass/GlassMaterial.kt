package com.wexpa.liquidglass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.exp
import kotlin.math.pow

/**
 * The measured Liquid Glass material, as formulas.
 *
 * Every number here was fitted from calibration captures of iOS 27 on an iPhone 13
 * (`docs/research/measured-model.md`; the raw record is `ios27-measurements-raw.md`). The
 * captures were made through a known test image, so each value is a measurement of the real
 * material rather than a tuning. Units are dp, which is pt on the device the numbers came from.
 *
 * Two families exist. **In-app glass** — a navigation pill, round buttons, a toolbar — follows
 * the app's colour scheme and the user's Tint Amount slider. **Shell glass** — icons, dock,
 * Control Center, App Library, cards — takes its tone from the wallpaper and ignores light/dark
 * entirely. The factories on [GlassStyle.Companion] build both.
 */
object GlassMaterial {

    /** The Tint Amount slider's default position. */
    const val DEFAULT_TINT_AMOUNT: Float = 50f

    /** Bevel width as a fraction of the corner radius: 0.55 ± 0.11 from icons to the sheet. */
    const val BEVEL_RATIO: Float = 0.6f

    /**
     * Sigma of the fine kernel, the blur the detail seen through the glass actually gets, in
     * dp: 0.5 at Clear, 0.9 at the default, 1.6 at Tinted, from a pixel-level fit of the pill.
     */
    fun fineKernel(tintAmount: Float = DEFAULT_TINT_AMOUNT): Dp {
        val u = (tintAmount / 100f).coerceIn(0f, 1f)
        return (0.5f + 1.5f * u * u).dp
    }

    /**
     * Sigma of the wide kernel that decides the tone: 10 dp, from a pixel-level fit of the pill
     * over the test image at every tint (the earlier edge-spread estimate of 25 dp was window
     * limited and is superseded).
     */
    val wideKernel: Dp = 10.dp

    /**
     * How far the 16 November lab fit (#199, `tools/glass_model.py`) applies at a Tint Amount: 1 at
     * the default, where it was measured, falling to 0 at 0 and 100, where the earlier fit stays until
     * those tints are re-measured (#208). The blend between is authored.
     */
    internal fun labWeight(tintAmount: Float): Float =
        (1f - kotlin.math.abs(tintAmount.coerceIn(0f, 100f) - DEFAULT_TINT_AMOUNT) / 50f).coerceIn(0f, 1f)

    /** Lab fit, Tint 50: sigma of the wide kernel B, 46.5 px at 3x (the fit is flat from 44 to 52 px). */
    internal val labWideKernel: Dp = 15.5.dp

    /**
     * Lab fit, Tint 50: the reach of the detail copy D, 1 px at 3x (measured at 3x only), drawn as four
     * taps at that reach (the shader's detailAt): 2 px stripes vanish, 4, 6 and 8 px keep 50, 75 and 85 %.
     */
    internal val labDetailReach: Dp = (1f / 3f).dp

    /**
     * Whether [reach] takes the four-tap detail kernel (`uDetailCross`): above zero and at most [labDetailReach],
     * with 0.1 % slack because a Dp lerp between two equal reaches can land one float step above (884 of 10,001
     * morph fractions at 1/3 dp), which would flip the kernel for those frames.
     */
    internal fun usesDetailCross(reach: Dp): Boolean = reach.value > 0f && reach.value <= labDetailReach.value * 1.001f

    /** Lab fit, Tint 50: the detail share k (dark 0.277, light 0.273). */
    internal fun labDetailShare(dark: Boolean): Float = if (dark) 0.277f else 0.273f

    /** Lab fit, Tint 50: the one-sided term c, +0.22 on light glass, -0.22 on dark. */
    internal fun labDetailLift(dark: Boolean): Float = if (dark) -0.22f else 0.22f

    /**
     * Lab fit, Tint 50: the tone over flat grey as a blend toward the tint colour plus a lift and a bow.
     * Dark: 31.72 + 254.40 x - 101.01 x^2 (rms 0.22, at most 0.42 levels from the 17 measured points),
     * so black at 0.3985, lift 31.72/255, bow 101.01/255. Light: 132.5 + 0.502 in, so white at 0.498
     * and lift 5.5/255, clipping at 255 from about 242.
     */
    internal fun labOpacity(dark: Boolean): Float = if (dark) 0.3985f else 0.498f
    internal fun labLift(dark: Boolean): Float = if (dark) 31.72f / 255f else 5.5f / 255f
    internal fun labToneCurve(dark: Boolean): Float = if (dark) 101.01f / 255f else 0f

    /**
     * Menu panel (#204), fitted on iOS 27's Safari `<select>` popup in `M-select-{dark,light}` and nothing else
     * (video, HDR10 decoded, 17 flat greys and 12 stripe cases per appearance; panel interior only, no rim,
     * shadow or corner). `lin = B`, then the tone: a pure blur with no detail term, where Regular has
     * `lin = B + k (D - B) + c |D - B|` with k 0.277. Sigma of B: dark 29.7 px = 9.9 dp, light 35.4 px = 11.8 dp
     * (Regular: 15.5 dp). The fitted k (0.018 dark, 0.021 light) and c (-0.019, +0.037) are within noise and are
     * set to exactly 0 ([GlassStyle.menu] has `fineShare` and `detailLift` 0).
     */
    internal fun menuWideKernel(dark: Boolean): Dp = if (dark) 9.9.dp else 11.8.dp

    /**
     * Menu panel (#204), the tone over flat grey in the same form as [labOpacity], [labLift] and [labToneCurve]:
     * dark `29.59 + 248.85 x - 155.87 x^2` (rms 1.01 levels over the 17 measured greys), light
     * `135.06 + 159.10 x - 35.25 x^2` (rms 1.38, clipping at 255 from about in 232). Dark: black at 0.6354, lift
     * 29.59/255, bow 155.87/255. Light: white at 0.5143, lift 3.91/255, bow 35.25/255. The dark bow is 1.54 times
     * Regular's: output peaks at 129 near in 200 and falls to 122 at white.
     */
    internal fun menuOpacity(dark: Boolean): Float = if (dark) 0.6354f else 0.5143f
    internal fun menuLift(dark: Boolean): Float = if (dark) 29.59f / 255f else 3.91f / 255f
    internal fun menuToneCurve(dark: Boolean): Float = if (dark) 155.87f / 255f else 35.25f / 255f

    /**
     * Lab fit, Tint 50 (#201): the colour transfer over flat colour, `tone(y) + (g0 + g1 y) (x - y)` for each channel
     * x about the Rec. 709 luma y of the sRGB code values (0-1), then clipped; g0 and g1 per channel (R, G, B). Fitted
     * on the 125-point RGB cube of the lab stills, converted once from Display P3 to sRGB and clipped to the sRGB
     * gamut: dark 0.99 levels rms, at most 7.1; light 0.83 rms, at most 5.3. Greys are unchanged.
     */
    internal fun labChromaGain(dark: Boolean): FloatArray =
        if (dark) floatArrayOf(1.317f, 1.256f, 1.314f) else floatArrayOf(0.988f, 0.952f, 0.979f)
    internal fun labChromaSlope(dark: Boolean): FloatArray =
        if (dark) floatArrayOf(-0.427f, -0.351f, -0.429f) else floatArrayOf(-0.076f, 0.028f, -0.045f)

    /**
     * The colour transfer's uniforms for a style: g0 (R, G, B), g1 (R, G, B) and the weight (`uChromaGain`,
     * `uChromaSlope`, `uChromaWeight`). Derived from [GlassStyle.detailLift], so no new style field is needed: the
     * sign picks the appearance and |c| / 0.22 is the lab weight, which [GlassStyle.inApp] and [GlassStyle.toolbar]
     * set as `labDetailLift(dark) * labWeight(tint)`. Zero lift, as on every style without the lab material (the tab
     * bar's earlier fit among them), leaves the per-channel tone alone.
     */
    internal fun chromaOf(detailLift: Float): FloatArray {
        val dark = detailLift < 0f
        val weight = (kotlin.math.abs(detailLift) / kotlin.math.abs(labDetailLift(dark))).coerceIn(0f, 1f)
        if (weight <= 0f) return FloatArray(7)
        return labChromaGain(dark) + labChromaSlope(dark) + floatArrayOf(weight)
    }

    /**
     * The colour transfer a style asks for: [chromaOf] of its [GlassStyle.detailLift] when [GlassStyle.chromaGain] is
     * unset (NaN), else the flat output-referred gain g on all three channels with no slope and full weight, the shader
     * then leaving the finished colour at g times the backdrop's chroma about its luma.
     */
    internal fun chromaOf(detailLift: Float, chromaGain: Float): FloatArray {
        if (chromaGain.isNaN()) return chromaOf(detailLift)
        val g = chromaGain.coerceIn(0f, MAX_CHROMA_GAIN)
        return floatArrayOf(g, g, g, 0f, 0f, 0f, 1f)
    }

    /** The largest [GlassStyle.chromaGain] the host passes on, authored as a guard (the measured Control Center value is 1.58). */
    internal const val MAX_CHROMA_GAIN: Float = 4f

    /** The light in-app tint colour: a constant 241/255 at every slider position. */
    val lightTint: Color = Color(0xFFF1F1F1)

    /** The dark in-app material is black at [opacity] plus this fixed lift, 35/255. */
    const val DARK_LIFT: Float = 35f / 255f

    /**
     * Tint opacity `a(t)`, from the pill's flat-black and flat-white levels: `a = 1 - (white -
     * black) / 255`. Rises from ~0.43 at Clear to ~0.73 at Tinted; light rises early, dark late.
     * Interpolated between the measured slider positions rather than fitted, because a
     * quadratic overshoots the dark curve by 0.02 at the default, which is 5 levels over white.
     */
    fun opacity(dark: Boolean, tintAmount: Float = DEFAULT_TINT_AMOUNT): Float =
        interpolate(if (dark) DARK_OPACITY else LIGHT_OPACITY, tintAmount)

    /** Linear interpolation in a flat (tint, value, tint, value, ...) table, clamped at its ends. */
    private fun interpolate(table: FloatArray, tintAmount: Float): Float {
        val t = tintAmount.coerceIn(0f, 100f)
        var i = 0
        while (i < table.size - 4 && table[i + 2] <= t) i += 2
        val t0 = table[i]; val a0 = table[i + 1]; val t1 = table[i + 2]; val a1 = table[i + 3]
        val f = if (t1 > t0) ((t - t0) / (t1 - t0)).coerceIn(0f, 1f) else 0f
        return a0 + (a1 - a0) * f
    }

    /** Measured (tint, opacity) pairs, light appearance, verified slider positions. */
    private val LIGHT_OPACITY = floatArrayOf(0f, 0.435f, 31f, 0.514f, 50f, 0.565f, 62f, 0.600f, 100f, 0.733f)

    /** Measured (tint, opacity) pairs, dark appearance. */
    private val DARK_OPACITY = floatArrayOf(0f, 0.416f, 29f, 0.439f, 50f, 0.451f, 62f, 0.510f, 86f, 0.655f, 100f, 0.737f)

    /** The light toolbar's tint colour: white, where the pill's is 241/255. */
    val toolbarLightTint: Color = Color.White

    /**
     * Tint opacity of the toolbar role, `a(t)`. The toolbar capsule of the reference stills sits
     * over 16 px stripes only, so its medians over black and over white stripes fix two numbers
     * per slider position; with the kernels held at the pill's they fix opacity and tint colour
     * (light) or opacity and lift (dark). Light: white at 0.55 rising to 0.67 — more opaque
     * than the pill at Clear, less at Tinted. Dark: black at 0.55 at Clear, 0.41 at the default
     * and 0.63 at Tinted over the same 35/255 lift as the pill; the dip is what the medians say
     * (the toolbar brightens toward the default while the pill does not). Both reproduce the
     * phone within 2 levels at every verified position; see `docs/research/measured-model.md`,
     * section 2.
     */
    fun toolbarOpacity(dark: Boolean, tintAmount: Float = DEFAULT_TINT_AMOUNT): Float =
        interpolate(if (dark) DARK_TOOLBAR_OPACITY else LIGHT_TOOLBAR_OPACITY, tintAmount)

    /** Solved (tint, opacity) pairs for the light toolbar, white tint. */
    private val LIGHT_TOOLBAR_OPACITY = floatArrayOf(0f, 0.552f, 31f, 0.570f, 50f, 0.579f, 62f, 0.595f, 100f, 0.667f)

    /** Solved (tint, opacity) pairs for the dark toolbar, black over the 35/255 lift. */
    private val DARK_TOOLBAR_OPACITY = floatArrayOf(0f, 0.550f, 29f, 0.469f, 35f, 0.452f, 50f, 0.408f, 62f, 0.452f, 86f, 0.567f, 100f, 0.626f)

    /**
     * Share of fine detail in the backdrop term, `w = 0.95 (1 - t/100)^1.5`: 0.95 at Clear, 0.34
     * at the default, 0 at Tinted. The pixel-level pill fit gives 0.75-0.85 at Clear and the
     * toolbar's stripes 0.8-1.0; the curve sits with the toolbar there and with both at the
     * default. Same in both appearances.
     */
    fun fineShare(tintAmount: Float = DEFAULT_TINT_AMOUNT): Float =
        (0.95f * (1f - (tintAmount / 100f).coerceIn(0f, 1f)).pow(1.5f)).coerceIn(0f, 1f)

    /**
     * Gaussian sigma of the system backdrop blur behind Control Center, App Library and the
     * Today view: 7.7 dp up to the default, rising to 11 dp at Tinted.
     */
    fun systemBackdropSigma(tintAmount: Float = DEFAULT_TINT_AMOUNT): Dp {
        val u = (tintAmount / 100f).coerceIn(0f, 1f)
        return (7.7f + 3.3f * u.pow(3)).dp
    }

    /**
     * The dim applied to app content below a cover sheet's edge as a function of how far the
     * sheet has been pulled, in dp: `1 - 0.311 (1 - e^(-pull / 150 dp))`, saturating at 0.706.
     * Identical at every Tint Amount. Multiply the content by it, or draw black at 1 minus it.
     */
    fun coverSheetDim(pull: Dp): Float {
        val x = pull.value.coerceAtLeast(0f)
        return 1f - 0.311f * (1f - exp(-x / 150f))
    }

    /**
     * The measured edge lens is not a fixed property of an edge: it forms while a finger drags
     * the surface past about 117 dp and is absent on a committed animation. This maps pull
     * distance to a formation amount for `lensFormation`; hosts that animate a release should
     * drive it back to 0 themselves.
     */
    fun lensFormation(trackedPull: Dp): Float =
        ((trackedPull.value - 80f) / 120f).coerceIn(0f, 1f)
}
