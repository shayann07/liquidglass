package com.wexpa.liquidglass

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Lifetime, rebasing, clock, grasp-kinematics and solver-failure regressions for the
 * **production** controller.
 *
 * These are the scenarios the September suite never ran: the bar changes size, density, item count
 * or motion mode while the body is at rest, travelling, held or releasing; the host's frame clock
 * restarts underneath a retained controller; a projection fails mid-release. Each one reproduces a
 * defect that was present at `aa9e97e` and was found by probing the compiled class, not by reading
 * it. The recorded before-state is in
 * `research/analysis/v3/final-polish/evidence/before_reproduction.md`.
 *
 * Nothing here asserts an optical constant. Every number is either a layout anchor the bar itself
 * declares, a containment property that has to hold at every substep, a threshold the authored
 * preset states, or the exact arithmetic of the bounded grasp law.
 */
class GlassSelectorLifetimeTest {

    private fun bar(
        width: Float = 1006f,
        count: Int = 5,
        density: Float = 3f,
        inset: Float = 18f,
        baseHalfWidth: Float = 96f,
        halfHeight: Float = 69f,
    ) = GlassSelectorBar(
        width = width,
        height = 162f,
        inset = inset,
        count = count,
        cornerRadius = 81f,
        baseHalfWidth = baseHalfWidth,
        baseHalfHeight = halfHeight,
        allowedHalfHeight = halfHeight,
        heldHalfHeight = halfHeight + 27f,
        heldHalfWidth = baseHalfWidth + 32f,
        density = density,
        maxBodyWidth = 600f,
    )

    private fun boundsOf(bar: GlassSelectorBar, width: Float = 150f): Pair<FloatArray, FloatArray> {
        val bounds = FloatArray(bar.count * 2)
        val centres = FloatArray(bar.count)
        for (i in 0 until bar.count) {
            val c = bar.centreOf(i)
            centres[i] = c
            bounds[i * 2] = c - width / 2f
            bounds[i * 2 + 1] = c + width / 2f
        }
        return bounds to centres
    }

    private fun controller(bar: GlassSelectorBar, startAt: Int = 2): GlassSelectorController {
        val c = GlassSelectorController()
        val (b, cs) = boundsOf(bar)
        c.attach(bar, b, cs)
        c.snapToRest(startAt)
        return c
    }

    private fun step(
        c: GlassSelectorController,
        bar: GlassSelectorBar,
        from: Float,
        to: Float,
        hz: Float = 90f,
        widths: Float = 150f,
    ) {
        val (b, cs) = boundsOf(bar, widths)
        val dt = 1f / hz
        var t = from
        while (t < to - 1e-6f) {
            t = min(to, t + dt)
            c.attach(bar, b, cs)
            c.advanceTo(t)
        }
    }

    /** The body is inside the rest bar. Only valid where no held allowance is owned. */
    private fun assertContained(c: GlassSelectorController, where: String, tolerance: Float = 0.2f) {
        val p = c.protrusion()
        assertTrue(p <= tolerance, "$where: protruded $p px past the rest bar (limit $tolerance)")
    }

    /** The body is inside the envelope it is currently entitled to. Always valid. */
    private fun assertFeasible(c: GlassSelectorController, where: String, tolerance: Float = 0.2f) {
        val e = c.worstConstraintExcess()
        assertTrue(e <= tolerance, "$where: violated its own envelope by $e px (limit $tolerance)")
        assertTrue(c.selectorValid, "$where: the selector was reported unusable")
        assertTrue(c.body.isWellFormed, "$where: published an ill-formed body ${c.body}")
    }

    // ------------------------------------------------------------------ §1 rebase

    @Test
    fun aResizeAtRestMovesTheBodyToTheNewAnchor() {
        val wide = bar(width = 1006f)
        val c = controller(wide, startAt = 2)
        step(c, wide, 0f, 0.2f)
        assertEquals(wide.centreOf(2), c.body.cx, 0.5f, "did not start on the wide bar's anchor")

        val narrow = bar(width = 706f)
        step(c, narrow, 0.2f, 0.4f)
        assertEquals(
            narrow.centreOf(2), c.body.cx, 0.5f,
            "a resize at rest left the body on the old bar's pixel anchor",
        )
        assertContained(c, "resize at rest")
        assertFeasible(c, "resize at rest")
    }

