package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Composites photo statuses (base photo + freehand strokes + stickers + texts) onto an offscreen
 * Canvas at full working resolution, then safely compresses to JPEG data URI within Firestore limits.
 */
object StatusPhotoCanvasRenderer {
    private const val MAX_CANVAS_SIDE_PX = 2048
    private const val MAX_OUTPUT_SIDE_PX = 1280
    private const val MAX_OUTPUT_BYTES = 420 * 1024

    suspend fun renderAndCompressPhoto(
        context: Context,
        photoUri: Uri,
        overlayBundle: StatusOverlayBundle?,
        viewportAspectRatio: Float = 0f,
        useContainWithBlur: Boolean = false
    ): String? = withContext(Dispatchers.IO) {
        if (overlayBundle == null || overlayBundle.isEmptyEdits()) {
            return@withContext ImageCompressionHelper.compressToDataUri(context, photoUri)
        }

        var orientedBitmap: Bitmap? = null
        var workingBitmap: Bitmap? = null
        var strokeBitmap: Bitmap? = null
        var scaledOutput: Bitmap? = null

        try {
            orientedBitmap = decodeOrientedPhotoBitmap(context, photoUri) ?: return@withContext null
            val w = orientedBitmap.width.coerceAtLeast(1)
            val h = orientedBitmap.height.coerceAtLeast(1)

            val mappedBundle = mapViewportOverlayToPhoto(
                bundle = overlayBundle,
                photoWidth = w,
                photoHeight = h,
                viewportAspectRatio = viewportAspectRatio,
                useContainWithBlur = useContainWithBlur
            )

            workingBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val mainCanvas = Canvas(workingBitmap)
            mainCanvas.drawBitmap(orientedBitmap, 0f, 0f, null)
            orientedBitmap.recycle()
            orientedBitmap = null

            val minDim = min(w, h).toFloat()

            // 1. Freehand strokes (drawn on an isolated transparent layer so erasers only clear ink)
            if (mappedBundle.strokes.isNotEmpty()) {
                strokeBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val strokeCanvas = Canvas(strokeBitmap)
                val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                }
                val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }

                mappedBundle.strokes.forEach { stroke ->
                    if (stroke.points.isEmpty()) return@forEach
                    val p = if (stroke.isEraser) clearPaint else drawPaint
                    if (!stroke.isEraser) {
                        p.color = stroke.colorArgb.toInt()
                    }
                    p.strokeWidth = (stroke.strokeWidthNorm * minDim).coerceAtLeast(3f)
                    if (stroke.points.size == 1) {
                        val pt = stroke.points.first()
                        val dotPaint = Paint(p).apply { style = Paint.Style.FILL }
                        strokeCanvas.drawCircle(pt.xNorm * w, pt.yNorm * h, p.strokeWidth * 0.5f, dotPaint)
                    } else {
                        val path = Path()
                        stroke.points.forEachIndexed { idx, pt ->
                            val px = pt.xNorm * w
                            val py = pt.yNorm * h
                            if (idx == 0) path.moveTo(px, py) else path.lineTo(px, py)
                        }
                        strokeCanvas.drawPath(path, p)
                    }
                }
                mainCanvas.drawBitmap(strokeBitmap, 0f, 0f, null)
                strokeBitmap.recycle()
                strokeBitmap = null
            }

            // 2. Merged z-ordered stickers and texts
            val orderedLayers = buildList<OrderedPhotoLayer> {
                mappedBundle.stickers.forEach { add(OrderedPhotoLayer.Sticker(it)) }
                mappedBundle.texts.forEach { add(OrderedPhotoLayer.Text(it)) }
            }.sortedBy { it.z }

            orderedLayers.forEach { layer ->
                when (layer) {
                    is OrderedPhotoLayer.Sticker -> drawStickerOverlay(mainCanvas, layer.item, w, h, minDim)
                    is OrderedPhotoLayer.Text -> drawTextOverlay(mainCanvas, layer.item, w, h, minDim)
                }
            }

