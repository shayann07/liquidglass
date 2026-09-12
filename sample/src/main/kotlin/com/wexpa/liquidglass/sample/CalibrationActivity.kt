package com.wexpa.liquidglass.sample

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wexpa.liquidglass.GlassMaterial
import com.wexpa.liquidglass.GlassStyle
import com.wexpa.liquidglass.LiquidGlassScene
import com.wexpa.liquidglass.liquidGlass
import com.wexpa.liquidglass.liquidGlassSource

/**
 * The calibration scene: the same test image the iOS 27 captures were made through, with the
 * measured chrome at the same places, so a screenshot of this screen can be measured by the
 * capture toolchain and compared number for number with the phone.
 *
 * The image is 1170x2532 (an iPhone 13 at 3x) and is drawn scaled to the screen width; the six
 * panels are placed in image pixels and scaled with it. Tap the top-left corner to cycle the
 * Tint Amount (0, 50, 100), the top-right corner to toggle light/dark, the bottom-left corner to
 * switch the target. Every state is also reachable through extras, for `adb shell am start`:
 * `--ei tint 50 --ez dark true --es target light`; `--ez wide false` switches the wide tone
 * kernel off and `--ei wideDp 10` sets its sigma, for calibration experiments.
 */
class CalibrationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val tint0 = intent.getIntExtra("tint", 50)
        val dark0 = intent.getBooleanExtra("dark", true)
        val target0 = intent.getStringExtra("target") ?: "light"
        val wide0 = intent.getBooleanExtra("wide", true)
        val wideDp0 = intent.getIntExtra("wideDp", 10)
        val animate0 = intent.getBooleanExtra("animate", false)
        setContent { Calibration(tint0, dark0, target0, wide0, wideDp0, animate0) }
    }
}

private data class PanelBox(val x0: Int, val y0: Int, val x1: Int, val y1: Int, val r: Int)

/** The Photos chrome of the reference stills, in image px: pill, back, more, toolbar, two buttons. */
private val panels = listOf(
    PanelBox(345, 160, 825, 290, 85), PanelBox(50, 160, 180, 290, 65), PanelBox(990, 160, 1120, 290, 65),
    PanelBox(345, 2310, 825, 2450, 70), PanelBox(85, 2310, 225, 2450, 70), PanelBox(945, 2310, 1085, 2450, 70),
)

@Composable
private fun Calibration(tint0: Int, dark0: Boolean, target0: String, wide0: Boolean, wideDp0: Int, animate0: Boolean) {
    var tint by remember { mutableIntStateOf(tint0) }
    // `--ez animate true` sweeps the Tint Amount continuously so every panel re-renders every
    // frame, for frame-time readings with `dumpsys gfxinfo`.
    if (animate0) {
        val sweep = androidx.compose.animation.core.rememberInfiniteTransition(label = "tint_sweep")
        val t by sweep.animateFloat(
            initialValue = 0f, targetValue = 100f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                androidx.compose.animation.core.tween(2000), androidx.compose.animation.core.RepeatMode.Reverse,
            ),
            label = "tint",
        )
        tint = t.toInt()
    }
    var dark by remember { mutableStateOf(dark0) }
    var target by remember { mutableStateOf(target0) }
    val density = LocalDensity.current
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    // Image px -> screen px -> dp. The whole scene scales with the width, so a screenshot maps
    // back to image px by one factor.
    val scale = screenWidthPx / 1170f
    fun px(v: Int): Dp = with(density) { (v * scale).toDp() }
    // `--ez wide false` switches the wide tone kernel off, to tell a strip problem from a tone one.
    fun GlassStyle.wideSwitch() = if (wide0) copy(wideKernel = wideDp0.dp) else copy(wideKernel = 0.dp, fineShare = 1f)
    val style = GlassStyle.inApp(dark = dark, tintAmount = tint.toFloat()).wideSwitch()
    // The fourth box is the toolbar capsule, which is its own role.
    val toolbar = GlassStyle.toolbar(dark = dark, tintAmount = tint.toFloat()).wideSwitch()

    LiquidGlassScene(background = Color.Black, modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Image(
            painter = painterResource(
                if (target == "light") R.drawable.calibration_target_light else R.drawable.calibration_target_dark,
            ),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.size(px(1170), px(2532)).liquidGlassSource(),
        )
        for ((i, p) in panels.withIndex()) {
            Box(
                modifier = Modifier
                    .offset(px(p.x0), px(p.y0))
                    .size(px(p.x1 - p.x0), px(p.y1 - p.y0))
                    .liquidGlass(shape = RoundedCornerShape(px(p.r)), style = if (i == 3) toolbar else style),
            )
        }
        // Corner controls, outside the measured boxes.
        Box(Modifier.size(px(160), px(140)).clickable { tint = when (tint) { 0 -> 50; 50 -> 100; else -> 0 } })
        Box(Modifier.align(Alignment.TopEnd).size(px(160), px(140)).clickable { dark = !dark })
        Box(Modifier.align(Alignment.BottomStart).size(px(160), px(140)).clickable { target = if (target == "light") "dark" else "light" })
        Row(Modifier.align(Alignment.BottomEnd).padding(px(8))) {
            BasicText(
                "t=$tint ${if (dark) "dark" else "light"} $target  a=%.2f w=%.2f".format(
                    GlassMaterial.opacity(dark, tint.toFloat()), GlassMaterial.fineShare(tint.toFloat()),
                ),
                style = TextStyle(color = Color(0xFFFF00FF), fontSize = 10.sp),
            )
        }
    }
}
