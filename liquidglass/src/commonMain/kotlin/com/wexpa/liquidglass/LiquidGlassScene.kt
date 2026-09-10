package com.wexpa.liquidglass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape

/**
 * The glass every [Modifier.liquidGlass] in this subtree refracts, unless it is given one.
 *
 * Null outside a [LiquidGlassScene]. Read it with [currentLiquidGlassState], which fails with a
 * useful message rather than a null pointer.
 */
val LocalLiquidGlassState: ProvidableCompositionLocal<LiquidGlassState?> =
    compositionLocalOf { null }

/**
 * The state in scope, or an error explaining what is missing.
 */
@Composable
@ReadOnlyComposable
fun currentLiquidGlassState(): LiquidGlassState =
    LocalLiquidGlassState.current ?: error(
        "No LiquidGlassState in scope. Wrap this screen in LiquidGlassScene(background = …) { … }, " +
            "or pass a state explicitly to liquidGlass(state = …).",
    )

/**
 * Declares one piece of glass for a whole screen, so nothing below has to be handed a state.
 *
 * This is the setup the material needs, in one place:
 *
 * ```kotlin
 * LiquidGlassScene(background = MyTheme.ground) {
 *     Wallpaper(Modifier.liquidGlassSource())     // what gets refracted
 *
 *     Card(
 *         colors = CardDefaults.cardColors(containerColor = Color.Transparent),
 *         modifier = Modifier.liquidGlass(RoundedCornerShape(24.dp)),
 *     ) { … }                                     // anywhere below, no state threaded through
 * }
 * ```
 *
 * **Two rules, and they are the whole of the setup.**
 *
 * One thing has to be marked as the backdrop with [Modifier.liquidGlassSource]. That is what
 * gets recorded and read by every panel. Usually it is the wallpaper, the scrolling body, or
 * whatever sits at the bottom of the screen.
 *
 * Nothing wearing [Modifier.liquidGlass] may sit inside that backdrop's own subtree, or it would
 * sample itself. Every implementation of this material has the same constraint, because a layer
 * cannot read the layer it is part of. In practice this is easy: mark the background, and put
 * glass beside it rather than inside it.
 *
 * **One thing this cannot do for you.** A component that paints its own opaque fill will cover
 * the glass drawn behind it, so converting a Material component means making its container
 * transparent as well as adding the modifier. That is how Compose components paint, not a
 * limitation of the shader.
 *
 * [inversion], [frost] and [contrast] are environment-wide by nature — the app decides, and
 * every piece of glass in it agrees — which is why they live here rather than on each panel.
 */
@Composable
fun LiquidGlassScene(
    background: Color,
    modifier: Modifier = Modifier,
    inversion: Float = 0f,
    frost: Float = 0f,
    contrast: Float = 0f,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberLiquidGlassState(
        background = background,
        inversion = inversion,
        frost = frost,
        contrast = contrast,
    )
    CompositionLocalProvider(LocalLiquidGlassState provides state) {
        Box(modifier = modifier, content = content)
    }
}

/**
 * Provides an existing [state] to a subtree, for hosts that build the state themselves.
 *
 * [LiquidGlassScene] is the usual way in. This is for the case where the state already exists —
 * a second state recorded for glass that looks `through` other glass, say — and only needs to be
 * put in scope.
 */
@Composable
fun ProvideLiquidGlassState(
    state: LiquidGlassState,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalLiquidGlassState provides state, content = content)
}

/**
 * Marks this content as the backdrop, using the state in scope.
 *
 * The no-argument form of [Modifier.liquidGlassSource]. Requires a [LiquidGlassScene] above it.
 */
fun Modifier.liquidGlassSource(): Modifier = composed {
    liquidGlassSource(currentLiquidGlassState())
}

/**
 * Draws this element as glass over the backdrop in scope.
 *
 * The no-argument form of [Modifier.liquidGlass]: it takes the state from the nearest
 * [LiquidGlassScene] instead of being handed one, so a panel deep in a screen needs nothing
 * threaded down to it. Every other parameter behaves exactly as it does there.
 *
 * Remember that a component painting its own opaque fill will cover this. Converting a Material
 * component means making its container transparent as well as adding the modifier.
 */
fun Modifier.liquidGlass(
    shape: Shape = RectangleShape,
    style: GlassStyle = GlassStyle.Regular,
    light: GlassLight = GlassLight.Default,
    interaction: GlassInteraction? = null,
    materialize: Float = 1f,
    pressSource: GlassPressSource? = null,
    refractContent: Boolean = false,
    through: LiquidGlassState? = null,
): Modifier = composed {
    liquidGlass(
        state = currentLiquidGlassState(),
        shape = shape,
        style = style,
        light = light,
        interaction = interaction,
        materialize = materialize,
        pressSource = pressSource,
        refractContent = refractContent,
        through = through,
    )
}
