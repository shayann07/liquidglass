package com.wexpa.liquidglass

import androidx.compose.runtime.Immutable
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * The authored preset for the pose selector.
 *
 * **Every number here is a design constant.** None is a recovered Apple time constant, spring
 * frequency or damping ratio. What the recording `JGEY2190.MP4` does establish, frame by frame,
 * is written next to the constant it informed; the rest is authored inside the ranges the
 * iOS-parity brief's section 8.3 declares. See `research/analysis/v3/ios-parity/run2/MODEL.md`.
 *
 * Frequencies are per second, damping dimensionless, lengths in design units (slots or the
 * reference body's own dimensions) unless a name says px.
 */
@Immutable
internal data class GlassPoseSpec(
    /**
     * Centre response. One law for every distance: a longer tap simply develops more speed.
     *
     * Authored critical response, retained for stable navigation. JGEY2190 n176–182 was
     * previously cited as a tap fit, but its complete episode continues through Contacts to
     * Calls with a raised lens. It does not identify a single-target tap response.
     */
    val centreOmega: Float = 40f,
    val centreZeta: Float = 1f,
    /**
     * The spine: elongation along the motion. Fast, so it tracks the speed that drives it; the
     * pill in the recording is longest about 25 ms after the centre is fastest and has contracted
     * again two frames after the centre arrives.
     */
    val spineOmega: Float = 90f,
    val spineZeta: Float = 1f,
    /**
     * Resting-tap shape recovery, separate from held motion. The owner's 6690–6701 sequence
     * requires more deformation for longer travel and recovery on arrival, within the bar.
     * These are authored spring parameters: those untimed stills cannot identify a frequency.
     * Keep the centre critical; the shape can compress and recover without bodily overshoot.
     */
    val tapSpineOmega: Float = 36f,
    val tapSpineZeta: Float = 0.43f,
    val tapElongationSlots: Float = 0.36f,
    val tapReferenceSpeed: Float = 15f,
    /** Recover existing elongation gently, without exciting it from return velocity. */
    val spineReleaseOmega: Float = 32f,
    /** Strain follows an anchored off-axis pull. Taper remains zero. */
    val shapeOmega: Float = 32f,
    val shapeZeta: Float = 0.72f,
    /** Pressure and accommodation. Held growth takes ~130 ms in the recording (g7 n575–582). */
    val pressureOmega: Float = 45f,
    val pressureZeta: Float = 1f,
    /**
     * How fast the pressure subsides once the finger is gone. The recording's released lens
     * shrinks steadily over ~250 ms (g14 n1333–1348); at 30/s the body was back inside the bar
     * 85 ms after UP on the phone, because the protrusion is only the excess over the bar and
     * needs a 40 % fall of the pressure to vanish. Held squeeze still uses [pressureOmega].
     */
    val pressureFallOmega: Float = 14f,
    /**
     * Formation: how far the material has become the held lens. It rises fast — the resting pill
     * shows a lens rim in the very next frame after a press (n2045) — and falls slowly: the
     * released lens takes about 250 ms to become the dark pill again (g14 n1333–1348).
     */
    val formRiseOmega: Float = 60f,
    val formOmega: Float = 18f,
    val formZeta: Float = 1f,
    /** How long an eligible press must last before it becomes a hold. */
    val holdThresholdSeconds: Float = 0.120f,

    /**
     * Reference speed, in slots per second, for the smooth speed term `sv = v² / (V² + v²)`.
     *
     * Authored to the recording's separation: the catalogue's held widths barely change below
     * about 800 px/s (three slots/s on that bar) and the one-slot tap peaks near 15 slots/s where
     * the pill has to reach its full elongation. 11.5 puts an ordinary 1.5 s traverse at
     * `sv ≈ 0.05` and the tap peak at `sv ≈ 0.62`.
     */
    val referenceSpeed: Float = 11.5f,
    /** Authored held-input scale. R10 reduces sensitivity after the owner's r9.1 rejection. */
    val heldReferenceSpeed: Float = 20f,
    /** Time-domain filtering rejects packet jitter without delaying the grasp or selection. */
    val velocityTau: Float = 0.035f,
    /** Reference acceleration, slots per second squared, for the taper term. */
    val referenceAcceleration: Float = 26f,

    /**
     * Maximum added half-spine during held motion, in slots. Authored .50 replaces .85:
     * the owner found even extreme held drags too elastic. The old tap-based justification
     * cannot identify this held-input gain; resting taps have their own parameters above.
     */
    val elongationSlots: Float = 0.50f,
    /**
     * How much of the elongation a pull *across* the bar may keep, as a fraction. No reference
     * shows a vertical pull at all (gesture 60's "rise" is a page transition), so the 2D response
     * is authored by symmetry and bounded by the vertical envelope below rather than by evidence.
     */
    val acrossElongation: Float = 0f,

    /** Trace-free strain gain from *speed*. Zero: the recording's pills stretch at constant height. */
    val stretchGain: Float = 0f,
    val maxStrain: Float = 0.42f,
    /**
     * The stretch of a held body pulled **off** the bar, per dp of its centre's displacement from
     * the bar's centre line: trace-free strain along the pull (length x e^s, width x e^-s).
     *
     * From the owner's recording of pulled glass (`reference-6756`, iOS 26 Phone app): the search
     * button and every keypad key stretch **linearly with displacement and not at all with
     * speed** — a 5 s pull, a 1.2 s pull and a 7 s creep reach the same extreme at the same
     * displacement (S01/S04/S08; K01–K16) — as a symmetric ellipse (width profile
     * 0.61/0.87/1/0.87/0.61 at 10/25/50/75/90 % of the length) whose width thins hard. At a 37 pt
     * displacement the length is x1.27 and the width x0.69 of the held size (S01 n440), keys
     * x1.20 / x0.78 at 30 pt: (ln 1.27 + ln(1/0.69)) / 2 / 37 ≈ 0.0075 per pt of trace-free
     * strain and (ln 1.27 − ln(1/0.69)) / 2 / 37 ≈ −0.0015 per pt of area (pressure). The tab
     * bubble itself was never pulled off its bar in any recording; along the bar it travels
     * without stretching (T04), which the spine law already does at ordinary speeds. Applying
     * the free glass's law to the bubble's off-axis pull was an inference, not a tab measurement.
     * The owner rejected its strength. R10 uses a smaller authored gain and limit for this
     * anchored bubble; it does not change the independently measured free-button interaction.
     */
    val pullStrainPerDp: Float = 0.0038f,
    /** The matching area change per dp of off-axis pull, as log pressure (see above). */
    val pullPressurePerDp: Float = -0.0007f,
    /** Soft onset and saturation of inferred off-bar strain; no angular or distance dead zone. */
    val pullOnsetDp: Float = 12f,
    val pullMaxStrain: Float = 0.12f,
    /**
     * How much of the finger's off-axis travel the **centre** follows, and the most it may
     * follow, as a fraction of the bar's height. The owner, on the phone against the iPhone:
     * a swipe up or at an angle must not carry the bubble off the bar — it stays where it is
     * and squeezes toward the angle. So the body is anchored on its bar: the centre gives a
     * little (the free glass of reference-6756 has a lagging tail, not a fixed one) and the
     * shape does the reaching.
     */
    val pullFollow: Float = 0.2f,
    val pullFollowCapRatio: Float = 0.10f,
    /** Authored end-anchor compliance. Full-screen r11 drags otherwise lose half the lens.
     * This bounds visual travel, not selection intent or the free held contour. */
    val endTravelCapRatio: Float = 0.08f,
    /**
     * The tilt of the squeeze toward the swipe's angle: the along-bar speed times this many
     * seconds is the along-bar part of the pull vector, so a diagonal swipe stretches the body
     * diagonally and a finger that stops leaves it stretched straight off the bar.
     */
    val pullTiltSeconds: Float = 0.08f,
    /**
     * Calm held pressure, as a log expansion. See [poseHeldPressure].
     *
     * The inherited 1.28 still ratio used the inset selector as its bar and is rejected. The
     * recording's Phone bar reads (185.5 + 17 + 29) / 185.5 = 1.25 for the g7 hold. The r10
     * optics audit re-read every reference at matched scale: 6717 1.12, LOLL8185 n1380 1.22,
     * 6756 T01 1.21, T04 1.23, against 1.30-1.37 on the phone. 1.20 sits in that band.
     */
    val heldHeightRatio: Float = 1.20f,
    /**
     * Maximum log contraction under a held drag. Original 6735 reads about .79 bar heights
     * (untimed, so no identifiable finger speed). With calm height 1.20, the inherited .55
     * allowed 1.20*exp(-.55)=.69 bar heights. Authored .40 gives an asymptote of .80: hard
     * pulls can still flatten below rest without the rejected collapse. Taps are not squeezed.
     */
    val squeeze: Float = 0.40f,
    /** Taper gains. Zero: the transit pill's ends read symmetric in every inspected frame. */
    val taperFromAcceleration: Float = 0f,
    val taperFromVelocity: Float = 0f,
    /** Invertibility guard: `length(k) <= maxTaper`, checked every substep. */
    val maxTaper: Float = 0f,
    /**
     * How far movement alone forms the material, for a tap that never becomes a hold. Small:
     * the transit pill stays dark inside with a rim (n183, n2050), it does not become the lens.
     */
    val formFromMovement: Float = 0.25f,
    /** The immediate formation of a press that has not yet become a hold: the rim of n2045. */
    val pressFormation: Float = 0.3f,

    /**
     * Accommodation: added half-spine, in px, is searched within this multiple of the slot.
     *
     * Read together with the pressure above: at 1.26 bar heights the calm held body is already
     * about 1.5x its resting width before a pixel of spine is added, and 0.20 puts a hold between
     * two items at about 1.9 slots, between the brief's 6721 (≈1.73) and the ledger's 6717 fit.
     */
    val maxAccommodationSlots: Float = 0.20f,
    /**
     * The finger speed, in slots per second, above which accommodation has faded to nothing.
     * Below it the demand scales with `1 - speed / fade`: an ordinary 1.5 s traverse (about 3.4
     * slots/s on Vitals) gets none, a finger creeping between two items still gets it.
     */
    val accommodationFadeSpeed: Float = 2.5f,
    /**
     * Accommodation needs a real stop. The owner, holding and swiping on the phone: the width
     * "keeps fluctuating" — the fit-to-item expansion faded in and out with every change of the
     * finger's speed. Now it engages only after the finger has been slower than
     * [accommodationEngageSpeed] (slots per second) for [accommodationDwellSeconds], lets go only
     * above [accommodationReleaseSpeed], and moves through its own gentler spring
     * ([accommodationOmega], critical) either way.
     */
    val accommodationEngageSpeed: Float = 0.35f,
    val accommodationReleaseSpeed: Float = 0.8f,
    val accommodationDwellSeconds: Float = 0.15f,
    val accommodationOmega: Float = 16f,
    /** Padding added beyond the admitted icon/label union, in dp. */
    val admitPaddingDp: Float = 6f,
    /** Admission hysteresis, in dp. */
    val admitInsetDp: Float = 2f,
    val admitReleaseDp: Float = 4f,

    /**
     * One vertical envelope for a held body, as a fraction of the bar's height: growth past the
     * bar, the centre's follow of the finger and the spine's reach across the bar all share it.
     *
     * The grasp constraint is solved in both axes (`c = g - e^p o`), which is what makes the
     * body sit where the finger is — the recording's g7 hold with a low finger protrudes 17 px
     * above and 29 px below its bar. What the envelope bounds is how far a finger dragged *off*
     * the bar can carry the body; and it is the node's allocation, so nothing here can be
     * clipped by its own layer. 0.30 was authored with no reference; `reference-6756` shows a
     * pulled free glass body's head following the finger 48 pt beyond its held edge with no
     * limit in sight (S01 n440, the screen was the limit); the bubble itself stays anchored on
     * its bar (the owner's call against the iPhone, [pullFollow]) and only its stretched head
     * reaches out, so 0.50 of the bar covers the capped follow plus a fully stretched body
     * ([maxStrain]) with room for the springs.
     */
    val heldExcursionRatio: Float = 0.50f,
    /** Calm growth is about the touched material point; off-axis travel is separately anchored. */
    val graspFollowAcross: Float = 1f,

    /** Grasp tracking. Fast and critical: this is a response, not a smoothing filter. */
    val graspOmega: Float = 78f,
    /** Acceleration filter time constant, in seconds. Filtered in time, never in event count. */
    val accelerationTau: Float = 0.045f,
    /** A finger with no sample for this long has stopped driving. */
    val stopWindowSeconds: Float = 0.040f,
    /** The corrected navigation model ignores perpendicular input for selector shape. */
    val travelOnly: Boolean = false,
    /** Keep the released capsule responsive to travel while press formation subsides. */
    val releaseTravel: Boolean = false,
) {
    companion object {
        /** Authored response constrained by IMG_6756 T04 and the 6690–6701 transit stills.
         * Off-axis free-button strain was never measured on a selector; keep it on the bar.
         * The held height stays below T04's approximately 1.23-bar-height sequence envelope.
         * Rates are deliberately not described as fitted Apple timing. */
        val Calm = GlassPoseSpec(
            centreOmega = 24f, spineOmega = 30f, spineZeta = 0.78f,
            tapSpineOmega = 26f, tapSpineZeta = 0.72f, tapElongationSlots = 0.55f,
            tapReferenceSpeed = 10f, spineReleaseOmega = 22f,
            pressureOmega = 22f, pressureFallOmega = 14f, formRiseOmega = 30f,
            heldHeightRatio = 1.20f, heldReferenceSpeed = 12f,
            elongationSlots = 0.45f, squeeze = 0.10f,
            pullStrainPerDp = 0f, pullPressurePerDp = 0f, pullFollow = 0f,
            endTravelCapRatio = 0.025f, graspOmega = 40f,
            maxAccommodationSlots = 0.15f,
            travelOnly = true,
            releaseTravel = true,
        )
    }
}

/**
 * Calm held pressure from a measured height ratio, section 8.3.
 *
 * The ratio is against the **bar's** height, and the reference body is not the bar, so the log
 * has to be taken on the ratio of the two actual heights. `log(1.13)` alone would only be right
 * if the resting body were exactly as tall as the bar, which it is not.
 */
internal fun poseHeldPressure(heldHeightRatio: Float, barHeight: Float, referenceHeight: Float): Float {
    if (referenceHeight <= 1e-4f || barHeight <= 1e-4f) return 0f
    return kotlin.math.ln((heldHeightRatio * barHeight) / referenceHeight)
}

/** Which of the controller's states owns the body right now. */
internal enum class GlassPoseMode { Rest, TapTransit, PressPending, Held, Released }

/**
 * The pose selector controller: one persistent body, its pose states, and the rules that decide
 * what it is doing.
 *
 * It owns no Compose state and no pointer plumbing. The adapter feeds it timestamped pointer
 * facts in the bar's frame and reads [frame] back each displayed frame.
 *
 * ## The body
 *
 * A capsule whose **spine is a vector**: the rendered body is the hull of two discs of radius
 * `r e^p` at `c ± L(cos θ, sin θ)`. Motion lengthens the spine along the motion and the caps
 * keep their curvature — this is what the recording's tap-transit pill does (it spans origin and
 * destination at the same height, then contracts from the back), and what the previous
 * trace-free strain could not do (it had to lower the height to widen the body, and could reach
 * +17 % at most). Pressure `p` scales the whole body isotropically and is what a hold does;
 * a strong held pull subtracts from it, which is 6735's flattening.
 *
 * - **Held mode has no resting-bar contact at all.** A held body may protrude. It is not
 *   *required* to: 6717 and 6721 are taller than the bar while the hard-pulled 6735 is shorter.
 * - **Movement in any direction** orients the spine; there is no privileged axis, and the
 *   double-angle representation means no orientation flip at a reversal.
 * - **Accommodation, pressure and elongation are separate**, and are reported separately, so a
 *   total width is never clamped as a proxy for controlling one of them.
 */
internal class GlassPoseController(
    var spec: GlassPoseSpec = GlassPoseSpec(),
) {
    // ------------------------------------------------------------------ pose state

    private val pose = GlassPose()
    private val velocity = GlassPose()
    private val equilibrium = GlassPose()

    /** The live pose map, rebuilt once per substep and shared by everything that reads geometry. */
    val frame = GlassPoseFrame()

    private var reference = GlassReference(1f, 1f)
    private lateinit var bar: GlassSelectorBar

    var mode: GlassPoseMode = GlassPoseMode.Rest
        private set
    var motionEnabled: Boolean = true
    var started: Boolean = false
        private set

    /** 0 the flat resting inset, 1 the fully formed held material. */
    private val form = GlassSpring()
    val formation: Float get() = form.value.coerceIn(0f, 1f)

    // ------------------------------------------------------------------- the clock

    /** Seconds since the controller's own origin. Only differences matter. */
    private var now: Float = 0f
    private var pressStart: Float = 0f
    /** Where the finger went down: the grasp a hold takes is of the material touched there. */
    private var downX: Float = 0f
    private var downY: Float = 0f
    /** Whether the DOWN landed on the body as it was then, before any press growth. */
    private var downOnBody: Boolean = false
    private var pressEligible: Boolean = false
    private var pointerDownActive: Boolean = false
    private var lastSampleAt: Float = 0f

    // -------------------------------------------------------------------- the grasp

    /**
     * The material point the pointer owns, as an offset from `c` in rendered px **at unit
     * pressure**: its rendered position is `c + e^p o`. Stationary growth therefore scales the
     * body around the finger and the touched point does not slide (the section 13 gate). Set at
     * real acquisition, never on a pending DOWN.
     */
    private var graspQx: Float = 0f
    private var graspQy: Float = 0f
    private var hasGrasp: Boolean = false
    /** The finger's y when the grasp was taken: the off-axis pull is measured from here. */
    private var graspY0: Float = 0f
    /** Accommodation dwell: how long the finger has been slow, and whether it has engaged. */
    private var slowFor: Float = 0f
    private var accommodating: Boolean = false
    /** Whether the current release came from a hold (a body outside the bar) or a mere press. */
    private var releasedFromHold: Boolean = false
    /** The rendered grasp position and its velocity: one fast response, not a chain of filters. */
    private val graspX = GlassSpring()
    private val graspY = GlassSpring()
    /** The raw pointer in the bar frame, and its measured velocity on the event clock. */
    private var pointerX: Float = 0f
    private var pointerY: Float = 0f
    private var pointerVx: Float = 0f
    private var pointerVy: Float = 0f
    private var lastPointerX: Float = 0f
    private var lastPointerY: Float = 0f
    private var lastPointerTime: Double = 0.0
    private var hasPointerTime: Boolean = false
    private var pointerClockOrigin: Double = 0.0
    private var pointerSimulationOrigin: Float = 0f
    /** Filtered acceleration, in px/s^2, filtered in **seconds** and not in event count. */
    private var accelX: Float = 0f
    private var accelY: Float = 0f
    private var lastVx: Float = 0f
    private var lastVy: Float = 0f

    // -------------------------------------------------------------------- selection

    /** Navigation intent, kept separate from the visible geometry as section 5 requires. */
    var restIndex: Int = 0
    /**
     * The index the host asked for, unclamped. A selection can arrive before the bar that holds
     * it (a tab added and selected in one update): clamped against the old count it would rest on
     * the wrong tab once the new bar attaches, so the rebase clamps this against the new one.
     */
    private var requestedIndex: Int = 0
        private set
    var interactionId: Int = 0
        private set

    // ----------------------------------------------------------------- accommodation

    private var itemLeft: FloatArray = FloatArray(0)
    private var itemRight: FloatArray = FloatArray(0)
    private var itemCentre: FloatArray = FloatArray(0)
    private var inkHalfHeight: Float = 0f
    private var admitted: Int = -1
    private var admittedNeighbour: Int = -1

    /** Reported separately, never folded into one number: section 9. */
    var accommodationDemand: Float = 0f
        private set
    var pressureLog: Float = 0f
        private set
    /**
     * The elongation, dimensionless: how much the rendered half-spine exceeds the resting body's
     * own `a`, over the resting body's half width. Zero for a calm hold, however large it has
     * grown; positive in transit or under a moving finger.
     */
    var strainMagnitude: Float = 0f
        private set
    /** The same elongation in px, for the tests that compare against slots. */
    var elongationPx: Float = 0f
        private set
    /**
     * The end asymmetry along the travel axis, **in px**: the taper scaled by the body's own
     * reach, so it is the same kind of quantity the two-disk body reported as a radius
     * difference and one test can gate both paths.
     */
    val taperX: Float get() = pose.kx * frame.maxAbsZ

    // --------------------------------------------------------------------- contact

    /** Contour samples, allocated once. */
    private val contour = FloatArray(CONTOUR_SAMPLES * 2)
    /** True when the last contact solve could not find a feasible state. Disclosed, never hidden. */
    var contactFailures: Int = 0
        private set
    var solverFailed: Boolean = false
        private set

    private val scratchZ = FloatArray(2)
    private val scratch = FloatArray(2)
    private val query = GlassPoseQuery()

    // ------------------------------------------------------------------- lifecycle

    fun attach(
        bar: GlassSelectorBar,
        itemBounds: FloatArray?,
        itemCentres: FloatArray?,
        inkHalfHeight: Float = this.inkHalfHeight,
    ) {
        val first = !this::bar.isInitialized
        val relayout = first || !this.bar.sameFrameAs(bar)
        this.bar = bar
        if (this.inkHalfHeight != max(inkHalfHeight, 0f)) accommodationValid = false
        this.inkHalfHeight = max(inkHalfHeight, 0f)
        if (itemBounds != null && itemCentres != null && itemCentres.isNotEmpty()) {
            if (itemLeft.size != itemCentres.size) {
                itemLeft = FloatArray(itemCentres.size)
                itemRight = FloatArray(itemCentres.size)
                itemCentre = FloatArray(itemCentres.size)
            }
            for (i in itemCentres.indices) {
                if (itemLeft[i] != itemBounds[i * 2] || itemRight[i] != itemBounds[i * 2 + 1] ||
                    itemCentre[i] != itemCentres[i]
                ) {
                    accommodationValid = false
                }
                itemLeft[i] = itemBounds[i * 2]
                itemRight[i] = itemBounds[i * 2 + 1]
                itemCentre[i] = itemCentres[i]
            }
        }
        if (!relayout) return
        // The reference capsule is the resting selector: half-spine and radius from the bar's own
        // declared pill. A layout change rebuilds it and rebases the body onto the latest valid
        // item, clearing every piece of gesture ownership together.
        reference = referenceFor(bar)
        if (!first && started) {
            interactionId++
            clearOwnership()
            snapToRest(requestedIndex)
        }
    }

    private fun referenceFor(bar: GlassSelectorBar): GlassReference {
        val r = max(bar.baseHalfHeight, 1f)
        val a = max(bar.baseHalfWidth - r, 0f)
        return GlassReference(a = a, r = r)
    }

    /** Put the body at rest on [index] with no motion. */
    fun snapToRest(index: Int) {
        requestedIndex = index
        restIndex = index.coerceIn(0, max(bar.count - 1, 0))
        pose.reset()
        velocity.reset()
        pose.cx = bar.centreOf(restIndex)
        pose.cy = bar.centreY
        // The resting spine, along the bar. Zero would also render it, but the state is what the
        // equilibrium is measured against, so it starts where the equilibrium is.
        pose.dx = reference.a
        pose.dy = 0f
        form.snapTo(0f)
        graspX.snapTo(pose.cx)
        graspY.snapTo(pose.cy)
        hasGrasp = false
        admitted = -1
        admittedNeighbour = -1
        mode = GlassPoseMode.Rest
        started = true
        solverFailed = false
        frame.update(reference, pose)
        captureLastGood()
        lastPublished.set(pose)
        formLast = 0f
        quietSteps = QUIET_STEPS
    }

    /** Aim the resting body at [index] without disturbing what it is doing. */
    fun retarget(index: Int) {
        requestedIndex = index
        restIndex = index.coerceIn(0, max(bar.count - 1, 0))
        // Aiming where the body already is is not travel: it must not wake the frame loop.
        if (abs(pose.cx - bar.centreOf(restIndex)) >= 0.25f) {
            quietSteps = 0
            if (mode == GlassPoseMode.Rest) mode = GlassPoseMode.TapTransit
        }
    }

    private fun clearOwnership() {
        accommodationValid = false
        pressEligible = false
        pointerDownActive = false
        hasGrasp = false
        pointerVx = 0f; pointerVy = 0f
        accelX = 0f; accelY = 0f
        admitted = -1
        admittedNeighbour = -1
    }

    // --------------------------------------------------------------------- pointer

    /**
     * Touch down. **Absolute pointer time is a Double**, and only the elapsed difference is
     * narrowed to Float: at a real device's multi-week uptime, Float seconds quantise to 125 ms
     * and a smooth 8 ms event stream becomes a stepped one. That repair is kept from the
     * owner-repair pass and is the reason velocity here is worth measuring at all.
     */
    fun pointerDown(x: Float, y: Float, eventSeconds: Double, eligible: Boolean) {
        pressStart = now
        pressEligible = eligible
        pointerDownActive = true
        downX = x; downY = y
        frame.update(reference, pose)
        frame.query(x, y, query)
        downOnBody = query.valid && query.coverageDistance <= 0f
        pointerX = x; pointerY = y
        lastPointerX = x; lastPointerY = y
        lastPointerTime = eventSeconds
        pointerClockOrigin = eventSeconds
        pointerSimulationOrigin = now
        hasPointerTime = true
        pointerVx = 0f; pointerVy = 0f
        accelX = 0f; accelY = 0f
        lastVx = 0f; lastVy = 0f
        lastSampleAt = now
        quietSteps = 0
        val regrab = mode == GlassPoseMode.Released && eligible && motionEnabled &&
            form.value > REGRAB_FORMATION
        mode = GlassPoseMode.PressPending
        // A pending DOWN records eligibility and pointer data. It does NOT take kinematic
        // ownership: the grasp is acquired at the moment the press actually becomes a hold, from
        // the body as it is then. The one exception is a re-grab: a finger landing on a body that
        // is still recovering from the last hold takes it back at once (section 5.1, "re-grab
        // replaces recovery ownership"). Sending it through the hold threshold again let the lens
        // fall to the pill and re-form - a blink 80 ms after the lift on the phone.
        if (regrab) {
            acquireGrasp(x, y, downOnBody)
            mode = GlassPoseMode.Held
        }
    }

    fun pointerMove(x: Float, y: Float, eventSeconds: Double) {
        if (spec.travelOnly && hasPointerTime && eventSeconds.isFinite()) {
            // Integrate the OLD target up to this input, then install the new one. Otherwise
            // several events between frames overwrite each other and the newest position is
            // incorrectly applied over the entire preceding frame interval.
            val eventTime = pointerSimulationOrigin + (eventSeconds - pointerClockOrigin).toFloat()
            if (eventTime.isFinite() && eventTime > now) advanceTo(eventTime)
        }
        pointerX = x
        pointerY = y
        lastSampleAt = now
        quietSteps = 0
        if (hasPointerTime) {
            val dt = (eventSeconds - lastPointerTime).toFloat()
            if (dt > 1e-5f) {
                val vx = (x - lastPointerX) / dt
                val vy = (y - lastPointerY) / dt
                // Acceleration from the change in velocity over real elapsed time, low-passed in
                // seconds. Differentiating a single event pair unfiltered would be noise.
                val alpha = 1f - exp(-dt / max(spec.accelerationTau, 1e-4f))
                accelX += alpha * ((vx - lastVx) / dt - accelX)
                accelY += alpha * ((vy - lastVy) / dt - accelY)
                lastVx = vx; lastVy = vy
                val velocityAlpha = 1f - exp(-dt / max(spec.velocityTau, 1e-4f))
                pointerVx += velocityAlpha * (vx - pointerVx)
                pointerVy += velocityAlpha * (vy - pointerVy)
                lastPointerX = x
                lastPointerY = y
                lastPointerTime = eventSeconds
            }
        }
    }

    /** A recognized drag. If the threshold already made this a hold, the existing grasp stays. */
    fun beginDrag(x: Float, y: Float) {
        pointerX = x
        pointerY = y
        quietSteps = 0
        if (mode != GlassPoseMode.Held) {
            acquireGrasp(downX, downY, downOnBody)
            mode = GlassPoseMode.Held
        }
    }

    /**
     * The finger left. The pose and every velocity are **preserved**; only the equilibria move to
     * the resting body. Section 8.1: no coordinate is reset, so there is no release kick and the
     * body recovers translation and shape together from wherever it actually is.
     */
    fun pointerUp(restIndex: Int) {
        quietSteps = 0
        interactionId++
        requestedIndex = restIndex
        this.restIndex = restIndex.coerceIn(0, max(bar.count - 1, 0))
        // A body released from a hold is outside the bar and regains contact later; a pill
        // released from a mere press never left it and keeps its contact throughout.
        releasedFromHold = mode == GlassPoseMode.Held
        mode = if (releasedFromHold) GlassPoseMode.Released else GlassPoseMode.TapTransit
        pressEligible = false
        pointerDownActive = false
        hasGrasp = false
        // The pointer's own speed stops driving new elongation the moment it is gone. The body
        // keeps the spine it already has and its own centre velocity, which is what carries the
        // stretch through the release.
        pointerVx = 0f; pointerVy = 0f
        accelX = 0f; accelY = 0f
    }

    fun cancel(restIndex: Int) = pointerUp(restIndex)

    /**
     * Take the grasp: store the touched point as an offset from the centre at unit pressure, so
     * stationary growth scales the body around the finger and nothing slides under it.
     *
     * For a pointer outside the current body the nearest point on the body is taken and the
     * rendered grasp starts there, approaching the pointer continuously. Nothing teleports and no
     * remote material point is fabricated.
     */
    private fun acquireGrasp(x: Float, y: Float, onBody: Boolean) {
        accommodationValid = false
        frame.update(reference, pose)
        frame.renderedToZ(x, y, scratchZ)
        frame.zToMaterial(scratchZ[0], scratchZ[1], scratch)
        // Clamp into the canonical body so an acquisition off the body owns a real point on it.
        val a = reference.a
        val r = max(reference.r, 1e-3f)
        var qx = scratch[0]
        var qy = scratch[1]
        val ex = qx - qx.coerceIn(-a, a)
        val len = sqrt(ex * ex + qy * qy)
        if (len > r) {
            val s = r / len
            qx = qx.coerceIn(-a, a) + ex * s
            qy *= s
        }
        // A press on another item travels to its centre; it does not grab a remote cap.
        if (!onBody) { qx = 0f; qy = (y - bar.centreY) / frame.expP }
        graspY0 = y
        slowFor = 0f
        accommodating = false
        graspQx = qx
        graspQy = qy
        hasGrasp = true
        // The rendered grasp starts where that material point currently is, then tracks the
        // pointer through one fast critical response.
        frame.materialToRendered(qx, qy, scratch)
        graspX.snapTo(scratch[0])
        graspY.snapTo(scratch[1])
        graspX.target = x
        graspY.target = y
    }

    // ---------------------------------------------------------------------- stepping

    /** Input can be slightly ahead of the next presentation timestamp. A stale presentation
     * is not a simulation clock reset. Explicit [advanceTo] still supports genuine rebasing. */
    fun advanceFrameTo(time: Float) {
        if (spec.travelOnly && time < now) return
        advanceTo(time)
    }

    /** The frame loop sleeps during a stationary hold. Input time can advance meanwhile;
     * resume from that simulation time instead of replaying the idle gap as frozen frames. */
    fun resumedFrameTime(previous: Float): Float = if (spec.travelOnly) max(previous, now) else previous

    /**
     * Advance to [time]. Backwards time is a **clock-origin change**, not a younger interaction:
     * the origin is rebased and every stored controller timestamp shifts with it, so no measured
     * duration is rewound. Pointer timestamps are on the event clock and are never shifted.
     */
    fun advanceTo(time: Float, maxCatchUpSeconds: Float = 0.5f) {
        if (time.isNaN()) return
        if (time < now) {
            val shift = time - now
            now = time
            pressStart += shift
            lastSampleAt += shift
            pointerSimulationOrigin += shift
            return
        }
        var remaining = time - now
        if (remaining <= 0f) return
        if (remaining > maxCatchUpSeconds) {
            // A long gap with the finger still down is a suspended loop, not an abandoned
            // gesture. Only a gap with no pointer down is lifecycle interruption.
            if (!pointerDownActive && (mode == GlassPoseMode.Held || mode == GlassPoseMode.PressPending)) {
                pointerVx = 0f; pointerVy = 0f
                mode = GlassPoseMode.Released
            }
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
        if (!motionEnabled && (mode == GlassPoseMode.Held || mode == GlassPoseMode.PressPending)) {
            interactionId++
            clearOwnership()
            mode = GlassPoseMode.Rest
        }

        // ---- formation
        val heldNow = motionEnabled && (
            mode == GlassPoseMode.Held ||
                (mode == GlassPoseMode.PressPending && pressEligible &&
                    now - pressStart >= spec.holdThresholdSeconds)
            )
        val slot = max(bar.slotWidth, 1f)
        // The speed that drives deformation: the finger's while held, the body's own while a tap
        // is in transit or a released body is still travelling. One law, so a long tap develops
        // more of it than a short one.
        val vx: Float
        val vy: Float
        if (mode == GlassPoseMode.Held) {
            if (now - lastSampleAt > spec.stopWindowSeconds) {
                val decay = exp(-dt / 0.060f)
                pointerVx *= decay
                pointerVy *= decay
                accelX *= decay
                accelY *= decay
            }
            vx = pointerVx
            vy = pointerVy
        } else if (mode == GlassPoseMode.Released) {
            // Calm throws retain travel deformation. Press formation still subsides below;
            // movement must not re-press or re-inflate a released selector.
            vx = if (spec.releaseTravel) velocity.cx else 0f
            vy = 0f
        } else {
            vx = velocity.cx
            vy = velocity.cy
        }
        val nvx = vx / slot
        val nvy = if (spec.travelOnly) 0f else vy / slot
        val speed2 = nvx * nvx + nvy * nvy
        val speed = sqrt(speed2)
        val tapMotion = mode != GlassPoseMode.Held &&
            (mode != GlassPoseMode.Released || spec.releaseTravel)
        val vRef = max(when {
            mode == GlassPoseMode.Held -> spec.heldReferenceSpeed
            tapMotion -> spec.tapReferenceSpeed
            else -> spec.referenceSpeed
        }, 1e-3f)
        val sv = speed2 / (vRef * vRef + speed2)
        // A press that is not yet a hold already forms a little: the rim of n2045.
        val pressResponse = when {
            heldNow -> 1f
            pointerDownActive && pressEligible -> spec.pressFormation
            else -> 0f
        }
        val formTarget = if (!motionEnabled || mode == GlassPoseMode.Released) {
            0f
        } else {
            1f - (1f - pressResponse) * (1f - spec.formFromMovement * sv)
        }
        form.target = formTarget
        form.step(dt, if (formTarget > form.value) spec.formRiseOmega else spec.formOmega, spec.formZeta)

        // ---- equilibria
        // Trace-free strain and taper: kept in the state for the shader contract, both zero.
        val gain = if (motionEnabled) 2f * spec.stretchGain * formation.coerceAtLeast(0.35f) else 0f
        val denom = vRef * vRef + speed2
        // A held body pulled off the bar stretches with its centre's displacement from the
        // centre line, along the pull and not with speed ([GlassPoseSpec.pullStrainPerDp]):
        // u < 0 is a stretch along y (exp(M) = diag(e^u, e^-u) with v = 0), which is the axis
        // the bar anchors. Along the bar the body is free and travels without stretching.
        val density = max(bar.density, 1e-3f)
        // The pull is the finger's travel off the bar since the grasp was taken: a finger that
        // presses off the centre line and stays there pulls nothing (the recording's presses
        // grow symmetrically, centroid within 1 px), a finger that then moves off the bar does.
        // Its direction leans with the along-bar speed ([GlassPoseSpec.pullTiltSeconds]) so a
        // diagonal swipe squeezes the body toward the swipe's angle; its size is the off-axis
        // travel alone, so a drag along the bar stretches nothing (reference-6756 T04).
        val pullY = if (mode == GlassPoseMode.Held && motionEnabled && hasGrasp) graspY.value - graspY0 else 0f
        val distanceDp = abs(pullY) / density
        val effectiveDp = distanceDp * distanceDp / (distanceDp + spec.pullOnsetDp).coerceAtLeast(1e-4f)
        val strainLimit = min(spec.pullMaxStrain, spec.maxStrain * 0.95f).coerceAtLeast(1e-4f)
        val pullStrain = strainLimit * tanh(spec.pullStrainPerDp * effectiveDp / strainLimit)
        var pullU = 0f
        var pullV = 0f
        if (pullStrain > 1e-5f) {
            val tiltX = vx * spec.pullTiltSeconds
            // The strain lives in the body's frame, before the spine's rotation.
            val psi = atan2(pullY, tiltX) - atan2(frame.sinTheta, frame.cosTheta)
            pullU = pullStrain * cos(2f * psi)
            pullV = pullStrain * sin(2f * psi)
        }
        equilibrium.u = gain * (nvx * nvx - 0.5f * speed2) / denom + pullU
        equilibrium.v = gain * (nvx * nvy) / denom + pullV

        val heldPressure = poseHeldPressure(spec.heldHeightRatio, bar.height, reference.height)
        // Squeeze belongs to a held pull only: the transit pill keeps its height (6698).
        val squeeze = if (mode == GlassPoseMode.Held) spec.squeeze * sv else 0f
        // The pulled body loses a little area as it stretches (reference-6756: -12 % at 37 pt).
        // Pressure shares the bounded deformation coordinate: it must not keep collapsing
        // after strain saturates, however far a pointer moves outside the bar.
        val pullPressure = spec.pullPressurePerDp * pullStrain / spec.pullStrainPerDp.coerceAtLeast(1e-4f)
        equilibrium.p = if (motionEnabled) heldPressure * formation - squeeze + pullPressure else 0f

        val ax = accelX / slot
        val ay = accelY / slot
        val aMag = sqrt(ax * ax + ay * ay)
        val aScale = max(spec.referenceAcceleration, 1e-3f)
        if (motionEnabled) {
            equilibrium.kx = spec.taperFromAcceleration * ax / (aScale + aMag) +
                spec.taperFromVelocity * nvx / (vRef + speed)
            equilibrium.ky = spec.taperFromAcceleration * ay / (aScale + aMag) +
                spec.taperFromVelocity * nvy / (vRef + speed)
        } else {
            equilibrium.kx = 0f
            equilibrium.ky = 0f
        }

        // Accommodation is a STATIONARY response (section 9, "stationary accommodation"): the
        // phone showed a held traverse breathing 30 px wider every time the finger passed between
        // two items, which no held drag in the recording does. It fades with speed and comes back
        // through its own spring when the finger stops.
        // A real stop, with hysteresis: engage after a dwell below the engage speed, let go
        // above the release speed (the owner's "keeps fluctuating" under a held swipe).
        if (mode == GlassPoseMode.Held && motionEnabled) {
            if (speed < spec.accommodationEngageSpeed) {
                slowFor += dt
            } else if (speed > spec.accommodationReleaseSpeed) {
                slowFor = 0f
                accommodating = false
            }
            if (!accommodating && slowFor >= spec.accommodationDwellSeconds) accommodating = true
        } else {
            slowFor = 0f
            accommodating = false
        }
        equilibrium.acc = if (accommodating) admittedSpine() else 0f

        // Motion adds a bounded horizontal spine; pressure only grows its rounded ends.
        // Pressure grows the capsule's rounded ends, not its straight middle. Phone T01
        // changes 284x162 -> roughly350x225; scaling the whole capsule predicts394px wide.
        // The rendered spine is already independent of pressure in GlassPoseFrame, and the
        // canonical grasp map accounts for its changing ratio to the expanded cap radius.
        val restSpine = reference.a
        var ex = restSpine
        var ey = 0f
        if (motionEnabled && speed2 > 1e-9f) {
            val horizontalSpeed2 = nvx * nvx
            val along = horizontalSpeed2 / (vRef * vRef + horizontalSpeed2)
            ex += (if (tapMotion) spec.tapElongationSlots else spec.elongationSlots) * slot * along
        }
        equilibrium.dx = ex
        equilibrium.dy = ey

        // ---- step the shape states
        val so = spec.shapeOmega
        val sz = spec.shapeZeta
        stepState(dt, pose.u, velocity.u, equilibrium.u, so, sz).let { pose.u = it[0]; velocity.u = it[1] }
        stepState(dt, pose.v, velocity.v, equilibrium.v, so, sz).let { pose.v = it[0]; velocity.v = it[1] }
        stepState(dt, pose.kx, velocity.kx, equilibrium.kx, so, sz).let { pose.kx = it[0]; velocity.kx = it[1] }
        stepState(dt, pose.ky, velocity.ky, equilibrium.ky, so, sz).let { pose.ky = it[0]; velocity.ky = it[1] }
        val po = spec.pressureOmega
        val pz = spec.pressureZeta
        // Subsiding after a release is slower than growing or being squeezed under a finger.
        val pressureRate = if (mode != GlassPoseMode.Held && equilibrium.p < pose.p) spec.pressureFallOmega else po
        stepState(dt, pose.p, velocity.p, equilibrium.p, pressureRate, pz).let { pose.p = it[0]; velocity.p = it[1] }
        stepState(dt, pose.acc, velocity.acc, equilibrium.acc, spec.accommodationOmega, 1f).let { pose.acc = it[0]; velocity.acc = it[1] }
        val spo = when {
            mode == GlassPoseMode.Released -> spec.spineReleaseOmega
            tapMotion -> spec.tapSpineOmega
            else -> spec.spineOmega
        }
        val spz = if (tapMotion) spec.tapSpineZeta else spec.spineZeta
        stepState(dt, pose.dx, velocity.dx, equilibrium.dx, spo, spz).let { pose.dx = it[0]; velocity.dx = it[1] }
        stepState(dt, pose.dy, velocity.dy, equilibrium.dy, spo, spz).let { pose.dy = it[0]; velocity.dy = it[1] }
        clampShape()

        // ---- the centre
        if (mode == GlassPoseMode.Held && hasGrasp) {
            frame.update(reference, pose)
            frame.materialToRendered(graspQx, graspQy, scratch)
            val shapeX = scratch[0] - pose.cx
            val shapeY = scratch[1] - pose.cy
            // Preserve the grasp inside the anchor range. Beyond it, resist the target before
            // stepping, rather than clipping only the displayed centre and storing overshoot.
            // grabCentreFor still resolves raw intent, so the end tabs remain reachable.
            graspX.target = glassAnchoredTravel(pointerX - shapeX, bar.firstCentre,
                bar.lastCentre, spec.endTravelCapRatio * bar.height) + shapeX
            graspY.target = pointerY
            graspX.step(dt, spec.graspOmega, 1f)
            graspY.step(dt, spec.graspOmega, 1f)
            val cap = (spec.pullFollowCapRatio * bar.height).coerceAtLeast(1e-4f)
            val pull = graspY.value - graspY0
            val response = tanh(spec.pullFollow * pull / cap)
            val follow = glassBoundedTravel(spec.pullFollow * pull, cap)
            pose.cx = graspX.value - shapeX
            pose.cy = graspY0 + follow - shapeY
            updateShapeVelocity()
            velocity.cx = graspX.velocity - shapeVel[0]
            velocity.cy = spec.pullFollow * (1f - response * response) * graspY.velocity - shapeVel[1]
        } else {
            val targetX = bar.centreOf(restIndex)
            val targetY = bar.centreY
            if (!motionEnabled) {
                pose.cx = targetX; pose.cy = targetY
                velocity.cx = 0f; velocity.cy = 0f
            } else {
                val co = spec.centreOmega
                val cz = spec.centreZeta
                stepState(dt, pose.cx, velocity.cx, targetX, co, cz).let { pose.cx = it[0]; velocity.cx = it[1] }
                stepState(dt, pose.cy, velocity.cy, targetY, co, cz).let { pose.cy = it[0]; velocity.cy = it[1] }
            }
        }

        frame.update(reference, pose)

        // ---- contact
        // Held has NO resting-bar contact: a held body is permitted to protrude. A released body
        // regains it only once it is entirely back inside the margin.
        val wantsContact = when (mode) {
            GlassPoseMode.Held -> false
            // Section 5.1: a released body regains resting contact only after the whole body is
            // back inside the margin AND its next predicted step is feasible. While the held
            // material is still subsiding its own pressure equilibrium is still larger than the
            // bar, so engaging contact there sets the pressure spring fighting the wall every
            // substep and reports a stream of failures for a body that has simply not finished
            // shrinking yet.
            GlassPoseMode.Released ->
                !releasedFromHold || (
                    form.value < CONTACT_REGAIN_FORMATION &&
                        pose.acc <= CONTACT_REGAIN_ACCOMMODATION &&
                        insideRestEnvelope(CONTACT_REGAIN_MARGIN)
                    )
            else -> true
        }
        if (wantsContact) {
            applyContact(dt)
        } else {
            solverFailed = false
        }

        frame.update(reference, pose)
        pressureLog = pose.p
        // The spine state's own length, without the accommodation the frame folds into it: the
        // elongation is what motion added, reported apart from what content asked for.
        val restHalfSpine = reference.a
        val spineLength = sqrt(pose.dx * pose.dx + pose.dy * pose.dy)
        elongationPx = max(spineLength - restHalfSpine, 0f)
        strainMagnitude = elongationPx / max(restHalfSpine + reference.r * frame.expP, 1f)
        captureLastGood()
        recordQuiet()

        if (mode == GlassPoseMode.PressPending && heldNow) {
            // The material the hold owns is what the finger touched when it went DOWN, on the
            // body as it is now; the grasp then tracks wherever the finger has moved since. With
            // every pre-slop move now reported, taking the current pointer here made a press
            // beside the pill grasp the pill's own material once the finger had drifted onto it,
            // and the body then trailed the finger by that offset for the whole drag.
            acquireGrasp(downX, downY, downOnBody)
            mode = GlassPoseMode.Held
        }
        if (mode == GlassPoseMode.Released || mode == GlassPoseMode.TapTransit) {
            val settled = abs(pose.cx - bar.centreOf(restIndex)) < 0.25f &&
                abs(velocity.cx) < 1.5f && abs(velocity.cy) < 1.5f &&
                pose.rho < 0.005f && abs(pose.p) < 0.005f && form.value < 0.01f &&
                abs(spineLength - restHalfSpine) < 0.25f &&
                abs(velocity.dx) < 1.5f && abs(velocity.dy) < 1.5f
            if (settled) mode = GlassPoseMode.Rest
        }
    }

    private val shapeVel = FloatArray(2)

    private fun updateShapeVelocity() {
        val h = 1f / 4800f
        val out = scratchDeriv
        var loX = 0f; var loY = 0f; var hiX = 0f; var hiY = 0f
        for (side in 0..1) {
            val sign = if (side == 0) -1 else 1
            scratchPose.set(pose)
            scratchPose.u += sign * h * velocity.u
            scratchPose.v += sign * h * velocity.v
            scratchPose.kx += sign * h * velocity.kx
            scratchPose.ky += sign * h * velocity.ky
            scratchPose.p += sign * h * velocity.p
            scratchPose.acc += sign * h * velocity.acc
            scratchPose.dx += sign * h * velocity.dx
            scratchPose.dy += sign * h * velocity.dy
            scratchPose.cx = 0f
            scratchPose.cy = 0f
            derivFrame.update(reference, scratchPose)
            derivFrame.materialToZ(graspQx, graspQy, out)
            derivFrame.zToRendered(out[0], out[1], out)
            if (sign < 0) { loX = out[0]; loY = out[1] } else { hiX = out[0]; hiY = out[1] }
        }
        shapeVel[0] = (hiX - loX) / (2f * h)
        shapeVel[1] = (hiY - loY) / (2f * h)
    }

    private val scratchPose = GlassPose()
    private val scratchDeriv = FloatArray(2)
    private val derivFrame = GlassPoseFrame()

    // ------------------------------------------------------------------ containment

    /** Is the whole contour inside the resting bar, with [margin] to spare? */
    private fun insideRestEnvelope(margin: Float): Boolean {
        frame.contour(CONTOUR_SAMPLES, contour)
        for (i in 0 until CONTOUR_SAMPLES) {
            if (barSignedDistance(contour[i * 2], contour[i * 2 + 1]) > -margin) return false
        }
        return true
    }

    /**
     * Containment on the **actual 2D contour**, not its centre or its bounding box.
     *
     * The correction moves the states that can fix the violation - the centre first, then the
     * pressure - rather than only the centre, so a body that is genuinely too big shrinks instead
     * of sliding. Incoming normal velocity at contact is removed; tangential and unconstrained
     * mode velocities are preserved.
     */
    private fun applyContact(dt: Float) {
        var iterations = 0
        var worst: Float
        do {
            frame.update(reference, pose)
            frame.contour(CONTOUR_SAMPLES, contour)
            worst = -Float.MAX_VALUE
            var wx = 0f
            var wy = 0f
            for (i in 0 until CONTOUR_SAMPLES) {
                val x = contour[i * 2]
                val y = contour[i * 2 + 1]
                val d = barSignedDistance(x, y)
                if (d > worst) { worst = d; wx = x; wy = y }
            }
            if (worst <= 0f) break
            // The outward direction at the worst point, by the bar's own gradient.
            val e = 0.5f
            val gx = (barSignedDistance(wx + e, wy) - barSignedDistance(wx - e, wy)) / (2f * e)
            val gy = (barSignedDistance(wx, wy + e) - barSignedDistance(wx, wy - e)) / (2f * e)
            val gl = sqrt(gx * gx + gy * gy)
            if (gl < 1e-5f) break
            val nx = gx / gl
            val ny = gy / gl

            // How far the body also violates on the OPPOSITE side. This is what decides between
            // the two corrections, and getting it wrong is why a body that is simply too tall
            // used to oscillate: translating it down makes its bottom violate, translating it
            // back up makes its top violate, and it never converges. The bilateral part of the
            // violation can only be fixed by shrinking; the rest by moving.
            var opposite = -Float.MAX_VALUE
            for (i in 0 until CONTOUR_SAMPLES) {
                val x = contour[i * 2]
                val y = contour[i * 2 + 1]
                val along = (x - pose.cx) * nx + (y - pose.cy) * ny
                if (along >= 0f) continue
                opposite = max(opposite, barSignedDistance(x, y))
            }
            val bilateral = min(worst, max(opposite, 0f))
            val oneSided = worst - bilateral

            if (oneSided > 0f) {
                val translate = min(oneSided, MAX_CONTACT_STEP)
                pose.cx -= nx * translate
                pose.cy -= ny * translate
            }
            if (bilateral > 0f) {
                // A body too long for the bar loses spine first - that is the state that made
                // it long - and only then is the whole body shrunk through the pressure.
                val restHalfSpine = reference.a
                val spineLength = sqrt(pose.dx * pose.dx + pose.dy * pose.dy)
                val extra = spineLength - restHalfSpine
                if (extra > 0.5f && abs(nx) > abs(ny)) {
                    val take = min(bilateral, extra)
                    val s = max((spineLength - take) / spineLength, 0.5f)
                    pose.dx *= s
                    pose.dy *= s
                    velocity.dx *= s
                    velocity.dy *= s
                } else {
                    // Shrink by the log of the ratio the reach has to change by, which is the
                    // state the isotropic expansion is expressed in.
                    val reach = max(sqrt((wx - pose.cx) * (wx - pose.cx) + (wy - pose.cy) * (wy - pose.cy)), 1f)
                    val shrink = kotlin.math.ln(max((reach - bilateral) / reach, 0.5f))
                    pose.p += max(shrink, -MAX_CONTACT_PRESSURE_STEP)
                    // The pressure is no longer free to spring straight back into the wall it
                    // was just pushed out of, any more than the centre is.
                    if (velocity.p > 0f) velocity.p = 0f
                }
            }
            // Remove the incoming normal component of the centre velocity; keep the tangent and
            // every unconstrained mode.
            val vn = velocity.cx * nx + velocity.cy * ny
            if (vn > 0f) {
                velocity.cx -= vn * nx
                velocity.cy -= vn * ny
            }
            iterations++
        } while (worst > CONTACT_TOLERANCE && iterations < MAX_CONTACT_ITERATIONS)

        frame.update(reference, pose)
        frame.contour(CONTOUR_SAMPLES, contour)
        worst = (0 until CONTOUR_SAMPLES).maxOf { barSignedDistance(contour[it * 2], contour[it * 2 + 1]) }
        if (worst > CONTACT_TOLERANCE) {
            // The corrected state is the closest this solve could reach and it is finite, so it
            // is published and the shortfall is disclosed. Restoring an earlier state would be
            // wrong twice over: that state is no more feasible against the envelope the body is
            // in now, and swapping back to it every substep is a loop rather than a recovery. A
            // default capsule is never substituted for a frame.
            contactFailures++
            solverFailed = true
            if (!pose.cx.isFinite() || !pose.cy.isFinite() || !pose.p.isFinite()) restoreLastGood()
        } else {
            solverFailed = false
        }
        frame.update(reference, pose)
    }

    /** Signed distance from a point to the resting bar's rounded rect; negative inside. */
    private fun barSignedDistance(px: Float, py: Float): Float {
        val hw = bar.width / 2f
        val hh = bar.allowedHalfHeight
        val rad = min(bar.cornerRadius, hh)
        val qx = abs(px - bar.width / 2f) - hw + rad
        val qy = abs(py - bar.centreY) - hh + rad
        val outside = sqrt(max(qx, 0f) * max(qx, 0f) + max(qy, 0f) * max(qy, 0f))
        return min(max(qx, qy), 0f) + outside - rad
    }

    private val lastGood = GlassPose()
    private val lastGoodVelocity = GlassPose()
    private var hasLastGood = false

    private fun captureLastGood() {
        lastGood.set(pose)
        lastGoodVelocity.set(velocity)
        hasLastGood = true
    }

    private fun restoreLastGood() {
        if (!hasLastGood) return
        pose.set(lastGood)
        velocity.set(lastGoodVelocity)
    }

    // ---------------------------------------------------------------- accommodation

    /**
     * The added half-spine the admitted content asks for, section 9.
     *
     * Eligibility comes from the **unaccommodated** body and the logical grasp, never from the
     * already expanded contour, so the expand-discover-expand feedback that made a global width
     * clamp look necessary cannot start. At most the primary item and one adjacent item on the
     * entered side contribute, with hysteresis.
     */
    private fun admittedSpine(): Float {
        if (itemCentre.isEmpty()) { accommodationDemand = 0f; return 0f }
        val g = graspX.value
        // Section 12: solve only when the inputs change. Re-running the bisection every substep
        // is not just wasted work - the body it probes has moved a fraction of a pixel since the
        // last one, so the answer jitters, and a jittering equilibrium means the frame loop can
        // never report itself idle and a finger resting on the glass keeps a frame callback alive
        // for ever.
        if (accommodationValid &&
            abs(g - accommodationGrasp) <= ACCOMMODATION_RESOLVE_PX &&
            abs(graspY.value - accommodationGraspY) <= ACCOMMODATION_RESOLVE_PX &&
            abs(pose.p - accommodationPressure) <= ACCOMMODATION_RESOLVE_PRESSURE
        ) {
            return accommodationDemand
        }
        accommodationGrasp = g
        accommodationGraspY = graspY.value
        accommodationPressure = pose.p
        accommodationValid = true
        val d = bar.density
        var nearest = 0
        var nearestDistance = Float.MAX_VALUE
        for (i in itemCentre.indices) {
            val dist = abs(itemCentre[i] - g)
            if (dist < nearestDistance) { nearestDistance = dist; nearest = i }
        }
        admitted = nearest
        // Freeze eligibility at the unaccommodated CALM body, the same body used by the
        // coverage probe. Rest-pill eligibility could exclude both adjacent centres at their
        // midpoint even though the held body already reaches them. Accommodation itself must
        // never enlarge this aperture and discover a third item.
        val calmPressure = poseHeldPressure(spec.heldHeightRatio, bar.height, reference.height)
        val aperture = reference.a + reference.r * exp(calmPressure)
        val admitInside = aperture - spec.admitInsetDp * d
        val releaseOutside = aperture + spec.admitReleaseDp * d
        var neighbour = -1
        var neighbourDistance = Float.MAX_VALUE
        for (i in itemCentre.indices) {
            if (i == nearest || abs(i - nearest) != 1) continue
            val dist = abs(itemCentre[i] - g)
            val keep = if (admittedNeighbour == i) dist <= releaseOutside else dist <= admitInside
            if (keep && dist < neighbourDistance) { neighbourDistance = dist; neighbour = i }
        }
        admittedNeighbour = neighbour
        val pad = spec.admitPaddingDp * d
        var left = itemLeft[nearest] - pad
        var right = itemRight[nearest] + pad
        if (neighbour >= 0) {
            left = min(left, itemLeft[neighbour] - pad)
            right = max(right, itemRight[neighbour] + pad)
        }
        // A bounded one-dimensional search over the added half-spine, with eligibility frozen.
        // The smallest spine that covers the pair is taken, and unnecessary expansion is not.
        val ceiling = spec.maxAccommodationSlots * bar.slotWidth
        // The pair first; if adding the neighbour cannot be helped at all, the item the grasp is
        // actually on is what the body is for.
        var demand = bestSpineFor(left, right, ceiling)
        if (demand <= 0f && neighbour >= 0) {
            admittedNeighbour = -1
            demand = bestSpineFor(itemLeft[nearest] - pad, itemRight[nearest] + pad, ceiling)
        }
        accommodationDemand = demand
        return demand
    }

    /** Invalidated by anything that can change the answer: layout, ink, or a new gesture. */
    private var accommodationGrasp: Float = Float.NaN
    private var accommodationGraspY: Float = Float.NaN
    private var accommodationPressure: Float = Float.NaN
    private var accommodationValid: Boolean = false

    /**
     * The smallest added half-spine in `[0, ceiling]` that gives the **best coverage achievable**
     * of the padded pair, measured on the actual rendered contour.
     *
     * Section 9 asks for "the smallest bounded value improving the eligible pair's coverage",
     * which is not the same as the smallest that fully encloses it. Full padded raw-label
     * enclosure is a preference, not a requirement - Apple's own 6721 leaves a label refracted at
     * the rim - and there are ordinary situations where it is simply unreachable: a grasp on one
     * end cap anchors that cap, so added spine grows the body **away** from the far side of the
     * label rather than toward it.
     *
     * So: scan for the smallest deficit, then take the smallest spine that attains it. When the
     * deficit reaches zero this is exactly the old bisection; when it cannot, the body still
     * grows as far as growing helps and then stops, instead of either expanding to the ceiling
     * for nothing or refusing to grow at all. Neither of those is "keep the edge crossing".
     */
    private fun bestSpineFor(left: Float, right: Float, ceiling: Float): Float {
        if (ceiling <= 0f) return 0f
        var bestSpine = 0f
        var bestDeficit = coverageDeficit(0f, left, right)
        val steps = 16
        for (i in 1..steps) {
            val spine = ceiling * i / steps
            val deficit = coverageDeficit(spine, left, right)
            if (deficit < bestDeficit - COVERAGE_EPSILON) {
                bestDeficit = deficit
                bestSpine = spine
            }
        }
        if (bestSpine <= 0f) return 0f
        // Refine downward: the smallest spine within the scan's own step that still attains the
        // best deficit, so no expansion is kept that does not pay for itself.
        var lo = max(bestSpine - ceiling / steps, 0f)
        var hi = bestSpine
        repeat(8) {
            val mid = (lo + hi) / 2f
            if (coverageDeficit(mid, left, right) <= bestDeficit + COVERAGE_EPSILON) hi = mid else lo = mid
        }
        return hi
    }

    /** Total corner overhang of the padded rectangle outside the body, in px; 0 when covered. */
    private fun coverageDeficit(spineExtra: Float, left: Float, right: Float): Float {
        probePose.set(pose)
        probePose.acc = spineExtra
        // Probe the body the hold is BECOMING, not the pill it still is: the calm held pressure,
        // and the resting spine along the bar. Probing the unformed pill demanded spine that the
        // formed lens then released, a 50 px burst-and-shrink at the start of every hold on the
        // phone (r5 drag_horizontal f136-f156).
        probePose.p = max(pose.p, poseHeldPressure(spec.heldHeightRatio, bar.height, reference.height))
        probePose.dx = reference.a
        probePose.dy = 0f
        probeFrame.update(reference, probePose)
        if (hasGrasp) {
            probeFrame.materialToRendered(graspQx, graspQy, scratch)
            probePose.cx = graspX.value - (scratch[0] - probePose.cx)
            val cap = (spec.pullFollowCapRatio * bar.height).coerceAtLeast(1e-4f)
            val follow = glassBoundedTravel(spec.pullFollow * (graspY.value - graspY0), cap)
            probePose.cy = graspY0 + follow - (scratch[1] - probePose.cy)
            probeFrame.update(reference, probePose)
        }
        val halfH = inkHalfHeight
        var deficit = 0f
        for (xi in 0..1) {
            val px = if (xi == 0) left else right
            for (yi in 0..1) {
                val py = bar.centreY + if (yi == 0) -halfH else halfH
                probeFrame.query(px, py, query)
                if (query.coverageDistance > 0f) deficit += query.coverageDistance
            }
        }
        return deficit
    }

    private val probePose = GlassPose()
    private val probeFrame = GlassPoseFrame()

    // ------------------------------------------------------------------- reporting

    /** How far the contour protrudes past the resting bar, in px; 0 when contained. */
    fun protrusion(): Float {
        frame.contour(CONTOUR_SAMPLES, contour)
        var worst = 0f
        for (i in 0 until CONTOUR_SAMPLES) {
            worst = max(worst, barSignedDistance(contour[i * 2], contour[i * 2 + 1]))
        }
        return worst
    }

    fun extents(out: GlassPoseExtents): GlassPoseExtents =
        glassPoseExtents(frame, CONTOUR_SAMPLES, contour, out)

    val isHeld: Boolean get() = mode == GlassPoseMode.Held
    val centreX: Float get() = pose.cx
    val centreY: Float get() = pose.cy
    val centreVelocityX: Float get() = velocity.cx
    val centreVelocityY: Float get() = velocity.cy

    /**
     * Where the body's centre will be for a pointer at [x], [y], **synchronously**.
     *
     * The host decides which tab a drag is previewing on the same frame the finger moved, and it
     * cannot wait for the frame loop to publish. This is the visible body's own centre, so the
     * preview follows the body rather than the raw finger - but it is **not** clamped by the
     * body's half width, because section 5 says navigation intent is never clamped by the
     * geometry. The caller clamps it to the item sequence, which is the only bound that belongs
     * to intent.
     */
    fun grabCentreFor(x: Float, y: Float): Float {
        if (!hasGrasp) return x
        frame.materialToRendered(graspQx, graspQy, scratch)
        return x - (scratch[0] - pose.cx)
    }

    /** The rendered position of the grasped material point; the attachment gate measures this. */
    fun graspRendered(out: FloatArray): FloatArray {
        if (!hasGrasp) { out[0] = pose.cx; out[1] = pose.cy; return out }
        frame.materialToRendered(graspQx, graspQy, out)
        return out
    }

    /**
     * Nothing has changed for long enough to stop asking for frames.
     *
     * Measured on what the body **did**, not on how far it is from a target and not on the mode:
     * a stationary held finger is idle, because the next pointer sample wakes the loop again, and
     * a held body that never reported itself idle would keep a frame callback alive for as long
     * as a finger rested on the glass. Contact regularly leaves a coordinate permanently short of
     * the target it is pulled toward, so a distance rule would never fire at all.
     *
     * A press whose hold timer has not expired is never idle: that threshold is the only thing
     * that will fire it.
     */
    val isIdle: Boolean
        get() = mode != GlassPoseMode.PressPending && quietSteps >= QUIET_STEPS

    private var quietSteps: Int = 0
    private val lastPublished = GlassPose()

    /** How far any pose coordinate moved this substep, in comparable units. */
    private fun recordQuiet() {
        var moved = 0f
        moved = max(moved, abs(pose.cx - lastPublished.cx))
        moved = max(moved, abs(pose.cy - lastPublished.cy))
        moved = max(moved, abs(pose.acc - lastPublished.acc))
        moved = max(moved, abs(pose.dx - lastPublished.dx))
        moved = max(moved, abs(pose.dy - lastPublished.dy))
        // The dimensionless states are scaled by the body's own size so a quiet threshold in px
        // means the same thing for all of them.
        val span = max(reference.a + reference.r, 1f)
        moved = max(moved, abs(pose.p - lastPublished.p) * span)
        moved = max(moved, abs(pose.u - lastPublished.u) * span)
        moved = max(moved, abs(pose.v - lastPublished.v) * span)
        moved = max(moved, abs(pose.kx - lastPublished.kx) * span)
        moved = max(moved, abs(pose.ky - lastPublished.ky) * span)
        moved = max(moved, abs(form.value - formLast) * 100f)
        lastPublished.set(pose)
        formLast = form.value
        quietSteps = if (moved <= QUIET_PX) quietSteps + 1 else 0
    }

    private var formLast: Float = 0f

    // --------------------------------------------------------------------- plumbing

    /**
     * Exact closed-form stepping for one scalar about a frozen target, shared by every state.
     *
     * Returns `(value, velocity)` in a reused pair so a substep allocates nothing. Velocities are
     * carried across every mode transition: nothing here resets a coordinate, which is what keeps
     * a release from kicking.
     */
    private fun stepState(
        dt: Float,
        value: Float,
        velocity: Float,
        target: Float,
        omega: Float,
        zeta: Float,
    ): FloatArray {
        scratchSpring.value = value
        scratchSpring.velocity = velocity
        scratchSpring.target = target
        scratchSpring.step(dt, omega, zeta)
        statePair[0] = scratchSpring.value
        statePair[1] = scratchSpring.velocity
        return statePair
    }

    private val scratchSpring = GlassSpring()
    private val statePair = FloatArray(2)

    /** The declared safety limits, enforced every substep rather than assumed. */
    private fun clampShape() {
        val rho = pose.rho
        if (rho > spec.maxStrain) {
            val s = spec.maxStrain / rho
            pose.u *= s; pose.v *= s
            velocity.u *= s; velocity.v *= s
        }
        val taper = sqrt(pose.kx * pose.kx + pose.ky * pose.ky)
        if (taper > spec.maxTaper) {
            val s = spec.maxTaper / taper
            pose.kx *= s; pose.ky *= s
            velocity.kx *= s; velocity.ky *= s
        }
        if (pose.acc < 0f) { pose.acc = 0f; velocity.acc = 0f }
        // The spine may not exceed what the node is sized for, whatever the springs do on the way.
        val maxSpine = reference.a + spec.elongationSlots * max(bar.slotWidth, 1f)
        val length = sqrt(pose.dx * pose.dx + pose.dy * pose.dy)
        if (length > maxSpine && length > 1e-6f) {
            val s = maxSpine / length
            pose.dx *= s; pose.dy *= s
            velocity.dx *= s; velocity.dy *= s
        }
    }

    private companion object {
        const val CONTOUR_SAMPLES = 256
        /** Movement under this, for [QUIET_STEPS] substeps running, counts as settled. */
        const val QUIET_PX = 0.02f
        const val QUIET_STEPS = 8
        const val CONTACT_TOLERANCE = 0.05f
        const val MAX_CONTACT_ITERATIONS = 24
        const val MAX_CONTACT_STEP = 12f
        const val MAX_CONTACT_PRESSURE_STEP = 0.06f
        // Zero, not a positive margin: the resting body exactly fills the resting envelope,
        // so requiring clearance would make the regain condition unreachable by construction.
        const val CONTACT_REGAIN_MARGIN = 0.0f
        const val CONTACT_REGAIN_ACCOMMODATION = 1.0f
        /** How far the grasp must move before the accommodation solve is worth redoing. */
        const val ACCOMMODATION_RESOLVE_PX = 1.0f
        /** ...and how much the body's own expansion must change before it is worth redoing. */
        const val ACCOMMODATION_RESOLVE_PRESSURE = 0.01f
        /** Coverage improvements below this are not worth the expansion that buys them. */
        const val COVERAGE_EPSILON = 0.5f
        const val CONTACT_REGAIN_FORMATION = 0.02f
        /** A body still this formed when a finger lands on it is re-grabbed, not pressed anew. */
        const val REGRAB_FORMATION = 0.15f
    }
}


/**
 * The widest body a pose selector can produce in this layout, for sizing its render node.
 *
 * A finite texture allocation is a **safety limit, not a material wall** (parity brief section
 * 5.1): the node is sized so the body never has to be shrunk to hide missing pixels. The bound
 * adds the accommodation ceiling, the peak elongation and the calm held expansion to the resting
 * body, all at their declared maxima, so it is conservative by construction rather than a guess
 * with a margin.
 */
internal fun GlassSelectorSpec.maxAccommodationWidth(
    bar: GlassSelectorBar?,
    barWidthPx: Float,
    pose: GlassPoseSpec = GlassPoseSpec(),
): Float {
    if (bar == null) return barWidthPx
    val r = max(bar.baseHalfHeight, 1f)
    val a = max(bar.baseHalfWidth - r, 0f)
    val held = exp(max(poseHeldPressure(pose.heldHeightRatio, bar.height, 2f * r), 0f))
    val slot = max(bar.slotWidth, 1f)
    val halfSpine = a + pose.elongationSlots * slot + pose.maxAccommodationSlots * slot
    return 2f * (halfSpine + r * held) * exp(pose.maxStrain) * (1f + pose.maxTaper) + 4f
}

/**
 * How far above and below the bar a pose selector's node must reach, in px.
 *
 * The same safety-limit rule as [maxAccommodationWidth], for the vertical axis. Growth past the
 * bar, the centre's follow of the finger and the spine's reach across the bar all share the one
 * envelope [GlassPoseSpec.heldExcursionRatio] declares, so that envelope — or the calm growth,
 * if a layout makes that larger — is what the node needs, plus a little for spring overshoot.
 */
internal fun GlassSelectorSpec.maxAccommodationOverflow(
    bar: GlassSelectorBar?,
    pose: GlassPoseSpec = GlassPoseSpec(),
): Float {
    if (bar == null) return 0f
    val r = max(bar.baseHalfHeight, 1f)
    val held = exp(max(poseHeldPressure(pose.heldHeightRatio, bar.height, 2f * r), 0f))
    val growth = r * held * (1f + pose.maxTaper) - bar.height / 2f
    val a = max(bar.baseHalfWidth - r, 0f)
    val halfSpine = a + (pose.elongationSlots + pose.maxAccommodationSlots) * max(bar.slotWidth, 1f)
    val support = (halfSpine * kotlin.math.sinh(pose.maxStrain) + r * held * exp(pose.maxStrain)) * (1f + pose.maxTaper)
    return max(support - bar.height / 2f, growth) + 8f
}
