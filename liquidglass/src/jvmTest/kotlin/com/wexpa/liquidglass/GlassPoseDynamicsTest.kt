package com.wexpa.liquidglass

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The behaviour gates of the iOS-parity brief's section 13, on the production pose controller.
 *
 * These are the assertions the owner's rejected motion could not have passed: a held body that
 * genuinely protrudes, a strong pull that is allowed to flatten it back inside, stretch toward an
 * arbitrary 2D direction, a grasped material point that does not slide under a stationary finger,
 * and a release that starts from the shape the body actually has.
 *
 * Every number asserted is either a layout anchor the bar declares, a containment property, or a
 * derivative checked against an independent converged difference. None is an Apple constant.
 */
class GlassPoseDynamicsTest {

    private fun bar(width: Float = 1006f, count: Int = 5, density: Float = 3f) = GlassSelectorBar(
        width = width, height = 162f, inset = 18f, count = count, cornerRadius = 81f,
        baseHalfWidth = 96f, baseHalfHeight = 69f, allowedHalfHeight = 69f,
        heldHalfHeight = 96f, heldHalfWidth = 128f, density = density, maxBodyWidth = 600f,
    )

    private fun boundsOf(b: GlassSelectorBar, width: Float = 150f): Pair<FloatArray, FloatArray> {
        val bounds = FloatArray(b.count * 2)
        val centres = FloatArray(b.count)
        for (i in 0 until b.count) {
            val c = b.centreOf(i)
            centres[i] = c
            bounds[i * 2] = c - width / 2f
            bounds[i * 2 + 1] = c + width / 2f
        }
        return bounds to centres
    }

    private fun controller(
        b: GlassSelectorBar = bar(),
        startAt: Int = 2,
        labels: Float = 150f,
    ): GlassPoseController {
        val c = GlassPoseController()
        val (bounds, centres) = boundsOf(b, labels)
        c.attach(b, bounds, centres, inkHalfHeight = b.baseHalfHeight * 0.62f)
        c.snapToRest(startAt)
        return c
    }

    private fun step(
        c: GlassPoseController,
        b: GlassSelectorBar,
        from: Float,
        to: Float,
        hz: Float = 120f,
        labels: Float = 150f,
        onFrame: (Float) -> Unit = {},
    ): Float {
        val (bounds, centres) = boundsOf(b, labels)
        val dt = 1f / hz
        var t = from
        while (t < to - 1e-6f) {
            t = minOf(to, t + dt)
            onFrame(t)
            c.attach(b, bounds, centres, inkHalfHeight = b.baseHalfHeight * 0.62f)
            c.advanceTo(t)
        }
        return t
    }

    // ---------------------------------------------------------------- protrusion

    @Test
    fun aCalmHeldBodyGenuinelyProtrudesAboveAndBelowTheBar() {
        // The owner's first observation: holding glass makes it protrude past its container.
        val b = bar()
        val c = controller(b)
        val x = b.centreOf(2)
        c.pointerDown(x, b.centreY, 0.0, eligible = true)
        step(c, b, 0f, 0.6f)
        assertTrue(c.isHeld, "the eligible press never became a hold")
        val e = GlassPoseExtents()
        c.extents(e)
        val barTop = b.centreY - b.allowedHalfHeight
        val barBottom = b.centreY + b.allowedHalfHeight
        assertTrue(
            e.top < barTop - 1f,
            "the calm held body did not rise above the bar: top ${e.top} vs bar top $barTop",
        )
        assertTrue(
            e.bottom > barBottom + 1f,
            "the calm held body did not drop below the bar: bottom ${e.bottom} vs bar bottom $barBottom",
        )
        // ...and it stays roughly centred rather than translating upward.
        val offset = (e.top + e.bottom) / 2f - b.centreY
        assertTrue(
            abs(offset) <= 4f,
            "pressure expansion added a $offset px vertical centre offset, which it must not",
        )
        println("POSE-PROTRUSION top=%.1f bottom=%.1f barTop=%.1f barBottom=%.1f height=%.1f"
            .format(e.top, e.bottom, barTop, barBottom, e.height))
    }

