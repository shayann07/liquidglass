package com.wexpa.liquidglass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ImageInfo

/**
 * The path-field image is declared premultiplied so that Skia links one glass program for it and for
 * the placeholder field (#78). That is only a declaration change if every alpha byte is 255, in which
 * case premultiplied and unpremultiplied bytes are the same image. This test pins both halves: the
 * alpha bytes, and the rendered pixels of a star-shaped glass (`uShapeKind` 1) with the field declared
 * premultiplied (production) against the same bytes declared unpremultiplied (the old declaration).
 */
class GlassPathFieldAlphaTypeTest {
    private val width = 160
    private val height = 140
    private val pad = 24
    private val band = 28f

    private fun star(): Path = Path().apply {
        val cx = width * 0.5f
        val cy = height * 0.5f
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) height * 0.48f else height * 0.2f
            val a = i * PI / 5.0 - PI / 2.0
            val p = Offset(cx + (cos(a) * r).toFloat(), cy + (sin(a) * r).toFloat())
            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        close()
    }

    private fun raw(bitmap: ImageBitmap): ByteArray {
        val skia = bitmap.asSkiaBitmap()
        return skia.readPixels(skia.imageInfo, skia.imageInfo.minRowBytes, 0, 0) ?: error("no pixels")
    }

    private fun declaredUnpremul(field: GlassPathField): GlassPathField {
        val src = field.bitmap.asSkiaBitmap()
        val info = ImageInfo(src.width, src.height, src.imageInfo.colorType, ColorAlphaType.UNPREMUL)
        val bitmap = Bitmap()
        bitmap.allocPixels(info)
        bitmap.installPixels(raw(field.bitmap))
        bitmap.setImmutable()
        return GlassPathField(bitmap.asComposeImageBitmap(), field.range, field.scale, field.layerWidth, field.layerHeight)
    }

    private fun render(field: GlassPathField): IntArray = GlassRender.render(
        width = width, height = height, pad = pad,
        refractBand = band, refractDepth = 12f,
        radii = FloatArray(4) { 20f }, profile = 1f, formation = 0f,
        specular = 0.4f, fresnel = 0.3f, innerShadow = 0.2f, blur = 2f,
        field = field,
        backdrop = { x, y ->
            val checker = if (((x / 6) + (y / 6)) % 2 == 0) 0x303030 else 0xB0B0B0
            checker or ((x * 3) and 0xFF shl 8) or ((y * 2) and 0xFF shl 16)
        },
    )

    @Test
    fun fieldImageIsPremultipliedAndFullyOpaque() {
        val field = assertNotNull(buildPathField(star(), width + 2 * pad, height + 2 * pad, pad.toFloat(), band))
        assertEquals(ColorAlphaType.PREMUL, field.bitmap.asSkiaBitmap().alphaType)
        val bytes = raw(field.bitmap)
        assertTrue(bytes.isNotEmpty())
        for (i in 3 until bytes.size step 4) {
            assertEquals(255, bytes[i].toInt() and 0xFF, "alpha byte of texel ${i / 4}")
        }
    }

    @Test
    fun premultipliedAndUnpremultipliedDeclarationsRenderTheSamePixels() {
        val field = assertNotNull(buildPathField(star(), width + 2 * pad, height + 2 * pad, pad.toFloat(), band))
        val old = declaredUnpremul(field)
        assertEquals(ColorAlphaType.UNPREMUL, old.bitmap.asSkiaBitmap().alphaType)
        assertContentEquals(raw(field.bitmap), raw(old.bitmap), "same bytes under both declarations")
        assertContentEquals(render(old), render(field), "rendered star glass, UNPREMUL vs PREMUL field")
    }
}
