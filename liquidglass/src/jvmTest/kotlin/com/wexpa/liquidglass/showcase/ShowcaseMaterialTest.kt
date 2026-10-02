package com.wexpa.liquidglass.showcase

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wexpa.liquidglass.GlassLight
import com.wexpa.liquidglass.GlassSquircleShape
import com.wexpa.liquidglass.GlassStyle
import com.wexpa.liquidglass.lerpGlassStyle
import com.wexpa.liquidglass.liquidGlass
import kotlin.test.Test

/** Every material knob a host reaches for, side by side over the same backdrop. Stills only. */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class ShowcaseMaterialTest {

    private fun cell(label: String, style: GlassStyle, shape: Shape = RoundedCornerShape(26.dp), w: Int = 136, h: Int = 96,
                     light: GlassLight = GlassLight.Default, materialize: Float = 1f,
                     background: Color = Color.Black, frost: Float = 0f, contrast: Float = 0f, renderScale: Float = 1f,
                     text: String? = null, textColor: Color = Color.White) =
        Cell(label, background, frost, contrast, renderScale) { state ->
            Box(
                Modifier.size(w.dp, h.dp).liquidGlass(state, shape, style, light = light, materialize = materialize),
                contentAlignment = Alignment.Center,
            ) {
                if (text != null) BasicText(text, style = TextStyle(color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
            }
        }

    private fun grid(name: String, cols: Int, cells: List<Cell>, backdrop: String = "photo", cellW: Int = 170, cellH: Int = 150) =
        showcaseScene(cellW * cols, cellH * ((cells.size + cols - 1) / cols) + GridBottomMargin.value.toInt()) {
            setContent {
                ShowcaseGrid(cols, cellW.dp, cellH.dp, cells) { m ->
                    when (backdrop) {
                        "grid" -> GridBackdrop(m)
                        "article" -> ArticleBackdrop(m)
                        "article-dark" -> ArticleBackdrop(m, dark = true)
                        else -> PhotoBackdrop(m)
                    }
                }
            }
            waitForIdle()
            shoot(Showcase.dir("material").resolve("$name.png"))
        }

    private val presets = listOf(
        "Regular" to GlassStyle.Regular,
        "inApp(dark=false)" to GlassStyle.inApp(dark = false),
        "inApp(dark=true)" to GlassStyle.inApp(dark = true),
        "Chrome (legacy)" to GlassStyle.Chrome,
        "DarkChrome (legacy)" to GlassStyle.DarkChrome,
        "Clear (legacy)" to GlassStyle.Clear,
        "Thick (legacy)" to GlassStyle.Thick,
        "coverSheet()" to GlassStyle.coverSheet(),
    )

    @Test fun presetsOverAPhoto() = grid("presets-photo", 4, presets.map { (n, s) -> cell(n, s) })

    @Test fun presetsOverAGrid() = grid("presets-grid", 4, presets.map { (n, s) -> cell(n, s) }, backdrop = "grid")

    @Test fun shapes() = grid(
        "shapes", 3,
        listOf(
            cell("CircleShape", GlassStyle.Regular, CircleShape, 110, 110),
            cell("RoundedCorner 26dp", GlassStyle.Regular),
            cell("Capsule (wide)", GlassStyle.Regular, CircleShape, 150, 54),
            cell("GlassSquircleShape n=4", GlassStyle.Regular, GlassSquircleShape(34.dp, 4f), 120, 110),
            cell("Asymmetric corners", GlassStyle.Regular, RoundedCornerShape(topStart = 44.dp, topEnd = 8.dp, bottomEnd = 44.dp, bottomStart = 8.dp)),
            cell("Star (path field)", GlassStyle.Regular, StarShape, 120, 120),
        ),
        backdrop = "grid",
    )

    @Test fun lightDirection() = grid(
        "light", 4,
        listOf(
            cell("GlassLight.Default", GlassStyle.Regular, CircleShape, 112, 112),
            cell("from top right", GlassStyle.Regular, CircleShape, 112, 112, light = GlassLight(0.7f, -0.7f)),
            cell("from below", GlassStyle.Regular, CircleShape, 112, 112, light = GlassLight(0f, 1f)),
            cell("from the left", GlassStyle.Regular, CircleShape, 112, 112, light = GlassLight(-1f, 0f)),
        ),
    )

    @Test fun materialize() = grid(
        "materialize", 5,
        listOf(0f, 0.25f, 0.5f, 0.75f, 1f).map { m ->
            cell("materialize = $m", GlassStyle.Regular, CircleShape, 120, 120, materialize = m)
        },
        cellW = 150,
    )

    @Test fun styleMorph() = grid(
        "morph", 5,
        listOf(0f, 0.25f, 0.5f, 0.75f, 1f).map { t ->
            cell("Regular→Thick $t", lerpGlassStyle(GlassStyle.Regular, GlassStyle.Thick, t), w = 124)
        },
        cellW = 150,
    )

    @Test fun legibilityOverText() = grid(
        "legibility", 3,
        listOf(
            cell("Regular", GlassStyle.Regular, CircleShape, 150, 50, text = "Label", textColor = Color(0xFF111111), background = Color(0xFFF6F4EF)),
            cell("inApp(dark=false)", GlassStyle.inApp(dark = false), CircleShape, 150, 50, text = "Label", textColor = Color(0xFF111111), background = Color(0xFFF6F4EF)),
            cell("Chrome (legacy)", GlassStyle.Chrome, CircleShape, 150, 50, text = "Label", textColor = Color(0xFF111111), background = Color(0xFFF6F4EF)),
        ),
        backdrop = "article", cellW = 200, cellH = 130,
    )

    @Test fun darkAppearanceOverText() = grid(
        "legibility-dark", 3,
        listOf(
            cell("inApp(dark=true)", GlassStyle.inApp(dark = true), CircleShape, 150, 50, text = "Label", background = Color(0xFF0B0D14)),
            cell("DarkChrome (legacy)", GlassStyle.DarkChrome, CircleShape, 150, 50, text = "Label", background = Color(0xFF0B0D14)),
            cell("Regular", GlassStyle.Regular, CircleShape, 150, 50, text = "Label", background = Color(0xFF0B0D14)),
        ),
        backdrop = "article-dark", cellW = 200, cellH = 130,
    )

    @Test fun environment() = grid(
        "environment", 4,
        listOf(
            cell("default state", GlassStyle.Regular, CircleShape, 112, 112),
            cell("frost = 1", GlassStyle.Regular, CircleShape, 112, 112, frost = 1f),
            cell("contrast = 1", GlassStyle.Regular, CircleShape, 112, 112, contrast = 1f),
            cell("frost + contrast", GlassStyle.Regular, CircleShape, 112, 112, frost = 1f, contrast = 1f),
        ),
    )

    @Test fun renderScale() = grid(
        "render-scale", 3,
        listOf(1f, 0.5f, 0.25f).map { rs ->
            cell("renderScale = $rs", GlassStyle.Regular, CircleShape, 130, 130, renderScale = rs)
        },
        backdrop = "grid",
    )

    @Test fun tint() = grid(
        "tint", 4,
        listOf(
            cell("no tint", GlassStyle.Regular.copy(tint = Color.Transparent)),
            cell("tint = blue 30%", GlassStyle.Regular.copy(tint = Color(0x4D2F6BFF))),
            cell("tint = amber 30%", GlassStyle.Regular.copy(tint = Color(0x4DFFB000))),
            cell("tint = black 45%", GlassStyle.Regular.copy(tint = Color(0x73000000))),
        ),
    )
}
