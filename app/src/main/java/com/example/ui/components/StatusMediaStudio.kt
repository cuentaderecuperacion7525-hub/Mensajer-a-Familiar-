package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RenderEffect
import android.graphics.Shader
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.FamilyMemberEntity
import com.example.ui.theme.InterFontFamily
import com.example.ui.viewmodel.StatusPublishUiState
import com.example.util.StatusDrawingPoint
import com.example.util.StatusDrawingStroke
import com.example.util.StatusEditorPalette
import com.example.util.StatusMusicCatalog
import com.example.util.StatusMusicTrack
import com.example.util.StatusOverlayBundle
import com.example.util.StatusStickerKind
import com.example.util.StatusStickerOverlay
import com.example.util.StatusStudioDraftItem
import com.example.util.StatusTextAlign
import com.example.util.StatusTextOverlay
import com.example.util.StatusTextStyle
import com.example.util.VideoPreviewInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.max

private enum class ActiveStudioTool {
    NONE,
    TEXT,
    DRAW
}

private val StudioRow1PillBg = Color(0xFF151B21)
private val StudioRow2Bg = Color(0xFF0F151B)
private val StudioSendBlue = Color(0xFF2F7FE0)
private val StudioCircleBg = Color.White.copy(alpha = 0.14f)
private val StudioCircleBorder = Color.White.copy(alpha = 0.20f)
private const val MAX_STUDIO_COMMENT_CHARS = 200