    @Test
    fun aResizeWhileTravellingLandsOnTheNewAnchor() {
        val wide = bar(width = 1006f)
        val c = controller(wide, startAt = 0)
        c.retarget(4)
        step(c, wide, 0f, 0.08f)
        assertTrue(c.body.cx > wide.centreOf(0) + 5f, "the body never started travelling")

        // The narrow bar's pill scales with its slot; a 96 px half width would not fit on its
        // last slot at all, and containment - not the rebase - would be what was measured.
        val narrow = bar(width = 706f, baseHalfWidth = 67f)
        step(c, narrow, 0.08f, 1.0f)
        assertEquals(
            narrow.centreOf(4), c.body.cx, 1.0f,
            "a resize while travelling did not re-aim at the new anchor",
        )
        assertContained(c, "resize while travelling")
    }

    @Test
    fun anItemCountChangeRebasesToAValidItem() {
        val five = bar(count = 5)
        val c = controller(five, startAt = 4)
        step(c, five, 0f, 0.3f)

        val three = bar(count = 3)
        val (b, cs) = boundsOf(three)
        c.attach(three, b, cs)
        step(c, three, 0.3f, 0.6f)
        assertEquals(
            three.centreOf(2), c.body.cx, 1.0f,
            "an item-count change did not rebase the body onto the last valid item",
        )
        assertContained(c, "count change")
    }

    @Test
    fun aDensityChangeWithTheSameOuterWidthIsStillARebase() {
        // Outer width unchanged; the inset, the pill and the envelope all change. A rebase keyed
        // only on width/height/count - which is what this was - keeps constraint rows built for an
        // envelope that no longer exists.
        val a = bar(inset = 18f, halfHeight = 69f, baseHalfWidth = 96f, density = 3f)
        val c = controller(a, startAt = 2)
        step(c, a, 0f, 0.3f)
        assertTrue(c.body.halfHeight > 60f, "the body was not using the tall envelope to begin with")

        val b2 = bar(inset = 40f, halfHeight = 40f, baseHalfWidth = 130f, density = 2f)
        step(c, b2, 0.3f, 0.7f)
        assertEquals(
            b2.centreOf(2), c.body.cx, 1.0f,
            "a density/inset change did not move the body to the new anchor",
        )
        assertTrue(
            c.body.halfHeight <= 40f + 0.2f,
            "the body kept the old envelope's height: ${c.body.halfHeight} > 40",
        )
        assertFeasible(c, "density change")
    }

    @Test
    fun aResizeWhileHeldClearsGestureOwnership() {
        val wide = bar(width = 1006f)
        val c = controller(wide, startAt = 2)
        step(c, wide, 0f, 0.1f)
        c.pointerDown(wide.centreOf(2), 81f, 0.1f, eligible = true)
        c.beginDrag(wide.centreOf(2))
        val before = c.interactionId
        step(c, wide, 0.1f, 0.5f)
        assertTrue(c.isHeld, "the drag never took the body")
        assertTrue(c.formation > 0.5f, "the held lens never formed")

        val narrow = bar(width = 706f)
        val (b, cs) = boundsOf(narrow)
        c.attach(narrow, b, cs)
        assertTrue(!c.isHeld, "a layout rebase left the old gesture owning the body")
        assertTrue(c.interactionId != before, "a rebase did not change the interaction identity")
        step(c, narrow, 0.5f, 1.2f)
        assertEquals(
            narrow.centreOf(2), c.body.cx, 1.0f,
            "a rebase during a hold did not adopt the new rest anchor",
        )
        assertContained(c, "rebase during hold")
    }

