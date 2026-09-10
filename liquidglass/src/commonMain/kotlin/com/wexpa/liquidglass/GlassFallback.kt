package com.wexpa.liquidglass

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * The material without a backdrop.
 *
 * Android cannot sample what is behind a view before API 31, and cannot run the shader before
 * API 33. Rather than leave those devices with a flat rectangle, this paints the parts of the
 * material that do not need the backdrop: the tint, the bevel's lit rim, and the dark inner
 * line that reads as thickness. It is plainer than the real thing, not broken — and because
 * it keeps the same rim lighting, a panel does not change character between devices.
 */
internal fun DrawScope.drawGlassFallback(
    style: GlassStyle,
    light: GlassLight,
    radii: FloatArray,
    bevel: Float,
) {
    val corner = CornerRadius(radii.getOrElse(0) { 0f }, radii.getOrElse(0) { 0f })
    val tint = style.tint

    // Opaque base first: with no backdrop to blur, the tint alone cannot carry content.
    drawRoundRect(color = style.fallbackSurface, cornerRadius = corner, size = size)

    // Base tint, lifted slightly on the side the light comes from so the panel is not flat.
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                tint.copy(alpha = (tint.alpha * 1.9f).coerceAtMost(1f)),
                tint.copy(alpha = tint.alpha * 0.85f),
            ),
            start = Offset(size.width * (0.5f + light.x * 0.5f), size.height * (0.5f + light.y * 0.5f)),
            end = Offset(size.width * (0.5f - light.x * 0.5f), size.height * (0.5f - light.y * 0.5f)),
        ),
        cornerRadius = corner,
        size = size,
    )

    // Lit rim: brightest where the edge faces the light, so a top-left light gives a bright
    // top edge fading around the corners.
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = style.specular * 0.55f),
                Color.White.copy(alpha = style.specular * 0.06f),
                Color.White.copy(alpha = style.specular * 0.18f),
            ),
            start = Offset(size.width * (0.5f + light.x * 0.5f), size.height * (0.5f + light.y * 0.5f)),
            end = Offset(size.width * (0.5f - light.x * 0.5f), size.height * (0.5f - light.y * 0.5f)),
        ),
        cornerRadius = corner,
        size = size,
        style = Stroke(width = bevel * 0.34f),
    )

    // Inner thickness line, just inside the rim.
    drawRoundRect(
        color = Color.Black.copy(alpha = style.innerShadow * 0.55f),
        topLeft = Offset(bevel * 0.5f, bevel * 0.5f),
        size = Size(
            (size.width - bevel).coerceAtLeast(0f),
            (size.height - bevel).coerceAtLeast(0f),
        ),
        cornerRadius = CornerRadius(
            (corner.x - bevel * 0.5f).coerceAtLeast(0f),
            (corner.y - bevel * 0.5f).coerceAtLeast(0f),
        ),
        style = Stroke(width = bevel * 0.22f),
    )
}
