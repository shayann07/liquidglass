package com.wexpa.liquidglass.showcase

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wexpa.liquidglass.GlassStyle
import com.wexpa.liquidglass.GlassTabBar
import com.wexpa.liquidglass.GlassTabBarStyle
import com.wexpa.liquidglass.liquidGlass
import com.wexpa.liquidglass.liquidGlassSource
import com.wexpa.liquidglass.rememberLiquidGlassState
import kotlin.test.Test

/**
 * The library in an app: an article scrolling under a glass toolbar, two round glass buttons,
 * a floating glass card and a glass tab bar. Only public API.
 */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class ShowcaseAppTest {

    private var scroll by mutableFloatStateOf(0f)
    private var selected by mutableIntStateOf(0)

    @androidx.compose.runtime.Composable
    private fun App(dark: Boolean = false, renderScale: Float = 1f) {
        val ground = if (dark) Color(0xFF0B0D14) else Color(0xFFF6F4EF)
        val ink = if (dark) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
        val glass = rememberLiquidGlassState(background = ground, renderScale = renderScale)
        Box(Modifier.fillMaxSize()) {
            // 1. The content, marked as the backdrop. It scrolls; the glass does not.
            ArticleBackdrop(
                dark = dark,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(unbounded = true, align = Alignment.Top)
                    .offset { IntOffset(0, (-scroll).toInt()) }
                    .padding(top = 64.dp)
                    .liquidGlassSource(glass),
            )

            // 2. A glass toolbar: title pill and two round buttons.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(44.dp).liquidGlass(glass, CircleShape, GlassStyle.inApp(dark = dark)),
                    contentAlignment = Alignment.Center) { Glyph(1, ink, 20.dp) }
                Box(
                    Modifier.weight(1f).height(44.dp).liquidGlass(glass, CircleShape, GlassStyle.inApp(dark = dark)),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText("Reading", style = TextStyle(color = ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
                }
                Box(Modifier.size(44.dp).liquidGlass(glass, CircleShape, GlassStyle.inApp(dark = dark)),
                    contentAlignment = Alignment.Center) { Glyph(2, Color(0xFFFF375F), 20.dp) }
            }

            // 3. A floating card over the page.
            Column(
                Modifier
                    .align(Alignment.Center)
                    .offset(y = 40.dp)
                    .padding(horizontal = 28.dp)
                    .fillMaxWidth()
                    .liquidGlass(glass, RoundedCornerShape(28.dp), if (dark) GlassStyle.inApp(dark = true) else GlassStyle.Regular)
                    .padding(20.dp),
            ) {
                BasicText("Now playing", style = TextStyle(color = ink.copy(alpha = 0.7f), fontSize = 12.sp))
                BasicText("Refraction, live", style = TextStyle(color = ink, fontSize = 22.sp, fontWeight = FontWeight.Bold))
            }

            // 4. The tab bar.
            GlassTabBar(
                state = glass,
                itemCount = 4,
                selectedIndex = selected,
                onSelected = { selected = it },
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 18.dp, vertical = 22.dp),
                style = GlassTabBarStyle.Measured(dark = dark),
            ) { index, isSelected -> TabItem(index, isSelected, dark = dark) }
        }
    }

    @Test fun appLight() = showcaseScene(390, 760) {
        scroll = 0f
        setContent { App() }
        waitForIdle()
        shoot(Showcase.dir("app").resolve("app-light.png"))
        scroll = 520f
        waitForIdle()
        shoot(Showcase.dir("app").resolve("app-light-scrolled.png"))
    }

    @Test fun appDark() = showcaseScene(390, 760) {
        scroll = 520f
        setContent { App(dark = true) }
        waitForIdle()
        shoot(Showcase.dir("app").resolve("app-dark.png"))
    }

    /** Video frames: lower density and half-resolution material to keep a CPU render tractable. */
    @Test fun appScrolling() = showcaseScene(390, 760, density = 1f) {
        scroll = 0f
        setContent { App(renderScale = 0.5f) }
        waitForIdle()
        mainClock.autoAdvance = false
        record(Showcase.dir("app/scroll"), frames = 40, stepMs = 50) { i ->
            val t = i / 39f
            val u = if (t < 0.7f) t / 0.7f else 1f - (t - 0.7f) / 0.3f * 0.35f
            scroll = u * u * (3 - 2 * u) * 700f
        }
    }
}
