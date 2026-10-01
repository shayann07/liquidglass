package com.wexpa.liquidglass

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
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

/** One opaque pixel, so the `field` child is always bound even when nothing samples it. */
private val placeholderFieldShader: org.jetbrains.skia.Shader by lazy {
    org.jetbrains.skia.Image.makeRaster(
        org.jetbrains.skia.ImageInfo(
            1,
            1,
            org.jetbrains.skia.ColorType.RGBA_8888,
            org.jetbrains.skia.ColorAlphaType.UNPREMUL,
        ),
        byteArrayOf(0, 0, 0, -1),
        4,
    ).makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP)
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
    builder.uniform(
        "uFuse",
        uniforms.fuse.getOrElse(0) { 0f },
        uniforms.fuse.getOrElse(1) { 0f },
        uniforms.fuse.getOrElse(2) { 0f },
        uniforms.fuse.getOrElse(3) { 0f },
    )
    builder.uniform("uFuseShape", uniforms.fuseRadius, uniforms.fuseWidth)
    builder.uniform("uRefractBand", uniforms.refractBand)
    builder.uniform("uAberration", uniforms.aberration)
    builder.uniform("uIor", uniforms.ior)
    builder.uniform("uBevelPower", uniforms.bevelPower)
    builder.uniform("uCornerPower", uniforms.cornerPower)
    builder.uniform("uShapeKind", uniforms.shapeKind)
    builder.uniform("uFieldRange", uniforms.fieldRange)
    builder.uniform("uFieldScale", uniforms.fieldScale)
    // See the Android actual: the child must be bound even when unread.
    builder.child("field", uniforms.field?.let { bitmap ->
        org.jetbrains.skia.Image.makeFromBitmap(bitmap.asSkiaBitmap())
            .makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP)
    } ?: placeholderFieldShader)
    builder.uniform("uTouch", uniforms.touchX, uniforms.touchY)
    builder.uniform("uTouchAmt", uniforms.touchAmount)
    builder.uniform("uMaterialize", uniforms.materialize)
    builder.uniform("uFrost", uniforms.frost)
    builder.uniform("uContrast", uniforms.contrast)
    builder.uniform("uMirror", uniforms.mirror)
    builder.uniform("uFresnel", uniforms.fresnel)
    builder.uniform("uHiChroma", uniforms.highlightChroma)
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
    builder.uniform("uDebugCoverage", uniforms.debugCoverage)
    builder.uniform("uIor", uniforms.ior)
    builder.uniform("uBevelPower", uniforms.bevelPower)
    builder.uniform("uFresnel", uniforms.fresnel)
    builder.uniform("uHiChroma", uniforms.highlightChroma)
    builder.uniform("uScale", uniforms.scale)
    builder.uniform("uFlip", uniforms.flip)
    builder.uniform("uRefractDepth", uniforms.refractDepth)
    builder.uniform("uBevel", uniforms.bevel)
    builder.uniform("uLight", uniforms.lightX, uniforms.lightY)
    builder.uniform("uSpecular", uniforms.specular)
    builder.uniform("uSpecularPow", uniforms.specularPower)
    builder.uniform("uCounterLight", uniforms.counterLight)
    builder.uniform("uEdgeShadow", uniforms.edgeShadow)
    builder.uniform("uRimSoft", uniforms.rimSoft)
    builder.uniform("uTintAbsorb", uniforms.tintAbsorb)
    builder.uniform("uEdgeLight", uniforms.edgeLight)
    builder.uniform("uBevelPeak", uniforms.bevelPeak)
    builder.uniform(
        "uTint",
        uniforms.tint.red,
        uniforms.tint.green,
        uniforms.tint.blue,
        uniforms.tint.alpha,
    )
    builder.uniform("uInnerShadow", uniforms.innerShadow)
    builder.uniform("uAdaptive", uniforms.adaptivity)
    builder.uniform("uProfile", uniforms.profile)
    builder.uniform("uFormation", uniforms.formation)
    builder.uniform("uHeldLens", uniforms.heldLens)
    builder.uniform("uHeldMagnification", uniforms.heldMagnification)
    builder.uniform("uHeldGlow", uniforms.heldGlow)
    builder.uniform("uHeldEdgeRecovery", uniforms.heldEdgeRecovery)
    builder.uniform("uRestMap", uniforms.restMap)
    builder.uniform(
        "uBody",
        uniforms.body.getOrElse(0) { 0f },
        uniforms.body.getOrElse(1) { 0f },
        uniforms.body.getOrElse(2) { 0f },
        uniforms.body.getOrElse(3) { 0f },
    )
    builder.uniform("uBodyY", uniforms.bodyY)
    builder.uniform("uBodyKind", uniforms.bodyKind)
    builder.uniform("uPoseA", uniforms.poseA[0], uniforms.poseA[1], uniforms.poseA[2], uniforms.poseA[3])
    builder.uniform("uPoseAInv", uniforms.poseAInv[0], uniforms.poseAInv[1], uniforms.poseAInv[2], uniforms.poseAInv[3])
    builder.uniform("uPoseC", uniforms.poseC[0], uniforms.poseC[1], uniforms.poseC[2], uniforms.poseC[3])
    builder.uniform("uPoseD", uniforms.poseD[0], uniforms.poseD[1], uniforms.poseD[2], uniforms.poseD[3])
    builder.uniform("uEndpointAlpha", uniforms.endpointAlpha)
    builder.uniform("uEdgeFold", uniforms.edgeFold)
    builder.uniform("uWideStrip", uniforms.wideStrip)
    builder.uniform("uWideScale", uniforms.wideScale)
    builder.uniform("uFineShare", uniforms.fineShare)
    builder.uniform("uWideKernel", uniforms.wideKernel)
    builder.uniform("uLift", uniforms.tintLift)
    builder.uniform("uLiftAdapt", uniforms.liftAdapt)

    // See the Android actual: only backdropBlur pre-blurs; the interior blur lives in the
    // shader so the rim keeps its detail. Skia's blur takes a sigma directly, so a named sigma
    // is passed as is and a legacy radius is passed as the radius it always was.
    val sigma = if (uniforms.backdropSigma > 0f) uniforms.backdropSigma else uniforms.backdropBlur
    val prepared = if (sigma > 0f) {
        ImageFilter.makeBlur(sigma, sigma, FilterTileMode.CLAMP)
    } else {
        null
    }
    return ImageFilter.makeRuntimeShader(
        runtimeShaderBuilder = builder,
        shaderName = "content",
        input = prepared,
    ).asComposeRenderEffect()
}