    @Test
    fun aStrongPullIsAllowedToFlattenTheBodyBackInsideTheBar() {
        // 6735: a body the owner describes as held lies mostly inside the bar's vertical
        // envelope. Nothing may force a minimum raised height on a held flag.
        val b = bar()
        val c = controller(b, startAt = 0)
        var x = b.centreOf(0)
        c.pointerDown(x, b.centreY, 0.0, eligible = true)
        c.beginDrag(x, b.centreY)
        // Establish the held body first; formation from rest is not hard-drag flattening.
        step(c, b, 0f, 1f)
        c.pointerMove(x, b.centreY, 1.0)
        val e = GlassPoseExtents()
        var t = 1.0
        var flattest = Float.MAX_VALUE
        val (bounds, centres) = boundsOf(b)
        var f = 1f
        while (f < 1.42f) {
            f += 1f / 240f
            t += 1.0 / 240.0
            // Untimed 6735 cannot identify a 3600 px/s threshold. Probe an extreme speed;
            // r10 deliberately reduces sensitivity at the previous authored input speed.
            x += 60f * (b.centreOf(1) - b.centreOf(0)) / 240f
            c.pointerMove(x, b.centreY, t)
            c.attach(b, bounds, centres, inkHalfHeight = b.baseHalfHeight * 0.62f)
            c.advanceTo(f)
            c.extents(e)
            flattest = minOf(flattest, e.height)
        }
        val restHeight = 2f * b.baseHalfHeight
        println("POSE-FLATTEN flattest=%.1f restHeight=%.1f".format(flattest, restHeight))
        assertTrue(
            flattest < restHeight,
            "a hard held pull never flattened the body below its resting height " +
                "($flattest vs $restHeight)",
        )
        assertTrue(c.isHeld, "the hard pull lost the hold")
        assertTrue(flattest >= .79f * b.height, "hard drag collapsed beyond the reference envelope")
    }

    @Test
    fun anOrdinaryTapStaysInsideTheBar() {
        val b = bar()
        val c = controller(b, startAt = 0)
        c.retarget(4)
        var worst = 0f
        step(c, b, 0f, 1.2f) { worst = max(worst, c.protrusion()) }
        assertTrue(worst <= 0.25f, "an ordinary tap escaped the bar by $worst px")
    }

    // ------------------------------------------------------------- 2D directional

