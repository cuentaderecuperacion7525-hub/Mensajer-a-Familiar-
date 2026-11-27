package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.FamilyMemberEntity
import com.example.data.model.FamilyStatusEntity
import com.example.data.model.JsonHelpers
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.LocalDarkTheme
import com.example.util.CameraCaptureHelper
import com.example.util.DocumentMediaHelper
import com.example.util.ImageCompressionHelper
import com.example.util.TimeFormatters
import com.example.util.VideoMediaHelper
import com.example.util.VideoPreviewInfo
import com.example.util.parseHexColor
import kotlinx.coroutines.delay
import java.io.File

val StatusBackgroundColors = listOf(
    "#128C7E",
    "#1A73E8",
    "#7B1FA2",
    "#C2185B",
    "#E65100",
    "#263238"
)

data class MemberStatusGroup(
    val member: FamilyMemberEntity,
    val items: List<FamilyStatusEntity>,
    val hasUnviewed: Boolean
)

@Composable
fun FamilyStatusStrip(
    activeMember: FamilyMemberEntity,
    allMembers: List<FamilyMemberEntity>,
    statuses: List<FamilyStatusEntity>,
    onAddStatus: () -> Unit,
    onOpenMemberStatuses: (MemberStatusGroup) -> Unit
) {
    val membersMap = remember(allMembers) { allMembers.associateBy { it.id } }
    val myStatuses = remember(statuses, activeMember.id) {
        statuses.filter { it.memberId == activeMember.id }
    }
    val otherGroups = remember(statuses, membersMap, activeMember.id) {
        statuses
            .filter { it.memberId != activeMember.id }
            .groupBy { it.memberId }
            .mapNotNull { (memberId, list) ->
                val author = membersMap[memberId] ?: return@mapNotNull null
                val hasUnviewed = list.any { st ->
                    !JsonHelpers.decodeStringList(st.viewedByMemberIdsJson).contains(activeMember.id)
                }
                MemberStatusGroup(author, list.sortedBy { it.createdAt }, hasUnviewed)
            }
            .sortedWith(compareByDescending<MemberStatusGroup> { it.hasUnviewed }.thenByDescending { it.items.last().createdAt })
    }

    val isDark = LocalDarkTheme.current
    val brandBlue = if (isDark) MaterialTheme.colorScheme.primary else Color(0xFF1A5FD4)
    val primaryText = if (isDark) MaterialTheme.colorScheme.onSurface else Color(0xFF1B1F2A)
    val secondaryText = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF5B6270)

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("family_status_strip")
    ) {
        // My Status item
        item(key = "my_status") {
            val hasStatus = myStatuses.isNotEmpty()
            val ringColor = if (hasStatus) brandBlue else brandBlue.copy(alpha = 0.72f)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(70.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        if (hasStatus) {
                            onOpenMemberStatuses(
                                MemberStatusGroup(
                                    member = activeMember,
                                    items = myStatuses.sortedBy { it.createdAt },
                                    hasUnviewed = false
                                )
                            )
                        } else {
                            onAddStatus()
                        }
                    }
                    .padding(vertical = 4.dp)
                    .testTag("my_status_tile")
            ) {
                Box(modifier = Modifier.size(54.dp), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .drawBehind {
                                val strokePx = 2.dp.toPx()
                                val cornerPx = 13.dp.toPx()
                                drawRoundRect(
                                    color = ringColor,
                                    cornerRadius = CornerRadius(cornerPx, cornerPx),
                                    style = Stroke(
                                        width = strokePx,
                                        pathEffect = if (hasStatus) null
                                        else PathEffect.dashPathEffect(floatArrayOf(10f, 7f), 0f)
                                    )
                                )
                            }
                            .padding(3.5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        MemberAvatar(
                            emoji = activeMember.avatarEmoji,
                            colorHex = activeMember.avatarColorHex,
                            photoData = activeMember.avatarPhotoData,
                            nameForInitials = activeMember.name,
                            size = 42.dp,
                            cornerRadius = 11.dp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(brandBlue)
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                            .clickable { onAddStatus() }
                            .testTag("add_status_quick_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Nuevo estado",
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = "Mi estado",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = InterFontFamily,
                        fontSize = 12.sp
                    ),
                    color = secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Other family members' statuses
        items(otherGroups, key = { it.member.id }) { group ->
            val hasVideo = group.items.any { it.isVideo }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(72.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onOpenMemberStatuses(group) }
                    .padding(vertical = 4.dp)
                    .testTag("status_tile_${group.member.id}")
            ) {
                val ringColor = if (group.hasUnviewed) {
                    brandBlue
                } else {
                    brandBlue.copy(alpha = 0.45f)
                }
                Box(modifier = Modifier.size(54.dp), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .border(2.dp, ringColor, RoundedCornerShape(13.dp))
                            .padding(3.5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        MemberAvatar(
                            emoji = group.member.avatarEmoji,
                            colorHex = group.member.avatarColorHex,
                            photoData = group.member.avatarPhotoData,
                            nameForInitials = group.member.name,
                            size = 42.dp,
                            cornerRadius = 11.dp
                        )
                    }
                    if (hasVideo) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(19.dp)
                                .clip(CircleShape)
                                .background(brandBlue)
                                .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Videocam,
                                contentDescription = "Estado en video",
                                tint = Color.White,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = group.member.name,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = InterFontFamily,
                        fontSize = 12.sp,
                        fontWeight = if (group.hasUnviewed) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = if (group.hasUnviewed) primaryText else secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

internal fun resolveStatusDisplayDurationMs(
    isVideo: Boolean,
    attachedMusic: com.example.util.StatusMusicTrack?
): Long {
    if (isVideo || attachedMusic == null) return 6000L
    return attachedMusic.trimDurationSec.coerceIn(6, 30) * 1000L
}

@Composable
private fun StatusSegmentedProgressRow(
    itemCount: Int,
    activeIndex: Int,
    currentStatusId: String,
    isVideo: Boolean,
    videoProgress: Float,
    isPaused: Boolean,
    displayDurationMs: Long = 6000L,
    onAutoAdvance: () -> Unit
) {
    var timerProgress by remember(currentStatusId) { mutableFloatStateOf(0f) }

    LaunchedEffect(currentStatusId, isVideo, isPaused, displayDurationMs) {
        if (isVideo || isPaused) return@LaunchedEffect
        val totalMs = displayDurationMs.coerceIn(3000L, 35000L)
        val stepMs = 50L
        val steps = (totalMs / stepMs).toInt()
        val startStep = (timerProgress * steps).toInt()
        for (i in startStep..steps) {
            timerProgress = i.toFloat() / steps.toFloat()
            delay(stepMs)
        }
        onAutoAdvance()
    }

    val activeProgress = if (isVideo) videoProgress.coerceIn(0f, 1f) else timerProgress.coerceIn(0f, 1f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (idx in 0 until itemCount) {
            val prog = when {
                idx < activeIndex -> 1f
                idx == activeIndex -> activeProgress
                else -> 0f
            }
            LinearProgressIndicator(
                progress = { prog },
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.3f),
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatusViewerDialog(
    group: MemberStatusGroup,
    activeMemberId: String,
    allMembers: List<FamilyMemberEntity>,
    mediaTransferProgress: Map<String, Float> = emptyMap(),
    onMarkViewed: (statusId: String) -> Unit,
    onDeleteStatus: (statusId: String) -> Unit,
    onReplyStatus: (status: FamilyStatusEntity, replyText: String) -> Unit,
    onPrepareStatusVideo: (status: FamilyStatusEntity, onReady: (File) -> Unit, onError: (String) -> Unit) -> Unit = { _, _, _ -> },
    onSaveStatusToGallery: (status: FamilyStatusEntity) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val isImeVisible = WindowInsets.isImeVisible
    val membersMap = remember(allMembers) { allMembers.associateBy { it.id } }
    var currentIndex by remember(group.member.id) { mutableIntStateOf(0) }
    val items = group.items
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    val safeIdx = currentIndex.coerceIn(0, items.lastIndex)
    val currentStatus = items[safeIdx]
    val isMine = group.member.id == activeMemberId

    var replyInput by remember(currentStatus.id) { mutableStateOf("") }
    var showViewersSheet by remember(currentStatus.id) { mutableStateOf(false) }
    var showOptionsMenu by remember(currentStatus.id) { mutableStateOf(false) }
    var isTouchPaused by remember(currentStatus.id) { mutableStateOf(false) }
    var isStatusAudioMuted by remember(group.member.id) { mutableStateOf(false) }

    val attachedMusic = currentStatus.musicTrack

    // Video status state
    var readyVideoFile by remember(currentStatus.id) { mutableStateOf<File?>(null) }
    var videoLoadError by remember(currentStatus.id) { mutableStateOf<String?>(null) }
    var videoRetryNonce by remember(currentStatus.id) { mutableIntStateOf(0) }
    var videoViewRef by remember(currentStatus.id) { mutableStateOf<VideoView?>(null) }
    var mediaPlayerRef by remember(currentStatus.id) { mutableStateOf<MediaPlayer?>(null) }
    var isVideoPlaying by remember(currentStatus.id) { mutableStateOf(true) }
    var videoProgress by remember(currentStatus.id) { mutableFloatStateOf(0f) }

    // Background music player for online status tracks
    var statusMusicPlayer by remember(currentStatus.id) { mutableStateOf<MediaPlayer?>(null) }
    var isStatusMusicPrepared by remember(currentStatus.id) { mutableStateOf(false) }

    val thumbBitmap = remember(currentStatus.id, currentStatus.videoThumbnailData) {
        ImageCompressionHelper.decodeToBitmap(currentStatus.videoThumbnailData)?.asImageBitmap()
    }
    val initialVideoRatio = remember(currentStatus.id, thumbBitmap) {
        if (thumbBitmap != null && thumbBitmap.height > 0) {
            (thumbBitmap.width.toFloat() / thumbBitmap.height.toFloat()).coerceIn(0.35f, 2.5f)
        } else {
            9f / 16f
        }
    }
    var videoAspectRatio by remember(currentStatus.id) { mutableFloatStateOf(initialVideoRatio) }

    // Photo zoom state (reset per status)
    var photoScale by remember(currentStatus.id) { mutableFloatStateOf(1f) }
    var photoOffset by remember(currentStatus.id) { mutableStateOf(Offset.Zero) }

    val viewerIds = remember(currentStatus.viewedByMemberIdsJson, activeMemberId) {
        JsonHelpers.decodeStringList(currentStatus.viewedByMemberIdsJson)
            .filterNot { it == activeMemberId }
    }

    LaunchedEffect(currentStatus.id) {
        onMarkViewed(currentStatus.id)
    }

    LaunchedEffect(currentStatus.id, currentStatus.isVideo, videoRetryNonce) {
        if (currentStatus.isVideo) {
            readyVideoFile = null
            videoLoadError = null
            videoProgress = 0f
            onPrepareStatusVideo(
                currentStatus,
                { file ->
                    videoLoadError = null
                    readyVideoFile = file
                },
                { errMsg ->
                    readyVideoFile = null
                    videoLoadError = errMsg
                }
            )
        }
    }

    val isOverlayPaused = isTouchPaused || replyInput.isNotEmpty() || showViewersSheet ||
        showOptionsMenu || photoScale > 1.05f || videoLoadError != null

    // Sync original video track audio volume (respects user mute button & track muteOriginalVideo flag)
    LaunchedEffect(currentStatus.id, mediaPlayerRef, isStatusAudioMuted, attachedMusic?.muteOriginalVideo) {
        val mp = mediaPlayerRef ?: return@LaunchedEffect
        val vol = if (isStatusAudioMuted || attachedMusic?.muteOriginalVideo == true) 0f else 1f
        runCatching { mp.setVolume(vol, vol) }
    }

    // Prepare & dispose online music stream for the active status slide
    DisposableEffect(currentStatus.id, attachedMusic?.previewUrl) {
        val musicUrl = attachedMusic?.previewUrl?.trim().orEmpty()
        var localPlayer: MediaPlayer? = null
        if (musicUrl.isNotBlank()) {
            val mp = MediaPlayer()
            localPlayer = mp
            statusMusicPlayer = mp
            isStatusMusicPrepared = false
            runCatching {
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                mp.setDataSource(musicUrl)
                mp.isLooping = false
                val vol = if (isStatusAudioMuted) 0f else 1f
                mp.setVolume(vol, vol)
                mp.setOnPreparedListener { readyMp ->
                    if (statusMusicPlayer === readyMp) {
                        isStatusMusicPrepared = true
                        val currentVol = if (isStatusAudioMuted) 0f else 1f
                        runCatching {
                            readyMp.setVolume(currentVol, currentVol)
                            val startMs = (attachedMusic?.trimStartSec ?: 0).coerceAtLeast(0) * 1000
                            if (startMs > 0) readyMp.seekTo(startMs)
                        }
                        if (!isOverlayPaused) {
                            runCatching { readyMp.start() }
                        }
                    }
                }
                mp.setOnErrorListener { _, _, _ -> true }
                mp.prepareAsync()
            }.onFailure {
                runCatching { mp.release() }
                if (statusMusicPlayer === mp) {
                    statusMusicPlayer = null
                    isStatusMusicPrepared = false
                }
            }
        }
        onDispose {
            try {
                videoViewRef?.stopPlayback()
            } catch (_: Exception) {
            }
            mediaPlayerRef = null
            val toRelease = localPlayer ?: statusMusicPlayer
            if (statusMusicPlayer === toRelease) {
                statusMusicPlayer = null
                isStatusMusicPrepared = false
            }
            if (toRelease != null) {
                runCatching { if (toRelease.isPlaying) toRelease.stop() }
                runCatching { toRelease.reset() }
                runCatching { toRelease.release() }
            }
        }
    }

    // Sync online music play/pause, mute volume, and trimmed loop window with viewer state
    LaunchedEffect(
        currentStatus.id,
        statusMusicPlayer,
        isStatusMusicPrepared,
        isOverlayPaused,
        isStatusAudioMuted,
        attachedMusic?.trimStartSec,
        attachedMusic?.trimDurationSec
    ) {
        val mp = statusMusicPlayer ?: return@LaunchedEffect
        if (!isStatusMusicPrepared) return@LaunchedEffect
        val startMs = (attachedMusic?.trimStartSec ?: 0).coerceAtLeast(0) * 1000
        val endMs = ((attachedMusic?.trimStartSec ?: 0) + (attachedMusic?.trimDurationSec ?: 30))
            .coerceAtLeast(5) * 1000
        val vol = if (isStatusAudioMuted) 0f else 1f
        runCatching { mp.setVolume(vol, vol) }
        val videoShouldPlay = !currentStatus.isVideo || (readyVideoFile != null && isVideoPlaying)
        if (isOverlayPaused || !videoShouldPlay) {
            runCatching { if (mp.isPlaying) mp.pause() }
            return@LaunchedEffect
        }
        runCatching {
            if (mp.currentPosition < startMs || mp.currentPosition >= endMs) {
                mp.seekTo(startMs)
            }
            if (!mp.isPlaying) mp.start()
        }
        while (!isOverlayPaused) {
            runCatching {
                if (mp.currentPosition >= endMs) {
                    mp.seekTo(startMs)
                }
            }
            delay(150L)
        }
    }

    // Track video progress smoothly without recomposing photo statuses
    LaunchedEffect(currentStatus.id, readyVideoFile, isVideoPlaying, isOverlayPaused) {
        if (!currentStatus.isVideo || readyVideoFile == null) return@LaunchedEffect
        val vv = videoViewRef
        if (isOverlayPaused) {
            try {
                if (vv?.isPlaying == true) vv.pause()
            } catch (_: Exception) {
            }
            return@LaunchedEffect
        } else if (isVideoPlaying) {
            try {
                if (vv != null && !vv.isPlaying) vv.start()
            } catch (_: Exception) {
            }
        }
        while (true) {
            val activeVv = videoViewRef
            if (activeVv != null) {
                try {
                    val dur = activeVv.duration.coerceAtLeast(1)
                    val pos = activeVv.currentPosition.coerceAtLeast(0)
                    if (dur > 0) {
                        videoProgress = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                    }
                } catch (_: Exception) {
                }
            }
            delay(80L)
        }
    }

    fun advanceNextOrDismiss() {
        if (safeIdx < items.lastIndex) {
            currentIndex = safeIdx + 1
        } else {
            onDismiss()
        }
    }

    fun goPrevious() {
        if (safeIdx > 0) {
            currentIndex = safeIdx - 1
        }
    }

    BackHandler {
        if (isImeVisible) {
            keyboardController?.hide()
            focusManager.clearFocus()
        } else {
            onDismiss()
        }
    }

    val hasMedia = currentStatus.isVideo || !currentStatus.mediaUri.isNullOrBlank()
    val bgColor = if (hasMedia) {
        Color(0xFF0B141A)
    } else {
        parseHexColor(currentStatus.backgroundHex)
    }
    val hasMenuActions = hasMedia || isMine

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = bgColor,
        modifier = Modifier
            .fillMaxSize()
            .zIndex(30f)
            .pointerInput(Unit) { detectTapGestures { } },
        topBar = {
            // 1. COMPACT TOP HEADER BAR WITH 3-DOTS MENU (⋮)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.56f))
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                StatusSegmentedProgressRow(
                    itemCount = items.size,
                    activeIndex = safeIdx,
                    currentStatusId = currentStatus.id,
                    isVideo = currentStatus.isVideo,
                    videoProgress = videoProgress,
                    isPaused = isOverlayPaused,
                    displayDurationMs = resolveStatusDisplayDurationMs(
                        isVideo = currentStatus.isVideo,
                        attachedMusic = attachedMusic
                    ),
                    onAutoAdvance = ::advanceNextOrDismiss
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MemberAvatar(
                        emoji = group.member.avatarEmoji,
                        colorHex = group.member.avatarColorHex,
                        photoData = group.member.avatarPhotoData,
                        size = 38.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isMine) "Mi estado" else group.member.name,
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val typePrefix = if (currentStatus.isVideo) "🎬 Video · " else ""
                        Text(
                            text = typePrefix + TimeFormatters.formatLastSeen(currentStatus.createdAt)
                                .replace("Últ. vez ", "Publicado "),
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (attachedMusic != null || currentStatus.isVideo) {
                        IconButton(
                            onClick = { isStatusAudioMuted = !isStatusAudioMuted },
                            modifier = Modifier.testTag("status_viewer_mute_button")
                        ) {
                            Icon(
                                imageVector = if (isStatusAudioMuted) Icons.AutoMirrored.Filled.VolumeOff
                                else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = if (isStatusAudioMuted) "Activar sonido" else "Silenciar",
                                tint = Color.White
                            )
                        }
                    }

                    if (hasMenuActions) {
                        Box {
                            IconButton(
                                onClick = { showOptionsMenu = true },
                                modifier = Modifier.testTag("status_more_options_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = "Más opciones del estado",
                                    tint = Color.White
                                )
                            }
                            DropdownMenu(
                                expanded = showOptionsMenu,
                                onDismissRequest = { showOptionsMenu = false }
                            ) {
                                if (currentStatus.isVideo && readyVideoFile != null) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(if (isVideoPlaying) "Pausar video" else "Reproducir video")
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (isVideoPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            showOptionsMenu = false
                                            val nextPlay = !isVideoPlaying
                                            isVideoPlaying = nextPlay
                                            try {
                                                if (nextPlay) videoViewRef?.start() else videoViewRef?.pause()
                                            } catch (_: Exception) {
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(if (isStatusAudioMuted) "Activar sonido" else "Silenciar")
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (isStatusAudioMuted) Icons.AutoMirrored.Filled.VolumeUp
                                                else Icons.AutoMirrored.Filled.VolumeOff,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            showOptionsMenu = false
                                            isStatusAudioMuted = !isStatusAudioMuted
                                        }
                                    )
                                }
                                if (hasMedia) {
                                    DropdownMenuItem(
                                        text = { Text("Guardar en Galería") },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Download, contentDescription = null)
                                        },
                                        onClick = {
                                            showOptionsMenu = false
                                            onSaveStatusToGallery(currentStatus)
                                        },
                                        modifier = Modifier.testTag("save_status_media_button")
                                    )
                                }
                                if (isMine) {
                                    DropdownMenuItem(
                                        text = { Text("Visto por (${viewerIds.size})") },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Visibility, contentDescription = null)
                                        },
                                        onClick = {
                                            showOptionsMenu = false
                                            showViewersSheet = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "Eliminar estado",
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Filled.Delete,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        onClick = {
                                            showOptionsMenu = false
                                            val deletingId = currentStatus.id
                                            onDeleteStatus(deletingId)
                                            if (items.size <= 1) onDismiss()
                                            else if (safeIdx >= items.lastIndex) currentIndex = (safeIdx - 1).coerceAtLeast(0)
                                        },
                                        modifier = Modifier.testTag("delete_status_menu_item")
                                    )
                                }
                            }
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                }

                if (attachedMusic != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.White.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                        modifier = Modifier
                            .align(Alignment.Start)
                            .widthIn(max = 310.dp)
                            .testTag("status_viewer_music_badge")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            if (attachedMusic.artworkUrl.isNotBlank()) {
                                AsyncImage(
                                    model = attachedMusic.artworkUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.MusicNote,
                                    contentDescription = null,
                                    tint = Color(0xFF4ADE80),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                            }
                            Text(
                                text = "${attachedMusic.title} · ${attachedMusic.artist}",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            // 3. BOTTOM FOOTER BAR (Directly in Activity window with safeDrawing + extra bottom margin so it is 100% visible!)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.78f))
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                    )
                    .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val showBottomComment = (hasMedia || currentStatus.overlayBundle?.texts?.isNotEmpty() == true) &&
                    currentStatus.text.isNotBlank()
                if (showBottomComment) {
                    Text(
                        text = currentStatus.text,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                if (isMine) {
                    Surface(
                        shape = RoundedCornerShape(50.dp),
                        color = Color.White.copy(alpha = 0.18f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .clickable { showViewersSheet = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.Visibility, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Text(
                                text = "Visto por ${viewerIds.size} familiar(es)",
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 560.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = Color.White.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.55f)),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 46.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 11.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (replyInput.isEmpty()) {
                                    Text(
                                        text = "Responder al estado...",
                                        color = Color.White.copy(alpha = 0.72f),
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                BasicTextField(
                                    value = replyInput,
                                    onValueChange = { replyInput = it },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                                    cursorBrush = SolidColor(Color.White),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("status_reply_input")
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                if (replyInput.isNotBlank()) {
                                    onReplyStatus(currentStatus, replyInput.trim())
                                    replyInput = ""
                                    onDismiss()
                                }
                            },
                            enabled = replyInput.isNotBlank(),
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .testTag("status_reply_send_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Enviar respuesta",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        // 2. CENTER FULL MEDIA VIEWPORT (Strictly bounded inside innerPadding between topBar and bottomBar!)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .clipToBounds()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            val availW = maxWidth
            val availH = maxHeight

            when {
                currentStatus.isVideo -> {
                    val file = readyVideoFile
                    if (file != null && file.exists() && videoLoadError == null && availW > 0.dp && availH > 0.dp) {
                        val safeRatio = videoAspectRatio.coerceIn(0.35f, 2.5f)
                        val containerRatio = availW / availH
                        val (fittedW, fittedH) = if (safeRatio > containerRatio) {
                            availW to (availW / safeRatio)
                        } else {
                            (availH * safeRatio) to availH
                        }

                        Box(
                            modifier = Modifier
                                .size(fittedW, fittedH)
                                .clip(RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            key(currentStatus.id, file.absolutePath, videoRetryNonce) {
                                AndroidView(
                                    factory = { ctx ->
                                        FrameLayout(ctx).apply {
                                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                            val vv = VideoView(ctx).apply {
                                                layoutParams = FrameLayout.LayoutParams(
                                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                                    Gravity.CENTER
                                                )
                                                setVideoPath(file.absolutePath)
                                                setOnPreparedListener { mp ->
                                                    mediaPlayerRef = mp
                                                    if (mp.videoWidth > 0 && mp.videoHeight > 0) {
                                                        videoAspectRatio = (mp.videoWidth.toFloat() / mp.videoHeight.toFloat())
                                                            .coerceIn(0.35f, 2.5f)
                                                    }
                                                    mp.isLooping = false
                                                    val vol = if (isStatusAudioMuted || attachedMusic?.muteOriginalVideo == true) 0f else 1f
                                                    mp.setVolume(vol, vol)
                                                    start()
                                                    isVideoPlaying = true
                                                }
                                                setOnErrorListener { _, _, _ ->
                                                    VideoMediaHelper.deleteCachedVideoFile(
                                                        ctx,
                                                        currentStatus.id,
                                                        "estado_${currentStatus.id}.mp4"
                                                    )
                                                    readyVideoFile = null
                                                    videoLoadError = "No se pudo reproducir el video. Intenta descargarlo de nuevo."
                                                    true
                                                }
                                                setOnCompletionListener {
                                                    videoProgress = 1f
                                                    advanceNextOrDismiss()
                                                }
                                            }
                                            videoViewRef = vv
                                            addView(vv)
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            currentStatus.overlayBundle?.let { layers ->
                                if (!layers.isEmptyEdits()) {
                                    StatusOverlayRenderBox(
                                        bundle = layers,
                                        interactive = false,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .testTag("status_viewer_video_overlays")
                                    )
                                }
                            }
                        }
                    } else {
                        if (thumbBitmap != null) {
                            Image(
                                bitmap = thumbBitmap,
                                contentDescription = "Miniatura de video de estado",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        if (videoLoadError == null) {
                            val dlPct = mediaTransferProgress[currentStatus.id]?.let { (it * 100f).toInt().coerceIn(1, 99) }
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = Color.Black.copy(alpha = 0.72f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 3.dp,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = if (dlPct != null) "Cargando video... $dlPct%" else "Preparando video...",
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }

                !currentStatus.mediaUri.isNullOrBlank() -> {
                    val decodedBitmap = remember(currentStatus.id, currentStatus.mediaUri) {
                        ImageCompressionHelper.decodeToBitmap(currentStatus.mediaUri)?.asImageBitmap()
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(currentStatus.id) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val nextScale = (photoScale * zoom).coerceIn(1f, 4f)
                                    photoScale = nextScale
                                    photoOffset = if (nextScale > 1f) photoOffset + pan else Offset.Zero
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (decodedBitmap != null) {
                            Image(
                                bitmap = decodedBitmap,
                                contentDescription = "Foto de estado",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(
                                        scaleX = photoScale,
                                        scaleY = photoScale,
                                        translationX = photoOffset.x,
                                        translationY = photoOffset.y
                                    )
                            )
                        } else {
                            val fallbackRequest = remember(currentStatus.id, currentStatus.mediaUri) {
                                ImageRequest.Builder(context)
                                    .data(ImageCompressionHelper.resolveCoilModel(currentStatus.mediaUri))
                                    .crossfade(false)
                                    .build()
                            }
                            AsyncImage(
                                model = fallbackRequest,
                                contentDescription = "Foto de estado",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(
                                        scaleX = photoScale,
                                        scaleY = photoScale,
                                        translationX = photoOffset.x,
                                        translationY = photoOffset.y
                                    )
                            )
                        }
                    }
                }

                else -> {
                    val layers = currentStatus.overlayBundle
                    val hasOverlayContent = layers != null && !layers.isEmptyEdits()
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (hasOverlayContent) {
                            StatusOverlayRenderBox(
                                bundle = layers!!,
                                interactive = false,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("status_viewer_text_overlays")
                            )
                        }
                        if ((!hasOverlayContent || layers!!.texts.isEmpty()) && currentStatus.text.isNotBlank()) {
                            Text(
                                text = currentStatus.text,
                                color = Color.White,
                                fontSize = 26.sp,
                                lineHeight = 34.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 28.dp)
                            )
                        }
                    }
                }
            }

            // Left / Right navigation tap strips + hold-to-pause (only active when not zoomed in and no error overlay)
            if (photoScale <= 1.05f && videoLoadError == null) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .pointerInput(currentStatus.id, safeIdx) {
                                detectTapGestures(
                                    onPress = {
                                        isTouchPaused = true
                                        try {
                                            tryAwaitRelease()
                                        } finally {
                                            isTouchPaused = false
                                        }
                                    },
                                    onTap = { goPrevious() },
                                    onDoubleTap = {
                                        if (!currentStatus.isVideo && !currentStatus.mediaUri.isNullOrBlank()) {
                                            photoScale = if (photoScale > 1.2f) 1f else 2.2f
                                            photoOffset = Offset.Zero
                                        }
                                    }
                                )
                            }
                    )
                    Box(
                        modifier = Modifier
                            .weight(2f)
                            .fillMaxHeight()
                            .pointerInput(currentStatus.id, safeIdx) {
                                detectTapGestures(
                                    onPress = {
                                        isTouchPaused = true
                                        try {
                                            tryAwaitRelease()
                                        } finally {
                                            isTouchPaused = false
                                        }
                                    },
                                    onTap = { advanceNextOrDismiss() },
                                    onDoubleTap = {
                                        if (!currentStatus.isVideo && !currentStatus.mediaUri.isNullOrBlank()) {
                                            photoScale = if (photoScale > 1.2f) 1f else 2.2f
                                            photoOffset = Offset.Zero
                                        }
                                    }
                                )
                            }
                    )
                }
            }

            // Error + Retry overlay rendered above tap strips so Reintentar always receives taps
            val errMessage = videoLoadError
            if (currentStatus.isVideo && errMessage != null) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.82f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                    modifier = Modifier.padding(horizontal = 28.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ErrorOutline,
                            contentDescription = "Error de video",
                            tint = Color(0xFFFCA5A5),
                            modifier = Modifier.size(38.dp)
                        )
                        Text(
                            text = errMessage,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = {
                                videoLoadError = null
                                videoRetryNonce++
                            },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("status_video_retry_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Reintentar")
                        }
                    }
                }
            }
        }
    }

    if (showViewersSheet) {
        AlertDialog(
            onDismissRequest = { showViewersSheet = false },
            title = { Text("Visto por (${viewerIds.size})") },
            text = {
                if (viewerIds.isEmpty()) {
                    Text("Nadie más ha visto este estado todavía.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(viewerIds) { id ->
                            val viewer = membersMap[id]
                            if (viewer != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    MemberAvatar(
                                        emoji = viewer.avatarEmoji,
                                        colorHex = viewer.avatarColorHex,
                                        photoData = viewer.avatarPhotoData,
                                        size = 36.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(viewer.name, style = MaterialTheme.typography.titleSmall)
                                        Text(viewer.roleLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showViewersSheet = false }) { Text("Cerrar") }
            }
        )
    }
}

