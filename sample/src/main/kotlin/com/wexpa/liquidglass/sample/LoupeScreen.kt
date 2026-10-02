package com.wexpa.liquidglass.sample

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wexpa.liquidglass.GlassInteraction
import com.wexpa.liquidglass.GlassProfile
import com.wexpa.liquidglass.GlassStyle
import com.wexpa.liquidglass.GlassTabBar
import com.wexpa.liquidglass.GlassTabBarStyle
import com.wexpa.liquidglass.liquidGlass
import com.wexpa.liquidglass.liquidGlassSource
import com.wexpa.liquidglass.rememberGlassPressSource
import com.wexpa.liquidglass.rememberLiquidGlassState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.launch

/**
 * Loupe: a star atlas you read through a piece of glass.
 *
 * The sky is drawn too small to read. A round glass loupe magnifies whatever is under it, and the
 * constellation names exist only inside the glass: they are drawn as the lens's own content, so
 * they appear where the loupe is and bend with its rim. Long-press the sky to summon the loupe
 * where you pressed; it materializes rather than fading. Drag it to read; drag the sky to pan.
 *
 * Only public liquidglass API. Every number here is authored for this demo, not measured.
 */
@Composable
fun LoupeScreen(reduceMotion: Boolean = false, modifier: Modifier = Modifier, loupeStyle: GlassStyle = LoupeStyle) {
    val density = LocalDensity.current
    val glass = rememberLiquidGlassState(background = SkyGround)
    val atlas = remember { Atlas.generate(seed = 27) }
    // Big enough to hold every label of a mode, so a redraw never re-measures text.
    val measurer = rememberTextMeasurer(cacheSize = 64)
    val scope = rememberCoroutineScope()

    var screen by remember { mutableStateOf(IntSize.Zero) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var mode by remember { mutableIntStateOf(0) }
    // The loupe's centre is read only in layout and draw lambdas, so dragging it never recomposes the
    // screen (which would redraw 1500 stars). Composition reads [loupeShown] instead.
    var loupeCentre by remember { mutableStateOf(Offset.Unspecified) }
    var loupeShown by remember { mutableStateOf(false) }
    val presence = remember { Animatable(0f) }
    val press = rememberGlassPressSource()

    val loupeSizePx = with(density) { LoupeSize.toPx() }
    val skyPx = with(density) { Size(Atlas.WIDTH.dp.toPx(), Atlas.HEIGHT.dp.toPx()) }

    // Sky coordinates (0..1 across the atlas) to screen pixels, and back.
    fun toScreen(p: Offset) = Offset(p.x * skyPx.width * zoom, p.y * skyPx.height * zoom) + pan
    fun toSky(s: Offset) = Offset((s.x - pan.x) / (skyPx.width * zoom), (s.y - pan.y) / (skyPx.height * zoom))
    fun clampPan(p: Offset): Offset {
        val minX = (screen.width - skyPx.width * zoom).coerceAtMost(0f)
        val minY = (screen.height - skyPx.height * zoom).coerceAtMost(0f)
        return Offset(p.x.coerceIn(minX, 0f), p.y.coerceIn(minY, 0f))
    }

    // Between the toolbar and the tab bar: over either, the loupe would take their touches.
    val topLimit = with(density) { (48 + 48 + 12).dp.toPx() } + loupeSizePx / 2f
    val bottomLimit = with(density) { (34 + 64 + 12).dp.toPx() } + loupeSizePx / 2f
    fun clampLoupe(c: Offset): Offset {
        if (screen == IntSize.Zero) return c
        val maxY = (screen.height - bottomLimit).coerceAtLeast(topLimit)
        return Offset(c.x.coerceIn(loupeSizePx / 2f, screen.width - loupeSizePx / 2f), c.y.coerceIn(topLimit, maxY))
    }

    fun summon(at: Offset) {
        loupeCentre = clampLoupe(at)
        loupeShown = true
        scope.launch {
            if (reduceMotion) presence.snapTo(1f) else presence.animateTo(1f, tween(380))
        }
    }

    fun dismiss() {
        scope.launch {
            if (reduceMotion) presence.snapTo(0f) else presence.animateTo(0f, tween(260))
            loupeCentre = Offset.Unspecified
            loupeShown = false
        }
    }

    // Named only when the loupe is actually over something: within half a loupe of it.
    val nearest by remember {
        derivedStateOf {
            if (!loupeCentre.isSpecified()) null
            else atlas.nearest(toSky(loupeCentre), mode, loupeSizePx / 2f / (skyPx.width * zoom))
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(SkyGround)
            .onSizeChanged {
                if (screen == IntSize.Zero) {
                    // Open with the loupe already out, over the first constellation.
                    screen = it
                    val target = Offset(it.width / 2f, it.height * 0.45f)
                    val c = atlas.figures.first().centre
                    pan = clampPan(target - Offset(c.x * skyPx.width, c.y * skyPx.height))
                    summon(target + (toScreen(c) - target))
                } else {
                    screen = it
                }
            },
    ) {
        // 1. The sky: the backdrop the glass refracts. Pans under a drag, summons the loupe on a long press.
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, delta ->
                        change.consume()
                        pan = clampPan(pan + delta)
                    }
                }
                .pointerInput(Unit) { detectTapGestures(onLongPress = { summon(it) }) }
                .liquidGlassSource(glass),
        ) {
            drawSky(atlas, mode, ::toScreen, zoom, labelled = false, measurer = measurer, density = density.density)
        }

        // 2. The loupe: magnifying glass whose content is the readable layer of the atlas.
        if (loupeShown && presence.value > 0f) {
            fun topLeft() = loupeCentre - Offset(loupeSizePx / 2f, loupeSizePx / 2f)
            Box(
                Modifier
                    .offset { topLeft().let { IntOffset(it.x.roundToInt(), it.y.roundToInt()) } }
                    .size(LoupeSize)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { press.press(it) },
                            onDragEnd = { press.release() },
                            onDragCancel = { press.release() },
                        ) { change, delta ->
                            change.consume()
                            loupeCentre = clampLoupe(loupeCentre + delta)
                            press.press(change.position)
                        }
                    }
                    .liquidGlass(
                        state = glass,
                        shape = CircleShape,
                        style = loupeStyle,
                        interaction = if (reduceMotion) GlassInteraction.ReducedMotion else GlassInteraction.Default,
                        pressSource = press,
                        materialize = presence.value,
                        refractContent = true,
                    ),
            ) {
                // Drawn in screen space and shifted into the loupe's frame: anything outside the
                // circle is simply not seen, so the names exist only where the glass is.
                Canvas(Modifier.fillMaxSize()) {
                    val shift = -topLeft()
                    drawSky(atlas, mode, { p -> toScreen(p) + shift }, zoom, labelled = true, measurer = measurer,
                        density = density.density, onlyLabels = true)
                }
            }
        }

        // 3. The toolbar: what the loupe is over, zoom, and putting the loupe away.
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassButton(glass, "−", reduceMotion) {
                val c = Offset(screen.width / 2f, screen.height / 2f)
                val before = toSky(c)
                zoom = (zoom / 1.4f).coerceAtLeast(1f)
                pan = clampPan(c - Offset(before.x * skyPx.width * zoom, before.y * skyPx.height * zoom))
            }
            Column(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .liquidGlass(glass, CircleShape, GlassStyle.inApp(dark = true)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                BasicText(
                    nearest?.name ?: (if (loupeShown) "Drag the loupe" else "Long-press the sky"),
                    style = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                )
                BasicText(
                    nearest?.detail ?: (if (loupeShown) "over a ${listOf("constellation", "planet", "nebula")[mode]} to name it" else "to bring out the loupe"),
                    style = TextStyle(color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp),
                    maxLines = 1,
                )
            }
            GlassButton(glass, "+", reduceMotion) {
                val c = Offset(screen.width / 2f, screen.height / 2f)
                val before = toSky(c)
                zoom = (zoom * 1.4f).coerceAtMost(3f)
                pan = clampPan(c - Offset(before.x * skyPx.width * zoom, before.y * skyPx.height * zoom))
            }
            GlassButton(glass, if (loupeShown) "×" else "◎", reduceMotion) {
                if (loupeShown) dismiss() else summon(Offset(screen.width / 2f, screen.height * 0.45f))
            }
        }

        // 4. What the atlas shows. The loupe stays where it is across modes.
        GlassTabBar(
            state = glass,
            itemCount = Modes.size,
            selectedIndex = mode,
            onSelected = { mode = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 28.dp, end = 28.dp, bottom = 34.dp)
                .widthIn(max = 420.dp),
            style = GlassTabBarStyle.V3(dark = true),
            motionEnabled = !reduceMotion,
        ) { index, selected ->
            BasicText(
                Modes[index],
                style = TextStyle(
                    color = if (selected) Color(0xFF8EC5FF) else Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
    }
}

@Composable
private fun GlassButton(glass: com.wexpa.liquidglass.LiquidGlassState, label: String, reduceMotion: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .liquidGlass(
                glass, CircleShape, GlassStyle.inApp(dark = true),
                interaction = if (reduceMotion) GlassInteraction.ReducedMotion else GlassInteraction.Pullable,
            )
            .pointerInput(onClick) { detectTapGestures(onTap = { onClick() }) },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = TextStyle(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium))
    }
}

