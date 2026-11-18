package com.wexpa.liquidglass.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wexpa.liquidglass.CapsuleShape
import com.wexpa.liquidglass.Glass
import com.wexpa.liquidglass.LiquidGlassTheme
import com.wexpa.liquidglass.glassEffect

/** The flat pages of the colour-transfer fixture, top to bottom; the last one is the grey reference. */
val ColourTransferSwatches: List<Pair<String, Color>> = listOf(
    "red" to Color(0xFFFF0000),
    "orange" to Color(0xFFFF8000),
    "yellow" to Color(0xFFFFFF00),
    "green" to Color(0xFF00FF00),
    "cyan" to Color(0xFF00FFFF),
    "blue" to Color(0xFF0000FF),
    "magenta" to Color(0xFFFF00FF),
    "dark magenta" to Color(0xFF800080),
    "grey 128" to Color(0xFF808080),
)

/**
 * Regular glass over flat saturated colour (#201): one band per colour in [ColourTransferSwatches], each
 * with a Regular pill (Tint 50, the default) at its centre, in the [dark] or light appearance. The pill
 * holds no content, so its interior shows the material alone: the measured colour transfer keeps and
 * boosts each band's chroma and leaves the grey band at the grey tone. Nothing here depends on time.
 * [glass] replaces the pills' material, e.g. `Glass(GlassStyle.controlCenterTile())` for the #206 device check.
 */
@Composable
fun ColourTransferScene(dark: Boolean, modifier: Modifier = Modifier, glass: Glass = Glass.Regular) {
    LiquidGlassTheme(dark = dark, background = if (dark) Color.Black else Color.White, glass = Glass.Regular) {
        Column(modifier.fillMaxSize()) {
            for ((_, colour) in ColourTransferSwatches) {
                Box(Modifier.fillMaxWidth().weight(1f).background(colour), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(220.dp, 48.dp).glassEffect(glass, CapsuleShape))
                }
            }
        }
    }
}
