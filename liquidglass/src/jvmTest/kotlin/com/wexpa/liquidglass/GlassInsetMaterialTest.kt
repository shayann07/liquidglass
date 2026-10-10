package com.wexpa.liquidglass

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.sin

/** A dark inset belongs to its substrate, not to the raised lens's whole silhouette. */
class GlassInsetMaterialTest {
    @Test fun substrateMaskUsesTheSamePullAndPressCoordinatesAsTheBar() {
        for(angle in listOf(-140f,-45f,0f,30f,90f)) {
            val frame=GlassMaterialDrawing(GlassPullDeformation(1.07f,.94f,angle,3f,-7f),
                pressX=1.02f,pressY=1.04f,centre=Offset(175f,40f))
            val origin=Offset(231f,-60f)
            val (rowX,rowY)=frame.footprintMap(origin)
            val rad=angle*kotlin.math.PI.toFloat()/180
            val c=cos(rad);val s=sin(rad)
            for(point in listOf(Offset(4f,4f),Offset(175f,40f),Offset(341f,74f))) {
                val x=(point.x-frame.centre.x)*frame.pressX
                val y=(point.y-frame.centre.y)*frame.pressY
                val ax=(c*x+s*y)*frame.pull.along
                val ay=(-s*x+c*y)*frame.pull.across
                val local=Offset(c*ax-s*ay+frame.centre.x+frame.pull.translationX,
                    s*ax+c*ay+frame.centre.y+frame.pull.translationY)-origin
                assertEquals(point.x+1f,rowX[0]*local.x+rowX[1]*local.y+rowX[2],.001f)
                assertEquals(point.y+1f,rowY[0]*local.x+rowY[1]*local.y+rowY[2],.001f)
            }
        }
    }

    @Test fun compressedCapsDoNotPaintRestingTintOntoThePage() {
        val pad=16
        val side=120+pad*2
        fun backdrop(x: Int,y: Int): Int =
            if(y in 36+pad until 84+pad) 0xff506070.toInt() else
                if((x/8)%2==0) 0xff1d4868.toInt() else 0xff3d7190.toInt()
        fun render(masked: Boolean,tint: Float, lift: Float)=GlassRender.render(
            width=120,height=120,pad=pad,profile=2f,heldLens=0f,heldGlow=0f,
            refractDepth=0f,tintAlpha=tint,tint=Triple(0f,0f,0f),lift=lift,
            tintMask=if(masked) { _,y -> if(y in 36+pad until 84+pad) 1f else 0f } else null,
            backdrop=::backdrop)
        val corrected=render(true,.5f,-10f/255f)
        val rejected=render(false,.5f,-10f/255f)
        val clear=render(false,0f,0f)
        // Both protruding caps retain the actual page colour/detail rather than a dark disk.
        for(y in listOf(20,100)) for(x in 48..72) {
            val index=(y+pad)*side+x+pad
            assertEquals(clear[index],corrected[index],"page tinted at$x,$y")
            assertTrue(GlassRender.luma(corrected[index])-GlassRender.luma(rejected[index])>25,
                "regression fixture must detect the old dark-cap defect")
        }
        // The resting inset itself keeps its original material over the bar substrate.
        for(y in 49..71) for(x in 40..80) {
            val index=(y+pad)*side+x+pad
            assertEquals(rejected[index],corrected[index],"inset changed inside substrate")
        }
    }

    @Test fun footprintAlphaAndOpticalCoverageRemainIndependent() {
        fun render(alpha: Float)=GlassRender.render(width=120,height=120,profile=2f,
            heldLens=.6f,heldGlow=.4f,refractDepth=0f,tintAlpha=.2f,
            tint=Triple(0f,0f,0f),lift=-.02f,specular=.3f,
            tintMask={_,_->alpha},backdrop={x,y -> 0xff000000.toInt() or ((x*2%255) shl 16) or (y%255 shl 8) or 128})
        val outside=render(0f);val inside=render(1f);val edge=render(.5f)
        for(i in edge.indices) {
            assertEquals(outside[i] ushr 24,inside[i] ushr 24,"tint changed body coverage")
            assertEquals(outside[i] ushr 24,edge[i] ushr 24)
            for(shift in listOf(0,8,16)) {
                val a=outside[i] ushr shift and 255;val b=inside[i] ushr shift and 255
                val e=edge[i] ushr shift and 255
                assertTrue(e in (minOf(a,b)-2)..(maxOf(a,b)+2),"mask edge added a halo")
            }
        }
    }
}
