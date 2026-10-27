package com.wexpa.liquidglass

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.math.abs

/** Original native material patches, not an inferred hidden page or finger timestamp. */
class GlassPhoneTapOpticsTest {
    /** The pill material these interior patches were calibrated with (V3: black .5, lift -10).
     * The 13 October resting measurement over both recordings reads the inset at .57-.69 of
     * the bar, not this law; the two disagree early in transit (frame6: 14 observed, 22 with
     * the new pill) and in the repeated long tap's pigment return (~50 ms late). Both are open
     * in review/atlas/reference-replay-2026-10-13; these tests keep the previous calibration. */
    private fun patchStyle() = GlassTabBarStyle.Calm().let { it.copy(pill = GlassTabBarStyle.V3().pill) }
    private fun bar() = GlassSelectorBar(834f,186f,21.75f,3,93f,139f,83.75f,90f,150f,150f,3f,800f)

    @Test fun longTapRetainsItsClearInteriorThroughLateTravel() = checkLongInteriors(
        origin = .855f,
        sourceTimes = floatArrayOf(.905f,.946667f,.963333f,.98f,.996667f,1.013333f,1.08f,1.13f),
        bars = intArrayOf(42,42,41,41,41,40,37,36),
        observed = floatArrayOf(42f,45f,45f,45f,45f,44f,38f,29f),
    )

    @Test fun repeatedLongTapRestoresPigmentGradually() = checkLongInteriors(
        // One independent geometry anchor: frame011 centre634px at2.796667s.
        // Inherited source endpoints221..748 and nominal40ms input remain provisional.
        origin = 2.676277683f,
        sourceTimes = floatArrayOf(2.746667f,2.763333f,2.78f,2.796667f,2.813333f,2.83f,
            2.846667f,2.863333f,2.88f,2.896667f,2.913333f,2.93f,2.946667f,2.963333f,
            2.98f,2.996667f,3.013333f),
        bars = intArrayOf(44,44,43,43,42,41,40,39,38,38,37,36,36,35,35,34,34),
        observed = floatArrayOf(45f,46f,46f,46f,45f,44f,43f,41f,39f,36f,33f,30f,
            27f,24f,21f,19f,18f),
    )

    private fun checkLongInteriors(origin: Float, sourceTimes: FloatArray, bars: IntArray,
        observed: FloatArray) {
        val b=bar(); val c=GlassPoseController(GlassPoseSpec.CalmWallLanding)
        c.attach(b,null,null); c.snapToRest(0)
        c.pointerDown(b.centreOf(2),b.centreY,0.0,true); c.retarget(2)
        c.advanceTo(.04f); c.pointerUp(2)
        val style=patchStyle()
        for(i in sourceTimes.indices) {
            c.advanceTo(sourceTimes[i]-origin)
            assertFalse(c.isHeld); assertFalse(c.solverFailed)
            val material=glassSelectorMaterial(style,c.formation,c.transitAppearance)
            val rgb=0xff000000.toInt() or (bars[i] shl 16) or (bars[i] shl 8) or bars[i]
            // Real production shader on the observed uniform bar. This isolates pigment;
            // it does not reconstruct hidden source pixels, full contours or edge transport.
            val pixels=GlassRender.render(width=400,height=260,pad=24,profile=2f,
                heldLens=material.heldLens,tintAlpha=material.tint.alpha,
                tint=Triple(material.tint.red,material.tint.green,material.tint.blue),lift=material.tintLift,
                backdrop={_,_->rgb})
            val rendered=GlassRender.luma(pixels[154*448+224])
            println("LONG_TAP_OPTICS source=${sourceTimes[i]} appearance=${c.transitAppearance} rgb=$rendered")
            assertEquals(observed[i].toDouble(),rendered,6.0,"source${sourceTimes[i]} interior on observed bar")
            assertTrue(c.transitAppearance in 0f..0.95f)
            assertTrue(material.heldLens<=.95f && c.formation<.25f)
        }
    }

