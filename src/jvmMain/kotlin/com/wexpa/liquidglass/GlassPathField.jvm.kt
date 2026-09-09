package com.wexpa.liquidglass

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

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
