package com.wexpa.liquidglass.material3

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.wexpa.liquidglass.Glass
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Renders a `DropdownMenu` over a chart in the menu material ([GlassMaterialDefaults.menu], the default) and in the
 * card-like panel it drew before 1.1.0 ([GlassMaterialDefaults.panel]), both appearances, for the record
 * (`review/atlas/menu-panel-2026-11-18/`). Skia software frames from the desktop test host at 2 px per dp.
 */
@OptIn(ExperimentalTestApi::class)
class GlassMenuRenderTest {
    private val out = File("build/reports/atlas/menu-panel").apply { mkdirs() }

    private fun shoot(name: String, dark: Boolean, glass: Glass) = runDesktopComposeUiTest(640, 560, testTimeout = 4.minutes) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f)) {
                MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                    Scaffold(Modifier.size(320.dp, 280.dp)) { _ ->
                        Box(Modifier.fillMaxSize()) {
                            // A chart: bright colour bars, black and white stripes and a grey ramp.
                            Canvas(Modifier.fillMaxSize()) {
                                val bars = listOf(Color(0xFFE53935), Color(0xFFFFB300), Color(0xFF43A047), Color(0xFF1E88E5), Color(0xFF8E24AA), Color.White, Color.Black)
                                val w = size.width / bars.size
                                bars.forEachIndexed { i, c -> drawRect(c, Offset(i * w, 0f), Size(w + 1, size.height * .4f)) }
                                var x = 0f
                                var on = true
                                while (x < size.width) { drawRect(if (on) Color.White else Color.Black, Offset(x, size.height * .4f), Size(16f, size.height * .25f)); x += 16f; on = !on }
                                for (i in 0 until 17) drawRect(Color((i * 16).coerceAtMost(255) / 255f, (i * 16).coerceAtMost(255) / 255f, (i * 16).coerceAtMost(255) / 255f),
                                    Offset(i * size.width / 17f, size.height * .65f), Size(size.width / 17f + 1, size.height * .35f))
                            }
                            Box(Modifier.padding(start = 40.dp, top = 40.dp)) {
                                Text("Options", color = Color.White)
                                DropdownMenu(expanded = true, onDismissRequest = {}, glass = glass) {
                                    DropdownMenuItem(text = { Text("Copy") }, onClick = {})
                                    DropdownMenuItem(text = { Text("Share") }, onClick = {})
                                    DropdownMenuItem(text = { Text("Delete") }, onClick = {})
                                }
                            }
                        }
                    }
                }
            }
        }
        waitForIdle()
        val img = onRoot().captureToImage().toAwtImage()
        ImageIO.write(img, "png", File(out, "$name.png"))
        assertTrue(img.width > 0)
    }

    @Test fun lightAfter() = shoot("after-light", dark = false, glass = GlassMaterialDefaults.menu)
    @Test fun lightBefore() = shoot("before-light", dark = false, glass = GlassMaterialDefaults.panel)
    @Test fun darkAfter() = shoot("after-dark", dark = true, glass = GlassMaterialDefaults.menu)
    @Test fun darkBefore() = shoot("before-dark", dark = true, glass = GlassMaterialDefaults.panel)
}
