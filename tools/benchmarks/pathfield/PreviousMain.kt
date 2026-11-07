package com.wexpa.liquidglass
// `main` 13ec13b's full-layer transform (before bounded blocks), renamed for a paired measurement.
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toPixelMap
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val FIELD_SCALE_MAIN = 2

/**
 * Rasterizes at [pad] in layer coordinates, then encodes the signed distance at half resolution.
 * [buffers] must be exclusive to this call; the returned bitmap retains none of its arrays.
 */
internal fun buildPathFieldMain(
    path: Path,
    layerWidth: Int,
    layerHeight: Int,
    pad: Float,
    band: Float,
    buffers: GlassPathFieldBuffers,
): GlassPathField? {
    val fw = (layerWidth / FIELD_SCALE_MAIN).coerceAtLeast(1)
    val fh = (layerHeight / FIELD_SCALE_MAIN).coerceAtLeast(1)
    if (fw < 2 || fh < 2) return null

    // Rasterise the outline at the layer's own resolution, at the panel's offset inside it, and
    // take the distance down to the field's resolution afterwards. Rasterising at field
    // resolution put the outline on a half texel whenever the pad was odd at renderScale=1,
    // and the boolean mask then rounded it: the sampled
    // silhouette sat a full pixel off the closed form for pads 23 and 25 and exactly on it for 22
    // and 24. A full-resolution mask keeps the outline where the layer has it.
    val mask = ImageBitmap(layerWidth, layerHeight)
    val canvas = Canvas(mask)
    val paint = Paint().apply { color = Color.White }
    canvas.save()
    canvas.translate(pad, pad)
    canvas.drawPath(path, paint)
    canvas.restore()

    val pixels = mask.toPixelMap()
    buffers.ensureCapacity(layerWidth * layerHeight)
    val inside = buffers.inside
    // Read the packed ARGB words directly: alpha above 0.5 is an alpha byte of 128 or more, the
    // same test `pixels[x, y].alpha > 0.5f` made through a Color per pixel.
    val argb = pixels.buffer
    for (y in 0 until layerHeight) {
        val row = pixels.bufferOffset + y * pixels.stride
        for (x in 0 until layerWidth) {
            inside[y * layerWidth + x] = (argb[row + x] ushr 24) >= 128
        }
    }

    val full = signedDistanceMain(inside, layerWidth, layerHeight, buffers)
    // A texel covers FIELD_SCALE_MAIN x FIELD_SCALE_MAIN layer pixels and is sampled at its centre, which
    // is the mean of the pixel centres it covers; distance is smooth, so the mean is the value.
    val distance = FloatArray(fw * fh)
    for (y in 0 until fh) {
        for (x in 0 until fw) {
            var acc = 0f; var n = 0
            for (dy in 0 until FIELD_SCALE_MAIN) for (dx in 0 until FIELD_SCALE_MAIN) {
                val xx = x * FIELD_SCALE_MAIN + dx; val yy = y * FIELD_SCALE_MAIN + dy
                if (xx < layerWidth && yy < layerHeight) { acc += full[yy * layerWidth + xx]; n++ }
            }
            distance[y * fw + x] = if (n > 0) acc / n / FIELD_SCALE_MAIN else 0f
        }
    }
    // `full` is in layer pixels; the field's encoding below is in texels (range / FIELD_SCALE_MAIN), so
    // the distance is divided by FIELD_SCALE_MAIN above to keep the stored units unchanged.

    // The shader only reads within the band, so spending the eight bits there rather than on
    // the far interior is what keeps the quantisation under a pixel.
    val range = (band * 1.5f).coerceAtLeast(8f) / FIELD_SCALE_MAIN
    val out = IntArray(fw * fh)
    for (i in out.indices) {
        val norm = ((distance[i] / range) * 0.5f + 0.5f).coerceIn(0f, 1f)
        val v = (norm * 255f).roundToInt().coerceIn(0, 255)
        out[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }

    return GlassPathField(
        bitmap = createFieldBitmap(fw, fh, out),
        range = range * FIELD_SCALE_MAIN,
        scale = 1f / FIELD_SCALE_MAIN,
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
private fun signedDistanceMain(inside: BooleanArray, w: Int, h: Int, buffers: GlassPathFieldBuffers): FloatArray {
    val count = w * h
    val outer = buffers.outer
    edtMain(inside, w, h, seedInside = false, buffers).copyInto(outer, endIndex = count)
    val inner = edtMain(inside, w, h, seedInside = true, buffers)
    for (i in 0 until count) outer[i] -= inner[i]
    return outer
}

private const val FAR_MAIN = 1e9f

/** Overwrites the active scratch rectangle with distances to the selected mask boundary. */
private fun edtMain(inside: BooleanArray, w: Int, h: Int, seedInside: Boolean, buffers: GlassPathFieldBuffers): FloatArray {
    val dx = buffers.dx
    val dy = buffers.dy
    val d = buffers.distance

    for (i in 0 until w * h) {
        val seed = if (seedInside) !inside[i] else inside[i]
        if (seed) {
            dx[i] = 0f; dy[i] = 0f; d[i] = 0f
        } else {
            dx[i] = FAR_MAIN; dy[i] = FAR_MAIN; d[i] = FAR_MAIN
        }
    }

    // Every neighbour below is inside the grid by its row/column guard, so [relax] needs no bounds
    // check; the visiting order and the comparison are unchanged, so the result is bit-identical.
    for (y in 0 until h) {
        val row = y * w
        for (x in 0 until w) {
            val i = row + x
            if (x > 0) relaxMain(dx, dy, d, i, i - 1, 1f, 0f)
            if (y > 0) {
                relaxMain(dx, dy, d, i, i - w, 0f, 1f)
                if (x > 0) relaxMain(dx, dy, d, i, i - w - 1, 1f, 1f)
                if (x < w - 1) relaxMain(dx, dy, d, i, i - w + 1, 1f, 1f)
            }
        }
        for (x in w - 2 downTo 0) {
            val i = row + x
            relaxMain(dx, dy, d, i, i + 1, 1f, 0f)
        }
    }
    for (y in h - 1 downTo 0) {
        val row = y * w
        for (x in w - 1 downTo 0) {
            val i = row + x
            if (x < w - 1) relaxMain(dx, dy, d, i, i + 1, 1f, 0f)
            if (y < h - 1) {
                relaxMain(dx, dy, d, i, i + w, 0f, 1f)
                if (x < w - 1) relaxMain(dx, dy, d, i, i + w + 1, 1f, 1f)
                if (x > 0) relaxMain(dx, dy, d, i, i + w - 1, 1f, 1f)
            }
        }
        for (x in 1 until w) {
            val i = row + x
            relaxMain(dx, dy, d, i, i - 1, 1f, 0f)
        }
    }

    for (i in 0 until w * h) if (d[i] >= FAR_MAIN) d[i] = min(w, h).toFloat()
    return d
}

/** Takes the neighbour [n]'s nearest-seed offset, one step further, when it improves pixel [i]. */
@Suppress("NOTHING_TO_INLINE")
private inline fun relaxMain(dx: FloatArray, dy: FloatArray, d: FloatArray, i: Int, n: Int, sx: Float, sy: Float) {
    val cx = dx[n] + sx
    val cy = dy[n] + sy
    if (cx >= FAR_MAIN || cy >= FAR_MAIN) return
    val cd = cx * cx + cy * cy
    val di = d[i]
    if (cd < di * di) {
        dx[i] = cx; dy[i] = cy; d[i] = sqrt(cd)
    }
}
