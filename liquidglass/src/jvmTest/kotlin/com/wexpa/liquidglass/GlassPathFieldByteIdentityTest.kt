package com.wexpa.liquidglass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toPixelMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Verifies that [buildPathField] produces output byte-for-byte identical to the unoptimized
 * full-resolution two-pass 8SSEDT reference across geometry, padding, dimension, and band regimes.
 */
class GlassPathFieldByteIdentityTest {

    private fun rawSkiaBytes(bitmap: ImageBitmap): ByteArray {
        val skia = bitmap.asSkiaBitmap()
        val rowBytes = skia.imageInfo.minRowBytes
        return skia.readPixels(skia.imageInfo, rowBytes, 0, 0)
            ?: error("Failed to read raw Skia pixels")
    }

    private fun assertFieldByteIdentical(
        expected: GlassPathField?,
        actual: GlassPathField?,
        label: String,
    ) {
        if (expected == null) {
            assertNull(actual, "$label: expected null field")
            return
        }
        assertNotNull(actual, "$label: expected non-null field")
        assertEquals(expected.range.toRawBits(), actual.range.toRawBits(), "$label: range bits")
        assertEquals(expected.scale.toRawBits(), actual.scale.toRawBits(), "$label: scale bits")
        assertEquals(expected.layerWidth, actual.layerWidth, "$label: layerWidth")
        assertEquals(expected.layerHeight, actual.layerHeight, "$label: layerHeight")
        assertEquals(expected.bitmap.width, actual.bitmap.width, "$label: bitmap.width")
        assertEquals(expected.bitmap.height, actual.bitmap.height, "$label: bitmap.height")
        assertEquals(expected.bitmap.config, actual.bitmap.config, "$label: bitmap.config")

        val expectedBytes = rawSkiaBytes(expected.bitmap)
        val actualBytes = rawSkiaBytes(actual.bitmap)
        if (!expectedBytes.contentEquals(actualBytes)) {
            val idx = expectedBytes.indices.first { expectedBytes[it] != actualBytes[it] }
            val texel = idx / 4
            val channel = idx % 4
            val tx = texel % expected.bitmap.width
            val ty = texel / expected.bitmap.width
            assertEquals(
                expectedBytes[idx].toInt() and 0xFF,
                actualBytes[idx].toInt() and 0xFF,
                "$label: first raw byte mismatch at byte=$idx texel=($tx,$ty) ch=$channel",
            )
        }
        assertContentEquals(expectedBytes, actualBytes, "$label: raw bytes")
    }

    private fun starPath(w: Float, h: Float, points: Int = 5): Path = Path().apply {
        val cx = w * 0.5f
        val cy = h * 0.5f
        val outerR = min(w, h) * 0.48f
        val innerR = outerR * 0.41f
        for (i in 0 until points * 2) {
            val r = if (i % 2 == 0) outerR else innerR
            val a = (i * PI / points) - PI / 2.0
            val x = cx + (cos(a) * r).toFloat()
            val y = cy + (sin(a) * r).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    private fun notchedPanelPath(w: Float, h: Float, notchDepth: Float = 12f): Path = Path().apply {
        moveTo(0f, 0f)
        lineTo(w * 0.37f, 0f)
        lineTo(w * 0.5f, notchDepth)
        lineTo(w * 0.63f, 0f)
        lineTo(w, 0f)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }

    private fun ringWithHolePath(w: Float, h: Float): Path = Path().apply {
        fillType = PathFillType.EvenOdd
        addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(min(w, h) * 0.3f)))
        addOval(Rect(w * 0.28f, h * 0.24f, w * 0.72f, h * 0.76f))
    }

    private fun disjointIslandsPath(w: Float, h: Float): Path = Path().apply {
        addOval(Rect(2f, 2f, w * 0.22f, h * 0.28f))
        addOval(Rect(w * 0.76f, h * 0.68f, w - 2f, h - 2f))
    }

    private fun bezierAndSliverPath(w: Float, h: Float): Path = Path().apply {
        moveTo(3.3f, h * 0.2f)
        cubicTo(w * 0.25f, -4.7f, w * 0.65f, h * 0.45f, w - 2.1f, h * 0.15f)
        lineTo(w * 0.85f, h - 1.4f)
        cubicTo(w * 0.55f, h * 0.6f, w * 0.3f, h * 0.95f, 1.7f, h * 0.75f)
        close()
        // Diagonal thin ribbon to exercise subpixel alpha thresholds and diagonal tie-breaking.
        moveTo(0f, h - 2f)
        lineTo(w * 0.9f, 0f)
        lineTo(w * 0.9f + 1.6f, 1.6f)
        lineTo(1.6f, h - 0.4f)
        close()
    }

