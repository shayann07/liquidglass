package com.wexpa.liquidglass

import org.jetbrains.skia.Bitmap
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
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Reads the geometry of the three shaders directly, as the source coordinate every pixel samples.
 *
 * The backdrop bound as `content` is coordinate-coded and read back in two passes per axis: the first
 * carries the coordinate coarsely in red (one level per ~2 px) and finely in green and blue with a 16 px
 * period, the two a half period apart so that wherever bilinear filtering straddles one channel's wrap
 * the other is mid-period; the second pass carries a 4 px period in the same two-phase form, resolved
 * against the first. With every photometric term switched off (no tint, lift, blur, wide kernel,
 * dispersion, lighting or contour) each shader's output is the backdrop it sampled, so the decoded value
 * is the source position of that pixel to 4/255 = 0.016 px (encoding self-check: [encodingError]), and
 * the alpha is the coverage. Four identical-uniform renders per field.
 *
 * That is the observable the shared-path gates are written on: where a pixel samples from, on the
 * complete caller path — materialisation, formation, corner mix, fades, source transforms, sampling —
 * not the lookup alone.
 */
internal object GlassSourceField {

    enum class Kind { PANEL, CONTENT, CONTAINER }

    class Field(val width: Int, val height: Int, val pad: Int, val sx: FloatArray, val sy: FloatArray, val alpha: FloatArray) {
        val layerWidth get() = width + pad * 2
        val layerHeight get() = height + pad * 2
        fun index(x: Int, y: Int) = y * layerWidth + x
    }

    private const val COARSE_PERIOD = 16f
    private const val FINE_PERIOD = 4f

    private val panel: RuntimeEffect by lazy { RuntimeEffect.makeForShader(GLASS_SHADER_SOURCE) }
    private val content: RuntimeEffect by lazy { RuntimeEffect.makeForShader(GLASS_CONTENT_SHADER_SOURCE) }
    private val container: RuntimeEffect by lazy { RuntimeEffect.makeForShader(GLASS_CONTAINER_SHADER_SOURCE) }

    /**
     * @param width panel width in layer px (already multiplied by the render scale if one is emulated).
     * @param radii corner radii in layer px, TL TR BR BL (the container takes the first for its member).
     * @param refractBand the band W in layer px.
     */
    fun render(
        kind: Kind,
        width: Int,
        height: Int,
        pad: Int,
        radii: FloatArray,
        refractBand: Float,
        profile: Float = 1f,
        formation: Float = 0f,
        heldLens: Float = 0f,
        restMap: Float = 0f,
        materialize: Float = 1f,
        cornerPower: Float = 2f,
        refractDepth: Float = 0f,
        /** The V3 two-disk body, in the element's own (uncentred) coordinates; null keeps uRadii. */
        body: GlassBody? = null,
        /** Straight-run fold strength on the measured profile; 0 keeps the shallow rest offset. */
        edgeFold: Float = 0f,
        /** 1 reads the continuous held ink map instead of the seamed one (content pass only). */
        heldInk: Float = 0f,
    ): Field {
        val w = width + pad * 2
        val h = height + pad * 2
        fun pass(alongX: Boolean, period: Float) =
            pass(kind, width, height, pad, radii, refractBand, profile, formation, heldLens, restMap, materialize, cornerPower, w, h, alongX, period, refractDepth, body, edgeFold, heldInk)
        val (cx, alpha) = pass(true, COARSE_PERIOD)
        val (fx, _) = pass(true, FINE_PERIOD)
        val (cy, _) = pass(false, COARSE_PERIOD)
        val (fy, _) = pass(false, FINE_PERIOD)
        val sx = FloatArray(w * h) { i -> refine(cx[i], fx[i]) }
        val sy = FloatArray(w * h) { i -> refine(cy[i], fy[i]) }
        return Field(width, height, pad, sx, sy, alpha)
    }

    fun render(kind: Kind, width: Int, height: Int, pad: Int, radius: Float, refractBand: Float, profile: Float = 1f,
               formation: Float = 0f, heldLens: Float = 0f, restMap: Float = 0f, materialize: Float = 1f, cornerPower: Float = 2f): Field =
        render(kind, width, height, pad, floatArrayOf(radius, radius, radius, radius), refractBand, profile, formation, heldLens, restMap, materialize, cornerPower)

    /** Coarse channel plus two fine channels of [period], a half period apart. */
    private fun encode(v: Int, n: Int, period: Float): Int {
        val p = period.toInt()
        val coarse = (v * 255f / (n - 1)).roundToInt().coerceIn(0, 255)
        val fine = ((v % p) / period * 255f).roundToInt().coerceIn(0, 255)
        val fine2 = (((v + p / 2) % p) / period * 255f).roundToInt().coerceIn(0, 255)
        return (coarse shl 16) or (fine shl 8) or fine2
    }

