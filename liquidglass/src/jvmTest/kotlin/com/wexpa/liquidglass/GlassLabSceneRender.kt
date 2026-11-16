package com.wexpa.liquidglass

import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test

/**
 * The lab scene of #200, opt-in: the toolbar style drawn through the production modifier at the
 * place of Safari's address pill in the 16 November lab stills (x 268-900, y 2287-2430 of a
 * 1170 x 2532 screen at 3x), over backdrops exported by the maintainer's private scorer.
 *
 * `-Pliquidglass.lab=<dir>` reads `<dir>/in/<appearance>/<name>.png` (rows 2000-2532 of the
 * screen) and writes the composite of the same crop to `<dir>/<tag>/<appearance>/<name>.png`,
 * `<tag>` from `-Pliquidglass.labTag` (default `render`). The stills stay private; only the
 * scores are published.
 */
class GlassLabSceneRender {
    @Test
    fun renderTheToolbarOverTheLabBackdrops() {
        val dir = System.getProperty("liquidglass.lab") ?: return
        val tag = System.getProperty("liquidglass.labTag") ?: "render"
        for (appearance in listOf("dark", "light")) {
            val input = File(dir, "in/$appearance")
            val output = File(dir, "$tag/$appearance").apply { mkdirs() }
            val files = input.listFiles { f -> f.name.endsWith(".png") }.orEmpty().sortedBy { it.name }
            if (files.isEmpty()) continue
            GlassLabScene(
                GlassStyle.toolbar(dark = appearance == "dark"),
                width = 1170, height = 532, pillX = 268, pillY = 2287 - 2000, pillW = 900 - 268, pillH = 2430 - 2287,
            ).use { scene ->
                for (file in files) {
                    val backdrop = GlassLabScene.pixelsOf(Image.makeFromEncoded(file.readBytes()))
                    val frame = scene.render(IntArray(backdrop.size) { backdrop[it] or (0xFF shl 24) })
                    val png = GlassLabScene.imageOf(frame, scene.width, scene.height).encodeToData(EncodedImageFormat.PNG)
                    File(output, file.name).writeBytes(checkNotNull(png).bytes)
                }
            }
        }
    }
}