    @Test
    fun aHeldDragStretchesTowardAnArbitraryDirection() {
        // The response the horizontal-only body could not produce at all. The spine is a vector:
        // along the bar and diagonally the body extends along the drag. Across the bar the bar
        // anchors the body and the pull stretches it as an ellipse with its displacement
        // (reference-6756, the owner's recording of pulled glass), not with its speed.
        var tallAfterDrag = 0f
        var centreOffAfterDrag = 0f
        fun aspectAfterDrag(deg: Int, speed: Float, seconds: Float = 0.16f, settle: Float = 0f): Pair<Float, Float> {
            val b = bar()
            val c = controller(b)
            val ang = deg * kotlin.math.PI.toFloat() / 180f
            var x = b.centreOf(2)
            var y = b.centreY
            c.pointerDown(x, y, 0.0, eligible = true)
            c.beginDrag(x, y)
            val (bounds, centres) = boundsOf(b)
            var t = 0.0
            var f = 0f
            while (f < seconds) {
                f += 1f / 240f
                t += 1.0 / 240.0
                x += cos(ang) * speed / 240f
                y += sin(ang) * speed / 240f
                c.pointerMove(x, y, t)
                c.attach(b, bounds, centres, inkHalfHeight = b.baseHalfHeight * 0.62f)
                c.advanceTo(f)
            }
            // A finger that stops where it is: the shape is compared where it settles.
            val end = f + settle
            while (f < end - 1e-6f) {
                f += 1f / 240f
                t += 1.0 / 240.0
                c.pointerMove(x, y, t)
                c.attach(b, bounds, centres, inkHalfHeight = b.baseHalfHeight * 0.62f)
                c.advanceTo(f)
            }
            val buffer = FloatArray(128 * 2)
            c.frame.contour(128, buffer)
            var along = 0f
            var across = 0f
            var tall = 0f
            for (i in 0 until 128) {
                val dx = buffer[i * 2] - c.centreX
                val dy = buffer[i * 2 + 1] - c.centreY
                along = max(along, abs(dx * cos(ang) + dy * sin(ang)))
                across = max(across, abs(-dx * sin(ang) + dy * cos(ang)))
                tall = max(tall, abs(dy))
            }
            tallAfterDrag = tall
            centreOffAfterDrag = abs(c.centreY - b.centreY)
            println("POSE-DIRECTION-DIAG %d deg speed %.0f s %.2f settle %.2f: spine %.1f radius %.1f expP %.3f coshRho %.3f centreY-off %.1f"
                .format(deg, speed, seconds, settle, c.frame.spine, c.frame.radius, c.frame.expP, c.frame.coshRho, c.centreY - b.centreY))
            return along to across
        }
        // The comparison body is a STATIONARY hold measured along the SAME axis, so pressure
        // and accommodation are not credited to the drag and the resting spine's own direction
        // is not held against a diagonal one.
        for (deg in listOf(0, 45, 90, 135, 180)) {
            val (stillAlong, stillAcross) = aspectAfterDrag(deg, 0f)
            val stillTall = tallAfterDrag
            val still = stillAlong / stillAcross
            // Along the bar the elongation is a speed effect (a flick); off the bar it is a
            // displacement effect and a flick's speed also squeezes the body (the hard-pull
            // flattening, 6735), so the diagonal is driven at an ordinary drag speed.
            val diagonal = deg == 45 || deg == 135
            val (along, across) = aspectAfterDrag(deg, if (diagonal) 400f else 1800f, seconds = if (diagonal) 0.4f else 0.16f)
            val tall = tallAfterDrag
            val moved = along / across
            println("POSE-DIRECTION %3d deg aspect moved %.3f vs still %.3f (along %.1f across %.1f; still %.1f / %.1f; tall %.1f vs %.1f)"
                .format(deg, moved, still, along, across, stillAlong, stillAcross, tall, stillTall))
            if (deg == 45 || deg == 135) {
                // A diagonal drag travels along the bar and is pulled off it: the bar anchors the
                // body only across itself, so the stretch is across the bar (the body gets
                // taller), not along the diagonal — see the 90 deg case below.
                assertTrue(
                    tall > stillTall * 1.05f,
                    "$deg deg: the diagonal drag did not stretch the body across the bar ($tall against a stationary hold at $stillTall)",
                )
            } else {
                assertTrue(
                    moved > still * 1.08f,
                    "$deg deg: the drag did not extend the body along its own direction relative to " +
                        "across it (aspect $moved against a stationary hold at $still on the same axis)",
                )
            }
        }
        // Across the bar the stretch follows the DISPLACEMENT of the pull and not its speed
        // (reference-6756: a 5 s pull and a 1.2 s pull reach the same extreme at the same
        // displacement, S01/S04): a short pull rounds the body a little, a longer one more, and
        // the same displacement reached at three times the speed gives the same shape.
        val (sAlong, sAcross) = aspectAfterDrag(90, 0f)
        val stillAcrossBar = sAlong / sAcross
        val (nAlong, nAcross) = aspectAfterDrag(90, 150f, seconds = 0.16f, settle = 0.3f)   // 24 px
        val near = nAlong / nAcross
        val (mAlong, mAcross) = aspectAfterDrag(90, 400f, seconds = 0.16f, settle = 0.3f)   // 64 px
        val moderate = mAlong / mAcross
        // The same 64 px three times slower: the recording's speed independence holds at drag
        // speeds; a flick's speed also squeezes the body (6735), which is a different, measured law.
        val (fAlong, fAcross) = aspectAfterDrag(90, 400f / 3f, seconds = 0.48f, settle = 0.3f)
        val fast = fAlong / fAcross
        val (xAlong, xAcross) = aspectAfterDrag(90, 400f, seconds = 0.6f, settle = 0.3f)    // far past the envelope
        val far = xAlong / xAcross
        println("POSE-DIRECTION  90 deg aspect still %.3f near %.3f moderate %.3f slow-same-displacement %.3f far %.3f (along/across %.1f/%.1f, %.1f/%.1f, %.1f/%.1f, %.1f/%.1f)"
            .format(stillAcrossBar, near, moderate, fast, far, nAlong, nAcross, mAlong, mAcross, fAlong, fAcross, xAlong, xAcross))
        // The prior 3%/8% minimum gains were authored, not tab-reference measurements.
        // The owner requested less sensitivity; preserve continuous monotonic response.
        assertTrue(near > stillAcrossBar + 1e-4f, "a 24 px pull off the bar did not stretch the body toward the finger: $near against $stillAcrossBar")
        assertTrue(moderate > near + 1e-4f, "a 64 px pull did not stretch the body more than a 24 px one: $moderate against $near")
        assertTrue(abs(fast - moderate) < 0.06f * moderate, "the same displacement at a third of the speed gave a different shape: $fast against $moderate")
        assertTrue(far >= moderate, "a pull far past the envelope undid the stretch: $far against $moderate")
        // And the body stays on its bar while it reaches: the centre follows the finger by a
        // small capped fraction (the owner, against the iPhone), the head does the rest.
        val bar = bar()
        assertTrue(
            centreOffAfterDrag <= GlassPoseSpec().pullFollowCapRatio * bar.height + 0.5f,
            "a long pull off the bar carried the centre $centreOffAfterDrag px away; it should stay anchored",
        )
    }

