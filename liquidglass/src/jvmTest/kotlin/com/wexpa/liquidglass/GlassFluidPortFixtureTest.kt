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
 * Reference trajectories of [GlassInteraction.Fluid] on controls that are not the search button, for the CanvasKit,
 * web and .NET free buttons (#196): a 100 dp circle (the size blend), a 120 x 48 dp pill (80 to 160 dp) and a 200 x
 * 150 dp card (anchored). `GlassFreePullPortFixtureTest` keeps the 62 dp search button, which must not change. Each
 * frame follows `Modifier.liquidGlass`'s own steps (press scale per axis, [glassMaterialPull], landing, upright,
 * [glassFreeSizeBlend]); a cancelled press returns on [GlassMotion.CalmUp] with no landing. Regenerate with
 * -Pliquidglass.exportShaders=true.
 */
class GlassFluidPortFixtureTest {
    private val density = 3f
    private val interaction = GlassInteraction.Fluid

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
    private class Control(val name: String, val w: Float, val h: Float, val radius: Float)

    private val controls = listOf(
        Control("circle-100dp", 300f, 300f, 150f),
        Control("pill-120x48dp", 360f, 144f, 72f),
        Control("card-200x150dp", 600f, 450f, 72f),
    )

    private fun scenario(c: Control, name: String, end: Double, calls: List<Call>): String {
        val mix = glassSizeMix(c.w, c.h, density)
        val growth = 8f * density + (2f * density - 8f * density) * mix
        val balloonX = glassPressScale(c.w, growth, interaction.pressScale)
        val balloonY = glassPressScale(c.h, growth, interaction.pressScale)
        val limit = minOf(c.w * balloonX, c.h * balloonY) * interaction.pullLimit
        val press = Anim(0f); val glow = Anim(0f); val px = Anim(0f); val py = Anim(0f)
        var anchor = Float.NaN; var moved = false
        val frames = StringBuilder()
        var next = 0
        for (i in 1..(end * 60).toInt()) {
            val tf = i / 60.0
            while (next < calls.size && calls[next].t <= tf) {
                val call = calls[next++]
                when (call.op) {
                    "down" -> {
                        moved = false
                        glow.to(call.t, 1f, GlassMotion.GlowIn)
                        press.to(call.t, 1f, if (mix >= 1f) GlassMotion.CalmDown else GlassMotion.BalloonDown)
                        px.to(call.t, 0f, GlassMotion.PullFollow); py.to(call.t, 0f, GlassMotion.PullFollow)
                    }
                    "move" -> {
                        val len = hypot(call.x, call.y)
                        if (!moved && len > 4f * density) { moved = true; glow.to(call.t, interaction.pullLiftShare, GlassMotion.FreeGlow) }
                        if (len > 4f * density) anchor = atan2(call.y, call.x) * (180f / kotlin.math.PI.toFloat())
                        val k = glassPullTargetScale(len, interaction.pullFollow, limit)
                        px.to(call.t, call.x * k, GlassMotion.PullFollow); py.to(call.t, call.y * k, GlassMotion.PullFollow)
                    }
                    "up" -> {
                        glow.to(call.t, 0f, GlassMotion.FreeGlow)
                        press.to(call.t, 0f, GlassMotion.PressUp)
                        val releaseX = interaction.pullReleaseDampingX.takeIf { it.isFinite() }?.let {
                            androidx.compose.animation.core.spring<Float>(dampingRatio = it.coerceIn(.05f, 2f), stiffness = 264f)
                        } ?: GlassMotion.FreeRelease
                        px.to(call.t, 0f, releaseX); py.to(call.t, 0f, GlassMotion.FreeRelease)
                    }
                    "cancel" -> {
                        anchor = Float.NaN
                        glow.to(call.t, 0f, GlassMotion.FreeGlow)
                        press.to(call.t, 0f, GlassMotion.CalmUp)
                        px.to(call.t, 0f, GlassMotion.CalmUp); py.to(call.t, 0f, GlassMotion.CalmUp)
                    }
                }
            }
            val amount = press.value(tf)
            val sx = 1f + (balloonX - 1f) * amount
            val sy = 1f + (balloonY - 1f) * amount
            val raw = glassMaterialPull(c.w, c.h, c.radius, density, sx, sy, px.value(tf), py.value(tf), limit, interaction)
            val a = anchor * (kotlin.math.PI.toFloat() / 180f)
            val free = glassUprightPull(glassFreeLanding(raw, anchor, hypot(c.w / 2f * cos(a), c.h / 2f * sin(a))))
            val d = glassFreeSizeBlend(free, interaction, c.w, c.h, density, sx, sy, c.radius, px.value(tf), py.value(tf))
            if (frames.isNotEmpty()) frames.append(",")
            frames.append("[%.9f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.4f,%.4f,%.4f]".format(java.util.Locale.ROOT,
                tf, glow.value(tf), sx, sy, d.along, d.across, d.angleDegrees, d.translationX, d.translationY, px.value(tf)))
        }
        val callsJson = calls.joinToString(",") { "[%.9f,\"%s\",%.3f,%.3f]".format(java.util.Locale.ROOT, it.t, it.op, it.x, it.y) }
        return "{\"name\":\"${c.name}/$name\",\"control\":{\"width\":${c.w},\"height\":${c.h},\"radius\":${c.radius}},\"end\":$end,\"calls\":[$callsJson],\n\"frames\":[$frames]}"
    }

