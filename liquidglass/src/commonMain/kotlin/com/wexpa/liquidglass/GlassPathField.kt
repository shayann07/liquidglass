package com.wexpa.liquidglass

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

    /** Grows all scratch planes together; smaller builds retain their existing arrays. */
    fun ensureCapacity(count: Int) {
        if (inside.size >= count) return
        inside = BooleanArray(count)
        dx = FloatArray(count)
        dy = FloatArray(count)
        distance = FloatArray(count)
        outer = FloatArray(count)
    }
}

/**
 * One bounded reusable workspace (at most ~4.25 MiB of primitive arrays), not one per panel.
 * Concurrent callers never wait for it or share writable buffers: they use temporary storage.
 * Larger builds also use temporary storage so a large one-off panel does not grow the pool.
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

    val full = signedDistance(inside, layerWidth, layerHeight, buffers)
    // A texel covers FIELD_SCALE x FIELD_SCALE layer pixels and is sampled at its centre, which
    // is the mean of the pixel centres it covers; distance is smooth, so the mean is the value.
    val distance = FloatArray(fw * fh)
    for (y in 0 until fh) {
        for (x in 0 until fw) {
            var acc = 0f; var n = 0
            for (dy in 0 until FIELD_SCALE) for (dx in 0 until FIELD_SCALE) {
                val xx = x * FIELD_SCALE + dx; val yy = y * FIELD_SCALE + dy
                if (xx < layerWidth && yy < layerHeight) { acc += full[yy * layerWidth + xx]; n++ }
            }
            distance[y * fw + x] = if (n > 0) acc / n / FIELD_SCALE else 0f
        }
    }
    // `full` is in layer pixels; the field's encoding below is in texels (range / FIELD_SCALE), so
    // the distance is divided by FIELD_SCALE above to keep the stored units unchanged.

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

    // Every neighbour below is inside the grid by its row/column guard, so [relax] needs no bounds
    // check; the visiting order and the comparison are unchanged, so the result is bit-identical.
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

/** Compose has no common way to make an [ImageBitmap] from raw pixels. */
internal expect fun createFieldBitmap(width: Int, height: Int, pixels: IntArray): ImageBitmap