    private fun fullLayerWithPinholePath(layerW: Int, layerH: Int, pad: Float): Path = Path().apply {
        fillType = PathFillType.EvenOdd
        addRect(Rect(-pad - 10f, -pad - 10f, layerW - pad + 10f, layerH - pad + 10f))
        val cx = layerW * 0.5f - pad
        val cy = layerH * 0.5f - pad
        addRect(Rect(cx - 3f, cy - 3f, cx + 3f, cy + 3f))
    }

    @Test
    fun comparatorDetectsSingleByteChange() {
        val p = notchedPanelPath(60f, 40f)
        val ref = buildPathFieldReference(p, 108, 88, 24f, 36f)!!
        val map = ref.bitmap.toPixelMap()
        val words = IntArray(map.width * map.height)
        ref.bitmap.readPixels(words)
        words[words.size / 2] = words[words.size / 2] xor 0x00010101
        val mutated = GlassPathField(
            bitmap = createFieldBitmap(map.width, map.height, words),
            range = ref.range,
            scale = ref.scale,
            layerWidth = ref.layerWidth,
            layerHeight = ref.layerHeight,
        )
        assertFailsWith<AssertionError> {
            assertFieldByteIdentical(ref, mutated, "mutated")
        }
    }

    @Test
    fun degenerateAndMinimumDimensionsMatchReference() {
        val p = notchedPanelPath(20f, 20f)
        for ((lw, lh) in listOf(0 to 0, -4 to 10, 10 to -4, 1 to 10, 3 to 10, 10 to 3, 3 to 3, 4 to 4, 5 to 5, 4 to 9, 9 to 4, 17 to 13)) {
            val expected = buildPathFieldReference(p, lw, lh, 2f, 16f)
            val actual = buildPathField(p, lw, lh, 2f, 16f)
            assertFieldByteIdentical(expected, actual, "dims=${lw}x$lh")
        }
        assertNull(buildPathField(p, 50_000, 50_000, 2f, 16f), "overflow pixel count should return null")
        for (band in listOf(-10f, 0f, Float.POSITIVE_INFINITY)) {
            val expected = buildPathFieldReference(p, 24, 24, 2f, band)
            val actual = buildPathField(p, 24, 24, 2f, band)
            assertFieldByteIdentical(expected, actual, "band=$band")
        }
        val nanActual = buildPathField(p, 24, 24, 2f, Float.NaN)
        val zeroExpected = buildPathFieldReference(p, 24, 24, 2f, 0f)
        assertFieldByteIdentical(zeroExpected, nanActual, "band=NaN falls back to minimum 8px range")
        val buffers = GlassPathFieldBuffers()
        val firstScratch = buffers.ensureAlphaScratch(64)
        kotlin.test.assertSame(firstScratch, buffers.ensureAlphaScratch(32))
    }

    @Test
    fun emptyAllInsideSingleSpeckAndPinholeMasksMatchReference() {
        val shared = GlassPathFieldBuffers()
        for ((lw, lh, pad, band) in listOf(
            listOf(64f, 48f, 12f, 24f),
            listOf(101f, 77f, 23f, 48f),
            listOf(32f, 32f, 4f, 4f),
        )) {
            val w = lw.toInt()
            val h = lh.toInt()
            val empty = Path()
            val full = Path().apply { addRect(Rect(-pad - 8f, -pad - 8f, w.toFloat(), h.toFloat())) }
            val speck = Path().apply { addRect(Rect(5f, 7f, 6f, 8f)) }
            val pinhole = fullLayerWithPinholePath(w, h, pad)

            for ((name, path) in listOf("empty" to empty, "full" to full, "speck" to speck, "pinhole" to pinhole)) {
                val label = "$name@${w}x$h/pad=$pad/band=$band"
                val ref = buildPathFieldReference(path, w, h, pad, band)
                assertFieldByteIdentical(ref, buildPathField(path, w, h, pad, band), "$label/pooled")
                assertFieldByteIdentical(ref, buildPathField(path, w, h, pad, band, shared), "$label/reused")
            }
        }
    }

