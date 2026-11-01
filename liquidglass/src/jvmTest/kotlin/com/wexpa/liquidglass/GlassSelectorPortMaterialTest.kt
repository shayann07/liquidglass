package com.wexpa.liquidglass

import androidx.compose.ui.unit.isSpecified
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Calm selector's material for the CanvasKit and .NET ports (ports/skia/selector.mjs
 * `calmSelectorMaterial`, ports/dotnet CalmSelectorMaterial): the production pill and lens styles
 * as shader uniforms, the fold constants, and uniform sets computed by the production
 * [glassSelectorMaterial] / [glassSelectorFormation] that the ports must reproduce. Lengths are
 * in dp (`dpFields`); the ports multiply them by their density. Regenerate with
 * -Pliquidglass.exportShaders=true; otherwise the committed fixture must match.
 */
class GlassSelectorPortMaterialTest {
    /** The style-derived uniforms exactly as LiquidGlass.kt builds them (lengths left in dp). */
    private fun uniforms(s: GlassStyle): Map<String, List<Float>> = linkedMapOf(
        "uRefractDepth" to listOf(s.refractionDepth.value),
        "uAberration" to listOf(s.dispersion),
        "uProfile" to listOf(s.profile.uniform),
        "uHeldLens" to listOf(s.heldLens.coerceIn(0f, 1f)),
        "uHeldMagnification" to listOf(s.heldMagnification.coerceIn(0f, .6f)),
        "uHeldGlow" to listOf(s.heldGlow.coerceIn(0f, 2f)),
        "uHeldGlowReach" to listOf(s.heldGlowReach.value.coerceAtLeast(0f)),
        // GlassStyle.capFold rides on the same uniform above 1, as LiquidGlass sets it.
        "uFoldCorners" to listOf(if (s.capFold > 0f) 1f + s.capFold.coerceIn(0f, 1f) else s.foldAtCorners.coerceIn(0f, 1f)),
        "uRimMirror" to listOf(s.rimMirror.coerceIn(0f, 1f)),
        "uRimMirrorGain" to listOf(s.rimMirrorGain.coerceIn(.05f, 1f)),
        "uHeldEdgeRecovery" to listOf(s.heldEdgeRecovery.value.coerceAtLeast(0f)),
        "uHeldEdgeTone" to listOf(s.heldEdgeTone.coerceIn(0f, 1f)),
        "uHeldGlowFloor" to listOf(s.heldGlowFloor.coerceIn(0f, 1f)),
        "uSoftDetail" to listOf(s.softDetail.coerceIn(0f, 1f)),
        "uRestMap" to listOf(s.restMap.uniform),
        "uEdgeFold" to listOf(s.edgeFold.coerceIn(0f, 2f)),
        "uInkSplit" to listOf(if (s.inkDispersion.isNaN()) s.dispersion else s.inkDispersion),
        "uHeldInk" to listOf(if (s.heldInkContinuous) 1f else 0f),
        "uWideKernel" to listOf(if (s.wideKernel.isSpecified) s.wideKernel.value else 0f),
        "uFineShare" to listOf(s.fineShare),
        "uLift" to listOf(s.tintLift),
        "uLiftAdapt" to listOf(s.liftAdaptivity),
        "uIor" to listOf(s.indexOfRefraction),
        "uBevelPower" to listOf(s.bevelPower),
        "uCornerPower" to listOf(s.cornerPower),
        "uMirror" to listOf(s.mirror),
        "uFresnel" to listOf(s.fresnel),
        "uHiChroma" to listOf(s.highlightChroma),
        "uLegibility" to listOf(s.legibility),
        "uBevel" to listOf(s.bevel.value),
        "uSpecular" to listOf(s.specular),
        "uSpecularPow" to listOf(s.specularPower),
        "uTint" to listOf(s.tint.red, s.tint.green, s.tint.blue, s.tint.alpha),
        "uInnerShadow" to listOf(s.innerShadow),
        "uAdaptive" to listOf(s.adaptivity),
        "uBlur" to listOf(s.blurRadius.value),
        "uCounterLight" to listOf(s.counterLight),
        "uEdgeShadow" to listOf(s.edgeShadow),
        "uCapEdgeShadow" to listOf(s.capEdgeShadow),
        "uRimSoft" to listOf(s.rimSoftness.value),
        "uTintAbsorb" to listOf(s.tintAbsorption),
        "uEdgeLight" to listOf(s.edgeLight),
        "uBevelPeak" to listOf(s.bevelPeak),
        // Host terms: the raw backdrop added over the bar it looks through.
        "rawShare" to listOf(s.rawShare),
        // The raw backdrop's blur (GlassStyle.rawBlur), in dp; a host term like rawShare.
        "rawBlur" to listOf(s.rawBlur.value),
        // Host terms for the selector body (18 October): its refraction band's scale, and its contact
        // shadow (strength, share inside the backdrop, blur and offset in dp; -1 = sized from the panel).
        "bodyBandScale" to listOf(s.bodyBandScale),
        "contactShadow" to listOf(s.contactShadow.coerceIn(0f, 1f)),
        "contactShadowInside" to listOf(s.contactShadowInside.coerceIn(0f, 1f)),
        "contactShadowBlur" to listOf(if (s.contactShadowBlur.isSpecified) s.contactShadowBlur.value else -1f),
        "contactShadowOffset" to listOf(if (s.contactShadowOffset.isSpecified) s.contactShadowOffset.value else -1f),
    )

