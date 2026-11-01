package com.wexpa.liquidglass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.first
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The V3 selector's Compose-side plumbing: one controller, one frame clock, one snapshot a frame.
 *
 * Everything that decides *what the body is doing* lives in [GlassSelectorController], which
 * knows nothing about Compose; this is the adapter that feeds it the bar's current layout and
 * pointer facts and hands back the body the material should draw. Simulation substeps change
 * numbers here, never composition: the loop asks for a frame only while something is actually
 * moving, and a bar at rest costs no frame callback at all.
 */
internal class GlassSelectorHandle(val controller: GlassSelectorController) {
    var body: GlassBody by mutableStateOf(GlassBody(0f, 0f, 1f, 1f, 0f))
    var formation: Float by mutableFloatStateOf(0f)
    var held: Boolean by mutableStateOf(false)
    var centreX: Float by mutableFloatStateOf(0f)
    /** Bumped whenever something happens that the frame loop has to wake up for. */
    var epoch: Int by mutableIntStateOf(0)

    /**
     * Simulation time, **retained with the handle**.
     *
     * It used to live in the frame-loop effect, which was keyed on the bar's geometry and on the
     * motion mode: a resize or a reduced-motion toggle restarted the clock at zero underneath a
     * retained controller, and with it every duration the controller measures. The controller now
     * rebases a moved origin rather than rewinding, and this keeps the origin from moving at all.
     */
    var clock: Float = 0f
    var previousFrame: Long = -1L

    // ------------------------------------------------- the V3 node's coordinate frame
    //
    // One rounded origin per displayed frame, shared by the node's placement, the body's own
    // coordinates, the native ink's placement and the touch point. Reading a moving centre
    // independently in layout and in drawing is what let the contour, the aperture and the finger
    // sit in three slightly different frames at fractional density.

    /** The stable node's width and height, and the rounded overflow it starts above the bar. */
    var envelopeWidth: Float = 0f
    var nodeHeight: Float = 0f
    var overflowTop: Float = 0f
    var barWidth: Float = 0f

    /** The pointer in the **bar** frame; NaN when no finger is on the glass. */
    var barPointerX: Float = Float.NaN
    var barPointerY: Float = Float.NaN
    var pressSource: GlassPressSource? = null

    /** One-entry memo for the bar the frame loop rebuilds every frame. */
    val barCache = GlassSelectorBarCache()

    /**
     * The node's left edge in bar px, rounded **once**. Placement, body, ink and touch all call
     * this, so the integer they use can never disagree.
     */
    fun nodeLeft(): Int =
        (body.cx - envelopeWidth / 2f)
            .coerceIn(0f, max(barWidth - envelopeWidth, 0f))
            .roundToInt()

    fun wake() {
        epoch++
    }

    fun publish() {
        body = controller.body
        formation = controller.formation
        held = controller.isHeld
        centreX = controller.body.cx
        resolvePress()
    }

    /**
     * Resolve the stored bar-frame pointer against the origin **this frame** is using.
     *
     * For a layer whose bar-local origin is `O`, the local point is `pBar - O` and its texture
     * coordinate `(pLocal + pad) * renderScale`; the inverse recovers `pBar`, so changing the
     * node's integer placement is exactly cancelled by the local-coordinate change and the
     * world-space sample does not move. Called on every pointer event **and** every displayed
     * frame, so a stationary finger over a growing body keeps the right point.
     */
    fun resolvePress() {
        val p = pressSource ?: return
        if (barPointerX.isNaN() || barPointerY.isNaN()) return
        if (envelopeWidth <= 0f || nodeHeight <= 0f) return
        p.press(
            Offset(
                (barPointerX - nodeLeft()).coerceIn(0f, envelopeWidth),
                (barPointerY + overflowTop).coerceIn(0f, nodeHeight),
            ),
        )
    }
}

/**
 * The bar as the controller sees it, rebuilt from layout rather than guessed.
 *
 * [allowedHalfHeight] is the ordinary tap envelope — the bar's own outline, inset by the
 * a narrow hairline, so a tap deforms *within* the bar and the bar's edge stays visible
 * around it. [heldHalfHeight] is the larger envelope a genuine hold may grow into, including the
 * bar's own held growth, so the two never have to be reconciled after the fact.
 */
