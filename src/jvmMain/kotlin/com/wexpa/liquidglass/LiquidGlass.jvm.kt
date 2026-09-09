package com.wexpa.liquidglass

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

actual object LiquidGlassSupport {
    /** Desktop renders through Skia, which runs SkSL everywhere. */
    actual val hasShaders: Boolean = true
    actual val hasBackdropBlur: Boolean = true
}

private val runtimeEffect: RuntimeEffect? by lazy {
    runCatching { RuntimeEffect.makeForShader(GLASS_SHADER_SOURCE) }.getOrNull()
}

internal actual fun createGlassRenderEffect(
    uniforms: GlassUniforms,
): androidx.compose.ui.graphics.RenderEffect? {
    val effect = runtimeEffect ?: return null
    if (uniforms.width <= 0f || uniforms.height <= 0f) return null

    val builder = RuntimeShaderBuilder(effect)
    builder.uniform("uSize", uniforms.width, uniforms.height)
    builder.uniform(
        "uRadii",
        uniforms.radii.getOrElse(0) { 0f },
        uniforms.radii.getOrElse(1) { 0f },
        uniforms.radii.getOrElse(2) { 0f },
        uniforms.radii.getOrElse(3) { 0f },
    )
    builder.uniform("uRefractBand", uniforms.refractBand)
    builder.uniform("uAberration", uniforms.aberration)
    builder.uniform("uIor", uniforms.ior)
    builder.uniform("uBevelPower", uniforms.bevelPower)
    builder.uniform("uMirror", uniforms.mirror)
    builder.uniform("uFresnel", uniforms.fresnel)
    builder.uniform("uLegibility", uniforms.legibility)
    builder.uniform("uBlur", uniforms.blurRadius)
    builder.uniform(
        "uBase",
        uniforms.background.red,
        uniforms.background.green,
        uniforms.background.blue,
    )
    builder.uniform(
        "uBackdrop",
        uniforms.backdrop[0],
        uniforms.backdrop[1],
        uniforms.backdrop[2],
        uniforms.backdrop[3],
    )
    builder.uniform("uPad", uniforms.pad)
    builder.uniform("uIor", uniforms.ior)
    builder.uniform("uBevelPower", uniforms.bevelPower)
    builder.uniform("uFresnel", uniforms.fresnel)
    builder.uniform("uScale", uniforms.scale)
    builder.uniform("uFlip", uniforms.flip)
    builder.uniform("uRefractDepth", uniforms.refractDepth)
    builder.uniform("uBevel", uniforms.bevel)
    builder.uniform("uLight", uniforms.lightX, uniforms.lightY)
    builder.uniform("uSpecular", uniforms.specular)
    builder.uniform("uSpecularPow", uniforms.specularPower)
    builder.uniform(
        "uTint",
        uniforms.tint.red,
        uniforms.tint.green,
        uniforms.tint.blue,
        uniforms.tint.alpha,
    )
    builder.uniform("uInnerShadow", uniforms.innerShadow)
    builder.uniform("uAdaptive", uniforms.adaptivity)

    // See the Android actual: only backdropBlur pre-blurs; the interior blur lives in the
    // shader so the rim keeps its detail.
    val prepared = if (uniforms.backdropBlur > 0f) {
        ImageFilter.makeBlur(uniforms.backdropBlur, uniforms.backdropBlur, FilterTileMode.CLAMP)
    } else {
        null
    }
    return ImageFilter.makeRuntimeShader(
        runtimeShaderBuilder = builder,
        shaderName = "content",
        input = prepared,
    ).asComposeRenderEffect()
}

internal actual fun Shape.cornerRadiiPx(
    size: Size,
    layoutDirection: LayoutDirection,
    density: Density,
): FloatArray = when (val outline = createOutline(size, layoutDirection, density)) {
    is Outline.Rounded -> outline.roundRect.toRadii()
    is Outline.Rectangle -> FloatArray(4)
    is Outline.Generic -> FloatArray(4)
}

private fun RoundRect.toRadii(): FloatArray = floatArrayOf(
    topLeftCornerRadius.x,
    topRightCornerRadius.x,
    bottomRightCornerRadius.x,
    bottomLeftCornerRadius.x,
)

private val containerEffect: RuntimeEffect? by lazy {
    runCatching { RuntimeEffect.makeForShader(GLASS_CONTAINER_SHADER_SOURCE) }.getOrNull()
}

internal actual fun createGlassContainerRenderEffect(
    uniforms: GlassContainerUniforms,
): androidx.compose.ui.graphics.RenderEffect? {
    val effect = containerEffect ?: return null
    if (uniforms.width <= 0f || uniforms.height <= 0f) return null

    val builder = RuntimeShaderBuilder(effect)
    builder.uniform("uSize", uniforms.width, uniforms.height)
    builder.uniform("uRect", uniforms.rects)
    builder.uniform("uRadius", uniforms.radii)
    builder.uniform("uCount", uniforms.count)
    builder.uniform("uMerge", uniforms.merge)
    builder.uniform("uPad", uniforms.pad)
    builder.uniform("uAberration", uniforms.aberration)
    builder.uniform("uBlur", uniforms.blurRadius)
    builder.uniform(
        "uBase",
        uniforms.background.red,
        uniforms.background.green,
        uniforms.background.blue,
    )
    builder.uniform(
        "uBackdrop",
        uniforms.backdrop[0],
        uniforms.backdrop[1],
        uniforms.backdrop[2],
        uniforms.backdrop[3],
    )
    builder.uniform("uRefractBand", uniforms.refractBand)
    builder.uniform("uRefractDepth", uniforms.refractDepth)
    builder.uniform("uBevel", uniforms.bevel)
    builder.uniform("uLight", uniforms.lightX, uniforms.lightY)
    builder.uniform("uSpecular", uniforms.specular)
    builder.uniform("uSpecularPow", uniforms.specularPower)
    builder.uniform(
        "uTint",
        uniforms.tint.red,
        uniforms.tint.green,
        uniforms.tint.blue,
        uniforms.tint.alpha,
    )
    builder.uniform("uInnerShadow", uniforms.innerShadow)
    builder.uniform("uAdaptive", uniforms.adaptivity)

    // See the Android actual: only backdropBlur pre-blurs; the interior blur lives in the
    // shader so the rim keeps its detail.
    val prepared = if (uniforms.backdropBlur > 0f) {
        ImageFilter.makeBlur(uniforms.backdropBlur, uniforms.backdropBlur, FilterTileMode.CLAMP)
    } else {
        null
    }
    return ImageFilter.makeRuntimeShader(
        runtimeShaderBuilder = builder,
        shaderName = "content",
        input = prepared,
    ).asComposeRenderEffect()
}
