package com.wexpa.liquidglass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.TileMode
import android.os.Build
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

actual object LiquidGlassSupport {
    /** RuntimeShader — and therefore refraction, specular and adaptive tint — is API 33+. */
    actual val hasShaders: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** RenderEffect, and so any backdrop blur at all, is API 31+. */
    actual val hasBackdropBlur: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}

/**
 * Compiling the shader is not free, so one instance is reused for the life of the process and
 * only its uniforms change per frame. RuntimeShader is not thread-safe, but every caller here
 * is on the UI thread inside a draw pass.
 */
/** One opaque pixel, so the `field` child is always bound even when nothing samples it. */
private val placeholderFieldShader: android.graphics.Shader by lazy {
    ImageShader(ImageBitmap(1, 1), TileMode.Clamp, TileMode.Clamp)
}

private val runtimeShader: RuntimeShader? by lazy {
    if (!LiquidGlassSupport.hasShaders) null
    else runCatching { RuntimeShader(GLASS_SHADER_SOURCE) }.getOrNull()
}

internal actual fun createGlassRenderEffect(
    uniforms: GlassUniforms,
): androidx.compose.ui.graphics.RenderEffect? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    val shader = runtimeShader ?: return null
    if (uniforms.width <= 0f || uniforms.height <= 0f) return null

    shader.setFloatUniform("uSize", uniforms.width, uniforms.height)
    shader.setFloatUniform(
        "uRadii",
        uniforms.radii.getOrElse(0) { 0f },
        uniforms.radii.getOrElse(1) { 0f },
        uniforms.radii.getOrElse(2) { 0f },
        uniforms.radii.getOrElse(3) { 0f },
    )
    shader.setFloatUniform("uRefractBand", uniforms.refractBand)
    shader.setFloatUniform("uAberration", uniforms.aberration)
    shader.setFloatUniform("uIor", uniforms.ior)
    shader.setFloatUniform("uBevelPower", uniforms.bevelPower)
    shader.setFloatUniform("uCornerPower", uniforms.cornerPower)
    shader.setFloatUniform("uShapeKind", uniforms.shapeKind)
    shader.setFloatUniform("uFieldRange", uniforms.fieldRange)
    shader.setFloatUniform("uFieldScale", uniforms.fieldScale)
    // `field` has to be bound whether or not it is read: an unbound child shader is a link
    // error, not a silent zero. The placeholder is one opaque pixel and costs nothing.
    shader.setInputShader("field", uniforms.field?.let { bitmap ->
        ImageShader(bitmap, TileMode.Clamp, TileMode.Clamp)
    } ?: placeholderFieldShader)
    shader.setFloatUniform("uTouch", uniforms.touchX, uniforms.touchY)
    shader.setFloatUniform("uTouchAmt", uniforms.touchAmount)
    shader.setFloatUniform("uMaterialize", uniforms.materialize)
    shader.setFloatUniform("uFrost", uniforms.frost)
    shader.setFloatUniform("uContrast", uniforms.contrast)
    shader.setFloatUniform("uMirror", uniforms.mirror)
    shader.setFloatUniform("uFresnel", uniforms.fresnel)
    shader.setFloatUniform("uLegibility", uniforms.legibility)
    shader.setFloatUniform("uBlur", uniforms.blurRadius)
    shader.setFloatUniform(
        "uBase",
        uniforms.background.red,
        uniforms.background.green,
        uniforms.background.blue,
    )
    shader.setFloatUniform(
        "uBackdrop",
        uniforms.backdrop[0],
        uniforms.backdrop[1],
        uniforms.backdrop[2],
        uniforms.backdrop[3],
    )
    shader.setFloatUniform("uPad", uniforms.pad)
    shader.setFloatUniform("uIor", uniforms.ior)
    shader.setFloatUniform("uBevelPower", uniforms.bevelPower)
    shader.setFloatUniform("uFresnel", uniforms.fresnel)
    shader.setFloatUniform("uScale", uniforms.scale)
    shader.setFloatUniform("uFlip", uniforms.flip)
    shader.setFloatUniform("uRefractDepth", uniforms.refractDepth)
    shader.setFloatUniform("uBevel", uniforms.bevel)
    shader.setFloatUniform("uLight", uniforms.lightX, uniforms.lightY)
    shader.setFloatUniform("uSpecular", uniforms.specular)
    shader.setFloatUniform("uSpecularPow", uniforms.specularPower)
    shader.setFloatUniform(
        "uTint",
        uniforms.tint.red,
        uniforms.tint.green,
        uniforms.tint.blue,
        uniforms.tint.alpha,
    )
    shader.setFloatUniform("uInnerShadow", uniforms.innerShadow)
    shader.setFloatUniform("uAdaptive", uniforms.adaptivity)

    // The interior blur is NOT chained ahead of the shader: pre-blurring destroys exactly the
    // detail the rim is supposed to bend. backdropBlur is the opt-in that does pre-blur, for
    // chrome that would rather hide its backdrop than refract it.
    val glass = RenderEffect.createRuntimeShaderEffect(shader, "content")
    return if (uniforms.backdropBlur > 0f) {
        RenderEffect.createChainEffect(
            glass,
            RenderEffect.createBlurEffect(
                uniforms.backdropBlur,
                uniforms.backdropBlur,
                Shader.TileMode.CLAMP,
            ),
        ).asComposeRenderEffect()
    } else {
        glass.asComposeRenderEffect()
    }
}