    @Test fun movingTapClearsItsInsetWithoutBecomingHeld() {
        val b=bar();val c=GlassPoseController(GlassPoseSpec.CalmWallLanding)
        c.attach(b,null,null);c.snapToRest(2)
        c.pointerDown(b.centreOf(1),b.centreY,0.0,true);c.retarget(1)
        val style=patchStyle()
        val times=floatArrayOf(.0125f,.029167f,.045834f,.0625f,.079167f,.095834f,.1125f,.145834f,.1625f)
        val bars=floatArrayOf(37f,43f,47f,49f,50f,50.5f,51f,51f,51f)
        val observed=floatArrayOf(14f,15f,23f,29f,35f,40f,44.5f,50f,52f)
        var released=false
        for(i in times.indices) {
            if(!released && times[i]>=.04f){c.advanceTo(.04f);c.pointerUp(1);released=true}
            c.advanceTo(times[i])
            assertFalse(c.isHeld);assertFalse(c.solverFailed)
            val material=glassSelectorMaterial(style,c.formation,c.transitAppearance)
            // Interior transfer is also exercised by the real production shader below.
            val expected=bars[i]*(1-material.tint.alpha)+255f*material.tintLift
            println("TAP_OPTICS i=$i f=${c.formation} appearance=${c.transitAppearance} rgb=$expected")
            assertEquals(observed[i],expected,6f,"native frame${i+6} interior on its observed bar")
            assertTrue(c.transitAppearance in 0f..0.95f)
        }
        val material=glassSelectorMaterial(style,c.formation,c.transitAppearance)
        val pixels=GlassRender.render(width=400,height=260,pad=24,profile=2f,
            heldLens=material.heldLens,tintAlpha=material.tint.alpha,
            tint=Triple(material.tint.red,material.tint.green,material.tint.blue),lift=material.tintLift,
            backdrop={_,_->0xff333333.toInt()})
        assertEquals(52.0,GlassRender.luma(pixels[154*448+224]),6.0)
        // The held family zooms its interior; the folding transit band (Measured) does not.
        val zoom=if(material.profile==GlassProfile.Held) material.heldMagnification*material.heldLens else 0f
        val scale=glassTransitInkScale(style,c.formation,c.transitAppearance)/(1f-zoom)
        assertEquals(79f/69f,scale,.025f,"visible icon scale; not a full font/edge fit")
        val sourceRadius=34.5f*glassTransitInkScale(style,c.formation,c.transitAppearance)
        val ink=GlassEndpointRender.render(width=400,height=260,pad=24,refractBand=1f,refractDepth=0f,
            heldLens=if(material.profile==GlassProfile.Held) material.heldLens else 0f,
            heldMagnification=material.heldMagnification,
            backdrop={_,_->0xff333333.toInt()},ink1={x,y->
                val dx=x-224f;val dy=y-154f
                if(dx*dx+dy*dy<=sourceRadius*sourceRadius)0xff149cff.toInt() else 0
            }).ink
        val visible=ink.indices.filter { (ink[it] ushr 24)>=180 }
        assertTrue(visible.isNotEmpty())
        val inkWidth=visible.maxOf {it%448}-visible.minOf {it%448}+1
        val inkHeight=visible.maxOf {it/448}-visible.minOf {it/448}+1
        assertEquals(79f,inkWidth.toFloat(),2f,"source complete icon79..80px")
        assertEquals(79f,inkHeight.toFloat(),2f,"source complete icon79..80px")
        println("TAP_OPTICS renderedInk=$inkWidth x $inkHeight")
        // 13 October: a moving tap shows the lens optics (video1 side-by-side) but never the
        // full held material, and its geometry and pointer ownership stay those of a tap.
        assertTrue(material.heldLens<=.95f)
        assertTrue(c.formation<.25f)
    }

    @Test fun appearanceDoesNotChangeThePoseOrHeldFormation() {
        for(target in listOf(1,2)) {
            val b=bar()
            val current=GlassPoseController(GlassPoseSpec.Calm)
            val previous=GlassPoseController(GlassPoseSpec.Calm.copy(tapAppearanceArea=0f))
            for(c in listOf(current,previous)){c.attach(b,null,null);c.snapToRest(0);c.retarget(target)}
            for(i in 0..960) {
                val t=i/480f
                for(c in listOf(current,previous))c.advanceTo(t)
                val a=GlassPoseExtents().also(current::extents)
                val p=GlassPoseExtents().also(previous::extents)
                assertEquals(previous.centreX,current.centreX,0f)
                assertEquals(previous.centreVelocityX,current.centreVelocityX,0f)
                assertEquals(previous.formation,current.formation,0f)
                assertEquals(p.width,a.width,0f);assertEquals(p.height,a.height,0f)
            }
            assertTrue(current.isIdle);assertEquals(0f,current.transitAppearance)
        }
    }

    @Test fun shortAppearanceIsScaleCadenceAndDirectionIndependent() {
        fun samples(hz:Int,scale:Float,reverse:Boolean):List<Float> {
            val b=GlassSelectorBar(834f*scale,186f*scale,21.75f*scale,3,93f*scale,
                139f*scale,83.75f*scale,90f*scale,150f*scale,150f*scale,3f*scale,800f*scale)
            val c=GlassPoseController(GlassPoseSpec.Calm)
            c.attach(b,null,null);c.snapToRest(if(reverse)2 else 0);c.retarget(1)
            val values=mutableListOf<Float>()
            for(i in 0..hz) {
                c.advanceFrameTo(i.toFloat()/hz)
                assertTrue(c.transitAppearance in 0f..0.95f)
                if(i%(hz/30)==0)values+=c.transitAppearance
            }
            return values
        }
        val reference=samples(120,1f,false)
        for(hz in listOf(30,60,90,120))for(scale in listOf(.5f,1f,2f))for(reverse in listOf(false,true)) {
            val actual=samples(hz,scale,reverse)
            for(i in reference.indices)assertEquals(reference[i],actual[i],.015f,"$hz/$scale/$reverse sample$i")
        }
    }

