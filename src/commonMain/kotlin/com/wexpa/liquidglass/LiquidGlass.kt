package com.wexpa.liquidglass

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
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
    /**
     * How far elements that allow it should invert light/dark, 0 to 1.
     *
     * Apple treats this as one decision per element rather than a per-pixel one, because
     * symbols and labels drawn *on* the glass flip in lockstep with it, and a fragment shader
     * behind them cannot reach them. So the app decides — from whatever it knows about its own
     * backdrop — and hands the same number to the glass and to its content. A permanently dark
     * app leaves it at 0 and nothing ever flips.
     */
    val inversion: Float,
    /**
     * Reduce Transparency, 0 to 1.
     *
     * Android has no system setting for this — iOS does, and Apple's material honours it — so
     * it is whatever in-app control the app exposes. It raises the material's opacity rather
     * than switching it off, which keeps the shape and the edge lighting for anyone who wants
     * to keep seeing them.
     */
    val frost: Float,
    /**
     * Increase Contrast, 0 to 1.
     *
     * Maps to `AccessibilityManager.isHighTextContrastEnabled` on Android, which is about text
     * rather than materials, so it is an approximation of Apple's setting rather than the same
     * thing. The material goes predominantly black or white with a contrasting border.
     */
    val contrast: Float,
) {
    internal var layer: GraphicsLayer? by mutableStateOf(null)
    internal var sourceCoordinates: LayoutCoordinates? by mutableStateOf(null)
    internal var sourceSize: Size by mutableStateOf(Size.Zero)

    /** True once a backdrop has been recorded and panels can sample it. */
    val isReady: Boolean get() = layer != null && sourceSize != Size.Zero
}