internal actual fun Shape.cornerRadiiPx(
    size: Size,
    layoutDirection: LayoutDirection,
    density: Density,
): FloatArray = when (val outline = createOutline(size, layoutDirection, density)) {
    is Outline.Rounded -> outline.roundRect.toRadii()
    is Outline.Rectangle -> FloatArray(4)
    // A path outline has no radii to read; the shader falls back to square corners, which is
    // the closest honest approximation without tracing the path.
    is Outline.Generic -> FloatArray(4)
}

private fun RoundRect.toRadii(): FloatArray = floatArrayOf(
    topLeftCornerRadius.x,
    topRightCornerRadius.x,
    bottomRightCornerRadius.x,
    bottomLeftCornerRadius.x,
)

private val containerShader: RuntimeShader? by lazy {
    if (!LiquidGlassSupport.hasShaders) null
    else runCatching { RuntimeShader(GLASS_CONTAINER_SHADER_SOURCE) }.getOrNull()
}

internal actual fun createGlassContainerRenderEffect(
    uniforms: GlassContainerUniforms,
): androidx.compose.ui.graphics.RenderEffect? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    val shader = containerShader ?: return null
    if (uniforms.width <= 0f || uniforms.height <= 0f) return null

    shader.setFloatUniform("uSize", uniforms.width, uniforms.height)
    shader.setFloatUniform("uRect", uniforms.rects)
    shader.setFloatUniform("uRadius", uniforms.radii)
    shader.setFloatUniform("uCount", uniforms.count)
    shader.setFloatUniform("uMerge", uniforms.merge)
    shader.setFloatUniform("uIor", uniforms.ior)
    shader.setFloatUniform("uBevelPower", uniforms.bevelPower)
    shader.setFloatUniform("uFresnel", uniforms.fresnel)
    shader.setFloatUniform("uPad", uniforms.pad)
    shader.setFloatUniform("uAberration", uniforms.aberration)
    shader.setFloatUniform("uBlur", uniforms.blurRadius)
    shader.setFloatUniform(
        "uBase",
        uniforms.background.red,
        uniforms.background.green,
        uniforms.background.blue,
    )
    shader.setFloatUniform(
        "uBackdrop",
        uniforms.backdrop[0],
        uniforms.backdrop[1],
        uniforms.backdrop[2],
        uniforms.backdrop[3],
    )
    shader.setFloatUniform("uRefractBand", uniforms.refractBand)
    shader.setFloatUniform("uRefractDepth", uniforms.refractDepth)
    shader.setFloatUniform("uBevel", uniforms.bevel)
    shader.setFloatUniform("uLight", uniforms.lightX, uniforms.lightY)
    shader.setFloatUniform("uSpecular", uniforms.specular)
    shader.setFloatUniform("uSpecularPow", uniforms.specularPower)
    shader.setFloatUniform(
        "uTint",
        uniforms.tint.red,
        uniforms.tint.green,
        uniforms.tint.blue,
        uniforms.tint.alpha,
    )
    shader.setFloatUniform("uInnerShadow", uniforms.innerShadow)
    shader.setFloatUniform("uAdaptive", uniforms.adaptivity)

    // The interior blur is NOT chained ahead of the shader: pre-blurring destroys exactly the
    // detail the rim is supposed to bend. backdropBlur is the opt-in that does pre-blur, for
    // chrome that would rather hide its backdrop than refract it.
    val glass = RenderEffect.createRuntimeShaderEffect(shader, "content")
    return if (uniforms.backdropBlur > 0f) {
        RenderEffect.createChainEffect(
            glass,
            RenderEffect.createBlurEffect(
                uniforms.backdropBlur,
                uniforms.backdropBlur,
                Shader.TileMode.CLAMP,
            ),
        ).asComposeRenderEffect()
    } else {
        glass.asComposeRenderEffect()
    }
}
