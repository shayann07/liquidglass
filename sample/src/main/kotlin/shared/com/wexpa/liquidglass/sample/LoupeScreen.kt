package com.wexpa.liquidglass.sample

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.*
import com.wexpa.liquidglass.*
import kotlin.math.roundToInt
import kotlin.random.Random

private val Ground = Color(0xFF09111E)
private val Ink = Color(0xFFEAF0F7)
private val Muted = Color(0xFF9BAABD)
private val Accent = Color(0xFFBBDAD8)
private val Panel = RoundedCornerShape(24.dp)
private val Fields = listOf("The northern sky", "A study in light", "Between the stars", "The quiet edge")
private val Scenes = listOf("Sky", "Type", "Lines")

/** Normalized canvas coordinates: independent of sidebars, DPI and window resizing. */
internal fun boundedLens(position: Offset, size: IntSize, radius: Float, margin: Float): Offset {
    fun axis(value: Float, extent: Int): Float {
        val inset = (radius + margin).coerceAtMost(extent / 2f)
        return value.coerceIn(inset, (extent - inset).coerceAtLeast(inset))
    }
    return Offset(axis(position.x, size.width), axis(position.y, size.height))
}

/** Shared Android/desktop demo. The sky is illustrative, not an astronomical instrument. */
@Composable
fun LoupeScreen(reduceMotion: Boolean = false, modifier: Modifier = Modifier,
                loupeStyle: GlassStyle = GlassStyle.clearLens(), initialScene: Int = 0) {
    var scene by remember { mutableIntStateOf(initialScene.coerceIn(0, 2)) }
    var field by remember { mutableIntStateOf(0) }
    var motion by remember { mutableStateOf(!reduceMotion) }
    var magnification by remember { mutableFloatStateOf(1.25f) }
    var lensVisible by remember { mutableStateOf(true) }
    // Honor later host accessibility changes; the in-app toggle can always reduce further.
    val interaction = if (motion && !reduceMotion) GlassInteraction.Calm else GlassInteraction.ReducedMotion
    Column(modifier.fillMaxSize().background(Ground).statusBarsPadding().navigationBarsPadding().padding(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) {
                Label("A", 22, Ground, FontWeight.Medium)
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Label("ATLAS STUDIO", 15, weight = FontWeight.SemiBold)
                Label("An exploration of light & glass", 11, Muted)
            }
            Action(if (motion && !reduceMotion) "Motion on" else "Motion reduced", "motion", selected = !motion,
                onClick = { motion = !motion })
        }
        BoxWithConstraints(Modifier.weight(1f)) {
            val wide = maxWidth >= 1000.dp
            val spacious = maxWidth >= 600.dp
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                if (wide) Catalog(field, { field = it }, Modifier.width(216.dp).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Label(Fields[field], if (wide) 28 else 20, weight = FontWeight.Medium)
                            Label("COLLECTION 0${field + 1}  /  ILLUSTRATIVE FIELD", 10, Muted)
                        }
                        if (spacious) Label("Drag the lens. Notice the details.", 12, Muted)
                    }
                    AtlasCanvas(scene, { scene = it }, field, magnification, lensVisible, interaction,
                        loupeStyle.copy(heldMagnification = 1f - 1f / magnification), Modifier.weight(1f).fillMaxWidth())
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Scenes.forEachIndexed { index, name ->
                            Action(name, "scene-$index", scene == index, { scene = index })
                        }
                        Spacer(Modifier.weight(1f))
                        Action("−", "zoom-out", onClick = { magnification = (magnification - .1f).coerceAtLeast(1f) })
                        Label("${(magnification * 100).roundToInt()}%", 12, Accent)
                        Action("+", "zoom-in", onClick = { magnification = (magnification + .1f).coerceAtMost(1.6f) })
                        if (spacious) Action(if (lensVisible) "Hide lens" else "Show lens", "lens-toggle",
                            onClick = { lensVisible = !lensVisible })
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Label("LIQUIDGLASS  /  COMPOSE", 10, Muted)
            Label("Live backdrop · Calm response", 10, Muted)
        }
    }
}

