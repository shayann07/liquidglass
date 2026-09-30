package com.wexpa.liquidglass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * FABLE run 2, second pass: renders the **V3** held lens over the Phone app's tab bar from the
 * iOS 27 recording (the same bar, outline and compositing as [GlassTabBarLensRenderTest]) for a
 * small grid of rim settings, so the compressed-exterior rim and its colour split can be set
 * against the iOS stills (6717's rim zoom) on the JVM before a device build. Opt-in:
 * `-Pliquidglass.calibration=<dir>` (the calibration property, forwarded by the build) with `library/phone_bar_rest.png` in it; writes
 * `library/v3_lens_d<dispersion>_b<blur>.png` for every variant plus the lifted bar.
 */
class GlassTabBarLensVariantsRenderTest {

    @Test
    fun renderTheV3LensRimVariantsOverThePhoneBar() {
        val dir = System.getProperty("liquidglass.calibration") ?: return
        val restFile = File(dir, "library/phone_bar_rest.png")
        if (!restFile.exists()) return
        val rest = ImageIO.read(restFile)
        val held = heldBar(rest, centreX = 479.5f, centreY = 145.5f, scale = 1.05f, lift = 16)
        ImageIO.write(held, "png", File(dir, "library/v3_bar_held.png"))

        val base = GlassTabBarStyle.V3(dark = true).lens
        // Second grid (FABLE r13): the interior haze and the rim, after the r10 optics audit.
        // Each entry: name, and a transform of the V3 lens.
        val variants: List<Pair<String, (GlassStyle) -> GlassStyle>> = listOf(
            "current" to { it },
            "pill_rest" to { GlassTabBarStyle.V3(dark = true).pill },
            "clean_nomag" to { it.copy(rawShare = 0.03f, legibility = 0f, tint = Color.White.copy(alpha = 0f), heldMagnification = 0.12f, dispersion = 0.05f, counterLight = 1f, edgeShadow = 0.12f) },
            "rim_s15_b2_g0" to { it.copy(rawShare = 0.03f, legibility = 0f, tint = Color.White.copy(alpha = 0f), heldMagnification = 0.12f, dispersion = 0.05f, counterLight = 1f, edgeShadow = 0.12f, bevel = 2.dp, specular = 0.15f, heldGlow = 0f) },
            "rim_s18_b2_g0" to { it.copy(rawShare = 0.03f, legibility = 0f, tint = Color.White.copy(alpha = 0f), heldMagnification = 0.12f, dispersion = 0.05f, counterLight = 1f, edgeShadow = 0.12f, bevel = 2.dp, specular = 0.18f, heldGlow = 0f) },
            "rim_s18_b2_g25" to { it.copy(rawShare = 0.03f, legibility = 0f, tint = Color.White.copy(alpha = 0f), heldMagnification = 0.12f, dispersion = 0.05f, counterLight = 1f, edgeShadow = 0.12f, bevel = 2.dp, specular = 0.18f, heldGlow = 0.25f) },
            "rim_s22_b2_g0" to { it.copy(rawShare = 0.03f, legibility = 0f, tint = Color.White.copy(alpha = 0f), heldMagnification = 0.12f, dispersion = 0.05f, counterLight = 1f, edgeShadow = 0.12f, bevel = 2.dp, specular = 0.22f, heldGlow = 0f) },
            "rim_s18_b15_g0" to { it.copy(rawShare = 0.03f, legibility = 0f, tint = Color.White.copy(alpha = 0f), heldMagnification = 0.12f, dispersion = 0.05f, counterLight = 1f, edgeShadow = 0.12f, bevel = 1.5.dp, specular = 0.18f, heldGlow = 0f) },
        )
        val x0 = 8
        val y0 = 32
        val w = 346
        val h = 228
        val r = 114
        val pad = 60
        val px = 3f
        for ((name, transform) in variants) {
            val lens = transform(base)
            val rendered = GlassRender.render(
                width = w, height = h, pad = pad,
                refractBand = GlassMaterial.BEVEL_RATIO * r, refractDepth = 0f,
                radii = FloatArray(4) { r.toFloat() },
                aberration = lens.dispersion,
                blur = lens.blurRadius.value * px,
                specular = lens.specular, fresnel = lens.fresnel, highlightChroma = lens.highlightChroma,
                innerShadow = lens.innerShadow, edgeShadow = lens.edgeShadow,
                tintAlpha = lens.tint.alpha, tint = Triple(lens.tint.red, lens.tint.green, lens.tint.blue),
                profile = lens.profile.uniform, formation = 0f, heldLens = lens.heldLens,
                heldMagnification = lens.heldMagnification,
                heldGlow = lens.heldGlow,
                wide = 0f, fineShare = 1f,
                lift = lens.tintLift, liftAdapt = 0f,
                bevel = lens.bevel.value * px, specularPower = lens.specularPower,
                counterLight = lens.counterLight, edgeLight = lens.edgeLight, legibility = lens.legibility,
                backdrop = { x, y ->
                    val sx = (x0 - pad + x).coerceIn(0, held.width - 1)
                    val sy = (y0 - pad + y).coerceIn(0, held.height - 1)
                    held.getRGB(sx, sy) and 0xFFFFFF
                },
            )
            val out = BufferedImage(held.width, held.height, BufferedImage.TYPE_INT_RGB)
            out.graphics.drawImage(held, 0, 0, null)
            val lw = w + pad * 2
            for (y in 0 until h + pad * 2) {
                for (x in 0 until lw) {
                    val p = rendered[y * lw + x]
                    val a = (p ushr 24) and 0xFF
                    if (a == 0) continue
                    val dx = x0 - pad + x
                    val dy = y0 - pad + y
                    if (dx < 0 || dy < 0 || dx >= out.width || dy >= out.height) continue
                    val under = out.getRGB(dx, dy)
                    fun ch(v: Int, s: Int) = (v ushr s) and 0xFF
                    val inv = 255 - a
                    val rr = ch(p, 16) + ch(under, 16) * inv / 255
                    val gg = ch(p, 8) + ch(under, 8) * inv / 255
                    val bb = ch(p, 0) + ch(under, 0) * inv / 255
                    out.setRGB(dx, dy, (rr.coerceAtMost(255) shl 16) or (gg.coerceAtMost(255) shl 8) or bb.coerceAtMost(255))
                }
            }
            ImageIO.write(out, "png", File(dir, "library/v3_lens_$name.png"))
            println("LENS-VARIANT $name written")
        }
    }

    /** The resting bar as the component shows it while held: grown and lifted. */
    private fun heldBar(rest: BufferedImage, centreX: Float, centreY: Float, scale: Float, lift: Int): BufferedImage {
        val out = BufferedImage(rest.width, rest.height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until rest.height) {
            for (x in 0 until rest.width) {
                val sx = centreX + (x - centreX) / scale
                val sy = centreY + (y - centreY) / scale
                val p = bilinear(rest, sx, sy)
                fun ch(s: Int): Int {
                    val v = ((p ushr s) and 0xFF) + lift
                    return v.coerceIn(0, 255)
                }
                out.setRGB(x, y, (ch(16) shl 16) or (ch(8) shl 8) or ch(0))
            }
        }
        return out
    }

    private fun bilinear(img: BufferedImage, fx: Float, fy: Float): Int {
        val x = fx.toInt().coerceIn(0, img.width - 2)
        val y = fy.toInt().coerceIn(0, img.height - 2)
        val tx = (fx - x).coerceIn(0f, 1f)
        val ty = (fy - y).coerceIn(0f, 1f)
        fun ch(p: Int, s: Int) = ((p ushr s) and 0xFF).toFloat()
        val p00 = img.getRGB(x, y)
        val p10 = img.getRGB(x + 1, y)
        val p01 = img.getRGB(x, y + 1)
        val p11 = img.getRGB(x + 1, y + 1)
        var out = 0
        for (s in intArrayOf(16, 8, 0)) {
            val v = ch(p00, s) * (1 - tx) * (1 - ty) + ch(p10, s) * tx * (1 - ty) +
                ch(p01, s) * (1 - tx) * ty + ch(p11, s) * tx * ty
            out = out or (v.toInt().coerceIn(0, 255) shl s)
        }
        return out
    }
}
