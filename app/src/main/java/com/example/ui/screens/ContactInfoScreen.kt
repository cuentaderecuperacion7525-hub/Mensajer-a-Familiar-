package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.CallRecordEntity
import com.example.data.model.CallType
import com.example.data.model.ChatMessageEntity
import com.example.data.model.ChatType
import com.example.data.model.FamilyMemberEntity
import com.example.data.model.MessageType
import com.example.ui.components.ChatWallpaperPalette
import com.example.ui.components.extractAvatarInitials
import com.example.ui.theme.DangerRed
import com.example.ui.theme.LocalDarkTheme
import com.example.ui.viewmodel.ChatThreadUi
import com.example.util.DocumentMediaHelper
import com.example.util.ImageCompressionHelper
import com.example.util.LinkPreviewHelper
import com.example.util.PresenceResolver
import com.example.util.TimeFormatters
import com.example.util.parseHexColor

private val ContactInkPrimary = Color(0xFF1B1F2A)
private val ContactInkSecondary = Color(0xFF5B6270)
private val ContactCardBg = Color(0xFFF5F7FB)
private val ContactDividerColor = Color(0xFFE3E6EC)
private val ContactPillInactiveBg = Color(0xFFF0F2F8)
private val ContactStatusPillBg = Color(0xFFE1F5EE)
private val ContactStatusPillText = Color(0xFF085041)
private val ContactOnlineDot = Color(0xFF2FA866)
private val ContactOfflineDot = Color(0xFF9AA0A6)
private val ContactWallpaperBorder = Color(0xFFC9CED8)

