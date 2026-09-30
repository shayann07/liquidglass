package com.wexpa.liquidglass.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wexpa.liquidglass.GlassTabBar
import com.wexpa.liquidglass.GlassTabBarStyle
import com.wexpa.liquidglass.liquidGlassSource
import com.wexpa.liquidglass.rememberLiquidGlassState

/**
 * The deterministic tab-bar fixture of `V3-MODEL.md` section 15.3, on a device.
 *
 * A fixed, busy, high-contrast page that scrolls **under** a bar that never hides, five items with
 * short and long labels, and large and small stroke detail in every glyph. That combination is
 * what the owner's four outcomes are actually about: page structure at the bar's straight edges,
 * ink clarity through the selector, tap travel inside the bar, and held growth around a label.
 *
 * `adb shell am start -n com.wexpa.liquidglass.sample/.TabBarFixtureActivity [--ez v3 false]`
 *
 * `v3` false selects the Phase 3 capsule selector on the same measured material, so the same
 * scene can be captured on both and compared frame for frame. Nothing here is tuned per capture:
 * the page is generated from its own coordinates and does not depend on time.
 */
class TabBarFixtureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val v3 = intent?.getBooleanExtra("v3", true) ?: true
        val phone = intent?.getBooleanExtra("phone", false) ?: false
        val compact = intent?.getBooleanExtra("compact", false) ?: false
        setContent { TabBarFixture(v3, phone, compact) }
    }
}

private val Labels = listOf("Now", "Activity", "Communities", "Chats", "You")

@Composable
private fun TabBarFixture(v3: Boolean, phone: Boolean, compact: Boolean) {
    // Matched Phone proportions from IMG_6756, independent of the Vitals page/content.
    // Android glyph/font shapes still differ; this fixture is a geometry/optics comparison,
    // not a whole-image pixel parity gate.
    val labels = if (phone) listOf("Calls", "Contacts", "Keypad") else Labels
    val state = rememberLiquidGlassState(background = Color.Black)
    var selected by remember { mutableIntStateOf(2) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .liquidGlassSource(state)
                .verticalScroll(rememberScrollState()),
        ) {
            if (!phone) repeat(24) { row -> PageRow(row) }
        }
        GlassTabBar(
            state = state,
            itemCount = labels.size,
            selectedIndex = selected,
            onSelected = { selected = it },
            modifier = if (phone) Modifier.align(Alignment.BottomStart)
                .padding(start = 21.67.dp, bottom = 21.33.dp).width(278.dp)
                else if (compact) Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp).width(348.dp)
                else Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 24.dp),
            style = if (v3) {
                GlassTabBarStyle.V3(dark = true).copy(
                    height = 62.dp, shape = RoundedCornerShape(percent = 50),
                    // IMG_6756 Phone and settled6701 both support about4pt per side.
                    pillInset = 4.dp,
                    pillWidth = if (phone) (284f / 3f).dp else 84.dp,
                )
            } else {
                GlassTabBarStyle.Measured(dark = true).copy(height = 62.dp, shape = RoundedCornerShape(percent = 50))
            },
        ) { index, isSelected ->
            FixtureTab(labels[index], isSelected)
        }
    }
}

/** One page row: a bright plate, a dense stripe field and readable text, none of it timed. */
@Composable
private fun PageRow(row: Int) {
    val warm = row % 3 == 0
    Column(Modifier.fillMaxWidth().height(96.dp)) {
        Canvas(Modifier.fillMaxWidth().height(48.dp)) {
            drawRect(if (warm) Color(0xFF20242C) else Color(0xFF0B0D12))
            var x = (row * 7 % 13).toFloat()
            var i = 0
            while (x < size.width) {
                val w = 4f + (i % 6) * 4f
                drawRect(
                    color = when ((i + row) % 4) {
                        0 -> Color(0xFFEDEDED)
                        1 -> Color(0xFF2E4F8F)
                        2 -> Color(0xFFCF5A2A)
                        else -> Color(0xFF17181C)
                    },
                    topLeft = Offset(x, 0f),
                    size = Size(w, size.height),
                )
                x += w + 6f
                i++
            }
        }
        Text(
            text = "Row $row  ABCDEFGHIJ 0123456789 the quick brown fox",
            color = Color(0xFFBFC6D2),
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            textAlign = TextAlign.Start,
        )
    }
}

/** A glyph with both a heavy stroke and a hairline, plus the label, in one colour. */
@Composable
private fun FixtureTab(label: String, selected: Boolean) {
    val ink = if (selected) Color(0xFF6FC7FF) else Color(0xFFCFCFCF)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Canvas(Modifier.size(22.dp)) {
            drawRect(ink, Offset(2f, 2f), Size(size.width - 4f, 2.5f))
            drawRect(ink, Offset(2f, size.height / 2f), Size(size.width - 4f, 1f))
            drawRect(ink, Offset(size.width / 2f - 1.25f, 2f), Size(2.5f, size.height - 4f))
        }
        Text(
            text = label,
            color = ink,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}
