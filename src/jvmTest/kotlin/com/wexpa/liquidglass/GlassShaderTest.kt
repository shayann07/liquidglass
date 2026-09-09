package com.wexpa.liquidglass

import org.jetbrains.skia.RuntimeEffect
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Compiles the shader.
 *
 * The shader only runs on Android 13 and above, so on an older test device it silently takes
 * the fallback path and a syntax error or a misnamed uniform would go unnoticed until someone
 * opened the app on a newer phone. Skia speaks the same dialect, so compiling it here catches
 * that on any machine, without a device.
 */
class GlassShaderTest {

    @Test
    fun testShaderCompiles() {
        val effect = RuntimeEffect.makeForShader(GLASS_SHADER_SOURCE)
        assertNotNull(effect, "GLASS_SHADER_SOURCE failed to compile")
    }

    @Test
    fun testEveryUniformTheHostSetsExistsInTheShader() {
        // A uniform the host sets but the shader does not declare is dropped silently on
        // Android; one the shader declares but the host never sets reads as zero. Both show up
        // as a material that renders but looks wrong, so the two lists are checked against
        // each other here rather than by eye on a device.
        val declared = Regex("""uniform\s+\w+\s+(\w+)\s*;""")
            .findAll(GLASS_SHADER_SOURCE)
            .map { it.groupValues[1] }
            .toSet()

        val setByHost = setOf(
            "uSize", "uRadii", "uRefractBand", "uRefractDepth", "uBevel",
            "uLight", "uSpecular", "uSpecularPow", "uTint", "uInnerShadow", "uAdaptive",
        )

        val missing = setByHost - declared
        assertTrue(missing.isEmpty(), "host sets uniforms the shader does not declare: $missing")

        // `content` is bound as the input shader rather than as a float uniform.
        val unset = declared - setByHost - setOf("content")
        assertTrue(unset.isEmpty(), "shader declares uniforms nothing sets: $unset")
    }
}