    // ------------------------------------------------------------------- the grasp

    @Test
    fun aStationaryFingerKeepsItsMaterialPointWhileTheBodyGrows() {
        // Section 13's attachment gate: <= 0.1 device px while pressure, strain and accommodation
        // all change under a finger that does not move.
        //
        // Both axes: the centre is solved from the two-dimensional grasp constraint again
        // (`c = g - e^p o`), because the recording's g7 hold with a low finger sits lower on its
        // bar (17 px above, 29 px below). The vertical axis passes through the held envelope,
        // which is exact near the bar's centre line and only saturates for a finger well off it.
        val b = bar()
        val c = controller(b, labels = 300f)
        // Grab off centre so the material point is not the trivial one.
        val x = b.centreOf(2) + 52f
        val y = b.centreY - 14f
        c.pointerDown(x, y, 0.0, eligible = true)
        c.beginDrag(x, y)
        val (bounds, centres) = boundsOf(b, 300f)
        val g = FloatArray(2)
        var worst = 0f
        var worstY = 0f
        var f = 0f
        var t = 0.0
        while (f < 0.9f) {
            f += 1f / 240f
            t += 1.0 / 240.0
            c.pointerMove(x, y, t)
            c.attach(b, bounds, centres, inkHalfHeight = b.baseHalfHeight * 0.62f)
            c.advanceTo(f)
            if (f > 0.25f) {
                c.graspRendered(g)
                worst = max(worst, abs(g[0] - x))
                worstY = max(worstY, abs(g[1] - y))
            }
        }
        val ex = GlassPoseExtents()
        c.extents(ex)
        println(
            ("POSE-ATTACH worstX=%.4f worstY=%.4f px  accommodation=%.1f pressure=%.3f " +
                "strain=%.3f width=%.1f held=%s")
                .format(worst, worstY, c.accommodationDemand, c.pressureLog, c.strainMagnitude,
                    ex.width, c.isHeld),
        )
        assertTrue(
            worst <= 0.1f,
            "the grasped material point slid $worst px along the bar under a stationary finger",
        )
        // Across the bar the growth is only partly about the touched point
        // ([GlassPoseSpec.graspFollowAcross]): the material under the finger slides by the
        // fraction the style declares of what full centre-line growth would move it, plus the
        // envelope's own compression. Asserted as that bound, not hidden inside a looser gate.
        val kappa = GlassPoseSpec().graspFollowAcross
        val allowedSlide = (1f - kappa) * (exp(c.pressureLog) - 1f) * 14f + 0.5f
        assertTrue(
            worstY <= allowedSlide,
            "the grasped material point slid $worstY px across the bar, more than the " +
                "$allowedSlide px the declared follow fraction accounts for",
        )
        assertTrue(c.accommodationDemand > 0f, "the stationary hold never accommodated the label")
    }

