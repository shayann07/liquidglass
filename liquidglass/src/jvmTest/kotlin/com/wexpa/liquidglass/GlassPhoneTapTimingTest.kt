package com.wexpa.liquidglass

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Native output PTS and inspected cross-section centres, not recovered pointer times. */
class GlassPhoneTapTimingTest {
    private fun createBar(s: Float=1f)=GlassSelectorBar(834f*s,186f*s,21.75f*s,3,93f*s,
        139f*s,83.75f*s,90f*s,150f*s,150f*s,3f*s,800f*s)
    private val bar=createBar()

    private fun trace(spec: GlassPoseSpec, start: Int = 2, target: Int = 1): List<FloatArray> {
        val c = GlassPoseController(spec)
        c.attach(bar,null,null); c.snapToRest(start)
        c.pointerDown(bar.centreOf(target),bar.centreY,0.0,true); c.retarget(target)
        var released = false
        return (0..960).map { i ->
            val t = i/960f
            if (!released && t >= .04f) { c.advanceTo(.04f); c.pointerUp(target); released=true }
            c.advanceTo(t)
            assertFalse(c.solverFailed)
            val e = GlassPoseExtents().also(c::extents)
            floatArrayOf(t,c.centreX+62f,e.width,e.height)
        }
    }

    @Test fun consecutiveShortTripsFollowOneAlignedTrajectory() {
        // Both windows/failed probes and SHA/PTS provenance are retained in the short-tap
        // research packet. Fit ONE time offset per complete sequence; no per-frame rematch.
        // Finger timing is absent, and pinning the final optical edge exactly magnifies its
        // small spatial uncertainty earlier in the trip. Both candidate and old model get
        // the same one-parameter alignment, and the old response must still fail the gate.
        // 8px allows optical-centre, shared-bar transform and authored resting-origin error.
        val episodes = listOf(
            arrayOf(floatArrayOf(-.15f,735f),floatArrayOf(-.133333f,715.5f),
                floatArrayOf(-.116666f,690.5f),floatArrayOf(-.1f,662.5f),
                floatArrayOf(-.083333f,635f),floatArrayOf(-.066666f,608.5f),
                floatArrayOf(-.05f,584f),floatArrayOf(-.016666f,544f),floatArrayOf(0f,529.5f)),
            arrayOf(floatArrayOf(-.133333f,735.5f),floatArrayOf(-.116667f,718f),
                floatArrayOf(-.1f,693.5f),floatArrayOf(-.083333f,666.5f),
                floatArrayOf(-.066667f,639f),floatArrayOf(-.05f,613.5f),
                floatArrayOf(-.033333f,591f),floatArrayOf(-.016667f,570.5f),floatArrayOf(0f,555f)),
        )
        fun errors(model: List<FloatArray>, source: Array<FloatArray>): FloatArray {
            fun at(time: Float): Float {
                if(time<=0f) return model.first()[1]
                val i=(time*960f).toInt().coerceAtMost(model.lastIndex-1)
                val lo=model[i]; val hi=model[i+1]
                return lo[1]+(time-lo[0])/(hi[0]-lo[0])*(hi[1]-lo[1])
            }
            // 0.1ms search precision is much smaller than the native16.7/33.3ms samples.
            val shift=(0..3500).minBy { i ->
                source.sumOf { (relative,x) -> val d=at(i*.0001f+relative)-x; (d*d).toDouble() }
            }*.0001f
            return source.map { (relative,x) -> at(shift+relative)-x }.toFloatArray()
        }
        val model=trace(GlassPoseSpec.Calm)
        val old=trace(GlassPoseSpec.Calm.copy(tapShortTripRate=1f))
        for ((episode, source) in episodes.withIndex()) {
            val error=errors(model,source); val oldError=errors(old,source)
            val rms=sqrt(error.map { it*it }.average())
            val oldRms=sqrt(oldError.map { it*it }.average())
            println("PHONE_SHORT_TIMING episode=$episode rms=$rms max=${error.maxOf(::abs)} oldRms=$oldRms")
            assertTrue(error.all { abs(it)<=8f },"episode$episode error=${error.toList()}")
            assertTrue(rms<4.5,"episode$episode RMS$rms")
            assertTrue(oldRms>12,"the unchanged response should still fail this evidence gate")
        }
    }

    @Test fun longTripKeepsItsVerifiedTrajectoryAndShape() {
        val current=trace(GlassPoseSpec.Calm,0,2)
        val previous=trace(GlassPoseSpec.Calm.copy(tapShortTripRate=1f),0,2)
        for(i in current.indices) for(axis in 1..3) {
            assertEquals(previous[i][axis],current[i][axis],0f,"long trip sample$i axis$axis")
        }
        assertTrue(current.last()[1] > current.first()[1])
    }

    @Test fun shortTripCadenceScaleAndDirectionStayConsistent() {
        fun samples(hz:Int, scale:Float, reverse:Boolean):List<FloatArray> {
            val b=createBar(scale);val start=if(reverse)2 else 0
            val c=GlassPoseController(GlassPoseSpec.Calm)
            c.attach(b,null,null);c.snapToRest(start);c.retarget(1)
            val result=mutableListOf<FloatArray>()
            for(i in 0..hz*2) {
                c.advanceFrameTo(i.toFloat()/hz)
                assertFalse(c.solverFailed)
                if(i%(hz/30)==0) {
                    val e=GlassPoseExtents().also(c::extents)
                    result+=floatArrayOf(abs(c.centreX-b.centreOf(start))/scale,e.width/scale,e.height/scale)
                }
            }
            assertTrue(c.isIdle,"$hz Hz scale$scale reverse$reverse")
            return result
        }
        val reference=samples(120,1f,false)
        for(hz in listOf(30,60,90,120)) for(scale in listOf(.5f,1f,2f)) for(reverse in listOf(false,true)) {
            val current=samples(hz,scale,reverse)
            for(i in current.indices) for(axis in 0..2)
                assertEquals(reference[i][axis],current[i][axis],.8f,"$hz Hz scale$scale reverse$reverse at$i/$axis")
        }
    }

    @Test fun shortTripRegrabPreservesActualCentreVelocity() {
        for(t in listOf(.04f,.07f,.12f,.20f)) {
            val c=GlassPoseController(GlassPoseSpec.Calm)
            c.attach(bar,null,null);c.snapToRest(2);c.retarget(1)
            val h=1f/9600f
            c.advanceTo(t-h);val before=c.centreX
            c.advanceTo(t);val x=c.centreX;val velocity=c.centreVelocityX
            assertEquals((x-before)/h,velocity,8f,"physical-time derivative at$t")
            val shape=GlassPoseExtents().also(c::extents)
            c.pointerDown(x,bar.centreY,t.toDouble(),true)
            val after=GlassPoseExtents().also(c::extents)
            assertEquals(x,c.centreX,0f);assertEquals(velocity,c.centreVelocityX,0f)
            assertEquals(shape.width,after.width,0f);assertEquals(shape.height,after.height,0f)
            c.advanceTo(t+h);assertFalse(c.solverFailed)
            assertTrue(abs(c.centreX-x)<1f)
        }
    }
}
