package com.wexpa.liquidglass

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.math.abs

/** Consecutive reference series, not another independently selected best frame. */
class GlassPhoneLandingSequenceTest {
    private val bar = GlassSelectorBar(834f,186f,21.75f,3,93f,142.5f,80f,90f,150f,150f,3f,800f)

    @Test fun exportEdgeRecoveryExperiments() {
        val output=File("build/reports/atlas/phone-edge-candidates.csv")
        output.parentFile.mkdirs()
        output.bufferedWriter().use { out ->
            out.appendLine("gain,recoveryOmega,recoveryZeta,hold,dragDuration,time,cx,width,height,formation")
            for(gain in listOf(1f,2f,3f,4f,6f)) for(omega in listOf(10f,14f,18f,22f)) {
                val c=GlassPoseController(GlassPoseSpec.Calm.copy(releaseEdgeAnchor=true,
                    releaseImpactGain=gain,releaseRecoveryOmega=omega))
                c.attach(bar,null,null);c.snapToRest(0)
                c.pointerDown(bar.firstCentre,bar.centreY,0.0,true)
                c.advanceTo(.8f,.9f);c.beginDrag(bar.firstCentre,bar.centreY)
                for(i in 1..48) {
                    val t=.8f+.1f*i/48
                    c.pointerMove(bar.firstCentre+2f*bar.slotWidth*i/48,bar.centreY,t.toDouble())
                    c.advanceFrameTo(t)
                }
                val before=GlassPoseExtents().also(c::extents)
                val vx=c.centreVelocityX
                c.pointerUp(2)
                val e=GlassPoseExtents().also(c::extents)
                assertEquals(before.width,e.width,0f)
                assertEquals(vx,c.centreVelocityX,0f)
                for(i in 0..1440) {
                    val t=i/480f
                    c.advanceTo(.9f+t,.5f);c.extents(e)
                    assertFalse(c.solverFailed,"gain=$gain omega=$omega t=$t")
                    assertTrue(e.width.isFinite() && e.height.isFinite())
                    out.appendLine("$gain,$omega,1.0,0.8,0.1,$t,${c.centreX},${e.width},${e.height},${c.formation}")
                }
                assertTrue(c.isIdle,"gain=$gain omega=$omega does not settle")
                assertEquals(285f,e.width,.1f)
                assertEquals(160f,e.height,.1f)
            }
        }
    }

    private fun throwing(reverse: Boolean = false, hold: Float = .8f, duration: Float = .1f): GlassPoseController {
        val start=if(reverse)2 else 0
        val target=2-start
        val c=GlassPoseController(GlassPoseSpec.Calm)
        c.attach(bar,null,null);c.snapToRest(start)
        c.pointerDown(bar.centreOf(start),bar.centreY,0.0,true)
        c.advanceTo(hold,.9f);c.beginDrag(bar.centreOf(start),bar.centreY)
        val steps=maxOf(2,(duration*480).toInt())
        for(i in 1..steps) {
            val t=hold+duration*i/steps
            c.pointerMove(bar.centreOf(start)+(bar.centreOf(target)-bar.centreOf(start))*i/steps,
                bar.centreY,t.toDouble())
            c.advanceFrameTo(t)
        }
        c.pointerUp(target)
        return c
    }

    @Test fun consecutiveBilateralRecoveryMatchesWidthAndEndpointFacingEdge() {
        // Native consecutive optical cross-sections, not independently rematched stills.
        // Right2.523333..2.99s, left4.573333..4.94s; all raw RGB probes/PTS are in the
        // phone-landing-sequence-2026-10-09 packet. Same authored input in both directions.
        val right=floatArrayOf(241f,241f,242f,244f,247f,250f,253f,257f,261f,264f,267f,
            271f,274f,276f,278f,281f,282f,283f,284f,285f,285f,285f,285f,284f,284f,284f,284f,284f,285f)
        val left=floatArrayOf(239f,241f,242f,245f,248f,251f,255f,258f,262f,265f,269f,
            272f,274f,277f,278f,281f,282f,283f,284f,283f,283f,284f,284f)
        for(reverse in listOf(false,true)) {
            val c=throwing(reverse)
            val samples=mutableListOf<FloatArray>()
            repeat(481) { i ->
                c.advanceTo(.9f+i/480f)
                val e=GlassPoseExtents().also(c::extents)
                samples+=floatArrayOf(i/480f,e.width,c.centreX,e.height)
            }
            val peak=samples.indices.minBy { samples[it][1] }
            val expected=if(reverse)left else right
            val edgeRest=bar.centreOf(if(reverse)0 else 2)+(if(reverse)-1 else 1)*142.5f
            val leadingEdges=mutableListOf<Float>()
            for(i in expected.indices) {
                val sample=samples[peak+i*8] // Native late-window cadence60Hz, solver samples480Hz.
                assertEquals(expected[i],sample[1],6f,"reverse=$reverse sample=$i width")
                val edge=sample[2]+(if(reverse)-1 else 1)*sample[1]*.5f
                assertEquals(edgeRest,edge,2.5f,"reverse=$reverse sample=$i endpoint edge")
                leadingEdges+=edge
                // Curvature remains diagnostic: partial-arc fits depend on their row window.
                // Do not replace the measured width/edge trajectory with an isolated cap fit.
            }
            assertTrue(leadingEdges.max()-leadingEdges.min()<2.5f)
            val recovery=samples.drop(peak)
            assertTrue(recovery.maxOf { it[1] }<=285.5f,"unobserved broad width rebound")
        }
    }

    @Test fun regrabCarriesTheVelocityOfTheShapeAwareCentre() {
        for(reverse in listOf(false,true)) for(time in listOf(.96f,1.05f,1.14f,1.3f)) {
            val c=throwing(reverse)
            val h=1f/9600f
            c.advanceTo(time-h)
            val x0=c.centreX
            c.advanceTo(time)
            val x1=c.centreX
            val velocity=c.centreVelocityX
            assertEquals((x1-x0)/h,velocity,8f,"stored velocity differs from centre derivative")
            val before=GlassPoseExtents().also(c::extents)
            c.pointerDown(c.centreX,bar.centreY,time.toDouble(),true)
            val after=GlassPoseExtents().also(c::extents)
            assertEquals(x1,c.centreX,0f)
            assertEquals(velocity,c.centreVelocityX,0f)
            assertEquals(before.width,after.width,0f)
            c.advanceTo(time+h)
            assertTrue(abs(c.centreX-x1)<2f,"re-grab jumps after its first advance")
            assertFalse(c.solverFailed)
        }
    }
}