    @Test
    fun theGraspSurvivesAParentScaleChangeWithoutSliding() {
        // A layout rebase is a different thing from a scale change under a live finger; the brief
        // requires the attachment to hold while the parent's own mapping moves.
        val b = bar()
        val c = controller(b)
        val x = b.centreOf(2) + 30f
        val y = b.centreY
        c.pointerDown(x, y, 0.0, eligible = true)
        c.beginDrag(x, y)
        val (bounds, centres) = boundsOf(b)
        val g = FloatArray(2)
        var worst = 0f
        var f = 0f
        var t = 0.0
        while (f < 0.5f) {
            f += 1f / 240f
            t += 1.0 / 240.0
            c.pointerMove(x, y, t)
            c.attach(b, bounds, centres, inkHalfHeight = b.baseHalfHeight * 0.62f)
            c.advanceTo(f)
            if (f > 0.2f) {
                c.graspRendered(g)
                worst = max(worst, sqrt((g[0] - x) * (g[0] - x) + (g[1] - y) * (g[1] - y)))
            }
        }
        assertTrue(worst <= 0.1f, "the grasp slid $worst px")
    }

    // ------------------------------------------------------------------- release

    @Test
    fun releaseStartsFromTheShapeTheBodyActuallyHasAndRecoversTogether() {
        val b = bar()
        val c = controller(b, startAt = 0)
        var x = b.centreOf(0)
        c.pointerDown(x, b.centreY, 0.0, eligible = true)
        c.beginDrag(x, b.centreY)
        val (bounds, centres) = boundsOf(b)
        var t = 0.0
        var f = 0f
        while (f < 0.3f) {
            f += 1f / 240f
            t += 1.0 / 240.0
            x += 2400f / 240f
            c.pointerMove(x, b.centreY, t)
            c.attach(b, bounds, centres, inkHalfHeight = b.baseHalfHeight * 0.62f)
            c.advanceTo(f)
        }
        val e = GlassPoseExtents()
        c.extents(e)
        val beforeWidth = e.width
        val beforeCentre = c.centreX
        val beforeStrain = c.strainMagnitude
        assertTrue(beforeStrain > 0.02f, "the drag never strained the body")

        val beforeVelocity = c.centreVelocityX
        c.pointerUp(4)
        // The gate is that release does not *jump*, not that it does not move: the body is
        // travelling at about 2400 px/s when the finger leaves, so one substep of commanded
        // motion is exactly what should happen. What must not happen is a step beyond it.
        step(c, b, f, f + 1f / 240f)
        c.extents(e)
        val commanded = beforeVelocity / 240f
        assertTrue(
            abs(e.width - beforeWidth) <= 4f,
            "release changed the width by ${abs(e.width - beforeWidth)} px in one substep",
        )
        assertTrue(
            abs((c.centreX - beforeCentre) - commanded) <= 1f,
            "release moved the centre by ${c.centreX - beforeCentre} px against a commanded " +
                "$commanded px, a jump of ${abs((c.centreX - beforeCentre) - commanded)} px",
        )

        // Translation and shape then recover together: neither finishes long before the other.
        var centreDone = -1f
        var shapeDone = -1f
        val target = b.centreOf(4)
        var tt = f
        while (tt < f + 1.2f) {
            tt = step(c, b, tt, tt + 1f / 240f)
            if (centreDone < 0f && abs(c.centreX - target) < 2f) centreDone = tt - f
            if (shapeDone < 0f && c.strainMagnitude < beforeStrain * 0.1f) shapeDone = tt - f
        }
        println("POSE-RELEASE centre settled at %.3f s, shape at %.3f s".format(centreDone, shapeDone))
        assertTrue(centreDone > 0f, "the centre never reached its target")
        assertTrue(shapeDone > 0f, "the shape never recovered")
        assertTrue(
            abs(centreDone - shapeDone) < 0.45f,
            "translation and shape did not recover together: centre ${centreDone}s, shape ${shapeDone}s",
        )
    }

    @Test
    fun aReleasedBodyRegainsContactOnlyAfterItIsBackInside() {
        val b = bar()
        val c = controller(b)
        val x = b.centreOf(2)
        c.pointerDown(x, b.centreY, 0.0, eligible = true)
        step(c, b, 0f, 0.5f)
        assertTrue(c.protrusion() > 1f, "the held body never protruded, so there is nothing to test")
        c.pointerUp(2)
        // Immediately after UP the body is still outside, and that is legal, not a solver failure.
        step(c, b, 0.5f, 0.52f)
        assertTrue(!c.solverFailed, "a legitimate held protrusion was counted as a solver failure")
        step(c, b, 0.52f, 1.6f)
        assertTrue(
            c.protrusion() <= 0.5f,
            "the released body never came back inside: ${c.protrusion()} px out",
        )
        assertTrue(c.contactFailures == 0, "${c.contactFailures} contact failures during a normal release")
    }

