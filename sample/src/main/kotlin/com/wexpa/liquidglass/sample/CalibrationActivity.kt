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
import androidx.compose.foundation.layout.wrapContentSize
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.LayoutDirection
import com.wexpa.liquidglass.GlassFuse
import com.wexpa.liquidglass.GlassMaterial
import com.wexpa.liquidglass.GlassProfile
import com.wexpa.liquidglass.GlassStyle
import com.wexpa.liquidglass.LiquidGlassDiagnosticApi
import com.wexpa.liquidglass.LiquidGlassDiagnostics
import com.wexpa.liquidglass.LiquidGlassScene
import com.wexpa.liquidglass.LocalLiquidGlassState
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
// This screen is a measurement rig: it is where opting in to the diagnostics is the point.
@OptIn(LiquidGlassDiagnosticApi::class)
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
        // E1: `--ez unit true` draws the 1170x2532 target at 1:1 screen px (top-left aligned, clipped
        // on narrower screens) so Android and JVM pixels compare without any resampling.
        val unit0 = intent.getBooleanExtra("unit", false)
        // `--ei offsetY -180` shifts the whole scene up so the bottom row fits a 2400 px screen at 1:1.
        val offsetY0 = intent.getIntExtra("offsetY", 0)
        // Path-backed device fixture (padding contract, Astra round 6): `--es fixture path --ef renderScale 0.5
        // --ef padPx 22.5` draws four 1:1 panels over the target whose pad is set by padPx alone (wide kernel off,
        // blur radius = padPx): a closed-form capsule, the same capsule as a sampled-field path, a held-profile
        // path capsule and a fused closed-form capsule, at the given render scale.
        val fixture0 = intent.getStringExtra("fixture")
        val renderScale0 = intent.getFloatExtra("renderScale", 1f)
        val padPx0 = intent.getFloatExtra("padPx", 22.5f)
        // Astra round 7 diagnostic: `--ef padOverride 23` changes only the recorded pad (all material uniforms,
        // blur radius included, stay as the style defines them); `--ez coverage true` renders flat magenta times
        // the production coverage through the same geometry, field, recording and draw-back path.
        LiquidGlassDiagnostics.recordPadOverridePx = if (intent.hasExtra("padOverride")) intent.getFloatExtra("padOverride", 0f) else null
        LiquidGlassDiagnostics.coverageOnly = intent.getBooleanExtra("coverage", false)
        setContent {
            if (fixture0 == "path") PathFixture(renderScale0, padPx0, target0)
            else Calibration(tint0, dark0, target0, wide0, wideDp0, animate0, unit0, offsetY0)
        }
    }
}

private data class PanelBox(val x0: Int, val y0: Int, val x1: Int, val y1: Int, val r: Int)

/** The Photos chrome of the reference stills, in image px: pill, back, more, toolbar, two buttons. */
// E1 (2026-09-13): the same boxes the JVM harness GlassCalibrationRenderTest declares, so the
// Android/JVM comparison tests one declared geometry. The measured phone outlines (E1_outline.json)
// are not applied here yet.
private val panels = listOf(
    PanelBox(346, 141, 822, 273, 66), PanelBox(50, 141, 178, 273, 66), PanelBox(992, 141, 1120, 273, 66),
    PanelBox(342, 2304, 828, 2448, 72), PanelBox(84, 2304, 230, 2448, 73), PanelBox(940, 2304, 1086, 2448, 73),
)

