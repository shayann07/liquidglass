package com.wexpa.liquidglass.material3

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.center
import androidx.compose.ui.test.down
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Switch and slider geometry and timing against the iOS 27 recordings (#205). Measured numbers are
 * asserted within the measurement's tolerance: sizes within 0.5 dp at rest and 1.5 dp held (the held
 * outline was read to +-3 px at 3 px per point and includes a soft shadow), timing within one 60 fps
 * frame. Interaction expectations (a tap toggles once, a disabled switch never shows a lens) are
 * authored. The frames are read from the real node sizes under a manual test clock.
 */
@OptIn(ExperimentalTestApi::class)
class GlassKnobTest {
    private val frame = 1000f / 60f
    private val scale = 4f

    private data class Box4(val left: Float, val top: Float, val width: Float, val height: Float) {
        val centerX get() = left + width / 2
        val centerY get() = top + height / 2
    }

    /** A node's bounds in dp, from the real layout. */
    private fun SemanticsNodeInteraction.inDp(density: Float): Box4 {
        val b = fetchSemanticsNode().boundsInRoot
        return Box4(b.left / density, b.top / density, b.width / density, b.height / density)
    }

    @Composable private fun Frame(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalDensity provides Density(scale)) {
            MaterialTheme(colorScheme = lightColorScheme()) { Box(Modifier.padding(8.dp)) { content() } }
        }
    }

    // ---- switch, measured on the native UIKit switch -------------------------------------------------

    @Test fun theSwitchTrackAndRestKnobAreTheMeasuredSize() = runDesktopComposeUiTest(400, 240, testTimeout = 2.minutes) {
        var checked by mutableStateOf(false)
        setContent { Frame { Switch(checked, { checked = it }, Modifier.testTag("sw")) } }
        waitForIdle()
        val track = onNodeWithTag("sw").inDp(scale)
        val knob = onNodeWithTag(GlassKnobTestTag, useUnmergedTree = true).inDp(scale)
        assertEquals(63f, track.width, .5f, "track width (measured 63 dp)")
        assertEquals(28f, track.height, .5f, "track height (measured 28 dp)")
        assertEquals(37f, knob.width, .5f, "knob width (measured 37 dp)")
        assertEquals(24f, knob.height, .5f, "knob height (measured 24 dp)")
        assertEquals(2f, knob.left - track.left, .5f, "inset from the track's start (measured 2 dp)")
        assertEquals(track.top + track.height / 2f, knob.top + knob.height / 2f, .5f, "knob centred vertically on the track")
        checked = true
        waitForIdle()
        val on = onNodeWithTag(GlassKnobTestTag, useUnmergedTree = true).inDp(scale)
        assertEquals(22f, on.left - knob.left, .5f, "travel (measured 22 dp)")
        assertEquals(2f, track.left + track.width - (on.left + on.width), .5f, "inset from the track's end when on (measured 2 dp)")
    }

    @Test fun aPressedKnobIsTheMeasuredLensSizeAroundTheSameCentre() = runDesktopComposeUiTest(400, 240, testTimeout = 2.minutes) {
        mainClock.autoAdvance = false
        setContent { Frame { Switch(false, {}, Modifier.testTag("sw")) } }
        mainClock.advanceTimeBy(100)
        val rest = onNodeWithTag(GlassKnobTestTag, useUnmergedTree = true).inDp(scale)
        onNodeWithTag("sw").performTouchInput { down(center) }
        mainClock.advanceTimeBy(1000)
        val held = onNodeWithTag(GlassKnobTestTag, useUnmergedTree = true).inDp(scale)
        assertEquals(64.5f, held.width, 1.5f, "held width (measured outline 64.5 dp +-1)")
        assertEquals(38.5f, held.height, 1.5f, "held height (measured outline 38.5 dp +-1)")
        assertEquals(1.75f, held.width / rest.width, .05f, "width ratio (measured 1.75)")
        assertEquals(1.6f, held.height / rest.height, .05f, "height ratio (measured 1.6)")
        assertEquals(rest.centerX, held.centerX, .5f, "the lens grows around the knob's centre")
        assertEquals(rest.centerY, held.centerY, .5f)
    }

    @Test fun pressInPeaksAbout100MsAfterTheTouch() = runDesktopComposeUiTest(400, 240, testTimeout = 2.minutes) {
        mainClock.autoAdvance = false
        setContent { Frame { Switch(false, {}, Modifier.testTag("sw")) } }
        mainClock.advanceTimeBy(100)
        onNodeWithTag("sw").performTouchInput { down(center) }
        val widths = ArrayList<Float>()
        repeat(14) {
            mainClock.advanceTimeByFrame()
            widths += onNodeWithTag(GlassKnobTestTag, useUnmergedTree = true).inDp(scale).width
        }
        // Timed from the first frame in which the knob has left rest, as the recording's 100 ms is.
        val first = widths.indexOfFirst { it > 37.01f }
        val peak = widths.indices.maxBy { widths[it] }
        val peakMs = (peak - first) * frame
        assertTrue(peakMs in 100 - frame..100 + frame, "the lens peaked ${peakMs.toInt()} ms after it first moved (measured 100 ms +-1 frame): $widths")
        val heldWidth = 64.75f
        assertTrue(widths[peak] > heldWidth, "no overshoot: the peak is ${widths[peak]} (measured a small overshoot)")
        assertTrue(widths[peak] - heldWidth < 0.25f * (heldWidth - 37f), "overshoot too large: ${widths[peak]}")
    }

    @Test fun releaseSettlesWithin133MsAndTheKnobNeverUndershoots() = runDesktopComposeUiTest(400, 240, testTimeout = 2.minutes) {
        mainClock.autoAdvance = false
        setContent { Frame { Switch(false, {}, Modifier.testTag("sw")) } }
        mainClock.advanceTimeBy(100)
        onNodeWithTag("sw").performTouchInput { down(center) }
        mainClock.advanceTimeBy(1000)
        onNodeWithTag("sw").performTouchInput { up() }
        val widths = ArrayList<Float>()
        repeat(14) {
            mainClock.advanceTimeByFrame()
            widths += onNodeWithTag(GlassKnobTestTag, useUnmergedTree = true).inDp(scale).width
        }
        val first = widths.indexOfFirst { it < 64.7f }
        val settled = widths.indexOfFirst { it <= 37.5f }
        assertTrue(settled >= 0 && (settled - first) * frame <= 133 + frame,
            "the knob took ${(settled - first) * frame} ms to settle from its first move (measured 133 ms +-1 frame): $widths")
        assertTrue(widths.all { it >= 36.5f }, "the knob shrank below its rest size: $widths")
    }

    @Test fun travelIs90PercentIn7FramesAtRestBy11AndNeverOvershoots() = runDesktopComposeUiTest(400, 240, testTimeout = 2.minutes) {
        mainClock.autoAdvance = false
        var checked by mutableStateOf(false)
        setContent { Frame { Switch(checked, { checked = it }, Modifier.testTag("sw")) } }
        mainClock.advanceTimeBy(500)
        val start = onNodeWithTag(GlassKnobTestTag, useUnmergedTree = true).inDp(scale).left
        checked = true
        val travelled = ArrayList<Float>()
        repeat(16) {
            mainClock.advanceTimeByFrame()
            travelled += onNodeWithTag(GlassKnobTestTag, useUnmergedTree = true).inDp(scale).left - start
        }
        // Counted from the last frame at rest, as the recording's 7 and 10-11 frames are.
        val rest = travelled.indexOfLast { it == 0f }
        val ninety = travelled.indexOfFirst { it >= 22f * .9f } - rest
        val atRest = travelled.indexOfFirst { it >= 22f - .5f } - rest
        assertTrue(ninety in 6..8, "90 percent after $ninety frames (measured 7 +-1): $travelled")
        assertTrue(atRest in 8..12, "at rest after $atRest frames (measured 10-11): $travelled")
        assertTrue(travelled.max() <= 22f + .5f, "overshoot ${travelled.max() - 22f} dp (measured none, gate 0.5 dp)")
    }

    @Test fun aTapTogglesOnceAndADisabledSwitchNeverGrowsALens() = runDesktopComposeUiTest(400, 240, testTimeout = 2.minutes) {
        var changes = 0
        var enabled by mutableStateOf(true)
        setContent { Frame { Switch(false, { changes++ }, Modifier.testTag("sw"), enabled = enabled) } }
        waitForIdle()
        onNodeWithTag("sw").performClick()
        waitForIdle()
        assertEquals(1, changes, "a tap must toggle once")
        enabled = false
        mainClock.autoAdvance = false
        mainClock.advanceTimeBy(300)
        onNodeWithTag("sw").performTouchInput { down(center) }
        mainClock.advanceTimeBy(1000)
        assertEquals(37f, onNodeWithTag(GlassKnobTestTag, useUnmergedTree = true).inDp(scale).width, .5f, "a disabled knob grew")
        assertEquals(1, changes)
    }

    // ---- slider, measured on Safari's web slider ----------------------------------------------------

    @Test fun theSliderThumbIs24By16AndDoesNotChangeWhileDragged() = runDesktopComposeUiTest(480, 240, testTimeout = 2.minutes) {
        mainClock.autoAdvance = false
        var value by mutableStateOf(.5f)
        setContent { Frame { Slider(value, { value = it }, Modifier.width(100.dp).testTag("sl")) } }
        mainClock.advanceTimeBy(100)
        val rest = onNodeWithTag(SliderThumbTestTag, useUnmergedTree = true).inDp(scale)
        assertEquals(24f, rest.width, 1f, "thumb width (measured 24 dp web)")
        assertEquals(16f, rest.height, 1f, "thumb height (measured 16 dp web)")
        onNodeWithTag("sl").performTouchInput { down(center) }
        mainClock.advanceTimeBy(1000)
        val held = onNodeWithTag(SliderThumbTestTag, useUnmergedTree = true).inDp(scale)
        assertEquals(1f, held.width / rest.width, .02f, "the thumb scaled while held (measured: no press scale)")
        assertEquals(1f, held.height / rest.height, .02f)
    }

    @Test fun theSliderTrackIs4DpThick() = runDesktopComposeUiTest(480, 240, testTimeout = 2.minutes) {
        setContent {
            Frame {
                val colors = SliderDefaults.colors(activeTrackColor = Color.Red, inactiveTrackColor = Color.Blue)
                Box(Modifier.background(Color.Black).width(100.dp).testTag("sl")) { Slider(.25f, {}, Modifier.width(100.dp), colors = colors) }
            }
        }
        waitForIdle()
        val pixels = onNodeWithTag("sl").captureToImage().toPixelMap()
        // A column in the unfilled part, away from the thumb: count the track's pixels.
        val x = (pixels.width * .85f).toInt()
        val thickness = (0 until pixels.height).count { y -> pixels[x, y].let { it.blue > .8f && it.red < .2f && it.green < .2f } } / scale
        assertEquals(4f, thickness, .5f, "track thickness (measured 4 dp)")
    }
}
