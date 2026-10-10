package com.wexpa.liquidglass

import java.io.File
import kotlin.math.abs
import kotlin.test.*

/** Authored input sweeps: the reference recording does not expose finger-up timing. */
class GlassPhoneThrowTest {
    private val bar = GlassSelectorBar(834f,186f,21.75f,3,93f,142.5f,80f,90f,150f,150f,3f,800f)

    @Test fun exportEndpointCompressionCandidates() {
        val output = File("build/reports/atlas/phone-throw-candidates.csv")
        output.parentFile.mkdirs()
        output.bufferedWriter().use { out ->
            out.appendLine("gain,hold,dragDuration,distanceSlots,time,cx,width,height,formation")
            for (gain in listOf(0f,.25f,.5f,1f,2f,2.25f,4f,8f)) {
                var minimum = Float.MAX_VALUE
                var maximumHeight = 0f
                for (hold in listOf(.06f,.8f)) for (duration in listOf(.06f,.1f,.24f)) {
                    val c=GlassPoseController(GlassPoseSpec.Calm.copy(releaseImpactGain=gain))
                    c.attach(bar,null,null,0f); c.snapToRest(0)
                    c.pointerDown(bar.centreOf(0),bar.centreY,0.0,true)
                    c.advanceTo(hold,.5f); c.beginDrag(bar.centreOf(0),bar.centreY)
                    val steps=(duration*480).toInt()
                    for (i in 1..steps) {
                        val t=hold+duration*i/steps
                        c.pointerMove(bar.centreOf(0)+2f*bar.slotWidth*i/steps,bar.centreY,t.toDouble())
                        c.advanceFrameTo(t)
                    }
                    c.pointerUp(2)
                    val e=GlassPoseExtents()
                    for(i in 0..960) {
                        val t=i/480f
                        c.advanceTo(hold+duration+t,.5f); c.extents(e)
                        assertFalse(c.solverFailed)
                        minimum=minOf(minimum,e.width); maximumHeight=maxOf(maximumHeight,e.height)
                        out.appendLine("$gain,$hold,$duration,2,$t,${c.centreX},${e.width},${e.height},${c.formation}")
                    }
                    assertEquals(285f,e.width,.5f)
                    assertEquals(160f,e.height,.5f)
                    assertTrue(c.isIdle,"release never sleeps gain=$gain duration=$duration")
                }
                println("PHONE_THROW gain=$gain minWidth=$minimum maxHeight=$maximumHeight")
            }
        }
    }

    private fun throwing(spec: GlassPoseSpec = GlassPoseSpec.Calm, reverse: Boolean = false,
                         duration: Float = .1f, target: Int = if (reverse) 0 else 2): GlassPoseController {
        val start=if(reverse)2 else 0
        val c=GlassPoseController(spec)
        c.attach(bar,null,null);c.snapToRest(start)
        c.pointerDown(bar.centreOf(start),bar.centreY,0.0,true)
        c.advanceTo(.8f,.9f);c.beginDrag(bar.centreOf(start),bar.centreY)
        val steps=(duration*480).toInt()
        for(i in 1..steps) {
            val t=.8f+duration*i/steps
            c.pointerMove(bar.centreOf(start)+(bar.centreOf(target)-bar.centreOf(start))*i/steps,
                bar.centreY,t.toDouble())
            c.advanceFrameTo(t)
        }
        val before=GlassPoseExtents().also(c::extents)
        val vx=c.centreVelocityX
        c.pointerUp(target)
        val after=GlassPoseExtents().also(c::extents)
        assertEquals(before.width,after.width,0f,"UP changed shape")
        assertEquals(vx,c.centreVelocityX,0f,"UP changed velocity")
        return c
    }

    @Test fun hardLandingCanReachTheObservedNarrowBodyAndLargeCapTogether() {
        val c=throwing()
        var best=Float.POSITIVE_INFINITY
        var pair=FloatArray(3)
        for(i in 0..480) {
            c.advanceTo(.9f+i/480f)
            val e=GlassPoseExtents().also(c::extents)
            // Source082: cross-section width242, partial left-cap circle R116.14.
            // Eight/six-pixel diagnostic gates include contour-vs-optical measurement.
            // The inferred full height is NOT a directly visible source measurement.
            val error=maxOf(abs(e.width-242f)/8f,abs(e.height/2f-116.14f)/6f)
            if(error<best) {best=error;pair=floatArrayOf(e.width,e.height/2,c.centreX)}
            assertFalse(c.solverFailed)
        }
        println("PHONE_LANDING pairedWidth=${pair[0]} capRadius=${pair[1]} centre=${pair[2]} score=$best")
        assertTrue(best<=1f,"no paired landing silhouette: ${pair.toList()}")
    }

