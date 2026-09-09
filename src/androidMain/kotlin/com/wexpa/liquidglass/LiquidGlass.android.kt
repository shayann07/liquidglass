package com.wexpa.liquidglass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
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

    // Blur first, then refract the blurred result: sampling a sharp backdrop through the
    // bevel produces aliased streaks, because neighbouring output pixels reach for widely
    // separated input pixels near the rim.
    val blur = if (uniforms.blurRadius > 0f) {
        RenderEffect.createBlurEffect(
            uniforms.blurRadius,
            uniforms.blurRadius,
            Shader.TileMode.CLAMP,
        )
    } else {
        null
    }

    val glass = RenderEffect.createRuntimeShaderEffect(shader, "content")
    val chained = if (blur != null) RenderEffect.createChainEffect(glass, blur) else glass
    return chained.asComposeRenderEffect()
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
