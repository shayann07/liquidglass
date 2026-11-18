package com.wexpa.liquidglass.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wexpa.liquidglass.GlassScrollEdges
import com.wexpa.liquidglass.glassScrollEdge

/**
 * Device fixture of the scroll edge effect (#207): a list of flat grey blocks (the edge's tint profile) and black and
 * white stripes (its blur), with `Modifier.glassScrollEdge` on both edges. Scrolled by `--ei item` / `--ei offset`
 * (px) so a screenshot reads the edge at a known scroll position. Dark by default.
 *
 *     adb shell am start -n com.wexpa.liquidglass.sample/.ScrollEdgeActivity [--ez dark false] [--ei item 3] [--ei offset 40]
 */
class ScrollEdgeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val dark = intent?.getBooleanExtra("dark", true) ?: true
        val item = intent?.getIntExtra("item", 2) ?: 2
        val offset = intent?.getIntExtra("offset", 0) ?: 0
        setContent { ScrollEdgeFixture(dark, item, offset) }
    }
}

@Composable
private fun ScrollEdgeFixture(dark: Boolean, item: Int, offset: Int) {
    val ground = if (dark) Color.Black else Color.White
    val state = rememberLazyListState(initialFirstVisibleItemIndex = item, initialFirstVisibleItemScrollOffset = offset)
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize().background(ground).glassScrollEdge(state, ground, GlassScrollEdges.Both, inverted = dark),
    ) {
        items(40) { index -> FixtureRow(index) }
    }
}

/** Even rows: a flat grey block (levels 32 to 224, steps of 32). Odd rows: 8 vertical stripes, black and white. */
@Composable
private fun FixtureRow(index: Int) {
    if (index % 2 == 0) {
        val level = (32 * (1 + (index / 2) % 7)) / 255f
        Box(Modifier.fillMaxWidth().height(96.dp).background(Color(level, level, level)))
    } else {
        Row(Modifier.fillMaxWidth().height(96.dp)) {
            repeat(8) { i -> Box(Modifier.weight(1f).fillMaxSize().background(if (i % 2 == 0) Color.Black else Color.White)) }
        }
    }
}