    @Test
    fun aRebaseDuringReleaseLeavesNoInheritedAllowance() {
        val wide = bar(width = 1006f)
        val c = controller(wide, startAt = 2)
        c.pointerDown(wide.centreOf(2), 81f, 0f, eligible = true)
        c.beginDrag(wide.centreOf(2))
        step(c, wide, 0f, 0.4f)
        c.pointerMove(wide.centreOf(3), 0.42f)
        step(c, wide, 0.4f, 0.45f)
        c.pointerUp(3)

        val narrow = bar(width = 706f)
        val (b, cs) = boundsOf(narrow)
        c.attach(narrow, b, cs)
        // The release allowance must not survive the rebase: the body cannot sit outside the new
        // rest envelope on the strength of an allowance measured against a polygon that is gone.
        step(c, narrow, 0.45f, 0.48f)
        assertContained(c, "rebase during release")
        assertFeasible(c, "rebase during release")
    }

    @Test
    fun anInkOnlyRevisionKeepsTheHold() {
        val b0 = bar()
        val c = controller(b0, startAt = 2)
        c.pointerDown(b0.centreOf(2), 81f, 0f, eligible = true)
        c.beginDrag(b0.centreOf(2))
        step(c, b0, 0f, 0.4f)
        val formationBefore = c.formation
        val idBefore = c.interactionId
        assertTrue(formationBefore > 0.9f, "the lens never formed")

        // New label widths only: the bar's coordinate frame is unchanged.
        val (bounds, centres) = boundsOf(b0, width = 260f)
        c.attach(b0, bounds, centres)
        assertTrue(c.isHeld, "an ink-only revision cancelled the hold")
        assertEquals(idBefore, c.interactionId, "an ink-only revision changed the interaction identity")
        assertTrue(
            c.formation >= formationBefore - 1e-3f,
            "an ink-only revision reset the formation: $formationBefore -> ${c.formation}",
        )
        step(c, b0, 0.4f, 0.9f, widths = 260f)
        assertTrue(
            c.body.halfWidth > 140f,
            "the wider label was never accommodated: half width ${c.body.halfWidth}",
        )
    }

    // ------------------------------------------------------------------- §1 clock

    @Test
    fun aClockRestartDoesNotRewindTheHoldThreshold() {
        val b0 = bar()
        val c = controller(b0, startAt = 2)
        step(c, b0, 0f, 1f)
        c.pointerDown(c.body.cx, 81f, 1f, eligible = true)
        // The adapter used to restart its effect-local clock at zero on a key change while
        // retaining the controller. No measured duration may be rewound by that.
        c.advanceTo(0f)
        c.advanceTo(0.2f)
        assertTrue(
            c.formation > 0f,
            "200 ms of eligible press past a 120 ms threshold left formation at ${c.formation}",
        )
        assertTrue(c.isHeld, "the press never became a hold")
    }

    @Test
    fun aRewoundClockDoesNotRewindTheReleaseAge() {
        val b0 = bar()
        val c = controller(b0, startAt = 2)
        step(c, b0, 0f, 0.5f)
        c.pointerDown(b0.centreOf(2), 81f, 0.5f, eligible = true)
        c.beginDrag(b0.centreOf(2))
        step(c, b0, 0.5f, 0.9f)
        c.pointerUp(2)
        c.advanceTo(0f)
        // The allowance is a decaying exponential of the release age. A negative age would make it
        // grow instead, and the body would be let further out of the bar the longer it waited.
        var t = 0f
        while (t < 0.7f) {
            t += 1f / 240f
            val (b, cs) = boundsOf(b0)
            c.attach(b0, b, cs)
            c.advanceTo(t)
            assertFeasible(c, "release after a rewound clock at t=$t")
        }
        assertContained(c, "settled after a rewound clock", tolerance = 0.5f)
    }

    @Test
    fun aStationaryHeldFingerSurvivesASuspendedLoop() {
        val b0 = bar()
        val c = controller(b0, startAt = 2)
        c.pointerDown(b0.centreOf(2), 81f, 0f, eligible = true)
        c.beginDrag(b0.centreOf(2))
        step(c, b0, 0f, 0.4f)
        assertTrue(c.isHeld, "the drag never took the body")
        // The host suspended its idle loop for two seconds; the finger never left the glass and
        // the next MOVE resumes it. That is suspension, not an abandoned gesture.
        c.pointerMove(b0.centreOf(2) + 4f, 2.4f)
        c.advanceTo(2.4f)
        assertTrue(c.isHeld, "a suspended idle loop turned a held finger into an abandoned gesture")
        assertFeasible(c, "resumed hold")
    }

