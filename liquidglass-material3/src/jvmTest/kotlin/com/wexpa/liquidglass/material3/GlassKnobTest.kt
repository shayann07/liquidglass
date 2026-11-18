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
        assertEquals(57.7f, held.width, 1f, "held width (measured 57.7 dp, was 64.5 read off the tap peak)")
        assertEquals(38.5f, held.height, 1.5f, "held height (measured outline 38.5 dp +-1)")
        assertEquals(1.56f, held.width / rest.width, .03f, "width ratio (measured 1.56, was 1.75)")
        assertEquals(1.6f, held.height / rest.height, .05f, "height ratio (measured 1.6)")
        assertEquals(rest.centerX, held.centerX, .5f, "the lens grows around the knob's centre")
        assertEquals(rest.centerY, held.centerY, .5f)
    }

    /**
     * The recording shows the lens only where it is taller than the 28 dp track, so press and release are timed
     * on that visible part: the lens outline went 85 -> 121 px (28.3 -> 40.3 dp) in 6 frames (peak 100 ms in,
     * 5 frames after the first frame clearly above the track), then settled at 115 px (38.3 dp).
     */
    @Test fun pressInPeaksAbout100MsAfterTheLensFirstClearsTheTrack() = runDesktopComposeUiTest(400, 240, testTimeout = 2.minutes) {
        mainClock.autoAdvance = false
        setContent { Frame { Switch(false, {}, Modifier.testTag("sw")) } }
        mainClock.advanceTimeBy(100)
        onNodeWithTag("sw").performTouchInput { down(center) }
        val heights = ArrayList<Float>()
        val widths = ArrayList<Float>()
        repeat(16) {
            mainClock.advanceTimeByFrame()
            val knob = onNodeWithTag(GlassKnobTestTag, useUnmergedTree = true).inDp(scale)
            heights += knob.height
            widths += knob.width
        }
        val first = heights.indexOfFirst { it > 28.5f }
        val peak = heights.indices.maxBy { heights[it] }
        val peakFrames = peak - first
        // The PressSpring (zeta .55, omega 25) stepped at 16 ms and quantised to whole pixels at 4x puts the
        // peak 6 frames after the first frame above 28.5 dp, for either alignment of the first animation frame.
        // The recording has 5 +- 1, so the window is that +-1 frame (the old 100 ms +-1 frame bound), not wider.
        assertTrue(peakFrames in 4..6, "the lens peaked $peakFrames frames after clearing the track (measured 5 +-1, spring gives 6): $heights")
        val held = 38.4f
        assertTrue(heights[peak] > held, "no overshoot: the peak is ${heights[peak]} (measured 40.3 dp over a 38.3 dp plateau)")
        // The spring's overshoot is exp(-pi zeta / sqrt(1 - zeta^2)) = 13 percent of the 14.4 dp growth = 1.9 dp
        // (peak 40.3 dp measured, 40.0 stepped); a bound of 3 dp is the old "under a quarter of the growth" cap
        // made to the measured value, and fails a spring with half the damping (about 5.9 dp).
        assertTrue(heights[peak] - held < 3f, "overshoot too large: the peak is ${heights[peak]} over the $held dp plateau: $heights")
        assertEquals(40.3f, heights[peak], 1.5f, "peak lens height")
        // Width and height share one fraction today; pin the width at the peak so a per-axis spring cannot drift.
        assertEquals(1.63f, widths[peak] / 37f, .03f, "peak width over the 37 dp rest knob (measured 1.62-1.64): $widths")
    }

    /** Release from a hold: 115 -> 84 px (38.3 dp -> the 28 dp track) in 8 frames (133 ms), never below the rest knob. */
    @Test fun releaseReachesTheTrackHeightIn133MsAndTheKnobNeverUndershoots() = runDesktopComposeUiTest(400, 240, testTimeout = 2.minutes) {
        mainClock.autoAdvance = false
        setContent { Frame { Switch(false, {}, Modifier.testTag("sw")) } }
        mainClock.advanceTimeBy(100)
        onNodeWithTag("sw").performTouchInput { down(center) }
        mainClock.advanceTimeBy(1000)
        onNodeWithTag("sw").performTouchInput { up() }
        val heights = ArrayList<Float>()
        repeat(16) {
            mainClock.advanceTimeByFrame()
            heights += onNodeWithTag(GlassKnobTestTag, useUnmergedTree = true).inDp(scale).height
        }
        val first = heights.indexOfFirst { it < 38.2f }
        val onTrack = heights.indexOfFirst { it <= 28.4f }
        val frames = onTrack - first + 1
        assertTrue(onTrack >= 0 && frames in 7..9, "the lens took $frames frames to fall to the track (measured 8 = 133 ms): $heights")
        assertTrue(heights.all { it >= 23.5f }, "the knob shrank below its rest size: $heights")
    }

    /**
     * A whole tap in I-menus-dark (tap A, 24.012-24.328 s, 60 fps): the lens outline is taller than the 84 px
     * track from 24.012 to 24.295 (18 frames, 300 ms), with its peak
     * (118 px, 39 dp incl. shadow) 80-130 ms in. The recording does not show the finger, so the 200 ms touch (down to up, which
     * includes the 40 ms the growth is hidden inside the track) is inferred from where the shrink starts (24.18 s): the assertion is that a touch of that length reproduces
     * the measured lifetime, not that real taps last 200 ms.
     */
    @Test fun aTapsLensLivesAbout300MsFromTheTouch() = runDesktopComposeUiTest(400, 240, testTimeout = 2.minutes) {
        mainClock.autoAdvance = false
        var checked by mutableStateOf(false)
        setContent { Frame { Switch(checked, { checked = it }, Modifier.testTag("sw")) } }
        mainClock.advanceTimeBy(500)
        onNodeWithTag("sw").performTouchInput { down(center) }
        val heights = ArrayList<Float>()
        repeat(30) { i ->
            if (i == 12) { onNodeWithTag("sw").performTouchInput { up() }; checked = true }
            mainClock.advanceTimeByFrame()
            heights += onNodeWithTag(GlassKnobTestTag, useUnmergedTree = true).inDp(scale).height
        }
        val first = heights.indexOfFirst { it > 28.4f }
        val last = heights.indexOfLast { it > 28.4f }
        val lifetime = (last - first + 1) * frame
        assertTrue(lifetime in 300 - 2 * frame..300 + 2 * frame, "the lens lived ${lifetime.toInt()} ms (measured 300 ms +-2 frames): $heights")
        assertEquals(40.3f, heights.max(), 1.5f, "peak lens height")
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

    /** Track colour of a settled switch, read from the rendered pixels at the track end away from the knob. */
    private fun androidx.compose.ui.test.ComposeUiTest.trackColour(dark: Boolean, checked: Boolean, custom: Color? = null): Color {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(scale)) {
                MaterialTheme(colorScheme = if (dark) androidx.compose.material3.darkColorScheme() else lightColorScheme()) {
                    Box(Modifier.background(if (dark) Color.Black else Color.White).padding(8.dp)) {
                        if (custom != null) Switch(checked, {}, Modifier.testTag("sw"), colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = custom, uncheckedTrackColor = custom))
                        else Switch(checked, {}, Modifier.testTag("sw"))
                    }
                }
            }
        }
        mainClock.advanceTimeBy(1000)
        val px = onNodeWithTag("sw").captureToImage().toPixelMap()
        // Off: knob sits left, sample the right end; on: the reverse.
        val x = if (checked) (px.width * .06f).toInt() else (px.width * .94f).toInt()
        return px[x, px.height / 2]
    }

    private fun assertRgb(expected: Int, actual: Float, what: String) =
        assertEquals(expected.toFloat(), actual * 255f, 2f, what)

    /** Measured on the native switch in the dark appearance (I-menus-dark): on (47, 210, 88), off (90, 90, 94), max channel diff 4. */
    @Test fun aDarkSwitchUsesTheMeasuredIosTrackColours() = runDesktopComposeUiTest(400, 240, testTimeout = 2.minutes) {
        mainClock.autoAdvance = false
        val on = trackColour(dark = true, checked = true)
        assertRgb(47, on.red, "on red"); assertRgb(210, on.green, "on green"); assertRgb(88, on.blue, "on blue")
    }

    @Test fun aDarkSwitchOffTrackIsTheMeasuredGrey() = runDesktopComposeUiTest(400, 240, testTimeout = 2.minutes) {
        mainClock.autoAdvance = false
        val off = trackColour(dark = true, checked = false)
        assertRgb(90, off.red, "off red"); assertRgb(90, off.green, "off green"); assertRgb(94, off.blue, "off blue")
    }

    /** No light-appearance switch is recorded: a light scheme keeps the theme's colours, not the dark grey. */
    @Test fun aLightSwitchKeepsTheThemeColours() = runDesktopComposeUiTest(400, 240, testTimeout = 2.minutes) {
        mainClock.autoAdvance = false
        val off = trackColour(dark = false, checked = false)
        assertTrue(off.red * 255f > 150f, "the light off track must not be the dark grey: $off")
    }

    @Test fun aCallersTrackColoursStillWin() = runDesktopComposeUiTest(400, 240, testTimeout = 2.minutes) {
        mainClock.autoAdvance = false
        val c = trackColour(dark = true, checked = true, custom = Color(200, 10, 10))
        assertRgb(200, c.red, "custom red"); assertRgb(10, c.green, "custom green")
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
