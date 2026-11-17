package com.wexpa.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Renders the scroll edge effect for the record (`review/atlas/scroll-edge-2026-11-18/`): the earlier composable
 * (`GlassScrollEdge`, authored gradient) and `Modifier.glassScrollEdge` (measured), over a page of colour tiles and
 * text scrolled up under the top edge, both appearances. Skia software frames, 2 px per dp, desktop test host.
 */
@OptIn(ExperimentalTestApi::class)
class GlassScrollEdgeRenderTest {
    private val out = File("build/reports/atlas/scroll-edge").apply { mkdirs() }

    @Composable private fun Page(dark: Boolean) {
        val ink = if (dark) Color.White else Color.Black
        val tiles = listOf(Color(255, 44, 85), Color(47, 210, 88), Color(191, 91, 242), Color(100, 211, 255))
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            for (i in 0 until 6) {
                BasicText("Heading $i: the quick brown fox", style = TextStyle(color = ink, fontSize = 18.sp))
                Box(Modifier.fillMaxWidth().height(36.dp).background(tiles[i % 4]))
                BasicText("Body text under the bar, set small so the blur is easy to read.", style = TextStyle(color = ink, fontSize = 12.sp))
            }
        }
    }

    private fun shoot(name: String, dark: Boolean, measured: Boolean) =
        runSkikoComposeUiTest(size = Size(660f, 520f), density = Density(2f), testTimeout = 5.minutes) {
            val ground = if (dark) Color.Black else Color.White
            setContent {
                Box(Modifier.fillMaxSize().background(ground)) {
                    if (measured) {
                        Box(Modifier.fillMaxSize().glassScrollEdge(ground, inverted = dark, topIntensity = { 1f })) { Page(dark) }
                    } else {
                        Page(dark)
                        GlassScrollEdge(ground, Modifier.align(Alignment.TopCenter), inverted = dark)
                    }
                }
            }
            waitForIdle()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "$name.png"))
            assertTrue(true)
        }

    @Test fun darkBefore() = shoot("before-dark", dark = true, measured = false)
    @Test fun darkAfter() = shoot("after-dark", dark = true, measured = true)
    @Test fun lightBefore() = shoot("before-light", dark = false, measured = false)
    @Test fun lightAfter() = shoot("after-light", dark = false, measured = true)
}
