package com.wexpa.liquidglass

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Response families. Calm is authored and opt-in; Expressive preserves existing timing. */
enum class GlassResponse { Expressive, Calm }

/** Material-only drag geometry, independent of press expansion and animation timing. */
enum class GlassPullShape {
    /** Size-aware control/card feedback; large surfaces keep the existing 2dp extension cap. */
    Adaptive,
    /** Coupled stretch/narrowing for a navigation surface. At most 4.8% axis stretch before
     * viewport limiting, plus at most5.6% of the short side as a material-only directional bias.
     * These authored bounds fit selected Phone edges; finger gain is not identified.
     * Layout and ordinary labels stay fixed. Generic cards should keep Adaptive. */
    AreaPreserving,
    /**
     * A free-standing button pulled off its place, as iOS 26's Phone search button is: the body
     * follows the finger by [GlassInteraction.pullFollow] (resisted by [GlassInteraction.pullLimit]),
     * stretching along the pull and thinning across it, and its content moves and stretches with
     * it. Drawing only: layout, hit targets and pointer coordinates stay where they were. The
     * backdrop is still read at the button's layout position. Measured on IMG_6756 (display
     * colours, 2026-10-15): the body rises up to 114 px, 297-303 px tall and 158-166 px wide.
     */
    Free,
}

/** Where the finger is on a panel and how far the press has come up, for the shader. */
@Immutable
internal data class GlassPress(
    val x: Float,
    val y: Float,
    val amount: Float,
    /** The finger's travel since it went down, in the panel's px, while it is still down. */
    val pullX: Float = 0f,
    val pullY: Float = 0f,
    /** Whether the finger is down and still owns the gesture (a scroll that takes it ends this). */
    val pulling: Boolean = false,
    /** Whether the press now drags something (the tab bar's selector); see [GlassInteraction.dragPressShare]. */
    val dragging: Boolean = false,
    val pressCount: Int = 0,
) {
    companion object {
        /** Far enough off-panel that the Gaussian is zero, so an idle panel costs nothing. */
        val None = GlassPress(-1e5f, -1e5f, 0f)
    }
}

/**
 * How a piece of glass responds to being touched.
 *
 * Apple makes this opt-in — an element is `interactive` or it is not — because the response is
 * strong: the element "scales, bounces and shimmers", the material "illuminates from within"
 * starting under the fingertip and spreading to nearby glass, and it has "gel-like flexibility
 * as it moves in tandem with your interaction".
 *
 * Three channels move together and they are deliberately not one animation. The scale is a
 * springy overshoot, because that is the part the hand feels. The glow rises fast and falls
 * slowly, because a press should acknowledge instantly and let go gently. And the touch point
 * itself tracks continuously while down, then freezes where it was and fades, so lifting a
 * finger does not drag the highlight back to the middle of the panel.
 */
