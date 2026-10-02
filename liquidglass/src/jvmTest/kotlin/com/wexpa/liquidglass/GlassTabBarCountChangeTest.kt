package com.wexpa.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A tab added and selected in the same update. The selection reaches the selector before the bar
 * that holds it, so an index clamped against the old count rested the body on the wrong tab.
 */
@OptIn(ExperimentalTestApi::class)
class GlassTabBarCountChangeTest {
    private class Host {
        var count by mutableIntStateOf(3)
        var selected by mutableIntStateOf(1)
    }

    private fun addAndSelect(style: GlassTabBarStyle, label: String) = runComposeUiTest {
        val host = Host()
        setContent {
            val state = rememberLiquidGlassState(background = Color.Black, renderScale = 0.25f)
            Box(Modifier.size(500.dp, 200.dp)) {
                Box(Modifier.fillMaxSize().background(Color.DarkGray).liquidGlassSource(state))
                GlassTabBar(
                    state = state,
                    itemCount = host.count,
                    selectedIndex = host.selected,
                    onSelected = { host.selected = it },
                    modifier = Modifier.width(500.dp),
                    style = style,
                ) { _, _ -> Box(Modifier.size(20.dp)) }
            }
        }
        waitForIdle()
        Snapshot.withMutableSnapshot {
            host.count = 4
            host.selected = 3
        }
        waitForIdle()
        val x = onNodeWithTag(GlassTabBarSemantics.TAG, useUnmergedTree = true)
            .fetchSemanticsNode().config[GlassTabBarSemantics.SelectorX]
        val expected = GlassTabBarGeometry(barWidth = 500f, inset = 6f, count = 4).centreOf(3)
        assertTrue(abs(x - expected) < 0.5f, "$label: selector at $x, the new last tab is at $expected")
    }

    @Test fun capsule() = addAndSelect(GlassTabBarStyle.Measured(dark = true), "capsule")

    @Test fun v3() = addAndSelect(GlassTabBarStyle.V3(dark = true), "V3")
}