    @Test
    fun aLateFrameWithNoFingerStillRecovers() {
        val b0 = bar()
        val c = controller(b0, startAt = 2)
        c.pointerDown(b0.centreOf(2), 81f, 0f, eligible = true)
        c.beginDrag(b0.centreOf(2))
        step(c, b0, 0f, 0.4f)
        c.pointerUp(2)
        // Lifecycle interruption with no pointer down: recovery, not replay.
        c.advanceTo(9f)
        assertTrue(c.formation < 0.01f, "the lens survived a nine-second interruption")
        assertContained(c, "lifecycle recovery", tolerance = 0.5f)
    }

    // ------------------------------------------------------------------ §1 motion

    @Test
    fun disablingMotionMidGestureCancelsOwnershipWithoutSelecting() {
        val b0 = bar()
        val c = controller(b0, startAt = 1)
        c.pointerDown(b0.centreOf(1), 81f, 0f, eligible = true)
        c.beginDrag(b0.centreOf(1))
        c.pointerMove(b0.centreOf(3), 0.2f)
        step(c, b0, 0f, 0.3f)
        assertTrue(c.isHeld, "the drag never took the body")

        c.motionEnabled = false
        step(c, b0, 0.3f, 0.32f)
        assertTrue(!c.isHeld, "disabling motion left the gesture owning the body")
        assertEquals(
            b0.centreOf(1), c.body.cx, 0.5f,
            "disabling motion did not adopt the current selected rest state",
        )
        assertTrue(c.formation < 0.01f, "reduced motion kept a formed lens")
        assertContained(c, "motion disabled")
    }

    // ---------------------------------------------------------------- §4 failure

    @Test
    fun aSolverFailurePublishesAFeasibleFallbackNotAnAdvancedPosition() {
        val b0 = bar()
        val c = controller(b0, startAt = 2)
        c.pointerDown(b0.centreOf(2), 81f, 0f, eligible = true)
        c.beginDrag(b0.centreOf(2))
        c.pointerMove(b0.centreOf(4), 0.2f)
        step(c, b0, 0f, 0.3f)
        c.pointerUp(4)
        // Mid-release fault injection: every projection from here fails.
        c.injectProjectionFailure = true
        var t = 0.3f
        val (b, cs) = boundsOf(b0)
        while (t < 0.5f) {
            t += 1f / 240f
            c.attach(b0, b, cs)
            c.advanceTo(t)
            assertFeasible(c, "mid-release solver failure at t=$t", tolerance = 0.5f)
        }
        assertTrue(c.solverFailed, "the injected failure was not observable")

        c.injectProjectionFailure = false
        step(c, b0, 0.5f, 1.6f)
        assertTrue(!c.solverFailed, "the failure state never cleared")
        assertContained(c, "after the fault cleared", tolerance = 0.5f)
    }

    @Test
    fun aStaticSolverFailureNeverPublishesAnInfeasibleBody() {
        val b0 = bar()
        val c = controller(b0, startAt = 0)
        c.injectProjectionFailure = true
        c.retarget(4)
        var t = 0f
        val (b, cs) = boundsOf(b0)
        while (t < 0.6f) {
            t += 1f / 240f
            c.attach(b0, b, cs)
            c.advanceTo(t)
            assertFeasible(c, "static solver failure at t=$t")
        }
        assertTrue(c.solverFailed, "the injected failure was not observable")
    }

    @Test
    fun aConstraintBufferOverflowIsTreatedAsAFailure() {
        val b0 = bar()
        val c = controller(b0, startAt = 2)
        c.injectConstraintCapacity = 8
        step(c, b0, 0f, 0.2f)
        assertTrue(c.solverFailed, "an overflowed constraint buffer was not reported as a failure")
        assertTrue(c.body.isWellFormed, "overflow published an ill-formed body ${c.body}")
        assertContained(c, "constraint overflow", tolerance = 0.5f)
    }

    // -------------------------------------------------- §4 moving-wall velocity

