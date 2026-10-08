package com.wexpa.liquidglass

import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Spatial envelope only: IMG6735 has no recorded input timestamps. */
class GlassHeldTravelReferenceTest {
    private fun referenceBar() = GlassSelectorBar(1044f, 186f, 27f, 5, 93f, 115f, 81f, 90f,
        150f, 112.5f, 3f, 700f)

    @Test fun tuningHeldTravelDoesNotChangeTheRestingTapCurve() {
        val bar = referenceBar()
        fun controller(spec: GlassPoseSpec) = GlassPoseController(spec).also {
            it.attach(bar, null, null); it.snapToRest(0); it.retarget(4)
        }
        val current = controller(GlassPoseSpec.Calm)
        val previous = controller(GlassPoseSpec.Calm.copy(
            heldTravelPressureOmega = 0f, squeeze = .10f, elongationSlots = .45f))
        for (i in 0..480) {
            current.advanceTo(i / 480f); previous.advanceTo(i / 480f)
            val a = GlassPoseExtents().also(current::extents)
            val b = GlassPoseExtents().also(previous::extents)
            assertEquals(b.width, a.width, .001f, "held gain clipped independent tap shape")
            assertEquals(b.height, a.height, .001f, "held gain changed tap recoil")
        }
    }

    @Test fun ordinaryHoldGrowthIsUnchangedAndEarlyFastTravelStaysBounded() {
        val bar = referenceBar()
        fun controller(spec: GlassPoseSpec) = GlassPoseController(spec).also {
            it.attach(bar, null, null); it.snapToRest(0)
            it.pointerDown(bar.centreOf(0), bar.centreY, 0.0, true)
        }
        val current = controller(GlassPoseSpec.Calm)
        val previous = controller(GlassPoseSpec.Calm.copy(
            heldTravelPressureOmega = 0f, squeeze = .10f, elongationSlots = .45f))
        for (i in 0..480) {
            current.advanceTo(i / 480f); previous.advanceTo(i / 480f)
            val a = GlassPoseExtents().also(current::extents)
            val b = GlassPoseExtents().also(previous::extents)
            assertEquals(b.width, a.width, .001f, "ordinary press width changed")
            assertEquals(b.height, a.height, .001f, "ordinary press height changed")
        }
        for (wait in listOf(0f, .06f, .12f, .8f)) {
            val c = controller(GlassPoseSpec.Calm)
            c.advanceTo(wait); c.beginDrag(bar.centreOf(0), bar.centreY)
            for (i in 1..120) {
                val elapsed = i / 480f
                val progress = (elapsed / .06f).coerceAtMost(1f)
                c.pointerMove(bar.centreOf(0) + (bar.centreOf(4) - bar.centreOf(0)) * progress,
                    bar.centreY, (wait + elapsed).toDouble())
                c.advanceFrameTo(wait + elapsed)
                val e = GlassPoseExtents().also(c::extents)
                assertTrue(e.height >= 136f, "early travel collapsed below the original bound")
                assertTrue(e.height <= bar.height * 1.23f)
                assertFalse(c.solverFailed)
            }
        }
    }

    @Test fun strongTravelCanReachTheOriginalFlattenedPairWithoutIncreasingOffAxisPull() {
        val bar = referenceBar()
        val rows = arrayListOf("duration,time,width,height,phase")
        var best = Float.POSITIVE_INFINITY
        var pairedWidth = 0f
        var pairedHeight = 0f
        var bestDuration = 0f
        var minimumHeight = Float.POSITIVE_INFINITY
        var middleError = Float.POSITIVE_INFINITY
        var middleWidth = 0f
        var middleHeight = 0f
        for (duration in listOf(.06f, .08f, .10f, .14f, .20f, .30f, .50f, 1f, 1.5f)) {
            val controller = GlassPoseController(GlassPoseSpec.Calm)
            controller.attach(bar, null, null); controller.snapToRest(0)
            controller.pointerDown(bar.centreOf(0), bar.centreY, 0.0, true)
            for (i in 1..384) controller.advanceTo(i / 480f)
            controller.beginDrag(bar.centreOf(0), bar.centreY)
            val extent = GlassPoseExtents()
            for (i in 1..960) {
                val elapsed = i / 480f
                if (elapsed <= duration) {
                    val x = bar.centreOf(0) + (bar.centreOf(4) - bar.centreOf(0)) * elapsed / duration
                    controller.pointerMove(x, bar.centreY, (.8f + elapsed).toDouble())
                }
                controller.advanceFrameTo(.8f + elapsed); controller.extents(extent)
                assertTrue(controller.isHeld)
                assertFalse(controller.solverFailed)
                val phase = (controller.centreX - bar.centreOf(0)) / (bar.centreOf(4) - bar.centreOf(0))
                rows += "$duration,$elapsed,${extent.width},${extent.height},$phase"
                val error = abs(extent.width - 314f) + abs(extent.height - 142f)
                if (error < best) {
                    best = error; pairedWidth = extent.width; pairedHeight = extent.height
                    bestDuration = duration
                }
                val intermediate = abs(extent.width - 326f) + abs(extent.height - 174f)
                if (intermediate < middleError) {
                    middleError = intermediate; middleWidth = extent.width; middleHeight = extent.height
                }
                minimumHeight = minOf(minimumHeight, extent.height)
            }
            assertEquals(223.2f, extent.height, .5f, "stationary hold failed to recover")
        }
        File("build/reports/atlas").apply { mkdirs() }.resolve("held-travel-reference.csv")
            .writeText(rows.joinToString("\n") + "\n")
        println("HELD_PAIR width=$pairedWidth height=$pairedHeight duration=$bestDuration minHeight=$minimumHeight")
        println("HELD_INTERMEDIATE width=$middleWidth height=$middleHeight")
        // Untimed strong-travel IMG6735: outer bar186px, selector314x142px; optical
        // edge ambiguity admits6px. Authored speed sweep tests reachability, not input timing.
        assertEquals(314f, pairedWidth, 6f, "strong travel width")
        assertEquals(142f, pairedHeight, 6f, "strong travel cannot flatten to the original")
        assertTrue(minimumHeight >= 136f, "travel exceeds the selected conservative compression bound")
        assertEquals(326f, middleWidth, 6f, "intermediate travel remains too narrow")
        assertEquals(174f, middleHeight, 6f, "intermediate travel height")
    }
}
