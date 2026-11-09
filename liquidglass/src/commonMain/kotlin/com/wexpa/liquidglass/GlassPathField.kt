package com.wexpa.liquidglass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
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
 * differencing over a wide epsilon where a pixel of noise is nothing. The mask and distance
 * transform are computed at full resolution to preserve pad alignment, then the field is stored
 * at half resolution; bilinear sampling covers the difference.
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
 *
 * [size] is rounded to layout pixels. [pad] must be the value returned by [recordPad], already
 * quantized to the recording grid; keep it unchanged to match the shader's sampling origin.
 *
 * The first field, any field replacing an empty one, and any field whose outline, pad, band,
 * density or direction changed, is built during composition, so no frame ever shows a field for
 * another shape or origin, or none. A change of
 * size alone (the resizing, animating case) is built on [glassPathFieldDispatcher] instead and the
 * previous field, already drawn at the same origin, stays in use until the new one is published;
 * builds requested while one is running are conflated to the latest size.
 */
@Composable
internal fun rememberGlassPathField(
    shape: Shape,
    size: Size,
    pad: Float,
    band: Float,
    density: Density,
    layoutDirection: LayoutDirection,
    enabled: Boolean,
): GlassPathField? {
    // Do not even ask the shape for an outline on a backend that cannot consume its field.
    if (!enabled) return null
    // Match layout's whole-pixel size in both the cache key and outline construction.
    val fieldSize = Size(size.width.roundToInt().toFloat(), size.height.roundToInt().toFloat())
    // recordPad already snaps to the render-scale grid. A second rounding would move the
    // rasterized outline away from the shader's uPad origin at fractional render scales.
    val fieldPad = pad
    // The outline depends on density and direction as well as size: an asymmetric path mirrors
    // under RTL, so both belong in the key. The outline itself is made here, on the composition
    // thread, so a shape's own code never runs on a background thread.
    // Everything except the size: a change here cannot reuse the previous field, so it builds now.
    val generation = remember(shape, fieldPad, band, density, layoutDirection) { Any() }
    val request = remember(generation, fieldSize) {
        PathFieldRequest(generation, fieldSize, fieldPad, band, shape.pathFor(fieldSize, layoutDirection, density))
    }
    val published = remember(generation) { mutableStateOf(PublishedPathField(request, request.build())) }
    val latest = rememberUpdatedState(request)
    LaunchedEffect(generation) {
        snapshotFlow { latest.value }.conflate().collect { next ->
            // Composition builds synchronously both a newer generation (this effect is about to be
            // cancelled) and any request while there is no field to keep; neither is built twice.
            val current = published.value
            if (next.generation !== generation || current.field == null || next === current.request) return@collect
            val field = withContext(glassPathFieldDispatcher) { next.build() }
            published.value = PublishedPathField(next, field)
        }
    }
    val shown = published.value
    if (shown.field == null && shown.request !== request) {
        // Nothing drawable to keep (an empty first size, before layout): build before the frame.
        val now = remember(request) { PublishedPathField(request, request.build()) }
        SideEffect { published.value = now }
        return now.field
    }
    return shown.field
}

/**
 * Where size-only rebuilds run. [Dispatchers.Default] is a worker pool on Android and the JVM;
 * on Wasm it is the browser's one thread, where the build still leaves composition and the frame.
 * Settable only so tests can hold a build and look at what composition returns meanwhile.
 */
internal var glassPathFieldDispatcher: CoroutineDispatcher = Dispatchers.Default

/** One field's inputs, with the outline already made. [generation] changes with everything but size. */
private class PathFieldRequest(
    val generation: Any,
    val size: Size,
    val pad: Float,
    val band: Float,
    val path: Path?,
) {
    fun build(): GlassPathField? {
        val w = (size.width + pad * 2f).roundToInt()
        val h = (size.height + pad * 2f).roundToInt()
        if (w <= 0 || h <= 0 || size.width <= 0f || size.height <= 0f || path == null) return null
        return buildPathField(path, w, h, pad, band)
    }
}

