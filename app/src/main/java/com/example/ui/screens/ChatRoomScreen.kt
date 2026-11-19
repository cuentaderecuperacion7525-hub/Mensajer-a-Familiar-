package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.MenuItemCompat
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
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.example.data.model.CallRecordEntity
import com.example.data.model.CallType
import com.example.data.model.ChatMessageEntity
import com.example.data.model.ChatType
import com.example.data.model.FamilyMemberEntity
import com.example.data.model.JsonHelpers
import com.example.data.model.MessageType
import com.example.data.model.PresenceStatus
import com.example.ui.components.ChatMessageBubble
import com.example.ui.components.ChatWallpaperPalette
import com.example.ui.components.ChatWallpaperPickerDialog
import com.example.ui.components.ForwardMessageDialog
import com.example.ui.components.FullScreenImageDialog
import com.example.ui.components.FullScreenVideoDialog
import com.example.ui.components.ImagePreviewSendDialog
import com.example.ui.components.LocationPickerDialog
import com.example.ui.components.MemberAvatar
import com.example.ui.components.MessageActionsBottomSheet
import com.example.ui.components.MessageInfoDialog
import com.example.ui.components.PickerEmojis
import com.example.ui.components.PollCreatorDialog
import com.example.ui.components.VideoPreviewSendDialog
import com.example.ui.theme.DangerRed
import com.example.ui.theme.LocalDarkTheme
import com.example.ui.theme.OnlineGreen
import com.example.ui.viewmodel.ChatThreadUi
import com.example.ui.viewmodel.VoiceRecordingState
import com.example.util.CameraCaptureHelper
import com.example.util.MessageFormatter
import com.example.util.TimeFormatters
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatRoomScreen(
    chatUi: ChatThreadUi,
    activeMember: FamilyMemberEntity,
    allMembers: List<FamilyMemberEntity>,
    allChatPreviews: List<ChatThreadUi> = emptyList(),
    calls: List<CallRecordEntity> = emptyList(),
    selectedWallpaperKey: String = "default",
    selectedNotificationToneUri: String = "",
    messages: List<ChatMessageEntity>,
    voiceRecording: VoiceRecordingState,
    playingVoiceMessageId: String?,
    playingVoiceProgress: Float,
    snackbarMessage: String?,
    onClearSnackbar: () -> Unit,
    onBack: () -> Unit,
    onSendText: (text: String, replyToId: String?) -> Unit,
    onSendImage: (uri: Uri, caption: String, replyToId: String?) -> Unit,
    onSendLocation: (name: String, address: String, lat: Double?, lng: Double?, replyToId: String?, liveDurationMinutes: Int) -> Unit,
    onSendDocument: (uri: Uri, replyToId: String?) -> Unit = { _, _ -> },
    onSendVideo: (uri: Uri, caption: String, replyToId: String?) -> Unit = { _, _, _ -> },
    onOpenDocument: (ChatMessageEntity) -> Unit = {},
    onOpenVideo: (ChatMessageEntity, (File) -> Unit) -> Unit = { _, _ -> },
    voicePlaybackSpeed: Float = 1.0f,
    onCycleVoiceSpeed: () -> Unit = {},
    mediaTransferProgress: Map<String, Float> = emptyMap(),
    activeUploadLabel: String? = null,
    activeUploadProgress: Float? = null,
    onStopLiveLocation: (messageId: String) -> Unit = {},
    onSendPoll: (question: String, options: List<String>) -> Unit,
    onVotePoll: (messageId: String, optionId: String) -> Unit,
    onEditMessage: (messageId: String, newText: String) -> Unit,
    onForwardMessage: (message: ChatMessageEntity, targetChatId: String) -> Unit = { _, _ -> },
    onSelectWallpaper: (String) -> Unit = {},
    onSelectNotificationTone: (String) -> Unit = {},
    onToggleReaction: (messageId: String, emoji: String) -> Unit,
    onTogglePin: (messageId: String) -> Unit,
    onTogglePinChat: () -> Unit = {},
    onToggleMuteChat: () -> Unit = {},
    onDeleteForMe: (List<String>) -> Unit,
    onClearChat: (() -> Unit)? = null,
    onDeleteForEveryone: (List<String>) -> Unit,
    onStartVoiceRecording: () -> Unit,
    onCancelVoiceRecording: () -> Unit,
    onSendVoiceRecording: (replyToId: String?) -> Unit,
    onToggleVoicePlay: (messageId: String) -> Unit,
    onStartCall: (CallType) -> Unit,
    onTypingChange: (isTyping: Boolean) -> Unit,
    onToggleTheme: () -> Unit,
    onGoToHomePanel: () -> Unit,
    onSwitchProfile: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val isImeVisible = WindowInsets.isImeVisible

    val membersMap = remember(allMembers) { allMembers.associateBy { it.id } }
    val visibleMessages = remember(messages, activeMember.id) {
        messages.filter { !JsonHelpers.decodeStringList(it.hiddenForMemberIdsJson).contains(activeMember.id) }
    }
    val messageMap = remember(visibleMessages) { visibleMessages.associateBy { it.id } }

    var inputTextValue by remember { mutableStateOf(TextFieldValue("")) }
    var replyingTo by remember { mutableStateOf<ChatMessageEntity?>(null) }
    var editingMessage by remember { mutableStateOf<ChatMessageEntity?>(null) }
    var actionTargetMessage by remember { mutableStateOf<ChatMessageEntity?>(null) }
    var forwardTargetMessage by remember { mutableStateOf<ChatMessageEntity?>(null) }
    var infoTargetMessage by remember { mutableStateOf<ChatMessageEntity?>(null) }
    var showChatInfoDialog by remember { mutableStateOf(false) }
    var showWallpaperDialog by remember { mutableStateOf(false) }
    var showClearChatConfirmDialog by remember { mutableStateOf(false) }
    var showBlockContactConfirmDialog by remember { mutableStateOf(false) }
    var isContactBlocked by rememberSaveable(chatUi.chat.id) { mutableStateOf(false) }

    var isSearchOpen by remember { mutableStateOf(false) }
    var inChatQuery by remember { mutableStateOf("") }
    var currentMatchIndex by remember { mutableIntStateOf(0) }

    val selectedIds = remember { mutableStateListOf<String>() }
    val isMultiSelectMode = selectedIds.isNotEmpty()
    var showBatchDeleteDialog by remember { mutableStateOf(false) }

    var showEmojiDrawer by remember { mutableStateOf(false) }
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var showHeaderMenu by remember { mutableStateOf(false) }
    var showLocationDialog by remember { mutableStateOf(false) }
    var showPollDialog by remember { mutableStateOf(false) }
    var pendingImageUri by remember { mutableStateOf<Uri?>(null) }
    var pendingVideoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var viewerImageUri by remember { mutableStateOf<String?>(null) }
    var viewerVideoFile by remember { mutableStateOf<File?>(null) }
    var viewerVideoCaption by remember { mutableStateOf("") }
    var showPrivacyInfoSheet by remember { mutableStateOf(false) }

    // Capture initial unread incoming messages snapshot upon opening this chat (before they are marked read)
    var initialUnreadMessageId by remember(chatUi.chat.id) { mutableStateOf<String?>(null) }
    var initialUnreadCount by remember(chatUi.chat.id) { mutableIntStateOf(0) }
    var unreadSnapshotCaptured by remember(chatUi.chat.id) { mutableStateOf(false) }

    LaunchedEffect(chatUi.chat.id, visibleMessages.isNotEmpty()) {
        if (!unreadSnapshotCaptured && visibleMessages.isNotEmpty()) {
            unreadSnapshotCaptured = true
            val unreadList = visibleMessages.filter { msg ->
                msg.senderId != activeMember.id &&
                    msg.type != MessageType.DELETED &&
                    !JsonHelpers.decodeStringList(msg.readByMemberIdsJson).contains(activeMember.id)
            }
            if (unreadList.isNotEmpty()) {
                initialUnreadMessageId = unreadList.first().id
                initialUnreadCount = unreadList.size
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> if (uri != null) pendingImageUri = uri }
    )
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> if (uri != null) pendingVideoUri = uri }
    )
    val cameraCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success && pendingCameraUri != null) {
                pendingImageUri = pendingCameraUri
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
                    cameraCaptureLauncher.launch(uri)
                }
            } else {
                scope.launch { snackbarHostState.showSnackbar("Permiso de cámara necesario para tomar foto") }
            }
        }
    )
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                onSendDocument(uri, replyingTo?.id)
                replyingTo = null
            }
        }
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

    val isNearBottom by remember(visibleMessages.size) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            visibleMessages.isEmpty() || lastVisible >= visibleMessages.lastIndex - 1
        }
    }
    var unseenWhileScrolledUp by remember { mutableIntStateOf(0) }
    var lastObservedMsgCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(snackbarMessage) {
        if (snackbarMessage != null) {
            snackbarHostState.showSnackbar(snackbarMessage)
            onClearSnackbar()
        }
    }

    LaunchedEffect(visibleMessages.size) {
        if (visibleMessages.isNotEmpty() && !isSearchOpen) {
            if (lastObservedMsgCount == 0) {
                val unreadTargetId = initialUnreadMessageId
                val unreadIdx = if (unreadTargetId != null) {
                    visibleMessages.indexOfFirst { it.id == unreadTargetId }
                } else -1
                val targetIndex = if (unreadIdx >= 0) unreadIdx else visibleMessages.lastIndex
                listState.scrollToItem(targetIndex)
                unseenWhileScrolledUp = 0
            } else if (isNearBottom) {
                listState.animateScrollToItem(visibleMessages.lastIndex)
                unseenWhileScrolledUp = 0
            } else if (visibleMessages.size > lastObservedMsgCount) {
                unseenWhileScrolledUp += (visibleMessages.size - lastObservedMsgCount)
            }
        }
        lastObservedMsgCount = visibleMessages.size
    }

    LaunchedEffect(isNearBottom) {
        if (isNearBottom) {
            unseenWhileScrolledUp = 0
        }
    }

    LaunchedEffect(isImeVisible) {
        if (isImeVisible) {
            showEmojiDrawer = false
            if (visibleMessages.isNotEmpty() && !isSearchOpen) {
                listState.animateScrollToItem(visibleMessages.lastIndex)
            }
        }
    }

    BackHandler {
        when {
            isImeVisible -> {
                keyboardController?.hide()
                focusManager.clearFocus()
            }
            showChatInfoDialog -> showChatInfoDialog = false
            showPrivacyInfoSheet -> showPrivacyInfoSheet = false
            showHeaderMenu -> showHeaderMenu = false
            showAttachmentMenu -> showAttachmentMenu = false
            showEmojiDrawer -> showEmojiDrawer = false
            isSearchOpen -> {
                isSearchOpen = false
                inChatQuery = ""
            }
            isMultiSelectMode -> selectedIds.clear()
            else -> onBack()
        }
    }

    val pinnedMsg = chatUi.chat.pinnedMessageId?.let { messageMap[it] }
    val isDark = LocalDarkTheme.current
    val wallpaperColor = remember(selectedWallpaperKey, isDark) {
        ChatWallpaperPalette.resolveColor(selectedWallpaperKey, isDark)
    }

    if (showChatInfoDialog) {
        ContactInfoScreen(
            chatUi = chatUi,
            activeMember = activeMember,
            allMembers = allMembers,
            visibleMessages = visibleMessages,
            calls = calls,
            selectedWallpaperKey = selectedWallpaperKey,
            selectedNotificationToneUri = selectedNotificationToneUri,
            isContactBlocked = isContactBlocked,
            onSelectWallpaper = onSelectWallpaper,
            onSelectNotificationTone = onSelectNotificationTone,
            onTogglePinChat = onTogglePinChat,
            onToggleMuteChat = onToggleMuteChat,
            onStartCall = onStartCall,
            onSearchInChat = {
                showChatInfoDialog = false
                isSearchOpen = true
            },
            onOpenImage = { uri -> viewerImageUri = uri },
            onOpenDocument = onOpenDocument,
            onRequestClearChat = { showClearChatConfirmDialog = true },
            onRequestToggleBlockContact = { showBlockContactConfirmDialog = true },
            onBack = { showChatInfoDialog = false }
        )
    } else {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
        ),
        containerColor = wallpaperColor,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.windowInsetsPadding(
                    WindowInsets.navigationBars.union(WindowInsets.ime).only(WindowInsetsSides.Bottom)
                )
            )
        },
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            keyboardController?.hide()
                                            focusManager.clearFocus()
                                            showChatInfoDialog = true
                                        }
                                        .padding(end = 6.dp)
                                        .testTag("chat_header_info_button")
                                ) {
                                    MemberAvatar(
                                        emoji = chatUi.displayEmoji,
                                        colorHex = chatUi.displayColorHex,
                                        photoData = chatUi.displayPhotoData,
                                        size = 42.dp,
                                        isOnline = chatUi.isOnline,
                                        showOnlineDot = false,
                                        nameForInitials = chatUi.displayTitle,
                                        isGroupAvatar = chatUi.chat.type == ChatType.GROUP
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = chatUi.displayTitle,
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (chatUi.isMuted) {
                                                Spacer(modifier = Modifier.width(5.dp))
                                                Icon(
                                                    imageVector = Icons.Filled.NotificationsOff,
                                                    contentDescription = "Notificaciones silenciadas",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                            if (chatUi.isPinned) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Filled.PushPin,
                                                    contentDescription = "Chat fijado",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                        val isLiveStatus = chatUi.isOnline ||
                                            chatUi.subtitleStatus.contains("escribiendo", ignoreCase = true) ||
                                            chatUi.subtitleStatus.contains("grabando", ignoreCase = true)
                                        val cleanHeaderSubtitle = when {
                                            isContactBlocked -> "Contacto bloqueado"
                                            chatUi.subtitleStatus.contains("escribiendo", ignoreCase = true) ||
                                                chatUi.subtitleStatus.contains("grabando", ignoreCase = true) -> chatUi.subtitleStatus
                                            chatUi.isOnline -> "En línea"
                                            else -> chatUi.subtitleStatus
                                        }
                                        Text(
                                            text = cleanHeaderSubtitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isContactBlocked) DangerRed
                                            else if (isLiveStatus) OnlineGreen
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            },
                            navigationIcon = {
                                IconButton(
                                    onClick = {
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                        onBack()
                                    },
                                    modifier = Modifier.testTag("chat_back_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = { onStartCall(CallType.VOICE) },
                                    modifier = Modifier.testTag("chat_header_voice_call_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Call,
                                        contentDescription = "Llamada de voz",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                IconButton(
                                    onClick = { onStartCall(CallType.VIDEO) },
                                    modifier = Modifier.testTag("chat_header_video_call_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Videocam,
                                        contentDescription = "Videollamada",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                IconButton(
                                    onClick = { showHeaderMenu = !showHeaderMenu },
                                    modifier = Modifier.testTag("chat_more_menu_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.MoreVert,
                                        contentDescription = "Opciones del chat",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface,
                                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                                actionIconContentColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
                androidx.compose.material3.HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
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

                // Messages List + Empty Chat Greeting State + Floating Scroll-to-Bottom Button
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (visibleMessages.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp)
                                .testTag("empty_chat_greeting_state"),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFFE4EDFC),
                                modifier = Modifier
                                    .widthIn(max = 300.dp)
                                    .testTag("empty_chat_privacy_notice")
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Lock,
                                        contentDescription = "Chat privado",
                                        tint = Color(0xFF1A5FD4),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Los mensajes y las llamadas de este chat son privados. Solo las personas de tu familia en este chat pueden acceder a ellos.",
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp,
                                        color = Color(0xFF1B1F2A),
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Más información",
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF1A5FD4),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { showPrivacyInfoSheet = true }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                            .testTag("empty_chat_privacy_learn_more")
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            items(visibleMessages, key = { it.id }) { msg ->
                                if (initialUnreadMessageId == msg.id && initialUnreadCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shadowElevation = 2.dp,
                                            modifier = Modifier.testTag("unread_messages_divider")
                                        ) {
                                            Text(
                                                text = if (initialUnreadCount == 1) "1 mensaje sin leer"
                                                else "$initialUnreadCount mensajes sin leer",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                                            )
                                        }
                                    }
                                }
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
                                    onToggleReaction = { emoji -> onToggleReaction(msg.id, emoji) },
                                    onSwipeReply = {
                                        editingMessage = null
                                        replyingTo = msg
                                    },
                                    onStopLiveLocation = { onStopLiveLocation(msg.id) },
                                    voicePlaybackSpeed = voicePlaybackSpeed,
                                    onCycleVoiceSpeed = onCycleVoiceSpeed,
                                    downloadProgress = mediaTransferProgress[msg.id],
                                    onVideoClick = { videoMsg ->
                                        onOpenVideo(videoMsg) { readyFile ->
                                            viewerVideoCaption = videoMsg.text
                                            viewerVideoFile = readyFile
                                        }
                                    },
                                    onDocumentClick = onOpenDocument
                                )
                            }
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = !isNearBottom && visibleMessages.isNotEmpty(),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 14.dp, bottom = 10.dp)
                    ) {
                        Box {
                            SmallFloatingActionButton(
                                onClick = {
                                    scope.launch {
                                        listState.animateScrollToItem(visibleMessages.lastIndex)
                                    }
                                    unseenWhileScrolledUp = 0
                                },
                                shape = CircleShape,
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("scroll_to_bottom_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowDown,
                                    contentDescription = "Bajar al final del chat"
                                )
                            }
                            if (unseenWhileScrolledUp > 0) {
                                Badge(
                                    modifier = Modifier.align(Alignment.TopEnd)
                                ) {
                                    Text(unseenWhileScrolledUp.toString())
                                }
                            }
                        }
                    }
                }

                // Composer Area (Floating Pill style adapted to app Material 3 colors)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(
                            WindowInsets.navigationBars.union(WindowInsets.ime).only(WindowInsetsSides.Bottom)
                        )
                ) {
                    if (activeUploadLabel != null) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 3.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 2.dp)
                                .testTag("upload_progress_banner")
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = activeUploadLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    val pct = ((activeUploadProgress ?: 0f) * 100).toInt().coerceIn(1, 100)
                                    Text(
                                        text = "$pct%",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { (activeUploadProgress ?: 0.05f).coerceIn(0.05f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(4.dp))
                                )
                            }
                        }
                    }

                    val isDarkComposer = LocalDarkTheme.current
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = if (isDarkComposer) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                        else Color(0xFFF2F4F8),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Reply / Edit Banner docked cleanly inside the pill
                            val bannerTarget = editingMessage ?: replyingTo
                            if (bannerTarget != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                        .padding(horizontal = 14.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .height(28.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
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
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            if (editingMessage != null) {
                                                editingMessage = null
                                                inputTextValue = TextFieldValue("")
                                            }
                                            replyingTo = null
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "Cancelar",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                            }

                            if (voiceRecording.isRecording) {
                                // Voice Recording Bar (Hands-Free Locked Mode) inside the floating pill
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(DangerRed)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = TimeFormatters.formatDuration(voiceRecording.elapsedSec),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = DangerRed
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(50.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Lock,
                                                contentDescription = "Manos libres activo",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = "Manos libres",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
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
                                        modifier = Modifier.testTag("send_voice_button")
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.Send,
                                                contentDescription = "Enviar nota de voz",
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Unified Pill Row: [ Emoji | Mensaje | Attach | (Send / Mic) ]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    IconButton(
                                        onClick = {
                                            val nextOpen = !showEmojiDrawer
                                            if (nextOpen) keyboardController?.hide()
                                            showEmojiDrawer = nextOpen
                                        },
                                        modifier = Modifier.testTag("toggle_emoji_button")
                                    ) {
                                        Icon(
                                            imageVector = if (showEmojiDrawer) Icons.Filled.EmojiEmotions else Icons.Outlined.SentimentSatisfied,
                                            contentDescription = "Emojis",
                                            tint = if (showEmojiDrawer) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 2.dp, vertical = 12.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (inputTextValue.text.isEmpty()) {
                                            Text(
                                                text = "Mensaje",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        val formattingTextToolbar = rememberFormattingTextToolbar(
                                            hasNonBlankSelection = {
                                                !inputTextValue.selection.collapsed &&
                                                    inputTextValue.text.substring(
                                                        inputTextValue.selection.min.coerceIn(0, inputTextValue.text.length),
                                                        inputTextValue.selection.max.coerceIn(0, inputTextValue.text.length)
                                                    ).isNotBlank()
                                            },
                                            onApplyBold = {
                                                inputTextValue = applyFormatToSelection(inputTextValue, "*")
                                            },
                                            onApplyItalic = {
                                                inputTextValue = applyFormatToSelection(inputTextValue, "_")
                                            },
                                            onApplyStrike = {
                                                inputTextValue = applyFormatToSelection(inputTextValue, "~")
                                            }
                                        )
                                        CompositionLocalProvider(LocalTextToolbar provides formattingTextToolbar) {
                                            BasicTextField(
                                                value = inputTextValue,
                                                onValueChange = {
                                                    inputTextValue = it
                                                    onTypingChange(it.text.isNotBlank())
                                                },
                                                maxLines = 5,
                                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                                    color = MaterialTheme.colorScheme.onSurface
                                                ),
                                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("chat_message_input")
                                            )
                                        }
                                    }

                                    Box {
                                        IconButton(
                                            onClick = { showAttachmentMenu = true },
                                            modifier = Modifier.testTag("attach_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.AttachFile,
                                                contentDescription = "Adjuntar multimedia",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .rotate(40f)
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = showAttachmentMenu,
                                            onDismissRequest = { showAttachmentMenu = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Tomar foto") },
                                                leadingIcon = { Icon(Icons.Filled.CameraAlt, contentDescription = null) },
                                                onClick = {
                                                    showAttachmentMenu = false
                                                    val hasCam = ContextCompat.checkSelfPermission(
                                                        context, Manifest.permission.CAMERA
                                                    ) == PackageManager.PERMISSION_GRANTED
                                                    if (hasCam) {
                                                        val uri = CameraCaptureHelper.createTempPhotoUri(context)
                                                        if (uri != null) {
                                                            pendingCameraUri = uri
                                                            cameraCaptureLauncher.launch(uri)
                                                        }
                                                    } else {
                                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                                    }
                                                },
                                                modifier = Modifier.testTag("attach_take_photo")
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Foto de galería") },
                                                leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null) },
                                                onClick = {
                                                    showAttachmentMenu = false
                                                    photoPickerLauncher.launch(
                                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                    )
                                                },
                                                modifier = Modifier.testTag("attach_gallery_photo")
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Video de galería (corto)") },
                                                leadingIcon = { Icon(Icons.Filled.Movie, contentDescription = null) },
                                                onClick = {
                                                    showAttachmentMenu = false
                                                    videoPickerLauncher.launch(
                                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                                    )
                                                },
                                                modifier = Modifier.testTag("attach_gallery_video")
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Documento o archivo (hasta 5 MB)") },
                                                leadingIcon = { Icon(Icons.Filled.Description, contentDescription = null) },
                                                onClick = {
                                                    showAttachmentMenu = false
                                                    documentPickerLauncher.launch(arrayOf("*/*"))
                                                },
                                                modifier = Modifier.testTag("attach_document")
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
                                                },
                                                modifier = Modifier.testTag("attach_location")
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Crear encuesta") },
                                                leadingIcon = { Icon(Icons.Filled.Poll, contentDescription = null) },
                                                onClick = {
                                                    showAttachmentMenu = false
                                                    showPollDialog = true
                                                },
                                                modifier = Modifier.testTag("attach_poll")
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            val hasCam = ContextCompat.checkSelfPermission(
                                                context, Manifest.permission.CAMERA
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (hasCam) {
                                                val uri = CameraCaptureHelper.createTempPhotoUri(context)
                                                if (uri != null) {
                                                    pendingCameraUri = uri
                                                    cameraCaptureLauncher.launch(uri)
                                                }
                                            } else {
                                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                            }
                                        },
                                        modifier = Modifier.testTag("direct_camera_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.CameraAlt,
                                            contentDescription = "Tomar foto",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    if (inputTextValue.text.isNotBlank()) {
                                        IconButton(
                                            onClick = {
                                                val rawText = inputTextValue.text
                                                val editing = editingMessage
                                                if (editing != null) {
                                                    onEditMessage(editing.id, rawText)
                                                    editingMessage = null
                                                } else {
                                                    onSendText(rawText, replyingTo?.id)
                                                    replyingTo = null
                                                }
                                                inputTextValue = TextFieldValue("")
                                                showEmojiDrawer = false
                                            },
                                            modifier = Modifier.testTag("send_message_button")
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                                    contentDescription = "Enviar mensaje",
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    } else {
                                        val startHandsFreeMic = {
                                            val granted = ContextCompat.checkSelfPermission(
                                                context, Manifest.permission.RECORD_AUDIO
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (granted) onStartVoiceRecording()
                                            else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                        IconButton(
                                            onClick = startHandsFreeMic,
                                            modifier = Modifier
                                                .pointerInput(Unit) {
                                                    var dragUpAccum = 0f
                                                    detectVerticalDragGestures(
                                                        onDragStart = { dragUpAccum = 0f },
                                                        onVerticalDrag = { _, dragAmount ->
                                                            dragUpAccum += dragAmount
                                                            if (dragUpAccum < -32f) {
                                                                dragUpAccum = 0f
                                                                startHandsFreeMic()
                                                            }
                                                        }
                                                    )
                                                }
                                                .testTag("record_voice_button")
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Mic,
                                                    contentDescription = "Grabar nota de voz (toca o desliza hacia arriba para manos libres)",
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    AnimatedVisibility(visible = showEmojiDrawer) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PickerEmojis.forEach { emoji ->
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                inputTextValue = insertTextAtSelection(inputTextValue, emoji)
                                            },
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

            if (showHeaderMenu) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.25f))
                        .clickable { showHeaderMenu = false }
                ) {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp,
                        shadowElevation = 12.dp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 6.dp, end = 12.dp)
                            .width(268.dp)
                            .clickable(enabled = false) {}
                            .testTag("chat_more_menu")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showHeaderMenu = false
                                        showChatInfoDialog = true
                                    }
                                    .padding(horizontal = 20.dp, vertical = 13.dp)
                                    .testTag("chat_menu_info_gallery"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = if (chatUi.chat.type == ChatType.GROUP) "Info del grupo" else "Info del contacto",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showHeaderMenu = false
                                        showWallpaperDialog = true
                                    }
                                    .padding(horizontal = 20.dp, vertical = 13.dp)
                                    .testTag("chat_menu_wallpaper"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Image,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = "Fondo del chat",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showHeaderMenu = false
                                        isSearchOpen = true
                                    }
                                    .padding(horizontal = 20.dp, vertical = 13.dp)
                                    .testTag("chat_menu_search"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = "Buscar mensajes",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showHeaderMenu = false
                                        onTogglePinChat()
                                    }
                                    .padding(horizontal = 20.dp, vertical = 13.dp)
                                    .testTag("chat_menu_toggle_pin_chat"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.PushPin,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = if (chatUi.isPinned) "Desfijar chat" else "Fijar chat arriba",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showHeaderMenu = false
                                        onToggleMuteChat()
                                    }
                                    .padding(horizontal = 20.dp, vertical = 13.dp)
                                    .testTag("chat_menu_toggle_mute_chat"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (chatUi.isMuted) Icons.Filled.Notifications else Icons.Outlined.NotificationsOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = if (chatUi.isMuted) "Activar notificaciones" else "Silenciar notificaciones",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 6.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showHeaderMenu = false
                                        showClearChatConfirmDialog = true
                                    }
                                    .padding(horizontal = 20.dp, vertical = 13.dp)
                                    .testTag("chat_menu_clear_chat"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = null,
                                    tint = DangerRed,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = "Vaciar chat",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = DangerRed
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showHeaderMenu = false
                                        showBlockContactConfirmDialog = true
                                    }
                                    .padding(horizontal = 20.dp, vertical = 13.dp)
                                    .testTag("chat_menu_block_contact"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Block,
                                    contentDescription = null,
                                    tint = DangerRed,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = if (isContactBlocked) "Desbloquear contacto" else "Bloquear contacto",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = DangerRed
                                )
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
            onForward = { forwardTargetMessage = target },
            onMessageInfo = { infoTargetMessage = target },
            onCopy = {
                clipboard.setText(AnnotatedString(target.text))
                scope.launch { snackbarHostState.showSnackbar("Texto copiado") }
            },
            onEdit = {
                replyingTo = null
                editingMessage = target
                inputTextValue = TextFieldValue(target.text, TextRange(target.text.length))
            },
            onTogglePin = { onTogglePin(target.id) },
            onStartMultiSelect = {
                if (!selectedIds.contains(target.id)) selectedIds.add(target.id)
            },
            onDeleteForMe = { onDeleteForMe(listOf(target.id)) },
            onDeleteForEveryone = { onDeleteForEveryone(listOf(target.id)) }
        )
    }

    forwardTargetMessage?.let { msgToForward ->
        ForwardMessageDialog(
            chatPreviews = allChatPreviews,
            currentChatId = chatUi.chat.id,
            onSelectTargetChat = { targetChatId ->
                onForwardMessage(msgToForward, targetChatId)
                forwardTargetMessage = null
            },
            onDismiss = { forwardTargetMessage = null }
        )
    }

    infoTargetMessage?.let { infoMsg ->
        MessageInfoDialog(
            message = infoMsg,
            allMembers = allMembers,
            onDismiss = { infoTargetMessage = null }
        )
    }

    if (showWallpaperDialog) {
        ChatWallpaperPickerDialog(
            selectedWallpaperKey = selectedWallpaperKey,
            onSelectWallpaper = onSelectWallpaper,
            onDismiss = { showWallpaperDialog = false }
        )
    }

    if (showPrivacyInfoSheet) {
        val privacySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showPrivacyInfoSheet = false },
            sheetState = privacySheetState,
            modifier = Modifier.testTag("empty_chat_privacy_sheet")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFE4EDFC),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = Color(0xFF1A5FD4),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Text(
                    text = "Privacidad en FamiChat",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "FamiChat es un espacio solo para tu familia. Los mensajes y las llamadas están protegidos mientras viajan por internet y solo pueden verlos las personas que forman parte de este chat.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = { showPrivacyInfoSheet = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("empty_chat_privacy_sheet_close")
                ) {
                    Text("Entendido")
                }
            }
        }
    }

    if (showClearChatConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearChatConfirmDialog = false },
            title = { Text("Vaciar chat") },
            text = { Text("¿Deseas vaciar todos los mensajes de este chat para ti?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearChatConfirmDialog = false
                        selectedIds.clear()
                        replyingTo = null
                        editingMessage = null
                        isSearchOpen = false
                        inChatQuery = ""
                        if (onClearChat != null) {
                            onClearChat()
                        } else {
                            val allIds = visibleMessages.map { it.id }
                            if (allIds.isNotEmpty()) {
                                onDeleteForMe(allIds)
                            }
                            scope.launch { snackbarHostState.showSnackbar("Chat vaciado") }
                        }
                    },
                    modifier = Modifier.testTag("clear_chat_confirm_button")
                ) {
                    Text("Vaciar chat", color = DangerRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearChatConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showBlockContactConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showBlockContactConfirmDialog = false },
            title = {
                Text(if (isContactBlocked) "Desbloquear a ${chatUi.displayTitle}" else "Bloquear a ${chatUi.displayTitle}")
            },
            text = {
                Text(
                    if (isContactBlocked) "¿Deseas desbloquear este contacto para volver a interactuar normalmente?"
                    else "¿Deseas bloquear a ${chatUi.displayTitle}?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isContactBlocked = !isContactBlocked
                        showBlockContactConfirmDialog = false
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (isContactBlocked) "Contacto bloqueado" else "Contacto desbloqueado"
                            )
                        }
                    }
                ) {
                    Text(
                        text = if (isContactBlocked) "Desbloquear" else "Bloquear",
                        color = DangerRed
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockContactConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
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

    pendingVideoUri?.let { uri ->
        VideoPreviewSendDialog(
            videoUri = uri,
            onDismiss = { pendingVideoUri = null },
            onSend = { caption ->
                onSendVideo(uri, caption, replyingTo?.id)
                replyingTo = null
                pendingVideoUri = null
            }
        )
    }

    viewerImageUri?.let { uri ->
        FullScreenImageDialog(imageUri = uri, onDismiss = { viewerImageUri = null })
    }

    viewerVideoFile?.let { file ->
        FullScreenVideoDialog(
            videoFile = file,
            caption = viewerVideoCaption,
            onDismiss = { viewerVideoFile = null }
        )
    }

    if (showLocationDialog) {
        LocationPickerDialog(
            onDismiss = { showLocationDialog = false },
            onSendLocation = { name, address, lat, lng, liveDurationMinutes ->
                onSendLocation(name, address, lat, lng, replyingTo?.id, liveDurationMinutes)
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

private const val MENU_ID_COPY = 0
private const val MENU_ID_PASTE = 1
private const val MENU_ID_CUT = 2
private const val MENU_ID_SELECT_ALL = 3
private const val MENU_ID_BOLD = 101
private const val MENU_ID_ITALIC = 102
private const val MENU_ID_STRIKE = 103

@Composable
private fun rememberFormattingTextToolbar(
    hasNonBlankSelection: () -> Boolean,
    onApplyBold: () -> Unit,
    onApplyItalic: () -> Unit,
    onApplyStrike: () -> Unit
): TextToolbar {
    val view = LocalView.current
    val latestHasSelection by rememberUpdatedState(hasNonBlankSelection)
    val latestBold by rememberUpdatedState(onApplyBold)
    val latestItalic by rememberUpdatedState(onApplyItalic)
    val latestStrike by rememberUpdatedState(onApplyStrike)

    val toolbar = remember(view) {
        object : TextToolbar {
            private var actionMode: ActionMode? = null
            private var selectionRect: Rect = Rect.Zero
            private var onCopyRequested: (() -> Unit)? = null
            private var onPasteRequested: (() -> Unit)? = null
            private var onCutRequested: (() -> Unit)? = null
            private var onSelectAllRequested: (() -> Unit)? = null

            override val status: TextToolbarStatus
                get() = if (actionMode != null) TextToolbarStatus.Shown else TextToolbarStatus.Hidden

            private fun populateMenu(menu: Menu) {
                menu.clear()
                onCopyRequested?.let {
                    menu.add(0, MENU_ID_COPY, 0, android.R.string.copy)
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                }
                onPasteRequested?.let {
                    menu.add(0, MENU_ID_PASTE, 1, android.R.string.paste)
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                }
                onCutRequested?.let {
                    menu.add(0, MENU_ID_CUT, 2, android.R.string.cut)
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                }
                val canFormatSelection = (onCopyRequested != null || onCutRequested != null) &&
                    latestHasSelection()
                if (canFormatSelection) {
                    val boldTitle = SpannableString("B").apply {
                        setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    menu.add(0, MENU_ID_BOLD, 3, boldTitle).apply {
                        setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                        MenuItemCompat.setContentDescription(this, "Negrita")
                    }

                    val italicTitle = SpannableString("I").apply {
                        setSpan(StyleSpan(Typeface.BOLD_ITALIC), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    menu.add(0, MENU_ID_ITALIC, 4, italicTitle).apply {
                        setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                        MenuItemCompat.setContentDescription(this, "Cursiva")
                    }

                    val strikeTitle = SpannableString("S").apply {
                        setSpan(StrikethroughSpan(), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    menu.add(0, MENU_ID_STRIKE, 5, strikeTitle).apply {
                        setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                        MenuItemCompat.setContentDescription(this, "Tachado")
                    }
                }
                onSelectAllRequested?.let {
                    menu.add(0, MENU_ID_SELECT_ALL, 6, android.R.string.selectAll)
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                }
            }

            private val callback = object : ActionMode.Callback2() {
                override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                    if (menu != null) populateMenu(menu)
                    return true
                }

                override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                    if (menu != null) {
                        populateMenu(menu)
                        return true
                    }
                    return false
                }

                override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                    when (item?.itemId) {
                        MENU_ID_COPY -> onCopyRequested?.invoke()
                        MENU_ID_PASTE -> onPasteRequested?.invoke()
                        MENU_ID_CUT -> onCutRequested?.invoke()
                        MENU_ID_BOLD -> latestBold()
                        MENU_ID_ITALIC -> latestItalic()
                        MENU_ID_STRIKE -> latestStrike()
                        MENU_ID_SELECT_ALL -> onSelectAllRequested?.invoke()
                        else -> return false
                    }
                    mode?.finish()
                    return true
                }

                override fun onDestroyActionMode(mode: ActionMode?) {
                    if (actionMode === mode) {
                        actionMode = null
                    }
                }

                override fun onGetContentRect(
                    mode: ActionMode?,
                    targetView: View?,
                    outRect: android.graphics.Rect?
                ) {
                    outRect?.set(
                        selectionRect.left.roundToInt(),
                        selectionRect.top.roundToInt(),
                        selectionRect.right.roundToInt(),
                        selectionRect.bottom.roundToInt()
                    )
                }
            }

            override fun showMenu(
                rect: Rect,
                onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?,
                onSelectAllRequested: (() -> Unit)?
            ) {
                this.selectionRect = rect
                this.onCopyRequested = onCopyRequested
                this.onPasteRequested = onPasteRequested
                this.onCutRequested = onCutRequested
                this.onSelectAllRequested = onSelectAllRequested
                val existing = actionMode
                if (existing == null) {
                    actionMode = view.startActionMode(callback, ActionMode.TYPE_FLOATING)
                } else {
                    existing.invalidate()
                    existing.invalidateContentRect()
                }
            }

            override fun hide() {
                actionMode?.finish()
                actionMode = null
            }
        }
    }

    DisposableEffect(toolbar) {
        onDispose { toolbar.hide() }
    }

    return toolbar
}

internal fun applyFormatToSelection(current: TextFieldValue, token: String): TextFieldValue {
    val start = current.selection.min.coerceIn(0, current.text.length)
    val end = current.selection.max.coerceIn(start, current.text.length)
    if (start == end) return current

    val selected = current.text.substring(start, end)
    // Toggle off if surrounding characters already hold the token
    if (start >= token.length &&
        end + token.length <= current.text.length &&
        current.text.substring(start - token.length, start) == token &&
        current.text.substring(end, end + token.length) == token
    ) {
        val nextText = current.text.removeRange(end, end + token.length)
            .removeRange(start - token.length, start)
        return TextFieldValue(
            text = nextText,
            selection = TextRange(start - token.length, end - token.length)
        )
    }

    // Toggle off if selection itself includes the wrapping tokens
    if (selected.length > token.length * 2 && selected.startsWith(token) && selected.endsWith(token)) {
        val unwrapped = selected.substring(token.length, selected.length - token.length)
        val nextText = current.text.replaceRange(start, end, unwrapped)
        return TextFieldValue(
            text = nextText,
            selection = TextRange(start, start + unwrapped.length)
        )
    }

    val wrapped = "$token$selected$token"
    val nextText = current.text.replaceRange(start, end, wrapped)
    return TextFieldValue(
        text = nextText,
        selection = TextRange(start + token.length, end + token.length)
    )
}

private fun insertTextAtSelection(current: TextFieldValue, insertion: String): TextFieldValue {
    val start = current.selection.min.coerceIn(0, current.text.length)
    val end = current.selection.max.coerceIn(start, current.text.length)
    val nextText = current.text.replaceRange(start, end, insertion)
    val nextCursor = start + insertion.length
    return TextFieldValue(text = nextText, selection = TextRange(nextCursor))
}
