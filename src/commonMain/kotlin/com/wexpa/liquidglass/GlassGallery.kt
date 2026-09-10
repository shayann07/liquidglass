package com.wexpa.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A tuning surface for the material.
 *
 * The material can only be judged against content worth refracting. Over a near-black app the
 * lensing has nothing to bend and every style looks the same; over hard edges and saturated
 * colour the differences between the styles are obvious, and so are the mistakes. This exists
 * to be screenshotted and compared, and doubles as the library's sample.
 */
@Composable
fun GlassGallery(modifier: Modifier = Modifier) {
    val glass = rememberLiquidGlassState(background = Color(0xFF1B2A6B))

    // The backdrop lives *inside* the scroll, with the panels, so the two share one frame.
    //
    // The other way round does not work, and the reason is worth knowing: Compose applies a
    // scroll offset when it draws rather than when it lays out, so a panel inside a scroll
    // cannot discover its offset from a backdrop outside it — every layout API reports its
    // unscrolled position. Chrome floating over a scrolling list is the arrangement the
    // material is actually for, and it works because the glass is outside the scroll; this
    // gallery is the other supported case, where everything is inside it together.
    Box(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            GalleryBackdrop(Modifier.matchParentSize().liquidGlassSource(glass))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Spacer(Modifier.height(24.dp))

                GlassPanel(glass, "Clear · capsule", GlassStyle.Clear, CircleShape, 64.dp)
                GlassPanel(glass, "Regular · card", GlassStyle.Regular, RoundedCornerShape(28.dp), 96.dp)
                GlassPanel(glass, "Thick · sheet", GlassStyle.Thick, RoundedCornerShape(32.dp), 150.dp)

                // Apple's actual corner geometry, with the material's field matching the clip.
                GlassPanel(
                    glass,
                    "Squircle · n=4",
                    GlassStyle.Regular,
                    GlassSquircleShape(cornerRadius = 34.dp, power = 4f),
                    110.dp,
                )

                // No closed form: the host measures this one and the shader reads the measurement.
                GlassPanel(glass, "Path · star", GlassStyle.Regular, StarShape, 190.dp)

                Label("Press it - scale, glow, magnifier", size = 13)
                GlassPanel(
                    glass,
                    "Interactive",
                    GlassStyle.Regular,
                    RoundedCornerShape(30.dp),
                    92.dp,
                    interaction = GlassInteraction.Default,
                )

                Spacer(Modifier.height(4.dp))
                Label("Slider - the knob is glass only while you drag it", size = 13)
                var sliderValue by remember { mutableStateOf(0.38f) }
                GlassSlider(
                    state = glass,
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(4.dp))
                Label("Container - members fusing", size = 13)
                LiquidGlassContainer(
                    state = glass,
                    modifier = Modifier.fillMaxWidth().height(110.dp),
                    style = GlassStyle.Regular,
                    mergeDistance = 40.dp,
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(Modifier.size(96.dp).glassMember(CircleShape))
                        Box(Modifier.size(96.dp).glassMember(CircleShape))
                        Box(Modifier.width(120.dp).height(84.dp).glassMember(RoundedCornerShape(28.dp)))
                    }
                }

                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

/**
 * Frost and contrast are properties of the environment rather than of one panel — the app
 * decides and every piece of glass in it agrees — so showing both at once means two states over
 * two backdrops rather than two styles over one.
 */
@Composable
private fun AccessibilityPanel(
    label: String,
    frost: Float,
    contrast: Float,
    modifier: Modifier = Modifier,
) {
    val state = rememberLiquidGlassState(
        background = Color(0xFF1B2A6B),
        frost = frost,
        contrast = contrast,
    )
    Box(modifier = modifier.height(84.dp)) {
        GalleryBackdrop(Modifier.fillMaxSize().liquidGlassSource(state))
        Box(
            modifier = Modifier
                .fillMaxSize()
                .liquidGlass(state, RoundedCornerShape(24.dp), GlassStyle.Regular),
            contentAlignment = Alignment.Center,
        ) {
            Label(label, size = 14)
        }
    }
}

@Composable
private fun GlassPanel(
    state: LiquidGlassState,
    label: String,
    style: GlassStyle,
    shape: Shape,
    height: Dp,
    interaction: GlassInteraction? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .liquidGlass(state, shape, style, interaction = interaction),
        contentAlignment = Alignment.Center,
    ) {
        Label(label)
    }
}

@Composable
private fun Label(text: String, size: Int = 15) {
    BasicText(
        text = text,
        style = TextStyle(color = Color.White, fontSize = size.sp, fontWeight = FontWeight.Medium),
    )
}

/** A five-pointed star: no closed-form distance field, so it exercises the measured path. */
private object StarShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outer = minOf(cx, cy) * 0.96f
        val inner = outer * 0.44f
        val path = Path()
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outer else inner
            val a = (-PI / 2f + i * PI / 5f).toFloat()
            val x = cx + r * cos(a)
            val y = cy + r * sin(a)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

/**
 * Saturated colour, hard edges and fine text — the three things that make refraction visible.
 * A smooth gradient alone hides almost every flaw in a lensing shader.
 */
@Composable
private fun GalleryBackdrop(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFF1B2A6B), Color(0xFFB3245F), Color(0xFFE86A2C),
                    Color(0xFFF2C744), Color(0xFF128A6B), Color(0xFF2A1B6B),
                ),
                start = Offset.Zero,
                end = Offset(1400f, 2600f),
            )
        )
    ) {
        // Hard stripes: any displacement at the rim shows up as a visible kink in a straight line.
        Column(Modifier.fillMaxSize()) {
            repeat(48) { i ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(if (i % 2 == 0) 46.dp else 40.dp)
                        .background(if (i % 2 == 0) Color(0x33000000) else Color(0x22FFFFFF))
                )
            }
        }
        Row(Modifier.fillMaxSize()) {
            repeat(9) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(if (i % 2 == 0) Color(0x1AFFFFFF) else Color.Transparent)
                )
            }
        }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            repeat(48) {
                BasicText(
                    text = "REFRACTION TEST 0123456789 — fine text shows displacement",
                    style = TextStyle(color = Color.White, fontSize = 12.sp),
                    maxLines = 1,
                )
            }
        }
    }
}
