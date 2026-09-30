package com.wexpa.liquidglass

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VectorizedAnimationSpec
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Deterministic replay traces of the tab bar's selector motion (closeout Phase 2, section 5).
 *
 * What is real here: the springs ([GlassTabBarStyle.arrive], `settle`, `form`, `subside`, `gelSpring`)
 * sampled through their own vectorised form exactly as Compose's `animate` and `animateFloatAsState`
 * sample them, the [VelocityTracker] the component feeds, the [GlassTabBarGeometry] it lays tabs out
 * with, and the Vitals bar's actual size on the Pixel 7. What is a transcription: the state rules of
 * `GlassTabBar.kt`'s pointer handler (down → arrive at the finger's tab; slop → drag with the grab
 * offset, direct following, tab preview on boundary crossings; up → settle to the projected tab and
 * commit; every exit releases held/dragging/press), reproduced line for line in [Selector] so they can
 * be driven with millisecond-stamped events and stepped at any frame rate. Compose applies input on the
 * frame after it arrives and reads spring values as of the last frame, and so does this.
 *
 * States are compared at equal elapsed times across 60 / 90 / 120 Hz and with one dropped frame, never
 * by frame index.
 */
class GlassTabBarMotionTraceTest {

    // The Vitals nav bar on the Pixel 7: 1080 px wide screen, 14 dp side padding, 62 dp tall, 5 tabs,
    // 6 dp content padding, at 2.625 px/dp; Android's touch slop is 8 dp.
    private val density = 2.625f
    private val barWidth = 1080f - 2f * 14f * density
    private val barHeight = 62f * density
    private val geometry = GlassTabBarGeometry(barWidth = barWidth, inset = 6f * density, count = 5)
    private val touchSlop = 8f * density
    private val style = GlassTabBarStyle.Measured(dark = true)

    /** One spring in flight, sampled as Compose samples it: from a start state, by elapsed time. */
    private inner class Motion(spec: AnimationSpec<Float>, val startMs: Double, val from: Float, val to: Float, val v0: Float) {
        private val vec: VectorizedAnimationSpec<AnimationVector1D> = spec.vectorize(Float.VectorConverter)
        private val a = AnimationVector1D(from); private val b = AnimationVector1D(to); private val v = AnimationVector1D(v0)
        fun value(tMs: Double): Float = vec.getValueFromNanos(((tMs - startMs) * 1e6).toLong().coerceAtLeast(0), a, b, v).value
        fun velocity(tMs: Double): Float = vec.getVelocityFromNanos(((tMs - startMs) * 1e6).toLong().coerceAtLeast(0), a, b, v).value
        val durationMs: Double get() = vec.getDurationNanos(a, b, v) / 1e6
    }

    /** `animateFloatAsState`: retargets from its current value and velocity. */
    private inner class Animated(initial: Float) {
        var motion: Motion? = null
        var target = initial
        var lastValue = initial
        var lastVelocity = 0f
        fun retarget(newTarget: Float, spec: AnimationSpec<Float>, nowMs: Double) {
            if (newTarget == target) return
            target = newTarget
            motion = Motion(spec, nowMs, lastValue, newTarget, lastVelocity)
        }
        fun sample(nowMs: Double) {
            val m = motion ?: return
            lastValue = m.value(nowMs); lastVelocity = m.velocity(nowMs)
            if (nowMs - m.startMs >= m.durationMs) { lastValue = m.to; lastVelocity = 0f; motion = null }
        }
    }

    sealed class Event(val tMs: Double, val x: Float, val y: Float) {
        class Down(t: Double, x: Float, y: Float) : Event(t, x, y)
        class Move(t: Double, x: Float, y: Float) : Event(t, x, y)
        class Up(t: Double, x: Float, y: Float) : Event(t, x, y)
    }

