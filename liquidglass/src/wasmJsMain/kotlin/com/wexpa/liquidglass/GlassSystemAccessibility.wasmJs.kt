package com.wexpa.liquidglass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

private fun prefersReducedMotion(): Boolean =
    js("window.matchMedia('(prefers-reduced-motion: reduce)').matches")

private fun prefersReducedTransparency(): Boolean =
    js("window.matchMedia('(prefers-reduced-transparency: reduce)').matches")

private fun prefersMoreContrast(): Boolean =
    js("window.matchMedia('(prefers-contrast: more)').matches")

@Composable
internal actual fun rememberGlassSystemAccessibility(): GlassSystemAccessibility = remember {
    GlassSystemAccessibility(
        reduceMotion = prefersReducedMotion(),
        reduceTransparency = prefersReducedTransparency(),
        increaseContrast = prefersMoreContrast(),
    )
}
