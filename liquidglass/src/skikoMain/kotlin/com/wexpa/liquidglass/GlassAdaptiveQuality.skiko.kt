package com.wexpa.liquidglass

import androidx.compose.runtime.Composable

// The desktop and web have no per-frame deadline to read without requesting frames, so they do not adapt yet (#118).
@Composable
internal actual fun GlassFrameMisses(enabled: Boolean, onFrame: (missed: Boolean) -> Unit) = Unit
