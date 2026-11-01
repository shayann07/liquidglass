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
import kotlin.math.min

/** One resolved drawing transform, shared by a bar's material and both ink variants.
 * This never participates in measuring, placement, semantics or pointer coordinates. */
internal data class GlassMaterialDrawing(
    val pull: GlassPullDeformation = GlassPullDeformation.None,
    val pressX: Float = 1f,
    val pressY: Float = 1f,
    val centre: Offset = Offset.Zero,
    val footprint: ImageBitmap? = null,
    /** The transform the material's content follows: the press and pull without the reach toward
     * the finger ([GlassInteraction.pressReach]), which shapes the outline only. On iOS the held
     * bar's icons follow its plain press scale while its edge reaches for the finger (video2,
     * held at Calls: selected ink at 204 px, the 1.05 scale's position, 2026-10-14). */
    val contentPull: GlassPullDeformation = pull,
    val contentPressX: Float = pressX,
)

@Stable
internal class GlassMaterialDrawingState {
    var drawing by mutableStateOf(GlassMaterialDrawing())
}

/**
 * Connects a free glass button to the bar it fuses with ([GlassTabBarAccessory.link]): the button's
 * [liquidGlass] publishes its animated press growth and pull, and the bar's outline reaches for
 * where the button is drawn, not where it is laid out. iOS 26's Phone search button joins the bar
 * through a neck while pressed and breaks away from it when pulled (IMG_6756, 2026-10-15).
 */
@Stable
class GlassAccessoryLink internal constructor() {
    internal val drawing = GlassMaterialDrawingState()
    internal var barState: LiquidGlassState? by mutableStateOf(null)
}

/** A [GlassAccessoryLink] that survives recomposition. */
@Composable
fun rememberGlassAccessoryLink(): GlassAccessoryLink = remember { GlassAccessoryLink() }

/**
 * The accessory's drawn body around its laid-out centre: centre offset, then its radius along x
 * and along y (px). For a body stretched at a slant these are its reach along each axis, not its
 * bounding box, so a shape built on them stays inside it.
 */
internal fun GlassMaterialDrawing.accessoryExtents(halfWidth: Float, halfHeight: Float): FloatArray {
    val a = pull.angleDegrees * (kotlin.math.PI.toFloat() / 180f)
    val c = cos(a); val s = sin(a)
    // The pressed body stretched along the pull (half axis A) and thinned across it (B).
    val r = (halfWidth * pressX + halfHeight * pressY) / 2f
    val along = r * pull.along; val across = r * pull.across
    fun reach(cx: Float, sx: Float) = 1f / kotlin.math.sqrt((cx * cx) / (along * along) + (sx * sx) / (across * across))
    return floatArrayOf(pull.translationX, pull.translationY, reach(c, s), reach(s, c))
}

/**
 * The largest stadium (half extents and corner radius, px) inside an axis-aligned ellipse with
 * half axes [a], [b]: a bar fusing with a stretched round accessory must not show its rounded-rect
 * copy past the accessory's elliptical body. Equal axes give the circle itself.
 */
internal fun glassInscribedStadium(a: Float, b: Float): FloatArray {
    if (a <= 0f || b <= 0f) return floatArrayOf(a, b, min(a, b))
    val long = maxOf(a, b); val short = min(a, b)
    if (long - short < 1e-3f) return floatArrayOf(a, b, short)
    fun inside(c: Float, r: Float): Boolean {
        // The cap circle centred on the long axis at c, radius r, sampled every 7.5 degrees.
        for (i in 0..12) {
            val t = i * (kotlin.math.PI.toFloat() / 24f)
            val x = c + r * cos(t); val y = r * sin(t)
            if ((x * x) / (long * long) + (y * y) / (short * short) > 1.0001f) return false
        }
        return true
    }
    var best = floatArrayOf(short, short, 0f); var bestArea = -1f
    for (i in 0..8) {
        val r = short * (0.6f + 0.05f * i)
        var lo = 0f; var hi = long - r
        if (!inside(0f, r)) continue
        repeat(14) { val mid = (lo + hi) / 2f; if (inside(mid, r)) lo = mid else hi = mid }
        val area = 4f * lo * r + kotlin.math.PI.toFloat() * r * r
        // 3% inside, for the slant the axis-aligned stadium cannot follow.
        if (area > bestArea) { bestArea = area; best = floatArrayOf((lo + r) * .97f, r * .97f, r * .97f) }
    }
    return if (a >= b) floatArrayOf(best[0], best[1], best[2]) else floatArrayOf(best[1], best[0], best[2])
}

/** Where the material's content draws the point [x], [y] (its own frame): the forward map of
 * [glassMaterialForeground]'s transform, the press and pull without the reach. */
internal fun GlassMaterialDrawing.contentPoint(x: Float, y: Float): Offset {
    val pull = contentPull
    var px = (x - centre.x) * contentPressX
    var py = (y - centre.y) * pressY
    val a = pull.angleDegrees * kotlin.math.PI / 180.0
    val c = kotlin.math.cos(a).toFloat(); val s = kotlin.math.sin(a).toFloat()
    // R(a) S R(-a), as withGlassMaterialPull applies it, then the translation.
    val u = (c * px + s * py) * pull.along
    val v = (-s * px + c * py) * pull.across
    px = c * u - s * v
    py = s * u + c * v
    return Offset(centre.x + px + pull.translationX, centre.y + py + pull.translationY)
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
    /** How much of the drawing the content follows, 0 to 1, read at draw time; null follows all of it. */
    share: (() -> Float)? = null,
): Modifier = if (state == null) this else drawWithContent {
    val frame = state.drawing
    val k = share?.invoke()?.coerceIn(0f, 1f) ?: 1f
    if (k <= 0f) { this@drawWithContent.drawContent(); return@drawWithContent }
    val p = frame.contentPull
    val pull = if (k >= 1f) p else GlassPullDeformation(1f + (p.along - 1f) * k, 1f + (p.across - 1f) * k,
        p.angleDegrees, p.translationX * k, p.translationY * k)
    withGlassMaterialPull(pull, 1f + (frame.contentPressX - 1f) * k, 1f + (frame.pressY - 1f) * k,
        pivot = frame.centre - origin) { this@drawWithContent.drawContent() }
}
