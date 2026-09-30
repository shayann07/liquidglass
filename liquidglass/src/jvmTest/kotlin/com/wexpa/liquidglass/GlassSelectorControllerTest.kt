package com.wexpa.liquidglass

import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The controller scenarios of `V3-MODEL.md` section 15.2, run on the **production** controller
 * rather than on a demonstration model beside it.
 *
 * [GlassTabBarGestureTest] drives the same class through the real component and its real pointer
 * plumbing; what this file adds is deterministic traces at chosen frame rates and delays, which a
 * UI harness cannot produce reliably. Every number asserted is either a prediction of the
 * authored preset (section 6) or a containment property that has to hold at every substep.
 */
class GlassSelectorControllerTest {

    /** A five-item bar at Vitals' size on a 3x screen, in physical px. */
    private fun bar(density: Float = 3f) = GlassSelectorBar(
        width = 1006f,
        height = 162f,
        inset = 18f,
        count = 5,
        cornerRadius = 81f,
        baseHalfWidth = 96f,
        baseHalfHeight = 69f,
        allowedHalfHeight = 69f,
        heldHalfHeight = 96f,
        heldHalfWidth = 128f,
        density = density,
        maxBodyWidth = 600f,
    )

    private fun itemBounds(bar: GlassSelectorBar, widths: FloatArray): Pair<FloatArray, FloatArray> {
        val bounds = FloatArray(widths.size * 2)
        val centres = FloatArray(widths.size)
        for (i in widths.indices) {
            val c = bar.centreOf(i)
            centres[i] = c
            bounds[i * 2] = c - widths[i] / 2f
            bounds[i * 2 + 1] = c + widths[i] / 2f
        }
        return bounds to centres
    }

    private fun controller(
        bar: GlassSelectorBar = bar(),
        widths: FloatArray = FloatArray(5) { 150f },
        spec: GlassSelectorSpec = GlassSelectorSpec(),
        startAt: Int = 0,
    ): GlassSelectorController {
        val c = GlassSelectorController(spec)
        val (bounds, centres) = itemBounds(bar, widths)
        c.attach(bar, bounds, centres)
        c.snapToRest(startAt)
        return c
    }

    /** Advance to [seconds] in frames of [hz], asserting containment at every frame. */
    private class Trace {
        val time = ArrayList<Float>()
        val centre = ArrayList<Float>()
        val left = ArrayList<Float>()
        val right = ArrayList<Float>()
        val width = ArrayList<Float>()
        val skew = ArrayList<Float>()
        val protrusion = ArrayList<Float>()
        val formation = ArrayList<Float>()
    }

    private fun run(
        c: GlassSelectorController,
        bar: GlassSelectorBar,
        seconds: Float,
        hz: Float = 90f,
        widths: FloatArray = FloatArray(5) { 150f },
        onFrame: (Float, GlassSelectorController) -> Unit = { _, _ -> },
    ): Trace {
        val trace = Trace()
        val (bounds, centres) = itemBounds(bar, widths)
        val dt = 1f / hz
        var t = 0f
        while (t < seconds) {
            t += dt
            onFrame(t, c)
            c.attach(bar, bounds, centres)
            c.advanceTo(t)
            val body = c.body
            trace.time.add(t)
            trace.centre.add(body.cx)
            trace.left.add(body.left)
            trace.right.add(body.right)
            trace.width.add(body.length + 2f * body.radius)
            trace.skew.add(body.skew)
            trace.protrusion.add(c.protrusion())
            trace.formation.add(c.formation)
            assertTrue(!body.cx.isNaN() && body.isWellFormed, "body became invalid at t=$t: $body")
            assertTrue(!c.solverFailed, "the projection failed at t=$t")
        }
        return trace
    }

    @Test
    fun anOrdinaryTapStaysInsideTheBarAndNeverFormsTheHeldLens() {
        val b = bar()
        for (target in listOf(1, 2, 4)) {
            for (from in listOf(0, 4)) {
                val c = controller(b, startAt = from)
                c.retarget(target)
                val trace = run(c, b, 1.2f, hz = 240f)
                val worstProtrusion = trace.protrusion.max()
                val worstFormation = trace.formation.max()
                assertTrue(
                    worstProtrusion <= 0.2f,
                    "a tap from $from to $target left the bar by $worstProtrusion px",
                )
                assertTrue(
                    worstFormation <= 1e-4f,
                    "a tap from $from to $target formed the held lens to $worstFormation",
                )
                // Settled within the authored one-second budget.
                val finalCentre = trace.centre.last()
                assertTrue(
                    abs(finalCentre - b.centreOf(target)) <= 0.5f * b.density,
                    "a tap from $from to $target settled $finalCentre px from ${b.centreOf(target)}",
                )
            }
        }
    }

