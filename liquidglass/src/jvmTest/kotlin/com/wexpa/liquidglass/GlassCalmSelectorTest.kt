package com.wexpa.liquidglass

import kotlin.math.abs
import kotlin.test.*

/** Original Phone T04: held body never becomes a free-button off-axis squeeze.
 * Dynamics rates are authored; the 1.23 bar-height bound is an observed envelope. */
class GlassCalmSelectorTest {
    private val bar = GlassSelectorBar(360f, 64f, 6f, 4, 32f, 39f, 29f, 29f, 40f, 55f, 1f, 260f)
    private fun controller(): GlassPoseController = GlassPoseController(GlassPoseSpec.Calm).also {
        it.attach(bar, FloatArray(8) { i -> bar.centreOf(i / 2) + if (i % 2 == 0) -20f else 20f },
            FloatArray(4) { i -> bar.centreOf(i) }, 18f)
        it.snapToRest(1)
    }
    private fun advance(c: GlassPoseController, from: Float, to: Float) {
        var t = from
        while (t < to) { t = minOf(to, t + 1f / 120); c.advanceTo(t) }
    }
    @Test fun perpendicularFullScreenDragsDoNotSquishTheSelector() {
        val c = controller()
        c.pointerDown(bar.centreOf(1), 32f, 0.0, true)
        advance(c, 0f, .8f)
        val held = GlassPoseExtents().also(c::extents)
        var time = .8f
        for (distance in listOf(-2000f, 2000f, -100000f, 100000f, 0f)) {
            c.pointerMove(bar.centreOf(1), 32f + distance, time.toDouble())
            advance(c, time, time + .3f); time += .3f
            val extreme = GlassPoseExtents().also(c::extents)
            assertTrue(abs(extreme.height - held.height) < .5f, "selector height changed on perpendicular drag: ${extreme.height}")
            assertTrue(abs(extreme.width - held.width) < .5f, "selector width changed on perpendicular drag")
            assertTrue(extreme.height <= 64f * 1.23f)
            assertFalse(c.solverFailed)
        }
        c.pointerUp(1); advance(c, time, time + 2f)
        assertTrue(abs(GlassPoseExtents().also(c::extents).height - 58f) < .5f)
    }
    @Test fun longerTravelDeformsMoreAndReturnsToItsRestingSize() {
        fun trace(target: Int): Float {
            val c = controller(); c.retarget(target)
            var maximum = 0f
            for (i in 1..180) {
                c.advanceTo(i / 120f)
                val e = GlassPoseExtents().also(c::extents)
                maximum = maxOf(maximum, e.width)
                assertTrue(e.height <= bar.height + .2f)
                assertFalse(c.solverFailed)
            }
            val settled = GlassPoseExtents().also(c::extents)
            assertTrue(abs(settled.width - 78f) < .5f)
            return maximum
        }
        val short = trace(2); val long = trace(3)
        assertTrue(long > short + 5f, "distance did not affect shape: $short $long")
        assertTrue(short > 88f, "selector remained rigid: $short")
        println("CALM_TRAVEL short=$short long=$long rest=78")
    }
}
