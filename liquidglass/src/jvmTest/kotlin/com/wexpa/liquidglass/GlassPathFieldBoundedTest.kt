package com.wexpa.liquidglass

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.toPixelMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The narrow-band transform must hand back the field the whole-layer transform does, byte for byte.
 * These compare it with the whole-layer reference ([buildPathFieldReference]) over shapes, sizes, pads
 * and bands that put the outline in every relation to the band: hugging the layer's edge, thin,
 * hollow, in pieces, curved, absent. The cases were written for #180's bounded blocks and still
 * cover the same ground.
 */
class GlassPathFieldBoundedTest {
    internal class Case(val name: String, val w: Int, val h: Int, val pad: Int, val band: Float, val path: Path)

    private fun roundRect(w: Int, h: Int, r: Float) = Path().apply {
        addRoundRect(RoundRect(Rect(0f, 0f, w.toFloat(), h.toFloat()), CornerRadius(r, r)))
    }

    private fun star(w: Int, h: Int, points: Int) = Path().apply {
        val cx = w / 2f; val cy = h / 2f
        for (i in 0 until points * 2) {
            val a = PI.toFloat() * i / points
            val k = if (i % 2 == 0) 1f else 0.45f
            val x = cx + cos(a) * cx * k; val y = cy + sin(a) * cy * k
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    private fun blob(w: Int, h: Int) = Path().apply {
        moveTo(w * 0.1f, h * 0.5f)
        cubicTo(w * 0.1f, h * -0.2f, w * 0.7f, h * 0.1f, w * 0.95f, h * 0.35f)
        cubicTo(w * 1.1f, h * 0.9f, w * 0.4f, h * 1.2f, w * 0.1f, h * 0.5f)
        close()
    }

    private fun ring(w: Int, h: Int) = Path().apply {
        fillType = PathFillType.EvenOdd
        addOval(Rect(0f, 0f, w.toFloat(), h.toFloat()))
        addOval(Rect(w * 0.3f, h * 0.3f, w * 0.7f, h * 0.7f))
    }

    private fun islands(w: Int, h: Int) = Path().apply {
        addRoundRect(RoundRect(Rect(0f, 0f, w * 0.3f, h * 0.4f), CornerRadius(20f, 20f)))
        addRoundRect(RoundRect(Rect(w * 0.6f, h * 0.55f, w.toFloat(), h.toFloat()), CornerRadius(8f, 8f)))
    }

    private fun notch(w: Int, h: Int) = Path().apply {
        moveTo(0f, 0f); lineTo(w * .4f, 0f); lineTo(w * .5f, 12f); lineTo(w * .6f, 0f)
        lineTo(w.toFloat(), 0f); lineTo(w.toFloat(), h.toFloat()); lineTo(0f, h.toFloat()); close()
    }

    internal fun cases(): List<Case> {
        val out = ArrayList<Case>()
        for ((w, h, pad, band) in listOf(
            Quad(200, 150, 23, 48f), Quad(333, 201, 25, 8f), Quad(600, 300, 24, 48f),
            Quad(1080, 300, 24, 48f), Quad(700, 700, 30, 96f), Quad(1080, 720, 24, 48f),
            Quad(400, 400, 24, 30f), Quad(90, 900, 24, 48f),
        )) {
            out += Case("roundRect", w, h, pad, band, roundRect(w, h, minOf(w, h) / 2f))
            out += Case("squareRect", w, h, pad, band, roundRect(w, h, 0f))
            out += Case("star", w, h, pad, band, star(w, h, 5))
            out += Case("blob", w, h, pad, band, blob(w, h))
            out += Case("ring", w, h, pad, band, ring(w, h))
            out += Case("islands", w, h, pad, band, islands(w, h))
            out += Case("notch", w, h, pad, band, notch(w, h))
        }
        // A bar thinner than a block, and one that is a single pixel row.
        out += Case("thin", 900, 6, 24, 48f, roundRect(900, 6, 3f))
        out += Case("hairline", 900, 1, 24, 48f, roundRect(900, 1, 0f))
        // Nothing drawn: no outline, so no seeds to measure from.
        out += Case("empty", 300, 200, 24, 48f, Path())
        return out
    }

    internal data class Quad(val a: Int, val b: Int, val c: Int, val d: Float)

    internal fun pixels(bitmap: ImageBitmap): List<Int> {
        val map = bitmap.toPixelMap()
        return List(map.width * map.height) { i -> (map[i % map.width, i / map.width].red * 255f).toInt() }
    }

    @Test fun everyCaseMatchesTheWholeLayerReferenceByteForByte() {
        val buffers = GlassPathFieldBuffers()
        for (c in cases()) {
            val lw = c.w + 2 * c.pad
            val lh = c.h + 2 * c.pad
            val reference = buildPathFieldReference(c.path, lw, lh, c.pad.toFloat(), c.band)
            val ours = buildPathField(c.path, lw, lh, c.pad.toFloat(), c.band, buffers)
            assertNotNull(reference, c.name)
            assertNotNull(ours, c.name)
            assertEquals(pixels(reference.bitmap), pixels(ours.bitmap), "${c.name} ${c.w}x${c.h} pad=${c.pad} band=${c.band}")
            assertEquals(reference.range, ours.range)
        }
    }

    /**
     * A layer wider than 1024 px with bands whose encoded half-span sits just under, on and just over the
     * narrow band's limit (1020 px); one step past it the build takes the whole-layer transform instead. The
     * rounded rectangle checks that choice; the bar at the left edge leaves the right end about 2000 px from any
     * seed, so the capped sweep has to leave pixels unreached and carry offsets out to its cap.
     */
    @Test fun wideLayerAtTheNarrowBandLimitMatchesTheReference() {
        val buffers = GlassPathFieldBuffers()
        val w = 2100; val h = 260; val pad = 24
        val bar = Path().apply { addRect(Rect(0f, 0f, 20f, h.toFloat())) }
        for ((name, path) in listOf("roundRect" to roundRect(w, h, 90f), "leftBar" to bar)) {
            for (band in listOf(679f, 680f, 681f)) {
                val reference = buildPathFieldReference(path, w + 2 * pad, h + 2 * pad, pad.toFloat(), band)
                val ours = buildPathField(path, w + 2 * pad, h + 2 * pad, pad.toFloat(), band, buffers)
                assertNotNull(reference, "$name band=$band"); assertNotNull(ours, "$name band=$band")
                assertEquals(pixels(reference.bitmap), pixels(ours.bitmap), "$name band=$band")
            }
        }
    }
}
