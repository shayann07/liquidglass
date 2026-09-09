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
    /**
     * Radius of the interior blur, which tapers to nothing at the rim so the edge stays sharp.
     *
     * This is the blur that keeps the material readable as glass: the rim goes on carrying a
     * crisp, compressed image of what lies just outside the element while the middle softens.
     * It is done with a rotating tap disc inside the shader, so it is cheap but slightly
     * grainy at large radii. Prefer it wherever the backdrop is worth looking at.
     */
    val blurRadius: Dp = 10.dp,
    /**
     * Radius of a blur applied to the backdrop before the shader ever sees it.
     *
     * Higher quality than [blurRadius] and much better at destroying text, but it also
     * destroys the detail the rim is supposed to compress, so the signature edge goes with it.
     * That is the right trade for chrome floating over an app's own content and the wrong one
     * for a surface meant to show its backdrop. The two can be combined; usually only one is
     * wanted at a time.
     */
    val backdropBlur: Dp = 0.dp,
    /** Width of the band, measured in from the rim, over which refraction acts. */
    val refractionBand: Dp = 26.dp,
    /** Peak displacement of the sampled backdrop at the rim. */
    val refractionDepth: Dp = 22.dp,
    /**
     * How far red and blue split from green at the rim, as a fraction of the displacement.
     * Small on purpose: this is a fringe, and past a few percent it stops reading as glass
     * and starts reading as a broken colour channel.
     */
    val dispersion: Float = 0.06f,
    /** Width of the lit bevel. */
    val bevel: Dp = 4.dp,
    /** Tint colour; its alpha is the strength. */
    val tint: Color = Color.White.copy(alpha = 0.06f),
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
    val innerShadow: Float = 0.07f,
    /**
     * The surface painted under the material when there is no backdrop to sample.
     *
     * A panel carrying labels has to stay legible. With a backdrop the tint alone is enough,
     * because the backdrop arrives blurred; without one, a 10% tint over live content leaves
     * the labels unreadable. Supply the colour that suits the app's ground — the default suits
     * a dark one.
     */
    val fallbackSurface: Color = Color(0xF2141416),
    /**
     * Whether this element may invert light/dark with its backdrop.
     *
     * Apple makes the inversion a whole-element decision gated by size: small chrome — tab
     * bars, nav bars — flips to hold contrast, while large surfaces adapt without flipping,
     * because a flip across that much area is distracting. Only elements that read as small
     * actually invert; see `elementSizeFactor`.
     */
    val invertsWithBackdrop: Boolean = true,
    /**
     * Opacity of the dimming layer drawn beneath the material.
     *
     * The regular variant carries its own legibility through blur and backdrop-luminosity
     * adjustment. The clear variant has no adaptation at all, so Apple's guidance is to put a
     * dimming layer under it — the one number the HIG actually gives, at 35%.
     */
    val dimmingLayer: Float = 0f,
) {
    companion object {
        /** Carries controls. Tinted enough to keep content on top legible. */
        val Regular = GlassStyle()

        /**
         * Floating chrome: tab bars, toolbars, accessory pills, over an app's own content.
         *
         * The other presets assume a backdrop worth refracting. Chrome usually floats over a
         * plain ground carrying sparse, high-contrast text, where there is almost nothing to
         * bend and the little there is arrives half-legible, smeared along the rim and sitting
         * under the labels. So this trades the signature for legibility: a shallow bend over a
         * narrow band, and a real [backdropBlur] rather than the interior one, which costs the
         * crisp rim but makes what comes through unreadable instead of distracting.
         */
        val Chrome = GlassStyle(
            blurRadius = 0.dp,
            backdropBlur = 14.dp,
            refractionBand = 14.dp,
            refractionDepth = 9.dp,
            dispersion = 0.03f,
            bevel = 3.dp,
            tint = Color.White.copy(alpha = 0.11f),
            adaptivity = 0.9f,
            specular = 0.5f,
            innerShadow = 0.06f,
        )


        /** Thinner and more transparent, for chrome that should mostly disappear. */
        val Clear = GlassStyle(
            blurRadius = 4.dp,
            refractionBand = 30.dp,
            refractionDepth = 26.dp,
            dispersion = 0.09f,
            bevel = 3.dp,
            tint = Color.White.copy(alpha = 0.03f),
            adaptivity = 0.35f,
            specular = 0.7f,
            innerShadow = 0.05f,
            // The clear variant does not adapt, so it carries the HIG's 35% dimming layer.
            dimmingLayer = 0.35f,
        )

        /** Heavier: a sheet or a dialog that must hold a lot of content. */
        val Thick = GlassStyle(
            blurRadius = 16.dp,
            refractionBand = 22.dp,
            refractionDepth = 16.dp,
            dispersion = 0.04f,
            bevel = 5.dp,
            tint = Color.White.copy(alpha = 0.13f),
            adaptivity = 0.8f,
            specular = 0.45f,
            innerShadow = 0.10f,
            // A sheet is a large surface: it adapts to its backdrop but never flips polarity.
            invertsWithBackdrop = false,
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