internal fun glassSelectorBarOf(
    density: Density,
    barWidthPx: Float,
    barHeightPx: Float,
    style: GlassTabBarStyle,
    itemCount: Int,
    formation: Float,
    spec: GlassSelectorSpec,
    cache: GlassSelectorBarCache? = null,
): GlassSelectorBar {
    // The bar is rebuilt on the frame clock, and the four dp-to-px conversions plus the object
    // itself are pure functions of inputs that almost never change between frames - the formation
    // is not even one of them. A one-entry cache turns the steady state into a pointer compare.
    val hit = cache?.get(barWidthPx, barHeightPx, style, itemCount, density.density)
    if (hit != null) return hit
    val built = buildGlassSelectorBar(density, barWidthPx, barHeightPx, style, itemCount, spec)
    cache?.put(barWidthPx, barHeightPx, style, itemCount, density.density, built)
    return built
}

/** One-entry memo for [glassSelectorBarOf], owned by the handle so it lives as long as the loop. */
internal class GlassSelectorBarCache {
    private var width = Float.NaN
    private var height = Float.NaN
    private var style: GlassTabBarStyle? = null
    private var count = -1
    private var density = Float.NaN
    private var bar: GlassSelectorBar? = null

    fun get(w: Float, h: Float, s: GlassTabBarStyle, c: Int, d: Float): GlassSelectorBar? =
        if (w == width && h == height && s === style && c == count && d == density) bar else null

    fun put(w: Float, h: Float, s: GlassTabBarStyle, c: Int, d: Float, value: GlassSelectorBar) {
        width = w; height = h; style = s; count = c; density = d; bar = value
    }
}

private fun buildGlassSelectorBar(
    density: Density,
    barWidthPx: Float,
    barHeightPx: Float,
    style: GlassTabBarStyle,
    itemCount: Int,
    spec: GlassSelectorSpec,
): GlassSelectorBar = with(density) {
    val insetPx = style.contentPadding.toPx()
    val geometry = glassTabBarGeometry(density, barWidthPx, style, itemCount)
    val slot = if (geometry.fixed) geometry.slotWidth else (barWidthPx - insetPx * 2f) / itemCount
    val pillWidth: Dp = if (style.pillWidth.isSpecified) maxOf(style.pillWidth, slot.toDp()) else slot.toDp()
    // A requested rest width may exceed the room around an end anchor on a compact bar.
    // Bound the reference capsule before it is published: moving the contact-solver centre
    // later would misalign the settled selection and leave its spring pushing into the wall.
    // This is layout feasibility, not a cap on held deformation or navigation intent.
    val restHalfWidth = if (spec.poseMotion) {
        min(pillWidth.toPx() / 2f, ((if (geometry.fixed) geometry.firstCentre else insetPx + slot / 2f) - density.density).coerceAtLeast(density.density))
    } else pillWidth.toPx() / 2f
    val requestedHalfHeight = barHeightPx / 2f - style.pillInset.toPx()
    val pillHalfHeight = if (spec.poseMotion) min(requestedHalfHeight, restHalfWidth) else requestedHalfHeight
    val grownHalf = barHeightPx / 2f * max(style.heldScale, 1f)
    GlassSelectorBar(
        width = barWidthPx,
        height = barHeightPx,
        inset = insetPx,
        count = itemCount,
        cornerRadius = barHeightPx / 2f,
        baseHalfWidth = restHalfWidth,
        baseHalfHeight = pillHalfHeight,
        // The rest silhouette is not a motion wall: 6698 is taller than settled6701.
        // Keep the old controller's constraint unchanged; only the opt-in pose model uses
        // the bar envelope minus one dp, while the settled inset is still style.pillInset.
        allowedHalfHeight = if (spec.poseMotion) max(pillHalfHeight, barHeightPx / 2f - density.density) else pillHalfHeight,
        heldHalfHeight = grownHalf + style.lensOverflow.toPx(),
        heldHalfWidth = (pillWidth.toPx() + style.lensExtraWidth.toPx()) / 2f,
        density = this.density,
        // Conservative recording extent retained from the previous compositor. The controller
        // separately enforces spec.maxWidthSlots on the actual contour, including deformation.
        maxBodyWidth = min(
            barWidthPx,
            2f * min(pillWidth.toPx() + style.lensExtraWidth.toPx(),
                max(spec.maxWidthSlots, 2.4f) * slot / 2f + slot) + spec.maxExtraLength * slot,
        ),
        fixedSlot = geometry.fixedSlot,
        endReach = geometry.endReach,
    )
}

