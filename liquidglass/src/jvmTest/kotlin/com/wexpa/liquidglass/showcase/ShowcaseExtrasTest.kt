package com.wexpa.liquidglass.showcase

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wexpa.liquidglass.GlassGallery
import com.wexpa.liquidglass.GlassScrollEdge
import com.wexpa.liquidglass.GlassScrollEdgeStyle
import com.wexpa.liquidglass.GlassStyle
import com.wexpa.liquidglass.LiquidGlassCoverSheet
import com.wexpa.liquidglass.LiquidGlassScene
import com.wexpa.liquidglass.liquidGlass
import com.wexpa.liquidglass.liquidGlassSource
import com.wexpa.liquidglass.rememberLiquidGlassState
import kotlin.test.Test

/** The rest of the public surface: the built-in gallery, the cover sheet, scroll edges, and the implicit-state scene. */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class ShowcaseExtrasTest {

    @Test fun builtInGallery() = showcaseScene(390, 820, density = 1.5f) {
        setContent { GlassGallery(Modifier.fillMaxSize()) }
        waitForIdle()
        val out = Showcase.dir("extras")
        shoot(out.resolve("gallery-1.png"))
        onRoot().performTouchInput { swipeUp(startY = bottom * 0.9f, endY = bottom * 0.15f, durationMillis = 600) }
        waitForIdle()
        shoot(out.resolve("gallery-2.png"))
    }

    @Test fun coverSheet() = showcaseScene(780, 560, density = 1.25f) {
        setContent {
            Row(Modifier.fillMaxSize().background(Ink)) {
                for ((pull, tracked) in listOf(220 to false, 380 to true, 560 to false)) {
                    Box(Modifier.weight(1f).fillMaxSize().padding(4.dp)) {
                        PhotoBackdrop(Modifier.fillMaxSize())
                        LiquidGlassCoverSheet(pull = pull.dp, tracked = tracked, background = Color(0xFF101018)) {
                            Column(Modifier.fillMaxSize().background(Color(0xFF101018)).padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                BasicText("9:41", style = TextStyle(color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Bold))
                                repeat(5) { i ->
                                    Box(Modifier.fillMaxWidth().height(54.dp).background(Color(0xFF2A2A3A), RoundedCornerShape(16.dp)).padding(12.dp)) {
                                        BasicText("Notification ${i + 1}", style = TextStyle(color = Color.White, fontSize = 14.sp))
                                    }
                                }
                            }
                        }
                        Box(Modifier.align(Alignment.BottomCenter).padding(8.dp).background(Color(0xCC0E1222)).padding(6.dp)) {
                            Caption("pull = ${pull}dp, tracked = $tracked")
                        }
                    }
                }
            }
        }
        waitForIdle()
        shoot(Showcase.dir("extras").resolve("cover-sheet.png"))
    }

    @Test fun scrollEdges() = showcaseScene(780, 360, density = 1.5f) {
        setContent {
            Row(Modifier.fillMaxSize()) {
                for ((label, style) in listOf("none" to null, "Soft" to GlassScrollEdgeStyle.Soft, "Hard" to GlassScrollEdgeStyle.Hard)) {
                    Box(Modifier.weight(1f).fillMaxSize()) {
                        val glass = rememberLiquidGlassState(background = Color(0xFFF6F4EF))
                        ArticleBackdrop(Modifier.fillMaxSize().liquidGlassSource(glass))
                        if (style != null) GlassScrollEdge(ground = Color(0xFFF6F4EF), height = 70.dp, style = style)
                        Box(Modifier.padding(12.dp).fillMaxWidth().height(44.dp)
                            .liquidGlass(glass, CircleShape, GlassStyle.inApp(dark = false)), contentAlignment = Alignment.Center) {
                            BasicText("Toolbar", style = TextStyle(color = Color(0xFF111111), fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
                        }
                        Box(Modifier.align(Alignment.BottomCenter).background(Color(0xCC0E1222)).padding(6.dp)) {
                            Caption("GlassScrollEdge: $label")
                        }
                    }
                }
            }
        }
        waitForIdle()
        shoot(Showcase.dir("extras").resolve("scroll-edge.png"))
    }

    /** The same panel through the implicit-state API: no state passed anywhere. */
    @Test fun implicitScene() = showcaseScene(360, 240, density = 2f) {
        setContent {
            LiquidGlassScene(background = Color.Black, modifier = Modifier.fillMaxSize()) {
                PhotoBackdrop(Modifier.fillMaxSize().liquidGlassSource())
                Box(Modifier.align(Alignment.Center).size(220.dp, 110.dp)
                    .liquidGlass(RoundedCornerShape(30.dp), GlassStyle.Regular), contentAlignment = Alignment.Center) {
                    BasicText("LiquidGlassScene", style = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
                }
            }
        }
        waitForIdle()
        shoot(Showcase.dir("extras").resolve("implicit-scene.png"))
    }
}
