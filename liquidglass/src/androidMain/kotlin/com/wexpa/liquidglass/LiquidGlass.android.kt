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

/**
 * The sampled distance field, read bilinearly. It is stored at half resolution on the premise that
 * bilinear sampling puts back what the halving takes out (GlassPathField.kt). A default-filter
 * BitmapShader bound as a RuntimeShader child has no paint to take a filter bit from, so the
 * filter is set explicitly. Only reached on the shader path, which is API 33+.
 */
private fun pathFieldShader(bitmap: ImageBitmap): Shader {
    val shader = ImageShader(bitmap, TileMode.Clamp, TileMode.Clamp)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader is android.graphics.BitmapShader) {
        shader.filterMode = android.graphics.BitmapShader.FILTER_MODE_LINEAR
    }
    return shader
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
    shader.setFloatUniform(
        "uFuse",
        uniforms.fuse.getOrElse(0) { 0f },
        uniforms.fuse.getOrElse(1) { 0f },
        uniforms.fuse.getOrElse(2) { 0f },
        uniforms.fuse.getOrElse(3) { 0f },
    )
    shader.setFloatUniform("uFuseShape", uniforms.fuseRadius, uniforms.fuseWidth)
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
    shader.setInputShader("field", uniforms.field?.let(::pathFieldShader) ?: placeholderFieldShader)
    shader.setFloatUniform("uTouch", uniforms.touchX, uniforms.touchY)
    shader.setFloatUniform("uTouchAmt", uniforms.touchAmount)
    shader.setFloatUniform("uMaterialize", uniforms.materialize)
    shader.setFloatUniform("uFrost", uniforms.frost)
    shader.setFloatUniform("uContrast", uniforms.contrast)
    shader.setFloatUniform("uDebugCoverage", uniforms.debugCoverage)
    shader.setFloatUniform("uMirror", uniforms.mirror)
    shader.setFloatUniform("uFresnel", uniforms.fresnel)
    shader.setFloatUniform("uHiChroma", uniforms.highlightChroma)
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
    shader.setFloatUniform("uScale", uniforms.scale)
    shader.setFloatUniform("uFlip", uniforms.flip)
    shader.setFloatUniform("uRefractDepth", uniforms.refractDepth)
    shader.setFloatUniform("uBevel", uniforms.bevel)
    shader.setFloatUniform("uLight", uniforms.lightX, uniforms.lightY)
    shader.setFloatUniform("uSpecular", uniforms.specular)
    shader.setFloatUniform("uSpecularPow", uniforms.specularPower)
    shader.setFloatUniform("uCounterLight", uniforms.counterLight)
    shader.setFloatUniform("uEdgeShadow", uniforms.edgeShadow)
    shader.setFloatUniform("uRimSoft", uniforms.rimSoft)
    shader.setFloatUniform("uTintAbsorb", uniforms.tintAbsorb)
    shader.setFloatUniform("uEdgeLight", uniforms.edgeLight)
    shader.setFloatUniform("uBevelPeak", uniforms.bevelPeak)
    shader.setFloatUniform(
        "uTint",
        uniforms.tint.red,
        uniforms.tint.green,
        uniforms.tint.blue,
        uniforms.tint.alpha,
    )
    shader.setFloatUniform("uInnerShadow", uniforms.innerShadow)
    shader.setFloatUniform("uAdaptive", uniforms.adaptivity)
    shader.setFloatUniform("uProfile", uniforms.profile)
    shader.setFloatUniform("uFormation", uniforms.formation)
    shader.setFloatUniform("uHeldLens", uniforms.heldLens)
    shader.setFloatUniform("uHeldMagnification", uniforms.heldMagnification)
    shader.setFloatUniform("uHeldGlow", uniforms.heldGlow)
    shader.setFloatUniform("uHeldEdgeRecovery", uniforms.heldEdgeRecovery)
    shader.setFloatUniform("uRestMap", uniforms.restMap)
    shader.setFloatUniform(
        "uBody",
        uniforms.body.getOrElse(0) { 0f },
        uniforms.body.getOrElse(1) { 0f },
        uniforms.body.getOrElse(2) { 0f },
        uniforms.body.getOrElse(3) { 0f },
    )
    shader.setFloatUniform("uBodyY", uniforms.bodyY)
    shader.setFloatUniform("uBodyKind", uniforms.bodyKind)
    shader.setFloatUniform("uPoseA", uniforms.poseA[0], uniforms.poseA[1], uniforms.poseA[2], uniforms.poseA[3])
    shader.setFloatUniform("uPoseAInv", uniforms.poseAInv[0], uniforms.poseAInv[1], uniforms.poseAInv[2], uniforms.poseAInv[3])
    shader.setFloatUniform("uPoseC", uniforms.poseC[0], uniforms.poseC[1], uniforms.poseC[2], uniforms.poseC[3])
    shader.setFloatUniform("uPoseD", uniforms.poseD[0], uniforms.poseD[1], uniforms.poseD[2], uniforms.poseD[3])
    shader.setFloatUniform("uEndpointAlpha", uniforms.endpointAlpha)
    shader.setFloatUniform("uEdgeFold", uniforms.edgeFold)
    shader.setFloatUniform("uWideStrip", uniforms.wideStrip)
    shader.setFloatUniform("uWideScale", uniforms.wideScale)
    shader.setFloatUniform("uFineShare", uniforms.fineShare)
    shader.setFloatUniform("uWideKernel", uniforms.wideKernel)
    shader.setFloatUniform("uLift", uniforms.tintLift)
    shader.setFloatUniform("uLiftAdapt", uniforms.liftAdapt)

    // The interior blur is NOT chained ahead of the shader: pre-blurring destroys exactly the
    // detail the rim is supposed to bend. backdropBlur is the opt-in that does pre-blur, for
    // chrome that would rather hide its backdrop than refract it.
    val glass = RenderEffect.createRuntimeShaderEffect(shader, "content")
    val radius = platformBlurRadius(uniforms.backdropSigma, uniforms.backdropBlur)
    return if (radius > 0f) {
        RenderEffect.createChainEffect(
            glass,
            RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP),
        ).asComposeRenderEffect()
    } else {
        glass.asComposeRenderEffect()
    }
}

