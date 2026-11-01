package com.wexpa.liquidglass

import android.app.UiModeManager
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberGlassSystemAccessibility(): GlassSystemAccessibility {
    val context = LocalContext.current
    // A configuration change (which a contrast change raises) reads the settings again.
    val configuration = LocalConfiguration.current
    return remember(context, configuration) {
        val animatorScale = runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
        val contrast = if (Build.VERSION.SDK_INT >= 34) {
            runCatching { context.getSystemService(UiModeManager::class.java)?.contrast ?: 0f }.getOrDefault(0f)
        } else 0f
        GlassSystemAccessibility(reduceMotion = animatorScale == 0f, increaseContrast = contrast > 0f)
    }
}
