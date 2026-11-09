package com.wexpa.liquidglass

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

internal actual fun rasterizeMaskAlpha(
    path: Path,
    width: Int,
    height: Int,
    pad: Float,
    buffers: GlassPathFieldBuffers,
): ByteArray? {
    val mask = ImageBitmap(width, height, ImageBitmapConfig.Alpha8)
    val canvas = Canvas(mask)
    val paint = Paint().apply { color = Color.White }
    canvas.save()
    canvas.translate(pad, pad)
    canvas.drawPath(path, paint)
    canvas.restore()
    val skia = mask.asSkiaBitmap()
    val info = ImageInfo(width, height, ColorType.ALPHA_8, ColorAlphaType.PREMUL)
    return skia.readPixels(info, width, 0, 0)
}

internal actual fun createGreyscaleFieldBitmap(
    width: Int,
    height: Int,
    grey: ByteArray,
): ImageBitmap {
    // Skia wants RGBA bytes with an unpremultiplied opaque alpha so sampling never scales distance.
    val bytes = ByteArray(width * height * 4)
    for (i in 0 until width * height) {
        val v = grey[i]
        val o = i * 4
        bytes[o] = v
        bytes[o + 1] = v
        bytes[o + 2] = v
        bytes[o + 3] = -1
    }
    val info = ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
    val bitmap = Bitmap()
    bitmap.allocPixels(info)
    bitmap.installPixels(bytes)
    bitmap.setImmutable()
    return bitmap.asComposeImageBitmap()
}

internal actual fun createFieldBitmap(
    width: Int,
    height: Int,
    pixels: IntArray,
): ImageBitmap {
    // Skia wants bytes; the field is greyscale so the channel order does not matter, but the
    // alpha does - an unpremultiplied opaque byte keeps the sampler from scaling the value.
    val bytes = ByteArray(width * height * 4)
    for (i in pixels.indices) {
        val argb = pixels[i]
        val o = i * 4
        bytes[o] = (argb shr 16 and 0xFF).toByte()
        bytes[o + 1] = (argb shr 8 and 0xFF).toByte()
        bytes[o + 2] = (argb and 0xFF).toByte()
        bytes[o + 3] = (argb shr 24 and 0xFF).toByte()
    }
    val info = ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
    val bitmap = Bitmap()
    bitmap.allocPixels(info)
    bitmap.installPixels(bytes)
    bitmap.setImmutable()
    return bitmap.asComposeImageBitmap()
}
