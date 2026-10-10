package com.wexpa.liquidglass

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** New owner-labelled Phone taps. Spatial phase only; not recovered input timing. */
class GlassPhoneTapVolumeTest {
    private fun bar(scale: Float = 1f, count: Int = 3) = GlassSelectorBar(
        (if (count == 3) 834f else 1044f) * scale, 186f * scale,
        (if (count == 3) 21.75f else 27f) * scale, count, 93f * scale,
        (if (count == 3) 139f else 115f) * scale,
        (if (count == 3) 83.75f else 81f) * scale,
        90f * scale, 150f * scale, 150f * scale, 3f * scale, 800f * scale)

    @Test fun pairedShortAndLongTapShapes() {
        val bar = GlassSelectorBar(834f,186f,21.75f,3,93f,139f,83.75f,90f,150f,150f,3f,800f)
        // Three accepted pairs; 12px width admits optical cross-section vs full bounds,
        // 6px height admits the retained RGB probes. These are diagnostic spatial gates.
        val cases = listOf(
            floatArrayOf(2f,.803605f,396f,159f),
            floatArrayOf(2f,.968691f,342f,151f),
            floatArrayOf(1f,.758364f,329f,204f),
        )
        for ((distance, phase, width, height) in cases) {
            val c = GlassPoseController(GlassPoseSpec.Calm)
            c.attach(bar,null,null); c.snapToRest(0)
            c.pointerDown(bar.centreOf(distance.toInt()),bar.centreY,0.0,true)
            c.retarget(distance.toInt())
            val e = GlassPoseExtents()
            var best = Float.POSITIVE_INFINITY
            var actualWidth = 0f
            var actualHeight = 0f
            var released = false
            for (i in 0..480) {
                val t = i / 480f
                if (!released && t >= .04f) {
                    c.advanceTo(.04f); c.pointerUp(distance.toInt()); released=true
                }
                c.advanceTo(t); c.extents(e)
                assertFalse(c.solverFailed)
                assertFalse(c.isHeld)
                val p = (c.centreX-bar.centreOf(0))/(bar.centreOf(distance.toInt())-bar.centreOf(0))
                if (abs(p-phase)<best) {
                    best=abs(p-phase); actualWidth=e.width; actualHeight=e.height
                }
            }
            println("PHONE_PAIR slots=$distance phase=$phase width=$actualWidth height=$actualHeight")
            assertEquals(width,actualWidth,12f,"Phone tap width at phase $phase")
            assertEquals(height,actualHeight,6f,"Phone tap paired height at phase $phase")
        }
    }

    @Test fun cadenceScaleAndReversePreserveTheShape() {
        fun samples(hz: Int, scale: Float, reverse: Boolean): List<FloatArray> {
            val b=bar(scale)
            val c=GlassPoseController(GlassPoseSpec.Calm)
            c.attach(b,null,null); c.snapToRest(if(reverse)2 else 0); c.retarget(if(reverse)0 else 2)
            val e=GlassPoseExtents()
            val result=mutableListOf<FloatArray>()
            for(i in 0..hz*2) {
                c.advanceFrameTo(i.toFloat()/hz); c.extents(e)
                assertFalse(c.solverFailed)
                assertTrue(e.width.isFinite() && e.height.isFinite())
                assertTrue(e.height/scale in 140f..230f,"tap envelope ${e.height/scale}")
                if(i%(hz/30)==0) result+=floatArrayOf(e.width/scale,e.height/scale)
            }
            assertEquals(b.centreOf(if(reverse)0 else 2),c.centreX,.1f*scale)
            assertEquals(278f*scale,e.width,.5f*scale)
            assertEquals(167.5f*scale,e.height,.5f*scale)
            return result
        }
        val reference=samples(120,1f,false)
        for(hz in listOf(30,60,90,120)) for(scale in listOf(.5f,1f,2f)) {
            val candidate=samples(hz,scale,true)
            for(i in reference.indices) for(axis in 0..1) {
                assertEquals(reference[i][axis],candidate[i][axis],.8f,"$hz Hz scale$scale at$i")
            }
        }
    }