internal actual fun platformBlurRadiusForSigma(sigma: Float): Float = sigma

internal actual fun createWideKernelEffect(sigmaPx: Float): androidx.compose.ui.graphics.RenderEffect? =
    ImageFilter.makeBlur(sigmaPx, sigmaPx, FilterTileMode.CLAMP).asComposeRenderEffect()

private val contentEffect: RuntimeEffect? by lazy {
    runCatching { RuntimeEffect.makeForShader(GLASS_CONTENT_SHADER_SOURCE) }.getOrNull()
}

internal actual fun createGlassContentRenderEffect(
    uniforms: GlassUniforms,
): androidx.compose.ui.graphics.RenderEffect? {
    val effect = contentEffect ?: return null
    if (uniforms.width <= 0f || uniforms.height <= 0f) return null

    val builder = RuntimeShaderBuilder(effect)
    builder.uniform("uSize", uniforms.width, uniforms.height)
    builder.uniform("uPad", uniforms.pad)
    builder.uniform(
        "uRadii",
        uniforms.radii.getOrElse(0) { 0f },
        uniforms.radii.getOrElse(1) { 0f },
        uniforms.radii.getOrElse(2) { 0f },
        uniforms.radii.getOrElse(3) { 0f },
    )
    builder.uniform("uCornerPower", uniforms.cornerPower)
    builder.uniform("uShapeKind", uniforms.shapeKind)
    builder.uniform("uFieldRange", uniforms.fieldRange)
    builder.uniform("uFieldScale", uniforms.fieldScale)
    builder.child("field", uniforms.field?.let { bitmap ->
        org.jetbrains.skia.Image.makeFromBitmap(bitmap.asSkiaBitmap())
            .makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP)
    } ?: placeholderFieldShader)
    builder.uniform(
        "uFuse",
        uniforms.fuse.getOrElse(0) { 0f },
        uniforms.fuse.getOrElse(1) { 0f },
        uniforms.fuse.getOrElse(2) { 0f },
        uniforms.fuse.getOrElse(3) { 0f },
    )
    builder.uniform("uFuseShape", uniforms.fuseRadius, uniforms.fuseWidth)
    builder.uniform("uRefractBand", uniforms.refractBand)
    builder.uniform("uRefractDepth", uniforms.refractDepth)
    builder.uniform("uIor", uniforms.ior)
    builder.uniform("uBevelPower", uniforms.bevelPower)
    builder.uniform("uAberration", uniforms.aberration)
    builder.uniform("uScale", uniforms.scale)
    builder.uniform("uMaterialize", uniforms.materialize)
    builder.uniform("uTouch", uniforms.touchX, uniforms.touchY)
    builder.uniform("uTouchAmt", uniforms.touchAmount)
    builder.uniform("uProfile", uniforms.profile)
    builder.uniform("uFormation", uniforms.formation)
    builder.uniform("uHeldLens", uniforms.heldLens)
    builder.uniform("uHeldMagnification", uniforms.heldMagnification)
    builder.uniform("uRestMap", uniforms.restMap)
    builder.uniform(
        "uBody",
        uniforms.body.getOrElse(0) { 0f },
        uniforms.body.getOrElse(1) { 0f },
        uniforms.body.getOrElse(2) { 0f },
        uniforms.body.getOrElse(3) { 0f },
    )
    builder.uniform("uBodyY", uniforms.bodyY)
    builder.uniform("uBodyKind", uniforms.bodyKind)
    builder.uniform("uPoseA", uniforms.poseA[0], uniforms.poseA[1], uniforms.poseA[2], uniforms.poseA[3])
    builder.uniform("uPoseAInv", uniforms.poseAInv[0], uniforms.poseAInv[1], uniforms.poseAInv[2], uniforms.poseAInv[3])
    builder.uniform("uPoseC", uniforms.poseC[0], uniforms.poseC[1], uniforms.poseC[2], uniforms.poseC[3])
    builder.uniform("uPoseD", uniforms.poseD[0], uniforms.poseD[1], uniforms.poseD[2], uniforms.poseD[3])
    builder.uniform("uEndpointAlpha", uniforms.endpointAlpha)
    builder.uniform("uEdgeFold", uniforms.edgeFold)
    builder.uniform("uInkSplit", uniforms.inkSplit)
    builder.uniform("uHeldInk", uniforms.heldInk)
    return ImageFilter.makeRuntimeShader(
        runtimeShaderBuilder = builder,
        shaderName = "content",
        input = null,
    ).asComposeRenderEffect()
}

