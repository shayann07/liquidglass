package com.wexpa.liquidglass.showcase

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wexpa.liquidglass.GlassInteraction
import com.wexpa.liquidglass.GlassStyle
import com.wexpa.liquidglass.LiquidGlassContainer
import com.wexpa.liquidglass.liquidGlass
import com.wexpa.liquidglass.liquidGlassSource
import com.wexpa.liquidglass.rememberLiquidGlassState
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test

/**
 * Touch, driven by real Compose pointer events through the production modifier: a press, a pull
 * on a small control and on a large card, members fusing in a container, and materializing in.
 * Recorded frame by frame on a manual clock; the render scale is halved to keep a CPU render
 * tractable, which softens the material slightly against the stills.
 */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class ShowcaseInteractionTest {

    private val scale = 1.5f

    private fun control(
        name: String, w: Int, h: Int, shape: androidx.compose.ui.graphics.Shape,
        interaction: GlassInteraction, path: (Float) -> Offset, label: String,
    ) = showcaseScene(360, 300, scale) {
        setContent {
            val glass = rememberLiquidGlassState(background = Color.Black, renderScale = 0.5f)
            Box(Modifier.fillMaxSize()) {
                PhotoBackdrop(Modifier.fillMaxSize().liquidGlassSource(glass))
                Box(
                    Modifier.align(Alignment.Center).size(w.dp, h.dp).testTag("control")
                        .liquidGlass(glass, shape, GlassStyle.Regular, interaction = interaction),
                    contentAlignment = Alignment.Center,
                ) { BasicText(label, style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)) }
            }
        }
        waitForIdle()
        mainClock.autoAdvance = false
        val node = onNodeWithTag("control")
        val centre = Offset(w * scale / 2f, h * scale / 2f)
        val out = Showcase.dir("interaction/$name")
        var n = record(out, 6, 33)
        node.performTouchInput { down(centre) }
        n = record(out, 12, 33, n)
        val moves = 36
        n = record(out, moves, 33, n) { i ->
            val p = path((i + 1f) / moves)
            node.performTouchInput { moveTo(centre + Offset(p.x * scale, p.y * scale)) }
        }
        node.performTouchInput { up() }
        record(out, 30, 33, n)
    }

    /** Down, a short hold, then a 110 dp drag that sweeps round from the right to the top-left. */
    private val sweep: (Float) -> Offset = { t ->
        val a = -t * 2.2f
        val r = 110f * minOf(1f, t * 3f)
        Offset(r * cos(a), r * sin(a))
    }

    @Test fun pressDefault() = control("press-default", 120, 120, CircleShape, GlassInteraction.Default, { Offset.Zero }, "Press")

    @Test fun pullSmallControl() = control("pull-small", 64, 64, CircleShape, GlassInteraction.Pullable, sweep, "")

    @Test fun pullLargeCard() = control("pull-card", 260, 150, RoundedCornerShape(30.dp), GlassInteraction.Pullable, sweep, "Large card")

    @Test fun reducedMotion() = control("reduced-motion", 120, 120, CircleShape, GlassInteraction.ReducedMotion, sweep, "Reduced")

    // The container records a blur strip below its own bounds; on desktop anything outside the
    // window is clipped from that recording, so it is kept clear of the bottom edge here.
    @Test fun containerFusion() = showcaseScene(420, 260, scale) {
        var gap by mutableFloatStateOf(140f)
        setContent {
            val glass = rememberLiquidGlassState(background = Color.Black, renderScale = 0.5f)
            Box(Modifier.fillMaxSize()) {
                GridBackdrop(Modifier.fillMaxSize().liquidGlassSource(glass))
                LiquidGlassContainer(glass, Modifier.fillMaxWidth().height(150.dp).offset(y = 30.dp), GlassStyle.Regular, mergeDistance = 40.dp) {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.align(Alignment.Center).offset(x = (-(gap / 2f) - 45f).dp).size(90.dp).glassMember(CircleShape))
                        Box(Modifier.align(Alignment.Center).offset(x = (gap / 2f + 45f).dp).size(90.dp).glassMember(CircleShape))
                    }
                }
            }
        }
        waitForIdle()
        mainClock.autoAdvance = false
        record(Showcase.dir("interaction/container"), 72, 33) { i ->
            val t = i / 71f
            val u = if (t < 0.5f) t * 2f else 2f - t * 2f
            gap = 140f - u * u * (3 - 2 * u) * 170f
        }
    }

    @Test fun materializeIn() = showcaseScene(360, 240, scale) {
        var m by mutableFloatStateOf(0f)
        setContent {
            val glass = rememberLiquidGlassState(background = Color.Black, renderScale = 0.5f)
            Box(Modifier.fillMaxSize()) {
                PhotoBackdrop(Modifier.fillMaxSize().liquidGlassSource(glass))
                Box(Modifier.align(Alignment.Center).size(220.dp, 120.dp)
                    .liquidGlass(glass, RoundedCornerShape(32.dp), GlassStyle.Regular, materialize = m))
            }
        }
        waitForIdle()
        mainClock.autoAdvance = false
        record(Showcase.dir("interaction/materialize"), 54, 33) { i ->
            val t = (i / 53f * 1.5f).coerceAtMost(1f)
            m = t * t * (3 - 2 * t)
        }
    }
}
