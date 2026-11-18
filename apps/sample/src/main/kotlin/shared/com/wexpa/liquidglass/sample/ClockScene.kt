package com.wexpa.liquidglass.sample

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wexpa.liquidglass.*

/**
 * Geometry of a stock four-tab bar in dark appearance over a bare black page (the Clock app, iPhone 13 mini
 * recording T70-third-bar, 1170 px wide, 3 px/pt): tab centres 225 / 465 / 705 / 942 px, so a slot of about
 * 79.7 pt, a bar of 347 x 62 pt on the Phone bar's row. Built to give the tap clock a third tab width (#70);
 * the resting pill width is the Phone bar's, not measured.
 */
object ClockGeometry {
    val barLeft = 21.dp
    val barWidth = 347.dp
    val itemInset = 14.17.dp
    val pillWidth = 95.dp

    fun style(base: GlassTabBarStyle): GlassTabBarStyle =
        base.copy(height = PhoneGeometry.barHeight, contentPadding = itemInset, pillWidth = pillWidth)
}

private val ClockLabels = listOf("World Clock", "Alarms", "Stopwatch", "Timers")

/** A four-tab Calm bar over a black page: no content under the bar, as in the recording. */
@Composable
fun ClockScene(
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    style: GlassTabBarStyle = ClockGeometry.style(GlassTabBarStyle.Calm()),
    accent: Color = Color(0xFFFF9F0A),
) {
    LiquidGlassTheme(dark = true, background = Color.Black) {
        Box(modifier.size(PhoneGeometry.width, PhoneGeometry.height).background(Color.Black)) {
            val barTop = PhoneGeometry.barCentreY - PhoneGeometry.barHeight / 2
            GlassTabBar(
                4, selected, onSelected,
                Modifier.offset(ClockGeometry.barLeft, barTop).width(ClockGeometry.barWidth).testTag("clock-bar"),
                style = style,
            ) { index, isSelected ->
                val color = if (isSelected) accent else Color.White
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Canvas(Modifier.size(26.dp)) {
                        drawCircle(color, size.minDimension * .42f, center, style = Stroke(size.minDimension * .09f))
                        drawLine(color, center, Offset(center.x, center.y - size.minDimension * .25f), size.minDimension * .08f, StrokeCap.Round)
                    }
                    Spacer(Modifier.height(3.dp))
                    BasicText(ClockLabels[index],
                        style = TextStyle(color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold))
                }
            }
        }
    }
}