@Immutable
data class GlassInteraction(
    /** Legacy press response. Use [Pullable] for the reference-derived balloon/pull. */
    val pressScale: Float = 1.04f,
    val illumination: Float = 1f,
    val gel: Boolean = true,
    /** Zero preserves the Legacy proportional scale; opt-in uses absolute per-side growth. */
    val pressGrowth: Dp = 0.dp,
    val pressLift: Float = 0f,
    /**
     * Press-and-pull: the material deforms independently of its fixed layout. [pullFollow]
     * controls input resistance, not translation of the control. It lengthens by [pullElongation] of the pull's
     * length (relative to its own extent) and thins across it [pullWidthRatio] times as fast,
     * and springs back under-damped on release (`GlassMotion.PullRelease`). The input gains
     * are authored; the recording constrains body deformation ([glassPullDeformation]). A scroll or another node taking the gesture ends the
     * pull at once. Off by default: an element inside a list would squish for the first few
     * pixels of every scroll.
     */
    val pull: Boolean = false,
    val pullFollow: Float = 0.78f,
    /** Elongation as a share of the pull's length, in px of the element's extent (0.45). */
    val pullElongation: Float = 0.45f,
    /** How much faster the width thins than the length grows (1.1). */
    val pullWidthRatio: Float = 1.1f,
    /** Input resistance budget in pressed short-side lengths; layout remains anchored. */
    val pullLimit: Float = Float.POSITIVE_INFINITY,
    /** Timing of geometric feedback; illumination remains a separate acknowledgement. */
    val response: GlassResponse = GlassResponse.Expressive,
    /** Adaptive uses [pullElongation]/[pullWidthRatio] and the size policy. AreaPreserving uses
     * a bounded log-strain response instead; [pullFollow]/[pullLimit] still govern input resistance.
     * Choose the navigation preset normally; generic cards should keep Adaptive. */
    val pullShape: GlassPullShape = GlassPullShape.Adaptive,
    /**
     * While pressed, the side nearer the touch grows further, by this share of the width for a
     * touch at the very end (proportionally less nearer the centre); the far side keeps its
     * ordinary growth (requires [pull]). Zero, the default, grows symmetrically. Calm navigation
     * uses 0.03 in its dark appearance: the iOS 26 Phone bar held at its first tab grows about
     * 15 px more on that side while its far side grows as a plain 1.05 press (2026-10-14).
     */
    val pressReach: Float = 0f,
    /**
     * Release spring for the press, overriding [response]'s when positive: stiffness and damping
     * ratio of the return to rest after the finger lifts. Zero keeps the response's own release.
     * Dark Calm navigation uses 530 / 0.6: the iOS 26 Phone bar returns to rest about 0.12 s
     * after lift with about 10% overshoot (video2, three releases, 2026-10-14). The spring starts
     * one frame in: the finger lifted before the frame that observes it, and Compose shows an
     * animation's first change a frame after its target moves, so without it the return began
     * about 45 ms after the lift where iOS's begins at it.
     */
    val releaseStiffness: Float = 0f,
    val releaseDamping: Float = 1f,
    /**
     * Share of the press (its growth, [pressReach] and pull) that remains while the press drags
     * something, 0 to 1: a tab bar whose selector is being dragged. One keeps the whole press.
     * Dark Calm navigation uses 0.7: once a held iOS 26 Phone bar's lens is dragged, the bar
     * grows about 10 px a side wherever the finger is, against about 25 px while held still
     * (video2, held throws and the reversal episode, 2026-10-14). Authored: its edges fit best at 0.7.
     */
    val dragPressShare: Float = 1f,
    /**
     * Share of the press glow ([pressLift] and [illumination]) that remains once the finger has
     * moved more than 4 dp, reached over 0.35 s; one keeps it all. iOS 26's Phone search button
     * reads 95 pressed and still and settles at 70 once dragged, any distance, against 32 at rest
     * (IMG_6756 in display colours: 0.6 of the lift, 2026-10-15).
     */
    val pullLiftShare: Float = 1f,
    /**
     * [GlassPullShape.Free]'s release damping along x, when it differs from y's
     * ([GlassMotion.FreeRelease], 0.45). NaN, the default, uses the same spring on both axes;
     * [Pullable] uses 0.3, fitted on iOS's sideways releases.
     */
    val pullReleaseDampingX: Float = Float.NaN,
) {
    companion object {
        val Default = GlassInteraction()
        /** Recommended restrained feedback: at most 2dp press growth per edge, 3% press
         * scale and gradual resisted drag. Large surfaces retain the 2dp extension cap.
         * These input/timing choices are authored, not measured Apple finger trajectories. */
        val Calm = GlassInteraction(
            pressScale = 1.03f, pressGrowth = 2.dp, illumination = 0.35f,
            pressLift = 0.04f, pull = true, pullFollow = 0.2f,
            pullElongation = 0.03f, pullWidthRatio = 1f, pullLimit = 0.5f,
            response = GlassResponse.Calm,
        )
        /** Touch expansion is independent of drag. The modifier preserves small controls and
         * smoothly reduces drag strain on larger surfaces; see docs/generic-interaction.md. */
        val Pullable = GlassInteraction(
            // Original S01: 186 -> 238 peak -> 234 settled; independent K01: 234 -> 286
            // peak -> 282 settled. Eight points per side is the equilibrium, not 8.7.
            pressScale = 1.26f, pressGrowth = 8.dp, pressLift = 0.2f, pull = true,
            // Authored input compliance; 0.124 / 0.2 = 0.62 length change per body travel. 0.2 since
            // 18 October (was 0.35): the owner's device check found the held search button far too
            // eager to follow a swipe against iOS's; the recordings have no finger trace to fit it.
            pullFollow = 0.2f, pullElongation = 0.124f, pullWidthRatio = 1f, pullLimit = 0.5f,
            // Released sideways, iOS's search button springs further past its place than upward (IMG_6756
            // 19-34 s, three releases: glyph +6 px against our +2 at 0.45; 18 October): x damping 0.3 scores
            // centre 2.3 px rms (2.6), half-width 2.2 (2.7) and its narrowest 83 px (86; iOS 78).
            pullReleaseDampingX = 0.3f,
        )
        /**
         * What Apple's Reduce Motion asks for: "decreases the intensity of some effects and
         * disables any elastic properties". So the scale, the bounce, the gel and the pull go,
         * and the glow stays at half strength — the feedback survives, the elasticity does not.
         */
        val ReducedMotion = GlassInteraction(pressScale = 1f, illumination = 0.5f, pressLift = 0f, gel = false, pull = false)
    }
}

