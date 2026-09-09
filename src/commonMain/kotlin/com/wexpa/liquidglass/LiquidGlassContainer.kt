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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
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
    var origin by remember { mutableStateOf(Offset.Zero) }

    val scope = remember(members) {
        object : LiquidGlassContainerScope {
            override fun Modifier.glassMember(shape: Shape): Modifier = composed {
                val member = remember { GlassMember().also { members += it } }
                // Captured in composition: onGloballyPositioned runs outside any of them.
                val density = LocalDensity.current
                val direction = LocalLayoutDirection.current
                onGloballyPositioned { coords ->
                    val topLeft = coords.positionInRoot() - origin
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
            .onGloballyPositioned { origin = it.positionInRoot() }
            .drawWithContent {
                val source = state.layer
                val active = members.filter { !it.bounds.isEmpty }

                if (source != null && LiquidGlassSupport.hasShaders && active.isNotEmpty()) {
                    val effect = createGlassContainerRenderEffect(
                        GlassContainerUniforms(
                            width = size.width,
                            height = size.height,
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
                        val delta = origin - state.sourceOrigin
                        glassLayer.record {
                            translate(-delta.x, -delta.y) { drawLayer(source) }
                        }
                        glassLayer.renderEffect = effect
                        drawLayer(glassLayer)
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
    val rects: FloatArray,
    val radii: FloatArray,
    val count: Float,
    val merge: Float,
    val refractBand: Float,
    val refractDepth: Float,
    val bevel: Float,
    val lightX: Float,
    val lightY: Float,
    val specular: Float,
    val specularPower: Float,
    val tint: androidx.compose.ui.graphics.Color,
    val innerShadow: Float,
    val adaptivity: Float,
    val blurRadius: Float,
)

internal expect fun createGlassContainerRenderEffect(
    uniforms: GlassContainerUniforms,
): androidx.compose.ui.graphics.RenderEffect?