private fun Offset.isSpecified() = this != Offset.Unspecified && x.isFinite() && y.isFinite()

internal val SkyGround = Color(0xFF05060C)
internal val Modes = listOf("Stars", "Planets", "Deep sky")
internal val LoupeSize = 150.dp

/** Clear glass for the loupe: Regular with every blur removed. Authored for this demo, not measured. */
internal val LoupeStyleClear: GlassStyle = GlassStyle.Regular.copy(
    // A loupe is clear: no frost, no scatter, almost no tint.
    blurRadius = 0.dp,
    backdropBlur = 0.dp,
    wideKernel = 0.dp,
    tint = Color.Black.copy(alpha = 0.06f),
    heldLens = 1f,
    heldMagnification = 0.4f,
    heldGlow = 0.6f,
)

/**
 * The loupe: the clear glass above on the held-lens profile, whose interior magnifies the names it
 * carries (heldMagnification 0.4 is 1 / 0.6 = 1.67x) and splits them into colour at the rim.
 */
internal val LoupeStyle: GlassStyle = LoupeStyleClear.copy(profile = GlassProfile.Held)

// ---------------------------------------------------------------------------------------------
// The atlas: generated once from a seed, in sky coordinates (0..1 on both axes).

internal class Star(val p: Offset, val r: Float, val color: Color)
internal class Figure(val name: String, val detail: String, val points: List<Offset>, val centre: Offset)
internal class Body(val name: String, val detail: String, val p: Offset, val r: Float, val color: Color, val orbit: Float)
internal class Nebula(val name: String, val detail: String, val p: Offset, val r: Float, val color: Color)

