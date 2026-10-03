package com.wexpa.liquidglass

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface

/**
 * The three passes of the endpoint compositor, off-device, in the same order the modifier runs
 * them: the material emits the opaque endpoint `B1`, the content pass emits the selected ink
 * premultiplied and unmasked, the two are composed in one offscreen layer, and the aperture pass
 * multiplies the result by coverage.
 *
 * Each intermediate comes back separately, so a test can check the algebra against the passes'
 * own declared outputs rather than against a restatement of the implementation:
 *
 * ```
 * C1 = c1 + (1 - a1) B1        the offscreen source-over
 * out = (m C1, m)              the aperture pass
 * C  = out over C0             what a viewer sees
 * ```
 */
internal object GlassEndpointRender {

    private val panel: RuntimeEffect by lazy { RuntimeEffect.makeForShader(GLASS_SHADER_SOURCE) }
    private val content: RuntimeEffect by lazy { RuntimeEffect.makeForShader(GLASS_CONTENT_SHADER_SOURCE) }
    private val aperture: RuntimeEffect by lazy { RuntimeEffect.makeForShader(GLASS_ENDPOINT_SHADER_SOURCE) }

    class Result(
        val width: Int,
        val height: Int,
        val pad: Int,
        /** `B1`, opaque: what the selector's material shows with no semantic ink anywhere. */
        val b1: IntArray,
        /** `(c1, a1)`, premultiplied: the selected ink through the same geometry, unmasked. */
        val ink: IntArray,
        /** `C1`, opaque: the offscreen composition of the two. */
        val endpoint: IntArray,
        /** `(m C1, m)`, premultiplied: what is drawn over the page. */
        val masked: IntArray,
    ) {
        val layerWidth get() = width + pad * 2
        fun index(x: Int, y: Int) = y * layerWidth + x
    }