@Composable
fun StatusMediaStudioScreen(
    drafts: List<StatusStudioDraftItem>,
    activeIndex: Int,
    activeDraft: StatusStudioDraftItem,
    publishState: StatusPublishUiState,
    allMembers: List<FamilyMemberEntity>,
    activeMemberId: String,
    favoriteMusicTracks: List<StatusMusicTrack>,
    onToggleFavoriteMusicTrack: (StatusMusicTrack) -> Unit,
    musicSearchCatalog: suspend (String) -> Result<List<StatusMusicTrack>> = StatusMusicCatalog::searchTracks,
    onSelectDraftIndex: (Int) -> Unit,
    onRemoveDraftAt: (Int) -> Unit,
    onUpdateActiveDraft: ((StatusStudioDraftItem) -> StatusStudioDraftItem) -> Unit,
    onRequestAddMedia: (isVideo: Boolean, fromCamera: Boolean) -> Unit,
    onRequestClose: () -> Unit,
    onDismissPublishError: () -> Unit,
    onPublishDrafts: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val isPublishing = publishState is StatusPublishUiState.Publishing
    val publishError = (publishState as? StatusPublishUiState.Error)?.message

    var activeTool by remember { mutableStateOf(ActiveStudioTool.NONE) }
    var showStickerSheet by remember { mutableStateOf(false) }
    var showMusicPickerSheet by remember { mutableStateOf(false) }
    var trimmingTrack by remember { mutableStateOf<StatusMusicTrack?>(null) }
    var showAudienceSheet by remember { mutableStateOf(false) }
    var showAddMediaMenu by remember { mutableStateOf(false) }
    var showMentionList by remember { mutableStateOf(false) }

    // Text tool session state
    var editingTextId by remember { mutableStateOf<String?>(null) }
    var textDraftValue by remember { mutableStateOf("") }
    var textDraftColor by remember { mutableStateOf(0xFFFFFFFFL) }
    var textDraftStyle by remember { mutableStateOf(StatusTextStyle.BOLD) }
    var textDraftAlign by remember { mutableStateOf(StatusTextAlign.CENTER) }
    var textDraftHasBg by remember { mutableStateOf(false) }
    val textEditorFocusRequester = remember { FocusRequester() }

    // Draw tool session state
    var drawSessionInitialStrokes by remember { mutableStateOf<List<StatusDrawingStroke>>(emptyList()) }
    var drawUndoStack by remember { mutableStateOf<List<List<StatusDrawingStroke>>>(emptyList()) }
    var drawRedoStack by remember { mutableStateOf<List<List<StatusDrawingStroke>>>(emptyList()) }
    var drawActiveStroke by remember { mutableStateOf<StatusDrawingStroke?>(null) }
    var drawColorArgb by remember { mutableStateOf(0xFFEF4444L) }
    var drawThicknessIdx by remember { mutableIntStateOf(1) }
    var drawIsEraser by remember { mutableStateOf(false) }

    // Drag-to-trash state
    var isDraggingOverlay by remember { mutableStateOf(false) }
    var isDragOverTrash by remember { mutableStateOf(false) }

    // Video preview state
    var videoViewRef by remember(activeDraft.id) { mutableStateOf<VideoView?>(null) }
    var videoPlayerRef by remember(activeDraft.id) { mutableStateOf<MediaPlayer?>(null) }
    var isVideoPlaying by remember(activeDraft.id) { mutableStateOf(true) }
    var isVideoPrepared by remember(activeDraft.id) { mutableStateOf(false) }

    // Music preview playback (loops within trimStartSec..trimStartSec + trimDurationSec)
    var musicPreviewPlayer by remember(activeDraft.id) { mutableStateOf<MediaPlayer?>(null) }
    var musicPreparedKey by remember(activeDraft.id) { mutableStateOf<String?>(null) }
    var isMusicPlaying by remember(activeDraft.id) { mutableStateOf(true) }

    val isOverlayModalOpen = showStickerSheet || showMusicPickerSheet || trimmingTrack != null ||
        showAudienceSheet || showAddMediaMenu

    // Measure photo aspect ratio cleanly (off-UI) so we automatically switch between cover & contain+blur
    var photoAspectRatio by remember(activeDraft.uri, activeDraft.isVideo) { mutableFloatStateOf(0f) }
    var blurFallbackBitmap by remember(activeDraft.uri, activeDraft.isVideo) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(activeDraft.uri, activeDraft.isVideo) {
        if (!activeDraft.isVideo) {
            val measured = withContext(Dispatchers.IO) {
                runCatching {
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(activeDraft.uri)?.use {
                        BitmapFactory.decodeStream(it, null, opts)
                    }
                    val ratio = if (opts.outWidth > 0 && opts.outHeight > 0) {
                        opts.outWidth.toFloat() / opts.outHeight.toFloat()
                    } else {
                        0f
                    }
                    // Tiny thumbnail bitmap solely for background blur fallback on pre-Android 12
                    val blurOpts = BitmapFactory.Options().apply { inSampleSize = 16 }
                    val tiny = context.contentResolver.openInputStream(activeDraft.uri)?.use {
                        BitmapFactory.decodeStream(it, null, blurOpts)
                    }
                    ratio to tiny
                }.getOrDefault(0f to null)
            }
            photoAspectRatio = measured.first
            blurFallbackBitmap = measured.second
        } else {
            val vThumb = activeDraft.videoPreviewInfo?.thumbnailBitmap
            if (vThumb != null && vThumb.height > 0) {
                photoAspectRatio = vThumb.width.toFloat() / vThumb.height.toFloat()
            }
        }
    }

    DisposableEffect(activeDraft.uri) {
        onDispose {
            val bmp = blurFallbackBitmap
            blurFallbackBitmap = null
            if (bmp != null && !bmp.isRecycled) {
                runCatching { bmp.recycle() }
            }
        }
    }

    // Sync video mute when music track specifies muteOriginalVideo
    val muteVideoAudio = activeDraft.musicTrack?.muteOriginalVideo == true
    LaunchedEffect(videoPlayerRef, isVideoPrepared, muteVideoAudio) {
        val mp = videoPlayerRef ?: return@LaunchedEffect
        val vol = if (muteVideoAudio) 0f else 1f
        runCatching { mp.setVolume(vol, vol) }
    }

    // Music player setup & trimmed loop
    val activeTrack = activeDraft.musicTrack
    val shouldPlayMusic = activeTrack != null &&
        activeTool == ActiveStudioTool.NONE &&
        !isOverlayModalOpen &&
        !isPublishing &&
        isMusicPlaying &&
        (!activeDraft.isVideo || isVideoPlaying)

    DisposableEffect(activeDraft.id, activeTrack?.previewUrl) {
        val track = activeTrack
        var localMp: MediaPlayer? = null
        if (track != null && track.previewUrl.isNotBlank()) {
            val mp = MediaPlayer()
            localMp = mp
            musicPreviewPlayer = mp
            musicPreparedKey = null
            runCatching {
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                mp.setDataSource(track.previewUrl)
                mp.isLooping = false
                mp.setOnPreparedListener { ready ->
                    if (musicPreviewPlayer === ready) {
                        musicPreparedKey = track.previewUrl
                        runCatching { ready.seekTo(track.trimStartSec * 1000) }
                        if (shouldPlayMusic) runCatching { ready.start() }
                    }
                }
                mp.setOnErrorListener { _, _, _ -> true }
                mp.prepareAsync()
            }.onFailure {
                runCatching { mp.release() }
                if (musicPreviewPlayer === mp) musicPreviewPlayer = null
            }
        }
        onDispose {
            val target = localMp ?: musicPreviewPlayer
            if (musicPreviewPlayer === target) {
                musicPreviewPlayer = null
                musicPreparedKey = null
            }
            if (target != null) {
                runCatching { if (target.isPlaying) target.stop() }
                runCatching { target.reset() }
                runCatching { target.release() }
            }
        }
    }

    LaunchedEffect(
        shouldPlayMusic,
        musicPreparedKey,
        activeTrack?.trimStartSec,
        activeTrack?.trimDurationSec
    ) {
        val mp = musicPreviewPlayer ?: return@LaunchedEffect
        val track = activeTrack ?: return@LaunchedEffect
        if (musicPreparedKey != track.previewUrl) return@LaunchedEffect
        val startMs = track.trimStartSec.coerceAtLeast(0) * 1000
        val endMs = (track.trimStartSec + track.trimDurationSec).coerceAtLeast(track.trimStartSec + 5) * 1000
        if (!shouldPlayMusic) {
            runCatching { if (mp.isPlaying) mp.pause() }
            return@LaunchedEffect
        }
        runCatching {
            if (mp.currentPosition < startMs || mp.currentPosition >= endMs) {
                mp.seekTo(startMs)
            }
            if (!mp.isPlaying) mp.start()
        }
        while (shouldPlayMusic) {
            runCatching {
                if (mp.currentPosition >= endMs) {
                    mp.seekTo(startMs)
                }
            }
            delay(150L)
        }
    }

    fun openTextEditor(existing: StatusTextOverlay? = null) {
        showMentionList = false
        if (existing != null) {
            editingTextId = existing.id
            textDraftValue = existing.text
            textDraftColor = existing.colorArgb
            textDraftStyle = existing.style
            textDraftAlign = existing.align
            textDraftHasBg = existing.hasBackground
        } else {
            editingTextId = null
            textDraftValue = ""
            textDraftColor = 0xFFFFFFFFL
            textDraftStyle = StatusTextStyle.BOLD
            textDraftAlign = StatusTextAlign.CENTER
            textDraftHasBg = false
        }
        activeTool = ActiveStudioTool.TEXT
    }

    fun commitTextEditor() {
        keyboardController?.hide()
        focusManager.clearFocus()
        val trimmed = textDraftValue.trim()
        val targetId = editingTextId
        if (trimmed.isNotEmpty()) {
            onUpdateActiveDraft { cur ->
                val maxZ = maxOf(
                    cur.overlays.texts.maxOfOrNull { it.zOrder } ?: 0,
                    cur.overlays.stickers.maxOfOrNull { it.zOrder } ?: 0
                ) + 1
                val updatedTexts = if (targetId != null && cur.overlays.texts.any { it.id == targetId }) {
                    cur.overlays.texts.map { item ->
                        if (item.id == targetId) {
                            item.copy(
                                text = trimmed,
                                colorArgb = textDraftColor,
                                style = textDraftStyle,
                                align = textDraftAlign,
                                hasBackground = textDraftHasBg,
                                zOrder = maxZ
                            )
                        } else {
                            item
                        }
                    }
                } else {
                    cur.overlays.texts + StatusTextOverlay(
                        id = "txt_${UUID.randomUUID().toString().take(8)}",
                        text = trimmed,
                        xNorm = 0.5f,
                        yNorm = 0.44f,
                        colorArgb = textDraftColor,
                        style = textDraftStyle,
                        align = textDraftAlign,
                        hasBackground = textDraftHasBg,
                        zOrder = maxZ
                    )
                }
                cur.copy(overlays = cur.overlays.copy(texts = updatedTexts))
            }
        }
        editingTextId = null
        activeTool = ActiveStudioTool.NONE
    }

    fun cancelTextEditor() {
        keyboardController?.hide()
        focusManager.clearFocus()
        editingTextId = null
        activeTool = ActiveStudioTool.NONE
    }

    fun openDrawTool() {
        showMentionList = false
        focusManager.clearFocus()
        keyboardController?.hide()
        drawSessionInitialStrokes = activeDraft.overlays.strokes
        drawUndoStack = emptyList()
        drawRedoStack = emptyList()
        drawActiveStroke = null
        drawIsEraser = false
        activeTool = ActiveStudioTool.DRAW
    }

    fun cancelDrawTool() {
        drawActiveStroke = null
        onUpdateActiveDraft { cur ->
            cur.copy(overlays = cur.overlays.copy(strokes = drawSessionInitialStrokes))
        }
        activeTool = ActiveStudioTool.NONE
    }

    fun commitDrawTool() {
        drawActiveStroke = null
        activeTool = ActiveStudioTool.NONE
    }

    BackHandler(enabled = true) {
        when {
            showMentionList -> showMentionList = false
            activeTool == ActiveStudioTool.TEXT -> cancelTextEditor()
            activeTool == ActiveStudioTool.DRAW -> cancelDrawTool()
            !isOverlayModalOpen -> onRequestClose()
        }
    }

    // Track current audience across drafts
    val currentAudienceIds = activeDraft.overlays.audienceMemberIds
    val mentionCandidates = remember(allMembers, activeMemberId) {
        allMembers.filter { it.id != activeMemberId }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("status_media_studio")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. FULLSCREEN MEDIA + OVERLAY VIEWPORT (weight(1f) so bottom 2-row bar stays anchored cleanly)
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black)
                    .clipToBounds()
            ) {
                val vpRatio = if (maxHeight > 0.dp) maxWidth.value / maxHeight.value else 9f / 16f
                val ratioDiff = if (photoAspectRatio > 0f) {
                    max(photoAspectRatio / vpRatio, vpRatio / photoAspectRatio)
                } else {
                    1f
                }
                val useContainWithBlur = ratioDiff > 1.42f

                // Media Background + Main Layer
                if (activeDraft.isVideo) {
                    val vThumb = activeDraft.videoPreviewInfo?.thumbnailBitmap
                    if (useContainWithBlur && vThumb != null && !vThumb.isRecycled) {
                        BlurredMediaBackground(
                            bitmap = vThumb,
                            uri = null
                        )
                    }
                    key(activeDraft.uri) {
                        AndroidView(
                            factory = { ctx ->
                                FrameLayout(ctx).apply {
                                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                    val vv = VideoView(ctx).apply {
                                        layoutParams = FrameLayout.LayoutParams(
                                            FrameLayout.LayoutParams.MATCH_PARENT,
                                            FrameLayout.LayoutParams.MATCH_PARENT,
                                            android.view.Gravity.CENTER
                                        )
                                        setVideoURI(activeDraft.uri)
                                        setOnPreparedListener { mp ->
                                            videoPlayerRef = mp
                                            isVideoPrepared = true
                                            mp.isLooping = true
                                            val vol = if (muteVideoAudio) 0f else 1f
                                            mp.setVolume(vol, vol)
                                            if (isVideoPlaying) start()
                                        }
                                        setOnErrorListener { _, _, _ -> true }
                                    }
                                    videoViewRef = vv
                                    addView(vv)
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    enabled = activeTool == ActiveStudioTool.NONE,
                                    indication = null,
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                                ) {
                                    val vv = videoViewRef ?: return@clickable
                                    if (isVideoPlaying) {
                                        isVideoPlaying = false
                                        runCatching { if (vv.isPlaying) vv.pause() }
                                    } else {
                                        isVideoPlaying = true
                                        runCatching { if (!vv.isPlaying) vv.start() }
                                    }
                                }
                        )
                    }
                    if (!isVideoPrepared && vThumb != null && !vThumb.isRecycled) {
                        Image(
                            bitmap = remember(vThumb) { vThumb.asImageBitmap() },
                            contentDescription = "Vista previa de video",
                            contentScale = if (useContainWithBlur) ContentScale.Fit else ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    if (useContainWithBlur) {
                        BlurredMediaBackground(
                            bitmap = blurFallbackBitmap,
                            uri = activeDraft.uri
                        )
                    }
                    val req = remember(activeDraft.uri) {
                        ImageRequest.Builder(context)
                            .data(activeDraft.uri)
                            .crossfade(false)
                            .build()
                    }
                    AsyncImage(
                        model = req,
                        contentDescription = "Imagen del estado",
                        contentScale = if (useContainWithBlur) ContentScale.Fit else ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Interactive Canvas Overlay Layer (Drawings + Stickers + Texts)
                StatusOverlayRenderBox(
                    bundle = activeDraft.overlays,
                    activeInProgressStroke = drawActiveStroke,
                    isDrawingMode = activeTool == ActiveStudioTool.DRAW,
                    interactive = activeTool == ActiveStudioTool.NONE || activeTool == ActiveStudioTool.DRAW,
                    hiddenTextId = if (activeTool == ActiveStudioTool.TEXT) editingTextId else null,
                    modifier = Modifier.fillMaxSize(),
                    onStrokeStart = { pt ->
                        val thickness = StatusEditorPalette.brushThicknessNorms[drawThicknessIdx].second
                        drawActiveStroke = StatusDrawingStroke(
                            id = "stk_${UUID.randomUUID().toString().take(8)}",
                            points = listOf(pt),
                            colorArgb = drawColorArgb,
                            strokeWidthNorm = thickness,
                            isEraser = drawIsEraser
                        )
                    },
                    onStrokeAppend = { pt ->
                        val cur = drawActiveStroke ?: return@StatusOverlayRenderBox
                        drawActiveStroke = cur.copy(points = cur.points + pt)
                    },
                    onStrokeEnd = {
                        val finished = drawActiveStroke
                        drawActiveStroke = null
                        if (finished != null && finished.points.isNotEmpty()) {
                            drawUndoStack = drawUndoStack + listOf(activeDraft.overlays.strokes)
                            drawRedoStack = emptyList()
                            onUpdateActiveDraft { cur ->
                                cur.copy(
                                    overlays = cur.overlays.copy(
                                        strokes = cur.overlays.strokes + finished
                                    )
                                )
                            }
                        }
                    },
                    onBringTextToFront = { id ->
                        onUpdateActiveDraft { cur ->
                            val maxZ = maxOf(
                                cur.overlays.texts.maxOfOrNull { it.zOrder } ?: 0,
                                cur.overlays.stickers.maxOfOrNull { it.zOrder } ?: 0
                            ) + 1
                            cur.copy(
                                overlays = cur.overlays.copy(
                                    texts = cur.overlays.texts.map { if (it.id == id) it.copy(zOrder = maxZ) else it }
                                )
                            )
                        }
                    },
                    onEditTextTap = { tapped -> openTextEditor(tapped) },
                    onTransformText = { id, nx, ny, ns, nr ->
                        onUpdateActiveDraft { cur ->
                            cur.copy(
                                overlays = cur.overlays.copy(
                                    texts = cur.overlays.texts.map {
                                        if (it.id == id) it.copy(xNorm = nx, yNorm = ny, scale = ns, rotationDeg = nr) else it
                                    }
                                )
                            )
                        }
                    },
                    onBringStickerToFront = { id ->
                        onUpdateActiveDraft { cur ->
                            val maxZ = maxOf(
                                cur.overlays.texts.maxOfOrNull { it.zOrder } ?: 0,
                                cur.overlays.stickers.maxOfOrNull { it.zOrder } ?: 0
                            ) + 1
                            cur.copy(
                                overlays = cur.overlays.copy(
                                    stickers = cur.overlays.stickers.map { if (it.id == id) it.copy(zOrder = maxZ) else it }
                                )
                            )
                        }
                    },
                    onTransformSticker = { id, nx, ny, ns, nr ->
                        onUpdateActiveDraft { cur ->
                            cur.copy(
                                overlays = cur.overlays.copy(
                                    stickers = cur.overlays.stickers.map {
                                        if (it.id == id) it.copy(xNorm = nx, yNorm = ny, scale = ns, rotationDeg = nr) else it
                                    }
                                )
                            )
                        }
                    },
                    onDragStateChange = { dragging, _, overTrash, elementId, isText ->
                        isDraggingOverlay = dragging
                        isDragOverTrash = dragging && overTrash
                        if (!dragging && overTrash) {
                            onUpdateActiveDraft { cur ->
                                if (isText) {
                                    cur.copy(overlays = cur.overlays.copy(texts = cur.overlays.texts.filterNot { it.id == elementId }))
                                } else {
                                    cur.copy(overlays = cur.overlays.copy(stickers = cur.overlays.stickers.filterNot { it.id == elementId }))
                                }
                            }
                        }
                    }
                )

                // 2. TOP DARK GRADIENT (rgba 0,0,0,0.6 to transparent, 110.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.60f), Color.Transparent)
                            )
                        )
                )

                // TOP BAR + MUSIC TAG OR TOOL CONTROLS
                if (activeTool == ActiveStudioTool.NONE) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // 3. Top-left circular close (X), no top Publish button
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(StudioCircleBg)
                                    .border(1.dp, StudioCircleBorder, CircleShape)
                                    .clickable(enabled = !isPublishing) { onRequestClose() }
                                    .semantics { contentDescription = "Cerrar" }
                                    .testTag("status_close_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Cerrar",
                                    tint = Color.White,
                                    modifier = Modifier.size(21.dp)
                                )
                            }

                            // Center optional removable Music tag (Punto 7)
                            if (activeTrack != null) {
                                StudioTopMusicChip(
                                    track = activeTrack,
                                    isPlaying = isMusicPlaying,
                                    onTogglePlay = { isMusicPlaying = !isMusicPlaying },
                                    onEditTrim = { trimmingTrack = activeTrack },
                                    onRemove = {
                                        onUpdateActiveDraft { it.copy(musicTrack = null) }
                                    },
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .padding(horizontal = 10.dp)
                                )
                            }
                        }

                        if (activeDraft.isVideo && activeTrack != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color.Black.copy(alpha = 0.55f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .clip(RoundedCornerShape(50))
                                    .clickable {
                                        onUpdateActiveDraft { cur ->
                                            cur.musicTrack?.let { tr ->
                                                cur.copy(musicTrack = tr.copy(muteOriginalVideo = !tr.muteOriginalVideo))
                                            } ?: cur
                                        }
                                    }
                                    .testTag("status_video_mute_toggle")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (muteVideoAudio) Icons.AutoMirrored.Filled.VolumeOff
                                        else Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = null,
                                        tint = if (muteVideoAudio) Color(0xFFFCA5A5) else Color(0xFF4ADE80),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = if (muteVideoAudio) "Audio original silenciado" else "Audio original activo",
                                        fontFamily = InterFontFamily,
                                        fontSize = 11.5.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    // 4. Right vertical column (16.dp spacing): Texto, Sticker, Música, Dibujo
                    if (!isDraggingOverlay) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End))
                                .padding(top = 74.dp, end = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            StudioRightToolButton(
                                icon = Icons.Filled.Title,
                                label = "Texto",
                                testTag = "studio_tool_text",
                                onClick = { openTextEditor(null) }
                            )
                            StudioRightToolButton(
                                icon = Icons.Filled.EmojiEmotions,
                                label = "Sticker",
                                testTag = "studio_tool_sticker",
                                onClick = {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    showStickerSheet = true
                                }
                            )
                            StudioRightToolButton(
                                icon = Icons.Filled.MusicNote,
                                label = "Música",
                                testTag = "status_music_button",
                                isActive = activeTrack != null,
                                onClick = {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    showMusicPickerSheet = true
                                }
                            )
                            StudioRightToolButton(
                                icon = Icons.Filled.Brush,
                                label = "Dibujo",
                                testTag = "studio_tool_draw",
                                onClick = { openDrawTool() }
                            )
                        }
                    }

                    // Video play/pause center helper tap badge when paused
                    if (activeDraft.isVideo && !isVideoPlaying && !isDraggingOverlay) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(62.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f))
                                .border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                                .clickable {
                                    isVideoPlaying = true
                                    runCatching { videoViewRef?.start() }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Reproducir video",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                }

                // 9. TRASH DROP ZONE AT BOTTOM CENTER WHILE DRAGGING OVERLAY
                androidx.compose.animation.AnimatedVisibility(
                    visible = isDraggingOverlay,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 18.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isDragOverTrash) 64.dp else 54.dp)
                            .clip(CircleShape)
                            .background(
                                if (isDragOverTrash) Color(0xFFEF4444) else Color.Black.copy(alpha = 0.72f)
                            )
                            .border(
                                width = 1.5.dp,
                                color = if (isDragOverTrash) Color.White else Color.White.copy(alpha = 0.45f),
                                shape = CircleShape
                            )
                            .testTag("studio_drag_trash_zone"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Eliminar elemento",
                            tint = Color.White,
                            modifier = Modifier.size(if (isDragOverTrash) 30.dp else 25.dp)
                        )
                    }
                }

                // 5. TEXT TOOL OVERLAY EDITOR
                if (activeTool == ActiveStudioTool.TEXT) {
                    TextToolActiveOverlay(
                        text = textDraftValue,
                        colorArgb = textDraftColor,
                        style = textDraftStyle,
                        align = textDraftAlign,
                        hasBackground = textDraftHasBg,
                        focusRequester = textEditorFocusRequester,
                        onTextChange = { textDraftValue = it.take(240) },
                        onSelectColor = { textDraftColor = it },
                        onCycleStyle = {
                            val values = StatusTextStyle.entries
                            textDraftStyle = values[(textDraftStyle.ordinal + 1) % values.size]
                        },
                        onCycleAlign = {
                            val values = StatusTextAlign.entries
                            textDraftAlign = values[(textDraftAlign.ordinal + 1) % values.size]
                        },
                        onToggleBg = { textDraftHasBg = !textDraftHasBg },
                        onDiscard = ::cancelTextEditor,
                        onDone = ::commitTextEditor
                    )
                }

                // 8. DRAW TOOL CONTROLS OVERLAY
                if (activeTool == ActiveStudioTool.DRAW) {
                    DrawToolControlsOverlay(
                        selectedColor = drawColorArgb,
                        thicknessIdx = drawThicknessIdx,
                        isEraser = drawIsEraser,
                        canUndo = drawUndoStack.isNotEmpty(),
                        canRedo = drawRedoStack.isNotEmpty(),
                        hasStrokes = activeDraft.overlays.strokes.isNotEmpty(),
                        onSelectColor = {
                            drawIsEraser = false
                            drawColorArgb = it
                        },
                        onSelectThickness = { drawThicknessIdx = it },
                        onToggleEraser = { drawIsEraser = !drawIsEraser },
                        onUndo = {
                            val prev = drawUndoStack.lastOrNull() ?: return@DrawToolControlsOverlay
                            drawRedoStack = drawRedoStack + listOf(activeDraft.overlays.strokes)
                            drawUndoStack = drawUndoStack.dropLast(1)
                            onUpdateActiveDraft { it.copy(overlays = it.overlays.copy(strokes = prev)) }
                        },
                        onRedo = {
                            val next = drawRedoStack.lastOrNull() ?: return@DrawToolControlsOverlay
                            drawUndoStack = drawUndoStack + listOf(activeDraft.overlays.strokes)
                            drawRedoStack = drawRedoStack.dropLast(1)
                            onUpdateActiveDraft { it.copy(overlays = it.overlays.copy(strokes = next)) }
                        },
                        onClearAll = {
                            if (activeDraft.overlays.strokes.isNotEmpty()) {
                                drawUndoStack = drawUndoStack + listOf(activeDraft.overlays.strokes)
                                drawRedoStack = emptyList()
                                onUpdateActiveDraft { it.copy(overlays = it.overlays.copy(strokes = emptyList())) }
                            }
                        },
                        onDiscard = ::cancelDrawTool,
                        onDone = ::commitDrawTool
                    )
                }
            }

            // 11–13. TWO-ROW BOTTOM BAR (Hidden whenever a tool is active)
            if (activeTool == ActiveStudioTool.NONE) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black)
                        .imePadding()
                ) {
                    // Publish error banner ("No se pudo publicar. Inténtalo de nuevo")
                    if (!publishError.isNullOrBlank()) {
                        Surface(
                            color = Color(0xFF3E1F24),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onDismissPublishError() }
                                .testTag("studio_publish_error_banner")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "No se pudo publicar. Inténtalo de nuevo",
                                    fontFamily = InterFontFamily,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFFCA5A5),
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "Cerrar",
                                    fontFamily = InterFontFamily,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // @ Mention popup list right above Row 1
                    val mentionSearch = remember(activeDraft.caption) {
                        val lastAt = activeDraft.caption.lastIndexOf('@')
                        if (lastAt >= 0) {
                            val tail = activeDraft.caption.substring(lastAt + 1)
                            if (!tail.contains(' ') && !tail.contains('\n')) tail.lowercase() else null
                        } else {
                            null
                        }
                    }
                    LaunchedEffect(mentionSearch) {
                        if (mentionSearch != null) showMentionList = true
                    }

                    if (showMentionList && mentionCandidates.isNotEmpty()) {
                        val filteredMentions = remember(mentionCandidates, mentionSearch) {
                            if (mentionSearch.isNullOrBlank()) mentionCandidates
                            else mentionCandidates.filter { it.name.lowercase().contains(mentionSearch) }
                        }
                        if (filteredMentions.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = Color(0xFF141920),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                                    .heightIn(max = 176.dp)
                                    .testTag("studio_mention_popup")
                            ) {
                                LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
                                    items(filteredMentions, key = { it.id }) { member ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .defaultMinSize(minHeight = 44.dp)
                                                .clickable {
                                                    val tagText = "@${member.name.trim()} "
                                                    onUpdateActiveDraft { cur ->
                                                        val base = cur.caption
                                                        val lastAt = base.lastIndexOf('@')
                                                        val replaced = if (lastAt >= 0 && mentionSearch != null) {
                                                            base.substring(0, lastAt) + tagText
                                                        } else {
                                                            val sep = if (base.isEmpty() || base.endsWith(' ')) "" else " "
                                                            base + sep + tagText
                                                        }
                                                        cur.copy(caption = replaced.take(MAX_STUDIO_COMMENT_CHARS))
                                                    }
                                                    showMentionList = false
                                                }
                                                .padding(horizontal = 14.dp, vertical = 6.dp)
                                                .testTag("studio_mention_member_${member.id}"),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            MemberAvatar(
                                                emoji = member.avatarEmoji,
                                                colorHex = member.avatarColorHex,
                                                photoData = member.avatarPhotoData,
                                                nameForInitials = member.name,
                                                size = 30.dp
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = member.name,
                                                fontFamily = InterFontFamily,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color.White,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = member.roleLabel,
                                                fontFamily = InterFontFamily,
                                                fontSize = 12.sp,
                                                color = Color.White.copy(alpha = 0.55f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Multi-draft thumbnail strip if user added more than 1 image/video
                    if (drafts.size > 1) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black)
                        ) {
                            items(drafts.indices.toList(), key = { drafts[it].id }) { idx ->
                                val item = drafts[idx]
                                val selected = idx == activeIndex
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .border(
                                            width = if (selected) 2.dp else 1.dp,
                                            color = if (selected) StudioSendBlue else Color.White.copy(alpha = 0.25f),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable { onSelectDraftIndex(idx) }
                                ) {
                                    if (item.isVideo) {
                                        val vb = item.videoPreviewInfo?.thumbnailBitmap
                                        if (vb != null && !vb.isRecycled) {
                                            Image(
                                                bitmap = remember(vb) { vb.asImageBitmap() },
                                                contentDescription = "Elemento ${idx + 1}",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(Color(0xFF1A222D)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Outlined.Videocam, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                            }
                                        }
                                    } else {
                                        AsyncImage(
                                            model = item.uri,
                                            contentDescription = "Elemento ${idx + 1}",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.72f))
                                            .clickable { onRemoveDraftAt(idx) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Quitar elemento",
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ROW 1 (sobre fondo negro): Pill 56.dp, #151B21, sin borde
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black)
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(StudioRow1PillBg)
                                .padding(horizontal = 6.dp, vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left: Add photo/video icon
                                IconButton(
                                    onClick = { showAddMediaMenu = true },
                                    enabled = !isPublishing,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .testTag("studio_add_media_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AddPhotoAlternate,
                                        contentDescription = "Agregar foto o video",
                                        tint = Color.White.copy(alpha = 0.88f),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                // Center: Editable comment up to 3 lines ("Añade un comentario…")
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 6.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (activeDraft.caption.isEmpty()) {
                                        Text(
                                            text = "Añade un comentario…",
                                            fontFamily = InterFontFamily,
                                            fontSize = 15.sp,
                                            color = Color.White.copy(alpha = 0.56f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    BasicTextField(
                                        value = activeDraft.caption,
                                        onValueChange = { next ->
                                            val clipped = next.take(MAX_STUDIO_COMMENT_CHARS)
                                            onUpdateActiveDraft { it.copy(caption = clipped) }
                                        },
                                        maxLines = 3,
                                        textStyle = TextStyle(
                                            fontFamily = InterFontFamily,
                                            fontSize = 15.sp,
                                            color = Color.White
                                        ),
                                        cursorBrush = SolidColor(StudioSendBlue),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag(
                                                if (activeDraft.isVideo) "status_video_caption_input"
                                                else "status_photo_caption_input"
                                            )
                                    )
                                }

                                // Right: @ mention icon
                                IconButton(
                                    onClick = {
                                        showMentionList = !showMentionList
                                    },
                                    enabled = !isPublishing,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .testTag("studio_mention_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AlternateEmail,
                                        contentDescription = "Mencionar familiar",
                                        tint = if (showMentionList) StudioSendBlue else Color.White.copy(alpha = 0.88f),
                                        modifier = Modifier.size(21.dp)
                                    )
                                }
                            }
                        }
                    }

                    // ROW 2 (sobre fondo #0F151B, respetando safe area inferior)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(StudioRow2Bg)
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left: 44.dp pill "Estados (Familia)"
                        val audienceLabel = if (currentAudienceIds == null) {
                            "Estados (Familia)"
                        } else {
                            "Estados (${currentAudienceIds.size} pers.)"
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.White.copy(alpha = 0.12f),
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .clip(RoundedCornerShape(50))
                                .clickable(enabled = !isPublishing) { showAudienceSheet = true }
                                .semantics { contentDescription = audienceLabel }
                                .testTag("studio_audience_pill")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Group,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = audienceLabel,
                                    fontFamily = InterFontFamily,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White,
                                    maxLines = 1
                                )
                            }
                        }

                        // Right: 48.dp blue circle (#2F7FE0) with white paper plane ("Publicar")
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isPublishing) StudioSendBlue.copy(alpha = 0.65f) else StudioSendBlue
                                )
                                .clickable(enabled = !isPublishing) {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    onPublishDrafts()
                                }
                                .semantics { contentDescription = "Publicar" }
                                .testTag("publish_status_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isPublishing) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.4.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Publicar",
                                    tint = Color.White,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Sheet: Stickers (Emojis + Útiles)
        if (showStickerSheet) {
            StatusStickerPickerSheet(
                onPickSticker = { kind, value ->
                    showStickerSheet = false
                    onUpdateActiveDraft { cur ->
                        val maxZ = maxOf(
                            cur.overlays.texts.maxOfOrNull { it.zOrder } ?: 0,
                            cur.overlays.stickers.maxOfOrNull { it.zOrder } ?: 0
                        ) + 1
                        cur.copy(
                            overlays = cur.overlays.copy(
                                stickers = cur.overlays.stickers + StatusStickerOverlay(
                                    id = "stk_${UUID.randomUUID().toString().take(8)}",
                                    kind = kind,
                                    value = value,
                                    xNorm = 0.5f,
                                    yNorm = 0.48f,
                                    zOrder = maxZ
                                )
                            )
                        )
                    }
                },
                onDismiss = { showStickerSheet = false }
            )
        }

        // Bottom Sheet: Music Catalog -> Trimmer
        if (showMusicPickerSheet) {
            StatusMusicPickerSheet(
                selectedTrack = activeTrack,
                favoriteTracks = favoriteMusicTracks,
                onToggleFavorite = onToggleFavoriteMusicTrack,
                onSelectTrack = { chosen ->
                    showMusicPickerSheet = false
                    trimmingTrack = chosen.copy(
                        muteOriginalVideo = if (activeDraft.isVideo) {
                            activeTrack?.muteOriginalVideo ?: true
                        } else {
                            false
                        }
                    )
                },
                onDismiss = { showMusicPickerSheet = false },
                searchCatalog = musicSearchCatalog
            )
        }

        val currentTrim = trimmingTrack
        if (currentTrim != null) {
            StatusMusicTrimmerSheet(
                track = currentTrim,
                onConfirm = { trimmed ->
                    trimmingTrack = null
                    isMusicPlaying = true
                    onUpdateActiveDraft { it.copy(musicTrack = trimmed) }
                },
                onDismiss = { trimmingTrack = null }
            )
        }

        // Bottom Sheet: Audience ("Toda la familia" / "Personas concretas")
        if (showAudienceSheet) {
            StatusAudiencePickerSheet(
                allMembers = allMembers,
                activeMemberId = activeMemberId,
                selectedAudienceIds = currentAudienceIds,
                onApplyAudience = { selectedIds ->
                    showAudienceSheet = false
                    onUpdateActiveDraft { cur ->
                        cur.copy(overlays = cur.overlays.copy(audienceMemberIds = selectedIds))
                    }
                },
                onDismiss = { showAudienceSheet = false }
            )
        }

        // Quick Add Photo / Video selector sheet
        if (showAddMediaMenu) {
            AddMoreMediaChooserSheet(
                onAddPhotoGallery = {
                    showAddMediaMenu = false
                    onRequestAddMedia(false, false)
                },
                onAddPhotoCamera = {
                    showAddMediaMenu = false
                    onRequestAddMedia(false, true)
                },
                onAddVideoGallery = {
                    showAddMediaMenu = false
                    onRequestAddMedia(true, false)
                },
                onAddVideoCamera = {
                    showAddMediaMenu = false
                    onRequestAddMedia(true, true)
                },
                onDismiss = { showAddMediaMenu = false }
            )
        }
    }
}

@Composable
private fun BlurredMediaBackground(
    bitmap: Bitmap?,
    uri: Uri?
) {
    val context = LocalContext.current
    val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                renderEffect = RenderEffect.createBlurEffect(
                    42f,
                    42f,
                    Shader.TileMode.CLAMP
                ).asComposeRenderEffect()
                scaleX = 1.12f
                scaleY = 1.12f
            }
    } else {
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = 1.12f
                scaleY = 1.12f
                alpha = 0.65f
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (bitmap != null && !bitmap.isRecycled) {
            Image(
                bitmap = remember(bitmap) { bitmap.asImageBitmap() },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = blurModifier
            )
        } else if (uri != null) {
            val req = remember(uri) {
                ImageRequest.Builder(context)
                    .data(uri)
                    .size(160)
                    .crossfade(false)
                    .build()
            }
            AsyncImage(
                model = req,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = blurModifier
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.34f))
        )
    }
}

@Composable
private fun StudioRightToolButton(
    icon: ImageVector,
    label: String,
    testTag: String,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clickable { onClick() }
            .semantics { contentDescription = label }
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (isActive) Color(0xFF143D2B) else StudioCircleBg)
                .border(
                    width = 1.dp,
                    color = if (isActive) Color(0xFF25D366) else StudioCircleBorder,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) Color(0xFF4ADE80) else Color.White,
                modifier = Modifier.size(21.dp)
            )
        }
        Text(
            text = label,
            fontFamily = InterFontFamily,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            maxLines = 1
        )
    }
}

