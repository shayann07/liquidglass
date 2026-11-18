package com.wexpa.liquidglass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Evidence for #196: a 56 dp button, a 120 x 48 dp chip and a 200 x 150 dp card, each under [GlassInteraction.Calm] (what
 * `interactive()` did before) and [GlassInteraction.Fluid] (what it does now), at rest, held still, pulled 40 dp up
 * and 100 ms after release. Desktop Skiko renders, density 1, a striped page; the frames are written to
 * `build/reports/atlas/fluid` for `review/atlas/interactive-fluid-2026-11-18`. Own renders only.
 */
@OptIn(ExperimentalTestApi::class)
class GlassFluidEvidenceTest {
    private val out = File("build/reports/atlas/fluid").apply { mkdirs() }

    private class Spec(val name: String, val w: Int, val h: Int, val shape: androidx.compose.ui.graphics.Shape)

    private val specs = listOf(
        Spec("button56", 56, 56, CircleShape),
        Spec("chip120x48", 120, 48, RoundedCornerShape(24.dp)),
        Spec("card200x150", 200, 150, RoundedCornerShape(24.dp)),
    )

    private fun frames(response: GlassInteraction, label: String) {
        for (spec in specs) runComposeUiTest(testTimeout = 4.minutes) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    LiquidGlassTheme(dark = true) {
                        Box(Modifier.size(300.dp, 300.dp).background(Color(0xFF202428)).testTag("scene")) {
                            Canvas(Modifier.size(300.dp, 300.dp)) {
                                var x = 0f
                                while (x < size.width) { drawRect(Color(0xFFE8E8E8), Offset(x, 0f), Size(10f, size.height)); x += 26f }
                            }
                            Box(Modifier.offset(((300 - spec.w) / 2).dp, 150.dp).size(spec.w.dp, spec.h.dp).testTag("control")
                                .glassEffect(Glass.Regular.interactive(response = response), spec.shape))
                        }
                    }
                }
            }
            waitForIdle()
            fun save(state: String) {
                val img = onNodeWithTag("scene").captureToImage().toAwtImage()
                ImageIO.write(img, "png", File(out, "${spec.name}-$label-$state.png"))
            }
            save("rest")
            mainClock.autoAdvance = false
            onNodeWithTag("control").performTouchInput { down(center) }
            mainClock.advanceTimeBy(600); save("held")
            onNodeWithTag("control").performTouchInput { moveBy(Offset(0f, -40f)) }
            mainClock.advanceTimeBy(600); save("pulled")
            onNodeWithTag("control").performTouchInput { up() }
            mainClock.advanceTimeBy(100); save("released-100ms")
            mainClock.autoAdvance = true
            waitForIdle()
        }
    }

    @Test fun framesBeforeAndAfter() {
        frames(GlassInteraction.Calm, "calm")
        frames(GlassInteraction.Fluid, "fluid")
        assertTrue(File(out, "button56-fluid-pulled.png").length() > 0)
    }
}
