package com.wexpa.liquidglass

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp

/**
 * Which optical model the shader runs.
 *
 * [Measured] is the material fitted from calibration captures of iOS 27 (see [GlassMaterial]
 * and `docs/research/measured-model.md`): an inward fold lens whose width follows the corner
 * radius, a two-kernel backdrop, a per-appearance tint with a fixed lift, and dispersion only in
 * the mirrored zone. [Legacy] is the 0.1 model: an outward Snell bevel, a soft mirrored echo, a
 * single interior scatter and a tone-mapped tint. The presets that were tuned against the 0.1
 * model keep it so their look does not move under them.
 */
enum class GlassProfile {
    Legacy,
    Measured,
    /**
     * The family of the lens that forms under a finger on a tab bar (measured model, section 2c):
     * identity at rest and, as [GlassStyle.heldLens] rises to 1, an outer band that pulls the
     * exterior inward and compresses it, a seam that hides what lies between that band and the
     * interior, and the interior shown as it is. The material is otherwise the measured one. A
     * resting indicator and the lens it swells into both sit in this family, so the morph
     * between them never crosses into the fold.
     */
    Held,
    /** Standalone magnifier with one C1-continuous source map for backdrop and foreground.
     * Authored optical model; the measured tab-bar Held profile stays separate. */
    Lens,
}

/**
 * The physical parameters of a piece of glass.
 *
 * These are the knobs the shader consumes. The constructor defaults are the earlier measured
 * in-app fit, before the three-term material of #200 (so [detailLift] and [toneCurve] default to
 * 0), and `GlassStyle()` is no longer [Regular]. To customise Regular, start from it:
 * `GlassStyle.Regular.copy(...)`. The factories on the companion build the other measured roles,
 * and the [GlassProfile.Legacy] presets keep the 0.1 material for hosts tuned against it.
 */
