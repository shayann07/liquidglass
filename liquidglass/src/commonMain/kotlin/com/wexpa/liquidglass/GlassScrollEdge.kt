package com.wexpa.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** How the scroll edge effect dissolves content beneath floating chrome. */
enum class GlassScrollEdgeStyle {
    /**
     * A gradient that ramps up over the overlap and a little beyond. The default, and what
     * suits a toolbar the content scrolls continuously under.
     */
    Soft,

    /**
     * Uniform across the whole band. For chrome with a hard bottom edge, where a gradient
     * would read as a smudge rather than as a boundary.
     */
    Hard,
}

/**
 * The scroll edge effect: content dissolving into the ground beneath floating chrome.
 *
 * This is **not** the glass material, and conflating the two is the usual mistake. Apple ships
 * it as a separate object — `UIScrollEdgeEffect` on the scroll view, not on the glass — because
 * it solves a different problem: the material makes chrome legible against what is behind it,
 * and this keeps the content from appearing to slide out from under the chrome's edge. An app
 * can want either without the other.
 *
 * Place it as a sibling directly beneath the chrome, matched to the overlap. It has no backdrop
 * of its own and never samples anything, so it costs a gradient.
 *
 * The [inverted] flag exists because Apple swaps the fade for a dimming when dark content
 * pushes the glass into its dark style — a fade to the ground colour over dark content does
 * nothing visible, so the effect would silently stop working exactly when it is needed. Drive
 * it from the same signal as [LiquidGlassState.inversion] so the two stay in step.
 */
@Composable
fun GlassScrollEdge(
    /** The colour the content dissolves into: the app's own ground. */
    ground: Color,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    style: GlassScrollEdgeStyle = GlassScrollEdgeStyle.Soft,
    inverted: Boolean = false,
    /** Which way the chrome sits: true when it floats at the bottom of the screen. */
    fromBottom: Boolean = false,
) {
    val peak = if (inverted) 0.18f else 0.90f
    val brush = when (style) {
        GlassScrollEdgeStyle.Hard -> Brush.verticalGradient(
            0f to ground.copy(alpha = peak),
            1f to ground.copy(alpha = peak),
        )
        // Quadratic rather than linear: a linear ramp reads as a grey band with two visible
        // ends, where a quadratic one has no edge you can point at.
        GlassScrollEdgeStyle.Soft -> {
            val stops = List(6) { i ->
                val t = i / 5f
                val a = if (fromBottom) t * t else (1f - t) * (1f - t)
                t to ground.copy(alpha = a * peak)
            }
            Brush.verticalGradient(*stops.toTypedArray())
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(brush),
    )
}

/**
 * The drop shadow a piece of glass casts, as parameters rather than as a draw call.
 *
 * Apple keys the shadow to element size — a larger surface sits further off its background —
 * and raises it as busy content scrolls underneath. Compose has no common shadow API that takes
 * a colour and a blur, so this hands back the numbers and lets the caller apply them however
 * its platform allows.
 */
@Immutable
data class GlassShadow(
    val alpha: Float,
    val blurRadius: Dp,
    val offsetY: Dp,
)

/**
 * [sizeFactor] is `elementSizeFactor` — 0 for small chrome, 1 for a large surface — and
 * [contrast] is how busy the backdrop is, 0 for a flat ground and 1 for text scrolling under.
 */
fun glassShadow(sizeFactor: Float, contrast: Float = 0f): GlassShadow {
    val scale = sizeFactor.coerceIn(0f, 1f)
    return GlassShadow(
        alpha = glassShadowAlpha(contrast),
        blurRadius = (18f * (0.6f + 0.6f * scale)).dp,
        offsetY = (4f * (0.5f + 0.9f * scale)).dp,
    )
}
