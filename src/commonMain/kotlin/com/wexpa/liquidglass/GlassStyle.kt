package com.wexpa.liquidglass

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The physical parameters of a piece of glass.
 *
 * These are the knobs the shader actually consumes. [Regular] and [Clear] mirror the two
 * variants Apple's material exposes: regular is the workhorse that carries controls and
 * leans on tint for legibility, clear is thinner and lets more of the backdrop through.
 */
@Immutable
data class GlassStyle(
    /** How far the backdrop is blurred before the glass samples it. */
    val blurRadius: Dp = 24.dp,
    /** Width of the band, measured in from the rim, over which refraction acts. */
    val refractionBand: Dp = 18.dp,
    /** Peak displacement of the sampled backdrop at the rim. */
    val refractionDepth: Dp = 12.dp,
    /** Width of the lit bevel. */
    val bevel: Dp = 6.dp,
    /** Tint colour; its alpha is the strength. */
    val tint: Color = Color.White.copy(alpha = 0.10f),
    /**
     * How strongly the tint reacts to the backdrop's brightness. At 0 the tint is fixed; at 1
     * the glass darkens over bright content and lifts over dark content, which is what keeps
     * whatever sits on the glass readable as the backdrop scrolls beneath it.
     */
    val adaptivity: Float = 0.65f,
    /** Rim highlight intensity. */
    val specular: Float = 0.55f,
    /** Rim highlight tightness. Higher is a narrower, harder glint. */
    val specularPower: Float = 6f,
    /** Strength of the dark inner line that reads as the thickness of the glass. */
    val innerShadow: Float = 0.16f,
    /**
     * The surface painted under the material when there is no backdrop to sample.
     *
     * A panel carrying labels has to stay legible. With a backdrop the tint alone is enough,
     * because the backdrop arrives blurred; without one, a 10% tint over live content leaves
     * the labels unreadable. Supply the colour that suits the app's ground — the default suits
     * a dark one.
     */
    val fallbackSurface: Color = Color(0xF2141416),
) {
    companion object {
        /** Carries controls. Tinted enough to keep content on top legible. */
        val Regular = GlassStyle()

        /** Thinner and more transparent, for chrome that should mostly disappear. */
        val Clear = GlassStyle(
            blurRadius = 16.dp,
            refractionBand = 22.dp,
            refractionDepth = 16.dp,
            tint = Color.White.copy(alpha = 0.05f),
            adaptivity = 0.35f,
            specular = 0.7f,
            innerShadow = 0.10f,
        )

        /** Heavier: a sheet or a dialog that must hold a lot of content. */
        val Thick = GlassStyle(
            blurRadius = 40.dp,
            refractionBand = 14.dp,
            refractionDepth = 9.dp,
            bevel = 8.dp,
            tint = Color.White.copy(alpha = 0.14f),
            adaptivity = 0.8f,
            specular = 0.45f,
            innerShadow = 0.20f,
        )
    }
}

/**
 * Where the light sits, as a unit vector in the panel's own space.
 *
 * `y` is negative upward, matching Compose's coordinate system, so the default is a light
 * above and slightly to the left — the direction a highlight has to come from for a surface
 * to read as convex rather than concave.
 */
@Immutable
data class GlassLight(val x: Float = -0.35f, val y: Float = -0.94f) {
    companion object {
        val Default = GlassLight()
    }
}
