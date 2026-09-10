package com.wexpa.liquidglass

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.pow

/**
 * A rounded rectangle whose corners are superellipse arcs rather than circular ones.
 *
 * Apple's shapes are squircles. `RoundedCornerShape` draws a circular arc, which meets the
 * straight edge with a visible change in curvature — the corner "starts" somewhere. A
 * superellipse corner blends into the edge continuously, and the difference is small, constant
 * and exactly where the eye checks, which is why it is worth having a shape for.
 *
 * Use it as an ordinary [Shape] — for `clip`, `background`, `border`, anything — and pass the
 * same [power] to [GlassStyle.cornerPower] so the material's distance field agrees with the
 * clip. [LiquidGlassSquircle] does both for you.
 *
 * The path is a polyline, not beziers. Sixteen segments per corner is under a third of a pixel
 * of chord error on a 32dp corner, it is built once per size, and it avoids the approximation
 * error of fitting cubics to a curve that has no exact cubic form anyway.
 */
@Immutable
data class GlassSquircleShape(
    val cornerRadius: Dp,
    /** 2 is a circular arc — the same shape `RoundedCornerShape` draws. 4 is Apple's squircle. */
    val power: Float = 4f,
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val radius = with(density) { cornerRadius.toPx() }
            .coerceAtMost(minOf(size.width, size.height) / 2f)
        if (radius <= 0f) return Outline.Rectangle(size.toRect())
        return Outline.Generic(squirclePath(size, radius, power.coerceAtLeast(2f)))
    }
}

private fun Size.toRect() = androidx.compose.ui.geometry.Rect(0f, 0f, width, height)

private const val SEGMENTS = 16

/**
 * One superellipse corner, swept from the edge tangent round to the next edge tangent.
 *
 * The curve is `|x/r|^n + |y/r|^n = 1` in corner-local coordinates. Parameterising by angle and
 * solving for the radius at that angle keeps the point spacing even, which a naive sweep in x
 * does not — it bunches every point into the diagonal and leaves the tangents ragged.
 */
private fun squirclePath(size: Size, r: Float, n: Float): Path {
    val w = size.width
    val h = size.height
    val path = Path()

    fun corner(cx: Float, cy: Float, sx: Float, sy: Float, startAtVertical: Boolean) {
        for (i in 0..SEGMENTS) {
            val t = i.toFloat() / SEGMENTS
            // Sweep the quarter in even angular steps of the *unit* superellipse.
            val angle = (if (startAtVertical) (1f - t) else t) * (kotlin.math.PI.toFloat() / 2f)
            val ca = kotlin.math.cos(angle)
            val sa = kotlin.math.sin(angle)
            val inv = 1f / n
            // Radius of the Lame curve at this angle.
            val denom = (kotlin.math.abs(ca).pow(n) + kotlin.math.abs(sa).pow(n)).pow(inv)
            val x = r * ca / denom
            val y = r * sa / denom
            val px = cx + sx * x
            val py = cy + sy * y
            if (path.isEmpty && i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
    }

    // Top-left, top-right, bottom-right, bottom-left, each swept so the path stays continuous.
    corner(r, r, -1f, -1f, startAtVertical = false)
    corner(w - r, r, 1f, -1f, startAtVertical = true)
    corner(w - r, h - r, 1f, 1f, startAtVertical = false)
    corner(r, h - r, -1f, 1f, startAtVertical = true)
    path.close()
    return path
}

/** The squircle corner radius Apple's own containers land near, for a card-sized surface. */
val DefaultSquircleRadius: Dp = 28.dp
