package com.wexpa.liquidglass

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The harness half of the controlled padding diagnostic (Astra round 7).
 *
 * The same four fixture geometries the sample's `PathFixture` draws — a closed-form capsule, the
 * same capsule as a sampled path, a held-profile path capsule and a fused capsule with a sibling
 * to its right — at the same sizes in px, built from the same [GlassStyle] at the Xiaomi's
 * density, at render scales 1, 0.5 and 0.25 and under the same test-only recorded-pad overrides.
 * Everything material is replaced by flat colour times the production coverage
 * (`debugCoverage = 1`), so the silhouette read here is the coverage function's, not the rim
 * lighting's or the interior colour's: a 50 % luma crossing can move when the material changes,
 * a 50 % coverage crossing cannot.
 *
 * This is the reference the device numbers are compared against. It exercises the geometry, the
 * field and `uPad`; it cannot exercise the Compose recording and draw-back translate, which only
 * run on Android — which is exactly why the device capture exists.
 */
@OptIn(LiquidGlassDiagnosticApi::class)
class GlassCoverageDiagnosticTest {

    private val NL = System.lineSeparator()

    /** The Xiaomi Redmi Note 13 the device half is captured on: 1080x2400 at 440 dpi. */
    private val density = Density(2.75f)

    /** The fixture's own style: wide kernel off, blur radius = padPx px, bevel 6 px (sample/CalibrationActivity). */
    private val base = GlassStyle.inApp(dark = true, tintAmount = 50f).copy(
        wideKernel = 0.dp, fineShare = 1f,
        blurRadius = with(density) { 22.5f.toDp() }, bevel = with(density) { 6f.toDp() },
    )
    private val held = base.copy(profile = GlassProfile.Held)

    private val h = 60
    private val fusedW = 120
    private val fuseGap = 20
    private val sibW = 60

    private class Case(
        val name: String,
        val width: Int,
        val style: GlassStyle,
        val pathBacked: Boolean,
        val fuse: GlassFuse?,
    )

    private fun cases(): List<Case> = listOf(
        Case("closed", 200, base, pathBacked = false, fuse = null),
        Case("path", 200, base, pathBacked = true, fuse = null),
        Case("held_path", 200, held, pathBacked = true, fuse = null),
        Case(
            "fused_closed", fusedW, base, pathBacked = false,
            fuse = with(density) {
                GlassFuse(
                    bounds = DpRect(
                        left = (fusedW + fuseGap).toFloat().toDp(), top = 0f.toDp(),
                        right = (fusedW + fuseGap + sibW).toFloat().toDp(), bottom = h.toFloat().toDp(),
                    ),
                    cornerRadius = 20f.toDp(), smoothing = 16f.toDp(),
                )
            },
        ),
    )

    private fun capsule(width: Int): Path =
        Path().apply { addRoundRect(RoundRect(0f, 0f, width.toFloat(), h.toFloat(), CornerRadius(h / 2f, h / 2f))) }

    private fun alpha(p: Int) = (p ushr 24) and 0xFF

    /** Sub-pixel position where coverage crosses one half along a scan; null when it never does. */
    private fun crossing(values: IntArray): Double? {
        for (i in 1 until values.size) {
            val a = values[i - 1]; val b = values[i]
            if (a < 128 && b >= 128) return (i - 1) + (128.0 - a) / (b - a)
        }
        return null
    }

    private class Edges(
        val top: Double, val bottom: Double, val left: Double, val right: Double,
        val rightClipped: Boolean, val unionInsidePad: Boolean,
    )

    /**
     * The four 50 % coverage crossings of a rendered layer, returned in full-resolution panel
     * coordinates (pad removed, scale undone), so every scale is reported on one ruler.
     */
    private fun edges(px: IntArray, layerW: Int, layerH: Int, padLayer: Int, w: Int, hh: Int, s: Float, unionInsidePad: Boolean): Edges {
        val cx = padLayer + w / 2
        val cy = padLayer + hh / 2
        val down = IntArray(cy + 1) { alpha(px[it * layerW + cx]) }
        val up = IntArray(layerH - cy) { alpha(px[(layerH - 1 - it) * layerW + cx]) }
        val right = IntArray(cx + 1) { alpha(px[cy * layerW + it]) }
        val leftFromRight = IntArray(layerW - cx) { alpha(px[cy * layerW + (layerW - 1 - it)]) }
        val t = crossing(down) ?: error("no top crossing")
        val b = crossing(up) ?: error("no bottom crossing")
        val l = crossing(right) ?: error("no left crossing")
        // The union of a fused pair can reach past the recorded pad, and then the layer's own edge
        // cuts it: that is not a silhouette, so it is reported as clipped rather than as a reading.
        val clipped = alpha(px[cy * layerW + layerW - 1]) >= 128
        val r = crossing(leftFromRight)
        // Sample index i is the centre of layer pixel i, at continuous coordinate i + 0.5; without
        // that half pixel every edge reads 0.5 layer px small, which at quarter scale is 2 full px.
        return Edges(
            top = (t + 0.5 - padLayer) / s,
            bottom = (layerH - (b + 0.5) - padLayer) / s,
            left = (l + 0.5 - padLayer) / s,
            right = if (r == null) Double.NaN else (layerW - (r + 0.5) - padLayer) / s,
            rightClipped = clipped,
            unionInsidePad = unionInsidePad,
        )
    }