    @Test fun interruptedTapAndHoldTransitionsStayContinuous() {
        val b=bar()
        val c=GlassPoseController(GlassPoseSpec.Calm)
        c.attach(b,null,null); c.snapToRest(0); c.retarget(2)
        val before=GlassPoseExtents(); val after=GlassPoseExtents()
        c.advanceTo(.11f); c.extents(before)
        c.pointerDown(c.centreX,b.centreY,.11,true); c.retarget(0); c.extents(after)
        assertEquals(before.width,after.width,.001f)
        assertEquals(before.height,after.height,.001f)
        c.advanceTo(.24f); assertTrue(c.isHeld)
        c.extents(before); c.pointerUp(0); c.extents(after)
        assertEquals(before.width,after.width,.001f)
        assertEquals(before.height,after.height,.001f)
        for(i in 1..960) {
            c.advanceTo(.24f+i/480f); c.extents(after)
            assertFalse(c.solverFailed)
            assertTrue(after.height.isFinite() && after.width.isFinite())
            assertTrue(after.height in 130f..235f)
        }
        // A later stationary press must use ordinary press growth, not stale tap area.
        val old=GlassPoseController(GlassPoseSpec.CalmIndependent)
        old.attach(b,null,null); old.snapToRest(0)
        c.pointerDown(b.centreOf(0),b.centreY,3.0,true)
        old.pointerDown(b.centreOf(0),b.centreY,3.0,true)
        // Controllers may have different clock origins; compare equal elapsed intervals.
        for(i in 1..96) {
            c.advanceTo(2.24f+i/480f); old.advanceTo(i/480f)
            c.extents(after); old.extents(before)
            assertEquals(before.height,after.height,.5f)
        }
    }

    @Test fun sleepingFrameLoopLeavesExactRestAndCanWakeAgain() {
        for (hz in listOf(30, 60, 90, 120)) for (count in listOf(3, 5)) {
            val b = bar(count = count)
            val c = GlassPoseController(GlassPoseSpec.Calm)
            c.attach(b, null, null); c.snapToRest(0)
            var time = 0f
            for (target in listOf(count - 1, 0, 1)) {
                c.retarget(target)
                assertFalse(c.isIdle, "retarget must wake the frame loop")
                var frames = 0
                while (!c.isIdle && frames < hz * 3) {
                    time += 1f / hz; c.advanceFrameTo(time); frames++
                }
                assertTrue(c.isIdle, "$hz Hz / $count slots did not settle")
                val e = GlassPoseExtents().also(c::extents)
                assertEquals(b.centreOf(target), c.centreX, .001f)
                val resting = GlassPoseController(GlassPoseSpec.Calm)
                resting.attach(b, null, null); resting.snapToRest(target)
                val expected = GlassPoseExtents().also(resting::extents)
                // Extents sample the contour; compare with the same exact rest geometry.
                assertEquals(expected.width, e.width, .001f)
                assertEquals(expected.height, e.height, .001f)
            }
        }
    }

    @Test fun reducedMotionAndRelayoutClearTransientArea() {
        val b=bar()
        val c=GlassPoseController(GlassPoseSpec.Calm)
        c.attach(b,null,null);c.snapToRest(0);c.retarget(2);c.advanceTo(.12f)
        c.motionEnabled=false;c.advanceTo(.13f)
        c.attach(bar(2f),null,null)
        val e=GlassPoseExtents().also(c::extents)
        assertEquals(556f,e.width,.1f);assertEquals(335f,e.height,.1f)
        c.retarget(0);c.advanceTo(.15f);c.extents(e)
        assertEquals(556f,e.width,.1f);assertEquals(335f,e.height,.1f)
    }
}
