package com.wexpa.liquidglass

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.lerp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch

/**
 * A tab bar whose selection indicator is a piece of glass you can pick up.
 *
 * This is the component the material was measured against. Every number in its default style
 * is read off native screenshots of an iOS 26 tab bar in dark appearance, and the parts that
 * matter are the parts that are easy to get wrong:
 *
 *  - **At rest the indicator is not glass.** It is a flat dark inset cut into the bar, with an
 *    edge two or three pixels wide and no optics of any kind. Apple's rule for slider knobs
 *    applies to it: the knob "transforms into Liquid Glass during interaction". Give it a bend
 *    at rest and it grows a bright ring, which is the single most visible tell that an
 *    implementation is not the real thing.
 *  - **Touch-down makes it glass.** The inset swells into a lens a few points taller than the
 *    bar, standing proud by the same amount above and below, and about one item plus 16pt
 *    wide. It forms in about a tenth of a second and subsides in about a third.
 *  - **The lens looks through the bar, not past it.** On iOS the lens sits above the bar, so
 *    what reaches the eye has already been through the bar's frost. This component records the
 *    bar and hands it to the lens as the glass it looks [through][liquidGlass] — the lens's
 *    interior reads as the bar does, its rim pulls the bar's own hairline and the dark gap
 *    around the bar inward, and the content behind the bar is no more visible through the lens
 *    than it was through the bar.
 *  - **The tabs under the lens change colour because you are looking at them through it.** The
 *    row is drawn twice, once in its resting state on the bar and once in its selected state
 *    inside the lens as [refracted content][liquidGlass]. The lens shows only the part it is
 *    over, so a tab half under it is half one colour and half the other, and its edges fringe
 *    where the rim refracts them.
 *  - **The lens is the thing you drag.** It goes exactly where the finger goes, previews the
 *    tab it is over, and commits once, on release — a flick carries it to the next tab. A tap is
 *    the item's own click, once; a gesture another node takes over, a vertical scroll, a second
 *    finger or the node going away selects nothing and settles the lens back on the selection.
 *
 * [item] is called for every tab twice on the shader path — `selected = false` for the copy on
 * the bar and `selected = true` for the copy inside the lens — and once, with the real
 * selection, where the shader is unavailable. Render the same geometry either way and change
 * only colour: the two copies have to line up to the pixel where the lens crosses them.
 *
 * Put a shadow on [modifier] with `clip = false`, so the lens can stand proud of the bar.
 */
