package com.wexpa.liquidglass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Shared state linking a backdrop to the glass panels drawn over it.
 *
 * One state serves any number of panels: the backdrop is recorded once per frame and each
 * panel samples the region beneath itself out of that single recording.
 */
@Stable
class LiquidGlassState internal constructor(
    /**
     * The opaque colour behind the recorded backdrop.
     *
     * A backdrop layer is transparent wherever the recorded subtree painted nothing, and the
     * glass has to reconstruct what a viewer actually sees there, which is this colour showing
     * through. Getting it wrong is visible: too dark and the panels grow a black halo, too
     * light and they bloom.
     */
    internal val background: Color,
) {
    internal var layer: GraphicsLayer? by mutableStateOf(null)
    internal var sourceCoordinates: LayoutCoordinates? by mutableStateOf(null)
    internal var sourceSize: Size by mutableStateOf(Size.Zero)

    /** True once a backdrop has been recorded and panels can sample it. */
    val isReady: Boolean get() = layer != null && sourceSize != Size.Zero
}

@Composable
fun rememberLiquidGlassState(background: Color = Color.Black): LiquidGlassState =
    remember(background) { LiquidGlassState(background) }

/**
 * Marks this content as the backdrop that glass panels refract.
 *
 * The content is recorded into a layer and then drawn from it, so it is rasterised once and
 * read many times. Put this on the thing the glass floats over — usually the scrolling body —
 * and not on an ancestor that also contains the glass, or the panel would sample itself.
 */
fun Modifier.liquidGlassSource(state: LiquidGlassState): Modifier = composed {
    val layer = rememberGraphicsLayer()
    this
        .onGloballyPositioned { coords ->
            state.sourceCoordinates = coords
            state.sourceSize = Size(coords.size.width.toFloat(), coords.size.height.toFloat())
        }
        .drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            state.layer = layer
            drawLayer(layer)
        }
}

/**
 * Draws this element as a piece of glass over the backdrop registered in [state].
 *
 * Where the platform can run the shader the panel is a real optical element: the backdrop is
 * blurred, bent through the bevel, tinted against its own brightness, and lit. Where it
 * cannot — see [LiquidGlassSupport] — it degrades to a tinted surface with the same rim
 * lighting, which is a plainer material rather than a broken one.
 */
fun Modifier.liquidGlass(
    state: LiquidGlassState,
    shape: Shape = RectangleShape,
    style: GlassStyle = GlassStyle.Regular,
    light: GlassLight = GlassLight.Default,
): Modifier = composed {
    val glassLayer = rememberGraphicsLayer()
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    this
        .onGloballyPositioned { coordinates = it }
        .drawWithContent {
            val source = state.layer
            val radii = shape.cornerRadiiPx(size, layoutDirection, this)

            val delta = panelOffsetInSource(state.sourceCoordinates, coordinates)

            if (source != null && delta != null && LiquidGlassSupport.hasShaders) {
                // Apple's material samples "an area larger than itself" — that is what makes it
                // lens rather than merely blur. Record that margin, or displacement at the rim
                // clamps against the panel's own edge and the lensing has nothing to bend.
                val pad = style.refractionDepth.toPx() * 1.4f +
                    maxOf(style.blurRadius.toPx(), style.backdropBlur.toPx())
                val sizeFactor = elementSizeFactor(size, this)
                val effect = createGlassRenderEffect(
                    GlassUniforms(
                        width = size.width,
                        height = size.height,
                        pad = pad,
                        scale = sizeFactor,
                        flip = if (style.invertsWithBackdrop && sizeFactor < 0.5f) 1f else 0f,
                        radii = radii,
                        refractBand = style.refractionBand.toPx(),
                        refractDepth = style.refractionDepth.toPx(),
                        aberration = style.dispersion,
                        bevel = style.bevel.toPx(),
                        backdrop = sampleBounds(pad, delta, size, state.sourceSize),
                        background = state.background,
                        lightX = light.x,
                        lightY = light.y,
                        specular = style.specular,
                        specularPower = style.specularPower,
                        tint = style.tint,
                        innerShadow = style.innerShadow,
                        adaptivity = style.adaptivity,
                        blurRadius = style.blurRadius.toPx(),
                        backdropBlur = style.backdropBlur.toPx(),
                    )
                )
                if (effect != null) {
                    // Record the padded slice of backdrop beneath this panel, in the panel's own
                    // coordinates offset by the pad, then let the chain blur and refract it.
                    val padPx = pad.toInt()
                    glassLayer.record(
                        size = IntSize(
                            (size.width.toInt() + padPx * 2).coerceAtLeast(1),
                            (size.height.toInt() + padPx * 2).coerceAtLeast(1),
                        )
                    ) {
                    // Fill with the ground first. The padded slice reaches past the backdrop
                    // near a screen edge, and the backdrop is itself transparent wherever the
                    // app painted nothing; blurring either kind of hole drags transparency
                    // inward and the panel grows a halo of whatever it composites against.
                    // Baking the ground in once leaves an opaque image for the blur to work
                    // on, which is both correct and cheaper than compensating downstream.
                        drawRect(state.background)
                        translate(-delta.x + pad, -delta.y + pad) { drawLayer(source) }
                    }
                    glassLayer.renderEffect = effect
                    if (style.dimmingLayer > 0f) {
                        drawRoundRect(
                            color = Color.Black.copy(alpha = style.dimmingLayer),
                            cornerRadius = CornerRadius(radii.getOrElse(0) { 0f }),
                            size = size,
                        )
                    }
                    translate(-pad, -pad) { drawLayer(glassLayer) }
                    drawContent()
                    return@drawWithContent
                }
            }

            drawGlassFallback(style, light, radii, style.bevel.toPx())
            drawContent()
        }
}

