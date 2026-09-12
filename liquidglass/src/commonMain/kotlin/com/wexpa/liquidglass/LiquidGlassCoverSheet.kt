package com.wexpa.liquidglass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A cover sheet as iOS 27 draws one: an opaque surface pulled down over the app, whose bottom
 * edge is a lens over *its own* content, while the app beneath is dimmed by how far the sheet
 * has come.
 *
 * Measured (`docs/research/measured-model.md`, section 5):
 *
 * - The sheet shows its own content sharp and undimmed; nothing of the app shows through it.
 * - What the sheet covers is dimmed by `1 - 0.311 (1 - e^(-pull / 150 dp))`, saturating at 0.706
 *   after about 230 dp of pull, and is not blurred.
 * - The edge lens is the fold of [GlassProfile.Measured] over a band of 0.6 of the corner radius.
 *   It forms only while a finger tracks the sheet past about 117 dp, and a released sheet that
 *   animates home shows none of it, so [tracked] decides whether [pull] drives the lens or the
 *   lens fades out.
 *
 * The sheet is laid out at the full size of this composable and offset up by its own height
 * minus [pull], so at `pull = 0` it is entirely above the top edge and at `pull = height` it
 * covers the screen. [content] is the sheet's own surface — a wallpaper, a list — and is what
 * the edge refracts; it is recorded as a backdrop of its own, so the lens sees the sheet and not
 * the app. Put the app beneath this composable in the same [LiquidGlassScene] or outside it;
 * the sheet neither samples nor needs the app's backdrop.
 *
 * Drive [pull] from a drag; [GlassMaterial.coverSheetDim] and [GlassMaterial.lensFormation] are
 * the two curves applied here, exposed for hosts that draw their own sheet.
 */
@Composable
fun LiquidGlassCoverSheet(
    /** How far the sheet has been pulled down, from 0 (hidden) to the sheet's height. */
    pull: Dp,
    /** True while a finger is tracking the sheet; false once it is released or animating. */
    tracked: Boolean,
    modifier: Modifier = Modifier,
    /** The sheet's outline; the measured sheet's bottom corners are the display's own, 47 dp. */
    shape: Shape = RoundedCornerShape(bottomStart = 47.dp, bottomEnd = 47.dp),
    /** The ground the sheet's own backdrop is painted on where its content is transparent. */
    background: Color = Color.Black,
    style: GlassStyle = GlassStyle.coverSheet(),
    light: GlassLight = GlassLight.Default,
    content: @Composable BoxScope.() -> Unit,
) {
    val dim = GlassMaterial.coverSheetDim(pull)
    val formationTarget = if (tracked) GlassMaterial.lensFormation(pull) else 0f
    // The fold forms with the finger and relaxes on release; the relaxation is not measured, so
    // it is a short ease rather than a fitted curve.
    val formation by animateFloatAsState(
        targetValue = formationTarget,
        animationSpec = if (tracked) tween(0) else tween(220),
        label = "cover_sheet_lens",
    )
    val sheetState = rememberLiquidGlassState(background = background)
    Box(
        modifier = modifier
            .fillMaxSize()
            // The dim over what the sheet covers. Drawn under the sheet across the whole area,
            // which is the same thing as drawing it below the edge, because the sheet is opaque.
            .drawWithContent {
                drawRect(Color.Black.copy(alpha = (1f - dim).coerceIn(0f, 1f)))
                drawContent()
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        placeable.placeRelative(0, (pull.toPx() - placeable.height).roundToInt())
                    }
                },
        ) {
            // The sheet's own surface is the backdrop the edge refracts.
            Box(Modifier.fillMaxSize().liquidGlassSource(sheetState), content = content)
            Box(
                Modifier
                    .fillMaxSize()
                    .liquidGlass(
                        state = sheetState,
                        shape = shape,
                        style = style,
                        light = light,
                        lensFormation = formation,
                    ),
            )
        }
    }
}
