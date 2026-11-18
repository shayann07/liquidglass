package com.wexpa.liquidglass.material3

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Renders the glass Switch and Slider at rest and held over a colourful ground, for the record
 * (`review/atlas/switch-slider-2026-11-18/`). The label of the run ("before" or "after") comes from
 * the ATLAS_LABEL environment variable. Frames are Skia software renders at 3 px per dp from the
 * desktop test host, not device captures.
 */
@OptIn(ExperimentalTestApi::class)
class GlassKnobRenderTest {
    private val out = File("build/reports/atlas/switch-slider").apply { mkdirs() }
    private val label = System.getenv("ATLAS_LABEL") ?: "after"

    @Composable private fun Row2(content: @Composable () -> Unit) =
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) { content() }

    private fun shoot(name: String, dark: Boolean, hold: String?) = runDesktopComposeUiTest(900, 330, testTimeout = 4.minutes) {
        mainClock.autoAdvance = false
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(3f)) {
                MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                    Scaffold(Modifier.size(300.dp, 110.dp)) { _ ->
                        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xFFE35D6A), Color(0xFFF2B33D), Color(0xFF3CC47C), Color(0xFF4C8DF6))))) {
                            Column(Modifier.align(Alignment.Center), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Row2 {
                                    Switch(false, {}, Modifier.testTag("off"))
                                    Switch(true, {}, Modifier.testTag("on"))
                                    Switch(true, {}, Modifier.testTag("held"))
                                }
                                Slider(.55f, {}, Modifier.padding(horizontal = 20.dp).fillMaxWidth().testTag("slider"))
                            }
                        }
                    }
                }
            }
        }
        mainClock.advanceTimeBy(600)
        if (hold != null) onNodeWithTag(hold).performTouchInput { down(center) }
        mainClock.advanceTimeBy(600)
        val img = onRoot().captureToImage().toAwtImage()
        ImageIO.write(img, "png", File(out, "$label-$name.png"))
        assertTrue(img.width > 0)
    }

    @Test fun lightRest() = shoot("light-rest", dark = false, hold = null)
    @Test fun lightSwitchHeld() = shoot("light-switch-held", dark = false, hold = "held")
    @Test fun lightSliderHeld() = shoot("light-slider-held", dark = false, hold = "slider")
    @Test fun darkRest() = shoot("dark-rest", dark = true, hold = null)
    @Test fun darkSwitchHeld() = shoot("dark-switch-held", dark = true, hold = "held")
}