/**
 * Runs the controller on the frame clock while anything is moving.
 *
 * The loop stops as soon as [GlassSelectorController.isIdle] reports that the centre, every shape
 * mode and the formation are all settled, and restarts when [GlassSelectorHandle.wake] is called.
 * A very late frame is handled inside the controller as lifecycle recovery rather than by
 * replaying a stale gesture here.
 */
@Composable
internal fun GlassSelectorFrameLoop(
    handle: GlassSelectorHandle,
    barProvider: () -> GlassSelectorBar,
    itemBounds: FloatArray?,
    itemCentres: FloatArray?,
    inkHalfHeight: Float,
    motionEnabled: Boolean,
) {
    // The loop resolves its layout and style inputs **when the frame runs**, not from the
    // composition that happened to start it. Keying the effect on those inputs instead - which is
    // what it did - restarted the clock every duration in the controller is measured on.
    val currentBar by rememberUpdatedState(barProvider)
    val currentBounds by rememberUpdatedState(itemBounds)
    val currentCentres by rememberUpdatedState(itemCentres)
    val currentInk by rememberUpdatedState(inkHalfHeight)
    val currentMotion by rememberUpdatedState(motionEnabled)
    LaunchedEffect(handle) {
        var seen = handle.epoch
        while (true) {
            while (true) {
                val idle = withFrameNanos { nanos ->
                    // The first frame after a wake contributes no time: nothing happened while
                    // the loop was suspended, so replaying that gap would be inventing motion.
                    if (handle.previousFrame < 0L) handle.previousFrame = nanos
                    handle.clock += (nanos - handle.previousFrame) / 1_000_000_000f
                    handle.previousFrame = nanos
                    handle.controller.motionEnabled = currentMotion
                    handle.controller.attach(currentBar(), currentBounds, currentCentres, currentInk)
                    handle.controller.advanceTo(handle.clock)
                    handle.publish()
                    handle.controller.isIdle
                }
                if (idle) break
            }
            // Nothing left to animate: stop asking for frames until something happens. A pointer
            // event, a selection change, a layout change or a motion toggle bumps the counter.
            val next = snapshotFlow { handle.epoch }.first { it != seen }
            seen = next
            handle.previousFrame = -1L
        }
    }
}

/**
 * The widths each item's icon and label actually occupy, as one layout snapshot.
 *
 * Items are equal slots inside the bar's inset and their content is centred, so a measured
 * content width plus the slot's own centre is the complete bound; nothing is read off a raster
 * and no slot width is guessed. The emphasised item is reported at its emphasised size, because
 * that is the ink the body has to make room for.
 */
internal class GlassItemBounds(count: Int) {
    val widths = FloatArray(count)
    val bounds = FloatArray(count * 2)
    val centres = FloatArray(count)

    fun refresh(bar: GlassSelectorBar, emphasisIndex: Int, emphasis: Float) {
        for (i in widths.indices) {
            val centre = bar.centreOf(i)
            val w = widths[i] * (if (i == emphasisIndex) max(emphasis, 1f) else 1f)
            centres[i] = centre
            bounds[i * 2] = centre - w / 2f
            bounds[i * 2 + 1] = centre + w / 2f
        }
    }
}

/**
 * The body in the selector node's own frame: the node starts [dy] above the bar and [dx] along it.
 * The shift is exact and continuous; only the node's placement is rounded to whole pixels.
 */
internal fun GlassBody.movedInto(dx: Float, dy: Float): GlassBody =
    copy(cx = cx - dx, cy = cy + dy)

/** Clamped to the travel the bar allows, so a grab off the end does not aim off-panel. */
internal fun GlassSelectorBar.clampCentre(x: Float): Float = min(max(x, firstCentre), lastCentre)

/**
 * The same clamp, for a body of half width [halfWidth]: it must also fit inside the bar.
 *
 * A capsule bar of half height H holds a disk of radius r centred on its spine exactly when the
 * disk's centre lies between r and width - r, so keeping `cx ± halfWidth` inside the bar is
 * enough. Without this a wide held body dragged to the end is handed a centre it cannot have, and
 * the projection - whose metric would rather change the shape than the centre - answers by
 * collapsing the body to its minimum radius instead of stopping it at the end.
 *
 * A body wider than the bar can only be centred, which is what the fallback does.
 */
