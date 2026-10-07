package com.wexpa.liquidglass

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset

/** One resolved drawing transform, shared by a bar's material and both ink variants.
 * This never participates in measuring, placement, semantics or pointer coordinates. */
internal data class GlassMaterialDrawing(
    val pull: GlassPullDeformation = GlassPullDeformation.None,
    val pressX: Float = 1f,
    val pressY: Float = 1f,
    val centre: Offset = Offset.Zero,
)

@Stable
internal class GlassMaterialDrawingState {
    var drawing by mutableStateOf(GlassMaterialDrawing())
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
