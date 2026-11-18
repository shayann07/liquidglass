package com.wexpa.liquidglass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

/**
 * A glass element drawn through the production modifier ([liquidGlass] over a [liquidGlassSource])
 * at 3x, over a backdrop given as pixels, so a test reads what an app would show: the style's own
 * uniforms, pad and wide kernel, not a hand-built uniform set. The default geometry is the place of
 * Safari's address pill in the 16 November lab stills (#199): 632 x 143 px, capsule, its interior
 * zones at 104-147 and 502-537 px from its left end and 38-108 px from its top.
 *
 * With [container] the same pill is the one member of a [LiquidGlassContainer] filling the scene, so
 * the merged-glass shader and its host draw the same style at the same place.
 */
internal class GlassLabScene(
    style: GlassStyle,
    val width: Int = 900,
    val height: Int = 480,
    val pillX: Int = 134,
    val pillY: Int = 168,
    val pillW: Int = 632,
    val pillH: Int = 143,
    val container: Boolean = false,
    /** Where the light comes from; the lone piece and the container's [Glass] light take it alike. */
    val light: GlassLight = GlassLight.Default,
) : AutoCloseable {
    /** The style drawn. Setting it re-draws the same scene, so a sweep over styles need not build one scene each. */
    var style by mutableStateOf(style)
    private var drawn: GlassStyle? = null

    /** The backdrop: a ground colour, then rectangles (x, y, w, h, argb) of every other colour over it. */
    private var ground by mutableStateOf(0)
    private var backdrop by mutableStateOf(IntArray(0))
    private var time = 0L
    private val scene = ImageComposeScene(width, height, Density(3f)) {
        val glass = rememberLiquidGlassState()
        Box(Modifier.fillMaxSize()) {
            // Drawn content, not a bitmap: the quarter-scale wide strip re-plays the recording at that scale, which
            // averages drawn shapes by coverage but point-samples a bitmap, aliasing its 4, 6 and 12 px periods into
            // the tone on this backend. The ground goes down first and each other colour over it, so no
            // anti-aliased seam sits over a third colour.
            Box(Modifier.fillMaxSize().liquidGlassSource(glass).drawBehind {
                drawRect(Color(ground))
                val r = backdrop
                var i = 0
                while (i < r.size) {
                    drawRect(Color(r[i + 4]), Offset(r[i].toFloat(), r[i + 1].toFloat()), Size(r[i + 2].toFloat(), r[i + 3].toFloat()))
                    i += 5
                }
            })
            val pill = Modifier.offset { IntOffset(pillX, pillY) }.size((pillW / 3f).dp, (pillH / 3f).dp)
            if (container) {
                LiquidGlassContainer(glass, Modifier.fillMaxSize(), style = this@GlassLabScene.style, light = light) {
                    Box(Modifier.fillMaxSize()) { Box(pill.glassMember(RoundedCornerShape(50))) }
                }
            } else {
                Box(pill.liquidGlass(glass, RoundedCornerShape(50), this@GlassLabScene.style, light))
            }
        }
    }

    /** The interior zones, as column ranges and a row range, in scene pixels. */
    val zoneColumns = listOf(pillX + 104 until pillX + 147, pillX + 502 until pillX + 537)
    val zoneRows = pillY + 38 until pillY + 108

    /** Renders over [argb] (opaque, row-major, [width] x [height]) and returns the frame, row-major ARGB. */
    fun render(argb: IntArray): IntArray {
        ground = argb.asList().groupingBy { it }.eachCount().maxBy { it.value }.key
        backdrop = rectsOf(argb, width, height, ground)
        // The first frame records the new backdrop; the second draws the glass over that recording. A container
        // learns where its members are only after its first layout, so it takes one frame more, and so does a new style:
        // its first frame recomposes and may change the host's recording pad.
        val warmUp = (if (container) 2 else 1) + (if (drawn != null && drawn != style) 1 else 0)
        drawn = style
        repeat(warmUp) { scene.render(time); time += 16_000_000L }
        val frame = scene.render(time); time += 16_000_000L
        return pixelsOf(frame)
    }

    override fun close() = scene.close()

    /**
     * A 30 px black stem (the stem of 110 pt bold text at 3x) on the #190 probe's off-white page at the pill's centre:
     * turning points of the row-averaged profile across the interior. A single soft stem with the detail term's own
     * halo has at most 7; main's five-tap along-axis average gave 15 (fix/190-axis-comb).
     */
    fun stemTurningPoints(): Int = turningPoints(stemProfile())

    /** The row-averaged luma across the interior over that stem, from 100 px inside the pill's left end to 100 px inside its right. */
    fun stemProfile(): DoubleArray {
        val centreX = pillX + pillW / 2
        val out = render(IntArray(width * height) { i ->
            if ((i % width) - centreX in -15..14) 0xFF000000.toInt() else 0xFFF3F0E8.toInt()
        })
        return DoubleArray(pillW - 200) { i ->
            val x = pillX + 100 + i
            zoneRows.sumOf { y -> GlassRender.luma(out[y * width + x]) } / zoneRows.count()
        }
    }

    companion object {
        fun grey(v: Int): Int = (0xFF shl 24) or (v shl 16) or (v shl 8) or v

        /** Turning points of at least [amplitude] levels (the probe of `GlassAxisDetailRenderTest`, abandoned #190 branch). */
        fun turningPoints(p: DoubleArray, amplitude: Double = 4.0): Int {
            var count = 0
            var last = p[0]
            var direction = 0
            for (v in p) {
                if (direction >= 0 && v < last - amplitude) {
                    if (direction > 0) count++
                    direction = -1; last = v
                } else if (direction <= 0 && v > last + amplitude) {
                    if (direction < 0) count++
                    direction = 1; last = v
                } else if ((direction > 0 && v > last) || (direction < 0 && v < last)) {
                    last = v
                }
            }
            return count
        }

        /** Runs of each non-[ground] colour along a row, repeated rows merged into one band: (x, y, w, h, argb). */
        fun rectsOf(argb: IntArray, w: Int, h: Int, ground: Int): IntArray {
            val out = ArrayList<Int>()
            var y0 = 0
            while (y0 < h) {
                var y1 = y0 + 1
                while (y1 < h && (0 until w).all { argb[y1 * w + it] == argb[y0 * w + it] }) y1++
                var x0 = 0
                while (x0 < w) {
                    var x1 = x0 + 1
                    while (x1 < w && argb[y0 * w + x1] == argb[y0 * w + x0]) x1++
                    if (argb[y0 * w + x0] != ground) out += listOf(x0, y0, x1 - x0, y1 - y0, argb[y0 * w + x0])
                    x0 = x1
                }
                y0 = y1
            }
            return out.toIntArray()
        }

        fun imageOf(argb: IntArray, w: Int, h: Int): Image {
            val bytes = ByteArray(w * h * 4)
            for (i in argb.indices) {
                val p = argb[i]
                bytes[i * 4] = (p and 0xFF).toByte()
                bytes[i * 4 + 1] = ((p ushr 8) and 0xFF).toByte()
                bytes[i * 4 + 2] = ((p ushr 16) and 0xFF).toByte()
                bytes[i * 4 + 3] = 0xFF.toByte()
            }
            return Image.makeRaster(ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.PREMUL), bytes, w * 4)
        }

        fun pixelsOf(image: Image): IntArray {
            val bitmap = Bitmap()
            bitmap.allocPixels(ImageInfo(image.width, image.height, ColorType.BGRA_8888, ColorAlphaType.PREMUL))
            check(image.readPixels(bitmap, 0, 0)) { "readPixels failed" }
            val bytes = checkNotNull(bitmap.readPixels())
            return IntArray(image.width * image.height) { i ->
                ((bytes[i * 4 + 3].toInt() and 0xFF) shl 24) or ((bytes[i * 4 + 2].toInt() and 0xFF) shl 16) or
                    ((bytes[i * 4 + 1].toInt() and 0xFF) shl 8) or (bytes[i * 4].toInt() and 0xFF)
            }
        }
    }
}
