package com.wexpa.liquidglass.sample

import android.os.Bundle
import android.util.Log
import androidx.compose.runtime.CompositionLocalProvider
import com.wexpa.liquidglass.LiquidGlassDiagnosticApi
import com.wexpa.liquidglass.LiquidGlassDiagnostics
import com.wexpa.liquidglass.LocalGlassAdaptiveQuality
import androidx.activity.ComponentActivity
import com.wexpa.liquidglass.GlassTabBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * The Calm navigation comparison scene ([PhoneScene], the one the desktop replay renders) on a
 * device or emulator, for Android verification of the current Calm selector.
 *
 * `adb shell am start -n com.wexpa.liquidglass.sample/.PhoneActivity [--ez natural true]`
 */
class PhoneActivity : ComponentActivity() {
    @OptIn(LiquidGlassDiagnosticApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val natural = intent?.getBooleanExtra("natural", true) ?: true
        // "light": light Calm over the light palette (measured on the owner's light recording, 17 October).
        val light = intent?.getBooleanExtra("light", false) ?: false
        // "renderScale": record the glass at a fraction of full resolution (performance comparisons).
        val renderScale = intent?.getFloatExtra("renderScale", 1f) ?: 1f
        // "adaptive": LocalGlassAdaptiveQuality on (#118). "adaptiveDeadlineScale": count a frame as missed past this
        // fraction of its deadline, to drive the governor on a phone that meets its deadlines. Steps are logged
        // under the tag "GlassAdaptive".
        val adaptive = intent?.getBooleanExtra("adaptive", false) ?: false
        if (adaptive) {
            LiquidGlassDiagnostics.adaptiveDeadlineScale = intent?.getFloatExtra("adaptiveDeadlineScale", 1f)?.takeIf { it != 1f }
            LiquidGlassDiagnostics.adaptiveQualityListener = { Log.i("GlassAdaptive", "factor $it") }
        }
        setContent {
            var selected by remember { mutableIntStateOf(0) }
            val palette = if (light) PhonePalette.Light else if (natural) PhonePalette.Natural else PhonePalette.Recorded
            CompositionLocalProvider(LocalGlassAdaptiveQuality provides adaptive) {
            Box(Modifier.fillMaxSize().background(palette.page), contentAlignment = Alignment.BottomCenter) {
                PhoneScene(selected, { selected = it }, Modifier.navigationBarsPadding().testTag("phone-scene"), palette = palette,
                    style = PhoneGeometry.style(GlassTabBarStyle.Calm(dark = !light)), renderScale = renderScale,
                    callsBadge = 2)
            }
            }
        }
    }
}
