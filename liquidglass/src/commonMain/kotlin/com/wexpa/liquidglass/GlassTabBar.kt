package com.wexpa.liquidglass

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
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
 *    tab it is over, and commits once, on release — a flick carries it to the next tab.
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
        val barWidthPx = with(density) { maxWidth.toPx() }
        val barHeightPx = with(density) { maxHeight.toPx() }
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

        // Hold the preview until the real selection catches up, or the tab under the finger
        // would blink back to the old one for the frame before navigation lands.
        LaunchedEffect(selected, dragIndex) {
            if (dragIndex >= 0 && dragIndex == selected) dragIndex = -1
        }

        // The selector's position. A plain float rather than an Animatable, because a drag
        // callback cannot suspend and the lens has to be where the finger is on the same frame.
        var offsetX by remember { mutableFloatStateOf(restingAt) }
        var settleVelocity by remember { mutableFloatStateOf(0f) }
        var settleTarget by remember { mutableFloatStateOf(restingAt) }
        var motion by remember { mutableStateOf<Job?>(null) }
        val scope = rememberCoroutineScope()
        val press = rememberGlassPressSource()
        var held by remember { mutableStateOf(false) }
        var dragging by remember { mutableStateOf(false) }
        var liveVelocity by remember { mutableFloatStateOf(0f) }

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

        // Only follow the selection when the finger is not the one moving it.
        LaunchedEffect(restingAt, motionEnabled) {
            if (held || dragging || dragIndex >= 0) return@LaunchedEffect
            // A release has already aimed here and is carrying the fling velocity with it.
            if (settleTarget == restingAt) return@LaunchedEffect
            if (!motionEnabled) {
                motion?.cancel()
                settleTarget = restingAt
                offsetX = restingAt
            } else {
                settleTo(restingAt, 0f, style.settle)
            }
        }

        // Inset to lens and back, size and material on one spring so the rim cannot arrive
        // after the shape.
        val lift by animateFloatAsState(
            targetValue = if (held) 1f else 0f,
            animationSpec = when {
                !motionEnabled -> snap()
                held -> style.form
                else -> style.subside
            },
            label = "glass_tab_bar_lift",
        )
        val pillHeight: Dp = with(density) { maxHeight } - style.pillInset * 2
        val lensHeight: Dp = with(density) { maxHeight } + style.lensOverflow * 2
        val selectorWidth: Dp = lerp(slotWidthDp, slotWidthDp + style.lensExtraWidth, lift)
        val selectorHeight: Dp = lerp(pillHeight, lensHeight, lift)
        val selectorWidthPx = with(density) { selectorWidth.toPx() }
        val selectorHeightPx = with(density) { selectorHeight.toPx() }
        val selectorRisePx = with(density) { style.lensRise.toPx() } * lift
        val selectorStyle = lerpGlassStyle(style.pill, style.lens, lift)

        fun selectorLeft() = offsetX - selectorWidthPx / 2f
        fun selectorTop() = (barHeightPx - selectorHeightPx) / 2f - selectorRisePx

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
            targetValue = gelTarget,
            animationSpec = style.gelSpring,
            label = "glass_tab_bar_gel",
        )

        // Clamped to the lens: a finger that grabbed away from it still lights the edge it is
        // pulling on, instead of aiming the glow off-panel where it simply vanishes.
        fun localPress(x: Float, y: Float) = Offset(
            (x - selectorLeft()).coerceIn(0f, selectorWidthPx),
            (y - selectorTop()).coerceIn(0f, selectorHeightPx),
        )

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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(geometry, motionEnabled) {
                    awaitEachGesture {
                        // The item under the finger consumes the down, so this must not
                        // require an unconsumed one.
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)

                        var lastIndex = geometry.nearestIndex(down.position.x)
                        try {
                            motion?.cancel()
                            held = true
                            dragIndex = lastIndex
                            press.press(localPress(down.position.x, down.position.y))
                            if (motionEnabled) {
                                settleTo(geometry.centreOf(lastIndex), 0f, style.arrive)
                            } else {
                                settleTarget = geometry.centreOf(lastIndex)
                                offsetX = settleTarget
                            }

                            val grabbed = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                                change.consume()
                            }
                            if (grabbed == null || !motionEnabled) {
                                // Lifted without dragging: a tap on whatever the lens went to.
                                // The item's own clickable commits the same index; a host's
                                // selection should be idempotent, and both may fire.
                                select(lastIndex)
                                return@awaitEachGesture
                            }

                            motion?.cancel()
                            dragging = true

                            // The lens keeps whatever offset from the finger it had when the drag
                            // began, capped at half its own width: grabbing the lens tracks
                            // exactly, grabbing anywhere else brings it to the finger. Measured
                            // from the DOWN, not from where slop was crossed — slop can be tens
                            // of dp, and a drag that loses it cannot reach the outermost tab.
                            val grabOffset = (offsetX - down.position.x)
                                .coerceIn(-selectorWidthPx / 2f, selectorWidthPx / 2f)

                            fun moveTo(x: Float, y: Float) {
                                offsetX = (x + grabOffset).coerceIn(geometry.firstCentre, geometry.lastCentre)
                                press.press(localPress(x, y))
                                val index = geometry.nearestIndex(offsetX)
                                if (index != lastIndex) {
                                    lastIndex = index
                                    dragIndex = index
                                }
                            }

                            tracker.addPosition(grabbed.uptimeMillis, grabbed.position)
                            moveTo(grabbed.position.x, grabbed.position.y)

                            horizontalDrag(grabbed.id) { change ->
                                tracker.addPosition(change.uptimeMillis, change.position)
                                liveVelocity = tracker.calculateVelocity().x
                                moveTo(change.position.x, change.position.y)
                                change.consume()
                            }

                            val released = tracker.calculateVelocity().x
                            dragging = false
                            liveVelocity = 0f

                            val projected = geometry.projected(lastIndex, released, flingVelocity)
                            // Settle first, so the effect watching the selection sees a spring
                            // already aimed at the right place and leaves its velocity alone.
                            settleTo(geometry.centreOf(projected), released, style.settle)
                            dragIndex = projected
                            select(projected)
                        } finally {
                            // However the gesture ends — lift, cancel, or the node going away —
                            // the finger is no longer on the glass.
                            held = false
                            dragging = false
                            liveVelocity = 0f
                            press.release()
                        }
                    }
                },
        ) {
            // The bar: plate and items, recorded so the lens can look through them. The lens is
            // a sibling of this box, not a child, or it would sample itself.
            Box(modifier = Modifier.matchParentSize().liquidGlassSource(barState)) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .liquidGlass(
                            state = state,
                            shape = barShape,
                            style = style.bar,
                            light = style.light,
                            // The bar itself does not respond to touch on the reference: no
                            // scale, no glow. The lens is the response.
                            interaction = null,
                            materialize = materialize,
                        ),
                )
                ItemRow(
                    count = itemCount,
                    inset = style.contentPadding,
                    selectedFor = { index -> !lensCarriesInk && index == activeIndex },
                    onClick = { index -> select(index) },
                    item = item,
                )
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
                            ItemRow(
                                count = itemCount,
                                inset = style.contentPadding,
                                selectedFor = { true },
                                onClick = null,
                                item = item,
                            )
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
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
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
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onClick(index) }
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                item(index, selectedFor(index))
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
    val slotWidth: Float get() = (barWidth - inset * 2f) / count
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
    /** How much wider than one item the held lens is: 16pt measured. */
    val lensExtraWidth: Dp = 16.dp,
    /** Vertical offset of the held lens. The native reference is centred; the earlier video read 5pt high. */
    val lensRise: Dp = 0.dp,
    /** How the lens responds under the finger. No scale — the reference does not bounce — and a faint centre glow. */
    val lensInteraction: GlassInteraction = GlassInteraction(pressScale = 1f, illumination = 0.5f, gel = false),
    /** The lens forms in about a tenth of a second: two frames at 30fps in the reference. */
    val form: AnimationSpec<Float> = spring(dampingRatio = 0.80f, stiffness = 1400f),
    /** And subsides over about a third: ten to fourteen frames. */
    val subside: AnimationSpec<Float> = spring(dampingRatio = 0.80f, stiffness = 600f),
    /** Touch-down sends the selector to the finger's tab: stiff, so it is there before the drag. */
    val arrive: AnimationSpec<Float> = spring(dampingRatio = 0.85f, stiffness = 1400f),
    /** Release settles it onto a tab carrying whatever velocity the finger left it with. */
    val settle: AnimationSpec<Float> = spring(dampingRatio = 0.72f, stiffness = 520f),
    /** Release speed past which the selector carries on to the next tab. */
    val flingVelocity: Dp = 420.dp,
    /** Peak stretch along the travel, as a fraction, reached at [gelReference] per second. */
    val gel: Float = 0.06f,
    val gelReference: Dp = 1200.dp,
    val gelSpring: AnimationSpec<Float> = spring(dampingRatio = 0.6f, stiffness = 500f),
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

        /** A dark-appearance bar, every number measured off the reference. */
        val Dark = GlassTabBarStyle()

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
