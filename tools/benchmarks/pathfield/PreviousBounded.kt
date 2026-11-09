package com.wexpa.liquidglass
// `main` 2f7580f's bounded-block transform (#180, #188), renamed for a paired measurement against the
// narrow-band transform that replaced it. Benchmark-only; compiled by tools/benchmarks/pathfield.init.gradle.
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
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
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

private const val FIELD_SCALE_BOUNDED = 2

/**
 * Rasterizes at [pad] in layer coordinates, then encodes the signed distance at half resolution.
 * [buffers] must be exclusive to this call; the returned bitmap retains none of its arrays.
 * [bounded] false runs the transform over the whole layer; the bytes are identical either way,
 * and tests use it as the reference.
 */
internal fun buildPathFieldBounded(
    path: Path,
    layerWidth: Int,
    layerHeight: Int,
    pad: Float,
    band: Float,
    buffers: GlassPathFieldBuffers,
    bounded: Boolean = true,
): GlassPathField? {
    val fw = (layerWidth / FIELD_SCALE_BOUNDED).coerceAtLeast(1)
    val fh = (layerHeight / FIELD_SCALE_BOUNDED).coerceAtLeast(1)
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

    val full = signedDistanceBounded(inside, layerWidth, layerHeight, band, buffers, bounded)
    // A texel covers FIELD_SCALE_BOUNDED x FIELD_SCALE_BOUNDED layer pixels and is sampled at its centre, which
    // is the mean of the pixel centres it covers; distance is smooth, so the mean is the value.
    val distance = FloatArray(fw * fh)
    for (y in 0 until fh) {
        for (x in 0 until fw) {
            var acc = 0f; var n = 0
            for (dy in 0 until FIELD_SCALE_BOUNDED) for (dx in 0 until FIELD_SCALE_BOUNDED) {
                val xx = x * FIELD_SCALE_BOUNDED + dx; val yy = y * FIELD_SCALE_BOUNDED + dy
                if (xx < layerWidth && yy < layerHeight) { acc += full[yy * layerWidth + xx]; n++ }
            }
            distance[y * fw + x] = if (n > 0) acc / n / FIELD_SCALE_BOUNDED else 0f
        }
    }
    // `full` is in layer pixels; the field's encoding below is in texels (range / FIELD_SCALE_BOUNDED), so
    // the distance is divided by FIELD_SCALE_BOUNDED above to keep the stored units unchanged.

    // The shader only reads within the band, so spending the eight bits there rather than on
    // the far interior is what keeps the quantisation under a pixel.
    val range = encodedReachBounded(band) / FIELD_SCALE_BOUNDED
    val out = IntArray(fw * fh)
    for (i in out.indices) {
        val norm = ((distance[i] / range) * 0.5f + 0.5f).coerceIn(0f, 1f)
        val v = (norm * 255f).roundToInt().coerceIn(0, 255)
        out[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }

    return GlassPathField(
        bitmap = createFieldBitmap(fw, fh, out),
        range = range * FIELD_SCALE_BOUNDED,
        scale = 1f / FIELD_SCALE_BOUNDED,
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
 *
 * With [bounded], blocks of the layer too far from the outline to matter are not visited; see
 * [activeBlocksBounded]. The field's bytes do not change.
 */
private fun signedDistanceBounded(
    inside: BooleanArray,
    w: Int,
    h: Int,
    band: Float,
    buffers: GlassPathFieldBuffers,
    bounded: Boolean,
): FloatArray {
    val count = w * h
    val outer = buffers.outer
    fun transform(blocks: ActiveBlocksBounded?): Boolean {
        if (!edtBounded(inside, w, h, seedInside = false, buffers, blocks)) return false
        buffers.distance.copyInto(outer, endIndex = count)
        if (!edtBounded(inside, w, h, seedInside = true, buffers, blocks)) return false
        val inner = buffers.distance
        for (i in 0 until count) outer[i] -= inner[i]
        return true
    }
    val blocks = if (bounded) activeBlocksBounded(inside, w, h, band) else null
    // A bounded pass that leaves a visited pixel unreached falls back to the whole layer.
    if (blocks == null || !transform(blocks)) transform(null)
    return outer
}

private const val FAR_BOUNDED = 1e9f

/**
 * Layer px from the outline at which the stored byte saturates, in the layer's own pixels: the encoder's
 * range, centred on zero. Inherited from the encoder (one and a half bands, at least 8 px). The encoder and
 * [activeBlocksBounded] both read it here, so a change to the encoding range moves what the bounded transform skips with it.
 */
private fun encodedReachBounded(band: Float): Float = (band * 1.5f).coerceAtLeast(8f)

/**
 * Layer blocks are [BLOCK_BOUNDED] pixels square (authored). Even, so a field texel (two pixels square, aligned to
 * the layer's origin) never straddles two blocks.
 */
private const val BLOCK_BOUNDED = 16

/** Slack beyond the encoded reach before a pixel counts as saturated, in px; covers texel averaging (authored). */
private const val SATURATION_MARGIN_BOUNDED = 8f

/** Px added for the outline pixel pair sitting at a block's far edge, and the diagonal pair (authored). */
private const val OUTLINE_SLACK_BOUNDED = 2f

/** Which blocks of a layer the transform has to visit. [active] is indexed `by * columns + bx`. */
internal class ActiveBlocksBounded(val columns: Int, val rows: Int, val active: BooleanArray)

/**
 * The blocks the transform visits, or null to visit them all.
 *
 * The encoded field saturates once a pixel is one and a half bands from the outline, so every
 * pixel much farther than that stores the same byte whatever distance it was given. A block counts
 * as far when it is wider of every block the outline passes through than that range plus
 * [SATURATION_MARGIN_BOUNDED]. A far block is one colour of mask, so its pixels are either seeds, which the
 * transform never changes, or pixels whose true distance is past the range; the transform's value
 * for those is never below the true distance, so it saturates too. Skipping them leaves every pixel
 * nearer than the range with exactly the state the whole-layer transform gives it: a candidate
 * offset only grows along its chain back to the seed that supplied it, so nothing a skipped pixel
 * could have offered would have been accepted by a nearer one.
 */
internal fun activeBlocksBounded(inside: BooleanArray, w: Int, h: Int, band: Float): ActiveBlocksBounded? {
    val columns = (w + BLOCK_BOUNDED - 1) / BLOCK_BOUNDED
    val rows = (h + BLOCK_BOUNDED - 1) / BLOCK_BOUNDED
    val reach = IntArray(columns * rows) { Int.MAX_VALUE }
    var anyOutline = false
    // A block holds the outline when two neighbouring pixels, in it or across its edge, differ.
    for (y in 0 until h) {
        val row = y * w
        val by = y / BLOCK_BOUNDED
        for (x in 0 until w) {
            val v = inside[row + x]
            val bx = x / BLOCK_BOUNDED
            if (x + 1 < w && inside[row + x + 1] != v) {
                reach[by * columns + bx] = 0; reach[by * columns + (x + 1) / BLOCK_BOUNDED] = 0; anyOutline = true
            }
            if (y + 1 < h && inside[row + w + x] != v) {
                reach[by * columns + bx] = 0; reach[((y + 1) / BLOCK_BOUNDED) * columns + bx] = 0; anyOutline = true
            }
        }
    }
    // Without an outline the whole-layer transform has no seed to measure from; keep its behaviour.
    if (!anyOutline) return null
    // Chebyshev distance to the nearest outline block, in blocks: a forward and a backward sweep.
    for (by in 0 until rows) for (bx in 0 until columns) {
        var best = reach[by * columns + bx]
        if (bx > 0) best = minOf(best, reach[by * columns + bx - 1].plusOneBounded())
        if (by > 0) {
            best = minOf(best, reach[(by - 1) * columns + bx].plusOneBounded())
            if (bx > 0) best = minOf(best, reach[(by - 1) * columns + bx - 1].plusOneBounded())
            if (bx < columns - 1) best = minOf(best, reach[(by - 1) * columns + bx + 1].plusOneBounded())
        }
        reach[by * columns + bx] = best
    }
    for (by in rows - 1 downTo 0) for (bx in columns - 1 downTo 0) {
        var best = reach[by * columns + bx]
        if (bx < columns - 1) best = minOf(best, reach[by * columns + bx + 1].plusOneBounded())
        if (by < rows - 1) {
            best = minOf(best, reach[(by + 1) * columns + bx].plusOneBounded())
            if (bx < columns - 1) best = minOf(best, reach[(by + 1) * columns + bx + 1].plusOneBounded())
            if (bx > 0) best = minOf(best, reach[(by + 1) * columns + bx - 1].plusOneBounded())
        }
        reach[by * columns + bx] = best
    }
    // A pixel d blocks from an outline block is at least (d - 1) * BLOCK_BOUNDED - 2 pixels from the outline
    // ([OUTLINE_SLACK_BOUNDED] covers the outline pixel pair sitting at a block's far edge, and the diagonal pair).
    val farPixels = encodedReachBounded(band) + SATURATION_MARGIN_BOUNDED
    val farBlocks = ceil((farPixels + OUTLINE_SLACK_BOUNDED) / BLOCK_BOUNDED).toInt() + 1
    val active = BooleanArray(reach.size)
    var skipped = false
    for (i in reach.indices) { active[i] = reach[i] < farBlocks; if (!active[i]) skipped = true }
    return if (skipped) ActiveBlocksBounded(columns, rows, active) else null
}

private fun Int.plusOneBounded(): Int = if (this == Int.MAX_VALUE) this else this + 1

/**
 * Fills [runs] with the pixel ranges `[start, end)` of row [y] that lie in active blocks, in
 * ascending order, and returns how many. Without [blocks] the whole row is one range.
 */
private fun rowRunsBounded(blocks: ActiveBlocksBounded?, y: Int, w: Int, runs: IntArray): Int {
    if (blocks == null) { runs[0] = 0; runs[1] = w; return 1 }
    val base = (y / BLOCK_BOUNDED) * blocks.columns
    var n = 0
    var bx = 0
    while (bx < blocks.columns) {
        if (!blocks.active[base + bx]) { bx++; continue }
        val first = bx
        while (bx < blocks.columns && blocks.active[base + bx]) bx++
        runs[2 * n] = first * BLOCK_BOUNDED
        runs[2 * n + 1] = minOf(w, bx * BLOCK_BOUNDED)
        n++
    }
    return n
}

/**
 * Overwrites the active scratch rectangle with distances to the selected mask boundary. Returns
 * false when a pixel in an active block was never reached, which the caller answers by
 * transforming the whole layer. Pixels in skipped blocks hold a distance past any range.
 */
private fun edtBounded(
    inside: BooleanArray,
    w: Int,
    h: Int,
    seedInside: Boolean,
    buffers: GlassPathFieldBuffers,
    blocks: ActiveBlocksBounded?,
): Boolean {
    val dx = buffers.dx
    val dy = buffers.dy
    val d = buffers.distance

    for (i in 0 until w * h) {
        val seed = if (seedInside) !inside[i] else inside[i]
        if (seed) {
            dx[i] = 0f; dy[i] = 0f; d[i] = 0f
        } else {
            dx[i] = FAR_BOUNDED; dy[i] = FAR_BOUNDED; d[i] = FAR_BOUNDED
        }
    }

    val runs = IntArray(2 * (blocks?.columns ?: 0) + 2)
    // Every neighbour below is inside the grid by its row/column guard, so [relax] needs no bounds
    // check. A seed (distance 0) is never improved, so the sweeps pass over it; the visiting order
    // and the comparison are otherwise unchanged, so the result is bit-identical.
    for (y in 0 until h) {
        val row = y * w
        val n = rowRunsBounded(blocks, y, w, runs)
        for (r in 0 until n) for (x in runs[2 * r] until runs[2 * r + 1]) {
            val i = row + x
            if (d[i] == 0f) continue
            if (x > 0) relaxBounded(dx, dy, d, i, i - 1, 1f, 0f)
            if (y > 0) {
                relaxBounded(dx, dy, d, i, i - w, 0f, 1f)
                if (x > 0) relaxBounded(dx, dy, d, i, i - w - 1, 1f, 1f)
                if (x < w - 1) relaxBounded(dx, dy, d, i, i - w + 1, 1f, 1f)
            }
        }
        for (r in n - 1 downTo 0) for (x in minOf(runs[2 * r + 1] - 1, w - 2) downTo runs[2 * r]) {
            val i = row + x
            if (d[i] == 0f) continue
            relaxBounded(dx, dy, d, i, i + 1, 1f, 0f)
        }
    }
    for (y in h - 1 downTo 0) {
        val row = y * w
        val n = rowRunsBounded(blocks, y, w, runs)
        for (r in n - 1 downTo 0) for (x in runs[2 * r + 1] - 1 downTo runs[2 * r]) {
            val i = row + x
            if (d[i] == 0f) continue
            if (x < w - 1) relaxBounded(dx, dy, d, i, i + 1, 1f, 0f)
            if (y < h - 1) {
                relaxBounded(dx, dy, d, i, i + w, 0f, 1f)
                if (x < w - 1) relaxBounded(dx, dy, d, i, i + w + 1, 1f, 1f)
                if (x > 0) relaxBounded(dx, dy, d, i, i + w - 1, 1f, 1f)
            }
        }
        for (r in 0 until n) for (x in maxOf(runs[2 * r], 1) until runs[2 * r + 1]) {
            val i = row + x
            if (d[i] == 0f) continue
            relaxBounded(dx, dy, d, i, i - 1, 1f, 0f)
        }
    }

    if (blocks == null) {
        for (i in 0 until w * h) if (d[i] >= FAR_BOUNDED) d[i] = min(w, h).toFloat()
        return true
    }
    for (y in 0 until h) {
        val n = rowRunsBounded(blocks, y, w, runs)
        for (r in 0 until n) for (x in runs[2 * r] until runs[2 * r + 1]) if (d[y * w + x] >= FAR_BOUNDED) return false
    }
    // What is left unreached lies in skipped blocks, farther from the outline than any range.
    val beyond = (w + h).toFloat()
    for (i in 0 until w * h) if (d[i] >= FAR_BOUNDED) d[i] = beyond
    return true
}

/** Takes the neighbour [n]'s nearest-seed offset, one step further, when it improves pixel [i]. */
@Suppress("NOTHING_TO_INLINE")
private inline fun relaxBounded(dx: FloatArray, dy: FloatArray, d: FloatArray, i: Int, n: Int, sx: Float, sy: Float) {
    val cx = dx[n] + sx
    val cy = dy[n] + sy
    if (cx >= FAR_BOUNDED || cy >= FAR_BOUNDED) return
    val cd = cx * cx + cy * cy
    val di = d[i]
    if (cd < di * di) {
        dx[i] = cx; dy[i] = cy; d[i] = sqrt(cd)
    }
}

/** Compose has no common way to make an [ImageBitmap] from raw pixels. */
