package com.wexpa.liquidglass.showcase

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Duration.Companion.minutes
import org.junit.Assume.assumeTrue
import com.wexpa.liquidglass.LiquidGlassState
import com.wexpa.liquidglass.liquidGlassSource
import com.wexpa.liquidglass.rememberLiquidGlassState

/**
 * Shared pieces of the showcase render: an output directory, a scene runner at a phone density,
 * a frame recorder, and a backdrop with enough colour, edges and text for the glass to bend.
 *
 * Opt-in: nothing runs unless -Pliquidglass.showcase=<dir> is passed, so CI never pays for it.
 */
internal object Showcase {
    val outDir: File? = System.getProperty("liquidglass.showcase")?.let { File(it).apply { mkdirs() } }

    fun require() = assumeTrue("pass -Pliquidglass.showcase=<dir> to render the showcase", outDir != null)

    fun dir(name: String): File = File(outDir!!, name).apply { mkdirs() }
}

/** Runs [block] in a scene of [widthDp] x [heightDp] at [density], with a generous deadline. */
@OptIn(ExperimentalTestApi::class)
internal fun showcaseScene(
    widthDp: Int,
    heightDp: Int,
    density: Float = 2f,
    block: SkikoComposeUiTest.() -> Unit,
) {
    Showcase.require()
    runSkikoComposeUiTest(
        size = Size(widthDp * density, heightDp * density),
        density = Density(density),
        testTimeout = 30.minutes,
    ) { block() }
}

/** Captures the whole scene to [file] as PNG. */
@OptIn(ExperimentalTestApi::class)
internal fun SkikoComposeUiTest.shoot(file: File) {
    val image = onRoot().captureToImage().toAwtImage()
    file.parentFile.mkdirs()
    ImageIO.write(image, "png", file)
}

/** Records [frames] frames at [stepMs] each into [dir]/frame-NNNN.png, calling [onFrame] before each. */
@OptIn(ExperimentalTestApi::class)
internal fun SkikoComposeUiTest.record(
    dir: File,
    frames: Int,
    stepMs: Long = 16L,
    startAt: Int = 0,
    onFrame: (Int) -> Unit = {},
): Int {
    dir.mkdirs()
    for (i in 0 until frames) {
        onFrame(i)
        mainClock.advanceTimeBy(stepMs)
        shoot(File(dir, "frame-%04d.png".format(startAt + i)))
    }
    return startAt + frames
}

// ------------------------------------------------------------------------------------------
// Backdrops

internal val Ink = Color(0xFF0E1222)

/** A sunset "photo": saturated gradients and hard silhouettes, the kind of thing glass is over. */
@Composable
internal fun PhotoBackdrop(modifier: Modifier = Modifier, phase: Float = 0f) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(listOf(Color(0xFF1B1F5E), Color(0xFF7A2E8E), Color(0xFFF2675A), Color(0xFFFFC25C))))
        drawCircle(Color(0xFFFFE6A3), radius = h * 0.16f, center = Offset(w * (0.68f + 0.05f * sin(phase)), h * 0.58f))
        drawCircle(Brush.radialGradient(listOf(Color(0x80FFFFFF), Color.Transparent), center = Offset(w * 0.68f, h * 0.58f), radius = h * 0.4f),
            radius = h * 0.4f, center = Offset(w * 0.68f, h * 0.58f))
        // Two ridges of mountains.
        fun ridge(base: Float, amp: Float, freq: Float, color: Color, shift: Float) {
            val p = Path()
            p.moveTo(0f, h)
            var x = 0f
            while (x <= w) {
                val y = base + amp * (sin(x / w * freq + shift) * 0.6f + sin(x / w * freq * 2.3f + shift * 1.7f) * 0.4f)
                p.lineTo(x, y)
                x += w / 80f
            }
            p.lineTo(w, h); p.close()
            drawPath(p, color)
        }
        ridge(h * 0.70f, h * 0.06f, 7f, Color(0xFF3B1F4F), 0.4f)
        ridge(h * 0.80f, h * 0.05f, 11f, Color(0xFF1A1030), 2.1f)
        // Stars: small hard dots that show refraction and dispersion clearly.
        for (i in 0 until 60) {
            val sx = ((i * 73) % 100) / 100f * w
            val sy = ((i * 37) % 45) / 100f * h
            drawCircle(Color.White.copy(alpha = 0.85f), radius = 1.2f + (i % 3), center = Offset(sx, sy))
        }
    }
}

