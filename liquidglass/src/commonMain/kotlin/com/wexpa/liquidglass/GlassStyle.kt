package com.wexpa.liquidglass

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
 * These are the knobs the shader consumes. The constructor defaults are the measured in-app
 * material in a light appearance at the default Tint Amount, so `GlassStyle()` is a navigation
 * pill as iOS 27 draws one; the factories on the companion build the other measured roles, and
 * the [GlassProfile.Legacy] presets keep the 0.1 material for hosts tuned against it.
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
     * Sigma of the wide kernel, the second of the measured material's two blurs.
     *
     * Across a hard edge inside a navigation pill the tone follows a 10 dp kernel while 16 px
     * stripes under the same glass keep a third of their contrast at the default Tint Amount,
     * which one blur cannot do. So the backdrop term is a mix of the fine kernel and this one,
     * weighted by [fineShare]. Rendered once per frame as a quarter-scale blurred copy of the
     * backdrop; set it to 0 to switch it off.
     */
    val wideKernel: Dp = 10.dp,
    /**
     * Share of the fine kernel in the backdrop term, 0 to 1; the rest is the wide kernel.
     * Measured as `0.95 (1 - t/100)^1.5` against Tint Amount t: 0.95 at Clear, 0.34 at the
     * default, 0 at Tinted, the same in both appearances. See [GlassMaterial.fineShare].
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
     * How the detail seen through the glass is drawn, 0 to 1. At 0 (the default) it is the measured
     * high-pass along the element's long axis (FINDINGS 22), sampled through a per-pixel rotated
     * scatter. At 1 it is a plain soft copy: the wide kernel's tone plus [fineShare] of the fine
     * blur, sampled without rotation. Read in display colours, iOS 26's Phone bar shows page text
     * under it as one soft, legible copy; the along-axis high-pass draws ghost copies of every
     * glyph about 16 and 34 px to either side and the rotation turns the rest into grain
     * (docs/2, horizontal detail 2.72 against iOS's 2.77, 4.94 at 0; 2026-10-15).
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
     * the base of the system backdrops' adaptive lift, 142/255.
     *
     * May be negative when the lift does not adapt ([liftAdaptivity] 0): the resting tab-bar
     * indicator is the bar's output at 0.857 minus 17/255, over black and over content alike.
     */
    val tintLift: Float = 0f,
    /**
     * How far the lift falls with the wide-kernel luma of what is behind the glass, per unit
     * luma. The system backdrops measure 0.864: over the dark test image they lift black to 60,
     * over the light one to 10, with the same gain. In-app glass measures 0.
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
     * outside the outline: the outermost wholly covered pixel matches the body. It is about 1.5 px
     * wide, centred 0.25 px outside. It darkens what is behind it by 0.61 (dark appearance) or 0.31
     * (light appearance) at the sides, and falls to zero at the lit poles: 5.7% of the side value at
     * 20 degrees off the light, 20.5% at 40, 36.2% at 60 and 37.4% at 120 (dark appearance). Here
     * the angular law is `(1 - |cos|)^0.75` of the angle to the light ([GlassLight]). It fits the
     * dark appearance's nine angles within 0.04 of the side value.
     * Whether iOS darkens multiplicatively or additively is not determinable from the captures
     * (over black both are invisible). This draws black at this opacity over the backdrop,
     * which is multiplicative. Lone elements, the tab bar and `GlassEffectContainer`'s merged
     * shapes all draw it.
     */
    val outerContour: Float = 0f,
    /**
     * The share of [outerContour] that remains at the lit poles. The light appearance keeps about
     * 8-9% of the backdrop there (about a quarter of its 31% side value). The dark appearance goes to
     * zero. The light appearance's profile is flatter than the law above between the pole and 60
     * degrees (it reads 8.1% at 20 and 11.3% at 40), so a floor of 0.25 overshoots it there by up to
     * 0.15 of the side value.
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
) {
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

        /** In-app glass, light appearance, default Tint Amount: the measured navigation pill. */
        val Regular = GlassStyle()

        /**
         * In-app glass — a navigation pill, round buttons, a toolbar — for one appearance and
         * one Tint Amount. Every number is measured (`docs/research/measured-model.md`, section
         * 2): opacity from the pill's black and white levels, the fine share from the toolbar's
         * stripes, the tint colour and lift per appearance, the edge line per appearance.
         */
        fun inApp(dark: Boolean, tintAmount: Float = GlassMaterial.DEFAULT_TINT_AMOUNT): GlassStyle {
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
         * A toolbar or tab bar: the Photos toolbar's role, solved from its medians over the test
         * image's stripes with the pill's kernels (`docs/research/measured-model.md`, section 2,
         * "The toolbar role"). It is the pill's material with its own tint. Light: white (the
         * pill's is 241) at 0.55-0.67 opacity against the pill's 0.44-0.73, so at Clear it reads
         * about 142 over black where the pill reads 105. Dark: black at 0.55 at Clear, 0.41 at
         * the default and 0.63 at Tinted over the pill's 35/255 lift, so at Clear its white side
         * reads 152 where the pill's reads 180. Both are more opaque than the pill at Clear and
         * respond less to Tint Amount.
         */
        fun toolbar(dark: Boolean, tintAmount: Float = GlassMaterial.DEFAULT_TINT_AMOUNT): GlassStyle {
            val a = GlassMaterial.toolbarOpacity(dark, tintAmount)
            return inApp(dark = dark, tintAmount = tintAmount).copy(
                tint = if (dark) Color.Black.copy(alpha = a) else GlassMaterial.toolbarLightTint.copy(alpha = a),
            )
        }

        /**
         * A full-screen system backdrop: Control Center, App Library, the Today view. A 7.7 dp
         * sigma over app content (11 dp at Tinted), gain 0.32, and a lift that adapts to what is
         * behind it — 60/255 over the dark test image, 10/255 over the light one — so it reads
         * light over dark content and dark over light. No lens: a full-screen layer has no rim.
         */
        fun systemBackdrop(tintAmount: Float = GlassMaterial.DEFAULT_TINT_AMOUNT): GlassStyle = GlassStyle(
            backdropSigma = GlassMaterial.systemBackdropSigma(tintAmount),
            refractionBand = 0.dp,
            wideKernel = 0.dp,
            fineShare = 1f,
            tint = Color.Black.copy(alpha = 0.68f),
            tintLift = 142f / 255f,
            liftAdaptivity = 0.864f,
            bevel = 0.dp,
            specular = 0f,
            edgeLight = 0f,
            fresnel = 0f,
            innerShadow = 0f,
            contactShadow = 0f,
            invertsWithBackdrop = false,
        )

        /**
         * A menu or popover: the system blur (9.5 dp sigma) with a dark tint, gain 0.24 and a
         * lift of 48/255 that does not adapt to its backdrop, on a screen the host has dimmed to
         * 0.52 behind it (`dimmingLayer` is drawn under the panel, so the host dims the screen).
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