private val endpointEffect: RuntimeEffect? by lazy {
    runCatching { RuntimeEffect.makeForShader(GLASS_ENDPOINT_SHADER_SOURCE) }.getOrNull()
}

internal actual fun createGlassEndpointRenderEffect(
    uniforms: GlassUniforms,
): androidx.compose.ui.graphics.RenderEffect? {
    val effect = endpointEffect ?: return null
    if (uniforms.width <= 0f || uniforms.height <= 0f) return null

    val builder = RuntimeShaderBuilder(effect)
    builder.uniform("uSize", uniforms.width, uniforms.height)
    builder.uniform("uPad", uniforms.pad)
    builder.uniform(
        "uRadii",
        uniforms.radii.getOrElse(0) { 0f },
        uniforms.radii.getOrElse(1) { 0f },
        uniforms.radii.getOrElse(2) { 0f },
        uniforms.radii.getOrElse(3) { 0f },
    )
    builder.uniform(
        "uFuse",
        uniforms.fuse.getOrElse(0) { 0f },
        uniforms.fuse.getOrElse(1) { 0f },
        uniforms.fuse.getOrElse(2) { 0f },
        uniforms.fuse.getOrElse(3) { 0f },
    )
    builder.uniform("uFuseShape", uniforms.fuseRadius, uniforms.fuseWidth)
    builder.uniform("uCornerPower", uniforms.cornerPower)
    builder.uniform("uShapeKind", uniforms.shapeKind)
    builder.uniform("uFieldRange", uniforms.fieldRange)
    builder.uniform("uFieldScale", uniforms.fieldScale)
    builder.child("field", uniforms.field?.let { bitmap ->
        org.jetbrains.skia.Image.makeFromBitmap(bitmap.asSkiaBitmap())
            .makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP)
    } ?: placeholderFieldShader)
    builder.uniform(
        "uBody",
        uniforms.body.getOrElse(0) { 0f },
        uniforms.body.getOrElse(1) { 0f },
        uniforms.body.getOrElse(2) { 0f },
        uniforms.body.getOrElse(3) { 0f },
    )
    builder.uniform("uBodyY", uniforms.bodyY)
    builder.uniform("uBodyKind", uniforms.bodyKind)
    builder.uniform("uPoseA", uniforms.poseA[0], uniforms.poseA[1], uniforms.poseA[2], uniforms.poseA[3])
    builder.uniform("uPoseAInv", uniforms.poseAInv[0], uniforms.poseAInv[1], uniforms.poseAInv[2], uniforms.poseAInv[3])
    builder.uniform("uPoseC", uniforms.poseC[0], uniforms.poseC[1], uniforms.poseC[2], uniforms.poseC[3])
    builder.uniform("uPoseD", uniforms.poseD[0], uniforms.poseD[1], uniforms.poseD[2], uniforms.poseD[3])
    return ImageFilter.makeRuntimeShader(
        runtimeShaderBuilder = builder,
        shaderName = "endpoint",
        input = null,
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
    builder.uniform("uIor", uniforms.ior)
    builder.uniform("uBevelPower", uniforms.bevelPower)
    builder.uniform("uFresnel", uniforms.fresnel)
    builder.uniform("uHiChroma", uniforms.highlightChroma)
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
    builder.uniform("uCounterLight", uniforms.counterLight)
    builder.uniform("uEdgeShadow", uniforms.edgeShadow)
    builder.uniform("uRimSoft", uniforms.rimSoft)
    builder.uniform("uTintAbsorb", uniforms.tintAbsorb)
    builder.uniform("uEdgeLight", uniforms.edgeLight)
    builder.uniform("uBevelPeak", uniforms.bevelPeak)
    builder.uniform(
        "uTint",
        uniforms.tint.red,
        uniforms.tint.green,
        uniforms.tint.blue,
        uniforms.tint.alpha,
    )
    builder.uniform("uInnerShadow", uniforms.innerShadow)
    builder.uniform("uAdaptive", uniforms.adaptivity)
    builder.uniform("uProfile", uniforms.profile)
    builder.uniform("uFormation", 0f)
    builder.uniform("uHeldLens", 0f)
    builder.uniform("uRestMap", uniforms.restMap)
    builder.uniform("uWideStrip", uniforms.wideStrip)
    builder.uniform("uWideScale", uniforms.wideScale)
    builder.uniform("uFineShare", uniforms.fineShare)
    builder.uniform("uWideKernel", uniforms.wideKernel)
    builder.uniform("uLift", uniforms.tintLift)
    builder.uniform("uLiftAdapt", uniforms.liftAdapt)

    // See the Android actual: only backdropBlur pre-blurs; the interior blur lives in the
    // shader so the rim keeps its detail.
    val sigma = if (uniforms.backdropSigma > 0f) uniforms.backdropSigma else uniforms.backdropBlur
    val prepared = if (sigma > 0f) {
        ImageFilter.makeBlur(sigma, sigma, FilterTileMode.CLAMP)
    } else {
        null
    }
    return ImageFilter.makeRuntimeShader(
        runtimeShaderBuilder = builder,
        shaderName = "content",
        input = prepared,
    ).asComposeRenderEffect()
}