    data class Sample(
        val tMs: Double, val offsetX: Float, val settleVelocity: Float, val lift: Float, val liftVelocity: Float, val gel: Float,
        val held: Boolean, val dragging: Boolean, val dragIndex: Int, val selected: Int, val pressX: Float, val pressY: Float,
        val pressed: Boolean, val pointerX: Float?,
    )

    /** The transcription of GlassTabBar.kt's selector state (HEAD aa9e97e plus the Phase 2 local fixes). */
    private inner class Selector(initialSelected: Int, private val motionEnabled: Boolean = true, private val legacyRules: Boolean = false) {
        var selected = initialSelected
        var offsetX = geometry.centreOf(initialSelected)
        var settleVelocity = 0f
        var settleTarget = offsetX
        var position: Motion? = null
        var held = false
        var dragging = false
        var dragIndex = -1
        var liveVelocity = 0f
        val lift = Animated(0f)
        val gel = Animated(0f)
        var pressX = 0f; var pressY = 0f; var pressed = false
        // gesture-local
        private var tracker: VelocityTracker? = null
        private var downX = 0f; private var slopCrossed = false; private var grabOffset = 0f; private var lastIndex = -1
        private var pointerX: Float? = null
        val log = ArrayList<String>()
        val selections = ArrayList<Pair<Double, Int>>()

        private val selectorWidthPx get() = with(style) { (if (pillWidth.value.isNaN()) geometry.slotWidth else max(pillWidth.value * density, geometry.slotWidth)) + lensExtraWidth.value * density * lift.lastValue }
        private val selectorHeightPx get() = barHeight - style.pillInset.value * density * 2f + (style.lensOverflow.value * density * 2f + style.pillInset.value * density * 2f) * lift.lastValue
        private fun selectorLeft() = offsetX - selectorWidthPx / 2f
        private fun selectorTop() = (barHeight - selectorHeightPx) / 2f
        private fun localPress(x: Float, y: Float) {
            pressX = (x - selectorLeft()).coerceIn(0f, selectorWidthPx); pressY = (y - selectorTop()).coerceIn(0f, selectorHeightPx); pressed = true
        }

        /** GlassTabBar.settleTo: cancel the running spring and start one from the current value. */
        private fun settleTo(target: Float, initialVelocity: Float, spec: AnimationSpec<Float>, nowMs: Double) {
            settleTarget = target
            position = Motion(spec, nowMs, offsetX, target, initialVelocity)
        }

        /** LaunchedEffect(restingAt): follow the selection only when no finger is moving the lens. */
        private fun onSelectionChanged(nowMs: Double) {
            val restingAt = geometry.centreOf(selected)
            if (dragIndex >= 0 && dragIndex == selected) dragIndex = -1          // LaunchedEffect(selected, dragIndex)
            if (held || dragging || dragIndex >= 0) return
            if (settleTarget == restingAt) return
            if (!motionEnabled) { position = null; settleTarget = restingAt; offsetX = restingAt } else settleTo(restingAt, if (legacyRules) 0f else settleVelocity, style.settle, nowMs)
        }

        /** The production handler's releaseToSelection: no selection, settle to the current one. */
        fun releaseToSelection(nowMs: Double) {
            held = false; dragging = false; liveVelocity = 0f; pressed = false; dragIndex = -1
            val target = geometry.centreOf(selected)
            if (!motionEnabled) { position = null; settleTarget = target; offsetX = target; settleVelocity = 0f }
            else if (settleTarget != target || position == null) settleTo(target, settleVelocity, style.settle, nowMs)
        }

        fun select(index: Int, nowMs: Double) {
            selections.add(nowMs to index)
            if (index != selected) { selected = index; onSelectionChanged(nowMs) } else onSelectionChanged(nowMs)
        }

        fun apply(e: Event, nowMs: Double) {
            when (e) {
                is Event.Down -> {
                    tracker = VelocityTracker().also { it.addPosition(e.tMs.toLong(), Offset(e.x, e.y)) }
                    downX = e.x; slopCrossed = false; pointerX = e.x
                    lastIndex = geometry.nearestIndex(e.x)
                    position = null                                   // motion?.cancel()
                    held = true
                    dragIndex = lastIndex
                    localPress(e.x, e.y)
                    if (motionEnabled) settleTo(geometry.centreOf(lastIndex), if (legacyRules) 0f else settleVelocity, style.arrive, nowMs)
                    else { settleTarget = geometry.centreOf(lastIndex); offsetX = settleTarget }
                    log.add("t=${e.tMs} DOWN x=${e.x} -> arrive at tab $lastIndex from ${"%.1f".format(offsetX)} v=${"%.0f".format(settleVelocity)}")
                }
                is Event.Move -> {
                    val tr = tracker ?: return
                    tr.addPosition(e.tMs.toLong(), Offset(e.x, e.y))
                    pointerX = e.x
                    if (!slopCrossed) {
                        if (abs(e.x - downX) < touchSlop || !motionEnabled) return
                        // awaitHorizontalTouchSlopOrCancellation returned: the drag is claimed
                        slopCrossed = true
                        position = null
                        dragging = true
                        grabOffset = (offsetX - downX).coerceIn(-selectorWidthPx / 2f, selectorWidthPx / 2f)
                        log.add("t=${e.tMs} SLOP grab offset ${"%.1f".format(grabOffset)}")
                    }
                    liveVelocity = tr.calculateVelocity().x
                    moveTo(e.x, e.y)
                }
                is Event.Up -> {
                    val tr = tracker ?: return
                    pointerX = null
                    if (!slopCrossed || !motionEnabled) {
                        // a tap: the item's own clickable selects (the ancestor no longer does), then the
                        // ancestor releases to the selection
                        log.add("t=${e.tMs} UP (tap) item click selects $lastIndex")
                        selections.add(nowMs to lastIndex)
                        if (lastIndex != selected) selected = lastIndex
                        releaseToSelection(nowMs)
                    } else {
                        // Phase 2 fix: the lift belongs to the gesture the tracker saw; without it a hold
                        // before the lift keeps the last moving velocity and can fling the lens.
                        if (!legacyRules) tr.addPosition(e.tMs.toLong(), Offset(e.x, e.y))
                        val released = tr.calculateVelocity().x
                        dragging = false; liveVelocity = 0f
                        val projected = decideDragEnd(true, true, false, lastIndex, released, style.flingVelocity.value * density, geometry)!!
                        settleTo(geometry.centreOf(projected), released, style.settle, nowMs)
                        dragIndex = projected
                        log.add("t=${e.tMs} UP released v=${"%.0f".format(released)} px/s -> settle to tab $projected")
                        select(projected, nowMs)
                        held = false; dragging = false; liveVelocity = 0f; pressed = false
                    }
                    tracker = null
                }
            }
        }

        private fun moveTo(x: Float, y: Float) {
            offsetX = (x + grabOffset).coerceIn(geometry.firstCentre, geometry.lastCentre)
            localPress(x, y)
            val index = geometry.nearestIndex(offsetX)
            if (index != lastIndex) { lastIndex = index; dragIndex = index }
        }

        /** One frame at [nowMs]: input that arrived since the last frame, then the springs as of now. */
        fun frame(nowMs: Double, pending: List<Event>): Sample {
            for (e in pending) apply(e, nowMs)
            position?.let { m ->
                offsetX = m.value(nowMs); settleVelocity = m.velocity(nowMs)
                if (nowMs - m.startMs >= m.durationMs) { offsetX = m.to; settleVelocity = 0f; position = null }
            }
            lift.retarget(if (held) 1f else 0f, if (held) style.form else style.subside, nowMs)
            lift.sample(nowMs)
            val velocityNow = if (dragging) liveVelocity else settleVelocity
            val gelTarget = if (motionEnabled) style.gel * (abs(velocityNow) / (style.gelReference.value * density)).coerceAtMost(1f) else 0f
            gel.retarget(gelTarget, style.gelSpring, nowMs)
            gel.sample(nowMs)
            return Sample(nowMs, offsetX, settleVelocity, lift.lastValue, lift.lastVelocity, gel.lastValue, held, dragging, dragIndex, selected, pressX, pressY, pressed, pointerX)
        }
    }