/** The field currently drawn, and the request it was built for. */
private class PublishedPathField(val request: PathFieldRequest, val field: GlassPathField?)

/**
 * Null for an empty size or an outline the shader evaluates analytically. A copy, because a shape may
 * reuse one mutable [Path] for every size, and a background build must not read it while the next
 * composition rewrites it.
 */
private fun Shape.pathFor(size: Size, layoutDirection: LayoutDirection, density: Density): Path? {
    if (size.width <= 0f || size.height <= 0f) return null
    val path = (createOutline(size, layoutDirection, density) as? Outline.Generic)?.path ?: return null
    return Path().apply { fillType = path.fillType; addPath(path) }
}

/** Builds an independently owned field while borrowing bounded scratch storage if available. */
internal fun buildPathField(
    path: Path,
    layerWidth: Int,
    layerHeight: Int,
    pad: Float,
    band: Float,
): GlassPathField? = GlassPathFieldBufferPool.use(layerWidth.toLong() * layerHeight) { buffers ->
    buildPathField(path, layerWidth, layerHeight, pad, band, buffers)
}

/** Scratch storage owns no published bitmap. Every active element is overwritten before use. */
internal class GlassPathFieldBuffers {
    var inside = BooleanArray(0); private set
    var dx = FloatArray(0); private set
    var dy = FloatArray(0); private set
    var distance = FloatArray(0); private set
    var outer = FloatArray(0); private set
    internal var state = IntArray(0); private set
    internal var alphaScratch = ByteArray(0); private set
    internal var greyOut = ByteArray(0); private set

    /** Grows all scratch planes together; smaller builds retain their existing arrays. */
    fun ensureCapacity(count: Int) {
        if (inside.size >= count) return
        inside = BooleanArray(count)
        dx = FloatArray(count)
        dy = FloatArray(count)
        distance = FloatArray(count)
        outer = FloatArray(count)
    }

    /** Allocates only the packed single-pass state plane and compact byte buffers. */
    internal fun ensurePackedCapacity(count: Int, texelCount: Int) {
        if (state.size < count) state = IntArray(count)
        if (greyOut.size < texelCount) greyOut = ByteArray(texelCount)
    }

    internal fun ensureAlphaScratch(count: Int): ByteArray {
        if (alphaScratch.size < count) alphaScratch = ByteArray(count)
        return alphaScratch
    }
}

/**
 * One bounded reusable workspace (~1.06 MiB for packed narrow-band builds; up to ~4.25 MiB if the
 * uncapped fallback allocates float planes), not one per panel. Concurrent callers and builds over
 * [MAX_RETAINED_PIXELS] use temporary storage without blocking or growing the pool.
 */
internal object GlassPathFieldBufferPool {
    private const val MAX_RETAINED_PIXELS = 256L * 1024L
    private val mutex = Mutex()
    private val buffers = GlassPathFieldBuffers()

    /** Loans exclusive scratch arrays, falling back to temporary storage without blocking. */
    fun <T> use(pixels: Long, block: (GlassPathFieldBuffers) -> T): T {
        if (pixels !in 1..MAX_RETAINED_PIXELS || !mutex.tryLock()) {
            return block(GlassPathFieldBuffers())
        }
        return try { block(buffers) } finally { mutex.unlock() }
    }
}

/**
 * Packed single-pass 8SSEDT state: bit 31 = inside mask, bit 30 = unreached within `limit`,
 * bits 15..29 = `dy`, bits 0..14 = `dx`. Inside and outside propagation are disjoint across pixels,
 * and capping at `limit = ceil(range * FIELD_SCALE) + 3` (`<= 1023`) preserves every unclamped texel.
 */
