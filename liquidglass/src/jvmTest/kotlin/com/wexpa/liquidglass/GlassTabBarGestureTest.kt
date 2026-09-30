package com.wexpa.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The production [GlassTabBar], its real item clickables and its real pointer handler, driven by real
 * Compose touch events (down / move / up / cancel, a second finger, a scrolling parent that consumes),
 * with a selection recorder (closeout Phase 3, section 3). These are the regression tests of gesture
 * *behaviour*; [GlassTabBarMotionTraceTest] remains the dynamics diagnostic and shares only
 * [decideDragEnd] with production.
 *
 * Layout under test: the bar at 500 x 62 dp (density 1), five tabs, `GlassTabBarStyle.Measured`, so slot
 * = 97.6 px and the centres are 54.8, 152.4, 250, 347.6, 445.2; the Compose test touch slop is 18 px.
 *
 * Not covered here, and said so: an OS-level ACTION_CANCEL delivered by the platform. The test harness's
 * `cancel()` and a consuming parent exercise the same `awaitHorizontalTouchSlopOrCancellation` /
 * `horizontalDrag` cancel returns that an OS cancel reaches; the decision itself is shared production
 * logic ([decideDragEnd] and the handler's release path).
 */
@OptIn(ExperimentalTestApi::class)
open class GlassTabBarGestureTest {

    /**
     * The style under test. Every scenario below is about ownership - who selects, how many times,
     * and what the selector is left doing - which must hold identically on the capsule path and on
     * the V3 deforming-body path. [GlassTabBarV3GestureTest] runs the same file against the V3
     * selector so a new visual model cannot quietly change what a gesture means.
     */
    internal open val style: GlassTabBarStyle get() = GlassTabBarStyle.Measured(dark = true)

    internal class Recorder {
        val calls = ArrayList<Int>()
        var selected by mutableIntStateOf(2)
        /** When set, a parent of the bar consumes every pointer change in the initial pass: another node has taken the gesture. */
        var parentConsumes by mutableStateOf(false)
    }

    private val barWidthPx = 500f
    private val barHeightPx = 62f
    internal val geometry = GlassTabBarGeometry(barWidth = barWidthPx, inset = 6f, count = 5)
    internal fun cx(i: Int) = geometry.centreOf(i)
    internal val y = barHeightPx / 2f

    @Composable
    private fun Fixture(rec: Recorder, scrollingParent: Boolean = false, motionEnabled: Boolean = true) {
        // Quarter-resolution material: the gesture is what is under test, and the CPU shader render per frame
        // is what would otherwise push a settle past the harness's one-minute budget.
        val state = rememberLiquidGlassState(background = Color.Black, renderScale = 0.25f)
        val bar: @Composable () -> Unit = {
            GlassTabBar(
                state = state,
                itemCount = 5,
                selectedIndex = rec.selected,
                onSelected = { rec.calls.add(it); rec.selected = it },
                modifier = Modifier.testTag("bar").width(500.dp),
                style = style,
                motionEnabled = motionEnabled,
            ) { _, _ -> Box(Modifier.size(20.dp)) }
        }
        if (scrollingParent) {
            Column(Modifier.size(500.dp, 400.dp).verticalScroll(rememberScrollState())) {
                Box(Modifier.width(500.dp).height(300.dp).background(Color.DarkGray).liquidGlassSource(state))
                bar()
                Box(Modifier.width(500.dp).height(900.dp))
            }
        } else {
            Box(Modifier.size(500.dp, 200.dp)) {
                Box(Modifier.fillMaxSize().background(Color.DarkGray).liquidGlassSource(state))
                Box(
                    Modifier.width(500.dp).height(62.dp).pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val e = awaitPointerEvent(PointerEventPass.Initial)
                                if (rec.parentConsumes) e.changes.forEach { it.consume() }
                            }
                        }
                    },
                ) { bar() }
            }
        }
    }

    internal fun SemanticsNodeInteraction.state(): Map<String, Any?> {
        val c = fetchSemanticsNode().config
        fun <T> get(key: androidx.compose.ui.semantics.SemanticsPropertyKey<T>): T? = if (c.contains(key)) c[key] else null
        return mapOf(
            "x" to get(GlassTabBarSemantics.SelectorX), "held" to get(GlassTabBarSemantics.Held),
            "dragging" to get(GlassTabBarSemantics.Dragging), "dragIndex" to get(GlassTabBarSemantics.DragIndex),
        )
    }

    internal fun assertReleased(node: SemanticsNodeInteraction, expectedTab: Int, label: String) {
        val s = node.state()
        assertEquals(false, s["held"], "$label: held still set")
        assertEquals(false, s["dragging"], "$label: dragging still set")
        assertEquals(-1, s["dragIndex"], "$label: preview not cleared: ${s["dragIndex"]}")
        val x = s["x"] as Float
        assertTrue(abs(x - cx(expectedTab)) < 0.5f, "$label: lens at $x, expected tab $expectedTab at ${cx(expectedTab)}")
    }

    internal fun run(scrollingParent: Boolean = false, motionEnabled: Boolean = true, body: androidx.compose.ui.test.ComposeUiTest.(Recorder, SemanticsNodeInteraction) -> Unit) = runComposeUiTest {
        val rec = Recorder()
        setContent { Fixture(rec, scrollingParent, motionEnabled) }
        waitForIdle()
        // The gesture node inside the bar carries the selector state (unmerged: it is a descendant).
        val node = onNodeWithTag(GlassTabBarSemantics.TAG, useUnmergedTree = true)
        body(rec, node)
    }

    /** Lets every spring finish: the clock auto-advances until the tree is idle. */
    internal fun androidx.compose.ui.test.ComposeUiTest.settle() { waitForIdle() }

    @Test
    fun aTapSelectsOnceThroughTheItem() = run { rec, node ->
        node.performTouchInput { down(Offset(cx(3), y)); advanceEventTime(60); up() }
        settle()
        assertEquals(listOf(3), rec.calls, "one callback for one tap")
        assertReleased(node, 3, "tap")
    }

    @Test
    fun tappingTheSelectedTabIsOneCallbackAndStaysPut() = run { rec, node ->
        node.performTouchInput { down(Offset(cx(2), y)); advanceEventTime(60); up() }
        settle()
        assertEquals(listOf(2), rec.calls)
        assertReleased(node, 2, "selected-tab tap")
    }

    @Test
    fun aDragThatStopsHoldsAndLiftsCommitsTheTabUnderTheLensWithoutAFling() = run { rec, node ->
        node.performTouchInput {
            down(Offset(cx(1), y))
            for (k in 1..10) { advanceEventTime(10); moveTo(Offset(cx(1) + k * 18f, y)) }   // 1800 px/s
            for (k in 1..10) { advanceEventTime(40); moveTo(Offset(cx(1) + 180f, y)) }       // 400 ms held still
            up()
        }
        settle()
        val under = geometry.nearestIndex(cx(1) + 180f)
        assertEquals(listOf(under), rec.calls, "the tab under the lens, exactly once, no fling")
        assertReleased(node, under, "hold then lift")
    }

    @Test
    open fun aFastLiftFlingsToTheNextTab() = run { rec, node ->
        node.performTouchInput {
            down(Offset(cx(0), y))
            for (k in 1..6) { advanceEventTime(8); moveTo(Offset(cx(0) + k * 20f, y)) }      // 2500 px/s
            advanceEventTime(8); up()
        }
        settle()
        // The lens was on tab 2 when the finger landed on tab 0, so it keeps the grab offset of half its
        // width (48.8 px) and sits at finger + 48.8 = 223.6 px at the lift: over tab 2; the fling carries one
        // further.
        val under = geometry.nearestIndex(cx(0) + 120f + 48.8f)
        assertEquals(listOf((under + 1).coerceAtMost(4)), rec.calls, "a fling carries one tab further than $under")
        assertReleased(node, rec.calls[0], "fling")
    }

    @Test
    fun aCancelBeforeSlopSelectsNothingAndReleases() = run { rec, node ->
        // The harness cannot inject a platform ACTION_CANCEL on this target; a gesture taken by another
        // node (every change consumed in the initial pass) is the cancellation the handler sees.
        node.performTouchInput { down(Offset(cx(4), y)) }
        rec.parentConsumes = true
        node.performTouchInput { advanceEventTime(40); moveBy(Offset(2f, 0f)); advanceEventTime(40); up() }
        rec.parentConsumes = false
        settle()
        assertEquals(emptyList<Int>(), rec.calls, "a cancelled press must not select")
        assertReleased(node, 2, "cancel before slop")
    }

    @Test
    fun aCancelAfterCrossingTabsSelectsNothingAndSettlesBack() = run { rec, node ->
        node.performTouchInput {
            down(Offset(cx(2), y))
            for (k in 1..12) { advanceEventTime(12); moveTo(Offset(cx(2) + k * 18f, y)) }   // crosses into tab 3 and 4
        }
        assertTrue((node.state()["dragIndex"] as Int) > 2, "the preview moved on before the takeover")
        rec.parentConsumes = true                                  // another node takes the drag mid-way
        node.performTouchInput { advanceEventTime(12); moveBy(Offset(10f, 0f)); advanceEventTime(12); up() }
        rec.parentConsumes = false
        settle()
        assertEquals(emptyList<Int>(), rec.calls, "a cancelled drag must not commit the tab it was over")
        assertReleased(node, 2, "cancel after crossing")
    }

    @Test
    fun aVerticalScrollInAScrollingParentTakesTheGestureWithoutASelection() = run(scrollingParent = true) { rec, node ->
        node.performTouchInput {
            down(Offset(cx(3), y))
            for (k in 1..8) { advanceEventTime(12); moveTo(Offset(cx(3), y - k * 15f)) }
            advanceEventTime(12); up()
        }
        settle()
        assertEquals(emptyList<Int>(), rec.calls, "a vertical scroll over the bar is not a tap or a drag")
        assertReleased(node, 2, "vertical takeover")
    }

    @Test
    fun aTakeoverAfterAFlingLeavesTheCommittedSelectionAndReleases() = run { rec, node ->
        node.performTouchInput {
            down(Offset(cx(0), y))
            for (k in 1..6) { advanceEventTime(8); moveTo(Offset(cx(0) + k * 20f, y)) }
            advanceEventTime(8); up()
        }
        // The harness runs the clock to idle inside performTouchInput, so the fling has settled by the time
        // the next press lands; the value/velocity continuity of a press that interrupts a settle is the
        // dynamics diagnostic's job (GlassTabBarMotionTraceTest), this test's job is the ownership.
        node.performTouchInput { down(Offset(cx(1), y)) }
        rec.parentConsumes = true
        node.performTouchInput { advanceEventTime(30); moveBy(Offset(2f, 0f)); advanceEventTime(30); up() }
        rec.parentConsumes = false
        settle()
        assertEquals(1, rec.calls.size, "only the completed drag selected: ${rec.calls}")
        assertReleased(node, rec.calls[0], "cancel while settling")
    }

    @Test
    fun aSecondTapAfterAReleaseIsASecondCallbackAndNothingSticks() = run { rec, node ->
        node.performTouchInput { down(Offset(cx(1), y)); advanceEventTime(60); up() }
        // See above: the release has completed when the second press lands here; the mid-release retarget
        // itself is exercised in GlassTabBarMotionTraceTest.
        node.performTouchInput { down(Offset(cx(1) + 3f, y)); advanceEventTime(60); up() }
        settle()
        assertEquals(listOf(1, 1), rec.calls, "two taps, two callbacks, no extra one from the ancestor")
        assertReleased(node, 1, "re-press during release")
    }

    @Test
    fun aSecondFingerTakingOverCancelsTheDragInsteadOfCommittingWithStaleVelocity() = run { rec, node ->
        node.performTouchInput {
            down(0, Offset(cx(1), y))
            for (k in 1..6) { advanceEventTime(10); moveTo(0, Offset(cx(1) + k * 18f, y)) }
            down(1, Offset(cx(3), y + 5f))
            advanceEventTime(10); up(0)                    // the driving finger leaves while another is down
            for (k in 1..4) { advanceEventTime(10); moveTo(1, Offset(cx(3) + k * 18f, y + 5f)) }
            advanceEventTime(10); up(1)
        }
        settle()
        assertEquals(emptyList<Int>(), rec.calls, "a handed-over drag is cancelled, never committed")
        assertReleased(node, 2, "two fingers")
    }

    @Test
    fun withMotionDisabledATapStillSelectsOnceAndADragSelectsNothing() = run(motionEnabled = false) { rec, node ->
        node.performTouchInput { down(Offset(cx(4), y)); advanceEventTime(60); up() }
        settle()
        assertEquals(listOf(4), rec.calls)
        node.performTouchInput {
            down(Offset(cx(4), y))
            for (k in 1..8) { advanceEventTime(12); moveTo(Offset(cx(4) - k * 20f, y)) }
            advanceEventTime(12); up()
        }
        settle()
        assertEquals(listOf(4), rec.calls, "no lens to drag: a swipe selects nothing")
        val s = node.state()
        assertFalse(s["held"] as Boolean); assertEquals(-1, s["dragIndex"])
    }
}
