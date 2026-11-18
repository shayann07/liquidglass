package com.wexpa.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
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
 * of its own and never samples anything, so it costs a gradient. It is always drawn, whatever the
 * scroll position; to draw iOS 27's measured effect, tied to the scroll state, put
 * [glassScrollEdge] on the scrolling container instead.
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


/** Which edges of a scrolling container [glassScrollEdge] draws. */
enum class GlassScrollEdges { Top, Bottom, Both }

/**
 * The scroll edge effect of iOS 27, measured on Safari's top edge: content scrolled under the chrome is
 * blurred, progressively, and in a dark appearance tinted toward grey. Put it on the scrolling container itself;
 * it draws over the content, under sibling chrome, and never consumes scroll.
 *
 * - **Blur** (both appearances, measured): the content's edges stay smeared across the band and are sharp again
 *   [height] (35 dp measured) from the edge. Two blurred copies of the content, 3.3 dp and 1 dp, are laid over
 *   the sharp content with masks fitted to the measured edge widths. Android before 12 draws no blur (the
 *   platform has no render effect there), only the tint.
 * - **Tint** ([inverted], the dark appearance, measured): a blend toward grey 124 at alpha 0.41, flat for the first
 *   10 dp and gone by 34 dp. In a light appearance there is no tint.
 * - **Intensity** ([topIntensity], [bottomIntensity], 0 to 1, read in the draw phase, so scrolling never
 *   recomposes): zero draws nothing, and the content is drawn as if the modifier were absent. The ramp from the
 *   scroll state is authored (24 dp of scroll), as is the bottom edge, a mirror of the top: the measured take has
 *   neither a clean bottom edge nor the ramp-in.
 *
 * With [style] = [GlassScrollEdgeStyle.Hard] it draws the uniform band of the [GlassScrollEdge] composable instead
 * (authored strength, in [ground], no blur). [ground] is used by that style only: the measured effect does not fade
 * toward the app's ground.
 *
 * @param ground the app's ground, for [GlassScrollEdgeStyle.Hard].
 * @param height the band, from the edge to where the effect is gone; the measured profile scales with it. 35 dp is
 *   the measured band; a taller bar's distance is measured from its own edge in iOS, which is not measured here.
 * @param inverted the dark appearance's tint; the same signal as [LiquidGlassState.inversion] and the appearance.
 */
fun Modifier.glassScrollEdge(
    ground: Color,
    edges: GlassScrollEdges = GlassScrollEdges.Top,
    height: Dp = GlassScrollEdgeProfile.BandDp.dp,
    style: GlassScrollEdgeStyle = GlassScrollEdgeStyle.Soft,
    inverted: Boolean = false,
    topIntensity: () -> Float,
    bottomIntensity: () -> Float = topIntensity,
): Modifier = composed {
    val src = rememberGraphicsLayer()
    val top = rememberEdgeLayers()
    val bottom = rememberEdgeLayers()
    drawWithContent {
        val ti = if (edges != GlassScrollEdges.Bottom) topIntensity().coerceIn(0f, 1f) else 0f
        val bi = if (edges != GlassScrollEdges.Top) bottomIntensity().coerceIn(0f, 1f) else 0f
        if (ti <= 0f && bi <= 0f) { drawContent(); return@drawWithContent }
        val band = height.toPx().coerceAtMost(size.height / 2f)
        if (style == GlassScrollEdgeStyle.Hard) {
            drawContent()
            val peak = if (inverted) GlassScrollEdgeProfile.HardAlphaInverted else GlassScrollEdgeProfile.HardAlpha
            if (ti > 0f) drawRect(ground.copy(alpha = peak * ti), Offset.Zero, Size(size.width, band))
            if (bi > 0f) drawRect(ground.copy(alpha = peak * bi), Offset(0f, size.height - band), Size(size.width, band))
            return@drawWithContent
        }
        // The content is recorded once and drawn from the recording, so the strips below read it without a second draw.
        src.record { this@drawWithContent.drawContent() }
        // The profile scales with the band actually drawn, so a container shorter than twice the band ends its effect
        // inside the capped band rather than cutting it off.
        val scale = band / GlassScrollEdgeProfile.BandDp.dp.toPx()
        // Each edge strip is composited on its own (sharp and blurred copies mixed, not stacked), so the sharp content is
        // drawn here only outside the strips: under a transparent page a sharp copy would show through the blur.
        val pad = GlassScrollEdgeProfile.SigmaADp * 3f * density
        val strip = (band + pad).coerceAtMost(size.height)
        val topStrip = if (ti > 0f) strip else 0f
        val bottomStrip = if (bi > 0f) strip else 0f
        if (size.height - topStrip - bottomStrip > 0f) {
            clipRect(0f, topStrip, size.width, size.height - bottomStrip) { drawLayer(src) }
        }
        val w = size.width
        if (ti > 0f && bi > 0f && topStrip + bottomStrip > size.height) {
            // The strips overlap (a container shorter than both): one isolated layer for the whole height, so the
            // bottom's mix works on the top's result instead of repainting the overlap from the sharp content.
            isolated(Rect(0f, 0f, w, size.height), src) {
                mixEdge(src, top, atTop = true, ti, scale, band)
                mixEdge(src, bottom, atTop = false, bi, scale, band)
            }
        } else {
            if (ti > 0f) isolated(Rect(0f, 0f, w, topStrip), src) { mixEdge(src, top, atTop = true, ti, scale, band) }
            if (bi > 0f) isolated(Rect(0f, size.height - bottomStrip, w, size.height), src) { mixEdge(src, bottom, atTop = false, bi, scale, band) }
        }
        if (inverted && ti > 0f) tintEdge(atTop = true, ti, scale, band)
        if (inverted && bi > 0f) tintEdge(atTop = false, bi, scale, band)
    }
}