private const val SIGN_BIT = -0x80000000
private const val UNREACHED_BIT = 0x40000000
private const val IN_UNREACHED = SIGN_BIT or UNREACHED_BIT
private const val OUT_UNREACHED = UNREACHED_BIT
private const val COORD_MASK = 0x7FFF
private const val DY_SHIFT = 15
/**
 * Largest encoded half-span (layer px) the narrow-band transform handles (authored): its cap `ceil(span) + 3`
 * then stays under 1024, inside the 15-bit offset fields with room to spare. Wider bands take the whole-layer transform.
 */
private const val MAX_NARROW_HALF_SPAN = 1020f

/**
 * Layer px from the outline at which the stored byte saturates: the encoder's range, centred on zero. Inherited from
 * the encoder (one and a half bands, at least 8 px). Every reader of the encoding range goes through here, so the
 * narrow band's cap moves with it.
 */
private fun encodedReach(band: Float): Float = (band * 1.5f).coerceAtLeast(8f)

/** Exact IEEE-754 `Float` square roots for integer squared distances up to `128^2` (covers `band <= 83`). */
private val SQRT_TABLE = FloatArray(128 * 128 + 1) { sqrt(it.toFloat()) }

/** Whether `k.toFloat() < sqrt(k.toFloat())^2` in IEEE-754 `Float`, for exact-tie reproduction. */
private val TIE_WINS = BooleanArray(SQRT_TABLE.size) { k ->
    val d = SQRT_TABLE[k]
    k.toFloat() < d * d
}

@Suppress("NOTHING_TO_INLINE")
private inline fun sqrtFor(cdInt: Int): Float =
    if (cdInt < SQRT_TABLE.size) SQRT_TABLE[cdInt] else sqrt(cdInt.toFloat())

internal fun edtTieWins(cdInt: Int): Boolean =
    if (cdInt < TIE_WINS.size) {
        TIE_WINS[cdInt]
    } else {
        val d = sqrt(cdInt.toFloat())
        cdInt.toFloat() < d * d
    }

/**
 * Rasterizes at [pad] in layer coordinates, then encodes the signed distance at half resolution.
 * [buffers] must be exclusive to this call; the returned bitmap retains none of its arrays.
 */
