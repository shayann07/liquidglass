package com.wexpa.liquidglass

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
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
 * Renders the panel shader off-device, so optics can be asserted numerically.
 *
 * Everything here mirrors what `LiquidGlass.jvm.kt` does at runtime: the same shader text, the
 * same uniform names, a padded backdrop bound as `content`, and the panel inset by [pad] inside
 * it. What it adds is that the result comes back as pixels a test can read, which is the only
 * way to check a claim like "the refraction is continuous across the medial axis" without a
 * phone and a ruler.
 *
 * The backdrop is opaque by construction, as the real host guarantees.
 */
internal object GlassRender {

    private val effect: RuntimeEffect by lazy { RuntimeEffect.makeForShader(GLASS_SHADER_SOURCE) }

    /** Luminance, Rec. 709, of an ARGB pixel — the same measure the device harness uses. */
    fun luma(argb: Int): Double {
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /**
     * @param backdrop called for every pixel of the padded layer, returning opaque ARGB.
     * @return the rendered layer, row-major, width = [width] + 2 * [pad].
     */
    fun render(
        width: Int,
        height: Int,
        pad: Int = 32,
        refractBand: Float = 24f,
        refractDepth: Float = 12f,
        radii: FloatArray = FloatArray(4) { minOf(width, height) / 2f },
        cornerPower: Float = 2f,
        bevelPower: Float = 2f,
        ior: Float = 1.5f,
        aberration: Float = 0f,
        mirror: Float = 0f,
        blur: Float = 0f,
        specular: Float = 0f,
        fresnel: Float = 0f,
        highlightChroma: Float = 0f,
        innerShadow: Float = 0f,
        edgeShadow: Float = 0f,
        rimSoft: Float = 0f,
        tintAbsorb: Float = 0f,
        tintAlpha: Float = 0f,
        tint: Triple<Float, Float, Float> = Triple(1f, 1f, 1f),
        profile: Float = 0f,
        formation: Float = 1f,
        heldLens: Float = 0f,
        wide: Float = 0f,
        fineShare: Float = 1f,
        lift: Float = 0f,
        liftAdapt: Float = 0f,
        bevel: Float = 2f,
        specularPower: Float = 4f,
        counterLight: Float = 1f,
        edgeLight: Float = 1f,
        legibility: Float = 0f,
        backdrop: (x: Int, y: Int) -> Int,
    ): IntArray {
        val w = width + pad * 2
        val h = height + pad * 2

        // The wide tone kernel, as the host builds it: a quarter-scale copy under a Gaussian
        // blur in a strip below the sharp copy. Blurred on the CPU here; the host uses a
        // render effect.
        val wideScale = 0.25f
        val stripW = if (wide > 0f) kotlin.math.ceil(w * wideScale).toInt() else 0
        val stripH = if (wide > 0f) kotlin.math.ceil(h * wideScale).toInt() else 0
        val lh = h + stripH
        val info = ImageInfo(w, lh, ColorType.BGRA_8888, ColorAlphaType.PREMUL)
        val src = Bitmap()
        src.allocPixels(info)
        val pixels = IntArray(w * lh) { i ->
            val x = i % w
            val y = i / w
            if (y < h) backdrop(x, y) or (0xFF shl 24) else 0xFF000000.toInt()
        }
        if (stripH > 0) {
            // The wide kernel sees only the element's own footprint: the backdrop inside the
            // panel, edge-replicated beyond it. Set -Dliquidglass.wideClamp=false for the padded
            // version.
            val clamp = System.getProperty("liquidglass.wideClamp", "false").toBoolean()
            val src = if (clamp) IntArray(w * h) { i ->
                val x = (i % w).coerceIn(pad, pad + width - 1); val y = (i / w).coerceIn(pad, pad + height - 1)
                pixels[y * w + x]
            } else pixels
            val strip = blurredQuarter(src, w, h, stripW, stripH, wide * wideScale)
            for (y in 0 until stripH) for (x in 0 until stripW) pixels[(h + y) * w + x] = strip[y * stripW + x]
        }
        src.installPixels(info, intsToBgra(pixels, w, lh), w * 4)
        // Bilinear, as the platform samples a recorded layer: the fold compresses content by
        // 1.3x in the mirrored zone, and nearest sampling would drop single-pixel features there.
        val srcShader = Image.makeFromBitmap(src)
            .makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, SamplingMode.LINEAR)

        val b = RuntimeShaderBuilder(effect)
        b.child("content", srcShader)
        b.child("field", srcShader) // bound but unread while uShapeKind is 0
        b.uniform("uSize", width.toFloat(), height.toFloat())
        b.uniform("uPad", pad.toFloat())
        b.uniform("uBackdrop", 0f, 0f, w.toFloat(), h.toFloat())
        b.uniform("uBase", 0f, 0f, 0f)
        b.uniform("uRadii", radii[0], radii[1], radii[2], radii[3])
        b.uniform("uFuse", 0f, 0f, 0f, 0f)
        b.uniform("uFuseShape", 0f, 0f)
        b.uniform("uRefractBand", refractBand)
        b.uniform("uRefractDepth", refractDepth)
        b.uniform("uIor", ior)
        b.uniform("uBevelPower", bevelPower)
        b.uniform("uCornerPower", cornerPower)
        b.uniform("uShapeKind", 0f)
        b.uniform("uFieldRange", 1f)
        b.uniform("uFieldScale", 1f)
        b.uniform("uAberration", aberration)
        b.uniform("uMirror", mirror)
        b.uniform("uBlur", blur)
        b.uniform("uBevel", bevel)
        b.uniform("uLight", 0f, -1f)
        b.uniform("uSpecular", specular)
        b.uniform("uSpecularPow", specularPower)
        b.uniform("uCounterLight", counterLight)
        b.uniform("uEdgeLight", edgeLight)
        b.uniform("uBevelPeak", 0f)
        b.uniform("uEdgeShadow", edgeShadow)
        b.uniform("uRimSoft", rimSoft)
        b.uniform("uTintAbsorb", tintAbsorb)
        b.uniform("uFresnel", fresnel)
        b.uniform("uHiChroma", highlightChroma)
        b.uniform("uInnerShadow", innerShadow)
        b.uniform("uTint", tint.first, tint.second, tint.third, tintAlpha)
        b.uniform("uAdaptive", 0f)
        b.uniform("uLegibility", legibility)
        b.uniform("uProfile", profile)
        b.uniform("uFormation", formation)
        b.uniform("uHeldLens", heldLens)
        b.uniform("uWideStrip", if (stripH > 0) h.toFloat() else 0f)
        b.uniform("uWideScale", wideScale)
        b.uniform("uFineShare", fineShare)
        b.uniform("uWideKernel", wide)
        b.uniform("uLift", lift)
        b.uniform("uLiftAdapt", liftAdapt)
        b.uniform("uScale", 0f)
        b.uniform("uFlip", 0f)
        b.uniform("uTouch", width / 2f, height / 2f)
        b.uniform("uTouchAmt", 0f)
        b.uniform("uMaterialize", 1f)
        b.uniform("uFrost", 0f)
        b.uniform("uContrast", 0f)

        val surface = Surface.makeRasterN32Premul(w, h)
        val canvas: Canvas = surface.canvas
        // Transparent, so pixels outside the panel read as such and composite as nothing.
        canvas.clear(0)
        canvas.drawPaint(Paint().apply { shader = b.makeShader() })

        val out = Bitmap()
        out.allocPixels(ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.PREMUL))
        check(surface.readPixels(out, 0, 0)) { "readPixels failed" }
        return bgraToInts(out.readPixels()!!, w, h)
    }

    /** A box-downsampled copy of the sharp region under a separable Gaussian of [sigma] px. */
    private fun blurredQuarter(pixels: IntArray, w: Int, h: Int, sw: Int, sh: Int, sigma: Float): IntArray {
        fun lin(v: Int) = v.toFloat()
        fun enc(v: Float) = v.toInt()
        val small = Array(3) { FloatArray(sw * sh) }
        for (y in 0 until sh) for (x in 0 until sw) {
            var r = 0f; var g = 0f; var bl = 0f; var n = 0
            for (dy in 0 until 4) for (dx in 0 until 4) {
                val sx = (x * 4 + dx).coerceAtMost(w - 1)
                val sy = (y * 4 + dy).coerceAtMost(h - 1)
                val p = pixels[sy * w + sx]
                r += lin((p ushr 16) and 0xFF); g += lin((p ushr 8) and 0xFF); bl += lin(p and 0xFF); n++
            }
            small[0][y * sw + x] = r / n; small[1][y * sw + x] = g / n; small[2][y * sw + x] = bl / n
        }
        val radius = kotlin.math.ceil(sigma * 3).toInt().coerceAtLeast(1)
        val kernel = FloatArray(radius * 2 + 1) { kotlin.math.exp(-((it - radius) * (it - radius)) / (2f * sigma * sigma)) }
        val ksum = kernel.sum()
        fun pass(src: FloatArray, horizontal: Boolean): FloatArray {
            val dst = FloatArray(sw * sh)
            for (y in 0 until sh) for (x in 0 until sw) {
                var acc = 0f
                for (k in -radius..radius) {
                    val sx = if (horizontal) (x + k).coerceIn(0, sw - 1) else x
                    val sy = if (horizontal) y else (y + k).coerceIn(0, sh - 1)
                    acc += src[sy * sw + sx] * kernel[k + radius]
                }
                dst[y * sw + x] = acc / ksum
            }
            return dst
        }
        val ch = small.map { pass(pass(it, true), false) }
        return IntArray(sw * sh) { i ->
            val r = enc(ch[0][i]).coerceIn(0, 255); val g = enc(ch[1][i]).coerceIn(0, 255); val bl = enc(ch[2][i]).coerceIn(0, 255)
            (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
        }
    }

    private fun intsToBgra(pixels: IntArray, w: Int, h: Int): ByteArray {
        val bytes = ByteArray(w * h * 4)
        for (i in pixels.indices) {
            val p = pixels[i]
            bytes[i * 4] = (p and 0xFF).toByte()
            bytes[i * 4 + 1] = ((p ushr 8) and 0xFF).toByte()
            bytes[i * 4 + 2] = ((p ushr 16) and 0xFF).toByte()
            bytes[i * 4 + 3] = ((p ushr 24) and 0xFF).toByte()
        }
        return bytes
    }

    private fun bgraToInts(bytes: ByteArray, w: Int, h: Int): IntArray {
        val out = IntArray(w * h)
        for (i in out.indices) {
            val bl = bytes[i * 4].toInt() and 0xFF
            val g = bytes[i * 4 + 1].toInt() and 0xFF
            val r = bytes[i * 4 + 2].toInt() and 0xFF
            val a = bytes[i * 4 + 3].toInt() and 0xFF
            out[i] = (a shl 24) or (r shl 16) or (g shl 8) or bl
        }
        return out
    }
}
