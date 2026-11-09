package com.wexpa.liquidglass

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.min

internal actual fun rasterizeMaskAlpha(
    path: Path,
    width: Int,
    height: Int,
    pad: Float,
    buffers: GlassPathFieldBuffers,
): ByteArray? {
    val mask = ImageBitmap(width, height)
    val canvas = Canvas(mask)
    val paint = Paint().apply { color = Color.White }
    canvas.save()
    canvas.translate(pad, pad)
    canvas.drawPath(path, paint)
    canvas.restore()
    val out = buffers.ensureAlphaScratch(width * height)
    val bitmap = mask.asAndroidBitmap()
    val stripRows = min(32, height)
    val strip = IntArray(width * stripRows)
    var y0 = 0
    while (y0 < height) {
        val rows = min(stripRows, height - y0)
        bitmap.getPixels(strip, 0, width, 0, y0, width, rows)
        val count = width * rows
        val dstOffset = y0 * width
        for (i in 0 until count) {
            out[dstOffset + i] = (strip[i] ushr 24).toByte()
        }
        y0 += rows
    }
    return out
}

internal actual fun createGreyscaleFieldBitmap(
    width: Int,
    height: Int,
    grey: ByteArray,
): ImageBitmap {
    val count = width * height
    val pixels = IntArray(count)
    for (i in 0 until count) {
        val v = grey[i].toInt() and 0xFF
        pixels[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
}

internal actual fun createFieldBitmap(
    width: Int,
    height: Int,
    pixels: IntArray,
): ImageBitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    .asImageBitmap()