    private fun run(events: List<Event>, hz: Double, endMs: Double, initialSelected: Int, dropFrameAt: Double? = null, legacyRules: Boolean = false): List<Sample> {
        val sel = Selector(initialSelected, legacyRules = legacyRules)
        val out = ArrayList<Sample>()
        val period = 1000.0 / hz
        var frame = 0; var next = 0
        var dropped = false
        while (frame * period <= endMs + 1e-9) {
            val t = frame * period
            frame++
            if (dropFrameAt != null && !dropped && t >= dropFrameAt) { dropped = true; continue }
            val pending = ArrayList<Event>()
            // An event stamped on the frame instant belongs to that frame (1 us of slack for the float sum).
            while (next < events.size && events[next].tMs <= t + 1e-6) { pending.add(events[next]); next++ }
            out.add(sel.frame(t, pending))
        }
        lastLog = sel.log; lastSelections = sel.selections
        return out
    }

    private var lastLog: List<String> = emptyList()
    private var lastSelections: List<Pair<Double, Int>> = emptyList()

    private fun ramp(t0: Double, t1: Double, x0: Float, x1: Float, y: Float, stepMs: Double = 8.0): List<Event> {
        val out = ArrayList<Event>()
        var t = t0 + stepMs
        while (t < t1 - 1e-9) { out.add(Event.Move(t, x0 + (x1 - x0) * ((t - t0) / (t1 - t0)).toFloat(), y)); t += stepMs }
        out.add(Event.Move(t1, x1, y))
        return out
    }

