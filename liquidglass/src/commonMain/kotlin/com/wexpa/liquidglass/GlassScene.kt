package com.wexpa.liquidglass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Owns a single backdrop recording and its glass overlays. Place scrolling/photo/content
 * in [backdrop]; place controls in [content]. A glass overlay never records itself.
 * The caller controls the host's size through [modifier]. Existing explicit state APIs remain
 * available through [GlassSceneScope.state] for advanced container/tab-bar integrations.
 */
@Composable
fun GlassScene(
    background: Color,
    backdrop: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable GlassSceneScope.() -> Unit,
) {
    val state = rememberLiquidGlassState(background = background)
    Box(modifier) {
        Box(Modifier.matchParentSize().liquidGlassSource(state), content = backdrop)
        val scope = remember(state, this) { GlassSceneScope(state, this) }
        scope.content()
    }
}

/** A scene-bound modifier API; there is no global state or hidden cross-window recording. */
@Stable
class GlassSceneScope internal constructor(
    val state: LiquidGlassState,
    boxScope: BoxScope,
) : BoxScope by boxScope {
    /** Material only: add the normal Compose clickable/semantics/size modifiers as usual.
     * Touch feedback observes input without taking click or scroll ownership.
     * Set [interaction] to null for a passive panel. Foreground labels stay fixed. */
    fun Modifier.glass(
        shape: Shape = RoundedCornerShape(20.dp),
        style: GlassStyle = GlassStyle.Regular,
        interaction: GlassInteraction? = GlassInteraction.Calm,
        materialize: Float = 1f,
    ): Modifier = liquidGlass(state, shape, style, interaction = interaction, materialize = materialize)
}