/** Internal corrections for the existing free-button role; no constructor or copy ABI changes. */
internal val GlassInteraction.pressFlash: Boolean get() = pull && pullShape == GlassPullShape.Free
internal val GlassInteraction.uprightPull: Boolean get() = pull && pullShape == GlassPullShape.Free

/** Springs and timings for the press, kept together so the three channels cannot drift apart. */
internal object GlassMotion {
    val CalmDown = spring<Float>(dampingRatio = 1f, stiffness = 220f)
    val CalmUp = spring<Float>(dampingRatio = 1f, stiffness = 260f)
    val CalmFollow = spring<Float>(dampingRatio = 1f, stiffness = 350f)
    /**
     * The Legacy press is unchanged. The opt-in balloon is fitted to native-PTS S01 extent:
     * 0.62 damping, 644 stiffness, RMS 0.83 px. Independent K01 width gives 0.63 / 595 and
     * confirms the same 8 pt settled growth. The visible peak is about 8% above that growth,
     * not the equilibrium. Evidence: refinement-r6/press-fit-and-holdout.json.
     */
    val PressDown = spring<Float>(dampingRatio = 0.55f, stiffness = 900f)
    val BalloonDown = spring<Float>(dampingRatio = 0.62f, stiffness = 644f)
    /** Coming back is calmer than going down; a bouncy release reads as a bug. */
    val PressUp = spring<Float>(dampingRatio = 0.72f, stiffness = 700f)
    /** The pull tracks the finger stiffly while it is down. */
    val PullFollow = spring<Float>(dampingRatio = 1f, stiffness = 2500f)
    /**
     * Native-PTS centroid fit of IMG_6756 S01: decay 8.39/s, damped frequency 14.1 rad/s.
     * Stiffness is alpha^2 + omegaD^2, NOT omegaD^2. Display-threshold sensitivity gives
     * 268–286 and damping 0.49–0.51; use the two low-threshold fits (RMS 1.2–1.3 px).
     * Evidence: astra-independent/refinement-r6/original-release-fit.json.
     */
    val PullRelease = spring<Float>(dampingRatio = 0.51f, stiffness = 269f)

    /**
     * [GlassPullShape.Free]'s release. The same S01-family recordings fitted on the body's NEAR edge
     * (the one leading the pull), which is what the travel drives once the far edge holds at rest
     * ([glassFreeLanding]): four upward releases of IMG_6756 in display colours, 4.8 px rms, damping
     * 0.45, 16.25 rad/s (2026-10-15). The centroid fit above reads more damped because the floored
     * far edge halves the centroid's overshoot.
     */
    val FreeRelease = spring<Float>(dampingRatio = 0.45f, stiffness = 264f)

    /** Geometry during a morph: no visible overshoot on a large surface. */
    val Morph = spring<Float>(dampingRatio = 0.82f, stiffness = 380f)

    /** The glow rises fast enough to feel instant. */
    val GlowIn = tween<Float>(durationMillis = 90)

    /** And falls slowly enough that lifting off is not a snap. */
    val GlowOut = tween<Float>(durationMillis = 260)

    /**
     * [GlassPullShape.Free]'s glow after the lift, and its settling to [GlassInteraction.pullLiftShare]
     * once dragged: IMG_6756's search button fades from 69 to 33 in 0.35 s with this curve, within
     * 5% of the remaining lift at every frame of four releases (display colours, 2026-10-15).
     */
    val FreeGlow = tween<Float>(durationMillis = 350)

    /**
     * Materialising in and out. Apple: "instead of fading, Liquid Glass objects materialize in
     * and out by gradually modulating the light bending and lensing", and the UIKit guidance is
     * to prefer setting the effect over setting alpha. So this drives `uMaterialize`, which
     * scales displacement, scatter, tint, mirror, specular and shadow together; at 0 the shader
     * returns the backdrop untouched and the element is gone without alpha being involved.
     */
    val MaterializeIn = tween<Float>(durationMillis = 320, easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f))
    val MaterializeOut = tween<Float>(durationMillis = 240, easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f))

    /** Reduce Motion: no spring, no scale, just a short honest fade of the material itself. */
    val MaterializeReduced = tween<Float>(durationMillis = 160)

    const val DEFAULT_STIFFNESS = Spring.StiffnessMedium
}