@Composable
fun rememberLiquidGlassState(
    background: Color = Color.Black,
    inversion: Float = 0f,
    frost: Float = 0f,
    contrast: Float = 0f,
): LiquidGlassState = remember(background, inversion, frost, contrast) {
    LiquidGlassState(background, inversion, frost, contrast)
}

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
    /**
     * Whether the element responds to touch. Opt-in, as Apple makes it: the response is strong
     * enough that applying it to everything would be noise. See [GlassInteraction].
     */
    interaction: GlassInteraction? = null,
    /**
     * Whether the material is present, 0 to 1, for materialising in and out.
     *
     * Apple: "instead of fading, Liquid Glass objects materialize in and out by gradually
     * modulating the light bending and lensing", and the UIKit guidance is to prefer setting
     * the effect over setting alpha. Drive this instead of `alpha` and the element arrives by
     * becoming glass rather than by becoming opaque.
     */
    materialize: Float = 1f,
    /**
     * Drive the press from outside instead of letting this element watch its own pointer.
     *
     * For when another node owns the gesture — an indicator inside a bar that the bar drags.
     * See [GlassPressSource].
     */
    pressSource: GlassPressSource? = null,
    /**
     * Draw this element's content *inside* the glass rather than on top of it.
     *
     * Normally content sits on the material: labels on a button, icons on a bar. Some content
     * is the material's subject instead — what a magnifier is held over, the symbol a slider
     * knob carries, the tab a selection lens has slid across — and that content has to be
     * bent, colour-split and lit along with everything else the glass shows. Apple's tab bar
     * does exactly this: as the selection indicator crosses a symbol, the symbol is seen
     * through the lens, and its edges fringe where the rim refracts them.
     *
     * With this on, the content gets its own pass through the same distance field, bevel and
     * dispersion the backdrop goes through, and is drawn over the material: bent and
     * colour-split where the rim refracts it, clipped to the shape, and otherwise untouched —
     * the glass dims what is behind it, not what is printed inside it, which is what the
     * reference shows. Anything positioned outside the shape simply is not seen, which is what
     * lets a lens carry a copy of a whole row and show only the part it is over.
     *
     * Where the shader cannot run the content is drawn on top instead, as it always was.
     */
    refractContent: Boolean = false,
): Modifier = composed {
    val glassLayer = rememberGraphicsLayer()
    val contentLayer = rememberGraphicsLayer()
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val (press, pressModifier) = rememberGlassPress(
        enabled = interaction != null,
        source = pressSource,
    )

    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val measured = coordinates?.let { Size(it.size.width.toFloat(), it.size.height.toFloat()) }
        ?: Size.Zero
    val padForField = with(density) {
        style.refractionDepth.toPx() * 1.4f +
            maxOf(style.blurRadius.toPx(), style.backdropBlur.toPx())
    }
    // Measured once per shape and size, never per frame. Null for every shape that has a closed
    // form, which is all of them until someone reaches for a path.
    val pathField = if (shape.needsSampledField(measured, direction, density)) {
        rememberGlassPathField(
            shape = shape,
            size = measured,
            pad = padForField,
            band = with(density) { style.refractionBand.toPx() },
            density = density,
            layoutDirection = direction,
        )
    } else {
        null
    }

    // The scale is a separate channel from the glow on purpose: it is the part the hand feels,
    // so it overshoots going down and settles calmly coming back. A single spring for both
    // makes the highlight bounce, which reads as a rendering glitch rather than as a press.
    val targetScale = if (interaction != null && press.amount > 0.5f) interaction.pressScale else 1f
    val pressScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = if (targetScale > 1f) GlassMotion.PressDown else GlassMotion.PressUp,
        label = "glass_press_scale",
    )

    this
        .onGloballyPositioned { coordinates = it }
        .then(pressModifier)
        .graphicsLayer {
            scaleX = pressScale
            scaleY = pressScale
        }
        .drawWithContent {
            val source = state.layer
            val radii = shape.glassRadii(size, layoutDirection, this)

            val delta = panelOffsetInSource(state.sourceCoordinates, coordinates)

            if (source != null && delta != null && LiquidGlassSupport.hasShaders) {
                // Apple's material samples "an area larger than itself" — that is what makes it
                // lens rather than merely blur. Record that margin, or displacement at the rim
                // clamps against the panel's own edge and the lensing has nothing to bend.
                val pad = style.refractionDepth.toPx() * 1.4f +
                    maxOf(style.blurRadius.toPx(), style.backdropBlur.toPx())
                val sizeFactor = elementSizeFactor(size, this)
                val bounds = sampleBounds(pad, delta, size, state.sourceSize)
                if (!hasSampleRegion(bounds)) {
                    drawGlassFallback(style, light, radii, style.bevel.toPx())
                    drawContent()
                    return@drawWithContent
                }
                val uniforms = GlassUniforms(
                        width = size.width,
                        height = size.height,
                        pad = pad,
                        scale = sizeFactor,
                        flip = if (style.invertsWithBackdrop && sizeFactor < 0.5f) {
                            state.inversion
                        } else {
                            0f
                        },
                        radii = radii,
                        refractBand = style.refractionBand.toPx(),
                        refractDepth = style.refractionDepth.toPx(),
                        aberration = style.dispersion,
                        ior = style.indexOfRefraction,
                        bevelPower = style.bevelPower,
                        cornerPower = if (shape is GlassSquircleShape) {
                            shape.power
                        } else {
                            style.cornerPower
                        },
                        shapeKind = if (pathField != null) 1f else 0f,
                        fieldRange = pathField?.range ?: 1f,
                        fieldScale = pathField?.scale ?: 1f,
                        field = pathField?.bitmap,
                        mirror = style.mirror,
                        fresnel = style.fresnel,
                        legibility = style.legibility,
                        bevel = style.bevel.toPx(),
                        backdrop = bounds,
                        background = state.background,
                        touchX = press.x,
                        touchY = press.y,
                        touchAmount = press.amount * (interaction?.illumination ?: 0f),
                        materialize = materialize.coerceIn(0f, 1f),
                        frost = state.frost,
                        contrast = state.contrast,
                        lightX = light.x,
                        lightY = light.y,
                        specular = style.specular,
                        specularPower = style.specularPower,
                        tint = style.tint,
                        innerShadow = style.innerShadow,
                        adaptivity = style.adaptivity,
                        blurRadius = style.blurRadius.toPx(),
                        backdropBlur = style.backdropBlur.toPx(),
                        counterLight = style.counterLight,
                        edgeLight = style.edgeLight,
                        bevelPeak = style.bevelPeak,
                    )
                val effect = createGlassRenderEffect(uniforms)
                if (effect != null) {
                    // Record the padded slice of backdrop beneath this panel, in the panel's own
                    // coordinates offset by the pad, then let the chain blur and refract it.
                    val padPx = pad.toInt()
                    val paddedSize = IntSize(
                        (size.width.toInt() + padPx * 2).coerceAtLeast(1),
                        (size.height.toInt() + padPx * 2).coerceAtLeast(1),
                    )
                    if (refractContent) {
                        // The content, alone and transparent, at the same padded size as the
                        // backdrop so the two passes share one coordinate frame.
                        contentLayer.record(size = paddedSize) {
                            translate(pad, pad) { this@drawWithContent.drawContent() }
                        }
                    }
                    glassLayer.record(size = paddedSize) {
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
                    // The content pass: the same field and the same bend, over the material.
                    val contentEffect = if (refractContent) createGlassContentRenderEffect(uniforms) else null
                    if (contentEffect != null) {
                        contentLayer.renderEffect = contentEffect
                        translate(-pad, -pad) { drawLayer(contentLayer) }
                    } else {
                        drawContent()
                    }
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

/**
 * Corner radii in px, in the order the shader expects: TL, TR, BR, BL.
 *
 * A [GlassSquircleShape] produces a generic path outline, so the platform reader cannot see its
 * radii; it is intercepted here because a squircle *is* analytic — it is exactly what
 * `uCornerPower` describes — and routing it through the sampled field would be slower and less
 * exact for no reason.
 */
internal fun Shape.glassRadii(size: Size, layoutDirection: LayoutDirection, density: Density): FloatArray {
    if (this is GlassSquircleShape) {
        val r = with(density) { cornerRadius.toPx() }
            .coerceAtMost(minOf(size.width, size.height) / 2f)
        return floatArrayOf(r, r, r, r)
    }
    return cornerRadiiPx(size, layoutDirection, density)
}

/** True when the shape has no closed form and the shader has to read a measured field. */
internal fun Shape.needsSampledField(
    size: Size,
    layoutDirection: LayoutDirection,
    density: Density,
): Boolean {
    if (this is GlassSquircleShape) return false
    if (size.width <= 0f || size.height <= 0f) return false
    return createOutline(size, layoutDirection, density) is Outline.Generic
}

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
    val ior: Float,
    val bevelPower: Float,
    val cornerPower: Float,
    val shapeKind: Float,
    val fieldRange: Float,
    val fieldScale: Float,
    /** The measured field, when the shape has no closed form. */
    val field: androidx.compose.ui.graphics.ImageBitmap?,
    val mirror: Float,
    val fresnel: Float,
    val legibility: Float,
    val bevel: Float,
    /** Where inside the padded layer real pixels exist; sampling past it would read nothing. */
    val backdrop: FloatArray,
    /** Opaque ground the recorded backdrop sits on, for the transparent parts of it. */
    val background: Color,
    val touchX: Float,
    val touchY: Float,
    val touchAmount: Float,
    val materialize: Float,
    val frost: Float,
    val contrast: Float,
    val lightX: Float,
    val lightY: Float,
    val specular: Float,
    val specularPower: Float,
    val tint: Color,
    val innerShadow: Float,
    val adaptivity: Float,
    val blurRadius: Float,
    val backdropBlur: Float,
    val counterLight: Float,
    val edgeLight: Float,
    val bevelPeak: Float,
) {
    override fun equals(other: Any?): Boolean =
        other is GlassUniforms &&
            width == other.width && height == other.height &&
            pad == other.pad && scale == other.scale && flip == other.flip &&
            radii.contentEquals(other.radii) &&
            refractBand == other.refractBand && refractDepth == other.refractDepth &&
            aberration == other.aberration && backdrop.contentEquals(other.backdrop) &&
            ior == other.ior && bevelPower == other.bevelPower && mirror == other.mirror &&
            cornerPower == other.cornerPower && shapeKind == other.shapeKind &&
            fieldRange == other.fieldRange && fieldScale == other.fieldScale &&
            field === other.field &&
            touchX == other.touchX &&
            touchY == other.touchY && touchAmount == other.touchAmount &&
            materialize == other.materialize && frost == other.frost &&
            contrast == other.contrast &&
            fresnel == other.fresnel && legibility == other.legibility &&
            background == other.background &&
            bevel == other.bevel && lightX == other.lightX && lightY == other.lightY &&
            specular == other.specular && specularPower == other.specularPower &&
            tint == other.tint && innerShadow == other.innerShadow &&
            adaptivity == other.adaptivity && blurRadius == other.blurRadius &&
            backdropBlur == other.backdropBlur &&
            counterLight == other.counterLight && edgeLight == other.edgeLight &&
            bevelPeak == other.bevelPeak

    override fun hashCode(): Int = width.hashCode() * 31 + height.hashCode() + radii.contentHashCode()
}

internal expect fun createGlassRenderEffect(
    uniforms: GlassUniforms,
): androidx.compose.ui.graphics.RenderEffect?

/**
 * The content pass — see [GLASS_CONTENT_SHADER_SOURCE]. Takes the same uniforms as the material
 * so the two passes cannot disagree about the geometry.
 */
internal expect fun createGlassContentRenderEffect(
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
    // includeMotionFrameOfReference matters more than it looks. Compose tags scroll offsets as
    // a "motion frame of reference" and leaves them out by default, so without this the panel
    // reports where it would be if the list had never scrolled — and every glass element samples
    // a backdrop that slides out from under it as you scroll. It is invisible on a repetitive
    // backdrop and catastrophic past the recorded height, where the sample region inverts.
    return source.localPositionOf(panel, Offset.Zero, includeMotionFrameOfReference = true)
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
    val left = maxOf(0f, backdropLeft) + 0.5f
    val top = maxOf(0f, backdropTop) + 0.5f
    val right = minOf(layerWidth, backdropLeft + sourceSize.width) - 0.5f
    val bottom = minOf(layerHeight, backdropTop + sourceSize.height) - 0.5f
    // An element can sit entirely outside the recorded backdrop — see the note on
    // `hasSampleRegion`. Returning an inverted rect would hand the shader a clamp whose min
    // exceeds its max, which is undefined and reads on screen as a solid block of nothing.
    return floatArrayOf(left, top, maxOf(left, right), maxOf(top, bottom))
}

/**
 * Whether there is any recorded backdrop under this element at all.
 *
 * There is one arrangement where the answer is no: a glass element *inside* a scrolling
 * container whose backdrop source is *outside* it. Compose applies that scroll offset when it
 * draws rather than when it lays out, so every layout API — `positionInRoot`, `positionOnScreen`,
 * `localPositionOf` with or without the motion frame of reference — reports the element's
 * unscrolled position. The offset between the panel and the backdrop is therefore unknowable
 * from public API, and once the element scrolls past the recorded height the sample region
 * collapses entirely.
 *
 * The supported arrangement is the one the material is for and the one Apple describes: glass
 * is chrome, and the backdrop is the content scrolling beneath it. Put `liquidGlassSource` on
 * the scrolling body and the glass outside the scroll, or put both inside it so they share the
 * frame. When neither holds, this returns false and the caller degrades to the flat fallback
 * surface rather than drawing a hole.
 */
internal fun hasSampleRegion(bounds: FloatArray): Boolean =
    bounds[2] - bounds[0] > 1f && bounds[3] - bounds[1] > 1f

internal fun elementSizeFactor(size: Size, density: Density): Float {
    val minEdgeDp = with(density) { minOf(size.width, size.height).toDp().value }
    return ((minEdgeDp - 56f) / (320f - 56f)).coerceIn(0f, 1f)
}
