package com.wexpa.liquidglass

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Which source map the resting corner lens uses (the `rest` term of the measured profile's fold family:
 * chrome at rest, at the curved parts of an outline only).
 *
 * [Legacy] is the map that shipped in 0.2.0-SNAPSHOT: the ring 0.55 W inside mapped onto the outer band.
 * [Measured] is the resting-corner source map fitted on the Photos toolbar's 72 px ends (the closeout's E9
 * record, 190 measured display-to-source landmark pairs, both appearances, both calibration targets),
 * compiled as the table in [GlassRestMapTable]. It is a scoped candidate, not a global replacement:
 *
 *  - It changes only the resting-corner contribution. The fully formed sheet fold, the straight-run
 *    formula, the held-lens family, coverage, padding, normals, corner weighting, dispersion, blur and
 *    lighting are untouched, and the existing formation blend carries it toward the same full fold.
 *  - It was measured at one radius: 72 reference px on the iPhone 13's 3 px/pt screen, which the
 *    library carries as its 24 dp design unit by convention (a point is not a measured Android dp
 *    equivalence). At any other radius the shader evaluates the same normalised table, which is a scale
 *    extrapolation (`s_R(d) = (R/72) s_72(d 72/R)`), not a second measurement; the Vitals bar's 31 dp
 *    corner is such an extrapolation.
 *  - Its shallow region (d < 6 reference px, under the rim) is a constant extension of the first knot and
 *    is unmeasured; the existing displacement fade over the outermost 1.5 px applies there.
 *
 * The default is [Legacy] everywhere. Immutable per style; hosts opt a surface in explicitly.
 */
enum class GlassRestMap {
    Legacy,
    Measured,
}

/** The map as the shader reads it: 0 legacy, 1 measured. */
internal val GlassRestMap.uniform: Float
    get() = when (this) {
        GlassRestMap.Legacy -> 0f
        GlassRestMap.Measured -> 1f
    }

/**
 * The measured resting-corner table, in the shader's own normalisation: `u = d / W` in, `s / W` out,
 * with `W = BEVEL_RATIO * R` (0.6 R). Defined once at R = 72 reference px, W = 43.2; the values are
 * dimensionless so the same table serves every radius (as an extrapolation away from 72).
 *
 * Shape: constant [V0] below the first knot (the unmeasured d < 6 extension), piecewise linear between
 * the knots, a cubic Hermite over the final interval whose slope runs from the incoming linear slope
 * [M0_END] to exactly 1 at the last knot, and identity at and above the last knot ([IDENTITY_U]). So the
 * map is continuous everywhere, its derivative is continuous into the identity join, and it has one
 * minimum (near u = 0.44, the ring 0.643 W inside) with the source depth decreasing before it and
 * increasing after — the orientation the measurement fixed.
 *
 * [source] is the reference implementation the shader text [sksl] is generated from and tested against;
 * the closeout's `exports/rest_source_map_reference_v2.py` is the same function in the analysis
 * toolchain. Compiled against the fitted curve the knots were chosen from, the table is within 0.146
 * reference px everywhere on 6..36 (gate 0.25).
 */
internal object GlassRestMapTable {
    /** Knot inputs u = d / 43.2, for d = 6, 7, 8, 9.5, 11, 13, 15, 19, 23, 27, 31.5, 36 reference px. */
    val U = floatArrayOf(
        0.138889f, 0.162037f, 0.185185f, 0.219907f, 0.254630f, 0.300926f,
        0.347222f, 0.439815f, 0.532407f, 0.625000f, 0.729167f, 0.833333f,
    )

    /** Knot outputs s / 43.2. The last equals the last input: identity, exactly. */
    val V = floatArrayOf(
        0.875903f, 0.823079f, 0.784259f, 0.741898f, 0.711296f, 0.681921f,
        0.661713f, 0.642755f, 0.649884f, 0.681898f, 0.745278f, 0.833333f,
    )

    /** The constant extension below the first knot. */
    val V0: Float get() = V[0]

    /** Identity at and above this u (36 / 43.2). */
    val IDENTITY_U: Float get() = U[U.size - 1]

    /** Start of the Hermite interval (31.5 / 43.2). */
    val HERMITE_U: Float get() = U[U.size - 2]

    /** Slope entering the final interval: that of the preceding linear segment. */
    val M0_END: Float get() = (V[V.size - 2] - V[V.size - 3]) / (U[U.size - 2] - U[U.size - 3])

    /** Slope leaving the final interval: identity. */
    const val M1_END: Float = 1f

