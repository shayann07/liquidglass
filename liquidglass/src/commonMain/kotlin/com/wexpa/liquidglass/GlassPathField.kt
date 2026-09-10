package com.wexpa.liquidglass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * A signed distance field for a shape the shader cannot describe analytically.
 *
 * The material's whole optical model runs off a distance field: depth from the rim drives the
 * refraction band, and the field's gradient is the surface normal. For rectangles, rounded
 * rectangles, capsules, circles and squircles that field has a closed form and the shader
 * evaluates it directly. For anything else — a star, a blob, a hand-drawn `GenericShape` — it
 * does not, and the honest options are to refuse the shape or to measure it.
 *
 * This measures it: rasterise the outline once, run an exact Euclidean distance transform over
 * the result, and hand the shader a texture to read instead of a formula to evaluate. It is
 * rebuilt only when the shape or the size changes, never per frame.
 *
 * Two things about the encoding are deliberate. The field is stored in eight bits over a range
 * of about one and a half refraction bands, which quantises distance to roughly a pixel — fine,
 * because the shader only ever asks two questions of it: how deep into the band a pixel is
 * (about 1% of the band) and which way the surface faces, and it answers the second by
 * differencing over a wide epsilon where a pixel of noise is nothing. And it is computed at
 * half resolution, because a distance field is smooth almost everywhere and bilinear sampling
 * puts back more than the halving takes out.
 */
internal class GlassPathField(
    val bitmap: ImageBitmap,
    /** Distance in px that the 0..1 stored range covers, centred on zero. */
    val range: Float,
    /** Layer px to field texel. Below 1 because the field is stored downscaled. */
    val scale: Float,
    /** Layer-space size the field was rasterised for, so a stale one is detectable. */
    val layerWidth: Int,
    val layerHeight: Int,
)

/** Half resolution: a distance field is smooth, and bilinear sampling covers the difference. */
private const val FIELD_SCALE = 2

/**
 * Builds the field for [shape] at [size], covering the padded layer.
 *
 * Keyed on everything that changes the result, so scrolling and pressing never touch it and a
 * resize rebuilds it exactly once.
 */
@Composable
internal fun rememberGlassPathField(
    shape: Shape,
    size: Size,
    pad: Float,
    band: Float,
    density: Density,
    layoutDirection: LayoutDirection,
): GlassPathField? {
    val w = (size.width + pad * 2f).roundToInt()
    val h = (size.height + pad * 2f).roundToInt()
    return remember(shape, w, h, band) {
        if (w <= 0 || h <= 0 || size.width <= 0f || size.height <= 0f) return@remember null
        val outline = shape.createOutline(size, layoutDirection, density)
        val path = (outline as? Outline.Generic)?.path ?: return@remember null
        buildPathField(path, w, h, pad, band)
    }
}

