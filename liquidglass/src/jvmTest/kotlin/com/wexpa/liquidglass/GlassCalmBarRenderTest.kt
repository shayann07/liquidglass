package com.wexpa.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes

/** Actual navigation pointer path and production material; no fitted iOS pixel target. */
@OptIn(ExperimentalTestApi::class)
class GlassCalmBarRenderTest {
    @Test fun wholeBarSurvivesFullScreenPullsWithoutMovingItsLayout() = runComposeUiTest(testTimeout = 3.minutes) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                val state = rememberLiquidGlassState(Color(0xff0a1829), renderScale = .5f)
                var selected by remember { mutableIntStateOf(1) }
                Box(Modifier.size(480.dp, 220.dp).testTag("scene")) {
                    Row(Modifier.fillMaxSize().liquidGlassSource(state)) {
                        repeat(24) { i -> Box(Modifier.width(20.dp).fillMaxHeight()
                            .background(if (i % 2 == 0) Color(0xff16334c) else Color(0xff28566d))) }
                    }
                    GlassTabBar(state, 3, selected, { selected = it },
                        Modifier.align(Alignment.Center).width(400.dp).testTag("bar-layout"),
                        style = GlassTabBarStyle.Calm()) { index, _ ->
                        Box(Modifier.size((12 + index * 4).dp).background(Color(0xffbbe5ee)))
                    }
                }
            }
        }
        waitForIdle()
        val directory = File("build/reports/atlas").apply { mkdirs() }
        val node = onNodeWithTag(GlassTabBarSemantics.TAG, useUnmergedTree = true)
        val initial = onNodeWithTag("bar-layout").fetchSemanticsNode().boundsInRoot
        fun capture(name: String) {
            ImageIO.write(onNodeWithTag("scene").captureToImage().toAwtImage(), "png", File(directory, "bar-$name.png"))
            assertEquals(initial, onNodeWithTag("bar-layout").fetchSemanticsNode().boundsInRoot,
                "drag moved the bar's layout or hit target")
            assertEquals(true, node.fetchSemanticsNode().config[GlassTabBarSemantics.SelectorValid])
        }
        capture("rest")
        node.performTouchInput { down(Offset(200f,31f)); advanceEventTime(800); moveTo(Offset(200f,31f)) }
        waitForIdle();capture("held")
        for ((label, position) in listOf(
            "top" to Offset(200f,-2000f), "bottom" to Offset(200f,2000f),
            "left" to Offset(-2000f,31f), "right" to Offset(2000f,31f),
            "corner" to Offset(-2000f,-2000f),
        )) {
            node.performTouchInput { advanceEventTime(250);moveTo(position) }
            waitForIdle();capture(label)
        }
        node.performTouchInput { up() };waitForIdle();capture("released")
        assertEquals(false,node.fetchSemanticsNode().config[GlassTabBarSemantics.Held])
    }
}
