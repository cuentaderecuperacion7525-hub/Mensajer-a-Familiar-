package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CallType
import com.example.data.model.ChatMessageEntity
import com.example.data.model.ChatType
import com.example.data.model.FamilyMemberEntity
import com.example.data.model.JsonHelpers
import com.example.data.model.MessageType
import com.example.data.model.PresenceStatus
import com.example.ui.components.ChatMessageBubble
import com.example.ui.components.FullScreenImageDialog
import com.example.ui.components.ImagePreviewSendDialog
import com.example.ui.components.LocationPickerDialog
import com.example.ui.components.MemberAvatar
import com.example.ui.components.MessageActionsBottomSheet
import com.example.ui.components.PickerEmojis
import com.example.ui.components.PollCreatorDialog
import com.example.ui.theme.ChatWallpaperDark
import com.example.ui.theme.ChatWallpaperLight
import com.example.ui.theme.DangerRed
import com.example.ui.theme.LocalDarkTheme
import com.example.ui.theme.OnlineGreen
import com.example.ui.viewmodel.ChatThreadUi
import com.example.ui.viewmodel.VoiceRecordingState
import com.example.util.MessageFormatter
import com.example.util.TimeFormatters
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatRoomScreen(
    chatUi: ChatThreadUi,
    activeMember: FamilyMemberEntity,
    allMembers: List<FamilyMemberEntity>,
    messages: List<ChatMessageEntity>,
    voiceRecording: VoiceRecordingState,
    playingVoiceMessageId: String?,
    playingVoiceProgress: Float,
    snackbarMessage: String?,
    onClearSnackbar: () -> Unit,
    onBack: () -> Unit,
    onSendText: (text: String, replyToId: String?) -> Unit,
    onSendImage: (uri: Uri, caption: String, replyToId: String?) -> Unit,
    onSendLocation: (name: String, address: String, lat: Double?, lng: Double?, replyToId: String?) -> Unit,
    onSendPoll: (question: String, options: List<String>) -> Unit,
    onVotePoll: (messageId: String, optionId: String) -> Unit,
    onEditMessage: (messageId: String, newText: String) -> Unit,
    onToggleReaction: (messageId: String, emoji: String) -> Unit,
    onTogglePin: (messageId: String) -> Unit,
    onDeleteForMe: (List<String>) -> Unit,
    onDeleteForEveryone: (List<String>) -> Unit,
    onStartVoiceRecording: () -> Unit,
    onCancelVoiceRecording: () -> Unit,
    onSendVoiceRecording: (replyToId: String?) -> Unit,
    onToggleVoicePlay: (messageId: String) -> Unit,
    onStartCall: (CallType) -> Unit,
    onTypingChange: (isTyping: Boolean) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val membersMap = remember(allMembers) { allMembers.associateBy { it.id } }
    val visibleMessages = remember(messages, activeMember.id) {
        messages.filter { !JsonHelpers.decodeStringList(it.hiddenForMemberIdsJson).contains(activeMember.id) }
    }
    val messageMap = remember(visibleMessages) { visibleMessages.associateBy { it.id } }

    var inputText by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<ChatMessageEntity?>(null) }
    var editingMessage by remember { mutableStateOf<ChatMessageEntity?>(null) }
    var actionTargetMessage by remember { mutableStateOf<ChatMessageEntity?>(null) }

    var isSearchOpen by remember { mutableStateOf(false) }
    var inChatQuery by remember { mutableStateOf("") }
    var currentMatchIndex by remember { mutableIntStateOf(0) }

    val selectedIds = remember { mutableStateListOf<String>() }
    val isMultiSelectMode = selectedIds.isNotEmpty()
    var showBatchDeleteDialog by remember { mutableStateOf(false) }

    var showEmojiDrawer by remember { mutableStateOf(false) }
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var showLocationDialog by remember { mutableStateOf(false) }
    var showPollDialog by remember { mutableStateOf(false) }
    var pendingImageUri by remember { mutableStateOf<Uri?>(null) }
    var viewerImageUri by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> if (uri != null) pendingImageUri = uri }
    )
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) onStartVoiceRecording()
            else scope.launch { snackbarHostState.showSnackbar("Permiso de micrófono necesario para grabar audio") }
        }
    )
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { perms ->
            val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (granted) showLocationDialog = true
            else scope.launch { snackbarHostState.showSnackbar("Permiso de ubicación denegado") }
        }
    )

    val matchedIndices = remember(visibleMessages, inChatQuery) {
        if (inChatQuery.isBlank()) emptyList()
        else visibleMessages.mapIndexedNotNull { idx, msg ->
            if (msg.text.contains(inChatQuery, ignoreCase = true) ||
                msg.pollQuestion?.contains(inChatQuery, ignoreCase = true) == true
            ) idx else null
        }
    }

    LaunchedEffect(snackbarMessage) {
        if (snackbarMessage != null) {
            snackbarHostState.showSnackbar(snackbarMessage)
            onClearSnackbar()
        }
    }

    LaunchedEffect(visibleMessages.size) {
        if (visibleMessages.isNotEmpty() && !isSearchOpen) {
            listState.animateScrollToItem(visibleMessages.lastIndex)
        }
    }

    BackHandler(enabled = isMultiSelectMode || isSearchOpen || showEmojiDrawer) {
        when {
            isMultiSelectMode -> selectedIds.clear()
            isSearchOpen -> {
                isSearchOpen = false
                inChatQuery = ""
            }
            showEmojiDrawer -> showEmojiDrawer = false
        }
    }

    val pinnedMsg = chatUi.chat.pinnedMessageId?.let { messageMap[it] }
    val wallpaperColor = if (LocalDarkTheme.current) ChatWallpaperDark else ChatWallpaperLight

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = wallpaperColor,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                when {
                    isMultiSelectMode -> {
                        TopAppBar(
                            title = { Text("${selectedIds.size} seleccionado(s)") },
                            navigationIcon = {
                                IconButton(onClick = { selectedIds.clear() }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Cancelar selección")
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = {
                                        val combined = selectedIds.mapNotNull { messageMap[it]?.text }
                                            .filter { it.isNotBlank() }
                                            .joinToString("\n")
                                        if (combined.isNotBlank()) {
                                            clipboard.setText(AnnotatedString(combined))
                                            scope.launch { snackbarHostState.showSnackbar("Mensajes copiados") }
                                        }
                                        selectedIds.clear()
                                    },
                                    modifier = Modifier.testTag("batch_copy_button")
                                ) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copiar seleccionados")
                                }
                                IconButton(
                                    onClick = { showBatchDeleteDialog = true },
                                    modifier = Modifier.testTag("batch_delete_button")
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Eliminar seleccionados")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface,
                                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                                actionIconContentColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                    isSearchOpen -> {
                        TopAppBar(
                            title = {
                                OutlinedTextField(
                                    value = inChatQuery,
                                    onValueChange = {
                                        inChatQuery = it
                                        currentMatchIndex = 0
                                    },
                                    placeholder = {
                                        Text(
                                            "Buscar en el chat...",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        focusedBorderColor = Color.Transparent,
                                        unfocusedBorderColor = Color.Transparent,
                                        cursorColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.fillMaxWidth().testTag("in_chat_search_input")
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = { isSearchOpen = false; inChatQuery = "" }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cerrar búsqueda")
                                }
                            },
                            actions = {
                                if (matchedIndices.isNotEmpty()) {
                                    Text(
                                        text = "${currentMatchIndex + 1}/${matchedIndices.size}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    IconButton(onClick = {
                                        currentMatchIndex = (currentMatchIndex - 1 + matchedIndices.size) % matchedIndices.size
                                        scope.launch { listState.animateScrollToItem(matchedIndices[currentMatchIndex]) }
                                    }) {
                                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Coincidencia anterior")
                                    }
                                    IconButton(onClick = {
                                        currentMatchIndex = (currentMatchIndex + 1) % matchedIndices.size
                                        scope.launch { listState.animateScrollToItem(matchedIndices[currentMatchIndex]) }
                                    }) {
                                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Siguiente coincidencia")
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                                actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                    else -> {
                        TopAppBar(
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    MemberAvatar(
                                        emoji = chatUi.displayEmoji,
                                        colorHex = chatUi.displayColorHex,
                                        size = 40.dp,
                                        isOnline = chatUi.isOnline,
                                        showOnlineDot = true
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = chatUi.displayTitle,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val isLiveStatus = chatUi.isOnline ||
                                            chatUi.subtitleStatus.startsWith("Escribiendo") ||
                                            chatUi.subtitleStatus.startsWith("Grabando")
                                        Text(
                                            text = chatUi.subtitleStatus,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isLiveStatus) OnlineGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = onBack, modifier = Modifier.testTag("chat_back_button")) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = { isSearchOpen = true },
                                    modifier = Modifier.testTag("chat_search_button")
                                ) {
                                    Icon(Icons.Filled.Search, contentDescription = "Buscar mensajes")
                                }
                                IconButton(
                                    onClick = { onStartCall(CallType.VOICE) },
                                    modifier = Modifier.testTag("chat_voice_call_button")
                                ) {
                                    Icon(Icons.Filled.Call, contentDescription = "Llamada de voz")
                                }
                                IconButton(
                                    onClick = { onStartCall(CallType.VIDEO) },
                                    modifier = Modifier.testTag("chat_video_call_button")
                                ) {
                                    Icon(Icons.Filled.Videocam, contentDescription = "Videollamada")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface,
                                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                                actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
                androidx.compose.material3.HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(modifier = Modifier.fillMaxSize().widthIn(max = 720.dp)) {
                // Pinned Message Banner
                if (pinnedMsg != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        tonalElevation = 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val idx = visibleMessages.indexOfFirst { it.id == pinnedMsg.id }
                                if (idx >= 0) scope.launch { listState.animateScrollToItem(idx) }
                            }
                            .testTag("pinned_message_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.PushPin,
                                contentDescription = "Mensaje fijado",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Mensaje fijado",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = MessageFormatter.stripMarkdownForPreview(pinnedMsg.text)
                                        .ifBlank { pinnedMsg.pollQuestion ?: "Multimedia fijado" },
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { onTogglePin(pinnedMsg.id) }) {
                                Icon(Icons.Filled.Close, contentDescription = "Desfijar", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // Messages List
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    items(visibleMessages, key = { it.id }) { msg ->
                        val sender = membersMap[msg.senderId]
                        val repliedMsg = msg.replyToMessageId?.let { messageMap[it] }
                        val repliedSenderName = repliedMsg?.senderId?.let { membersMap[it]?.name }
                        ChatMessageBubble(
                            message = msg,
                            isMine = msg.senderId == activeMember.id,
                            isGroupChat = chatUi.chat.type == ChatType.GROUP,
                            sender = sender,
                            repliedMessage = repliedMsg,
                            repliedSenderName = repliedSenderName,
                            activeMemberId = activeMember.id,
                            isSelected = selectedIds.contains(msg.id),
                            isMultiSelectMode = isMultiSelectMode,
                            isPlayingVoice = playingVoiceMessageId == msg.id,
                            voicePlaybackProgress = if (playingVoiceMessageId == msg.id) playingVoiceProgress else 0f,
                            searchQuery = inChatQuery,
                            onToggleSelect = {
                                if (selectedIds.contains(msg.id)) selectedIds.remove(msg.id)
                                else selectedIds.add(msg.id)
                            },
                            onLongPress = { actionTargetMessage = msg },
                            onReplyClick = { replyId ->
                                val idx = visibleMessages.indexOfFirst { it.id == replyId }
                                if (idx >= 0) scope.launch { listState.animateScrollToItem(idx) }
                            },
                            onImageClick = { uri -> viewerImageUri = uri },
                            onToggleVoicePlay = { onToggleVoicePlay(msg.id) },
                            onVotePoll = { optId -> onVotePoll(msg.id, optId) },
                            onToggleReaction = { emoji -> onToggleReaction(msg.id, emoji) }
                        )
                    }
                }

                // Composer Area
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                ) {
                    Column {
                        // Reply / Edit Banner
                        val bannerTarget = editingMessage ?: replyingTo
                        if (bannerTarget != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (editingMessage != null) "Editando mensaje"
                                        else "Respondiendo a ${membersMap[bannerTarget.senderId]?.name ?: "Mensaje"}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = MessageFormatter.stripMarkdownForPreview(bannerTarget.text)
                                            .ifBlank { bannerTarget.pollQuestion ?: "Adjunto" },
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = {
                                    if (editingMessage != null) {
                                        editingMessage = null
                                        inputText = ""
                                    }
                                    replyingTo = null
                                }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Cancelar")
                                }
                            }
                        }

                        // Quick Format Toolbar (*negrita*, _cursiva_, ~tachado~)
                        if (!voiceRecording.isRecording) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Formato:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                FormatChipButton(
                                    icon = Icons.Filled.FormatBold,
                                    desc = "Negrita",
                                    tag = "format_bold_button"
                                ) { inputText = wrapOrAppendToken(inputText, "*") }
                                FormatChipButton(
                                    icon = Icons.Filled.FormatItalic,
                                    desc = "Cursiva",
                                    tag = "format_italic_button"
                                ) { inputText = wrapOrAppendToken(inputText, "_") }
                                FormatChipButton(
                                    icon = Icons.Filled.FormatStrikethrough,
                                    desc = "Tachado",
                                    tag = "format_strike_button"
                                ) { inputText = wrapOrAppendToken(inputText, "~") }
                            }
                        }

                        if (voiceRecording.isRecording) {
                            // Voice Recording Bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(DangerRed)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = TimeFormatters.formatDuration(voiceRecording.elapsedSec),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = DangerRed
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Row(
                                    modifier = Modifier.weight(1f).height(28.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    voiceRecording.amplitudes.forEach { amp ->
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight(amp.coerceIn(0.2f, 1f))
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = onCancelVoiceRecording,
                                    modifier = Modifier.testTag("cancel_voice_button")
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Cancelar audio", tint = DangerRed)
                                }
                                IconButton(
                                    onClick = {
                                        onSendVoiceRecording(replyingTo?.id)
                                        replyingTo = null
                                    },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                        .testTag("send_voice_button")
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Enviar nota de voz",
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        } else {
                            // Standard Composer Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                IconButton(
                                    onClick = { showEmojiDrawer = !showEmojiDrawer },
                                    modifier = Modifier.testTag("toggle_emoji_button")
                                ) {
                                    Icon(
                                        Icons.Filled.EmojiEmotions,
                                        contentDescription = "Emojis",
                                        tint = if (showEmojiDrawer) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedTextField(
                                    value = inputText,
                                    onValueChange = {
                                        inputText = it
                                        onTypingChange(it.isNotBlank())
                                    },
                                    placeholder = { Text("Escribe un mensaje...") },
                                    maxLines = 4,
                                    shape = RoundedCornerShape(24.dp),
                                    modifier = Modifier.weight(1f).testTag("chat_message_input")
                                )
                                Box {
                                    IconButton(
                                        onClick = { showAttachmentMenu = true },
                                        modifier = Modifier.testTag("attach_button")
                                    ) {
                                        Icon(
                                            Icons.Filled.AttachFile,
                                            contentDescription = "Adjuntar multimedia",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showAttachmentMenu,
                                        onDismissRequest = { showAttachmentMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Foto de galería") },
                                            leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null) },
                                            onClick = {
                                                showAttachmentMenu = false
                                                photoPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Enviar ubicación") },
                                            leadingIcon = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
                                            onClick = {
                                                showAttachmentMenu = false
                                                val hasFine = ContextCompat.checkSelfPermission(
                                                    context, Manifest.permission.ACCESS_FINE_LOCATION
                                                ) == PackageManager.PERMISSION_GRANTED
                                                val hasCoarse = ContextCompat.checkSelfPermission(
                                                    context, Manifest.permission.ACCESS_COARSE_LOCATION
                                                ) == PackageManager.PERMISSION_GRANTED
                                                if (hasFine || hasCoarse) {
                                                    showLocationDialog = true
                                                } else {
                                                    locationPermissionLauncher.launch(
                                                        arrayOf(
                                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                                        )
                                                    )
                                                }
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Crear encuesta") },
                                            leadingIcon = { Icon(Icons.Filled.Poll, contentDescription = null) },
                                            onClick = {
                                                showAttachmentMenu = false
                                                showPollDialog = true
                                            }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                if (inputText.isNotBlank()) {
                                    IconButton(
                                        onClick = {
                                            val editing = editingMessage
                                            if (editing != null) {
                                                onEditMessage(editing.id, inputText)
                                                editingMessage = null
                                            } else {
                                                onSendText(inputText, replyingTo?.id)
                                                replyingTo = null
                                            }
                                            inputText = ""
                                            showEmojiDrawer = false
                                        },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                            .testTag("send_message_button")
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "Enviar mensaje",
                                            tint = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                } else {
                                    IconButton(
                                        onClick = {
                                            val granted = ContextCompat.checkSelfPermission(
                                                context, Manifest.permission.RECORD_AUDIO
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (granted) onStartVoiceRecording()
                                            else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                            .testTag("record_voice_button")
                                    ) {
                                        Icon(
                                            Icons.Filled.Mic,
                                            contentDescription = "Grabar nota de voz",
                                            tint = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                            }
                        }

                        AnimatedVisibility(visible = showEmojiDrawer) {
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PickerEmojis.forEach { emoji ->
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .clickable { inputText += emoji },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(emoji, fontSize = 22.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    actionTargetMessage?.let { target ->
        MessageActionsBottomSheet(
            message = target,
            isMine = target.senderId == activeMember.id,
            isAdmin = activeMember.isAdmin,
            isPinned = chatUi.chat.pinnedMessageId == target.id,
            onDismiss = { actionTargetMessage = null },
            onReact = { emoji -> onToggleReaction(target.id, emoji) },
            onReply = {
                editingMessage = null
                replyingTo = target
            },
            onCopy = {
                clipboard.setText(AnnotatedString(target.text))
                scope.launch { snackbarHostState.showSnackbar("Texto copiado") }
            },
            onEdit = {
                replyingTo = null
                editingMessage = target
                inputText = target.text
            },
            onTogglePin = { onTogglePin(target.id) },
            onStartMultiSelect = {
                if (!selectedIds.contains(target.id)) selectedIds.add(target.id)
            },
            onDeleteForMe = { onDeleteForMe(listOf(target.id)) },
            onDeleteForEveryone = { onDeleteForEveryone(listOf(target.id)) }
        )
    }

    if (showBatchDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            title = { Text("Eliminar ${selectedIds.size} mensaje(s)") },
            text = { Text("¿Cómo deseas eliminar los mensajes seleccionados?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteForEveryone(selectedIds.toList())
                        selectedIds.clear()
                        showBatchDeleteDialog = false
                    }
                ) { Text("Eliminar para todos", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onDeleteForMe(selectedIds.toList())
                        selectedIds.clear()
                        showBatchDeleteDialog = false
                    }
                ) { Text("Eliminar para mí") }
            }
        )
    }

    pendingImageUri?.let { uri ->
        ImagePreviewSendDialog(
            imageUri = uri,
            onDismiss = { pendingImageUri = null },
            onSend = { caption ->
                onSendImage(uri, caption, replyingTo?.id)
                replyingTo = null
                pendingImageUri = null
            }
        )
    }

    viewerImageUri?.let { uri ->
        FullScreenImageDialog(imageUri = uri, onDismiss = { viewerImageUri = null })
    }

    if (showLocationDialog) {
        LocationPickerDialog(
            onDismiss = { showLocationDialog = false },
            onSendLocation = { name, address, lat, lng ->
                onSendLocation(name, address, lat, lng, replyingTo?.id)
                replyingTo = null
                showLocationDialog = false
            }
        )
    }

    if (showPollDialog) {
        PollCreatorDialog(
            onDismiss = { showPollDialog = false },
            onCreatePoll = { q, opts ->
                onSendPoll(q, opts)
                showPollDialog = false
            }
        )
    }
}

@Composable
private fun FormatChipButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = desc,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun wrapOrAppendToken(current: String, token: String): String {
    if (current.isBlank()) return "${token}texto${token}"
    val trimmed = current.trimEnd()
    val lastSpace = trimmed.lastIndexOf(' ')
    return if (lastSpace >= 0) {
        val prefix = trimmed.substring(0, lastSpace + 1)
        val word = trimmed.substring(lastSpace + 1)
        if (word.startsWith(token) && word.endsWith(token) && word.length > 2) {
            current
        } else {
            "$prefix$token$word$token "
        }
    } else {
        if (trimmed.startsWith(token) && trimmed.endsWith(token) && trimmed.length > 2) {
            current
        } else {
            "$token$trimmed$token "
        }
    }
}
