package com.wexpa.liquidglass

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals

/** The new preset must preserve all click, cancellation, scrolling and reduce-motion rules. */
@OptIn(ExperimentalTestApi::class)
class GlassTabBarCalmGestureTest : GlassTabBarGestureTest() {
    override val style: GlassTabBarStyle get() = GlassTabBarStyle.Calm()
    @Test override fun aFastLiftFlingsToTheNextTab() = run { rec, node ->
        node.performTouchInput {
            down(Offset(cx(0), y))
            for (k in 1..6) { advanceEventTime(8); moveTo(Offset(cx(0) + k * 20f, y)) }
            advanceEventTime(8); up()
        }
        settle()
        // Pose navigation follows the finger's intent, as V3 does; the legacy fixture
        // instead adds its capsule's old grasp offset to the intended selection.
        val under = geometry.nearestIndex(cx(0) + 120f)
        assertEquals(listOf((under + 1).coerceAtMost(4)), rec.calls)
        assertReleased(node, rec.calls[0], "calm fling")
    }

    private class Ticks : androidx.compose.ui.hapticfeedback.HapticFeedback {
        val types = mutableListOf<androidx.compose.ui.hapticfeedback.HapticFeedbackType>()
        override fun performHapticFeedback(hapticFeedbackType: androidx.compose.ui.hapticfeedback.HapticFeedbackType) {
            types += hapticFeedbackType
        }
    }

    /** #116: one selection tick for a tap; none when the host changes the selection itself. */
    @Test fun aTapTicksOnce() {
        val ticks = Ticks(); haptics = ticks
        run { rec, node ->
            node.performTouchInput { down(Offset(cx(3), y)); advanceEventTime(60); up() }
            settle()
            assertEquals(listOf(3), rec.calls)
            assertEquals(listOf(androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentTick), ticks.types)
        }
    }

    /** #116: a finger pressing tab 1 (not the selection) marks it, one tick, and dragging the held lens on
     * to tab 4 ticks once per tab it enters (2, 3, 4). */
    @Test fun aHeldDragTicksForEachTabItCrosses() {
        val ticks = Ticks(); haptics = ticks
        run { _, node ->
            node.performTouchInput {
                down(Offset(cx(1), y)); advanceEventTime(300); moveTo(Offset(cx(1), y))
                for (k in 1..30) { advanceEventTime(16); moveTo(Offset(cx(1) + k * (cx(4) - cx(1)) / 30f, y)) }
                advanceEventTime(200); up()
            }
            settle()
            assertEquals(4, ticks.types.size, "ticks: ${ticks.types}")
        }
    }
}
