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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A tuning surface for the material.
 *
 * The material can only be judged against content worth refracting. Over a near-black app the
 * lensing has nothing to bend and every style looks the same; over hard edges and saturated
 * colour the differences between regular, clear and thick are obvious, and so are the
 * mistakes. This exists to be screenshotted and compared, and doubles as the library's sample.
 */
@Composable
fun GlassGallery(modifier: Modifier = Modifier) {
    val glass = rememberLiquidGlassState(background = Color(0xFF1B2A6B))

    Box(modifier = modifier.fillMaxSize()) {
        GalleryBackdrop(Modifier.fillMaxSize().liquidGlassSource(glass))

        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Spacer(Modifier.height(24.dp))

            // Small chrome: clear, and allowed to invert against a bright backdrop.
            GlassPanel(glass, "Clear · capsule", GlassStyle.Clear, CircleShape, 64.dp)

            // The workhorse.
            GlassPanel(glass, "Regular · card", GlassStyle.Regular, RoundedCornerShape(28.dp), 96.dp)

            // A large surface: adapts, never flips.
            GlassPanel(glass, "Thick · sheet", GlassStyle.Thick, RoundedCornerShape(32.dp), 150.dp)

            Spacer(Modifier.height(4.dp))
            Label("Container · two members fusing", size = 13)
            // Members overlap within `spacing`, so their fields fuse into one outline.
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
                    Box(
                        Modifier
                            .width(120.dp)
                            .height(84.dp)
                            .glassMember(RoundedCornerShape(28.dp))
                    )
                }
            }
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
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .liquidGlass(state, shape, style),
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
            repeat(26) { i ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(if (i % 2 == 0) 46.dp else 40.dp)
                        .background(if (i % 2 == 0) Color(0x33000000) else Color(0x22FFFFFF))
                )
            }
        }
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
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
            repeat(20) {
                BasicText(
                    text = "REFRACTION TEST 0123456789 — fine text shows displacement",
                    style = TextStyle(color = Color.White, fontSize = 12.sp),
                    maxLines = 1,
                )
            }
        }
    }
}