/** [glassScrollEdge] driven by a [ScrollState]: the top edge fades in over the first 24 dp of scroll, the bottom out over the last 24 dp. */
fun Modifier.glassScrollEdge(
    state: ScrollState,
    ground: Color,
    edges: GlassScrollEdges = GlassScrollEdges.Top,
    height: Dp = GlassScrollEdgeProfile.BandDp.dp,
    style: GlassScrollEdgeStyle = GlassScrollEdgeStyle.Soft,
    inverted: Boolean = false,
): Modifier = composed {
    val ramp = with(androidx.compose.ui.platform.LocalDensity.current) { GlassScrollEdgeProfile.RampDp.dp.toPx() }
    glassScrollEdge(
        ground, edges, height, style, inverted,
        topIntensity = { state.value / ramp },
        bottomIntensity = { (state.maxValue - state.value).coerceAtLeast(0) / ramp },
    )
}

/**
 * [glassScrollEdge] driven by a [LazyListState]: the top edge fades in over the first 24 dp of scroll, the bottom out
 * over the last 24 dp. Reads the first and last visible items only, so it costs the same for any list length.
 * A list knows how far it has scrolled only inside its first visible item, so once that item has scrolled out the edge
 * is at full strength: with a first item shorter than 24 dp the edge rises only to (item height / 24 dp) of full strength
 * while that item scrolls out, then jumps to full.
 */
fun Modifier.glassScrollEdge(
    state: LazyListState,
    ground: Color,
    edges: GlassScrollEdges = GlassScrollEdges.Top,
    height: Dp = GlassScrollEdgeProfile.BandDp.dp,
    style: GlassScrollEdgeStyle = GlassScrollEdgeStyle.Soft,
    inverted: Boolean = false,
): Modifier = composed {
    val ramp = with(androidx.compose.ui.platform.LocalDensity.current) { GlassScrollEdgeProfile.RampDp.dp.toPx() }
    // Content scrolled past the list's start and its end; with reverseLayout the start is the physical bottom.
    val start = { if (state.firstVisibleItemIndex > 0) 1f else state.firstVisibleItemScrollOffset / ramp }
    val end = {
        val info = state.layoutInfo
        val last = info.visibleItemsInfo.lastOrNull()
        when {
            last == null || !state.canScrollForward -> 0f
            last.index < info.totalItemsCount - 1 -> 1f
            else -> (last.offset + last.size - (info.viewportEndOffset - info.afterContentPadding)) / ramp
        }
    }
    glassScrollEdge(
        ground, edges, height, style, inverted,
        topIntensity = { if (state.layoutInfo.reverseLayout) end() else start() },
        bottomIntensity = { if (state.layoutInfo.reverseLayout) start() else end() },
    )
}

/** The layers one edge's strip needs: the two blurred copies of the content. */
private class EdgeLayers(val a: GraphicsLayer, val b: GraphicsLayer)

