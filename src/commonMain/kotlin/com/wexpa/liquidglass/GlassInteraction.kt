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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput

/** Where the finger is on a panel and how far the press has come up, for the shader. */
@Immutable
internal data class GlassPress(
    val x: Float,
    val y: Float,
    val amount: Float,
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
    /** Peak scale while held. Apple gives no number; this is a press you can see and not feel. */
    val pressScale: Float = 1.04f,
    /** Luminance lift under the fingertip at the peak. Past about 0.1 this reads as a flash. */
    val illumination: Float = 1f,
    /** Whether the element squashes along the drag axis while it is moved. */
    val gel: Boolean = true,
) {
    companion object {
        val Default = GlassInteraction()

        /**
         * What Apple's Reduce Motion asks for: "decreases the intensity of some effects and
         * disables any elastic properties". So the scale, the bounce and the gel go, and the
         * glow stays at half strength — the feedback survives, the elasticity does not.
         */
        val ReducedMotion = GlassInteraction(pressScale = 1f, illumination = 0.5f, gel = false)
    }
}

/** Springs and timings for the press, kept together so the three channels cannot drift apart. */
internal object GlassMotion {
    /** One visible overshoot of about a percent, settling in roughly 220ms. */
    val PressDown = spring<Float>(dampingRatio = 0.55f, stiffness = 900f)

    /** Coming back is calmer than going down; a bouncy release reads as a bug. */
    val PressUp = spring<Float>(dampingRatio = 0.72f, stiffness = 700f)

    /** Geometry during a morph: no visible overshoot on a large surface. */
    val Morph = spring<Float>(dampingRatio = 0.82f, stiffness = 380f)

    /** The glow rises fast enough to feel instant. */
    val GlowIn = tween<Float>(durationMillis = 90)

    /** And falls slowly enough that lifting off is not a snap. */
    val GlowOut = tween<Float>(durationMillis = 260)

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
    internal var isPressed by mutableStateOf(false)

    /** Call on every pointer move as well as on down, so the glow tracks rather than jumps. */
    fun press(localPosition: Offset) {
        this.localPosition = localPosition
        isPressed = true
    }

    /**
     * The position is deliberately left where it was: the glow fades from where the finger
     * lifted rather than sliding back to the middle of the panel.
     */
    fun release() {
        isPressed = false
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
): Pair<GlassPress, Modifier> {
    if (!enabled) return GlassPress.None to Modifier

    if (source != null) {
        val amount by animateFloatAsState(
            targetValue = if (source.isPressed) 1f else 0f,
            animationSpec = if (source.isPressed) GlassMotion.GlowIn else GlassMotion.GlowOut,
            label = "glass_press_amount_external",
        )
        return GlassPress(source.localPosition.x, source.localPosition.y, amount) to Modifier
    }

    var point by remember { mutableStateOf(Offset.Zero) }
    var down by remember { mutableStateOf(false) }
    val amount by animateFloatAsState(
        targetValue = if (down) 1f else 0f,
        animationSpec = if (down) GlassMotion.GlowIn else GlassMotion.GlowOut,
        label = "glass_press_amount",
    )

    val modifier = Modifier.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull()
                if (change == null) continue
                if (change.pressed) {
                    // Track continuously: a drag should carry the highlight with it.
                    point = change.position
                    down = true
                } else {
                    // Freeze where the finger left, and let the glow fade from there rather
                    // than snapping back to the centre of the panel.
                    down = false
                }
            }
        }
    }

    return GlassPress(point.x, point.y, amount) to modifier
}