    private val dpFields = listOf("uRefractDepth", "uHeldGlowReach", "uHeldEdgeRecovery", "uWideKernel", "uBevel", "uBlur", "uRimSoft")

    private fun json(map: Map<String, List<Float>>): String = map.entries.joinToString(",", "{", "}") { (k, v) ->
        "\"$k\":" + if (v.size == 1) "${v[0]}" else v.joinToString(",", "[", "]")
    }

    @Test fun calmSelectorMaterialForPorts() {
        val styles = StringBuilder()
        val samples = mutableListOf<String>()
        for (dark in listOf(true, false)) {
            val style = GlassTabBarStyle.Calm(dark)
            if (styles.isNotEmpty()) styles.append(",\n")
            styles.append("\"${if (dark) "dark" else "light"}\":{\"pill\":${json(uniforms(style.pill))},\"lens\":${json(uniforms(style.lens))}," +
                "\"bar\":${json(uniforms(style.bar))},\"clearBar\":${json(uniforms(GlassTabBarStyle.CalmClear(dark).bar))},\"heldLift\":${style.heldLift},\"touchLift\":${style.touchLift}," +
                "\"pillInkFollows\":${style.pill.inkDispersion.isNaN()},\"lensInkFollows\":${style.lens.inkDispersion.isNaN()}," +
                "\"tapOptics\":${style.tapOptics},\"tapOpticsFold\":${style.tapOpticsFold},\"tapTransitFold\":${if (style.tapTransitFold.isNaN()) style.tapOpticsFold else style.tapTransitFold},\"tapTintFade\":${style.tapTintFade}}")
            for (formation in listOf(0f, .2f, .49f, .5f, .7f, 1f)) for (transit in listOf(0f, .3f, .8f, 1f)) for (travel in listOf(0f, .6f)) {
                val material = glassSelectorMaterial(style, formation, transit, travel)
                val lensFormation = glassSelectorFormation(style, formation, transit, travel)
                samples += "{\"dark\":$dark,\"formation\":$formation,\"transit\":$transit,\"heldTravel\":$travel," +
                    "\"uFormation\":${lensFormation.coerceIn(0f, 1f)},\"values\":${json(uniforms(material))}}"
            }
        }
        val fold = GlassStyle().copy(profile = GlassProfile.Measured)
        val text = "{\"dpFields\":${dpFields.joinToString(",", "[", "]") { "\"$it\"" }},\n" +
            "\"fold\":{\"measuredProfile\":${fold.profile.uniform},\"foldCorners\":1.0,\"transitFoldCorners\":$GlassSelectorTransitFoldCorners,\"rimMirror\":$GlassSelectorRimMirror,\"heldRimMirror\":$GlassSelectorHeldRimMirror," +
            "\"rimMirrorGain\":$GlassSelectorRimGain,\"dispersion\":$GlassSelectorRimDispersion,\"movingDispersion\":$GlassSelectorRimFringeMoving," +
            "\"heldMovingDispersion\":$GlassSelectorHeldFringeMoving,\"inkSplitShare\":$GlassSelectorInkSplitShare,\"transitInkSplitShare\":$GlassSelectorTransitInkSplitShare},\n" +
            "\"styles\":{\n$styles},\n\"samples\":[\n${samples.joinToString(",\n")}]}\n"
        val file = File("../ports/skia/fixtures/selector-material.json")
        if (System.getProperty("liquidglass.exportShaders") == "true") { file.parentFile.mkdirs(); file.writeText(text) }
        assertEquals(text, file.readText().replace("\r\n", "\n"),
            "Regenerate the selector material with -Pliquidglass.exportShaders=true")
    }
}
