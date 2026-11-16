package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.CallDirection
import com.example.data.model.CallRecordEntity
import com.example.data.model.CallType
import com.example.data.model.ChatType
import com.example.data.model.FamilyEventEntity
import com.example.data.model.FamilyMemberEntity
import com.example.data.model.FamilyNoteEntity
import com.example.data.model.ShoppingItemEntity
import com.example.ui.components.AppUpdateDialog
import com.example.ui.components.MemberAvatar
import com.example.ui.components.MessageStatusIcon
import com.example.ui.components.UserProfileOptionsDialog
import com.example.ui.theme.OnlineGreen
import com.example.ui.viewmodel.ChatThreadUi
import com.example.ui.viewmodel.MainTab
import com.example.util.AppReleaseInfo
import com.example.util.AppUpdateUiState
import com.example.util.TimeFormatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainHomeScreen(
    activeMember: FamilyMemberEntity,
    allMembers: List<FamilyMemberEntity>,
    userGroupsCount: Int,
    isDarkTheme: Boolean,
    updateRepoSlug: String,
    updateState: AppUpdateUiState,
    selectedTab: MainTab,
    searchQuery: String,
    chatPreviews: List<ChatThreadUi>,
    calls: List<CallRecordEntity>,
    shoppingItems: List<ShoppingItemEntity>,
    events: List<FamilyEventEntity>,
    notes: List<FamilyNoteEntity>,
    snackbarMessage: String?,
    onClearSnackbar: () -> Unit,
    onToggleTheme: () -> Unit,
    onUpdateOwnStatus: (Boolean, String) -> Unit,
    onCheckForUpdate: () -> Unit,
    onSaveUpdateRepoSlug: (String) -> Unit,
    onDownloadUpdate: (AppReleaseInfo) -> Unit,
    onResetUpdateState: () -> Unit,
    onSelectTab: (MainTab) -> Unit,
    onSearchChange: (String) -> Unit,
    onOpenChat: (String) -> Unit,
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
    onDeleteNote: (String) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showNewChatDialog by remember { mutableStateOf(false) }
    var showProfileOptionsDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(snackbarMessage) {
        if (snackbarMessage != null) {
            snackbarHostState.showSnackbar(snackbarMessage)
            onClearSnackbar()
        }
    }

    val totalUnread = remember(chatPreviews) { chatPreviews.sumOf { it.unreadCount } }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 700.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { showProfileOptionsDialog = true }
                                    .padding(end = 8.dp)
                                    .testTag("active_profile_switcher")
                            ) {
                                MemberAvatar(
                                    emoji = activeMember.avatarEmoji,
                                    colorHex = activeMember.avatarColorHex,
                                    size = 38.dp,
                                    isOnline = activeMember.isOnline,
                                    showOnlineDot = true
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "NidoFamiliar",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${activeMember.name} · ${activeMember.lastSeenText}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        },
                        actions = {
                            Box {
                                IconButton(
                                    onClick = { showOverflowMenu = true },
                                    modifier = Modifier.testTag("home_more_menu_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.MoreVert,
                                        contentDescription = "Opciones de perfil"
                                    )
                                }
                                DropdownMenu(
                                    expanded = showOverflowMenu,
                                    onDismissRequest = { showOverflowMenu = false },
                                    modifier = Modifier.testTag("home_more_menu")
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Mi perfil y características") },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Person, contentDescription = null)
                                        },
                                        onClick = {
                                            showOverflowMenu = false
                                            showProfileOptionsDialog = true
                                        },
                                        modifier = Modifier.testTag("menu_profile_options")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Switch (Cambiar de perfil)") },
                                        leadingIcon = {
                                            Icon(Icons.Filled.SwapHoriz, contentDescription = null)
                                        },
                                        onClick = {
                                            showOverflowMenu = false
                                            onSwitchProfile()
                                        },
                                        modifier = Modifier.testTag("menu_switch_profile")
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(if (isDarkTheme) "Cambiar a tema blanco" else "Cambiar a tema oscuro")
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (isDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            showOverflowMenu = false
                                            onToggleTheme()
                                        },
                                        modifier = Modifier.testTag("menu_toggle_theme")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Actualizar aplicación") },
                                        leadingIcon = {
                                            Icon(Icons.Filled.SystemUpdate, contentDescription = null)
                                        },
                                        onClick = {
                                            showOverflowMenu = false
                                            showUpdateDialog = true
                                            onCheckForUpdate()
                                        },
                                        modifier = Modifier.testTag("menu_check_update")
                                    )
                                    if (activeMember.isAdmin) {
                                        DropdownMenuItem(
                                            text = { Text("Panel de Administración") },
                                            leadingIcon = {
                                                Icon(Icons.Filled.AdminPanelSettings, contentDescription = null)
                                            },
                                            onClick = {
                                                showOverflowMenu = false
                                                onOpenAdminPanel()
                                            },
                                            modifier = Modifier.testTag("menu_admin_panel")
                                        )
                                    }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
                }
            },
            bottomBar = {
                if (!isWide) {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        MainNavBarItems(
                            selectedTab = selectedTab,
                            unreadCount = totalUnread,
                            onSelectTab = onSelectTab
                        )
                    }
                }
            },
            floatingActionButton = {
                if (selectedTab == MainTab.CHATS) {
                    FloatingActionButton(
                        onClick = { showNewChatDialog = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("new_chat_fab")
                    ) {
                        Icon(Icons.Filled.AddComment, contentDescription = "Nuevo chat")
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
                            MainTab.CHATS -> ChatsListSection(
                                chats = chatPreviews,
                                searchQuery = searchQuery,
                                onSearchChange = onSearchChange,
                                onOpenChat = onOpenChat,
                                emptyTip = "No hay chats que coincidan con tu búsqueda."
                            )
                            MainTab.GROUPS -> ChatsListSection(
                                chats = chatPreviews.filter { it.chat.type == ChatType.GROUP },
                                searchQuery = searchQuery,
                                onSearchChange = onSearchChange,
                                onOpenChat = onOpenChat,
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
                }
            }
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
                                        size = 42.dp,
                                        isOnline = member.isOnline,
                                        showOnlineDot = true
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(member.name, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            member.roleLabel,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
            userGroupsCount = userGroupsCount,
            isDarkTheme = isDarkTheme,
            onToggleTheme = onToggleTheme,
            onUpdateStatus = { online, statusText ->
                onUpdateOwnStatus(online, statusText)
                showProfileOptionsDialog = false
            },
            onOpenAdminPanel = onOpenAdminPanel,
            onRequestSwitchProfile = onSwitchProfile,
            onDismiss = { showProfileOptionsDialog = false }
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
private fun MainNavRailItems(
    selectedTab: MainTab,
    unreadCount: Int,
    onSelectTab: (MainTab) -> Unit
) {
    val entries = listOf(
        Triple(MainTab.CHATS, "Chats", Pair(Icons.AutoMirrored.Filled.Chat, Icons.AutoMirrored.Outlined.Chat)),
        Triple(MainTab.GROUPS, "Grupos", Pair(Icons.Filled.Groups, Icons.Outlined.Groups)),
        Triple(MainTab.CALLS, "Llamadas", Pair(Icons.Filled.Call, Icons.Outlined.Call)),
        Triple(MainTab.BOARD, "Tablón", Pair(Icons.Filled.Dashboard, Icons.Outlined.Dashboard))
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
    onSelectTab: (MainTab) -> Unit
) {
    val entries = listOf(
        Triple(MainTab.CHATS, "Chats", Pair(Icons.AutoMirrored.Filled.Chat, Icons.AutoMirrored.Outlined.Chat)),
        Triple(MainTab.GROUPS, "Grupos", Pair(Icons.Filled.Groups, Icons.Outlined.Groups)),
        Triple(MainTab.CALLS, "Llamadas", Pair(Icons.Filled.Call, Icons.Outlined.Call)),
        Triple(MainTab.BOARD, "Tablón", Pair(Icons.Filled.Dashboard, Icons.Outlined.Dashboard))
    )

    entries.forEach { (tab, label, icons) ->
        val selected = selectedTab == tab
        MainNavBarItem(
            selected = selected,
            label = label,
            icon = if (selected) icons.first else icons.second,
            badgeCount = if (tab == MainTab.CHATS) unreadCount else 0,
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
    tag: String,
    onClick: () -> Unit
) {
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
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = Modifier.testTag(tag)
    )
}

@Composable
private fun ChatsListSection(
    chats: List<ChatThreadUi>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onOpenChat: (String) -> Unit,
    emptyTip: String
) {
    val filtered = remember(chats, searchQuery) {
        if (searchQuery.isBlank()) chats
        else chats.filter {
            it.displayTitle.contains(searchQuery, ignoreCase = true) ||
                it.lastMessagePreview.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            shape = RoundedCornerShape(21.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .height(42.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Buscar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Buscar chats o mensajes...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
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
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = emptyTip,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                items(filtered, key = { it.chat.id }) { item ->
                    ChatRowItem(item = item, onClick = { onOpenChat(item.chat.id) })
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 82.dp, end = 16.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatRowItem(
    item: ChatThreadUi,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("chat_item_${item.chat.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MemberAvatar(
            emoji = item.displayEmoji,
            colorHex = item.displayColorHex,
            size = 54.dp,
            isOnline = item.isOnline,
            showOnlineDot = true
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.displayTitle,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item.lastMessageTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (item.unreadCount > 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (item.isOnline) "En línea" else item.subtitleStatus,
                style = MaterialTheme.typography.labelSmall,
                color = if (item.isOnline) OnlineGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.isLastMessageFromMe && item.lastMessageStatus != null) {
                    MessageStatusIcon(status = item.lastMessageStatus)
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = item.lastMessagePreview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = item.unreadCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
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