internal fun buildPathField(
    path: Path,
    layerWidth: Int,
    layerHeight: Int,
    pad: Float,
    band: Float,
): GlassPathField? {
    val fw = (layerWidth / FIELD_SCALE).coerceAtLeast(1)
    val fh = (layerHeight / FIELD_SCALE).coerceAtLeast(1)
    if (fw < 2 || fh < 2) return null

    // Rasterise the outline into the padded layer, at the panel's offset inside it.
    val mask = ImageBitmap(fw, fh)
    val canvas = Canvas(mask)
    val paint = Paint().apply { color = Color.White }
    canvas.save()
    canvas.translate(pad / FIELD_SCALE, pad / FIELD_SCALE)
    canvas.scale(1f / FIELD_SCALE, 1f / FIELD_SCALE)
    canvas.drawPath(path, paint)
    canvas.restore()

    val pixels = mask.toPixelMap()
    val inside = BooleanArray(fw * fh)
    for (y in 0 until fh) {
        for (x in 0 until fw) {
            inside[y * fw + x] = pixels[x, y].alpha > 0.5f
        }
    }

    val distance = signedDistance(inside, fw, fh)

    // The shader only reads within the band, so spending the eight bits there rather than on
    // the far interior is what keeps the quantisation under a pixel.
    val range = (band * 1.5f).coerceAtLeast(8f) / FIELD_SCALE
    val out = IntArray(fw * fh)
    for (i in out.indices) {
        val norm = ((distance[i] / range) * 0.5f + 0.5f).coerceIn(0f, 1f)
        val v = (norm * 255f).roundToInt().coerceIn(0, 255)
        out[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }

    return GlassPathField(
        bitmap = createFieldBitmap(fw, fh, out),
        range = range * FIELD_SCALE,
        scale = 1f / FIELD_SCALE,
        layerWidth = layerWidth,
        layerHeight = layerHeight,
    )
}

/**
 * Exact Euclidean distance, negative inside, by the 8-point sequential transform.
 *
 * Two passes over the grid propagating the nearest-boundary offset vector, run once for the
 * inside and once for the outside, then subtracted. Chamfer weights would be a line shorter and
 * are wrong by up to 8% on diagonals, which shows up as a normal that leans the wrong way on
 * exactly the sloped edges a hand-drawn shape is made of.
 */
private fun signedDistance(inside: BooleanArray, w: Int, h: Int): FloatArray {
    val outer = edt(inside, w, h, seedInside = false)
    val inner = edt(inside, w, h, seedInside = true)
    return FloatArray(w * h) { outer[it] - inner[it] }
}

private const val FAR = 1e9f

private fun edt(inside: BooleanArray, w: Int, h: Int, seedInside: Boolean): FloatArray {
    val dx = FloatArray(w * h)
    val dy = FloatArray(w * h)
    val d = FloatArray(w * h)

    for (i in 0 until w * h) {
        val seed = if (seedInside) !inside[i] else inside[i]
        if (seed) {
            dx[i] = 0f; dy[i] = 0f; d[i] = 0f
        } else {
            dx[i] = FAR; dy[i] = FAR; d[i] = FAR
        }
    }

    fun compare(i: Int, ox: Int, oy: Int, sx: Int, sy: Int) {
        val nx = i + ox + oy * w
        if (nx < 0 || nx >= w * h) return
        val cx = dx[nx] + sx
        val cy = dy[nx] + sy
        if (cx >= FAR || cy >= FAR) return
        val cd = cx * cx + cy * cy
        if (cd < d[i] * d[i]) {
            dx[i] = cx; dy[i] = cy; d[i] = sqrt(cd)
        }
    }

    for (y in 0 until h) {
        for (x in 0 until w) {
            val i = y * w + x
            if (x > 0) compare(i, -1, 0, 1, 0)
            if (y > 0) compare(i, 0, -1, 0, 1)
            if (x > 0 && y > 0) compare(i, -1, -1, 1, 1)
            if (x < w - 1 && y > 0) compare(i, 1, -1, 1, 1)
        }
        for (x in w - 1 downTo 0) {
            val i = y * w + x
            if (x < w - 1) compare(i, 1, 0, 1, 0)
        }
    }
    for (y in h - 1 downTo 0) {
        for (x in w - 1 downTo 0) {
            val i = y * w + x
            if (x < w - 1) compare(i, 1, 0, 1, 0)
            if (y < h - 1) compare(i, 0, 1, 0, 1)
            if (x < w - 1 && y < h - 1) compare(i, 1, 1, 1, 1)
            if (x > 0 && y < h - 1) compare(i, -1, 1, 1, 1)
        }
        for (x in 0 until w) {
            val i = y * w + x
            if (x > 0) compare(i, -1, 0, 1, 0)
        }
    }

    for (i in d.indices) if (d[i] >= FAR) d[i] = min(w, h).toFloat()
    return d
}

/** Compose has no common way to make an [ImageBitmap] from raw pixels. */
internal expect fun createFieldBitmap(width: Int, height: Int, pixels: IntArray): ImageBitmap
