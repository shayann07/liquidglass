package com.wexpa.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Real desktop Compose pointer events and production coverage/draw transforms. The flat
 * diagnostic colour isolates geometry; this does not establish optical or Android parity. */
@OptIn(ExperimentalTestApi::class, LiquidGlassDiagnosticApi::class)
class GlassSurfaceDesktopTest {
    private fun bounds(image: BufferedImage, ink: Boolean = false): List<Int> {
        var left = image.width; var top = image.height; var right = -1; var bottom = -1
        for (y in 0 until image.height) for (x in 0 until image.width) {
            val rgb = image.getRGB(x, y)
            val r = (rgb shr 16) and 255; val g = (rgb shr 8) and 255; val b = rgb and 255
            val hit = if (ink) g > 180 && r < 80 && b < 80 else r > 180 && b > 180 && g < 80
            if (hit) { left = minOf(left, x); right = maxOf(right, x); top = minOf(top, y); bottom = maxOf(bottom, y) }
        }
        assertTrue(right >= left, "No rendered ${if (ink) "foreground" else "glass"}")
        return listOf(left, top, right, bottom)
    }

    @Test fun desktopPressIsPreservedWhileOnlyLargeSurfaceDragBecomesSubtle() {
        val report = File("build/reports/astra/surface-r14").apply { mkdirs() }
        LiquidGlassDiagnostics.withDiagnostics(coverageOnly = true) {
            for ((label, dimensions) in listOf("button" to (56 to 56), "card" to (320 to 180), "pill" to (350 to 44))) {
                val (w, h) = dimensions
                runComposeUiTest {
                    setContent {
                        CompositionLocalProvider(LocalDensity provides Density(1f)) {
                            val glass = rememberLiquidGlassState(background = Color.Black, renderScale = 0.5f)
                            Box(Modifier.size(600.dp, 400.dp).testTag("scene")) {
                                Box(Modifier.fillMaxSize().liquidGlassSource(glass).background(Color.Black))
                                Box(Modifier.align(Alignment.Center).size(w.dp, h.dp).testTag("control")
                                    .liquidGlass(glass, RoundedCornerShape((minOf(w, h) / 2).dp),
                                        GlassStyle.DarkChrome.copy(blurRadius = 0.dp, wideKernel = 0.dp),
                                        interaction = GlassInteraction.Pullable)) {
                                    Box(Modifier.align(Alignment.Center).size(8.dp).background(Color.Green))
                                }
                            }
                        }
                    }
                    waitForIdle()
                    fun capture(name: String): BufferedImage = onNodeWithTag("scene").captureToImage().toAwtImage().also {
                        ImageIO.write(it, "png", File(report, "$label-$name.png"))
                    }
                    val node = onNodeWithTag("control")
                    val rest = capture("rest"); val resting = bounds(rest); val ink = bounds(rest, true)
                    node.performTouchInput { down(Offset(w / 2f, h / 2f)) }
                    waitForIdle()
                    val held = capture("press"); val pressed = bounds(held)
                    assertEquals(ink, bounds(held, true), "press moved foreground")
                    assertTrue(pressed[2] - pressed[0] >= resting[2] - resting[0] + 11, "press expansion changed")
                    for ((direction, target) in listOf("right" to Offset(2000f, h / 2f),
                        "up" to Offset(w / 2f, -2000f), "diagonal" to Offset(-2000f, -2000f))) {
                        node.performTouchInput { advanceEventTime(100); moveTo(target) }
                        waitForIdle()
                        val dragged = capture(direction); val b = bounds(dragged)
                        assertEquals(ink, bounds(dragged, true), "drag moved foreground")
                        if (label != "button") {
                            // Two edges at the 180/255 coverage threshold allow 1px raster rounding each.
                            assertTrue(abs((b[2] - b[0]) - (pressed[2] - pressed[0])) <= 4, "$label width over-stretched: $b versus $pressed")
                            assertTrue(abs((b[3] - b[1]) - (pressed[3] - pressed[1])) <= 4, "$label height over-stretched: $b versus $pressed")
                        } else if (direction == "up") {
                            assertTrue(b[3] - b[1] > pressed[3] - pressed[1] + 8, "small control lost its drag response")
                        }
                        println("DESKTOP_SURFACE $label $direction press=$pressed drag=$b foreground=${bounds(dragged, true)}")
                    }
                    // SkikoInputDispatcher.enqueueCancel is a no-op in Compose 1.12; test real up.
                    node.performTouchInput { up() }; waitForIdle()
                    val released = bounds(capture("released"))
                    resting.zip(released).forEach { (a, b) -> assertTrue(abs(a - b) <= 1, "release did not restore rest") }
                }
            }
        }
    }
}
