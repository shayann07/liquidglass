package com.wexpa.liquidglass

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.lerp

/**
 * Blends two styles, for an element morphing from one role into another.
 *
 * The reason this exists rather than "just animate the size": Apple states that the material's
 * characteristics change *with* size — deeper shadows, more pronounced lensing, softer
 * scattering as an element grows. So a 48dp button becoming a 280dp menu is not one shape
 * growing, it is a `Regular` becoming something nearer `Thick`, and the numbers have to travel
 * with the geometry.
 *
 * The observable failure is specific and worth knowing: hold the edge parameters constant while
 * the shape grows and the rim stays exactly as wide as it was, which reads as a picture of glass
 * being stretched rather than as a piece of glass getting bigger.
 *
 * `uScale` is handled separately and needs no help here — it is derived from the measured size
 * every frame, so it follows the geometry on its own.
 */
fun lerpGlassStyle(start: GlassStyle, stop: GlassStyle, fraction: Float): GlassStyle {
    val t = fraction.coerceIn(0f, 1f)
    // Exact at both ends, so a morph that arrives lands on the stop style bit for bit.
    fun f(a: Float, b: Float) = if (t <= 0f) a else if (t >= 1f) b else a + (b - a) * t
    // An unspecified band or sigma means "derived", which cannot be interpolated; it snaps with
    // the rest of the identity at the midpoint.
    fun d(a: Dp, b: Dp) = if (a.isUnspecified || b.isUnspecified) (if (t < 0.5f) a else b) else lerp(a, b, t)
    // An unspecified ink split means "follow dispersion", which is an identity rather than a
    // quantity, so it snaps with the rest of the identity instead of interpolating into NaN.
    fun s(a: Float, b: Float) = if (a.isNaN() || b.isNaN()) (if (t < 0.5f) a else b) else f(a, b)
    return GlassStyle(
        profile = if (t < 0.5f) start.profile else stop.profile,
        restMap = if (t < 0.5f) start.restMap else stop.restMap,
        blurRadius = d(start.blurRadius, stop.blurRadius),
        backdropBlur = d(start.backdropBlur, stop.backdropBlur),
        backdropSigma = d(start.backdropSigma, stop.backdropSigma),
        refractionBand = d(start.refractionBand, stop.refractionBand),
        wideKernel = d(start.wideKernel, stop.wideKernel),
        fineShare = f(start.fineShare, stop.fineShare),
        tintLift = f(start.tintLift, stop.tintLift),
        liftAdaptivity = f(start.liftAdaptivity, stop.liftAdaptivity),
        refractionDepth = d(start.refractionDepth, stop.refractionDepth),
        indexOfRefraction = f(start.indexOfRefraction, stop.indexOfRefraction),
        bevelPower = f(start.bevelPower, stop.bevelPower),
        cornerPower = f(start.cornerPower, stop.cornerPower),
        dispersion = f(start.dispersion, stop.dispersion),
        heldLens = f(start.heldLens, stop.heldLens),
        heldMagnification = f(start.heldMagnification, stop.heldMagnification),
        heldGlow = f(start.heldGlow, stop.heldGlow),
        heldEdgeRecovery = d(start.heldEdgeRecovery, stop.heldEdgeRecovery),
        rawShare = f(start.rawShare, stop.rawShare),
        mirror = f(start.mirror, stop.mirror),
        bevel = d(start.bevel, stop.bevel),
        tint = lerpTintPremultiplied(start.tint, stop.tint, t),
        adaptivity = f(start.adaptivity, stop.adaptivity),
        legibility = f(start.legibility, stop.legibility),
        specular = f(start.specular, stop.specular),
        specularPower = f(start.specularPower, stop.specularPower),
        counterLight = f(start.counterLight, stop.counterLight),
        edgeShadow = f(start.edgeShadow, stop.edgeShadow),
        rimSoftness = d(start.rimSoftness, stop.rimSoftness),
        tintAbsorption = f(start.tintAbsorption, stop.tintAbsorption),
        edgeLight = f(start.edgeLight, stop.edgeLight),
        bevelPeak = f(start.bevelPeak, stop.bevelPeak),
        fresnel = f(start.fresnel, stop.fresnel),
        highlightChroma = f(start.highlightChroma, stop.highlightChroma),
        contactShadow = f(start.contactShadow, stop.contactShadow),
        innerShadow = f(start.innerShadow, stop.innerShadow),
        // Not interpolated: these are decisions, not quantities. A half-inverted element or a
        // half-opaque fallback is not a state anything wants to be in, so they snap at the
        // midpoint with the rest of the identity.
        fallbackSurface = if (t < 0.5f) start.fallbackSurface else stop.fallbackSurface,
        invertsWithBackdrop = if (t < 0.5f) start.invertsWithBackdrop else stop.invertsWithBackdrop,
        dimmingLayer = f(start.dimmingLayer, stop.dimmingLayer),
        // A fold strength is a quantity and interpolates; a <= 1/3 simply has no turning point,
        // so passing through it is continuous in the map as well as in the number.
        edgeFold = f(start.edgeFold, stop.edgeFold),
        inkDispersion = s(start.inkDispersion, stop.inkDispersion),
        // A decision, like the profile and the rest map beside it: it snaps at the midpoint. Two
        // styles a component morphs between must therefore agree on it, or the ink's source map
        // changes shape halfway through the morph. `GlassTabBarStyleTest` asserts that for the
        // presets this library ships.
        heldInkContinuous = if (t < 0.5f) start.heldInkContinuous else stop.heldInkContinuous,
    )
}

