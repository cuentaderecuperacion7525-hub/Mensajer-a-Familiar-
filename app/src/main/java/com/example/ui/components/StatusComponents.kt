package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.FamilyMemberEntity
import com.example.data.model.FamilyStatusEntity
import com.example.data.model.JsonHelpers
import com.example.util.CameraCaptureHelper
import com.example.util.ImageCompressionHelper
import com.example.util.TimeFormatters
import com.example.util.parseHexColor
import kotlinx.coroutines.delay

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
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(68.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        if (myStatuses.isNotEmpty()) {
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
                Box(modifier = Modifier.size(58.dp), contentAlignment = Alignment.Center) {
                    val ringColor = if (myStatuses.isNotEmpty()) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .border(2.dp, ringColor, CircleShape)
                            .padding(3.dp)
                    ) {
                        MemberAvatar(
                            emoji = activeMember.avatarEmoji,
                            colorHex = activeMember.avatarColorHex,
                            photoData = activeMember.avatarPhotoData,
                            size = 50.dp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
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
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (myStatuses.isNotEmpty()) "Mi estado (${myStatuses.size})" else "Mi estado",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Other family members' statuses
        items(otherGroups, key = { it.member.id }) { group ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(68.dp)
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
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .border(2.5.dp, ringColor, CircleShape)
                        .padding(3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    MemberAvatar(
                        emoji = group.member.avatarEmoji,
                        colorHex = group.member.avatarColorHex,
                        photoData = group.member.avatarPhotoData,
                        size = 50.dp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = group.member.name,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (group.hasUnviewed) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = if (group.hasUnviewed) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun CreateStatusDialog(
    onDismiss: () -> Unit,
    onPublishText: (text: String, backgroundHex: String) -> Unit,
    onPublishPhoto: (imageUri: Uri, caption: String) -> Unit
) {
    val context = LocalContext.current
    var modeTab by remember { mutableIntStateOf(0) } // 0 = Text, 1 = Photo
    var text by remember { mutableStateOf("") }
    var selectedHex by remember { mutableStateOf(StatusBackgroundColors.first()) }
    var pickedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var caption by remember { mutableStateOf("") }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> if (uri != null) pickedPhotoUri = uri }
    )

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success && pendingCameraUri != null) {
                pickedPhotoUri = pendingCameraUri
            }
        }
    )

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                val uri = CameraCaptureHelper.createTempPhotoUri(context)
                if (uri != null) {
                    pendingCameraUri = uri
                    takePictureLauncher.launch(uri)
                }
            }
        }
    )

    fun launchCameraForStatus() {
        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            val uri = CameraCaptureHelper.createTempPhotoUri(context)
            if (uri != null) {
                pendingCameraUri = uri
                takePictureLauncher.launch(uri)
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
                        text = { Text("Texto") }
                    )
                    Tab(
                        selected = modeTab == 1,
                        onClick = { modeTab = 1 },
                        text = { Text("Foto") }
                    )
                }

                if (modeTab == 0) {
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
                } else {
                    if (pickedPhotoUri != null) {
                        AsyncImage(
                            model = pickedPhotoUri,
                            contentDescription = "Foto para estado",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(16.dp))
                        )
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
                                    pickerLauncher.launch(
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
                            onClick = { launchCameraForStatus() },
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
                                pickerLauncher.launch(
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
                        value = caption,
                        onValueChange = { if (it.length <= 200) caption = it },
                        label = { Text("Comentario (opcional)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("status_photo_caption_input")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (modeTab == 0) onPublishText(text, selectedHex)
                    else pickedPhotoUri?.let { onPublishPhoto(it, caption) }
                },
                enabled = if (modeTab == 0) text.isNotBlank() else pickedPhotoUri != null,
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
fun StatusViewerDialog(
    group: MemberStatusGroup,
    activeMemberId: String,
    allMembers: List<FamilyMemberEntity>,
    onMarkViewed: (statusId: String) -> Unit,
    onDeleteStatus: (statusId: String) -> Unit,
    onReplyStatus: (status: FamilyStatusEntity, replyText: String) -> Unit,
    onDismiss: () -> Unit
) {
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
    var showViewersSheet by remember { mutableStateOf(false) }
    var segmentProgress by remember(currentStatus.id) { mutableFloatStateOf(0f) }

    LaunchedEffect(currentStatus.id) {
        onMarkViewed(currentStatus.id)
    }

    LaunchedEffect(currentStatus.id, replyInput.isNotEmpty(), showViewersSheet) {
        if (replyInput.isNotEmpty() || showViewersSheet) return@LaunchedEffect
        val totalMs = 5500L
        val stepMs = 50L
        val steps = (totalMs / stepMs).toInt()
        val startStep = (segmentProgress * steps).toInt()
        for (i in startStep..steps) {
            segmentProgress = i.toFloat() / steps.toFloat()
            delay(stepMs)
        }
        if (safeIdx < items.lastIndex) {
            currentIndex = safeIdx + 1
        } else {
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val bgColor = if (!currentStatus.mediaUri.isNullOrBlank()) {
            Color.Black
        } else {
            parseHexColor(currentStatus.backgroundHex)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
        ) {
            // Content
            if (!currentStatus.mediaUri.isNullOrBlank()) {
                AsyncImage(
                    model = ImageCompressionHelper.resolveCoilModel(currentStatus.mediaUri),
                    contentDescription = "Foto de estado",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
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

            // Left / Right invisible tap zones (below header, above footer)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 96.dp, bottom = 96.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable {
                            if (safeIdx > 0) currentIndex = safeIdx - 1
                            else segmentProgress = 0f
                        }
                )
                Box(
                    modifier = Modifier
                        .weight(2f)
                        .fillMaxHeight()
                        .clickable {
                            if (safeIdx < items.lastIndex) currentIndex = safeIdx + 1
                            else onDismiss()
                        }
                )
            }

            // Top gradient + progress segments + author bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent)
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 14.dp)
                    .align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items.forEachIndexed { idx, _ ->
                        val prog = when {
                            idx < safeIdx -> 1f
                            idx == safeIdx -> segmentProgress
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
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MemberAvatar(
                        emoji = group.member.avatarEmoji,
                        colorHex = group.member.avatarColorHex,
                        photoData = group.member.avatarPhotoData,
                        size = 40.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isMine) "Mi estado" else group.member.name,
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = TimeFormatters.formatLastSeen(currentStatus.createdAt)
                                .replace("Últ. vez ", "Publicado "),
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    if (isMine) {
                        IconButton(onClick = {
                            val deletingId = currentStatus.id
                            onDeleteStatus(deletingId)
                            if (items.size <= 1) onDismiss()
                            else if (safeIdx >= items.lastIndex) currentIndex = (safeIdx - 1).coerceAtLeast(0)
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Eliminar estado", tint = Color.White)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                }
            }

            // Bottom area: Photo caption + Reply bar or Viewers button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 18.dp)
                    .align(Alignment.BottomCenter),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!currentStatus.mediaUri.isNullOrBlank() && currentStatus.text.isNotBlank()) {
                    Text(
                        text = currentStatus.text,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                if (isMine) {
                    val viewerIds = remember(currentStatus.viewedByMemberIdsJson) {
                        JsonHelpers.decodeStringList(currentStatus.viewedByMemberIdsJson)
                            .filterNot { it == activeMemberId }
                    }
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
                        OutlinedTextField(
                            value = replyInput,
                            onValueChange = { replyInput = it },
                            placeholder = { Text("Responder al estado...", color = Color.White.copy(alpha = 0.7f)) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.White.copy(alpha = 0.8f),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.45f),
                                cursorColor = Color.White
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.weight(1f)
                        )
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
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Enviar respuesta",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }

            if (showViewersSheet) {
                val viewerIds = JsonHelpers.decodeStringList(currentStatus.viewedByMemberIdsJson)
                    .filterNot { it == activeMemberId }
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
    }
}