private enum class ContactSharedTab { PHOTOS, FILES, LINKS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactInfoScreen(
    chatUi: ChatThreadUi,
    activeMember: FamilyMemberEntity,
    allMembers: List<FamilyMemberEntity>,
    visibleMessages: List<ChatMessageEntity>,
    calls: List<CallRecordEntity>,
    selectedWallpaperKey: String,
    selectedNotificationToneUri: String,
    isContactBlocked: Boolean,
    onSelectWallpaper: (String) -> Unit,
    onSelectNotificationTone: (String) -> Unit,
    onTogglePinChat: () -> Unit,
    onToggleMuteChat: () -> Unit,
    onStartCall: (CallType) -> Unit,
    onSearchInChat: () -> Unit,
    onOpenImage: (String) -> Unit,
    onOpenDocument: (ChatMessageEntity) -> Unit,
    onRequestClearChat: () -> Unit,
    onRequestToggleBlockContact: () -> Unit,
    onBack: () -> Unit,
    isDarkTheme: Boolean = LocalDarkTheme.current
) {
    val screenBg = if (isDarkTheme) MaterialTheme.colorScheme.background else Color.White
    val cardBg = if (isDarkTheme) MaterialTheme.colorScheme.surface else ContactCardBg
    val inkPrimary = if (isDarkTheme) MaterialTheme.colorScheme.onSurface else ContactInkPrimary
    val inkSecondary = if (isDarkTheme) MaterialTheme.colorScheme.onSurfaceVariant else ContactInkSecondary
    val dividerColor = if (isDarkTheme) MaterialTheme.colorScheme.outline.copy(alpha = 0.28f) else ContactDividerColor
    val pillInactiveBg = if (isDarkTheme) MaterialTheme.colorScheme.surfaceVariant else ContactPillInactiveBg
    val statusPillBg = if (isDarkTheme) Color(0xFF133827) else ContactStatusPillBg
    val statusPillText = if (isDarkTheme) Color(0xFF6EE7B7) else ContactStatusPillText
    val wallpaperBorder = if (isDarkTheme) MaterialTheme.colorScheme.outline else ContactWallpaperBorder
    val sheetBg = if (isDarkTheme) MaterialTheme.colorScheme.surface else Color.White

    val context = LocalContext.current
    val isGroup = chatUi.chat.type == ChatType.GROUP

    val contactMember = remember(chatUi, activeMember.id, allMembers) {
        if (isGroup) null
        else {
            val fromParticipants = chatUi.participants.firstOrNull { it.id != activeMember.id }
            fromParticipants?.let { p -> allMembers.firstOrNull { it.id == p.id } ?: p }
        }
    }

    val accentColor = remember(contactMember?.avatarColorHex, chatUi.displayColorHex) {
        parseHexColor(
            contactMember?.avatarColorHex?.ifBlank { chatUi.displayColorHex } ?: chatUi.displayColorHex,
            fallback = Color(0xFF1A5FD4)
        )
    }

    val displayName = contactMember?.name?.ifBlank { chatUi.displayTitle } ?: chatUi.displayTitle
    val displayPhoto = contactMember?.avatarPhotoData?.ifBlank { chatUi.displayPhotoData } ?: chatUi.displayPhotoData
    val displayEmoji = contactMember?.avatarEmoji ?: chatUi.displayEmoji
    val isOnline = contactMember?.isOnline ?: chatUi.isOnline
    val initials = remember(displayName, displayEmoji) {
        extractAvatarInitials(displayName, displayEmoji)
    }

    val statusPillLabel = remember(contactMember, isGroup) {
        if (isGroup) null else resolveContactStatusPill(contactMember)
    }
    val personalBio = remember(contactMember?.bioText, chatUi.partnerBioText, isGroup) {
        if (isGroup) "" else contactMember?.bioText?.trim().orEmpty().ifBlank { chatUi.partnerBioText.trim() }
    }
    val lastSeenLine = remember(contactMember, chatUi.subtitleStatus, isGroup) {
        if (isGroup) {
            "${chatUi.participants.size} miembros en este grupo"
        } else {
            resolveContactLastSeen(contactMember, chatUi.subtitleStatus)
        }
    }
    val memberSinceLine = remember(contactMember?.createdAt, isGroup) {
        val created = contactMember?.createdAt ?: 0L
        if (!isGroup && created > 0L) {
            "Miembro desde ${TimeFormatters.formatDayMonthYear(created)}"
        } else {
            null
        }
    }

    val sharedPhotos = remember(visibleMessages) {
        visibleMessages
            .filter { it.type == MessageType.IMAGE && !it.mediaUri.isNullOrBlank() }
            .reversed()
    }
    val sharedFiles = remember(visibleMessages) {
        visibleMessages
            .filter { it.type == MessageType.DOCUMENT }
            .reversed()
    }
    val sharedLinks = remember(visibleMessages) {
        visibleMessages
            .filter { it.type != MessageType.DELETED && it.text.isNotBlank() }
            .mapNotNull { msg ->
                LinkPreviewHelper.extractFirstUrl(msg.text)?.let { url -> msg to url }
            }
            .reversed()
    }

    val currentWallpaper = remember(selectedWallpaperKey) {
        ChatWallpaperPalette.options.firstOrNull { it.key == selectedWallpaperKey }
            ?: ChatWallpaperPalette.options.first()
    }

    var selectedSharedTab by remember { mutableStateOf(ContactSharedTab.PHOTOS) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showWallpaperSheet by remember { mutableStateOf(false) }
    var showAllPhotosSheet by remember { mutableStateOf(false) }
    var showFallbackToneSheet by remember { mutableStateOf(false) }

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val pickedUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            if (pickedUri == null || pickedUri == defaultUri) {
                onSelectNotificationTone("")
            } else {
                onSelectNotificationTone(pickedUri.toString())
            }
        }
    }

    BackHandler {
        when {
            showWallpaperSheet -> showWallpaperSheet = false
            showAllPhotosSheet -> showAllPhotosSheet = false
            showFallbackToneSheet -> showFallbackToneSheet = false
            showMoreMenu -> showMoreMenu = false
            else -> onBack()
        }
    }

