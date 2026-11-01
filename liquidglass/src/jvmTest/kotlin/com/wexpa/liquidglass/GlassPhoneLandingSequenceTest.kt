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
                val c=GlassPoseController(GlassPoseSpec.CalmImpact.copy(releaseEdgeAnchor=true,
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

    /** The measured post-landing narrowing (241 -> 285 px), reproduced by the retained impact
     * model here and by current Calm's two-edge landing in calmLandsAgainstTheWall... below. */
    private fun throwing(reverse: Boolean = false, hold: Float = .8f, duration: Float = .1f,
                         spec: GlassPoseSpec = GlassPoseSpec.CalmImpact): GlassPoseController {
        val start=if(reverse)2 else 0
        val target=2-start
        val c=GlassPoseController(spec)
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

    /** The first two-edge landing (2026-10-14, [GlassPoseSpec.CalmCriticalLanding]): the same
     * measured series on the authored test throw, without the impact model's area exchange. The leading edge stops at the bar's end; the trailing edge carries
     * on toward the wall and springs back, so the pill narrows from behind and never grows. */
    @Test fun criticalLandingNarrowsFromBehindOnTheTestThrow() {
        val right=floatArrayOf(241f,241f,242f,244f,247f,250f,253f,257f,261f,264f,267f,
            271f,274f,276f,278f,281f,282f,283f,284f,285f,285f,285f,285f,284f,284f,284f,284f,284f,285f)
        val left=floatArrayOf(239f,241f,242f,245f,248f,251f,255f,258f,262f,265f,269f,
            272f,274f,277f,278f,281f,282f,283f,284f,283f,283f,284f,284f)
        val held=bar.height*GlassPoseSpec.CalmCriticalLanding.heldHeightRatio
        for(reverse in listOf(false,true)) {
            val c=throwing(reverse, spec=GlassPoseSpec.CalmCriticalLanding)
            val samples=mutableListOf<FloatArray>()
            repeat(721) { i ->
                c.advanceTo(.9f+i/480f)
                assertFalse(c.solverFailed)
                val e=GlassPoseExtents().also(c::extents)
                samples+=floatArrayOf(i/480f,e.width,c.centreX,e.height)
            }
            val wall=if(reverse)0f else bar.width
            val sign=if(reverse)-1f else 1f
            for(s in samples) {
                // Leading edge: at most a few pixels past the bar's end, never flying beyond it.
                assertTrue(sign*(s[2]+sign*s[1]*.5f-wall)<4f,"reverse=$reverse t=${s[0]} leading edge past the wall")
                assertTrue(s[3]<=held+1f,"reverse=$reverse t=${s[0]} landing grew taller than held")
            }
            val peak=samples.indices.filter { samples[it][0]>.05f && samples[it][0]<.5f }.minBy { samples[it][1] }
            val expected=if(reverse)left else right
            for(i in expected.indices) assertEquals(expected[i],samples[peak+i*8][1],7f,"reverse=$reverse sample=$i width")
            assertTrue(samples.drop(peak).maxOf { it[1] }<=285.5f,"unobserved width rebound")
            assertTrue(c.isIdle)
        }
        // The previous Calm never narrowed: its minimum after landing stayed near rest.
        val free=throwing(spec=GlassPoseSpec.CalmFreeLanding)
        var narrowest=Float.MAX_VALUE
        repeat(240) { i -> free.advanceTo(1f+i/480f); narrowest=minOf(narrowest,GlassPoseExtents().also(free::extents).width) }
        assertTrue(narrowest>270f,"CalmFreeLanding changed: $narrowest")
    }

    /** The Phone bar as PhoneScene lays it out, in video px (3 px/dp): 8 dp item inset, 95 dp pill. */
    private val phoneBar: GlassSelectorBar = run {
        val width = 834f; val height = 186f; val inset = 24f; val density = 3f
        val slot = (width - inset * 2f) / 3f
        val rest = minOf(285f / 2f, (inset + slot / 2f - density).coerceAtLeast(density))
        val half = minOf(height / 2f - 12f, rest)
        GlassSelectorBar(width, height, inset, 3, height / 2f, rest, half,
            maxOf(half, height / 2f - density), half, rest, density, width)
    }

    /**
     * Calm (2026-10-14, second pass): video2's own first right- and left-wall throws, replayed
     * from the inferred finger (tools/reference-replay/v2-script.txt, bar-local x = video x - 63),
     * against the measured consecutive widths **at their own timestamps**, not re-aligned at the
     * narrowest frame. The trailing edge travels on underdamped toward the wall
     * ([GlassPoseSpec.releaseTrailingInOmega]) and keeps a tenth of its speed at the impact; the
     * earlier critical landing narrowed about 0.15 s early (rms 14.4 / 21.5 px on these series;
     * now 3.0 / 6.3; pass 12's held travel strain, 3.1 / 6.1). The finger leads the ink by 33 ms
     * (v2script.py LEAD).
     */
    @Test fun calmLandsAgainstTheWallAndNarrowsFromBehind() {
        val right = floatArrayOf(241f,241f,242f,244f,247f,250f,253f,257f,261f,264f,267f,
            271f,274f,276f,278f,281f,282f,283f,284f,285f,285f,285f,285f,284f,284f,284f,284f,284f,285f)
        val left = floatArrayOf(239f,241f,242f,245f,248f,251f,255f,258f,262f,265f,269f,
            272f,274f,277f,278f,281f,282f,283f,284f,283f,283f,284f,284f)
        // (ms, op, video x): down, moves, up as GlassTabBar issues them. The press is held
        // (past the hold threshold) before it moves, so the drag needs no separate beginDrag.
        val rightThrow = listOf(Triple(1203,"down",217f), Triple(2124,"move",285.4f), Triple(2140,"move",399.3f),
            Triple(2157,"move",513.2f), Triple(2174,"move",627.1f), Triple(2190,"move",742f),
            Triple(2206,"move",884.4f), Triple(2207,"up",884.4f))
        val leftThrow = listOf(Triple(3386,"down",742f), Triple(4157,"move",681.7f), Triple(4174,"move",586.9f),
            Triple(4190,"move",492.1f), Triple(4207,"move",397.2f), Triple(4224,"move",248.5f),
            Triple(4240,"move",217f), Triple(4256,"move",153f), Triple(4257,"up",153f))
        val held = phoneBar.height * GlassPoseSpec.Calm.heldHeightRatio
        for ((events, series, start, tolerance) in listOf(
            Quad(rightThrow, right, 2.523333, 7f), Quad(leftThrow, left, 4.573333, 12f))) {
            val target = if (series === right) 2 else 0
            val c = GlassPoseController(GlassPoseSpec.Calm)
            c.attach(phoneBar, null, null); c.snapToRest(2 - target)
            val e = GlassPoseExtents()
            val widths = mutableListOf<Pair<Double, Float>>()
            var next = 0
            var frame = (events.first().first / 1000.0 * 60).toInt()
            while (frame / 60.0 < start + series.size / 60.0 + .4) {
                val t = frame / 60.0
                while (next < events.size && events[next].first / 1000.0 <= t) {
                    val (ms, op, x) = events[next++]
                    when (op) {
                        "down" -> { c.pointerDown(x - 63f, phoneBar.centreY, ms / 1000.0, true); c.retarget(2 - target) }
                        "move" -> c.pointerMove(x - 63f, phoneBar.centreY, ms / 1000.0)
                        "up" -> c.pointerUp(target)
                    }
                }
                c.advanceFrameTo(t.toFloat())
                assertFalse(c.solverFailed)
                c.extents(e)
                widths += t to e.width
                if (c.mode == GlassPoseMode.Released) {
                    val wall = if (target == 2) phoneBar.width else 0f
                    val lead = if (target == 2) e.right - wall else wall - e.left
                    // A formed lens presses past the wall by at most its formation's share of the
                    // give, and is back at the wall once it is the pill.
                    val give = GlassPoseSpec.Calm.releaseWallGive * phoneBar.height * c.formation
                    assertTrue(lead < give + 4f, "target=$target t=$t leading edge $lead px past the wall (give $give)")
                    assertTrue(e.height <= held + 1f, "target=$target t=$t landing grew taller than held")
                }
                frame++
            }
            var squares = 0.0
            for (i in series.indices) {
                val t = start + i / 60.0
                val k = widths.indexOfFirst { it.first >= t }
                val (t0, w0) = widths[k - 1]; val (t1, w1) = widths[k]
                val ours = w0 + (w1 - w0) * ((t - t0) / (t1 - t0)).toFloat()
                assertEquals(series[i], ours, tolerance, "target=$target t=$t width")
                squares += (ours - series[i]).toDouble().let { it * it }
            }
            val rms = kotlin.math.sqrt(squares / series.size)
            // Left: its first frames narrow up to 11.2 px less than iOS.
            assertTrue(rms < (if (target == 2) 3.5 else 7.0), "target=$target width rms $rms")
            assertTrue(widths.filter { it.first > start }.maxOf { it.second } <= 285.5f, "unobserved width rebound")
        }
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    /** A lens released still keeps its glass briefly ([GlassPoseSpec.releaseStillHoldSeconds]),
     * then loses it; the controller must not report idle in between, or a host's frame loop
     * sleeps with the lens formed (found in the GPU replay, 2026-10-14). */
    @Test fun aStillReleaseKeepsItsGlassBrieflyThenLosesIt() {
        val c = GlassPoseController(GlassPoseSpec.Calm)
        c.attach(phoneBar, null, null); c.snapToRest(0)
        c.pointerDown(phoneBar.centreOf(0), phoneBar.centreY, 0.0, true)
        c.advanceTo(.6f, .9f)
        assertTrue(c.isHeld && c.formation > .99f)
        c.pointerUp(0)
        var t = .6f
        while (t < .6f + GlassPoseSpec.Calm.releaseStillHoldSeconds - .02f) {
            t += 1f / 60f; c.advanceFrameTo(t)
            assertTrue(c.formation > .95f, "still release lost its glass early at $t")
            assertFalse(c.isIdle, "idle while holding its glass at $t")
        }
        while (c.formation > .01f && t < 2f) {
            assertFalse(c.isIdle, "idle with the lens still formed (${c.formation}) at $t")
            t += 1f / 60f; c.advanceFrameTo(t)
        }
        assertTrue(c.formation <= .01f, "the still-released lens never lost its glass")
        // The previous Calm loses it at once.
        val old = GlassPoseController(GlassPoseSpec.CalmCriticalLanding)
        old.attach(phoneBar, null, null); old.snapToRest(0)
        old.pointerDown(phoneBar.centreOf(0), phoneBar.centreY, 0.0, true); old.advanceTo(.6f, .9f)
        old.pointerUp(0); old.advanceTo(.7f)
        assertTrue(old.formation < .9f)
    }

    /** #106, audit item 104: IMG_6756's still-released lens (three holds) keeps its outline ~43 ms
     * after the lift and is flat by ~93 ms. The replay (holds-41) collapses at 60-77 ms; here the
     * formation has to still be whole at 25 ms and mostly gone by 65 ms. Authored rate. */
    @Test fun aStillReleaseLosesItsGlassAsQuicklyAsIos27() {
        val c = GlassPoseController(GlassPoseSpec.Calm)
        c.attach(phoneBar, null, null); c.snapToRest(0)
        c.pointerDown(phoneBar.centreOf(0), phoneBar.centreY, 0.0, true)
        c.advanceTo(.6f, .9f)
        assertTrue(c.isHeld && c.formation > .99f)
        c.pointerUp(0)
        c.advanceFrameTo(.625f)
        assertTrue(c.formation > .95f, "lost its glass before the hold: ${c.formation}")
        c.advanceFrameTo(.665f)
        assertTrue(c.formation < .3f, "still formed 65 ms after the lift: ${c.formation}")
        c.advanceFrameTo(.71f)
        assertTrue(c.formation < .02f, "still formed 110 ms after the lift: ${c.formation}")
    }

    /** #106, audit item 107: a still press forms no lens before it becomes a hold (iOS shows none
     * before ~100 ms in IMG_6756), then rises at heldFormRiseOmega: about half by 180 ms and nearly
     * whole by 300 ms, as the replay's lens level follows iOS's from 170 ms on. */
    @Test fun aStillPressFormsOnlyOnceItIsAHold() {
        val c = GlassPoseController(GlassPoseSpec.Calm)
        c.attach(phoneBar, null, null); c.snapToRest(0)
        c.pointerDown(phoneBar.centreOf(0), phoneBar.centreY, 0.0, true)
        c.advanceFrameTo(.09f)
        assertFalse(c.isHeld, "held before the threshold")
        assertEquals(0f, c.formation, 1e-4f, "formed before the hold")
        c.advanceFrameTo(.115f)
        assertTrue(c.isHeld, "not held at 115 ms")
        c.advanceFrameTo(.182f)
        assertTrue(c.formation in .4f..0.65f, "formation at 182 ms: ${c.formation}")
        c.advanceFrameTo(.3f)
        assertTrue(c.formation > .93f, "not formed by 300 ms: ${c.formation}")
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
