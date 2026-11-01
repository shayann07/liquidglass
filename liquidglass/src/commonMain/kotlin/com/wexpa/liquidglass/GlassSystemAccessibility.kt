package com.wexpa.liquidglass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

/**
 * What the platform's accessibility settings ask of glass (#115). iOS's glass follows Reduce
 * Transparency, Increase Contrast and Reduce Motion by itself; these are the defaults of
 * [LiquidGlassTheme]'s `reduceTransparency` and `increaseContrast` and of [GlassTabBar]'s
 * `motionEnabled`, so an app that passes its own values still wins.
 */
@Immutable
internal data class GlassSystemAccessibility(
    val reduceMotion: Boolean = false,
    val reduceTransparency: Boolean = false,
    val increaseContrast: Boolean = false,
)

/**
 * The platform's settings, read when this enters the composition:
 * - **Android:** animator duration scale 0 (Remove animations) and, from API 34, the system contrast level.
 * - **Web:** `prefers-reduced-motion`, `prefers-reduced-transparency` and `prefers-contrast: more`.
 * - **Desktop JVM:** has no portable setting and reports none.
 */
@Composable
internal expect fun rememberGlassSystemAccessibility(): GlassSystemAccessibility
