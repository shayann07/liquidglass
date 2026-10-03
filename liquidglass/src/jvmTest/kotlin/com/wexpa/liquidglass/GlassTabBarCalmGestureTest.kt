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
}