    @Test
    fun shapesOddEvenFractionalPadsAndBandsAreByteIdentical() {
        val reused = GlassPathFieldBuffers()
        data class Case(val w: Int, val h: Int, val pad: Float, val band: Float)

        val cases = listOf(
            Case(120, 80, 22f, 16f),
            Case(120, 80, 23f, 16f),
            Case(121, 81, 24f, 36f),
            Case(121, 81, 25f, 36f),
            Case(100, 60, 17f / 0.75f, 16f),
            Case(145, 97, 12.5f, 4f),
            Case(145, 97, 0f, 48f),
            Case(220, 130, 24f, 96f),
            Case(90, 70, 16f, 260f),
            Case(64, 50, 10f, 800f),
        )

        for ((w, h, pad, band) in cases) {
            val lw = (w + 2f * pad).roundToInt()
            val lh = (h + 2f * pad).roundToInt()
            val shapes = listOf(
                "notch" to notchedPanelPath(w.toFloat(), h.toFloat()),
                "star" to starPath(w.toFloat(), h.toFloat()),
                "ring" to ringWithHolePath(w.toFloat(), h.toFloat()),
                "islands" to disjointIslandsPath(w.toFloat(), h.toFloat()),
                "bezier" to bezierAndSliverPath(w.toFloat(), h.toFloat()),
            )
            for ((name, path) in shapes) {
                val label = "$name/panel=${w}x$h/layer=${lw}x$lh/pad=$pad/band=$band"
                val ref = buildPathFieldReference(path, lw, lh, pad, band)
                assertFieldByteIdentical(ref, buildPathField(path, lw, lh, pad, band), "$label/pooled")
                assertFieldByteIdentical(ref, buildPathField(path, lw, lh, pad, band, reused), "$label/reused")
            }
        }
    }

    @Test
    fun benchmarkSizesAreByteIdentical() {
        val reused = GlassPathFieldBuffers()
        for ((w, h) in listOf(512 to 256, 1080 to 300, 1080 to 720)) {
            val path = notchedPanelPath(w.toFloat(), h.toFloat(), notchDepth = 12f)
            val lw = w + 48
            val lh = h + 48
            val ref = buildPathFieldReference(path, lw, lh, 24f, 48f)
            assertFieldByteIdentical(ref, buildPathField(path, lw, lh, 24f, 48f), "bench-${w}x$h/pooled")
            assertFieldByteIdentical(ref, buildPathField(path, lw, lh, 24f, 48f, reused), "bench-${w}x$h/reused")
        }
    }

    /** Exhaustively checks the integer squared-distance ordering invariant over the full narrow-band range. */
    @Test
    fun integerSquaredDistanceOrderingMatchesIeeeFloatAcrossEntireNarrowBandRange() {
        val maxPrevCd = 1024 * 1024
        for (prevCd in 0..maxPrevCd) {
            val di = sqrt(prevCd.toFloat())
            val diSq = di * di
            if (prevCd > 0) {
                kotlin.test.assertTrue(
                    (prevCd - 1).toFloat() < diSq,
                    "prevCd=$prevCd: prevCd-1 must strictly beat di*di",
                )
            }
            kotlin.test.assertFalse(
                (prevCd + 1).toFloat() < diSq,
                "prevCd=$prevCd: prevCd+1 must never beat di*di",
            )
            assertEquals(
                prevCd.toFloat() < diSq,
                edtTieWins(prevCd),
                "prevCd=$prevCd: exact tie table must match IEEE-754 float comparison",
            )
        }
    }
}

private const val REF_FIELD_SCALE = 2
private const val REF_FAR = 1e9f

