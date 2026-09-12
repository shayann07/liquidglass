package com.wexpa.liquidglass

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Renders the measured held lens ([GlassTabBarStyle.HeldLensMeasured]) over the Phone app's tab
 * bar from the iOS 27 screen recording, at the outline the recording shows for the lens held on
 * the Calls tab (frame 1380 of LOLL8185: x 8..354, rows 32..259 of the strip that starts at frame
 * row 2230), so the capture toolchain can compare the library with the phone along the same rows
 * and columns. Opt-in like [GlassCalibrationRenderTest]: `-Dliquidglass.calibration=<dir>` with
 * `library/phone_bar_rest.png` (1170x300, the resting bar) in it; writes
 * `library/phone_bar_lens_lib.png`.
 *
 * The bar under the lens is built the way the component builds it: the resting bar grown 1.05
 * about its centre and lifted 16/255 where it is; the raw content behind it is black, so the
 * lens's raw share adds nothing here. The glyph under the lens is not grown 1.18 here, so its size is not comparable; the
 * bar's edges, the band, the seam and the tones are.
 */
class GlassTabBarLensRenderTest {

    @Test
    fun renderTheHeldLensOverThePhoneBar() {
        val dir = System.getProperty("liquidglass.calibration") ?: return
        val restFile = File(dir, "library/phone_bar_rest.png")
        if (!restFile.exists()) return
        val rest = ImageIO.read(restFile)
        val held = heldBar(rest, centreX = 479.5f, centreY = 145.5f, scale = 1.05f, lift = 16)

        val lens = GlassTabBarStyle.HeldLensMeasured
        val x0 = 8
        val y0 = 32
        val w = 346
        val h = 228
        val r = 114
        val pad = 60
        val px = 3f
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
            wide = 0f, fineShare = 1f,
            lift = lens.tintLift, liftAdapt = 0f,
            bevel = lens.bevel.value * px, specularPower = lens.specularPower,
            counterLight = lens.counterLight, edgeLight = lens.edgeLight, legibility = 0f,
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
        ImageIO.write(out, "png", File(dir, "library/phone_bar_lens_lib.png"))
        ImageIO.write(held, "png", File(dir, "library/phone_bar_held_lib.png"))
    }

    /** The resting bar as the component shows it while held: grown and lifted. */
    private fun heldBar(rest: BufferedImage, centreX: Float, centreY: Float, scale: Float, lift: Int): BufferedImage {
        val out = BufferedImage(rest.width, rest.height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until rest.height) {
            for (x in 0 until rest.width) {
                // Inverse of the growth: where this pixel came from in the resting bar.
                val sx = centreX + (x - centreX) / scale
                val sy = centreY + (y - centreY) / scale
                val p = bilinear(rest, sx, sy)
                val luma = 0.2126 * ((p ushr 16) and 0xFF) + 0.7152 * ((p ushr 8) and 0xFF) + 0.0722 * (p and 0xFF)
                val onBar = luma > 5.0
                fun ch(s: Int): Int {
                    val v = ((p ushr s) and 0xFF) + if (onBar) lift else 0
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
        val p00 = img.getRGB(x, y); val p10 = img.getRGB(x + 1, y); val p01 = img.getRGB(x, y + 1); val p11 = img.getRGB(x + 1, y + 1)
        var out = 0
        for (s in intArrayOf(16, 8, 0)) {
            val v = (ch(p00, s) * (1 - tx) + ch(p10, s) * tx) * (1 - ty) + (ch(p01, s) * (1 - tx) + ch(p11, s) * tx) * ty
            out = out or (v.toInt().coerceIn(0, 255) shl s)
        }
        return out
    }
}