@Immutable
data class GlassStyle(
    /** Which optical model the shader runs. See [GlassProfile]. */
    val profile: GlassProfile = GlassProfile.Measured,
    /**
     * Radius of the interior scatter, which tapers to nothing at the rim so the edge stays
     * sharp. A rotating tap disc inside the shader, so it is cheap but grainy at large radii.
     *
     * In the measured model this is the *fine* kernel, and it is small: the detail seen through
     * iOS 27's glass is blurred by half a point, not ten. What softens the material is the wide
     * kernel, [wideKernel], which decides tone and leaves detail alone.
     */
    val blurRadius: Dp = 1.5.dp,
    /**
     * Radius of a blur applied to the backdrop before the shader sees it.
     *
     * Higher quality than [blurRadius] and much better at destroying text, but it also destroys
     * the detail the rim compresses, so the signature edge goes with it. That is the right
     * trade for chrome floating over an app's own content and the wrong one for a surface meant
     * to show its backdrop.
     */
    val backdropBlur: Dp = 0.dp,
    /**
     * Gaussian sigma of a blur applied to the backdrop before the shader sees it, the measured
     * way of saying [backdropBlur]: the system backdrops behind Control Center, App Library and
     * the Today view are a 7.7 dp sigma over app content. Set either; when both are set this one
     * wins. It is converted to the platform's own blur radius at bind time, so the same number
     * means the same blur on Android and on the desktop.
     */
    val backdropSigma: Dp = Dp.Unspecified,
    /**
     * Width of the band, measured in from the rim, over which refraction acts.
     *
     * Unspecified means *derived from the shape*: the measured bevel is 0.6 of the corner
     * radius for every element from a 15 dp icon to a 47 dp sheet corner, so a capsule gets a
     * band 0.3 of its height and a sharp-cornered rectangle gets none. Set it explicitly to
     * override that, which the legacy presets do.
     */
    val refractionBand: Dp = Dp.Unspecified,
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
     * Scale on a selector body's own refraction band (0.6 of its radius), 1 by default. A wider band
     * pulls more of the surroundings into the held lens's rim, so the bar's edge bends along the
     * lens's contour instead of showing through as a second outline.
     */
    val bodyBandScale: Float = 1f,
    /**
     * Sigma of the wide kernel B, the blur that sets the material's tone: `lin = B + k (D - B) + c |D - B|`
     * with k = [fineShare] and c = [detailLift] (`tools/glass_model.py`). Measured on iOS 27 as
     * 15.5 dp (46.5 px at 3x; the fit is flat from 44 to 52 px) at Tint 50 and, on the Photos chrome, at Tint 0
     * and 100 (#208); [GlassStyle.inApp], [GlassStyle.toolbar] and [Regular] use it at every tint. The 10 dp default
     * remains for styles that do not take the lab material, such as the tab bar's earlier fit. Rendered once per
     * frame as a quarter-scale blurred copy of the
     * backdrop; set it to 0 to switch it off.
     */
    val wideKernel: Dp = 10.dp,
    /**
     * The detail share k: how much of the backdrop's own detail is laid over the wide kernel's
     * tone, 0 to 1. Measured on iOS 27 at Tint 50 as 0.277 (dark) and 0.273 (light), the same for
     * detail along and across a bar, and at Tint 0 and 100 on the Photos chrome as 0.663 / 0 (#208), linear between
     * the three tints (authored; [GlassMaterial.measuredDetailShare]). The earlier fit, `0.95 (1 - t/100)^1.5`
     * ([GlassMaterial.fineShare]), no longer reaches [GlassStyle.inApp], [GlassStyle.toolbar] or [Regular]; it
     * remains for the styles built on the earlier fit, such as the tab bar's bar. With the wide kernel on, the
     * softened detail copy (which
     * [legibility] also reads) fades out as the larger of this share and |[detailLift]| falls from
     * 0.10 to 0.02 (authored), and is not drawn below that, so a share that runs toward 0 near
     * Tint 100 does not switch the look at one tint.
     */
    val fineShare: Float = 0.34f,
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
     * How far red and blue split from green.
     *
     * Legacy: as a fraction of the displacement, everywhere in the band, and faint. Measured:
     * as a fraction of the bevel width, and only in the mirrored zone — the outer 0.28 of the
     * band — where red shows content 0.07 of the band deeper than blue; the stretched zone has
     * no colour split at all. That is the rainbow fringe on the cover sheet's edge.
     */
    val dispersion: Float = 0.07f,
    /**
     * How far the held lens of [GlassProfile.Held] has formed, 0 (identity) to 1 (the measured
     * lens under a finger). Ignored by the other profiles. Fitted on the Phone and App Store
     * tab bars of iOS 27 (measured model, section 2c): over the outer half of the band a pixel
     * at depth u shows the exterior from 1.3u - 0.29 W, so the content just outside the rim is
     * pulled in and compressed; from half the band inward the content is shown where it is,
     * and the source content between 0.36 W and 0.5 W is never displayed. The modifier records
     * the wider margin this needs.
     */
    val heldLens: Float = 0f,
    /**
     * Interior magnification of the held lens, as the zoom fraction at full formation: 0.2 shows
     * the content under the lens at 1 / (1 - 0.2) = 1.25x about the body's centre, rising and
     * falling with [heldLens]. Authored from the calm held references (6717, 6721, and the
     * recording's g7/g14 holds), where the held item reads about 1.25x its neighbours and the
     * bar's own edge is carried outward inside the lens. Zero leaves the interior sampled 1:1,
     * which is what every profile other than [GlassProfile.Held] does.
     */
    val heldMagnification: Float = 0f,
    /**
     * How much of the held lens's inward rim glow is drawn, 0 to 2 (1 is the historical full
     * amount; values above 1 are for a wider [heldGlowReach]). The glow is the measured
     * "+16 falling to +1 over 15 px" behind the lens's lit edge line; a lens that is meant to show
     * nothing but the exterior compressed into its band, with colour separation where content
     * crosses it, sets this to 0 together with [specular], [counterLight] and [edgeShadow].
     */
    val heldGlow: Float = 1f,
    /**
     * Inward recovery length of the held lens's dark side edge. Zero keeps the historical
     * narrow contour. V3 opts into a short shoulder and tail derived from original Phone
     * profiles; the curve is authored, not an identified Apple optical formula.
     */
    val heldEdgeRecovery: Dp = 0.dp,
    /**
     * Opt-in: the held lens's outermost pixels as a grey line of this level (0 to 1), drawn
     * nearly opaque over whatever lies behind, instead of only the additive rim light. Zero keeps
     * the historical additive rim. The held iOS 26 Phone lens's outline reads about 65 grey levels
     * over the bar (83) and over the black page alike: a line, not a highlight (video2, held and
     * moving lens, 2026-10-14). Calm's lens uses 65/255.
     */
    val heldEdgeTone: Float = 0f,
    /**
     * Opt-in: how the held lens's inward rim glow ([heldGlow]) composes, from 0 (default: added
     * to whatever is behind) to 1 (a floor: the glow only shows where the glass is darker than
     * it). iOS's held lens glows inward from its edge over the black page (36, 22, 20, 17 ... 9
     * levels) but adds nothing over its bar, where a travelling lens shows no bright line
     * (video2 1.8 s and 19.0 s, 2026-10-15 pass 13). Calm's lens uses 1.
     */
    val heldGlowFloor: Float = 0f,
    /**
     * How the fine scatter is sampled, 0 to 1: at 0 through a per-pixel rotation (grain), at 1
     * unrotated. Since #200 the detail is always one soft copy over the wide kernel's tone; the
     * along-axis high-pass that drew ghost copies of every glyph about 16 and 34 px to either side
     * (#190) is gone. [inApp] sets 1 at the measured Tint 50.
     */
    val softDetail: Float = 0f,
    /**
     * A share of the raw backdrop added on top of the glass this element looks
     * [through][liquidGlass], 0 to 1. The measured tab-bar lens shows the bar's output plus
     * 0.11 of the content behind the bar, which is what makes its interior read clearer than
     * the bar around it (+12 levels over the App Store's content) while adding nothing over
     * black. Ignored without `through`.
     */
    val rawShare: Float = 0f,
    /**
     * Gaussian sigma of the raw backdrop that [rawShare] adds. Zero adds it sharp, as before. A
     * blurred share lifts the glass over content by what the content around it holds and adds
     * nothing over black, without drawing the content's edges a second time. Ignored without
     * [rawShare].
     */
    val rawBlur: Dp = 0.dp,
    /**
     * Amplitude of the mirrored edge band — the broad, soft, upside-down echo of nearby content
     * over roughly the outer third of the surface. A thin band reads as a hard streak; this one
     * is what makes the edge read as liquid rather than as a bevel.
     */
    val mirror: Float = 0f,
    /** Width of the lit bevel. The measured pill's bright edge line is 2-4 px, about a point. */
    val bevel: Dp = 1.5.dp,
    /**
     * Tint colour; its alpha is the opacity `a`.
     *
     * Measured: the material is `(1 - a) * backdrop + a * tint + lift`. In a light appearance
     * the tint is a constant 241/255 grey and the lift is 0; in a dark appearance the tint is
     * black and the lift is 35/255. The opacity follows Tint Amount from about 0.43 to 0.73 in
     * both. See [GlassMaterial.opacity].
     */
    val tint: Color = Color(0xFFF1F1F1).copy(alpha = 0.565f),
    /**
     * A fixed luminance lift added after the tint, 0 to 1. The dark in-app material's 35/255;
     * the Control Center layer's 10.23/255.
     *
     * May be negative when the lift does not adapt ([liftAdaptivity] 0): the resting tab-bar
     * indicator is the bar's output at 0.857 minus 17/255, over black and over content alike.
     */
    val tintLift: Float = 0f,
    /**
     * How far the lift falls with the wide-kernel luma of what is behind the glass, per unit
     * luma. In-app glass measures 0, and so does the Control Center layer (an earlier fit read
     * 0.864 from whole-image means of a non-flat target; per-pixel and flat-grey reads refute it).
     */
    val liftAdaptivity: Float = 0f,
    /**
     * How strongly the tint reacts to the backdrop's brightness. At 0 the tint is fixed; at 1
     * the glass darkens over bright content and lifts over dark content, which is what keeps
     * whatever sits on the glass readable as the backdrop scrolls beneath it.
     */
    val adaptivity: Float = 0f,
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
    /**
     * A dark contour in the outermost pixels, all the way round, independent of the light.
     *
     * This is what separates glass from what is behind it in iOS 26.1's successor: Apple's
     * 2026 revision pairs a darkened edge with a brighter specular so an element stays
     * distinct over busy content. It sits *outside* the lit edge line rather than over it, so
     * the two do not cancel.
     *
     * It is also what defines a shape where nothing is lit. The edge line above is entirely
     * directional, so at the sides of a lens lit from overhead it contributes nothing; the
     * reference shows a one-pixel *dark* step there, which is this. [GlassTabBarStyle.HeldLens]
     * sets it to the measured 0.08. iOS 27's own contour lies outside the outline instead; that is
     * [outerContour], which [GlassTabBarStyle.Ios27] uses.
     */
    val edgeShadow: Float = 0f,
    /**
     * Extra [edgeShadow] where the outline faces sideways: the contour darkens by
     * `1 + capEdgeShadow * nx^2`, nx the outline normal's horizontal component, so a capsule's rounded
     * ends read darker than its straight runs. Zero, the default, leaves the contour uniform. Light
     * `GlassTabBarStyle.Calm()`'s bar uses 2: iOS's light tab bar reads 246 along its top and bottom
     * and 178-182 at its ends' outermost point (IMG_7048 36.5 s); 2 gives 177-186 there (18 October).
     */
    val capEdgeShadow: Float = 0f,
    /**
     * A dark contour drawn **outside** the outline, strongest where the rim is unlit, as iOS 27
     * draws it. It is what keeps a control distinct over bright content. 0, the default, draws none.
     *
     * Measured on iOS 27's 44 pt navigation buttons over a white page
     * (`docs/research/ios27-measurements-raw.md`, "The dark contour"). The contour lies entirely
     * outside the outline: the outermost wholly covered pixel matches the body. It is 2.5 px deep:
     * its three outermost pixel centres weigh 1 : 0.5 : 0 (both appearances, sides and poles). The
     * dark appearance darkens what is behind it by 0.61 at the sides, multiplicatively, so a positive
     * value draws black at this opacity. The light appearance is additive, a plus-darker blend that
     * removes 0.30 of full scale whatever the backdrop (76 levels over 128 and over 255), so a
     * negative value removes `-outerContour` of full scale. Both fall toward the lit poles: 5.7% of
     * the side value at 20 degrees off the light, 20.5% at 40, 36.2% at 60 and 37.4% at 120 (dark
     * appearance). Here the angular law is `(1 - |cos|)^0.75` of the angle to the light
     * ([GlassLight]). It fits the dark appearance's nine angles within 0.04 of the side value.
     * Lone elements, the tab bar and `GlassEffectContainer`'s merged shapes all draw it.
     */
    val outerContour: Float = 0f,
    /**
     * The share of [outerContour] that remains at the lit poles. The light appearance keeps 0.13 of
     * its 0.30 side value there (10 levels at the first pixel, 5 at the second, measured). The dark
     * appearance goes to zero. The light appearance's profile is flatter than the law above between
     * the pole and 60 degrees (it reads 8.1% at 20 and 11.3% at 40 of the former multiplicative
     * value), so those mid angles are inherited, not re-measured.
     */
    val outerContourFloor: Float = 0f,
    /**
     * How far the compressed rim image is smeared along the surface normal.
     *
     * Where the rim compresses a hard boundary it lands as a single bright line, which reads as
     * a stroke someone drew rather than as an image being squeezed. A little smearing turns that
     * line into the short gradient the reference shows. Costs three backdrop samples per channel
     * inside the band instead of one, so it is off by default.
     *
     * **Use it sparingly, and watch what it costs.** The rim being *legible* is the most
     * recognisable property of this material: it shows a compressed but readable image of what
     * lies just outside. Softening trades exactly that away. Around 2-3 dp takes the hard line
     * off a boundary while leaving text in the band readable; by 7 dp the band is a smooth
     * gradient and the detail in it is gone, which is a different material rather than a better
     * one.
     */
    val rimSoftness: Dp = 0.dp,
    /**
     * Whether the tint behaves as a blend (0) or as an absorbing medium (1).
     *
     * A blend moves every pixel the same distance toward one colour and flattens whatever light
     * and shade the backdrop had. A medium multiplies, so that structure survives, and scatters
     * a little of its own colour where the backdrop is dark so the hue still reads over black.
     *
     * 0 is the default and it is not a taste decision: the blend's strength is fitted to
     * measurement, and it is the pair of numbers that pins this material. Raise it for tinted
     * glass over photographs, where a blend visibly flattens the picture.
     */
    val tintAbsorption: Float = 0f,
    /** Schlick rim reflectance gain: how much the bevel brightens as it turns away. */
    val fresnel: Float = 0.10f,
    /**
     * How much of the rim highlight is applied as lightness rather than as white.
     *
     * At 0 the highlight is added to the colour, which is the cheapest thing to do and also
     * desaturates: adding white to a saturated backdrop pulls it toward grey, so a lit rim over
     * a deep blue reads as a milky smear rather than as bright blue glass. At 1 the whole
     * highlight is applied in Oklab instead, raising lightness and chroma together, so the rim
     * gets brighter *and* keeps the hue of whatever is behind it.
     *
     * Lightness is allowed past its own ceiling on purpose. The core of the lobe still clips to
     * white, which is what a specular does; only the shoulder stays coloured, and that asymmetry
     * is most of the difference between glass that glows and a white overlay.
     *
     * Off on [DarkChrome], which was measured from a device rather than tuned, until there are
     * new measurements to move it to.
     */
    val highlightChroma: Float = 0.7f,
    /**
     * How much of the panel's own contact shadow is drawn into the backdrop it refracts.
     *
     * A shadow drawn over or under the glass is a picture of a shadow. A shadow drawn into the
     * backdrop *before* the shader samples it is part of the image the rim bends, so the edge
     * shows a compressed, warped smear of the panel's own shadow, which is what a real lens
     * resting on a surface does and the strongest cue that the panel is sitting on something
     * rather than pasted onto it. It also shares the interior scatter, so shadow and backdrop
     * defocus together.
     *
     * Scales [glassShadow]'s alpha for this panel. Set it to 0 if the host already draws an
     * elevation shadow behind the panel, or the two will stack.
     */
    val contactShadow: Float = 1f,
    /**
     * Share of [contactShadow] in the copy drawn into the backdrop (what the glass refracts), 0 to 1;
     * the copy on the page keeps the full strength. One, the default, draws both alike. Light
     * `GlassTabBarStyle.Calm()`'s held lens uses 0.5: iOS's shadow reads 242 below the lens and
     * 250-253 through the lens where it overhangs the page (IMG_7048, 18 October).
     */
    val contactShadowInside: Float = 1f,
    /**
     * The contact shadow's blur radius and downward offset; unspecified, the default, sizes both
     * from the panel ([glassShadow]). Light `GlassTabBarStyle.Calm()`'s held lens sets them: iOS's
     * shadow under it is tight and offset down (none above the lens, 242 for ~15 px below it).
     */
    val contactShadowBlur: Dp = Dp.Unspecified,
    val contactShadowOffset: Dp = Dp.Unspecified,
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
    /**
     * Which source map the resting corner lens of the measured profile uses. See [GlassRestMap]: the
     * default is the map that shipped; [GlassRestMap.Measured] is the resting-corner table fitted on the
     * Photos toolbar's 72 px ends, for the toolbar/bar role that path was measured on. Ignored by the
     * legacy profile and by the held family. Last on purpose: every constructor parameter that existed
     * before it keeps its position, so positional call sites written against 0.1 still compile.
     */
    val restMap: GlassRestMap = GlassRestMap.Legacy,
    /**
     * Straight-run fold strength, the `a` of `f(u) = u + a (1 - u)^3` on the measured profile.
     *
     * The measured resting *corner* table is untouched by this: the two profiles are blended
     * through a geometric arc/run transition, so a corner keeps its measured source map and only
     * a straight edge takes this candidate. For `a > 1/3` the profile folds once, at
     * `uFold = 1 - 1/sqrt(3a)`, with minimum source depth `1 - 2/(3 sqrt(3a))`; page structure
     * therefore appears twice near the edge with opposite orientation, which is the mechanism
     * behind the stretched, reversed page text at a straight edge. 0 keeps the shallow inward
     * offset a committed sheet pull measured, which is what every existing consumer has.
     *
     * The number is **authored**, not a recovered optical constant. Its intended range is
     * 0.55 to 1.20 (V3-MODEL.md section 10.2).
     */
    val edgeFold: Float = 0f,
    /**
     * Per-channel split for this element's semantic [refracted content][liquidGlass], as a
     * fraction of the displacement.
     *
     * Unspecified (NaN) means "whatever [dispersion] is", which is what the material has always
     * done. 0 takes one sharp sample instead, which is the only form whose alpha is exactly the
     * glyph's own: a maximum over three channels' alphas is not correct coloured transmission
     * over an arbitrary background, and it is what turns a white glyph into an opaque blocker of
     * green and blue. Set it explicitly when ink and page material should not split alike.
     */
    val inkDispersion: Float = Float.NaN,
    /**
     * Whether the held lens's **ink** map is continuous through the middle of its band.
     *
     * The shipped held source steps by about 0.14 W at half the band. The captures did not
     * decisively establish a true discontinuity there, and a smooth path across one source plane
     * has a continuous source coordinate, so the sharp ink map connects the outward branch to the
     * interior across 0.45..0.55 instead (V3-MODEL.md section 10.3). The page material's own map
     * is unaffected; this is one coordinate blend, not a blend of two glyph images.
     */
    val heldInkContinuous: Boolean = false,
    /**
     * How far inward the held lens's rim glow ([heldGlow]) reaches before falling to 1/e.
     * Zero ties it to [bevel], the historical narrow glow. Video2's moving lens shows a glow of
     * about +25 at the edge fading over roughly 6 pt across its band (2026-10-13).
     */
    val heldGlowReach: Dp = 0.dp,
    /**
     * Confine the measured fold ([GlassProfile.Measured] at formation) to the outline's corner
     * arcs, 0 to 1. Zero folds every edge as the measured cover sheet does. A moving tab-bar
     * selector uses 1: video1/video2 show its rim mirroring content at the cap ends while its
     * long top and bottom runs keep a thin band.
     */
    val foldAtCorners: Float = 0f,
    /**
     * Lens the outline's corner arcs at rest, 0 to 1, with [GlassProfile.Measured]: the rounded ends of
     * a capsule pull in what lies beyond them as the held lens's map does, while the straight runs keep
     * their shallow rest offset. Zero, the default, leaves the arcs on the rest map. iOS 26's tab bar
     * bends the page under its rounded ends, an icon's edge outside the bar showing ~30 px inside its cap
     * (the owner's Contacts recording, IMG_7049, 18 October); a fold along the straight runs garbles
     * text, so `Calm()` uses this instead of [edgeFold].
     */
    val capFold: Float = 0f,
    /**
     * With [GlassProfile.Measured] and a formation, replace the measured fold with a reflective
     * rim: within this fraction b of the band a pixel at depth u shows the content at depth
     * 2b - u, mirrored about b, and the interior is shown in place. Continuous, with no hidden
     * seam. Zero keeps the measured fold. A moving tab-bar selector uses it (video1/video2,
     * 2026-10-13: icon strokes near a travelling lens's ends wrap mirrored along its rim).
     */
    val rimMirror: Float = 0f,
    /**
     * With [rimMirror]: how much deeper content the mirrored rim reaches per unit of band, 0.05
     * to 1. One is the plain mirror (a pixel at depth u shows 2b - u); below one the rim shows
     * b + (b - u) x gain, so content near the band is drawn out radially toward the rim,
     * magnified by 1 / gain. A moving tab-bar selector uses it (video2, 2026-10-14: icons near a
     * travelling lens's caps stretch into long petals reaching the rim).
     */
    val rimMirrorGain: Float = 1f,
    /**
     * The one-sided detail term c of the measured material, -1 to 1: the glass keeps detail on its
     * own side of the surroundings at [fineShare] + c and detail on the other side at [fineShare] - c,
     * `lin = B + k (D - B) + c |D - B|` (`tools/glass_model.py`). Measured on iOS 27 at Tint 50
     * (#199): +0.22 on light glass, -0.22 on dark. Zero, the default, is the plain two-kernel mix.
     * It also carries the material's measured colour transfer (#201): over colour the glass keeps and
     * boosts saturation in the appearance its sign names, in full at |c| = 0.22 and in proportion below.
     */
    val detailLift: Float = 0f,
    /**
     * The bow of the material's tone curve, `toneCurve x (1 - x)` per channel, where x is the
     * backdrop term (the material before the tint). The shader adds it after the tint blend, scaled
     * by the material and tint coverage but not by the tint opacity, and before [tintLift]; the tint
     * blend and [tintLift] give the curve's straight part. The dark in-app curve measured on iOS 27
     * at Tint 50 is concave, 101/255 (#200); zero is straight.
     */
    val toneCurve: Float = 0f,
    /**
     * An output-referred chroma ratio for the material, or [Float.NaN] (the default) to leave the colour transfer to
     * [detailLift]. Set to g, the finished glass keeps g times the backdrop's chroma about its own luma: the colour is
     * `T(Y) + g (backdrop - Y)`, Y the backdrop's Rec.709 luma and T(Y) the grey the material makes of it (tint blend,
     * [tintLift], tone curve). The gain keeps the material's resulting luma, not the backdrop's, and g = 1 returns the
     * backdrop's saturation exactly (a plain black tint alone would leave 1 - tint opacity of it). It replaces the lab colour transfer that [detailLift] otherwise selects; flat per channel, no
     * slope. Measured on iOS 27 Control Center tiles at 1.58 (sd 0.06, 8 hues; [controlCenterTile]). Clamped to 0..4.
     * Unset is byte-identical to the styles before this field existed. A morph snaps it at the midpoint when only one
     * end sets it, because "unset" is an identity rather than a quantity.
     */
    val chromaGain: Float = Float.NaN,
) {
    /** Keeps the constructor before [chromaGain]; new code uses the primary constructor. */
    @Deprecated("Binary compatibility with the constructor before chromaGain", level = DeprecationLevel.HIDDEN)
    constructor(
        profile: GlassProfile = GlassProfile.Measured,
        blurRadius: Dp = 1.5.dp,
        backdropBlur: Dp = 0.dp,
        backdropSigma: Dp = Dp.Unspecified,
        refractionBand: Dp = Dp.Unspecified,
        refractionDepth: Dp = 14.dp,
        bodyBandScale: Float = 1f,
        wideKernel: Dp = 10.dp,
        fineShare: Float = 0.34f,
        indexOfRefraction: Float = 1.5f,
        bevelPower: Float = 2f,
        cornerPower: Float = 2f,
        dispersion: Float = 0.07f,
        heldLens: Float = 0f,
        heldMagnification: Float = 0f,
        heldGlow: Float = 1f,
        heldEdgeRecovery: Dp = 0.dp,
        heldEdgeTone: Float = 0f,
        heldGlowFloor: Float = 0f,
        softDetail: Float = 0f,
        rawShare: Float = 0f,
        rawBlur: Dp = 0.dp,
        mirror: Float = 0f,
        bevel: Dp = 1.5.dp,
        tint: Color = Color(0xFFF1F1F1).copy(alpha = 0.565f),
        tintLift: Float = 0f,
        liftAdaptivity: Float = 0f,
        adaptivity: Float = 0f,
        legibility: Float = 0.6f,
        specular: Float = 0.55f,
        specularPower: Float = 6f,
        counterLight: Float = 0.35f,
        edgeLight: Float = 1f,
        bevelPeak: Float = 0f,
        edgeShadow: Float = 0f,
        capEdgeShadow: Float = 0f,
        outerContour: Float = 0f,
        outerContourFloor: Float = 0f,
        rimSoftness: Dp = 0.dp,
        tintAbsorption: Float = 0f,
        fresnel: Float = 0.10f,
        highlightChroma: Float = 0.7f,
        contactShadow: Float = 1f,
        contactShadowInside: Float = 1f,
        contactShadowBlur: Dp = Dp.Unspecified,
        contactShadowOffset: Dp = Dp.Unspecified,
        innerShadow: Float = 0.07f,
        fallbackSurface: Color = Color(0xF2141416),
        invertsWithBackdrop: Boolean = true,
        dimmingLayer: Float = 0f,
        restMap: GlassRestMap = GlassRestMap.Legacy,
        edgeFold: Float = 0f,
        inkDispersion: Float = Float.NaN,
        heldInkContinuous: Boolean = false,
        heldGlowReach: Dp = 0.dp,
        foldAtCorners: Float = 0f,
        capFold: Float = 0f,
        rimMirror: Float = 0f,
        rimMirrorGain: Float = 1f,
        detailLift: Float = 0f,
        toneCurve: Float = 0f,
    ) : this(
        profile = profile,
        blurRadius = blurRadius,
        backdropBlur = backdropBlur,
        backdropSigma = backdropSigma,
        refractionBand = refractionBand,
        refractionDepth = refractionDepth,
        bodyBandScale = bodyBandScale,
        wideKernel = wideKernel,
        fineShare = fineShare,
        indexOfRefraction = indexOfRefraction,
        bevelPower = bevelPower,
        cornerPower = cornerPower,
        dispersion = dispersion,
        heldLens = heldLens,
        heldMagnification = heldMagnification,
        heldGlow = heldGlow,
        heldEdgeRecovery = heldEdgeRecovery,
        heldEdgeTone = heldEdgeTone,
        heldGlowFloor = heldGlowFloor,
        softDetail = softDetail,
        rawShare = rawShare,
        rawBlur = rawBlur,
        mirror = mirror,
        bevel = bevel,
        tint = tint,
        tintLift = tintLift,
        liftAdaptivity = liftAdaptivity,
        adaptivity = adaptivity,
        legibility = legibility,
        specular = specular,
        specularPower = specularPower,
        counterLight = counterLight,
        edgeLight = edgeLight,
        bevelPeak = bevelPeak,
        edgeShadow = edgeShadow,
        capEdgeShadow = capEdgeShadow,
        outerContour = outerContour,
        outerContourFloor = outerContourFloor,
        rimSoftness = rimSoftness,
        tintAbsorption = tintAbsorption,
        fresnel = fresnel,
        highlightChroma = highlightChroma,
        contactShadow = contactShadow,
        contactShadowInside = contactShadowInside,
        contactShadowBlur = contactShadowBlur,
        contactShadowOffset = contactShadowOffset,
        innerShadow = innerShadow,
        fallbackSurface = fallbackSurface,
        invertsWithBackdrop = invertsWithBackdrop,
        dimmingLayer = dimmingLayer,
        restMap = restMap,
        edgeFold = edgeFold,
        inkDispersion = inkDispersion,
        heldInkContinuous = heldInkContinuous,
        heldGlowReach = heldGlowReach,
        foldAtCorners = foldAtCorners,
        capFold = capFold,
        rimMirror = rimMirror,
        rimMirrorGain = rimMirrorGain,
        detailLift = detailLift,
        toneCurve = toneCurve,
        chromaGain = Float.NaN,
    )

    /** Keeps the `copy` before [chromaGain] in the binary API; new code uses the generated `copy`. */
    @Deprecated("Binary compatibility with the copy before chromaGain", level = DeprecationLevel.HIDDEN)
    fun copy(
        profile: GlassProfile = this.profile,
        blurRadius: Dp = this.blurRadius,
        backdropBlur: Dp = this.backdropBlur,
        backdropSigma: Dp = this.backdropSigma,
        refractionBand: Dp = this.refractionBand,
        refractionDepth: Dp = this.refractionDepth,
        bodyBandScale: Float = this.bodyBandScale,
        wideKernel: Dp = this.wideKernel,
        fineShare: Float = this.fineShare,
        indexOfRefraction: Float = this.indexOfRefraction,
        bevelPower: Float = this.bevelPower,
        cornerPower: Float = this.cornerPower,
        dispersion: Float = this.dispersion,
        heldLens: Float = this.heldLens,
        heldMagnification: Float = this.heldMagnification,
        heldGlow: Float = this.heldGlow,
        heldEdgeRecovery: Dp = this.heldEdgeRecovery,
        heldEdgeTone: Float = this.heldEdgeTone,
        heldGlowFloor: Float = this.heldGlowFloor,
        softDetail: Float = this.softDetail,
        rawShare: Float = this.rawShare,
        rawBlur: Dp = this.rawBlur,
        mirror: Float = this.mirror,
        bevel: Dp = this.bevel,
        tint: Color = this.tint,
        tintLift: Float = this.tintLift,
        liftAdaptivity: Float = this.liftAdaptivity,
        adaptivity: Float = this.adaptivity,
        legibility: Float = this.legibility,
        specular: Float = this.specular,
        specularPower: Float = this.specularPower,
        counterLight: Float = this.counterLight,
        edgeLight: Float = this.edgeLight,
        bevelPeak: Float = this.bevelPeak,
        edgeShadow: Float = this.edgeShadow,
        capEdgeShadow: Float = this.capEdgeShadow,
        outerContour: Float = this.outerContour,
        outerContourFloor: Float = this.outerContourFloor,
        rimSoftness: Dp = this.rimSoftness,
        tintAbsorption: Float = this.tintAbsorption,
        fresnel: Float = this.fresnel,
        highlightChroma: Float = this.highlightChroma,
        contactShadow: Float = this.contactShadow,
        contactShadowInside: Float = this.contactShadowInside,
        contactShadowBlur: Dp = this.contactShadowBlur,
        contactShadowOffset: Dp = this.contactShadowOffset,
        innerShadow: Float = this.innerShadow,
        fallbackSurface: Color = this.fallbackSurface,
        invertsWithBackdrop: Boolean = this.invertsWithBackdrop,
        dimmingLayer: Float = this.dimmingLayer,
        restMap: GlassRestMap = this.restMap,
        edgeFold: Float = this.edgeFold,
        inkDispersion: Float = this.inkDispersion,
        heldInkContinuous: Boolean = this.heldInkContinuous,
        heldGlowReach: Dp = this.heldGlowReach,
        foldAtCorners: Float = this.foldAtCorners,
        capFold: Float = this.capFold,
        rimMirror: Float = this.rimMirror,
        rimMirrorGain: Float = this.rimMirrorGain,
        detailLift: Float = this.detailLift,
        toneCurve: Float = this.toneCurve,
    ): GlassStyle = copy(
        profile = profile,
        blurRadius = blurRadius,
        backdropBlur = backdropBlur,
        backdropSigma = backdropSigma,
        refractionBand = refractionBand,
        refractionDepth = refractionDepth,
        bodyBandScale = bodyBandScale,
        wideKernel = wideKernel,
        fineShare = fineShare,
        indexOfRefraction = indexOfRefraction,
        bevelPower = bevelPower,
        cornerPower = cornerPower,
        dispersion = dispersion,
        heldLens = heldLens,
        heldMagnification = heldMagnification,
        heldGlow = heldGlow,
        heldEdgeRecovery = heldEdgeRecovery,
        heldEdgeTone = heldEdgeTone,
        heldGlowFloor = heldGlowFloor,
        softDetail = softDetail,
        rawShare = rawShare,
        rawBlur = rawBlur,
        mirror = mirror,
        bevel = bevel,
        tint = tint,
        tintLift = tintLift,
        liftAdaptivity = liftAdaptivity,
        adaptivity = adaptivity,
        legibility = legibility,
        specular = specular,
        specularPower = specularPower,
        counterLight = counterLight,
        edgeLight = edgeLight,
        bevelPeak = bevelPeak,
        edgeShadow = edgeShadow,
        capEdgeShadow = capEdgeShadow,
        outerContour = outerContour,
        outerContourFloor = outerContourFloor,
        rimSoftness = rimSoftness,
        tintAbsorption = tintAbsorption,
        fresnel = fresnel,
        highlightChroma = highlightChroma,
        contactShadow = contactShadow,
        contactShadowInside = contactShadowInside,
        contactShadowBlur = contactShadowBlur,
        contactShadowOffset = contactShadowOffset,
        innerShadow = innerShadow,
        fallbackSurface = fallbackSurface,
        invertsWithBackdrop = invertsWithBackdrop,
        dimmingLayer = dimmingLayer,
        restMap = restMap,
        edgeFold = edgeFold,
        inkDispersion = inkDispersion,
        heldInkContinuous = heldInkContinuous,
        heldGlowReach = heldGlowReach,
        foldAtCorners = foldAtCorners,
        capFold = capFold,
        rimMirror = rimMirror,
        rimMirrorGain = rimMirrorGain,
        detailLift = detailLift,
        toneCurve = toneCurve,
        chromaGain = chromaGain,
    )

    /** Keeps the 1.0 constructor in the binary API; new code uses the primary constructor. */
    @Deprecated("Binary compatibility with 1.0", level = DeprecationLevel.HIDDEN)
    constructor(
        profile: GlassProfile = GlassProfile.Measured,
        blurRadius: Dp = 1.5.dp,
        backdropBlur: Dp = 0.dp,
        backdropSigma: Dp = Dp.Unspecified,
        refractionBand: Dp = Dp.Unspecified,
        refractionDepth: Dp = 14.dp,
        bodyBandScale: Float = 1f,
        wideKernel: Dp = 10.dp,
        fineShare: Float = 0.34f,
        indexOfRefraction: Float = 1.5f,
        bevelPower: Float = 2f,
        cornerPower: Float = 2f,
        dispersion: Float = 0.07f,
        heldLens: Float = 0f,
        heldMagnification: Float = 0f,
        heldGlow: Float = 1f,
        heldEdgeRecovery: Dp = 0.dp,
        heldEdgeTone: Float = 0f,
        heldGlowFloor: Float = 0f,
        softDetail: Float = 0f,
        rawShare: Float = 0f,
        rawBlur: Dp = 0.dp,
        mirror: Float = 0f,
        bevel: Dp = 1.5.dp,
        tint: Color = Color(0xFFF1F1F1).copy(alpha = 0.565f),
        tintLift: Float = 0f,
        liftAdaptivity: Float = 0f,
        adaptivity: Float = 0f,
        legibility: Float = 0.6f,
        specular: Float = 0.55f,
        specularPower: Float = 6f,
        counterLight: Float = 0.35f,
        edgeLight: Float = 1f,
        bevelPeak: Float = 0f,
        edgeShadow: Float = 0f,
        capEdgeShadow: Float = 0f,
        outerContour: Float = 0f,
        outerContourFloor: Float = 0f,
        rimSoftness: Dp = 0.dp,
        tintAbsorption: Float = 0f,
        fresnel: Float = 0.10f,
        highlightChroma: Float = 0.7f,
        contactShadow: Float = 1f,
        contactShadowInside: Float = 1f,
        contactShadowBlur: Dp = Dp.Unspecified,
        contactShadowOffset: Dp = Dp.Unspecified,
        innerShadow: Float = 0.07f,
        fallbackSurface: Color = Color(0xF2141416),
        invertsWithBackdrop: Boolean = true,
        dimmingLayer: Float = 0f,
        restMap: GlassRestMap = GlassRestMap.Legacy,
        edgeFold: Float = 0f,
        inkDispersion: Float = Float.NaN,
        heldInkContinuous: Boolean = false,
        heldGlowReach: Dp = 0.dp,
        foldAtCorners: Float = 0f,
        capFold: Float = 0f,
        rimMirror: Float = 0f,
        rimMirrorGain: Float = 1f,
    ) : this(
        profile = profile,
        blurRadius = blurRadius,
        backdropBlur = backdropBlur,
        backdropSigma = backdropSigma,
        refractionBand = refractionBand,
        refractionDepth = refractionDepth,
        bodyBandScale = bodyBandScale,
        wideKernel = wideKernel,
        fineShare = fineShare,
        indexOfRefraction = indexOfRefraction,
        bevelPower = bevelPower,
        cornerPower = cornerPower,
        dispersion = dispersion,
        heldLens = heldLens,
        heldMagnification = heldMagnification,
        heldGlow = heldGlow,
        heldEdgeRecovery = heldEdgeRecovery,
        heldEdgeTone = heldEdgeTone,
        heldGlowFloor = heldGlowFloor,
        softDetail = softDetail,
        rawShare = rawShare,
        rawBlur = rawBlur,
        mirror = mirror,
        bevel = bevel,
        tint = tint,
        tintLift = tintLift,
        liftAdaptivity = liftAdaptivity,
        adaptivity = adaptivity,
        legibility = legibility,
        specular = specular,
        specularPower = specularPower,
        counterLight = counterLight,
        edgeLight = edgeLight,
        bevelPeak = bevelPeak,
        edgeShadow = edgeShadow,
        capEdgeShadow = capEdgeShadow,
        outerContour = outerContour,
        outerContourFloor = outerContourFloor,
        rimSoftness = rimSoftness,
        tintAbsorption = tintAbsorption,
        fresnel = fresnel,
        highlightChroma = highlightChroma,
        contactShadow = contactShadow,
        contactShadowInside = contactShadowInside,
        contactShadowBlur = contactShadowBlur,
        contactShadowOffset = contactShadowOffset,
        innerShadow = innerShadow,
        fallbackSurface = fallbackSurface,
        invertsWithBackdrop = invertsWithBackdrop,
        dimmingLayer = dimmingLayer,
        restMap = restMap,
        edgeFold = edgeFold,
        inkDispersion = inkDispersion,
        heldInkContinuous = heldInkContinuous,
        heldGlowReach = heldGlowReach,
        foldAtCorners = foldAtCorners,
        capFold = capFold,
        rimMirror = rimMirror,
        rimMirrorGain = rimMirrorGain,
        detailLift = 0f,
        toneCurve = 0f,
    )

    /** Keeps the 1.0 `copy` in the binary API; new code uses the generated `copy`. */
    @Deprecated("Binary compatibility with 1.0", level = DeprecationLevel.HIDDEN)
    fun copy(
        profile: GlassProfile = this.profile,
        blurRadius: Dp = this.blurRadius,
        backdropBlur: Dp = this.backdropBlur,
        backdropSigma: Dp = this.backdropSigma,
        refractionBand: Dp = this.refractionBand,
        refractionDepth: Dp = this.refractionDepth,
        bodyBandScale: Float = this.bodyBandScale,
        wideKernel: Dp = this.wideKernel,
        fineShare: Float = this.fineShare,
        indexOfRefraction: Float = this.indexOfRefraction,
        bevelPower: Float = this.bevelPower,
        cornerPower: Float = this.cornerPower,
        dispersion: Float = this.dispersion,
        heldLens: Float = this.heldLens,
        heldMagnification: Float = this.heldMagnification,
        heldGlow: Float = this.heldGlow,
        heldEdgeRecovery: Dp = this.heldEdgeRecovery,
        heldEdgeTone: Float = this.heldEdgeTone,
        heldGlowFloor: Float = this.heldGlowFloor,
        softDetail: Float = this.softDetail,
        rawShare: Float = this.rawShare,
        rawBlur: Dp = this.rawBlur,
        mirror: Float = this.mirror,
        bevel: Dp = this.bevel,
        tint: Color = this.tint,
        tintLift: Float = this.tintLift,
        liftAdaptivity: Float = this.liftAdaptivity,
        adaptivity: Float = this.adaptivity,
        legibility: Float = this.legibility,
        specular: Float = this.specular,
        specularPower: Float = this.specularPower,
        counterLight: Float = this.counterLight,
        edgeLight: Float = this.edgeLight,
        bevelPeak: Float = this.bevelPeak,
        edgeShadow: Float = this.edgeShadow,
        capEdgeShadow: Float = this.capEdgeShadow,
        outerContour: Float = this.outerContour,
        outerContourFloor: Float = this.outerContourFloor,
        rimSoftness: Dp = this.rimSoftness,
        tintAbsorption: Float = this.tintAbsorption,
        fresnel: Float = this.fresnel,
        highlightChroma: Float = this.highlightChroma,
        contactShadow: Float = this.contactShadow,
        contactShadowInside: Float = this.contactShadowInside,
        contactShadowBlur: Dp = this.contactShadowBlur,
        contactShadowOffset: Dp = this.contactShadowOffset,
        innerShadow: Float = this.innerShadow,
        fallbackSurface: Color = this.fallbackSurface,
        invertsWithBackdrop: Boolean = this.invertsWithBackdrop,
        dimmingLayer: Float = this.dimmingLayer,
        restMap: GlassRestMap = this.restMap,
        edgeFold: Float = this.edgeFold,
        inkDispersion: Float = this.inkDispersion,
        heldInkContinuous: Boolean = this.heldInkContinuous,
        heldGlowReach: Dp = this.heldGlowReach,
        foldAtCorners: Float = this.foldAtCorners,
        capFold: Float = this.capFold,
        rimMirror: Float = this.rimMirror,
        rimMirrorGain: Float = this.rimMirrorGain,
    ): GlassStyle = copy(
        profile = profile,
        blurRadius = blurRadius,
        backdropBlur = backdropBlur,
        backdropSigma = backdropSigma,
        refractionBand = refractionBand,
        refractionDepth = refractionDepth,
        bodyBandScale = bodyBandScale,
        wideKernel = wideKernel,
        fineShare = fineShare,
        indexOfRefraction = indexOfRefraction,
        bevelPower = bevelPower,
        cornerPower = cornerPower,
        dispersion = dispersion,
        heldLens = heldLens,
        heldMagnification = heldMagnification,
        heldGlow = heldGlow,
        heldEdgeRecovery = heldEdgeRecovery,
        heldEdgeTone = heldEdgeTone,
        heldGlowFloor = heldGlowFloor,
        softDetail = softDetail,
        rawShare = rawShare,
        rawBlur = rawBlur,
        mirror = mirror,
        bevel = bevel,
        tint = tint,
        tintLift = tintLift,
        liftAdaptivity = liftAdaptivity,
        adaptivity = adaptivity,
        legibility = legibility,
        specular = specular,
        specularPower = specularPower,
        counterLight = counterLight,
        edgeLight = edgeLight,
        bevelPeak = bevelPeak,
        edgeShadow = edgeShadow,
        capEdgeShadow = capEdgeShadow,
        outerContour = outerContour,
        outerContourFloor = outerContourFloor,
        rimSoftness = rimSoftness,
        tintAbsorption = tintAbsorption,
        fresnel = fresnel,
        highlightChroma = highlightChroma,
        contactShadow = contactShadow,
        contactShadowInside = contactShadowInside,
        contactShadowBlur = contactShadowBlur,
        contactShadowOffset = contactShadowOffset,
        innerShadow = innerShadow,
        fallbackSurface = fallbackSurface,
        invertsWithBackdrop = invertsWithBackdrop,
        dimmingLayer = dimmingLayer,
        restMap = restMap,
        edgeFold = edgeFold,
        inkDispersion = inkDispersion,
        heldInkContinuous = heldInkContinuous,
        heldGlowReach = heldGlowReach,
        foldAtCorners = foldAtCorners,
        capFold = capFold,
        rimMirror = rimMirror,
        rimMirrorGain = rimMirrorGain,
        detailLift = detailLift,
        toneCurve = toneCurve,
    )
    companion object {
        /** Clear standalone lens. [magnification] is the centre zoom, gradually returning to
         * identity at the boundary. No internal fold/seam and no separate foreground zoom.
         * Authored, not an Apple calibration. Supported range: 1x to 2.5x. */
        fun clearLens(magnification: Float = 1.25f): GlassStyle {
            require(magnification.isFinite() && magnification in 1f..2.5f) {
                "Lens magnification must be finite and between 1 and 2.5"
            }
            return GlassStyle(
                profile = GlassProfile.Lens, blurRadius = 0.dp, wideKernel = 0.dp,
                fineShare = 1f, tint = Color.White.copy(alpha = 0.015f), tintLift = 0f,
                heldLens = 1f, heldMagnification = 1f - 1f / magnification,
                bevel = 1.2.dp, bevelPeak = 0f, specular = 0.28f, fresnel = 0.02f,
                counterLight = 0.35f, dispersion = 0f, innerShadow = 0f,
                contactShadow = 0.15f, invertsWithBackdrop = false,
            )
        }

        /**
         * In-app glass, light appearance, default Tint Amount: the default style of [liquidGlass]. Its
         * interior is the material measured on iOS 27 at Tint 50 (#199); its rim is unchanged (#202).
         */
        val Regular: GlassStyle by lazy { GlassStyle().towardLab(dark = false, tintAmount = GlassMaterial.DEFAULT_TINT_AMOUNT) }

        /**
         * In-app glass — a navigation pill, round buttons, a toolbar — for one appearance and
         * one Tint Amount. Every number is measured (`docs/research/measured-model.md`, section
         * 2): opacity from the pill's black and white levels, the fine share from the toolbar's
         * stripes, the tint colour and lift per appearance, the edge line per appearance.
         */
        fun inApp(dark: Boolean, tintAmount: Float = GlassMaterial.DEFAULT_TINT_AMOUNT): GlassStyle =
            inAppEarlier(dark, tintAmount).towardLab(dark, tintAmount)

        /**
         * The lab material (#199, #200; `tools/glass_model.py`) at a Tint Amount: measured at Tint 50 in the Safari lab
         * and at Tint 0 and 100 on the Photos chrome (#208), piecewise-linear between those three (authored); see
         * [GlassMaterial.measuredDetailShare]. The material is `lin = B + k (D - B) + c |D - B|`, then the appearance's
         * tone; the toolbar, the address pill and plain in-app glass share it. The earlier fit no longer reaches these
         * styles at any tint.
         */
        private fun GlassStyle.towardLab(dark: Boolean, tintAmount: Float): GlassStyle {
            val t = tintAmount.coerceIn(0f, 100f)
            val labTint = (if (dark) Color.Black else Color.White).copy(alpha = GlassMaterial.measuredOpacity(dark, t))
            return copy(
                tint = labTint,
                tintLift = GlassMaterial.labLift(dark),
                fineShare = GlassMaterial.measuredDetailShare(dark, t),
                wideKernel = GlassMaterial.labWideKernel,
                // Held, not blended: at this reach the host picks the four-tap detail kernel (uDetailCross), and a
                // wider reach would draw four displaced copies.
                blurRadius = GlassMaterial.labDetailReach,
                detailLift = GlassMaterial.measuredDetailLift(dark, t),
                toneCurve = GlassMaterial.measuredToneCurve(dark, t),
                // The lab stills show no extra tint over busy content: the stripes keep k at every contrast.
                legibility = 0f,
                softDetail = 1f,
            )
        }

        private fun inAppEarlier(dark: Boolean, tintAmount: Float): GlassStyle {
            val a = GlassMaterial.opacity(dark, tintAmount)
            return GlassStyle(
                // The measured resting-corner map (GlassRestMap.Measured), fitted on the Photos toolbar's
                // 72 px ends; the pill (66 px) and round buttons (73 px) sit at nearly that radius. Since 26
                // October (#97): over the calibration chart it gives the caps and round buttons the dark
                // ovals iOS shows (COSG3073 276 s), where the legacy map showed light ones.
                restMap = GlassRestMap.Measured,
                tint = if (dark) Color.Black.copy(alpha = a) else GlassMaterial.lightTint.copy(alpha = a),
                tintLift = if (dark) GlassMaterial.DARK_LIFT else 0f,
                fineShare = GlassMaterial.fineShare(tintAmount),
                // The scatter disc's sigma is about 0.6 of its radius.
                blurRadius = GlassMaterial.fineKernel(tintAmount) / 0.6f,
                // The edge line, read around the pill's ends with the outlines matched (rim fit,
                // 2026-09-12): a bead peaking one pixel in at +60 over whatever is beneath, then
                // +40, +22, +10 over the next three pixels and gone by 8, the same in both
                // appearances and at the bottom as at the top; a tight lobe, +16 at 30 degrees
                // off the light and +8 at 45. The outermost pixel is a dark contour, not yet
                // drawn. Additive white, so over white in a light appearance it clips away,
                // which is what the phone shows.
                bevel = 1.33.dp,
                bevelPeak = 0.25f,
                specular = 0.25f,
                specularPower = 6f,
                counterLight = 1f,
                edgeLight = 0f,
                highlightChroma = 0f,
                fresnel = 0f,
                innerShadow = 0f,
                // The phone's pill over flat white reads 247-249 at every tint: no shadow is cast
                // into the backdrop beneath it. The 0.1 contact shadow darkened the backdrop
                // under the panel by ~20 levels at Clear, which the phone does not do.
                contactShadow = 0f,
                invertsWithBackdrop = false,
            )
        }

        /**
         * A toolbar or tab bar: the Photos toolbar's role (`docs/research/measured-model.md`, section 2, "The toolbar
         * role"). Its tone, tint, lift, detail share, wide kernel and tone curve are the lab material's
         * ([GlassMaterial.measuredOpacity], [GlassMaterial.labLift], [GlassMaterial.measuredDetailShare] and the other
         * measured node functions, applied last by the same step as [inApp]): measured at Tint 50 in the Safari lab and at
         * Tint 0 and 100 on the Photos chrome (#208), linear between the three nodes (authored). At every Tint Amount,
         * Tint 0 and 100 included, `toolbar` therefore has the same white or black tint at the fitted opacity as
         * [inApp]; the toolbar's own role values (white 0.55-0.67 and black 0.41-0.63, solved from the Photos stripes)
         * survive only in [toolbarEarlier], which the tab bar's bar keeps.
         */
        fun toolbar(dark: Boolean, tintAmount: Float = GlassMaterial.DEFAULT_TINT_AMOUNT): GlassStyle =
            toolbarEarlier(dark, tintAmount).towardLab(dark, tintAmount)

        /**
         * The toolbar on the earlier fit (before the #200 lab material). The tab bar's bar keeps it: the tab bar is
         * scored against its own recordings and moves to the lab material in #203.
         */
        internal fun toolbarEarlier(dark: Boolean, tintAmount: Float = GlassMaterial.DEFAULT_TINT_AMOUNT): GlassStyle {
            val a = GlassMaterial.toolbarOpacity(dark, tintAmount)
            return inAppEarlier(dark = dark, tintAmount = tintAmount).copy(
                tint = if (dark) Color.Black.copy(alpha = a) else GlassMaterial.toolbarLightTint.copy(alpha = a),
            )
        }

        /**
         * A full-screen system backdrop: Control Center, App Library, the Today view. A 7.7 dp
         * sigma over app content (11 dp at Tinted) and a plain affine map of the page, measured:
         * out = 10.23 + 0.3608 x the blurred backdrop level (of 255), so black reads 10 and white
         * 102 — lighter over lighter, no luma adaptation. Tint and Light/Dark do not change it.
         * No lens: a full-screen layer has no rim.
         */
        fun systemBackdrop(tintAmount: Float = GlassMaterial.DEFAULT_TINT_AMOUNT): GlassStyle = GlassStyle(
            backdropSigma = GlassMaterial.systemBackdropSigma(tintAmount),
            refractionBand = 0.dp,
            wideKernel = 0.dp,
            fineShare = 1f,
            tint = Color.Black.copy(alpha = 0.639f), // measured: 1 - 0.3608
            tintLift = 10.23f / 255f, // measured offset at black
            liftAdaptivity = 0f, // measured: none
            bevel = 0.dp,
            specular = 0f,
            edgeLight = 0f,
            fresnel = 0f,
            innerShadow = 0f,
            contactShadow = 0f,
            invertsWithBackdrop = false,
        )

        /**
         * A menu or popover: iOS 27's pop-up menu in the dark appearance (#102, measured on the Quick Look title-pill
         * menu: black reads 59, the tone peaks near 129, 8.17 dp blur) and the Safari `<select>` panel in the light one
         * (#204, 11.8 dp; no light pop-up menu was recorded). A pure blur, then a compressed tone, with no detail term
         * (Regular keeps 0.277 of the fine detail; the menu keeps none). Panel interior only: rim, shadow and corner
         * are not measured, and the rim fields are [inApp]'s. The page behind the title-pill menu was not dimmed;
         * the Photos long-press menu dims it to 0.52 (measured), which is the host's job. [menu] without an argument is
         * the earlier dark fit on the Photos context menu.
         */
        fun menu(dark: Boolean): GlassStyle = inAppEarlier(dark, GlassMaterial.DEFAULT_TINT_AMOUNT).copy(
            tint = (if (dark) Color.Black else Color.White).copy(alpha = GlassMaterial.menuOpacity(dark)),
            tintLift = GlassMaterial.menuLift(dark),
            toneCurve = GlassMaterial.menuToneCurve(dark),
            wideKernel = GlassMaterial.menuWideKernel(dark),
            fineShare = 0f,
            detailLift = 0f,
            // As the lab material: one short reach, so the host takes the four-tap detail kernel.
            blurRadius = GlassMaterial.labDetailReach,
            legibility = 0f,
            softDetail = 1f,
        )

        /**
         * A sheet, measured on iOS 27's share sheet (#102) in both appearances: a pure blur (7.17 dp dark, 8.67 dp
         * light), then a compressed tone (dark: black reads 59, the tone peaks near 129; light: black reads 145),
         * with no detail term. Interior only: rim, shadow, corner and the grabber are not measured, and the rim fields
         * are [inApp]'s. iOS does not dim the page behind its share sheet, but this style carries no scrim: Material 3's
         * `ModalBottomSheet` draws the sheet with this style and keeps its own scrim, a known difference.
         */
        fun sheet(dark: Boolean): GlassStyle = inAppEarlier(dark, GlassMaterial.DEFAULT_TINT_AMOUNT).copy(
            tint = (if (dark) Color.Black else Color.White).copy(alpha = GlassMaterial.sheetOpacity(dark)),
            tintLift = GlassMaterial.sheetLift(dark),
            toneCurve = GlassMaterial.sheetToneCurve(dark),
            wideKernel = GlassMaterial.sheetWideKernel(dark),
            fineShare = 0f,
            detailLift = 0f,
            blurRadius = GlassMaterial.labDetailReach,
            legibility = 0f,
            softDetail = 1f,
        )

        /**
         * A menu or popover, the earlier fit on the Photos context menu: the system blur (9.5 dp sigma) with a dark
         * tint, gain 0.24 and a lift of 48/255 that does not adapt to its backdrop, on a screen the host has dimmed
         * to 0.52 behind it (`dimmingLayer` is drawn under the panel, so the host dims the screen). Dark only; see
         * [menu] with `dark` for the lab-form panel fitted on the Safari `<select>` popup in both appearances.
         */
        fun menu(): GlassStyle = GlassStyle(
            contactShadow = 0f,
            backdropSigma = 9.5.dp,
            wideKernel = 0.dp,
            fineShare = 1f,
            tint = Color.Black.copy(alpha = 0.76f),
            tintLift = 48f / 255f,
            bevel = 1.dp,
            specular = 0.15f,
            edgeLight = 1.5f,
            counterLight = 1f,
            invertsWithBackdrop = false,
        )

        /**
         * A Clear-style Home Screen icon over a wallpaper: 2.2 dp sigma, gain about 0.5 with a
         * lift of about 60/255 of the dimmed wallpaper, a rim on the top edge and a darker
         * contour below. Shell glass: it ignores light/dark and never inverts.
         */
        fun shellIcon(): GlassStyle = GlassStyle(
            contactShadow = 0f,
            blurRadius = 4.dp,
            wideKernel = 0.dp,
            fineShare = 1f,
            tint = Color.Black.copy(alpha = 0.5f),
            tintLift = 60f / 255f,
            bevel = 1.5.dp,
            specular = 0.35f,
            counterLight = 0.1f,
            edgeLight = 1.5f,
            edgeShadow = 0.06f,
            invertsWithBackdrop = false,
        )

        /**
         * An iOS 27 Control Center tile (a round toggle, a wide Focus pill, the media tile, a slider track) over the
         * Control Center layer. Shell glass: it ignores light/dark and never inverts, and carries one measured tint, so
         * it has no `dark` and no tint amount.
         *
         * Measured on `CC-over-lab-dark.MP4` (iPhone, 59.94 fps, 58 frames from 4 to 118 s, 46 grey frames and 8 colour
         * phases), against the layer under the tile G, read in the gap beside it:
         * - Inactive: the interior is the straight map `51.1 + 0.903 G` (rms 0.26 levels on the toggle, 0.34 on the media
         *   tile), which is black at [tint] opacity 0.097 plus a [tintLift] of 51.1/255 (fitted).
         * - No blur: the tile is a pointwise map of the layer beneath, same edge ramp inside as in the gap, so
         *   [blurRadius] and [wideKernel] are 0 and [fineShare] 1.
         * - Colour: about its luma the tile keeps 1.58 times the layer's chroma (sd 0.06 over 8 hues, n = 16), set as
         *   [chromaGain] = 1.58. The plain tint alone would leave 0.903 of it.
         * - Active: a near-opaque body at 250 whatever the grey beneath (fitted `249.8 + 0.022 G`, rms 0.14, 47 frames) that
         *   keeps 0.50 of the layer's chroma about that grey (from the active tile's readings over the eight colour phases,
         *   unclipped channels only: 0.504, sd 0.03, n = 8; over yellow the body's blue reads 202). Built as a white tint at
         *   full opacity, which the shader caps at 0.95, so [tintLift] 5/255 (authored) puts the body back at 250 within
         *   2.5 levels from G 10 to 102, and [chromaGain] 0.5.
         *
         * - Rim and contour (fitted by sector on the toggle, 3 grey frames, then checked by `GlassControlCenterRimTest`): iOS lights
         *   the tile's top and bottom equally and its sides not at all, so the light is vertical ([GlassLight] `(0, -1)`;
         *   the default light tilts 20 degrees and lights one side too, so this style takes the vertical light itself whenever the
         *   host passes the default one), with
         *   [counterLight] 1. The pole line peaks 155 / 215 over a layer of 10 / 56 (the reading at 102 clips at 255), the
         *   diagonals about 100 / 150 / 200 and the sides +3; [bevel] 1.25 dp, [bevelPeak] 0.3, [specular] 0.3, [specularPower]
         *   4 and [edgeLight] 1.5 give 160 / 211 / 251 at the poles (within 5), 109 / 155 / 201 on the diagonals (within 7), but +7 to +9 over
         *   the measured line at the sides.
         *   Its dark contour lies outside the outline at the sides and diagonals and is absent at the poles: [outerContour] 1
         *   with no floor, [innerShadow] and [edgeShadow] 0. Known gap: the measured contour is darker and about 2 px wide
         *   (24 / 7 / 0 at the sides over a layer of 102 / 56 / 10), the shader's band 1.5 px and at most 0.67 deep
         *   (59 / 37 / 7); the glow reaching 6 px in at the poles is 12 to 18 levels short.
         *
         * Not measured, so authored: the active tile's rim (clipped at 255 over every layer), press and hold, the light
         * appearance and the Tint Amount response.
         *
         * Geometry is the host's to draw, measured on the same take at 3x: a round toggle 67 dp across (a circle), a wide
         * tile 149 x 67 dp (a pill, radius 33.5 dp), the media tile 149 x 149 dp with radius 29.6 dp, a slider track
         * 67 x 149 dp, 15 dp between tiles. The glass is the shell's, so [Modifier.liquidGlass] takes a rounded shape of
         * that size.
         */
        fun controlCenterTile(active: Boolean = false): GlassStyle = GlassStyle(
            contactShadow = 0f,
            blurRadius = 0.dp,
            backdropSigma = 0.dp,
            wideKernel = 0.dp,
            fineShare = 1f,
            tint = if (active) Color.White else Color.Black.copy(alpha = 0.097f),
            tintLift = if (active) 5f / 255f else 51.1f / 255f,
            chromaGain = if (active) 0.5f else 1.58f,
            bevel = 1.25.dp,
            bevelPeak = 0.3f,
            specular = 0.3f,
            specularPower = 4f,
            counterLight = 1f,
            edgeLight = 1.5f,
            fresnel = 0f,
            innerShadow = 0f,
            edgeShadow = 0f,
            outerContour = 1f,
            invertsWithBackdrop = false,
        )

        /**
         * The dock: nearly clear, a 1 dp sigma, gain 0.85 with a lift of 13/255, and stripes that
         * bend only over its rounded ends. Shell glass.
         */
        fun dock(): GlassStyle = GlassStyle(
            contactShadow = 0f,
            blurRadius = 2.dp,
            wideKernel = 0.dp,
            fineShare = 1f,
            tint = Color.Black.copy(alpha = 0.15f),
            tintLift = 13f / 255f,
            bevel = 1.dp,
            specular = 0.12f,
            counterLight = 1f,
            edgeLight = 1.2f,
            invertsWithBackdrop = false,
        )

        /**
         * The cover sheet's edge: a sharp, undimmed interior (the sheet shows its own content
         * at 0.97) with the full fold lens along its rim, a thin dark line over bright content
         * and a faint bright rim over dark. What the sheet covers is dimmed by
         * [GlassMaterial.coverSheetDim], which the host draws beneath the sheet's edge, not
         * under the sheet. Drive `lensFormation` from the tracked pull.
         */
        fun coverSheet(): GlassStyle = GlassStyle(
            blurRadius = 0.dp,
            wideKernel = 0.dp,
            fineShare = 1f,
            tint = Color.Black.copy(alpha = 0.03f),
            bevel = 3.dp,
            specular = 0.10f,
            counterLight = 1f,
            edgeLight = 1.5f,
            edgeShadow = 0.10f,
            innerShadow = 0.04f,
            contactShadow = 0f,
            invertsWithBackdrop = false,
        )

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
            profile = GlassProfile.Legacy,
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
            profile = GlassProfile.Legacy,
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
            edgeLight = 5.1f,
            fresnel = 0f,
            innerShadow = 0f,
            // Measured from a device, so it keeps the additive highlight it was measured with
            // until there is a capture that says otherwise.
            highlightChroma = 0f,
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
            profile = GlassProfile.Legacy,
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
            profile = GlassProfile.Legacy,
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
 * `y` is negative upward, matching Compose's coordinate system. The default is straight above,
 * `(0, -1)`: every resting iOS 27 rim measured (the Safari lab stills, the recorded tab bars, Control
 * Center) is lit the same at its top and bottom edges and not at all at its sides, which is what a
 * light straight above draws. Before 1.1.0 the default was the authored `(-0.35, -0.94)`.
 */
@Immutable
data class GlassLight(val x: Float = 0f, val y: Float = -1f) {
    companion object {
        val Default = GlassLight()
    }
}