    /** The coordinate the fine channels name, using whichever is far from its wrap relative to [estimate]. */
    private fun decodeFine(estimate: Float, fine: Float, fine2: Float, period: Float): Float {
        val m1 = ((estimate % period) + period) % period
        val dist1 = minOf(m1, period - m1)
        val m2 = (((estimate + period / 2) % period) + period) % period
        val dist2 = minOf(m2, period - m2)
        return if (dist1 >= dist2) {
            val frac = fine / 255f * period
            val k = ((estimate - frac) / period).roundToInt()
            k * period + frac
        } else {
            val frac = fine2 / 255f * period
            val k = ((estimate + period / 2 - frac) / period).roundToInt()
            k * period + frac - period / 2
        }
    }

    /** The second pass's 4 px phases resolved against the first pass's 16 px result. */
    private fun refine(coarseResolved: Float, finePacked: Float): Float {
        if (coarseResolved.isNaN() || finePacked.isNaN()) return Float.NaN
        val fine = (finePacked.toInt() ushr 8) and 0xFF
        val fine2 = finePacked.toInt() and 0xFF
        // The first pass is already in the shader's frame (+0.5 applied); undo it for the wrap arithmetic.
        return decodeFine(coarseResolved - 0.5f, fine.toFloat(), fine2.toFloat(), FINE_PERIOD) + 0.5f
    }

