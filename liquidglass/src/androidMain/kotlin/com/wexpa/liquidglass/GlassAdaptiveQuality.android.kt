package com.wexpa.liquidglass

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

@OptIn(LiquidGlassDiagnosticApi::class)
@Composable
internal actual fun GlassFrameMisses(enabled: Boolean, onFrame: (missed: Boolean) -> Unit) {
    val window = LocalContext.current.findActivity()?.window
    val latest = rememberUpdatedState(onFrame)
    DisposableEffect(enabled, window) {
        if (!enabled || window == null) return@DisposableEffect onDispose { }
        val handler = Handler(Looper.getMainLooper())
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            if (metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1L) return@OnFrameMetricsAvailableListener
            val total = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
            // From API 31 a frame carries its own deadline; a frame that finishes after it is a frame the
            // display shows late. Before that, or when a frame reports none (-1), twice the display's interval
            // stands in for it.
            val reported = if (Build.VERSION.SDK_INT >= 31) metrics.getMetric(FrameMetrics.DEADLINE) else -1L
            val deadline = if (reported > 0) reported
            else (2_000_000_000.0 / (window.decorView.display?.refreshRate ?: 60f)).toLong()
            if (total <= 0) return@OnFrameMetricsAvailableListener
            val scale = LiquidGlassDiagnostics.adaptiveDeadlineScale ?: 1f
            latest.value(total > deadline * scale)
        }
        window.addOnFrameMetricsAvailableListener(listener, handler)
        onDispose { runCatching { window.removeOnFrameMetricsAvailableListener(listener) } }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
