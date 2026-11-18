package com.wexpa.liquidglass

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The rim and contour of [GlassStyle.controlCenterTile] against the 18 November measurement of the iOS 27 Control Center toggle
 * (#206, `CC-over-lab-dark.MP4`, `.local/sxs/plans/206-measured.md` section 5, read again by sector). Drawn as a 201 px circle
 * (67 dp at 3x) over a flat layer G through the production modifier with a vertical light: the measured toggle's rim is
 * lit at its top and bottom equally and not at all at its sides, and its dark contour lies outside the outline at the sides
 * only (what `GlassStyle.outerContour` draws). No light is passed: the tile takes the overhead light by itself.
 */
class GlassControlCenterRimTest {
    private val failures = mutableListOf<String>()

    private fun check(ok: Boolean, message: String) {
        println((if (ok) "CC206R ok   " else "CC206R FAIL ") + message)
        if (!ok) failures += message
    }

    private fun toggle(style: GlassStyle) = GlassLabScene(style, pillW = 201, pillH = 201, light = GlassLight.Default)

    /** Measured sector profiles from r 96.0 in 0.5 px steps (mean RGB, median over the sector, two toggles). */
    private val measured = mapOf(
        "10-pole" to doubleArrayOf(79.0, 79.2, 84.0, 97.8, 122.0, 133.0, 152.0, 154.7, 111.0, 43.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0),
        "10-diag" to doubleArrayOf(62.0, 61.5, 62.0, 71.0, 84.0, 92.8, 94.2, 101.8, 61.0, 34.2, 6.2, 7.0, 8.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0),
        "10-side" to doubleArrayOf(60.0, 60.0, 60.0, 61.0, 60.0, 61.0, 60.5, 62.5, 44.5, 9.8, 0.0, 3.0, 7.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0),
        "56-pole" to doubleArrayOf(124.2, 124.0, 130.0, 147.5, 175.2, 189.0, 211.2, 215.2, 166.8, 92.5, 56.0, 56.0, 56.0, 56.0, 56.0, 56.0, 56.0, 56.0, 56.0, 56.0),
        "56-diag" to doubleArrayOf(103.0, 103.8, 104.5, 115.0, 130.0, 141.2, 142.5, 151.0, 105.0, 71.5, 35.5, 40.2, 47.8, 54.0, 56.0, 56.0, 56.0, 56.0, 56.0, 56.0),
        "56-side" to doubleArrayOf(102.0, 102.0, 102.0, 103.0, 102.0, 103.0, 102.2, 104.2, 83.0, 32.0, 6.8, 22.8, 41.0, 56.0, 56.0, 56.0, 56.0, 56.0, 56.0, 56.0),
        "102-pole" to doubleArrayOf(169.5, 169.5, 176.5, 195.8, 229.2, 243.5, 255.0, 255.0, 223.0, 142.2, 102.0, 102.0, 102.0, 102.0, 102.0, 102.0, 102.0, 102.0, 102.0, 102.0),
        "102-diag" to doubleArrayOf(145.0, 145.8, 145.5, 158.5, 177.0, 189.0, 190.0, 201.5, 150.0, 112.5, 68.0, 77.2, 88.2, 99.0, 102.0, 102.0, 102.0, 102.0, 102.0, 102.0),
        "102-side" to doubleArrayOf(143.0, 143.0, 143.0, 144.0, 143.0, 144.5, 143.0, 146.5, 122.8, 61.2, 24.2, 50.0, 77.8, 101.8, 102.0, 102.0, 102.0, 102.0, 102.0, 102.0),
    )

    private fun sector(kind: String, deg: Double): Boolean = when (kind) {
        "pole" -> abs(deg - 90) <= 20 || abs(deg - 270) <= 20
        "side" -> deg <= 20 || deg >= 340 || abs(deg - 180) <= 20
        else -> listOf(45.0, 135.0, 225.0, 315.0).any { abs(deg - it) <= 10 }
    }

    private fun profile(s: GlassLabScene, out: IntArray, kind: String): DoubleArray {
        val cx = s.pillX + 100
        val cy = s.pillY + 100
        val vals = List(14) { ArrayList<Double>() }
        for (y in cy - 120..cy + 120) for (x in cx - 120..cx + 120) {
            val dx = (x - cx).toDouble()
            val dy = (y - cy).toDouble()
            val r = hypot(dx, dy)
            val b = ((r - 96.0) / 0.5).toInt()
            if (r >= 96.0 && b in 0 until 14 && sector(kind, (Math.toDegrees(Math.atan2(dy, dx)) + 360) % 360)) {
                val p = out[y * s.width + x]
                vals[b] += (((p ushr 16) and 0xFF) + ((p ushr 8) and 0xFF) + (p and 0xFF)) / 3.0
            }
        }
        return DoubleArray(14) { vals[it].sorted()[vals[it].size / 2] }
    }

    private fun peak(p: DoubleArray) = (4..9).maxOf { p[it] }   // r 98.0 to 100.5
    private fun contour(p: DoubleArray) = (9..12).minOf { p[it] } // r 100.5 to 102.0

    /** The same toggle at 1x (67 px, 1 px per dp), where bevelPeak 0.3 x 1.25 dp is under half a pixel and the chamfer branch applies. */
    @Test
    fun theRimAtOneTimesDensity() {
        for (density in listOf(3f, 1f)) {
            val px = (201 * density / 3f).toInt()
            GlassLabScene(GlassStyle.controlCenterTile(), pillW = px, pillH = px, density = density).use { s ->
                for (g in listOf(10, 56, 102)) {
                    val out = s.render(IntArray(s.width * s.height) { GlassLabScene.grey(g) })
                    val cx = s.pillX + px / 2; val cy = s.pillY + px / 2; val r0 = px / 2.0
                    fun at(dx: Int, dy: Int): Int { val p = out[(cy + dy) * s.width + cx + dx]; return (((p ushr 16) and 0xFF) + ((p ushr 8) and 0xFF) + (p and 0xFF)) / 3 }
                    val reach = (r0 + 4).toInt()
                    val top = (-reach..0).map { at(0, it) }
                    val bottom = (0..reach).map { at(0, it) }
                    val right = (0..reach).map { at(it, 0) }
                    println("CC206X ${density}x G=$g: top peak ${top.max()} bottom peak ${bottom.max()} side peak ${right.max()} side contour ${right.min()} interior ${at(0, 0)}")
                }
            }
        }
    }

    @Test
    fun theRimIsLitAtTheTopAndBottomOnlyAndTheContourIsDarkAtTheSides() {
        toggle(GlassStyle.controlCenterTile()).use { scene ->
            for (g in listOf(10, 56, 102)) {
                val out = scene.render(IntArray(scene.width * scene.height) { GlassLabScene.grey(g) })
                val rows = listOf("pole", "diag", "side").associateWith { profile(scene, out, it) }
                for ((kind, ours) in rows) {
                    val ref = measured.getValue("$g-$kind")
                    println("CC206R G=$g $kind peak %.0f (measured %.0f) contour %.0f (measured %.0f) | ours ".format(peak(ours), peak(ref), contour(ours), contour(ref)) +
                        ours.joinToString(" ") { "%.0f".format(it) })
                }
                val pole = rows.getValue("pole")
                val diag = rows.getValue("diag")
                val side = rows.getValue("side")
                val refPole = measured.getValue("$g-pole")
                val refDiag = measured.getValue("$g-diag")
                // Pole peak: within 8 of the measured one (the G 102 reading clips at 255 on the device, so there it must reach 245).
                if (g < 100) check(abs(peak(pole) - peak(refPole)) <= 8.0, "G $g pole peak %.0f, measured %.0f (gate 8)".format(peak(pole), peak(refPole)))
                else check(peak(pole) >= 245.0, "G $g pole peak %.0f, measured 255 clipped (gate 245)".format(peak(pole)))
                check(abs(peak(diag) - peak(refDiag)) <= 9.0, "G $g diagonal peak %.0f, measured %.0f (gate 9)".format(peak(diag), peak(refDiag)))
                // The sides carry no lit line: at most 12 above the interior (measured +3).
                check(peak(side) - side[0] <= 12.0, "G $g side peak %.0f over an interior of %.0f (gate 12; measured +3)".format(peak(side), side[0]))
                // The contour is the sides' and the diagonals', and nothing at the poles.
                check(contour(pole) >= g - 3.0, "G $g pole contour %.0f (none: at least %d)".format(contour(pole), g - 3))
                check(contour(side) <= 0.75 * g, "G $g side contour %.0f (at most %.0f; measured %.0f)".format(contour(side), 0.75 * g, contour(measured.getValue("$g-side"))))
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("; "))
    }
}
