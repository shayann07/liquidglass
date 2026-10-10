package com.wexpa.liquidglass

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlassBarShapeReferenceTest {
    @Test fun biasedMaterialFitsEveryViewportEdgeWithoutMovingTheLayout() {
        val interaction=checkNotNull(GlassTabBarStyle.Calm().barInteraction)
        val w=834f;val h=186f;val r=h/2;val limit=h*1.05f*interaction.pullLimit
        for ((x,y) in listOf(0f to 0f,336f to 0f,0f to 314f,336f to 314f,168f to 157f)) {
            val sx=glassPressScaleInViewport(1.05f,w,x,1170f)
            val sy=glassPressScaleInViewport(1.05f,h,y,500f)
            for(degrees in 0 until 360 step 15) {
                val a=degrees*kotlin.math.PI.toFloat()/180f
                val compliance=glassPullTargetScale(10000f,interaction.pullFollow,limit)
                val raw=glassMaterialPull(w,h,r,3f,sx,sy,cos(a)*10000*compliance,
                    sin(a)*10000*compliance,limit,interaction)
                val p=glassPullInViewport(raw,w,h,r,sx,sy,x+w/2,y+h/2,1170f,500f)
                val angle=p.angleDegrees*kotlin.math.PI.toFloat()/180f
                val c=cos(angle);val s=sin(angle)
                repeat(72) { n ->
                    val t=n*kotlin.math.PI.toFloat()/36f;val nx=cos(t);val ny=sin(t)
                    val px=((if(nx<0)-1 else 1)*(w/2-r)+r*nx)*sx
                    val py=((if(ny<0)-1 else 1)*(h/2-r)+r*ny)*sy
                    val u=(c*px+s*py)*p.along;val v=(-s*px+c*py)*p.across
                    val outX=x+w/2+p.translationX+c*u-s*v
                    val outY=y+h/2+p.translationY+s*u+c*v
                    assertTrue(outX in -.01f..1170.01f && outY in -.01f..500.01f,
                        "biased contour escaped viewport: $outX,$outY at origin$x,$y pull$degrees")
                }
            }
        }
    }

    @Test fun prolongedUpwardPullMatchesTheOriginalTopAndBottomSeparately() {
        val interaction = checkNotNull(GlassTabBarStyle.Calm().barInteraction)
        val w=834f; val h=186f; val press=1.05f; val centre=283.5f
        val limit=h*press*interaction.pullLimit
        val dy=-10000f*glassPullTargetScale(10000f,interaction.pullFollow,limit)
        val p=glassMaterialPull(w,h,h/2,3f,press,press,0f,dy,limit,interaction)
        val a=p.angleDegrees*kotlin.math.PI.toFloat()/180f
        val c=(p.along-p.across)*cos(a)*sin(a)*press
        val d=(p.along*sin(a)*sin(a)+p.across*cos(a)*cos(a))*press
        val halfHeight=abs(c)*(w/2-h/2)+h/2*hypot(c,d)
        val top=centre+p.translationY-halfHeight
        val bottom=centre+p.translationY+halfHeight
        println("PHONE_T04_EDGES top=$top bottom=$bottom centre=${centre+p.translationY}")
        // Original selected PTS29863/600: top171..172, bottom375, rest centre283.5.
        // This tests visible drawing bounds, not widget placement or an identified finger gain.
        assertEquals(171.5f,top,1f,"top edge stayed centred instead of extending upward")
        assertEquals(375f,bottom,1f,"far edge moved away from the original bound")
        assertTrue(abs(p.translationY)<=10.5f,"visible bias exceeded the chosen reference")
        assertEquals(0f,p.translationX)
    }

    @Test fun oppositeAndDiagonalPullsPreserveAreaAndDoNotReachTheLimitEarly() {
        val interaction = checkNotNull(GlassTabBarStyle.Calm().barInteraction)
        val w = 834f; val h = 186f; val press = 1.05f
        val limit = h * press * interaction.pullLimit
        fun shape(x: Float, y: Float): GlassPullDeformation {
            val compliance = glassPullTargetScale(hypot(x, y), interaction.pullFollow, limit)
            return glassMaterialPull(w,h,h/2,3f,press,press,x*compliance,y*compliance,limit,interaction)
        }
        for (degrees in 0 until 360 step 5) {
            val a = degrees * kotlin.math.PI.toFloat() / 180f
            val p = shape(cos(a)*10000, sin(a)*10000)
            assertEquals(1f, p.along*p.across, 1e-6f)
            assertTrue(p.along <= 1.049f && p.across >= .954f)
            assertTrue(hypot(p.translationX,p.translationY)<=10.5f)
            assertTrue(p.translationX*cos(a)+p.translationY*sin(a)>=0f)
        }
        val up = shape(0f,-10000f); val down = shape(0f,10000f)
        assertEquals(up.along,down.along);assertEquals(up.across,down.across)
        assertEquals(up.translationY,-down.translationY)
        val moderate = shape(0f,60f)
        assertTrue(moderate.along > 1f && moderate.along < 1.018f,
            "a20dp pull must not saturate the authored input response")
        assertTrue(abs(moderate.translationY)<1.3f,"ordinary pull reached its drawing limit early")
        assertEquals(GlassPullDeformation.None,shape(0f,0f))
        assertEquals(GlassPullShape.Adaptive,GlassInteraction.Calm.pullShape,
            "navigation's relative deformation must not change card defaults")
    }

    @Test fun prolongedBarPullMatchesBothReferenceDimensions() {
        val interaction = checkNotNull(GlassTabBarStyle.Calm().barInteraction)
        val w = 834f; val h = 186f; val density = 3f; val press = 1.05f
        val targetLimit = h * press * interaction.pullLimit
        val rawY = -10000f
        val dy = rawY * glassPullTargetScale(abs(rawY), interaction.pullFollow, targetLimit)
        val shape = glassMaterialPull(w, h, h / 2, density, press, press, 0f, dy, targetLimit, interaction)
        val a = shape.angleDegrees * (kotlin.math.PI.toFloat() / 180f)
        val xx = (shape.along * cos(a) * cos(a) + shape.across * sin(a) * sin(a)) * press
        val xy = (shape.along - shape.across) * sin(a) * cos(a) * press
        val yy = (shape.along * sin(a) * sin(a) + shape.across * cos(a) * cos(a)) * press
        val width = 2 * (abs(xx) * (w / 2 - h / 2) + h / 2 * hypot(xx, xy))
        val height = 2 * (abs(xy) * (w / 2 - h / 2) + h / 2 * hypot(xy, yy))
        println("PHONE_T04_DIMENSIONS width=$width height=$height")
        // Original T04 PTS31074/600: clean central-row width833px, height206px.
        // Earlier sustained frame has205px height. Bounds include one-pixel edge uncertainty
        // and the fixture's rounded834px base width. This does not fit input speed or centre.
        assertEquals(205f, height, 1.1f, "prolonged-pull height")
        assertEquals(834f, width, 4f, "the bar must narrow as it grows taller")
    }
}
