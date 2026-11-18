package com.example.ui.screens

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CallDirection
import com.example.data.model.CallRecordEntity
import com.example.data.model.CallType
import com.example.data.model.ChatType
import com.example.data.model.FamilyEventEntity
import com.example.data.model.FamilyMemberEntity
import com.example.data.model.FamilyNoteEntity
import com.example.data.model.FamilyStatusEntity
import com.example.data.model.ShoppingItemEntity
import androidx.compose.material.icons.filled.NotificationsActive
import com.example.ui.components.AppUpdateDialog
import com.example.ui.components.CreateStatusDialog
import com.example.ui.components.FamilyStatusStrip
import com.example.ui.components.MemberAvatar
import com.example.ui.components.MemberStatusGroup
import com.example.ui.components.MessageStatusIcon
import com.example.ui.components.StatusViewerDialog
import com.example.ui.components.UserProfileOptionsDialog
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.OnlineGreen
import com.example.ui.viewmodel.ChatThreadUi
import com.example.ui.viewmodel.MainTab
import com.example.util.AppReleaseInfo
import com.example.util.AppUpdateUiState
import com.example.util.FamiChatNotificationHelper
import com.example.util.TimeFormatters
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MainHomeScreen(
    activeMember: FamilyMemberEntity,
    allMembers: List<FamilyMemberEntity>,
    userGroupsCount: Int,
    isDarkTheme: Boolean,
    notificationsEnabled: Boolean,
    updateRepoSlug: String,
    updateState: AppUpdateUiState,
    selectedTab: MainTab,
    searchQuery: String,
    chatPreviews: List<ChatThreadUi>,
    statuses: List<FamilyStatusEntity>,
    calls: List<CallRecordEntity>,
    shoppingItems: List<ShoppingItemEntity>,
    events: List<FamilyEventEntity>,
    notes: List<FamilyNoteEntity>,
    snackbarMessage: String?,
    onClearSnackbar: () -> Unit,
    onToggleTheme: () -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onUpdateOwnProfile: (nickname: String, avatarColorHex: String, isOnline: Boolean, statusText: String, bioText: String, newPhotoUri: Uri?, removePhoto: Boolean) -> Unit,
    onPublishTextStatus: (text: String, backgroundHex: String) -> Unit,
    onPublishPhotoStatus: (uri: Uri, caption: String) -> Unit,
    onPublishVideoStatus: (uri: Uri, caption: String) -> Unit = { _, _ -> },
    onPrepareStatusVideo: (status: FamilyStatusEntity, onReady: (java.io.File) -> Unit, onError: (String) -> Unit) -> Unit = { _, _, _ -> },
    onSaveStatusMediaToGallery: (status: FamilyStatusEntity) -> Unit = {},
    mediaTransferProgress: Map<String, Float> = emptyMap(),
    onMarkStatusViewed: (String) -> Unit,
    onDeleteStatus: (String) -> Unit,
    onReplyToStatus: (FamilyStatusEntity, String) -> Unit,
    onCheckForUpdate: () -> Unit,
    onSaveUpdateRepoSlug: (String) -> Unit,
    onDownloadUpdate: (AppReleaseInfo) -> Unit,
    onResetUpdateState: () -> Unit,
    onSelectTab: (MainTab) -> Unit,
    onSearchChange: (String) -> Unit,
    onOpenChat: (String) -> Unit,
    onTogglePinChat: (String) -> Unit = {},
    onToggleMuteChat: (String) -> Unit = {},
    onStartDirectChat: (String) -> Unit,
    onSwitchProfile: () -> Unit,
    onOpenAdminPanel: () -> Unit,
    onStartCall: (chatId: String, title: String, emoji: String, colorHex: String, type: CallType) -> Unit,
    onAddShoppingItem: (String, String, String) -> Unit,
    onToggleShoppingItem: (ShoppingItemEntity) -> Unit,
    onDeleteShoppingItem: (String) -> Unit,
    onClearCheckedShopping: () -> Unit,
    onAddEvent: (String, String, String, String, String, String) -> Unit,
    onDeleteEvent: (String) -> Unit,
    onSaveNote: (String?, String, String, String, Boolean) -> Unit,
    onDeleteNote: (String) -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenNotifications: () -> Unit = {}
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val isImeVisible = WindowInsets.isImeVisible
    val snackbarHostState = remember { SnackbarHostState() }
    var showNewChatDialog by remember { mutableStateOf(false) }
    var showProfileOptionsDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showUpdateDialog by rememberSaveable { mutableStateOf(false) }
    var showCreateStatusDialog by remember { mutableStateOf(false) }
    var viewingStatusMemberId by remember { mutableStateOf<String?>(null) }

    var isExitArmed by remember { mutableStateOf(false) }
    var exitArmToken by remember { mutableIntStateOf(0) }

    LaunchedEffect(exitArmToken) {
        if (exitArmToken > 0) {
            delay(3_000L)
            isExitArmed = false
        }
    }

    LaunchedEffect(
        selectedTab,
        showOverflowMenu,
        showNewChatDialog,
        showProfileOptionsDialog,
        showCreateStatusDialog,
        showUpdateDialog,
        viewingStatusMemberId,
        isImeVisible
    ) {
        if (selectedTab != MainTab.CHATS ||
            showOverflowMenu ||
            showNewChatDialog ||
            showProfileOptionsDialog ||
            showCreateStatusDialog ||
            showUpdateDialog ||
            viewingStatusMemberId != null ||
            isImeVisible
        ) {
            isExitArmed = false
        }
    }

    val canExitDirectly = isExitArmed &&
        selectedTab == MainTab.CHATS &&
        !showOverflowMenu &&
        !showNewChatDialog &&
        !showProfileOptionsDialog &&
        !showCreateStatusDialog &&
        !showUpdateDialog &&
        viewingStatusMemberId == null &&
        !isImeVisible

    BackHandler(enabled = !canExitDirectly) {
        when {
            isImeVisible -> {
                keyboardController?.hide()
                focusManager.clearFocus()
            }
            showOverflowMenu -> showOverflowMenu = false
            selectedTab != MainTab.CHATS -> onSelectTab(MainTab.CHATS)
            else -> {
                isExitArmed = true
                exitArmToken++
            }
        }
    }

    var hasNotifPermission by remember {
        mutableStateOf(FamiChatNotificationHelper.canPostNotifications(context))
    }
    var notifBannerDismissed by rememberSaveable { mutableStateOf(false) }
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasNotifPermission = granted
            if (granted) onToggleNotifications(true)
        }
    )

    val viewingStatusGroup = remember(viewingStatusMemberId, statuses, allMembers) {
        val targetId = viewingStatusMemberId ?: return@remember null
        val member = allMembers.find { it.id == targetId } ?: return@remember null
        val list = statuses.filter { it.memberId == targetId }.sortedBy { it.createdAt }
        if (list.isEmpty()) null else MemberStatusGroup(member, list, hasUnviewed = false)
    }

    LaunchedEffect(snackbarMessage) {
        if (snackbarMessage != null) {
            snackbarHostState.showSnackbar(snackbarMessage)
            onClearSnackbar()
        }
    }

    val totalUnread = remember(chatPreviews) { chatPreviews.sumOf { it.unreadCount } }
    val brandBlue = if (isDarkTheme) MaterialTheme.colorScheme.primary else Color(0xFF1A5FD4)
    val textPrimary = if (isDarkTheme) MaterialTheme.colorScheme.onSurface else Color(0xFF1B1F2A)
    val textSecondary = if (isDarkTheme) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF5B6270)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 700.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = if (isDarkTheme) MaterialTheme.colorScheme.surface else Color.White,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                                .testTag("active_profile_switcher"),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "FamiChat",
                                fontFamily = InterFontFamily,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Medium,
                                color = textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (activeMember.isOnline) OnlineGreen
                                            else Color(0xFF9AA0A6)
                                        )
                                )
                                Text(
                                    text = "${activeMember.name} · ${activeMember.lastSeenText}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = InterFontFamily
                                    ),
                                    color = textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showOverflowMenu = !showOverflowMenu },
                            modifier = Modifier.testTag("home_more_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "Opciones de perfil",
                                tint = textPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (isDarkTheme) MaterialTheme.colorScheme.surface else Color.White,
                        titleContentColor = textPrimary,
                        actionIconContentColor = textPrimary
                    )
                )
            },
            floatingActionButton = {
                if (selectedTab == MainTab.CHATS) {
                    FloatingActionButton(
                        onClick = { showNewChatDialog = true },
                        shape = CircleShape,
                        containerColor = brandBlue,
                        contentColor = Color.White,
                        elevation = FloatingActionButtonDefaults.elevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 2.dp,
                            focusedElevation = 0.dp,
                            hoveredElevation = 0.dp
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("fab_new_chat")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Nuevo chat",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            },
            bottomBar = {
                if (!isWide) {
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        NavigationBar(
                            windowInsets = WindowInsets.navigationBars,
                            containerColor = if (isDarkTheme) MaterialTheme.colorScheme.surface else Color.White,
                            tonalElevation = 0.dp
                        ) {
                            MainNavBarItems(
                                selectedTab = selectedTab,
                                unreadCount = totalUnread,
                                isDarkTheme = isDarkTheme,
                                onSelectTab = onSelectTab
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWide) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))
                        MainNavRailItems(
                            selectedTab = selectedTab,
                            unreadCount = totalUnread,
                            onSelectTab = onSelectTab
                        )
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(modifier = Modifier.fillMaxSize().widthIn(max = 720.dp)) {
                        when (selectedTab) {
                            MainTab.CHATS -> Column(modifier = Modifier.fillMaxSize()) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    notificationsEnabled &&
                                    !hasNotifPermission &&
                                    !notifBannerDismissed
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.Notifications,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "Activa notificaciones para recibir alertas de mensajes y llamadas",
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.weight(1f)
                                            )
                                            TextButton(onClick = { notifBannerDismissed = true }) {
                                                Text("Luego")
                                            }
                                            Button(
                                                onClick = {
                                                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                                },
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                modifier = Modifier.testTag("enable_notifications_banner_btn")
                                            ) {
                                                Text("Permitir")
                                            }
                                        }
                                    }
                                }
                                FamilyStatusStrip(
                                    activeMember = activeMember,
                                    allMembers = allMembers,
                                    statuses = statuses,
                                    onAddStatus = { showCreateStatusDialog = true },
                                    onOpenMemberStatuses = { group ->
                                        viewingStatusMemberId = group.member.id
                                    }
                                )
                                val statusUploadEntry = mediaTransferProgress.entries.firstOrNull { it.key.startsWith("st_") }
                                if (statusUploadEntry != null) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "🎬 Subiendo estado en video...",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            val pctInt = (statusUploadEntry.value * 100f).toInt().coerceIn(5, 100)
                                            Text(
                                                text = "$pctInt%",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        androidx.compose.material3.LinearProgressIndicator(
                                            progress = { statusUploadEntry.value.coerceIn(0.05f, 1f) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                        )
                                    }
                                }
                                ChatsListSection(
                                    chats = chatPreviews,
                                    allMembers = allMembers,
                                    showHomeSummaryPill = true,
                                    isDarkTheme = isDarkTheme,
                                    searchQuery = searchQuery,
                                    onSearchChange = onSearchChange,
                                    onOpenChat = { targetChatId ->
                                        if (targetChatId.startsWith("virtual_direct_")) {
                                            val otherId = targetChatId.removePrefix("virtual_direct_")
                                            onStartDirectChat(otherId)
                                        } else {
                                            onOpenChat(targetChatId)
                                        }
                                    },
                                    onTogglePinChat = onTogglePinChat,
                                    onToggleMuteChat = onToggleMuteChat,
                                    emptyTip = "No hay chats que coincidan con tu búsqueda."
                                )
                            }
                            MainTab.GROUPS -> ChatsListSection(
                                chats = chatPreviews.filter { it.chat.type == ChatType.GROUP },
                                isDarkTheme = isDarkTheme,
                                searchQuery = searchQuery,
                                onSearchChange = onSearchChange,
                                onOpenChat = onOpenChat,
                                onTogglePinChat = onTogglePinChat,
                                onToggleMuteChat = onToggleMuteChat,
                                emptyTip = if (activeMember.isAdmin) {
                                    "No hay grupos. Toca el ícono de escudo arriba para crear uno."
                                } else {
                                    "Aún no perteneces a un grupo familiar."
                                }
                            )
                            MainTab.CALLS -> CallsListSection(
                                calls = calls,
                                onCallBack = { call, type ->
                                    onStartCall(call.chatId, call.title, call.avatarEmoji, call.avatarColorHex, type)
                                }
                            )
                            MainTab.BOARD -> FamilyBoardTab(
                                activeMember = activeMember,
                                members = allMembers,
                                shoppingItems = shoppingItems,
                                events = events,
                                notes = notes,
                                onAddShoppingItem = onAddShoppingItem,
                                onToggleShoppingItem = onToggleShoppingItem,
                                onDeleteShoppingItem = onDeleteShoppingItem,
                                onClearCheckedShopping = onClearCheckedShopping,
                                onAddEvent = onAddEvent,
                                onDeleteEvent = onDeleteEvent,
                                onSaveNote = onSaveNote,
                                onDeleteNote = onDeleteNote
                            )
                        }
                    }

                    if (showOverflowMenu) {
                        val updateSubtitle = when (updateState) {
                            is AppUpdateUiState.UpdateAvailable -> "Nueva versión v${updateState.release.versionName}"
                            is AppUpdateUiState.Downloading -> "Descargando ${updateState.progressPercent.coerceAtLeast(0)}%"
                            is AppUpdateUiState.ReadyToInstall -> "Lista para instalar"
                            else -> "Estás al día"
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.22f))
                                .clickable { showOverflowMenu = false }
                        ) {
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 4.dp,
                                shadowElevation = 12.dp,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 6.dp, end = 12.dp)
                                    .width(304.dp)
                                    .clickable(enabled = false) {}
                                    .testTag("home_more_menu")
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState())
                                        .padding(vertical = 8.dp)
                                ) {
                                    // Section: CUENTA
                                    Text(
                                        text = "CUENTA",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.8.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 6.dp)
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                showOverflowMenu = false
                                                showProfileOptionsDialog = true
                                            }
                                            .padding(horizontal = 20.dp, vertical = 12.dp)
                                            .testTag("menu_profile_options"),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Person,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(
                                            text = "Mi perfil",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                showOverflowMenu = false
                                                onSwitchProfile()
                                            }
                                            .padding(horizontal = 20.dp, vertical = 12.dp)
                                            .testTag("menu_switch_profile"),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.SwapHoriz,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(
                                            text = "Cambiar de perfil",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                    )

                                    // Section: PREFERENCIAS
                                    Text(
                                        text = "PREFERENCIAS",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.8.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 6.dp)
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                showOverflowMenu = false
                                                onOpenNotifications()
                                            }
                                            .padding(horizontal = 20.dp, vertical = 10.dp)
                                            .testTag("menu_toggle_push_notifications"),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Notifications,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Notificaciones",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = if (hasNotifPermission && notificationsEnabled) "Activadas" else "Desactivadas",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (hasNotifPermission && notificationsEnabled) OnlineGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onToggleTheme() }
                                            .padding(horizontal = 20.dp, vertical = 12.dp)
                                            .testTag("menu_toggle_theme"),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.DarkMode,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(
                                            text = "Tema oscuro",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                        MenuPillSwitch(
                                            checked = isDarkTheme,
                                            onCheckedChange = { onToggleTheme() }
                                        )
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                showOverflowMenu = false
                                                onOpenSettings()
                                            }
                                            .padding(horizontal = 20.dp, vertical = 12.dp)
                                            .testTag("menu_settings"),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Settings,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(
                                            text = "Ajustes",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    if (activeMember.isAdmin) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 6.dp),
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                        )
                                        // Section: FAMILIA
                                        Text(
                                            text = "FAMILIA",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 0.8.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 6.dp)
                                        )
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    showOverflowMenu = false
                                                    onOpenAdminPanel()
                                                }
                                                .padding(horizontal = 20.dp, vertical = 10.dp)
                                                .testTag("menu_admin_panel"),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Shield,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Text(
                                                text = "Panel de\nadministración",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(50),
                                                color = if (isDarkTheme) MaterialTheme.colorScheme.primaryContainer
                                                else Color(0xFFE8F0FE)
                                            ) {
                                                Text(
                                                    text = "Admin",
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }

                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                    )

                                    // Section: AYUDA
                                    Text(
                                        text = "AYUDA",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.8.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 6.dp)
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                showOverflowMenu = false
                                                showUpdateDialog = true
                                                onCheckForUpdate()
                                            }
                                            .padding(horizontal = 20.dp, vertical = 10.dp)
                                            .testTag("menu_check_update"),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.FileDownload,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column {
                                            Text(
                                                text = "Buscar actualización",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = updateSubtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        viewingStatusGroup?.let { group ->
            StatusViewerDialog(
                group = group,
                activeMemberId = activeMember.id,
                allMembers = allMembers,
                mediaTransferProgress = mediaTransferProgress,
                onMarkViewed = onMarkStatusViewed,
                onDeleteStatus = onDeleteStatus,
                onReplyStatus = onReplyToStatus,
                onPrepareStatusVideo = onPrepareStatusVideo,
                onSaveStatusToGallery = onSaveStatusMediaToGallery,
                onDismiss = { viewingStatusMemberId = null }
            )
        }
    }

    if (showNewChatDialog) {
        val others = allMembers.filter { it.id != activeMember.id }
        AlertDialog(
            onDismissRequest = { showNewChatDialog = false },
            title = { Text("Iniciar chat directo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (others.isEmpty()) {
                        Text("No hay otros integrantes registrados aún.")
                    } else {
                        others.forEach { member ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        showNewChatDialog = false
                                        onStartDirectChat(member.id)
                                    }
                                    .testTag("start_chat_with_${member.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MemberAvatar(
                                        emoji = member.avatarEmoji,
                                        colorHex = member.avatarColorHex,
                                        photoData = member.avatarPhotoData,
                                        size = 42.dp,
                                        isOnline = member.isOnline,
                                        showOnlineDot = true
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(member.name, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = "${member.roleLabel} · ${member.lastSeenText}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (member.isOnline) OnlineGreen else MaterialTheme.colorScheme.onSurfaceVariant,
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
                TextButton(onClick = { showNewChatDialog = false }) { Text("Cerrar") }
            }
        )
    }

    if (showProfileOptionsDialog) {
        UserProfileOptionsDialog(
            member = activeMember,
            onUpdateProfile = { nickname, avatarColorHex, online, statusText, bioText, newPhotoUri, removePhoto ->
                onUpdateOwnProfile(nickname, avatarColorHex, online, statusText, bioText, newPhotoUri, removePhoto)
                showProfileOptionsDialog = false
            },
            onDismiss = { showProfileOptionsDialog = false }
        )
    }

    if (showCreateStatusDialog) {
        CreateStatusDialog(
            onDismiss = { showCreateStatusDialog = false },
            onPublishText = { text, bgHex ->
                onPublishTextStatus(text, bgHex)
                showCreateStatusDialog = false
            },
            onPublishPhoto = { uri, caption ->
                onPublishPhotoStatus(uri, caption)
                showCreateStatusDialog = false
            },
            onPublishVideo = { uri, caption ->
                onPublishVideoStatus(uri, caption)
                showCreateStatusDialog = false
            }
        )
    }

    if (showUpdateDialog) {
        AppUpdateDialog(
            isAdmin = activeMember.isAdmin,
            currentRepoSlug = updateRepoSlug,
            updateState = updateState,
            onCheckAgain = onCheckForUpdate,
            onSaveRepoSlug = onSaveUpdateRepoSlug,
            onDownloadUpdate = onDownloadUpdate,
            onDismiss = {
                showUpdateDialog = false
                onResetUpdateState()
            }
        )
    }
}

@Composable
private fun MenuPillSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val trackColor = if (checked) MaterialTheme.colorScheme.primary else Color(0xFF787E8A)
    Box(
        modifier = Modifier
            .width(46.dp)
            .height(26.dp)
            .clip(CircleShape)
            .background(trackColor)
            .clickable { onCheckedChange(!checked) }
            .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

@Composable
private fun MainNavRailItems(
    selectedTab: MainTab,
    unreadCount: Int,
    onSelectTab: (MainTab) -> Unit
) {
    val entries = listOf(
        Triple(MainTab.CHATS, "Chats", Pair(Icons.AutoMirrored.Filled.Chat, Icons.AutoMirrored.Outlined.Chat)),
        Triple(MainTab.GROUPS, "Grupos", Pair(Icons.Filled.Groups, Icons.Outlined.Groups)),
        Triple(MainTab.CALLS, "Llamadas", Pair(Icons.Filled.Call, Icons.Outlined.Call)),
        Triple(MainTab.BOARD, "Avisos", Pair(Icons.AutoMirrored.Filled.VolumeUp, Icons.AutoMirrored.Outlined.VolumeUp))
    )

    entries.forEach { (tab, label, icons) ->
        val selected = selectedTab == tab
        NavigationRailItem(
            selected = selected,
            onClick = { onSelectTab(tab) },
            icon = {
                Box {
                    Icon(
                        imageVector = if (selected) icons.first else icons.second,
                        contentDescription = label
                    )
                    if (tab == MainTab.CHATS && unreadCount > 0) {
                        Badge(modifier = Modifier.align(Alignment.TopEnd)) {
                            Text(unreadCount.toString())
                        }
                    }
                }
            },
            label = { Text(label) },
            modifier = Modifier.testTag("nav_${tab.name.lowercase()}")
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.MainNavBarItems(
    selectedTab: MainTab,
    unreadCount: Int,
    isDarkTheme: Boolean = false,
    onSelectTab: (MainTab) -> Unit
) {
    val entries = listOf(
        Triple(MainTab.CHATS, "Chats", Pair(Icons.AutoMirrored.Filled.Chat, Icons.AutoMirrored.Outlined.Chat)),
        Triple(MainTab.GROUPS, "Grupos", Pair(Icons.Filled.Groups, Icons.Outlined.Groups)),
        Triple(MainTab.CALLS, "Llamadas", Pair(Icons.Filled.Call, Icons.Outlined.Call)),
        Triple(MainTab.BOARD, "Avisos", Pair(Icons.AutoMirrored.Filled.VolumeUp, Icons.AutoMirrored.Outlined.VolumeUp))
    )

    entries.forEach { (tab, label, icons) ->
        val selected = selectedTab == tab
        MainNavBarItem(
            selected = selected,
            label = label,
            icon = if (selected) icons.first else icons.second,
            badgeCount = if (tab == MainTab.CHATS) unreadCount else 0,
            isDarkTheme = isDarkTheme,
            tag = "nav_${tab.name.lowercase()}",
            onClick = { onSelectTab(tab) }
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.MainNavBarItem(
    selected: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badgeCount: Int,
    isDarkTheme: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    val activeBlue = if (isDarkTheme) MaterialTheme.colorScheme.primary else Color(0xFF1A5FD4)
    val inactiveGray = if (isDarkTheme) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF5B6270)
    val activePill = if (isDarkTheme) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    } else {
        Color(0xFFDCE8FC)
    }

    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            Box {
                Icon(icon, contentDescription = label)
                if (badgeCount > 0) {
                    Badge(modifier = Modifier.align(Alignment.TopEnd)) {
                        Text(badgeCount.toString())
                    }
                }
            }
        },
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = InterFontFamily,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = activeBlue,
            selectedTextColor = activeBlue,
            indicatorColor = activePill,
            unselectedIconColor = inactiveGray,
            unselectedTextColor = inactiveGray
        ),
        modifier = Modifier.testTag(tag)
    )
}

@Composable
private fun ChatsListSection(
    chats: List<ChatThreadUi>,
    allMembers: List<FamilyMemberEntity> = emptyList(),
    showHomeSummaryPill: Boolean = false,
    isDarkTheme: Boolean = false,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onOpenChat: (String) -> Unit,
    onTogglePinChat: (String) -> Unit = {},
    onToggleMuteChat: (String) -> Unit = {},
    emptyTip: String
) {
    val brandBlue = if (isDarkTheme) MaterialTheme.colorScheme.primary else Color(0xFF1A5FD4)
    val textPrimary = if (isDarkTheme) MaterialTheme.colorScheme.onSurface else Color(0xFF1B1F2A)
    val textSecondary = if (isDarkTheme) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF5B6270)
    val searchBg = if (isDarkTheme) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f) else Color(0xFFF0F2F8)
    val summaryPillBg = if (isDarkTheme) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else Color(0xFFE4EDFC)

    val filtered = remember(chats, searchQuery) {
        if (searchQuery.isBlank()) chats
        else chats.filter {
            it.displayTitle.contains(searchQuery, ignoreCase = true) ||
                it.lastMessagePreview.contains(searchQuery, ignoreCase = true)
        }
    }

    val atHomeCount = remember(allMembers) {
        allMembers.count { it.lastSeenText.trim().equals("En casa", ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            shape = RoundedCornerShape(50),
            color = searchBg,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .heightIn(min = 44.dp)
                .height(44.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Buscar",
                    tint = textSecondary,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Buscar chats o mensajes",
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = InterFontFamily),
                            color = textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = InterFontFamily,
                            color = textPrimary
                        ),
                        cursorBrush = SolidColor(brandBlue),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("chat_list_search")
                    )
                }
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onSearchChange("") },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Limpiar búsqueda",
                            tint = textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        if (showHomeSummaryPill && atHomeCount > 0 && allMembers.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(50),
                color = summaryPillBg,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
            ) {
                Text(
                    text = "$atHomeCount de ${allMembers.size} en casa",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = brandBlue,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = emptyTip,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = InterFontFamily),
                    color = textSecondary
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(top = 4.dp, bottom = 84.dp)
            ) {
                items(filtered, key = { it.chat.id }) { item ->
                    ChatRowItem(
                        item = item,
                        isDarkTheme = isDarkTheme,
                        onClick = { onOpenChat(item.chat.id) },
                        onTogglePin = { onTogglePinChat(item.chat.id) },
                        onToggleMute = { onToggleMuteChat(item.chat.id) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatRowItem(
    item: ChatThreadUi,
    isDarkTheme: Boolean = false,
    onClick: () -> Unit,
    onTogglePin: () -> Unit = {},
    onToggleMute: () -> Unit = {}
) {
    var showContextMenu by remember { mutableStateOf(false) }
    val brandBlue = if (isDarkTheme) MaterialTheme.colorScheme.primary else Color(0xFF1A5FD4)
    val textPrimary = if (isDarkTheme) MaterialTheme.colorScheme.onSurface else Color(0xFF1B1F2A)
    val textSecondary = if (isDarkTheme) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF5B6270)
    val amberStatus = Color(0xFFD97706)

    val isGroup = item.chat.type == ChatType.GROUP
    val isHomeGroup = isGroup && (
        item.displayTitle.trim().equals("Mi Familia", ignoreCase = true) ||
            item.displayEmoji == "🏠"
        )
    val isEmptyThread = item.lastMessagePreview == "Sin mensajes aún" ||
        item.lastMessagePreview == "Aún no hay mensajes. ¡Saluda a la familia!" ||
        item.lastMessagePreview.isBlank()

    val cleanStatus = item.subtitleStatus.trim()
    val hasExplicitMemberStatus = !isGroup && (
        cleanStatus.equals("En línea", ignoreCase = true) ||
            cleanStatus.equals("En casa", ignoreCase = true) ||
            cleanStatus.equals("Ocupado", ignoreCase = true) ||
            cleanStatus.equals("En el trabajo", ignoreCase = true) ||
            cleanStatus.equals("En la escuela", ignoreCase = true) ||
            cleanStatus.startsWith("Escribiendo", ignoreCase = true) ||
            cleanStatus.startsWith("Grabando", ignoreCase = true)
        )

    val secondaryLineText = when {
        !isEmptyThread -> item.lastMessagePreview
        hasExplicitMemberStatus -> cleanStatus
        else -> "Toca para saludar"
    }

    val rightTimeText = if (isEmptyThread) "" else item.lastMessageTime

    val directDotColor = when {
        isGroup -> null
        cleanStatus.equals("En el trabajo", ignoreCase = true) ||
            cleanStatus.equals("En la escuela", ignoreCase = true) -> amberStatus
        item.isOnline -> OnlineGreen
        else -> Color(0xFF9AA0A6)
    }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showContextMenu = true }
                )
                .defaultMinSize(minHeight = 60.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag("chat_item_${item.chat.id}"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MemberAvatar(
                emoji = item.displayEmoji,
                colorHex = if (isHomeGroup) "#1A5FD4" else item.displayColorHex,
                photoData = item.displayPhotoData,
                size = 44.dp,
                isOnline = item.isOnline,
                showOnlineDot = !isGroup,
                showOfflineDot = !isGroup,
                statusDotColor = directDotColor,
                nameForInitials = item.displayTitle,
                isGroupAvatar = isGroup,
                useHomeGroupIcon = isHomeGroup,
                cornerRadius = 12.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.displayTitle,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.Medium
                            ),
                            color = textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (item.isMuted) {
                            Spacer(modifier = Modifier.width(5.dp))
                            Icon(
                                imageVector = Icons.Filled.NotificationsOff,
                                contentDescription = "Chat silenciado",
                                tint = textSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (item.isPinned) {
                            Text(
                                text = "Fijado",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = textSecondary
                            )
                        }
                        if (rightTimeText.isNotBlank()) {
                            Text(
                                text = rightTimeText,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = InterFontFamily,
                                    fontWeight = if (item.unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal
                                ),
                                color = if (item.unreadCount > 0) brandBlue else textSecondary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isEmptyThread && item.isLastMessageFromMe && item.lastMessageStatus != null) {
                        MessageStatusIcon(status = item.lastMessageStatus)
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = secondaryLineText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = InterFontFamily,
                            fontWeight = if (item.unreadCount > 0 && !isEmptyThread) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (item.unreadCount > 0 && !isEmptyThread) textPrimary else textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (item.unreadCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(
                                    if (item.isMuted) MaterialTheme.colorScheme.outline
                                    else brandBlue
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = item.unreadCount.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        if (!item.chat.id.startsWith("virtual_direct_")) {
            DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = { showContextMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(if (item.isPinned) "Desfijar chat" else "Fijar chat arriba") },
                    leadingIcon = { Icon(Icons.Filled.PushPin, contentDescription = null) },
                    onClick = {
                        showContextMenu = false
                        onTogglePin()
                    },
                    modifier = Modifier.testTag("context_pin_chat_${item.chat.id}")
                )
                DropdownMenuItem(
                    text = { Text(if (item.isMuted) "Activar notificaciones" else "Silenciar notificaciones") },
                    leadingIcon = {
                        Icon(
                            imageVector = if (item.isMuted) Icons.Filled.Notifications else Icons.Filled.NotificationsOff,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        showContextMenu = false
                        onToggleMute()
                    },
                    modifier = Modifier.testTag("context_mute_chat_${item.chat.id}")
                )
            }
        }
    }
}

@Composable
private fun CallsListSection(
    calls: List<CallRecordEntity>,
    onCallBack: (CallRecordEntity, CallType) -> Unit
) {
    if (calls.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No hay llamadas registradas aún.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(calls, key = { it.id }) { call ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MemberAvatar(
                    emoji = call.avatarEmoji,
                    colorHex = call.avatarColorHex,
                    size = 48.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = call.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (call.direction == CallDirection.MISSED) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val dirIcon = when (call.direction) {
                            CallDirection.OUTGOING -> Icons.AutoMirrored.Filled.CallMade
                            CallDirection.INCOMING -> Icons.AutoMirrored.Filled.CallReceived
                            CallDirection.MISSED -> Icons.AutoMirrored.Filled.CallMissed
                        }
                        val dirTint = if (call.direction == CallDirection.MISSED)
                            MaterialTheme.colorScheme.error else OnlineGreen
                        Icon(
                            imageVector = dirIcon,
                            contentDescription = call.direction.name,
                            tint = dirTint,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        val dur = if (call.durationSec > 0) " · ${TimeFormatters.formatDuration(call.durationSec)}" else " · Perdida"
                        Text(
                            text = "${TimeFormatters.formatDateTime(call.timestamp)}$dur",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = { onCallBack(call, CallType.VOICE) },
                    modifier = Modifier.testTag("callback_voice_${call.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Call,
                        contentDescription = "Llamada de voz",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = { onCallBack(call, CallType.VIDEO) },
                    modifier = Modifier.testTag("callback_video_${call.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Videocam,
                        contentDescription = "Videollamada",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