    /** The map, evaluated exactly as the shader evaluates it. */
    fun source(u: Float): Float {
        val n = U.size
        if (u >= U[n - 1]) return u
        if (u >= U[n - 2]) {
            val u0 = U[n - 2]; val u1 = U[n - 1]; val h = u1 - u0; val t = (u - u0) / h
            val t2 = t * t; val t3 = t2 * t
            return (2f * t3 - 3f * t2 + 1f) * V[n - 2] + (t3 - 2f * t2 + t) * h * M0_END +
                (-2f * t3 + 3f * t2) * V[n - 1] + (t3 - t2) * h * M1_END
        }
        for (i in n - 3 downTo 0) {
            if (u >= U[i]) {
                val m = (V[i + 1] - V[i]) / (U[i + 1] - U[i])
                return V[i] + m * (u - U[i])
            }
        }
        return V[0]
    }

    /** A float as a six-decimal SkSL literal, without touching the platform's locale. */
    private fun lit(x: Float): String {
        val n = (x.toDouble() * 1_000_000.0).roundToLong()
        val a = abs(n)
        val frac = (a % 1_000_000).toString().padStart(6, '0')
        return (if (n < 0) "-" else "") + (a / 1_000_000).toString() + "." + frac
    }

    /** `float restSourceMeasured(float u) { ... }`, generated from the table above. */
    val sksl: String by lazy {
        val n = U.size
        val sb = StringBuilder()
        sb.append("float restSourceMeasured(float u) {\n")
        sb.append("    if (u >= ${lit(U[n - 1])}) {\n        return u;\n    }\n")
        sb.append("    if (u >= ${lit(U[n - 2])}) {\n")
        sb.append("        float h = ${lit(U[n - 1] - U[n - 2])};\n")
        sb.append("        float t = (u - ${lit(U[n - 2])}) / h;\n")
        sb.append("        float t2 = t * t;\n        float t3 = t2 * t;\n")
        sb.append("        return (2.0 * t3 - 3.0 * t2 + 1.0) * ${lit(V[n - 2])} + (t3 - 2.0 * t2 + t) * h * ${lit(M0_END)}\n")
        sb.append("            + (-2.0 * t3 + 3.0 * t2) * ${lit(V[n - 1])} + (t3 - t2) * h * ${lit(M1_END)};\n    }\n")
        for (i in n - 3 downTo 0) {
            val m = (V[i + 1] - V[i]) / (U[i + 1] - U[i])
            sb.append("    if (u >= ${lit(U[i])}) {\n        return ${lit(V[i])} + ${lit(m)} * (u - ${lit(U[i])});\n    }\n")
        }
        sb.append("    return ${lit(V[0])};\n}")
        sb.toString()
    }
}

/**
 * The resting-corner source map as SkSL, shared verbatim by the panel, content and container shaders so
 * the three cannot disagree on where a corner pixel samples from. `uRestMap` selects the table; every
 * style defaults to the legacy one.
 */
internal val GLASS_REST_SOURCE_SKSL: String = """
// The lens of chrome at rest, at the curved parts of an outline only (FINDINGS 18): the shipping map. A
// pixel at depth u = d/W inside a rounded end shows the ring 0.55 W (0.33 R) inside, the same ring for the
// whole of 0.14-0.38 W, then eases to identity by 0.76 W; the outer 0.14 W shows slightly deeper content
// again. A straight run shows none of it (the dock's top edge leaves a boundary 30 px inside in place).
float restSourceLegacy(float u) {
    if (u >= 0.76) {
        return u;
    } else if (u >= 0.62) {
        return 0.64 + 0.857 * (u - 0.62);
    } else if (u >= 0.38) {
        return 0.55 + 0.375 * (u - 0.38);
    } else if (u >= 0.14) {
        return 0.55;
    }
    return 0.55 + 0.93 * (0.14 - u);
}

// The measured resting-corner map (GlassRestMap.Measured; GlassRestMapTable is the authority and this text
// is generated from it). Fitted on the Photos toolbar's 72 px ends over 190 measured landmark pairs: the
// source depth falls from 0.876 W at the rim band's inner edge to a minimum of 0.643 W at 0.44 W, rises
// again and meets identity at 0.833 W with slope 1. Below 0.139 W (d < 6 px at R = 72) it is a constant,
// unmeasured extension. At radii other than 72 reference px this is a scale extrapolation.
${GlassRestMapTable.sksl}

float restSource(float u) {
    return (uRestMap > 0.5) ? restSourceMeasured(u) : restSourceLegacy(u);
}
"""