/**
 * Lets a caller that already owns the gesture drive a panel's press response.
 *
 * `Modifier.liquidGlass` normally tracks its own pointer, which is right for a panel that is
 * also the thing you touch. It is wrong whenever some *other* node owns the gesture — a
 * selection indicator inside a tab bar is the case that forced this: the indicator sits beneath
 * the tab buttons, so it never sees a touch, and the bar has to drag it. Without a way in, such
 * an element can be moved but can never light up, which is exactly the half-finished feel of an
 * indicator that animates but does not respond.
 *
 * Positions are in the **panel's** local pixels, not the gesture owner's.
 */
@Stable
class GlassPressSource internal constructor() {
    internal var localPosition by mutableStateOf(Offset.Zero)
    internal var pullOffset by mutableStateOf(Offset.Zero)
    internal var isPressed by mutableStateOf(false)
    /** The press now drags something ([GlassInteraction.dragPressShare]); cleared on release. */
    internal var dragging by mutableStateOf(false)
    /** A caller's critically damped press-down stiffness for a Calm response; zero keeps [GlassMotion.CalmDown]. */
    internal var downStiffness: Float = 0f
    internal var pressCount by mutableIntStateOf(0)
        internal set

    /** Call on every pointer move as well as on down, so the glow tracks rather than jumps. */
    fun press(localPosition: Offset, pullOffset: Offset = Offset.Zero) {
        require(localPosition.x.isFinite() && localPosition.y.isFinite() &&
            pullOffset.x.isFinite() && pullOffset.y.isFinite()) { "Press coordinates must be finite" }
        if (!isPressed) {
            pressCount++
        }
        this.localPosition = localPosition
        this.pullOffset = pullOffset
        isPressed = true
    }

    /**
     * The position is deliberately left where it was: the glow fades from where the finger
     * lifted rather than sliding back to the middle of the panel.
     */
    fun release() {
        isPressed = false
        dragging = false
    }
}

@Composable
fun rememberGlassPressSource(): GlassPressSource = remember { GlassPressSource() }

/**
 * Tracks a press for [Modifier.liquidGlass], returning the point and amount the shader wants.
 *
 * With no [source] it watches its own pointer, and observes rather than consumes, so an element
 * can be both interactive glass and a normal button without the two fighting over the gesture.
 * With one, the caller is driving and this only animates the amount.
 */
@Composable
internal fun rememberGlassPress(
    enabled: Boolean,
    source: GlassPressSource?,
    /** [GlassInteraction.pullLiftShare]; with [freeGlow], [GlassMotion.FreeGlow] after the lift. */
    pullLiftShare: Float = 1f,
    freeGlow: Boolean = false,
    /** px past which the finger has moved for [pullLiftShare]. */
    moveThreshold: Float = Float.POSITIVE_INFINITY,
): Pair<GlassPress, Modifier> {
    if (!enabled) return GlassPress.None to Modifier

    if (source != null) {
        val amount by animateFloatAsState(
            targetValue = if (source.isPressed) 1f else 0f,
            animationSpec = if (source.isPressed) GlassMotion.GlowIn else GlassMotion.GlowOut,
            label = "glass_press_amount_external",
        )
        return GlassPress(source.localPosition.x, source.localPosition.y, amount,
            source.pullOffset.x, source.pullOffset.y, pulling = source.isPressed, dragging = source.dragging,
            pressCount = source.pressCount) to Modifier
    }

    var point by remember { mutableStateOf(Offset.Zero) }
    var down by remember { mutableStateOf(false) }
    var downPoint by remember { mutableStateOf(Offset.Zero) }
    var pull by remember { mutableStateOf(Offset.Zero) }
    var pulling by remember { mutableStateOf(false) }
    var moved by remember { mutableStateOf(false) }
    var pressCount by remember { mutableIntStateOf(0) }
    val amount by animateFloatAsState(
        targetValue = if (!down) 0f else if (moved) pullLiftShare.coerceIn(0f, 1f) else 1f,
        animationSpec = when {
            down && moved -> GlassMotion.FreeGlow
            down -> GlassMotion.GlowIn
            freeGlow -> GlassMotion.FreeGlow
            else -> GlassMotion.GlowOut
        },
        label = "glass_press_amount",
    )

    val modifier = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val first = awaitFirstDown(requireUnconsumed = false)
            point = first.position
            downPoint = first.position
            pull = Offset.Zero
            moved = false
            down = true
            pulling = true
            pressCount++
            try {
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == first.id } ?: break
                    if (!change.pressed || (change.isConsumed && change.position != change.previousPosition)) break
                    point = change.position
                    pull = change.position - downPoint
                    if (pull.getDistance() > moveThreshold) moved = true
                }
            } finally {
                down = false
                pulling = false
            }
        }
    }

    return GlassPress(point.x, point.y, amount, pull.x, pull.y, pulling, pressCount = pressCount) to modifier
}