internal fun buildPathField(
    path: Path,
    layerWidth: Int,
    layerHeight: Int,
    pad: Float,
    band: Float,
    buffers: GlassPathFieldBuffers,
): GlassPathField? {
    val fw = (layerWidth / FIELD_SCALE).coerceAtLeast(1)
    val fh = (layerHeight / FIELD_SCALE).coerceAtLeast(1)
    val totalPixels = layerWidth.toLong() * layerHeight
    if (fw < 2 || fh < 2 || totalPixels > Int.MAX_VALUE) return null

    // Rasterise the outline at the layer's own resolution, at the panel's offset inside it, and
    // take the distance down to the field's resolution afterwards. Rasterising at field
    // resolution put the outline on a half texel whenever the pad was odd at renderScale=1,
    // and the boolean mask then rounded it: the sampled
    // silhouette sat a full pixel off the closed form for pads 23 and 25 and exactly on it for 22
    // and 24. A full-resolution mask keeps the outline where the layer has it.
    val count = totalPixels.toInt()
    val texelCount = fw * fh
    buffers.ensurePackedCapacity(count, texelCount)
    val state = buffers.state
    val grey = buffers.greyOut
    val alpha = rasterizeMaskAlpha(path, layerWidth, layerHeight, pad, buffers) ?: return null

    var insideCount = 0
    var inMinX = layerWidth
    var inMaxX = -1
    var inMinY = layerHeight
    var inMaxY = -1
    var outMinX = layerWidth
    var outMaxX = -1
    var outMinY = layerHeight
    var outMaxY = -1

    // Alpha >= 128 is negative in signed two's-complement Byte (-128..-1), identical to `(argb ushr 24) >= 128`.
    for (y in 0 until layerHeight) {
        val row = y * layerWidth
        var rowHasIn = false
        var rowHasOut = false
        for (x in 0 until layerWidth) {
            if (alpha[row + x] < 0) {
                state[row + x] = IN_UNREACHED
                insideCount++
                rowHasIn = true
                if (x < inMinX) inMinX = x
                if (x > inMaxX) inMaxX = x
            } else {
                state[row + x] = OUT_UNREACHED
                rowHasOut = true
                if (x < outMinX) outMinX = x
                if (x > outMaxX) outMaxX = x
            }
        }
        if (rowHasIn) {
            if (y < inMinY) inMinY = y
            if (y > inMaxY) inMaxY = y
        }
        if (rowHasOut) {
            if (y < outMinY) outMinY = y
            if (y > outMaxY) outMaxY = y
        }
    }

    // The shader only reads within the band, so spending the eight bits there rather than on
    // the far interior is what keeps the quantisation under a pixel.
    val safeBand = if (band.isNaN()) 0f else band
    val range = encodedReach(safeBand) / FIELD_SCALE

    if (insideCount == 0 || insideCount == count) {
        val f = if (insideCount == 0) {
            min(layerWidth, layerHeight).toFloat()
        } else {
            -min(layerWidth, layerHeight).toFloat()
        }
        val dist = (0f + f + f + f + f) / 4f / FIELD_SCALE
        val norm = ((dist / range) * 0.5f + 0.5f).coerceIn(0f, 1f)
        val v = (norm * 255f).roundToInt().coerceIn(0, 255).toByte()
        grey.fill(v, 0, texelCount)
        return GlassPathField(
            bitmap = createGreyscaleFieldBitmap(fw, fh, grey),
            range = range * FIELD_SCALE,
            scale = 1f / FIELD_SCALE,
            layerWidth = layerWidth,
            layerHeight = layerHeight,
        )
    }

    val halfSpan = range * FIELD_SCALE
    if (!halfSpan.isFinite() || halfSpan > MAX_NARROW_HALF_SPAN) {
        return buildPathFieldUncapped(state, layerWidth, layerHeight, fw, fh, range, buffers)
    }

    // +3 px (authored) past the encoded half-span: covers the 2x2 texel average, so every block that
    // reaches the cap clamps to the same byte the uncapped transform gives it.
    val limit = ceil(halfSpan).toInt() + 3
    val maxCd = limit * limit
    val xStart = maxOf(0, inMinX - limit, outMinX - limit)
    val xEnd = minOf(layerWidth - 1, inMaxX + limit, outMaxX + limit)
    val yStart = maxOf(0, inMinY - limit, outMinY - limit)
    val yEnd = minOf(layerHeight - 1, inMaxY + limit, outMaxY + limit)

    if (xStart <= xEnd && yStart <= yEnd) {
        sweepTopDown(state, layerWidth, xStart, xEnd, yStart, yEnd, maxCd)
        sweepBottomUp(state, layerWidth, xStart, xEnd, yStart, yEnd, maxCd)
    }

    val farDist = limit.toFloat()
    for (ty in 0 until fh) {
        val row0 = (ty * FIELD_SCALE) * layerWidth
        val row1 = row0 + layerWidth
        val outRow = ty * fw
        for (tx in 0 until fw) {
            val col = tx * FIELD_SCALE
            val s00 = state[row0 + col]
            val s01 = state[row0 + col + 1]
            val s10 = state[row1 + col]
            val s11 = state[row1 + col + 1]
            if ((s00 and s01 and s10 and s11 and UNREACHED_BIT) != 0) {
                grey[outRow + tx] = if (s00 < 0) 0 else -1
            } else {
                val f00 = signedPixelDistance(s00, farDist)
                val f01 = signedPixelDistance(s01, farDist)
                val f10 = signedPixelDistance(s10, farDist)
                val f11 = signedPixelDistance(s11, farDist)
                val dist = (0f + f00 + f01 + f10 + f11) / 4f / FIELD_SCALE
                val norm = ((dist / range) * 0.5f + 0.5f).coerceIn(0f, 1f)
                grey[outRow + tx] = (norm * 255f).roundToInt().coerceIn(0, 255).toByte()
            }
        }
    }

    return GlassPathField(
        bitmap = createGreyscaleFieldBitmap(fw, fh, grey),
        range = range * FIELD_SCALE,
        scale = 1f / FIELD_SCALE,
        layerWidth = layerWidth,
        layerHeight = layerHeight,
    )
}