/** A light article page: headings, body text and coloured tiles. Tests legibility under glass. */
@Composable
internal fun ArticleBackdrop(modifier: Modifier = Modifier, dark: Boolean = false) {
    val ground = if (dark) Color(0xFF0B0D14) else Color(0xFFF6F4EF)
    val text = if (dark) Color(0xFFE8E8EE) else Color(0xFF15161A)
    val sub = if (dark) Color(0xFF9CA0B0) else Color(0xFF5D6170)
    Column(modifier.background(ground).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(18.dp))
        BasicText("Field notes", style = TextStyle(color = sub, fontSize = 13.sp, fontWeight = FontWeight.Medium))
        BasicText("Light, bent", style = TextStyle(color = text, fontSize = 34.sp, fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(Color(0xFFFF5A5F), Color(0xFFFFB400), Color(0xFF00A699), Color(0xFF3D5AFE), Color(0xFFB620E0)).forEach {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(it))
            }
        }
        Spacer(Modifier.height(14.dp))
        val body = "Glass does not hide what is behind it. It bends it, so the edge of a panel " +
            "shows the page curving away, and colours split a little where the rim is " +
            "steepest. Everything under this paragraph is ordinary content: text, tiles, a " +
            "photo. The glass is drawn on top and reads all of it."
        repeat(3) {
            BasicText(body, style = TextStyle(color = text, fontSize = 15.sp, lineHeight = 21.sp))
            Spacer(Modifier.height(12.dp))
        }
        Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(20.dp))) {
            PhotoBackdrop(Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(12.dp))
        repeat(3) {
            BasicText(body, style = TextStyle(color = text, fontSize = 15.sp, lineHeight = 21.sp))
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Diagonal stripes and a grid: the backdrop that makes displacement easiest to read. */
@Composable
internal fun GridBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(Brush.linearGradient(listOf(Color(0xFF00C6FF), Color(0xFF7B2FF7), Color(0xFFFF4E8A)),
            start = Offset.Zero, end = Offset(size.width, size.height)))
        val step = 18.dp.toPx()
        var x = 0f
        while (x < size.width) { drawLine(Color.White.copy(alpha = 0.55f), Offset(x, 0f), Offset(x, size.height), 1.5f); x += step }
        var y = 0f
        while (y < size.height) { drawLine(Color.White.copy(alpha = 0.55f), Offset(0f, y), Offset(size.width, y), 1.5f); y += step }
        for (i in 0 until 6) {
            drawCircle(Color(0xFFFFE14D), radius = 10.dp.toPx(), center = Offset(size.width * (i + 0.5f) / 6f, size.height * 0.5f))
        }
    }
}

// ------------------------------------------------------------------------------------------
// Little icons and a star shape, drawn rather than loaded.

@Composable
internal fun Glyph(kind: Int, color: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val c = center
        val stroke = Stroke(width = s * 0.1f)
        when (kind % 4) {
            0 -> { // house
                val p = Path().apply {
                    moveTo(c.x - s * 0.4f, c.y); lineTo(c.x, c.y - s * 0.4f); lineTo(c.x + s * 0.4f, c.y)
                    lineTo(c.x + s * 0.4f, c.y + s * 0.4f); lineTo(c.x - s * 0.4f, c.y + s * 0.4f); close()
                }
                drawPath(p, color, style = stroke)
            }
            1 -> { // magnifier
                drawCircle(color, radius = s * 0.26f, center = Offset(c.x - s * 0.08f, c.y - s * 0.08f), style = stroke)
                drawLine(color, Offset(c.x + s * 0.12f, c.y + s * 0.12f), Offset(c.x + s * 0.4f, c.y + s * 0.4f), s * 0.1f)
            }
            2 -> { // heart-ish
                drawCircle(color, radius = s * 0.18f, center = Offset(c.x - s * 0.15f, c.y - s * 0.1f))
                drawCircle(color, radius = s * 0.18f, center = Offset(c.x + s * 0.15f, c.y - s * 0.1f))
                val p = Path().apply { moveTo(c.x - s * 0.33f, c.y - s * 0.02f); lineTo(c.x, c.y + s * 0.38f); lineTo(c.x + s * 0.33f, c.y - s * 0.02f); close() }
                drawPath(p, color)
            }
            else -> { // person
                drawCircle(color, radius = s * 0.17f, center = Offset(c.x, c.y - s * 0.18f), style = stroke)
                drawArc(color, 200f, 140f, false, topLeft = Offset(c.x - s * 0.36f, c.y + s * 0.05f),
                    size = Size(s * 0.72f, s * 0.6f), style = stroke)
            }
        }
    }
}

