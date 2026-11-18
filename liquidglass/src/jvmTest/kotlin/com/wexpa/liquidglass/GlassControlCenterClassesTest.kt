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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The Control Center element classes of #206 against `CC-over-lab-dark.MP4` (18 November 2026): the round toggle, the wide
 * tile, the media tile and the tall slider track, each drawn at its measured size through the production modifier over a flat
 * layer G, plus the slider's opaque fill. The measured interiors (0-255, median of the take's patches over 46 grey frames,
 * `206-measured.md` sections 2 and 8) are in [measured].
 */
class GlassControlCenterClassesTest {
    private val failures = mutableListOf<String>()

    private fun check(ok: Boolean, message: String) {
        println((if (ok) "CC206C ok   " else "CC206C FAIL ") + message)
        if (!ok) failures += message
    }

    /** One glass layer at its measured size (px at 3x) over the flat page; later layers draw on top. */
    private class Layer(val style: GlassStyle, val shape: Shape, val x: Int, val y: Int, val w: Int, val h: Int, val clip: Shape? = null)

    private val width = 600
    private val height = 720

    private fun render(g: Int, layers: List<Layer>): IntArray {
        var ground by mutableStateOf(0)
        ground = GlassLabScene.grey(g)
        val scene = ImageComposeScene(width, height, Density(3f)) {
            val glass = rememberLiquidGlassState()
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().liquidGlassSource(glass).drawBehind { drawRect(Color(ground)) })
                for (l in layers) {
                    var m = Modifier.offset { IntOffset(l.x, l.y) }.size((l.w / 3f).dp, (l.h / 3f).dp)
                    if (l.clip != null) m = m.clip(l.clip)
                    Box(m.liquidGlass(glass, l.shape, l.style))
                }
            }
        }
        try {
            repeat(3) { n -> scene.render(n * 16_000_000L) }
            return GlassLabScene.pixelsOf(scene.render(3 * 16_000_000L))
        } finally {
            scene.close()
        }
    }

    private fun IntArray.luma(x: Int, y: Int): Double {
        val p = this[y * width + x]
        return (((p ushr 16) and 0xFF) + ((p ushr 8) and 0xFF) + (p and 0xFF)) / 3.0
    }

    private fun IntArray.patch(cx: Int, cy: Int, r: Int = 10) =
        (-r..r).flatMap { dy -> (-r..r).map { dx -> luma(cx + dx, cy + dy) } }.average()

    private val greys = listOf(10, 56, 102)
    private val measured = mapOf(
        "round toggle" to listOf(60.0, 102.0, 143.0),
        "wide tile" to listOf(61.0, 103.0, 144.0),
        "media tile" to listOf(60.0, 102.0, 143.0),
        "slider track" to listOf(61.0, 103.0, 145.0),
    )

    @Test
    fun everyClassMatchesItsMeasuredInterior() {
        val tile = GlassStyle.controlCenterTile()
        val classes = listOf(
            "round toggle" to Layer(tile, RoundedCornerShape(50), 100, 100, 201, 201),
            "wide tile" to Layer(tile, RoundedCornerShape(50), 100, 100, 448, 201),
            "media tile" to Layer(tile, RoundedCornerShape(29.6.dp), 100, 100, 448, 448),
            "slider track" to Layer(tile, RoundedCornerShape(50), 100, 100, 201, 448),
        )
        val errors = mutableListOf<Double>()
        for ((name, layer) in classes) {
            val cx = layer.x + layer.w / 2
            val cy = layer.y + layer.h / 2
            greys.forEachIndexed { i, g ->
                val v = render(g, listOf(layer)).patch(cx, cy)
                val e = v - measured.getValue(name)[i]
                errors += e
                check(abs(e) <= 2.0, "$name over G $g: %.1f, measured %.0f (error %+.1f, gate 2)".format(v, measured.getValue(name)[i], e))
            }
        }
        val rms = sqrt(errors.sumOf { it * it } / errors.size)
        check(rms <= 1.5, "rms over four classes and three greys %.2f (gate 1.5; the issue asks for 5)".format(rms))
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun theInteriorIsFlatFromTheCentreOutToTheRim() {
        // No ring term: the measured interior (median over all angles) varies at most 1 level between r 55 and 92 px (red and green phases).
        val layer = Layer(GlassStyle.controlCenterTile(), RoundedCornerShape(50), 100, 100, 201, 201)
        for (g in greys) {
            val out = render(g, listOf(layer))
            val cx = 200
            val cy = 200
            val bands = listOf(0.0 to 30.0, 55.0 to 70.0, 70.0 to 85.0).map { (a, b) ->
                val v = ArrayList<Double>()
                for (y in cy - 95..cy + 95) for (x in cx - 95..cx + 95) {
                    val r = hypot((x - cx).toDouble(), (y - cy).toDouble())
                    if (r >= a && r < b) v += out.luma(x, y)
                }
                v.sorted()[v.size / 2]
            }
            val spread = bands.max() - bands.min()
            check(spread <= 2.0, "G $g: interior by radius %s spread %.1f (gate 2)".format(bands.joinToString { "%.1f".format(it) }, spread))
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun aSliderIsATrackTileWithAnOpaqueFillOfTheActiveTile() {
        // The take's fill reads 250, 251, 252 over G 10, 56, 102 and steps from the track to the fill in one pixel:
        // the fill is the active tile, clipped to the track's pill.
        val track = Layer(GlassStyle.controlCenterTile(), RoundedCornerShape(50), 100, 100, 201, 448)
        val fill = Layer(GlassStyle.controlCenterTile(active = true), RectangleShape, 100, 300, 201, 248, clip = RoundedCornerShape(50))
        val fillMeasured = listOf(250.0, 251.0, 252.0)
        greys.forEachIndexed { i, g ->
            val out = render(g, listOf(track, fill))
            val t = out.patch(200, 200)
            val f = out.patch(200, 420)
            val want = measured.getValue("slider track")[i]
            check(abs(t - want) <= 2.0, "slider track over G $g: %.1f, measured %.0f (gate 2)".format(t, want))
            check(abs(f - fillMeasured[i]) <= 3.0, "slider fill over G $g: %.1f, measured %.0f (gate 3)".format(f, fillMeasured[i]))
            val transit = (285..315).count { out.luma(200, it) > t + 3 && out.luma(200, it) < f - 3 }
            check(transit <= 3, "slider edge over G $g: $transit rows between the plateaus (gate 3)")
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }
}
