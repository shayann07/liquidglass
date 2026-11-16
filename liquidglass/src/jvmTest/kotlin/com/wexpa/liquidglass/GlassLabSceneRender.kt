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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
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
            val names = input.listFiles { f -> f.name.endsWith(".png") }.orEmpty().sortedBy { it.name }
            if (names.isEmpty()) continue
            var backdrop by mutableStateOf(load(names.first()))
            val style = GlassStyle.toolbar(dark = appearance == "dark")
            val scene = ImageComposeScene(WIDTH, HEIGHT, Density(3f)) {
                val glass = rememberLiquidGlassState()
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().liquidGlassSource(glass).drawBehind { drawImage(backdrop) })
                    Box(
                        Modifier.offset { IntOffset(PILL_X, PILL_Y) }
                            .size((PILL_W / 3f).dp, (PILL_H / 3f).dp)
                            .liquidGlass(glass, RoundedCornerShape(50), style),
                    )
                }
            }
            try {
                var t = 0L
                for (file in names) {
                    backdrop = load(file)
                    // Two frames: the first records the new backdrop, the second draws the glass over it.
                    scene.render(t); t += 16_000_000L
                    val frame = scene.render(t); t += 16_000_000L
                    File(output, file.name).writeBytes(checkNotNull(frame.encodeToData(EncodedImageFormat.PNG)).bytes)
                }
            } finally {
                scene.close()
            }
        }
    }

    private fun load(file: File): ImageBitmap = Image.makeFromEncoded(file.readBytes()).toComposeImageBitmap()

    private companion object {
        const val WIDTH = 1170
        const val HEIGHT = 532
        const val PILL_X = 268
        const val PILL_Y = 2287 - 2000
        const val PILL_W = 900 - 268
        const val PILL_H = 2430 - 2287
    }
}