internal val TabLabels = listOf("Home", "Search", "Saved", "Profile")

@Composable
internal fun TabItem(index: Int, selected: Boolean, dark: Boolean = true) {
    val c = when {
        selected -> Color(0xFF4DA3FF)
        dark -> Color.White.copy(alpha = 0.92f)
        else -> Color(0xFF1C1C1E)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Glyph(index, c)
        BasicText(TabLabels[index % TabLabels.size], style = TextStyle(color = c, fontSize = 10.sp, fontWeight = FontWeight.Medium))
    }
}

@Composable
internal fun Caption(text: String, color: Color = Color.White, modifier: Modifier = Modifier, size: Int = 13) {
    BasicText(text, modifier, style = TextStyle(color = color, fontSize = size.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
}

/** A five-pointed star: no closed form, so the library measures it into a distance field. */
internal val StarShape: Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val p = Path()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outer = minOf(cx, cy)
        val inner = outer * 0.5f
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outer else inner
            val a = -PI / 2 + i * PI / 5
            val x = cx + (r * cos(a)).toFloat()
            val y = cy + (r * sin(a)).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        return Outline.Generic(p)
    }
}


// ------------------------------------------------------------------------------------------
// A comparison grid. Each cell is its own scene with its own LiquidGlassState, showing its slice
// of one shared backdrop, so per-state settings (frost, contrast, render scale) can sit side by side.

internal class Cell(
    val label: String,
    val background: Color = Color.Black,
    val frost: Float = 0f,
    val contrast: Float = 0f,
    val renderScale: Float = 1f,
    val content: @Composable BoxScope.(LiquidGlassState) -> Unit,
)

@Composable
internal fun ShowcaseGrid(
    cols: Int,
    cellW: Dp,
    cellH: Dp,
    cells: List<Cell>,
    backdrop: @Composable (Modifier) -> Unit,
) {
    val rows = (cells.size + cols - 1) / cols
    val sceneW = cellW * cols
    val sceneH = cellH * rows
    Column(Modifier.background(Ink)) {
        for (r in 0 until rows) Row {
            for (c in 0 until cols) {
                val i = r * cols + c
                // Only the backdrop slice is clipped. A clipped ancestor around the glass itself
                // cuts off the blur strip the panel records below its own bounds on desktop, and
                // the lower part of the panel then reads the ground colour.
                Box(Modifier.size(cellW, cellH)) {
                    if (i < cells.size) {
                        val cell = cells[i]
                        val state = rememberLiquidGlassState(
                            background = cell.background, frost = cell.frost,
                            contrast = cell.contrast, renderScale = cell.renderScale,
                        )
                        Box(Modifier.fillMaxSize().clipToBounds()) {
                            backdrop(
                                Modifier
                                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                                    .requiredSize(sceneW, sceneH)
                                    .offset(x = -cellW * c, y = -cellH * r)
                                    .liquidGlassSource(state),
                            )
                        }
                        Box(Modifier.fillMaxSize().padding(bottom = 26.dp), contentAlignment = Alignment.Center) {
                            cell.content(this, state)
                        }
                        Box(
                            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(26.dp).background(Color(0xCC0E1222)),
                            contentAlignment = Alignment.Center,
                        ) { Caption(cell.label, size = 11) }
                    }
                }
            }
        }
        // Room below the last row: each panel records a blur strip under its own bounds, and on
        // desktop the window clips it. The showcase crops this margin off afterwards.
        Spacer(Modifier.fillMaxWidth().height(GridBottomMargin))
    }
}

internal val GridBottomMargin = 50.dp