@Suppress("NOTHING_TO_INLINE")
private inline fun signedPixelDistance(sp: Int, farDist: Float): Float {
    val unsigned = if ((sp and UNREACHED_BIT) != 0) {
        farDist
    } else {
        val dx = sp and COORD_MASK
        val dy = (sp ushr DY_SHIFT) and COORD_MASK
        sqrtFor(dx * dx + dy * dy)
    }
    return if (sp < 0) -unsigned else unsigned
}

/** Top-down pass of the single-pass disjoint 8SSEDT over `[xStart..xEnd] x [yStart..yEnd]`. */
private fun sweepTopDown(
    state: IntArray,
    w: Int,
    xStart: Int,
    xEnd: Int,
    yStart: Int,
    yEnd: Int,
    maxCd: Int,
) {
    for (y in yStart..yEnd) {
        val row = y * w
        if (y == yStart) {
            sweepRowHorizontal(state, row + xStart, row + xEnd, maxCd)
        } else if (xStart == xEnd) {
            val i = row + xStart
            val s = state[i]
            val unreached = (s and SIGN_BIT) or UNREACHED_BIT
            val up = state[i - w]
            if (up != unreached) state[i] = relaxPacked(s, up, 0, 1, maxCd)
        } else {
            val i0 = row + xStart
            val lastI = row + xEnd
            var s0 = state[i0]
            val u0 = (s0 and SIGN_BIT) or UNREACHED_BIT
            var upLeft = state[i0 - w]
            var up = state[i0 - w + 1]
            if (((upLeft xor u0) or (up xor u0)) != 0) {
                if (upLeft != u0) s0 = relaxPacked(s0, upLeft, 0, 1, maxCd)
                if (up != u0) s0 = relaxPacked(s0, up, 1, 1, maxCd)
                state[i0] = s0
            }
            var prev = s0
            var i = i0 + 1
            while (i < lastI) {
                var s = state[i]
                val unreached = (s and SIGN_BIT) or UNREACHED_BIT
                val upRight = state[i - w + 1]
                if (((prev xor unreached) or (up xor unreached) or (upLeft xor unreached) or (upRight xor unreached)) != 0) {
                    if (prev != unreached) s = relaxPacked(s, prev, 1, 0, maxCd)
                    if (up != unreached) s = relaxPacked(s, up, 0, 1, maxCd)
                    if (upLeft != unreached) s = relaxPacked(s, upLeft, 1, 1, maxCd)
                    if (upRight != unreached) s = relaxPacked(s, upRight, 1, 1, maxCd)
                    state[i] = s
                }
                prev = s
                upLeft = up
                up = upRight
                i++
            }
            var sEnd = state[lastI]
            val uEnd = (sEnd and SIGN_BIT) or UNREACHED_BIT
            if (((prev xor uEnd) or (up xor uEnd) or (upLeft xor uEnd)) != 0) {
                if (prev != uEnd) sEnd = relaxPacked(sEnd, prev, 1, 0, maxCd)
                if (up != uEnd) sEnd = relaxPacked(sEnd, up, 0, 1, maxCd)
                if (upLeft != uEnd) sEnd = relaxPacked(sEnd, upLeft, 1, 1, maxCd)
                state[lastI] = sEnd
            }
            var right = sEnd
            var j = lastI - 1
            while (j >= i0) {
                val s = state[j]
                val unreached = (s and SIGN_BIT) or UNREACHED_BIT
                if (right != unreached) {
                    val ns = relaxPacked(s, right, 1, 0, maxCd)
                    state[j] = ns
                    right = ns
                } else {
                    right = s
                }
                j--
            }
        }
    }
}