            val outScale = MAX_OUTPUT_SIDE_PX.toFloat() / max(w, h).toFloat()
            val finalBitmap = if (outScale < 1f) {
                Bitmap.createScaledBitmap(
                    workingBitmap,
                    (w * outScale).toInt().coerceAtLeast(1),
                    (h * outScale).toInt().coerceAtLeast(1),
                    true
                ).also {
                    scaledOutput = it
                    workingBitmap?.recycle()
                    workingBitmap = null
                }
            } else {
                workingBitmap!!
            }

            var quality = 86
            val baos = ByteArrayOutputStream()
            do {
                baos.reset()
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                quality -= 10
            } while (baos.size() > MAX_OUTPUT_BYTES && quality >= 34)

            val encoded = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            "data:image/jpeg;base64,$encoded"
        } catch (_: Exception) {
            null
        } finally {
            runCatching { orientedBitmap?.takeIf { !it.isRecycled }?.recycle() }
            runCatching { workingBitmap?.takeIf { !it.isRecycled }?.recycle() }
            runCatching { strokeBitmap?.takeIf { !it.isRecycled }?.recycle() }
            runCatching { scaledOutput?.takeIf { !it.isRecycled }?.recycle() }
        }
    }

    private fun drawStickerOverlay(
        canvas: Canvas,
        sticker: StatusStickerOverlay,
        w: Int,
        h: Int,
        minDim: Float
    ) {
        val cx = sticker.xNorm * w
        val cy = sticker.yNorm * h
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(sticker.rotationDeg)
        canvas.scale(sticker.scale, sticker.scale)

        when (sticker.kind) {
            StatusStickerKind.EMOJI -> {
                val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = (minDim * 0.145f).coerceAtLeast(36f)
                    textAlign = Paint.Align.CENTER
                }
                val fm = paint.fontMetrics
                val yOffset = -(fm.ascent + fm.descent) / 2f
                canvas.drawText(sticker.value, 0f, yOffset, paint)
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
                val label = "$prefix${sticker.value}"
                val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = (minDim * 0.048f).coerceAtLeast(20f)
                    color = android.graphics.Color.WHITE
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                val textWidth = textPaint.measureText(label)
                val padX = textPaint.textSize * 0.72f
                val padY = textPaint.textSize * 0.42f
                val fm = textPaint.fontMetrics
                val textH = fm.descent - fm.ascent
                val rect = RectF(
                    -(textWidth / 2f + padX),
                    -(textH / 2f + padY),
                    textWidth / 2f + padX,
                    textH / 2f + padY
                )
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (sticker.kind == StatusStickerKind.FAMILY_BADGE) {
                        android.graphics.Color.argb(232, 47, 127, 224)
                    } else {
                        android.graphics.Color.argb(220, 21, 27, 33)
                    }
                }
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = max(2f, minDim * 0.004f)
                    color = android.graphics.Color.argb(120, 255, 255, 255)
                }
                val radius = rect.height() / 2f
                canvas.drawRoundRect(rect, radius, radius, bgPaint)
                canvas.drawRoundRect(rect, radius, radius, borderPaint)
                val yOffset = -(fm.ascent + fm.descent) / 2f
                canvas.drawText(label, 0f, yOffset, textPaint)
            }
        }
        canvas.restore()
    }

    private fun drawTextOverlay(
        canvas: Canvas,
        item: StatusTextOverlay,
        w: Int,
        h: Int,
        minDim: Float
    ) {
        val cx = item.xNorm * w
        val cy = item.yNorm * h
        val textSizePx = (minDim * 0.068f).coerceAtLeast(24f)
        val typeface = when (item.style) {
            StatusTextStyle.NORMAL -> Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            StatusTextStyle.BOLD -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            StatusTextStyle.ITALIC -> Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
            StatusTextStyle.MONOSPACE -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = textSizePx
            this.typeface = typeface
            val rawColor = item.colorArgb.toInt()
            this.color = if (item.hasBackground && item.colorArgb == 0xFFFFFFFFL) {
                android.graphics.Color.WHITE
            } else {
                rawColor
            }
            if (!item.hasBackground) {
                setShadowLayer(textSizePx * 0.16f, 0f, textSizePx * 0.05f, android.graphics.Color.argb(170, 0, 0, 0))
            }
        }

        val maxLayoutW = (w * 0.78f).toInt().coerceAtLeast(120)
        val alignment = when (item.align) {
            StatusTextAlign.LEFT -> Layout.Alignment.ALIGN_NORMAL
            StatusTextAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
            StatusTextAlign.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
        }

        val layout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(item.text, 0, item.text.length, textPaint, maxLayoutW)
                .setAlignment(alignment)
                .setIncludePad(false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(item.text, textPaint, maxLayoutW, alignment, 1.0f, 0f, false)
        }

        var maxLineW = 0f
        for (i in 0 until layout.lineCount) {
            maxLineW = max(maxLineW, layout.getLineWidth(i))
        }
        val contentW = maxLineW.coerceAtLeast(40f)
        val contentH = layout.height.toFloat().coerceAtLeast(textSizePx)

        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(item.rotationDeg)
        canvas.scale(item.scale, item.scale)

        if (item.hasBackground) {
            val isWhiteText = (item.colorArgb == 0xFFFFFFFFL)
            val bgColor = if (isWhiteText) {
                android.graphics.Color.argb(220, 21, 27, 33)
            } else {
                val c = item.colorArgb.toInt()
                android.graphics.Color.argb(
                    232,
                    android.graphics.Color.red(c),
                    android.graphics.Color.green(c),
                    android.graphics.Color.blue(c)
                )
            }
            if (!isWhiteText) {
                val r = android.graphics.Color.red(bgColor)
                val g = android.graphics.Color.green(bgColor)
                val b = android.graphics.Color.blue(bgColor)
                val luma = 0.299 * r + 0.587 * g + 0.114 * b
                textPaint.color = if (luma > 185) android.graphics.Color.rgb(17, 24, 39) else android.graphics.Color.WHITE
            }
            val padX = textSizePx * 0.55f
            val padY = textSizePx * 0.34f
            val rect = RectF(
                -(contentW / 2f + padX),
                -(contentH / 2f + padY),
                contentW / 2f + padX,
                contentH / 2f + padY
            )
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor }
            val corner = textSizePx * 0.42f
            canvas.drawRoundRect(rect, corner, corner, bgPaint)
        }

        canvas.translate(-maxLayoutW / 2f, -contentH / 2f)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun decodeOrientedPhotoBitmap(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var rotationDegrees = 0f
        var flipHorizontal = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { exifStream ->
                    val exif = ExifInterface(exifStream)
                    when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> rotationDegrees = 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> rotationDegrees = 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> rotationDegrees = 270f
                        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> flipHorizontal = true
                        ExifInterface.ORIENTATION_TRANSPOSE -> {
                            rotationDegrees = 90f
                            flipHorizontal = true
                        }
                        ExifInterface.ORIENTATION_TRANSVERSE -> {
                            rotationDegrees = 270f
                            flipHorizontal = true
                        }
                    }
                }
            }
        }

        var sampleSize = 1
        val maxSide = max(bounds.outWidth, bounds.outHeight)
        while (maxSide / sampleSize > MAX_CANVAS_SIDE_PX * 2) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val raw = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: return null

        val oriented = if (rotationDegrees != 0f || flipHorizontal) {
            val matrix = Matrix().apply {
                if (flipHorizontal) preScale(-1f, 1f)
                if (rotationDegrees != 0f) postRotate(rotationDegrees)
            }
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
                .also { if (it != raw) raw.recycle() }
        } else {
            raw
        }

        val scale = MAX_CANVAS_SIDE_PX.toFloat() / max(oriented.width, oriented.height).coerceAtLeast(1)
        return if (scale < 1f) {
            Bitmap.createScaledBitmap(
                oriented,
                (oriented.width * scale).toInt().coerceAtLeast(1),
                (oriented.height * scale).toInt().coerceAtLeast(1),
                true
            ).also { if (it != oriented) oriented.recycle() }
        } else {
            oriented
        }
    }

    /**
     * Converts overlay coordinates normalized in the Studio viewport (`0..1`) into coordinates
     * normalized in the oriented photo bitmap (`0..1`), accounting for `ContentScale.Crop` vs
     * letterboxed `ContentScale.Fit` and relative min-dimension scaling.
     */
    internal fun mapViewportOverlayToPhoto(
        bundle: StatusOverlayBundle,
        photoWidth: Int,
        photoHeight: Int,
        viewportAspectRatio: Float,
        useContainWithBlur: Boolean
    ): StatusOverlayBundle {
        if (photoWidth <= 0 || photoHeight <= 0 || viewportAspectRatio <= 0.05f || viewportAspectRatio.isNaN()) {
            return bundle
        }
        val imgRatio = photoWidth.toFloat() / photoHeight.toFloat()
        val vpRatio = viewportAspectRatio

        val dispW: Float
        val dispH: Float
        if (useContainWithBlur) {
            if (imgRatio > vpRatio) {
                dispW = 1f
                dispH = (vpRatio / imgRatio).coerceAtLeast(0.05f)
            } else {
                dispW = (imgRatio / vpRatio).coerceAtLeast(0.05f)
                dispH = 1f
            }
        } else {
            if (imgRatio > vpRatio) {
                dispW = (imgRatio / vpRatio).coerceAtLeast(0.05f)
                dispH = 1f
            } else {
                dispW = 1f
                dispH = (vpRatio / imgRatio).coerceAtLeast(0.05f)
            }
        }

        val vpMinRatio = min(vpRatio, 1f)
        val renderedMinRatio = min(dispW * vpRatio, dispH).coerceAtLeast(0.05f)
        val scaleFactor = (vpMinRatio / renderedMinRatio).coerceIn(0.35f, 3.5f)

        fun mapPointX(vx: Float): Float = 0.5f + (vx - 0.5f) / dispW
        fun mapPointY(vy: Float): Float = 0.5f + (vy - 0.5f) / dispH

        return bundle.copy(
            texts = bundle.texts.map { t ->
                t.copy(
                    xNorm = mapPointX(t.xNorm).coerceIn(0.03f, 0.97f),
                    yNorm = mapPointY(t.yNorm).coerceIn(0.03f, 0.97f),
                    scale = (t.scale * scaleFactor).coerceIn(0.25f, 6.0f)
                )
            },
            stickers = bundle.stickers.map { st ->
                st.copy(
                    xNorm = mapPointX(st.xNorm).coerceIn(0.03f, 0.97f),
                    yNorm = mapPointY(st.yNorm).coerceIn(0.03f, 0.97f),
                    scale = (st.scale * scaleFactor).coerceIn(0.25f, 6.0f)
                )
            },
            strokes = bundle.strokes.map { stroke ->
                stroke.copy(
                    strokeWidthNorm = (stroke.strokeWidthNorm * scaleFactor).coerceIn(0.002f, 0.08f),
                    points = stroke.points.map { pt ->
                        StatusDrawingPoint(
                            xNorm = mapPointX(pt.xNorm).coerceIn(-0.5f, 1.5f),
                            yNorm = mapPointY(pt.yNorm).coerceIn(-0.5f, 1.5f)
                        )
                    }
                )
            }
        )
    }
}

private sealed class OrderedPhotoLayer(val z: Int) {
    data class Sticker(val item: StatusStickerOverlay) : OrderedPhotoLayer(item.zOrder)
    data class Text(val item: StatusTextOverlay) : OrderedPhotoLayer(item.zOrder)
}