    private val y = barHeight / 2f

    // ---- the four traces --------------------------------------------------------------------------------

    private fun traceStopHoldRelease(): Pair<List<Event>, Int> {
        // Grab tab 1, drag right at ~1700 px/s for 150 ms, hold still for 400 ms, lift.
        val x0 = geometry.centreOf(1)
        return (listOf(Event.Down(0.0, x0, y)) + ramp(0.0, 150.0, x0, x0 + 255f, y) + listOf(Event.Up(550.0, x0 + 255f, y))) to 1
    }

    private fun traceReversal(): Pair<List<Event>, Int> {
        val x0 = geometry.centreOf(2)
        val a = ramp(0.0, 120.0, x0, x0 + 200f, y); val b = ramp(120.0, 320.0, x0 + 200f, x0 - 200f, y); val c = ramp(320.0, 470.0, x0 - 200f, x0 + 150f, y)
        return (listOf(Event.Down(0.0, x0, y)) + a + b + c + listOf(Event.Up(470.0, x0 + 150f, y))) to 2
    }

    private fun tracePressReleaseSelectedThenRePress(): Pair<List<Event>, Int> {
        val x0 = geometry.centreOf(2)
        // tap the selected tab (no slop), then press it again 90 ms into the release
        return listOf(Event.Down(0.0, x0, y), Event.Up(120.0, x0, y), Event.Down(210.0, x0 + 4f, y), Event.Up(700.0, x0 + 4f, y)) to 2
    }

    private fun traceRapidTwoBoundaryRetarget(): Pair<List<Event>, Int> {
        // From tab 0 across two boundaries inside 80 ms, lifted at 100 ms while still fast.
        val x0 = geometry.centreOf(0)
        return (listOf(Event.Down(0.0, x0, y)) + ramp(0.0, 80.0, x0, x0 + 410f, y) + listOf(Event.Up(100.0, x0 + 410f, y))) to 0
    }

    private data class Trace(val name: String, val events: List<Event>, val initial: Int, val endMs: Double)