/**
 * Android's blur takes a radius and turns it into a Gaussian sigma of `0.57735 * radius + 0.5`
 * (Skia's `ConvertRadiusToSigma`). A style that names a sigma has to be inverted through that,
 * or the same number would blur about 1.7x less here than on the desktop.
 */
private fun platformBlurRadius(sigma: Float, radius: Float): Float =
    if (sigma > 0f) platformBlurRadiusForSigma(sigma) else radius

internal actual fun platformBlurRadiusForSigma(sigma: Float): Float =
    ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0f)

internal actual fun createWideKernelEffect(sigmaPx: Float): androidx.compose.ui.graphics.RenderEffect? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val radius = platformBlurRadiusForSigma(sigmaPx)
    return RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP).asComposeRenderEffect()
}

private val contentRuntimeShader: RuntimeShader? by lazy {
    if (!LiquidGlassSupport.hasShaders) null
    else runCatching { RuntimeShader(GLASS_CONTENT_SHADER_SOURCE) }.getOrNull()
}

internal actual fun createGlassContentRenderEffect(
    uniforms: GlassUniforms,
): androidx.compose.ui.graphics.RenderEffect? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    val shader = contentRuntimeShader ?: return null
    if (uniforms.width <= 0f || uniforms.height <= 0f) return null

    shader.setFloatUniform("uSize", uniforms.width, uniforms.height)
    shader.setFloatUniform("uPad", uniforms.pad)
    shader.setFloatUniform(
        "uRadii",
        uniforms.radii.getOrElse(0) { 0f },
        uniforms.radii.getOrElse(1) { 0f },
        uniforms.radii.getOrElse(2) { 0f },
        uniforms.radii.getOrElse(3) { 0f },
    )
    shader.setFloatUniform("uCornerPower", uniforms.cornerPower)
    shader.setFloatUniform("uShapeKind", uniforms.shapeKind)
    shader.setFloatUniform("uFieldRange", uniforms.fieldRange)
    shader.setFloatUniform("uFieldScale", uniforms.fieldScale)
    shader.setInputShader("field", uniforms.field?.let(::pathFieldShader) ?: placeholderFieldShader)
    shader.setFloatUniform(
        "uFuse",
        uniforms.fuse.getOrElse(0) { 0f },
        uniforms.fuse.getOrElse(1) { 0f },
        uniforms.fuse.getOrElse(2) { 0f },
        uniforms.fuse.getOrElse(3) { 0f },
    )
    shader.setFloatUniform("uFuseShape", uniforms.fuseRadius, uniforms.fuseWidth)
    shader.setFloatUniform("uRefractBand", uniforms.refractBand)
    shader.setFloatUniform("uRefractDepth", uniforms.refractDepth)
    shader.setFloatUniform("uIor", uniforms.ior)
    shader.setFloatUniform("uBevelPower", uniforms.bevelPower)
    shader.setFloatUniform("uAberration", uniforms.aberration)
    shader.setFloatUniform("uScale", uniforms.scale)
    shader.setFloatUniform("uMaterialize", uniforms.materialize)
    shader.setFloatUniform("uTouch", uniforms.touchX, uniforms.touchY)
    shader.setFloatUniform("uTouchAmt", uniforms.touchAmount)
    shader.setFloatUniform("uProfile", uniforms.profile)
    shader.setFloatUniform("uFormation", uniforms.formation)
    shader.setFloatUniform("uHeldLens", uniforms.heldLens)
    shader.setFloatUniform("uHeldMagnification", uniforms.heldMagnification)
    shader.setFloatUniform("uRestMap", uniforms.restMap)
    shader.setFloatUniform(
        "uBody",
        uniforms.body.getOrElse(0) { 0f },
        uniforms.body.getOrElse(1) { 0f },
        uniforms.body.getOrElse(2) { 0f },
        uniforms.body.getOrElse(3) { 0f },
    )
    shader.setFloatUniform("uBodyY", uniforms.bodyY)
    shader.setFloatUniform("uBodyKind", uniforms.bodyKind)
    shader.setFloatUniform("uPoseA", uniforms.poseA[0], uniforms.poseA[1], uniforms.poseA[2], uniforms.poseA[3])
    shader.setFloatUniform("uPoseAInv", uniforms.poseAInv[0], uniforms.poseAInv[1], uniforms.poseAInv[2], uniforms.poseAInv[3])
    shader.setFloatUniform("uPoseC", uniforms.poseC[0], uniforms.poseC[1], uniforms.poseC[2], uniforms.poseC[3])
    shader.setFloatUniform("uPoseD", uniforms.poseD[0], uniforms.poseD[1], uniforms.poseD[2], uniforms.poseD[3])
    shader.setFloatUniform("uEndpointAlpha", uniforms.endpointAlpha)
    shader.setFloatUniform("uEdgeFold", uniforms.edgeFold)
    shader.setFloatUniform("uInkSplit", uniforms.inkSplit)
    shader.setFloatUniform("uHeldInk", uniforms.heldInk)
    return RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
}

