@file:OptIn(ExperimentalMaterial3Api::class)

package com.wexpa.liquidglass.material3

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSliderState
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntOffset
import com.wexpa.liquidglass.CapsuleShape
import androidx.compose.material3.RangeSlider as M3RangeSlider
import androidx.compose.material3.Slider as M3Slider

/**
 * Material 3's `Switch`, iOS style: a 63 x 28 dp capsule track in [colors]' track colours (fading over
 * 420 ms; in a dark scheme the measured iOS ones by default, see [defaultSwitchColors]) and a 37 x 24 dp white knob that becomes a clear glass lens, 1.56 x 1.6 times larger, while
 * pressed. Sizes, travel and timing are measured on iOS 27's switch; the lens strength, the disabled look
 * and [thumbContent] are authored. The same parameters as Material's.
 */
@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    thumbContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    colors: SwitchColors = defaultSwitchColors(),
    interactionSource: MutableInteractionSource? = null,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val travel by animateFloatAsState(if (checked) 1f else 0f, KnobSpec.TravelSpring, label = "switch")
    val target = when {
        !enabled -> if (checked) colors.disabledCheckedTrackColor else colors.disabledUncheckedTrackColor
        checked -> colors.checkedTrackColor
        else -> colors.uncheckedTrackColor
    }
    val track by animateColorAsState(target, tween(KnobSpec.TrackFadeMillis), label = "switchTrack")
    val toggle = if (onCheckedChange != null) Modifier.toggleable(checked, source, null, enabled, Role.Switch, onCheckedChange) else Modifier
    Box(
        modifier.then(toggle).size(KnobSpec.SwitchTrackWidth, KnobSpec.SwitchTrackHeight).background(track, CapsuleShape),
        contentAlignment = Alignment.CenterStart,
    ) {
        // Read in the layout phase, so a travelling knob does not recompose the switch.
        Box(
            Modifier.offset { IntOffset((KnobSpec.KnobInset + KnobSpec.KnobTravel * travel).roundToPx(), 0) },
            contentAlignment = Alignment.Center,
        ) {
            GlassKnob(held = pressed, enabled = enabled)
            thumbContent?.invoke()
        }
    }
}

/**
 * The switch's default track colours. In a dark scheme they are the iOS 27 ones, measured on the native
 * Bluetooth switch (dark appearance): on (47, 210, 88), off (90, 90, 94). No light-appearance switch is
 * recorded, so a light scheme keeps the theme's primary and a faint on-surface.
 */
@Composable
private fun defaultSwitchColors(): SwitchColors {
    val scheme = MaterialTheme.colorScheme
    return if (scheme.isDark) {
        SwitchDefaults.colors(checkedTrackColor = KnobSpec.OnTrackDark, uncheckedTrackColor = KnobSpec.OffTrackDark)
    } else {
        SwitchDefaults.colors(checkedTrackColor = scheme.primary, uncheckedTrackColor = scheme.onSurface.copy(alpha = .16f))
    }
}

/**
 * Material 3's `Slider`, iOS style: a thin 4 dp track running under the thumb's whole travel and a
 * 24 x 16 dp opaque white thumb that does not change while held (measured on Safari's slider; no native
 * thumb-slider recording exists, so the native size is authored). The same parameters as Material's.
 */
@Composable
fun Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(
        activeTrackColor = MaterialTheme.colorScheme.primary,
        inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .16f),
    ),
    interactionSource: MutableInteractionSource? = null,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    // The value overload with custom thumb and track: the same call on Material 3 1.9 and later.
    M3Slider(
        value = value, onValueChange = onValueChange, modifier = modifier, enabled = enabled,
        onValueChangeFinished = onValueChangeFinished, colors = colors, interactionSource = source, steps = steps,
        thumb = { SliderThumb(enabled) },
        track = { GlassSliderTrack(it, valueRange, colors, enabled) },
        valueRange = valueRange,
    )
}

/**
 * Material 3's `RangeSlider`, iOS style: the same thin track, filled between the two thumbs, and
 * two thumbs like [Slider]'s. The second thumb is authored (only one thumb was recorded). The same
 * parameters as Material's.
 */
@Composable
fun RangeSlider(
    value: ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(
        activeTrackColor = MaterialTheme.colorScheme.primary,
        inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .16f),
    ),
) {
    val start = remember { MutableInteractionSource() }
    val end = remember { MutableInteractionSource() }
    M3RangeSlider(
        value = value, onValueChange = onValueChange, modifier = modifier, enabled = enabled,
        valueRange = valueRange, onValueChangeFinished = onValueChangeFinished, colors = colors,
        startInteractionSource = start, endInteractionSource = end,
        startThumb = { SliderThumb(enabled) },
        endThumb = { SliderThumb(enabled) },
        track = { GlassRangeTrack(it, colors, enabled) },
        steps = steps,
    )
}

/** [GlassSliderTrack] for a range: filled between the two thumbs' centres. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlassRangeTrack(state: RangeSliderState, colors: SliderColors, enabled: Boolean) {
    val active = if (enabled) colors.activeTrackColor else colors.disabledActiveTrackColor
    val inactive = if (enabled) colors.inactiveTrackColor else colors.disabledInactiveTrackColor
    val range = state.valueRange
    fun fraction(v: Float) = if (range.endInclusive > range.start)
        ((v - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f) else 0f
    val from = fraction(state.activeRangeStart)
    val to = fraction(state.activeRangeEnd)
    Canvas(Modifier.fillMaxWidth().height(KnobSpec.SliderTrackHeight)) {
        val r = CornerRadius(size.height / 2f)
        val overhang = KnobSpec.SliderThumbWidth.toPx() / 2f
        startToEnd {
            drawRoundRect(inactive, Offset(-overhang, 0f), Size(size.width + 2f * overhang, size.height), r)
            drawRoundRect(active, Offset(from * size.width, 0f), Size((to - from) * size.width, size.height), r)
        }
    }
}

/**
 * iOS's slider track: one thin capsule, filled from the start to the thumb's centre, with no gap
 * around the thumb (Material's track breaks there). 4 dp tall (measured on Safari's web slider, about 3.6 dp if the native one is 1/1.12 of it).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlassSliderTrack(state: SliderState, range: ClosedFloatingPointRange<Float>, colors: SliderColors, enabled: Boolean) {
    val active = if (enabled) colors.activeTrackColor else colors.disabledActiveTrackColor
    val inactive = if (enabled) colors.inactiveTrackColor else colors.disabledInactiveTrackColor
    val fraction = if (range.endInclusive > range.start)
        ((state.value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f) else 0f
    Canvas(Modifier.fillMaxWidth().height(KnobSpec.SliderTrackHeight)) {
        val r = CornerRadius(size.height / 2f)
        // Material lays the track out between the thumb's centre positions (half a thumb in from each
        // end). iOS's capsule runs under the whole thumb, so draw half a thumb past both ends.
        val overhang = KnobSpec.SliderThumbWidth.toPx() / 2f
        startToEnd {
            drawRoundRect(inactive, Offset(-overhang, 0f), Size(size.width + 2f * overhang, size.height), r)
            drawRoundRect(active, Offset(-overhang, 0f), Size(overhang + fraction * size.width, size.height), r)
        }
    }
}

/** Draws [block] from the start edge: mirrored right to left, where Material places the thumbs from the right (#89). */
private inline fun DrawScope.startToEnd(block: DrawScope.() -> Unit) {
    if (layoutDirection == LayoutDirection.Rtl) scale(-1f, 1f) { block() } else block()
}
