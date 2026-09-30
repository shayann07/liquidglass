package com.wexpa.liquidglass

import androidx.compose.runtime.Immutable
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * The authored V3 selector preset.
 *
 * Every number here is a **design constant**, not a recovered Apple time constant or a measured
 * physical property. They are the seeds of `V3-MODEL.md` section 6, chosen to make the candidate
 * concrete; the bounded ranges in that document are what tuning is allowed to move them within.
 * Angular frequencies are per second, thresholds in seconds, sizes as fractions of a slot width
 * or of the base radius.
 */
@Immutable
data class GlassSelectorSpec(
    /** Centre travel: critically damped by default, so a tap does not bounce the whole body. */
    val centreOmega: Float = 22f,
    val centreZeta: Float = 1.0f,
    /** The shape modes may overshoot internally; containment stops them leaving the bar. */
    val shapeOmega: Float = 26f,
    val shapeZeta: Float = 0.80f,
    /** How long an eligible press must last before the held lens starts forming. */
    val holdThresholdSeconds: Float = 0.120f,
    val formOmega: Float = 36f,
    val formZeta: Float = 1f,
    /** Size demand while held, including stationary content accommodation. */
    val sizeOmega: Float = 24f,
    val sizeZeta: Float = 0.90f,
    /** Peak velocity-driven extra length, in slot widths, before containment. */
    val maxExtraLength: Float = 0.60f,
    /** Peak radius reduction at speed, as a fraction of the base radius. */
    val maxRadiusDrop: Float = 0.30f,
    /** Peak end asymmetry, as a fraction of the base radius. */
    val maxSkew: Float = 0.18f,
    /**
     * Shared recovery rate. Authored 10/s: about 336ms for a zero-speed 90%-to-10% spring
     * decay, consistent in scale with E7's 314ms raised-area observation (a different metric).
     * The earlier 18/s seed blinked the lens away; neither rate is recovered Apple physics.
     */
    val releaseOmega: Float = 10f,
    /** How fast a hold acquired away from the body reels its attachment offset in. */
    val attachOmega: Float = 40f,
    /** Smallest end radius and smallest disk separation, in dp. */
    val minRadiusDp: Float = 4f,
    val minLengthDp: Float = 1f,
    /** Admission hysteresis, in dp: a neighbour is taken this far inside and let go this far out. */
    val admitInsetDp: Float = 2f,
    val admitReleaseDp: Float = 4f,
    /** Padding added beyond the admitted icon/label union, in dp. */
    val admitPaddingDp: Float = 6f,
    /** Total width ceiling, including accommodation AND speed deformation. Authored, not fitted. */
    val maxWidthSlots: Float = 1.9f,
    /**
     * Which motion model the V3 selector uses.
     *
     * `true` - the default - selects the 2D pose body of `GlassPoseDynamics.kt`: eight pose
     * states, free held protrusion, strain toward an arbitrary direction, and a release that
     * recovers translation and shape together. `false` keeps the five-coordinate horizontal-only
     * controller, whose motion the owner rejected; it exists so the two can be compared on one
     * phone, not as a supported configuration.
     *
     * Note that [maxWidthSlots] and every other field above belong to the **old** controller. The
     * pose controller has its own authored preset, `GlassPoseSpec`, and does not read them: the
     * parity brief supersedes the hard silhouette ceiling those numbers express.
     */
    val poseMotion: Boolean = true,
)

/** Which of the controller's states owns the body right now. */
internal enum class GlassSelectorMode { Rest, PressPending, TapTravel, Held, Release }

/**
 * The bar the body lives in, for one frame. Everything in physical px, bar-local, y down.
 *
 * [allowedHalfHeight] is the ordinary tap envelope (the bar itself) and [heldHalfHeight] the
 * larger envelope a genuine hold is allowed to grow into. The two are separate so an ordinary
 * tap can never acquire the held protrusion, whatever its speed.
 */
internal class GlassSelectorBar(
    val width: Float,
    val height: Float,
    val inset: Float,
    val count: Int,
    val cornerRadius: Float,
    val baseHalfWidth: Float,
    val baseHalfHeight: Float,
    val allowedHalfHeight: Float,
    val heldHalfHeight: Float,
    val heldHalfWidth: Float,
    val density: Float,
    /** The widest the body may ever be, so the host can size a stable node around it. */
    val maxBodyWidth: Float,
) {
    val slotWidth: Float get() = (width - inset * 2f) / count
    fun centreOf(index: Int): Float = inset + (index + 0.5f) * slotWidth
    val firstCentre: Float get() = centreOf(0)
    val lastCentre: Float get() = centreOf(count - 1)
    val centreY: Float get() = height / 2f

    /**
     * Whether [other] describes the **same coordinate frame and the same constraint set**.
     *
     * Every field here changes a plane of the containment polygon, a shape limit or an item
     * anchor, so every one of them has to invalidate. Keying a rebase on width/height/count alone
     * - which is what this used to do - lets a density change, a padding change or a style change
     * keep constraint rows built for an envelope that no longer exists, and lets the body sit on a
     * pixel anchor the new layout never declared.
     */
    fun sameFrameAs(other: GlassSelectorBar): Boolean =
        width == other.width && height == other.height && inset == other.inset &&
            count == other.count && cornerRadius == other.cornerRadius &&
            baseHalfWidth == other.baseHalfWidth && baseHalfHeight == other.baseHalfHeight &&
            allowedHalfHeight == other.allowedHalfHeight && heldHalfHeight == other.heldHalfHeight &&
            heldHalfWidth == other.heldHalfWidth && density == other.density &&
            maxBodyWidth == other.maxBodyWidth
}

/**
 * The bounded grasp offset `D(A) = clamp(beta*A, -B, B)` of the final brief, section 3.
 *
 * The body's centre while held is `cx = fingerX + offset - D(A)`, where `A = L/2 + r` is the
 * body's own half width and `B` the base body's half width. Writing it this way - rather than
 * hiding the bound inside a re-clamped beta - is what makes its derivative well defined, and the
 * derivative is what release has to carry.
 */
internal fun glassGraspOffsetD(beta: Float, halfWidth: Float, baseHalfWidth: Float): Float =
    (beta * halfWidth).coerceIn(-baseHalfWidth, baseHalfWidth)

/** `D'(A)`: beta inside the cap, exactly zero once the pixel cap is active. */
internal fun glassGraspOffsetSlope(beta: Float, halfWidth: Float, baseHalfWidth: Float): Float =
    if (abs(beta * halfWidth) >= baseHalfWidth) 0f else beta

/**
 * The unconstrained centre velocity of a bounded grasp:
 * `xRequested' = fingerVelocity + offset' - D'(A) * A'`.
 *
 * The implementation this replaces stored `fingerVelocity - beta * A'` with beta already clamped
 * in pixels, which is not the derivative of the position it renders. The brief's counterexample
 * is exact: with `B = 50, A = 100, beta = 0.8, A' = 40` and a still finger the cap is active, so
 * the growth contributes nothing and the correct velocity is 0 - the old formula returned -20.
 */
internal fun glassGraspCentreVelocity(
    beta: Float,
    halfWidth: Float,
    halfWidthVelocity: Float,
    baseHalfWidth: Float,
    fingerVelocity: Float,
    offsetVelocity: Float,
): Float = fingerVelocity + offsetVelocity -
    glassGraspOffsetSlope(beta, halfWidth, baseHalfWidth) * halfWidthVelocity

/**
 * One scalar second-order state, stepped exactly rather than by fixed per-frame increments.
 *
 * `x'' + 2ζω x' + ω²(x - target) = 0` has a closed form over an interval where the target is
 * frozen, so there is no integration damping to confuse with the damping that was asked for and
 * no dependence on frame rate. The under- and critically damped regimes are written separately;
 * an over-damped ζ would need the real-root solution and is rejected rather than pushed through
 * the under-damped square root.
 */
internal class GlassSpring(var value: Float = 0f, var velocity: Float = 0f) {
    var target: Float = value

    fun snapTo(x: Float) {
        value = x
        target = x
        velocity = 0f
    }

    fun step(dt: Float, omega: Float, zeta: Float) {
        if (dt <= 0f || omega <= 0f) return
        val d = value - target
        if (d == 0f && velocity == 0f) return
        val z = zeta.coerceIn(0f, 1f)
        if (z >= 0.9999f) {
            val e = exp(-omega * dt)
            val dNew = e * ((1f + omega * dt) * d + dt * velocity)
            val vNew = e * ((1f - omega * dt) * velocity - omega * omega * dt * d)
            value = target + dNew
            velocity = vNew
        } else {
            val a = z * omega
            val b = omega * sqrt(1f - z * z)
            val e = exp(-a * dt)
            val cb = cos(b * dt)
            val sb = sin(b * dt)
            val dNew = e * (d * cb + (velocity + a * d) * sb / b)
            val vNew = e * (velocity * cb - (a * velocity + omega * omega * d) * sb / b)
            value = target + dNew
            velocity = vNew
        }
    }

