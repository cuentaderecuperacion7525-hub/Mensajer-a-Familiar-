package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.local.PreferencesManager
import com.example.data.model.ChatMessageEntity
import com.example.data.model.ChatType
import com.example.data.model.FamilyMemberEntity
import com.example.data.model.JsonHelpers
import com.example.data.model.MessageStatus
import com.example.data.model.MessageType
import com.example.ui.theme.ChatWallpaperDark
import com.example.ui.theme.ChatWallpaperLight
import com.example.ui.theme.LocalDarkTheme
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.ReadBlue
import com.example.ui.viewmodel.ChatThreadUi
import com.example.util.FamiChatNotificationHelper
import com.example.util.ImageCompressionHelper
import com.example.util.MessageFormatter
import com.example.util.TimeFormatters

data class ChatWallpaperOption(
    val key: String,
    val label: String,
    val lightBg: Color,
    val darkBg: Color
)

object ChatWallpaperPalette {
    val options = listOf(
        ChatWallpaperOption("default", "Clásico", ChatWallpaperLight, ChatWallpaperDark),
        ChatWallpaperOption("mint", "Menta", Color(0xFFE1F3EA), Color(0xFF0E241E)),
        ChatWallpaperOption("sky", "Cielo", Color(0xFFE3EEF9), Color(0xFF102030)),
        ChatWallpaperOption("sand", "Arena", Color(0xFFF6EFE2), Color(0xFF231E16)),
        ChatWallpaperOption("lavender", "Lavanda", Color(0xFFEDE7F6), Color(0xFF1F182D)),
        ChatWallpaperOption("rose", "Coral", Color(0xFFFCE8EC), Color(0xFF28161C))
    )

    fun resolveColor(key: String?, isDark: Boolean): Color {
        val opt = options.firstOrNull { it.key == key } ?: options.first()
        return if (isDark) opt.darkBg else opt.lightBg
    }
}

