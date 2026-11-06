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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The bounded transform skips layer blocks too far from the outline to matter, and must hand back
 * the field the whole-layer transform does, byte for byte. These compare the two over shapes,
 * sizes, pads and bands that put the outline in every relation to the blocks: hugging the layer's
 * edge, thin, hollow, in pieces, curved, absent.
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

    @Test fun boundedFieldMatchesTheWholeLayerTransformByteForByte() {
        val buffers = GlassPathFieldBuffers()
        for (c in cases()) {
            val lw = c.w + 2 * c.pad
            val lh = c.h + 2 * c.pad
            val whole = buildPathField(c.path, lw, lh, c.pad.toFloat(), c.band, buffers, bounded = false)
            val bounded = buildPathField(c.path, lw, lh, c.pad.toFloat(), c.band, buffers, bounded = true)
            assertNotNull(whole, c.name)
            assertNotNull(bounded, c.name)
            assertEquals(pixels(whole.bitmap), pixels(bounded.bitmap), "${c.name} ${c.w}x${c.h} pad=${c.pad} band=${c.band}")
            assertEquals(whole.range, bounded.range)
        }
        // The comparison is only worth anything if the bounded path skipped work in some case.
        val big = roundRect(1080, 720, 40f)
        val mask = ImageBitmap(1080 + 48, 720 + 48)
        val canvas = androidx.compose.ui.graphics.Canvas(mask)
        canvas.translate(24f, 24f)
        canvas.drawPath(big, androidx.compose.ui.graphics.Paint().apply { color = androidx.compose.ui.graphics.Color.White })
        val map = mask.toPixelMap()
        val inside = BooleanArray(mask.width * mask.height) { map[it % mask.width, it / mask.width].alpha > 0.5f }
        val blocks = activeBlocks(inside, mask.width, mask.height, 48f)
        assertTrue(blocks != null && blocks.active.any { !it }, "a 1080x720 panel at band 48 should leave blocks in its middle unvisited")
    }

    @Test fun anAbsentOutlineKeepsTheWholeLayerBehaviour() {
        val inside = BooleanArray(300 * 200)
        assertNull(activeBlocks(inside, 300, 200, 48f))
        assertNull(activeBlocks(BooleanArray(300 * 200) { true }, 300, 200, 48f))
    }

    @Test fun smallLayersVisitEveryBlock() {
        // Everything is within range of the outline, so there is nothing to skip.
        val w = 120; val h = 90
        val inside = BooleanArray(w * h) { i -> i % w in 20 until 100 && i / w in 20 until 70 }
        assertNull(activeBlocks(inside, w, h, 48f))
    }
}