internal fun GlassSelectorBar.clampCentre(x: Float, halfWidth: Float): Float {
    val lo = max(firstCentre, halfWidth)
    val hi = min(lastCentre, width - halfWidth)
    return if (lo > hi) width / 2f else min(max(x, lo), hi)
}


/**
 * The pose selector's Compose-side plumbing: one controller, one retained clock, one snapshot a
 * frame.
 *
 * It mirrors [GlassSelectorHandle]'s contract exactly - the same retained clock, the same single
 * rounded node origin, the same bar-frame pointer resolved per displayed frame - so the corrected
 * lifetime, coordinate and cancellation behaviour carries over unchanged. What differs is the
 * body it publishes.
 */
internal class GlassPoseHandle(val controller: GlassPoseController) {
    var render: GlassPoseRender? by mutableStateOf(null)
    var formation: Float by mutableFloatStateOf(0f)
    var transitAppearance: Float by mutableFloatStateOf(0f)
    var heldTravel: Float by mutableFloatStateOf(0f)
    var held: Boolean by mutableStateOf(false)
    var centreX: Float by mutableFloatStateOf(0f)
    var centreY: Float by mutableFloatStateOf(0f)
    var left: Float by mutableFloatStateOf(0f)
    var right: Float by mutableFloatStateOf(0f)
    /** Signed end asymmetry: the pose body's taper along the travel axis. */
    var taper: Float by mutableFloatStateOf(0f)
    var epoch: Int by mutableIntStateOf(0)

    /** Simulation time, retained with the handle so no recomposition can restart it. */
    var clock: Float = 0f
    var previousFrame: Long = -1L

    var envelopeWidth: Float = 0f
    var nodeHeight: Float = 0f
    var overflowTop: Float = 0f
    var barWidth: Float = 0f
    var barPointerX: Float = Float.NaN
    var barPointerY: Float = Float.NaN
    var pressSource: GlassPressSource? = null
    val barCache = GlassSelectorBarCache()
    /**
     * How far the bar's own press moves the body at a bar-local x, or null: the drawn body then
     * sits where the bar's pressed content puts that point, as its tab does. iOS's held lens
     * at Calls is centred on the pressed tab (203 px), not on the finger (219; video2 1.8 s,
     * 2026-10-15 pass 13). Navigation, touch and selection stay in the controller's frame.
     */
    var barShift: ((Float) -> Float)? = null
    /**
     * How far the drawn body stretches at one end, px, at a bar-local x, or null: positive grows its right end,
     * negative its left; the other end stays. A held lens beside an accessory that yields to it reaches over the
     * accessory's edge (#107). Drawing only: navigation, touch and selection stay in the controller's frame.
     */
    var endStretch: ((Float) -> Float)? = null
    /** The current [barShift] at the controller's centre, applied by [publish]. */
    var barShiftX: Float = 0f
        private set

    private val extents = GlassPoseExtents()

    /** The node's left edge in bar px, rounded once: placement, body, ink and touch all use it. */
    fun nodeLeft(): Int =
        (centreX - envelopeWidth / 2f)
            .roundToInt()

    /** The texture follows the free body; the resting bar is not a clipping rectangle. */
    fun nodeTop(): Int = (centreY - nodeHeight / 2f).roundToInt()

    fun wake() { epoch++ }

