package com.wexpa.liquidglass

import kotlin.math.abs
import kotlin.test.*
import java.io.File

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
                // The new tap-only source reaches204px in a186px bar. The old
                // always-contained assertion was an inferred invariant, not iOS evidence.
                assertTrue(e.height <= bar.height * 1.23f)
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

    @Test fun movementBeyondAnEndAnchorDoesNotInventSelectorTravel() {
        for (edge in listOf(0, bar.count - 1)) {
            val sign = if (edge == 0) -1f else 1f
            val c = controller(); c.snapToRest(edge)
            c.pointerDown(bar.centreOf(edge), 32f, 0.0, true)
            advance(c, 0f, .8f)
            c.beginDrag(bar.centreOf(edge), 32f)
            c.pointerMove(bar.centreOf(edge) + sign * 1000f, 32f, .8)
            advance(c, .8f, 2f)
            val held = GlassPoseExtents().also(c::extents)
            val centre = c.centreX
            var maximumWidthError = 0f
            var maximumHeightError = 0f
            // The target is already saturated. More outward travel, reversing while still
            // outside, and diagonal movement must not act like travel between tabs.
            for (i in 1..120) {
                val t = 2f + i / 120f
                val distance = if ((i / 12) % 2 == 0) 1000f else 10000f
                c.pointerMove(bar.centreOf(edge) + sign * distance, if (i % 2 == 0) -2000f else 2000f, t.toDouble())
                c.advanceFrameTo(t)
                val e = GlassPoseExtents().also(c::extents)
                maximumWidthError = maxOf(maximumWidthError, abs(e.width - held.width))
                maximumHeightError = maxOf(maximumHeightError, abs(e.height - held.height))
                assertEquals(centre, c.centreX, .5f, "stationary end target moved")
                assertFalse(c.solverFailed)
            }
            println("CALM_END_ANCHOR edge=$edge widthError=$maximumWidthError heightError=$maximumHeightError")
            assertTrue(maximumWidthError < .5f, "unavailable travel stretched selector by $maximumWidthError px")
            assertTrue(maximumHeightError < .5f, "unavailable travel squeezed selector by $maximumHeightError px")
            // Returning to the tab range must resume deformation and preserve navigation intent.
            val destination = if (edge == 0) 2 else 1
            for (i in 1..60) {
                val x = bar.centreOf(edge) + (bar.centreOf(destination) - bar.centreOf(edge)) * i / 60f
                c.pointerMove(x, 32f, (3f + i / 120f).toDouble())
            }
            assertTrue(c.elongationPx > 1f, "real travel lost its shape response")
            c.pointerUp(destination); advance(c, 3.5f, 5.5f)
            assertEquals(bar.centreOf(destination), c.centreX, .5f)
        }
    }

    @Test fun heldAndReleasedTravelKeepTheirShapeResponseWithoutReinflating() {
        val rows = arrayListOf("gesture,target,time,width,height,formation")
        fun trace(target: Int, release: Boolean): Float {
            val c = controller()
            c.pointerDown(bar.centreOf(1), 32f, 0.0, true)
            advance(c, 0f, .8f)
            if (release) c.pointerUp(target) else c.beginDrag(bar.centreOf(1), 32f)
            var maxSpine = 0f
            var previousForm = c.formation
            for (i in 1..240) {
                val elapsed = i / 120f
                if (!release && elapsed <= .25f) {
                    val x = bar.centreOf(1) + (bar.centreOf(target) - bar.centreOf(1)) * elapsed / .25f
                    c.pointerMove(x, 32f, (.8f + elapsed).toDouble())
                }
                c.advanceTo(.8f + elapsed)
                val e = GlassPoseExtents().also(c::extents)
                maxSpine = maxOf(maxSpine, c.elongationPx)
                assertTrue(e.height <= bar.height * 1.23f, "reference height ceiling exceeded")
                assertFalse(c.solverFailed, "invalid contour during travel/recovery")
                if (release) {
                    assertTrue(c.formation <= previousForm + .0001f, "throw re-formed pressed material")
                    previousForm = c.formation
                }
                rows += "${if (release) "throw" else "held"},$target,$elapsed,${e.width},${e.height},${c.formation}"
            }
            if (!release) { c.pointerUp(target); advance(c, 2.8f, 4.8f) }
            val end = GlassPoseExtents().also(c::extents)
            assertEquals(78f, end.width, .5f)
            assertEquals(58f, end.height, .5f)
            return maxSpine
        }
        for (release in listOf(false, true)) {
            val short = trace(2, release); val long = trace(3, release)
            assertTrue(short > 2f, "travel did not deform: release=$release spine=$short")
            assertTrue(long > short + 2f, "long trip did not deform more: release=$release $short $long")
            println("CALM_HELD_THROW release=$release shortSpine=$short longSpine=$long")
        }
        File("build/reports/atlas").apply { mkdirs() }
            .resolve("selector-travel.csv").writeText(rows.joinToString("\n") + "\n")
    }

    @Test fun reversingAndThrowingKeepTheSameShapeAtDifferentFrameRates() {
        fun trace(hz: Int): List<FloatArray> {
            val c = controller()
            c.pointerDown(bar.centreOf(1), 32f, 0.0, true)
            advance(c, 0f, .8f); c.beginDrag(bar.centreOf(1),32f)
            val results = arrayListOf<FloatArray>()
            // Pointer samples always arrive at 120Hz. Only presentation cadence changes.
            var nextFrame = .8f + 1f/hz
            for (i in 1..240) {
                val t = .8f + i/120f
                while (nextFrame < t - 1e-5f) { c.advanceFrameTo(nextFrame); nextFrame += 1f/hz }
                if (i <= 60) {
                    val fraction = if (i <= 30) i/30f else (60-i)/30f
                    c.pointerMove(bar.centreOf(1) + bar.slotWidth * 2f * fraction,32f,t.toDouble())
                }
                if (i == 60) c.pointerUp(3)
                if (i % 12 == 0) {
                    c.advanceFrameTo(t)
                    val e=GlassPoseExtents().also(c::extents)
                    assertTrue(e.height <= bar.height * 1.23f)
                    assertFalse(c.solverFailed)
                    results += floatArrayOf(e.left,e.top,e.width,e.height,c.formation)
                }
            }
            return results
        }
        val reference=trace(120)
        for(hz in listOf(30,60,90)) {
            val candidate=trace(hz)
            for(i in reference.indices) for(j in 0..4)
                assertEquals(reference[i][j],candidate[i][j],if(j==4) .015f else 1.5f,
                    "frame-dependent motion: $hz Hz sample=$i coordinate=$j")
        }
    }

    @Test fun aMoveAfterAnIdleHoldResumesWithoutReplayingTheIdleGap() {
        val c=controller()
        c.pointerDown(bar.centreOf(1),32f,1_825_000.0,true)
        advance(c,0f,1f)
        assertTrue(c.isIdle)
        val before=c.centreX
        c.pointerMove(bar.centreOf(2),32f,1_825_010.0)
        val resumed=c.resumedFrameTime(1f)
        assertTrue(resumed>=10f,"frame clock retained ten seconds of hidden input time")
        c.advanceFrameTo(resumed+.1f)
        assertTrue(c.centreX>before+20f,"input remained frozen after waking")
        c.pointerUp(2);advance(c,resumed+.1f,resumed+2f)
        assertEquals(bar.centreOf(2),c.centreX,.5f)
    }
}