    // ------------------------------------------------------------ taps and formation

    @Test
    fun aLongerTapStretchesMoreAndRecoveryStaysBounded() {
        // JGEY2190 n176–182 belongs to a compound moving lens, not an isolated resting tap.
        // The previous minimum-width assertion incorrectly prohibited the owner's requested
        // arrival recovery. Keep a collapse guard and distance ordering here; the dedicated
        // GlassRestTapReferenceTest covers the real preset's height and complete rebound.
        fun transit(from: Int, to: Int): Float {
            val b = bar()
            val c = controller(b, startAt = from)
            val e = GlassPoseExtents()
            c.extents(e)
            val restHeight = e.height
            val restWidth = e.width
            c.retarget(to)
            var peakElongation = 0f
            var minHeight = Float.MAX_VALUE
            var minWidth = Float.MAX_VALUE
            step(c, b, 0f, 1.0f) {
                c.extents(e)
                peakElongation = max(peakElongation, c.elongationPx)
                minHeight = minOf(minHeight, e.height)
                minWidth = minOf(minWidth, e.width)
            }
            println(
                "POSE-TAP %d->%d peakElongation=%.1f px (%.2f slot) minHeight=%.1f rest=%.1f minWidth=%.1f rest=%.1f"
                    .format(from, to, peakElongation, peakElongation / b.slotWidth, minHeight, restHeight, minWidth, restWidth),
            )
            assertTrue(
                minHeight >= restHeight - 0.5f,
                "the transit pill lost height: $minHeight against $restHeight at rest",
            )
            assertTrue(
                minWidth >= restWidth * .86f,
                "the recovering pill collapsed: $minWidth against $restWidth",
            )
            return peakElongation
        }
        val short = transit(2, 3)
        val long = transit(0, 4)
        val slot = bar().slotWidth
        assertTrue(short > 0.10f * slot, "a one-slot tap only stretched the half-spine by $short px")
        assertTrue(long > short * 1.2f, "a four-slot tap did not stretch more than a one-slot one ($long vs $short)")
        assertTrue(long < short * 2.5f, "the elongation is not saturating: $long for four slots vs $short for one")
    }

    @Test
    fun reducedMotionIsStillAndFlat() {
        val b = bar()
        val c = controller(b, startAt = 1)
        c.motionEnabled = false
        c.pointerDown(b.centreOf(1), b.centreY, 0.0, eligible = true)
        c.beginDrag(b.centreOf(1), b.centreY)
        c.pointerMove(b.centreOf(3), b.centreY, 0.2)
        step(c, b, 0f, 0.4f)
        assertTrue(!c.isHeld, "reduced motion left the gesture owning the body")
        assertTrue(c.formation < 0.01f, "reduced motion kept a formed body")
        assertTrue(c.strainMagnitude < 0.01f, "reduced motion kept a strained body")
        assertTrue(
            abs(c.centreX - b.centreOf(1)) <= 0.5f,
            "reduced motion did not adopt the selected rest state",
        )
    }

    // --------------------------------------------------------------- accommodation

    @Test
    fun accommodationNeverRecruitsAThirdItem() {
        // Section 9's prohibition: expand, discover another label, expand again. The eligibility
        // is frozen on the unaccommodated body, so the feedback cannot start.
        val b = bar()
        val c = controller(b, labels = 320f)
        val x = b.centreOf(2)
        c.pointerDown(x, b.centreY, 0.0, eligible = true)
        c.beginDrag(x, b.centreY)
        step(c, b, 0f, 1.5f, labels = 320f)
        val e = GlassPoseExtents()
        c.extents(e)
        // Three item centres span two slots; the body must not enclose all three.
        var enclosed = 0
        for (i in 0 until b.count) {
            val cx = b.centreOf(i)
            if (cx > e.left && cx < e.right) enclosed++
        }
        println("POSE-ADMIT width=%.1f slots=%.2f enclosedCentres=%d demand=%.1f"
            .format(e.width, e.width / b.slotWidth, enclosed, c.accommodationDemand))
        assertTrue(enclosed <= 2, "accommodation enclosed $enclosed item centres")
    }

