package com.wexpa.liquidglass

import android.graphics.Canvas
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import android.view.View
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * Shared native renderer that renders the Liquid Glass effect by sampling from
 * a [GlassBackdrop]'s [RenderNode] using Android's native [RenderEffect] and [android.graphics.RuntimeShader].
 *
 * Reuses the library's exact optics, uniforms, and fallback rendering pipeline without
 * spinning up internal `ComposeView`s per surface.
 */
class GlassNativeRenderer(
    val targetView: View,
    var backdrop: GlassBackdrop? = null,
    var glass: Glass = Glass.Regular,
    var shape: Shape = CapsuleShape,
    var dark: Boolean = false,
    var backgroundColor: Color = if (dark) Color.Black else Color.White,
) {
    var touchX: Float = -1e5f
    var touchY: Float = -1e5f
    var touchAmount: Float = 0f

    val glassNode: RenderNode? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        RenderNode("LiquidGlassSurface")
    } else {
        null
    }

    private var wideNode: RenderNode? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        RenderNode("LiquidGlassWideStrip")
    } else {
        null
    }

    private val viewLocation = IntArray(2)
    private val backdropLocation = IntArray(2)
    private val androidOutline = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        android.graphics.Outline()
    } else {
        null
    }
    private val drawScope = CanvasDrawScope()
    private var cachedPathField: GlassPathField? = null
    private var cachedPathFieldKey: Any? = null

    internal var alphaMultiplier: Float = 1f

    private val scratchClipPath = android.graphics.Path()
    private val scratchMatrix = android.graphics.Matrix()
    private val scratchSourceRect = android.graphics.RectF()
    private val scratchNodePath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) android.graphics.Path() else null

    internal fun discard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            glassNode?.discardDisplayList()
            wideNode?.discardDisplayList()
        }
        cachedPathField = null
        cachedPathFieldKey = null
    }

    internal fun resetInteraction() {
        touchX = -1e5f
        touchY = -1e5f
        touchAmount = 0f
    }

    fun draw(canvas: Canvas, width: Int, height: Int) {
        if (width <= 0 || height <= 0 || alphaMultiplier <= 0.001f) return

        // Finding 7: Glass.Identity deliberately represents no material. Short-circuit immediately.
        if (glass.isIdentity || glass.style(dark) == null) {
            return
        }

        val node = glassNode
        val bd = backdrop
        val sourceNode = bd?.sourceNode
        val density = Density(targetView.context.resources.displayMetrics.density)
        val layoutDirection = if (targetView.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
            LayoutDirection.Rtl
        } else {
            LayoutDirection.Ltr
        }

        // Setup outline & clipping preserving full asymmetric corner geometry and RTL
        val composeOutline = shape.createOutline(
            Size(width.toFloat(), height.toFloat()),
            layoutDirection,
            density,
        )
        val clipPath = scratchClipPath
        clipPath.reset()
        when (composeOutline) {
            is Outline.Rectangle -> {
                clipPath.addRect(0f, 0f, width.toFloat(), height.toFloat(), android.graphics.Path.Direction.CW)
            }
            is Outline.Rounded -> {
                val rr = composeOutline.roundRect
                val rArray = floatArrayOf(
                    rr.topLeftCornerRadius.x, rr.topLeftCornerRadius.y,
                    rr.topRightCornerRadius.x, rr.topRightCornerRadius.y,
                    rr.bottomRightCornerRadius.x, rr.bottomRightCornerRadius.y,
                    rr.bottomLeftCornerRadius.x, rr.bottomLeftCornerRadius.y,
                )
                clipPath.addRoundRect(rr.left, rr.top, rr.right, rr.bottom, rArray, android.graphics.Path.Direction.CW)
            }
            is Outline.Generic -> {
                clipPath.set(composeOutline.path.asAndroidPath())
            }
        }

        if (node != null && sourceNode != null && canvas.isHardwareAccelerated && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val style = glass.style(dark) ?: return
            val radii = shape.glassRadii(Size(width.toFloat(), height.toFloat()), layoutDirection, density)
            val bandPx = style.bandPx(radii, Size(width.toFloat(), height.toFloat()), density)
            val pad = style.recordPad(radii, Size(width.toFloat(), height.toFloat()), null, density, 1f, bandPx)
            val paddedWidth = kotlin.math.ceil(width + pad * 2f).toInt().coerceAtLeast(1)
            val paddedHeight = kotlin.math.ceil(height + pad * 2f).toInt().coerceAtLeast(1)

            // Wide-kernel blur strip setup
            val wideKernelPx = if (style.wideKernel.isSpecified) with(density) { style.wideKernel.toPx() } else 0f
            val useWide = style.profile != GlassProfile.Legacy && wideKernelPx >= 1f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            val wideScale = 0.25f
            val stripWidth = if (useWide) kotlin.math.ceil(paddedWidth * wideScale).toInt().coerceAtLeast(1) else 0
            val stripHeight = if (useWide) kotlin.math.ceil(paddedHeight * wideScale).toInt().coerceAtLeast(1) else 0
            val totalNodeWidth = paddedWidth
            val totalNodeHeight = paddedHeight + stripHeight

            val matrix = scratchMatrix
            matrix.reset()
            bd.transformMatrixToGlobal(matrix)
            targetView.transformMatrixToLocal(matrix)

            val outline = androidOutline
            node.setClipToOutline(false)
            node.setAlpha(1f)
            if (outline != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val nodePath = scratchNodePath ?: android.graphics.Path()
                    nodePath.set(clipPath)
                    nodePath.offset(pad, pad)
                    outline.setPath(nodePath)
                    node.setOutline(outline)
                    node.setClipToOutline(true)
                } else if (composeOutline is Outline.Rounded) {
                    val rr = composeOutline.roundRect
                    if (rr.topLeftCornerRadius.x == rr.topRightCornerRadius.x &&
                        rr.topLeftCornerRadius.x == rr.bottomLeftCornerRadius.x &&
                        rr.topLeftCornerRadius.x == rr.bottomRightCornerRadius.x) {
                        outline.setRoundRect(
                            (rr.left + pad).toInt(),
                            (rr.top + pad).toInt(),
                            (rr.right + pad).toInt(),
                            (rr.bottom + pad).toInt(),
                            rr.topLeftCornerRadius.x,
                        )
                        node.setOutline(outline)
                        node.setClipToOutline(true)
                    }
                }
            }

            node.setPosition(0, 0, totalNodeWidth, totalNodeHeight)
            val recordingCanvas = node.beginRecording(totalNodeWidth, totalNodeHeight)
            try {
                val bgArgb = (if (backgroundColor.isSpecified) backgroundColor else if (dark) Color.Black else Color.White).toArgb()
                recordingCanvas.drawColor(bgArgb)

                recordingCanvas.save()
                recordingCanvas.translate(pad, pad)
                recordingCanvas.concat(matrix)
                recordingCanvas.drawRenderNode(sourceNode)
                recordingCanvas.restore()

                if (useWide) {
                    val wn = wideNode ?: RenderNode("LiquidGlassWideStrip").also { wideNode = it }
                    wn.setPosition(0, 0, stripWidth, stripHeight)
                    val wideCanvas = wn.beginRecording(stripWidth, stripHeight)
                    try {
                        wideCanvas.drawColor(bgArgb)
                        wideCanvas.scale(wideScale, wideScale)
                        wideCanvas.translate(pad, pad)
                        wideCanvas.concat(matrix)
                        wideCanvas.drawRenderNode(sourceNode)
                    } finally {
                        wn.endRecording()
                    }
                    val wideEffect = createAndroidWideKernelEffect(wideKernelPx * wideScale)
                    wn.setRenderEffect(wideEffect)

                    recordingCanvas.save()
                    recordingCanvas.translate(0f, paddedHeight.toFloat())
                    recordingCanvas.drawRenderNode(wn)
                    recordingCanvas.restore()
                }
            } finally {
                node.endRecording()
            }

            val sourceRect = scratchSourceRect
            sourceRect.set(0f, 0f, bd.width.toFloat(), bd.height.toFloat())
            matrix.mapRect(sourceRect)

            val pathField = if (composeOutline is Outline.Generic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val band = with(density) { if (style.refractionBand.isSpecified) style.refractionBand.toPx() else 0f }
                val key = listOf(shape, paddedWidth, paddedHeight, pad, band, layoutDirection, density.density)
                if (cachedPathFieldKey == key && cachedPathField != null) {
                    cachedPathField
                } else {
                    val composePath = androidx.compose.ui.graphics.Path().apply {
                        fillType = composeOutline.path.fillType
                        addPath(composeOutline.path)
                    }
                    val field = buildPathField(composePath, paddedWidth, paddedHeight, pad, band)
                    cachedPathField = field
                    cachedPathFieldKey = key
                    field
                }
            } else {
                null
            }

            // Apply shader / blur effect
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val press = if (touchAmount > 0f) GlassPress(touchX, touchY, touchAmount) else GlassPress.None
                val uniforms = buildGlassPanelUniforms(
                    density = density,
                    shape = shape,
                    size = Size(width.toFloat(), height.toFloat()),
                    glass = glass,
                    dark = dark,
                    background = backgroundColor,
                    deltaX = -sourceRect.left,
                    deltaY = -sourceRect.top,
                    sourceSize = Size(sourceRect.width(), sourceRect.height()),
                    tiltX = 0f,
                    tiltY = 0f,
                    ambientAlpha = 1f,
                    pressAlpha = touchAmount,
                    press = press,
                    tintField = null,
                    interaction = if (glass.isInteractive) glass.interaction else null,
                    contrast = 0f,
                    layoutDirection = layoutDirection,
                    pathField = pathField,
                ).copy(
                    wideStrip = if (useWide) paddedHeight.toFloat() else 0f,
                    wideScale = wideScale,
                )
                val effect = createAndroidGlassRenderEffect(uniforms)
                node.setRenderEffect(effect)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val blurPx = style.blurRadius.value * density.density
                val blurEffect = if (blurPx > 0f) {
                    RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
                } else {
                    null
                }
                node.setRenderEffect(blurEffect)
            }

            // Fade the complete drawable once, including the legacy fallback overlay.
            val saveCount = if (alphaMultiplier < 0.999f) {
                canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(),
                    (alphaMultiplier * 255f).toInt().coerceIn(0, 255))
            } else {
                canvas.save()
            }
            try {
                canvas.clipPath(clipPath)
                val sourceSave = canvas.save()
                try {
                    canvas.translate(-pad, -pad)
                    canvas.drawRenderNode(node)
                } finally {
                    canvas.restoreToCount(sourceSave)
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    drawFallbackOverlay(canvas, width, height, density, layoutDirection)
                }
            } finally {
                canvas.restoreToCount(saveCount)
            }
            return
        }

        // Software canvas or API < 29 fallback
        val alphaInt = (alphaMultiplier * 255f).toInt().coerceIn(0, 255)
        val saveCount = if (alphaMultiplier < 0.999f) {
            canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), alphaInt)
        } else {
            canvas.save()
        }
        try {
            canvas.clipPath(clipPath)
            drawFallbackOverlay(canvas, width, height, density, layoutDirection)
        } finally {
            canvas.restoreToCount(saveCount)
        }
    }

    private fun drawFallbackOverlay(
        canvas: Canvas,
        width: Int,
        height: Int,
        density: Density,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ) {
        if (glass.isIdentity) return
        val style = glass.style(dark) ?: return
        val light = glass.light(dark)
        val radii = shape.glassRadii(Size(width.toFloat(), height.toFloat()), layoutDirection, density)
        val bevelPx = with(density) { style.bevel.toPx() }

        drawScope.draw(
            density = density,
            layoutDirection = layoutDirection,
            canvas = ComposeCanvas(canvas),
            size = Size(width.toFloat(), height.toFloat()),
        ) {
            drawGlassFallback(style, light, radii, bevelPx)
        }
    }
}
