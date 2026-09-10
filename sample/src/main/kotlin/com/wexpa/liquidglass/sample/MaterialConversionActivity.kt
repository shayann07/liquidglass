package com.wexpa.liquidglass.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wexpa.liquidglass.GlassStyle
import com.wexpa.liquidglass.LiquidGlassScene
import com.wexpa.liquidglass.liquidGlass
import com.wexpa.liquidglass.liquidGlassSource

/**
 * Proof that stock Material 3 components convert, with no bespoke glass component involved.
 *
 *     adb shell am start -n com.wexpa.liquidglass.sample/.MaterialConversionActivity
 *
 * Every glass element here is an ordinary Material composable with two changes: its container
 * colour set to transparent, and `Modifier.liquidGlass(shape)` added. No state is threaded
 * anywhere; the scene provides it.
 */
class MaterialConversionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialConversion() }
    }
}

@Composable
private fun MaterialConversion() {
    LiquidGlassScene(
        background = Color(0xFF101427),
        modifier = Modifier.fillMaxSize(),
    ) {
        // The backdrop: the one thing that has to be marked.
        Backdrop(Modifier.fillMaxSize().liquidGlassSource())

        // Deliberately a scrolling column over a backdrop that does NOT scroll: the arrangement
        // a real app has, and the one the gallery's comment says cannot resolve its offset.
        // A LazyColumn, because it recycles its children and is what a real list actually is.
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(
                top = insets.calculateTopPadding() + 16.dp,
                bottom = insets.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            item {
                Text(
                    "Stock Material 3, converted",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            items(List(12) { it }) { index ->
            // An ordinary Material 3 Card. Transparent container, one modifier.
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .liquidGlass(RoundedCornerShape(28.dp), GlassStyle.Regular),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("Card $index", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "androidx.compose.material3.Card",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 13.sp,
                    )
                }
            }
            }

            item {
            // An ordinary Material 3 Button.
            Button(
                onClick = {},
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                elevation = null,
                shape = RoundedCornerShape(percent = 50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .liquidGlass(RoundedCornerShape(percent = 50), GlassStyle.Chrome),
            ) {
                Text("Button", color = Color.White, fontSize = 16.sp)
            }
            }
        }
    }
}

/** Something worth bending: bands of colour and fine text. */
@Composable
private fun Backdrop(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                listOf(
                    Color(0xFFB23A48), Color(0xFFD98324), Color(0xFF2E933C),
                    Color(0xFF2274A5), Color(0xFF5B2A86),
                ),
            ),
        ),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(30.dp)) {
            repeat(40) {
                Text(
                    "REFRACTION TEST 0123456789 - fine text shows displacement",
                    color = Color.White,
                    fontSize = 15.sp,
                )
            }
        }
    }
}