/** Bottom-up pass of the single-pass disjoint 8SSEDT over `[xStart..xEnd] x [yStart..yEnd]`. */
private fun sweepBottomUp(
    state: IntArray,
    w: Int,
    xStart: Int,
    xEnd: Int,
    yStart: Int,
    yEnd: Int,
    maxCd: Int,
) {
    for (y in yEnd downTo yStart) {
        val row = y * w
        if (y == yEnd) {
            sweepRowHorizontalReverse(state, row + xStart, row + xEnd, maxCd)
        } else if (xStart == xEnd) {
            val i = row + xStart
            val s = state[i]
            val unreached = (s and SIGN_BIT) or UNREACHED_BIT
            val down = state[i + w]
            if (down != unreached) state[i] = relaxPacked(s, down, 0, 1, maxCd)
        } else {
            val i0 = row + xStart
            val lastI = row + xEnd
            var sEnd = state[lastI]
            val uEnd = (sEnd and SIGN_BIT) or UNREACHED_BIT
            var downRight = state[lastI + w]
            var down = state[lastI + w - 1]
            if (((downRight xor uEnd) or (down xor uEnd)) != 0) {
                if (downRight != uEnd) sEnd = relaxPacked(sEnd, downRight, 0, 1, maxCd)
                if (down != uEnd) sEnd = relaxPacked(sEnd, down, 1, 1, maxCd)
                state[lastI] = sEnd
            }
            var next = sEnd
            var i = lastI - 1
            while (i > i0) {
                var s = state[i]
                val unreached = (s and SIGN_BIT) or UNREACHED_BIT
                val downLeft = state[i + w - 1]
                if (((next xor unreached) or (down xor unreached) or (downRight xor unreached) or (downLeft xor unreached)) != 0) {
                    if (next != unreached) s = relaxPacked(s, next, 1, 0, maxCd)
                    if (down != unreached) s = relaxPacked(s, down, 0, 1, maxCd)
                    if (downRight != unreached) s = relaxPacked(s, downRight, 1, 1, maxCd)
                    if (downLeft != unreached) s = relaxPacked(s, downLeft, 1, 1, maxCd)
                    state[i] = s
                }
                next = s
                downRight = down
                down = downLeft
                i--
            }
            var s0 = state[i0]
            val u0 = (s0 and SIGN_BIT) or UNREACHED_BIT
            if (((next xor u0) or (down xor u0) or (downRight xor u0)) != 0) {
                if (next != u0) s0 = relaxPacked(s0, next, 1, 0, maxCd)
                if (down != u0) s0 = relaxPacked(s0, down, 0, 1, maxCd)
                if (downRight != u0) s0 = relaxPacked(s0, downRight, 1, 1, maxCd)
                state[i0] = s0
            }
            var left = s0
            var j = i0 + 1
            while (j <= lastI) {
                val s = state[j]
                val unreached = (s and SIGN_BIT) or UNREACHED_BIT
                if (left != unreached) {
                    val ns = relaxPacked(s, left, 1, 0, maxCd)
                    state[j] = ns
                    left = ns
                } else {
                    left = s
                }
                j++
            }
        }
    }
}

private fun sweepRowHorizontal(state: IntArray, i0: Int, lastI: Int, maxCd: Int) {
    var prev = state[i0]
    for (i in i0 + 1..lastI) {
        val s = state[i]
        val unreached = (s and SIGN_BIT) or UNREACHED_BIT
        if (prev != unreached) {
            val ns = relaxPacked(s, prev, 1, 0, maxCd)
            state[i] = ns
            prev = ns
        } else {
            prev = s
        }
    }
    var right = prev
    for (i in lastI - 1 downTo i0) {
        val s = state[i]
        val unreached = (s and SIGN_BIT) or UNREACHED_BIT
        if (right != unreached) {
            val ns = relaxPacked(s, right, 1, 0, maxCd)
            state[i] = ns
            right = ns
        } else {
            right = s
        }
    }
}

