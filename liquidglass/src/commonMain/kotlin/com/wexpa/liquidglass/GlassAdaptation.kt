package com.wexpa.liquidglass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/**
 * Decides whether glass over a given region should flip to its light or dark style.
 *
 * Apple treats the flip as one decision per element rather than per pixel, because symbols and
 * labels drawn *on* the glass have to flip with it, and a fragment shader behind them cannot
 * reach them. So the decision is made here, on the host, and the same number goes to the glass
 * and to whatever draws the content — see [LiquidGlassState.inversion].
 *
 * Three things make this behave rather than chatter.
 *
 * It reads a **sparse grid**, thirty samples, not the whole region. Backdrop luminance is a
 * low-frequency question and reading every pixel to answer it would cost more than the material.
 *
 * It uses **hysteresis**, entering the flip above 0.60 and leaving it below 0.44. A single
 * threshold with no band flickers every time a headline scrolls past, because luminance crosses
 * it repeatedly within one gesture.
 *
 * And it **crossfades** over 180ms rather than switching. Apple publishes no threshold, no band
 * and no duration; these are ours, and the shape of the mechanism is what matters.
 */
@Composable
fun rememberBackdropInversion(
    state: LiquidGlassState,
    /** The region to judge, in the backdrop's own coordinates. Null means the whole backdrop. */
    region: Rect? = null,
    /** How often to look. Ten times a second is well under a scroll's own frame rate. */
    sampleIntervalMillis: Long = 100L,
    enterAbove: Float = 0.60f,
    leaveBelow: Float = 0.44f,
): State<Float> {
    var flipped by remember { mutableStateOf(false) }
    val currentRegion = rememberUpdatedState(region)

    LaunchedEffect(state, sampleIntervalMillis) {
        while (true) {
            delay(sampleIntervalMillis)
            val layer = state.layer ?: continue
            val luminance = withContext(Dispatchers.Default) {
                sampleLuminance(layer, currentRegion.value)
            } ?: continue
            flipped = when {
                luminance > enterAbove -> true
                luminance < leaveBelow -> false
                else -> flipped
            }
        }
    }

    return animateFloatAsState(
        targetValue = if (flipped) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "glass_inversion",
    )
}

private const val GRID_X = 6
private const val GRID_Y = 5

/**
 * Mean luminance over a sparse grid of the recorded backdrop.
 *
 * Reading a `GraphicsLayer` back means turning it into a bitmap, which is not free, so this
 * runs off the main thread and at a fixed low rate. Returns null when the layer cannot be read
 * — during the first frame, or on a platform that refuses — and the caller simply keeps its
 * previous decision, which is the right failure mode for something that is meant to be stable.
 */
private suspend fun sampleLuminance(layer: GraphicsLayer, region: Rect?): Float? {
    val bitmap: ImageBitmap = runCatching { layer.toImageBitmap() }.getOrNull() ?: return null
    if (bitmap.width <= 0 || bitmap.height <= 0) return null

    val left = ((region?.left ?: 0f).toInt()).coerceIn(0, bitmap.width - 1)
    val top = ((region?.top ?: 0f).toInt()).coerceIn(0, bitmap.height - 1)
    val right = ((region?.right ?: bitmap.width.toFloat()).toInt()).coerceIn(left + 1, bitmap.width)
    val bottom = ((region?.bottom ?: bitmap.height.toFloat()).toInt())
        .coerceIn(top + 1, bitmap.height)

    val pixels = runCatching {
        bitmap.toPixelMap(left, top, right - left, bottom - top)
    }.getOrNull() ?: return null

    var total = 0f
    var count = 0
    for (gy in 0 until GRID_Y) {
        for (gx in 0 until GRID_X) {
            val x = ((gx + 0.5f) / GRID_X * pixels.width).toInt().coerceIn(0, pixels.width - 1)
            val y = ((gy + 0.5f) / GRID_Y * pixels.height).toInt().coerceIn(0, pixels.height - 1)
            val c = pixels[x, y]
            total += 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
            count++
        }
    }
    return if (count == 0) null else total / count
}

/**
 * How opaque a glass element's drop shadow should be over the given backdrop.
 *
 * Apple: as content scrolls under a glass element its shadows become more prominent. The
 * direction is theirs, the numbers are ours — a solid light ground needs almost no shadow to
 * separate the element from it, and text underneath needs noticeably more or the element stops
 * reading as a separate layer.
 *
 * [contrast] is the same local-contrast signal the shader derives for free; pass 0 if the app
 * has no better estimate and the shadow simply sits at its floor.
 */
fun glassShadowAlpha(contrast: Float): Float = 0.14f + 0.16f * contrast.coerceIn(0f, 1f)