    /** The spring acceleration before any contact correction: the drive, not the collision. */
    fun acceleration(omega: Float, zeta: Float): Float =
        -2f * zeta * omega * velocity - omega * omega * (value - target)
}

/**
 * The V3 selector controller: a small deforming body, its contact constraints, and the rules that
 * decide what it is doing.
 *
 * It owns no Compose state and no pointer plumbing. [GlassTabBar] feeds it timestamped pointer
 * facts and reads [body] back each displayed frame; the deterministic tests drive the same class
 * directly. Simulation substeps change numbers, not composition.
 */
internal class GlassSelectorController(
    var spec: GlassSelectorSpec = GlassSelectorSpec(),
) {
    private val cxS = GlassSpring()
    private val cyS = GlassSpring()
    private val lengthS = GlassSpring()
    private val radiusS = GlassSpring()
    private val skewS = GlassSpring()
    /** 0 the flat resting inset, 1 the fully formed held lens. */
    private val formS = GlassSpring()

    private val q = FloatArray(5)
    private val qStar = FloatArray(5)
    private val vStar = FloatArray(5)
    private val vOut = FloatArray(5)
    private val projected = FloatArray(5)
    private val projector = GlassBodyProjector()
    // Generously sized: a capsule bar at a large radius needs a hundred planes for its inscribed
    // polygon alone, and a release adds the rest planes on top of the held ones. Overflow is
    // reported (see GlassBodyConstraints.overflowed) rather than silently dropping a wall.
    private var constraints = GlassBodyConstraints(512)
    private var restConstraints = GlassBodyConstraints(512)

    private lateinit var bar: GlassSelectorBar

    var mode: GlassSelectorMode = GlassSelectorMode.Rest
        private set
    /** Seconds since the controller's clock origin; only differences matter. */
    private var now: Float = 0f
    private var pressStart: Float = 0f
    private var pressEligible: Boolean = false
    private var releaseStart: Float = 0f

    /** Grasp: a fixed normalized attachment in [-0.8, 0.8] plus a relaxing offset. */
    private var beta: Float = 0f
    private var graspX: Float = 0f
    private var graspVelocity: Float = 0f
    private var attachOffset: Float = 0f
    /**
     * The attachment offset's own velocity.
     *
     * The offset is a **critically damped second-order state targeting zero**, not a first-order
     * decay: acquisition has to be able to choose its initial velocity independently so the
     * incoming centre velocity survives the grab. A first-order decay fixes `o'(0) = -omega*o0`
     * and therefore throws away whatever the body was already doing.
     */
    private var attachOffsetVelocity: Float = 0f
    private var fingerX: Float = 0f
    private var lastFingerX: Float = 0f
    // Absolute Android uptime can be weeks old. Float seconds have 125ms steps at 20 days;
    // convert a Double time DIFFERENCE to Float, never the absolute event timestamps.
    private var lastFingerTime: Double = 0.0
    /** The controller-clock time of the most recent pointer sample, for the stop rule below. */
    private var lastSampleAt: Float = 0f
    /** The centre velocity the grasp is asking for, before any wall takes it away. */
    private var heldDrive: Float = 0f
    /** The most recent grasp the finger asked for, against what the solver could give. */
    var graspError: Float = 0f
        private set

    /**
     * The admitted ink's own vertical half extent, in bar px, from the same layout snapshot.
     *
     * Zero means "treat the demand as horizontal only", which is what a caller that has not
     * measured its ink gets and is exactly the old behaviour.
     */
    var inkHalfHeight: Float = 0f
        private set

    /** Admitted item bounds, in bar px, from one layout snapshot. */
    private var itemLeft: FloatArray = FloatArray(0)
    private var itemRight: FloatArray = FloatArray(0)
    private var itemCentre: FloatArray = FloatArray(0)
    private var admitted: Int = -1
    private var admittedNeighbour: Int = -1

    /** True when the last projection could not produce a feasible body. */
    var solverFailed: Boolean = false
        private set
    /** True when the body is pressed against a wall and its extra length is being cut short. */
    var saturated: Boolean = false
        private set
    /**
     * True when nothing constrained the **centre** on the last substep: no end clamp, no
     * positional correction, no velocity correction. The kinematic gate needs this rather than
     * [saturated], which is true almost always because the body rests on its own height limit.
     */
    internal var centreUnconstrained: Boolean = false
        private set
    var motionEnabled: Boolean = true

    /** False until the first [attach] plus [snapToRest]; the host uses it to seed the body once. */
    var started: Boolean = false
        private set

    /** Consecutive substeps in which nothing moved by more than [QUIET_PX]. */
    private var quietSteps: Int = 0
    private val lastQ = FloatArray(6)

    private var releaseExcess = FloatArray(0)
    private var releaseSpeed = FloatArray(0)
    private var releaseActive = false
    /** Recovery ownership outlives the protrusion allowance; an ordinary tap never acquires it. */
    private var releasedFromHold = false

    // ---------------------------------------------------------------- lifetime and ownership

    /** The item the body rests on when no gesture owns it. Survives a rebase; the anchor does not. */
    private var restIndex: Int = 0

    /**
     * Bumped whenever ownership changes hands: a rebase, a release, a cancellation, a motion
     * toggle. A coroutine that started under an earlier value must not mutate the controller
     * afterwards, so the host compares this rather than assuming its gesture is still current.
     */
    var interactionId: Int = 0
        private set

    /** True between DOWN and UP/cancel. A long frame gap with a finger still down is suspension. */
    private var pointerDownActive: Boolean = false

    /** The last position both projections accepted; what a failed substep falls back to. */
    private val lastFeasible = FloatArray(5)
    private var hasLastFeasible = false

    /** The frozen release seed of section 4: `q0` at release and a feasible rest `qR`. */
    private val seedQ0 = FloatArray(5)
    private val seedQR = FloatArray(5)
    private var seedValid = false

    /** Per-row velocity limits: `b_j'(t)`, zero for a static wall. */
    private var rowLimit = FloatArray(512)
    /** Where the release's rest rows begin inside [constraints]; -1 when none are appended. */
    private var releaseRowStart = -1
    /** The frozen outer envelope key for the current release interval. */
    private var releaseEnvelopeKey = Float.NaN

    /**
     * False when not even the simple feasible fallback can be admitted by this layout.
     *
     * The host suppresses the selector and keeps the interactive row rather than drawing a body
     * that violates its own containment. Any occurrence in a normal acceptance scenario is a
     * correctness failure, not a graceful degradation.
     */
    var selectorValid: Boolean = true
        private set

    /**
     * The worst `A_j·v - b_j'(t)` seen over the substeps of the last [advanceTo], across rows that
     * are active at the projected position. Zero when every active row's inequality held.
     */
    var rowVelocityExcess: Float = 0f
        private set

    // Test-only fault injection (see V3-FINAL-POLISH section 4). Internal, so only this module
    // and its own test source set can reach it; nothing in the production path sets either.
    internal var injectProjectionFailure: Boolean = false
    internal var injectConstraintCapacity: Int = 0
        set(value) {
            field = value
            if (value > 0) {
                constraints = GlassBodyConstraints(value)
                restConstraints = GlassBodyConstraints(value)
                constraintsKey = Float.NaN
                restKey = Float.NaN
                releaseEnvelopeKey = Float.NaN
            }
        }

    val body: GlassBody
        get() = GlassBody(cxS.value, cyS.value, lengthS.value, radiusS.value, skewS.value)

    /** The body's half width without building one: `L/2 + r`, read several times a substep. */
    private val halfWidthNow: Float get() = lengthS.value / 2f + radiusS.value

    // One budget for the final contour. A wider explicitly configured resting pill still fits.
    private val widthLimit: Float get() = min(bar.width,
        max(2f * bar.baseHalfWidth, min(bar.maxBodyWidth, spec.maxWidthSlots * bar.slotWidth)))

    /** 0 the flat resting inset, 1 the fully formed held lens. */
    val formation: Float get() = formS.value.coerceIn(0f, 1f)

    val centreVelocity: Float get() = cxS.velocity

    val isHeld: Boolean get() = mode == GlassSelectorMode.Held

    /**
     * Nothing left to animate: no finger, the centre and every shape mode are on target and
     * still, and the lens has fully subsided. The host stops asking for frames here, so a
     * resting bar costs no frame callback at all.
     */
    /**
     * Nothing has changed for long enough to stop asking for frames.
     *
     * Measured on what the body **did**, not on how far it is from its target: containment
     * regularly leaves a coordinate permanently short of the target it is being pulled toward -
     * a body pressed into the bar's end cap is the ordinary case - and a distance-to-target rule
     * would then never report idle and the host would ask for a frame a frame for ever. Quiet
     * substeps also handle a spring's turning point, where the velocity passes through zero while
     * the body is still moving.
     *
     * A press whose hold timer has not expired is never idle: the threshold is what turns it into
     * a hold and nothing else will fire it. A finger resting motionless on a settled body is
     * idle, because the next pointer sample wakes the loop again.
     */
    val isIdle: Boolean
        get() = mode != GlassSelectorMode.PressPending &&
            !releaseActive &&
            abs(graspVelocity) <= 1f &&
            quietSteps >= QUIET_STEPS

    /**
     * Take the latest layout.
     *
     * An **ink-only revision** - new text, a new paint variant, a different emphasis or different
     * measured bounds, with the bar's coordinate frame unchanged - refreshes accommodation and
     * nothing else: it does not cancel a hold, reset the formation or disturb the release.
     *
     * A genuine change of the bar's coordinate frame or constraint set is a **lifecycle rebase**:
     * the body is put at rest on the latest valid item in the new frame, every piece of gesture,
     * preview, press and release ownership is cleared together, and the interaction id is bumped
     * so a cancelled coroutine from the old interaction cannot mutate what replaced it. The 0.25 px
     * event-continuity gate does not apply to this transition, and only to this one: an ordinary
     * tap, hold or release never gets the exception.
     */
    fun attach(
        bar: GlassSelectorBar,
        itemBounds: FloatArray?,
        itemCentres: FloatArray?,
        inkHalfHeight: Float = this.inkHalfHeight,
    ) {
        val first = !this::bar.isInitialized
        val relayout = first || !this.bar.sameFrameAs(bar)
        this.bar = bar
        // Ink measurements, not a coordinate frame: a taller label refreshes accommodation and
        // never rebases.
        this.inkHalfHeight = max(inkHalfHeight, 0f)
        if (itemBounds != null && itemCentres != null && itemCentres.isNotEmpty()) {
            if (itemLeft.size != itemCentres.size) {
                itemLeft = FloatArray(itemCentres.size)
                itemRight = FloatArray(itemCentres.size)
                itemCentre = FloatArray(itemCentres.size)
            }
            for (i in itemCentres.indices) {
                itemLeft[i] = itemBounds[i * 2]
                itemRight[i] = itemBounds[i * 2 + 1]
                itemCentre[i] = itemCentres[i]
            }
        }
        if (!relayout) return
        // Every cached row set belongs to the old polygon. Release-plane indices are never reused
        // across a different polygon, so the allowance arrays go with them.
        constraintsKey = Float.NaN
        restKey = Float.NaN
        releaseEnvelopeKey = Float.NaN
        releaseRowStart = -1
        hasLastFeasible = false
        solverFailed = false
        selectorValid = true
        // Before the first snapToRest the host still owns the seed; there is nothing to rebase.
        if (!first && started) rebaseToRest()
    }

    /**
     * The declared lifecycle rebase: a feasible rest state in the **new** bar, with every piece of
     * obsolete ownership cleared in one place rather than a field at a time.
     */
    private fun rebaseToRest() {
        interactionId++
        clearGestureOwnership()
        snapToRest(restIndex.coerceIn(0, max(bar.count - 1, 0)))
    }

    /** Gesture, preview, press and release ownership, and the sampled history behind them. */
    private fun clearGestureOwnership() {
        releasedFromHold = false
        pressEligible = false
        pointerDownActive = false
        releaseActive = false
        releaseRowStart = -1
        releaseEnvelopeKey = Float.NaN
        seedValid = false
        beta = 0f
        attachOffset = 0f
        attachOffsetVelocity = 0f
        graspVelocity = 0f
        heldDrive = 0f
        graspError = 0f
        admitted = -1
        admittedNeighbour = -1
        if (releaseExcess.isNotEmpty()) {
            releaseExcess.fill(0f)
            releaseSpeed.fill(0f)
        }
    }

    /** Put the body at rest on [index] with no motion. Used at first composition and on a rebase. */
    fun snapToRest(index: Int) {
        releasedFromHold = false
        restIndex = index.coerceIn(0, max(bar.count - 1, 0))
        cxS.snapTo(bar.centreOf(restIndex))
        cyS.snapTo(bar.centreY)
        lengthS.snapTo(max(2f * bar.baseHalfWidth - 2f * bar.baseHalfHeight, spec.minLengthDp * bar.density))
        radiusS.snapTo(bar.baseHalfHeight)
        skewS.snapTo(0f)
        formS.snapTo(0f)
        mode = GlassSelectorMode.Rest
        releaseActive = false
        releaseRowStart = -1
        releaseEnvelopeKey = Float.NaN
        seedValid = false
        admitted = -1
        admittedNeighbour = -1
        started = true
        solverFailed = false
        selectorValid = true
        hasLastFeasible = false
        quietSteps = QUIET_STEPS
        lastQ[0] = cxS.value; lastQ[1] = cyS.value; lastQ[2] = lengthS.value
        lastQ[3] = radiusS.value; lastQ[4] = skewS.value; lastQ[5] = formS.value
    }

    /** Aim the resting body at [index] without disturbing what it is currently doing. */
    fun retarget(index: Int) {
        restIndex = index.coerceIn(0, max(bar.count - 1, 0))
        val target = bar.centreOf(restIndex)
        cxS.target = target
        // Aiming where the body already is is not travel: it must not wake the frame loop.
        if (abs(cxS.value - target) >= 0.25f) {
            quietSteps = 0
            if (mode == GlassSelectorMode.Rest) mode = GlassSelectorMode.TapTravel
        }
    }

    /**
     * Touch down. The controller's own clock is the frame clock, so the hold threshold is
     * measured on it; pointer timestamps are kept for velocity only, where precision actually
     * matters and where mixing two clock bases would be wrong.
     */
    fun pointerDown(x: Float, y: Float, eventSeconds: Float, eligible: Boolean) {
        pointerDown(x, y, eventSeconds.toDouble(), eligible)
    }

    fun pointerDown(x: Float, y: Float, eventSeconds: Double, eligible: Boolean) {
        pressStart = now
        quietSteps = 0
        pressEligible = eligible
        pointerDownActive = true
        fingerX = x
        lastFingerX = x
        lastFingerTime = eventSeconds
        graspVelocity = 0f
        // A pending DOWN records eligibility and pointer data; it does **not** take kinematic
        // ownership. The grasp is acquired at the moment the press actually becomes a hold, from
        // the body as it is *then* and the latest pointer - not from a body that is up to
        // `holdThresholdSeconds` out of date.
        lastSampleAt = now
        mode = GlassSelectorMode.PressPending
        // A DOWN during an existing held release preserves the legal envelope and its age: the
        // release allowance is only replaced by an actual state transition. Clearing it here would
        // snap a protruding body back into the rest bar under the finger.
    }

    /**
     * A recognized horizontal drag: from here the body is grasped rather than sprung.
     *
     * If the gesture is **already** held - the threshold fired before slop was crossed - the
     * existing grasp is kept. Acquiring it a second time would re-seat the attachment offset from
     * a finger that has since moved and step the centre.
     */
    fun beginDrag(x: Float) {
        fingerX = x
        if (mode != GlassSelectorMode.Held) {
            acquireGrasp(x)
            mode = GlassSelectorMode.Held
        }
        quietSteps = 0
    }

    /**
     * Where the body's centre is being asked to go for a finger at [fingerX], from the grasp the
     * drag acquired. The host uses it to decide which tab the drag is previewing on the same
     * frame the finger moved, without waiting for the body to get there.
     */
    fun grabCentreFor(fingerX: Float): Float {
        val a = max(halfWidthNow, 1e-3f)
        // Navigation intent is independent of how far the *contour* can centre at an end cap.
        // Clamping this by the growing half width made the outermost item unreachable on a
        // slow drag; a fast fling happened to hide the defect by adding one item afterwards.
        return (fingerX + attachOffset - glassGraspOffsetD(beta, a, bar.baseHalfWidth))
            .coerceIn(bar.firstCentre, bar.lastCentre)
    }

    fun pointerMove(x: Float, eventSeconds: Float) {
        pointerMove(x, eventSeconds.toDouble())
    }

    fun pointerMove(x: Float, eventSeconds: Double) {
        fingerX = x
        lastSampleAt = now
        quietSteps = 0
        if (eventSeconds > lastFingerTime) {
            graspVelocity = (x - lastFingerX) / max((eventSeconds - lastFingerTime).toFloat(), 1e-4f)
            lastFingerX = x
            lastFingerTime = eventSeconds
        }
    }

    /**
     * The finger left. [carryVelocity] is the **finger's** terminal velocity as the host's tracker
     * judged it, which is what a fling decision uses; the body keeps its own centre velocity,
     * which differs from the finger's whenever the body is also changing size.
     */
    fun pointerUp(restIndex: Int) {
        // Only a genuine held interaction, or a still-active recovery from one, may own a
        // protrusion allowance. Ordinary tap travel keeps the rest envelope throughout, whatever
        // its speed: positive travel velocity must not manufacture a held allowance on every UP.
        val owned = mode == GlassSelectorMode.Held || releaseActive
        releasedFromHold = owned || releasedFromHold
        interactionId++
        this.restIndex = restIndex.coerceIn(0, max(bar.count - 1, 0))
        quietSteps = 0
        cxS.target = bar.centreOf(this.restIndex)
        mode = GlassSelectorMode.Release
        releaseStart = now
        admitted = -1
        admittedNeighbour = -1
        pressEligible = false
        pointerDownActive = false
        graspVelocity = 0f
        heldDrive = 0f
        attachOffsetVelocity = 0f
        if (owned) {
            armReleaseEnvelope()
        } else {
            releaseActive = false
            releaseRowStart = -1
            releaseEnvelopeKey = Float.NaN
            seedValid = false
        }
    }

    /** Cancelled: identical geometry recovery, no selection. */
    fun cancel(restIndex: Int) = pointerUp(restIndex)

    /**
     * Take the grasp, using the same capped law the substep uses.
     *
     * `offset0 = cx + D(A0) - fingerX0` - initializing from the **uncapped** `beta*A0` here while
     * the step uses the capped `D(A)` is exactly the jump the brief describes. The offset's
     * initial velocity is chosen so the body's incoming centre velocity survives the grab:
     * `offsetVelocity0 = cx' - fingerVelocity0 + D'(A0) * A'`, which a first-order decay cannot
     * express. A re-grab during a release therefore seeds from the current state, never from rest.
     */
    private fun acquireGrasp(x: Float) {
        releasedFromHold = false
        releaseActive = false
        releaseRowStart = -1
        releaseEnvelopeKey = Float.NaN
        constraintsKey = Float.NaN
        seedValid = false
        val a = max(halfWidthNow, 1e-3f)
        beta = ((x - cxS.value) / a).coerceIn(-0.8f, 0.8f)
        val d = glassGraspOffsetD(beta, a, bar.baseHalfWidth)
        attachOffset = cxS.value + d - x
        val vA = lengthS.velocity / 2f + radiusS.velocity
        attachOffsetVelocity = cxS.velocity - graspVelocity +
            glassGraspOffsetSlope(beta, a, bar.baseHalfWidth) * vA
        graspX = x + attachOffset
        fingerX = x
    }

    /** Record the present excess and outward speed against the **rest** bar, for section 7.3. */
    private fun armReleaseEnvelope() {
        val rows = restConstraints.rows
        if (rows == 0) {
            releaseActive = false
            seedValid = false
            return
        }
        if (releaseExcess.size < rows) {
            releaseExcess = FloatArray(rows)
            releaseSpeed = FloatArray(rows)
        }
        q[0] = cxS.value; q[1] = cyS.value; q[2] = lengthS.value; q[3] = radiusS.value; q[4] = skewS.value
        vStar[0] = cxS.velocity; vStar[1] = cyS.velocity
        vStar[2] = lengthS.velocity; vStar[3] = radiusS.velocity; vStar[4] = skewS.velocity
        for (row in 0 until rows) {
            releaseExcess[row] = max(0f, restConstraints.value(row, q) - restConstraints.b[row])
            var s = 0f
            val i = row * 5
            for (c in 0..4) s += restConstraints.a[i + c] * vStar[c]
            releaseSpeed[row] = max(0f, s)
        }
        // The constructive seed of section 4, frozen for this release interval. `q0` is where the
        // release starts; `qR` is the nominal feasible rest body in the current bar. Convexity
        // then gives `A_j*qSeed <= b_j + f(t)*d_j <= b_j + a_j(t)` on the fixed rest rows, so the
        // seed is a **validated** fallback for the constraints as they are now - not yesterday's
        // feasible point, which is unsafe the moment a wall moves inward.
        for (i in 0..4) seedQ0[i] = q[i]
        seedQR[0] = bar.centreOf(restIndex)
        seedQR[1] = bar.centreY
        seedQR[2] = max(2f * bar.baseHalfWidth - 2f * bar.baseHalfHeight, spec.minLengthDp * bar.density)
        seedQR[3] = bar.baseHalfHeight
        seedQR[4] = 0f
        seedValid = glassBodyFeasible(restConstraints, seedQR, tolerance = 0.05f)
        releaseActive = true
        releaseRowStart = -1
        releaseEnvelopeKey = Float.NaN
    }

    /** `f(t) = (1 + omega t) exp(-omega t)`, the seed's blend from `q0` back to `qR`. */
    private fun seedInto(t: Float, outQ: FloatArray, outV: FloatArray) {
        val w = spec.releaseOmega
        val e = exp(-w * t)
        val f = (1f + w * t) * e
        val fDot = -w * w * t * e
        for (i in 0..4) {
            outQ[i] = (1f - f) * seedQR[i] + f * seedQ0[i]
            outV[i] = fDot * (seedQ0[i] - seedQR[i])
        }
    }

    /**
     * Advance to [time] on a common clock with a maximum substep of 1/240 s.
     *
     * A long delayed frame runs the substeps it needs and renders once; it never replays a stale
     * gesture or synthesizes a pointer sample. Beyond [maxCatchUpSeconds] the interaction is
     * treated as abandoned, which is lifecycle recovery rather than an animation frame.
     */
    fun advanceTo(time: Float, maxCatchUpSeconds: Float = 0.5f) {
        rowVelocityExcess = 0f
        if (time.isNaN()) return
        // Simulation time belongs to the controller, not to a restartable effect-local clock.
        //
        // Time going backwards means the host's clock **origin** moved, not that the interaction
        // got younger. Assigning `now = time` alone - which is what happened before - rewound the
        // hold threshold, the release age and every press timestamp with it: a 200 ms eligible
        // press past a 120 ms threshold stayed at formation 0 for ever. So the origin is rebased
        // and every stored controller timestamp is shifted by exactly the same amount, which
        // changes no measured duration and can never produce a negative release age. Pointer
        // timestamps are deliberately *not* shifted: they are on the event clock, which is the
        // only clock velocity may be measured on.
        if (time < now) {
            val shift = time - now
            now = time
            pressStart += shift
            releaseStart += shift
            lastSampleAt += shift
            return
        }
        var remaining = time - now
        if (remaining <= 0f) return
        if (remaining > maxCatchUpSeconds) {
            if (!pointerDownActive) {
                // A backgrounded/restarted renderer must not replay only the first 500ms of
                // a many-second release. Adopt its settled state; ordinary frames never use
                // this lifecycle path. This matters now that recovery is deliberately slower.
                snapToRest(restIndex)
                now = time
                return
            }
            // A long gap with the finger still on the glass is a **suspended loop**, not an
            // abandoned gesture: a stationary held finger whose next MOVE arrives seconds later
            // must keep its hold. Only a gap with no pointer down is lifecycle interruption.
            // Either way the gap is never replayed as motion.
            remaining = maxCatchUpSeconds
        }
        val maxStep = 1f / 240f
        while (remaining > 1e-7f) {
            val dt = min(remaining, maxStep)
            substep(dt)
            remaining -= dt
            now += dt
        }
        now = time
    }

    private fun substep(dt: Float) {
        val s = bar.slotWidth
        val omegaC = spec.centreOmega
        val zetaC = spec.centreZeta

        // Disabling motion cancels active gesture ownership **without selecting** and adopts the
        // current selected rest state. Leaving the mode alone - which is what happened before -
        // left a reduced-motion bar still being dragged by a finger it was no longer animating.
        if (!motionEnabled &&
            (mode == GlassSelectorMode.Held || mode == GlassSelectorMode.PressPending || releaseActive)
        ) {
            interactionId++
            clearGestureOwnership()
            mode = GlassSelectorMode.Rest
            cxS.target = bar.centreOf(restIndex)
            // Reduced motion is still and flat: the lens does not subside over a fifth of a
            // second, it is simply not there.
            formS.snapTo(0f)
        }

        // Held formation: DOWN alone never starts the protruding lens; only an eligible press
        // that outlives the threshold, or a recognized drag, does.
        val wantForm = motionEnabled && (
            mode == GlassSelectorMode.Held ||
                (mode == GlassSelectorMode.PressPending && pressEligible &&
                    now - pressStart >= spec.holdThresholdSeconds)
            )
        formS.target = if (wantForm) 1f else 0f
        formS.step(dt, if (wantForm) spec.formOmega else spec.releaseOmega, spec.formZeta)

        // The drive: the pre-contact spring acceleration, never a contact correction.
        //
        // A grasped body has no spring pulling its centre - the finger is - so while it is held
        // the drive is what the same spring would be doing at that speed. Using the spring's own
        // acceleration there would read zero whenever the grasp is clamped at the end of the
        // bar, and the body would stop deforming exactly where the reference deforms it most.
        val aPre = if (mode == GlassSelectorMode.Held) {
            -2f * zetaC * omegaC * heldDrive
        } else {
            cxS.acceleration(omegaC, zetaC)
        }
        val driveVelocity = if (mode == GlassSelectorMode.Held) graspVelocity else cxS.velocity
        val u = abs(driveVelocity) / max(s * omegaC, 1e-3f)
        val z = aPre / max(s * omegaC * omegaC, 1e-3f)

        val baseHalfWidth = bar.baseHalfWidth
        val baseRadius = bar.baseHalfHeight
        val form = formS.value.coerceIn(0f, 1f)
        val heldRadius = baseRadius + (bar.heldHalfHeight - baseRadius) * form
        val heldHalfWidth = baseHalfWidth + (bar.heldHalfWidth - baseHalfWidth) * form

        val extra = if (motionEnabled) spec.maxExtraLength * s * tanh(u) else 0f
        val skewTarget = if (motionEnabled) -spec.maxSkew * baseRadius * tanh(z) else 0f
        // A body's half height is r + |k|, so end asymmetry costs vertical envelope. Aiming the
        // mean radius at the full envelope and the asymmetry on top of it puts the two in
        // competition: the wall wins, the radius sits at the limit, and the velocity projection
        // then damps every attempt to grow k - the body stays a capsule however hard it is
        // driven. Carving the asymmetry out of the radius instead keeps the declared envelope
        // exactly and lets the ends differ inside it.
        val radiusTarget = if (motionEnabled) {
            max(
                heldRadius * (1f - spec.maxRadiusDrop * tanh(u)) - abs(skewTarget),
                spec.minRadiusDp * bar.density + abs(skewTarget),
            )
        } else {
            heldRadius
        }
        // Content accommodation: a size demand of its own, sprung every substep, so a stationary
        // finger still grows the body around the label it has arrived at. It is computed after
        // the radius target because the end-cap deficit depends on the radius the body is
        // actually heading for - the speed drop and the asymmetry carve-out both shrink the cap,
        // and a demand computed against the envelope radius leaves the corners short.
        val requiredHalfWidth = if (mode == GlassSelectorMode.Held) {
            // The cap the corners will actually meet, not the one the radius is heading for: the
            // radius is damped against the height wall it is resting on and arrives a fraction of
            // a pixel short, and a demand computed against the target leaves that fraction of the
            // label outside. Taking the smaller of the current and target caps makes the demand
            // self-correcting - a shorter cap asks for more length, and length is an independent
            // coordinate, so there is no loop.
            val cap = min(radiusTarget, radiusS.value) - abs(skewS.value)
            admittedHalfWidth(heldHalfWidth, max(cap, 1e-3f))
        } else {
            heldHalfWidth
        }
        // `A = L/2 + r`, so the length that delivers a half-width demand depends on the radius the
        // body **has**, not the one it is heading for. Against a height wall the radius arrives a
        // fraction of a pixel short and stays there, and pricing the length at the target radius
        // leaves exactly that fraction of the demand unmet for ever. The radius does not depend on
        // the length, so this is a cascade, not a loop.
        val radiusNow = if (mode == GlassSelectorMode.Held) min(radiusTarget, radiusS.value) else radiusTarget
        // Content growth and motion strain share one envelope. Adding strain to an already
        // accommodated two-item body is what let a drag recruit a third item.
        val targetWidth = min(widthLimit, max(2f * requiredHalfWidth, 2f * heldHalfWidth + extra))
        val lengthTarget = max(targetWidth - 2f * radiusNow, spec.minLengthDp * bar.density)

        val recovering = releasedFromHold &&
            (mode == GlassSelectorMode.Release || mode == GlassSelectorMode.PressPending)
        if (recovering) {
            // A released body relaxes as ONE vector q, to the selected resting body. With the
            // same critically damped operator on every coordinate, any linear contour extent
            // (in particular W=L+2r) also follows that exact spring. No travelling-centre speed
            // is fed back as fresh stretch while the body is trying to shrink.
            lengthS.target = max(2f * baseHalfWidth - 2f * baseRadius, spec.minLengthDp * bar.density)
            radiusS.target = baseRadius
            skewS.target = 0f
        } else {
            lengthS.target = lengthTarget
            radiusS.target = radiusTarget
            skewS.target = skewTarget
        }
        cyS.target = bar.centreY

        val shapeOmega = if (recovering) spec.releaseOmega else if (mode == GlassSelectorMode.Held) spec.sizeOmega else spec.shapeOmega
        val shapeZeta = if (recovering) 1f else if (mode == GlassSelectorMode.Held) spec.sizeZeta else spec.shapeZeta
        lengthS.step(dt, shapeOmega, shapeZeta)
        radiusS.step(dt, shapeOmega, shapeZeta)
        skewS.step(dt, if (recovering) spec.releaseOmega else spec.shapeOmega, if (recovering) 1f else spec.shapeZeta)
        cyS.step(dt, if (recovering) spec.releaseOmega else omegaC, if (recovering) 1f else zetaC)

        if (mode == GlassSelectorMode.Held) {
            // A finger that has stopped must stop driving. The velocity tracker's own rule is
            // that two samples more than 40 ms apart mean a stop, so past that window the drive
            // decays on the real clock instead of keeping the last moving speed alive for as long
            // as the finger rests (V3-MODEL section 6).
            if (now - lastSampleAt > 0.040f) graspVelocity *= exp(-dt / 0.060f)
            // The attachment offset is a critically damped state targeting zero, stepped exactly:
            //   o(t)  = [o0 + (v0 + w o0) t] e^(-w t)
            //   o'(t) = [v0 - w (v0 + w o0) t] e^(-w t)
            // so the offset's own velocity is a real coordinate and acquisition can choose it.
            val w = spec.attachOmega
            val o0 = attachOffset
            val v0 = attachOffsetVelocity
            val e = exp(-w * dt)
            attachOffset = (o0 + (v0 + w * o0) * dt) * e
            attachOffsetVelocity = (v0 - w * (v0 + w * o0) * dt) * e

            graspX = fingerX + attachOffset
            val a = max(halfWidthNow, 1e-3f)
            // The grasp is normalized so that width changes happen around it, but it is also
            // bounded in pixels: a body that has grown to two and a half slots would otherwise
            // put its centre most of a slot away from the finger holding it, and the tab a drag
            // commits is decided by that centre. The bound is the base body's own half width,
            // which is the same reach the capsule selector allowed. Declared deviation from the
            // seed of V3-MODEL section 7.1, kept because it preserves what a drag selects.
            //
            // Written as `D(A) = clamp(beta*A, -B, B)` so that its derivative exists: beta inside
            // the cap, exactly zero once the cap is active.
            val requested = graspX - glassGraspOffsetD(beta, a, bar.baseHalfWidth)
            // A finger past the end of the bar asks for a centre the body cannot have. Take the
            // bounded closest feasible grasp instead of handing the projection an impossible
            // request: the metric would rather collapse the body than move its centre, so an
            // unclamped request 300 px outside would shrink the lens to nothing instead of
            // stopping it at the end (V3-MODEL section 7.1).
            val feasible = bar.clampCentre(requested, a)
            graspError = abs(requested - feasible)
            val vA = (lengthS.velocity / 2f + radiusS.velocity)
            // Two different velocities, deliberately.
            //
            // The **drive** is the finger's own speed. The deformation follows it, and it must
            // not be fed back the size change it is itself producing: radius target depends on
            // the skew target, the skew target on the drive, and a drive that included the radius
            // velocity would close that loop and chatter at the substep rate instead of settling.
            //
            // The **motion** is the derivative of the centre the body actually renders:
            // `fingerVelocity + offset' - D'(A) A'`. At a clamp it is the feasible one-sided
            // tangent against that bound's **own** speed, which is `A'` at the `A` bound,
            // `-A'` at the `barWidth - A` bound and zero at a fixed slot bound. Zero is wrong at
            // a moving bound and would erase a velocity the body genuinely has.
            heldDrive = graspVelocity
            val unconstrained = glassGraspCentreVelocity(
                beta = beta,
                halfWidth = a,
                halfWidthVelocity = vA,
                baseHalfWidth = bar.baseHalfWidth,
                fingerVelocity = graspVelocity,
                offsetVelocity = attachOffsetVelocity,
            )
            cxS.velocity = clampedCentreVelocity(requested, feasible, a, vA, unconstrained)
            cxS.value = feasible
            cxS.target = feasible
        } else {
            if (!motionEnabled) {
                cxS.value = cxS.target
                cxS.velocity = 0f
            } else {
                cxS.step(dt, if (recovering) spec.releaseOmega else omegaC, if (recovering) 1f else zetaC)
            }
            // A finger that stopped and is no longer driving must not keep an old speed alive.
            graspVelocity = 0f
            heldDrive = 0f
            attachOffsetVelocity = 0f
        }

        // Containment, on the envelope this mode is entitled to.
        rebuildConstraints()
        qStar[0] = cxS.value; qStar[1] = cyS.value
        qStar[2] = lengthS.value; qStar[3] = radiusS.value; qStar[4] = skewS.value
        val requestedCx = qStar[0]
        if (mode != GlassSelectorMode.Held) graspError = 0f
        vStar[0] = cxS.velocity; vStar[1] = cyS.velocity
        vStar[2] = lengthS.velocity; vStar[3] = radiusS.velocity; vStar[4] = skewS.velocity

        // A complete `(position, velocity, mode)` snapshot is published only after **both**
        // projections succeed. Publishing an advanced unprojected position with zero velocity -
        // which is what the old failure branch did, despite a comment claiming it retained the
        // last feasible state - renders a body that is outside its own bar.
        // Overflow is checked on **both** sets: the tap envelope is a copy of the rest rows, so a
        // plane that did not fit in `restConstraints` is silently absent from the copy and the
        // copy itself never reports it. A dropped plane lets the body escape exactly where the
        // plane was.
        val positionOk = !injectProjectionFailure &&
            !constraints.overflowed && !restConstraints.overflowed &&
            projector.project(qStar, constraints, projected)
        var published = false
        if (positionOk) {
            val wasSaturated = projector.activeRows > 0
            for (i in 0..4) q[i] = projected[i]
            val velocityOk = !injectProjectionFailure &&
                projector.projectVelocity(vStar, constraints, q, vOut, limits = rowLimit)
            if (velocityOk) {
                saturated = wasSaturated
                // The centre is free on this substep only when nothing touched it: no clamp at
                // the ends, no positional correction and no velocity correction. A shape limit
                // being active - which it almost always is, because the body sits against its own
                // height envelope - does not make the centre constrained, so `saturated` is the
                // wrong predicate for the kinematic gate.
                centreUnconstrained = graspError <= 1e-3f &&
                    abs(q[0] - qStar[0]) <= 1e-3f &&
                    abs(vOut[0] - vStar[0]) <= 1e-3f
                cxS.value = q[0]; cyS.value = q[1]
                lengthS.value = q[2]; radiusS.value = q[3]; skewS.value = q[4]
                graspError = max(graspError, abs(requestedCx - q[0]))
                cxS.velocity = vOut[0]; cyS.velocity = vOut[1]
                lengthS.velocity = vOut[2]; radiusS.velocity = vOut[3]; skewS.velocity = vOut[4]
                for (i in 0..4) lastFeasible[i] = q[i]
                hasLastFeasible = true
                solverFailed = false
                selectorValid = true
                published = true
                rowVelocityExcess = max(rowVelocityExcess, activeRowVelocityExcess(q, vOut))
            }
        }
        if (!published) {
            centreUnconstrained = false
            publishCheckedFallback()
        }

        // Back to rest when there is nothing left to do, from a release **or** from ordinary tap
        // travel. Without the second case a bar that has simply been told which tab is selected
        // never reports itself idle, and the host keeps asking for frames for ever.
        if ((mode == GlassSelectorMode.Release || mode == GlassSelectorMode.TapTravel) &&
            abs(cxS.value - cxS.target) < 0.25f && abs(cxS.velocity) < 1f &&
            formS.value < 0.01f && !releaseActive
        ) {
            mode = GlassSelectorMode.Rest
            releasedFromHold = false
        }
        // The press has outlived the threshold: take the grasp **now**, from the body as it is at
        // this instant and the latest pointer. Acquiring at DOWN used a body up to 120 ms stale.
        if (mode == GlassSelectorMode.PressPending && wantForm) {
            acquireGrasp(fingerX)
            mode = GlassSelectorMode.Held
        }

        var moved = 0f
        moved = max(moved, abs(cxS.value - lastQ[0]))
        moved = max(moved, abs(cyS.value - lastQ[1]))
        moved = max(moved, abs(lengthS.value - lastQ[2]))
        moved = max(moved, abs(radiusS.value - lastQ[3]))
        moved = max(moved, abs(skewS.value - lastQ[4]))
        moved = max(moved, abs(formS.value - lastQ[5]) * 100f)
        lastQ[0] = cxS.value; lastQ[1] = cyS.value; lastQ[2] = lengthS.value
        lastQ[3] = radiusS.value; lastQ[4] = skewS.value; lastQ[5] = formS.value
        quietSteps = if (moved <= QUIET_PX) quietSteps + 1 else 0
    }

    /**
     * The one-sided tangent at the centre clamp, against the bound's **own** speed.
     *
     * The lower bound is `max(firstCentre, A)` and the upper `min(lastCentre, barWidth - A)`.
     * A slot bound is fixed, so its speed is zero; the `A` bound moves at `A'` and the
     * `barWidth - A` bound at `-A'`. At a tie the feasible one-sided rule takes the binding
     * tangent: the largest lower-bound speed, the smallest upper-bound speed.
     */
    private fun clampedCentreVelocity(
        requested: Float,
        feasible: Float,
        halfWidth: Float,
        halfWidthVelocity: Float,
        unconstrained: Float,
    ): Float {
        val lo = max(bar.firstCentre, halfWidth)
        val hi = min(bar.lastCentre, bar.width - halfWidth)
        if (lo > hi) return 0f                                   // wider than the bar: centred
        if (requested < feasible - 1e-4f && feasible <= lo + 1e-4f) {
            var bound = if (halfWidth >= bar.firstCentre - 1e-4f) halfWidthVelocity else 0f
            if (abs(halfWidth - bar.firstCentre) <= 1e-4f) bound = max(halfWidthVelocity, 0f)
            return max(unconstrained, bound)
        }
        if (requested > feasible + 1e-4f && feasible >= hi - 1e-4f) {
            var bound = if (bar.width - halfWidth <= bar.lastCentre + 1e-4f) -halfWidthVelocity else 0f
            if (abs((bar.width - halfWidth) - bar.lastCentre) <= 1e-4f) bound = min(-halfWidthVelocity, 0f)
            return min(unconstrained, bound)
        }
        return unconstrained
    }

    /**
     * The independent oracle for section 4's gate: over every row that is active at [position],
     * how far `A_j·v` exceeds that row's own limit `b_j'(t)`. Computed from the constraint data
     * directly, not from anything the projector reports about itself.
     */
    private fun activeRowVelocityExcess(position: FloatArray, velocity: FloatArray): Float {
        var worst = 0f
        for (row in 0 until constraints.rows) {
            if (constraints.value(row, position) < constraints.b[row] - 0.5f) continue
            var s = 0f
            val i = row * 5
            for (c in 0..4) s += constraints.a[i + c] * velocity[c]
            worst = max(worst, s - rowLimit[row])
        }
        return worst
    }

    /**
     * A checked fallback pair, never an advanced unprojected position plus zero velocity.
     *
     * In order: the frozen release seed (whose feasibility against the **current** rows is proved
     * by convexity and then checked numerically anyway), the last state both projections accepted
     * if it is still feasible now, and finally the nominal rest body. If even that is infeasible
     * the selector is marked invalid so the host can suppress it while keeping the interactive
     * row; that is a reported correctness failure, not a passing degradation.
     */
    private fun publishCheckedFallback() {
        solverFailed = true
        saturated = true
        var chosenQ: FloatArray? = null
        var chosenV: FloatArray? = null

        if (releaseActive && seedValid) {
            seedInto(max(now - releaseStart, 0f), qStar, vStar)
            if (glassBodyFeasible(constraints, qStar, tolerance = 0.05f) &&
                activeRowVelocityExcess(qStar, vStar) <= 0.5f
            ) {
                chosenQ = qStar
                chosenV = vStar
            }
        }
        if (chosenQ == null && hasLastFeasible &&
            glassBodyFeasible(constraints, lastFeasible, tolerance = 0.05f)
        ) {
            chosenQ = lastFeasible
            for (i in 0..4) vOut[i] = 0f
            chosenV = vOut
        }
        if (chosenQ == null) {
            q[0] = bar.centreOf(restIndex)
            q[1] = bar.centreY
            q[2] = max(2f * bar.baseHalfWidth - 2f * bar.baseHalfHeight, spec.minLengthDp * bar.density)
            q[3] = bar.baseHalfHeight
            q[4] = 0f
            for (i in 0..4) vOut[i] = 0f
            if (glassBodyFeasible(constraints, q, tolerance = 0.05f) ||
                glassBodyFeasible(restConstraints, q, tolerance = 0.05f)
            ) {
                chosenQ = q
                chosenV = vOut
            }
        }
        if (chosenQ == null || chosenV == null) {
            // Nothing this layout can admit. Keep the previous numbers - never advance them -
            // and tell the host the selector is not drawable.
            selectorValid = false
            cxS.velocity = 0f; cyS.velocity = 0f
            lengthS.velocity = 0f; radiusS.velocity = 0f; skewS.velocity = 0f
            if (hasLastFeasible) {
                cxS.value = lastFeasible[0]; cyS.value = lastFeasible[1]
                lengthS.value = lastFeasible[2]; radiusS.value = lastFeasible[3]
                skewS.value = lastFeasible[4]
            }
            return
        }
        selectorValid = true
        cxS.value = chosenQ[0]; cyS.value = chosenQ[1]
        lengthS.value = chosenQ[2]; radiusS.value = chosenQ[3]; skewS.value = chosenQ[4]
        cxS.velocity = chosenV[0]; cyS.velocity = chosenV[1]
        lengthS.velocity = chosenV[2]; radiusS.velocity = chosenV[3]; skewS.velocity = chosenV[4]
        cxS.target = cxS.value.takeIf { mode == GlassSelectorMode.Held } ?: cxS.target
    }

    /**
     * Section 7.2: the admitted icon/label union sets a half-width demand of its own. Candidates
     * come from a **fixed base aperture**, never from the already expanded body, so growth cannot
     * recruit a third item; at most one neighbour joins the item the grasp is on.
     */
    private fun admittedHalfWidth(heldHalfWidth: Float, capRadius: Float): Float {
        if (itemCentre.isEmpty()) return heldHalfWidth
        val g = graspX
        val d = bar.density
        var nearest = 0
        var nearestDistance = Float.MAX_VALUE
        for (i in itemCentre.indices) {
            val dist = abs(itemCentre[i] - g)
            if (dist < nearestDistance) { nearestDistance = dist; nearest = i }
        }
        admitted = nearest
        val aperture = bar.baseHalfWidth
        val admitInside = aperture - spec.admitInsetDp * d
        val releaseOutside = aperture + spec.admitReleaseDp * d
        var neighbour = -1
        var neighbourDistance = Float.MAX_VALUE
        for (i in itemCentre.indices) {
            if (i == nearest) continue
            if (abs(i - nearest) != 1) continue
            val dist = abs(itemCentre[i] - g)
            val keep = if (admittedNeighbour == i) dist <= releaseOutside else dist <= admitInside
            if (keep && dist < neighbourDistance) { neighbourDistance = dist; neighbour = i }
        }
        admittedNeighbour = neighbour
        val pad = spec.admitPaddingDp * d
        var bLeft = itemLeft[nearest] - pad
        var bRight = itemRight[nearest] + pad
        if (neighbour >= 0) {
            bLeft = min(bLeft, itemLeft[neighbour] - pad)
            bRight = max(bRight, itemRight[neighbour] + pad)
        }
        // The demand is derived from the **same capped law** the position uses. With the body's
        // half width `A` and `D(A) = clamp(beta*A, -B, B)`, the left edge reaches `g - (A + D(A))`
        // and the right edge `g + (A - D(A))`, so the two requirements are
        //   A + D(A) >= g - paddedLeft     and     A - D(A) >= paddedRight - g.
        // Both left sides are strictly increasing in A - slope at least 0.2 below the cap, exactly
        // 1 above it - so each has one exact solution and neither needs a fit or a search.
        // Using the uncapped beta here while the position uses the bounded grasp is what left a
        // long label uncovered on one side.
        // The ends are round, so horizontal span alone does not prove the label fits. At the ink's
        // own vertical half extent `h` the body's horizontal reach is short of `A` by exactly
        // `cap - sqrt(cap^2 - h^2)`, where `cap` is the smaller of the two end radii; add that to
        // each side's demand and the four corners of the padded rectangle are contained, which
        // contains the rectangle. `h >= cap` cannot be accommodated at all and is reported.
        val h = min(inkHalfHeight, capRadius)
        val deficit = capRadius - sqrt(max(capRadius * capRadius - h * h, 0f))
        val b = bar.baseHalfWidth
        val aCap = if (abs(beta) > 1e-6f) b / abs(beta) else Float.MAX_VALUE
        val capOffset = if (beta >= 0f) b else -b
        val required = max(
            max(heldHalfWidth, minHalfWidthForReach(g - bLeft + deficit, 1f + beta, capOffset, aCap)),
            minHalfWidthForReach(bRight - g + deficit, 1f - beta, -capOffset, aCap),
        )
        accommodationDemand = required
        // The raw padded rectangle is a preference, not a licence for unlimited enlargement.
        // Apple IMG_6721 still refracts the label at the rim. Leave an over-budget demand
        // observable; it must neither shrink the ink nor expand the selector to three items.
        return min(required, widthLimit / 2f)
    }

    /**
     * The smallest `A` with `slope*A >= demand` below the cap, or `A + capOffset >= demand` above
     * it. Exact on both linear branches; the two agree at `A = aCap` by construction.
     */
    private fun minHalfWidthForReach(demand: Float, slope: Float, capOffset: Float, aCap: Float): Float {
        if (demand <= 0f) return 0f
        if (slope > 1e-4f) {
            val a = demand / slope
            if (a <= aCap) return a
        }
        return max(demand - capOffset, aCap)
    }

    /**
     * The half width accommodation asked for on the last substep, before the ceiling and the
     * containment projection. The host and the tests compare it with the body that was actually
     * produced rather than assuming the demand was met: an accommodation the envelope cannot give
     * is reported as constrained, never hidden by shrinking the text or growing the bar.
     */
    var accommodationDemand: Float = 0f
        private set

    /**
     * The inscribed polygons, rebuilt only when they actually change.
     *
     * A capsule bar needs about a hundred planes and each one costs a cosine and a sine. Rebuilding
     * both sets on every substep - four times a frame, twice over - was the single largest cost the
     * controller added, and neither set changes unless the bar's layout does or the held envelope
     * grows. The key is the envelope height, quantised: a quarter of a pixel of growth cannot move
     * a plane by more than the polygon's own chord error.
     */
    private var constraintsKey: Float = Float.NaN
    private var restKey: Float = Float.NaN
    private var restRows: Int = 0

    /** The frozen outer envelope the current release interval is contained by. */
    private var releaseHalfHeight = 0f

    private fun ensureRowLimitCapacity() {
        if (rowLimit.size < constraints.a.size / 5) rowLimit = FloatArray(constraints.a.size / 5)
    }

    private fun rebuildConstraints() {
        val d = bar.density
        val heldNow = formS.value.coerceIn(0f, 1f)
        val tapEnvelope = mode != GlassSelectorMode.Held && !releaseActive
        val restHalfHeight = bar.allowedHalfHeight
        ensureRowLimitCapacity()
        if (restKey != restHalfHeight) {
            restConstraints.clear()
            restConstraints.addInscribedRoundRect(
                centreX = bar.width / 2f,
                centreY = bar.centreY,
                halfWidth = bar.width / 2f,
                halfHeight = restHalfHeight,
                cornerRadius = min(bar.cornerRadius, restHalfHeight),
            )
            restConstraints.addShapeLimits(
                minRadius = spec.minRadiusDp * d,
                minLength = spec.minLengthDp * d,
                maxWidth = widthLimit,
                maxHalfHeight = restHalfHeight,
            )
            restKey = restHalfHeight
            restRows = restConstraints.rows
        }
        if (tapEnvelope && !releaseActive) {
            if (constraintsKey == restHalfHeight && constraints.rows == restRows) return
            // An ordinary tap gets the bar itself and nothing more, whatever its speed.
            constraints.clear()
            constraintsKey = restHalfHeight
            releaseRowStart = -1
            for (row in 0 until restConstraints.rows) {
                val i = row * 5
                constraints.add(
                    restConstraints.a[i], restConstraints.a[i + 1], restConstraints.a[i + 2],
                    restConstraints.a[i + 3], restConstraints.a[i + 4], restConstraints.b[row],
                )
            }
            ensureRowLimitCapacity()
            for (row in 0 until constraints.rows) rowLimit[row] = 0f
            return
        }
        if (releaseActive) {
            // A release is contained by a **frozen outer held envelope** - fixed for the whole
            // interval, so its rows genuinely have zero velocity - plus the fixed rest rows with
            // their time-varying allowances. The trigonometric polygon is built once here; only
            // the allowances and their derivatives change per substep.
            if (releaseEnvelopeKey.isNaN() || releaseRowStart < 0) {
                releaseHalfHeight = max(
                    bar.allowedHalfHeight + (bar.heldHalfHeight - bar.allowedHalfHeight) * heldNow,
                    bar.allowedHalfHeight,
                )
                constraints.clear()
                constraints.addInscribedRoundRect(
                    centreX = bar.width / 2f,
                    centreY = bar.centreY,
                    halfWidth = bar.width / 2f,
                    halfHeight = releaseHalfHeight,
                    cornerRadius = min(
                        bar.cornerRadius + (releaseHalfHeight - bar.allowedHalfHeight),
                        releaseHalfHeight,
                    ),
                )
                constraints.addShapeLimits(
                    minRadius = spec.minRadiusDp * d,
                    minLength = spec.minLengthDp * d,
                    maxWidth = widthLimit,
                    maxHalfHeight = releaseHalfHeight,
                )
                releaseRowStart = constraints.rows
                for (row in 0 until min(restConstraints.rows, releaseExcess.size)) {
                    val i = row * 5
                    constraints.add(
                        restConstraints.a[i], restConstraints.a[i + 1], restConstraints.a[i + 2],
                        restConstraints.a[i + 3], restConstraints.a[i + 4], restConstraints.b[row],
                    )
                }
                releaseEnvelopeKey = releaseHalfHeight
                constraintsKey = Float.NaN
                ensureRowLimitCapacity()
                for (row in 0 until releaseRowStart) rowLimit[row] = 0f
            }
            applyReleaseAllowance()
            return
        }
        // A hold gets the larger envelope, growing with the formation.
        val heldHalfHeight = bar.allowedHalfHeight + (bar.heldHalfHeight - bar.allowedHalfHeight) * heldNow
        val key = -(kotlin.math.round(heldHalfHeight * 4f) / 4f)
        if (key == constraintsKey) return
        constraints.clear()
        constraintsKey = key
        releaseRowStart = -1
        constraints.addInscribedRoundRect(
            centreX = bar.width / 2f,
            centreY = bar.centreY,
            halfWidth = bar.width / 2f,
            halfHeight = heldHalfHeight,
            cornerRadius = min(bar.cornerRadius + (heldHalfHeight - bar.allowedHalfHeight), heldHalfHeight),
        )
        constraints.addShapeLimits(
            minRadius = spec.minRadiusDp * d,
            minLength = spec.minLengthDp * d,
            maxWidth = widthLimit,
            maxHalfHeight = heldHalfHeight,
        )
        ensureRowLimitCapacity()
        for (row in 0 until constraints.rows) rowLimit[row] = 0f
    }

    /**
     * Section 7.3: on release a protruding body is not required to fit the rest envelope at once.
     * Each rest plane keeps an allowance that starts at the excess it already had, continues the
     * outward speed it already had, and decays; nothing gains a new outward speed it did not have.
     *
     * The allowance's **derivative** is the row's own velocity limit. Requiring `A_j·v <= 0` on a
     * shrinking wall is infeasible - the wall is moving in faster than a stationary body can
     * follow - so the velocity projection is given `a_j'(t)`, not zero. Position, allowance and
     * derivative are all evaluated at the same simulation timestamp.
     */
    private fun applyReleaseAllowance() {
        if (releaseRowStart < 0) return
        val t = max(now - releaseStart, 0f)
        val w = spec.releaseOmega
        val e = exp(-w * t)
        var anyLeft = false
        val count = min(min(restConstraints.rows, releaseExcess.size), constraints.rows - releaseRowStart)
        for (row in 0 until count) {
            val d0 = releaseExcess[row]
            val u0 = releaseSpeed[row]
            // Every rest plane is enforced from the first frame of the release, with an allowance
            // that is simply zero where the body already fitted. Enforcing only the violated ones
            // and switching to the full set when the allowance expires would put the body outside
            // planes it was never held to, and the projection would then snap it back in one frame.
            val still = d0 > 0f || u0 > 0f
            val allowance = if (still) (d0 + (u0 + w * d0) * t) * e else 0f
            val slope = if (still) (u0 - w * (u0 + w * d0) * t) * e else 0f
            if (allowance > 0.25f) anyLeft = true
            val target = releaseRowStart + row
            constraints.setRhs(target, restConstraints.b[row] + allowance)
            rowLimit[target] = slope
        }
        if (!anyLeft) {
            // Transitioning to the rest set is only legal once it is actually feasible; until
            // then the allowance - not a rename - is what holds the body.
            q[0] = cxS.value; q[1] = cyS.value; q[2] = lengthS.value
            q[3] = radiusS.value; q[4] = skewS.value
            if (glassBodyFeasible(restConstraints, q, tolerance = 0.25f)) {
                releaseActive = false
                releaseRowStart = -1
                releaseEnvelopeKey = Float.NaN
                constraintsKey = Float.NaN
                seedValid = false
            }
        }
    }

    /**
     * Retain the previous conservative recording extent. This is allocation, NOT permission
     * for the visible body to grow: [widthLimit] alone governs the contour. Changing the
     * recording origin as part of a motion repair also changes resampling phase in the
     * material/ink chain, so that separate optimisation needs its own optical verification.
     */
    fun maxBodyWidth(): Float {
        if (!this::bar.isInitialized) return 0f
        var widest = 2f * bar.heldHalfWidth
        for (i in itemCentre.indices) {
            val pad = spec.admitPaddingDp * bar.density
            var left = itemLeft[i] - pad
            var right = itemRight[i] + pad
            if (i > 0) left = min(left, itemLeft[i - 1] - pad)
            if (i < itemCentre.size - 1) right = max(right, itemRight[i + 1] + pad)
            val reach = max(itemCentre[i] - left, right - itemCentre[i])
            widest = max(widest, 2f * (reach + bar.baseHalfWidth))
        }
        val allocationCeiling = 2f * min(2f * bar.heldHalfWidth,
            max(spec.maxWidthSlots, 2.4f) * bar.slotWidth / 2f + bar.slotWidth)
        return min(bar.width, min(widest, allocationCeiling) + spec.maxExtraLength * bar.slotWidth)
    }

    /**
     * How far the published body violates the envelope it is **currently entitled to**, in px.
     *
     * [protrusion] measures against the rest bar, which a held or releasing body is legitimately
     * outside; this measures against the applicable constraint set, which nothing may ever be
     * outside. Zero is the only passing value.
     */
    internal fun worstConstraintExcess(): Float {
        q[0] = cxS.value; q[1] = cyS.value; q[2] = lengthS.value
        q[3] = radiusS.value; q[4] = skewS.value
        return constraints.worstExcess(q)
    }

    /** The declared protrusion of the current body past the rest bar, in px; 0 when contained. */
    fun protrusion(): Float = glassBodyProtrusion(
        body = body,
        centreX = bar.width / 2f,
        centreY = bar.centreY,
        halfWidth = bar.width / 2f,
        halfHeight = bar.allowedHalfHeight,
        cornerRadius = min(bar.cornerRadius, bar.allowedHalfHeight),
    )

    /** For diagnostics: the drive that section 6 predicts at the current speed, in px. */
    fun extraLengthDrive(): Float {
        val s = bar.slotWidth
        val v = if (mode == GlassSelectorMode.Held) graspVelocity else cxS.velocity
        return spec.maxExtraLength * s * tanh(abs(v) / max(s * spec.centreOmega, 1e-3f))
    }

    @Suppress("unused")
    fun signOfSkewDrive(): Float = sign(skewS.target)

    private companion object {
        /** Movement under this, for [QUIET_STEPS] substeps running, counts as settled. */
        const val QUIET_PX = 0.02f
        const val QUIET_STEPS = 8
    }
}
