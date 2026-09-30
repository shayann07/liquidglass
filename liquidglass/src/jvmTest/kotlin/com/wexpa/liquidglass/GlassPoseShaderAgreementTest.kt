package com.wexpa.liquidglass

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Surface
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The GPU's pose geometry against [GlassPoseFrame]'s, read back out of a real Skia render.
 *
 * The brief's section 6.3 requires **one** shared geometry source across the panel, content and
 * aperture passes, and says not to repeat the old normal discrepancy between them. This is the
 * check that the shared SkSL actually evaluates to the same thing the CPU does: a probe shader
 * that includes the production `GLASS_POSE_SKSL` verbatim and encodes `poseQuery`'s four outputs
 * into colour channels, compared against the CPU query at the same points.
 *
 * Nothing here reimplements the geometry. The comparison is production SkSL against production
 * Kotlin; if they drift apart, this fails.
 */
class GlassPoseShaderAgreementTest {

    private val width = 240
    private val height = 160

    /**
     * Distance, normal and curvature packed into RGBA. Distance and curvature are scaled and
     * biased into 0..1 so an 8-bit readback still resolves them to a useful precision, and the
     * decode below undoes exactly that.
     */
    private val probeSource = """
uniform float2  uSize;
uniform float4  uPoseA;
uniform float4  uPoseAInv;
uniform float4  uPoseC;
uniform float4  uPoseD;
uniform float   uDistanceScale;
uniform float   uChannel;      // 0 packs (distance, normal); 1 packs the curvature

$GLASS_POSE_SKSL

half4 main(float2 fragCoord) {
    float2 p = fragCoord - uSize * 0.5;
    float4 q = poseQuery(p, uPoseA, uPoseAInv, uPoseC, uPoseD);
    // Alpha stays 1. The readback surface is premultiplied, so an alpha below 1 would have its
    // colour divided back out on an unpremultiplied read and destroy the very precision this
    // test is measuring - which is exactly what an earlier version of this probe did.
    if (uChannel > 0.5) {
        // Curvature is about 1 / (radius * stretch); a x10 scale spans 0..1 without saturating.
        float k = clamp(q.w * 10.0, 0.0, 1.0);
        return half4(half(k), 0.0, 0.0, 1.0);
    }
    float d = clamp(q.x / uDistanceScale * 0.5 + 0.5, 0.0, 1.0);
    float nx = clamp(q.y * 0.5 + 0.5, 0.0, 1.0);
    float ny = clamp(q.z * 0.5 + 0.5, 0.0, 1.0);
    return half4(half(d), half(nx), half(ny), 1.0);
}
"""

    private val effect: RuntimeEffect by lazy { RuntimeEffect.makeForShader(probeSource) }

