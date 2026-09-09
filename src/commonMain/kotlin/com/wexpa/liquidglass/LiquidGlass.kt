package com.wexpa.liquidglass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.LayoutDirection

/**
 * Shared state linking a backdrop to the glass panels drawn over it.
 *
 * One state serves any number of panels: the backdrop is recorded once per frame and each
 * panel samples the region beneath itself out of that single recording.
 */
@Stable
class LiquidGlassState internal constructor() {
    internal var layer: GraphicsLayer? by mutableStateOf(null)
    internal var sourceOrigin: Offset by mutableStateOf(Offset.Zero)
    internal var sourceSize: Size by mutableStateOf(Size.Zero)

    /** True once a backdrop has been recorded and panels can sample it. */
    val isReady: Boolean get() = layer != null && sourceSize != Size.Zero
}

@Composable
fun rememberLiquidGlassState(): LiquidGlassState = remember { LiquidGlassState() }

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
            state.sourceOrigin = coords.positionInRoot()
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
    var origin by remember { mutableStateOf(Offset.Zero) }

    this
        .onGloballyPositioned { origin = it.positionInRoot() }
        .drawWithContent {
            val source = state.layer
            val radii = shape.cornerRadiiPx(size, layoutDirection, this)

            if (source != null && LiquidGlassSupport.hasShaders) {
                val effect = createGlassRenderEffect(
                    GlassUniforms(
                        width = size.width,
                        height = size.height,
                        radii = radii,
                        refractBand = style.refractionBand.toPx(),
                        refractDepth = style.refractionDepth.toPx(),
                        bevel = style.bevel.toPx(),
                        lightX = light.x,
                        lightY = light.y,
                        specular = style.specular,
                        specularPower = style.specularPower,
                        tint = style.tint,
                        innerShadow = style.innerShadow,
                        adaptivity = style.adaptivity,
                        blurRadius = style.blurRadius.toPx(),
                    )
                )
                if (effect != null) {
                    // Redraw the slice of backdrop that sits under this panel, in the panel's
                    // own coordinates, then let the effect chain blur and refract it.
                    val delta = origin - state.sourceOrigin
                    glassLayer.record {
                        translate(-delta.x, -delta.y) { drawLayer(source) }
                    }
                    glassLayer.renderEffect = effect
                    drawLayer(glassLayer)
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
    density: androidx.compose.ui.unit.Density,
): FloatArray

@Stable
internal data class GlassUniforms(
    val width: Float,
    val height: Float,
    val radii: FloatArray,
    val refractBand: Float,
    val refractDepth: Float,
    val bevel: Float,
    val lightX: Float,
    val lightY: Float,
    val specular: Float,
    val specularPower: Float,
    val tint: Color,
    val innerShadow: Float,
    val adaptivity: Float,
    val blurRadius: Float,
) {
    override fun equals(other: Any?): Boolean =
        other is GlassUniforms &&
            width == other.width && height == other.height &&
            radii.contentEquals(other.radii) &&
            refractBand == other.refractBand && refractDepth == other.refractDepth &&
            bevel == other.bevel && lightX == other.lightX && lightY == other.lightY &&
            specular == other.specular && specularPower == other.specularPower &&
            tint == other.tint && innerShadow == other.innerShadow &&
            adaptivity == other.adaptivity && blurRadius == other.blurRadius

    override fun hashCode(): Int = width.hashCode() * 31 + height.hashCode() + radii.contentHashCode()
}

internal expect fun createGlassRenderEffect(
    uniforms: GlassUniforms,
): androidx.compose.ui.graphics.RenderEffect?
