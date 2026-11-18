package com.wexpa.liquidglass

import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Pins the rim and outline of #202 and #109 against iOS 27 (measured; see review/atlas/rim-2026-11-18).
 *
 * - The iOS 27 rim (Material 3's `Ios27Rim` on the toolbar, Tint 50, overhead light) over flat greys: rim excess over the
 *   interior at d = 0..3 px inside the outline, against the Safari lab pill (Antigravity m97/m202, measured). The
 *   tolerance (4 levels) is authored.
 * - Merged glass draws the same rim as a lone piece (it lacked the resting glow before #202).
 * - The dark Calm bar's round button: its rim's angular shape, a sharp glint on a broad tail (#109, IMG_6756 34.2 s:
 *   75 deg / 90 deg = 0.64, 45 deg / 90 deg = 0.28 of the excess over the interior). The bands are authored.
 */
class GlassRimMeasuredTest {
    private val overhead = GlassLight(0f, -1f)

    // liquidglass-material3's Ios27Rim (GlassMaterialTheme.kt), copied: this module cannot depend on material3.
    private fun ios27Rim(dark: Boolean) = GlassStyle.toolbar(dark, 50f).copy(
        bevel = 1.6.dp, bevelPeak = 0f, specular = 0.27f, specularPower = 2.6f,
        outerContour = if (dark) 0.61f else -0.30f, outerContourFloor = if (dark) 0f else 0.13f, dispersion = 0f,
    )

    /** Rim excess at d = 0..12 along the pill's top run, over flat grey [g]. */
    private fun topExcess(dark: Boolean, g: Int, container: Boolean): List<Double> =
        GlassLabScene(ios27Rim(dark), width = 900, height = 480, pillX = 134, pillY = 168, pillW = 632, pillH = 144,
            light = overhead, container = container).use { s ->
            val out = s.render(IntArray(s.width * s.height) { GlassLabScene.grey(g) })
            val xs = s.pillX + 250 until s.pillX + 382
            val row = { y: Int -> xs.map { x -> GlassRender.luma(out[y * s.width + x]) }.average() }
            val interior = row(s.pillY + 72)
            (0..12).map { d -> row(s.pillY + d) - interior }
        }

    @Test
    fun theIos27RimFallsOffAsIosDoes() {
        // iOS, d = 0..3 (measured, Safari lab pill).
        val ios = mapOf(
            Pair(true, 0) to listOf(67, 47, 31, 13), Pair(true, 128) to listOf(66, 46, 31, 12),
            Pair(true, 255) to listOf(64, 45, 30, 12), Pair(false, 0) to listOf(65, 46, 30, 12),
            Pair(false, 128) to listOf(58, 45, 30, 12),
        )
        for ((key, want) in ios) {
            val (dark, g) = key
            val got = topExcess(dark, g, container = false)
            println("rim ${if (dark) "dark" else "light"} grey $g d0..3 ours " + got.take(4).joinToString(" ") { "%+.0f".format(it) } +
                " | iOS " + want.joinToString(" ") { "%+d".format(it) })
            for (d in 0..3) assertTrue(abs(got[d] - want[d]) <= 4.0,
                "${if (dark) "dark" else "light"} grey $g d=$d: ours %.1f, iOS %d".format(got[d], want[d]))
        }
    }

    @Test
    fun mergedGlassDrawsTheRimOfALonePiece() {
        for (dark in listOf(true, false)) {
            val lone = topExcess(dark, 128, container = false)
            val merged = topExcess(dark, 128, container = true)
            for (d in 0..12) assertTrue(abs(lone[d] - merged[d]) <= 1.0,
                "${if (dark) "dark" else "light"} d=$d: lone %.1f, merged %.1f".format(lone[d], merged[d]))
        }
    }

    @Test
    fun theDarkCalmBarsRoundButtonGlintsOnABroadTail() {
        val style = GlassTabBarStyle.Calm(dark = true).bar
        GlassLabScene(style, width = 480, height = 480, pillX = 168, pillY = 168, pillW = 144, pillH = 144, light = overhead).use { s ->
            val out = s.render(IntArray(s.width * s.height) { GlassLabScene.grey(0) })
            val cx = s.pillX + s.pillW / 2.0
            val cy = s.pillY + s.pillH / 2.0
            val lum = { x: Double, y: Double -> GlassRender.luma(out[y.roundToInt() * s.width + x.roundToInt()]) }
            val interior = lum(cx, cy + 30.0)
            val peak = { deg: Double ->
                val t = Math.toRadians(deg)
                var best = 0.0
                var r = 58.0
                while (r <= 72.0) { best = max(best, lum(cx + cos(t) * r, cy - sin(t) * r)); r += 0.25 }
                best - interior
            }
            val p90 = peak(90.0)
            val r75 = peak(75.0) / p90
            val r45 = peak(45.0) / p90
            println("calm button excess at 90 deg %.1f, 75/90 %.2f (iOS 0.64), 45/90 %.2f (iOS 0.28)".format(p90, r75, r45))
            assertTrue(r75 in 0.52..0.76, "75/90 = %.2f, iOS 0.64".format(r75))
            assertTrue(r45 in 0.20..0.42, "45/90 = %.2f, iOS 0.28".format(r45))
        }
    }
}