private val endpointRuntimeShader: RuntimeShader? by lazy {
    if (!LiquidGlassSupport.hasShaders) null
    else runCatching { RuntimeShader(GLASS_ENDPOINT_SHADER_SOURCE) }.getOrNull()
}

internal actual fun createGlassEndpointRenderEffect(
    uniforms: GlassUniforms,
): androidx.compose.ui.graphics.RenderEffect? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    val shader = endpointRuntimeShader ?: return null
    if (uniforms.width <= 0f || uniforms.height <= 0f) return null

    shader.setFloatUniform("uSize", uniforms.width, uniforms.height)
    shader.setFloatUniform("uPad", uniforms.pad)
    shader.setFloatUniform(
        "uRadii",
        uniforms.radii.getOrElse(0) { 0f },
        uniforms.radii.getOrElse(1) { 0f },
        uniforms.radii.getOrElse(2) { 0f },
        uniforms.radii.getOrElse(3) { 0f },
    )
    shader.setFloatUniform(
        "uFuse",
        uniforms.fuse.getOrElse(0) { 0f },
        uniforms.fuse.getOrElse(1) { 0f },
        uniforms.fuse.getOrElse(2) { 0f },
        uniforms.fuse.getOrElse(3) { 0f },
    )
    shader.setFloatUniform("uFuseShape", uniforms.fuseRadius, uniforms.fuseWidth)
    shader.setFloatUniform("uCornerPower", uniforms.cornerPower)
    shader.setFloatUniform("uShapeKind", uniforms.shapeKind)
    shader.setFloatUniform("uFieldRange", uniforms.fieldRange)
    shader.setFloatUniform("uFieldScale", uniforms.fieldScale)
    shader.setInputShader("field", uniforms.field?.let(::pathFieldShader) ?: placeholderFieldShader)
    shader.setFloatUniform(
        "uBody",
        uniforms.body.getOrElse(0) { 0f },
        uniforms.body.getOrElse(1) { 0f },
        uniforms.body.getOrElse(2) { 0f },
        uniforms.body.getOrElse(3) { 0f },
    )
    shader.setFloatUniform("uBodyY", uniforms.bodyY)
    shader.setFloatUniform("uBodyKind", uniforms.bodyKind)
    shader.setFloatUniform("uPoseA", uniforms.poseA[0], uniforms.poseA[1], uniforms.poseA[2], uniforms.poseA[3])
    shader.setFloatUniform("uPoseAInv", uniforms.poseAInv[0], uniforms.poseAInv[1], uniforms.poseAInv[2], uniforms.poseAInv[3])
    shader.setFloatUniform("uPoseC", uniforms.poseC[0], uniforms.poseC[1], uniforms.poseC[2], uniforms.poseC[3])
    shader.setFloatUniform("uPoseD", uniforms.poseD[0], uniforms.poseD[1], uniforms.poseD[2], uniforms.poseD[3])
    return RenderEffect.createRuntimeShaderEffect(shader, "endpoint").asComposeRenderEffect()
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
    shader.setFloatUniform("uHiChroma", uniforms.highlightChroma)
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
    shader.setFloatUniform("uCounterLight", uniforms.counterLight)
    shader.setFloatUniform("uEdgeShadow", uniforms.edgeShadow)
    shader.setFloatUniform("uRimSoft", uniforms.rimSoft)
    shader.setFloatUniform("uTintAbsorb", uniforms.tintAbsorb)
    shader.setFloatUniform("uEdgeLight", uniforms.edgeLight)
    shader.setFloatUniform("uBevelPeak", uniforms.bevelPeak)
    shader.setFloatUniform(
        "uTint",
        uniforms.tint.red,
        uniforms.tint.green,
        uniforms.tint.blue,
        uniforms.tint.alpha,
    )
    shader.setFloatUniform("uInnerShadow", uniforms.innerShadow)
    shader.setFloatUniform("uAdaptive", uniforms.adaptivity)
    shader.setFloatUniform("uProfile", uniforms.profile)
    shader.setFloatUniform("uFormation", 0f)
    shader.setFloatUniform("uHeldLens", 0f)
    shader.setFloatUniform("uRestMap", uniforms.restMap)
    shader.setFloatUniform("uWideStrip", uniforms.wideStrip)
    shader.setFloatUniform("uWideScale", uniforms.wideScale)
    shader.setFloatUniform("uFineShare", uniforms.fineShare)
    shader.setFloatUniform("uWideKernel", uniforms.wideKernel)
    shader.setFloatUniform("uLift", uniforms.tintLift)
    shader.setFloatUniform("uLiftAdapt", uniforms.liftAdapt)

    // The interior blur is NOT chained ahead of the shader: pre-blurring destroys exactly the
    // detail the rim is supposed to bend. backdropBlur is the opt-in that does pre-blur, for
    // chrome that would rather hide its backdrop than refract it.
    val glass = RenderEffect.createRuntimeShaderEffect(shader, "content")
    val radius = platformBlurRadius(uniforms.backdropSigma, uniforms.backdropBlur)
    return if (radius > 0f) {
        RenderEffect.createChainEffect(
            glass,
            RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP),
        ).asComposeRenderEffect()
    } else {
        glass.asComposeRenderEffect()
    }
}
