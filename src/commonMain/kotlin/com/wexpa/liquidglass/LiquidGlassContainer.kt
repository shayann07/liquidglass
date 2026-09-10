package com.wexpa.liquidglass

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/** One panel taking part in a container's merged field. */
@Stable
class GlassMember internal constructor() {
    internal var bounds: Rect by mutableStateOf(Rect.Zero)
    internal var radius: Float by mutableStateOf(0f)
}

/** Marks children that should be fused into the container's single body of glass. */
interface LiquidGlassContainerScope {
    /** Registers this element's bounds with the container. It draws no material of its own. */
    fun Modifier.glassMember(shape: Shape): Modifier
}

/**
 * Renders several panels as one body of glass.
 *
 * Members inside this container do not each get their own material. Their shapes are combined
 * into a single distance field with a smooth minimum, so panels that come within
 * [mergeDistance] of each other grow a neck and fuse, then separate again as they move apart.
 * The merge is geometry, not an animation, so it behaves correctly at any speed — which is
 * what makes it read as liquid rather than as two rectangles cross-fading.
 *
 * Requires the shader path; see [LiquidGlassSupport.hasShaders]. Without it each member falls
 * back to its own plain surface, which does not fuse.
 */
@Composable
fun LiquidGlassContainer(
    state: LiquidGlassState,
    modifier: Modifier = Modifier,
    style: GlassStyle = GlassStyle.Regular,
    light: GlassLight = GlassLight.Default,
    mergeDistance: Dp = 24.dp,
    content: @Composable LiquidGlassContainerScope.() -> Unit,
) {
    val members = remember { mutableStateListOf<GlassMember>() }
    val glassLayer = rememberGraphicsLayer()
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val scope = remember(members) {
        object : LiquidGlassContainerScope {
            override fun Modifier.glassMember(shape: Shape): Modifier = composed {
                val member = remember { GlassMember().also { members += it } }
                // Captured in composition: onGloballyPositioned runs outside any of them.
                val density = LocalDensity.current
                val direction = LocalLayoutDirection.current
                onGloballyPositioned { coords ->
                    val container = coordinates
                    val topLeft = if (container != null && container.isAttached && coords.isAttached) {
                        container.localPositionOf(coords, Offset.Zero)
                    } else {
                        Offset.Zero
                    }
                    val size = Size(coords.size.width.toFloat(), coords.size.height.toFloat())
                    member.bounds = Rect(topLeft, size)
                    member.radius = shape.cornerRadiiPx(size, direction, density)
                        .firstOrNull() ?: 0f
                }
            }
        }
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { coordinates = it }
            .drawWithContent {
                val source = state.layer
                val active = members.filter { !it.bounds.isEmpty }

                val delta = panelOffsetInSource(state.sourceCoordinates, coordinates)

                if (source != null && delta != null &&
                    LiquidGlassSupport.hasShaders && active.isNotEmpty()
                ) {
                    val pad = style.refractionDepth.toPx() * 1.4f +
                    maxOf(style.blurRadius.toPx(), style.backdropBlur.toPx())
                    val bounds = sampleBounds(pad, delta, size, state.sourceSize)
                    if (!hasSampleRegion(bounds)) {
                        drawContent()
                        return@drawWithContent
                    }
                    val effect = createGlassContainerRenderEffect(
                        GlassContainerUniforms(
                            width = size.width,
                            height = size.height,
                            pad = pad,
                            backdrop = bounds,
                            background = state.background,
                            rects = FloatArray(MAX_GLASS_MEMBERS * 4).also { out ->
                                active.take(MAX_GLASS_MEMBERS).forEachIndexed { i, m ->
                                    out[i * 4] = m.bounds.left
                                    out[i * 4 + 1] = m.bounds.top
                                    out[i * 4 + 2] = m.bounds.width
                                    out[i * 4 + 3] = m.bounds.height
                                }
                            },
                            radii = FloatArray(MAX_GLASS_MEMBERS).also { out ->
                                active.take(MAX_GLASS_MEMBERS).forEachIndexed { i, m ->
                                    out[i] = m.radius
                                }
                            },
                            count = active.size.coerceAtMost(MAX_GLASS_MEMBERS).toFloat(),
                            merge = mergeDistance.toPx(),
                            refractBand = style.refractionBand.toPx(),
                            refractDepth = style.refractionDepth.toPx(),
                            aberration = style.dispersion,
                            ior = style.indexOfRefraction,
                            bevelPower = style.bevelPower,
                            fresnel = style.fresnel,
                            bevel = style.bevel.toPx(),
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
                            edgeShadow = style.edgeShadow,
                            rimSoft = style.rimSoftness.toPx(),
                            tintAbsorb = style.tintAbsorption,
                            edgeLight = style.edgeLight,
                            bevelPeak = style.bevelPeak,
                        )
                    )
                    if (effect != null) {
                        val padPx = pad.toInt()
                        glassLayer.record(
                            size = IntSize(
                                (size.width.toInt() + padPx * 2).coerceAtLeast(1),
                                (size.height.toInt() + padPx * 2).coerceAtLeast(1),
                            )
                        ) {
                            // See the note in Modifier.liquidGlass: the blur needs an
                            // opaque image or the fused body grows a halo.
                            drawRect(state.background)
                            translate(-delta.x + pad, -delta.y + pad) { drawLayer(source) }
                        }
                        glassLayer.renderEffect = effect
                        translate(-pad, -pad) { drawLayer(glassLayer) }
                    }
                }
                drawContent()
            },
    ) {
        scope.content()
    }
}

@Stable
internal class GlassContainerUniforms(
    val width: Float,
    val height: Float,
    val pad: Float,
    val backdrop: FloatArray,
    val background: androidx.compose.ui.graphics.Color,
    val rects: FloatArray,
    val radii: FloatArray,
    val count: Float,
    val merge: Float,
    val refractBand: Float,
    val refractDepth: Float,
    val aberration: Float,
    val ior: Float,
    val bevelPower: Float,
    val fresnel: Float,
    val bevel: Float,
    val lightX: Float,
    val lightY: Float,
    val specular: Float,
    val specularPower: Float,
    val tint: androidx.compose.ui.graphics.Color,
    val innerShadow: Float,
    val adaptivity: Float,
    val blurRadius: Float,
    val backdropBlur: Float,
    val counterLight: Float,
    val edgeLight: Float,
    val bevelPeak: Float,
    val edgeShadow: Float,
    val rimSoft: Float,
    val tintAbsorb: Float,
)

internal expect fun createGlassContainerRenderEffect(
    uniforms: GlassContainerUniforms,
): androidx.compose.ui.graphics.RenderEffect?