    /**
     * @param backdrop opaque ARGB for every pixel of the padded layer: the page plus whatever
     *        glass the selector looks through, already composed, as the host records it.
     * @param ink1 premultiplied ARGB of the selected semantic ink, transparent where there is none.
     */
    fun render(
        width: Int,
        height: Int,
        pad: Int,
        radii: FloatArray = FloatArray(4) { minOf(width, height) / 2f },
        refractBand: Float = 24f,
        refractDepth: Float = 10f,
        profile: Float = 2f,
        heldLens: Float = 1f,
        heldMagnification: Float = 0f,
        restMap: Float = 0f,
        edgeFold: Float = 0f,
        heldInk: Float = 0f,
        inkSplit: Float = 0f,
        aberration: Float = 0f,
        tintAlpha: Float = 0f,
        tint: Triple<Float, Float, Float> = Triple(1f, 1f, 1f),
        specular: Float = 0f,
        edgeLight: Float = 0f,
        edgeShadow: Float = 0f,
        bevel: Float = 0f,
        counterLight: Float = 0f,
        body: GlassBody? = null,
        backdrop: (x: Int, y: Int) -> Int,
        ink1: (x: Int, y: Int) -> Int,
    ): Result {
        val w = width + pad * 2
        val h = height + pad * 2
        val backdropShader = shaderOf(w, h, opaque = true, pixel = backdrop)
        val inkShader = shaderOf(w, h, opaque = false, pixel = ink1)

        fun bindGeometry(b: RuntimeShaderBuilder) {
            b.uniform("uSize", width.toFloat(), height.toFloat())
            b.uniform("uPad", pad.toFloat())
            b.uniform("uRadii", radii[0], radii[1], radii[2], radii[3])
            b.uniform("uFuse", 0f, 0f, 0f, 0f)
            b.uniform("uFuseShape", 0f, 0f)
            b.uniform("uCornerPower", 2f)
            b.uniform("uShapeKind", 0f)
            b.uniform("uFieldRange", 1f)
            b.uniform("uFieldScale", 1f)
            if (body != null) {
                b.uniform(
                    "uBody",
                    body.leftCentreX - width / 2f,
                    body.rightCentreX - width / 2f,
                    body.leftRadius,
                    body.rightRadius,
                )
                b.uniform("uBodyY", body.cy - height / 2f)
                b.uniform("uBodyKind", 1f)
            } else {
                b.uniform("uBody", 0f, 0f, 0f, 0f)
                b.uniform("uBodyY", 0f)
                b.uniform("uBodyKind", 0f)
                // The pose body is off in these fixtures; the uniforms still have to be
                // set, because a declared uniform left unbound reads whatever was there.
                b.uniform("uPoseA", 1f, 0f, 0f, 1f)
                b.uniform("uPoseAInv", 1f, 0f, 0f, 1f)
                b.uniform("uPoseC", 0f, 0f, 0f, 0f)
                b.uniform("uPoseD", 1f, 1f, 1f, 0f)
            // The pose body is off in these fixtures; the uniforms still have to be
            // set, because a declared uniform left unbound reads whatever was there.
            b.uniform("uPoseA", 1f, 0f, 0f, 1f)
            b.uniform("uPoseAInv", 1f, 0f, 0f, 1f)
            b.uniform("uPoseC", 0f, 0f, 0f, 0f)
            b.uniform("uPoseD", 1f, 1f, 1f, 0f)
            }
        }

        // 1. the material, emitting the opaque endpoint B1
        val pb = RuntimeShaderBuilder(panel)
        pb.child("content", backdropShader)
        pb.child("field", backdropShader)
        bindGeometry(pb)
        pb.uniform("uBackdrop", 0.5f, 0.5f, w - 0.5f, h - 0.5f)
        pb.uniform("uBase", 0f, 0f, 0f)
        pb.uniform("uRefractBand", refractBand)
        pb.uniform("uRefractDepth", refractDepth)
        pb.uniform("uIor", 1.5f)
        pb.uniform("uBevelPower", 2f)
        pb.uniform("uAberration", aberration)
        pb.uniform("uMirror", 0f)
        pb.uniform("uBlur", 0f)
        pb.uniform("uBevel", bevel)
        pb.uniform("uLight", 0f, -1f)
        pb.uniform("uSpecular", specular)
        pb.uniform("uSpecularPow", 4f)
        pb.uniform("uCounterLight", counterLight)
        pb.uniform("uEdgeLight", edgeLight)
        pb.uniform("uBevelPeak", 0f)
        pb.uniform("uEdgeShadow", edgeShadow)
        pb.uniform("uRimSoft", 0f)
        pb.uniform("uTintAbsorb", 0f)
        pb.uniform("uFresnel", 0f)
        pb.uniform("uHiChroma", 0f)
        pb.uniform("uInnerShadow", 0f)
        pb.uniform("uTint", tint.first, tint.second, tint.third, tintAlpha)
        pb.uniform("uAdaptive", 0f)
        pb.uniform("uLegibility", 0f)
        pb.uniform("uProfile", profile)
        pb.uniform("uFormation", 0f)
        pb.uniform("uHeldLens", heldLens)
        pb.uniform("uHeldMagnification", heldMagnification)
        pb.uniform("uHeldGlow", 1f)
        pb.uniform("uHeldEdgeRecovery", 0f)
        pb.uniform("uRestMap", restMap)
        pb.uniform("uEdgeFold", edgeFold)
        pb.uniform("uWideStrip", 0f)
        pb.uniform("uWideScale", 0.25f)
        pb.uniform("uFineShare", 1f)
        pb.uniform("uWideKernel", 0f)
        pb.uniform("uLift", 0f)
        pb.uniform("uLiftAdapt", 0f)
        pb.uniform("uScale", 0f)
        pb.uniform("uFlip", 0f)
        pb.uniform("uTouch", -1e5f, -1e5f)
        pb.uniform("uTouchAmt", 0f)
        pb.uniform("uMaterialize", 1f)
        pb.uniform("uFrost", 0f)
        pb.uniform("uContrast", 0f)
        pb.uniform("uDebugCoverage", 0f)
        pb.uniform("uEndpointAlpha", 1f)
        val b1 = paint(w, h, pb)

        // 2. the ink, premultiplied and unmasked
        val cb = RuntimeShaderBuilder(content)
        cb.child("content", inkShader)
        cb.child("field", inkShader)
        bindGeometry(cb)
        cb.uniform("uRefractBand", refractBand)
        cb.uniform("uRefractDepth", refractDepth)
        cb.uniform("uIor", 1.5f)
        cb.uniform("uBevelPower", 2f)
        cb.uniform("uAberration", aberration)
        cb.uniform("uScale", 0f)
        cb.uniform("uMaterialize", 1f)
        cb.uniform("uTouch", -1e5f, -1e5f)
        cb.uniform("uTouchAmt", 0f)
        cb.uniform("uProfile", profile)
        cb.uniform("uFormation", 0f)
        cb.uniform("uHeldLens", heldLens)
        cb.uniform("uHeldMagnification", heldMagnification)
        cb.uniform("uRestMap", restMap)
        cb.uniform("uEdgeFold", edgeFold)
        cb.uniform("uInkSplit", inkSplit)
        cb.uniform("uHeldInk", heldInk)
        cb.uniform("uEndpointAlpha", 1f)
        val inkOut = paint(w, h, cb)

        // 3. the offscreen endpoint: ink over the opaque material, which is source-over's own
        //    definition of c1 + (1 - a1) B1.
        val endpointSurface = Surface.makeRasterN32Premul(w, h)
        endpointSurface.canvas.clear(0)
        endpointSurface.canvas.drawImage(imageOf(w, h, b1), 0f, 0f)
        endpointSurface.canvas.drawImage(
            imageOf(w, h, inkOut),
            0f,
            0f,
            Paint().apply { blendMode = BlendMode.SRC_OVER },
        )
        val endpoint = readback(endpointSurface, w, h)

        // 4. the aperture: times coverage, giving (m C1, m)
        val ab = RuntimeShaderBuilder(aperture)
        val endpointShader = shaderOf(w, h, opaque = false, pixel = { x, y -> endpoint[y * w + x] })
        ab.child("endpoint", endpointShader)
        ab.child("field", endpointShader)
        bindGeometry(ab)
        val masked = paint(w, h, ab)

        return Result(width, height, pad, b1, inkOut, endpoint, masked)
    }

