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
        backdrop: (x: Int, y: Int) -> Int,
    ): IntArray {
        val w = width + pad * 2
        val h = height + pad * 2

        val info = ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.PREMUL)
        val src = Bitmap()
        src.allocPixels(info)
        val pixels = IntArray(w * h) { backdrop(it % w, it / w) or (0xFF shl 24) }
        src.installPixels(info, intsToBgra(pixels, w, h), w * 4)
        val srcShader = Image.makeFromBitmap(src)
            .makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP)

        val b = RuntimeShaderBuilder(effect)
        b.child("content", srcShader)
        b.child("field", srcShader) // bound but unread while uShapeKind is 0
        b.uniform("uSize", width.toFloat(), height.toFloat())
        b.uniform("uPad", pad.toFloat())
        b.uniform("uBackdrop", 0f, 0f, w.toFloat(), h.toFloat())
        b.uniform("uBase", 0f, 0f, 0f)
        b.uniform("uRadii", radii[0], radii[1], radii[2], radii[3])
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
        b.uniform("uBevel", 2f)
        b.uniform("uLight", 0f, -1f)
        b.uniform("uSpecular", specular)
        b.uniform("uSpecularPow", 4f)
        b.uniform("uCounterLight", 1f)
        b.uniform("uEdgeLight", 1f)
        b.uniform("uBevelPeak", 0f)
        b.uniform("uEdgeShadow", edgeShadow)
        b.uniform("uRimSoft", rimSoft)
        b.uniform("uTintAbsorb", tintAbsorb)
        b.uniform("uFresnel", fresnel)
        b.uniform("uHiChroma", highlightChroma)
        b.uniform("uInnerShadow", innerShadow)
        b.uniform("uTint", tint.first, tint.second, tint.third, tintAlpha)
        b.uniform("uAdaptive", 0f)
        b.uniform("uLegibility", 0f)
        b.uniform("uScale", 0f)
        b.uniform("uFlip", 0f)
        b.uniform("uTouch", width / 2f, height / 2f)
        b.uniform("uTouchAmt", 0f)
        b.uniform("uMaterialize", 1f)
        b.uniform("uFrost", 0f)
        b.uniform("uContrast", 0f)

        val surface = Surface.makeRasterN32Premul(w, h)
        val canvas: Canvas = surface.canvas
        canvas.clear(0xFF000000.toInt())
        canvas.drawPaint(Paint().apply { shader = b.makeShader() })

        val out = Bitmap()
        out.allocPixels(ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.PREMUL))
        check(surface.readPixels(out, 0, 0)) { "readPixels failed" }
        return bgraToInts(out.readPixels()!!, w, h)
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
