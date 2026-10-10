package com.wexpa.liquidglass

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.ceil

/** One resolved drawing transform, shared by a bar's material and both ink variants.
 * This never participates in measuring, placement, semantics or pointer coordinates. */
internal data class GlassMaterialDrawing(
    val pull: GlassPullDeformation = GlassPullDeformation.None,
    val pressX: Float = 1f,
    val pressY: Float = 1f,
    val centre: Offset = Offset.Zero,
    val footprint: ImageBitmap? = null,
)

@Stable
internal class GlassMaterialDrawingState {
    var drawing by mutableStateOf(GlassMaterialDrawing())
}

/** Independent native-resolution alpha input. A transparent border makes out-of-bounds
 * samples empty; no read from offscreen storage in another shader's dynamic input. */
@Composable
internal fun rememberGlassFootprint(shape: Shape, size: Size, density: Density,
                                   direction: LayoutDirection, enabled: Boolean): ImageBitmap? =
    remember(shape, size, density, direction, enabled) {
        if (!enabled || size.width <= 0f || size.height <= 0f) return@remember null
        ImageBitmap(ceil(size.width).toInt()+2,ceil(size.height).toInt()+2).also { bitmap ->
            val canvas=Canvas(bitmap)
            canvas.translate(1f,1f)
            canvas.drawOutline(shape.createOutline(size,direction,density),Paint().apply { color=Color.White })
        }
    }

/** Exact inverse of withGlassMaterialPull, with selector-local -> bar-local translation.
 * Returns two affine rows; bitmap coordinates remain native even at reduced material scale. */
internal fun GlassMaterialDrawing.footprintMap(originInBar: Offset): Pair<FloatArray,FloatArray> {
    val radians=pull.angleDegrees * (kotlin.math.PI.toFloat()/180f)
    val c=cos(radians);val s=sin(radians)
    val a=1f/pull.along.coerceAtLeast(.001f);val b=1f/pull.across.coerceAtLeast(.001f)
    val m00=(c*c*a+s*s*b)/pressX.coerceAtLeast(.001f)
    val m01=(c*s*(a-b))/pressX.coerceAtLeast(.001f)
    val m10=(c*s*(a-b))/pressY.coerceAtLeast(.001f)
    val m11=(s*s*a+c*c*b)/pressY.coerceAtLeast(.001f)
    val dx=originInBar.x-centre.x-pull.translationX
    val dy=originInBar.y-centre.y-pull.translationY
    return floatArrayOf(m00,m01,centre.x+m00*dx+m01*dy+1f,1f) to
        floatArrayOf(m10,m11,centre.y+m10*dx+m11*dy+1f,0f)
}

/** [origin] maps this drawing node into the bar's coordinate frame. Selected ink lives
 * in a moving selector node, but must use the same BAR pivot as ordinary ink. */
internal fun Modifier.glassMaterialForeground(
    state: GlassMaterialDrawingState?, origin: Offset = Offset.Zero,
): Modifier = if (state == null) this else drawWithContent {
    val frame = state.drawing
    withGlassMaterialPull(frame.pull, frame.pressX, frame.pressY,
        pivot = frame.centre - origin) { this@drawWithContent.drawContent() }
}
