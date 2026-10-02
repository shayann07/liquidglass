package com.wexpa.liquidglass.sample

import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/**
 * Loupe, the sample's launcher screen: a star atlas read through a glass magnifier.
 * See [LoupeScreen].
 *
 *     adb shell am start -n com.wexpa.liquidglass.sample/.LoupeActivity
 */
class LoupeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // The system's "Remove animations" switch sets the animator scale to 0.
        val reduceMotion = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        setContent { LoupeScreen(reduceMotion = reduceMotion) }
    }
}
