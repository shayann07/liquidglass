package com.wexpa.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.focusable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Search mode (#112): the geometry end states and the state machine around them. */
@OptIn(ExperimentalTestApi::class)
class GlassTabBarSearchModeTest {
    // The 390 dp reference screen's search row: field from 9.8 to 381.9 dp (ios_27_liquid_ui_sample_ref.MP4, 40.5 s).
    private val rowWidth = 372.1f

    @Test fun restIsTheBarAGapAndACircle() {
        val r = glassSearchRects(rowWidth, 62f, 8f, 0f)
        assertEquals(rowWidth - 62f - 8f, r.barWidth, 1e-3f)
        assertEquals(1f, r.barMaterial, 1e-6f)
        assertEquals(rowWidth - 62f, r.fieldLeft, 1e-3f)
        assertEquals(62f, r.fieldWidth, 1e-3f)
        assertEquals(62f, r.fieldHeight, 1e-3f)
        assertEquals(0f, r.closeDiameter, 1e-6f)
    }

    @Test fun openMatchesTheMeasuredEndState() {
        val r = glassSearchRects(rowWidth, 62f, 8f, 1f)
        // Measured: field 9.8 .. 325.6 dp (315.8 wide, 47.7 tall), close 334.8 .. 381.9 (47.1), 9.2 dp apart.
        assertEquals(315.8f, r.fieldWidth, 1f)
        assertEquals(47.7f, r.fieldHeight, 1e-3f)
        assertEquals(47.1f, r.closeDiameter, 1e-3f)
        assertEquals(0f, r.barMaterial, 1e-6f)
        assertEquals(9.2f, (r.closeCentreX - r.closeDiameter / 2f) - r.fieldRight, 1e-3f)
        assertEquals(rowWidth, r.closeCentreX + r.closeDiameter / 2f, 1e-3f)
        assertEquals(31f, r.centreY, 1e-3f)
    }

    @Test fun progressIsMonotoneInEveryPiece() {
        var last = glassSearchRects(rowWidth, 62f, 8f, 0f)
        for (i in 1..100) {
            val r = glassSearchRects(rowWidth, 62f, 8f, i / 100f)
            assertTrue(r.fieldWidth >= last.fieldWidth - 1e-4f, "field width at $i")
            assertTrue(r.barWidth <= last.barWidth + 1e-4f, "bar width at $i")
            assertTrue(r.barMaterial <= last.barMaterial + 1e-6f, "bar material at $i")
            assertTrue(r.closeDiameter >= last.closeDiameter - 1e-4f, "close at $i")
            assertTrue(r.fieldHeight <= last.fieldHeight + 1e-4f, "field height at $i")
            last = r
        }
    }

    @Test fun leavingIsAboutAThirdShorterThanEntering() {
        val ratio = GlassSearchMotion.CollapseMillis.toFloat() / GlassSearchMotion.ExpandMillis
        assertEquals(0.67f, ratio, 0.05f)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.scene(state: GlassSearchState, motion: Boolean = true,
                                                             onSelected: (Int) -> Unit = {}, selected: Int = 1) = setContent {
        CompositionLocalProvider(LocalDensity provides Density(1f)) {
            LiquidGlassTheme(dark = true, background = Color(0xFF202020), renderScale = .25f) {
                Box(Modifier.size(400.dp, 160.dp).background(Color(0xFF202020)).testTag("scene")) {
                    GlassTabBar(3, selected, onSelected, state, { Box(Modifier.size(100.dp, 20.dp).focusable().testTag("field")) },
                        Modifier.width(372.dp).testTag("row"), style = GlassTabBarStyle.Calm().copy(height = 62.dp),
                        motionEnabled = motion) { _, _ -> Box(Modifier.size(18.dp).background(Color.White)) }
                }
            }
        }
    }

    /**
     * Steps the manual clock in 50 ms frames, so no test waits on idleness. Each frame renders the glass in software
     * (about half a second here), which is what made the first version of these tests time out.
     */
    private fun androidx.compose.ui.test.ComposeUiTest.step(ms: Int) = repeat(ms / 50 + 1) { mainClock.advanceTimeBy(50, ignoreFrameDuration = true) }

    @Test fun tappingTheButtonOpensAndTheCloseButtonClosesIt() = runComposeUiTest(testTimeout = 90.seconds) {
        val state = GlassSearchState()
        scene(state)
        mainClock.autoAdvance = false
        step(64)
        assertFalse(state.expanded)
        onNodeWithContentDescription("Search").performClick()
        step(400)
        assertTrue(state.expanded)
        assertEquals(1f, state.fraction, 1e-3f)
        onNodeWithTag("field").assertExists()
        onNodeWithContentDescription("Close search").performClick()
        step(300)
        assertFalse(state.expanded)
        assertEquals(0f, state.fraction, 1e-3f)
    }

    @Test fun escapeClosesAndSelectionSurvivesTheRoundTrip() = runComposeUiTest(testTimeout = 90.seconds) {
        val state = GlassSearchState()
        var calls = 0
        scene(state, onSelected = { calls++ }, selected = 2)
        mainClock.autoAdvance = false
        step(64)
        state.expand()
        step(400)
        assertTrue(state.expanded)
        onNodeWithTag("row").performKeyInput { pressKey(Key.Escape) }
        step(300)
        assertFalse(state.expanded)
        assertEquals(0, calls)
        assertEquals(0f, state.fraction, 1e-3f)
    }

    @Test fun closingDuringTheOpeningReversesFromWhereItIs() = runComposeUiTest(testTimeout = 90.seconds) {
        val state = GlassSearchState()
        scene(state)
        mainClock.autoAdvance = false
        step(64)
        state.expand()
        var tries = 0
        while (state.fraction < 0.05f && tries++ < 30) mainClock.advanceTimeBy(16, ignoreFrameDuration = true)
        val mid = state.fraction
        assertTrue(mid > 0.05f && mid < 0.95f, "mid-flight at $mid after $tries frames")
        state.collapse()
        // The old animation runs on until the effect restarts, a frame or two, at up to 0.1 per frame; it never goes on to 1.
        var peak = mid
        repeat(8) { mainClock.advanceTimeBy(50, ignoreFrameDuration = true); peak = maxOf(peak, state.fraction) }
        assertTrue(peak < 0.95f && peak <= mid + 0.3f, "reversing from $mid, peaked at $peak")
        step(400)
        assertEquals(0f, state.fraction, 1e-3f)
    }

    @Test fun reducedMotionSwapsTheStatesWithoutTravel() = runComposeUiTest(testTimeout = 4.minutes) {
        val state = GlassSearchState()
        scene(state, motion = false)
        waitForIdle()
        mainClock.autoAdvance = false
        state.expand()
        mainClock.advanceTimeBy(32)
        assertEquals(1f, state.fraction, 1e-6f)
    }
}