    fun publish() {
        controller.extents(extents)
        barShiftX = barShift?.invoke(controller.centreX) ?: 0f
        centreX = controller.centreX + barShiftX
        centreY = controller.centreY
        left = extents.left + barShiftX
        right = extents.right + barShiftX
        formation = controller.formation
        transitAppearance = controller.transitAppearance
        heldTravel = controller.heldTravel
        held = controller.isHeld
        taper = controller.taperX
        // Bar-local to the element's CENTRED frame, which is what the shaders work in:
        //     centred.x = pBar.x - nodeLeft - envelopeWidth / 2
        //     centred.y = pBar.y + overflowTop - nodeHeight / 2
        // so the offsets subtracted from `c` are the ones below. Getting the sign of the envelope
        // term wrong puts the body a whole node-width outside the node it is drawn in, where it
        // renders nothing at all and the bar shows through with its own ink.
        // The held lens's outline (#93): a superellipse as it forms; a resting selector stays a capsule.
        @OptIn(LiquidGlassDiagnosticApi::class)
        val squircle = LiquidGlassDiagnostics.lensSquircle
        controller.frame.squircle = (squircle?.getOrNull(0) ?: controller.spec.heldSquircle) * formation.coerceIn(0f, 1f)
        controller.frame.squircleExponent = squircle?.getOrNull(1) ?: controller.spec.squircleExponent
        render = glassPoseRenderOf(
            controller.frame,
            dx = nodeLeft().toFloat() + envelopeWidth / 2f - barShiftX,
            dy = nodeTop().toFloat() + nodeHeight / 2f,
        ).also { r ->
            val stretch = endStretch?.invoke(controller.centreX) ?: 0f
            if (stretch != 0f) {
                // Half the stretch lengthens the spine about the centre and half moves the centre, so only one end moves.
                // The spine is a canonical length: the pose matrix's first column maps it to rendered px.
                val ax = r.a[0]; val ay = r.a[2]
                val n = kotlin.math.sqrt(ax * ax + ay * ay).coerceAtLeast(1e-4f)
                r.d[0] += kotlin.math.abs(stretch) / 2f / n
                r.c[0] += stretch / 2f * ax / n
                r.c[1] += stretch / 2f * ay / n
                if (stretch > 0f) right += stretch else left += stretch
            }
        }
        resolvePress()
    }

    fun resolvePress() {
        val p = pressSource ?: return
        if (barPointerX.isNaN() || barPointerY.isNaN()) return
        if (envelopeWidth <= 0f || nodeHeight <= 0f) return
        p.press(
            Offset(
                (barPointerX - nodeLeft()).coerceIn(0f, envelopeWidth),
                (barPointerY - nodeTop()).coerceIn(0f, nodeHeight),
            ),
        )
    }
}

/**
 * Runs the pose controller on the frame clock while anything is moving. Same contract as
 * [GlassSelectorFrameLoop]: inputs resolved when the frame runs, never captured from the
 * composition that started the effect.
 */
@Composable
internal fun GlassPoseFrameLoop(
    handle: GlassPoseHandle,
    barProvider: () -> GlassSelectorBar,
    itemBounds: FloatArray?,
    itemCentres: FloatArray?,
    inkHalfHeight: Float,
    motionEnabled: Boolean,
) {
    val currentBar by rememberUpdatedState(barProvider)
    val currentBounds by rememberUpdatedState(itemBounds)
    val currentCentres by rememberUpdatedState(itemCentres)
    val currentInk by rememberUpdatedState(inkHalfHeight)
    val currentMotion by rememberUpdatedState(motionEnabled)
    LaunchedEffect(handle) {
        var seen = handle.epoch
        while (true) {
            while (true) {
                val idle = withFrameNanos { nanos ->
                    if (handle.previousFrame < 0L) {
                        // Waking: the input that woke the loop has already advanced the
                        // simulation to its own timestamp, but this frame presents later than
                        // that. Present the frame's own time, or the whole gesture runs that far
                        // behind the finger: about 1.5 frames on video2's throws after a still
                        // hold (2026-10-14 pass 12). Only when the pointer and frame clocks
                        // visibly share a base (0-50 ms apart); travel-only (Calm) specs only.
                        val c = handle.controller
                        if (c.spec.travelOnly && c.hasPointerClock) {
                            val lag = nanos / 1_000_000_000.0 - c.lastPointerSeconds
                            if (lag > 0.0 && lag <= WAKE_CATCH_UP_SECONDS) handle.clock += lag.toFloat()
                        }
                        handle.previousFrame = nanos
                    }
                    handle.clock += (nanos - handle.previousFrame) / 1_000_000_000f
                    handle.previousFrame = nanos
                    handle.controller.motionEnabled = currentMotion
                    handle.controller.attach(currentBar(), currentBounds, currentCentres, currentInk)
                    handle.controller.advanceFrameTo(handle.clock)
                    handle.publish()
                    handle.controller.isIdle
                }
                if (idle) break
            }
            val next = snapshotFlow { handle.epoch }.first { it != seen }
            seen = next
            handle.clock = handle.controller.resumedFrameTime(handle.clock)
            handle.previousFrame = -1L
        }
    }
}

/** The most a woken pose loop catches up from its waking input to the frame: three 60 Hz frames. */
private const val WAKE_CATCH_UP_SECONDS = 0.05
