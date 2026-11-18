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

    /** Measured: the held knob's outline is 193-200 x 113-117 px, 1.75 and 1.6 times the rest knob (+-3 px). */
    const val HeldScaleX = 1.75f
    const val HeldScaleY = 1.6f
    val HeldWidth: Dp get() = KnobWidth * HeldScaleX
    val HeldHeight: Dp get() = KnobHeight * HeldScaleY
    /** Authored: the lens strength. The recording shows outline and centre colour only, not the magnification. */
    const val LensStrength = 1.25f

    /**
     * Fitted to the tap's height frames (86 -> 120 px in 100 ms, 118 at +120 ms, 116 at +170 ms, 115 held):
     * a spring that peaks about 100 ms in and overshoots by about a sixth of the way.
     */
    val PressSpring: FiniteAnimationSpec<Float> = spring(dampingRatio = .5f, stiffness = 1050f)
    /** Fitted: 112 -> 85 px in 133 ms, then constant: critically damped, settled in about 130 ms. */
    val ReleaseSpring: FiniteAnimationSpec<Float> = spring(dampingRatio = 1f, stiffness = 1900f)
    /**
     * Fitted to Safari's switch frames (timing class only, the web switch is larger): 90 percent in 7
     * frames, at rest in 10-11 frames (167-183 ms), no overshoot. The recording starts slowly and ends
     * abruptly, which no critically damped spring does; this one is a little faster at the 90 percent
     * mark (6 frames) and settles in 9.
     */
    val TravelSpring: FiniteAnimationSpec<Float> = spring(dampingRatio = 1f, stiffness = 1400f)
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
 * The switch knob: an opaque white pill at rest, a clear glass lens 1.75 x 1.6 times larger while
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
