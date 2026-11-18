package com.wexpa.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Evidence frames for search mode (#112) and the toolbar group (#91/#102), for `review/`. Opt-in: nothing is written
 * unless `EVIDENCE_DIR=<dir>` is set, so a normal suite run leaves no files. Each frame is a Skia software render at
 * 3 px per dp from the desktop test host over a colourful ground (the glass needs something to refract), not a device
 * capture. Files: `112-search-{rest,expanded}-{light,dark}.png` and `91-toolbar-group-{light,dark}.png`.
 */
@OptIn(ExperimentalTestApi::class)
class GlassSearchToolbarEvidenceTest {
    private val dir: File? = System.getenv("EVIDENCE_DIR")?.takeIf { it.isNotBlank() }?.let { File(it).apply { mkdirs() } }

    private val stripes = Brush.horizontalGradient(listOf(Color(0xFFE35D6A), Color(0xFFF2B33D), Color(0xFF3CC47C), Color(0xFF4C8DF6)))

    private fun ground(dark: Boolean): Color = if (dark) Color(0xFF14141A) else Color(0xFFF2F2F7)

    private fun shoot(name: String, content: @Composable () -> Unit) = runComposeUiTest(testTimeout = 6.minutes) {
        setContent { CompositionLocalProvider(LocalDensity provides Density(3f)) { content() } }
        mainClock.autoAdvance = false
        repeat(14) { mainClock.advanceTimeBy(50, ignoreFrameDuration = true) }
        val img = onNodeWithTag("scene").captureToImage().toAwtImage()
        ImageIO.write(img, "png", File(dir!!, "$name.png"))
        assertTrue(img.width > 0)
    }

    private fun search(dark: Boolean, expanded: Boolean) {
        if (dir == null) return
        val state = GlassSearchState(initiallyExpanded = expanded)
        val ink = if (dark) Color.White else Color(0xFF1C1C1E)
        shoot("112-search-${if (expanded) "expanded" else "rest"}-${if (dark) "dark" else "light"}") {
            LiquidGlassTheme(dark = dark, background = ground(dark)) {
                Box(Modifier.size(400.dp, 150.dp).background(ground(dark)).testTag("scene")) {
                    Box(Modifier.fillMaxSize().padding(top = 18.dp, bottom = 70.dp).background(stripes))
                    GlassTabBar(3, 1, {}, state, {
                        Box(Modifier.size(120.dp, 22.dp).focusable().background(ink.copy(alpha = .35f), CircleShape))
                    }, Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp).width(372.dp),
                        style = GlassTabBarStyle.Calm(dark).copy(height = 62.dp), motionEnabled = true) { _, selected ->
                        Box(Modifier.size(20.dp).clip(CircleShape).background(if (selected) Color(0xFF0A84FF) else ink.copy(alpha = .7f)))
                    }
                }
            }
        }
    }

    private fun toolbar(dark: Boolean) {
        if (dir == null) return
        val ink = if (dark) Color.White else Color(0xFF1C1C1E)
        shoot("91-toolbar-group-${if (dark) "dark" else "light"}") {
            LiquidGlassTheme(dark = dark, background = ground(dark)) {
                Box(Modifier.size(400.dp, 190.dp).background(stripes).testTag("scene")) {
                    Column(Modifier.align(Alignment.Center), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        GlassToolbarGroup { repeat(3) { Box(Modifier.padding(6.dp).size(32.dp).clip(CircleShape).background(ink.copy(alpha = .8f))) } }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            GlassToolbarGroup { repeat(2) { Box(Modifier.padding(6.dp).size(32.dp).clip(CircleShape).background(ink.copy(alpha = .8f))) } }
                            GlassToolbarGroup(glass = Glass.Regular, minHeight = 52.dp) { Box(Modifier.padding(6.dp).size(32.dp).clip(CircleShape).background(ink.copy(alpha = .8f))) }
                        }
                    }
                }
            }
        }
    }

    @Test fun searchRestLight() = search(dark = false, expanded = false)
    @Test fun searchRestDark() = search(dark = true, expanded = false)
    @Test fun searchExpandedLight() = search(dark = false, expanded = true)
    @Test fun searchExpandedDark() = search(dark = true, expanded = true)
    @Test fun toolbarGroupLight() = toolbar(dark = false)
    @Test fun toolbarGroupDark() = toolbar(dark = true)
}