    @Test
    fun everyActiveMovingRowRespectsItsOwnAllowanceVelocity() {
        val b0 = bar()
        val c = controller(b0, startAt = 2)
        c.pointerDown(b0.centreOf(2), 81f, 0f, eligible = true)
        c.beginDrag(b0.centreOf(2))
        // A hard held swipe to the right end: the body protrudes, then is released.
        var x = b0.centreOf(2)
        var t = 0f
        val (bb, cc) = boundsOf(b0)
        while (t < 0.35f) {
            t += 1f / 240f
            x += 3000f / 240f
            c.pointerMove(x, t)
            c.attach(b0, bb, cc)
            c.advanceTo(t)
        }
        assertTrue(c.protrusion() > 1f, "the hard swipe never protruded past the rest bar")
        c.pointerUp(4)
        var worst = 0f
        while (t < 0.9f) {
            t += 1f / 240f
            c.attach(b0, bb, cc)
            c.advanceTo(t)
            worst = max(worst, c.rowVelocityExcess)
            assertFeasible(c, "release from a hard swipe at t=$t", tolerance = 0.5f)
        }
        assertTrue(
            worst <= 0.1f,
            "an active row's velocity exceeded its own allowance derivative by $worst px/s",
        )
        assertContained(c, "settled after a hard swipe", tolerance = 0.5f)
    }

    @Test
    fun anOrdinaryTapNeverAcquiresAHeldAllowance() {
        val b0 = bar()
        val c = controller(b0, startAt = 0)
        c.pointerDown(b0.centreOf(0), 81f, 0f, eligible = false)
        // Fast travel across the whole bar with no held acquisition at all: positive travel
        // velocity must not manufacture a held allowance on UP.
        c.retarget(4)
        step(c, b0, 0f, 0.1f)
        c.pointerUp(4)
        var t = 0.1f
        val (bb, cc) = boundsOf(b0)
        while (t < 1.0f) {
            t += 1f / 240f
            c.attach(b0, bb, cc)
            c.advanceTo(t)
            assertContained(c, "tap travel at t=$t")
        }
    }

    // ------------------------------------------------------------ §3 kinematics

    /**
     * The exact counterexample of the brief: a body whose half width grows at 40 px/s with a
     * **capped** grasp has zero unconstrained centre velocity from that growth, because the cap's
     * derivative is zero. The implementation this replaces returned -20 px/s.
     */
    @Test
    fun aCappedGraspContributesNoGrowthVelocity() {
        val v = glassGraspCentreVelocity(
            beta = 0.8f,
            halfWidth = 100f,
            halfWidthVelocity = 40f,
            baseHalfWidth = 50f,
            fingerVelocity = 0f,
            offsetVelocity = 0f,
        )
        assertEquals(0f, v, 1e-4f, "a capped grasp manufactured a velocity from width growth")
        assertEquals(
            50f, glassGraspOffsetD(0.8f, 100f, 50f), 1e-4f,
            "D(A) did not saturate at the pixel cap",
        )
    }

    @Test
    fun anUncappedGraspCarriesTheFullGrowthTerm() {
        val v = glassGraspCentreVelocity(
            beta = 0.4f,
            halfWidth = 100f,
            halfWidthVelocity = 40f,
            baseHalfWidth = 500f,
            fingerVelocity = 0f,
            offsetVelocity = 0f,
        )
        assertEquals(-16f, v, 1e-4f, "an uncapped grasp lost its growth term")
    }

    @Test
    fun aRelaxingOffsetContributesItsOwnVelocity() {
        val v = glassGraspCentreVelocity(
            beta = 0.4f,
            halfWidth = 100f,
            halfWidthVelocity = 0f,
            baseHalfWidth = 500f,
            fingerVelocity = 250f,
            offsetVelocity = -80f,
        )
        assertEquals(170f, v, 1e-4f, "the attachment offset's own velocity was dropped")
    }

