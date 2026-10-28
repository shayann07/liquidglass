package com.wexpa.liquidglass

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Reference trajectories of the free button ([GlassPullShape.Free] on [GlassInteraction.Pullable], the
 * Phone scene's search button) for the CanvasKit and .NET ports. It follows `Modifier.liquidGlass`'s own
 * steps with the same laws ([glassPressScale], [glassPullTargetScale], [glassMaterialPull],
 * [glassFreeLanding]) and the same [GlassMotion] specs; each animated value restarts from its current
 * value and velocity when its target changes, as `animateFloatAsState` does, at the event's time.
 * Regenerate with -Pliquidglass.exportShaders=true; otherwise the committed fixture must match.
 */
class GlassFreePullPortFixtureTest {
    private val density = 3f
    private val size = 186f
    private val interaction = GlassInteraction.Pullable.copy(pullShape = GlassPullShape.Free, pullLiftShare = 0.65f)

    /** One animated float with animateFloatAsState's retarget rule. */
    private class Anim(private var value0: Float) {
        private var start = 0.0
        private var anim: TargetBasedAnimation<Float, AnimationVector1D>? = null
        var target = value0; private set
        fun value(t: Double): Float = anim?.getValueFromNanos(nanos(t)) ?: value0
        private fun velocity(t: Double): Float = anim?.getVelocityVectorFromNanos(nanos(t))?.value ?: 0f
        private fun nanos(t: Double) = ((t - start).coerceAtLeast(0.0) * 1e9).toLong()
        fun to(t: Double, newTarget: Float, spec: AnimationSpec<Float>) {
            if (newTarget == target && anim != null) return
            val v = value(t); val vel = velocity(t)
            anim = TargetBasedAnimation(spec, Float.VectorConverter, v, newTarget, vel)
            start = t; value0 = v; target = newTarget
        }
    }

    private class Call(val t: Double, val op: String, val x: Float = 0f, val y: Float = 0f)

    private fun scenario(name: String, end: Double, calls: List<Call>): String {
        val growth = 8f * density
        val balloon = glassPressScale(size, growth, interaction.pressScale)
        val limit = size * balloon * interaction.pullLimit
        val press = Anim(0f); val glow = Anim(0f); val px = Anim(0f); val py = Anim(0f)
        var anchor = Float.NaN; var moved = false
        val frames = StringBuilder()
        var next = 0
        for (i in 1..(end * 60).toInt()) {
            val tf = i / 60.0
            while (next < calls.size && calls[next].t <= tf) {
                val c = calls[next++]
                when (c.op) {
                    "down" -> {
                        moved = false
                        glow.to(c.t, 1f, GlassMotion.GlowIn)
                        press.to(c.t, 1f, GlassMotion.BalloonDown)
                        px.to(c.t, 0f, GlassMotion.PullFollow); py.to(c.t, 0f, GlassMotion.PullFollow)
                    }
                    "move" -> {
                        val len = hypot(c.x, c.y)
                        if (!moved && len > 4f * density) { moved = true; glow.to(c.t, interaction.pullLiftShare, GlassMotion.FreeGlow) }
                        if (len > 4f * density) anchor = if (interaction.uprightPull)
                            kotlin.math.round(atan2(c.y, c.x) * (180f / kotlin.math.PI.toFloat()) / 90f) * 90f
                        else
                            atan2(c.y, c.x) * (180f / kotlin.math.PI.toFloat())
                        val k = glassPullTargetScale(len, interaction.pullFollow, limit)
                        px.to(c.t, c.x * k, GlassMotion.PullFollow); py.to(c.t, c.y * k, GlassMotion.PullFollow)
                    }
                    "up" -> {
                        glow.to(c.t, 0f, GlassMotion.FreeGlow)
                        press.to(c.t, 0f, GlassMotion.PressUp)
                        // GlassInteraction.pullReleaseDampingX, as LiquidGlass applies it to x.
                        val releaseX = interaction.pullReleaseDampingX.takeIf { it.isFinite() }?.let {
                            androidx.compose.animation.core.spring<Float>(dampingRatio = it.coerceIn(.05f, 2f), stiffness = 264f)
                        } ?: GlassMotion.FreeRelease
                        px.to(c.t, 0f, releaseX); py.to(c.t, 0f, GlassMotion.FreeRelease)
                    }
                }
            }
            val amount = press.value(tf)
            val pressScale = 1f + (balloon - 1f) * amount
            val raw = glassMaterialPull(size, size, size / 2f, density, pressScale, pressScale,
                px.value(tf), py.value(tf), limit, interaction)
            val a = anchor * (kotlin.math.PI.toFloat() / 180f)
            val d = glassFreeLanding(raw, anchor, hypot(size / 2f * cos(a), size / 2f * sin(a)))
            if (frames.isNotEmpty()) frames.append(",")
            frames.append("[%.9f,%.6f,%.6f,%.6f,%.6f,%.6f,%.4f,%.4f,%.4f]".format(java.util.Locale.ROOT,
                tf, glow.value(tf), pressScale, d.along, d.across, d.angleDegrees, d.translationX, d.translationY,
                px.value(tf)))
        }
        val callsJson = calls.joinToString(",") { "[%.9f,\"%s\",%.3f,%.3f]".format(java.util.Locale.ROOT, it.t, it.op, it.x, it.y) }
        return "{\"name\":\"$name\",\"end\":$end,\"calls\":[$callsJson],\n\"frames\":[$frames]}"
    }

    private fun scenarios(): List<String> {
        val list = mutableListOf<String>()
        // Pulled straight up 600 px over 1.2 s (IMG_6756's long pulls), held, released.
        run {
            val calls = mutableListOf(Call(.1, "down"))
            for (k in 1..72) calls += Call(.1 + k / 60.0, "move", 0f, -600f * k / 72f)
            calls += Call(1.8, "up")
            list += scenario("pull-up", 2.6, calls)
        }
        // A short pull toward the bar (left), released at once.
        run {
            val calls = mutableListOf(Call(.1, "down"))
            for (k in 1..24) calls += Call(.1 + k / 60.0, "move", -120f * k / 24f, 3f * k / 24f)
            calls += Call(.8, "up")
            list += scenario("pull-left", 1.6, calls)
        }
        list += scenario("tap", 1.0, listOf(Call(.1, "down"), Call(.22, "up")))
        list += scenario("still-press", 1.8, listOf(Call(.1, "down"), Call(1.0, "up")))
        return list
    }

    @Test fun freeButtonTrajectoriesForPorts() {
        val json = "{\"button\":{\"size\":186,\"density\":3,\"pressScale\":1.26,\"pressGrowth\":8,\"pressLift\":0.2," +
            "\"pullFollow\":${interaction.pullFollow},\"pullElongation\":${interaction.pullElongation},\"pullWidthRatio\":1,\"pullLimit\":0.5,\"pullLiftShare\":${interaction.pullLiftShare},\"pullReleaseDampingX\":${interaction.pullReleaseDampingX}," +
            "\"uprightPull\":${interaction.uprightPull}},\n" +
            "\"frameFields\":[\"t\",\"glow\",\"pressScale\",\"along\",\"across\",\"angle\",\"tx\",\"ty\",\"pullX\"],\n" +
            "\"scenarios\":[\n" + scenarios().joinToString(",\n") + "]}\n"
        val file = File("../ports/skia/fixtures/free-button.json")
        if (System.getProperty("liquidglass.exportShaders") == "true") { file.parentFile.mkdirs(); file.writeText(json) }
        assertEquals(json.replace("\r\n", "\n"), file.readText().replace("\r\n", "\n"),
            "Regenerate the free-button fixture with -Pliquidglass.exportShaders=true")
    }
}
