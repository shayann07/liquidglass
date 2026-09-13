package com.wexpa.liquidglass

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Consumer-level check of the padding contract: the sampled distance field, the shader's `uPad`
 * and the layer size are used together, at full and reduced scale, and the silhouette lands where
 * the closed form puts it. A field rasterised one pixel off the pad the shader reads it through
 * moves the silhouette by that pixel; the sentinel case below shows the check sees exactly that.
 *
 * What this cannot exercise on the JVM: the Compose recording and draw-back translate, which
 * only run on the Android shader path. Those share the same pad through [GlassStyle.recordPad]
 * and are verified by the device comparison.
 */
class GlassFieldPadConsumerTest {
    private val width = 300
    private val height = 120
    private val r = 60f
    private val band = 36f

    private fun capsule(): Path = Path().apply { addRoundRect(RoundRect(0f, 0f, width.toFloat(), height.toFloat(), CornerRadius(r, r))) }

    private fun alpha(p: Int) = (p ushr 24) and 0xFF

    /** Row where coverage crosses one half, going down the given column; sub-pixel by linear interpolation. */
    private fun topEdge(px: IntArray, layerW: Int, column: Int, from: Int, to: Int): Double {
        var prev = alpha(px[from * layerW + column])
        for (y in from + 1..to) {
            val cur = alpha(px[y * layerW + column])
            if (prev < 128 && cur >= 128) return (y - 1) + (128.0 - prev) / (cur - prev)
            prev = cur
        }
        error("no top edge in column $column")
    }

    /** Column where coverage crosses one half, going right along the given row. */
    private fun leftEdge(px: IntArray, layerW: Int, row: Int, from: Int, to: Int): Double {
        var prev = alpha(px[row * layerW + from])
        for (x in from + 1..to) {
            val cur = alpha(px[row * layerW + x])
            if (prev < 128 && cur >= 128) return (x - 1) + (128.0 - prev) / (cur - prev)
            prev = cur
        }
        error("no left edge in row $row")
    }

    private fun render(pad: Int, scale: Float, field: GlassPathField?, fuse: FloatArray? = null, held: Float = 0f): IntArray {
        val w = (width * scale).toInt(); val h = (height * scale).toInt()
        return GlassRender.render(
            width = w, height = h, pad = (pad * scale).toInt(),
            refractBand = band * scale, refractDepth = 0f,
            radii = FloatArray(4) { r * scale }, profile = 1f, formation = 0f, heldLens = held,
            field = field, fuse = fuse, fuseRadius = if (fuse != null) 20f * scale else 0f, fuseStrength = if (fuse != null) 24f * scale else 0f,
            backdrop = { x, y -> if (((x / 8) + (y / 8)) % 2 == 0) 0x606060 else 0xA0A0A0 },
        )
    }

    private fun silhouette(px: IntArray, pad: Int, scale: Float): Pair<Double, Double> {
        val w = (width * scale).toInt(); val h = (height * scale).toInt(); val p = (pad * scale).toInt()
        val layerW = w + 2 * p
        val top = topEdge(px, layerW, column = p + w / 2, from = 0, to = p + h / 2)
        val left = leftEdge(px, layerW, row = p + h / 2, from = 0, to = p + w / 2)
        // Back to full-resolution panel coordinates: subtract the pad, undo the scale.
        return Pair((top - p) / scale, (left - p) / scale)
    }

    @Test
    fun sampledFieldAtTheRecordPadMatchesTheClosedForm() {
        val pad = 24
        val field = buildPathField(capsule(), width + 2 * pad, height + 2 * pad, pad.toFloat(), band)!!
        val closed = silhouette(render(pad, 1f, null), pad, 1f)
        val sampled = silhouette(render(pad, 1f, field), pad, 1f)
        assertTrue(abs(closed.first - sampled.first) <= 0.75, "top edge: closed ${closed.first} vs field ${sampled.first}")
        assertTrue(abs(closed.second - sampled.second) <= 0.75, "left edge: closed ${closed.second} vs field ${sampled.second}")
    }