    /**
     * The stored centre velocity is the derivative of the centre the body actually renders, on a
     * smooth unconstrained interval. A stationary finger isolates the growth and offset
     * derivatives from any sampled input velocity, which is the term the old formula got wrong.
     */
    @Test
    fun storedCentreVelocityIsThePositionDerivativeUnderAStationaryFinger() {
        val b0 = bar()
        val (bounds, centres) = boundsOf(b0, width = 300f)
        val c = GlassSelectorController()
        c.attach(b0, bounds, centres)
        c.snapToRest(2)
        // Grab well off centre so beta is large and the growth term is real.
        val finger = b0.centreOf(2) + 60f
        c.pointerDown(finger, 81f, 0f, eligible = true)
        c.beginDrag(finger)

        val h = 1f / 20000f
        var t = 0f
        var worst = 0f
        var worstAt = 0f
        var samples = 0
        while (t < 0.5f) {
            c.attach(b0, bounds, centres)
            c.advanceTo(t)
            val recordable = t > 0.06f && c.centreUnconstrained && c.isHeld
            val x0 = c.body.cx
            val v0 = c.centreVelocity
            t += h
            c.attach(b0, bounds, centres)
            c.advanceTo(t)
            if (recordable && c.centreUnconstrained) {
                samples++
                val e = abs((c.body.cx - x0) / h - v0)
                if (e > worst) { worst = e; worstAt = t }
            }
            t += 1f / 240f - h
        }
        assertTrue(samples > 40, "no unconstrained interval was sampled ($samples)")
        assertTrue(
            worst <= 10f,
            "stored centre velocity differs from the rendered derivative by $worst px/s at t=$worstAt",
        )
    }

    /**
     * Acquisition uses the same capped law the step uses and chooses the attachment offset's
     * initial velocity, so neither the position nor the velocity steps at the grab. The gate is
     * the deviation from the **commanded** first-order motion, not the motion itself: the offset
     * reeling in is what the grasp is supposed to do.
     */
    @Test
    fun acquiringAGraspWhileAlreadyMovingDoesNotStepTheCentre() {
        val b0 = bar()
        val (bounds, centres) = boundsOf(b0, width = 300f)
        val c = GlassSelectorController()
        c.attach(b0, bounds, centres)
        c.snapToRest(0)
        c.retarget(4)
        step(c, b0, 0f, 0.12f, widths = 300f)
        val before = c.body.cx
        val vBefore = c.centreVelocity
        assertTrue(abs(vBefore) > 100f, "the body was not moving when the grasp was acquired")

        val finger = before + 150f
        c.pointerDown(finger, 81f, 0.12f, eligible = true)
        c.beginDrag(finger)
        val h = 1f / 20000f
        c.attach(b0, bounds, centres)
        c.advanceTo(0.12f + h)
        val jump = abs(c.body.cx - (before + vBefore * h))
        assertTrue(
            jump <= 0.25f,
            "acquiring a grasp stepped the centre by $jump px beyond its commanded motion",
        )
        assertTrue(
            abs(c.centreVelocity - vBefore) <= 60f,
            "acquiring a grasp stepped the velocity from $vBefore to ${c.centreVelocity} px/s",
        )
    }

    /**
     * A held gesture that later crosses drag slop keeps the grasp it already has. Acquiring it a
     * second time would re-seat the attachment offset from a finger that has since moved.
     */
    @Test
    fun crossingSlopAfterTheThresholdDoesNotReAcquireTheGrasp() {
        val b0 = bar()
        val c = controller(b0, startAt = 2)
        val start = b0.centreOf(2) + 40f
        c.pointerDown(start, 81f, 0f, eligible = true)
        step(c, b0, 0f, 0.25f)
        assertTrue(c.isHeld, "the threshold never fired")
        val moved = start + 90f
        c.pointerMove(moved, 0.25f)
        step(c, b0, 0.25f, 0.26f)
        val cxBefore = c.body.cx
        c.beginDrag(moved)
        step(c, b0, 0.26f, 0.27f)
        assertTrue(
            abs(c.body.cx - cxBefore) <= 6f,
            "crossing slop re-acquired the grasp and moved the centre by ${abs(c.body.cx - cxBefore)} px",
        )
    }

    // ---------------------------------------------------- §3 2D accommodation

