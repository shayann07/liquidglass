package com.wexpa.liquidglass.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/**
 * Regular glass over flat saturated colour, the device fixture of the colour transfer (#201): see
 * [ColourTransferScene]. Dark by default.
 *
 *     adb shell am start -n com.wexpa.liquidglass.sample/.ColourTransferActivity [--ez dark false]
 */
class ColourTransferActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val dark = intent?.getBooleanExtra("dark", true) ?: true
        setContent { ColourTransferScene(dark = dark) }
    }
}