/** What the current platform can do, so callers can choose a design that survives the gap. */
expect object LiquidGlassSupport {
    /** True when the full optical path — refraction, specular, adaptive tint — can run. */
    val hasShaders: Boolean

    /** True when at least the backdrop can be blurred. */
    val hasBackdropBlur: Boolean
}

/** Corner radii in px, in the order the shader expects: TL, TR, BR, BL. */
internal expect fun Shape.cornerRadiiPx(
    size: Size,
    layoutDirection: LayoutDirection,
    density: Density,
): FloatArray

@Stable
internal data class GlassUniforms(
    val width: Float,
    val height: Float,
    /** Backdrop recorded beyond each edge, so displacement near the rim has content to reach. */
    val pad: Float,
    /** 0 for small chrome, 1 for a large surface. Keys opacity, lensing and shadow depth. */
    val scale: Float,
    /** 1 when this element may invert light/dark with its backdrop. */
    val flip: Float,
    val radii: FloatArray,
    val refractBand: Float,
    val refractDepth: Float,
    val aberration: Float,
    val bevel: Float,
    /** Where inside the padded layer real pixels exist; sampling past it would read nothing. */
    val backdrop: FloatArray,
    /** Opaque ground the recorded backdrop sits on, for the transparent parts of it. */
    val background: Color,
    val lightX: Float,
    val lightY: Float,
    val specular: Float,
    val specularPower: Float,
    val tint: Color,
    val innerShadow: Float,
    val adaptivity: Float,
    val blurRadius: Float,
    val backdropBlur: Float,
) {
    override fun equals(other: Any?): Boolean =
        other is GlassUniforms &&
            width == other.width && height == other.height &&
            pad == other.pad && scale == other.scale && flip == other.flip &&
            radii.contentEquals(other.radii) &&
            refractBand == other.refractBand && refractDepth == other.refractDepth &&
            aberration == other.aberration && backdrop.contentEquals(other.backdrop) &&
            background == other.background &&
            bevel == other.bevel && lightX == other.lightX && lightY == other.lightY &&
            specular == other.specular && specularPower == other.specularPower &&
            tint == other.tint && innerShadow == other.innerShadow &&
            adaptivity == other.adaptivity && blurRadius == other.blurRadius &&
            backdropBlur == other.backdropBlur

    override fun hashCode(): Int = width.hashCode() * 31 + height.hashCode() + radii.contentHashCode()
}

internal expect fun createGlassRenderEffect(
    uniforms: GlassUniforms,
): androidx.compose.ui.graphics.RenderEffect?

/**
 * How "large" a panel reads, 0 to 1.
 *
 * Apple keys the material to element geometry rather than fixing it: bigger glass renders more
 * opaque, with deeper shadow and stronger lensing, while smaller glass renders clearer and is
 * allowed to invert light/dark to hold contrast. The scale below treats a 56dp control as
 * small and a 320dp surface as large, which puts a tab bar near the clear end and a sheet near
 * the opaque one.
 */
/**
 * The region of the padded layer the shader may read, in layer coordinates.
 *
 * Two different things go wrong outside it, which is why the bound is the intersection of two
 * rectangles rather than either one alone.
 *
 * The padded slice is deliberately larger than the panel so displacement near the rim has
 * somewhere to reach, and near a screen edge that slice hangs off the end of the backdrop.
 * Those pixels are not merely dark, they are a flat fill, and a panel that samples them grows
 * a band of solid colour at the rim. So the bound stops at the backdrop.
 *
 * The layer is also filled with the ground before the backdrop is drawn into it. That is what
 * keeps the blur honest: a blur over any transparent margin returns partly-transparent pixels,
 * and those composite as a halo whichever way they are unpacked. So the fill has to be there
 * even though nothing is allowed to sample it.
 *
 * Half a pixel of inset keeps bilinear filtering from reaching across either boundary.
 */
/**
 * Where this panel sits inside the backdrop recording, in the recording's own coordinates.
 *
 * Subtracting two `positionInRoot` values would be the obvious way to compute this and is
 * wrong as soon as anything between the two applies a transform: root positions come back with
 * that transform folded in, while the recorded layer is drawn in untransformed local space, so
 * the two disagree by the transform. An app that scales its content away behind a modal - a
 * 2.5% shrink is typical - would slide every panel's sample off by 2.5% of its distance from
 * the backdrop's origin, which is tens of pixels for chrome at the bottom of a screen.
 *
 * Asking the source for the panel's position in *its* space is both simpler and correct under
 * any transform. Returns null while either node is detached, which happens for a frame around
 * composition changes.
 */
internal fun panelOffsetInSource(
    source: LayoutCoordinates?,
    panel: LayoutCoordinates?,
): Offset? {
    if (source == null || panel == null) return null
    if (!source.isAttached || !panel.isAttached) return null
    return source.localPositionOf(panel, Offset.Zero)
}

internal fun sampleBounds(
    pad: Float,
    delta: Offset,
    panelSize: Size,
    sourceSize: Size,
): FloatArray {
    val layerWidth = panelSize.width + pad * 2f
    val layerHeight = panelSize.height + pad * 2f
    val backdropLeft = pad - delta.x
    val backdropTop = pad - delta.y
    return floatArrayOf(
        maxOf(0f, backdropLeft) + 0.5f,
        maxOf(0f, backdropTop) + 0.5f,
        minOf(layerWidth, backdropLeft + sourceSize.width) - 0.5f,
        minOf(layerHeight, backdropTop + sourceSize.height) - 0.5f,
    )
}

internal fun elementSizeFactor(size: Size, density: Density): Float {
    val minEdgeDp = with(density) { minOf(size.width, size.height).toDp().value }
    return ((minEdgeDp - 56f) / (320f - 56f)).coerceIn(0f, 1f)
}