    private fun render(frame: GlassPoseFrame, distanceScale: Float, channel: Float = 0f): IntArray {
        val b = RuntimeShaderBuilder(effect)
        b.uniform("uSize", width.toFloat(), height.toFloat())
        b.uniform("uPoseA", frame.a[0], frame.a[1], frame.a[2], frame.a[3])
        b.uniform("uPoseAInv", frame.aInv[0], frame.aInv[1], frame.aInv[2], frame.aInv[3])
        b.uniform("uPoseC", frame.cx, frame.cy, frame.bx, frame.by)
        b.uniform("uPoseD", frame.spine, frame.radius, frame.scaleX, frame.band())
        b.uniform("uDistanceScale", distanceScale)
        b.uniform("uChannel", channel)
        val surface = Surface.makeRasterN32Premul(width, height)
        surface.canvas.clear(0)
        surface.canvas.drawPaint(Paint().apply { shader = b.makeShader() })
        val out = Bitmap()
        out.allocPixels(ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL))
        check(surface.readPixels(out, 0, 0)) { "readPixels failed" }
        val bytes = out.readPixels()!!
        return IntArray(width * height) { i ->
            val bl = bytes[i * 4].toInt() and 0xFF
            val g = bytes[i * 4 + 1].toInt() and 0xFF
            val r = bytes[i * 4 + 2].toInt() and 0xFF
            val a = bytes[i * 4 + 3].toInt() and 0xFF
            (a shl 24) or (r shl 16) or (g shl 8) or bl
        }
    }

    private fun poses() = listOf(
        "identity" to GlassPose(),
        "pressure" to GlassPose().apply { p = 0.16f },
        "horizontal strain" to GlassPose().apply { u = 0.24f },
        "vertical strain" to GlassPose().apply { u = -0.24f },
        "diagonal strain" to GlassPose().apply { v = 0.22f },
        "arbitrary strain" to GlassPose().apply { u = 0.15f; v = -0.17f },
        "taper" to GlassPose().apply { kx = 0.15f; ky = 0.08f },
        "accommodation" to GlassPose().apply { acc = 30f },
        "everything" to GlassPose().apply { p = 0.13f; u = 0.16f; v = 0.12f; kx = 0.10f; ky = -0.08f; acc = 20f },
        "long spine" to GlassPose().apply { dx = 70f },
        "rotated spine" to GlassPose().apply { dx = 30f; dy = 36f },
        "spine across" to GlassPose().apply { dx = -34f },
        "rotated everything" to GlassPose().apply { p = 0.1f; dx = 20f; dy = -40f; kx = 0.07f; acc = 10f },
    )

    @Test
    fun theSharedPoseShaderAgreesWithTheCpuQuery() {
        val reference = GlassReference(a = 44f, r = 26f)
        val distanceScale = 120f
        var worstDistance = 0f
        var worstNormal = 0f
        var worstCurvature = 0f
        var worstAt = ""
        var sampled = 0
        val q = GlassPoseQuery()
        for ((name, pose) in poses()) {
            val frame = GlassPoseFrame().apply { update(reference, pose) }
            val pixels = render(frame, distanceScale)
            val curvaturePixels = render(frame, distanceScale, channel = 1f)
            for (py in 3 until height - 3 step 2) {
                for (px in 3 until width - 3 step 2) {
                    val x = px + 0.5f - width / 2f
                    val y = py + 0.5f - height / 2f
                    frame.query(x, y, q)
                    // Only score where the geometry is meaningful: inside the body and within one
                    // band of it. Far outside, the distance saturates the 8-bit encoding and the
                    // normal is not used by any pass.
                    if (q.coverageDistance > frame.band() * 1.5f) continue
                    if (q.coverageDistance < -frame.radius * frame.minStretch * 0.9f) continue
                    sampled++
                    val p = pixels[py * width + px]
                    val gd = ((p ushr 16 and 0xFF) / 255f - 0.5f) * 2f * distanceScale
                    val gnx = ((p ushr 8 and 0xFF) / 255f - 0.5f) * 2f
                    val gny = ((p and 0xFF) / 255f - 0.5f) * 2f
                    val gk = (curvaturePixels[py * width + px] ushr 16 and 0xFF) / 255f / 10f
                    val dd = abs(gd - q.coverageDistance)
                    // The 8-bit encoding alone costs distanceScale / 255 ~ 0.47 px, so the gate
                    // is that plus a small margin, not an assertion of exactness.
                    if (dd > worstDistance) { worstDistance = dd; worstAt = "$name at ($px,$py)" }
                    val nd = max(abs(gnx - q.normalX), abs(gny - q.normalY))
                    worstNormal = max(worstNormal, nd)
                    worstCurvature = max(worstCurvature, abs(gk - q.curvature))
                }
            }
        }
        println(
            "POSE-AGREEMENT sampled=$sampled worstDistance=%.3f px worstNormal=%.4f worstCurvature=%.5f at %s"
                .format(worstDistance, worstNormal, worstCurvature, worstAt),
        )
        assertTrue(sampled > 25_000, "only $sampled pixels were in the scored band")
        assertTrue(
            worstDistance <= 1.2f,
            "the shader's coverage distance differs from the CPU's by $worstDistance px at $worstAt",
        )
        assertTrue(
            worstNormal <= 0.02f,
            "the shader's normal differs from the CPU's by $worstNormal at $worstAt",
        )
        assertTrue(
            worstCurvature <= 0.004f,
            "the shader's transported curvature differs by $worstCurvature at $worstAt",
        )
    }

    @Test
    fun theIdentityPoseRendersTheCanonicalCapsuleInTheShader() {
        // A pose body at identity must be indistinguishable from the capsule the previous path
        // drew, or every previously verified optical result would shift under it.
        val reference = GlassReference(a = 50f, r = 30f)
        val frame = GlassPoseFrame().apply { update(reference, GlassPose()) }
        val pixels = render(frame, 120f)
        var worst = 0f
        for (py in 4 until height - 4 step 2) {
            for (px in 4 until width - 4 step 2) {
                val x = px + 0.5f - width / 2f
                val y = py + 0.5f - height / 2f
                val ex = x - x.coerceIn(-reference.a, reference.a)
                val canonical = kotlin.math.sqrt(ex * ex + y * y) - reference.r
                if (canonical > 40f || canonical < -25f) continue
                val gd = ((pixels[py * width + px] ushr 16 and 0xFF) / 255f - 0.5f) * 2f * 120f
                worst = max(worst, abs(gd - canonical))
            }
        }
        assertTrue(worst <= 1.2f, "the identity pose body is off the canonical capsule by $worst px")
    }
}