@Composable
private fun Calibration(tint0: Int, dark0: Boolean, target0: String, wide0: Boolean, wideDp0: Int, animate0: Boolean, unit0: Boolean = false, offsetY0: Int = 0) {
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
    val scale = if (unit0) 1f else screenWidthPx / 1170f
    fun px(v: Int): Dp = with(density) { (v * scale).toDp() }
    // `--ez wide false` switches the wide tone kernel off, to tell a strip problem from a tone one.
    fun GlassStyle.wideSwitch() = if (wide0) copy(wideKernel = wideDp0.dp) else copy(wideKernel = 0.dp, fineShare = 1f)
    // E1 adapter: a style length L dp means 3*L reference px at the phone's 3 px/pt; on this
    // screen the target is drawn at S = scale reference px per screen px... i.e. screen px per
    // reference px, so the length must be 3*S*L screen px = (3*S/D)*L dp. Applied after wideSwitch.
    val lengthFactor = 3f * scale / density.density
    fun GlassStyle.calibrated() = copy(
        blurRadius = blurRadius * lengthFactor, wideKernel = wideKernel * lengthFactor, bevel = bevel * lengthFactor,
    )
    val style = GlassStyle.inApp(dark = dark, tintAmount = tint.toFloat()).wideSwitch().calibrated()
    // The fourth box is the toolbar capsule, which is its own role.
    val toolbar = GlassStyle.toolbar(dark = dark, tintAmount = tint.toFloat()).wideSwitch().calibrated()

    LiquidGlassScene(background = Color.Black, modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Image(
            painter = painterResource(
                if (target == "light") R.drawable.calibration_target_light else R.drawable.calibration_target_dark,
            ),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            // wrapContentSize(unbounded) lets the 1170 px image exceed a narrower screen at 1:1 instead
            // of being coerced to the parent constraints (which would rescale it under the panels).
            modifier = Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).offset(0.dp, px(offsetY0)).size(px(1170), px(2532)).liquidGlassSource(),
        )
        for ((i, p) in panels.withIndex()) {
            Box(
                modifier = Modifier
                    .offset(px(p.x0), px(p.y0 + offsetY0))
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

/** A capsule delivered as a generic path, so the material takes the sampled-field route. */
private class PathCapsule : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(size.height / 2f, size.height / 2f))) })
}

/** Declared 1:1 boxes of the path fixture, in target px (all inside a 1080-px-wide screen). */
// Diagnostic layout (Astra round 7): every panel and the whole fused union over the target's black half
// (x < 585, rows ~100..320), fully inside the 1080 px screen, with >= 60 px between panels and windows.
internal val fixtureBoxes = listOf(
    Triple("closed", 40, 150), Triple("path", 330, 150), Triple("held_path", 40, 250), Triple("fused_closed", 330, 250),
)
internal const val FIXTURE_W = 200
internal const val FIXTURE_H = 60
internal const val FIXTURE_FUSED_W = 120
internal const val FIXTURE_FUSE_GAP = 20
internal const val FIXTURE_FUSE_SIB_W = 60

@OptIn(LiquidGlassDiagnosticApi::class)
@Composable
private fun PathFixture(renderScale: Float, padPx: Float, target0: String) {
    val density = LocalDensity.current
    fun px(v: Int): Dp = with(density) { v.toDp() }
    // Style whose recorded pad is padPx exactly: wide kernel off, blur radius padPx px (dp = px / density).
    val base = GlassStyle.inApp(dark = true, tintAmount = 50f).copy(
        wideKernel = 0.dp, fineShare = 1f, blurRadius = with(density) { padPx.toDp() }, bevel = with(density) { (2f * 3f).toDp() },
    )
    val held = base.copy(profile = GlassProfile.Held)
    LiquidGlassScene(background = Color.Black, renderScale = renderScale, modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Image(
            painter = painterResource(if (target0 == "light") R.drawable.calibration_target_light else R.drawable.calibration_target_dark),
            contentDescription = null, contentScale = ContentScale.FillWidth,
            modifier = Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).size(px(1170), px(2532)).liquidGlassSource(),
        )
        for ((kind, x0, y0) in fixtureBoxes) {
            val w = if (kind == "fused_closed") FIXTURE_FUSED_W else FIXTURE_W
            val shape: Shape = if (kind == "closed" || kind == "fused_closed") RoundedCornerShape(px(FIXTURE_H / 2)) else PathCapsule()
            val fuse = if (kind == "fused_closed") GlassFuse(
                // A sibling 20 px to the right of the fused capsule (screen x 470..530), inside the screen and over black,
                // so the whole fused union and its measurement windows are visible.
                bounds = DpRect(left = px(w + FIXTURE_FUSE_GAP), top = px(0), right = px(w + FIXTURE_FUSE_GAP + FIXTURE_FUSE_SIB_W), bottom = px(FIXTURE_H)),
                cornerRadius = px(20), smoothing = px(16),
            ) else null
            Box(
                modifier = Modifier.offset(px(x0), px(y0)).size(px(w), px(FIXTURE_H))
                    .liquidGlass(state = LocalLiquidGlassState.current!!, shape = shape, style = if (kind == "held_path") held else base, fuse = fuse),
            )
        }
        BasicText("fixture path rs=%.2f pad=%.1f override=%s coverage=%s".format(renderScale, padPx, LiquidGlassDiagnostics.recordPadOverridePx?.toString() ?: "-", LiquidGlassDiagnostics.coverageOnly), Modifier.align(Alignment.BottomEnd).padding(px(8)), style = TextStyle(color = Color(0xFFFF00FF), fontSize = 10.sp))
    }
}
