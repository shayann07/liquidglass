package com.wexpa.liquidglass.showcase

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.wexpa.liquidglass.GlassTabBar
import com.wexpa.liquidglass.GlassTabBarStyle
import com.wexpa.liquidglass.liquidGlassSource
import com.wexpa.liquidglass.rememberLiquidGlassState
import java.io.File
import kotlin.test.Test

/**
 * The tab bar, driven by real pointer events: a tap, then a press-and-hold that drags the
 * selector across the bar and lets go. Once on the shipped capsule selector and once on the
 * opt-in V3 deforming body; plus the reduced-motion bar.
 */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class ShowcaseTabBarTest {

    private val scale = 1.5f
    private val barWidthDp = 360f
    private val insetDp = 18f

    private fun SkikoComposeUiTest.script(out: File, style: GlassTabBarStyle, motionEnabled: Boolean = true) {
        var selected by mutableIntStateOf(0)
        setContent {
            val glass = rememberLiquidGlassState(background = Color.Black, renderScale = 0.5f)
            Box(Modifier.fillMaxSize()) {
                PhotoBackdrop(Modifier.fillMaxSize().liquidGlassSource(glass))
                GlassTabBar(
                    state = glass,
                    itemCount = 4,
                    selectedIndex = selected,
                    onSelected = { selected = it },
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = insetDp.dp).fillMaxWidth().testTag("bar"),
                    style = style,
                    motionEnabled = motionEnabled,
                ) { index, isSelected -> TabItem(index, isSelected) }
            }
        }
        waitForIdle()
        shoot(File(out.parentFile, "${out.name}-rest.png"))
        mainClock.autoAdvance = false
        val bar = onNodeWithTag("bar")
        val bounds = bar.fetchSemanticsNode().size
        val y = bounds.height / 2f
        val slot = bounds.width / 4f
        fun cx(i: Int) = slot * (i + 0.5f)

        // A tap on the third tab.
        var n = record(out, 4, 33)
        bar.performTouchInput { down(Offset(cx(2), y)) }
        n = record(out, 3, 33, n)
        bar.performTouchInput { up() }
        n = record(out, 26, 33, n)

        // Press and hold on it, drag to the first tab, hold, let go.
        bar.performTouchInput { down(Offset(cx(2), y)) }
        n = record(out, 14, 33, n)
        val steps = 30
        n = record(out, steps, 33, n) { i ->
            val t = (i + 1f) / steps
            val e = t * t * (3 - 2 * t)
            bar.performTouchInput { moveTo(Offset(cx(2) + (cx(0) - cx(2)) * e, y - 6f * scale * sin01(t))) }
        }
        n = record(out, 8, 33, n)
        bar.performTouchInput { up() }
        n = record(out, 32, 33, n)
        // Leave a still of where it settled.
        shoot(File(out.parentFile, "${out.name}-after.png"))
    }

    private fun sin01(t: Float) = kotlin.math.sin(t * Math.PI).toFloat()

    @Test fun capsuleSelector() = showcaseScene(barWidthDp.toInt() + 2 * insetDp.toInt(), 170, scale) {
        script(Showcase.dir("tabbar/capsule"), GlassTabBarStyle.Measured(dark = true))
    }

    @Test fun v3DeformingBody() = showcaseScene(barWidthDp.toInt() + 2 * insetDp.toInt(), 170, scale) {
        script(Showcase.dir("tabbar/v3"), GlassTabBarStyle.V3(dark = true))
    }

    @Test fun reducedMotion() = showcaseScene(barWidthDp.toInt() + 2 * insetDp.toInt(), 170, scale) {
        script(Showcase.dir("tabbar/reduced"), GlassTabBarStyle.Measured(dark = true), motionEnabled = false)
    }
}
