package com.wexpa.liquidglass

import org.jetbrains.skia.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Exercise the production filter graph when the ink strip is outside the destination clip. */
class GlassEndpointInputBoundsTest {
    @Test fun offscreenInkSurvivesClippingAtEveryViewportEdge() {
        val w = 128
        val h = 64
        val info = ImageInfo(w, h * 2, ColorType.RGBA_8888, ColorAlphaType.PREMUL)
        val pixels = ByteArray(w * h * 2 * 4)
        for (y in 0 until h * 2) for (x in 0 until w) {
            val i = (y * w + x) * 4
            if (y < h) {
                pixels[i] = 20; pixels[i + 1] = 40; pixels[i + 2] = 60; pixels[i + 3] = -1
            } else if (x in 45..82 && y - h in 22..41) {
                pixels[i] = -1; pixels[i + 1] = 90; pixels[i + 2] = 30; pixels[i + 3] = -1
            }
        }
        RuntimeEffect.makeForShader(GLASS_ENDPOINT_SHADER_SOURCE).use { effect ->
            Image.makeRaster(info, pixels, w * 4).use { input ->
                input.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP).use { placeholder ->
                    RuntimeShaderBuilder(effect).use { builder ->
                        builder.child("field", placeholder); builder.child("ink", placeholder)
                        builder.uniform("uSize", w.toFloat(), h.toFloat())
                        builder.uniform("uPad", 0f); builder.uniform("uInkStrip", h.toFloat())
                        builder.uniform("uRadii", 24f, 24f, 24f, 24f)
                        builder.uniform("uFuse", 0f, 0f, 0f, 0f); builder.uniform("uFuseShape", 0f, 0f)
                        builder.uniform("uCornerPower", 2f); builder.uniform("uShapeKind", 0f)
                        builder.uniform("uFieldRange", 1f); builder.uniform("uFieldScale", 1f)
                        builder.uniform("uBody", 0f, 0f, 0f, 0f)
                        builder.uniform("uBodyY", 0f); builder.uniform("uBodyKind", 0f)
                        builder.uniform("uPoseA", 1f, 0f, 0f, 1f); builder.uniform("uPoseAInv", 1f, 0f, 0f, 1f)
                        builder.uniform("uPoseC", 0f, 0f, 0f, 0f); builder.uniform("uPoseD", 1f, 1f, 1f, 0f)
                        glassEndpointInputFilter(builder, h.toFloat()).use { filter ->
                            Paint().use { paint ->
                                paint.imageFilter = filter
                                // Different placements put the storage below, across and outside
                                // the viewport. The visible ink must remain at its local centre.
                                for ((dx, dy) in listOf(0 to 0, -32 to 0, 32 to 0, 0 to -16, 0 to 16)) {
                                    Surface.makeRasterN32Premul(w, h).use { target ->
                                        target.canvas.clear(0)
                                        target.canvas.save()
                                        target.canvas.translate(dx.toFloat(), dy.toFloat())
                                        target.canvas.drawImage(input, 0f, 0f, paint)
                                        target.canvas.restore()
                                        Bitmap().use { bitmap ->
                                            bitmap.allocPixels(ImageInfo(w, h, ColorType.RGBA_8888, ColorAlphaType.PREMUL))
                                            assertTrue(target.readPixels(bitmap, 0, 0))
                                            val actual = bitmap.readPixels()!!
                                            val i = ((h / 2 + dy) * w + w / 2 + dx) * 4
                                            assertEquals(255, actual[i].toInt() and 255, "ink clipped at $dx,$dy")
                                            assertEquals(90, actual[i + 1].toInt() and 255)
                                            assertEquals(30, actual[i + 2].toInt() and 255)
                                            if (dy < 0) for (y in h + dy until h) {
                                                assertTrue((0 until w).all { x -> actual[(y * w + x) * 4 + 3] == 0.toByte() },
                                                    "storage visible below the material")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
