package com.wexpa.liquidglass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * A slider whose knob is a shape at rest and Liquid Glass for the duration of the drag.
 *
 * Apple names sliders as the canonical case of this rule: the knob "transforms into Liquid
 * Glass during interaction", and reverts to a plain shape afterwards. That is the same rule
 * [GlassTabBar]'s indicator follows, and it is implemented the same way here — the knob's style
 * is a morph between [GlassSliderStyle.knobRest] and [GlassSliderStyle.knobHeld], driven by
 * whether a finger is down.
 *
 * Three details are easy to get wrong and are handled here:
 *
 *  - **The knob looks through the track.** It sits above it, so what reaches the eye has already
 *    been through the track's material. The track is recorded as its own source and handed to
 *    the knob as the glass it looks `through`, which is what stops the knob punching a clear
 *    hole in the track down to the app's content.
 *  - **It does not scale-bounce.** The knob grows into glass; it does not pop. The held style is
 *    larger than the resting one, and the press interaction carries no scale of its own.
 *  - **It stretches with momentum.** Apple's 2025 sliders "preserve momentum and stretch". The
 *    knob elongates along the direction of travel in proportion to its speed and relaxes back,
 *    which is the whole of the liquid in a slider.
 *
 * @param value the current position, within [valueRange].
 * @param onValueChange called continuously while dragging.
 * @param motionEnabled pass `false` for Reduce Motion: the knob still becomes glass, but it
 *   arrives without spring or stretch.
 */
@Composable
fun GlassSlider(
    state: LiquidGlassState,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    style: GlassSliderStyle = GlassSliderStyle.Dark,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    motionEnabled: Boolean = true,
    materialize: Float = 1f,
) {
    val trackState = rememberLiquidGlassState(
        background = state.background,
        inversion = state.inversion,
        frost = state.frost,
        contrast = state.contrast,
    )
    val press = rememberGlassPressSource()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier = modifier.height(maxOf(style.knobSize, style.trackHeight)),
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val knobPx = with(density) { style.knobSize.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        // The knob's centre never leaves the track, so its travel is the track minus itself.
        val travel = (widthPx - knobPx).coerceAtLeast(0f)

        var held by remember { mutableStateOf(false) }
        val lift = remember { Animatable(0f) }
        val gel = remember { Animatable(0f) }
        // Where the knob actually is, in px of travel. Follows the value, but during a drag it
        // is the drag that leads and the value that follows.
        val knobX = remember { Animatable(fraction * travel) }

        LaunchedEffect(held, motionEnabled) {
            if (!motionEnabled) {
                lift.snapTo(if (held) 1f else 0f)
            } else {
                lift.animateTo(if (held) 1f else 0f, if (held) style.form else style.subside)
            }
        }
        LaunchedEffect(fraction, travel, held) {
            if (!held) {
                val target = fraction * travel
                if (motionEnabled) knobX.animateTo(target, style.settle) else knobX.snapTo(target)
            }
        }

        val knobStyle = lerpGlassStyle(style.knobRest, style.knobHeld, lift.value)
        val knobLeft = knobX.value.coerceIn(0f, travel)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(travel, motionEnabled) {
                    if (travel <= 0f) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        held = true
                        press.press(Offset(knobPx / 2f, knobPx / 2f))
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)

                        fun commit(x: Float) {
                            val clamped = (x - knobPx / 2f).coerceIn(0f, travel)
                            scope.launch { knobX.snapTo(clamped) }
                            onValueChange(valueRange.start + (clamped / travel) * span)
                        }
                        commit(down.position.x)

                        try {
                            horizontalDrag(down.id) { change ->
                                tracker.addPosition(change.uptimeMillis, change.position)
                                commit(change.position.x)
                                if (motionEnabled) {
                                    val speed = abs(tracker.calculateVelocity().x)
                                    val reference = with(density) { style.gelReference.toPx() }
                                    val target = (speed / reference).coerceIn(0f, 1f) * style.gel
                                    scope.launch { gel.animateTo(target, style.gelSpring) }
                                }
                                change.consume()
                            }
                        } finally {
                            held = false
                            press.release()
                            scope.launch { gel.animateTo(0f, style.gelSpring) }
                        }
                    }
                },
        ) {
            // The track, recorded so the knob can look through it.
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(style.trackHeight)
                    .liquidGlassSource(trackState),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .liquidGlass(
                            state = state,
                            shape = RoundedCornerShape(percent = 50),
                            style = style.track,
                            light = style.light,
                            interaction = null,
                            materialize = materialize,
                        ),
                )
                // The filled portion, behind the knob and inside the track's own shape.
                if (style.fill.alpha > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(with(density) { (knobLeft + knobPx / 2f).toDp() })
                            .background(style.fill, RoundedCornerShape(percent = 50)),
                    )
                }
            }

            // The knob, a sibling so it can stand proud of the track without being clipped by it.
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        IntOffset(
                            knobLeft.roundToInt(),
                            ((heightPx - knobPx) / 2f).roundToInt(),
                        )
                    }
                    .wrapContentSize(Alignment.TopStart, unbounded = true),
            ) {
                Box(
                    modifier = Modifier
                        .size(style.knobSize)
                        .graphicsLayer {
                            scaleX = 1f + gel.value
                            scaleY = 1f - gel.value
                        }
                        .liquidGlass(
                            state = state,
                            shape = RoundedCornerShape(percent = 50),
                            style = knobStyle,
                            light = style.light,
                            interaction = if (motionEnabled) style.knobInteraction else GlassInteraction.ReducedMotion,
                            pressSource = press,
                            through = trackState,
                            materialize = materialize,
                        ),
                )
            }
        }
    }
}