@Composable
private fun StudioTopMusicChip(
    track: StatusMusicTrack,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onEditTrim: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.Black.copy(alpha = 0.62f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(50))
            .clickable { onEditTrim() }
            .testTag("status_selected_music_pill")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E293B))
                    .clickable { onTogglePlay() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pausar música" else "Reproducir música",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${track.title} · ${track.artist}",
                fontFamily = InterFontFamily,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("status_music_remove_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Quitar música",
                    tint = Color.White.copy(alpha = 0.88f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun TextToolActiveOverlay(
    text: String,
    colorArgb: Long,
    style: StatusTextStyle,
    align: StatusTextAlign,
    hasBackground: Boolean,
    focusRequester: FocusRequester,
    onTextChange: (String) -> Unit,
    onSelectColor: (Long) -> Unit,
    onCycleStyle: () -> Unit,
    onCycleAlign: () -> Unit,
    onToggleBg: () -> Unit,
    onDiscard: () -> Unit,
    onDone: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(80L)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .pointerInput(Unit) { detectTapGestures { } }
            .imePadding()
            .testTag("studio_text_tool_overlay")
    ) {
        // Top Bar: X (left) & Listo (right)
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(StudioCircleBg)
                    .border(1.dp, StudioCircleBorder, CircleShape)
                    .clickable { onDiscard() }
                    .testTag("tool_text_discard"),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Descartar texto", tint = Color.White)
            }

            Button(
                onClick = onDone,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                modifier = Modifier
                    .defaultMinSize(minHeight = 44.dp)
                    .testTag("tool_text_done")
            ) {
                Text(
                    text = "Listo",
                    fontFamily = InterFontFamily,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Centered Live Text Input
        val rawColor = Color(colorArgb)
        val bgColor = if (hasBackground) {
            if (colorArgb == 0xFFFFFFFFL) Color(0xDC151B21) else rawColor.copy(alpha = 0.92f)
        } else {
            Color.Transparent
        }
        val textColor = if (hasBackground && colorArgb != 0xFFFFFFFFL) {
            val luma = 0.299f * rawColor.red + 0.587f * rawColor.green + 0.114f * rawColor.blue
            if (luma > 0.72f) Color(0xFF111827) else Color.White
        } else {
            rawColor
        }
        val textAlign = when (align) {
            StatusTextAlign.LEFT -> TextAlign.Start
            StatusTextAlign.CENTER -> TextAlign.Center
            StatusTextAlign.RIGHT -> TextAlign.End
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .background(bgColor, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                if (text.isEmpty()) {
                    Text(
                        text = "Escribe algo…",
                        fontFamily = InterFontFamily,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.50f),
                        textAlign = textAlign
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    textStyle = TextStyle(
                        fontFamily = if (style == StatusTextStyle.MONOSPACE) FontFamily.Monospace else InterFontFamily,
                        fontSize = 26.sp,
                        fontWeight = if (style == StatusTextStyle.BOLD || style == StatusTextStyle.MONOSPACE) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (style == StatusTextStyle.ITALIC) FontStyle.Italic else FontStyle.Normal,
                        color = textColor,
                        textAlign = textAlign,
                        shadow = if (hasBackground) null else Shadow(Color.Black.copy(alpha = 0.65f), blurRadius = 8f)
                    ),
                    cursorBrush = SolidColor(StudioSendBlue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .testTag("tool_text_input")
                )
            }
        }

        // Bottom Controls Bar: 4 Styles, Align, Background + 8 Colors
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.78f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val styleLabel = when (style) {
                    StatusTextStyle.NORMAL -> "Normal"
                    StatusTextStyle.BOLD -> "Negrita"
                    StatusTextStyle.ITALIC -> "Cursiva"
                    StatusTextStyle.MONOSPACE -> "Máquina"
                }
                ToolPillAction(
                    icon = when (style) {
                        StatusTextStyle.ITALIC -> Icons.Filled.FormatItalic
                        StatusTextStyle.BOLD -> Icons.Filled.FormatBold
                        else -> Icons.Filled.TextFields
                    },
                    label = styleLabel,
                    isActive = true,
                    testTag = "tool_text_style_btn",
                    onClick = onCycleStyle
                )

                val alignIcon = when (align) {
                    StatusTextAlign.LEFT -> Icons.AutoMirrored.Filled.FormatAlignLeft
                    StatusTextAlign.CENTER -> Icons.Filled.FormatAlignCenter
                    StatusTextAlign.RIGHT -> Icons.AutoMirrored.Filled.FormatAlignRight
                }
                ToolPillAction(
                    icon = alignIcon,
                    label = when (align) {
                        StatusTextAlign.LEFT -> "Izquierda"
                        StatusTextAlign.CENTER -> "Centro"
                        StatusTextAlign.RIGHT -> "Derecha"
                    },
                    isActive = false,
                    testTag = "tool_text_align_btn",
                    onClick = onCycleAlign
                )

                ToolPillAction(
                    icon = Icons.Filled.FormatColorFill,
                    label = "Fondo",
                    isActive = hasBackground,
                    testTag = "tool_text_bg_btn",
                    onClick = onToggleBg
                )
            }

            // 8-color palette
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusEditorPalette.eightColors.forEachIndexed { idx, argb ->
                    val selected = colorArgb == argb
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable { onSelectColor(argb) }
                            .semantics { contentDescription = "Color ${idx + 1}" }
                            .testTag("tool_text_color_$idx"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (selected) 32.dp else 26.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .border(
                                    width = if (selected) 3.dp else 1.5.dp,
                                    color = if (selected) StudioSendBlue else Color.White.copy(alpha = 0.7f),
                                    shape = CircleShape
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawToolControlsOverlay(
    selectedColor: Long,
    thicknessIdx: Int,
    isEraser: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    hasStrokes: Boolean,
    onSelectColor: (Long) -> Unit,
    onSelectThickness: (Int) -> Unit,
    onToggleEraser: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClearAll: () -> Unit,
    onDiscard: () -> Unit,
    onDone: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Top bar: X (left), Undo / Redo / Clear (middle), Listo (right)
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(StudioCircleBg)
                    .border(1.dp, StudioCircleBorder, CircleShape)
                    .clickable { onDiscard() }
                    .testTag("tool_draw_discard"),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Cancelar dibujo", tint = Color.White)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onUndo,
                    enabled = canUndo,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .testTag("tool_draw_undo")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Deshacer",
                        tint = if (canUndo) Color.White else Color.White.copy(alpha = 0.35f)
                    )
                }
                IconButton(
                    onClick = onRedo,
                    enabled = canRedo,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .testTag("tool_draw_redo")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Rehacer",
                        tint = if (canRedo) Color.White else Color.White.copy(alpha = 0.35f)
                    )
                }
            }

            Button(
                onClick = onDone,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                modifier = Modifier
                    .defaultMinSize(minHeight = 44.dp)
                    .testTag("tool_draw_done")
            ) {
                Text(
                    text = "Listo",
                    fontFamily = InterFontFamily,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Bottom Drawing Bar: 3 brush sizes, Eraser, Clear all + 8 colors
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.78f))
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 3 thicknesses
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusEditorPalette.brushThicknessNorms.forEachIndexed { idx, (name, _) ->
                        val active = thicknessIdx == idx && !isEraser
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (active) StudioSendBlue else StudioCircleBg,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 44.dp)
                                .clip(RoundedCornerShape(50))
                                .clickable { onSelectThickness(idx) }
                                .testTag("tool_draw_size_$idx")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = name,
                                    fontFamily = InterFontFamily,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                // Eraser & Clear All
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ToolPillAction(
                        icon = Icons.Filled.AutoFixHigh,
                        label = "Borrador",
                        isActive = isEraser,
                        testTag = "tool_draw_eraser",
                        onClick = onToggleEraser
                    )
                    ToolPillAction(
                        icon = Icons.Filled.DeleteSweep,
                        label = "Limpiar",
                        isActive = false,
                        enabled = hasStrokes,
                        testTag = "tool_draw_clear",
                        onClick = onClearAll
                    )
                }
            }

            // 8 colors
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusEditorPalette.eightColors.forEachIndexed { idx, argb ->
                    val selected = selectedColor == argb && !isEraser
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable { onSelectColor(argb) }
                            .semantics { contentDescription = "Color pincel ${idx + 1}" }
                            .testTag("tool_draw_color_$idx"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (selected) 32.dp else 26.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .border(
                                    width = if (selected) 3.dp else 1.5.dp,
                                    color = if (selected) StudioSendBlue else Color.White.copy(alpha = 0.7f),
                                    shape = CircleShape
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolPillAction(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    enabled: Boolean = true,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (isActive) StudioSendBlue else StudioCircleBg,
        modifier = Modifier
            .defaultMinSize(minHeight = 44.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(RoundedCornerShape(50))
            .clickable(enabled = enabled) { onClick() }
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                fontFamily = InterFontFamily,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
}

@Composable
private fun AddMoreMediaChooserSheet(
    onAddPhotoGallery: () -> Unit,
    onAddPhotoCamera: () -> Unit,
    onAddVideoGallery: () -> Unit,
    onAddVideoCamera: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(enabled = true) { onDismiss() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(58f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.60f))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
        )
        Surface(
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = Color(0xFF0E1219),
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .pointerInput(Unit) { detectTapGestures { } }
                .testTag("studio_add_media_sheet")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Agregar otra imagen o video",
                    fontFamily = InterFontFamily,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                AddMediaSheetOption(Icons.Outlined.PhotoLibrary, "Foto de galería", onAddPhotoGallery, "add_more_photo_gallery")
                AddMediaSheetOption(Icons.Outlined.PhotoCamera, "Tomar foto", onAddPhotoCamera, "add_more_photo_camera")
                AddMediaSheetOption(Icons.Outlined.Videocam, "Video de galería", onAddVideoGallery, "add_more_video_gallery")
                AddMediaSheetOption(Icons.Outlined.Videocam, "Grabar video", onAddVideoCamera, "add_more_video_camera")
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.End)
                        .defaultMinSize(minHeight = 44.dp)
                ) {
                    Text("Cancelar", color = Color.White.copy(alpha = 0.72f))
                }
            }
        }
    }
}

@Composable
private fun AddMediaSheetOption(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = StudioRow1PillBg,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            Text(
                text = label,
                fontFamily = InterFontFamily,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.White.copy(alpha = 0.5f))
        }
    }
}
