package com.wexpa.liquidglass

import java.io.File
import org.jetbrains.skia.RuntimeEffect
import kotlin.test.Test
import kotlin.test.assertEquals

/** The portable kit is generated from evaluated production strings, never a second shader. */
class GlassShaderBundleTest {
    @Test fun portableSourcesMatchProduction() {
        val directory = File("../ports/skia/shaders")
        val export = System.getProperty("liquidglass.exportShaders") == "true"
        if (export) directory.mkdirs()
        for ((name, source) in linkedMapOf(
            "material" to GLASS_SHADER_SOURCE,
            "content" to GLASS_CONTENT_SHADER_SOURCE,
            "endpoint" to GLASS_ENDPOINT_SHADER_SOURCE,
        )) {
            val file = File(directory, "$name.sksl")
            if (export) file.writeText(source)
            assertEquals(source.replace("\r\n", "\n"), file.readText().replace("\r\n", "\n"),
                "Regenerate portable shaders with -Pliquidglass.exportShaders=true")
            RuntimeEffect.makeForShader(source).close()
        }
        // Independent native JVM pixels for the CanvasKit port. RGBA is premultiplied;
        // using raw bytes avoids an image encoder silently changing edge alpha.
        for (profile in 0..3) {
            val pixels = GlassRender.render(64, 64, profile = profile.toFloat(), heldLens = 1f,
                heldMagnification = .2f, heldGlow = 0f, backdrop = { x, y ->
                    0xff000040.toInt() or (x shl 16) or (y shl 8)
                })
            val rgba = ByteArray(pixels.size * 4)
            pixels.forEachIndexed { i, p ->
                rgba[i * 4] = (p ushr 16).toByte(); rgba[i * 4 + 1] = (p ushr 8).toByte()
                rgba[i * 4 + 2] = p.toByte(); rgba[i * 4 + 3] = (p ushr 24).toByte()
            }
            val fixture = File(directory.parentFile, "fixtures").resolve("profile-$profile.rgba")
            if (export) { fixture.parentFile.mkdirs(); fixture.writeBytes(rgba) }
            // Native Skia versions may differ by one quantization level. This fixture should
            // still describe the current shader, rather than silently comparing two old ports.
            val expected = fixture.readBytes()
            assertEquals(rgba.size, expected.size)
            expected.indices.forEach { i ->
                kotlin.test.assertTrue(kotlin.math.abs((rgba[i].toInt() and 255) -
                    (expected[i].toInt() and 255)) <= 3, "Stale profile-$profile fixture at byte $i")
            }
        }
    }
}
