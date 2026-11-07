package com.wexpa.liquidglass

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toPixelMap
import java.lang.management.ManagementFactory
import kotlin.test.*

class FieldAllocationBenchmarkTest {
    /** Checks texel equality before measuring paired JVM allocation and elapsed time across three sizes. */
    @Test fun compareBaselineFreshAndPooled() {
        val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        bean.isThreadAllocatedMemoryEnabled = true
        val tid = Thread.currentThread().threadId()
        for ((w, h) in listOf(512 to 256, 1080 to 300, 1080 to 720)) {
            val p = Path().apply {
                moveTo(0f, 0f); lineTo(w*.4f, 0f); lineTo(w*.5f, 12f); lineTo(w*.6f, 0f)
                lineTo(w.toFloat(), 0f); lineTo(w.toFloat(), h.toFloat()); lineTo(0f,h.toFloat()); close()
            }
            /** Selects upstream (0), fresh (1), production pooled (2), or the previous main's pooled transform (3). */
            fun build(mode: Int) = when(mode) {
                0 -> buildPathFieldBaseline(p, w+48, h+48, 24f, 48f)!!
                1 -> buildPathField(p, w+48, h+48, 24f, 48f, GlassPathFieldBuffers())!!
                2 -> buildPathField(p, w+48, h+48, 24f, 48f)!!
                else -> GlassPathFieldBufferPool.use((w+48).toLong()*(h+48)) { buildPathFieldMain(p, w+48, h+48, 24f, 48f, it)!! }
            }
            val baseline = build(0).bitmap.toPixelMap()
            for(mode in 1..3) {
                val fixed = build(mode).bitmap.toPixelMap()
                for(y in 0 until baseline.height) for(x in 0 until baseline.width) {
                    assertEquals(baseline[x,y], fixed[x,y], "mode=$mode pixel=$x,$y")
                }
            }
            repeat(40) { for(mode in 0..3) build(mode) }
            val nanos = Array(4) { ArrayList<Long>() }
            val bytes = Array(4) { ArrayList<Long>() }
            repeat(61) { round ->
                for(mode in if(round%2==0) listOf(0,1,2,3) else listOf(3,2,1,0)) {
                    val beforeBytes = bean.getThreadAllocatedBytes(tid)
                    val start = System.nanoTime()
                    val field = build(mode)
                    val elapsed = System.nanoTime()-start
                    val allocated = bean.getThreadAllocatedBytes(tid)-beforeBytes
                    nanos[mode].add(elapsed); bytes[mode].add(allocated)
                    assertEquals(w+48, field.layerWidth)
                }
            }
            for(mode in 0..3) {
                nanos[mode].sort(); bytes[mode].sort()
                println("FIX_BENCH size=${w}x$h mode=$mode n=61 p50_ms=${nanos[mode][30]/1e6} p95_ms=${nanos[mode][57]/1e6} bytes_p50=${bytes[mode][30]} pixels_identical=true")
            }
        }
    }
}