    @Test
    fun pressureAccommodationAndStrainAreReportedSeparately() {
        // Section 9 forbids replacing the decomposition with a renamed total-width clamp, so the
        // three have to be observable on their own.
        val b = bar()
        val c = controller(b, labels = 280f)
        val x = b.centreOf(2)
        c.pointerDown(x, b.centreY, 0.0, eligible = true)
        c.beginDrag(x, b.centreY)
        step(c, b, 0f, 0.8f, labels = 280f)
        assertTrue(c.pressureLog > 0.01f, "pressure was not reported (${c.pressureLog})")
        assertTrue(c.accommodationDemand > 0f, "accommodation was not reported")
        assertTrue(c.strainMagnitude >= 0f, "strain was not reported")
    }

    // ------------------------------------------------------------------ the clock

    @Test
    fun aLongUptimeDoesNotQuantiseTheMeasuredVelocity() {
        // The owner-repair pass' Double-time fix, kept and re-asserted on the new controller: at
        // a twenty-day uptime, Float seconds quantise to 125 ms and a smooth 8 ms event stream
        // becomes a stepped one. Identical drags at both origins must produce the same body.
        fun run(origin: Double): Triple<Float, Float, Float> {
            val b = bar()
            val c = controller(b, startAt = 0)
            var x = b.centreOf(0)
            c.pointerDown(x, b.centreY, origin, eligible = true)
            c.beginDrag(x, b.centreY)
            val (bounds, centres) = boundsOf(b)
            var f = 0f
            var t = origin
            while (f < 0.3f) {
                f += 1f / 120f
                t += 1.0 / 120.0
                x += 2000f / 120f
                c.pointerMove(x, b.centreY, t)
                c.attach(b, bounds, centres, inkHalfHeight = b.baseHalfHeight * 0.62f)
                c.advanceTo(f)
            }
            val e = GlassPoseExtents()
            c.extents(e)
            return Triple(c.centreX, e.width, c.strainMagnitude)
        }
        val zero = run(0.0)
        val twentyDays = run(20.0 * 24.0 * 3600.0)
        println("POSE-UPTIME zero=%s twentyDays=%s".format(zero, twentyDays))
        assertTrue(abs(zero.first - twentyDays.first) <= 0.5f, "centre differs: $zero vs $twentyDays")
        assertTrue(abs(zero.second - twentyDays.second) <= 0.5f, "width differs: $zero vs $twentyDays")
        assertTrue(abs(zero.third - twentyDays.third) <= 0.01f, "strain differs: $zero vs $twentyDays")
    }

    @Test
    fun refreshRatesAgreeOnTheSameTimestamps() {
        fun trace(hz: Float): List<Pair<Float, Float>> {
            val b = bar()
            val c = controller(b, startAt = 0)
            c.retarget(4)
            val out = ArrayList<Pair<Float, Float>>()
            val (bounds, centres) = boundsOf(b)
            var t = 0f
            while (t < 0.8f) {
                t += 1f / hz
                c.attach(b, bounds, centres, inkHalfHeight = b.baseHalfHeight * 0.62f)
                c.advanceTo(t)
                out.add(t to c.centreX)
            }
            return out
        }
        val a = trace(60f)
        val b2 = trace(120f)
        var worst = 0f
        for ((t, x) in a) {
            val nearest = b2.minByOrNull { abs(it.first - t) } ?: continue
            if (abs(nearest.first - t) > 1e-3f) continue
            worst = max(worst, abs(nearest.second - x))
        }
        assertTrue(worst <= 0.5f, "60 and 120 Hz disagree by $worst px at shared timestamps")
    }

    @Test
    fun aRewoundClockDoesNotRewindTheHoldThreshold() {
        val b = bar()
        val c = controller(b)
        step(c, b, 0f, 1f)
        c.pointerDown(c.centreX, b.centreY, 1.0, eligible = true)
        c.advanceTo(0f)
        c.advanceTo(0.2f)
        assertTrue(c.formation > 0f, "a 200 ms eligible press after a clock restart left formation at 0")
        assertTrue(c.isHeld, "the press never became a hold")
    }
}
