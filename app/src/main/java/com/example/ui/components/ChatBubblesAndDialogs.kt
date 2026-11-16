package com.example.ui.components

import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt
import coil.compose.AsyncImage
import com.example.data.model.ChatMessageEntity
import com.example.data.model.FamilyMemberEntity
import com.example.data.model.JsonHelpers
import com.example.data.model.MessageType
import com.example.ui.theme.IncomingBubbleDark
import com.example.ui.theme.IncomingBubbleLight
import com.example.ui.theme.LocalDarkTheme
import com.example.ui.theme.OutgoingBubbleDark
import com.example.ui.theme.OutgoingBubbleLight
import com.example.util.ImageCompressionHelper
import com.example.util.LocationHelper
import com.example.util.MessageFormatter
import com.example.util.TimeFormatters
import com.example.util.parseHexColor
import kotlinx.coroutines.launch

val QuickReactionEmojis = listOf("❤️", "😂", "👍", "😮", "🙏", "🎉")

val PickerEmojis = listOf(
    "😀", "😂", "🥰", "😍", "🥳", "😎", "🤔", "🙌",
    "👍", "❤️", "🔥", "🎉", "🙏", "👏", "🏡", "🍕",
    "🌮", "☕", "🛒", "⚽", "🚗", "📍", "📷", "✅"
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ChatMessageBubble(
    message: ChatMessageEntity,
    isMine: Boolean,
    isGroupChat: Boolean,
    sender: FamilyMemberEntity?,
    repliedMessage: ChatMessageEntity?,
    repliedSenderName: String?,
    activeMemberId: String,
    isSelected: Boolean,
    isMultiSelectMode: Boolean,
    isPlayingVoice: Boolean,
    voicePlaybackProgress: Float,
    searchQuery: String,
    onToggleSelect: () -> Unit,
    onLongPress: () -> Unit,
    onReplyClick: (String) -> Unit,
    onImageClick: (String) -> Unit,
    onToggleVoicePlay: () -> Unit,
    onVotePoll: (String) -> Unit,
    onToggleReaction: (String) -> Unit,
    onSwipeReply: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val dark = LocalDarkTheme.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val swipeOffsetX = remember(message.id) { Animatable(0f) }

    val bubbleBg = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        isMine && dark -> OutgoingBubbleDark
        isMine -> OutgoingBubbleLight
        dark -> IncomingBubbleDark
        else -> IncomingBubbleLight
    }
    val bubbleBorderColor = if (!dark && !isMine && !isSelected) {
        MaterialTheme.colorScheme.outline
    } else Color.Transparent
    val bubbleShape = if (isMine) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
    }

    val reactionsMap = remember(message.reactionsJson) { JsonHelpers.decodeReactions(message.reactionsJson) }
    val groupedReactions = remember(reactionsMap) {
        reactionsMap.values.groupingBy { it }.eachCount()
    }

    val swipeThresholdPx = 115f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onSwipeReply != null && !isMultiSelectMode && message.type != MessageType.DELETED) {
                    Modifier.pointerInput(message.id, isMultiSelectMode, message.type) {
                        var didHaptic = false
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (swipeOffsetX.value >= swipeThresholdPx) {
                                    onSwipeReply()
                                }
                                didHaptic = false
                                scope.launch { swipeOffsetX.animateTo(0f) }
                            },
                            onDragCancel = {
                                didHaptic = false
                                scope.launch { swipeOffsetX.animateTo(0f) }
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                val next = (swipeOffsetX.value + dragAmount).coerceIn(0f, 165f)
                                if (next >= swipeThresholdPx && !didHaptic) {
                                    didHaptic = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                scope.launch { swipeOffsetX.snapTo(next) }
                            }
                        )
                    }
                } else Modifier
            )
    ) {
        if (swipeOffsetX.value > 10f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = (swipeOffsetX.value / swipeThresholdPx).coerceIn(0f, 0.25f))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Reply,
                    contentDescription = "Responder deslizando",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = (swipeOffsetX.value / swipeThresholdPx).coerceIn(0.25f, 1f)),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(swipeOffsetX.value.roundToInt(), 0) }
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    else Color.Transparent
                )
                .padding(horizontal = 12.dp, vertical = 3.dp),
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
        ) {
            Surface(
                shape = bubbleShape,
                color = bubbleBg,
                tonalElevation = 0.dp,
                shadowElevation = if (dark) 0.dp else 1.dp,
                modifier = Modifier
                    .widthIn(min = 96.dp, max = 310.dp)
                    .border(
                        width = if (bubbleBorderColor != Color.Transparent) 0.75.dp else 0.dp,
                        color = bubbleBorderColor,
                        shape = bubbleShape
                    )
                    .clip(bubbleShape)
                    .combinedClickable(
                        onClick = {
                            if (isMultiSelectMode) onToggleSelect()
                            else onLongPress()
                        },
                        onLongClick = onLongPress
                    )
                    .testTag("message_bubble_${message.id}")
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (!message.forwardedFromName.isNullOrBlank() && message.type != MessageType.DELETED) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(bottom = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Forward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Reenviado de ${message.forwardedFromName}",
                                style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (isGroupChat && !isMine && sender != null) {
                        Text(
                            text = "${sender.avatarEmoji} ${sender.name}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = parseHexColor(sender.avatarColorHex)
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                    }

                if (repliedMessage != null && message.type != MessageType.DELETED) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onReplyClick(repliedMessage.id) }
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(28.dp)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = repliedSenderName ?: "Mensaje",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                val snippet = when (repliedMessage.type) {
                                    MessageType.IMAGE -> "📷 Foto"
                                    MessageType.VOICE -> "🎤 Nota de voz"
                                    MessageType.LOCATION -> "📍 ${repliedMessage.locationName ?: "Ubicación"}"
                                    MessageType.POLL -> "📊 ${repliedMessage.pollQuestion ?: "Encuesta"}"
                                    MessageType.DELETED -> "Mensaje eliminado"
                                    MessageType.TEXT -> MessageFormatter.stripMarkdownForPreview(repliedMessage.text)
                                }
                                Text(
                                    text = snippet,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                when (message.type) {
                    MessageType.DELETED -> {
                        Text(
                            text = "🚫 Mensaje eliminado para todos",
                            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    MessageType.TEXT -> {
                        Text(
                            text = MessageFormatter.formatRichText(message.text, searchQuery),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    MessageType.IMAGE -> {
                        if (!message.mediaUri.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageCompressionHelper.resolveCoilModel(message.mediaUri),
                                contentDescription = "Imagen compartida",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(190.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        if (isMultiSelectMode) onToggleSelect()
                                        else onImageClick(message.mediaUri)
                                    }
                            )
                        }
                        if (message.text.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = MessageFormatter.formatRichText(message.text, searchQuery),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    MessageType.VOICE -> {
                        val amps = remember(message.voiceWaveformJson) {
                            JsonHelpers.decodeFloatList(message.voiceWaveformJson)
                                .ifEmpty { listOf(0.4f, 0.7f, 0.5f, 0.9f, 0.6f, 0.4f, 0.8f, 0.5f) }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            IconButton(
                                onClick = {
                                    if (isMultiSelectMode) onToggleSelect() else onToggleVoicePlay()
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .testTag("play_voice_${message.id}")
                            ) {
                                Icon(
                                    imageVector = if (isPlayingVoice) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (isPlayingVoice) "Pausar nota de voz" else "Reproducir nota de voz",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().height(26.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    amps.forEachIndexed { idx, amp ->
                                        val frac = (idx + 1).toFloat() / amps.size.toFloat()
                                        val active = isPlayingVoice && frac <= voicePlaybackProgress
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight(amp.coerceIn(0.2f, 1f))
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(
                                                    if (active) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                                )
                                        )
                                    }
                                }
                                Text(
                                    text = if (isPlayingVoice) {
                                        val elapsed = (message.voiceDurationSec * voicePlaybackProgress).toInt()
                                        "${TimeFormatters.formatDuration(elapsed)} / ${TimeFormatters.formatDuration(message.voiceDurationSec)}"
                                    } else {
                                        TimeFormatters.formatDuration(message.voiceDurationSec)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    MessageType.LOCATION -> {
                        val locTitle = message.locationName ?: "Ubicación"
                        val locAddr = message.locationAddress.orEmpty()
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    if (isMultiSelectMode) onToggleSelect()
                                    else LocationHelper.openInMaps(
                                        context = context,
                                        name = locTitle,
                                        address = locAddr,
                                        lat = message.locationLat,
                                        lng = message.locationLng
                                    )
                                }
                        ) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(86.dp)
                                        .background(if (dark) Color(0xFF1E293B) else Color(0xFFE7EEF6)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val gridColor = if (dark) Color.White.copy(alpha = 0.14f) else Color.White
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        drawLine(
                                            color = gridColor,
                                            start = Offset(0f, size.height * 0.35f),
                                            end = Offset(size.width, size.height * 0.65f),
                                            strokeWidth = 10f
                                        )
                                        drawLine(
                                            color = gridColor,
                                            start = Offset(size.width * 0.3f, 0f),
                                            end = Offset(size.width * 0.65f, size.height),
                                            strokeWidth = 8f
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Filled.LocationOn,
                                            contentDescription = "Ubicación",
                                            tint = Color.White
                                        )
                                    }
                                }
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = locTitle,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    if (locAddr.isNotBlank()) {
                                        Text(
                                            text = locAddr,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Toca para abrir en el mapa",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                    MessageType.POLL -> {
                        val options = remember(message.pollOptionsJson) {
                            JsonHelpers.decodePollOptions(message.pollOptionsJson)
                        }
                        val totalVotes = remember(options) { options.sumOf { it.voterIds.size } }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Poll,
                                contentDescription = "Encuesta",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = message.pollQuestion ?: "Encuesta",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        options.forEach { opt ->
                            val votedByMe = opt.voterIds.contains(activeMemberId)
                            val pct = if (totalVotes > 0) (opt.voterIds.size * 100) / totalVotes else 0
                            val fraction = if (totalVotes > 0) opt.voterIds.size.toFloat() / totalVotes else 0f
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        if (isMultiSelectMode) onToggleSelect() else onVotePoll(opt.id)
                                    }
                                    .testTag("poll_option_${message.id}_${opt.id}")
                            ) {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(fraction)
                                            .matchParentSize()
                                            .background(
                                                if (votedByMe) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                            )
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            if (votedByMe) {
                                                Icon(
                                                    Icons.Filled.CheckCircle,
                                                    contentDescription = "Tu voto",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                            }
                                            Text(opt.text, style = MaterialTheme.typography.bodyMedium)
                                        }
                                        Text(
                                            text = "$pct% (${opt.voterIds.size})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            text = "$totalVotes voto(s) · Toca una opción para votar",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (message.isEdited && message.type != MessageType.DELETED) {
                        Text(
                            text = "editado",
                            style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = TimeFormatters.formatHourMinute(message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isMine && message.type != MessageType.DELETED) {
                        MessageStatusIcon(status = message.status)
                    }
                }
            }
        }

        if (groupedReactions.isNotEmpty() && message.type != MessageType.DELETED) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 2.dp)
            ) {
                groupedReactions.forEach { (emoji, count) ->
                    val reactedByMe = reactionsMap[activeMemberId] == emoji
                    Surface(
                        shape = RoundedCornerShape(50.dp),
                        color = if (reactedByMe) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .clickable { onToggleReaction(emoji) }
                    ) {
                        Text(
                            text = if (count > 1) "$emoji $count" else emoji,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageActionsBottomSheet(
    message: ChatMessageEntity,
    isMine: Boolean,
    isAdmin: Boolean,
    isPinned: Boolean,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    onReply: () -> Unit,
    onForward: () -> Unit = {},
    onMessageInfo: () -> Unit = {},
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onTogglePin: () -> Unit,
    onStartMultiSelect: () -> Unit,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 20.dp)) {
            if (message.type != MessageType.DELETED) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    QuickReactionEmojis.forEach { emoji ->
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .clickable {
                                    onReact(emoji)
                                    onDismiss()
                                }
                                .testTag("react_$emoji")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(emoji, fontSize = 22.sp)
                            }
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                ListItem(
                    headlineContent = { Text("Responder") },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null) },
                    modifier = Modifier.clickable { onReply(); onDismiss() }.testTag("action_reply")
                )
                ListItem(
                    headlineContent = { Text("Reenviar") },
                    leadingContent = { Icon(Icons.Filled.Forward, contentDescription = null) },
                    modifier = Modifier.clickable { onForward(); onDismiss() }.testTag("action_forward")
                )
                if (isMine) {
                    ListItem(
                        headlineContent = { Text("Info. del mensaje") },
                        leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
                        modifier = Modifier.clickable { onMessageInfo(); onDismiss() }.testTag("action_info")
                    )
                }
                if (message.text.isNotBlank()) {
                    ListItem(
                        headlineContent = { Text("Copiar texto") },
                        leadingContent = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                        modifier = Modifier.clickable { onCopy(); onDismiss() }.testTag("action_copy")
                    )
                }
                if (isMine && message.type == MessageType.TEXT) {
                    ListItem(
                        headlineContent = { Text("Editar mensaje") },
                        leadingContent = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        modifier = Modifier.clickable { onEdit(); onDismiss() }.testTag("action_edit")
                    )
                }
                ListItem(
                    headlineContent = { Text(if (isPinned) "Desfijar mensaje" else "Fijar mensaje arriba") },
                    leadingContent = { Icon(Icons.Filled.PushPin, contentDescription = null) },
                    modifier = Modifier.clickable { onTogglePin(); onDismiss() }.testTag("action_pin")
                )
            }
            ListItem(
                headlineContent = { Text("Seleccionar varios mensajes") },
                leadingContent = { Icon(Icons.Filled.SelectAll, contentDescription = null) },
                modifier = Modifier.clickable { onStartMultiSelect(); onDismiss() }.testTag("action_multiselect")
            )
            ListItem(
                headlineContent = { Text("Eliminar para mí") },
                leadingContent = { Icon(Icons.Filled.Delete, contentDescription = null) },
                modifier = Modifier.clickable { onDeleteForMe(); onDismiss() }.testTag("action_delete_me")
            )
            if ((isMine || isAdmin) && message.type != MessageType.DELETED) {
                ListItem(
                    headlineContent = { Text("Eliminar para todos", color = MaterialTheme.colorScheme.error) },
                    leadingContent = {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    },
                    modifier = Modifier.clickable { onDeleteForEveryone(); onDismiss() }.testTag("action_delete_all")
                )
            }
        }
    }
}

@Composable
fun ImagePreviewSendDialog(
    imageUri: Uri,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit
) {
    var caption by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Vista previa de foto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = "Vista previa",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    label = { Text("Añadir comentario (opcional)") },
                    modifier = Modifier.fillMaxWidth().testTag("image_caption_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSend(caption) },
                modifier = Modifier.testTag("send_image_confirm")
            ) { Text("Enviar foto") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun FullScreenImageDialog(
    imageUri: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onDismiss)
        ) {
            AsyncImage(
                model = ImageCompressionHelper.resolveCoilModel(imageUri),
                contentDescription = "Imagen ampliada",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(16.dp)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Cerrar vista previa", tint = Color.White)
            }
        }
    }
}

@Composable
fun LocationPickerDialog(
    onDismiss: () -> Unit,
    onSendLocation: (name: String, address: String, lat: Double?, lng: Double?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var customName by remember { mutableStateOf("") }
    var customAddress by remember { mutableStateOf("") }
    var resolvedLat by remember { mutableStateOf<Double?>(null) }
    var resolvedLng by remember { mutableStateOf<Double?>(null) }
    var isFetchingGps by remember { mutableStateOf(false) }
    var gpsError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compartir ubicación") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            isFetchingGps = true
                            gpsError = null
                            LocationHelper.fetchCurrentGpsLocation(context)
                                .onSuccess { gps ->
                                    if (customName.isBlank()) customName = gps.title
                                    customAddress = gps.address
                                    resolvedLat = gps.latitude
                                    resolvedLng = gps.longitude
                                }
                                .onFailure { err ->
                                    gpsError = err.localizedMessage ?: "No se pudo obtener el GPS actual."
                                }
                            isFetchingGps = false
                        }
                    },
                    enabled = !isFetchingGps,
                    modifier = Modifier.fillMaxWidth().testTag("fetch_gps_button")
                ) {
                    if (isFetchingGps) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Obteniendo GPS...")
                    } else {
                        Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Obtener mi ubicación GPS actual")
                    }
                }

                if (gpsError != null) {
                    Text(
                        text = gpsError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (resolvedLat != null && resolvedLng != null) {
                    Text(
                        text = String.format(java.util.Locale.US, "GPS listo: %.5f, %.5f", resolvedLat, resolvedLng),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                OutlinedTextField(
                    value = customName,
                    onValueChange = { customName = it },
                    label = { Text("Nombre del lugar") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("location_name_input")
                )
                OutlinedTextField(
                    value = customAddress,
                    onValueChange = { customAddress = it },
                    label = { Text("Dirección o referencia") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("location_address_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSendLocation(customName, customAddress, resolvedLat, resolvedLng) },
                enabled = customName.isNotBlank() || resolvedLat != null,
                modifier = Modifier.testTag("location_send_button")
            ) { Text("Enviar ubicación") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun PollCreatorDialog(
    onDismiss: () -> Unit,
    onCreatePoll: (question: String, options: List<String>) -> Unit
) {
    var question by remember { mutableStateOf("") }
    val options = remember { mutableStateListOf("", "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crear encuesta rápida") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("Pregunta") },
                    modifier = Modifier.fillMaxWidth().testTag("poll_question_input")
                )
                options.forEachIndexed { idx, value ->
                    OutlinedTextField(
                        value = value,
                        onValueChange = { options[idx] = it },
                        label = { Text("Opción ${idx + 1}") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("poll_option_input_$idx")
                    )
                }
                if (options.size < 4) {
                    TextButton(onClick = { options.add("") }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Añadir opción")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreatePoll(question, options.toList()) },
                enabled = question.isNotBlank() && options.count { it.isNotBlank() } >= 2,
                modifier = Modifier.testTag("poll_send_button")
            ) { Text("Enviar encuesta") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
