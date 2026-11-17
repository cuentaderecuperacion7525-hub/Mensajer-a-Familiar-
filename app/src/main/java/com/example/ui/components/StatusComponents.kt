package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
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

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("family_status_strip")
    ) {
        // My Status item
        item(key = "my_status") {
            val hasVideo = myStatuses.any { it.isVideo }
            val hasStatus = myStatuses.isNotEmpty()
            val ringColor = if (hasStatus) MaterialTheme.colorScheme.primary
            else Color(0xFF9AA0A6)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(74.dp)
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
                    .padding(vertical = 2.dp)
                    .testTag("my_status_tile")
            ) {
                Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .drawBehind {
                                val strokePx = 2.2.dp.toPx()
                                val radius = (size.minDimension - strokePx) / 2f
                                drawCircle(
                                    color = ringColor,
                                    radius = radius,
                                    style = Stroke(
                                        width = strokePx,
                                        pathEffect = if (hasStatus) null
                                        else PathEffect.dashPathEffect(floatArrayOf(12f, 9f), 0f)
                                    )
                                )
                            }
                            .padding(5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        MemberAvatar(
                            emoji = activeMember.avatarEmoji,
                            colorHex = activeMember.avatarColorHex,
                            photoData = activeMember.avatarPhotoData,
                            nameForInitials = activeMember.name,
                            size = 52.dp
                        )
                    }
                    if (hasVideo) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1A73E8))
                                .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Videocam,
                                contentDescription = "Estado con video",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                            .clickable { onAddStatus() }
                            .testTag("add_status_quick_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Nuevo estado",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = "Mi estado",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    .width(86.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onOpenMemberStatuses(group) }
                    .padding(vertical = 2.dp)
                    .testTag("status_tile_${group.member.id}")
            ) {
                val ringColor = if (group.hasUnviewed) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                }
                Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .border(2.5.dp, ringColor, CircleShape)
                            .padding(5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        MemberAvatar(
                            emoji = group.member.avatarEmoji,
                            colorHex = group.member.avatarColorHex,
                            photoData = group.member.avatarPhotoData,
                            nameForInitials = group.member.name,
                            size = 52.dp
                        )
                    }
                    if (hasVideo) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1A73E8))
                                .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Videocam,
                                contentDescription = "Estado en video",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(5.dp))
                if (group.hasUnviewed) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(
                                SpanStyle(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                append(group.member.name)
                            }
                            withStyle(
                                SpanStyle(
                                    fontWeight = FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                append(" · nuevo")
                            }
                        },
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = group.member.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun CreateStatusDialog(
    onDismiss: () -> Unit,
    onPublishText: (text: String, backgroundHex: String) -> Unit,
    onPublishPhoto: (imageUri: Uri, caption: String) -> Unit,
    onPublishVideo: (videoUri: Uri, caption: String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    var modeTab by remember { mutableIntStateOf(0) } // 0 = Text, 1 = Photo, 2 = Video
    var text by remember { mutableStateOf("") }
    var selectedHex by remember { mutableStateOf(StatusBackgroundColors.first()) }

    var pickedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var photoCaption by remember { mutableStateOf("") }

    var pickedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraVideoUri by remember { mutableStateOf<Uri?>(null) }
    var videoPreview by remember { mutableStateOf<VideoPreviewInfo?>(null) }
    var videoCaption by remember { mutableStateOf("") }

    LaunchedEffect(pickedVideoUri) {
        val uri = pickedVideoUri
        if (uri == null) {
            videoPreview = null
        } else {
            videoPreview = VideoMediaHelper.extractVideoPreview(context, uri)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> if (uri != null) pickedPhotoUri = uri }
    )

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> if (uri != null) pickedVideoUri = uri }
    )

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success && pendingCameraPhotoUri != null) {
                pickedPhotoUri = pendingCameraPhotoUri
            }
        }
    )

    val captureVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo(),
        onResult = { success ->
            if (success && pendingCameraVideoUri != null) {
                pickedVideoUri = pendingCameraVideoUri
            }
        }
    )

    var pendingCameraMode by remember { mutableIntStateOf(1) } // 1 = Photo, 2 = Video

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                if (pendingCameraMode == 2) {
                    val uri = CameraCaptureHelper.createTempVideoUri(context)
                    if (uri != null) {
                        pendingCameraVideoUri = uri
                        captureVideoLauncher.launch(uri)
                    }
                } else {
                    val uri = CameraCaptureHelper.createTempPhotoUri(context)
                    if (uri != null) {
                        pendingCameraPhotoUri = uri
                        takePictureLauncher.launch(uri)
                    }
                }
            }
        }
    )

    fun launchCameraForPhoto() {
        pendingCameraMode = 1
        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            val uri = CameraCaptureHelper.createTempPhotoUri(context)
            if (uri != null) {
                pendingCameraPhotoUri = uri
                takePictureLauncher.launch(uri)
            }
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun launchCameraForVideo() {
        pendingCameraMode = 2
        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            val uri = CameraCaptureHelper.createTempVideoUri(context)
            if (uri != null) {
                pendingCameraVideoUri = uri
                captureVideoLauncher.launch(uri)
            }
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Publicar estado (24 horas)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TabRow(selectedTabIndex = modeTab) {
                    Tab(
                        selected = modeTab == 0,
                        onClick = { modeTab = 0 },
                        text = { Text("Texto") },
                        modifier = Modifier.testTag("status_tab_text")
                    )
                    Tab(
                        selected = modeTab == 1,
                        onClick = { modeTab = 1 },
                        text = { Text("Foto") },
                        modifier = Modifier.testTag("status_tab_photo")
                    )
                    Tab(
                        selected = modeTab == 2,
                        onClick = { modeTab = 2 },
                        text = { Text("Video") },
                        modifier = Modifier.testTag("status_tab_video")
                    )
                }

                when (modeTab) {
                    0 -> {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = parseHexColor(selectedHex),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = text.ifBlank { "Tu estado aparecerá aquí..." },
                                    color = Color.White.copy(alpha = if (text.isBlank()) 0.65f else 1f),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    textAlign = TextAlign.Center,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatusBackgroundColors.forEach { hex ->
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(parseHexColor(hex))
                                        .border(
                                            width = if (selectedHex == hex) 3.dp else 1.dp,
                                            color = if (selectedHex == hex) MaterialTheme.colorScheme.onSurface
                                            else Color.White.copy(alpha = 0.4f),
                                            shape = CircleShape
                                        )
                                        .clickable { selectedHex = hex }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = text,
                            onValueChange = { if (it.length <= 280) text = it },
                            label = { Text("¿Qué quieres compartir hoy?") },
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("status_text_input")
                        )
                    }

                    1 -> {
                        if (pickedPhotoUri != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF0B141A)),
                                contentAlignment = Alignment.Center
                            ) {
                                val photoRequest = remember(pickedPhotoUri) {
                                    ImageRequest.Builder(context)
                                        .data(pickedPhotoUri)
                                        .crossfade(false)
                                        .build()
                                }
                                AsyncImage(
                                    model = photoRequest,
                                    contentDescription = "Foto completa para estado",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Image,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(34.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Toca para elegir una foto de galería",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { launchCameraForPhoto() },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("status_take_photo_button")
                            ) {
                                Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Cámara")
                            }
                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("status_pick_gallery_button")
                            ) {
                                Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Galería")
                            }
                        }

                        OutlinedTextField(
                            value = photoCaption,
                            onValueChange = { if (it.length <= 200) photoCaption = it },
                            label = { Text("Comentario (opcional)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("status_photo_caption_input")
                        )
                    }

                    2 -> {
                        if (pickedVideoUri != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(210.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF0B141A)),
                                contentAlignment = Alignment.Center
                            ) {
                                val thumbBmp = videoPreview?.thumbnailBitmap
                                if (thumbBmp != null && !thumbBmp.isRecycled) {
                                    val imgBmp = remember(thumbBmp) { thumbBmp.asImageBitmap() }
                                    Image(
                                        bitmap = imgBmp,
                                        contentDescription = "Vista previa de video",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.6f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                val previewInfo = videoPreview
                                if (previewInfo != null) {
                                    val sizeLabel = if (previewInfo.sizeBytes > 0L) {
                                        " · ${DocumentMediaHelper.formatFileSize(previewInfo.sizeBytes)}"
                                    } else ""
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color.Black.copy(alpha = 0.72f),
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(10.dp)
                                    ) {
                                        Text(
                                            text = "🎬 ${TimeFormatters.formatDuration(previewInfo.durationSec)}$sizeLabel",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        videoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                        )
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Videocam,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Toca para elegir un video corto (hasta 5 MB)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 12.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { launchCameraForVideo() },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("status_record_video_button")
                            ) {
                                Icon(Icons.Filled.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Grabar")
                            }
                            OutlinedButton(
                                onClick = {
                                    videoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("status_pick_video_button")
                            ) {
                                Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Galería")
                            }
                        }

                        OutlinedTextField(
                            value = videoCaption,
                            onValueChange = { if (it.length <= 200) videoCaption = it },
                            label = { Text("Comentario del video (opcional)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("status_video_caption_input")
                        )
                    }
                }
            }
        },
        confirmButton = {
            val isEnabled = when (modeTab) {
                0 -> text.isNotBlank()
                1 -> pickedPhotoUri != null
                else -> pickedVideoUri != null
            }
            Button(
                onClick = {
                    when (modeTab) {
                        0 -> onPublishText(text, selectedHex)
                        1 -> pickedPhotoUri?.let { onPublishPhoto(it, photoCaption) }
                        2 -> pickedVideoUri?.let { onPublishVideo(it, videoCaption) }
                    }
                },
                enabled = isEnabled,
                modifier = Modifier.testTag("publish_status_button")
            ) {
                Text("Publicar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun StatusSegmentedProgressRow(
    itemCount: Int,
    activeIndex: Int,
    currentStatusId: String,
    isVideo: Boolean,
    videoProgress: Float,
    isPaused: Boolean,
    onAutoAdvance: () -> Unit
) {
    var timerProgress by remember(currentStatusId) { mutableFloatStateOf(0f) }

    LaunchedEffect(currentStatusId, isVideo, isPaused) {
        if (isVideo || isPaused) return@LaunchedEffect
        val totalMs = 6000L
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

@Composable
fun StatusViewerDialog(
    group: MemberStatusGroup,
    activeMemberId: String,
    allMembers: List<FamilyMemberEntity>,
    mediaTransferProgress: Map<String, Float> = emptyMap(),
    onMarkViewed: (statusId: String) -> Unit,
    onDeleteStatus: (statusId: String) -> Unit,
    onReplyStatus: (status: FamilyStatusEntity, replyText: String) -> Unit,
    onPrepareStatusVideo: (status: FamilyStatusEntity, onReady: (File) -> Unit) -> Unit = { _, _ -> },
    onSaveStatusToGallery: (status: FamilyStatusEntity) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
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

    // Video status state
    var readyVideoFile by remember(currentStatus.id) { mutableStateOf<File?>(null) }
    var videoViewRef by remember(currentStatus.id) { mutableStateOf<VideoView?>(null) }
    var mediaPlayerRef by remember(currentStatus.id) { mutableStateOf<android.media.MediaPlayer?>(null) }
    var isVideoPlaying by remember(currentStatus.id) { mutableStateOf(true) }
    var isVideoMuted by remember(currentStatus.id) { mutableStateOf(false) }
    var videoProgress by remember(currentStatus.id) { mutableFloatStateOf(0f) }

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
        if (currentStatus.isVideo) {
            onPrepareStatusVideo(currentStatus) { file ->
                readyVideoFile = file
            }
        }
    }

    val isOverlayPaused = replyInput.isNotEmpty() || showViewersSheet || showOptionsMenu || photoScale > 1.05f

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

    DisposableEffect(currentStatus.id) {
        onDispose {
            try {
                videoViewRef?.stopPlayback()
            } catch (_: Exception) {
            }
            mediaPlayerRef = null
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

    BackHandler(onBack = onDismiss)

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
                                            Text(if (isVideoMuted) "Activar sonido" else "Silenciar video")
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (isVideoMuted) Icons.AutoMirrored.Filled.VolumeUp
                                                else Icons.AutoMirrored.Filled.VolumeOff,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            showOptionsMenu = false
                                            val nextMute = !isVideoMuted
                                            isVideoMuted = nextMute
                                            try {
                                                val vol = if (nextMute) 0f else 1f
                                                mediaPlayerRef?.setVolume(vol, vol)
                                            } catch (_: Exception) {
                                            }
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
                if (hasMedia && currentStatus.text.isNotBlank()) {
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
                    if (file != null && file.exists() && availW > 0.dp && availH > 0.dp) {
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
                                                val vol = if (isVideoMuted) 0f else 1f
                                                mp.setVolume(vol, vol)
                                                start()
                                                isVideoPlaying = true
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
                    } else {
                        if (thumbBitmap != null) {
                            Image(
                                bitmap = thumbBitmap,
                                contentDescription = "Miniatura de video de estado",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        val dlPct = mediaTransferProgress[currentStatus.id]?.let { (it * 100f).toInt().coerceIn(5, 100) }
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
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = currentStatus.text,
                            color = Color.White,
                            fontSize = 26.sp,
                            lineHeight = 34.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Left / Right navigation tap strips (only active when not zoomed in)
            if (photoScale <= 1.05f) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .pointerInput(currentStatus.id, safeIdx) {
                                detectTapGestures(
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