    private fun paint(w: Int, h: Int, b: RuntimeShaderBuilder): IntArray {
        val surface = Surface.makeRasterN32Premul(w, h)
        surface.canvas.clear(0)
        surface.canvas.drawPaint(Paint().apply { shader = b.makeShader() })
        return readback(surface, w, h)
    }

    private fun readback(surface: Surface, w: Int, h: Int): IntArray {
        val out = Bitmap()
        out.allocPixels(ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.PREMUL))
        check(surface.readPixels(out, 0, 0)) { "readPixels failed" }
        val bytes = out.readPixels()!!
        return IntArray(w * h) { i ->
            val bl = bytes[i * 4].toInt() and 0xFF
            val g = bytes[i * 4 + 1].toInt() and 0xFF
            val r = bytes[i * 4 + 2].toInt() and 0xFF
            val a = bytes[i * 4 + 3].toInt() and 0xFF
            (a shl 24) or (r shl 16) or (g shl 8) or bl
        }
    }

    private fun imageOf(w: Int, h: Int, pixels: IntArray): Image {
        val info = ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.PREMUL)
        val bmp = Bitmap()
        bmp.allocPixels(info)
        bmp.installPixels(info, toBgra(pixels), w * 4)
        return Image.makeFromBitmap(bmp)
    }

    private fun shaderOf(w: Int, h: Int, opaque: Boolean, pixel: (Int, Int) -> Int): org.jetbrains.skia.Shader {
        val info = ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.PREMUL)
        val bmp = Bitmap()
        bmp.allocPixels(info)
        val pixels = IntArray(w * h) { i ->
            val p = pixel(i % w, i / w)
            if (opaque) p or (0xFF shl 24) else p
        }
        bmp.installPixels(info, toBgra(pixels), w * 4)
        return Image.makeFromBitmap(bmp)
            .makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, SamplingMode.LINEAR)
    }

    private fun toBgra(pixels: IntArray): ByteArray {
        val bytes = ByteArray(pixels.size * 4)
        for (i in pixels.indices) {
            val p = pixels[i]
            bytes[i * 4] = (p and 0xFF).toByte()
            bytes[i * 4 + 1] = ((p ushr 8) and 0xFF).toByte()
            bytes[i * 4 + 2] = ((p ushr 16) and 0xFF).toByte()
            bytes[i * 4 + 3] = ((p ushr 24) and 0xFF).toByte()
        }
        return bytes
    }
}