    @Test fun regrabReleaseReducedMotionAndRelayoutClearOrCarryAppearance() {
        val b=bar()
        for(target in listOf(1,2)) for(time in listOf(.07f,.12f,.18f,.28f,.34f)) {
            val c=GlassPoseController(GlassPoseSpec.Calm)
            c.attach(b,null,null);c.snapToRest(0);c.retarget(target);c.advanceTo(time)
            val before=c.transitAppearance
            c.pointerDown(c.centreX,b.centreY,time.toDouble(),true)
            c.beginDrag(c.centreX,b.centreY)
            assertEquals(before,c.transitAppearance,0f)
            c.advanceTo(time+1f/960f)
            assertTrue(abs(c.transitAppearance-before)<.03f)
            val held=c.transitAppearance;c.pointerUp(target)
            assertEquals(held,c.transitAppearance,0f)
            c.advanceTo(time+.4f)
            c.motionEnabled=false;c.advanceTo(time+.41f)
            assertEquals(0f,c.transitAppearance)
            assertFalse(c.solverFailed)
        }
        val c=GlassPoseController(GlassPoseSpec.Calm)
        c.attach(b,null,null);c.snapToRest(0);c.retarget(1);c.advanceTo(.15f)
        assertTrue(c.transitAppearance>.8f)
        c.attach(GlassSelectorBar(500f,100f,12f,3,50f,80f,45f,90f,150f,150f,1f,800f),null,null)
        assertEquals(0f,c.transitAppearance)
    }

    @Test fun stationaryHoldAndHistoricalStylesRetainExistingOptics() {
        val b=bar();val c=GlassPoseController(GlassPoseSpec.Calm)
        c.attach(b,null,null);c.snapToRest(0);c.pointerDown(b.firstCentre,b.centreY,0.0,true)
        for(i in 0..240){c.advanceTo(i/240f);assertEquals(0f,c.transitAppearance)}
        c.pointerUp(0)
        for(i in 1..240){c.advanceTo(1f+i/240f);assertEquals(0f,c.transitAppearance)}
        // The light preset light Calm replaced keeps the old tap appearance.
        val oldLight=GlassTabBarStyle.CalmRecordedSpace(dark=false)
        val style=lerpGlassStyle(oldLight.pill,oldLight.lens,.15f)
        assertEquals(style,glassTransitStyle(style,.95f,oldLight.tapTintFade))
        assertEquals(1f,glassTransitInkScale(oldLight,.15f,.95f))
        // Light Calm taps as dark Calm does: on the owner's light recording (IMG_7048 6.8-6.9 s) the
        // travelling pill refracts as clear glass and the selected ink grows, as in dark.
        val light=GlassTabBarStyle.Calm(dark=false)
        assertEquals(GlassTabBarStyle.Calm().tapTintFade,light.tapTintFade)
        assertEquals(GlassTabBarStyle.Calm().tapSelectedScale,light.tapSelectedScale)
        assertTrue(glassTransitInkScale(light,.15f,.95f)>1f)
        assertEquals(GlassTabBarStyle.V3().tapSelectedScale,1f)
        assertEquals(GlassTabBarStyle.V3().tapTintFade,0f)
    }

    @Test fun calmTapFormsFullGlassLensUnderIOS27() {
        val b = bar()
        val c = GlassPoseController(GlassPoseSpec.Calm)
        c.attach(b, null, null); c.snapToRest(0)
        c.pointerDown(b.centreOf(2), b.centreY, 0.0, true); c.retarget(2)
        c.advanceTo(.04f); c.pointerUp(2)
        val style = patchStyle()
        var peakFormation = 0f
        var peakLens = 0f
        var peakDispersion = 0f
        for (i in 1..120) {
            c.advanceTo(.04f + i / 480f)
            assertFalse(c.isHeld)
            peakFormation = maxOf(peakFormation, c.formation)
            val material = glassSelectorMaterial(style, c.formation, c.transitAppearance)
            peakLens = maxOf(peakLens, material.heldLens)
            peakDispersion = maxOf(peakDispersion, material.dispersion)
        }
        println("CALM_IOS27_TAP_LENS peakFormation=$peakFormation peakLens=$peakLens peakDispersion=$peakDispersion")
        assertTrue(peakFormation >= 0.85f, "iOS 27 tap transit formation must reach >= 0.85, was $peakFormation")
        assertTrue(peakLens >= 0.95f, "iOS 27 tap transit heldLens must reach full lens: $peakLens")
        assertTrue(peakDispersion >= GlassSelectorRimFringeMoving, "transit must show moving fringes: $peakDispersion")
        c.advanceTo(0.6f)
        assertTrue(c.formation < 0.05f, "settled tap must subside back to resting pill, was ${c.formation}")
    }
}
