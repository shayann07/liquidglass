package com.wexpa.liquidglass

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** Golden parameters from the actual Compose factory, consumed by the independent JS adapter. */
class GlassPortableMaterialSpecTest {
    @Test fun portableInAppParametersFollowTheProductionFactory() {
        val cases = buildList {
            for (dark in listOf(false, true)) {
                for (amount in listOf(0f, 17f, 29f, 31f, 50f, 62f, 79f, 86f, 100f)) {
                    val s = GlassStyle.inApp(dark, amount)
                    add("""{"dark":$dark,"tintAmount":$amount,"values":{"uTint":[${s.tint.red},${s.tint.green},${s.tint.blue},${s.tint.alpha}],"uLift":${s.tintLift},"uBlur":${s.blurRadius.value},"uWideKernel":${s.wideKernel.value},"uFineShare":${s.fineShare},"uAberration":${s.dispersion},"uRefractDepth":${s.refractionDepth.value},"uBevel":${s.bevel.value},"uBevelPeak":${s.bevelPeak},"uSpecular":${s.specular},"uSpecularPow":${s.specularPower},"uCounterLight":${s.counterLight},"uEdgeLight":${s.edgeLight},"uLegibility":${s.legibility}}}""")
                }
            }
        }.joinToString(",\n", "[\n", "\n]\n")
        val file = File("../ports/skia/fixtures/in-app-material.json")
        if (System.getProperty("liquidglass.exportShaders") == "true") file.writeText(cases)
        assertEquals(cases, file.readText().replace("\r\n", "\n"),
            "Regenerate in-app parameters with -Pliquidglass.exportShaders=true")
    }
}
