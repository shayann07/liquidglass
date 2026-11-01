package com.wexpa.liquidglass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.delay
import kotlin.time.TimeSource

/**
 * Lets [LiquidGlassTheme] lower the glass's cost by itself when frames are missed (#118). Off by default.
 *
 * Provide `true` above the theme and, while more than a quarter of the frames in a second miss their
 * deadline, the theme renders its glass at 0.75 of its `renderScale`, then 0.75 of that, down to half of
 * it. After three seconds without a missed frame it steps back up, one step at a time. Each step
 * rebuilds the glass layers once, so a step is rare by design: a single slow frame, such as the first
 * frame of a tap while the GPU clock rises, never triggers one. A step waits until the glass has been
 * still for 300 ms, because a rebuild in the middle of a gesture would cancel it.
 *
 * ```kotlin
 * CompositionLocalProvider(LocalGlassAdaptiveQuality provides true) {
 *     LiquidGlassTheme { App() }
 * }
 * ```
 *
 * Android reads each rendered frame's deadline from the window's frame metrics, which cost nothing while
 * nothing redraws. The desktop and web do not adapt yet: the theme keeps its `renderScale` there.
 */
val LocalGlassAdaptiveQuality = staticCompositionLocalOf { false }

/**
 * The pure decision behind [LocalGlassAdaptiveQuality]: a render-scale factor from a stream of frames,
 * each either on time or missed. Times are nanoseconds from any monotonic origin.
 */
internal class GlassQualityGovernor(
    private val step: Float = 0.75f,
    private val floor: Float = 0.5f,
    private val windowNanos: Long = 1_000_000_000L,
    private val missShare: Float = 0.25f,
    private val minFrames: Int = 20,
    private val recoverNanos: Long = 3_000_000_000L,
    private val idleNanos: Long = 300_000_000L,
) {
    /** The factor in force: [target] once the glass has been still for [idleNanos]. */
    var factor: Float = 1f
        private set

    /** The factor the frames call for; it takes effect at the next still moment ([apply]). */
    var target: Float = 1f
        private set

    private var lastFrame = Long.MIN_VALUE

    private val times = ArrayDeque<Long>()
    private val misses = ArrayDeque<Boolean>()
    private var missCount = 0
    private var lastMiss = Long.MIN_VALUE
    private var lastStep = Long.MIN_VALUE
    /** The factors stepped down from, so stepping back up retraces them (0.5 returns to 0.5625, not 0.667). */
    private val above = ArrayDeque<Float>()

    /** Feeds one rendered frame; returns true when [target] changed. */
    fun onFrame(now: Long, missed: Boolean): Boolean {
        lastFrame = now
        if (target > factor && missed) {
            // A miss while a step back up waits cancels it: the scale it would leave is still needed.
            above.addLast(target)
            target = factor
            missCount = 0; lastMiss = now
            return true
        }
        // Frames drawn before a pending step down lands say nothing about the scale it moves to.
        if (target != factor) return false
        times.addLast(now); misses.addLast(missed)
        if (missed) { missCount++; lastMiss = now }
        while (times.isNotEmpty() && now - times.first() > windowNanos) {
            times.removeFirst()
            if (misses.removeFirst()) missCount--
        }
        if (times.size < minFrames || missCount <= missShare * times.size || factor <= floor) return false
        above.addLast(factor)
        target = maxOf(floor, factor * step)
        lastStep = now
        // The frames that led here were drawn at the old scale; judge the new one on its own frames.
        times.clear(); misses.clear(); missCount = 0
        return true
    }

    /** Steps back up once nothing has missed for [recoverNanos]; returns true when [target] changed. */
    fun recover(now: Long): Boolean {
        if (factor >= 1f || target != factor) return false
        if (now - maxOf(lastMiss, lastStep) < recoverNanos) return false
        target = above.removeLastOrNull() ?: 1f
        lastStep = now
        times.clear(); misses.clear(); missCount = 0
        return true
    }

    /** Puts [target] in force if no frame has been drawn for [idleNanos]; returns true when [factor] changed. */
    fun apply(now: Long): Boolean {
        if (target == factor || now - lastFrame < idleNanos) return false
        factor = target
        lastStep = now
        times.clear(); misses.clear(); missCount = 0
        return true
    }
}

private val glassClockOrigin = TimeSource.Monotonic.markNow()

/** Nanoseconds on the governor's clock. */
internal fun glassClockNanos(): Long = glassClockOrigin.elapsedNow().inWholeNanoseconds

/**
 * Calls [onFrame] with whether each rendered frame missed its deadline, for as long as [enabled] and
 * this stays in the composition. Platforms that cannot tell never call it.
 */
@Composable
internal expect fun GlassFrameMisses(enabled: Boolean, onFrame: (missed: Boolean) -> Unit)

/** The factor [LiquidGlassTheme] multiplies its render scale by: 1 unless [enabled] and frames are being missed. */
@OptIn(LiquidGlassDiagnosticApi::class)
@Composable
internal fun rememberGlassAdaptiveFactor(enabled: Boolean): Float {
    // A new enabled period starts from full scale: history from before adaptation was turned off says nothing now.
    val governor = remember(enabled) { GlassQualityGovernor() }
    var factor by remember(enabled) { mutableFloatStateOf(1f) }
    var pending by remember(enabled) { mutableFloatStateOf(1f) }
    GlassFrameMisses(enabled) { missed -> if (governor.onFrame(glassClockNanos(), missed)) pending = governor.target }
    LaunchedEffect(enabled, factor, pending) {
        // Polls while a step is pending or the scale is lowered: a delay requests no frame, so an idle screen stays idle.
        while (enabled && (pending != factor || factor < 1f)) {
            delay(if (pending != factor) 100 else 500)
            val now = glassClockNanos()
            if (governor.recover(now)) pending = governor.target
            if (governor.apply(now)) {
                factor = governor.factor
                LiquidGlassDiagnostics.adaptiveQualityListener?.invoke(factor)
            }
        }
    }
    return if (enabled) factor else 1f
}