    private val traces = listOf(
        Trace("stop_hold_release", traceStopHoldRelease().first, 1, 1600.0),
        Trace("left_right_left_reversal", traceReversal().first, 2, 1600.0),
        Trace("press_release_selected_repress", tracePressReleaseSelectedThenRePress().first, 2, 1800.0),
        Trace("rapid_two_boundary_retarget", traceRapidTwoBoundaryRetarget().first, 0, 1400.0),
    )

    private fun checkInvariants(name: String, s: List<Sample>, events: List<Event>) {
        val failures = ArrayList<String>()
        var prev: Sample? = null
        for (x in s) {
            if (x.offsetX.isNaN() || x.lift.isNaN() || x.gel.isNaN() || x.settleVelocity.isNaN()) failures.add("NaN at t=${x.tMs}")
            // The gel spring is underdamped (0.6), so it may undershoot a little below zero as it relaxes;
            // the deformation stays bounded by the authored 5 % either way.
            if (x.gel < -style.gel * 0.25f || x.gel > style.gel * 1.5f) failures.add("gel ${x.gel} out of bounds at t=${x.tMs}")
            if (x.lift < -0.05f || x.lift > 1.2f) failures.add("lift ${x.lift} out of bounds at t=${x.tMs}")
            // While dragging the lens is clamped to the outer tab centres; a settle toward an outer tab may
            // overshoot them by the settle spring's own bounce (damping 0.72), observed up to ~10 px after a
            // 2400 px/s fling. That is the authored behaviour, recorded, not a jump.
            val slack = if (x.dragging) 1f else 25f
            if (x.offsetX < geometry.firstCentre - slack || x.offsetX > geometry.lastCentre + slack) failures.add("offset ${x.offsetX} outside the bar at t=${x.tMs}")
            if (prev != null) {
                val dt = (x.tMs - prev.tMs) / 1000.0
                val moved = abs(x.offsetX - prev.offsetX)
                // Direct following: a dragged lens moves at most as far as the finger moved between the frames.
                if (x.dragging && prev.dragging) {
                    val commanded = abs((x.pointerX ?: prev.pointerX ?: 0f) - (prev.pointerX ?: 0f)) + 1e-3f
                    if (moved > commanded + 1e-2f) failures.add("lens moved $moved px while the finger moved $commanded at t=${x.tMs}")
                }
                // A spring never jumps: bounded by its own velocity over the frame, generously.
                if (!x.dragging && !prev.dragging && !(prev.dragging || prev.held != x.held)) {
                    val bound = (max(abs(x.settleVelocity), abs(prev.settleVelocity)) * dt * 2.0 + 0.5).toFloat()
                    if (moved > bound) failures.add("position jump $moved px (bound $bound) at t=${x.tMs}")
                }
                val liftStep = abs(x.lift - prev.lift)
                if (liftStep > 0.35f) failures.add("lift jump $liftStep at t=${x.tMs}")
            }
            prev = x
        }
        val last = s.last()
        if (last.held || last.dragging || last.pressed) failures.add("held/dragging/pressed still set at the end")
        if (last.dragIndex >= 0) failures.add("dragIndex ${last.dragIndex} not cleared at the end")
        if (abs(last.offsetX - geometry.centreOf(last.selected)) > 0.5f) failures.add("not settled on the selected tab: ${last.offsetX} vs ${geometry.centreOf(last.selected)}")
        if (abs(last.lift) > 0.01f) failures.add("lift ${last.lift} not subsided")
        if (abs(last.gel) > 0.002f) failures.add("gel ${last.gel} not relaxed")
        assertTrue(failures.isEmpty(), "$name: " + failures.joinToString("; "))
    }

    private fun at(s: List<Sample>, tMs: Double): Sample? = s.firstOrNull { abs(it.tMs - tMs) < 1e-6 }

