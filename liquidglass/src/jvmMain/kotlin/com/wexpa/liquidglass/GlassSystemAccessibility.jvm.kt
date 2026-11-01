package com.wexpa.liquidglass

import androidx.compose.runtime.Composable

/** The desktop JVM has no portable accessibility setting to read; pass the values explicitly. */
@Composable
internal actual fun rememberGlassSystemAccessibility(): GlassSystemAccessibility = GlassSystemAccessibility()