    @Test
    fun everyWholePixelPadPutsTheSampledFieldOnTheClosedForm() {
        // recordPad guarantees a whole number of layer pixels, odd or even. The field is stored at
        // half resolution, so an odd pad places the outline on a half texel: this is where that
        // would show as a silhouette error against the closed form.
        val report = StringBuilder()
        var worst = 0.0
        for (pad in 22..25) {
            val field = buildPathField(capsule(), width + 2 * pad, height + 2 * pad, pad.toFloat(), band)!!
            val closed = silhouette(render(pad, 1f, null), pad, 1f)
            val sampled = silhouette(render(pad, 1f, field), pad, 1f)
            val dt = sampled.first - closed.first; val dl = sampled.second - closed.second
            worst = maxOf(worst, abs(dt), abs(dl))
            report.append("pad $pad: top ${"%.2f".format(dt)} left ${"%.2f".format(dl)}; ")
        }
        assertTrue(worst <= 0.75, "sampled-field silhouette error by pad parity: $report")
    }

    @Test
    fun aFieldBuiltOnePixelOffThePadMovesTheSilhouetteByThatPixel() {
        // The diagnosed bug class: the field rasterised at ceil(padPx) = 23 while the layer, uPad
        // and draw-back used 24. The consumer-level check must see it.
        val pad = 24
        val good = buildPathField(capsule(), width + 2 * pad, height + 2 * pad, pad.toFloat(), band)!!
        val off = buildPathField(capsule(), width + 2 * (pad - 1), height + 2 * (pad - 1), (pad - 1).toFloat(), band)!!
        val a = silhouette(render(pad, 1f, good), pad, 1f)
        val b = silhouette(render(pad, 1f, off), pad, 1f)
        assertTrue(abs(a.first - b.first) >= 0.75 && abs(a.second - b.second) >= 0.75, "a one-pixel field/pad mismatch must move the silhouette: top ${a.first} vs ${b.first}, left ${a.second} vs ${b.second}")
    }

    @Test
    fun reducedScaleKeepsTheSilhouetteWithClosedFormAndSampledField() {
        // renderScale 0.5: pad 24 -> 12 layer px, band and radii halved, the field's range and
        // scale transformed as GlassUniforms.scaledBy does (range * s, scale / s).
        val pad = 24
        val field = buildPathField(capsule(), width + 2 * pad, height + 2 * pad, pad.toFloat(), band)!!
        val halfField = GlassPathField(field.bitmap, field.range * 0.5f, field.scale / 0.5f, field.layerWidth, field.layerHeight)
        val closedFull = silhouette(render(pad, 1f, null), pad, 1f)
        val closedHalf = silhouette(render(pad, 0.5f, null), pad, 0.5f)
        val fieldHalf = silhouette(render(pad, 0.5f, halfField), pad, 0.5f)
        assertTrue(abs(closedFull.first - closedHalf.first) <= 0.75 && abs(closedFull.second - closedHalf.second) <= 0.75, "closed form full $closedFull vs half $closedHalf")
        assertTrue(abs(closedFull.first - fieldHalf.first) <= 1.0 && abs(closedFull.second - fieldHalf.second) <= 1.0, "closed form full $closedFull vs sampled field at half scale $fieldHalf")
    }

    @Test
    fun heldAndFusedGeometryKeepTheSilhouetteAcrossScales() {
        val pad = 32
        val fuse = floatArrayOf(width / 2f + 120f, 0f, 60f, 40f)   // a sibling to the right of the capsule
        val fullHeld = silhouette(render(pad, 1f, null, held = 1f), pad, 1f)
        val halfHeld = silhouette(render(pad, 0.5f, null, held = 1f), pad, 0.5f)
        assertTrue(abs(fullHeld.first - halfHeld.first) <= 0.75 && abs(fullHeld.second - halfHeld.second) <= 0.75, "held: full $fullHeld vs half $halfHeld")
        val fullFused = silhouette(render(pad, 1f, null, fuse = fuse), pad, 1f)
        val halfFused = silhouette(render(pad, 0.5f, null, fuse = fuse.map { it * 0.5f }.toFloatArray()), pad, 0.5f)
        assertTrue(abs(fullFused.first - halfFused.first) <= 0.75 && abs(fullFused.second - halfFused.second) <= 0.75, "fused: full $fullFused vs half $halfFused")
    }
}