    private fun scenarios(): List<String> {
        val list = mutableListOf<String>()
        for (c in controls) {
            run {
                val calls = mutableListOf(Call(.1, "down"))
                for (k in 1..60) calls += Call(.1 + k / 60.0, "move", 0f, -400f * k / 60f)
                calls += Call(1.5, "up")
                list += scenario(c, "pull-up", 2.4, calls)
            }
            run {
                val angle = 35f * (kotlin.math.PI.toFloat() / 180f)
                val calls = mutableListOf(Call(.1, "down"))
                for (k in 1..30) calls += Call(.1 + k / 60.0, "move", 260f * cos(angle) * k / 30f, -260f * sin(angle) * k / 30f)
                calls += Call(.9, "up")
                list += scenario(c, "pull-diagonal", 1.8, calls)
            }
            list += scenario(c, "still-press", 1.6, listOf(Call(.1, "down"), Call(.9, "up")))
            run {
                val calls = mutableListOf(Call(.1, "down"))
                for (k in 1..20) calls += Call(.1 + k / 60.0, "move", 0f, -240f * k / 20f)
                calls += Call(.6, "cancel")
                list += scenario(c, "scroll-cancel", 1.6, calls)
            }
        }
        return list
    }

    @Test fun fluidTrajectoriesForPorts() {
        val json = "{\"button\":{\"density\":3,\"pressScale\":1.26,\"pressGrowth\":8,\"pressLift\":0.2," +
            "\"pullFollow\":${interaction.pullFollow},\"pullElongation\":${interaction.pullElongation},\"pullWidthRatio\":1,\"pullLimit\":0.5,\"pullLiftShare\":${interaction.pullLiftShare},\"pullReleaseDampingX\":${interaction.pullReleaseDampingX}},\n" +
            "\"frameFields\":[\"t\",\"glow\",\"pressScale\",\"pressScaleY\",\"along\",\"across\",\"angle\",\"tx\",\"ty\",\"pullX\"],\n" +
            "\"scenarios\":[\n" + scenarios().joinToString(",\n") + "]}\n"
        val file = File("../ports/skia/fixtures/free-button-sizes.json")
        if (System.getProperty("liquidglass.exportShaders") == "true") { file.parentFile.mkdirs(); file.writeText(json) }
        assertEquals(json.replace("\r\n", "\n"), file.readText().replace("\r\n", "\n"),
            "Regenerate the size fixture with -Pliquidglass.exportShaders=true")
    }
}