/**
 * A style that follows a morph, driven on the same clock as the geometry.
 *
 * Use the same [animationSpec] the layout uses for the size and radius, so the material cannot
 * desynchronise from the shape it belongs to — a rim that arrives after the geometry is more
 * obviously wrong than one that never moved.
 */
@Composable
fun animateGlassStyle(
    start: GlassStyle,
    stop: GlassStyle,
    morphed: Boolean,
    animationSpec: AnimationSpec<Float> = GlassMotion.Morph,
): GlassStyle {
    val fraction by animateFloatAsState(
        targetValue = if (morphed) 1f else 0f,
        animationSpec = animationSpec,
        label = "glass_style_morph",
    )
    return lerpGlassStyle(start, stop, fraction)
}

private fun lerp(start: Color, stop: Color, fraction: Float): Color =
    androidx.compose.ui.graphics.lerp(start, stop, fraction)

/**
 * The tint interpolated **premultiplied**, so what it does to the material is linear in [t].
 *
 * The resting pill is a black tint at alpha 0.143 and the held lens a white one at 0.01. Lerping
 * colour and alpha separately puts a mid-grey at alpha 0.077 at the midpoint, which darkens the
 * bar by almost nothing: the pill's darkness then arrives in the last third of the morph, and on
 * the phone a released lens stayed bright for 170 ms and went dark in the next 60 (FABLE run 2,
 * `human-FABLE-r5/regrab.mp4`), while the recording's lens darkens as it shrinks. Interpolating
 * the premultiplied colour keeps the darkening proportional to the formation.
 */
internal fun lerpTintPremultiplied(a: Color, b: Color, t: Float): Color {
    if (t <= 0f) return a
    if (t >= 1f) return b
    val aa = a.alpha
    val ba = b.alpha
    val alpha = aa + (ba - aa) * t
    if (alpha <= 1e-5f) return Color(b.red, b.green, b.blue, alpha)
    fun ch(x: Float, y: Float) = ((x * aa + (y * ba - x * aa) * t) / alpha).coerceIn(0f, 1f)
    return Color(ch(a.red, b.red), ch(a.green, b.green), ch(a.blue, b.blue), alpha)
}