    @Test
    fun aLongerTapDrivesMoreExtraLengthThanAShortOne() {
        // Section 6's prediction: peak normalized speed is D/(e S), so a longer move drives a
        // larger extra length before contact. The actual body is reported separately from the
        // drive, because containment may cut the body short near an end.
        val b = bar()
        val widths = ArrayList<Float>()
        val drives = ArrayList<Float>()
        for (target in listOf(1, 2, 4)) {
            val c = controller(b, startAt = 0)
            c.retarget(target)
            var peakDrive = 0f
            val trace = run(c, b, 1.0f, hz = 240f) { _, ctl -> peakDrive = max(peakDrive, ctl.extraLengthDrive()) }
            widths.add(trace.width.max())
            drives.add(peakDrive)
        }
        assertTrue(drives[1] > drives[0] * 1.2f, "2-slot drive ${drives[1]} vs 1-slot ${drives[0]}")
        assertTrue(drives[2] > drives[1] * 1.1f, "4-slot drive ${drives[2]} vs 2-slot ${drives[1]}")
        assertTrue(widths[1] > widths[0] + 1f, "2-slot body ${widths[1]} vs 1-slot ${widths[0]}")
        // The drive predicted by section 6.1 at these distances: 0.60 S tanh(D / (e S)).
        val s = b.slotWidth
        for ((i, d) in listOf(1, 2, 4).withIndex()) {
            val u = d * s / (kotlin.math.E.toFloat() * s)
            val predicted = 0.60f * s * kotlin.math.tanh(u)
            assertTrue(
                abs(drives[i] - predicted) <= 0.12f * s,
                "at $d slots the peak drive was ${drives[i]} px, section 6.1 predicts $predicted px",
            )
        }
    }

    @Test
    fun theTrailingEndLagsGoingOutAndTheArrivingEndBulgesComingIn() {
        // k < 0 makes the left end larger. Accelerating to the right should leave a larger
        // trailing (left) end; braking on arrival should excite a larger leading (right) one.
        val b = bar()
        val c = controller(b, startAt = 0)
        c.retarget(4)
        val trace = run(c, b, 1.0f, hz = 240f)
        val quarter = trace.skew.size / 6
        val launching = trace.skew.take(quarter).min()
        val arriving = trace.skew.drop(quarter).max()
        assertTrue(launching < -1f, "launching right did not leave a larger trailing end: skew $launching")
        assertTrue(arriving > 1f, "arriving did not bulge the leading end: skew $arriving")
        // And the whole thing mirrors: the same move leftwards has the opposite sign throughout.
        val c2 = controller(b, startAt = 4)
        c2.retarget(0)
        val mirrored = run(c2, b, 1.0f, hz = 240f)
        val mLaunch = mirrored.skew.take(quarter).max()
        val mArrive = mirrored.skew.drop(quarter).min()
        assertTrue(abs(mLaunch + launching) <= 0.15f * abs(launching) + 0.5f, "launch mirror $mLaunch vs $launching")
        assertTrue(abs(mArrive + arriving) <= 0.15f * abs(arriving) + 0.5f, "arrival mirror $mArrive vs $arriving")
    }

    @Test
    fun theSameTimestampsGiveTheSameGeometryAtSixtyNinetyAndOneHundredAndTwenty() {
        val b = bar()
        val traces = listOf(60f, 90f, 120f).map { hz ->
            val c = controller(b, startAt = 0)
            c.retarget(3)
            run(c, b, 1.0f, hz = hz)
        }
        // Compare at the timestamps they share, by sampling each trace at the coarsest clock.
        var worstCentre = 0f
        var worstExtent = 0f
        for (i in traces[0].time.indices) {
            val t = traces[0].time[i]
            for (other in traces.drop(1)) {
                val j = other.time.indices.minByOrNull { abs(other.time[it] - t) } ?: continue
                if (abs(other.time[j] - t) > 1e-3f) continue
                worstCentre = max(worstCentre, abs(other.centre[j] - traces[0].centre[i]))
                worstExtent = max(worstExtent, abs(other.left[j] - traces[0].left[i]))
                worstExtent = max(worstExtent, abs(other.right[j] - traces[0].right[i]))
            }
        }
        assertTrue(worstCentre <= 0.5f, "centre differs by $worstCentre px between refresh rates")
        assertTrue(worstExtent <= 0.5f, "extents differ by $worstExtent px between refresh rates")
    }

