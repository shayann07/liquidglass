package com.wexpa.liquidglass.sample

import android.graphics.Color as AndroidColor
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wexpa.liquidglass.GlassTabBar
import com.wexpa.liquidglass.GlassTabBarStyle
import com.wexpa.liquidglass.LiquidGlassHostView
import com.wexpa.liquidglass.glassHitTarget
import kotlin.math.roundToInt

/**
 * Demonstrates `<com.wexpa.liquidglass.LiquidGlassView>` and
 * `<com.wexpa.liquidglass.LiquidGlassHostView>` inside a traditional Android XML layout
 * (`res/layout/activity_xml_interop.xml`).
 */
class XmlInteropActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_xml_interop)

        val container = findViewById<LinearLayout>(R.id.xmlBackdropContainer)
        populateBackdropRows(container)

        val tabBarHost = findViewById<LiquidGlassHostView>(R.id.xmlTabBarHost)
        val tabs = listOf("Home", "Browse", "Library", "Settings")
        tabBarHost.setThemedGlassContent { host ->
            var selectedTab by remember { mutableIntStateOf(0) }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                GlassTabBar(
                    itemCount = tabs.size,
                    selectedIndex = selectedTab,
                    onSelected = { selectedTab = it },
                    style = GlassTabBarStyle.Calm(dark = true),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .glassHitTarget("xml_sample_tab_bar", host),
                ) { index, isSelected ->
                    val ink = if (isSelected) Color(0xFF0897FF) else Color(0xFFEAEAF0)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        BasicText(
                            text = tabs[index],
                            style = TextStyle(
                                color = ink,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun populateBackdropRows(container: LinearLayout) {
        val density = resources.displayMetrics.density
        val palette = intArrayOf(
            0xFF1E3A8A.toInt(),
            0xFF0F766E.toInt(),
            0xFFB45309.toInt(),
            0xFF9D174D.toInt(),
            0xFF4C1D95.toInt(),
        )
        for (i in 0 until 24) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val padH = (18f * density).roundToInt()
                val padV = (14f * density).roundToInt()
                setPadding(padH, padV, padH, padV)
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(palette[i % palette.size], palette[(i + 1) % palette.size]),
                ).apply {
                    cornerRadius = 18f * density
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    bottomMargin = (12f * density).roundToInt()
                }
            }
            val title = TextView(this).apply {
                text = "Android View Row #${i + 1}"
                setTextColor(AndroidColor.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            }
            val body = TextView(this).apply {
                text = "0123456789 native Android View text refracted through XML LiquidGlassView and LiquidGlassHostView."
                setTextColor(0xD9FFFFFF.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            }
            card.addView(title)
            card.addView(body)
            container.addView(card)
        }
    }
}