    private fun render(case: Case, rs: Float, override: Float): Edges {
        val size = Size(case.width.toFloat(), h.toFloat())
        val radii = FloatArray(4) { h / 2f }
        LiquidGlassDiagnostics.recordPadOverridePx = override
        // recordPad is the contract under test: with the override in force it is
        // ceil(override * rs) / rs for every case, held and fused included.
        val pad = case.style.recordPad(radii, size, case.fuse, density, rs)
        val band = case.style.bandPx(radii, size, density)
        val fieldBand = with(density) { if (case.style.refractionBand.isSpecified) case.style.refractionBand.toPx() else 0f }
        val layerFullW = (size.width + pad * 2f).roundToInt()
        val layerFullH = (size.height + pad * 2f).roundToInt()
        val field = if (case.pathBacked) {
            val full = buildPathField(capsule(case.width), layerFullW, layerFullH, pad, fieldBand)!!
            // As GlassUniforms.scaledBy moves a field into the reduced layer: range * s, scale / s.
            if (rs >= 1f) full else GlassPathField(full.bitmap, full.range * rs, full.scale / rs, full.layerWidth, full.layerHeight)
        } else {
            null
        }
        val fusePx = case.fuse?.toPx(size, density)
        val w = (case.width * rs).toInt()
        val hh = (h * rs).toInt()
        val padLayer = (pad * rs).toInt()
        val px = GlassRender.render(
            width = w, height = hh, pad = padLayer,
            refractBand = band * rs, refractDepth = with(density) { case.style.refractionDepth.toPx() } * rs,
            radii = FloatArray(4) { radii[it] * rs },
            bevel = with(density) { case.style.bevel.toPx() } * rs,
            profile = case.style.profile.uniform, formation = 1f, heldLens = case.style.heldLens,
            field = field,
            fuse = fusePx?.let { floatArrayOf(it[0] * rs, it[1] * rs, it[2] * rs, it[3] * rs) },
            fuseRadius = (fusePx?.get(4) ?: 0f) * rs,
            fuseStrength = (fusePx?.get(5) ?: 0f) * rs,
            debugCoverage = 1f,
            backdrop = { _, _ -> 0x000000 },
        )
        // The fused union reaches gap + sibling width past the panel's right edge; a pad smaller
        // than that leaves the sibling outside the recorded layer entirely, and then the rightmost
        // crossing is the capsule's own edge, not the union's.
        val unionInsidePad = case.fuse == null || pad >= (fuseGap + sibW).toFloat()
        return edges(px, w + 2 * padLayer, hh + 2 * padLayer, padLayer, w, hh, rs, unionInsidePad)
    }

    @AfterTest
    fun clearDiagnostics() {
        LiquidGlassDiagnostics.reset()
    }

