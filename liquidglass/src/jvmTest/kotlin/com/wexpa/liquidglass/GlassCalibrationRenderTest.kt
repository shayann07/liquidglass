package com.wexpa.liquidglass

import androidx.compose.ui.unit.dp
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Renders the measured in-app chrome over the same calibration target the iOS captures used, at
 * the same boxes, so the capture toolchain (`still_elements.py`) can measure the library exactly
 * as it measured the phone. Opt-in: set `-Dliquidglass.calibration=<dir>` to a directory holding
 * `calibration-target-light.png` and `calibration-target-dark.png` (1170x2532); the composites
 * land in `<dir>/library/`. Without the property the test does nothing, so CI is unaffected.
 *
 * The scene is the Photos chrome of the reference stills: the "Today" pill 476x132 px r66 at
 * (346,141); back and more buttons 128x132 px at (50,141) and (992,141); the toolbar capsule 480x140 r70
 * at (345,2310) with round buttons at (85,2310) and (945,2310). Everything in device px (3x).
 */
class GlassCalibrationRenderTest {

    private data class Box(val x0: Int, val y0: Int, val x1: Int, val y1: Int, val r: Int)

    private val boxes = listOf(
        // The pill and the round buttons where the phone's stills show them: the pill's rim
        // lines at y 141 and 272, its tips at x 347 and 822, the buttons 128 px wide from x 50
        // and 992 (C1a-r50, both appearances). The earlier boxes sat 19 px too low.
        Box(346, 141, 822, 273, 66), Box(50, 141, 178, 273, 66), Box(992, 141, 1120, 273, 66),
        Box(342, 2304, 829, 2448, 72), Box(84, 2304, 228, 2448, 72), Box(940, 2304, 1084, 2448, 72),
    )

    /**
     * Which boxes render with the measured resting-corner map: `-Dliquidglass.restMap=toolbar` for the
     * toolbar capsule only (the role it was measured on), `all` for every box (the pill R66 and the round
     * buttons R73 as scale-extrapolation controls), anything else for the shipping map everywhere.
     */
    private val restMapMode = System.getProperty("liquidglass.restMap") ?: "legacy"