    /**
     * Horizontal span alone does not prove a label fits: the ends are round and the sides slope.
     * The gate is the four corners of each padded admitted rectangle against the final convex
     * body - containing the corners contains the rectangle.
     */
    @Test
    fun aStationaryHoldCoversTheAdmittedLabelInTwoDimensions() {
        for (labelWidth in listOf(150f, 260f, 320f)) {
            val b0 = bar()
            val (bounds, centres) = boundsOf(b0, width = labelWidth)
            // The ink block's own vertical extent: icon over label inside the pill.
            val halfHeight = b0.baseHalfHeight * 0.62f
            val c = GlassSelectorController()
            c.attach(b0, bounds, centres, halfHeight)
            c.snapToRest(2)
            val finger = b0.centreOf(2)
            c.pointerDown(finger, b0.centreY, 0f, eligible = true)
            c.beginDrag(finger)
            var t = 0f
            while (t < 0.35f) {
                t += 1f / 90f
                c.attach(b0, bounds, centres, halfHeight)
                c.advanceTo(t)
            }

            val pad = 6f * b0.density
            val left = bounds[4] - pad
            val right = bounds[5] + pad
            val top = b0.centreY - halfHeight
            val bottom = b0.centreY + halfHeight
            val body = c.body
            val query = GlassBodyQuery()
            var worst = -Float.MAX_VALUE
            for (px in listOf(left, right)) {
                for (py in listOf(top, bottom)) {
                    glassBodyQuery(body, px, py, query)
                    worst = max(worst, query.distance)
                }
            }
            val constrained = c.accommodationDemand > body.halfWidth + 1f
            if (constrained) {
                // Reported, not hidden: the envelope could not give what admission asked for.
                println(
                    "CONSTRAINED accommodation at label $labelWidth: " +
                        "demand ${c.accommodationDemand} vs half width ${body.halfWidth}",
                )
            } else {
                assertTrue(
                    worst <= 0f,
                    "label $labelWidth: a padded admitted corner sat $worst px outside the body",
                )
                assertTrue(
                    c.graspError <= 1f,
                    "label $labelWidth: grasp error ${c.graspError} px on a feasible accommodation",
                )
            }
        }
    }

    @Test
    fun aStationaryHoldAdmitsAtMostOneNeighbour() {
        val b0 = bar()
        val (bounds, centres) = boundsOf(b0, width = 150f)
        val c = GlassSelectorController()
        c.attach(b0, bounds, centres)
        c.snapToRest(2)
        c.pointerDown(b0.centreOf(2), b0.centreY, 0f, eligible = true)
        c.beginDrag(b0.centreOf(2))
        step(c, b0, 0f, 0.6f)
        // Three whole slots would be the third item joining; the ceiling and the fixed aperture
        // forbid it however far the body grows.
        assertTrue(
            c.body.halfWidth * 2f < 3f * b0.slotWidth,
            "a third item was admitted: body width ${c.body.halfWidth * 2f}, slot ${b0.slotWidth}",
        )
    }

