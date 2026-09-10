package com.wexpa.liquidglass.sample

import android.app.Activity
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wexpa.liquidglass.GlassInteraction
import com.wexpa.liquidglass.GlassStyle
import com.wexpa.liquidglass.LiquidGlassScene
import com.wexpa.liquidglass.liquidGlass
import com.wexpa.liquidglass.liquidGlassSource
import kotlin.reflect.KClass

/**
 * The sample's front door: three screens, each reached through a panel that is itself the
 * material. Nothing here is a bespoke glass component. Every panel is a `Column` with
 * `Modifier.liquidGlass()` on it, and the state comes from the scene.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Light icons over a dark ground regardless of the device's own setting.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            Home(open = { screen -> startActivity(Intent(this, screen.java)) })
        }
    }
}

private val Ground = Color(0xFF0B0E1A)

@Composable
private fun Home(open: (KClass<out Activity>) -> Unit) {
    LiquidGlassScene(background = Ground, modifier = Modifier.fillMaxSize()) {
        // The one thing an app has to mark: what the glass looks at.
        Backdrop(Modifier.fillMaxSize().liquidGlassSource())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Text over a busy backdrop is exactly what the thick preset is for.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(shape = RoundedCornerShape(26.dp), style = GlassStyle.Thick)
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BasicText(
                    "liquidglass",
                    style = TextStyle(color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.SemiBold),
                )
                BasicText(
                    "A refracting glass material for Compose. Everything on these screens is an " +
                        "ordinary composable with Modifier.liquidGlass() added.",
                    style = TextStyle(color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp),
                )
            }
            Spacer(Modifier.weight(1f))

            Door(
                title = "Gallery",
                detail = "Every preset, a squircle, an arbitrary path, a fusing container and " +
                    "the accessibility modes, over content worth refracting.",
                style = GlassStyle.Regular,
            ) { open(GalleryActivity::class) }
            Door(
                title = "Material 3, converted",
                detail = "Stock Card and Button. Transparent container, one modifier, no " +
                    "bespoke component.",
                style = GlassStyle.Chrome,
            ) { open(MaterialConversionActivity::class) }
            Door(
                title = "Optics",
                detail = "Rim softening, tint as an absorbing medium and the dome field, each " +
                    "shown off and on against the same backdrop.",
                style = GlassStyle.Clear,
            ) { open(ImprovementsActivity::class) }
        }
    }
}

/** A tappable panel. The press response is the library's, not a ripple. */
@Composable
private fun Door(title: String, detail: String, style: GlassStyle, onClick: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(shape = shape, style = style, interaction = GlassInteraction.Default)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BasicText(
            title,
            style = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium),
        )
        BasicText(
            detail,
            style = TextStyle(color = Color.White.copy(alpha = 0.78f), fontSize = 13.sp),
        )
    }
}

/** Saturated colour, hard edges and fine text: the things a lens makes visible. */
@Composable
private fun Backdrop(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                listOf(
                    Color(0xFF1B2A6B), Color(0xFF2E7BFF), Color(0xFF2FA36B),
                    Color(0xFFE8B23A), Color(0xFFD4483B), Color(0xFF6B3FA0),
                ),
            ),
        ),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(26.dp)) {
            repeat(48) { row ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(if (row % 2 == 0) Color.White else Color(0xFF101010)),
                )
                BasicText(
                    "0123456789 fine text under glass 0123456789",
                    style = TextStyle(color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