    @Test
    fun aDelayedFrameProducesNoStaleFlingAndNoInfiniteState() {
        for (delay in listOf(0.05f, 0.1f, 1.5f)) {
            val b = bar()
            val c = controller(b, startAt = 0)
            val (bounds, centres) = itemBounds(b, FloatArray(5) { 150f })
            c.pointerDown(x = b.centreOf(0), y = b.centreY, eventSeconds = 0f, eligible = true)
            var t = 0f
            repeat(30) {
                t += 1f / 90f
                c.attach(b, bounds, centres)
                c.advanceTo(t)
            }
            c.beginDrag(b.centreOf(0))
            var x = b.centreOf(0)
            repeat(20) {
                t += 1f / 90f
                x += 20f
                c.pointerMove(x, t)
                c.attach(b, bounds, centres)
                c.advanceTo(t)
            }
            // The frame that never came.
            t += delay
            c.attach(b, bounds, centres)
            c.advanceTo(t)
            val body = c.body
            assertTrue(body.isWellFormed && !body.cx.isNaN(), "delay $delay produced $body")
            assertTrue(abs(c.centreVelocity) < 1e5f, "delay $delay left velocity ${c.centreVelocity}")
            // A very long gap with the finger **still on the glass** is a suspended loop, not an
            // abandoned gesture: a stationary held finger whose next sample arrives seconds later
            // keeps its hold, and the gap is never replayed as motion. (Changed in the final
            // polish pass; before it, any gap past the catch-up limit dropped the hold, which
            // turned a held finger into an abandoned gesture on its next MOVE. The companion
            // case - a long gap with no pointer down - is asserted below and in
            // GlassSelectorLifetimeTest.aLateFrameWithNoFingerStillRecovers.)
            if (delay > 0.5f) {
                assertTrue(c.isHeld, "delay $delay dropped a hold whose finger never left")
                assertTrue(
                    abs(c.centreVelocity) < 4000f,
                    "delay $delay invented a fling of ${c.centreVelocity} px/s",
                )
            }
            // Nothing flings from here: the finger lifts onto a named slot and the body lands
            // there rather than being carried on by a velocity the delay invented.
            c.pointerUp(2)
            repeat(240) {
                t += 1f / 90f
                c.attach(b, bounds, centres)
                c.advanceTo(t)
            }
            assertTrue(
                abs(c.body.cx - b.centreOf(2)) <= 1f,
                "delay $delay settled " + abs(c.body.cx - b.centreOf(2)) + " px from the slot it was released on",
            )
        }
    }