@Composable
fun GlassTabBar(
    state: LiquidGlassState,
    itemCount: Int,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    style: GlassTabBarStyle = GlassTabBarStyle.Dark,
    /** False when the user or the system has asked for less motion: no lens, no gel, no fling. */
    motionEnabled: Boolean = true,
    /** 0 to 1, for the bar to materialize in and out. See [liquidGlass]. */
    materialize: Float = 1f,
    item: @Composable (index: Int, selected: Boolean) -> Unit,
) {
    require(itemCount > 0) { "GlassTabBar needs at least one item" }
    val barShape: Shape = style.shape
    // A true capsule at every size the selector passes through between inset and lens.
    val selectorShape = RoundedCornerShape(percent = 50)

    // The lens carries the selected ink only where the shader can refract it. Without the shader
    // the selector is a plain surface and the items colour themselves.
    val lensCarriesInk = LiquidGlassSupport.hasShaders

    // What the lens looks through: the bar's own rendered output, plate and items together.
    val barState = rememberLiquidGlassState(
        background = state.background,
        inversion = state.inversion,
        frost = state.frost,
        contrast = state.contrast,
    )

    // Nothing here clips: the lens stands proud of the bar while it is held.
    BoxWithConstraints(modifier = modifier.height(style.height)) {
        val density = LocalDensity.current
        val barWidthDp: Dp = maxWidth
        val barHeightDp: Dp = maxHeight
        val barWidthPx = with(density) { barWidthDp.toPx() }
        val barHeightPx = with(density) { barHeightDp.toPx() }
        val geometry = GlassTabBarGeometry(
            barWidth = barWidthPx,
            inset = with(density) { style.contentPadding.toPx() },
            count = itemCount,
        )
        val slotWidthDp = with(density) { geometry.slotWidth.toDp() }
        val flingVelocity = with(density) { style.flingVelocity.toPx() }
        val gelReference = with(density) { style.gelReference.toPx() }

        val selected = selectedIndex.coerceIn(0, itemCount - 1)
        val restingAt = geometry.centreOf(selected)

        // The gesture coroutine outlives the composition that started it, so it must not keep
        // calling the callback it happened to be born with.
        val select by rememberUpdatedState(onSelected)

        // Which tab the finger is over, or -1 when no finger is involved. A press or a drag
        // *shows* the tab it is over; it does not switch to it. Switching on every boundary
        // builds a screen per tab crossed, which can starve the gesture on a slow screen;
        // committing once on release is also what the gesture means.
        var dragIndex by remember { mutableIntStateOf(-1) }
        val activeIndex = if (dragIndex >= 0) dragIndex else selected
        // True from a finger's down to the end of its gesture, however that ends.
        var gestureDown by remember { mutableStateOf(false) }

        // The selector's position. A plain float rather than an Animatable, because a drag
        // callback cannot suspend and the lens has to be where the finger is on the same frame.
        var offsetX by remember { mutableFloatStateOf(restingAt) }
        var settleVelocity by remember { mutableFloatStateOf(0f) }
        var settleTarget by remember { mutableFloatStateOf(restingAt) }
        var motion by remember { mutableStateOf<Job?>(null) }
        val scope = rememberCoroutineScope()
        val press = rememberGlassPressSource()
        var v2Held by remember { mutableStateOf(false) }
        var dragging by remember { mutableStateOf(false) }
        var liveVelocity by remember { mutableFloatStateOf(0f) }

        // The V3 deforming body, when this style asks for one. Null leaves every line below on
        // the capsule path this component shipped with, which is what keeps the existing
        // behaviour, presets and tests exactly as they were.
        val spec = style.selector
        // Exactly one of these is non-null. `poseMotion` selects the 2D pose body this pass
        // introduced; the other path is the five-coordinate horizontal-only controller whose
        // motion the owner rejected, kept reachable so the two can be compared on one phone.
        val usePose = spec != null && spec.poseMotion
        val handle = if (spec != null && !usePose) {
            remember(spec) { GlassSelectorHandle(GlassSelectorController(spec)) }
        } else {
            null
        }
        val poseHandle = if (spec != null && usePose) {
            remember(spec) { GlassPoseHandle(GlassPoseController()) }
        } else {
            null
        }
        val anyHandle = handle != null || poseHandle != null
        val barCache = handle?.barCache ?: poseHandle?.barCache
        val selectorBar = if (anyHandle && spec != null) {
            glassSelectorBarOf(
                density, barWidthPx, barHeightPx, style, itemCount, 0f, spec,
                cache = barCache,
            )
        } else {
            null
        }
        val itemInk = remember(itemCount) { GlassItemBounds(itemCount) }
        // One layout snapshot serves the body's admission, both paint variants and the semantics.
        val formationNow = poseHandle?.formation ?: handle?.formation ?: 0f
        val emphasisNow = 1f + (style.selectedScale - 1f) * formationNow
        // The ink's own vertical half extent, measured from the row the body has to make room
        // for. Horizontal span alone does not prove a label fits inside a rounded end.
        val inkHalfHeightPx = with(density) {
            (barHeightDp / 2f - style.pillInset).toPx().coerceAtLeast(0f) * INK_VERTICAL_SHARE
        } * emphasisNow
        if (anyHandle && selectorBar != null) {
            itemInk.refresh(selectorBar, selected, emphasisNow)
            if (handle != null && !handle.controller.started) {
                handle.controller.attach(selectorBar, itemInk.bounds, itemInk.centres, inkHalfHeightPx)
                handle.controller.snapToRest(selected)
                handle.publish()
            }
            if (poseHandle != null && !poseHandle.controller.started) {
                poseHandle.controller.attach(selectorBar, itemInk.bounds, itemInk.centres, inkHalfHeightPx)
                poseHandle.controller.snapToRest(selected)
                poseHandle.publish()
            }
        }
        val held = poseHandle?.held ?: handle?.held ?: v2Held

        fun settleTo(target: Float, initialVelocity: Float, spec: AnimationSpec<Float>) {
            motion?.cancel()
            settleTarget = target
            motion = scope.launch {
                animate(
                    initialValue = offsetX,
                    targetValue = target,
                    initialVelocity = initialVelocity,
                    animationSpec = spec,
                ) { value, velocity ->
                    offsetX = value
                    settleVelocity = velocity
                }
                settleVelocity = 0f
            }
        }

        // Only follow the selection when the finger is not the one moving it. The retarget is
        // keyed on the geometry as well as the selection: the same selected index in a resized
        // bar is a **different pixel anchor**, and keeping the old target because the index did
        // not change is what left the body on the previous layout's centre.
        if (poseHandle != null) {
            LaunchedEffect(poseHandle, selected, geometry) {
                poseHandle.controller.retarget(selected)
                poseHandle.wake()
            }
            LaunchedEffect(poseHandle, geometry, motionEnabled) { poseHandle.wake() }
            GlassPoseFrameLoop(
                handle = poseHandle,
                barProvider = {
                    glassSelectorBarOf(
                        density, barWidthPx, barHeightPx, style, itemCount, poseHandle.formation,
                        spec!!, cache = poseHandle.barCache,
                    )
                },
                itemBounds = itemInk.bounds,
                itemCentres = itemInk.centres,
                inkHalfHeight = inkHalfHeightPx,
                motionEnabled = motionEnabled,
            )
        }
        if (handle != null) {
            LaunchedEffect(handle, selected, geometry) {
                handle.controller.retarget(selected)
                handle.wake()
            }
            // A layout or motion change no longer restarts the loop - the clock is retained with
            // the handle - so it has to wake it instead.
            LaunchedEffect(handle, geometry, motionEnabled) { handle.wake() }
            GlassSelectorFrameLoop(
                handle = handle,
                barProvider = {
                    glassSelectorBarOf(
                        density, barWidthPx, barHeightPx, style, itemCount, handle.formation, spec!!,
                        cache = handle.barCache,
                    )
                },
                itemBounds = itemInk.bounds,
                itemCentres = itemInk.centres,
                inkHalfHeight = inkHalfHeightPx,
                motionEnabled = motionEnabled,
            )
        }

        LaunchedEffect(restingAt, motionEnabled) {
            if (anyHandle) return@LaunchedEffect
            if (held || dragging || dragIndex >= 0) return@LaunchedEffect
            // A release has already aimed here and is carrying the fling velocity with it.
            if (settleTarget == restingAt) return@LaunchedEffect
            // Do not launch on the very first composition either: the lens is already at rest there.
            if (settleTarget.isNaN() && offsetX == restingAt) { settleTarget = restingAt; return@LaunchedEffect }
            if (!motionEnabled) {
                motion?.cancel()
                settleTarget = restingAt
                offsetX = restingAt
            } else {
                // From the spring's running velocity: a selection that changes while the lens is still
                // settling continues its motion rather than restarting from rest (Phase 2 audit).
                settleTo(restingAt, settleVelocity, style.settle)
            }
        }

        // Inset to lens and back, size and material on one spring so the rim cannot arrive
        // after the shape.
        val v2Lift by animateFloatAsState(
            targetValue = if (v2Held) 1f else 0f,
            animationSpec = when {
                !motionEnabled -> snap()
                held -> style.form
                else -> style.subside
            },
            label = "glass_tab_bar_lift",
        )
        // V3 drives formation from the controller, which is what makes a quick tap never start
        // the protruding held lens: only an eligible press that outlives the hold threshold, or
        // a recognized drag, forms it.
        val lift = poseHandle?.formation ?: handle?.formation ?: v2Lift
        val pillHeight: Dp = barHeightDp - style.pillInset * 2
        val lensHeight: Dp = barHeightDp + style.lensOverflow * 2
        val pillWidthDp: Dp = if (style.pillWidth.isSpecified) maxOf(style.pillWidth, slotWidthDp) else slotWidthDp
        val selectorWidth: Dp = lerp(pillWidthDp, pillWidthDp + style.lensExtraWidth, lift)
        val selectorHeight: Dp = lerp(pillHeight, lensHeight, lift)
        val selectorWidthPx = with(density) { selectorWidth.toPx() }
        val selectorHeightPx = with(density) { selectorHeight.toPx() }
        val selectorRisePx = with(density) { style.lensRise.toPx() } * lift
        val selectorStyle = lerpGlassStyle(style.pill, style.lens, lift)

        // The V3 body owns its own centre; offsetX follows it so semantics, the press glow and
        // the diagnostics all read one number.
        val selectorX = poseHandle?.centreX ?: handle?.centreX ?: offsetX

        fun selectorLeft() = selectorX - selectorWidthPx / 2f
        fun selectorTop() = (barHeightPx - selectorHeightPx) / 2f - selectorRisePx
        // The gesture coroutine is not restarted by recomposition, so it reads the capsule's box
        // through this rather than through values captured when the coroutine started.
        val selectorBox = rememberUpdatedState(
            Rect(selectorLeft(), selectorTop(), selectorLeft() + selectorWidthPx, selectorTop() + selectorHeightPx),
        )

        // Apple's "gel-like flexibility as it moves in tandem with your interaction": a few
        // percent along the travel at speed, lagged by its own spring so the deformation trails
        // the movement. An unlagged stretch reads as a rendering artefact.
        val velocityNow = if (dragging) liveVelocity else settleVelocity
        val gelTarget = if (motionEnabled) {
            style.gel * (abs(velocityNow) / gelReference).coerceAtMost(1f)
        } else {
            0f
        }
        val gel by animateFloatAsState(
            // V3 has no global gel: the body's own length, radius and end asymmetry carry the
            // deformation, so nothing rasterised is ever scaled (V3-MODEL section 3's identity
            // invariant). The capsule path keeps the measured stretch it shipped with.
            targetValue = if (anyHandle) 0f else gelTarget,
            animationSpec = style.gelSpring,
            label = "glass_tab_bar_gel",
        )

        // Clamped to the lens: a finger that grabbed away from it still lights the edge it is
        // pulling on, instead of aiming the glow off-panel where it simply vanishes.
        //
        // This is the **capsule** selector's box. The V3 node is a different node at a different
        // origin and a different size, and `uTouch` warps the sampled source, so feeding it a
        // point measured against this box is an optical defect and not merely a misplaced glow.
        // V3 therefore stores the pointer in the stable bar frame and resolves it against the
        // node origin the frame is actually using - see GlassSelectorHandle.resolvePress.
        fun localPress(x: Float, y: Float): Offset {
            val box = selectorBox.value
            return Offset((x - box.left).coerceIn(0f, box.width), (y - box.top).coerceIn(0f, box.height))
        }

        /** One pointer entry point for both paths, each in its own declared frame. */
        fun pressAt(x: Float, y: Float) {
            when {
                poseHandle != null -> {
                    poseHandle.barPointerX = x
                    poseHandle.barPointerY = y
                    poseHandle.resolvePress()
                }
                handle != null -> {
                    handle.barPointerX = x
                    handle.barPointerY = y
                    handle.resolvePress()
                }
                else -> press.press(localPress(x, y))
            }
        }

        fun releasePress() {
            handle?.barPointerX = Float.NaN
            handle?.barPointerY = Float.NaN
            poseHandle?.barPointerX = Float.NaN
            poseHandle?.barPointerY = Float.NaN
            press.release()
        }

        // The gesture lives on an ancestor of both the lens and the items, not on a sibling
        // between them. Compose hit-tests overlapping siblings from the top down and stops at
        // the first that takes the touch, so a gesture node under the item buttons would never
        // see a finger at all. An ancestor is on every hit path, and it is offered the main
        // pass after its children have had theirs — exactly when a drag should be allowed to
        // take a gesture off a button.
        //
        // Touch-down is already a press: the lens forms and goes to the tab under the finger.
        // Nothing is *claimed* until the pointer crosses the horizontal slop, so a tap still
        // reaches the item beneath and stays a tap.
        // The latest selection, for a gesture that outlives the composition it started in.
        val currentSelected = rememberUpdatedState(selected)

        // Every way a gesture ends short of a committed drag: the finger lifts on a tap (the item's own
        // clickable is the one owner of that selection), another node takes the pointer, a vertical
        // scroll wins, a second finger replaces the first, or the node goes away. Nothing is selected
        // here; the preview ends and the lens settles from wherever it is to the selection the host
        // actually holds, carrying only the velocity a spring already has (a drag has none to carry).
        //
        // [tapped] is the tab a finger lifted on without dragging. The item's clickable selects it
        // after this runs, but the selection the host holds is still the old one here; aiming the
        // body at it would spend the frames until the new selection lands travelling backwards.
        // The tap's tab is kept as the preview instead, and the grace below recovers if the host
        // declines it.
        fun releaseToSelection(active: Boolean, tapped: Int = -1) {
            v2Held = false
            dragging = false
            liveVelocity = 0f
            releasePress()
            dragIndex = tapped
            val aim = if (tapped >= 0) tapped else currentSelected.value
            if (poseHandle != null) {
                // The same continuous recovery toward the latest legitimate selection, with no
                // new commit: a cancelled gesture is not a successful release.
                poseHandle.controller.cancel(aim)
                poseHandle.wake()
                return
            }
            if (handle != null) {
                // The body recovers its geometry continuously from wherever it is, under the
                // release envelope, and commits nothing.
                handle.controller.cancel(aim)
                handle.wake()
                return
            }
            if (!active) {
                // The node is gone or restarting: launch nothing, and leave the target unset so the
                // effect that follows the selection re-aims the lens if it comes back.
                settleTarget = Float.NaN
                return
            }
            val target = geometry.centreOf(aim)
            if (!motionEnabled) {
                motion?.cancel()
                settleTarget = target
                offsetX = target
                settleVelocity = 0f
            } else if (settleTarget != target || motion?.isActive != true) {
                settleTo(target, settleVelocity, style.settle)
            }
        }

        // Hold the preview until the real selection catches up, or the tab under the finger
        // would blink back to the old one for the frame before navigation lands. A host may also
        // decline the tab a release asked for (a tab behind sign-in, say): then the selection
        // never arrives, so once the finger is gone and the host has had a beat to answer, the
        // preview ends and the selector returns to the selection the host actually holds.
        LaunchedEffect(selected, dragIndex, gestureDown) {
            if (dragIndex >= 0 && dragIndex == selected) {
                dragIndex = -1
                return@LaunchedEffect
            }
            if (gestureDown || dragIndex < 0) return@LaunchedEffect
            delay(SELECTION_GRACE_MILLIS)
            dragIndex = -1
            when {
                poseHandle != null -> { poseHandle.controller.cancel(selected); poseHandle.wake() }
                handle != null -> { handle.controller.cancel(selected); handle.wake() }
                !motionEnabled -> {
                    motion?.cancel()
                    settleTarget = restingAt
                    offsetX = restingAt
                    settleVelocity = 0f
                }
                else -> settleTo(restingAt, settleVelocity, style.settle)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    testTag = GlassTabBarSemantics.TAG
                    this[GlassTabBarSemantics.SelectorX] = selectorX
                    this[GlassTabBarSemantics.Held] = held
                    this[GlassTabBarSemantics.Formation] = lift
                    this[GlassTabBarSemantics.BodyWidth] = poseHandle?.let { it.right - it.left }
                        ?: handle?.body?.let { it.length + 2f * it.radius } ?: selectorWidthPx
                    // The pose body's end asymmetry is its taper, not a radius difference;
                    // it is reported through the same key so one test covers both paths.
                    this[GlassTabBarSemantics.BodySkew] =
                        poseHandle?.taper ?: handle?.body?.skew ?: 0f
                    this[GlassTabBarSemantics.Protrusion] =
                        poseHandle?.controller?.protrusion() ?: handle?.controller?.protrusion() ?: 0f
                    this[GlassTabBarSemantics.Dragging] = dragging
                    this[GlassTabBarSemantics.DragIndex] = dragIndex
                    if (handle != null) {
                        this[GlassTabBarSemantics.NodeLeft] = handle.nodeLeft().toFloat()
                        this[GlassTabBarSemantics.NodeTop] = -handle.overflowTop
                        this[GlassTabBarSemantics.TouchLocalX] = press.localPosition.x
                        this[GlassTabBarSemantics.TouchLocalY] = press.localPosition.y
                        this[GlassTabBarSemantics.SelectorValid] = handle.controller.selectorValid
                    }
                    if (poseHandle != null) {
                        this[GlassTabBarSemantics.NodeLeft] = poseHandle.nodeLeft().toFloat()
                        this[GlassTabBarSemantics.NodeTop] = poseHandle.nodeTop().toFloat()
                        this[GlassTabBarSemantics.TouchLocalX] = press.localPosition.x
                        this[GlassTabBarSemantics.TouchLocalY] = press.localPosition.y
                        this[GlassTabBarSemantics.SelectorValid] = !poseHandle.controller.solverFailed
                    }
                }
                .pointerInput(geometry, motionEnabled, handle, poseHandle, style, flingVelocity) {
                    // Read here, outside the restricted gesture scope: whether this pointer-input job is still
                    // alive when a gesture ends decides whether anything may be launched from its release.
                    val gestureJob = currentCoroutineContext()[Job]
                    awaitEachGesture {
                        // The item under the finger consumes the down, so this must not
                        // require an unconsumed one.
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)

                        var lastIndex = geometry.nearestIndex(down.position.x)
                        var committed = false
                        var tapped = -1
                        gestureDown = true
                        try {
                            motion?.cancel()
                            dragIndex = lastIndex
                            pressAt(down.position.x, down.position.y)
                            if (poseHandle != null) {
                                // Down is a press indication and a travel target. It is not the
                                // held material: that needs the hold threshold or a real drag.
                                poseHandle.controller.pointerDown(
                                    x = down.position.x,
                                    y = down.position.y,
                                    eventSeconds = down.uptimeMillis / 1000.0,
                                    eligible = motionEnabled,
                                )
                                poseHandle.controller.retarget(lastIndex)
                                poseHandle.wake()
                            } else if (handle != null) {
                                handle.controller.pointerDown(
                                    x = down.position.x,
                                    y = down.position.y,
                                    eventSeconds = down.uptimeMillis / 1000.0,
                                    eligible = motionEnabled,
                                )
                                handle.controller.retarget(lastIndex)
                                handle.wake()
                            } else {
                                v2Held = true
                            }
                            if (anyHandle) {
                                // no capsule spring to aim; the controller owns the centre
                            } else if (motionEnabled) {
                                // Carry whatever velocity the lens still has: a press during a release
                                // retargets the spring in place instead of snapping its velocity to zero.
                                settleTo(geometry.centreOf(lastIndex), settleVelocity, style.arrive)
                            } else {
                                settleTarget = geometry.centreOf(lastIndex)
                                offsetX = settleTarget
                                settleVelocity = 0f
                            }

                            if (!motionEnabled) {
                                // No lens to drag: the item's clickable owns the tap; this only waits for
                                // the finger to leave, however it leaves.
                                awaitHorizontalTouchSlopOrCancellation(down.id) { _, _ -> }
                                if (liftedWithoutDrag(down.id)) tapped = lastIndex
                                return@awaitEachGesture
                            }
                            val grabbed = if (poseHandle != null) {
                                awaitPoseTouchSlopOrCancellation(
                                    down, (poseHandle.controller.spec.holdThresholdSeconds * 1000).toLong(),
                                    onMove = { change ->
                                        poseHandle.controller.pointerMove(change.position.x, change.position.y, change.uptimeMillis / 1000.0)
                                        poseHandle.wake()
                                        pressAt(change.position.x, change.position.y)
                                    },
                                )
                            } else {
                                awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                            }
                            if (grabbed == null) {
                                // Lifted without dragging (a tap: the item's clickable selects, once),
                                // or taken by another node / a vertical scroll (a cancel: nobody
                                // selects). Both end in releaseToSelection below.
                                if (liftedWithoutDrag(down.id)) tapped = lastIndex
                                return@awaitEachGesture
                            }

                            motion?.cancel()
                            dragging = true
                            handle?.controller?.beginDrag(down.position.x)
                            handle?.wake()
                            poseHandle?.controller?.beginDrag(down.position.x, down.position.y)
                            poseHandle?.wake()

                            // The lens keeps whatever offset from the finger it had when the drag
                            // began, capped at half its own width: grabbing the lens tracks
                            // exactly, grabbing anywhere else brings it to the finger. Measured
                            // from the DOWN, not from where slop was crossed — slop can be tens
                            // of dp, and a drag that loses it cannot reach the outermost tab.
                            val halfSelector = selectorBox.value.width / 2f
                            val grabOffset = (offsetX - down.position.x).coerceIn(-halfSelector, halfSelector)

                            fun moveTo(x: Float, y: Float) {
                                // Navigation intent, kept separate from the visible geometry:
                                // the body may protrude, taper or rise while the tab a drag is
                                // previewing stays inside the item sequence.
                                val centre = when {
                                    // Intent is the finger, bounded only by the item sequence.
                                    // Section 5: never clamp it by the body's half width, and do
                                    // not make it follow a silhouette that is free to protrude,
                                    // taper or rise away from the tab being chosen.
                                    poseHandle != null ->
                                        x.coerceIn(geometry.firstCentre, geometry.lastCentre)
                                    handle != null -> handle.controller.grabCentreFor(x)
                                    else -> (x + grabOffset).coerceIn(geometry.firstCentre, geometry.lastCentre)
                                }
                                if (!anyHandle) offsetX = centre
                                pressAt(x, y)
                                val index = geometry.nearestIndex(centre)
                                if (index != lastIndex) {
                                    lastIndex = index
                                    dragIndex = index
                                }
                            }

                            tracker.addPosition(grabbed.uptimeMillis, grabbed.position)
                            handle?.controller?.pointerMove(grabbed.position.x, grabbed.uptimeMillis / 1000.0)
                            poseHandle?.controller?.pointerMove(
                                grabbed.position.x, grabbed.position.y, grabbed.uptimeMillis / 1000.0,
                            )
                            moveTo(grabbed.position.x, grabbed.position.y)

                            // Only the pointer that crossed slop drives the lens. If Compose hands the
                            // drag to another finger, that interaction is cancelled rather than followed
                            // with a tracker full of positions from two fingers (the simpler policy).
                            var handoff = false
                            val onDrag: (PointerInputChange) -> Unit = move@ { change ->
                                if (change.id != grabbed.id) {
                                    handoff = true
                                    return@move
                                }
                                tracker.addPosition(change.uptimeMillis, change.position)
                                liveVelocity = tracker.calculateVelocity().x
                                handle?.controller?.pointerMove(change.position.x, change.uptimeMillis / 1000.0)
                                handle?.wake()
                                poseHandle?.controller?.pointerMove(
                                    change.position.x, change.position.y, change.uptimeMillis / 1000.0,
                                )
                                poseHandle?.wake()
                                moveTo(change.position.x, change.position.y)
                                change.consume()
                            }
                            val completed = if (poseHandle != null) drag(grabbed.id, onDrag)
                                else horizontalDrag(grabbed.id, onDrag)

                            // The terminal sample of the active pointer: its lift. It is part of the
                            // gesture the tracker judges: without it a finger that stops, holds and then
                            // lifts is released with the velocity it had before it stopped (the tracker
                            // only assumes a stop between two samples 40 ms apart) and the lens flings
                            // to the next tab from a standstill (Phase 2 audit trace 1).
                            val terminal = currentEvent.changes.firstOrNull { it.id == grabbed.id }
                            val terminalUp = terminal != null && !terminal.pressed
                            if (terminalUp) tracker.addPosition(terminal!!.uptimeMillis, terminal.position)
                            val released = tracker.calculateVelocity().x
                            dragging = false
                            liveVelocity = 0f

                            val projected = decideDragEnd(completed, terminalUp, handoff, lastIndex, released, flingVelocity, geometry)
                                ?: return@awaitEachGesture   // cancelled: no selection, releaseToSelection below
                            committed = true
                            if (poseHandle != null) {
                                // Every pose coordinate and velocity is preserved; only the
                                // equilibria move to the resting body, so translation and shape
                                // recover together from the shape the body actually has.
                                poseHandle.controller.pointerUp(projected)
                                poseHandle.wake()
                            } else if (handle != null) {
                                // The body keeps its own centre velocity, which is not the
                                // finger's whenever it is also changing size, and recovers its
                                // shape under the release envelope.
                                handle.controller.pointerUp(projected)
                                handle.wake()
                            } else {
                                // Settle first, so the effect watching the selection sees a spring
                                // already aimed at the right place and leaves its velocity alone.
                                settleTo(geometry.centreOf(projected), released, style.settle)
                            }
                            dragIndex = projected
                            select(projected)
                        } finally {
                            // However the gesture ends — lift, cancel, or the node going away —
                            // the finger is no longer on the glass.
                            gestureDown = false
                            if (committed) {
                                v2Held = false
                                dragging = false
                                liveVelocity = 0f
                                releasePress()
                            } else {
                                releaseToSelection(active = gestureJob?.isActive == true, tapped = tapped)
                            }
                        }
                    }
                },
        ) {
            // The bar: plate and items, recorded so the lens can look through them. The lens is
            // a sibling of this box, not a child, or it would sample itself. While held the
            // whole bar grows about its centre and its material lifts (1.05 and +16/255 on the
            // iOS 27 reference), so the recording is laid out with a margin to grow into: the
            // plate is laid out at the grown size, so its backdrop stays 1:1, and the items
            // scale with it.
            val barScale = 1f + (style.heldScale - 1f) * lift
            // The lens's outline, in the plate's own coordinates: the plate is laid out at the
            // grown size and centred, so the outer origin sits (1 - barScale) / 2 inside it.
            val barFuse = if (!anyHandle && lift > 0.01f && style.lensFuse > 0.dp) {
                val lw = selectorWidthPx * (1f + gel)
                val lh = selectorHeightPx * (1f - gel)
                val cx = selectorLeft() + selectorWidthPx / 2f - barWidthPx * (1f - barScale) / 2f
                val cy = selectorTop() + selectorHeightPx / 2f - barHeightPx * (1f - barScale) / 2f
                with(density) {
                    GlassFuse(
                        bounds = DpRect(
                            left = (cx - lw / 2f).toDp(),
                            top = (cy - lh / 2f).toDp(),
                            right = (cx + lw / 2f).toDp(),
                            bottom = (cy + lh / 2f).toDp(),
                        ),
                        cornerRadius = (minOf(lw, lh) / 2f).toDp(),
                        smoothing = style.lensFuse * lift,
                    )
                }
            } else {
                null
            }
            val margin = (style.heldScale - 1f).coerceAtLeast(0f)
            val barStyle = if (style.heldLift != 0f) {
                style.bar.copy(tintLift = style.bar.tintLift + style.heldLift * lift)
            } else {
                style.bar
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layout { measurable, constraints ->
                        val w = constraints.maxWidth
                        val h = constraints.maxHeight
                        val gw = (w * (1f + margin)).roundToInt()
                        val gh = (h * (1f + margin)).roundToInt()
                        val placeable = measurable.measure(Constraints.fixed(gw, gh))
                        layout(w, h) { placeable.place((w - gw) / 2, (h - gh) / 2) }
                    }
                    .then(if (!anyHandle) Modifier.liquidGlassSource(barState) else Modifier),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxSize(fraction = barScale / (1f + margin))
                        // V3 records the bar's **material alone** as what the selector looks
                        // through: no icon and no label is in that source, so no filtered copy of
                        // the ordinary ink can survive underneath the selected ink
                        // (V3-MODEL section 8.2).
                        .then(if (anyHandle) Modifier.liquidGlassSource(barState) else Modifier)
                        .liquidGlass(
                            state = state,
                            shape = barShape,
                            style = barStyle,
                            light = style.light,
                            // The bar does not glow or bounce under the finger on the reference;
                            // it grows and lifts, which is done above. The lens is the response.
                            interaction = null,
                            materialize = materialize,
                            // The lens is proud of the bar, and on the reference the bar's own
                            // outline does not simply end under it: it bows out to meet it, one
                            // liquid silhouette with no crease (measured model, section 2d).
                            fuse = barFuse,
                        ),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxSize(fraction = 1f / (1f + margin))
                        .graphicsLayer {
                            scaleX = barScale
                            scaleY = barScale
                        },
                ) {
                    ItemRow(
                        count = itemCount,
                        inset = style.contentPadding,
                        selectedFor = { index -> !lensCarriesInk && index == activeIndex },
                        onClick = { index -> select(index) },
                        semanticSelected = selected,
                        item = item,
                        emphasisIndex = activeIndex,
                        emphasis = 1f + (style.selectedScale - 1f) * lift,
                        onMeasured = if (anyHandle) { index, width -> itemInk.widths[index] = width } else null,
                    )
                }
            }

            if (anyHandle) {
                // The V3 selector occupies a **stable** node: the bar plus the held overflow,
                // never resized and never re-placed. The body moves and deforms inside it as
                // continuous floating-point geometry, so the moving optical origin is never
                // rounded to a whole pixel and the raster allocation never churns.
                // **One** rounded overflow. The node was placed at `-round(overflow)`, its native
                // ink at `+round(overflow)`, and the body translated by the unrounded value: at a
                // fractional density that put the contour in a different vertical frame from the
                // aperture and the ink it is supposed to be registered with.
                // The style's `lensOverflow` is a 4 dp constant that predates the pose model and
                // has no relation to how far the held body actually grows. Section 5.1's rule -
                // a finite allocation is a safety limit, not a material wall - applies to this
                // axis exactly as it does to the width, so the pose path sizes it from the pose.
                val overflowPx = remember(selectorBar, spec, style.lensOverflow, usePose) { max(
                    with(density) { style.lensOverflow.toPx() },
                    if (usePose) spec!!.maxAccommodationOverflow(selectorBar, poseHandle!!.controller.spec) else 0f,
                ) }
                val overflowTopPx = overflowPx.roundToInt()
                // A node just big enough for the widest body this style allows, plus the held
                // overflow. It is a **stable** size - never reallocated - and it follows the body
                // by whole pixels while the body's own centre stays continuous inside it, so the
                // moving optical origin is never rounded and the raster never churns. Sizing it
                // to the whole bar would be simpler and would put five times the pixels through
                // three shader passes every frame for no gain.
                // A finite allocation is a safety limit, not a material wall: the node is sized
                // for the widest body this style can produce so the body is never shrunk to hide
                // missing pixels (parity brief section 5.1).
                val widest = remember(selectorBar, spec, barWidthPx) {
                    handle?.controller?.maxBodyWidth()
                        ?: spec!!.maxAccommodationWidth(selectorBar, barWidthPx, poseHandle!!.controller.spec)
                }
                // The horizontal pad is the rim's optical margin - it was always the style's own
                // overflow - and must not follow the vertical excursion the pose path now sizes
                // the node's HEIGHT from. Letting it do so added about 200 px of node width for
                // nothing: `widest` already contains every horizontal term the body has.
                val sidePx = with(density) { style.lensOverflow.toPx() }
                val envelopePx = min(
                    barWidthPx,
                    widest.coerceAtLeast(2f * (selectorBar?.baseHalfWidth ?: 0f)) +
                        4f * sidePx + 8f,
                )
                val envelopeDp = with(density) { envelopePx.toDp() }
                val nodeHeightPx = (barHeightPx + 2f * overflowPx).roundToInt().toFloat()
                val nodeHeightDp = with(density) { nodeHeightPx.toDp() }
                // The frame this displayed frame uses, published to the handle so the body, the
                // aperture, the native ink and the touch point all read the same origin instead
                // of each resolving a moving centre for itself. `nodeLeft()` is computed once,
                // from the body snapshot this composition is drawing.
                handle?.envelopeWidth = envelopePx
                handle?.nodeHeight = nodeHeightPx
                handle?.overflowTop = overflowTopPx.toFloat()
                handle?.barWidth = barWidthPx
                handle?.pressSource = press
                val poseFrameChanged = poseHandle != null &&
                    (poseHandle.envelopeWidth != envelopePx || poseHandle.nodeHeight != nodeHeightPx)
                poseHandle?.envelopeWidth = envelopePx
                poseHandle?.nodeHeight = nodeHeightPx
                poseHandle?.overflowTop = overflowTopPx.toFloat()
                poseHandle?.barWidth = barWidthPx
                poseHandle?.pressSource = press
                // The first composition seeds the body before this block has told the handle how
                // big its node is, so that first snapshot carries a translation built from zeros.
                // Republish now that the frame is known, or one frame renders from it.
                if (poseHandle != null && (poseFrameChanged || poseHandle.render == null)) poseHandle.publish()
                val nodeLeftPx = handle?.nodeLeft() ?: poseHandle!!.nodeLeft()
                val nodeTopPx = poseHandle?.nodeTop() ?: -overflowTopPx
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset { IntOffset(nodeLeftPx, nodeTopPx) }
                        // `size` alone is COERCED to the parent's constraints, and the parent is
                        // the bar - so the node was silently measured at the bar's height while
                        // the body's uniforms were computed for the taller frame it asked for.
                        // With a 4 dp style overflow that was a 4 px registration error nothing
                        // caught; sized from the pose it became 26 px, and the held body was
                        // drawn half a bar too high with its own item's ordinary ink left showing
                        // underneath. Measure unbounded first, exactly as the non-pose selector
                        // below already does, then take the size that was asked for.
                        .wrapContentSize(Alignment.TopStart, unbounded = true)
                        .size(width = envelopeDp, height = nodeHeightDp)
                        .liquidGlassCore(
                            state = state,
                            shape = selectorShape,
                            style = selectorStyle,
                            light = style.light,
                            interaction = if (motionEnabled) {
                                style.lensInteraction
                            } else {
                                GlassInteraction.ReducedMotion
                            },
                            materialize = materialize,
                            pressSource = press,
                            refractContent = lensCarriesInk,
                            through = barState,
                            // The bar's outline is not enlarged to hide the body: containment is
                            // what keeps a tap inside it, and a hold is allowed out by its own
                            // declared envelope (V3-MODEL section 12).
                            fuse = null,
                            lensFormation = 0f,
                            body = handle?.body?.movedInto(nodeLeftPx.toFloat(), overflowTopPx.toFloat()),
                            poseBody = poseHandle?.render,
                            endpointComposite = lensCarriesInk,
                        ),
                ) {
                    if (lensCarriesInk) {
                        // The selected variant of the same one layout, at the bar's own size and
                        // origin and at native resolution. It is the C1 ink of the endpoint
                        // compositor; nothing here is scaled by the body's deformation.
                        Box(
                            // Measured at the **bar's** size whatever the node's constraints are,
                            // and placed at the bar's origin: the selected row is one layout of
                            // the same items at the same coordinates as the ordinary row, and the
                            // node only decides how much of it the aperture can show.
                            modifier = Modifier
                                .layout { measurable, constraints ->
                                    val placeable = measurable.measure(
                                        Constraints.fixed(
                                            barWidthPx.roundToInt(),
                                            barHeightPx.roundToInt(),
                                        ),
                                    )
                                    layout(constraints.maxWidth, constraints.maxHeight) {
                                        // The same rounded origin the node and the body use, and
                                        // at scale 1: native ink never borrows the material's
                                        // render scale.
                                        placeable.place(-nodeLeftPx, -nodeTopPx)
                                    }
                                }
                                .graphicsLayer {
                                    // This layer has the selector node's size, but both ink
                                    // endpoints scale about the BAR centre. A default .5 pivot
                                    // shifted the selected source by (scale-1)*(barCentre-nodeCentre).
                                    transformOrigin = TransformOrigin(
                                        (barWidthPx / 2f - nodeLeftPx) / envelopePx,
                                        (barHeightPx / 2f - nodeTopPx) / nodeHeightPx,
                                    )
                                    scaleX = barScale
                                    scaleY = barScale
                                },
                        ) {
                            ItemRow(
                                count = itemCount,
                                inset = style.contentPadding,
                                selectedFor = { index -> !usePose || index == activeIndex },
                                onClick = null,
                                item = item,
                                emphasisIndex = activeIndex,
                                emphasis = 1f + (style.selectedScale - 1f) * lift,
                            )
                        }
                    }
                }
                return@BoxWithConstraints
            }

            // The selector. Positioned explicitly with unbounded constraints: it is taller than
            // the bar while held, and both `size` (coerced to the parent) and `requiredSize`
            // (centred within it) would place it wrongly.
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset { IntOffset(selectorLeft().roundToInt(), selectorTop().roundToInt()) }
                    .wrapContentSize(Alignment.TopStart, unbounded = true),
            ) {
                Box(
                    modifier = Modifier
                        .size(width = selectorWidth, height = selectorHeight)
                        .graphicsLayer {
                            // Volume-preserving: what it gains along the travel it loses across.
                            scaleX = 1f + gel
                            scaleY = 1f - gel
                        }
                        .liquidGlass(
                            state = state,
                            shape = selectorShape,
                            style = selectorStyle,
                            light = style.light,
                            interaction = if (motionEnabled) style.lensInteraction else GlassInteraction.ReducedMotion,
                            pressSource = press,
                            refractContent = lensCarriesInk,
                            through = barState,
                        ),
                ) {
                    if (lensCarriesInk) {
                        // The same row again, selected, laid out at the bar's size and pinned to
                        // the bar's origin so it does not move with the lens. The lens shows
                        // whatever part of it it happens to be over, refracted.
                        Box(
                            modifier = Modifier.layout { measurable, constraints ->
                                val placeable = measurable.measure(
                                    Constraints.fixed(barWidthPx.roundToInt(), barHeightPx.roundToInt()),
                                )
                                layout(constraints.maxWidth, constraints.maxHeight) {
                                    placeable.place(-selectorLeft().roundToInt(), -selectorTop().roundToInt())
                                }
                            },
                        ) {
                            // Grown with the bar, about the bar's centre, and the tab under the
                            // lens grown again about its own centre, exactly as the copy on the
                            // bar is, so the two copies stay on top of each other.
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = barScale
                                        scaleY = barScale
                                    },
                            ) {
                                ItemRow(
                                    count = itemCount,
                                    inset = style.contentPadding,
                                    selectedFor = { index -> !usePose || index == activeIndex },
                                    onClick = null,
                                    item = item,
                                    emphasisIndex = activeIndex,
                                    emphasis = 1f + (style.selectedScale - 1f) * lift,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemRow(
    count: Int,
    inset: Dp,
    selectedFor: (Int) -> Boolean,
    onClick: ((Int) -> Unit)?,
    item: @Composable (index: Int, selected: Boolean) -> Unit,
    /** The tab under the lens, grown by [emphasis] about its own centre; the rest stay at 1. */
    emphasisIndex: Int = -1,
    emphasis: Float = 1f,
    /**
     * Reports each item's measured icon-and-label width, so the V3 body can make room for the
     * content it is over from layout rather than from a guess. Items are equal slots and their
     * content is centred, so a width plus the slot's centre is the whole bound.
     */
    onMeasured: ((index: Int, width: Float) -> Unit)? = null,
    /** The tab the host has selected, for accessibility; only the interactive row reports it. */
    semanticSelected: Int = -1,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            // The interactive row is the one accessibility sees: a group of tabs. Every other copy
            // (the lens's refracted row, the V3 selected ink) is decoration and is hidden, or a
            // screen reader would read each label two or three times.
            .then(if (onClick != null) Modifier.selectableGroup() else Modifier.clearAndSetSemantics {})
            .padding(horizontal = inset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(
                        if (onClick != null) {
                            Modifier.selectable(
                                selected = index == semanticSelected,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Tab,
                            ) { onClick(index) }
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                // Sprung per item, so the growth walks from tab to tab under a drag instead of
                // jumping; both copies of the row animate the same targets on the same clock.
                val itemScale by animateFloatAsState(
                    targetValue = if (index == emphasisIndex) emphasis else 1f,
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 900f),
                    label = "glass_tab_bar_item_scale",
                )
                val measured = if (onMeasured != null) {
                    Modifier.onGloballyPositioned { onMeasured(index, it.size.width.toFloat()) }
                } else {
                    Modifier
                }
                // One call site whatever the scale: a branch on it made two different call sites,
                // so the item's whole subtree, with its remembered state and running animations,
                // was discarded each time emphasis began and again when it settled to exactly 1.
                Box(
                    modifier = measured.graphicsLayer {
                        scaleX = itemScale
                        scaleY = itemScale
                    },
                ) {
                    item(index, selectedFor(index))
                }
            }
        }
    }
}

/**
 * The item layout a [GlassTabBar] uses, kept free of Compose so it can be tested on the JVM.
 * Items are equal slots across the bar inside a horizontal [inset]; the selector's centre is
 * clamped to the first and last slot centres.
 */
@Immutable
internal data class GlassTabBarGeometry(val barWidth: Float, val inset: Float, val count: Int) {
    // Never negative: a bar narrower than its two insets (an expand-in, a zero-width first
    // frame) would otherwise put firstCentre past lastCentre, and every coerceIn between them throws.
    val slotWidth: Float get() = ((barWidth - inset * 2f) / count).coerceAtLeast(0f)
    val firstCentre: Float get() = centreOf(0)
    val lastCentre: Float get() = centreOf(count - 1)

    fun centreOf(index: Int): Float = inset + (index + 0.5f) * slotWidth

    fun nearestIndex(x: Float): Int = ((x - inset) / slotWidth).toInt().coerceIn(0, count - 1)

    /** A flick carries to the next item even if the finger never got it there. */
    fun projected(lastIndex: Int, releasedVelocity: Float, flingVelocity: Float): Int = when {
        releasedVelocity > flingVelocity -> lastIndex + 1
        releasedVelocity < -flingVelocity -> lastIndex - 1
        else -> lastIndex
    }.coerceIn(0, count - 1)
}

/**
 * The pose path's slop wait: horizontal slop before the hold threshold, any direction after it,
 * and a vertical move before the hold leaves the pointer to a page gesture. Null on a lift or when
 * another node consumes the pointer.
 */
private suspend fun AwaitPointerEventScope.awaitPoseTouchSlopOrCancellation(
    down: PointerInputChange,
    holdMillis: Long,
    onMove: (PointerInputChange) -> Unit,
): PointerInputChange? {
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return null
        if (!change.pressed || change.isConsumed) return null
        onMove(change)
        val delta = change.position - down.position
        val held = change.uptimeMillis - down.uptimeMillis >= holdMillis
        val crossed = if (held) delta.getDistance() > viewConfiguration.touchSlop
            else kotlin.math.abs(delta.x) > viewConfiguration.touchSlop
        if (crossed) {
            change.consume()
            return change
        }
        // Before ownership matures, let a vertical page gesture take over as before.
        if (!held && kotlin.math.abs(delta.y) > viewConfiguration.touchSlop) return null
    }
}

/**
 * What a drag commits, from what the gesture actually delivered: the tab the released velocity projects
 * to, or nothing. Nothing is committed when [horizontalDrag] reported a cancellation (another node
 * consumed the pointer), when the pointer that drove the lens did not end with a lift ([terminalUp]
 * false: it vanished, or was still down while something else ended the drag), or when Compose handed
 * the drag to a second finger ([handoff]). The production handler and the replay diagnostics both call
 * this, so there is one decision, not two transcriptions of it.
 */
internal fun decideDragEnd(
    completed: Boolean,
    terminalUp: Boolean,
    handoff: Boolean,
    lastIndex: Int,
    releasedVelocity: Float,
    flingVelocity: Float,
    geometry: GlassTabBarGeometry,
): Int? = if (!completed || !terminalUp || handoff) null else geometry.projected(lastIndex, releasedVelocity, flingVelocity)

/** Selector state exposed through semantics, for the component's own tests; nothing reads it otherwise. */
internal object GlassTabBarSemantics {
    const val TAG = "GlassTabBarGestureNode"
    val SelectorX = SemanticsPropertyKey<Float>("GlassTabBarSelectorX")
    val Held = SemanticsPropertyKey<Boolean>("GlassTabBarHeld")
    val Dragging = SemanticsPropertyKey<Boolean>("GlassTabBarDragging")
    val DragIndex = SemanticsPropertyKey<Int>("GlassTabBarDragIndex")
    /** V3 only: how far the held lens has formed, the body's width and end asymmetry, in px. */
    val Formation = SemanticsPropertyKey<Float>("GlassTabBarFormation")
    val BodyWidth = SemanticsPropertyKey<Float>("GlassTabBarBodyWidth")
    val BodySkew = SemanticsPropertyKey<Float>("GlassTabBarBodySkew")
    /** How far the body's contour is outside the resting bar, in px; 0 during an ordinary tap. */
    val Protrusion = SemanticsPropertyKey<Float>("GlassTabBarProtrusion")

    /**
     * V3 only: the selector node's rounded left edge in bar px, and the touch point in that
     * node's own local px — the exact pair the body, the aperture and the native ink are drawn
     * with. `NodeLeft + TouchLocalX` must return the bar-local point the finger is at, which is
     * what makes the optical frame checkable from outside the library and on a device.
     */
    val NodeLeft = SemanticsPropertyKey<Float>("GlassTabBarNodeLeft")
    val NodeTop = SemanticsPropertyKey<Float>("GlassTabBarNodeTop")
    val TouchLocalX = SemanticsPropertyKey<Float>("GlassTabBarTouchLocalX")
    val TouchLocalY = SemanticsPropertyKey<Float>("GlassTabBarTouchLocalY")
    /** V3 only: false when no feasible body exists and the selector is suppressed. */
    val SelectorValid = SemanticsPropertyKey<Boolean>("GlassTabBarSelectorValid")
}

/**
 * Everything a [GlassTabBar] looks and moves like. [Dark] is measured; see
 * `docs/research/reference-measurements.md` for every number's source.
 */
@Immutable
data class GlassTabBarStyle(
    /** The bar's material. */
    val bar: GlassStyle = GlassStyle.DarkChrome,
    /** The indicator at rest: a flat dark inset, no optics. */
    val pill: GlassStyle = RestingInset,
    /** The indicator while held: clear glass with a thin lit edge and a strong rim. */
    val lens: GlassStyle = HeldLens,
    /** Straight above: the reference bar's top and bottom edges are lit identically, its sides not at all. */
    val light: GlassLight = GlassLight(0f, -1f),
    /** The bar's outline. A capsule, as the reference is. */
    val shape: Shape = RoundedCornerShape(percent = 50),
    /** The reference bar is 64pt tall. */
    val height: Dp = 64.dp,
    /** Horizontal inset of the item row inside the bar. */
    val contentPadding: Dp = 6.dp,
    /** Bar showing above and below the resting inset: about 3pt on a 64pt bar. */
    val pillInset: Dp = 3.dp,
    /** How far the held lens stands proud of the bar, above and below alike: 4.3pt measured. */
    val lensOverflow: Dp = 4.dp,
    /** How much wider than the resting inset the held lens is: 16pt on the iOS 26 reference, 21pt on iOS 27. */
    val lensExtraWidth: Dp = 16.dp,
    /** Vertical offset of the held lens. The native reference is centred; the earlier video read 5pt high. */
    val lensRise: Dp = 0.dp,
    /** How the lens responds under the finger. No scale — the reference does not bounce — and a faint centre glow. */
    val lensInteraction: GlassInteraction = GlassInteraction(pressScale = 1f, illumination = 0.5f, gel = false),
    /** The lens forms in about a tenth of a second: two frames at 30fps in the reference. */
    val form: AnimationSpec<Float> = spring(dampingRatio = 0.80f, stiffness = 1400f),
    /**
     * And subsides over about four tenths.
     *
     * Measured off the reference's own release (LOLL8185, the lens at rest on a tab, finger
     * lifted at frame 1944): the lens stands 13 px proud of the bar and reaches the flat inset
     * 30 frames later, 22 of them between nine tenths and one tenth of the travel, which is
     * 367 ms. At stiffness 600 the library did it in 83 ms, so the lens blinked out instead of
     * settling and the press read as never having happened. Stiffness 60 measures 383 ms on an
     * S24+ by the same nine-to-one measure.
     */
    val subside: AnimationSpec<Float> = spring(dampingRatio = 1f, stiffness = 60f),
    /** Touch-down sends the selector to the finger's tab: stiff, so it is there before the drag. */
    val arrive: AnimationSpec<Float> = spring(dampingRatio = 0.85f, stiffness = 1400f),
    /** Release settles it onto a tab carrying whatever velocity the finger left it with. */
    val settle: AnimationSpec<Float> = spring(dampingRatio = 0.72f, stiffness = 520f),
    /** Release speed past which the selector carries on to the next tab. */
    val flingVelocity: Dp = 420.dp,
    /**
     * Peak stretch along the travel, as a fraction, reached at [gelReference] per second.
     *
     * Measured on the iOS 27 recording, which is what set both numbers: the lens stands 13 px
     * proud of the bar at rest and 7 to 8 px proud above 300 pt a second, so it loses about 5%
     * of its height at that speed and gains it back when it stops. The reference was 1200 dp a
     * second before, four times too fast to show at any speed a finger actually drags at.
     */
    val gel: Float = 0.05f,
    val gelReference: Dp = 300.dp,
    val gelSpring: AnimationSpec<Float> = spring(dampingRatio = 0.6f, stiffness = 500f),
    /**
     * The resting inset's width; the wider of this and one item's slot is used. iOS 27 sizes
     * its indicator to the item's label plus about 57pt, which on a five-tab bar is 84pt, wider
     * than the slot (measured model, section 2c). Unspecified means the slot, which is what the
     * iOS 26 reference of [Dark] showed.
     */
    val pillWidth: Dp = Dp.Unspecified,
    /** How much the whole bar, plate and items, grows about its centre while held: 1.05 on iOS 27. */
    val heldScale: Float = 1f,
    /** Additive lift of the bar's material while held, as a fraction of white: 16/255 on iOS 27. */
    val heldLift: Float = 0f,
    /** How much the tab under the lens grows about its own centre while held: 1.18 on iOS 27. */
    val selectedScale: Float = 1f,
    /**
     * How wide the fusion between the bar's outline and the lens's is.
     *
     * The lens is proud of the bar and on iOS 27 the two are one silhouette: where the outlines
     * cross, the bar's edge bows out by about a quarter of this to meet the lens instead of
     * making a crease. Measured at 16pt on the reference; 0 leaves the lens sitting on top of
     * the bar as a separate shape.
     */
    val lensFuse: Dp = 0.dp,
    /**
     * The V3 deforming selector, or null for the capsule this component shipped with.
     *
     * Non-null replaces the scaled capsule with the constrained two-disk body of
     * [GlassSelectorSpec]: the selector's outline, normal, optical band and coverage all come
     * from that body, the bar's material alone is what the selector looks through, and the
     * selected ink is composited as a complete endpoint under one aperture coverage. Nothing
     * rasterised is scaled to fake the deformation.
     *
     * Null keeps every existing consumer, preset and measurement on the path they were verified
     * on; this is opt-in for exactly that reason.
     */
    val selector: GlassSelectorSpec? = null,
) {
    companion object {
        // Declaration order matters here: the constructor's defaults read RestingInset and
        // HeldLens, so Dark has to come after them or it is built while they are still null.

        /**
         * The indicator at rest — which is to say, not glass at all.
         *
         * Profiled down a column of the reference, the resting indicator's body is a flat 6 where
         * the bar around it reads 20, and its edge steps between the two in two or three pixels.
         * No refraction band, no mirrored echo, no colour fringe, no lit bevel. It looks through
         * the bar like the lens does, so this is a dark tint over the bar's own output and every
         * optical term at zero; what makes it read as glass is what happens when you touch it.
         */
        val RestingInset: GlassStyle = GlassStyle.DarkChrome.copy(
            blurRadius = 0.dp,
            backdropBlur = 0.dp,
            refractionBand = 8.dp,
            refractionDepth = 0.dp,
            dispersion = 0f,
            mirror = 0f,
            bevelPeak = 0f,
            tint = Color(0xFF040404).copy(alpha = 0.80f),
            adaptivity = 0f,
            legibility = 0f,
            specular = 0f,
            edgeLight = 0f,
            fresnel = 0f,
            innerShadow = 0f,
            invertsWithBackdrop = false,
            // Below Android 13 there is no shader. A lighter surface, because on that path the
            // items colour themselves and a selected item on a selected-coloured capsule vanishes.
            fallbackSurface = Color(0x3DFFFFFF),
        )

        /**
         * The indicator while held.
         *
         * Read off native screenshots at 3px/pt, this is clear glass with a thin lit edge and a
         * strong rim, and nothing softer than that:
         *
         *  - Its interior reads as the bar does (52 against 47 beside it, the difference a faint
         *    glow peaking in the middle), because it looks *through* the bar.
         *  - Its edge is a one-to-two-pixel line at about +85 on top and +70 below, and nothing
         *    but a one-pixel dark step at the sides — the same overhead light as the bar.
         *  - Its rim pulls what is outside it inward: the bar's hairline appears 11px in from
         *    where it is, and the black around the bar becomes a dark band inside the lens's
         *    edge. That is outward refraction, deep enough to move the hairline a third of the
         *    band, with a colour split strong enough to swirl the symbols it crosses.
         *  - There is no bead, no mirrored echo and no inner shadow. The "bead" an earlier pass
         *    fitted from a video was this edge-line, band and hairline blurred together.
         */
        val HeldLens: GlassStyle = GlassStyle.DarkChrome.copy(
            blurRadius = 0.dp,
            backdropBlur = 0.dp,
            refractionBand = 13.dp,
            // The reference's displacement is nearly uniform across its rim band: a ~20px dark
            // band of pulled-in exterior, and the bar's hairline moved 11px at 24px inside the
            // edge. A circular bevel (power 2) concentrates the bend at the very edge and moved
            // the hairline only 6px here; the flattest profile the shader allows spreads it
            // across the band, which is what the screenshots show.
            refractionDepth = 10.dp,
            bevelPower = 1.5f,
            dispersion = 0.60f,
            mirror = 0f,
            bevel = 1.5.dp,
            bevelPeak = 0f,
            tint = Color.White.copy(alpha = 0.02f),
            adaptivity = 0f,
            legibility = 0f,
            specular = 0.06f,
            specularPower = 4f,
            counterLight = 0.8f,
            edgeLight = 10.8f,
            // The reference's sides are a one-pixel dark step against an interior of about 44,
            // dipping to about 24. The edge line is directional and lands nothing there, so
            // this is what draws them.
            edgeShadow = 0.08f,
            fresnel = 0f,
            innerShadow = 0f,
            invertsWithBackdrop = false,
            fallbackSurface = Color(0x3DFFFFFF),
        )

        /**
         * The resting indicator on the measured material, from the iOS 27 tab-bar recordings
         * (measured model, section 2c). It looks through the bar and shows the bar's output at
         * 0.857 minus 17/255: 10 over black where the bar reads 32, 40 over the App Store's
         * content where the bar reads 67. No optics ([GlassProfile.Held] at 0 is identity), no
         * rim, a soft edge of about a point. Dark appearance; the light one was not captured.
         */
        val RestingInsetMeasured: GlassStyle = GlassStyle(
            profile = GlassProfile.Held,
            heldLens = 0f,
            blurRadius = 0.dp,
            wideKernel = 0.dp,
            fineShare = 1f,
            dispersion = 0f,
            mirror = 0f,
            tint = Color.Black.copy(alpha = 0.143f),
            tintLift = -17.4f / 255f,
            bevel = 0.dp,
            specular = 0f,
            edgeLight = 0f,
            fresnel = 0f,
            innerShadow = 0f,
            contactShadow = 0f,
            invertsWithBackdrop = false,
            fallbackSurface = Color(0x3DFFFFFF),
        )

        /**
         * The held lens on the measured material, from the same recordings.
         *
         *  - Its interior is the bar's output plus 0.11 of the raw content behind the bar and a
         *    1% white: +2 over the held bar over black, +12 over the App Store's content.
         *  - Its band, the outer half of 0.6 R, pulls the exterior in and compresses it 1.3x;
         *    the bar's own edge line lands 10 px inside the lens's rim, and a seam at half the
         *    band hides what lies between it and the interior, which is shown as it is
         *    ([GlassStyle.heldLens]).
         *  - A 3 px edge line of +68 over black at the top and bottom, none at the sides, where
         *    a two-pixel dark step of about -30 marks the outline; the line trails a glow that
         *    decays over about 5 pt inward.
         *  - Colour fringes along the band, blue outermost, about 2-3 px at the runs.
         */
        val HeldLensMeasured: GlassStyle = GlassStyle(
            profile = GlassProfile.Held,
            heldLens = 1f,
            rawShare = 0.11f,
            // A little scatter, so the raw share reads as through the bar's frost rather than
            // as a sharp print of the content behind it.
            blurRadius = 1.5.dp,
            wideKernel = 0.dp,
            fineShare = 1f,
            // Measured at the rim rather than fitted on the band: over the reference's black
            // keypad the lens's edge line peaks at 67 and its channels never separate by more
            // than 16 levels, blue just outside the line and red just inside. At 0.07 the
            // library split them by 98 and drew a saturated blue stroke (FINDINGS 24).
            dispersion = 0.006f,
            mirror = 0f,
            tint = Color.White.copy(alpha = 0.01f),
            tintLift = 0f,
            // The rim: a chamfer two points wide at 0.30, falling with the cosine to the light
            // (68, 57, 37 over black at 3x), the same at the bottom; no two-pixel edge line.
            bevel = 2.dp,
            bevelPeak = 0f,
            specular = 0.30f,
            specularPower = 1f,
            counterLight = 1f,
            edgeLight = 0f,
            edgeShadow = 0.12f,
            fresnel = 0f,
            innerShadow = 0f,
            contactShadow = 0f,
            invertsWithBackdrop = false,
            fallbackSurface = Color(0x3DFFFFFF),
        )

        /** A dark-appearance bar, every number measured off the reference. */
        val Dark = GlassTabBarStyle()

        /**
         * The bar on the measured material.
         *
         * The bar of an iOS 27 tab bar is the same role as the Photos toolbar, which was
         * measured through the calibration target at every Tint Amount in both appearances
         * (`docs/research/measured-model.md`, section 2), so [GlassStyle.toolbar] is the bar. The
         * resting inset and the held lens keep the 0.1 numbers, which were read off native
         * screenshots of an iOS 26 tab bar rather than through the target; they look through the
         * bar and are independent of its material. A tab bar over the target has not been
         * captured yet, so the indicator is the one part of this preset that is not measured
         * the same way.
         */
        fun Measured(dark: Boolean = true, tintAmount: Float = GlassMaterial.DEFAULT_TINT_AMOUNT): GlassTabBarStyle =
            Dark.copy(
                bar = GlassStyle.toolbar(dark = dark, tintAmount = tintAmount),
                pill = RestingInsetMeasured,
                lens = HeldLensMeasured,
                // iOS 27, from the Phone and App Store tab bars in the screen recording: the
                // inset 4pt inside the bar and 84pt wide on a five-tab bar; the lens 5.3pt proud
                // of the bar and 21pt wider than the inset; the bar grown 1.05 and lifted 16/255
                // while held; the tab under the lens grown 1.18 about its own centre.
                pillInset = 4.dp,
                pillWidth = 84.dp,
                lensOverflow = 5.dp,
                lensExtraWidth = 21.dp,
                heldScale = 1.05f,
                heldLift = 16f / 255f,
                selectedScale = 1.18f,
            )

        /**
         * The measured material with the V3 deforming selector, the exact ink compositor and the
         * straight-edge fold.
         *
         * Everything optical that was measured is unchanged: the bar is still the measured
         * toolbar, the resting indicator and held lens keep their measured numbers, and the
         * resting-corner source table is still the authority at a corner. What is new is
         * authored and says so - the body's dynamics ([GlassSelectorSpec]), the straight-run fold
         * strength, and the continuous connection through the held ink map's middle band.
         *
         * [edgeFold] is applied to the **bar**, which is where a straight run is long enough to
         * show page structure, and to the selector's own material for consistency. The lens's
         * semantic ink takes one sharp sample: a maximum over three channels' alphas is not
         * correct coloured transmission, so bulk ink dispersion is off and only the page material
         * splits (V3-MODEL section 8.3).
         */
        fun V3(
            dark: Boolean = true,
            tintAmount: Float = GlassMaterial.DEFAULT_TINT_AMOUNT,
            spec: GlassSelectorSpec = GlassSelectorSpec(),
            edgeFold: Float = 0.8f,
        ): GlassTabBarStyle {
            val measured = Measured(dark = dark, tintAmount = tintAmount)
            return measured.copy(
                // Original Phone T01's unchanged black backdrop:32 at rest,44 while held.
                // These plateaus differ from the general calibration target's35+16; keep
                // that older preset intact and make this correction explicit in dark V3.
                bar = measured.bar.copy(
                    edgeFold = edgeFold,
                    tintLift = if (dark) 32f / 255f else measured.bar.tintLift,
                ),
                heldLift = if (dark) 12f / 255f else measured.heldLift,
                // Both ends of the pill-to-lens morph must agree on every field that snaps at the
                // midpoint, or the ink's source map changes shape halfway through the morph.
                pill = measured.pill.copy(
                    edgeFold = edgeFold,
                    inkDispersion = 0f,
                    heldInkContinuous = false,
                    // The resting indicator against the owner's stills (r10 optics audit, item 4):
                    // iOS reads 0.28-0.32 of its bar on a dark page (6730: 10-12 over 40; the
                    // iOS 27 Phone rest: 10 over 32) where the measured affine law gave 0.55 of
                    // Vitals' lighter bar. A darker, more multiplicative tint: 0.5 x bar - 10.
                    // Authored against the stills; the iOS 27 App Store data point (0.6 of a
                    // bright bar) is not matched by it.
                    tint = Color.Black.copy(alpha = 0.5f),
                    tintLift = -10f / 255f,
                    legibility = 0f,
                ),
                lens = measured.lens.copy(
                    edgeFold = edgeFold,
                    // Ink crossing the lens's band splits into colour fringes of a few px in
                    // every held reference (6721 "Communities", 6727 "Calls"); the split is
                    // scaled by the bend, so the interior stays one sharp sample.
                    // 0.06 read as a saturated ghost of a neighbour's icon at the rim on the r16
                    // phone (review/r16_rim_zoom.png); the stills' fringes are a few px and pale.
                    inkDispersion = 0.03f,
                    // The stepped held map: ink between 0.36 W and 0.5 W is swallowed and what
                    // crosses the rim arrives as a compressed sliver, which is what 6727's
                    // "Calls" and 6721's "Communities" show (r10 audit, item 5: the continuous
                    // map compressed a crossing glyph to 0.73x and hid nothing; iOS 0.2x with
                    // ~30 px hidden).
                    heldInkContinuous = false,
                    // The held item reads 1.18-1.20x its neighbours (6717 Chats 1.19, iOS 27
                    // Calls 1.18; r10 audit item 7); with the bar's own 1.05 that is 0.12 here.
                    // The magnification is of the INK only: the panel pass no longer zooms the
                    // material, which carried the bar's edge outward inside the lens.
                    heldMagnification = 0.12f,
                    // The interior reads as the bar does (+2..+8 in every reference; r10 audit
                    // item 1): no contrast-driven tint, no white, a trace of the raw page.
                    legibility = 0f,
                    tint = Color.White.copy(alpha = 0f),
                    rawShare = 0.01f,
                    // IMG_6756 T01 has a thin lit top/bottom over black and a dark side step.
                    // Astra's zero-lit preset lost that raised boundary. Reassess Fable's
                    // retained directional lighting against the original profiles; these
                    // gains are authored, not recovered Apple optical constants. The direct
                    // fixture must pass edgeLight0/highlightChroma.7: r9's omitted parameters
                    // overstated brightness. Its physical peak46 versus original~94 motivates
                    // this .49 candidate; chromatic rim parity is verified separately.
                    bevel = 2.dp,
                    bevelPeak = 0f,
                    specular = 0.49f,
                    specularPower = 1f,
                    counterLight = 1f,
                    edgeShadow = 0.12f,
                    heldGlow = 0.4f,
                    // Original settled Phone side: dark minimum followed by ~6px recovery
                    // at 3px/pt. This opt-in authored tail preserves the old contour elsewhere.
                    heldEdgeRecovery = 1.dp,
                    dispersion = 0.05f,
                    blurRadius = 0.5.dp,
                ),
                // The body's own outline is what stands proud of the bar; there is no second
                // silhouette to fuse with.
                lensFuse = 0.dp,
                lensInteraction = GlassInteraction(pressScale = 1f, illumination = 0.15f, gel = false),
                // The optical map already enlarges native ink by 1/(1 - 0.12). With the
                // bar's 1.05 expansion that is 1.19x; another 1.18 item scale made it 1.41x.
                selectedScale = 1f,
                // Settled6701: ~160px pill inside184px bar at3x (~4pt per side).
                // 6730 belongs to held release; it cannot establish the resting inset.
                pillInset = 4.dp,
                selector = spec,
            )
        }

        /**
         * [Dark], with the separating contour and brighter highlight Apple's 2026 revision
         * added.
         *
         * The 2026 material was rebuilt around readability: it diffuses busy content behind it
         * more effectively, and it gains a darkened edge and brighter speculars so an element
         * stays distinct over that content. Both changes are stated by Apple; **neither number
         * here is measured**, because the reference captures this library was tuned against
         * predate the revision. They are a starting point, not a parity claim — everything in
         * [Dark] is measured and this is not.
         *
         * Note also that the 2026 release replaces the earlier Clear/Tinted toggle with a
         * continuous transparency slider whose default is its midpoint, so there is no longer
         * one correct opacity to match. Treat [GlassStyle.tint] as the host's to set.
         */
        val Ios27: GlassTabBarStyle = Dark.copy(
            bar = Dark.bar.copy(edgeShadow = 0.06f, specular = 0.09f),
            lens = Dark.lens.copy(edgeShadow = 0.16f, specular = 0.09f),
        )
    }
}

/**
 * The share of the resting pill's half height that an item's icon-over-label block occupies.
 *
 * An authored layout constant, not a measured optic: it is what the body's accommodation uses to
 * decide how far a rounded end has to be pushed out before the **corners** of the padded label
 * rectangle are inside it. Horizontal span alone does not prove a label fits between two round
 * caps, which is what the final-polish pass measured at up to 9.6 px of overhang.
 */
private const val INK_VERTICAL_SHARE = 0.62f

/**
 * How long a released gesture's preview waits for the host to apply the selection it asked for
 * before treating it as declined. Authored, not measured: long enough to cover a navigation that
 * lands a few frames late, short enough that a refused tab does not look selected.
 */
private const val SELECTION_GRACE_MILLIS = 400L

/** The gesture's own pointer has lifted: a tap, as opposed to a pointer taken by another node. */
private fun AwaitPointerEventScope.liftedWithoutDrag(id: PointerId): Boolean =
    currentEvent.changes.firstOrNull { it.id == id }?.pressed == false