    Scaffold(
        containerColor = screenBg,
        contentWindowInsets = WindowInsets.safeDrawing,
        modifier = Modifier
            .fillMaxSize()
            .testTag("contact_info_screen")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(screenBg)
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp)
            ) {
                // Flat theme-aware top bar with left Back arrow and right 3-dots menu
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("contact_info_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver al chat",
                            tint = inkPrimary
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showMoreMenu = true },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("contact_info_more_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "Más opciones",
                                tint = inkPrimary
                            )
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false },
                            containerColor = sheetBg
                        ) {
                            DropdownMenuItem(
                                text = { Text("Vaciar chat", color = DangerRed) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = null,
                                        tint = DangerRed
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    onRequestClearChat()
                                },
                                modifier = Modifier.testTag("contact_info_menu_clear")
                            )
                            if (!isGroup) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (isContactBlocked) "Desbloquear contacto" else "Bloquear contacto",
                                            color = DangerRed
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Filled.Block,
                                            contentDescription = null,
                                            tint = DangerRed
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        onRequestToggleBlockContact()
                                    },
                                    modifier = Modifier.testTag("contact_info_menu_block")
                                )
                            }
                        }
                    }
                }

                // Scrollable content with 20dp horizontal margins
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // HEADER (White background, no color band)
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .testTag("contact_info_avatar")
                    ) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(RoundedCornerShape(26.dp))
                                .background(accentColor),
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                displayPhoto.isNotBlank() -> {
                                    AsyncImage(
                                        model = ImageCompressionHelper.resolveCoilModel(displayPhoto),
                                        contentDescription = "Foto de $displayName",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                isGroup -> {
                                    Icon(
                                        imageVector = if (displayEmoji == "🏠") Icons.Filled.Home else Icons.Filled.Groups,
                                        contentDescription = displayName,
                                        tint = Color.White,
                                        modifier = Modifier.size(38.dp)
                                    )
                                }
                                initials.isNotEmpty() -> {
                                    Text(
                                        text = initials,
                                        color = Color.White,
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                else -> {
                                    Text(
                                        text = displayEmoji.ifBlank { "👤" },
                                        color = Color.White,
                                        fontSize = 30.sp
                                    )
                                }
                            }
                        }

                        // Presence dot: 18dp with 3dp surface border; green ONLY if online, gray otherwise
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(18.dp)
                                .background(
                                    color = if (isOnline) ContactOnlineDot else ContactOfflineDot,
                                    shape = CircleShape
                                )
                                .border(3.dp, screenBg, CircleShape)
                                .testTag("contact_info_presence_dot")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = displayName,
                        fontSize = 20.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.Medium,
                        color = inkPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("contact_info_name")
                    )

                    if (!statusPillLabel.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = statusPillBg,
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                            modifier = Modifier.testTag("contact_info_status_pill")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(ContactOnlineDot)
                                )
                                Text(
                                    text = statusPillLabel,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = statusPillText
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // PHRASE CARD (18dp radius)
                    if (personalBio.isNotBlank() || lastSeenLine.isNotBlank() || memberSinceLine != null) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = cardBg,
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("contact_info_phrase_card")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (personalBio.isNotBlank()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            text = "“",
                                            fontSize = 28.sp,
                                            lineHeight = 24.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = accentColor.copy(alpha = 0.35f),
                                            modifier = Modifier.padding(end = 6.dp)
                                        )
                                        Text(
                                            text = personalBio,
                                            fontSize = 13.sp,
                                            lineHeight = 19.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = inkPrimary,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(top = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                }
                                if (lastSeenLine.isNotBlank()) {
                                    Text(
                                        text = lastSeenLine,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp,
                                        color = inkSecondary,
                                        modifier = Modifier.testTag("contact_info_last_seen")
                                    )
                                }
                                if (memberSinceLine != null) {
                                    Text(
                                        text = memberSinceLine,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                        color = inkSecondary,
                                        modifier = Modifier.testTag("contact_info_member_since")
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    // ACTIONS (4 circles of 44dp with 12% accent background + 11sp labels)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("contact_info_actions_row"),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ContactQuickAction(
                            icon = Icons.Outlined.ChatBubbleOutline,
                            label = "Mensaje",
                            accentColor = accentColor,
                            labelColor = inkPrimary,
                            tag = "contact_action_message",
                            onClick = onBack,
                            modifier = Modifier.weight(1f)
                        )
                        ContactQuickAction(
                            icon = Icons.Outlined.Call,
                            label = "Llamar",
                            accentColor = accentColor,
                            labelColor = inkPrimary,
                            tag = "contact_action_voice_call",
                            onClick = { onStartCall(CallType.VOICE) },
                            modifier = Modifier.weight(1f)
                        )
                        ContactQuickAction(
                            icon = Icons.Outlined.Videocam,
                            label = "Video",
                            accentColor = accentColor,
                            labelColor = inkPrimary,
                            tag = "contact_action_video_call",
                            onClick = { onStartCall(CallType.VIDEO) },
                            modifier = Modifier.weight(1f)
                        )
                        ContactQuickAction(
                            icon = Icons.Filled.Search,
                            label = "Buscar",
                            accentColor = accentColor,
                            labelColor = inkPrimary,
                            tag = "contact_action_search",
                            onClick = onSearchInChat,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // SHARED SECTION (Fotos N, Archivos N, Enlaces N)
                    Spacer(modifier = Modifier.height(24.dp))
                    SectionHeaderLabel(text = "COMPARTIDO", color = inkSecondary)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("contact_info_shared_tabs"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SharedFilterPill(
                            label = "Fotos ${sharedPhotos.size}",
                            selected = selectedSharedTab == ContactSharedTab.PHOTOS,
                            accentColor = accentColor,
                            inactiveBg = pillInactiveBg,
                            inactiveText = inkPrimary,
                            tag = "contact_shared_tab_photos",
                            onClick = { selectedSharedTab = ContactSharedTab.PHOTOS }
                        )
                        SharedFilterPill(
                            label = "Archivos ${sharedFiles.size}",
                            selected = selectedSharedTab == ContactSharedTab.FILES,
                            accentColor = accentColor,
                            inactiveBg = pillInactiveBg,
                            inactiveText = inkPrimary,
                            tag = "contact_shared_tab_files",
                            onClick = { selectedSharedTab = ContactSharedTab.FILES }
                        )
                        SharedFilterPill(
                            label = "Enlaces ${sharedLinks.size}",
                            selected = selectedSharedTab == ContactSharedTab.LINKS,
                            accentColor = accentColor,
                            inactiveBg = pillInactiveBg,
                            inactiveText = inkPrimary,
                            tag = "contact_shared_tab_links",
                            onClick = { selectedSharedTab = ContactSharedTab.LINKS }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    when (selectedSharedTab) {
                        ContactSharedTab.PHOTOS -> {
                            if (sharedPhotos.isEmpty()) {
                                Text(
                                    text = "Aún no se han compartido fotos en este chat.",
                                    fontSize = 13.sp,
                                    color = inkSecondary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("contact_shared_photos_empty")
                                )
                            } else {
                                val visibleThumbs = sharedPhotos.take(4)
                                val extraCount = (sharedPhotos.size - 4).coerceAtLeast(0)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("contact_shared_photos_row"),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    visibleThumbs.forEachIndexed { idx, msg ->
                                        val uri = msg.mediaUri.orEmpty()
                                        val isOverflowTile = idx == 3 && extraCount > 0
                                        Box(
                                            modifier = Modifier
                                                .size(60.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(cardBg)
                                                .clickable {
                                                    if (isOverflowTile) {
                                                        showAllPhotosSheet = true
                                                    } else {
                                                        onOpenImage(uri)
                                                    }
                                                }
                                                .testTag("contact_shared_photo_$idx")
                                        ) {
                                            AsyncImage(
                                                model = ImageCompressionHelper.resolveCoilModel(uri),
                                                contentDescription = msg.text.ifBlank { "Foto compartida" },
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            if (isOverflowTile) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(Color.Black.copy(alpha = 0.55f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "+$extraCount",
                                                        color = Color.White,
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        modifier = Modifier.testTag("contact_shared_photos_overflow")
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        ContactSharedTab.FILES -> {
                            if (sharedFiles.isEmpty()) {
                                Text(
                                    text = "Aún no se han compartido archivos en este chat.",
                                    fontSize = 13.sp,
                                    color = inkSecondary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("contact_shared_files_empty")
                                )
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = cardBg,
                                    tonalElevation = 0.dp,
                                    shadowElevation = 0.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        sharedFiles.take(10).forEachIndexed { index, docMsg ->
                                            val sizeLabel = DocumentMediaHelper.formatFileSize(docMsg.fileSizeBytes)
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .defaultMinSize(minHeight = 48.dp)
                                                    .clickable { onOpenDocument(docMsg) }
                                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Description,
                                                    contentDescription = null,
                                                    tint = accentColor,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = docMsg.fileName?.ifBlank { "Documento" } ?: "Documento",
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = inkPrimary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = "$sizeLabel · ${TimeFormatters.formatCallDateTime(docMsg.timestamp)}",
                                                        fontSize = 11.sp,
                                                        color = inkSecondary,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                            if (index < sharedFiles.take(10).lastIndex) {
                                                HorizontalDivider(
                                                    color = dividerColor,
                                                    thickness = 1.dp,
                                                    modifier = Modifier.padding(horizontal = 14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        ContactSharedTab.LINKS -> {
                            if (sharedLinks.isEmpty()) {
                                Text(
                                    text = "Aún no se han compartido enlaces en este chat.",
                                    fontSize = 13.sp,
                                    color = inkSecondary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("contact_shared_links_empty")
                                )
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = cardBg,
                                    tonalElevation = 0.dp,
                                    shadowElevation = 0.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        sharedLinks.take(10).forEachIndexed { index, (linkMsg, url) ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .defaultMinSize(minHeight = 48.dp)
                                                    .clickable {
                                                        runCatching {
                                                            context.startActivity(
                                                                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                            )
                                                        }
                                                    }
                                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Link,
                                                    contentDescription = null,
                                                    tint = accentColor,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = url,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = inkPrimary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = TimeFormatters.formatCallDateTime(linkMsg.timestamp),
                                                        fontSize = 11.sp,
                                                        color = inkSecondary,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                            if (index < sharedLinks.take(10).lastIndex) {
                                                HorizontalDivider(
                                                    color = dividerColor,
                                                    thickness = 1.dp,
                                                    modifier = Modifier.padding(horizontal = 14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // CHAT SETTINGS CARD (4 rows)
                    Spacer(modifier = Modifier.height(24.dp))
                    SectionHeaderLabel(text = "AJUSTES DEL CHAT", color = inkSecondary)
                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = cardBg,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("contact_info_settings_card")
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // 1. Fondo del chat
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 52.dp)
                                    .clickable { showWallpaperSheet = true }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                    .testTag("contact_setting_wallpaper"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Image,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(21.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Fondo del chat",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = inkPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(if (isDarkTheme) currentWallpaper.darkBg else currentWallpaper.lightBg)
                                        .border(1.dp, wallpaperBorder, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = currentWallpaper.label,
                                    fontSize = 13.sp,
                                    color = inkSecondary,
                                    modifier = Modifier.testTag("contact_setting_wallpaper_value")
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = inkSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            HorizontalDivider(
                                color = dividerColor,
                                thickness = 1.dp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )

                            // 2. Tono de notificación
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 52.dp)
                                    .clickable {
                                        val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                                        val existingUri = selectedNotificationToneUri
                                            .takeIf { it.isNotBlank() }
                                            ?.let { runCatching { Uri.parse(it) }.getOrNull() }
                                            ?: defaultUri
                                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Tono de notificación")
                                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                            putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, defaultUri)
                                            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existingUri)
                                        }
                                        val canResolve = runCatching {
                                            intent.resolveActivity(context.packageManager) != null
                                        }.getOrDefault(false)
                                        if (canResolve) {
                                            ringtonePickerLauncher.launch(intent)
                                        } else {
                                            showFallbackToneSheet = true
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                    .testTag("contact_setting_notification_tone"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.NotificationsActive,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(21.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Tono de notificación",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = inkPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = if (selectedNotificationToneUri.isBlank()) "Predeterminado" else "Personalizado",
                                    fontSize = 13.sp,
                                    color = inkSecondary,
                                    modifier = Modifier.testTag("contact_setting_tone_value")
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = inkSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            HorizontalDivider(
                                color = dividerColor,
                                thickness = 1.dp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )

                            // 3. Fijar chat arriba
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 52.dp)
                                    .clickable { onTogglePinChat() }
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .testTag("contact_setting_pin_chat"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.PushPin,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(21.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Fijar chat arriba",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = inkPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(
                                    checked = chatUi.isPinned,
                                    onCheckedChange = { onTogglePinChat() },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = accentColor,
                                        uncheckedThumbColor = Color.White,
                                        uncheckedTrackColor = wallpaperBorder,
                                        uncheckedBorderColor = Color.Transparent
                                    ),
                                    modifier = Modifier.testTag("contact_setting_pin_switch")
                                )
                            }

                            HorizontalDivider(
                                color = dividerColor,
                                thickness = 1.dp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )

                            // 4. Silenciar notificaciones
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 52.dp)
                                    .clickable { onToggleMuteChat() }
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .testTag("contact_setting_mute_chat"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.NotificationsOff,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(21.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Silenciar notificaciones",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = inkPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(
                                    checked = chatUi.isMuted,
                                    onCheckedChange = { onToggleMuteChat() },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = accentColor,
                                        uncheckedThumbColor = Color.White,
                                        uncheckedTrackColor = wallpaperBorder,
                                        uncheckedBorderColor = Color.Transparent
                                    ),
                                    modifier = Modifier.testTag("contact_setting_mute_switch")
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 3-column Wallpaper Bottom Sheet
    if (showWallpaperSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showWallpaperSheet = false },
            sheetState = sheetState,
            containerColor = sheetBg,
            tonalElevation = 0.dp,
            modifier = Modifier.testTag("contact_wallpaper_sheet")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text(
                    text = "Fondo del chat",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = inkPrimary
                )
                val rows = remember { ChatWallpaperPalette.options.chunked(3) }
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    rows.forEach { rowOptions ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowOptions.forEach { opt ->
                                val selected = selectedWallpaperKey == opt.key ||
                                    (selectedWallpaperKey.isBlank() && opt.key == "default")
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            onSelectWallpaper(opt.key)
                                            showWallpaperSheet = false
                                        }
                                        .padding(vertical = 6.dp)
                                        .testTag("contact_wallpaper_option_${opt.key}")
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(if (isDarkTheme) opt.darkBg else opt.lightBg)
                                            .border(
                                                width = if (selected) 2.5.dp else 1.5.dp,
                                                color = if (selected) accentColor else wallpaperBorder,
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (selected) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = "Seleccionado",
                                                tint = accentColor,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = opt.label,
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (selected) accentColor else inkPrimary,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            repeat(3 - rowOptions.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }

    // Full shared photos sheet when tapping "+N"
    if (showAllPhotosSheet && sharedPhotos.isNotEmpty()) {
        val photosSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showAllPhotosSheet = false },
            sheetState = photosSheetState,
            containerColor = sheetBg,
            tonalElevation = 0.dp,
            modifier = Modifier.testTag("contact_all_photos_sheet")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Fotos compartidas (${sharedPhotos.size})",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = inkPrimary
                )
                val photoRows = remember(sharedPhotos) { sharedPhotos.chunked(3) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    photoRows.forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { msg ->
                                val uri = msg.mediaUri.orEmpty()
                                AsyncImage(
                                    model = ImageCompressionHelper.resolveCoilModel(uri),
                                    contentDescription = msg.text.ifBlank { "Foto compartida" },
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            showAllPhotosSheet = false
                                            onOpenImage(uri)
                                        }
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

    // Fallback tone picker sheet if device doesn't expose system RingtoneManager picker UI
    if (showFallbackToneSheet) {
        val toneSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val availableTones = remember(context) { queryDeviceNotificationTones(context) }
        ModalBottomSheet(
            onDismissRequest = { showFallbackToneSheet = false },
            sheetState = toneSheetState,
            containerColor = sheetBg,
            tonalElevation = 0.dp,
            modifier = Modifier.testTag("contact_tone_fallback_sheet")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Tono de notificación",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = inkPrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                ToneOptionRow(
                    title = "Predeterminado",
                    selected = selectedNotificationToneUri.isBlank(),
                    accentColor = accentColor,
                    defaultTextColor = inkPrimary,
                    onClick = {
                        onSelectNotificationTone("")
                        showFallbackToneSheet = false
                    }
                )
                availableTones.forEach { (title, uriStr) ->
                    HorizontalDivider(color = dividerColor, thickness = 1.dp)
                    ToneOptionRow(
                        title = title,
                        selected = selectedNotificationToneUri == uriStr,
                        accentColor = accentColor,
                        defaultTextColor = inkPrimary,
                        onClick = {
                            onSelectNotificationTone(uriStr)
                            showFallbackToneSheet = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeaderLabel(text: String, color: Color) {
    Text(
        text = text,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp,
        color = color,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ContactQuickAction(
    icon: ImageVector,
    label: String,
    accentColor: Color,
    labelColor: Color,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .testTag(tag)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accentColor,
                modifier = Modifier.size(21.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SharedFilterPill(
    label: String,
    selected: Boolean,
    accentColor: Color,
    inactiveBg: Color,
    inactiveText: Color,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) accentColor else inactiveBg,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                color = if (selected) Color.White else inactiveText,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ToneOptionRow(
    title: String,
    selected: Boolean,
    accentColor: Color,
    defaultTextColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) accentColor else defaultTextColor,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Seleccionado",
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

internal fun resolveContactStatusPill(member: FamilyMemberEntity?): String? {
    if (member == null) return null
    val raw = member.lastSeenText.trim()
    val candidate = if (raw.contains(" · ")) {
        raw.substringBefore(" · ").trim()
    } else {
        raw
    }
    val nonStatusLabels = setOf("", "Desconectado", "No se ha conectado")
    if (candidate !in nonStatusLabels && !candidate.startsWith("Últ.", ignoreCase = true)) {
        return candidate
    }
    return if (member.isOnline) "En línea" else null
}

internal fun resolveContactLastSeen(
    member: FamilyMemberEntity?,
    fallbackSubtitle: String,
    nowMs: Long = System.currentTimeMillis()
): String {
    if (member == null) return fallbackSubtitle.trim()
    if (!member.showLastSeen) return ""
    if (member.isOnline) return "En línea"
    if (member.liveStatusAt > 0L) {
        return TimeFormatters.formatLastSeen(member.liveStatusAt, nowMs)
    }
    val raw = member.lastSeenText.trim()
    val ultIdx = raw.indexOf("Últ.", ignoreCase = true)
    if (ultIdx >= 0) {
        return raw.substring(ultIdx).trim()
    }
    if (!PresenceResolver.hasEverConnected(member)) {
        return "No se ha conectado"
    }
    return "Desconectado"
}

private fun queryDeviceNotificationTones(context: Context): List<Pair<String, String>> {
    return runCatching {
        val manager = RingtoneManager(context).apply {
            setType(RingtoneManager.TYPE_NOTIFICATION)
        }
        val cursor = manager.cursor ?: return emptyList()
        val result = mutableListOf<Pair<String, String>>()
        cursor.use { c ->
            while (c.moveToNext() && result.size < 15) {
                val title = c.getString(RingtoneManager.TITLE_COLUMN_INDEX).orEmpty().trim()
                val uri = manager.getRingtoneUri(c.position)?.toString().orEmpty()
                if (title.isNotEmpty() && uri.isNotEmpty()) {
                    result.add(title to uri)
                }
            }
        }
        result
    }.getOrDefault(emptyList())
}
