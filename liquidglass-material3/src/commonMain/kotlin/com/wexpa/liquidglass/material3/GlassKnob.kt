package com.wexpa.liquidglass.material3

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.wexpa.liquidglass.CapsuleShape
import com.wexpa.liquidglass.Glass
import com.wexpa.liquidglass.glassEffect

/**
 * The numbers behind [Switch], [Slider] and [RangeSlider]. Every line says where it comes from:
 * measured (read from a recording of iOS 27), fitted (our spring parameters, fitted to measured frame
 * times) or authored (not visible in any recording).
 *
 * Switch: the native UIKit Bluetooth switch in `I-menus-dark` (iPhone, 3 px per point, 60 fps).
 * Slider: Safari's web slider in `Ctl-safari-controls-dark`, which renders about 1.12x native, so its
 * sizes are web sizes. No native thumb-slider recording exists.
 */
internal object KnobSpec {
    /** Measured: 189-190 x 84 px. */
    val SwitchTrackWidth = 63.dp
    val SwitchTrackHeight = 28.dp
    /** Measured: 111 x 72 px (off), 109 x 71 px (on) white core. */
    val KnobWidth = 37.dp
    val KnobHeight = 24.dp
    /** Measured: 6-7 px from the knob to the track edge, both ends. */
    val KnobInset = 2.dp
    /** Measured: 65-67 px rest to rest (63 - 37 - 2 x 2). */
    val KnobTravel = 22.dp

    /**
     * Measured, settled hold (I-menus-dark, slow-drag holds 30.63-31.21 s and 32.83-33.43 s, n = 73): width 173.35 +- 0.27 px =
     * 57.7 dp = 1.56 times the 37 dp knob (cap-arc circle fits, validated on the resting knob to 0.07 px), height 115.00 px =
     * 1.6 times. The earlier 1.75 was read off the soft-edged tap peak (193-200 px), which overshoots. The press spring's
     * overshoot takes the width to 1.63 times at the peak, as measured (1.62-1.64).
     */
    const val HeldScaleX = 1.56f
    const val HeldScaleY = 1.6f
    val HeldWidth: Dp get() = KnobWidth * HeldScaleX
    val HeldHeight: Dp get() = KnobHeight * HeldScaleY
    /** Authored: the lens strength. The recording shows outline and centre colour only, not the magnification. */
    const val LensStrength = 1.25f

    /**
     * Fitted to the lens outline height of a long hold in `I-menus-dark` (30.41-31.35 s, 60 fps): 85 px (the
     * track) -> 121 px in 6 frames (100 ms), settling at 115 px. The outline is only visible above the 84 px
     * track, so the growth is inferred to start about 40 ms before the first visible frame. Under-damped spring, rms
     * error 1.0 px over the visible frames (the earlier 1050 / 0.5 fit read the track as the knob's rest
     * height and left 2.2 px).
     */
    val PressSpring: FiniteAnimationSpec<Float> = spring(dampingRatio = .55f, stiffness = 625f)
    /**
     * Fitted to the same hold's release: 115 -> 84 px (the track) in 8 frames (133 ms), nearly linear after a
     * 2-frame ramp, no undershoot. Critically damped, rms error 0.9 px (the earlier 1900 fit counted the 133 ms
     * to the 24 dp knob size, which the track hides, and finished the visible part in 58 ms: 4.6 px).
     */
    val ReleaseSpring: FiniteAnimationSpec<Float> = spring(dampingRatio = 1f, stiffness = 361f)
    /**
     * Fitted to Safari's switch frames (timing class only, the web switch is larger): 90 percent in 7
     * frames, at rest in 10-11 frames (167-183 ms), no overshoot. The recording starts slowly and ends
     * abruptly, which no critically damped spring does; this one is a little faster at the 90 percent
     * mark (6 frames) and settles in 9.
     */
    val TravelSpring: FiniteAnimationSpec<Float> = spring(dampingRatio = 1f, stiffness = 1400f)
    /** Measured: the native switch's on track in the dark appearance, display sRGB (47, 210, 88). Light appearance not recorded. */
    val OnTrackDark = Color(47, 210, 88)
    /** Measured: the native switch's off track in the dark appearance, display sRGB (90, 90, 94). Light appearance not recorded. */
    val OffTrackDark = Color(90, 90, 94)
    /** Measured on Safari's switch: the track colour lags the knob and settles in about 420 ms. */
    const val TrackFadeMillis = 420

    /** Measured: 12-13 px (4 dp) on Safari's slider, about 3.6 dp if the native one is 1/1.12 of it. */
    val SliderTrackHeight = 4.dp
    /** Measured on Safari's slider: 71 x 48 px, opaque white, unchanged while held and dragged (no press scale). */
    val SliderThumbWidth = 24.dp
    val SliderThumbHeight = 16.dp
    /** Authored: the thumb's soft drop shadow (measured edge level (214, 214, 214) only). */
    val SliderThumbShadow = 2.dp
    /** Authored: the disabled knob and thumb. */
    val DisabledKnob = Color(0xFFE5E5EA)
}

/** Test tag of the knob's own node, so tests read its real, current size and position. */
internal const val GlassKnobTestTag = "liquidglass:knob"
internal const val SliderThumbTestTag = "liquidglass:sliderThumb"

/**
 * The switch knob: an opaque white pill at rest, a clear glass lens 1.56 x 1.6 times larger while
 * [held]. Sized in layout, not scaled, so the lens samples and refracts at its true size; only this
 * node is measured again per frame.
 */
@Composable
internal fun GlassKnob(held: Boolean, enabled: Boolean) {
    val fraction by animateFloatAsState(if (held) 1f else 0f, if (held) KnobSpec.PressSpring else KnobSpec.ReleaseSpring, label = "knob")
    // The wrapper keeps the rest size in the switch's layout and lets the knob grow around its centre.
    Box(Modifier.size(KnobSpec.KnobWidth, KnobSpec.KnobHeight).wrapContentSize(Alignment.Center, unbounded = true)) {
        val look = if (held && enabled) {
            Modifier.glassEffect(Glass.Lens(KnobSpec.LensStrength), CapsuleShape)
        } else {
            Modifier.background(if (enabled) Color.White else KnobSpec.DisabledKnob, CapsuleShape)
        }
        Box(
            Modifier.layout { measurable, _ ->
                val f = fraction
                val w = lerp(KnobSpec.KnobWidth.toPx(), KnobSpec.HeldWidth.toPx(), f).toInt().coerceAtLeast(0)
                val h = lerp(KnobSpec.KnobHeight.toPx(), KnobSpec.HeldHeight.toPx(), f).toInt().coerceAtLeast(0)
                val placeable = measurable.measure(Constraints.fixed(w, h))
                layout(w, h) { placeable.place(0, 0) }
            }.testTag(GlassKnobTestTag).then(look),
        )
    }
}

/** The slider thumb: a 24 x 16 dp opaque white pill with a soft shadow. No lens and no press scale: none was measured. */
@Composable
internal fun SliderThumb(enabled: Boolean) {
    Box(
        Modifier.size(KnobSpec.SliderThumbWidth, KnobSpec.SliderThumbHeight).testTag(SliderThumbTestTag)
            .shadow(KnobSpec.SliderThumbShadow, CapsuleShape, clip = false)
            .background(if (enabled) Color.White else KnobSpec.DisabledKnob, CapsuleShape),
    )
}
