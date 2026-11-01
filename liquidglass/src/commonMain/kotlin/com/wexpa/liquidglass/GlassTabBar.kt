package com.wexpa.liquidglass

import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
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
    itemCount: Int,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    style: GlassTabBarStyle = GlassTabBarStyle.Calm(LocalGlassDefaults.current.dark),
    /** False when the user or the system has asked for less motion: no lens, no gel, no fling.
     * Follows the platform's Reduce Motion by default. */
    motionEnabled: Boolean = !rememberGlassSystemAccessibility().reduceMotion,
    /** 0 to 1, for the bar to materialize in and out. See [glassEffect]. */
    materialize: Float = 1f,
    /** A separate glass control beside the bar's trailing end that the bar fuses with while
     * pressed, such as a search button. See [GlassTabBarAccessory]. */
    trailingAccessory: GlassTabBarAccessory? = null,
    item: @Composable (index: Int, selected: Boolean) -> Unit,
) {
    GlassPortal(modifier) { state ->
        GlassTabBar(state, itemCount, selectedIndex, onSelected, Modifier, style, motionEnabled, materialize,
            trailingAccessory, item)
    }
}

/** The bar itself, drawn from [state]'s recording. The public [GlassTabBar] supplies the root's. */
@Composable
internal fun GlassTabBar(
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
    /** A separate glass control beside the bar's trailing end that the bar fuses with while
     * pressed, such as a search button. See [GlassTabBarAccessory]. */
    trailingAccessory: GlassTabBarAccessory? = null,
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

    // The keyboard focus ring (#117) is drawn over everything the bar draws, the selector included:
    // drawn on the interactive row it sat under the pill or lens of the selected tab.
    val focusRing = remember { GlassFocusRingState() }
    androidx.compose.runtime.CompositionLocalProvider(LocalGlassFocusRing provides focusRing) {
    // Nothing here clips: the lens stands proud of the bar while it is held.
    BoxWithConstraints(
        modifier = modifier.height(style.height)
            .onGloballyPositioned { focusRing.bar = it }
            // A pointer press anywhere on the bar hides the ring until the next key: the desktop stays in keyboard
            // input mode under touch and synthetic presses, which focus the tab they press (#143 regression in replays).
            .pointerInput(Unit) {
                kotlinx.coroutines.coroutineScope {
                    val watcher = this
                    var cleanup: kotlinx.coroutines.Job? = null
                    awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                            // A press, not a held pointer moving: a key pressed mid-hold must still bring the ring back.
                            if (e.changes.any { it.pressed && !it.previousPressed }) {
                                // A new press supersedes the last release's pending check.
                                cleanup?.cancel()
                                focusRing.pointerSinceKey = true
                                focusRing.pointerSinceBlur = true
                            }
                            // A press that ends with no tab focused (the row's padding) leaves nothing to suppress: clear
                            // it two frames on, after any tab the press clicked has taken focus.
                            if (e.changes.none { it.pressed } && e.changes.any { it.previousPressed }) cleanup = watcher.launch {
                                androidx.compose.runtime.withFrameNanos { }
                                androidx.compose.runtime.withFrameNanos { }
                                if (!focusRing.barFocused) {
                                    focusRing.pointerSinceKey = false
                                    focusRing.pointerSinceBlur = false
                                }
                            }
                        }
                    }
                }
            }
            .onPreviewKeyEvent { focusRing.pointerSinceKey = false; false }
            // Focus that comes back with no pointer press on the bar since it left came from the keyboard (a Tab from a
            // text field, whose key the bar never sees) or from code: show the ring again. A pointer's own press
            // reaches this bar (Initial pass) before the tab it presses takes focus.
            .onFocusChanged {
                focusRing.barFocused = it.hasFocus
                if (!it.hasFocus) focusRing.pointerSinceBlur = false
                else if (!focusRing.pointerSinceBlur) focusRing.pointerSinceKey = false
            }
            .drawWithContent {
                drawContent()
                // In the bar's own coordinates, so a scaled or rotated ancestor cannot misplace it.
                val target = focusRing.target.value
                val bar = focusRing.bar
                if (target != null && bar != null && bar.isAttached && target.cell.isAttached) {
                    drawGlassFocusRing(bar.localBoundingBoxOf(target.cell, clipBounds = false))
                }
            },
    ) {
        val density = LocalDensity.current
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        val barWidthDp: Dp = maxWidth
        val barHeightDp: Dp = maxHeight
        val barWidthPx = with(density) { barWidthDp.toPx() }
        val barHeightPx = with(density) { barHeightDp.toPx() }
        val geometry = glassTabBarGeometry(density, barWidthPx, style, itemCount)
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
        val barPress = rememberGlassPressSource()
        val barDrawing = remember(style.deformItemsWithBar) {
            if (style.deformItemsWithBar) GlassMaterialDrawingState() else null
        }

        var barDown by remember { mutableStateOf(Offset.Zero) }
        // GlassPoseSpec.barPressDelaySeconds: where the finger is while the bar's press waits.
        var pendingBarPress by remember { mutableStateOf<Offset?>(null) }
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
            remember(spec) { GlassPoseHandle(GlassPoseController(
                if (spec?.response == GlassResponse.Calm) GlassPoseSpec.Calm else GlassPoseSpec())) }
        } else {
            null
        }
        val anyHandle = handle != null || poseHandle != null
        // GlassPoseSpec.selectionHaptics: one tick each time a finger marks another tab (a tap, or
        // a held lens crossing a boundary); never for the host's own selection changes.
        val haptics = LocalHapticFeedback.current
        val selectionHaptics = poseHandle?.controller?.spec?.selectionHaptics == true
        // GlassPoseSpec.hoverScale: the tab under a hovering pointer lifts (Calm); other styles keep 1, and so does
        // reduced motion, which asks for no such animation.
        focusRing.hoverScale = if (motionEnabled) poseHandle?.controller?.spec?.hoverScale ?: 1f else 1f
        var tickedIndex by remember { mutableIntStateOf(activeIndex) }
        LaunchedEffect(activeIndex) {
            if (activeIndex != tickedIndex) {
                if (selectionHaptics && dragIndex >= 0) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                tickedIndex = activeIndex
            }
        }
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
        val transitAppearance = poseHandle?.transitAppearance ?: 0f
        val emphasisNow = glassTransitInkScale(style,formationNow,transitAppearance)
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
        // At accessibility text sizes a held tab shows itself magnified in the middle of the window (#145), once the
        // press has lasted GlassLargeContentHoldSeconds: some selector paths report `held` from touch-down, and a tap
        // must never flash the viewer. Counted in frame time, so a test or replay clock drives it.
        val wantsViewer = held && LocalDensity.current.fontScale >= GlassLargeContentFontScale
        var viewerReady by remember { mutableStateOf(false) }
        LaunchedEffect(wantsViewer) {
            viewerReady = false
            if (!wantsViewer) return@LaunchedEffect
            // Counted from the press: a controller raises `held` only after its own hold threshold.
            val threshold = poseHandle?.controller?.spec?.holdThresholdSeconds ?: handle?.controller?.spec?.holdThresholdSeconds ?: 0f
            val wait = ((GlassLargeContentHoldSeconds - threshold).coerceAtLeast(0f) * 1e9f).toLong()
            val start = withFrameNanos { it }
            while (withFrameNanos { it } - start < wait) Unit
            viewerReady = true
        }
        if (wantsViewer && viewerReady && activeIndex in 0 until itemCount) {
            GlassLargeContentViewer(LocalGlassDefaults.current.dark) { item(activeIndex, true) }
        }
        // GlassPoseSpec.heldInkLift: rises within 80 ms of the hold, lets go over about .35 s after the lift.
        val heldInkLift = poseHandle?.controller?.spec?.heldInkLift ?: 0f
        val inkLift by animateFloatAsState(
            targetValue = if (held && heldInkLift > 0f) heldInkLift else 0f,
            animationSpec = if (held) tween(80) else tween(350),
            label = "glass_tab_bar_ink_lift",
        )

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
            barPress.downStiffness = poseHandle.controller.spec.barPressStiffness
            // GlassPoseSpec.barPressDelaySeconds: a still press reaches the bar after the delay.
            val barPending = pendingBarPress != null
            LaunchedEffect(poseHandle, barPending) {
                if (!barPending) return@LaunchedEffect
                // On the frame clock, not wall time: a replay renders frames slower than real time.
                val wait = (poseHandle.controller.spec.barPressDelaySeconds * 1e9f).toLong()
                val start = withFrameNanos { it }
                while (withFrameNanos { it } - start < wait) Unit
                pendingBarPress?.let { p ->
                    pendingBarPress = null
                    barPress.press(p, p - barDown)
                }
            }
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
        // [GlassTabBarStyle.itemsFollowBarPress]: false, the ink follows the bar's pressed drawing
        // only as far as the selector has formed (a hold), so a tap's press leaves it in place.
        val itemShare: (() -> Float)? = if (style.itemsFollowBarPress) null
            else ({
                // A tap's transit forms the selector part way; only a hold's formation counts.
                val f = (((poseHandle?.formation ?: handle?.formation ?: lift) - 0.5f) / 0.5f).coerceIn(0f, 1f)
                f * f * (3f - 2f * f)
            })
        // [GlassTabBarStyle.touchLift]: the bar's material lift for any touch, not only a hold.
        val touchAmount by animateFloatAsState(
            targetValue = if (gestureDown && style.touchLift && motionEnabled) 1f else 0f,
            animationSpec = if (gestureDown) tween(50) else tween(260),
            label = "glass_tab_bar_touch",
        )
        val pillHeight: Dp = barHeightDp - style.pillInset * 2
        val lensHeight: Dp = barHeightDp + style.lensOverflow * 2
        val pillWidthDp: Dp = if (style.pillWidth.isSpecified) maxOf(style.pillWidth, slotWidthDp) else slotWidthDp
        val selectorWidth: Dp = lerp(pillWidthDp, pillWidthDp + style.lensExtraWidth, lift)
        val selectorHeight: Dp = lerp(pillHeight, lensHeight, lift)
        val selectorWidthPx = with(density) { selectorWidth.toPx() }
        val selectorHeightPx = with(density) { selectorHeight.toPx() }
        val selectorRisePx = with(density) { style.lensRise.toPx() } * lift
        // The rim split follows how fast the lens moves (#73/#94): iOS's fringe energy rises 5-30x from a
        // still lens to one at 400-1500 px/s (3x). heldTravel is filtered for the strain and stayed under
        // 0.04 through IMG_6756's drag, so the lens's own speed, over 400 dp/s, drives it as well.
        val fringeSpeed = (abs(if (dragging) liveVelocity else settleVelocity) /
            with(density) { GlassSelectorFringeSpeed.toPx() }).coerceIn(0f, 1f)
        val selectorStyle = glassSelectorMaterial(style, lift, transitAppearance, max(poseHandle?.heldTravel ?: 0f, fringeSpeed))

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
            if (poseHandle != null && poseHandle.controller.spec.barPressDelaySeconds > 0f &&
                !barPress.dragging && !barPress.isPressed) {
                pendingBarPress = Offset(x, y)
            } else barPress.press(Offset(x, y), Offset(x, y) - barDown)
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
            pendingBarPress = null
            barPress.release()
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
                    this[GlassTabBarSemantics.Transit] = transitAppearance
                    this[GlassTabBarSemantics.HeldTravel] = poseHandle?.heldTravel ?: 0f
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
                    this[GlassTabBarSemantics.HoverIndex] = focusRing.hovered.intValue
                    this[GlassTabBarSemantics.HoverScale] = focusRing.hoverScale
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
                // #117 item 135: a hovering mouse or trackpad marks the tab under it. This node lies over the
                // items, so they never see the pointer themselves; it watches without consuming anything.
                .pointerInput(geometry, rtl, itemCount) {
                    // A restarted handler (resize, direction, item count) starts with no hovered tab.
                    try { awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                            val c = e.changes.firstOrNull() ?: continue
                            if (c.type != androidx.compose.ui.input.pointer.PointerType.Mouse) continue
                            focusRing.hovered.intValue =
                                if (e.type == androidx.compose.ui.input.pointer.PointerEventType.Exit || c.pressed ||
                                    c.position.x !in 0f..size.width.toFloat() || c.position.y !in 0f..size.height.toFloat()) -1
                                else geometry.nearestIndex(c.position.x).let {
                                    // Shared-width slots are a Row, which mirrors in RTL; fixed slots are placed by x.
                                    if (rtl && !geometry.fixed) itemCount - 1 - it else it
                                }
                        }
                    } } finally { focusRing.hovered.intValue = -1 }
                }
                .pointerInput(geometry, motionEnabled, handle, poseHandle, style, flingVelocity) {
                    // Read here, outside the restricted gesture scope: whether this pointer-input job is still
                    // alive when a gesture ends decides whether anything may be launched from its release.
                    val gestureJob = currentCoroutineContext()[Job]
                    awaitEachGesture {
                        // The item under the finger consumes the down, so this must not
                        // require an unconsumed one.
                        val down = awaitFirstDown(requireUnconsumed = false)
                        barDown = down.position
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
                                // The press now drags the lens (a hold alone does not): the bar keeps
                                // only its drag share ([GlassInteraction.dragPressShare]).
                                if (!barPress.dragging && abs(change.position.x - down.position.x) > viewConfiguration.touchSlop) {
                                    barPress.dragging = true
                                }
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
                                // The lift is the gesture's last sample: integrate up to it and
                                // install its position, or the travel between the last move and
                                // the lift is lost to the release (2026-10-15 review).
                                if (terminalUp) poseHandle.controller.pointerMove(
                                    terminal!!.position.x, terminal.position.y, terminal.uptimeMillis / 1000.0)
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
            // A neighbouring control the bar's material reaches for (iOS 26 Phone: the search
            // button). Anchored, so the bar's own press growth closes the gap, not the rect.
            val accessoryFuse = if (trailingAccessory != null && motionEnabled && barFuse == null) {
                with(density) {
                    val sizePx = trailingAccessory.size.toPx()
                    val gapPx = trailingAccessory.gap.toPx()
                    val plateOffset = barWidthPx * (barScale - 1f) / 2f
                    val left = (if (rtl) -gapPx - sizePx else barWidthPx + gapPx) + plateOffset
                    val top = (barHeightPx - sizePx) / 2f + barHeightPx * (barScale - 1f) / 2f
                    GlassFuse(
                        bounds = DpRect(left.toDp(), top.toDp(), (left + sizePx).toDp(), (top + sizePx).toDp()),
                        cornerRadius = (sizePx / 2f).toDp(),
                        smoothing = if (trailingAccessory.yieldsToLens && poseHandle != null) {
                            val endEdge = if (rtl) barWidthPx - poseHandle.left else poseHandle.right
                            trailingAccessory.fusion * (1f - glassAccessoryYield(poseHandle.formation, endEdge, barWidthPx, barHeightPx))
                        } else trailingAccessory.fusion,
                        anchored = true,
                    ).apply {
                        // Where the accessory is drawn ([GlassTabBarAccessory.link]): its press growth
                        // and pull move and reshape the rect the outline reaches for.
                        follow = trailingAccessory.link
                    }
                }
            } else {
                null
            }
            val margin = (style.heldScale - 1f).coerceAtLeast(0f)
            val barStyle = if (style.heldLift != 0f) {
                style.bar.copy(tintLift = style.bar.tintLift + style.heldLift * max(lift, touchAmount))
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
                        .liquidGlassCore(
                            state = state,
                            shape = barShape,
                            style = barStyle,
                            light = style.light,
                            // Whole-surface feedback is independent of selector travel.
                            interaction = if (motionEnabled) style.barInteraction else GlassInteraction.ReducedMotion,
                            pressSource = barPress,
                            materialize = materialize,
                            // The lens is proud of the bar, and on the reference the bar's own
                            // outline does not simply end under it: it bows out to meet it, one
                            // liquid silhouette with no crease (measured model, section 2d).
                            fuse = barFuse ?: accessoryFuse,
                            refractContent = false,
                            through = null,
                            lensFormation = 0f,
                            body = null,
                            endpointComposite = false,
                            materialDrawing = barDrawing,
                        ),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxSize(fraction = 1f / (1f + margin))
                        .graphicsLayer {
                            scaleX = barScale
                            scaleY = barScale
                        }
                        .glassMaterialForeground(barDrawing, share = itemShare),
                ) {
                    ItemRow(
                        count = itemCount,
                        inset = style.contentPadding,
                        geometry = geometry,
                        selectedFor = { index -> !lensCarriesInk && index == activeIndex },
                        onClick = { index -> select(index) },
                        semanticSelected = selected,
                        item = item,
                        emphasisIndex = activeIndex,
                        emphasis = glassTransitInkScale(style,lift,transitAppearance),
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
                // The drawn body follows the bar's pressed content, as its tab does (barShift);
                // republish when the bar's press moves while the pose loop may be asleep.
                if (poseHandle != null) {
                    val barFrame = barDrawing
                    poseHandle.barShift = barFrame?.let { state ->
                        { x: Float -> (state.drawing.contentPoint(x, barHeightPx / 2f).x - x) * (itemShare?.invoke() ?: 1f) }
                    }
                    LaunchedEffect(poseHandle, barFrame) {
                        if (barFrame != null) snapshotFlow { barFrame.drawing }.collect { poseHandle.publish() }
                    }
                }
                val nodeLeftPx = handle?.nodeLeft() ?: poseHandle!!.nodeLeft()
                val nodeTopPx = poseHandle?.nodeTop() ?: -overflowTopPx
                // [GlassTabBarAccessory.lensFusion]: a held lens at the accessory's end takes the neck
                // the bar gives up, in the lens node's own frame.
                val lensAccessoryFuse = if (trailingAccessory != null && motionEnabled && poseHandle != null &&
                    trailingAccessory.lensFusion > 0.dp) {
                    // A formed (held) lens only: the neck bridges just the last few px, so it shows only
                    // once the lens has reached the end; a tap's partial formation never draws it.
                    val f = ((poseHandle.formation - 0.5f) / 0.5f).coerceIn(0f, 1f)
                    val taken = f * f * (3f - 2f * f)
                    if (taken <= 0.01f) null else with(density) {
                        val sizePx = trailingAccessory.size.toPx()
                        val gapPx = trailingAccessory.gap.toPx()
                        val left = (if (rtl) -gapPx - sizePx else barWidthPx + gapPx) - nodeLeftPx
                        val top = (barHeightPx - sizePx) / 2f - nodeTopPx
                        GlassFuse(
                            bounds = DpRect(left.toDp(), top.toDp(), (left + sizePx).toDp(), (top + sizePx).toDp()),
                            cornerRadius = (sizePx / 2f).toDp(),
                            smoothing = trailingAccessory.lensFusion * taken,
                            anchored = true,
                        ).apply { follow = trailingAccessory.link }
                    }
                } else null
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
                            // The bar keeps its own outline. The node's declared envelope
                            // accommodates held growth and Calm's rounded moving taps.
                            fuse = lensAccessoryFuse,
                            lensFormation = glassSelectorFormation(style, lift, transitAppearance, poseHandle?.heldTravel ?: 0f),
                            body = handle?.body?.movedInto(nodeLeftPx.toFloat(), overflowTopPx.toFloat()),
                            poseBody = poseHandle?.render,
                            endpointComposite = lensCarriesInk,
                            packedEndpoint = supportsGlassEndpointInputs && spec?.response == GlassResponse.Calm,
                            tintWithinThrough = if (spec?.response == GlassResponse.Calm) barDrawing else null,
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
                                }
                                // In the bar-sized child's own frame (the layout above measures
                                // everything after it at the bar's size), so the press pivots on
                                // the bar centre: passing the node origin moved the held ink about
                                // 17 px left and 10 up of the bar's own icons (video2, 1.8 s).
                                .glassMaterialForeground(barDrawing, share = itemShare)
                                .glassInkLift(inkLift),
                        ) {
                            ItemRow(
                                count = itemCount,
                                inset = style.contentPadding,
                                geometry = geometry,
                                selectedFor = { index -> !usePose || style.lensInkAll || index == activeIndex },
                                onClick = null,
                                item = item,
                                emphasisIndex = activeIndex,
                                emphasis = glassTransitInkScale(style,lift,transitAppearance),
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
                                    }
                                    .glassMaterialForeground(barDrawing, share = itemShare),
                            ) {
                                ItemRow(
                                    count = itemCount,
                                    inset = style.contentPadding,
                                    geometry = geometry,
                                    selectedFor = { index -> !usePose || style.lensInkAll || index == activeIndex },
                                    onClick = null,
                                    item = item,
                                    emphasisIndex = activeIndex,
                                    emphasis = glassTransitInkScale(style,lift,transitAppearance),
                                )
                            }
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
     * content it is over from layout rather than from a guess. Each item's content is centred on
     * its slot's centre, so a width plus that centre is the whole bound.
     */
    onMeasured: ((index: Int, width: Float) -> Unit)? = null,
    /** The tab the host has selected, for accessibility; only the interactive row reports it. */
    semanticSelected: Int = -1,
    /** The bar's item layout, in px; with fixed slots the items sit at its centres. */
    geometry: GlassTabBarGeometry? = null,
) {
    // The interactive row is the one accessibility sees: a group of tabs. Every other copy
    // (the lens's refracted row, the V3 selected ink) is decoration and is hidden, or a
    // screen reader would read each label two or three times.
    val group = Modifier
        .fillMaxSize()
        .then(if (onClick != null) Modifier.selectableGroup() else Modifier.clearAndSetSemantics {})
    val fixed = geometry?.takeIf { it.fixed }
    if (fixed != null) {
        // Fixed slots (GlassTabBarStyle.slotWidth): each item centred on its slot centre, its
        // target half the gap to the nearer neighbour, so a closed-up end never overlaps the next.
        Layout(
            modifier = group,
            content = { repeat(count) { index -> ItemCell(index, Modifier, true, selectedFor, onClick, item, emphasisIndex, emphasis, onMeasured, semanticSelected) } },
        ) { measurables, constraints ->
            val h = constraints.maxHeight
            val placeables = measurables.mapIndexed { index, m ->
                val w = (fixed.itemHalfWidth(index) * 2f).roundToInt().coerceAtLeast(0)
                m.measure(Constraints.fixed(w, h))
            }
            layout(constraints.maxWidth, h) {
                placeables.forEachIndexed { index, p -> p.place((fixed.centreOf(index) - p.width / 2f).roundToInt(), 0) }
            }
        }
        return
    }
    Row(
        modifier = group.padding(horizontal = inset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        repeat(count) { index ->
            ItemCell(index, Modifier.weight(1f), false, selectedFor, onClick, item, emphasisIndex, emphasis, onMeasured, semanticSelected)
        }
    }
}

@Composable
private fun ItemCell(
    index: Int,
    sizing: Modifier,
    unboundedWidth: Boolean,
    selectedFor: (Int) -> Boolean,
    onClick: ((Int) -> Unit)?,
    item: @Composable (index: Int, selected: Boolean) -> Unit,
    emphasisIndex: Int,
    emphasis: Float,
    onMeasured: ((index: Int, width: Float) -> Unit)?,
    semanticSelected: Int,
) {
    Box(
        modifier = sizing
            .fillMaxHeight()
            .then(
                if (onClick != null) {
                    val interactions = remember { MutableInteractionSource() }
                    // A keyboard user has to see which tab has focus (#117). The lens is the
                    // touch indication, so nothing else is drawn for a press.
                    val focused by interactions.collectIsFocusedAsState()
                    // Only for keyboard navigation: a pointer that focuses a tab shows the lens.
                    val keyboard = LocalInputModeManager.current.inputMode == InputMode.Keyboard
                    val ring = LocalGlassFocusRing.current
                    var cell by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
                    val showRing = focused && keyboard && ring?.pointerSinceKey != true
                    androidx.compose.runtime.SideEffect {
                        val target = ring?.target ?: return@SideEffect
                        val at = cell
                        if (showRing && at != null) target.value = GlassFocusRingTarget(index, at)
                        else if (target.value?.index == index) target.value = null
                    }
                    // A focused tab removed from the bar (a smaller itemCount) takes its ring with it.
                    androidx.compose.runtime.DisposableEffect(ring) {
                        onDispose { if (ring?.target?.value?.index == index) ring.target.value = null }
                    }
                    Modifier
                        .onGloballyPositioned { cell = it }
                        .selectable(
                            selected = index == semanticSelected,
                            interactionSource = interactions,
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
        // A hovered tab's content lifts by GlassPoseSpec.hoverScale, in every copy of the row alike (the lens's
        // refracted row reads the same bar state), so the ink under the lens never disagrees with the bar's. Applied
        // at draw time, not as a layer property: the bar's material records this row, and only a draw-phase read
        // invalidates that recording (a layer's scale alone left the shader sampling the unscaled row).
        val ring = LocalGlassFocusRing.current
        val hoverTarget = if (ring != null && ring.hovered.intValue == index) ring.hoverScale else 1f
        val hoverScale by animateFloatAsState(
            targetValue = hoverTarget,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 900f),
            label = "glass_tab_bar_item_hover",
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
            modifier = (if (unboundedWidth) Modifier.wrapContentWidth(unbounded = true) else Modifier)
                .then(measured)
                .drawWithContent {
                    if (hoverScale == 1f) drawContent()
                    else scale(hoverScale) { this@drawWithContent.drawContent() }
                }
                .graphicsLayer {
                    scaleX = itemScale
                    scaleY = itemScale
                },
        ) {
            item(index, selectedFor(index))
        }
    }
}

/**
 * The item layout a [GlassTabBar] uses, kept free of Compose so it can be tested on the JVM.
 * Items are equal slots across the bar inside a horizontal [inset]; the selector's centre is
 * clamped to the first and last slot centres.
 */
@Immutable
internal data class GlassTabBarGeometry(
    val barWidth: Float,
    val inset: Float,
    val count: Int,
    /** [GlassTabBarStyle.slotWidth] in px; NaN shares the bar out. */
    val fixedSlot: Float = Float.NaN,
    /** With [fixedSlot], the nearest an item's centre comes to a bar end: its pill's half plus inset. */
    val endReach: Float = 0f,
) {
    val fixed: Boolean get() = fixedSlot.isFinite() && fixedSlot > 0f

    // Never negative: a bar narrower than its two insets (an expand-in, a zero-width first
    // frame) would otherwise put firstCentre past lastCentre, and every coerceIn between them throws.
    val slotWidth: Float get() = if (fixed) fixedSlot else ((barWidth - inset * 2f) / count).coerceAtLeast(0f)
    val firstCentre: Float get() = centreOf(0)
    val lastCentre: Float get() = centreOf(count - 1)

    fun centreOf(index: Int): Float = if (fixed) {
        glassFixedSlotCentre(barWidth, count, fixedSlot, endReach, index)
    } else inset + (index + 0.5f) * slotWidth

    fun nearestIndex(x: Float): Int = if (fixed) {
        var i = 0
        while (i < count - 1 && x > (centreOf(i) + centreOf(i + 1)) / 2f) i++
        i
    } else ((x - inset) / slotWidth).toInt().coerceIn(0, count - 1)

    /**
     * Each item's hit and layout half-width about its centre: half the gap to the nearer
     * neighbour, so the clamped end items never overlap the next one's target.
     */
    fun itemHalfWidth(index: Int): Float {
        if (!fixed) return slotWidth / 2f
        val c = centreOf(index)
        val left = if (index > 0) (c - centreOf(index - 1)) / 2f else Float.MAX_VALUE
        val right = if (index < count - 1) (centreOf(index + 1) - c) / 2f else Float.MAX_VALUE
        return min(min(left, right), slotWidth / 2f).coerceAtLeast(0f)
    }

    /** A flick carries to the next item even if the finger never got it there. */
    fun projected(lastIndex: Int, releasedVelocity: Float, flingVelocity: Float): Int = when {
        releasedVelocity > flingVelocity -> lastIndex + 1
        releasedVelocity < -flingVelocity -> lastIndex - 1
        else -> lastIndex
    }.coerceIn(0, count - 1)
}

/** The item layout [style] asks for on a [barWidthPx] bar. */
internal fun glassTabBarGeometry(density: Density, barWidthPx: Float, style: GlassTabBarStyle, count: Int): GlassTabBarGeometry =
    with(density) {
        if (!style.slotWidth.isSpecified) {
            return GlassTabBarGeometry(barWidthPx, style.contentPadding.toPx(), count)
        }
        val slot = style.slotWidth.toPx()
        val pill = if (style.pillWidth.isSpecified) max(style.pillWidth.toPx(), slot) else slot
        GlassTabBarGeometry(barWidthPx, style.contentPadding.toPx(), count, slot, pill / 2f + style.pillInset.toPx())
    }

/**
 * The centre of item [index] on equal [slot]-wide slots centred in a [barWidth] bar, held at least
 * [endReach] from either end ([GlassTabBarStyle.slotWidth]). One function for the tab bar's
 * geometry and the V3 selector's, so the two can never disagree about where a tab is.
 */
internal fun glassFixedSlotCentre(barWidth: Float, count: Int, slot: Float, endReach: Float, index: Int): Float {
    val mid = barWidth / 2f
    val free = mid + (index - (count - 1) / 2f) * slot
    val lo = min(endReach, mid)
    return free.coerceIn(lo, barWidth - lo)
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
/** The test tag of a [GlassTabBar]'s gesture node, for UI tests that tap or drag the bar. */
const val GlassTabBarTestTag: String = "GlassTabBarGestureNode"

internal object GlassTabBarSemantics {
    const val TAG = GlassTabBarTestTag
    val SelectorX = SemanticsPropertyKey<Float>("GlassTabBarSelectorX")
    val Held = SemanticsPropertyKey<Boolean>("GlassTabBarHeld")
    val Dragging = SemanticsPropertyKey<Boolean>("GlassTabBarDragging")
    val DragIndex = SemanticsPropertyKey<Int>("GlassTabBarDragIndex")
    /** The tab under a hovering pointer, or -1, and the scale it lifts to (#117). */
    val HoverIndex = SemanticsPropertyKey<Int>("GlassTabBarHoverIndex")
    val HoverScale = SemanticsPropertyKey<Float>("GlassTabBarHoverScale")
    /** V3 only: how far the held lens has formed, the body's width and end asymmetry, in px. */
    val Formation = SemanticsPropertyKey<Float>("GlassTabBarFormation")
    val BodyWidth = SemanticsPropertyKey<Float>("GlassTabBarBodyWidth")
    val BodySkew = SemanticsPropertyKey<Float>("GlassTabBarBodySkew")
    /** How far the body's contour is outside the resting bar, in px; Calm taps may protrude. */
    val Protrusion = SemanticsPropertyKey<Float>("GlassTabBarProtrusion")
    /** A tap's transit appearance and a held lens's travel, 0 to 1: the drivers of the transit fold and the rim split (#101). */
    val Transit = SemanticsPropertyKey<Float>("GlassTabBarTransit")
    val HeldTravel = SemanticsPropertyKey<Float>("GlassTabBarHeldTravel")

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
     * Authored slow recovery retained for compatibility with the historical selector.
     *
     * The former attribution to a measured LOLL8185 release at CFR1944 is withdrawn:
     * native-PTS crops still show a refracting lens over Calls in the assumed trailing-rest
     * frames1968/1980. A vanishing raised-area signal there does not establish settled rest
     * or finger-up timing. Neither the inherited314ms area duration nor its367ms fitted
     * first-order equivalent identifies this spring. See the release-provenance audit in
     * review/ATLAS-STABILIZATION.md. This documentation correction does not retune motion.
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
    /**
     * Whether [heldLift] also lifts the bar for any touch, a tap included, rising within 50 ms of
     * the press and falling over 260 ms after the lift. iOS's whole bar lifts on every touch:
     * +13 grey levels at the tab a tap never visits, back to rest ~350 ms after the ink leaves
     * (docs/1, 2026-10-15). False lifts only a formed (held) selector's bar, as before.
     */
    val touchLift: Boolean = false,
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
    /** Whole-bar material deformation; ordinary labels retain their layout positions. */
    val barInteraction: GlassInteraction? = null,
    /** Let visible ordinary/selected ink share the bar's bounded drawing response.
     * Layout, hit targets and selector travel remain independent. Enabled by [Calm]. */
    val deformItemsWithBar: Boolean = false,
    /**
     * With [deformItemsWithBar], whether the tabs' ink and the selector's anchor follow the bar's
     * press growth for any touch (true, the default) or only as far as the selector has formed, so
     * a tap's press leaves them where they are laid out (false). [Calm] uses false since 18 October:
     * on a tap iOS 26's tab icons do not move (IMG_7048 light taps: an icon the lens does not cross
     * holds within 0.1 px; ours slid 7 px and back, and the owner's device check saw them jump),
     * while on a hold they follow the bar (Calls moves 14 px out and grows 4%, 38.1-38.3 s).
     */
    val itemsFollowBarPress: Boolean = true,
    /** Fade the resting selector pigment during coupled tap travel; held optics stay separate. */
    val tapTintFade: Float = 0f,
    /** Transient selected-ink emphasis around its own anchor, fading as held zoom takes over. */
    val tapSelectedScale: Float = 1f,
    /**
     * How much of the held lens's optics a resting selector shows while it travels after a tap,
     * scaled by its transit appearance. Geometry is unaffected: the body keeps its tap size.
     * 0 keeps the historical dark inset whose pigment fades. Calm uses 1: in video1 the moving
     * selector refracts and magnifies the icons it crosses (2026-10-13 side-by-side).
     */
    val tapOptics: Float = 0f,
    /**
     * With [tapOptics], how strongly a moving, unheld selector folds its band (the lens's own
     * nearby content mirrored along the rim) instead of using the held map. 0 keeps the held
     * map. Calm dark uses 1, from the 2026-10-13 comparison with video1 and video2.
     */
    val tapOpticsFold: Float = 0f,
    /**
     * The fold of a tapped (unheld) selector while it travels; unspecified (NaN, the default)
     * follows [tapOpticsFold]. Zero shows the held lens's own optics on the travelling selector,
     * as a hold or a throw does, while a held lens's travel keeps [tapOpticsFold]'s rim.
     */
    val tapTransitFold: Float = Float.NaN,
    /**
     * The pose selector draws every item's selected ink inside its aperture, not only the
     * active item's. Video1/video2 (2026-10-13): every icon under a travelling or held iOS lens
     * shows its selected colour. False keeps the r13 behaviour.
     */
    val lensInkAll: Boolean = false,
    /**
     * Equal item slots of this width, centred in the bar, in place of the bar's width shared out
     * inside [contentPadding]. Where the slots are wider than the bar allows, the end items move
     * in to where their resting indicator ([pillWidth]) sits [pillInset] inside the bar's end, so
     * the inner spacing stays the slot and the ends close up. Measured on WhatsApp's iOS bar
     * (JGEY2190 50-74 s, 17 October): a 348pt bar, tab centres 193/370/584.5/799/976 px at 3x, a
     * 78pt pill 4pt inside the end; the inner spacing is 71.5pt (its widest item, 63.3pt, plus
     * 8pt), wider than a fifth of the bar. This rule puts every centre within 2 px; slots sized
     * per item miss by 13. Unspecified keeps the shared-out slots.
     */
    val slotWidth: Dp = Dp.Unspecified,
) {
    companion object {
        /** Recommended navigation preset. Material responds gently; the selector deforms
         * along travel only. Existing V3/Measured presets remain available for comparison. */
        fun Calm(dark: Boolean = true): GlassTabBarStyle = CalmRecordedSpace(dark).let { recorded ->
            if (!dark) calmLight(recorded) else recorded.copy(
                // Re-fitted in display colours (2026-10-15): the recordings are HDR10 and had been
                // decoded as if sRGB, which raised every dark level. Measured over five recordings
                // decoded correctly (docs/1, docs/2, IMG_6756, JGEY2190, LOLL8185; 117 rest moments,
                // .local/sxs/levels.py): iOS's bar at rest 33 (ours read 46 at lift 45/255), its
                // resting pill 15 (ours 27 at .42 black), its held bar 43.5 (ours 53).
                // Page text under the bar reads as one soft copy, as iOS's does ([GlassStyle.softDetail]).
                // Its rounded ends lens what lies beyond them (GlassStyle.capFold, 18 October; IMG_7049).
                // Its rim line in display colours (#96, 26 October, IMG_6756 34.3 s): 99, 79, 63, 45 inward over an
                // interior of 37, top and bottom alike; the recorded-space halving read 70, 66, 55, 44. Specular 0.25
                // (was 0.125 after the halving) reads 98, 88, 65, 45. The bar's line inside a held lens is iOS's too.
                bar = recorded.bar.copy(tintLift = 32f / 255f, softDetail = 1f, capFold = 1f, specular = 0.25f),
                // Pass 16 (all five recordings, catalogued from the touches the bar shows): iOS's held
                // bar 45-47 (ours 42-43 at 10/255), its held lens on a black page only ~2 above its
                // bar (ours +12: 55 against 48-49), its resting pill 10 on black pages and 15 on
                // docs/2's Calls page (ours 14-15 and 15 at .55 black). The lens's grey falls from
                // 0x3A to 0x2B; the pill's black rises to .58, between the two pages (30 Calls-page
                // moments at 0, 14 black-page moments at +4).
                heldLift = 13f / 255f,
                touchLift = true,
                // The icons stay put when the bar is touched; only the glass grows (18 October).
                itemsFollowBarPress = false,
                // 26 October (#94): the tapped selector folds again in transit. iOS stretches the ink
                // it carries into the rim (dots into teardrops along the top, the label streaked
                // along the bottom, docs/1 0.95-1.12 s); held optics instead cut the label at the
                // bottom rim. The 18 October smear came from the full-band mirror (1.0, gain 0.4);
                // the narrow stretch (0.3, gain 0.15) clears before landing.
                // Pass 18: both are clear glass with an offset, not a tint. Over a black page that
                // reads the same as a tint; over content it does not. The resting pill shows the bar
                // less 22/255, as the inset was first measured ("0.857 of the bar minus 17/255", in
                // the sRGB-decoded space): docs/2's Calls page under it p10/50/75 12/29/47 against
                // iOS's 15/25/51 (14/21/29 at .58 black), black 10 against 10. The held lens shows
                // the bar less 3/255: IMG_6756's black page 48/49 against 48/49. (Pass 18 added 0.30 of
                // the raw page to reach docs/2's percentiles; scored by region on 17 October that drew the
                // page's text under the lens sharp and white, p90 155 against iOS's 102, where iOS's lens
                // veils it as the bar does: without it 91, black page unchanged.) The share returns
                // blurred ([GlassStyle.rawBlur], 17 October): iOS's lens reads ~10 above ours wherever
                // the page has content and the same over black, which a blurred page term does and a
                // tint or glow cannot. Scored by region (.local/sxs/hazescore.py, docs/2 5.75 s and
                // IMG_6756 35.47 s), iOS / ours: text p50 82/83, p90 102/99, bottom band 75/70, black
                // interior 47/48, top band 64/53; five regions 5.6 rms (9.7 without the share).
                pill = recorded.pill.copy(tint = Color.Transparent, tintLift = -22f / 255f),
                // The held lens's rim band at 0.3 of the body's own (18 October): with the full band the
                // bar's straight edges and rounded end showed through as a second capsule inside the
                // lens ("two glasses", the owner's device check); iOS's lens fills with the bar nearly
                // to its rim and bends what lies beyond into a thin crescent (IMG_6756 35.47 s, docs/2
                // 5.75 s). Region levels move by 2 at most (top band 53 -> 51). Its raw page then wider and
                // stronger (0.5 at 50 dp, was 0.22 at 20 dp; 18 October): top band 60 against iOS's 64 (51),
                // bottom 73/75, text p90 / p50 101/102 and 86/82, black 48/47; five regions 2.8 rms (6.6).
                // 26 October (#77, owner: "check which one looks visually more close to the ios
                // references"): the band is back to 1.1 of the body's own. Measured on the bar's straight
                // edge under a held lens, iOS pulls it +10.9 px toward the lens centre on every recording
                // and 0.3 gave -2.2; 1.0 gives +9.8 and ~1.1 matches. Side by side with IMG_6756 35.5 /
                // 43.8 s and LOLL8185 24.3 s, 1.1 reproduces iOS's dark band inside the lens's top and
                // bottom rims, which 0.3 lacks (review/atlas/refraction-band-2026-10-26).
                // Its rim (#92, 26 October, IMG_6756 43.8 s in display colours): over the black page the
                // outline peaks 62-76 and the glow inside falls to 13 within 3 px and 5 by 8 px (ours
                // 63, 47, 13, 9, 8 ... with the glow at 0.4 over 2 dp; it was 1.5 over 5 dp, still 14 at
                // 15 px); at the sides the outline is a dark step, 14 over the bar's 48 (ours 21).
                lens = recorded.lens.copy(tint = Color.Transparent, rawShare = 0.5f, rawBlur = 50.dp,
                    tintLift = -3f / 255f, softDetail = 1f, bodyBandScale = GlassCalmLensBand,
                    heldGlow = 0.4f, heldGlowReach = 2.dp),
            )
        }

        /**
         * Light [Calm], measured on the owner's light recording (IMG_7048, display colours, 17 October;
         * Phone keypad page 36-60 s, App Store 64-100 s, WhatsApp 108-140 s). The bar passes 0.58 of
         * the page plus ~116 (seven page levels 171-245 under the bar, least squares), the dark Phone
         * bar's slope: white at 0.41 plus 10/255. Over white it reads 255. Its rim is a darker line
         * (246 top and bottom, 179-221 at the ends; ours 246 / 234: [GlassStyle.edgeShadow] .12 with
         * the highlight off, which over white only clips) and it casts a faint shadow (253; ours 251-253
         * at [GlassStyle.contactShadow] .2). The resting pill is clear glass 10/255 below the bar: iOS's
         * Phone pill reads 245 on the white keypad page (36-37 s, 58 s; 18 October, was 13/255 = 242 as a
         * compromise with WhatsApp's 236, which [CalmClear] now carries; the App Store's is invisible over
         * white, 255, and its scene sets that).
         * The held lens is clear glass over the bar; its edge is a soft grey band to ~243-246 where dark
         * Calm's is a 65-level line, so its outline tone is 243/255 and a faint inner band
         * ([GlassStyle.innerShadow] .08) reads 245 at the rim (iOS 244, but ~4 px wide where iOS's is
         * ~12); it adds no raw page. It casts a tight shadow offset under itself (iOS: none above the
         * lens, 242 for ~15 px below it, fading by ~36 px; [GlassStyle.contactShadow] .45 at 6 dp blur,
         * 5 dp down, half of it inside the lens: profile rms 2.6 levels over the page above, the overhang
         * and below, against 4.2 without a shadow; IMG_7048 38.5 / 42 s, 18 October). The bar lifts for any touch, page text under
         * bar and lens is one soft copy, and tap optics follow dark Calm (behaviour, not levels).
         * [CalmRecordedSpace] (false) keeps the light preset this replaced.
         */
        private fun calmLight(recorded: GlassTabBarStyle): GlassTabBarStyle = recorded.copy(
            bar = recorded.bar.copy(tint = Color.White.copy(alpha = 0.41f), tintLift = 10f / 255f, softDetail = 1f,
                edgeShadow = 0.12f, specular = 0f, bevelPeak = 0f, contactShadow = 0.2f, capFold = 1f,
                // Its rounded ends' outline darker than its runs (GlassStyle.capEdgeShadow, 18 October): iOS
                // 178-182 at the outermost point, ours 177-186 (was 236-241); left end 12 levels rms (45).
                capEdgeShadow = 2f),
            touchLift = true,
            itemsFollowBarPress = false,
            tapTransitFold = 0f,
            pill = recorded.pill.copy(tint = Color.Transparent, tintLift = -10f / 255f),
            lens = recorded.lens.copy(tint = Color.Transparent, rawShare = 0f, rawBlur = 0.dp, tintLift = 0f,
                softDetail = 1f, heldEdgeTone = 243f / 255f, innerShadow = 0.08f, contactShadow = 0.45f, bodyBandScale = GlassCalmLensBand,
                contactShadowInside = 0.5f, contactShadowBlur = 6.dp, contactShadowOffset = 5.dp),
            tapTintFade = 1f, tapSelectedScale = 1.15f, tapOptics = 1f, tapOpticsFold = 1f,
        )

        /**
         * [Calm] with the clearer bar of WhatsApp's and the App Store's iOS 26 tab bars. Those
         * bars pass about 0.9 of the page under them where the Phone bar, which [Calm] is fitted
         * to, passes 0.59; over a black page both read the same. Measured in display colours on
         * the owner's recordings (17 October): WhatsApp's bar 43 over its chats page (10) and 54
         * over its grey settings card (23), the App Store's 32 over black and 57 over its card
         * (28); its resting pill 19 and 30 over the same pages. The bar is black at
         * [clearBarOpacity] over the Phone bar's 32/255 lift; the pill and held lens look through
         * the bar as in [Calm], so they follow it. Over text iOS's App Store bar compresses
         * (band p50 / p75 69 / 80 where a plain 0.9 gave 73 / 107) and shows the text as one faint,
         * soft copy: [GlassStyle.legibility] 2 darkens the bar where the page under it has contrast
         * and leaves a plain card alone, and a 7 dp blur softens the text, which gives 68 / 79
         * (18 October; legibility 2.5 at the Phone bar's 1.5 dp blur gave 67 / 98 with dark halos). In light appearance the
         * bar is [Calm]'s (fitted on the App Store's cards) and the resting pill is WhatsApp's, 19/255
         * below the bar (iOS 236 over its white page, IMG_7048 108-140 s; 18 October). The App Store's
         * light pill is invisible over white: give it a `tintLift` of 0.
         */
        fun CalmClear(dark: Boolean = true): GlassTabBarStyle = Calm(dark).let { calm ->
            if (!dark) calm.copy(pill = calm.pill.copy(tintLift = -19f / 255f))
            else calm.copy(bar = calm.bar.copy(tint = Color.Black.copy(alpha = clearBarOpacity),
                legibility = 2f, blurRadius = 7.dp))
        }

        /** Black opacity of [CalmClear]'s bar, fitted over the four page levels its notes list. */
        internal const val clearBarOpacity: Float = 0.10f

        /**
         * [Calm] as calibrated before 15 October 2026, against the owner's recordings decoded as
         * if they were sRGB (they are HDR10): every dark level about 10-13 grey levels too light
         * on a real display. Kept to reproduce the earlier passes and their evidence.
         */
        fun CalmRecordedSpace(dark: Boolean = true): GlassTabBarStyle = V3(
            dark = dark, spec = GlassSelectorSpec(response = GlassResponse.Calm),
        ).let { v3 -> v3.copy(
            // The held lens's rim over black in video2 (x 190, 1.7 s): an outer edge line of 62,
            // then a glow fading inward 29, 21, 17, 14, 11, 9 across the band; V3 read 88 and
            // then black (specular .49 at .2 read 27). Now 63, then 29, 16, 13, 11, 10, 8. The
            // bright inner line the owner saw was the bar's own rim seen through the lens.
            // Inside the lens the page shows through crisply: text +27 to +37 over the interior
            // (p95/p99 at 1.7 s) from 133-level page text, about 0.28 of the raw page; V3's .01
            // left it as the bar's frost.
            // The outline is a ~65-level line over any ground (video2 held/moving lens, 14 October):
            // drawn as such (heldEdgeTone) with the additive lobe lowered, so it is no white ring.
            // The inward glow is a floor (heldGlowFloor): over the black page it is the measured
            // 36, 22, 20 ... 9 band (ours 26, 25, 22 ... 10 at 1.5, 5 dp), over the bar it adds
            // nothing (pass 13; pass 12 had turned it off because added over the bar it drew
            // bright lines on a travelling lens). Raw page share .05, re-scored in pass 13 on the
            // page text under the held lens at 1.8 s: p95/p99 82/105 against iOS's 84/109 (.28
            // read 108/112 once the lens looked through the bar, which already shows the page).
            lens = v3.lens.copy(specular = 0.08f, heldGlow = 1.5f, heldGlowReach = 5.dp, rawShare = 0.22f,
                // A grey veil over what the lens shows (tint .55 of 0x3A): iOS's held lens pulls the
                // page under it toward a mid grey, so the avatar under it reads 80 where a clear lens
                // read 98; with the raw share at .22 the text keeps its edge (p95/p99 80/105 against
                // 84/109) and the plain interior is 62 against 64 (video2 1.8 s, pass 14).
                tint = Color(0xFF3A3A3A).copy(alpha = 0.55f),
                heldEdgeTone = 65f / 255f, heldGlowFloor = 1f),
            // Measured over the black page of the owner's primary references (docs/1.MP4,
            // docs/2.MP4, recorded brightness space, 2026-10-13): bar 45 at rest and 53 held,
            // resting selector 24-30 (about .58 of the bar). V3's 32/44 and "0.5 x bar - 10"
            // came from older stills and left the resting selector nearly black (6-8).
            // No straight-run fold on the bar: on a 62 pt bar the folds from both edges cover
            // most of its height and doubled, reversed page text into a garbled smear. Both
            // recordings show one soft, legible copy of the text beneath (13 October).
            // The bar's rim: video2 at rest peaks +37 over its interior at the outermost pixel and
            // fades over ~4 px (82, 72, 64, 53 on 45); the measured in-app bead gave +72 one
            // pixel in, which the held lens also shows through itself as a bright inner line.
            bar = (if (dark) v3.bar.copy(tintLift = 45f / 255f) else v3.bar).let {
                it.copy(edgeFold = 0f, bevelPeak = it.bevelPeak * .5f, specular = it.specular * .5f)
            },
            heldLift = if (dark) 8f / 255f else v3.heldLift,
            pill = if (dark) v3.pill.copy(tint = Color.Black.copy(alpha = 0.42f), tintLift = 0f) else v3.pill,
            // Original Phone ordinary hold: bar186 ->196px. Reuse the rounded 1.05 press
            // from Measured(), shared with visible ink using Calm timing. Generic 3%/2dp
            // feedback undershot it; weakening press is not a correction to drag strain.
            heldScale = 1f,
            deformItemsWithBar = true,
            // New dark Phone tap patch/icon evidence. Light and historical presets are unchanged.
            tapTintFade = if (dark) 1f else 0f,
            tapSelectedScale = if (dark) 1.15f else 1f,
            tapOptics = if (dark) 1f else 0f,
            tapOpticsFold = if (dark) 1f else 0f,
            lensInkAll = true,
            barInteraction = GlassInteraction.Calm.copy(
                pressScale = 1.05f, pressGrowth = 0.dp,
                pullShape = GlassPullShape.AreaPreserving,
                // V3 already applies its measured heldLift to the bar's material. Adding
                // the generic control lift again changed the44-level Phone plateau to54.
                pressLift = 0f,
                // No reach toward the finger: iOS's held bar grows evenly (its far end +24-28 px,
                // its finger end about the same under the lens), and its lens, centred on the
                // pressed tab, is what extends toward the finger (video2 1.8 s; pass 13). The
                // .03 reach of pass 7 pushed our bar 13 px past the lens's cap.
                pressReach = 0f,
                // iOS's bar snaps back after lift (0.12 s, ~10% overshoot) and parts from the button.
                releaseStiffness = if (dark) 530f else 0f, releaseDamping = if (dark) .6f else 1f,
                // Once its lens is dragged, iOS's bar keeps about 10 px of growth a side.
                dragPressShare = if (dark) .7f else 1f,
            ),
        ) }
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
                // The legacy resting-corner map: the tab bar's caps were scored against the recordings with it
                // and carry their own cap lens (capFold); toolbar() itself moved to the measured map (#97).
                bar = GlassStyle.toolbar(dark = dark, tintAmount = tintAmount).copy(restMap = GlassRestMap.Legacy),
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
         * [Dark] with iOS 27's rim, measured (`docs/research/ios27-measurements-raw.md`, 4.5 and
         * section 5, on iOS 27's 44 pt navigation buttons at 3x):
         * - the dark contour outside the outline ([GlassStyle.outerContour]): 0.61 of the backdrop at
         *   the unlit sides in a dark appearance, zero at the lit poles;
         * - the lit edge: [Dark]'s bar ([GlassStyle.DarkChrome]) evaluates to a peak of 0.213 in the
         *   outermost pixel and halves 1.8 px in, where iOS 27 measures 0.256 and 2.34-2.41 px, so the
         *   bar's specular and bevel are scaled by those ratios;
         * - no dispersion (iOS 27's glass measures none to 0.002 px).
         *
         * The light was already straight overhead ([light]), as measured. The lens keeps [Dark]'s
         * measured held optics and takes the contour and the zero dispersion. Until 19 October this
         * preset held guessed numbers; these replace them.
         *
         * Note also that the 2026 release replaces the earlier Clear/Tinted toggle with a
         * continuous transparency slider whose default is its midpoint, so there is no longer
         * one correct opacity to match. Treat [GlassStyle.tint] as the host's to set.
         */
        val Ios27: GlassTabBarStyle = Dark.copy(
            bar = Dark.bar.copy(
                specular = Dark.bar.specular * (0.256f / 0.213f),
                bevel = Dark.bar.bevel * (2.37f / 1.8f),
                outerContour = 0.61f,
                dispersion = 0f,
            ),
            lens = Dark.lens.copy(outerContour = 0.61f, dispersion = 0f),
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

/**
 * A separate glass control beside a [GlassTabBar]'s trailing end, such as a search button.
 *
 * The bar does not draw it. It reaches for it: the bar's outline is a smooth union with the
 * accessory's circle, [fusion] wide, so the bar's own press growth closes [gap] and the two
 * join through a liquid neck, then part again on release. This is what the iOS 26 Phone tab bar
 * does with its search button (measured 2026-10-13: an 8 pt gap and a neck about 17 pt tall at
 * full press; the neck carries the bar's material up to the button's edge).
 *
 * Lay the accessory out yourself, vertically centred on the bar, [gap] beyond its trailing end,
 * [size] square, and compose it after the bar so its own glass draws on top. A [fusion] below
 * twice the [gap] keeps the two apart at rest.
 *
 * [yieldsToLens] (opt-in): a formed pose selector pressing into the accessory's end takes the
 * neck's place, so the bar and the button part as the lens arrives rather than when the finger
 * lifts. Observed in video2 (2026-10-14): on every throw into the search-button end the neck
 * disappears as the lens reaches the end (12.43 s, before the 12.455 s lift). Authored ramp.
 */
@Immutable
class GlassTabBarAccessory(
    val size: Dp,
    val gap: Dp = 8.dp,
    val fusion: Dp = 6.dp,
    val yieldsToLens: Boolean = false,
    /**
     * With [yieldsToLens], how far a held lens at the accessory's end reaches for it through a neck of
     * its own, taking the place of the bar's: 0 (the default) leaves a gap. iOS 26's Phone bar held at
     * its search-button end shows the lens pinched into the button top and bottom (IMG_7048 light,
     * 38.5 and 42 s, 18 October). Authored strength.
     */
    val lensFusion: Dp = 0.dp,
    /**
     * The accessory's own press and pull, published by its [liquidGlass] (same link): the bar's
     * outline reaches for where the accessory is drawn, so pressing it closes the gap through a
     * neck and pulling it away breaks the neck, as on iOS. Null keeps the accessory's laid-out circle.
     */
    val link: GlassAccessoryLink? = null,
) {
    override fun equals(other: Any?): Boolean =
        other is GlassTabBarAccessory && size == other.size && gap == other.gap && fusion == other.fusion &&
            yieldsToLens == other.yieldsToLens && lensFusion == other.lensFusion && link === other.link

    override fun hashCode(): Int = ((((size.hashCode() * 31 + gap.hashCode()) * 31 + fusion.hashCode()) * 31 +
        yieldsToLens.hashCode()) * 31 + lensFusion.hashCode()) * 31 + (link?.hashCode() ?: 0)
}

/**
 * How much of the accessory's neck a formed pose selector has taken over, 0 to 1: its formation
 * times how far its trailing-end edge has reached the bar's end, ramped over a tenth of the bar
 * height either side. Zero for every other selector.
 */
internal fun glassAccessoryYield(formation: Float, endEdge: Float, barWidth: Float, barHeight: Float): Float {
    val span = (.1f * barHeight).coerceAtLeast(1e-3f)
    val reach = ((endEdge - (barWidth - span)) / (2f * span)).coerceIn(0f, 1f)
    return formation.coerceIn(0f, 1f) * reach * reach * (3f - 2f * reach)
}

/**
 * The selector's material for a formation and tap transit appearance. Optics may form further
 * than geometry: a moving resting selector shows the lens's refraction and rim at its resting
 * size ([GlassTabBarStyle.tapOptics]) while its pigment fades ([GlassTabBarStyle.tapTintFade]).
 */
@OptIn(LiquidGlassDiagnosticApi::class)
internal fun glassSelectorMaterial(style: GlassTabBarStyle, formation: Float, transitAppearance: Float,
                                   heldTravel: Float = 0f): GlassStyle {
    val pigment = glassTransitStyle(lerpGlassStyle(style.pill, style.lens, formation), transitAppearance, style.tapTintFade)
    if (style.tapOptics <= 0f) return pigment
    // Optics follow the transit appearance; tint keeps its own measured fade and recovery, so
    // adding refraction does not delay the pigment's return after landing.
    val opticalLift = max(formation, transitAppearance * style.tapOptics).coerceIn(0f, 1f)
    val material = lerpGlassStyle(style.pill, style.lens, opticalLift).copy(tint = pigment.tint, tintLift = pigment.tintLift)
    // A moving, unheld selector folds its band (see [glassSelectorFold]) instead of the held map.
    // The rim's colour split grows with motion, as iOS's does (#73): the still value on a resting
    // held lens, the moving value on a tap in transit (the shader scales it by the fold) and on a
    // held lens at full travel.
    val fringe = LiquidGlassDiagnostics.selectorFringe
    val still = fringe?.getOrNull(0) ?: GlassSelectorRimDispersion
    val moving = fringe?.getOrNull(1) ?: GlassSelectorRimFringeMoving
    val foldCorners = fringe?.getOrNull(2) ?: GlassSelectorTransitFoldCorners
    val rimMirror = fringe?.getOrNull(3) ?: GlassSelectorRimMirror
    val rimGain = fringe?.getOrNull(4) ?: GlassSelectorRimGain
    val transitSoft = fringe?.getOrNull(5) ?: GlassSelectorTransitRimSoft
    val heldMoving = fringe?.getOrNull(6) ?: GlassSelectorHeldFringeMoving
    val fold = glassSelectorFold(style, formation, transitAppearance, heldTravel)
    return if (fold > 0f)
        material.copy(profile = if (formation >= 0.5f) material.profile else GlassProfile.Measured, foldAtCorners = foldCorners, rimMirror = rimMirror, rimMirrorGain = rimGain, dispersion = moving, inkDispersion = moving * (fringe?.getOrNull(7) ?: GlassSelectorTransitInkSplitShare),
            rimSoftness = if (transitSoft > 0f) transitSoft.dp else material.rimSoftness)
    // A held lens keeps the measured held map when still and blends toward the same rim mirror
    // at its caps as it travels ([glassSelectorFormation] carries the travel amount).
    else if (style.tapOpticsFold > 0f && (formation >= 0.5f || heldTravel > 0f)) {
        val split = still + (heldMoving - still) * heldTravel.coerceIn(0f, 1f)
        material.copy(foldAtCorners = 1f, rimMirror = rimMirror, rimMirrorGain = rimGain, dispersion = split, inkDispersion = split * GlassSelectorInkSplitShare)
    }
    else material
}

/** Rim-mirror band of a moving Calm selector (authored, 2026-10-14; 26 October 0.3, was 1.0: iOS's
 * transit stretches content about 15 px into the rim, docs/1 0.95-1.12 s, where the full band mirrored the
 * label away). */
internal const val GlassSelectorRimMirror = 0.3f
/** Radial draw of the moving selector's mirrored caps (authored, 2026-10-14 second pass; 26 October 0.15,
 * was 0.4: near 0 the rim repeats the band's inner content, a stretch rather than a mirror, as iOS's is). */
internal const val GlassSelectorRimGain = 0.15f
/** The Calm held lens's rim band, as a share of the body's own 0.6 R (#77, 26 October; was 0.3). */
internal const val GlassCalmLensBand = 1.1f
/** The rim split of a still held lens (#73, 26 October 0.08, was 0.05): with the split proportional to the
 * shift (GlassShaderSource) and the spectral rim, against IMG_6756 35.5 / 43.8 s by eye. Authored. */
internal const val GlassSelectorRimDispersion = 0.08f
/** The selector speed at which the held rim split reaches its moving value (#94). Authored. */
internal val GlassSelectorFringeSpeed = 400.dp
/** A held lens's rim split at full travel (#94): a long held drag fringed 0.2-0.6x iOS's at the tap value. */
internal const val GlassSelectorHeldFringeMoving = 0.14f
/** Smear of a tap's stretched rim in transit, dp; 0 keeps the lens's own (#94). */
internal const val GlassSelectorTransitRimSoft = 0f
/** The share of the rim split the selected ink takes: iOS's ink stays clean inside a held lens; the full
 * split drew a dark ring round each icon (LOLL8185 22.2 s, 26 October). Authored. */
internal const val GlassSelectorInkSplitShare = 0.3f
/** Authored correction scored on all four #124 defect windows (29 October): independent
 * ink-channel sampling manufactures neon green from blue glyphs. Keep transit ink sharp;
 * the panel's material dispersion and the held ink share remain independent. */
internal const val GlassSelectorTransitInkSplitShare = 0f
/** The rim split at full motion: a tap in transit, a held lens at full travel (#73). */
internal const val GlassSelectorRimFringeMoving = 0.14f
/** How far the unheld transit fold is confined to the caps (1) rather than running along the
 * whole rim (0). iOS's tapped lens also folds a little along its straight runs, the Keypad dots
 * stretching up its top rim and the label bending along its bottom one as it lands (video1,
 * 0.95-1.03 s). Authored against that frame sequence (2026-10-14 pass 12): .5 folded the label
 * away and drew the dots into bars, .85 left the runs nearly clean. 26 October 0.2 (was 0.75), with the
 * narrow stretch above: the runs fold as iOS's do without losing the label. */
internal const val GlassSelectorTransitFoldCorners = 0.2f

/** `lensFormation` for the selector: the transit fold while unheld, the held lens's travel
 * (rim-mirror blend at its caps) while held. */
internal fun glassSelectorFormation(style: GlassTabBarStyle, formation: Float, transitAppearance: Float,
                                    heldTravel: Float): Float =
    if (style.tapOpticsFold > 0f && heldTravel > 0f) (heldTravel * style.tapOpticsFold).coerceIn(0f, 1f)
    else glassSelectorFold(style, formation, transitAppearance, heldTravel)

/**
 * The fold strength of a moving, unheld selector's band, for `lensFormation`: 0 unless
 * [GlassTabBarStyle.tapOpticsFold] is on and transit optics lead held formation.
 *
 * Video1/video2 (2026-10-13): at the ends of a travelling lens iOS shows the lens's own nearby
 * content mirrored and wrapped along the rim, blue icon strokes following the cap. That is the
 * measured fold family's outer band (deeper content shown upside down), not the held map, which
 * pulls the exterior in and was measured on stationary held stills.
 */
internal fun glassSelectorFold(style: GlassTabBarStyle, formation: Float, transitAppearance: Float,
                               heldTravel: Float = 0f): Float {
    val fold = if (style.tapTransitFold.isNaN()) style.tapOpticsFold else style.tapTransitFold
    if (style.tapOptics <= 0f || fold <= 0f || (heldTravel > 0f && formation >= 0.5f)) return 0f
    val transit = (transitAppearance * style.tapOptics).coerceIn(0f, 1f)
    return if (transit > formation || (heldTravel == 0f && transit > 0f)) transit * fold.coerceIn(0f, 1f) else 0f
}

/** The gesture's own pointer has lifted: a tap, as opposed to a pointer taken by another node. */
private fun AwaitPointerEventScope.liftedWithoutDrag(id: PointerId): Boolean =
    currentEvent.changes.firstOrNull { it.id == id }?.pressed == false

/** The keyboard-focused tab and its layout (#117). */
internal class GlassFocusRingTarget(val index: Int, val cell: androidx.compose.ui.layout.LayoutCoordinates)

/** The bar's layout, its keyboard-focused tab, if any, and the tab under a hovering pointer, or -1 (#117). */
internal class GlassFocusRingState {
    var bar: androidx.compose.ui.layout.LayoutCoordinates? = null
    /** A pointer pressed the bar since the last key event: the ring stays hidden until the keyboard is used again. */
    var pointerSinceKey by mutableStateOf(false)
    /** A pointer pressed the bar since focus last left it: focus that returns without one is not the pointer's. */
    var pointerSinceBlur = false
    /** Whether a tab of the bar has focus. */
    var barFocused = false
    val target = mutableStateOf<GlassFocusRingTarget?>(null)
    val hovered = androidx.compose.runtime.mutableIntStateOf(-1)
    /** [GlassPoseSpec.hoverScale] of the bar's selector; 1 (off) for every style without it. */
    var hoverScale by androidx.compose.runtime.mutableFloatStateOf(1f)
}

/** Set by [GlassTabBar] so its interactive tabs can report keyboard focus to the bar, which draws the ring. */
internal val LocalGlassFocusRing = androidx.compose.runtime.staticCompositionLocalOf<GlassFocusRingState?> { null }

/** The focus ring for a keyboard-focused tab (#117), around [cell]: a 2 dp capsule, inset 3 dp, in the system
 * accent blue with a dark hairline outside it, so it reads over a light or a dark bar. */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGlassFocusRing(cell: androidx.compose.ui.geometry.Rect) {
    val inset = 3.dp.toPx()
    val stroke = 2.dp.toPx()
    val w = cell.width - 2f * inset
    val h = cell.height - 2f * inset
    if (w <= 0f || h <= 0f) return
    val r = androidx.compose.ui.geometry.CornerRadius(h / 2f)
    drawRoundRect(Color.Black.copy(alpha = .45f), androidx.compose.ui.geometry.Offset(cell.left + inset - 1f, cell.top + inset - 1f),
        androidx.compose.ui.geometry.Size(w + 2f, h + 2f), r, style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
    drawRoundRect(GlassFocusRingColor, androidx.compose.ui.geometry.Offset(cell.left + inset + stroke / 2f, cell.top + inset + stroke / 2f),
        androidx.compose.ui.geometry.Size(w - stroke, h - stroke), r, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
}

/** iOS's keyboard focus ring colour (system blue, dark appearance). */
internal val GlassFocusRingColor = Color(0xFF0A84FF)
/** [GlassPoseSpec.heldInkLift]: adds [amount] of light to every channel of the ink drawn here. A
 * colour filter leaves transparent pixels transparent, so only the ink brightens. */
private fun Modifier.glassInkLift(amount: Float): Modifier = if (amount <= 0f) this else drawWithContent {
    drawIntoCanvas { canvas ->
        val paint = androidx.compose.ui.graphics.Paint().apply {
            colorFilter = androidx.compose.ui.graphics.ColorFilter.lighting(Color.White, Color(amount, amount, amount))
        }
        canvas.saveLayer(androidx.compose.ui.geometry.Rect(androidx.compose.ui.geometry.Offset.Zero, size), paint)
        drawContent()
        canvas.restore()
    }
}
