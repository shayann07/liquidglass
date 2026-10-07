package com.wexpa.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertTrue

/** Ordinary/selected input images must register before selector optics are applied. */
@OptIn(ExperimentalTestApi::class)
class GlassMaterialDrawingTest {
    @Test fun shiftedSelectorNodeDrawsIdenticalInkWithTheSharedBarPivot() = runComposeUiTest {
        val shared=GlassMaterialDrawingState()
        var shifted by mutableStateOf(false)
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                val origin=if(shifted) Offset(67f,-16f) else Offset.Zero
                Box(Modifier.size(400.dp,160.dp).background(Color.Black).testTag("ink-scene")) {
                    Box(Modifier.offset {IntOffset(origin.x.toInt(),40+origin.y.toInt())}
                        .size(if(shifted) 212.dp else 400.dp,if(shifted) 100.dp else 62.dp)
                        .glassMaterialForeground(shared,origin)
                        .drawBehind {
                            // Same native ink in two different node coordinate frames.
                            for(x in listOf(80f,200f,310f)) {
                                drawRect(Color.White,Offset(x,17f)-origin,Size(13f,27f))
                                drawRect(Color.Cyan,Offset(x+17f,21f)-origin,Size(2f,19f))
                            }
                        })
                }
            }
        }
        fun pixels(): IntArray {
            waitForIdle()
            val image=onNodeWithTag("ink-scene").captureToImage().toAwtImage()
            return image.getRGB(0,0,image.width,image.height,null,0,image.width)
        }
        for(pull in listOf(GlassPullDeformation.None,
            GlassPullDeformation(1.0481f,.9541f,90f,0f,-3.472f),
            GlassPullDeformation(1.04f,1f/1.04f,27f,2.2f,-1.3f))) {
            runOnIdle {shared.drawing=GlassMaterialDrawing(pull,1.05f,1.05f,Offset(200f,31f));shifted=false}
            val ordinary=pixels()
            runOnIdle {shifted=true}
            val selected=pixels()
            val maxError=ordinary.indices.maxOf {i ->
                listOf(0,8,16,24).maxOf {s -> kotlin.math.abs((ordinary[i] ushr s and 255)-(selected[i] ushr s and 255))}
            }
            assertTrue(maxError<=1,"ordinary/selected input registration differs by $maxError channel levels")
        }
    }
}