@Composable
private fun rememberEdgeLayers(): EdgeLayers {
    val a = rememberGraphicsLayer()
    val b = rememberGraphicsLayer()
    return remember(a, b) { EdgeLayers(a, b) }
}

/** [block] in an isolated layer over [rect] that starts as the sharp content [src] there. */
private fun DrawScope.isolated(rect: Rect, src: GraphicsLayer, block: DrawScope.() -> Unit) = drawIntoCanvas { canvas ->
    canvas.saveLayer(rect, Paint())
    clipRect(rect.left, rect.top, rect.right, rect.bottom) { drawLayer(src); block() }
    canvas.restore()
}

/** The strip one edge covers: the band plus room for the wider blur, at most the container's height. */
private fun DrawScope.stripOf(band: Float): Float =
    (band + GlassScrollEdgeProfile.SigmaADp * 3f * density).coerceAtMost(size.height)

/**
 * One edge, inside an isolated layer that already holds the content: B mixed in by its weight, then A by its weight, as
 * the weights are defined. A mix (what is there x (1 - m) + blurred x m), not blurred over sharp, so transparent content
 * blurs too; over opaque content the two are the same. [intensity] scales every strength.
 */
private fun DrawScope.mixEdge(src: GraphicsLayer, layers: EdgeLayers, atTop: Boolean, intensity: Float, scale: Float, band: Float) {
    val density = this.density
    val strip = stripOf(band)
    val stripSize = IntSize(size.width.toInt().coerceAtLeast(1), strip.toInt().coerceAtLeast(1))
    val origin = if (atTop) 0f else size.height - strip
    fun copy(layer: GraphicsLayer, sigmaDp: Float) {
        val sigma = sigmaDp * scale * density
        layer.renderEffect = BlurEffect(platformBlurRadiusForSigma(sigma), platformBlurRadiusForSigma(sigma), TileMode.Clamp)
        layer.record(stripSize) { translate(0f, -origin) { drawLayer(src) } }
    }
    copy(layers.b, GlassScrollEdgeProfile.SigmaBDp)
    copy(layers.a, GlassScrollEdgeProfile.SigmaADp)
    // Distance from the edge at screen y: the strip's own y for the top edge, mirrored for the bottom.
    fun mask(f: (Float) -> Float): Brush {
        val n = 24
        val stops = Array(n + 1) { i ->
            val t = i / n.toFloat()
            val dp = t * strip / density
            (if (atTop) t else 1f - t) to Color.Black.copy(alpha = (f(dp) * intensity).coerceIn(0f, 1f))
        }.sortedBy { it.first }
        return Brush.verticalGradient(*stops.toTypedArray(), startY = origin, endY = origin + strip)
    }
    val rect = Rect(0f, origin, size.width, origin + strip)
    mixIn(layers.b, rect, mask { GlassScrollEdgeProfile.maskB(it, scale) })
    mixIn(layers.a, rect, mask { GlassScrollEdgeProfile.maskA(it, scale) })
}

/** The measured dark tint of one edge: grey 124 at 0.41 times the profile, drawn over the blurred strip. */
private fun DrawScope.tintEdge(atTop: Boolean, intensity: Float, scale: Float, band: Float) {
    val strip = stripOf(band)
    val origin = if (atTop) 0f else size.height - strip
    val n = 24
    val stops = Array(n + 1) { i ->
        val t = i / n.toFloat()
        val dp = t * strip / density
        (if (atTop) t else 1f - t) to GlassScrollEdgeProfile.DarkTintColor.copy(alpha = GlassScrollEdgeProfile.tintAlpha(dp, scale) * intensity)
    }.sortedBy { it.first }
    drawRect(Brush.verticalGradient(*stops.toTypedArray(), startY = origin, endY = origin + strip), Offset(0f, origin), Size(size.width, strip))
}

/** Inside the strip's isolated layer: what is there times (1 - m), plus [layer] times m, m being [mask]'s alpha. */
private fun DrawScope.mixIn(layer: GraphicsLayer, rect: Rect, mask: Brush) {
    drawRect(mask, rect.topLeft, rect.size, blendMode = BlendMode.DstOut)
    drawIntoCanvas { canvas ->
        canvas.saveLayer(rect, Paint().apply { blendMode = BlendMode.Plus })
        translate(rect.left, rect.top) { drawLayer(layer) }
        drawRect(mask, rect.topLeft, rect.size, blendMode = BlendMode.DstIn)
        canvas.restore()
    }
}
