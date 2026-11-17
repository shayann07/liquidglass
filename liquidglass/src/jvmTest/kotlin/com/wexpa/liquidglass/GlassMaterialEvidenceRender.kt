package com.wexpa.liquidglass

import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * Evidence for #200, opt-in: the toolbar style drawn through the production modifier over a synthetic page (no
 * recorded data): bars of 2 px, stripes of 4, 6 and 12 px, a 30 px stem and a 10 px checker, dark and light, at
 * Tint 0, 50 and 100. `-Pliquidglass.evidence=<dir>` writes `<dir>/<appearance>-tint<t>.png` (900 x 480 at 3x).
 */
class GlassMaterialEvidenceRender {
    private fun page(width: Int, height: Int, dark: Boolean): IntArray {
        val ground = if (dark) 0 else 255
        val ink = 255 - ground
        return IntArray(width * height) { i ->
            val x = i % width
            val y = i / width
            val v = when {
                y < 40 || y > height - 40 -> ground
                x < 90 -> ground
                x < 200 -> if (x % 24 < 2) ink else ground
                x < 290 -> if ((x / 2) % 2 == 0) ink else ground
                x < 380 -> if ((x / 3) % 2 == 0) ink else ground
                x < 470 -> if ((x / 6) % 2 == 0) ink else ground
                x < 560 -> if (x in 500 until 530) ink else ground
                x < 650 -> if ((x / 5 + y / 5) % 2 == 0) 64 else 192
                x < 740 -> if (x < 695) ink else ground
                else -> ground
            }
            GlassLabScene.grey(v)
        }
    }

    @Test
    fun renderTheToolbarOverASyntheticPage() {
        val dir = System.getProperty("liquidglass.evidence")?.let(::File) ?: return
        dir.mkdirs()
        for (dark in listOf(true, false)) for (tint in listOf(0f, 50f, 100f)) {
            GlassLabScene(GlassStyle.toolbar(dark = dark, tintAmount = tint)).use { s ->
                val out = s.render(page(s.width, s.height, dark))
                val image = GlassLabScene.imageOf(out, s.width, s.height)
                val name = "${if (dark) "dark" else "light"}-tint${tint.toInt()}.png"
                File(dir, name).writeBytes(checkNotNull(image.encodeToData(EncodedImageFormat.PNG)).bytes)
            }
        }
    }
}