/** Verbatim reference copy of the pre-#81 full-resolution two-pass 8SSEDT field builder. */
internal fun buildPathFieldReference(
    path: Path,
    layerWidth: Int,
    layerHeight: Int,
    pad: Float,
    band: Float,
): GlassPathField? {
    val fw = (layerWidth / REF_FIELD_SCALE).coerceAtLeast(1)
    val fh = (layerHeight / REF_FIELD_SCALE).coerceAtLeast(1)
    if (fw < 2 || fh < 2) return null

    val mask = ImageBitmap(layerWidth, layerHeight)
    val canvas = Canvas(mask)
    val paint = Paint().apply { color = Color.White }
    canvas.save()
    canvas.translate(pad, pad)
    canvas.drawPath(path, paint)
    canvas.restore()

    val pixels = mask.toPixelMap()
    val count = layerWidth * layerHeight
    val inside = BooleanArray(count)
    val argb = pixels.buffer
    for (y in 0 until layerHeight) {
        val row = pixels.bufferOffset + y * pixels.stride
        for (x in 0 until layerWidth) {
            inside[y * layerWidth + x] = (argb[row + x] ushr 24) >= 128
        }
    }

    val outer = edtReference(inside, layerWidth, layerHeight, seedInside = false)
    val inner = edtReference(inside, layerWidth, layerHeight, seedInside = true)
    for (i in 0 until count) outer[i] -= inner[i]
    val full = outer

    val distance = FloatArray(fw * fh)
    for (y in 0 until fh) {
        for (x in 0 until fw) {
            var acc = 0f
            var n = 0
            for (dy in 0 until REF_FIELD_SCALE) for (dx in 0 until REF_FIELD_SCALE) {
                val xx = x * REF_FIELD_SCALE + dx
                val yy = y * REF_FIELD_SCALE + dy
                if (xx < layerWidth && yy < layerHeight) {
                    acc += full[yy * layerWidth + xx]
                    n++
                }
            }
            distance[y * fw + x] = if (n > 0) acc / n / REF_FIELD_SCALE else 0f
        }
    }

    val range = (band * 1.5f).coerceAtLeast(8f) / REF_FIELD_SCALE
    val out = IntArray(fw * fh)
    for (i in out.indices) {
        val norm = ((distance[i] / range) * 0.5f + 0.5f).coerceIn(0f, 1f)
        val v = (norm * 255f).roundToInt().coerceIn(0, 255)
        out[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }

    return GlassPathField(
        bitmap = createFieldBitmap(fw, fh, out),
        range = range * REF_FIELD_SCALE,
        scale = 1f / REF_FIELD_SCALE,
        layerWidth = layerWidth,
        layerHeight = layerHeight,
    )
}

private fun edtReference(inside: BooleanArray, w: Int, h: Int, seedInside: Boolean): FloatArray {
    val count = w * h
    val dx = FloatArray(count)
    val dy = FloatArray(count)
    val d = FloatArray(count)

    for (i in 0 until count) {
        val seed = if (seedInside) !inside[i] else inside[i]
        if (seed) {
            dx[i] = 0f; dy[i] = 0f; d[i] = 0f
        } else {
            dx[i] = REF_FAR; dy[i] = REF_FAR; d[i] = REF_FAR
        }
    }

    for (y in 0 until h) {
        val row = y * w
        for (x in 0 until w) {
            val i = row + x
            if (x > 0) relaxReference(dx, dy, d, i, i - 1, 1f, 0f)
            if (y > 0) {
                relaxReference(dx, dy, d, i, i - w, 0f, 1f)
                if (x > 0) relaxReference(dx, dy, d, i, i - w - 1, 1f, 1f)
                if (x < w - 1) relaxReference(dx, dy, d, i, i - w + 1, 1f, 1f)
            }
        }
        for (x in w - 2 downTo 0) {
            val i = row + x
            relaxReference(dx, dy, d, i, i + 1, 1f, 0f)
        }
    }
    for (y in h - 1 downTo 0) {
        val row = y * w
        for (x in w - 1 downTo 0) {
            val i = row + x
            if (x < w - 1) relaxReference(dx, dy, d, i, i + 1, 1f, 0f)
            if (y < h - 1) {
                relaxReference(dx, dy, d, i, i + w, 0f, 1f)
                if (x < w - 1) relaxReference(dx, dy, d, i, i + w + 1, 1f, 1f)
                if (x > 0) relaxReference(dx, dy, d, i, i + w - 1, 1f, 1f)
            }
        }
        for (x in 1 until w) {
            val i = row + x
            relaxReference(dx, dy, d, i, i - 1, 1f, 0f)
        }
    }

    for (i in 0 until count) if (d[i] >= REF_FAR) d[i] = min(w, h).toFloat()
    return d
}

@Suppress("NOTHING_TO_INLINE")
private inline fun relaxReference(
    dx: FloatArray,
    dy: FloatArray,
    d: FloatArray,
    i: Int,
    n: Int,
    sx: Float,
    sy: Float,
) {
    val cx = dx[n] + sx
    val cy = dy[n] + sy
    if (cx >= REF_FAR || cy >= REF_FAR) return
    val cd = cx * cx + cy * cy
    val di = d[i]
    if (cd < di * di) {
        dx[i] = cx; dy[i] = cy; d[i] = sqrt(cd)
    }
}