@androidx.compose.runtime.Immutable
internal class Atlas(
    val stars: List<Star>,
    val figures: List<Figure>,
    val bodies: List<Body>,
    val nebulae: List<Nebula>,
) {
    data class Named(val name: String, val detail: String)

    fun nearest(p: Offset, mode: Int, reach: Float): Named? {
        // Sky y runs over a taller extent than x; compare in x units.
        fun d(a: Offset) = (a - p).let { Offset(it.x, it.y * HEIGHT / WIDTH) }.getDistance()
        val hit = when (mode) {
            0 -> figures.minByOrNull { d(it.centre) }?.takeIf { d(it.centre) <= reach }?.let { Named(it.name, it.detail) }
            1 -> bodies.minByOrNull { d(it.p) }?.takeIf { d(it.p) <= reach }?.let { Named(it.name, it.detail) }
            else -> nebulae.minByOrNull { d(it.p) }?.takeIf { d(it.p) <= reach }?.let { Named(it.name, it.detail) }
        }
        return hit
    }

    companion object {
        const val WIDTH = 1200
        const val HEIGHT = 2000

        private val figureNames = listOf(
            "Orion" to "The Hunter · 7 bright stars", "Cassiopeia" to "The Queen · W-shaped",
            "Ursa Major" to "Great Bear · The Plough", "Cygnus" to "The Swan · Northern Cross",
            "Lyra" to "The Lyre · Vega", "Scorpius" to "The Scorpion · Antares",
            "Taurus" to "The Bull · Aldebaran", "Gemini" to "The Twins · Castor & Pollux",
            "Leo" to "The Lion · Regulus", "Aquila" to "The Eagle · Altair",
            "Perseus" to "The Hero · Algol", "Andromeda" to "The Princess · M31 nearby",
        )
        private val bodyNames = listOf(
            "Mercury" to "0.39 AU · 88 days", "Venus" to "0.72 AU · 225 days", "Earth" to "1.00 AU · 365 days",
            "Mars" to "1.52 AU · 687 days", "Jupiter" to "5.20 AU · 11.9 years", "Saturn" to "9.58 AU · 29.5 years",
            "Uranus" to "19.2 AU · 84 years", "Neptune" to "30.1 AU · 165 years",
        )
        private val nebulaNames = listOf(
            "Orion Nebula" to "M42 · 1,344 ly", "Crab Nebula" to "M1 · 6,500 ly", "Ring Nebula" to "M57 · 2,570 ly",
            "Andromeda Galaxy" to "M31 · 2.5 million ly", "Lagoon Nebula" to "M8 · 4,100 ly",
            "Eagle Nebula" to "M16 · 7,000 ly", "Whirlpool Galaxy" to "M51 · 31 million ly",
        )

        fun generate(seed: Int): Atlas {
            val rnd = Random(seed)
            val tints = listOf(Color(0xFFFFFFFF), Color(0xFFCFE0FF), Color(0xFFFFF1D6), Color(0xFFFFD2A6), Color(0xFFB9D3FF))
            val stars = List(1500) {
                // Denser along a diagonal Milky Way band.
                val t = rnd.nextFloat()
                val band = rnd.nextFloat() < 0.35f
                val x = if (band) t else rnd.nextFloat()
                val y = if (band) (0.15f + 0.7f * t + (rnd.nextFloat() - 0.5f) * 0.12f).coerceIn(0f, 1f) else rnd.nextFloat()
                val bright = rnd.nextFloat() < 0.03f
                Star(Offset(x, y), if (bright) 1.6f + rnd.nextFloat() * 1.2f else 0.4f + rnd.nextFloat() * 0.8f, tints[rnd.nextInt(tints.size)])
            }
            val figures = figureNames.mapIndexed { i, (name, detail) ->
                val c = Offset(0.15f + (i % 3) * 0.35f + (rnd.nextFloat() - 0.5f) * 0.08f, 0.08f + (i / 3) * 0.24f + rnd.nextFloat() * 0.06f)
                var p = c
                val pts = List(5 + rnd.nextInt(3)) {
                    p += Offset((rnd.nextFloat() - 0.5f) * 0.12f, (rnd.nextFloat() - 0.5f) * 0.07f)
                    p
                }
                Figure(name, detail, pts, Offset(pts.map { it.x }.average().toFloat(), pts.map { it.y }.average().toFloat()))
            }
            val sun = Offset(0.5f, 0.5f)
            val bodyColors = listOf(Color(0xFFB0A79A), Color(0xFFE8C77A), Color(0xFF4F8EF7), Color(0xFFD5603A),
                Color(0xFFD9A066), Color(0xFFE6CD8A), Color(0xFF8FD8E0), Color(0xFF4062D8))
            val bodies = bodyNames.mapIndexed { i, (name, detail) ->
                val orbit = 0.05f + i * 0.055f
                val a = rnd.nextFloat() * 2f * PI.toFloat()
                Body(name, detail, sun + Offset(orbit * cos(a), orbit * sin(a) * (WIDTH.toFloat() / HEIGHT)),
                    if (i in 4..5) 0.012f else 0.006f, bodyColors[i], orbit)
            }
            val nebulaColors = listOf(Color(0xFFFF5FA2), Color(0xFF6FD3FF), Color(0xFF9B7BFF), Color(0xFFFFB86B), Color(0xFF6BFFB8))
            val nebulae = nebulaNames.mapIndexed { i, (name, detail) ->
                Nebula(name, detail, Offset(0.12f + rnd.nextFloat() * 0.76f, 0.08f + i * 0.13f), 0.04f + rnd.nextFloat() * 0.05f,
                    nebulaColors[i % nebulaColors.size])
            }
            return Atlas(stars, figures, bodies, nebulae)
        }
    }
}