    @Test
    fun aStationaryFingerStillGrowsTheBodyAroundTheLabelItIsOver() {
        // Section 7.2: the size demand is sprung every simulation step, including intervals in
        // which the finger does not move at all, so accommodation needs no extra pointer sample.
        val b = bar()
        // A middle label wider than the held body's base size, but still inside its declared
        // maximum, so this scenario is a feasible accommodation rather than a constrained one.
        // 420px plus padding exceeded the new *total* contour budget. That old test required
        // the runaway behaviour the owner rejected. Keep this a feasible growth case;
        // oversized demands are covered by the owner regression's hard contour ceiling.
        val widths = floatArrayOf(150f, 150f, 300f, 150f, 150f)
        val c = controller(b, widths = widths, startAt = 0)
        val (bounds, centres) = itemBounds(b, widths)
        var t = 0f
        c.pointerDown(x = b.centreOf(0), y = b.centreY, eventSeconds = 0f, eligible = true)
        repeat(20) {
            t += 1f / 90f
            c.attach(b, bounds, centres)
            c.advanceTo(t)
        }
        c.beginDrag(b.centreOf(0))
        // A quick sweep onto the long label, then the finger stops dead.
        var x = b.centreOf(0)
        val step = (b.centreOf(2) - b.centreOf(0)) / 6f
        repeat(6) {
            t += 1f / 90f
            x += step
            c.pointerMove(x, t)
            c.attach(b, bounds, centres)
            c.advanceTo(t)
        }
        val widthAtGrab = c.body.length + 2f * c.body.radius
        // 300 ms with the finger completely still.
        val stationarySamples = ArrayList<Float>()
        repeat(27) {
            t += 1f / 90f
            c.attach(b, bounds, centres)
            c.advanceTo(t)
            stationarySamples.add(c.body.length + 2f * c.body.radius)
        }
        val grown = stationarySamples.last()
        assertTrue(grown > widthAtGrab + 20f, "a stationary finger grew the body only $widthAtGrab -> $grown px")
        // The padded admitted union is covered.
        val pad = GlassSelectorSpec().admitPaddingDp * b.density
        assertTrue(
            c.body.left <= bounds[4] - pad + 1f && c.body.right >= bounds[5] + pad - 1f,
            "the body ${c.body.left}..${c.body.right} does not cover the padded label " +
                "${bounds[4] - pad}..${bounds[5] + pad}",
        )
        assertTrue(c.graspError <= b.density, "grasp error ${c.graspError} px exceeds one dp")
        // Two more seconds of stillness: no runaway, no third item swallowed.
        val settled = c.body.length + 2f * c.body.radius
        repeat(180) {
            t += 1f / 90f
            c.attach(b, bounds, centres)
            c.advanceTo(t)
        }
        val after = c.body.length + 2f * c.body.radius
        // The requirement is that stillness does not start a runaway: the body must not keep
        // growing. A little settling in the other direction, as the velocity drive finishes
        // decaying, is the spring finishing, not a defect.
        assertTrue(after <= settled + 2f, "the body kept growing: $settled -> $after px")
        // Not collapsing means still covering what it was admitted for. The bound used to be a
        // flat 20 px, which the final polish pass' exact capped-grasp demand exceeded: the old
        // demand divided by `1 - |beta|` with beta uncapped, asked for about five times the reach
        // at a large grasp, and was then clipped by the declared ceiling. The corrected demand is
        // smaller and settles further, so the end state is asserted directly instead.
        assertTrue(after >= settled - 30f, "the body collapsed while the finger was still: $settled -> $after px")
        assertTrue(
            c.body.left <= bounds[4] - pad + 1f && c.body.right >= bounds[5] + pad - 1f,
            "after two more seconds of stillness the body ${c.body.left}..${c.body.right} no longer " +
                "covers the padded label ${bounds[4] - pad}..${bounds[5] + pad}",
        )
        assertTrue(
            c.body.left > bounds[2] && c.body.right < bounds[7],
            "growth recruited an unrelated third item: ${c.body.left}..${c.body.right}",
        )
    }

    @Test
    fun aHoldMayLeaveTheBarAndRecoversContinuouslyOnRelease() {
        val b = bar()
        val c = controller(b, startAt = 2)
        val (bounds, centres) = itemBounds(b, FloatArray(5) { 150f })
        var t = 0f
        c.pointerDown(x = b.centreOf(2), y = b.centreY, eventSeconds = 0f, eligible = true)
        repeat(40) {
            t += 1f / 90f
            c.attach(b, bounds, centres)
            c.advanceTo(t)
        }
        c.beginDrag(b.centreOf(2))
        // A brisk drag toward the next tab. Not into the bar's own end cap: a body pressed into
        // the rounded end is squeezed by it, which is correct and is a different scenario.
        var x = b.centreOf(2)
        repeat(30) {
            t += 1f / 90f
            x += 8f
            c.pointerMove(x, t)
            c.attach(b, bounds, centres)
            c.advanceTo(t)
        }
        val heldProtrusion = c.protrusion()
        assertTrue(heldProtrusion > 1f, "a genuine hold never left the resting bar: $heldProtrusion px")
        // Release, and watch the recovery: every step monotone-ish and no jump.
        c.pointerUp(3)
        var previous = c.body
        var worstJump = 0f
        var stillOut = 0
        repeat(180) {
            t += 1f / 90f
            c.attach(b, bounds, centres)
            c.advanceTo(t)
            val body = c.body
            worstJump = max(
                worstJump,
                max(abs(body.cx - previous.cx), max(abs(body.left - previous.left), abs(body.right - previous.right))),
            )
            if (c.protrusion() > 0.2f) stillOut++
            previous = body
        }
        assertTrue(c.protrusion() <= 0.2f, "the body never came back inside: ${c.protrusion()} px")
        assertTrue(stillOut > 3, "the release snapped back instead of recovering continuously")
        assertTrue(worstJump <= 24f, "release jumped $worstJump px in one frame")
    }