    @Test fun reverseAndPresentationCadencePreserveLandingAndSleep() {
        fun trace(hz: Int,reverse: Boolean): List<FloatArray> {
            val c=throwing(reverse=reverse)
            val result=mutableListOf<FloatArray>()
            var next=.9f+1f/hz
            for(i in 1..240) {
                val t=.9f+i/120f
                while(next<t-1e-6f) { c.advanceFrameTo(next);next+=1f/hz }
                c.advanceFrameTo(t)
                val e=GlassPoseExtents().also(c::extents)
                assertFalse(c.solverFailed)
                result+=floatArrayOf(if(reverse)bar.width-c.centreX else c.centreX,e.width,e.height,c.formation)
            }
            assertTrue(c.isIdle,"landing does not stop scheduling frames")
            assertEquals(if(reverse)bar.firstCentre else bar.lastCentre,c.centreX,.01f)
            return result
        }
        val expected=trace(120,false)
        for(hz in listOf(30,60,90,120)) for(reverse in listOf(false,true)) {
            val actual=trace(hz,reverse)
            for(i in actual.indices) for(j in 0..3) assertEquals(expected[i][j],actual[i][j],
                if(j==3).01f else .75f,"$hz Hz reverse=$reverse sample=$i coordinate=$j")
        }
    }

    @Test fun middleLandingsAndStationaryHoldsKeepTheirPriorResponse() {
        for(target in listOf(0,1)) {
            val old=throwing(GlassPoseSpec.CalmVolume,target=target)
            val current=throwing(target=target)
            for(i in 0..240) {
                val t=.9f+i/120f
                old.advanceTo(t);current.advanceTo(t)
                val a=GlassPoseExtents().also(old::extents)
                val b=GlassPoseExtents().also(current::extents)
                assertEquals(a.width,b.width,0f);assertEquals(a.height,b.height,0f)
            }
        }
    }

    @Test fun regrabAndReducedMotionClearLandingOwnership() {
        val c=throwing();c.advanceTo(.98f)
        val before=GlassPoseExtents().also(c::extents)
        val vx=c.centreVelocityX
        c.pointerDown(c.centreX,bar.centreY,.98,true)
        assertTrue(c.isHeld)
        val after=GlassPoseExtents().also(c::extents)
        assertEquals(before.width,after.width,0f)
        assertEquals(vx,c.centreVelocityX,0f)
        for(i in 1..240) {c.advanceTo(.98f+i/120f);assertFalse(c.solverFailed)}
        c.pointerUp(2);c.motionEnabled=false
        c.advanceTo(3f)
        c.extents(after)
        assertEquals(285f,after.width,.1f)
        assertEquals(160f,after.height,.1f)
    }

    @Test fun extremeThrowsStayFiniteContinuousAndInsideTheAllocatedEnvelope() {
        for(count in listOf(3,5)) for(scale in listOf(.5f,1f,2f))
            for(duration in listOf(.008f,.04f,.1f,.4f)) {
            val b=GlassSelectorBar(width=(if(count==3)834f else 1390f)*scale,height=186f*scale,
                inset=21.75f*scale,count=count,cornerRadius=93f*scale,baseHalfWidth=142.5f*scale,
                baseHalfHeight=80f*scale,allowedHalfHeight=90f*scale,heldHalfWidth=150f*scale,
                heldHalfHeight=150f*scale,density=3f*scale,maxBodyWidth=800f*scale)
            val c=GlassPoseController(GlassPoseSpec.Calm)
            c.attach(b,null,null);c.snapToRest(0)
            c.pointerDown(b.firstCentre,b.centreY,0.0,true);c.advanceTo(.8f,.9f)
            c.beginDrag(b.firstCentre,b.centreY)
            val steps=maxOf(2,(duration*480).toInt())
            for(i in 1..steps) {
                val t=.8f+duration*i/steps
                // Continue far beyond the screen, including a large perpendicular component.
                c.pointerMove(b.firstCentre+20f*b.slotWidth*i/steps,-10000f*scale,t.toDouble())
                c.advanceFrameTo(t)
            }
            c.pointerUp(count-1)
            var previous=GlassPoseExtents().also(c::extents).width
            for(i in 1..960) {
                c.advanceTo(.8f+duration+i/480f)
                val e=GlassPoseExtents().also(c::extents)
                assertFalse(c.solverFailed)
                assertTrue(e.width.isFinite() && e.height.isFinite())
                assertTrue(e.height<=b.height*(1f+2f*GlassPoseSpec.Calm.heldExcursionRatio))
                assertTrue(abs(e.width-previous)<b.baseHalfWidth*.24f,"discontinuous collapsed spine")
                previous=e.width
            }
            assertTrue(c.isIdle)
            assertEquals(b.lastCentre,c.centreX,.01f)
        }
    }
}