    private fun pass(
        kind: Kind, width: Int, height: Int, pad: Int, radii: FloatArray, refractBand: Float,
        profile: Float, formation: Float, heldLens: Float, restMap: Float, materialize: Float, cornerPower: Float,
        w: Int, h: Int, alongX: Boolean, period: Float, refractDepth: Float,
        body: GlassBody?, edgeFold: Float, heldInk: Float,
    ): Pair<FloatArray, FloatArray> {
        val info = ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.PREMUL)
        val pixels = IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            (0xFF shl 24) or if (alongX) encode(x, w, period) else encode(y, h, period)
        }
        val src = Bitmap()
        src.allocPixels(info)
        src.installPixels(info, intsToBgra(pixels), w * 4)
        val srcShader = Image.makeFromBitmap(src).makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, SamplingMode.LINEAR)

        val effect = when (kind) { Kind.PANEL -> panel; Kind.CONTENT -> content; Kind.CONTAINER -> container }
        val b = RuntimeShaderBuilder(effect)
        b.child("content", srcShader)
        b.uniform("uSize", width.toFloat(), height.toFloat())
        b.uniform("uPad", pad.toFloat())
        b.uniform("uRefractBand", refractBand)
        b.uniform("uRefractDepth", refractDepth)
        b.uniform("uIor", 1.5f)
        b.uniform("uBevelPower", 2f)
        b.uniform("uAberration", 0f)
        b.uniform("uProfile", profile)
        b.uniform("uFormation", formation)
        b.uniform("uHeldLens", heldLens)
        b.uniform("uHeldMagnification", 0f)
        b.uniform("uHeldGlow", 1f)
        if (kind == Kind.PANEL) b.uniform("uHeldEdgeRecovery", 0f)
        b.uniform("uRestMap", restMap)
        when (kind) {
            Kind.PANEL, Kind.CONTENT -> {
                b.child("field", srcShader)
                b.uniform("uRadii", radii[0], radii[1], radii[2], radii[3])
                b.uniform("uFuse", 0f, 0f, 0f, 0f)
                b.uniform("uFuseShape", 0f, 0f)
                b.uniform("uCornerPower", cornerPower)
                b.uniform("uShapeKind", 0f)
                b.uniform("uFieldRange", 1f)
                b.uniform("uFieldScale", 1f)
                b.uniform("uScale", 0f)
                b.uniform("uMaterialize", materialize)
                b.uniform("uTouch", -1e5f, -1e5f)
                b.uniform("uTouchAmt", 0f)
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
            // The pose body is off in these fixtures; the uniforms still have to be
            // set, because a declared uniform left unbound reads whatever was there.
            b.uniform("uPoseA", 1f, 0f, 0f, 1f)
            b.uniform("uPoseAInv", 1f, 0f, 0f, 1f)
            b.uniform("uPoseC", 0f, 0f, 0f, 0f)
            b.uniform("uPoseD", 1f, 1f, 1f, 0f)
                }
                b.uniform("uEndpointAlpha", 0f)
                b.uniform("uEdgeFold", edgeFold)
            }
            Kind.CONTAINER -> {
                b.uniform("uRect", FloatArray(MAX_GLASS_MEMBERS * 4).also { it[0] = 0f; it[1] = 0f; it[2] = width.toFloat(); it[3] = height.toFloat() })
                b.uniform("uRadius", FloatArray(MAX_GLASS_MEMBERS).also { it[0] = radii[0] })
                b.uniform("uCount", 1f)
                b.uniform("uMerge", 0f)
            }
        }
        if (kind != Kind.CONTENT) {
            b.uniform("uBackdrop", 0f, 0f, w.toFloat(), h.toFloat())
            b.uniform("uBase", 0f, 0f, 0f)
            b.uniform("uMirror", 0f)
            b.uniform("uBlur", 0f)
            b.uniform("uBevel", 0f)
            b.uniform("uLight", 0f, 0f) // no facing anywhere: every lit term is exactly zero
            b.uniform("uSpecular", 0f)
            b.uniform("uSpecularPow", 4f)
            b.uniform("uCounterLight", 0f)
            b.uniform("uEdgeLight", 0f)
            b.uniform("uBevelPeak", 0f)
            b.uniform("uEdgeShadow", 0f)
            b.uniform("uRimSoft", 0f)
            b.uniform("uTintAbsorb", 0f)
            b.uniform("uFresnel", 0f)
            b.uniform("uHiChroma", 0f)
            b.uniform("uInnerShadow", 0f)
            b.uniform("uTint", 0f, 0f, 0f, 0f)
            b.uniform("uAdaptive", 0f)
            b.uniform("uWideStrip", 0f)
            b.uniform("uWideScale", 0.25f)
            b.uniform("uFineShare", 1f)
            b.uniform("uWideKernel", 0f)
            b.uniform("uLift", 0f)
            b.uniform("uLiftAdapt", 0f)
        }
        if (kind == Kind.CONTENT) {
            b.uniform("uInkSplit", 0f)
            b.uniform("uHeldInk", heldInk)
        }
        if (kind == Kind.PANEL) {
            b.uniform("uLegibility", 0f)
            b.uniform("uFlip", 0f)
            b.uniform("uFrost", 0f)
            b.uniform("uContrast", 0f)
            b.uniform("uDebugCoverage", 0f)
        }

        val surface = Surface.makeRasterN32Premul(w, h)
        surface.canvas.clear(0)
        surface.canvas.drawPaint(Paint().apply { shader = b.makeShader() })
        val out = Bitmap()
        out.allocPixels(ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL))
        check(surface.readPixels(out, 0, 0)) { "readPixels failed" }
        val bytes = out.readPixels()!!
        val coord = FloatArray(w * h); val alpha = FloatArray(w * h)
        val n = if (alongX) w else h
        for (i in 0 until w * h) {
            val bl = bytes[i * 4].toInt() and 0xFF
            val g = bytes[i * 4 + 1].toInt() and 0xFF
            val r = bytes[i * 4 + 2].toInt() and 0xFF
            val a = bytes[i * 4 + 3].toInt() and 0xFF
            alpha[i] = a / 255f
            coord[i] = when {
                a != 255 -> Float.NaN
                // Texel v is centred at coordinate v + 0.5, so a sample at coordinate c decodes to c - 0.5;
                // the field is reported in the shader's own coordinates, where pixel x is at x + 0.5.
                period == COARSE_PERIOD -> decodeFine(r / 255f * (n - 1), g.toFloat(), bl.toFloat(), period) + 0.5f
                else -> ((g shl 8) or bl).toFloat()      // the fine pass keeps its two phases packed for refine()
            }
        }
        return coord to alpha
    }

    private fun intsToBgra(pixels: IntArray): ByteArray {
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

    /** Self-check of the two-pass encoding on integer positions: worst decode error in px. */
    fun encodingError(): Float {
        val n = 600
        var worst = 0f
        for (v in 0 until n) {
            val c = encode(v, n, COARSE_PERIOD); val f = encode(v, n, FINE_PERIOD)
            val coarse = decodeFine(((c ushr 16) and 0xFF) / 255f * (n - 1), ((c ushr 8) and 0xFF).toFloat(), (c and 0xFF).toFloat(), COARSE_PERIOD)
            val fine = decodeFine(coarse, ((f ushr 8) and 0xFF).toFloat(), (f and 0xFF).toFloat(), FINE_PERIOD)
            worst = maxOf(worst, abs(fine - v))
        }
        return worst
    }
}
