package com.wexpa.liquidglass.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.wexpa.liquidglass.Glass
import com.wexpa.liquidglass.GlassStyle

/**
 * Regular glass over flat saturated colour, the device fixture of the colour transfer (#201): see
 * [ColourTransferScene]. Dark by default; `--ez cc true` puts the Control Center tile glass on the pills (#206).
 *
 *     adb shell am start -n com.wexpa.liquidglass.sample/.ColourTransferActivity [--ez dark false] [--ez cc true]
 */
class ColourTransferActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val dark = intent?.getBooleanExtra("dark", true) ?: true
        val glass = if (intent?.getBooleanExtra("cc", false) == true) Glass(GlassStyle.controlCenterTile()) else Glass.Regular
        setContent { ColourTransferScene(dark = dark, glass = glass) }
    }
}