private fun sweepRowHorizontalReverse(state: IntArray, i0: Int, lastI: Int, maxCd: Int) {
    var right = state[lastI]
    for (i in lastI - 1 downTo i0) {
        val s = state[i]
        val unreached = (s and SIGN_BIT) or UNREACHED_BIT
        if (right != unreached) {
            val ns = relaxPacked(s, right, 1, 0, maxCd)
            state[i] = ns
            right = ns
        } else {
            right = s
        }
    }
    var left = right
    for (i in i0 + 1..lastI) {
        val s = state[i]
        val unreached = (s and SIGN_BIT) or UNREACHED_BIT
        if (left != unreached) {
            val ns = relaxPacked(s, left, 1, 0, maxCd)
            state[i] = ns
            left = ns
        } else {
            left = s
        }
    }
}

/**
 * Propagates neighbour [sn] by `(sx, sy)` using integer squared distances, consulting [edtTieWins]
 * only on exact ties (`cdInt == prevCd`) so IEEE-754 `cd < di * di` behaviour is preserved verbatim.
 */
@Suppress("NOTHING_TO_INLINE")
private inline fun relaxPacked(s: Int, sn: Int, sx: Int, sy: Int, maxCd: Int): Int {
    val cx: Int
    val cy: Int
    val cdInt: Int
    if ((s xor sn) < 0) {
        cx = sx
        cy = sy
        cdInt = sx + sy
    } else {
        cx = (sn and COORD_MASK) + sx
        cy = ((sn ushr DY_SHIFT) and COORD_MASK) + sy
        cdInt = cx * cx + cy * cy
        if (cdInt > maxCd) return s
    }
    if ((s and UNREACHED_BIT) != 0 || isBetterDistance(s, cdInt)) {
        return (s and SIGN_BIT) or (cy shl DY_SHIFT) or cx
    }
    return s
}

private fun isBetterDistance(s: Int, cdInt: Int): Boolean {
    val prevDx = s and COORD_MASK
    val prevDy = (s ushr DY_SHIFT) and COORD_MASK
    val prevCd = prevDx * prevDx + prevDy * prevDy
    return if (cdInt != prevCd) cdInt < prevCd else edtTieWins(cdInt)
}

