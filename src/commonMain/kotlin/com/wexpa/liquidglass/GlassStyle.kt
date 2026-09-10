package com.wexpa.liquidglass

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The physical parameters of a piece of glass.
 *
 * These are the knobs the shader consumes. The presets mirror the variants Apple's material
 * exposes plus one of ours for floating chrome. Apple publishes no numeric rendering parameter
 * for any of this — the single number in the whole HIG is the clear variant's 35% dimming
 * layer — so every value here is ours, chosen against the gallery and against the one figure
 * anyone has published from a side-by-side comparison with a real device.
 */
@Immutable
data class GlassStyle(
    /**
     * Radius of the interior scatter, which tapers to nothing at the rim so the edge stays
     * sharp. This is the blur that keeps the material readable as glass: the rim goes on
     * carrying a crisp, compressed image of what lies just outside while the middle softens.
     * A rotating tap disc inside the shader, so it is cheap but grainy at large radii.
     */
    val blurRadius: Dp = 10.dp,
    /**
     * Radius of a blur applied to the backdrop before the shader sees it.
     *
     * Higher quality than [blurRadius] and much better at destroying text, but it also destroys
     * the detail the rim compresses, so the signature edge goes with it. That is the right
     * trade for chrome floating over an app's own content and the wrong one for a surface meant
     * to show its backdrop.
     */
    val backdropBlur: Dp = 0.dp,
    /** Width of the band, measured in from the rim, over which refraction acts. */
    val refractionBand: Dp = 26.dp,
    /**
     * Peak displacement of the sampled backdrop, reached at the rim.
     *
     * Published tuned values span 2.7x, from 9dp to 24dp on a ~48dp control. This sits at the
     * device-matched end, which is the only end anyone claims to have compared against a real
     * iOS 26 device, and which has a falsifiable failure mode at the other: over 17sp body text
     * a rim displacement much past 16dp leaves glyphs legible but smeared along the edge, which
     * reads as a rendering defect rather than as glass.
     */
    val refractionDepth: Dp = 14.dp,
    /**
     * Index of refraction. Because the bend is normalised by its own value at the rim, this
     * sets the *shape* of the falloff rather than its magnitude; 1.5 is window glass.
     */
    val indexOfRefraction: Float = 1.5f,
    /** Superellipse bevel exponent: 2 is a true circular arc, 4 the squircle-matched profile. */
    val bevelPower: Float = 2f,
    /**
     * Corner exponent of the *outline*, distinct from [bevelPower], which shapes the bevel.
     *
     * 2 is the circular arc `RoundedCornerShape` draws. 4 is the superellipse Apple actually
     * uses, and if the element is clipped to a matching [GlassSquircleShape] the material and
     * the clip agree; with a plain rounded rect the corner will read slightly fuller than the
     * clip, which is why this is not 4 by default.
     */
    val cornerPower: Float = 2f,
    /**
     * How far red and blue split from green at the rim, as a fraction of the displacement.
     * Faint on purpose: real UI glass shows almost no prismatic fringe, and past a few percent
     * this stops reading as glass and starts reading as a broken colour channel.
     */
    val dispersion: Float = 0.018f,
    /**
     * Amplitude of the mirrored edge band — the broad, soft, upside-down echo of nearby content
     * over roughly the outer third of the surface. A thin band reads as a hard streak; this one
     * is what makes the edge read as liquid rather than as a bevel.
     */
    val mirror: Float = 0.18f,
    /** Width of the lit bevel. The refraction band is four to seven times this. */
    val bevel: Dp = 4.dp,
    /** Tint colour; its alpha is the strength. */
    val tint: Color = Color.White.copy(alpha = 0.10f),
    /**
     * How strongly the tint reacts to the backdrop's brightness. At 0 the tint is fixed; at 1
     * the glass darkens over bright content and lifts over dark content, which is what keeps
     * whatever sits on the glass readable as the backdrop scrolls beneath it.
     */
    val adaptivity: Float = 0.65f,
    /**
     * How far local backdrop contrast raises the tint.
     *
     * Apple: as text scrolls underneath, the amount of tint shifts to keep controls legible.
     * The shader gets the signal for free, since its rim and interior samples already bracket
     * the backdrop's high frequencies.
     */
    val legibility: Float = 0.6f,
    /** Rim highlight intensity. */
    val specular: Float = 0.55f,
    /** Rim highlight tightness. Higher is a narrower, harder glint. */
    val specularPower: Float = 6f,
    /**
     * How much of the key light reaches the side of the bevel facing away from it, 0 to 1.
     *
     * A bead lit from one side only reads as a gradient, so there is always some. A free-standing
     * lens lit from above wants a little; a bar wants all of it — measured against the iOS 26
     * reference, a dark tab bar's top and bottom edges carry the same hairline (61 over a body
     * of 20 on both), and its straight sides carry none, which is exactly a light from directly
     * above reaching both faces equally. The counter-lobe tightens as it weakens, so the
     * historical 0.35 keeps the narrower lobe it was tuned with.
     */
    val counterLight: Float = 0.35f,
    /**
     * Brightness of the outermost line of the rim, relative to the bevel lobe.
     *
     * The two are different things and the reference separates them. Its resting bar is a
     * one-point hairline with almost no lobe behind it: a tiny [specular] and an [edgeLight]
     * well above 1. Its held lens is the opposite — a broad, soft lobe whose peak sits a few
     * points *inside* an edge that stays dark: a real [specular] over a wide [bevel] and an
     * [edgeLight] near 0. At 1 the two move together, which is how every preset was tuned.
     */
    val edgeLight: Float = 1f,
    /**
     * Where in the bevel the highlight peaks, as a fraction of [bevel] measured in from the edge.
     *
     * At 0 the bevel is a chamfer: brightest at the very edge and fading inward, which is how
     * every preset was tuned and what a hairline-edged bar wants. Above 0 it is a bead: the
     * outermost pixel stays dark and the highlight sits inside the edge, fading both ways. The
     * reference's held lens is a bead — its top rim rises from 16 to 110 over about three
     * points and falls back over three more, with the edge itself dark — so it sits near 0.4
     * over a bevel about six points wide.
     */
    val bevelPeak: Float = 0f,
    /** Schlick rim reflectance gain: how much the bevel brightens as it turns away. */
    val fresnel: Float = 0.10f,
    /** Strength of the dark inner line that reads as the thickness of the glass. */
    val innerShadow: Float = 0.07f,
    /**
     * The surface painted under the material when there is no backdrop to sample.
     *
     * A panel carrying labels has to stay legible. With a backdrop the tint alone is enough;
     * without one, a 10% tint over live content leaves the labels unreadable. Supply the colour
     * that suits the app's ground — the default suits a dark one.
     */
    val fallbackSurface: Color = Color(0xF2141416),
    /**
     * Whether this element may invert light/dark with its backdrop.
     *
     * Apple makes the inversion a whole-element decision gated by size: small chrome — tab
     * bars, nav bars — flips to hold contrast, while large surfaces adapt without flipping,
     * because a flip across that much area is distracting. The decision is the host's, not the
     * shader's, because symbols drawn on the glass have to flip in lockstep with it; see
     * [LiquidGlassState.inversion].
     */
    val invertsWithBackdrop: Boolean = true,
    /**
     * Opacity of the dimming layer drawn beneath the material.
     *
     * The regular variant carries its own legibility through scatter and tone mapping. The
     * clear variant has no adaptation at all, so Apple's guidance is to put a dimming layer
     * under it — at 35%, the one number the HIG actually gives.
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
            dispersion = 0.010f,
            mirror = 0.08f,
            bevel = 3.dp,
            tint = Color.White.copy(alpha = 0.11f),
            adaptivity = 0.90f,
            legibility = 0.9f,
            specular = 0.50f,
            fresnel = 0.08f,
            innerShadow = 0.06f,
        )

        /**
         * Floating chrome in a dark appearance, measured rather than tuned.
         *
         * Every number here comes from a frame-by-frame measurement of an iOS 26 tab bar in dark
         * mode (the Phone app, recorded at 720x1558; see `docs/research/reference-measurements.md`).
         * Over pure black the bar reads **20/255**, and the peaks of white text behind it come
         * through at **64%** of their own brightness. Two numbers, two unknowns: a tint of about
         * 0x37 at a strength of about 0.36 reproduces both. It is worth being precise about,
         * because the obvious reading — a dark tint at high alpha — passes only a quarter of
         * what is behind and turns the bar into a wall, which is the opposite of the material.
         * Its edge is a one-point
         * hairline at about twice the body's brightness on the top and bottom edges and absent
         * on the straight sides, with no dark inner line at all. The rim's refraction is shallow
         * and there is no visible dispersion at rest.
         *
         * The tint is a colour rather than an alpha, so it carries into [lerpGlassStyle] and
         * into the tone mapping like any other; the numbers are chosen so that
         * `tint.rgb x tint.alpha` reproduces the measured lift over black.
         */
        val DarkChrome = GlassStyle(
            blurRadius = 0.dp,
            backdropBlur = 3.dp,
            refractionBand = 12.dp,
            refractionDepth = 6.dp,
            dispersion = 0.010f,
            mirror = 0.05f,
            bevel = 1.2.dp,
            tint = Color(0xFF373737).copy(alpha = 0.36f),
            adaptivity = 0.4f,
            legibility = 0.5f,
            specular = 0.06f,
            specularPower = 4f,
            counterLight = 1f,
            edgeLight = 4.5f,
            fresnel = 0f,
            innerShadow = 0f,
            invertsWithBackdrop = false,
        )

        /**
         * Thinner and more transparent, for chrome that should mostly disappear.
         *
         * The zeros are Apple's, not a tuning choice: the clear variant "does not have adaptive
         * behaviours, it is permanently more transparent". So it does not tone-map, does not
         * react to contrast, never flips — and therefore *requires* the dimming layer.
         */
        val Clear = GlassStyle(
            blurRadius = 4.dp,
            refractionBand = 28.dp,
            refractionDepth = 16.dp,
            dispersion = 0.030f,
            mirror = 0.22f,
            bevel = 4.dp,
            tint = Color.White.copy(alpha = 0.04f),
            adaptivity = 0f,
            legibility = 0f,
            specular = 0.70f,
            fresnel = 0.14f,
            innerShadow = 0.05f,
            invertsWithBackdrop = false,
            dimmingLayer = 0.35f,
        )

        /** Heavier: a sheet or a dialog that must hold a lot of content. */
        val Thick = GlassStyle(
            blurRadius = 16.dp,
            refractionBand = 22.dp,
            refractionDepth = 12.dp,
            dispersion = 0.015f,
            mirror = 0.14f,
            bevel = 5.dp,
            tint = Color.White.copy(alpha = 0.13f),
            adaptivity = 0.80f,
            legibility = 0.5f,
            specular = 0.45f,
            specularPower = 7f,
            fresnel = 0.08f,
            innerShadow = 0.10f,
            // A sheet is a large surface: it adapts to its backdrop but never flips polarity.
            invertsWithBackdrop = false,
        )
    }
}

/**
 * Where the light sits, as a unit vector in the panel's own space.
 *
 * `y` is negative upward, matching Compose's coordinate system, so the default is a light above
 * and slightly to the left — the direction a highlight has to come from for a surface to read
 * as convex rather than concave.
 */
@Immutable
data class GlassLight(val x: Float = -0.35f, val y: Float = -0.94f) {
    companion object {
        val Default = GlassLight()
    }
}