@Composable
fun ForwardMessageDialog(
    chatPreviews: List<ChatThreadUi>,
    currentChatId: String,
    onSelectTargetChat: (targetChatId: String) -> Unit,
    onDismiss: () -> Unit
) {
    val candidates = remember(chatPreviews, currentChatId) {
        chatPreviews.filter { it.chat.id != currentChatId }.ifEmpty { chatPreviews }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Forward, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Reenviar mensaje a...")
            }
        },
        text = {
            if (candidates.isEmpty()) {
                Text("No hay otros chats disponibles.")
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(candidates, key = { it.chat.id }) { chatUi ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onSelectTargetChat(chatUi.chat.id) }
                                .testTag("forward_to_${chatUi.chat.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MemberAvatar(
                                    emoji = chatUi.displayEmoji,
                                    colorHex = chatUi.displayColorHex,
                                    photoData = chatUi.displayPhotoData,
                                    size = 42.dp,
                                    isOnline = chatUi.isOnline,
                                    showOnlineDot = true
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = chatUi.displayTitle,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (chatUi.chat.type == ChatType.GROUP) "Grupo familiar" else chatUi.subtitleStatus,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun MessageInfoDialog(
    message: ChatMessageEntity,
    allMembers: List<FamilyMemberEntity>,
    onDismiss: () -> Unit
) {
    val membersMap = remember(allMembers) { allMembers.associateBy { it.id } }
    val readerIds = remember(message.readByMemberIdsJson, message.senderId) {
        JsonHelpers.decodeStringList(message.readByMemberIdsJson)
            .filterNot { it == message.senderId }
    }
    val playedIds = remember(message.playedByMemberIdsJson, message.senderId) {
        JsonHelpers.decodeStringList(message.playedByMemberIdsJson)
            .filterNot { it == message.senderId }
    }
    val preview = when (message.type) {
        MessageType.IMAGE -> if (message.text.isNotBlank()) "📷 ${message.text}" else "📷 Foto"
        MessageType.VIDEO -> if (message.text.isNotBlank()) "🎬 ${message.text}" else "🎬 Video"
        MessageType.VOICE -> "🎤 Nota de voz (${TimeFormatters.formatDuration(message.voiceDurationSec)})"
        MessageType.LOCATION -> "📍 ${message.locationName ?: "Ubicación"}"
        MessageType.DOCUMENT -> "📄 ${message.fileName ?: "Documento"}"
        MessageType.POLL -> "📊 ${message.pollQuestion ?: "Encuesta"}"
        MessageType.DELETED -> "Mensaje eliminado"
        MessageType.TEXT -> MessageFormatter.stripMarkdownForPreview(message.text)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Info. del mensaje") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = preview,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            MessageStatusIcon(status = message.status)
                            Text(
                                text = when (message.status) {
                                    MessageStatus.SENDING -> "Enviando..."
                                    MessageStatus.SENT -> "Enviado · ${TimeFormatters.formatDateTime(message.timestamp)}"
                                    MessageStatus.DELIVERED -> "Entregado (✓✓ gris) · ${TimeFormatters.formatDateTime(message.timestamp)}"
                                    MessageStatus.READ -> "Leído (✓✓ azul) · ${TimeFormatters.formatDateTime(message.timestamp)}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (message.status == MessageStatus.READ) ReadBlue else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                HorizontalDivider()

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.DoneAll, contentDescription = null, tint = ReadBlue, modifier = Modifier.size(18.dp))
                    Text(
                        text = "Leído por (${readerIds.size})",
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                if (readerIds.isEmpty()) {
                    Text(
                        text = if (message.status == MessageStatus.READ) "Confirmado como leído en el chat." else "Aún sin lectura confirmada por destinatarios.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        readerIds.forEach { id ->
                            val member = membersMap[id]
                            if (member != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    MemberAvatar(
                                        emoji = member.avatarEmoji,
                                        colorHex = member.avatarColorHex,
                                        photoData = member.avatarPhotoData,
                                        size = 34.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(member.name, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }

                if (message.type == MessageType.VOICE) {
                    HorizontalDivider()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Filled.VolumeUp, contentDescription = null, tint = ReadBlue, modifier = Modifier.size(18.dp))
                        Text(
                            text = "Escuchado por (${playedIds.size})",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    if (playedIds.isEmpty()) {
                        Text(
                            text = "Aún no reproducido por el destinatario.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            playedIds.forEach { id ->
                                val member = membersMap[id]
                                if (member != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        MemberAvatar(
                                            emoji = member.avatarEmoji,
                                            colorHex = member.avatarColorHex,
                                            photoData = member.avatarPhotoData,
                                            size = 34.dp
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text("${member.name} (Escuchado)", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatInfoAndGalleryDialog(
    chatUi: ChatThreadUi,
    imageMessages: List<ChatMessageEntity>,
    selectedWallpaperKey: String,
    onSelectWallpaper: (String) -> Unit,
    onOpenImage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val isDark = LocalDarkTheme.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .testTag("chat_info_gallery_dialog")
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with avatar, name, close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MemberAvatar(
                        emoji = chatUi.displayEmoji,
                        colorHex = chatUi.displayColorHex,
                        photoData = chatUi.displayPhotoData,
                        size = 58.dp,
                        isOnline = chatUi.isOnline,
                        showOnlineDot = true
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = chatUi.displayTitle,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = chatUi.subtitleStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (chatUi.isOnline) OnlineGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                    }
                }

                // Bio / Description
                if (chatUi.partnerBioText.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Bio / Info personal",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(chatUi.partnerBioText, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                // Group Participants
                if (chatUi.chat.type == ChatType.GROUP) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Filled.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text(
                                text = "Integrantes (${chatUi.participants.size})",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            chatUi.participants.forEach { participant ->
                                Surface(
                                    shape = RoundedCornerShape(50.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        MemberAvatar(
                                            emoji = participant.avatarEmoji,
                                            colorHex = participant.avatarColorHex,
                                            photoData = participant.avatarPhotoData,
                                            size = 24.dp,
                                            isOnline = participant.isOnline,
                                            showOnlineDot = true
                                        )
                                        Text(participant.name, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))

                // Chat Wallpaper Picker
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Filled.Wallpaper, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text("Fondo de este chat", style = MaterialTheme.typography.titleSmall)
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(ChatWallpaperPalette.options, key = { it.key }) { opt ->
                            val selected = selectedWallpaperKey == opt.key ||
                                (selectedWallpaperKey.isBlank() && opt.key == "default")
                            val swatch = if (isDark) opt.darkBg else opt.lightBg
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSelectWallpaper(opt.key) }
                                    .padding(4.dp)
                                    .testTag("wallpaper_${opt.key}")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(swatch)
                                        .border(
                                            width = if (selected) 2.5.dp else 1.dp,
                                            color = if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selected) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = "Seleccionado",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = opt.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))

                // Shared Photos Gallery
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text("Fotos compartidas (${imageMessages.size})", style = MaterialTheme.typography.titleSmall)
                    }
                    if (imageMessages.isEmpty()) {
                        Text(
                            text = "Aún no se han compartido fotos en este chat.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val rows = remember(imageMessages) { imageMessages.reversed().chunked(3) }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            rows.forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    rowItems.forEach { msg ->
                                        val media = msg.mediaUri.orEmpty()
                                        AsyncImage(
                                            model = ImageCompressionHelper.resolveCoilModel(media),
                                            contentDescription = msg.text.ifBlank { "Foto compartida" },
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable { onOpenImage(media) }
                                        )
                                    }
                                    repeat(3 - rowItems.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatWallpaperPickerDialog(
    selectedWallpaperKey: String,
    onSelectWallpaper: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val isDark = LocalDarkTheme.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Wallpaper,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Fondo del chat")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Elige el color de fondo para esta conversación:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(ChatWallpaperPalette.options, key = { it.key }) { opt ->
                        val selected = selectedWallpaperKey == opt.key ||
                            (selectedWallpaperKey.isBlank() && opt.key == "default")
                        val swatch = if (isDark) opt.darkBg else opt.lightBg
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onSelectWallpaper(opt.key)
                                    onDismiss()
                                }
                                .padding(4.dp)
                                .testTag("dialog_wallpaper_${opt.key}")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(swatch)
                                    .border(
                                        width = if (selected) 2.5.dp else 1.dp,
                                        color = if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Seleccionado",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = opt.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

@Composable
fun PushAndCallDiagnosticsDialog(
    familyId: String,
    memberName: String,
    notificationsEnabled: Boolean,
    fcmToken: String,
    turnUrl: String,
    turnUsername: String,
    turnCredential: String,
    onSendTestNotification: () -> Unit,
    onSaveTurnConfig: (url: String, username: String, credential: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val systemNotifGranted = remember { FamiChatNotificationHelper.canPostNotifications(context) }
    val sanitizedTopic = remember(familyId) {
        val clean = familyId.replace(Regex("[^a-zA-Z0-9-_.~%]"), "_").take(48)
        "famichat_${clean.ifBlank { "default" }}"
    }

    var editTurnUrl by remember(turnUrl) { mutableStateOf(turnUrl) }
    var editTurnUser by remember(turnUsername) { mutableStateOf(turnUsername) }
    var editTurnCred by remember(turnCredential) { mutableStateOf(turnCredential) }
    var copiedBanner by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .testTag("push_call_diagnostics_dialog")
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Diagnóstico Push y Llamadas",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Estado de entregas ✓✓, FCM y servidores TURN 4G/5G",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DiagnosticStatusRow(
                            label = "Permiso de notificaciones del sistema",
                            ok = systemNotifGranted && notificationsEnabled,
                            detail = if (systemNotifGranted && notificationsEnabled) "Activo" else "Desactivado"
                        )
                        DiagnosticStatusRow(
                            label = "Doble palomita gris (✓✓) en 2º plano",
                            ok = true,
                            detail = "Automática (FCM + WorkManager)"
                        )
                        DiagnosticStatusRow(
                            label = "Canal Push FCM familiar",
                            ok = familyId.isNotBlank(),
                            detail = sanitizedTopic
                        )
                        DiagnosticStatusRow(
                            label = "Token FCM del dispositivo ($memberName)",
                            ok = fcmToken.isNotBlank(),
                            detail = if (fcmToken.isNotBlank()) "${fcmToken.take(16)}..." else "Pendiente de Google Play Services"
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onSendTestNotification,
                        modifier = Modifier.weight(1f).testTag("test_push_notification_button")
                    ) {
                        Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Probar alerta")
                    }
                    if (fcmToken.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(fcmToken))
                                copiedBanner = "Token FCM copiado al portapapeles"
                            },
                            modifier = Modifier.testTag("copy_fcm_token_button")
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Token")
                        }
                    }
                }

                if (copiedBanner != null) {
                    Text(
                        text = copiedBanner!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = OnlineGreen
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Guía rápida: Push instantáneo con app cerrada",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "1. FamiChat ya sincroniza tu token FCM en Firestore (members.fcmTokens) y se suscribe al tema '$sanitizedTopic' + ejecuta WorkManager cada 15 min.\n" +
                                "2. Cuando un mensaje llega en segundo plano, marca automáticamente Entregado (✓✓ gris) en la nube.\n" +
                                "3. Para disparo instantáneo (<1 seg) tras cierre forzoso del sistema, puedes activar un trigger Cloud Function en 'families/{familyId}/messages/{msgId}' que envíe un data message al tópico '$sanitizedTopic'.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider()

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Router,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Servidores TURN de respaldo (Datos móviles 4G/5G)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "Permite que las llamadas de voz y video crucen redes móviles con NAT estricto.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedTextField(
                    value = editTurnUrl,
                    onValueChange = { editTurnUrl = it },
                    label = { Text("Servidores TURN (separados por coma)") },
                    singleLine = false,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("turn_url_input")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = editTurnUser,
                        onValueChange = { editTurnUser = it },
                        label = { Text("Usuario TURN") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("turn_user_input")
                    )
                    OutlinedTextField(
                        value = editTurnCred,
                        onValueChange = { editTurnCred = it },
                        label = { Text("Clave TURN") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("turn_cred_input")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            editTurnUrl = PreferencesManager.DEFAULT_TURN_URL
                            editTurnUser = PreferencesManager.DEFAULT_TURN_USERNAME
                            editTurnCred = PreferencesManager.DEFAULT_TURN_CREDENTIAL
                            onSaveTurnConfig(editTurnUrl, editTurnUser, editTurnCred)
                        }
                    ) {
                        Text("Restaurar OpenRelay")
                    }
                    Button(
                        onClick = {
                            onSaveTurnConfig(editTurnUrl, editTurnUser, editTurnCred)
                            onDismiss()
                        },
                        modifier = Modifier.testTag("save_turn_config_button")
                    ) {
                        Text("Guardar TURN")
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticStatusRow(
    label: String,
    ok: Boolean,
    detail: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = if (ok) OnlineGreen else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = detail,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
