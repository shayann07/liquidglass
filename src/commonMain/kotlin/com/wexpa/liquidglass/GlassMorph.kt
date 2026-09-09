package com.wexpa.liquidglass

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
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
    fun f(a: Float, b: Float) = a + (b - a) * t
    fun d(a: Dp, b: Dp) = lerp(a, b, t)
    return GlassStyle(
        blurRadius = d(start.blurRadius, stop.blurRadius),
        backdropBlur = d(start.backdropBlur, stop.backdropBlur),
        refractionBand = d(start.refractionBand, stop.refractionBand),
        refractionDepth = d(start.refractionDepth, stop.refractionDepth),
        indexOfRefraction = f(start.indexOfRefraction, stop.indexOfRefraction),
        bevelPower = f(start.bevelPower, stop.bevelPower),
        cornerPower = f(start.cornerPower, stop.cornerPower),
        dispersion = f(start.dispersion, stop.dispersion),
        mirror = f(start.mirror, stop.mirror),
        bevel = d(start.bevel, stop.bevel),
        tint = lerp(start.tint, stop.tint, t),
        adaptivity = f(start.adaptivity, stop.adaptivity),
        legibility = f(start.legibility, stop.legibility),
        specular = f(start.specular, stop.specular),
        specularPower = f(start.specularPower, stop.specularPower),
        fresnel = f(start.fresnel, stop.fresnel),
        innerShadow = f(start.innerShadow, stop.innerShadow),
        // Not interpolated: these are decisions, not quantities. A half-inverted element or a
        // half-opaque fallback is not a state anything wants to be in, so they snap at the
        // midpoint with the rest of the identity.
        fallbackSurface = if (t < 0.5f) start.fallbackSurface else stop.fallbackSurface,
        invertsWithBackdrop = if (t < 0.5f) start.invertsWithBackdrop else stop.invertsWithBackdrop,
        dimmingLayer = f(start.dimmingLayer, stop.dimmingLayer),
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