    @Test
    fun renderTheReferenceChromeOverTheCalibrationTargets() {
        val dir = System.getProperty("liquidglass.calibration") ?: return
        val out = File(dir, "library").apply { mkdirs() }
        for (target in listOf("light", "dark")) {
            val src = ImageIO.read(File(dir, "calibration-target-$target.png"))
            for (dark in listOf(false, true)) {
                // The slider positions the iOS sweep was captured at, per appearance.
                val tints = if (dark) listOf(0f, 29f, 35f, 50f, 62f, 86f, 100f) else listOf(0f, 31f, 50f, 62f, 100f)
                for (tint in tints) {
                    val composite = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_RGB)
                    composite.graphics.drawImage(src, 0, 0, null)
                    val style = ios27Rim(GlassStyle.inApp(dark = dark, tintAmount = tint), dark)
                    val toolbar = ios27Rim(GlassStyle.toolbar(dark = dark, tintAmount = tint), dark)
                    // The fourth box is the toolbar capsule; the rest are the pill and buttons.
                    boxes.forEachIndexed { i, b ->
                        val measuredRest = restMapMode == "all" || (restMapMode == "toolbar" && i == 3)
                        drawPanel(src, composite, b, if (i == 3) toolbar else style, if (measuredRest) 1f else 0f)
                    }
                    val name = "C1a-lib%03d-%s-%s.png".format(tint.toInt(), target, if (dark) "dark" else "light")
                    ImageIO.write(composite, "png", File(out, name))
                }
            }
        }
    }

    /**
     * The rim the Material 3 module puts on every in-app style (`Ios27Rim` in `GlassMaterialTheme.kt`, copied: this module
     * cannot depend on it; `GlassRimMeasuredTest` copies it the same way). The composites measure the styles as they ship,
     * so the panels carry it, with the overhead light the renderer already uses.
     */
    private fun ios27Rim(style: GlassStyle, dark: Boolean): GlassStyle = style.copy(
        bevel = 1.6.dp, bevelPeak = 0f, specular = 0.27f, specularPower = 2.6f,
        outerContour = if (dark) 0.61f else -0.30f, outerContourFloor = if (dark) 0f else 0.13f, dispersion = 0f,
    )

    /**
     * Dark Calm's bar material in the toolbar capsule's box, over the dark target, for comparing with the
     * C1a stills (in-app glass over a known page, screenshots in display colours). Opt-in like the test
     * above; composites land in `<dir>/library/calm-bar-<preset>.png`. 2026-10-17.
     */
    @Test
    fun renderTheCalmBarOverTheDarkTarget() {
        val dir = System.getProperty("liquidglass.calibration") ?: return
        val out = File(dir, "library").apply { mkdirs() }
        val src = ImageIO.read(File(dir, "calibration-target-dark.png"))
        val presets = listOf(
            "calm" to GlassTabBarStyle.Calm(dark = true).bar,
            "calm-measured-detail" to GlassTabBarStyle.Calm(dark = true).bar.copy(softDetail = 0f),
            "calm-recorded" to GlassTabBarStyle.CalmRecordedSpace(dark = true).bar,
        )
        for ((name, style) in presets) {
            val composite = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_RGB)
            composite.graphics.drawImage(src, 0, 0, null)
            drawPanel(src, composite, boxes[3], style, 0f)
            ImageIO.write(composite, "png", File(out, "calm-bar-$name.png"))
        }
    }

    private fun drawPanel(src: BufferedImage, dst: BufferedImage, b: Box, style: GlassStyle, restMap: Float) {
        val w = b.x1 - b.x0
        val h = b.y1 - b.y0
        val pad = 60
        // Device px at 3x: dp values in the style are pt, so px = dp * 3.
        val px = 3f
        val band = GlassMaterial.BEVEL_RATIO * b.r
        val rendered = GlassRender.render(
            width = w, height = h, pad = pad,
            refractBand = band, refractDepth = 0f,
            radii = FloatArray(4) { b.r.toFloat() },
            aberration = style.dispersion,
            blur = style.blurRadius.value * px,
            specular = style.specular, fresnel = style.fresnel, highlightChroma = style.highlightChroma,
            innerShadow = style.innerShadow, edgeShadow = style.edgeShadow,
            tintAlpha = style.tint.alpha, tint = Triple(style.tint.red, style.tint.green, style.tint.blue),
            profile = 1f, formation = 0f, restMap = restMap,
            wide = style.wideKernel.value * px, fineShare = style.fineShare, softDetail = style.softDetail,
            lift = style.tintLift, liftAdapt = style.liftAdaptivity,
            detailLift = style.detailLift, toneCurve = style.toneCurve,
            outerContour = style.outerContour, outerContourFloor = style.outerContourFloor, bevelPeak = style.bevelPeak,
            bevel = style.bevel.value * px, specularPower = style.specularPower,
            counterLight = style.counterLight, edgeLight = style.edgeLight, legibility = 0f,
            backdrop = { x, y ->
                val sx = (b.x0 - pad + x).coerceIn(0, src.width - 1)
                val sy = (b.y0 - pad + y).coerceIn(0, src.height - 1)
                src.getRGB(sx, sy) and 0xFFFFFF
            },
        )
        val lw = w + pad * 2
        for (y in 0 until h + pad * 2) {
            for (x in 0 until lw) {
                val p = rendered[y * lw + x]
                val a = (p ushr 24) and 0xFF
                if (a == 0) continue
                val dx = b.x0 - pad + x
                val dy = b.y0 - pad + y
                if (dx < 0 || dy < 0 || dx >= dst.width || dy >= dst.height) continue
                // Premultiplied over the target.
                val under = dst.getRGB(dx, dy)
                fun ch(v: Int, s: Int) = (v ushr s) and 0xFF
                val inv = 255 - a
                val r = ch(p, 16) + ch(under, 16) * inv / 255
                val g = ch(p, 8) + ch(under, 8) * inv / 255
                val bl = ch(p, 0) + ch(under, 0) * inv / 255
                dst.setRGB(dx, dy, (r.coerceAtMost(255) shl 16) or (g.coerceAtMost(255) shl 8) or bl.coerceAtMost(255))
            }
        }
    }
}