@Composable private fun Catalog(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
    Column(modifier) {
        Label("FIELD NOTES", 10, Muted)
        Label("A closer look.", 24, weight = FontWeight.Medium)
        Label("Four compositions.\nOne continuous lens.", 13, Muted, modifier = Modifier.padding(top = 12.dp, bottom = 26.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(Fields) { index, name ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(if (selected == index) Color(0xFF1C2C3C) else Color(0xFF111D2B))
                    .clickable(role = Role.Tab) { onSelect(index) }.semantics { this.selected = selected == index }
                    .padding(16.dp)) {
                    Label("0${index + 1}", 11, Accent)
                    Label(name, 14, modifier = Modifier.padding(top = 18.dp))
                    Label(if (selected == index) "Exploring now  ↗" else "Open composition", 11, Muted,
                        modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        Label("Made to be looked through", 13, Accent)
        Label("Move across stars, fine type and\nlines. The same scene powers\nevery piece of glass.", 12, Muted,
            modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable private fun AtlasCanvas(scene: Int, onScene: (Int) -> Unit, field: Int, magnification: Float, lensVisible: Boolean,
                                    interaction: GlassInteraction, style: GlassStyle, modifier: Modifier) {
    val density = LocalDensity.current
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var normalized by remember(field) { mutableStateOf(Offset(.52f, .45f)) }
    val diameter = with(density) { minOf(208.dp.toPx(), (minOf(viewport.width, viewport.height) * .48f).coerceAtLeast(0f)) }
    val margin = with(density) { 10.dp.toPx() }
    val centre = boundedLens(Offset(normalized.x * viewport.width, normalized.y * viewport.height), viewport, diameter / 2, margin)
    val press = rememberGlassPressSource()
    GlassScene(background = Ground, modifier = modifier.clip(Panel).testTag("atlas-canvas").onSizeChanged { viewport = it },
        backdrop = { AtlasBackdrop(scene, field) }) {
        Column(Modifier.align(Alignment.TopStart).padding(28.dp)) {
            Label("OBSERVATION  /  0${field + 1}", 10, Accent)
            Label(if (scene == 0) "Find your perspective." else if (scene == 1) "Clarity, up close." else "Follow the curve.",
                26, modifier = Modifier.padding(top = 8.dp))
        }
        if (lensVisible && diameter > 0f) {
            Box(Modifier.offset { IntOffset((centre.x - diameter / 2).roundToInt(), (centre.y - diameter / 2).roundToInt()) }
                .size(with(density) { diameter.toDp() }).testTag("atlas-lens")
                .semantics { contentDescription = "Magnifier, ${(magnification * 100).roundToInt()} percent" }
                .liquidGlass(state, CircleShape, style, interaction = interaction, pressSource = press)
                .pointerInput(viewport, diameter, field) {
                    detectDragGestures(onDragStart = { press.press(it) }, onDragEnd = press::release,
                        onDragCancel = press::release) { change, amount ->
                        change.consume()
                        // Read state on each event; no remembered screen-space sidebar offsets.
                        val current = Offset(normalized.x * viewport.width, normalized.y * viewport.height)
                        val next = boundedLens(current + amount, viewport, diameter / 2, margin)
                        normalized = Offset(next.x / viewport.width.coerceAtLeast(1), next.y / viewport.height.coerceAtLeast(1))
                        press.press(Offset(diameter / 2, diameter / 2))
                    }
                })
        }
        Row(Modifier.align(Alignment.BottomStart).padding(24.dp).widthIn(max = 380.dp)
            .glass(shape = RoundedCornerShape(20.dp), style = GlassStyle.DarkChrome, interaction = interaction)
            .testTag("glass-note").padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Label("Light, held gently.", 18, weight = FontWeight.Medium)
                Label("Hold and pull this card. Its surface responds;\nthe words stay exactly where they are.", 12, Ink.copy(alpha = .8f),
                    modifier = Modifier.padding(top = 6.dp))
            }
        }
        if (with(density) { viewport.width.toDp() } >= 640.dp) {
            GlassTabBar(state, Scenes.size, scene, onScene,
                modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp).width(280.dp),
                style = GlassTabBarStyle.Calm(), motionEnabled = interaction != GlassInteraction.ReducedMotion) { index, selected ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Label(listOf("✦", "Aa", "▦")[index], 18, if (selected) Accent else Ink)
                    Label(Scenes[index], 10, if (selected) Accent else Muted)
                }
            }
        }
    }
}

@Composable private fun AtlasBackdrop(scene: Int, field: Int) {
    val measurer = rememberTextMeasurer()
    val stars = remember(field) { val random = Random(271 + field); List(650) { Triple(random.nextFloat(), random.nextFloat(), random.nextFloat()) } }
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Ground)
        val halo = listOf(Color(0xFF24496A), Color(0xFF514766), Color(0xFF235858), Color(0xFF5C4439))[field]
        drawRect(Brush.radialGradient(listOf(halo, Ground), Offset(size.width * .62f, size.height * .42f), size.width * .68f))
        if (scene == 0) {
            stars.forEach { (x, y, z) ->
                val point = Offset(x * size.width, y * size.height)
                if (z > .97f) drawCircle(Brush.radialGradient(listOf(Accent.copy(alpha = .3f), Color.Transparent), point, 12.dp.toPx()), 12.dp.toPx(), point)
                drawCircle(Ink.copy(alpha = .2f + z * .7f), (.3f + z * .9f).dp.toPx(), point)
            }
            val points = listOf(Offset(.31f, .32f), Offset(.46f, .42f), Offset(.56f, .35f), Offset(.62f, .56f), Offset(.74f, .51f))
                .map { Offset(it.x * size.width, it.y * size.height) }
            points.zipWithNext { a, b -> drawLine(Accent.copy(alpha = .38f), a, b, 1.dp.toPx()) }
            points.forEachIndexed { index, point ->
                drawCircle(Ink, 2.dp.toPx(), point)
                drawCircle(Accent.copy(alpha = .25f), 6.dp.toPx(), point, style = Stroke(1.dp.toPx()))
                drawText(measurer, listOf("NORTH", "ALTAIR", "VEGA", "DENEB", "EAST")[index], point + Offset(12.dp.toPx(), 5.dp.toPx()),
                    TextStyle(color = Accent, fontSize = 10.sp, letterSpacing = 1.sp))
            }
        } else if (scene == 1) {
            for (i in 0..6) drawText(measurer, if (i % 2 == 0) "A little light changes everything." else "Look closer. There is more to see.",
                Offset(38.dp.toPx(), size.height * .25f + i * 45.dp.toPx()),
                TextStyle(color = Ink.copy(alpha = 1f - i * .10f), fontSize = (26 - i * 2).sp))
        } else {
            val gap = 16.dp.toPx()
            for (x in 0..(size.width / gap).toInt()) drawLine(Accent.copy(alpha = .6f), Offset(x * gap, 0f), Offset(x * gap, size.height), 1.dp.toPx())
            for (y in 0..(size.height / gap).toInt()) drawLine(Ink.copy(alpha = .25f), Offset(0f, y * gap), Offset(size.width, y * gap), 1.dp.toPx())
        }
    }
}

@Composable private fun Action(text: String, tag: String, selected: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.testTag(tag).heightIn(min = 48.dp).widthIn(min = 48.dp).clip(RoundedCornerShape(14.dp))
        .background(if (selected) Color(0xFF253C4B) else Color.Transparent)
        .clickable(role = Role.Button, onClick = onClick).semantics {
            this.selected = selected
            if (tag == "zoom-in") contentDescription = "Increase magnification"
            if (tag == "zoom-out") contentDescription = "Decrease magnification"
        }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
        Label(text, 13, if (selected) Accent else Ink)
    }
}

@Composable private fun Label(text: String, size: Int, color: Color = Ink, weight: FontWeight = FontWeight.Normal,
                              modifier: Modifier = Modifier) {
    BasicText(text, modifier, style = TextStyle(color = color, fontSize = size.sp, fontWeight = weight, lineHeight = (size * 1.5f).sp))
}