    /**
     * The Pixel 7's own layout, at its own density, with the longest Vitals label at a larger
     * supported font setting.
     *
     * The bar is built through the **production** `glassSelectorBarOf` from the numbers the
     * device's accessibility tree reports for the installed review build - 1006 x 163 physical px,
     * inset 16, five items, density 2.625 - so nothing here is a guessed geometry. The label width
     * is the one measured on the device at font scale 1.0 (156 px for "Communities"), scaled by
     * the 1.3 font setting the device leg was captured at.
     *
     * Either the padded label is covered in two dimensions, or the case is reported as a
     * constrained accommodation with its numbers. It is never hidden by shrinking the text,
     * growing the bar or admitting a third item.
     */
    @Test
    fun theDeviceLayoutAccommodatesItsLongestLabelAtALargerFontSetting() {
        val density = androidx.compose.ui.unit.Density(2.625f, 1f)
        val style = GlassTabBarStyle.V3(dark = true)
        val spec = requireNotNull(style.selector) { "the V3 preset must carry a selector spec" }
        val deviceBar = glassSelectorBarOf(
            density = density,
            barWidthPx = 1006f,
            barHeightPx = 163f,
            style = style,
            itemCount = 5,
            formation = 1f,
            spec = spec,
        )
        for (fontScale in listOf(1.0f, 1.3f)) {
            val labelWidth = 156f * fontScale
            val (bounds, centres) = boundsOf(deviceBar, width = labelWidth)
            // The item's ink block, icon over label, inside the resting pill.
            val inkHalf = deviceBar.baseHalfHeight * 0.62f * fontScale
            val c = GlassSelectorController(spec)
            c.attach(deviceBar, bounds, centres, inkHalf)
            c.snapToRest(2)
            val finger = deviceBar.centreOf(2)
            c.pointerDown(finger, deviceBar.centreY, 0f, eligible = true)
            c.beginDrag(finger)
            var t = 0f
            while (t < 0.45f) {
                t += 1f / 90f
                c.attach(deviceBar, bounds, centres, inkHalf)
                c.advanceTo(t)
            }
            val pad = spec.admitPaddingDp * deviceBar.density
            val left = bounds[4] - pad
            val right = bounds[5] + pad
            val top = deviceBar.centreY - inkHalf
            val bottom = deviceBar.centreY + inkHalf
            val body = c.body
            val query = GlassBodyQuery()
            var worst = -Float.MAX_VALUE
            for (px in listOf(left, right)) {
                for (py in listOf(top, bottom)) {
                    glassBodyQuery(body, px, py, query)
                    worst = max(worst, query.distance)
                }
            }
            val constrained = c.accommodationDemand > body.halfWidth + 1f
            println(
                "DEVICE-ACCOMMODATION font=$fontScale label=$labelWidth inkHalf=$inkHalf " +
                    "demand=${c.accommodationDemand} A=${body.halfWidth} worstCorner=$worst " +
                    "constrained=$constrained graspError=${c.graspError}",
            )
            assertFeasible(c, "device layout at font $fontScale")
            if (!constrained) {
                assertTrue(
                    worst <= 0f,
                    "font $fontScale: a padded label corner sat $worst px outside the body on the " +
                        "device's own layout",
                )
            }
            // Whatever the accommodation, no third item is ever swallowed.
            assertTrue(
                body.halfWidth * 2f < 3f * deviceBar.slotWidth,
                "font $fontScale: a third item was admitted (${body.halfWidth * 2f} px vs slot " +
                    "${deviceBar.slotWidth})",
            )
        }
    }

    /**
     * Removal and recreation, as opposed to ordinary recomposition: a controller that is thrown
     * away and replaced must start clean on the new layout, with no inherited ownership, no
     * inherited clock and no callback from the one that went away.
     */
    @Test
    fun aRecreatedControllerStartsCleanOnTheNewLayout() {
        val wide = bar(width = 1006f)
        val old = controller(wide, startAt = 2)
        old.pointerDown(wide.centreOf(2), 81f, 0f, eligible = true)
        old.beginDrag(wide.centreOf(2))
        step(old, wide, 0f, 0.4f)
        assertTrue(old.isHeld, "the drag never took the old controller's body")

        // The node goes away and comes back on a different bar.
        val narrow = bar(width = 706f, baseHalfWidth = 67f)
        val fresh = controller(narrow, startAt = 1)
        assertTrue(!fresh.isHeld, "a fresh controller inherited a hold")
        assertEquals(narrow.centreOf(1), fresh.body.cx, 0.25f, "a fresh controller did not seed at rest")
        assertTrue(fresh.formation < 1e-4f, "a fresh controller inherited a formation")
        assertTrue(!fresh.solverFailed && fresh.selectorValid, "a fresh controller started invalid")
        // Its clock starts at its own origin: a press is measured from the first advance, not
        // from whatever the discarded controller's clock had reached.
        fresh.pointerDown(narrow.centreOf(1), 81f, 0f, eligible = true)
        fresh.advanceTo(0.05f)
        assertTrue(fresh.formation < 0.5f, "a 50 ms press past a 120 ms threshold already formed")
        fresh.advanceTo(0.25f)
        assertTrue(fresh.isHeld, "a 250 ms press never became a hold on the fresh controller")

        // The discarded one may still be stepped by a cancelled coroutine; it must not touch the
        // replacement, which is a different object with a different interaction identity.
        val freshIdBefore = fresh.interactionId
        step(old, wide, 0.4f, 0.8f)
        old.cancel(2)
        assertEquals(freshIdBefore, fresh.interactionId, "the discarded controller mutated the new one")
        assertTrue(fresh.isHeld, "the discarded controller cancelled the new one's hold")
    }
}
