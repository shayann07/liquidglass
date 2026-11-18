package com.wexpa.liquidglass

import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [GlassStyle.controlCenterTile] and [GlassStyle.chromaGain] (#206), against the 18 November measurement of the iOS 27
 * Control Center (`CC-over-lab-dark.MP4`, 58 frames; the tables are `.local/sxs/plans/206-measured.md` sections 2, 4 and
 * 7). The tile is drawn through the production modifier over a flat page of the layer colour G and its interior read
 * back; every expected value below is the measured one, none is fitted to this renderer.
 */
class GlassControlCenterTileTest {
    private val failures = mutableListOf<String>()

    private fun check(ok: Boolean, message: String) {
        println((if (ok) "CC206 ok   " else "CC206 FAIL ") + message)
        if (!ok) failures += message
    }

    private fun page(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    /** The mean interior colour of [scene] over a flat page. */
    private fun GlassLabScene.interior(r: Int, g: Int, b: Int): DoubleArray {
        val out = render(IntArray(width * height) { page(r, g, b) })
        val zone = zoneRows.flatMap { y -> zoneColumns.flatMap { xs -> xs.map { x -> out[y * width + x] } } }
        return DoubleArray(3) { ch -> zone.map { ((it ushr (16 - 8 * ch)) and 0xFF).toDouble() }.average() }
    }

    private fun luma(c: DoubleArray) = 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]
    private fun chroma(c: DoubleArray) = c.max() - c.min()

    /** Measured: layer under the tile G, tile interior (RGB), 0-255; the eight hues of the sweep. */
    private val hues = listOf(
        "red" to (intArrayOf(110, 8, 8) to intArrayOf(206, 44, 42)),
        "pink" to (intArrayOf(105, 71, 71) to intArrayOf(164, 110, 109)),
        "green" to (intArrayOf(70, 103, 69) to intArrayOf(95, 151, 96)),
        "blue" to (intArrayOf(9, 10, 61) to intArrayOf(58, 57, 139)),
        "teal" to (intArrayOf(5, 57, 57) to intArrayOf(30, 110, 111)),
        "magenta" to (intArrayOf(110, 7, 109) to intArrayOf(201, 37, 202)),
        "yellow" to (intArrayOf(102, 103, 0) to intArrayOf(149, 149, 1)),
        "olive" to (intArrayOf(102, 103, 69) to intArrayOf(145, 145, 90)),
    )

    /** Measured: grey layer G to grey tile interior, 6 of the 46 frames (section 7, gate 1). */
    private val greys = listOf(10 to 60, 34 to 82, 56 to 102, 78 to 122, 91 to 133, 102 to 143)

    @Test
    fun unsetChromaGainLeavesEveryExistingStyleOnTheLabTransfer() {
        val styles = listOf(
            GlassStyle(), GlassStyle.Regular, GlassStyle.Clear, GlassStyle.Thick, GlassStyle.menu(), GlassStyle.shellIcon(),
            GlassStyle.dock(), GlassStyle.coverSheet(), GlassStyle.clearLens(), GlassStyle.inApp(true), GlassStyle.inApp(false),
            GlassStyle.inApp(true, 20f), GlassStyle.inApp(false, 75f), GlassStyle.toolbar(true), GlassStyle.toolbar(false, 60f),
            GlassStyle.toolbarEarlier(true), GlassStyle.toolbarEarlier(false),
        )
        for (s in styles) {
            assertTrue(s.chromaGain.isNaN(), "a style that never set chromaGain reports it unset")
            // What both hosts (liquidGlass, LiquidGlassContainer) pass as uChroma*: the pre-#206 derivation, bit for bit.
            val lift = s.detailLift.coerceIn(-1f, 1f)
            assertContentEquals(GlassMaterial.chromaOf(lift), GlassMaterial.chromaOf(lift, s.chromaGain))
        }
        // Setting it to NaN again, or copying through the primary copy, keeps the style equal (NaN compares equal in a data class).
        assertEquals(GlassStyle.Regular, GlassStyle.Regular.copy(chromaGain = Float.NaN))
        assertTrue(GlassStyle.Regular != GlassStyle.Regular.copy(chromaGain = 1.2f))
        // A set gain replaces the lab transfer on the same style and is flat, slope off, weight full.
        assertContentEquals(floatArrayOf(1.58f, 1.58f, 1.58f, 0f, 0f, 0f, 1f), GlassMaterial.chromaOf(-0.22f, 1.58f))
        assertContentEquals(floatArrayOf(4f, 4f, 4f, 0f, 0f, 0f, 1f), GlassMaterial.chromaOf(0f, 99f))
        assertContentEquals(floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 1f), GlassMaterial.chromaOf(0f, -3f))
    }

    @Test
    fun existingStylesRenderTheSameWhetherOrNotTheFieldExists() {
        // Through the production modifier over saturated colour: the style as built, and the same style with the new field
        // spelled out as unset, draw the same pixels (the field is only read through chromaOf above).
        for (style in listOf(GlassStyle.inApp(true), GlassStyle.toolbar(false), GlassStyle.shellIcon())) {
            val a = GlassLabScene(style).use { it.interior(200, 20, 30) }
            val b = GlassLabScene(style.copy(chromaGain = Float.NaN)).use { it.interior(200, 20, 30) }
            assertContentEquals(a, b)
        }
    }

    @Test
    fun anInactiveTileKeepsTheMeasuredGreyLaw() {
        val tile = GlassStyle.controlCenterTile()
        val errors = GlassLabScene(tile).use { s ->
            greys.map { (g, measured) ->
                val c = s.interior(g, g, g)
                check(abs(c[0] - c[2]) <= 1.0, "grey G $g reads grey: %.1f %.1f %.1f".format(c[0], c[1], c[2]))
                val e = luma(c) - measured
                check(abs(e) <= 2.0, "grey G $g: interior %.1f, measured $measured (error %+.2f, gate 2)".format(luma(c), e))
                e
            }
        }
        val rms = sqrt(errors.sumOf { it * it } / errors.size)
        check(rms <= 1.5, "grey rms %.2f (gate 1.5)".format(rms))
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun anInactiveTileKeepsColourAtTheMeasuredGain() {
        val tile = GlassStyle.controlCenterTile()
        val plain = tile.copy(chromaGain = Float.NaN)
        val ratios = mutableListOf<Double>()
        GlassLabScene(tile).use { s ->
            for ((name, pair) in hues) {
                val (g, measured) = pair
                val c = s.interior(g[0], g[1], g[2])
                val dy = luma(c) - luma(DoubleArray(3) { measured[it].toDouble() })
                val chromaErr = (0..2).maxOf { abs(c[it] - measured[it]) }
                val ratio = chroma(c) / chroma(DoubleArray(3) { g[it].toDouble() })
                check(abs(dy) <= 3.0, "$name: Y error %+.2f (gate 3)".format(dy))
                check(chromaErr <= 10.0, "$name: largest channel error %.1f (gate 10)".format(chromaErr))
                // The yellow page clips its blue channel at 0 on the device as here, which pulls its ratio low.
                if (name != "yellow") {
                    ratios += ratio
                    check(abs(ratio - 1.58) <= 0.10, "$name: chroma ratio %.3f (1.58 +- 0.10)".format(ratio))
                }
            }
        }
        // Without the gain the plain black tint keeps only 0.903 of the chroma: the measured tile is 1.58, so the field is what makes it.
        GlassLabScene(plain).use { s ->
            val (g, measured) = hues.first().second
            val c = s.interior(g[0], g[1], g[2])
            val err = (0..2).maxOf { abs(c[it] - measured[it]) }
            check(err > 30.0, "red without chromaGain misses the measured tile by %.0f levels".format(err))
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun anActiveTileIsAnOpaqueBodyAt250() {
        val tile = GlassStyle.controlCenterTile(active = true)
        // Measured active-tile readings over the colour phases (206-measured.md section 2 and the sweep's active column).
        // Channels at 255 are clipped on the device, so only the others are compared.
        val colours = listOf(
            intArrayOf(110, 8, 8) to intArrayOf(255, 239, 238), intArrayOf(5, 57, 57) to intArrayOf(230, 255, 255),
            intArrayOf(110, 7, 109) to intArrayOf(255, 235, 255), intArrayOf(102, 103, 0) to intArrayOf(255, 255, 202),
            intArrayOf(70, 103, 69) to intArrayOf(238, 255, 239), intArrayOf(102, 103, 69) to intArrayOf(252, 254, 234),
        )
        GlassLabScene(tile).use { s ->
            for (g in listOf(10, 56, 102)) {
                val c = s.interior(g, g, g)
                check(abs(luma(c) - 250.0) <= 3.0 && chroma(c) <= 1.0, "active over G $g: %.1f %.1f %.1f (250 +- 3)".format(c[0], c[1], c[2]))
            }
            for ((g, measured) in colours) {
                val c = s.interior(g[0], g[1], g[2])
                val err = (0..2).filter { measured[it] < 255 }.maxOf { abs(c[it].coerceAtMost(255.0) - measured[it]) }
                check(err <= 8.0, "active over (${g.joinToString()}): %.0f %.0f %.0f, measured ${measured.joinToString(" ")} (channel error %.1f, gate 8)".format(c[0], c[1], c[2], err))
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("; "))
    }

    @Test
    fun aContainerMemberCarriesTheGainLikeALonePiece() {
        val tile = GlassStyle.controlCenterTile()
        val rows = hues.map { it.first to it.second.first } + ("grey 102" to intArrayOf(102, 102, 102))
        val alone = GlassLabScene(tile).use { s -> rows.map { (_, g) -> s.interior(g[0], g[1], g[2]) } }
        val merged = GlassLabScene(tile, container = true).use { s -> rows.map { (_, g) -> s.interior(g[0], g[1], g[2]) } }
        rows.forEachIndexed { i, (name, _) ->
            val worst = (0..2).maxOf { abs(alone[i][it] - merged[i][it]) }
            // Yellow's blue is clipped at 0 on the page (measured tile: 1): the lone piece leaves 5 where the container reaches 0.
            check(worst <= (if (name == "yellow") 5.0 else 2.0), "$name: alone %.0f %.0f %.0f, container %.0f %.0f %.0f".format(
                alone[i][0], alone[i][1], alone[i][2], merged[i][0], merged[i][1], merged[i][2]))
        }
        // And the container does read the field: the same tile without it sits well apart over red.
        val plain = GlassLabScene(tile.copy(chromaGain = Float.NaN), container = true).use { it.interior(110, 8, 8) }
        check(abs(chroma(merged[0]) - chroma(plain)) > 30.0, "container red chroma %.0f with the gain, %.0f without".format(chroma(merged[0]), chroma(plain)))
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun aMorphKeepsTheGainAtBothEndsAndSnapsWhenOnlyOneSetsIt() {
        val tile = GlassStyle.controlCenterTile()
        val regular = GlassStyle.Regular
        assertEquals(1.58f, lerpGlassStyle(tile, tile, 0.5f).chromaGain)
        assertEquals(1.58f, lerpGlassStyle(tile, regular, 0f).chromaGain)
        assertTrue(lerpGlassStyle(tile, regular, 1f).chromaGain.isNaN())
        assertEquals(1.58f, lerpGlassStyle(tile, regular, 0.4f).chromaGain)
        assertTrue(lerpGlassStyle(tile, regular, 0.6f).chromaGain.isNaN())
        assertEquals(1.5f, lerpGlassStyle(tile.copy(chromaGain = 1f), tile.copy(chromaGain = 2f), 0.5f).chromaGain)
    }

    /**
     * The evidence sheet of `review/atlas/control-center-tile-2026-11-18`: opt-in (`CC206_EVIDENCE_DIR=<dir>`), one row per
     * measured layer colour G: G, the tile with [GlassStyle.chromaGain] unset (the plain black tint), the tile at 1.58, and the
     * measured tile. Drawn through the production modifier at 3x, desktop Skia, 632 x 143 px wide tile over a flat page.
     */
    @Test
    fun evidenceSheet() {
        val dir = System.getenv("CC206_EVIDENCE_DIR") ?: return
        val rows = hues.map { it.first to it.second } + listOf("grey 102" to (intArrayOf(102, 102, 102) to intArrayOf(143, 143, 143)))
        val tile = GlassStyle.controlCenterTile()
        val unset = tile.copy(chromaGain = Float.NaN)
        val cellW = 316
        val cellH = 72
        val labelW = 110
        val img = BufferedImage(labelW + cellW * 4 + 5 * 8, (rows.size + 2) * (cellH + 8) + 8 + 40, BufferedImage.TYPE_INT_ARGB)
        val g2 = img.createGraphics()
        g2.color = Color(24, 24, 26); g2.fillRect(0, 0, img.width, img.height)
        g2.font = Font("SansSerif", Font.PLAIN, 14)
        val heads = listOf("layer under (G)", "chromaGain unset", "chromaGain 1.58", "measured tile")
        heads.forEachIndexed { i, h -> g2.color = Color(220, 220, 220); g2.drawString(h, labelW + 8 + i * (cellW + 8), 24) }
        fun cell(i: Int, row: Int, argb: IntArray?, flat: Int?) {
            val x = labelW + 8 + i * (cellW + 8)
            val y = 40 + row * (cellH + 8)
            if (flat != null) { g2.color = Color(flat); g2.fillRect(x, y, cellW, cellH); return }
            // The wide tile is 632 x 143 px: shown at half size.
            val w = 900; val px = 134; val py = 168
            val crop = BufferedImage(632, 143, BufferedImage.TYPE_INT_ARGB)
            for (yy in 0 until 143) for (xx in 0 until 632) crop.setRGB(xx, yy, argb!![(py + yy) * w + px + xx])
            g2.drawImage(crop, x, y, cellW, cellH, null)
        }
        fun swatch(r: IntArray) = (0xFF shl 24) or (r[0] shl 16) or (r[1] shl 8) or r[2]
        val a = GlassLabScene(unset); val b = GlassLabScene(tile)
        a.use { sa -> b.use { sb ->
            rows.forEachIndexed { row, (name, pair) ->
                val (g, measured) = pair
                val flat = IntArray(sa.width * sa.height) { swatch(g) }
                g2.color = Color(220, 220, 220); g2.drawString(name, 8, 40 + row * (cellH + 8) + 40)
                cell(0, row, null, swatch(g))
                cell(1, row, sa.render(flat), null)
                cell(2, row, sb.render(flat), null)
                cell(3, row, null, swatch(measured))
            }
        } }
        val active = GlassStyle.controlCenterTile(active = true)
        GlassLabScene(active).use { sc ->
            val row = rows.size
            val g = intArrayOf(110, 8, 8)
            g2.color = Color(220, 220, 220); g2.drawString("active, over red", 8, 40 + row * (cellH + 8) + 40)
            cell(0, row, null, swatch(g))
            cell(2, row, sc.render(IntArray(sc.width * sc.height) { swatch(g) }), null)
            // The measured active tile over this red (the reference the scoring test uses), not the neutral 250 over greys.
            cell(3, row, null, swatch(intArrayOf(255, 239, 238)))
        }
        g2.dispose()
        File(dir).mkdirs()
        ImageIO.write(img, "png", File(dir, "control-center-tile.png"))
    }

    /** The overhead light belongs to the tile preset, not to the colour transfer: a plain style with chromaGain keeps the default light. */
    @Test
    fun theOverheadLightIsTheTilesAndSurvivesCopiesOfThePreset() {
        val tile = GlassStyle.controlCenterTile()
        val overhead = GlassLight(0f, -1f)
        assertEquals(overhead, tile.litFrom(GlassLight.Default))
        assertEquals(overhead, GlassStyle.controlCenterTile(active = true).litFrom(GlassLight.Default))
        assertEquals(GlassLight(0.3f, -0.9f), tile.litFrom(GlassLight(0.3f, -0.9f)), "a host's own light wins")
        assertEquals(GlassLight.Default, GlassStyle.Regular.copy(chromaGain = 1.5f).litFrom(GlassLight.Default), "chromaGain alone does not light from above")
        assertEquals(GlassLight.Default, tile.copy(chromaGain = 1.5f).litFrom(GlassLight.Default), "copy is a new style, unmarked")
        assertEquals(overhead, Glass(tile).tint(androidx.compose.ui.graphics.Color.Red).style(false)!!.litFrom(GlassLight.Default), "a tinted tile keeps its light")
        assertEquals(overhead, lerpGlassStyle(tile, GlassStyle.Regular, 0.25f).litFrom(GlassLight.Default))
        assertEquals(GlassLight.Default, lerpGlassStyle(tile, GlassStyle.Regular, 0.75f).litFrom(GlassLight.Default))
    }
}