/**
 * Draws one mode of the atlas through [map] (sky -> pixels). With [labelled] the names are drawn
 * readable; the sky itself carries them at a size meant to be unreadable without the loupe.
 */
internal fun DrawScope.drawSky(
    atlas: Atlas,
    mode: Int,
    map: (Offset) -> Offset,
    zoom: Float,
    labelled: Boolean,
    measurer: androidx.compose.ui.text.TextMeasurer,
    density: Float,
    onlyLabels: Boolean = false,
) {
    val s = density * zoom
    if (!onlyLabels) {
        // The Milky Way: a faint diagonal glow under everything.
        val a = map(Offset(0f, 0.15f)); val b = map(Offset(1f, 0.85f))
        drawLine(Brush.linearGradient(listOf(Color(0x00334477), Color(0x40556AAA), Color(0x00334477)), a, b),
            a, b, strokeWidth = 220f * s)
        atlas.stars.forEach { drawCircle(it.color, it.r * s, map(it.p)) }
    }
    val tiny = TextStyle(color = Color(0x66AFC3FF), fontSize = (5 * zoom).sp)
    val big = TextStyle(color = Color(0xFFEAF2FF), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    val small = TextStyle(color = Color(0xCCEAF2FF), fontSize = 9.sp)
    fun label(name: String, detail: String, at: Offset) {
        if (labelled) {
            // Centred on the object, so the name sits in the middle of the loupe when it is over it.
            val n = measurer.measure(name, big)
            val d = measurer.measure(detail, small)
            drawText(n, topLeft = at - Offset(n.size.width / 2f, n.size.height.toFloat()))
            drawText(d, topLeft = at - Offset(d.size.width / 2f, -2f * density))
        } else {
            drawText(measurer, name, at + Offset(4f * s, -4f * s), tiny)
        }
    }
    when (mode) {
        0 -> atlas.figures.forEach { f ->
            if (!onlyLabels) {
                f.points.zipWithNext { p, q -> drawLine(Color(0x66B9CCFF), map(p), map(q), 0.8f * s) }
                f.points.forEach { drawCircle(Color.White, 1.8f * s, map(it)) }
            }
            label(f.name, f.detail, map(f.centre))
        }
        1 -> {
            val sun = map(Offset(0.5f, 0.5f))
            if (!onlyLabels) {
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF3C4), Color(0xFFFFB347), Color(0x00FF8C00)), sun, 40f * s), 40f * s, sun)
                atlas.bodies.forEach { b ->
                    val rx = b.orbit * Atlas.WIDTH * s
                    drawOval(Color(0x3398B4FF), sun - Offset(rx, rx), Size(rx * 2f, rx * 2f), style = Stroke(0.8f * s))
                    drawCircle(b.color, b.r * Atlas.WIDTH * s, map(b.p))
                }
            }
            atlas.bodies.forEach { label(it.name, it.detail, map(it.p)) }
        }
        else -> {
            if (!onlyLabels) atlas.nebulae.forEach { n ->
                val c = map(n.p); val r = n.r * Atlas.WIDTH * s
                drawCircle(Brush.radialGradient(listOf(n.color.copy(alpha = 0.55f), n.color.copy(alpha = 0.12f), Color.Transparent), c, r), r, c)
            }
            atlas.nebulae.forEach { label(it.name, it.detail, map(it.p)) }
        }
    }
}
