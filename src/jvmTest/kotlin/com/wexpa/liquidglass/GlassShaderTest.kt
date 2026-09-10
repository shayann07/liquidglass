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
            "uSize", "uRadii", "uRefractBand", "uBackdrop", "uAberration", "uBase", "uBlur", "uIor", "uBevelPower", "uMirror",
            "uFresnel", "uLegibility", "uCornerPower", "uTouch", "uTouchAmt",
            "uMaterialize", "uFrost", "uContrast", "uShapeKind", "uFieldRange", "uFieldScale", "uRefractDepth", "uBevel",
            "uLight", "uSpecular", "uSpecularPow", "uTint", "uInnerShadow", "uAdaptive",
            "uPad", "uScale", "uFlip", "uCounterLight", "uEdgeLight", "uBevelPeak",
        )

        val missing = setByHost - declared
        assertTrue(missing.isEmpty(), "host sets uniforms the shader does not declare: $missing")

        // `content` is bound as the input shader rather than as a float uniform.
        val unset = declared - setByHost - setOf("content", "field")
        assertTrue(unset.isEmpty(), "shader declares uniforms nothing sets: $unset")
    }
}

/**
 * The content pass, checked the same way — and checked against the panel shader, because the
 * two must bend identically or content will drift against the backdrop it sits in.
 */
class GlassContentShaderTest {

    @Test
    fun testContentShaderCompiles() {
        val effect = RuntimeEffect.makeForShader(GLASS_CONTENT_SHADER_SOURCE)
        assertNotNull(effect, "GLASS_CONTENT_SHADER_SOURCE failed to compile")
    }

    @Test
    fun testEveryUniformTheContentShaderDeclaresIsSetByTheHost() {
        val declared = Regex("""uniform\s+\w+\s+(\w+)\s*;""")
            .findAll(GLASS_CONTENT_SHADER_SOURCE)
            .map { it.groupValues[1] }
            .toSet()
        val setByHost = setOf(
            "uSize", "uPad", "uRadii", "uCornerPower", "uShapeKind", "uFieldRange", "uFieldScale",
            "uRefractBand", "uRefractDepth", "uIor", "uBevelPower", "uAberration", "uScale",
            "uMaterialize", "uTouch", "uTouchAmt",
        )
        val unset = declared - setByHost - setOf("content", "field")
        assertTrue(unset.isEmpty(), "content shader declares uniforms nothing sets: $unset")
        val missing = setByHost - declared
        assertTrue(missing.isEmpty(), "host sets content uniforms the shader lacks: $missing")
    }

    @Test
    fun testTheContentPassSharesThePanelShadersGeometry() {
        // The functions that decide where a pixel samples from must be the same text in both
        // shaders. A change to one that is not mirrored in the other would refract the content
        // through a different lens than the backdrop, which shows up as the symbol sliding
        // against the list behind it as the element moves.
        for (name in listOf("float lnNorm(", "float sdRoundRect(", "float bevelSlope(", "float snellShift(")) {
            val a = functionBody(GLASS_SHADER_SOURCE, name)
            val b = functionBody(GLASS_CONTENT_SHADER_SOURCE, name)
            assertTrue(a.isNotEmpty() && a == b, "$name differs between the panel and content shaders")
        }
    }

    private fun functionBody(source: String, signature: String): String {
        val start = source.indexOf(signature)
        if (start < 0) return ""
        val open = source.indexOf('{', start)
        var depth = 0
        for (i in open until source.length) {
            if (source[i] == '{') depth++
            if (source[i] == '}') { depth--; if (depth == 0) return source.substring(open, i + 1) }
        }
        return ""
    }
}

/** The container shader, checked the same way. */
class GlassContainerShaderTest {

    @Test
    fun testContainerShaderCompiles() {
        val effect = RuntimeEffect.makeForShader(GLASS_CONTAINER_SHADER_SOURCE)
        assertNotNull(effect, "GLASS_CONTAINER_SHADER_SOURCE failed to compile")
    }

    @Test
    fun testEveryUniformTheContainerShaderDeclaresIsSetByTheHost() {
        // The panel shader has had this check since it was written; the container did not, and
        // it cost three uniforms. uIor in particular reads as zero, which makes the exact-Snell
        // deviation evaluate to zero, which silently removes *all* refraction from a fused body
        // while leaving its geometry and lighting intact — so it still looks like a material,
        // just a flat one. Both shaders are checked now.
        val declared = Regex("""uniform\s+\w+\s+(\w+)""")
            .findAll(GLASS_CONTAINER_SHADER_SOURCE)
            .map { it.groupValues[1] }
            .filter { it.startsWith("u") }
            .toSet()

        val setByHost = setOf(
            "uSize", "uPad", "uBackdrop", "uBase", "uRect", "uRadius", "uCount", "uMerge",
            "uRefractBand", "uRefractDepth", "uAberration", "uIor", "uBevelPower", "uBlur",
            "uBevel", "uLight", "uSpecular", "uSpecularPow", "uFresnel", "uTint",
            "uInnerShadow", "uAdaptive", "uCounterLight", "uEdgeLight", "uBevelPeak",
        )

        val unset = declared - setByHost
        assertTrue(unset.isEmpty(), "container shader declares uniforms nothing sets: $unset")

        val missing = setByHost - declared
        assertTrue(missing.isEmpty(), "host sets container uniforms the shader lacks: $missing")
    }

    @Test
    fun testMemberLoopIsBoundedByTheDeclaredArraySize() {
        // SkSL needs unrollable loops, so the member cap is compiled into the source. If the
        // constant and the array size ever drift apart the shader still compiles and silently
        // ignores members, which is invisible until a container overflows.
        val arrayDecls = Regex("""uniform\s+\w+\s+\w+\[(\d+)]""")
            .findAll(GLASS_CONTAINER_SHADER_SOURCE)
            .map { it.groupValues[1].toInt() }
            .toSet()
        assertTrue(
            arrayDecls.all { it == MAX_GLASS_MEMBERS },
            "array sizes $arrayDecls do not all match MAX_GLASS_MEMBERS=$MAX_GLASS_MEMBERS",
        )
        assertTrue(
            GLASS_CONTAINER_SHADER_SOURCE.contains("i < $MAX_GLASS_MEMBERS"),
            "the member loop bound must be the same constant as the array size",
        )
    }
}