    @Test
    fun coverageSilhouetteIsPadInvariantAcrossScales() {
        val cells = listOf(1f to 23f, 1f to 24f, 0.5f to 22f, 0.5f to 24f, 0.25f to 20f, 0.25f to 24f, 1f to 100f, 0.5f to 100f)
        val json = StringBuilder("{\n")
        val report = StringBuilder()
        var worst = 0.0
        var worstWhere = ""
        val byCase = mutableMapOf<String, MutableList<Edges>>()
        for (case in cases()) {
            for ((rs, ovr) in cells) {
                val e = render(case, rs, ovr)
                byCase.getOrPut(case.name) { mutableListOf() }.add(e)
                // The declared box in panel coordinates: 0..width by 0..60, and for the fused case
                // the union reaches the sibling's right edge at width + gap + sibling width.
                val declaredRight = if (case.fuse != null && e.unionInsidePad) (fusedW + fuseGap + sibW).toDouble() else case.width.toDouble()
                val dt = e.top - 0.0; val db = e.bottom - h.toDouble(); val dl = e.left - 0.0
                val dr = if (e.rightClipped || e.right.isNaN()) Double.NaN else e.right - declaredRight
                val cell = "${case.name}_rs${rs}_pad${ovr.toInt()}"
                json.append(" \"$cell\": {\"d_top\": ${"%.3f".format(dt)}, \"d_bottom\": ${"%.3f".format(db)}, \"d_left\": ${"%.3f".format(dl)}, ")
                json.append("\"d_right\": ${if (dr.isNaN()) "null" else "%.3f".format(dr)}, \"right_clipped_by_pad\": ${e.rightClipped}, \"union_inside_pad\": ${e.unionInsidePad}, \"declared_right\": $declaredRight, ")
                json.append("\"centre_dy\": ${"%.3f".format((e.top + e.bottom) / 2 - h / 2.0)}, \"height\": ${"%.3f".format(e.bottom - e.top)}},\n")
                report.append("$cell: top ${"%.2f".format(dt)} bottom ${"%.2f".format(db)} left ${"%.2f".format(dl)}")
                report.append(if (dr.isNaN()) " right clipped\n" else " right ${"%.2f".format(dr)}\n")
                for ((label, v) in listOf("top" to dt, "bottom" to db, "left" to dl) + (if (dr.isNaN()) emptyList() else listOf("right" to dr))) {
                    if (abs(v) > worst) { worst = abs(v); worstWhere = "$cell $label" }
                }
            }
        }
        // The path-backed capsule against the closed form, cell for cell.
        val closed = byCase["closed"]!!; val path = byCase["path"]!!
        var worstPathClosed = 0.0
        for (i in closed.indices) {
            worstPathClosed = maxOf(
                worstPathClosed,
                abs(path[i].top - closed[i].top), abs(path[i].bottom - closed[i].bottom), abs(path[i].left - closed[i].left),
            )
        }
        json.append(" \"path_minus_closed_worst\": ${"%.3f".format(worstPathClosed)},\n")
        json.append(" \"worst_vs_declared\": ${"%.3f".format(worst)}, \"worst_where\": \"$worstWhere\",\n")
        json.append(" \"provenance\": {\"harness\": \"GlassRender + debugCoverage=1\", \"density\": 2.75, \"device_counterpart\": \"fixture_r7_xiaomi\"}\n}\n")
        // The research record is written only when asked for:
        // -Dliquidglass.coverageRecord=research/analysis/inverse-v1/E1_fixture_path_r7_jvm.json
        System.getProperty("liquidglass.coverageRecord")?.let { File(it).writeText(json.toString()) }
        println(report)
        println("worst vs declared $worst at $worstWhere; worst path - closed $worstPathClosed")
        // Pad invariance is a pairwise property: two edges can each sit inside the gate against their
        // declared position and still differ from each other by more than it, so every eligible edge is
        // compared across pads at one scale. A fused right edge enters only where both pads contain the
        // union and neither reading is cut by the layer, which is the rule the device comparison uses.
        val padPairs = listOf(
            Triple(1f, 24f, 23f), Triple(1f, 24f, 100f),
            Triple(0.5f, 24f, 22f), Triple(0.5f, 24f, 100f),
            Triple(0.25f, 24f, 20f),
        )
        val pairReport = StringBuilder()
        var worstPair = 0.0
        var worstPairWhere = ""
        for (case in cases()) {
            for ((rs, refPad, otherPad) in padPairs) {
                val a = render(case, rs, refPad)
                val b = render(case, rs, otherPad)
                val edgePairs = mutableListOf("top" to (a.top to b.top), "bottom" to (a.bottom to b.bottom), "left" to (a.left to b.left))
                if (!a.rightClipped && !b.rightClipped && !a.right.isNaN() && !b.right.isNaN() && a.unionInsidePad == b.unionInsidePad) {
                    edgePairs += "right" to (a.right to b.right)
                }
                for ((label, pair) in edgePairs) {
                    val d = abs(pair.first - pair.second)
                    pairReport.append("${case.name} rs$rs pad${otherPad.toInt()} vs pad${refPad.toInt()} $label: ${"%.3f".format(d)}")
                    pairReport.append(NL)
                    if (d > worstPair) { worstPair = d; worstPairWhere = "${case.name} rs$rs pad${otherPad.toInt()} vs ${refPad.toInt()} $label" }
                }
            }
        }
        println("worst pairwise pad difference $worstPair at $worstPairWhere")
        assertTrue(worst <= 0.75, "coverage silhouette vs the declared box (gate 0.75 px):\n$report")
        assertTrue(worstPathClosed <= 0.75, "path-backed vs closed-form coverage silhouette:\n$report")
        assertTrue(worstPair <= 0.75, "coverage silhouette is not pad-invariant (gate 0.75 px), worst $worstPairWhere:" + NL + pairReport)
    }

    @Test
    fun diagnosticsOffLeaveTheProductionPathUntouched() {
        // The switches default to off and reset() restores them, so the recorded pad and the coverage
        // uniform are the style's own whenever no rig is running.
        LiquidGlassDiagnostics.reset()
        val size = Size(200f, 60f)
        val radii = FloatArray(4) { 30f }
        val production = base.recordPad(radii, size, null, density, 1f)
        val overridden = LiquidGlassDiagnostics.withDiagnostics(recordPadOverridePx = 100f, coverageOnly = true) {
            base.recordPad(radii, size, null, density, 1f)
        }
        assertTrue(overridden == 100f, "the override should set the recorded pad, got $overridden")
        assertTrue(!LiquidGlassDiagnostics.coverageOnly && LiquidGlassDiagnostics.recordPadOverridePx == null, "withDiagnostics must reset")
        assertTrue(base.recordPad(radii, size, null, density, 1f) == production, "the production pad must come back unchanged")
        assertTrue(production == 23f, "the fixture style's own recorded pad is ceil(22.5) = 23, got $production")
    }
}
