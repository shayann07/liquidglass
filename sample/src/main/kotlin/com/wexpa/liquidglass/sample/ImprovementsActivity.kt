package com.wexpa.liquidglass.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wexpa.liquidglass.GlassStyle
import com.wexpa.liquidglass.LiquidGlassScene
import com.wexpa.liquidglass.liquidGlass
import com.wexpa.liquidglass.liquidGlassSource

/**
 * The three improvements, each shown off and on against the same backdrop.
 *
 *     adb shell am start -n com.wexpa.liquidglass.sample/.ImprovementsActivity
 */
class ImprovementsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Improvements() }
    }
}

@Composable
private fun Improvements() {
    LiquidGlassScene(background = Color(0xFF0B0E1A), modifier = Modifier.fillMaxSize()) {
        HardEdgedBackdrop(Modifier.fillMaxSize().liquidGlassSource())

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Head("Three improvements")

            Label("Rim softening  ·  left OFF, right ON")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Panel(
                    Modifier.weight(1f).height(120.dp).liquidGlass(
                        RoundedCornerShape(percent = 50),
                        GlassStyle.Clear.copy(rimSoftness = 0.dp),
                    ),
                )
                Panel(
                    Modifier.weight(1f).height(120.dp).liquidGlass(
                        RoundedCornerShape(percent = 50),
                        GlassStyle.Clear.copy(rimSoftness = 7.dp),
                    ),
                )
            }

            Label("Tint  ·  left BLEND, right MEDIUM")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Panel(
                    Modifier.weight(1f).height(120.dp).liquidGlass(
                        RoundedCornerShape(26.dp),
                        GlassStyle.Regular.copy(
                            tint = Color(0xFF2E7BFF).copy(alpha = 0.55f),
                            tintAbsorption = 0f,
                        ),
                    ),
                )
                Panel(
                    Modifier.weight(1f).height(120.dp).liquidGlass(
                        RoundedCornerShape(26.dp),
                        GlassStyle.Regular.copy(
                            tint = Color(0xFF2E7BFF).copy(alpha = 0.55f),
                            tintAbsorption = 1f,
                        ),
                    ),
                )
            }

            Label("Dome field  ·  a wide pill whose band reaches its own centre")
            Panel(
                Modifier.fillMaxWidth().height(86.dp).liquidGlass(
                    RoundedCornerShape(percent = 50),
                    // Band equal to the inradius: without the dome the middle stops bending.
                    GlassStyle.Clear.copy(refractionBand = 43.dp, refractionDepth = 20.dp),
                ),
            )

            Label("Stock Material 3 Text, converted with the same modifier")
            Text(
                "androidx.compose.material3.Text",
                color = Color.White,
                fontSize = 15.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(66.dp)
                    .liquidGlass(RoundedCornerShape(20.dp), GlassStyle.Chrome)
                    .padding(20.dp),
            )
        }
    }
}

@Composable
private fun Panel(modifier: Modifier) = Box(modifier)

@Composable
private fun Head(text: String) = Text(
    text,
    color = Color.White,
    fontSize = 19.sp,
    fontWeight = FontWeight.SemiBold,
    modifier = Modifier.padding(top = 44.dp, bottom = 2.dp),
)

@Composable
private fun Label(text: String) = Text(
    text,
    color = Color.White,
    fontSize = 12.sp,
    fontWeight = FontWeight.Medium,
    modifier = Modifier.padding(top = 6.dp),
)

/**
 * Hard horizontal edges and fine text: the two things that show rim compression and whether a
 * tint has flattened the ground it sits over.
 */
@Composable
private fun HardEdgedBackdrop(modifier: Modifier = Modifier) {
    val bands = listOf(
        Color(0xFFF2F2F2), Color(0xFF101010), Color(0xFFE8B23A), Color(0xFF101010),
        Color(0xFF2FA36B), Color(0xFFF2F2F2), Color(0xFF2E5BBA), Color(0xFF101010),
        Color(0xFFD4483B), Color(0xFFF2F2F2), Color(0xFF6B3FA0), Color(0xFF101010),
        Color(0xFF2FA36B), Color(0xFFF2F2F2), Color(0xFFE8B23A), Color(0xFF101010),
    )
    Column(modifier) {
        bands.forEach { colour ->
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f).background(colour),
            ) {
                Text(
                    "0123456789 fine text 0123456789 fine text",
                    color = if (colour.luminance() > 0.4f) Color(0xFF101010) else Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 6.dp, top = 4.dp),
                )
            }
        }
    }
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