    @Test
    fun testTheFourTracesAreStableAtEveryFrameRateAndAgreeAtEqualElapsedTimes() {
        val report = StringBuilder()
        for (tr in traces) {
            val runs = mapOf(60.0 to run(tr.events, 60.0, tr.endMs, tr.initial), 90.0 to run(tr.events, 90.0, tr.endMs, tr.initial), 120.0 to run(tr.events, 120.0, tr.endMs, tr.initial))
            val logs = HashMap<Double, List<String>>()
            for ((hz, s) in runs) { checkInvariants("${tr.name} @${hz.toInt()} Hz", s, tr.events); logs[hz] = lastLog }
            val dropped = run(tr.events, 60.0, tr.endMs, tr.initial, dropFrameAt = 140.0)
            checkInvariants("${tr.name} @60 Hz with a dropped frame", dropped, tr.events)
            // Same elapsed time, every 100 ms (a common instant of all three rates): the lens position,
            // lift and selection must agree within one frame of motion at the slowest rate.
            var worstPos = 0f; var worstLift = 0f; var worstT = 0.0
            var t = 0.0
            while (t <= tr.endMs) {
                val a = at(runs.getValue(60.0), t); val b = at(runs.getValue(90.0), t); val c = at(runs.getValue(120.0), t); val d = at(dropped, t)
                if (a != null && b != null && c != null) {
                    val p = max(abs(a.offsetX - b.offsetX), max(abs(a.offsetX - c.offsetX), abs(b.offsetX - c.offsetX)))
                    val l = max(abs(a.lift - b.lift), max(abs(a.lift - c.lift), abs(b.lift - c.lift)))
                    if (p > worstPos) { worstPos = p; worstT = t }
                    worstLift = max(worstLift, l)
                    assertEquals(a.selected, c.selected, "${tr.name}: selection differs between 60 and 120 Hz at t=$t")
                    assertEquals(a.dragIndex, c.dragIndex, "${tr.name}: dragIndex differs between 60 and 120 Hz at t=$t")
                    if (d != null) assertEquals(a.selected, d.selected, "${tr.name}: a dropped frame changed the selection at t=$t")
                }
                t += 100.0
            }
            val finals = runs.values.map { it.last() } + dropped.last()
            assertTrue(finals.map { it.selected }.distinct().size == 1, "${tr.name}: final selection differs across rates: ${finals.map { it.selected }}")
            assertTrue(finals.all { abs(it.offsetX - finals[0].offsetX) < 0.5f }, "${tr.name}: final position differs across rates")
            // One frame of a fast drag at 60 Hz is up to ~30 px (1700 px/s x 16.7 ms); that is the frame
            // quantisation Compose's input pipeline has, not a discontinuity.
            assertTrue(worstPos <= 35f, "${tr.name}: positions differ by $worstPos px at t=$worstT between frame rates")
            assertTrue(worstLift <= 0.2f, "${tr.name}: lift differs by $worstLift between frame rates")
            val overshoot = runs.getValue(60.0).maxOf { max(geometry.firstCentre - it.offsetX, it.offsetX - geometry.lastCentre) }.coerceAtLeast(0f)
            report.append("${tr.name}: final tab ${finals[0].selected} at x ${"%.1f".format(finals[0].offsetX)}; cross-rate max position diff ${"%.2f".format(worstPos)} px at t=$worstT ms, lift diff ${"%.3f".format(worstLift)}; max overshoot past the outer centres ${"%.1f".format(overshoot)} px\n")
            for (l in logs.getValue(60.0)) report.append("    $l\n")
        }
        println(report)
    }