/** Uncapped fallback for bands wider than the 10-bit narrow-band coordinate budget. */
private fun buildPathFieldUncapped(
    state: IntArray,
    layerWidth: Int,
    layerHeight: Int,
    fw: Int,
    fh: Int,
    range: Float,
    buffers: GlassPathFieldBuffers,
): GlassPathField {
    val count = layerWidth * layerHeight
    buffers.ensureCapacity(count)
    val inside = buffers.inside
    for (i in 0 until count) inside[i] = state[i] < 0
    val full = signedDistance(inside, layerWidth, layerHeight, buffers)
    val grey = buffers.greyOut
    for (y in 0 until fh) {
        val row0 = (y * FIELD_SCALE) * layerWidth
        val row1 = row0 + layerWidth
        val outRow = y * fw
        for (x in 0 until fw) {
            val col = x * FIELD_SCALE
            val acc = 0f + full[row0 + col] + full[row0 + col + 1] + full[row1 + col] + full[row1 + col + 1]
            val dist = acc / 4f / FIELD_SCALE
            val norm = ((dist / range) * 0.5f + 0.5f).coerceIn(0f, 1f)
            grey[outRow + x] = (norm * 255f).roundToInt().coerceIn(0, 255).toByte()
        }
    }
    return GlassPathField(
        bitmap = createGreyscaleFieldBitmap(fw, fh, grey),
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
private fun signedDistance(inside: BooleanArray, w: Int, h: Int, buffers: GlassPathFieldBuffers): FloatArray {
    val count = w * h
    val outer = buffers.outer
    edt(inside, w, h, seedInside = false, buffers).copyInto(outer, endIndex = count)
    val inner = edt(inside, w, h, seedInside = true, buffers)
    for (i in 0 until count) outer[i] -= inner[i]
    return outer
}

private const val FAR = 1e9f

/** Overwrites the active scratch rectangle with distances to the selected mask boundary. */
private fun edt(inside: BooleanArray, w: Int, h: Int, seedInside: Boolean, buffers: GlassPathFieldBuffers): FloatArray {
    val dx = buffers.dx
    val dy = buffers.dy
    val d = buffers.distance

    for (i in 0 until w * h) {
        val seed = if (seedInside) !inside[i] else inside[i]
        if (seed) {
            dx[i] = 0f; dy[i] = 0f; d[i] = 0f
        } else {
            dx[i] = FAR; dy[i] = FAR; d[i] = FAR
        }
    }

    for (y in 0 until h) {
        val row = y * w
        for (x in 0 until w) {
            val i = row + x
            if (x > 0) relax(dx, dy, d, i, i - 1, 1f, 0f)
            if (y > 0) {
                relax(dx, dy, d, i, i - w, 0f, 1f)
                if (x > 0) relax(dx, dy, d, i, i - w - 1, 1f, 1f)
                if (x < w - 1) relax(dx, dy, d, i, i - w + 1, 1f, 1f)
            }
        }
        for (x in w - 2 downTo 0) {
            val i = row + x
            relax(dx, dy, d, i, i + 1, 1f, 0f)
        }
    }
    for (y in h - 1 downTo 0) {
        val row = y * w
        for (x in w - 1 downTo 0) {
            val i = row + x
            if (x < w - 1) relax(dx, dy, d, i, i + 1, 1f, 0f)
            if (y < h - 1) {
                relax(dx, dy, d, i, i + w, 0f, 1f)
                if (x < w - 1) relax(dx, dy, d, i, i + w + 1, 1f, 1f)
                if (x > 0) relax(dx, dy, d, i, i + w - 1, 1f, 1f)
            }
        }
        for (x in 1 until w) {
            val i = row + x
            relax(dx, dy, d, i, i - 1, 1f, 0f)
        }
    }

    for (i in 0 until w * h) if (d[i] >= FAR) d[i] = min(w, h).toFloat()
    return d
}

/** Takes the neighbour [n]'s nearest-seed offset, one step further, when it improves pixel [i]. */
@Suppress("NOTHING_TO_INLINE")
private inline fun relax(dx: FloatArray, dy: FloatArray, d: FloatArray, i: Int, n: Int, sx: Float, sy: Float) {
    val cx = dx[n] + sx
    val cy = dy[n] + sy
    if (cx >= FAR || cy >= FAR) return
    val cd = cx * cx + cy * cy
    val di = d[i]
    if (cd < di * di) {
        dx[i] = cx; dy[i] = cy; d[i] = sqrt(cd)
    }
}

/**
 * Rasterizes [path] at [pad] into an alpha mask (`>= 128` is signed `< 0`) without a full ARGB copy,
 * or null when the platform cannot read the mask back; the caller then has no field rather than a blank one.
 */
internal expect fun rasterizeMaskAlpha(
    path: Path,
    width: Int,
    height: Int,
    pad: Float,
    buffers: GlassPathFieldBuffers,
): ByteArray?

/** Builds the half-resolution opaque greyscale field bitmap directly from 8-bit texels `grey[0 until width * height]`. */
internal expect fun createGreyscaleFieldBitmap(width: Int, height: Int, grey: ByteArray): ImageBitmap

/** Compose has no common way to make an [ImageBitmap] from raw pixels. */
internal expect fun createFieldBitmap(width: Int, height: Int, pixels: IntArray): ImageBitmap
