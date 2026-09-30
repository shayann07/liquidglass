package com.wexpa.liquidglass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.performTouchInput
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every ownership scenario of [GlassTabBarGestureTest], run against the V3 deforming selector, plus
 * the behaviour the V3 model adds: a tap never acquires the held lens, a real hold does, and the
 * body is contained throughout an ordinary tap.
 *
 * The inherited cases are the point. A new visual model that changed how many callbacks a tap makes,
 * or let a cancelled drag commit, would be a regression whatever it looked like.
 */
@OptIn(ExperimentalTestApi::class)
class GlassTabBarV3GestureTest : GlassTabBarGestureTest() {

    override val style: GlassTabBarStyle get() = GlassTabBarStyle.V3(dark = true)

    @Test
    fun aMatureHoldCanMovePurelyVerticallyAndReturnWithoutLosingOwnership() = run { rec, node ->
        mainClock.autoAdvance = false
        node.performTouchInput { down(Offset(cx(2), y)) }
        mainClock.advanceTimeBy(350)
        val before = node.fetchSemanticsNode().config[GlassTabBarSemantics.NodeTop]
        val beforeProtrusion = node.fetchSemanticsNode().config[GlassTabBarSemantics.Protrusion]
        node.performTouchInput {
            advanceEventTime(350)
            for (i in 1..10) { advanceEventTime(16); moveTo(Offset(cx(2), y - 8f * i)) }
        }
        mainClock.advanceTimeBy(80)
        val c = node.fetchSemanticsNode().config
        assertEquals(true, c[GlassTabBarSemantics.Dragging])
        assertTrue(c[GlassTabBarSemantics.NodeTop] < before - 1f && c[GlassTabBarSemantics.NodeTop] > before - 25f,
            "the mature vertical pull must be registered while the body remains anchored")
        assertTrue(c[GlassTabBarSemantics.Protrusion] > beforeProtrusion + 5f,
            "a registered off-axis pull must deform the actual contour")
        node.performTouchInput { advanceEventTime(100); up() }
        mainClock.autoAdvance = true
        settle()
        assertEquals(listOf(2), rec.calls)
        assertReleasedV3(node, 2, "vertical hold")
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.v3(): Map<String, Any?> {
        val c = fetchSemanticsNode().config
        fun <T> get(key: androidx.compose.ui.semantics.SemanticsPropertyKey<T>): T? =
            if (c.contains(key)) c[key] else null
        return mapOf(
            "formation" to get(GlassTabBarSemantics.Formation),
            "width" to get(GlassTabBarSemantics.BodyWidth),
            "skew" to get(GlassTabBarSemantics.BodySkew),
            "protrusion" to get(GlassTabBarSemantics.Protrusion),
        )
    }

    @Test
    fun aQuickTapNeverStartsTheHeldLens() = run { rec, node ->
        // Touch-down is a press indication and a travel target, not the protruding lens. The old
        // handler set the held state on every DOWN, so a tap began forming it before it was known
        // to be a tap; the hold threshold is what decides now.
        //
        // The clock has to be driven by hand here. With auto-advance the harness runs time on
        // between input blocks, so by the time the state could be read the press would have
        // outlived the hold threshold and formed the lens legitimately - which is the behaviour,
        // not a failure of it.
        mainClock.autoAdvance = false
        node.performTouchInput { down(Offset(cx(4), y)) }
        mainClock.advanceTimeBy(48)                       // three frames: well under the 120 ms threshold
        // SUPERSEDED by the iOS-parity brief's section 8.2, with the reason stated rather than
        // the assertion deleted. That section requires a tap-transiting selector to have a
        // motion-dependent material response even when it never becomes a hold: "a black resting
        // pill translating unchanged is not sufficient evidence of long-tap parity". So a press
        // under the threshold may form the material *a little* - here it is travelling to tab 4,
        // and that travel is what forms it.
        //
        // What must still hold, and is what the owner outcome actually says, is that touch-down
        // alone does not produce the HELD lens. That is asserted here instead of formation == 0.
        val earlyFormation = node.v3()["formation"] as Float
        assertTrue(
            earlyFormation < 0.4f,
            "a press shorter than the hold threshold reached held-lens formation: $earlyFormation",
        )
        mainClock.advanceTimeBy(200)                      // and past it, the lens does form
        assertTrue(
            (node.v3()["formation"] as Float) > 0.5f,
            "a press that outlived the threshold did not form the lens",
        )
        mainClock.autoAdvance = true
        node.performTouchInput { advanceEventTime(10); up() }
        settle()
        assertEquals(listOf(4), rec.calls)
        assertReleasedV3(node, 4, "quick tap")
        assertEquals(0f, node.v3()["formation"] as Float, 1e-2f, "the lens stayed formed after the lift")
    }

    @Test
    fun anOrdinaryTapKeepsTheWholeBodyInsideTheBar() = run { rec, node ->
        // Containment is checked at every substep inside the controller; here it is checked on the
        // real component, through the state the component publishes.
        node.performTouchInput { down(Offset(cx(4), y)); advanceEventTime(40); up() }
        var worst = 0f
        repeat(30) {
            mainClock.advanceTimeBy(16)
            waitForIdle()
            worst = maxOf(worst, node.v3()["protrusion"] as Float)
        }
        settle()
        assertTrue(worst <= 0.2f, "an ordinary tap left the bar by $worst px")
        assertEquals(listOf(4), rec.calls)
    }

    @Test
    fun aHeldDragFormsTheLensAndDeformsTheBody() = run { rec, node ->
        // The clock is driven by hand. With auto-advance every injected move is followed by a
        // full settle, so a "drag" becomes a sequence of static steps and the body's velocity
        // response - the thing under test - is never alive when the state can be read.
        mainClock.autoAdvance = false
        node.performTouchInput { down(Offset(cx(1), y)) }
        mainClock.advanceTimeBy(32)
        val restingWidth = node.v3()["width"] as Float
        node.performTouchInput {
            for (k in 1..12) { advanceEventTime(12); moveTo(Offset(cx(1) + k * 26f, y)) }
        }
        val dragging = node.v3()
        mainClock.autoAdvance = true
        assertTrue(
            (dragging["formation"] as Float) > 0.5f,
            "a recognized drag did not form the lens: ${dragging["formation"]}",
        )
        assertTrue(
            (dragging["width"] as Float) > restingWidth,
            "the body did not stretch: ${dragging["width"]} against $restingWidth at rest",
        )
        assertTrue(
            abs(dragging["skew"] as Float) < 0.01f,
            "the reference capsule acquired unsupported end asymmetry: ${dragging["skew"]}",
        )
        node.performTouchInput { advanceEventTime(10); up() }
        settle()
        assertEquals(1, rec.calls.size, "one commit for one drag: ${rec.calls}")
        assertReleasedV3(node, rec.calls[0], "held drag")
        assertEquals(0f, node.v3()["formation"] as Float, 1e-2f, "the lens stayed formed after release")
    }

    private fun assertReleasedV3(node: androidx.compose.ui.test.SemanticsNodeInteraction, tab: Int, label: String) {
        val c = node.fetchSemanticsNode().config
        assertEquals(false, c[GlassTabBarSemantics.Held], "$label: held still set")
        assertEquals(false, c[GlassTabBarSemantics.Dragging], "$label: dragging still set")
        assertEquals(-1, c[GlassTabBarSemantics.DragIndex], "$label: preview not cleared")
        val x = c[GlassTabBarSemantics.SelectorX]
        assertTrue(abs(x - cx(tab)) < 1.0f, "$label: body at $x, expected tab $tab at ${cx(tab)}")
    }

    /**
     * The base class computes the expected tab from "finger + 48.8 px", which is the **capsule**
     * selector's half-width grab offset. That constant describes one superseded model's geometry,
     * not a requirement, so it is overridden here rather than left to fail or quietly widened.
     *
     * What is asserted is unchanged in substance: one callback, and it is one tab beyond the tab
     * the finger was over when it lifted. The pose selector's navigation intent is the finger
     * itself, bounded only by the item sequence (iOS-parity brief section 5) - it is never
     * clamped by a body that is free to protrude, taper or rise away from the tab being chosen.
     */
    @Test
    override fun aFastLiftFlingsToTheNextTab() = run { rec, node ->
        node.performTouchInput {
            down(Offset(cx(0), y))
            for (k in 1..6) { advanceEventTime(8); moveTo(Offset(cx(0) + k * 20f, y)) }      // 2500 px/s
            advanceEventTime(8); up()
        }
        settle()
        val under = geometry.nearestIndex(cx(0) + 120f)
        assertEquals(
            listOf((under + 1).coerceAtMost(4)),
            rec.calls,
            "a fling carries one tab further than the tab under the finger at the lift ($under)",
        )
        assertReleased(node, rec.calls[0], "fling")
    }
}