    @Test
    fun reducedMotionKeepsTheBodyStillAndFlat() {
        val b = bar()
        val c = controller(b, startAt = 0)
        c.motionEnabled = false
        c.retarget(3)
        val trace = run(c, b, 0.5f, hz = 90f)
        assertTrue(trace.formation.max() <= 1e-4f, "reduced motion formed the lens")
        assertTrue(abs(trace.skew.max()) <= 0.01f && abs(trace.skew.min()) <= 0.01f, "reduced motion skewed the body")
        assertTrue(trace.protrusion.max() <= 0.2f, "reduced motion left the bar")
        assertTrue(
            abs(trace.centre.first() - b.centreOf(3)) <= 0.5f,
            "reduced motion did not arrive immediately: ${trace.centre.first()}",
        )
    }

    @Test
    fun aRetargetMidFlightKeepsTheValuesAndVelocitiesItAlreadyHad() {
        val b = bar()
        val c = controller(b, startAt = 0)
        c.retarget(4)
        val (bounds, centres) = itemBounds(b, FloatArray(5) { 150f })
        var t = 0f
        repeat(18) {
            t += 1f / 90f
            c.attach(b, bounds, centres)
            c.advanceTo(t)
        }
        val before = c.body
        val velocityBefore = c.centreVelocity
        assertTrue(abs(velocityBefore) > 100f, "the fixture must retarget while actually moving")
        // Section 15.2 gate 2 is about the same timestamp: applying the event must not move the
        // body at all. Ordinary travel between two frames is not an event discontinuity, so the
        // comparison is made with no time advanced in between.
        c.retarget(1)
        val immediately = c.body
        assertTrue(
            abs(immediately.cx - before.cx) <= 0.25f,
            "retarget moved the body by " + abs(immediately.cx - before.cx) + " px at the same timestamp",
        )
        assertTrue(
            abs(immediately.left - before.left) <= 0.25f && abs(immediately.right - before.right) <= 0.25f,
            "retarget moved the body's extents at the same timestamp",
        )
        assertTrue(
            c.centreVelocity == velocityBefore,
            "retarget restarted the velocity: " + velocityBefore + " -> " + c.centreVelocity,
        )
        // A frame later the body is responding to the new target as a second-order system, not
        // restarting: the velocity change over that frame is the spring's own acceleration, which
        // for a reversal of three slots is large and negative. Anything else - a reset to zero, or
        // no change at all - fails this.
        val dt = 1f / 90f
        val spec = GlassSelectorSpec()
        // The exact closed-form response of the same spring over the same interval, computed
        // here independently of the controller. A forward-Euler estimate would not do: the
        // controller steps exactly, and over a 90 Hz frame the two differ by a third.
        val reference = GlassSpring(before.cx, velocityBefore).also { it.target = b.centreOf(1) }
        reference.step(dt, spec.centreOmega, spec.centreZeta)
        val predicted = reference.velocity
        t += dt
        c.attach(b, bounds, centres)
        c.advanceTo(t)
        assertTrue(
            abs(c.centreVelocity - predicted) <= max(abs(predicted) * 0.05f, 20f),
            "a frame after the retarget the velocity is " + c.centreVelocity + ", the spring predicts " + predicted,
        )
    }

    @Test
    fun everySubstepIsContainedNotJustEveryRenderedFrame() {
        // A 60 Hz trace hides what happens between frames; the controller substeps at 1/240 s and
        // the body must be inside the bar at every one of them.
        val b = bar()
        val c = controller(b, startAt = 0)
        c.retarget(4)
        var t = 0f
        var worst = 0f
        val (bounds, centres) = itemBounds(b, FloatArray(5) { 150f })
        while (t < 1f) {
            t += 1f / 240f
            c.attach(b, bounds, centres)
            c.advanceTo(t)
            worst = max(worst, c.protrusion())
        }
        assertTrue(worst <= 0.2f, "a substep left the bar by $worst px")
    }
}
