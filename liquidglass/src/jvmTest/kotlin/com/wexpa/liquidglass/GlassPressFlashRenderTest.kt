package com.wexpa.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalTestApi::class)
class GlassPressFlashRenderTest {
    /** #125: a Free (search) button dims its foreground for a frame on press, then recovers to the
     * level a held press without the flash shows. The flash marker is observable state, so it must
     * not stay dim (the intArray marker did). An AreaPreserving button is the no-flash control.
     * Measured on 1 Nov: 1.0 at rest; Free .42 at +16 ms, .70 by +64 ms; the control .70 throughout. */
    @Test fun aPressFlashRecoversToTheHeldLevel() = runComposeUiTest(testTimeout = 3.minutes) {
        fun levels(shape: GlassPullShape): List<Float> {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    LiquidGlassTheme(dark = true) {
                        Box(Modifier.size(120.dp).background(Color.Black).testTag("flash-scene")) {
                            Box(Modifier.offset(20.dp, 20.dp).size(80.dp)
                                .glassEffect(Glass.Regular.interactive(response = GlassInteraction.Pullable.copy(
                                    pullShape = shape)), CircleShape)
                                .testTag("flash-button").clickable {}) {
                                Box(Modifier.align(Alignment.Center).size(20.dp).background(Color.White))
                            }
                        }
                    }
                }
            }
            waitForIdle()
            fun level() = onNodeWithTag("flash-scene").captureToImage().toPixelMap()[60, 60].red
            val out = mutableListOf(level())
            mainClock.autoAdvance = false
            onNodeWithTag("flash-button").performTouchInput { down(center) }
            mainClock.advanceTimeBy(16); out += level()
            mainClock.advanceTimeBy(200); out += level()
            mainClock.autoAdvance = true
            onNodeWithTag("flash-button").performTouchInput { up() }
            waitForIdle()
            return out
        }
        val flash = levels(GlassPullShape.Free)
        val control = levels(GlassPullShape.AreaPreserving)
        assertTrue(flash[0] > .95f, "resting foreground must be visible: $flash")
        assertTrue(flash[1] < control[1] - .15f, "no press flash at +16 ms: $flash vs $control")
        assertEquals(control[2], flash[2], .03f, "the held glyph did not recover from the flash: $flash vs $control")
    }
}
