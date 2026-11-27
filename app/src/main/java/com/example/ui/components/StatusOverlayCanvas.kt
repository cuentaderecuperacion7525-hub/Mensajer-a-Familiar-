package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.ui.theme.InterFontFamily
import com.example.util.StatusDrawingPoint
import com.example.util.StatusDrawingStroke
import com.example.util.StatusOverlayBundle
import com.example.util.StatusStickerKind
import com.example.util.StatusStickerOverlay
import com.example.util.StatusTextAlign
import com.example.util.StatusTextOverlay
import com.example.util.StatusTextStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

private sealed class RenderLayerItem(val zOrder: Int) {
    data class TextLayer(val item: StatusTextOverlay) : RenderLayerItem(item.zOrder)
    data class StickerLayer(val item: StatusStickerOverlay) : RenderLayerItem(item.zOrder)
}

@Composable
fun StatusOverlayRenderBox(
    bundle: StatusOverlayBundle,
    activeInProgressStroke: StatusDrawingStroke? = null,
    isDrawingMode: Boolean = false,
    interactive: Boolean = false,
    hiddenTextId: String? = null,
    modifier: Modifier = Modifier,
    onStrokeStart: ((StatusDrawingPoint) -> Unit)? = null,
    onStrokeAppend: ((StatusDrawingPoint) -> Unit)? = null,
    onStrokeEnd: (() -> Unit)? = null,
    onBringTextToFront: ((String) -> Unit)? = null,
    onEditTextTap: ((StatusTextOverlay) -> Unit)? = null,
    onTransformText: ((id: String, xNorm: Float, yNorm: Float, scale: Float, rotationDeg: Float) -> Unit)? = null,
    onDragStateChange: ((isDragging: Boolean, pointerYNorm: Float, isOverTrash: Boolean, elementId: String, isText: Boolean) -> Unit)? = null,
    onBringStickerToFront: ((String) -> Unit)? = null,
    onTransformSticker: ((id: String, xNorm: Float, yNorm: Float, scale: Float, rotationDeg: Float) -> Unit)? = null
) {
    val density = LocalDensity.current
    val currentOnStrokeStart by rememberUpdatedState(onStrokeStart)
    val currentOnStrokeAppend by rememberUpdatedState(onStrokeAppend)
    val currentOnStrokeEnd by rememberUpdatedState(onStrokeEnd)
    val currentIsDrawingMode by rememberUpdatedState(isDrawingMode)

    BoxWithConstraints(modifier = modifier) {
        val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val heightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        val minDimPx = min(widthPx, heightPx)

        // 1. Freehand strokes layer with isolated Offscreen compositing for clean Eraser strokes
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .then(
                    if (interactive && isDrawingMode) {
                        Modifier
                            .pointerInput(widthPx, heightPx) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    if (!currentIsDrawingMode) return@awaitEachGesture
                                    down.consume()
                                    currentOnStrokeStart?.invoke(
                                        StatusDrawingPoint(
                                            xNorm = (down.position.x / widthPx).coerceIn(0f, 1f),
                                            yNorm = (down.position.y / heightPx).coerceIn(0f, 1f)
                                        )
                                    )
                                    var activePointerId = down.id
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == activePointerId }
                                            ?: event.changes.firstOrNull()
                                            ?: break
                                        if (!change.pressed) {
                                            change.consume()
                                            break
                                        }
                                        activePointerId = change.id
                                        if (change.positionChanged()) {
                                            change.consume()
                                            currentOnStrokeAppend?.invoke(
                                                StatusDrawingPoint(
                                                    xNorm = (change.position.x / widthPx).coerceIn(0f, 1f),
                                                    yNorm = (change.position.y / heightPx).coerceIn(0f, 1f)
                                                )
                                            )
                                        }
                                    }
                                    currentOnStrokeEnd?.invoke()
                                }
                            }
                            .testTag("status_drawing_canvas")
                    } else {
                        Modifier
                    }
                )
        ) {
            val allStrokes = if (activeInProgressStroke != null) {
                bundle.strokes + activeInProgressStroke
            } else {
                bundle.strokes
            }
            allStrokes.forEach { stroke ->
                drawSingleStroke(
                    stroke = stroke,
                    canvasW = size.width,
                    canvasH = size.height
                )
            }
        }

        // 2. Z-ordered texts and stickers
        val orderedLayers = remember(bundle.texts, bundle.stickers, hiddenTextId) {
            buildList {
                bundle.texts
                    .filter { it.id != hiddenTextId }
                    .forEach { add(RenderLayerItem.TextLayer(it)) }
                bundle.stickers.forEach { add(RenderLayerItem.StickerLayer(it)) }
            }.sortedBy { it.zOrder }
        }

        orderedLayers.forEachIndexed { idx, layer ->
            val layerZ = (idx + 1).toFloat()
            when (layer) {
                is RenderLayerItem.StickerLayer -> {
                    val sticker = layer.item
                    val currentSticker by rememberUpdatedState(sticker)
                    val cx = sticker.xNorm * widthPx
                    val cy = sticker.yNorm * heightPx
                    Box(
                        modifier = Modifier
                            .zIndex(layerZ)
                            .offset { IntOffset(cx.roundToInt(), cy.roundToInt()) }
                            .graphicsLayer {
                                translationX = -size.width / 2f
                                translationY = -size.height / 2f
                                scaleX = sticker.scale
                                scaleY = sticker.scale
                                rotationZ = sticker.rotationDeg
                            }
                            .then(
                                if (interactive && !isDrawingMode) {
                                    Modifier.pointerInput(sticker.id, widthPx, heightPx) {
                                        handleOverlayTransformGestures(
                                            canvasW = widthPx,
                                            canvasH = heightPx,
                                            getXNorm = { currentSticker.xNorm },
                                            getYNorm = { currentSticker.yNorm },
                                            getScale = { currentSticker.scale },
                                            getRotation = { currentSticker.rotationDeg },
                                            onBringToFront = { onBringStickerToFront?.invoke(currentSticker.id) },
                                            onTap = { onBringStickerToFront?.invoke(currentSticker.id) },
                                            onUpdate = { nx, ny, ns, nr ->
                                                onTransformSticker?.invoke(currentSticker.id, nx, ny, ns, nr)
                                            },
                                            onDragState = { dragging, ny, overTrash ->
                                                onDragStateChange?.invoke(dragging, ny, overTrash, currentSticker.id, false)
                                            }
                                        )
                                    }
                                } else {
                                    Modifier
                                }
                            )
                            .testTag("canvas_sticker_${sticker.id}")
                    ) {
                        StickerOverlayVisual(sticker = sticker, minDimPx = minDimPx)
                    }
                }

                is RenderLayerItem.TextLayer -> {
                    val item = layer.item
                    val currentText by rememberUpdatedState(item)
                    val cx = item.xNorm * widthPx
                    val cy = item.yNorm * heightPx
                    Box(
                        modifier = Modifier
                            .zIndex(layerZ)
                            .offset { IntOffset(cx.roundToInt(), cy.roundToInt()) }
                            .graphicsLayer {
                                translationX = -size.width / 2f
                                translationY = -size.height / 2f
                                scaleX = item.scale
                                scaleY = item.scale
                                rotationZ = item.rotationDeg
                            }
                            .then(
                                if (interactive && !isDrawingMode) {
                                    Modifier.pointerInput(item.id, widthPx, heightPx) {
                                        handleOverlayTransformGestures(
                                            canvasW = widthPx,
                                            canvasH = heightPx,
                                            getXNorm = { currentText.xNorm },
                                            getYNorm = { currentText.yNorm },
                                            getScale = { currentText.scale },
                                            getRotation = { currentText.rotationDeg },
                                            onBringToFront = { onBringTextToFront?.invoke(currentText.id) },
                                            onTap = {
                                                onBringTextToFront?.invoke(currentText.id)
                                                onEditTextTap?.invoke(currentText)
                                            },
                                            onUpdate = { nx, ny, ns, nr ->
                                                onTransformText?.invoke(currentText.id, nx, ny, ns, nr)
                                            },
                                            onDragState = { dragging, ny, overTrash ->
                                                onDragStateChange?.invoke(dragging, ny, overTrash, currentText.id, true)
                                            }
                                        )
                                    }
                                } else {
                                    Modifier
                                }
                            )
                            .testTag("canvas_text_${item.id}")
                    ) {
                        TextOverlayVisual(item = item, maxWidthPx = widthPx, minDimPx = minDimPx)
                    }
                }
            }
        }
    }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.handleOverlayTransformGestures(
    canvasW: Float,
    canvasH: Float,
    getXNorm: () -> Float,
    getYNorm: () -> Float,
    getScale: () -> Float,
    getRotation: () -> Float,
    onBringToFront: () -> Unit,
    onTap: () -> Unit,
    onUpdate: (xNorm: Float, yNorm: Float, scale: Float, rotationDeg: Float) -> Unit,
    onDragState: (isDragging: Boolean, yNorm: Float, isOverTrash: Boolean) -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        onBringToFront()
        var totalMovementPx = 0f
        var startedDrag = false
        var xNorm = getXNorm()
        var yNorm = getYNorm()
        var scale = getScale()
        var rotation = getRotation()
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop

        while (true) {
            val event = awaitPointerEvent()
            val pressedCount = event.changes.count { it.pressed }
            if (pressedCount == 0) {
                break
            }
            val zoomChange = event.calculateZoom()
            val panChange = event.calculatePan()
            val rotChange = event.calculateRotation()
            val centroidSize = event.calculateCentroidSize(useCurrent = false)
            val rotMotion = abs(rotChange) * (PI.toFloat() / 180f) * centroidSize
            val zoomMotion = abs(1f - zoomChange) * centroidSize
            val panMag = panChange.getDistance()

            if (!pastTouchSlop) {
                totalMovementPx += panMag + zoomMotion + rotMotion
                if (totalMovementPx > touchSlop || pressedCount > 1) {
                    pastTouchSlop = true
                    startedDrag = true
                }
            }

            if (pastTouchSlop) {
                if (zoomChange != 1f || rotChange != 0f || panChange != Offset.Zero) {
                    xNorm = (xNorm + panChange.x / canvasW).coerceIn(0.05f, 0.95f)
                    yNorm = (yNorm + panChange.y / canvasH).coerceIn(0.05f, 0.96f)
                    scale = (scale * zoomChange).coerceIn(0.45f, 3.6f)
                    rotation += rotChange
                    onUpdate(xNorm, yNorm, scale, rotation)
                }
                val overTrash = pressedCount == 1 &&
                    yNorm >= 0.80f &&
                    xNorm in 0.34f..0.66f
                onDragState(true, yNorm, overTrash)
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        }

        val wasOverTrash = startedDrag && yNorm >= 0.80f && xNorm in 0.34f..0.66f
        if (startedDrag) {
            onDragState(false, yNorm, wasOverTrash)
        } else {
            onTap()
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSingleStroke(
    stroke: StatusDrawingStroke,
    canvasW: Float,
    canvasH: Float
) {
    if (stroke.points.isEmpty()) return
    val minDim = min(canvasW, canvasH)
    val strokePx = (stroke.strokeWidthNorm * minDim).coerceAtLeast(3f)
    val blendMode = if (stroke.isEraser) BlendMode.Clear else BlendMode.SrcOver
    val strokeColor = if (stroke.isEraser) Color.Transparent else Color(stroke.colorArgb)

    if (stroke.points.size == 1) {
        val pt = stroke.points.first()
        drawCircle(
            color = strokeColor,
            radius = strokePx * 0.5f,
            center = Offset(pt.xNorm * canvasW, pt.yNorm * canvasH),
            blendMode = blendMode
        )
        return
    }

    val path = Path().apply {
        stroke.points.forEachIndexed { idx, pt ->
            val x = pt.xNorm * canvasW
            val y = pt.yNorm * canvasH
            if (idx == 0) moveTo(x, y) else lineTo(x, y)
        }
    }
    drawPath(
        path = path,
        color = strokeColor,
        style = Stroke(
            width = strokePx,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        ),
        blendMode = blendMode
    )
}

@Composable
private fun StickerOverlayVisual(
    sticker: StatusStickerOverlay,
    minDimPx: Float
) {
    val density = LocalDensity.current
    when (sticker.kind) {
        StatusStickerKind.EMOJI -> {
            val spSize = with(density) { (minDimPx * 0.145f).coerceAtLeast(42f).toSp() }
            Text(
                text = sticker.value,
                fontSize = spSize
            )
        }
        StatusStickerKind.TIME_BADGE,
        StatusStickerKind.DATE_BADGE,
        StatusStickerKind.FAMILY_BADGE -> {
            val prefix = when (sticker.kind) {
                StatusStickerKind.TIME_BADGE -> "🕒 "
                StatusStickerKind.DATE_BADGE -> "📅 "
                StatusStickerKind.FAMILY_BADGE -> "👨‍👩‍👧‍👦 "
                else -> ""
            }
            val bgColor = if (sticker.kind == StatusStickerKind.FAMILY_BADGE) {
                Color(0xE82F7FE0)
            } else {
                Color(0xDC151B21)
            }
            val spSize = with(density) { (minDimPx * 0.048f).coerceAtLeast(22f).toSp() }
            Surface(
                shape = RoundedCornerShape(50),
                color = bgColor,
                border = BorderStroke(1.2.dp, Color.White.copy(alpha = 0.48f))
            ) {
                Text(
                    text = "$prefix${sticker.value}",
                    fontFamily = InterFontFamily,
                    fontSize = spSize,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun TextOverlayVisual(
    item: StatusTextOverlay,
    maxWidthPx: Float,
    minDimPx: Float
) {
    val density = LocalDensity.current
    val spSize = with(density) { (minDimPx * 0.068f).coerceAtLeast(26f).toSp() }
    val maxBoxWidthDp = with(density) { (maxWidthPx * 0.78f).toDp() }
    val fontFamily = when (item.style) {
        StatusTextStyle.MONOSPACE -> FontFamily.Monospace
        else -> InterFontFamily
    }
    val fontWeight = when (item.style) {
        StatusTextStyle.BOLD, StatusTextStyle.MONOSPACE -> FontWeight.Bold
        else -> FontWeight.Normal
    }
    val fontStyle = if (item.style == StatusTextStyle.ITALIC) FontStyle.Italic else FontStyle.Normal
    val textAlign = when (item.align) {
        StatusTextAlign.LEFT -> TextAlign.Start
        StatusTextAlign.CENTER -> TextAlign.Center
        StatusTextAlign.RIGHT -> TextAlign.End
    }

    val rawColor = Color(item.colorArgb)
    val resolvedBg = if (item.hasBackground) {
        if (item.colorArgb == 0xFFFFFFFFL) Color(0xDC151B21) else rawColor.copy(alpha = 0.92f)
    } else {
        Color.Transparent
    }
    val resolvedTextColor = if (item.hasBackground && item.colorArgb != 0xFFFFFFFFL) {
        val luma = 0.299f * rawColor.red + 0.587f * rawColor.green + 0.114f * rawColor.blue
        if (luma > 0.72f) Color(0xFF111827) else Color.White
    } else {
        rawColor
    }

    Box(
        modifier = Modifier
            .widthIn(max = maxBoxWidthDp)
            .background(resolvedBg, RoundedCornerShape(14.dp))
            .padding(
                horizontal = if (item.hasBackground) 14.dp else 4.dp,
                vertical = if (item.hasBackground) 8.dp else 2.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = item.text,
            style = TextStyle(
                fontFamily = fontFamily,
                fontSize = spSize,
                fontWeight = fontWeight,
                fontStyle = fontStyle,
                color = resolvedTextColor,
                textAlign = textAlign,
                shadow = if (item.hasBackground) {
                    null
                } else {
                    Shadow(
                        color = Color.Black.copy(alpha = 0.68f),
                        offset = Offset(0f, 3f),
                        blurRadius = 8f
                    )
                }
            )
        )
    }
}