/**
 * How a [GlassSlider] looks and moves.
 *
 * The knob's two styles are the whole design. At rest it is a plain shape with no optics at all,
 * for the same reason the tab bar's indicator is: give a resting knob even a shallow refraction
 * and its rim grows a bright ring of compressed track, which is the clearest tell that an
 * implementation is a recreation.
 */
@Immutable
data class GlassSliderStyle(
    /** The track. A frosted inset, not a lens. */
    val track: GlassStyle = GlassStyle.DarkChrome,
    /** The knob before a finger lands: a shape, not a material. */
    val knobRest: GlassStyle = KnobRest,
    /** The knob during the drag. */
    val knobHeld: GlassStyle = KnobHeld,
    val light: GlassLight = GlassLight(0f, -1f),
    val trackHeight: Dp = 16.dp,
    val knobSize: Dp = 30.dp,
    /** The portion behind the knob. Set to [Color.Transparent] for an unfilled track. */
    val fill: Color = Color.White.copy(alpha = 0.20f),
    /** Becoming glass: fast, because the reference is fully formed within two frames. */
    val form: AnimationSpec<Float> = spring(dampingRatio = 0.80f, stiffness = 1400f),
    /** Reverting: slower, over about a third of a second. */
    val subside: AnimationSpec<Float> = spring(dampingRatio = 0.80f, stiffness = 600f),
    /** Following the value when no finger is down. */
    val settle: AnimationSpec<Float> = spring(dampingRatio = 0.82f, stiffness = 700f),
    /** Peak elongation along the direction of travel, as a fraction. */
    val gel: Float = 0.06f,
    /** The speed, in dp per second, at which [gel] reaches its peak. */
    val gelReference: Dp = 1200.dp,
    val gelSpring: AnimationSpec<Float> = spring(dampingRatio = 0.6f, stiffness = 500f),
    /** No scale: the knob grows into glass rather than popping. */
    val knobInteraction: GlassInteraction = GlassInteraction(
        pressScale = 1f,
        illumination = 0.6f,
        gel = false,
    ),
) {
    companion object {
        // Declared before Dark: a companion initialises in declaration order, and a preset that
        // refers to a sibling declared after it reads null. See GlassTabBarStyle.
        /** A flat dark shape. No refraction, no bevel, no bead. */
        val KnobRest: GlassStyle = GlassStyle.DarkChrome.copy(
            blurRadius = 0.dp,
            backdropBlur = 0.dp,
            refractionBand = 8.dp,
            refractionDepth = 0.dp,
            dispersion = 0f,
            mirror = 0f,
            bevelPeak = 0f,
            tint = Color(0xFFF2F2F2).copy(alpha = 0.92f),
            adaptivity = 0f,
            legibility = 0f,
            specular = 0f,
            edgeLight = 0f,
            edgeShadow = 0f,
            fresnel = 0f,
            innerShadow = 0f,
            invertsWithBackdrop = false,
            fallbackSurface = Color(0xF2F2F2F2),
        )

        /** Clear glass with a lit rim and the dark side step, matching [GlassTabBarStyle.HeldLens]. */
        val KnobHeld: GlassStyle = GlassStyle.DarkChrome.copy(
            blurRadius = 0.dp,
            backdropBlur = 0.dp,
            refractionBand = 9.dp,
            refractionDepth = 7.dp,
            bevelPower = 1.5f,
            dispersion = 0.45f,
            mirror = 0f,
            bevel = 1.5.dp,
            bevelPeak = 0f,
            tint = Color.White.copy(alpha = 0.06f),
            adaptivity = 0f,
            legibility = 0f,
            specular = 0.08f,
            specularPower = 4f,
            counterLight = 0.8f,
            edgeLight = 10.8f,
            edgeShadow = 0.08f,
            fresnel = 0f,
            innerShadow = 0f,
            invertsWithBackdrop = false,
            fallbackSurface = Color(0x3DFFFFFF),
        )

        /** Dark appearance, matching [GlassTabBarStyle.Dark]. Must stay last. */
        val Dark: GlassSliderStyle = GlassSliderStyle()
    }
}