    @Test
    fun testAHoldBeforeTheLiftDoesNotFlingAndAFastLiftDoes() {
        // Stop/hold/release: the real VelocityTracker assumes the pointer stopped after 40 ms without
        // samples once the lift is fed to it; the lens must settle on the tab it is over, not fling.
        val (events, initial) = traceStopHoldRelease()
        val s = run(events, 90.0, 1600.0, initial)
        val idxUnder = geometry.nearestIndex(geometry.centreOf(1) + 255f)
        assertEquals(idxUnder, s.last().selected, "a lift after a 400 ms hold must select the tab under the lens; log: $lastLog")
        val (fast, initial2) = traceRapidTwoBoundaryRetarget()
        val f = run(fast, 90.0, 1400.0, initial2)
        val under = geometry.nearestIndex(geometry.centreOf(0) + 410f)
        assertEquals((under + 1).coerceAtMost(4), f.last().selected, "a fast lift must carry to the next tab; log: $lastLog")
        println("hold-release: ${lastLog}")
    }

    /**
     * The two defects the audit found in the shipped rules, demonstrated against the fixed ones. Both are
     * local: the up sample fed to the tracker, and the spring velocity carried into a retarget.
     */
    @Test
    fun testTheShippedRulesFlungAfterAHoldAndSnappedVelocityOnARetarget() {
        val (events, initial) = traceStopHoldRelease()
        run(events, 90.0, 1600.0, initial, legacyRules = true)
        val old = releasedVelocity(lastLog)
        run(events, 90.0, 1600.0, initial, legacyRules = false)
        val new = releasedVelocity(lastLog)
        println("hold then lift: released velocity shipped $old px/s, fixed $new px/s")
        assertTrue(abs(old) > style.flingVelocity.value * density, "the shipped rule should have kept the pre-stop velocity ($old)")
        assertTrue(abs(new) < 50f, "the fixed rule must release from a standstill ($new)")
        // Retarget in place: fling from tab 1 toward tab 2, then press the lens 60 ms into the settle.
        val x0 = geometry.centreOf(1)
        val fling = listOf(Event.Down(0.0, x0, y)) + ramp(0.0, 60.0, x0, x0 + 120f, y) + listOf(Event.Up(70.0, x0 + 120f, y))
        val rePress = fling + listOf(Event.Down(130.0, x0 + 170f, y), Event.Up(400.0, x0 + 170f, y))
        fun velocityStep(legacy: Boolean): Float {
            val s = run(rePress, 120.0, 1600.0, 1, legacyRules = legacy)
            val before = s.last { it.tMs < 130.0 }; val after = s.first { it.tMs >= 130.0 }
            return abs(after.settleVelocity - before.settleVelocity)
        }
        val stepOld = velocityStep(true); val stepNew = velocityStep(false)
        println("re-press during settle: velocity step shipped ${"%.0f".format(stepOld)} px/s, fixed ${"%.0f".format(stepNew)} px/s")
        assertTrue(stepOld > 200f, "the shipped arrive should have snapped the velocity ($stepOld)")
        assertTrue(stepNew < stepOld * 0.25f, "the fixed arrive must continue from the running velocity ($stepNew vs $stepOld)")
    }

    private fun releasedVelocity(log: List<String>): Float {
        val line = log.first { it.contains("released v=") }
        return line.substringAfter("released v=").substringBefore(" ").toFloat()
    }

    @Test
    fun testARePressDuringTheReleaseRetargetsFromTheCurrentValueAndVelocity() {
        val (events, initial) = tracePressReleaseSelectedThenRePress()
        val s = run(events, 120.0, 1800.0, initial)
        // The lift at the re-press frame continues from its subsiding value: no snap to 0 or 1.
        val before = s.last { it.tMs < 210.0 }; val after = s.first { it.tMs >= 210.0 }
        assertTrue(abs(after.lift - before.lift) < 0.15f, "lift snapped at the re-press: ${before.lift} -> ${after.lift}")
        assertTrue(before.lift > 0.05f, "the subside must still be under way at the re-press (lift ${before.lift})")
        // And the position spring is retargeted from its running velocity, not from zero.
        println("re-press: lift ${before.lift} -> ${after.lift}, velocity before ${before.settleVelocity} after ${after.settleVelocity}; log $lastLog")
    }
}
