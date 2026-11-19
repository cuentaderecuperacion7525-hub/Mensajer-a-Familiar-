package com.example

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.local.PreferencesManager
import com.example.data.model.CallType
import com.example.data.model.ChatType
import com.example.data.model.FamilyMemberEntity
import com.example.data.model.PresenceStatus
import com.example.data.remote.FamilyCloudRepository
import com.example.data.remote.GoogleAuthHelper
import com.example.data.remote.authStateFlow
import com.example.ui.components.PinVerificationDialog
import com.example.ui.screens.AdminAdvancedSettingsScreen
import com.example.ui.screens.AdminDiagnosticsScreen
import com.example.ui.screens.AdminPanelScreen
import com.example.ui.screens.CallOverlayScreen
import com.example.ui.screens.ChatRoomScreen
import com.example.ui.screens.FamilySetupScreen
import com.example.ui.screens.IncomingCallOverlay
import com.example.ui.screens.MainHomeScreen
import com.example.ui.screens.NotificationSettingsScreen
import com.example.ui.screens.ProfileSelectorScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.FamiChatTheme
import com.example.ui.viewmodel.FamilyViewModel
import com.example.ui.viewmodel.MainTab
import com.example.util.FamiChatNotificationHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

object Routes {
    const val SELECTOR = "selector"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val NOTIFICATIONS = "notifications"
    const val ADMIN_ADVANCED = "admin_advanced"
    const val ADMIN_DIAGNOSTICS = "admin_diagnostics"
    const val ADMIN = "admin"
    const val CHAT = "chat/{chatId}"
    fun chat(chatId: String) = "chat/$chatId"
}

class MainActivity : ComponentActivity() {
    private var pendingNotifChatId by mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val chatId = intent.getStringExtra(FamiChatNotificationHelper.EXTRA_OPEN_CHAT_ID)
        if (!chatId.isNullOrBlank()) {
            pendingNotifChatId = chatId
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingNotifChatId = intent?.getStringExtra(FamiChatNotificationHelper.EXTRA_OPEN_CHAT_ID)
        setContent {
            val context = LocalContext.current
            val appContext = context.applicationContext
            val prefs = remember { PreferencesManager(appContext) }
            val scope = rememberCoroutineScope()
            val isDarkTheme by prefs.isDarkThemeFlow.collectAsStateWithLifecycle(initialValue = false)

            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as? Activity)?.window ?: return@SideEffect
                    val controller = WindowCompat.getInsetsController(window, view)
                    controller.isAppearanceLightStatusBars = !isDarkTheme
                    controller.isAppearanceLightNavigationBars = !isDarkTheme
                }
            }

            val currentUser by remember { Firebase.auth.authStateFlow() }
                .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

            FamiChatTheme(
                darkTheme = isDarkTheme,
                onToggleTheme = {
                    scope.launch {
                        val current = prefs.isDarkThemeFlow.first()
                        prefs.setDarkTheme(!current)
                    }
                }
            ) {
                val signedInUser = currentUser
                val savedMemberState by produceState<Pair<Boolean, String?>>(initialValue = false to null, prefs) {
                    val id = runCatching { prefs.activeMemberIdFlow.first() }.getOrNull()
                    value = true to id
                }
                if (signedInUser == null) {
                    var authError by remember { mutableStateOf<String?>(null) }
                    var retryTick by remember { mutableStateOf(0) }

                    LaunchedEffect(retryTick) {
                        authError = null
                        try {
                            Firebase.auth.signInAnonymously().await()
                        } catch (e: Exception) {
                            authError = e.localizedMessage ?: "No se pudo conectar"
                        }
                    }

                    val (prefsLoaded, savedMemberId) = savedMemberState
                    if (prefsLoaded && savedMemberId == null) {
                        ProfileSelectorScreen(
                            members = emptyList(),
                            isMembersLoading = authError == null,
                            snackbarMessage = authError,
                            onClearSnackbar = { authError = null },
                            onSelectMember = {},
                            onRetryLoad = { retryTick++ }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                            contentAlignment = Alignment.Center
                        ) {
                            if (authError == null) {
                                CircularProgressIndicator()
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = authError ?: "Error de conexión",
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Button(onClick = { retryTick++ }) {
                                        Text("Reintentar")
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val vm: FamilyViewModel = viewModel(
                        key = "family_vm_${signedInUser.uid}",
                        factory = remember(signedInUser.uid) {
                            object : ViewModelProvider.Factory {
                                @Suppress("UNCHECKED_CAST")
                                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                    return FamilyViewModel(
                                        appContext = appContext,
                                        cloudRepo = FamilyCloudRepository(appContext),
                                        prefs = prefs,
                                        currentUserId = signedInUser.uid
                                    ) as T
                                }
                            }
                        }
                    )
                    FamiChatApp(
                        vm = vm,
                        userEmail = signedInUser.email.orEmpty(),
                        userName = signedInUser.displayName.orEmpty(),
                        isDarkTheme = isDarkTheme,
                        externalOpenChatId = pendingNotifChatId,
                        onConsumeExternalOpenChatId = { pendingNotifChatId = null },
                        onSignOutGoogle = {
                            scope.launch {
                                prefs.setActiveMemberId(null)
                                prefs.setActiveFamilyId(null)
                                GoogleAuthHelper.signOut(appContext)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FamiChatApp(
    vm: FamilyViewModel,
    userEmail: String,
    userName: String,
    isDarkTheme: Boolean,
    externalOpenChatId: String?,
    onConsumeExternalOpenChatId: () -> Unit,
    onSignOutGoogle: () -> Unit
) {
    LaunchedEffect(externalOpenChatId) {
        if (!externalOpenChatId.isNullOrBlank()) {
            vm.requestOpenChatFromNotification(externalOpenChatId)
            onConsumeExternalOpenChatId()
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, vm) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> vm.setAppForeground(true)
                Lifecycle.Event.ON_STOP -> vm.setAppForeground(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            vm.setAppForeground(false)
        }
    }

    val isSessionReady by vm.isSessionReady.collectAsStateWithLifecycle()
    val showFamilySpacesSetup by vm.showFamilySpacesSetup.collectAsStateWithLifecycle()
    val myFamilies by vm.myFamilies.collectAsStateWithLifecycle()
    val activeFamily by vm.activeFamily.collectAsStateWithLifecycle()
    val activeFamilyId by vm.activeFamilyId.collectAsStateWithLifecycle()
    val isFamilyActionBusy by vm.isFamilyActionBusy.collectAsStateWithLifecycle()
    val isMembersLoading by vm.isMembersLoading.collectAsStateWithLifecycle()
    val members by vm.members.collectAsStateWithLifecycle()
    val activeMember by vm.activeMember.collectAsStateWithLifecycle()

    if (!isSessionReady) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    if (showFamilySpacesSetup) {
        FamilySetupScreen(
            userEmail = userEmail,
            userName = userName,
            families = myFamilies,
            isBusy = isFamilyActionBusy,
            isDarkTheme = isDarkTheme,
            onToggleTheme = vm::toggleTheme,
            onSelectFamily = vm::selectFamilySpace,
            onCreateFamily = vm::createNewFamily,
            onJoinFamily = vm::joinFamilyWithCode,
            onSignOutGoogle = onSignOutGoogle
        )
        return
    }

    val navController = rememberNavController()
    val initialRoute = remember { if (activeMember != null) Routes.HOME else Routes.SELECTOR }
    val selectedTab by vm.selectedTab.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val chatPreviews by vm.chatPreviews.collectAsStateWithLifecycle()
    val rawChats by vm.rawChats.collectAsStateWithLifecycle()
    val allMessages by vm.allMessages.collectAsStateWithLifecycle()
    val statuses by vm.statuses.collectAsStateWithLifecycle()
    val calls by vm.calls.collectAsStateWithLifecycle()
    val shoppingItems by vm.shoppingItems.collectAsStateWithLifecycle()
    val events by vm.events.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    val masterPin by vm.masterPin.collectAsStateWithLifecycle()
    val switchPin by vm.switchPin.collectAsStateWithLifecycle()
    val notificationsEnabled by vm.notificationsEnabled.collectAsStateWithLifecycle()
    val chatWallpapers by vm.chatWallpapers.collectAsStateWithLifecycle()
    val chatNotificationTones by vm.chatNotificationTones.collectAsStateWithLifecycle()
    val pendingNotificationChatId by vm.pendingNotificationChatId.collectAsStateWithLifecycle()
    val updateRepoSlug by vm.updateRepoSlug.collectAsStateWithLifecycle()
    val updateState by vm.updateState.collectAsStateWithLifecycle()
    val activeCall by vm.activeCall.collectAsStateWithLifecycle()
    val incomingCall by vm.incomingCall.collectAsStateWithLifecycle()
    val voiceRecording by vm.voiceRecording.collectAsStateWithLifecycle()
    val playingVoiceId by vm.playingVoiceMessageId.collectAsStateWithLifecycle()
    val playingVoiceProgress by vm.playingVoiceProgress.collectAsStateWithLifecycle()
    val voicePlaybackSpeed by vm.voicePlaybackSpeed.collectAsStateWithLifecycle()
    val mediaTransferProgress by vm.mediaTransferProgress.collectAsStateWithLifecycle()
    val activeUploadLabel by vm.activeUploadLabel.collectAsStateWithLifecycle()
    val activeUploadProgress by vm.activeUploadProgress.collectAsStateWithLifecycle()
    val fcmToken by vm.fcmToken.collectAsStateWithLifecycle()
    val turnUrl by vm.turnUrl.collectAsStateWithLifecycle()
    val turnUsername by vm.turnUsername.collectAsStateWithLifecycle()
    val turnCredential by vm.turnCredential.collectAsStateWithLifecycle()
    val snackbarMessage by vm.snackbarMessage.collectAsStateWithLifecycle()

    var pendingProtectedMember by remember { mutableStateOf<FamilyMemberEntity?>(null) }
    var showSwitchKeyDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    var pendingCallAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val callPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { _ ->
            pendingCallAction?.invoke()
            pendingCallAction = null
        }
    )

    fun runWithCallPermissions(callType: CallType, action: () -> Unit) {
        val needed = buildList {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.RECORD_AUDIO)
            }
            if (callType == CallType.VIDEO &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.CAMERA)
            }
        }
        if (needed.isEmpty()) {
            action()
        } else {
            pendingCallAction = action
            callPermissionsLauncher.launch(needed.toTypedArray())
        }
    }

    fun navigateSingleTop(route: String) {
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        if (currentRoute == route) return
        navController.navigate(route) {
            launchSingleTop = true
        }
    }

    fun navigateToChatSingleTop(chatId: String) {
        val currentEntry = navController.currentBackStackEntry
        val isSameChat = currentEntry?.destination?.route == Routes.CHAT &&
            currentEntry.arguments?.getString("chatId") == chatId
        if (isSameChat) return
        navController.navigate(Routes.chat(chatId)) {
            launchSingleTop = true
        }
    }

    fun safePopBack(expectedRoute: String) {
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        if (currentRoute == expectedRoute && navController.previousBackStackEntry != null) {
            navController.popBackStack()
        }
    }

    LaunchedEffect(pendingNotificationChatId, activeMember?.id, chatPreviews.size) {
        val targetChatId = pendingNotificationChatId ?: return@LaunchedEffect
        if (activeMember != null && chatPreviews.any { it.chat.id == targetChatId }) {
            vm.selectTab(MainTab.CHATS)
            vm.openChat(targetChatId)
            navController.navigate(Routes.chat(targetChatId)) {
                popUpTo(Routes.HOME) { inclusive = false }
                launchSingleTop = true
            }
            vm.consumePendingNotificationChatId()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = initialRoute
        ) {
            composable(Routes.SELECTOR) {
                ProfileSelectorScreen(
                    members = members,
                    isMembersLoading = isMembersLoading,
                    snackbarMessage = snackbarMessage,
                    onClearSnackbar = vm::clearSnackbar,
                    onSelectMember = { member ->
                        if (member.isAdmin) {
                            pendingProtectedMember = member
                        } else {
                            vm.selectProfile(member.id)
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.SELECTOR) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    },
                    onRetryLoad = vm::retryLoadProfiles
                )
            }

            composable(Routes.HOME) {
                val member = activeMember
                if (member == null) {
                    LaunchedEffect(Unit) {
                        navController.navigate(Routes.SELECTOR) {
                            popUpTo(Routes.HOME) { inclusive = true }
                        }
                    }
                } else {
                    val userGroupsCount = remember(chatPreviews) {
                        chatPreviews.count { it.chat.type == ChatType.GROUP }
                    }
                    MainHomeScreen(
                        activeMember = member,
                        allMembers = members,
                        userGroupsCount = userGroupsCount,
                        isDarkTheme = isDarkTheme,
                        notificationsEnabled = notificationsEnabled,
                        updateRepoSlug = updateRepoSlug,
                        updateState = updateState,
                        selectedTab = selectedTab,
                        searchQuery = searchQuery,
                        chatPreviews = chatPreviews,
                        statuses = statuses,
                        calls = calls,
                        shoppingItems = shoppingItems,
                        events = events,
                        notes = notes,
                        snackbarMessage = snackbarMessage,
                        onClearSnackbar = vm::clearSnackbar,
                        onToggleTheme = vm::toggleTheme,
                        onToggleNotifications = vm::setNotificationsEnabled,
                        onUpdateOwnProfile = vm::updateOwnProfileDetails,
                        onPublishTextStatus = vm::publishTextStatus,
                        onPublishPhotoStatus = vm::publishPhotoStatus,
                        onPublishVideoStatus = vm::publishVideoStatus,
                        onPrepareStatusVideo = vm::prepareStatusVideo,
                        onSaveStatusMediaToGallery = vm::saveStatusMediaToGallery,
                        mediaTransferProgress = mediaTransferProgress,
                        onMarkStatusViewed = vm::markStatusViewed,
                        onDeleteStatus = vm::deleteStatus,
                        onReplyToStatus = { st, replyText ->
                            vm.replyToStatus(st, replyText) { chatId ->
                                navigateToChatSingleTop(chatId)
                            }
                        },
                        onCheckForUpdate = vm::checkForAppUpdate,
                        onSaveUpdateRepoSlug = { vm.saveUpdateRepoSlug(it, triggerCheck = true) },
                        onDownloadUpdate = vm::downloadAndInstallUpdate,
                        onResetUpdateState = vm::resetUpdateState,
                        onSelectTab = vm::selectTab,
                        onSearchChange = vm::updateSearchQuery,
                        onOpenChat = { chatId ->
                            vm.openChat(chatId)
                            navigateToChatSingleTop(chatId)
                        },
                        onTogglePinChat = vm::togglePinChat,
                        onToggleMuteChat = vm::toggleMuteChat,
                        onStartDirectChat = { otherId ->
                            vm.createOrOpenDirectChat(otherId) { chatId ->
                                navigateToChatSingleTop(chatId)
                            }
                        },
                        onSwitchProfile = {
                            if (member.isAdmin) {
                                vm.logoutToSelector()
                                navController.navigate(Routes.SELECTOR) {
                                    popUpTo(0) { inclusive = true }
                                }
                            } else {
                                showSwitchKeyDialog = true
                            }
                        },
                        onOpenAdminPanel = {
                            if (member.isAdmin) {
                                navigateSingleTop(Routes.ADMIN)
                            }
                        },
                        onStartCall = { chatId, title, emoji, colorHex, callType ->
                            runWithCallPermissions(callType) {
                                vm.startCall(chatId, title, emoji, colorHex, callType)
                            }
                        },
                        onAddShoppingItem = vm::addShoppingItem,
                        onToggleShoppingItem = vm::toggleShoppingItem,
                        onDeleteShoppingItem = vm::deleteShoppingItem,
                        onClearCheckedShopping = vm::clearCheckedShoppingItems,
                        onAddEvent = vm::addFamilyEvent,
                        onDeleteEvent = vm::deleteFamilyEvent,
                        onSaveNote = vm::saveFamilyNote,
                        onDeleteNote = vm::deleteFamilyNote,
                        onOpenSettings = { navigateSingleTop(Routes.SETTINGS) },
                        onOpenNotifications = { navigateSingleTop(Routes.NOTIFICATIONS) }
                    )
                }
            }

            composable(Routes.SETTINGS) {
                val member = activeMember
                if (member == null) {
                    LaunchedEffect(Unit) { safePopBack(Routes.SETTINGS) }
                } else {
                    SettingsScreen(
                        isAdmin = member.isAdmin,
                        isDarkTheme = isDarkTheme,
                        notificationsEnabled = notificationsEnabled,
                        updateRepoSlug = updateRepoSlug,
                        updateState = updateState,
                        onBack = { safePopBack(Routes.SETTINGS) },
                        onOpenNotifications = { navigateSingleTop(Routes.NOTIFICATIONS) },
                        onToggleTheme = vm::toggleTheme,
                        onCheckForUpdate = vm::checkForAppUpdate,
                        onSaveUpdateRepoSlug = { vm.saveUpdateRepoSlug(it, triggerCheck = true) },
                        onDownloadUpdate = vm::downloadAndInstallUpdate,
                        onResetUpdateState = vm::resetUpdateState,
                        onOpenAdvanced = {
                            if (member.isAdmin) {
                                navigateSingleTop(Routes.ADMIN_ADVANCED)
                            }
                        }
                    )
                }
            }

            composable(Routes.NOTIFICATIONS) {
                NotificationSettingsScreen(
                    notificationsEnabled = notificationsEnabled,
                    snackbarMessage = snackbarMessage,
                    onClearSnackbar = vm::clearSnackbar,
                    onToggleNotifications = vm::setNotificationsEnabled,
                    onSendTestNotification = vm::sendTestPushNotification,
                    onBack = { safePopBack(Routes.NOTIFICATIONS) }
                )
            }

            composable(Routes.ADMIN_ADVANCED) {
                val isAdmin = activeMember?.isAdmin == true
                AdminAdvancedSettingsScreen(
                    isAdmin = isAdmin,
                    onBack = { safePopBack(Routes.ADMIN_ADVANCED) },
                    onOpenDiagnostics = {
                        if (isAdmin) {
                            navigateSingleTop(Routes.ADMIN_DIAGNOSTICS)
                        }
                    }
                )
            }

            composable(Routes.ADMIN_DIAGNOSTICS) {
                val member = activeMember
                AdminDiagnosticsScreen(
                    isAdmin = member?.isAdmin == true,
                    familyId = activeFamilyId.orEmpty(),
                    memberName = member?.name.orEmpty(),
                    notificationsEnabled = notificationsEnabled,
                    fcmToken = fcmToken.orEmpty(),
                    turnUrl = turnUrl,
                    turnUsername = turnUsername,
                    turnCredential = turnCredential,
                    snackbarMessage = snackbarMessage,
                    onClearSnackbar = vm::clearSnackbar,
                    onSendTestNotification = vm::sendTestPushNotification,
                    onSaveTurnConfig = vm::saveTurnConfig,
                    onBack = { safePopBack(Routes.ADMIN_DIAGNOSTICS) }
                )
            }

            composable(
                route = Routes.CHAT,
                arguments = listOf(navArgument("chatId") { type = NavType.StringType })
            ) { backStackEntry ->
                val chatId = backStackEntry.arguments?.getString("chatId") ?: return@composable
                val member = activeMember
                val chatUi = chatPreviews.find { it.chat.id == chatId }
                if (member == null || chatUi == null) {
                    LaunchedEffect(Unit) { safePopBack(Routes.CHAT) }
                } else {
                    val chatMessages = remember(allMessages, chatId) {
                        allMessages.filter { it.chatId == chatId }
                    }
                    LaunchedEffect(chatMessages.size, member.id) {
                        vm.markChatMessagesAsRead(chatId)
                    }
                    ChatRoomScreen(
                        chatUi = chatUi,
                        activeMember = member,
                        allMembers = members,
                        allChatPreviews = chatPreviews,
                        calls = calls,
                        selectedWallpaperKey = chatWallpapers[chatId] ?: "default",
                        selectedNotificationToneUri = chatNotificationTones[chatId].orEmpty(),
                        messages = chatMessages,
                        voiceRecording = voiceRecording,
                        playingVoiceMessageId = playingVoiceId,
                        playingVoiceProgress = playingVoiceProgress,
                        snackbarMessage = snackbarMessage,
                        onClearSnackbar = vm::clearSnackbar,
                        onBack = {
                            vm.closeChat()
                            safePopBack(Routes.CHAT)
                        },
                        onSendText = { text, replyId -> vm.sendTextMessage(chatId, text, replyId) },
                        onSendImage = { uri, caption, replyId -> vm.sendImageMessage(chatId, uri, caption, replyId) },
                        onSendLocation = { name, addr, lat, lng, replyId, liveMins ->
                            vm.sendLocationMessage(chatId, name, addr, lat, lng, replyId, liveMins)
                        },
                        onSendDocument = { uri, replyId -> vm.sendDocumentMessage(chatId, uri, replyId) },
                        onSendVideo = { uri, caption, replyId -> vm.sendVideoMessage(chatId, uri, caption, replyId) },
                        onOpenDocument = vm::openOrDownloadDocument,
                        onOpenVideo = vm::openOrDownloadVideo,
                        voicePlaybackSpeed = voicePlaybackSpeed,
                        onCycleVoiceSpeed = vm::cycleVoicePlaybackSpeed,
                        mediaTransferProgress = mediaTransferProgress,
                        activeUploadLabel = activeUploadLabel,
                        activeUploadProgress = activeUploadProgress,
                        onStopLiveLocation = vm::stopLiveLocationSharing,
                        onSendPoll = { q, opts -> vm.sendPollMessage(chatId, q, opts) },
                        onVotePoll = vm::voteOnPoll,
                        onEditMessage = vm::editMessage,
                        onForwardMessage = vm::forwardMessage,
                        onSelectWallpaper = { key -> vm.setChatWallpaper(chatId, key) },
                        onSelectNotificationTone = { uri -> vm.setChatNotificationTone(chatId, uri) },
                        onToggleReaction = vm::toggleReaction,
                        onTogglePin = { msgId -> vm.togglePinMessage(chatId, msgId) },
                        onTogglePinChat = { vm.togglePinChat(chatId) },
                        onToggleMuteChat = { vm.toggleMuteChat(chatId) },
                        onDeleteForMe = vm::deleteMessagesForMe,
                        onDeleteForEveryone = vm::deleteMessagesForEveryone,
                        onStartVoiceRecording = { vm.startVoiceRecording(chatId) },
                        onCancelVoiceRecording = vm::cancelVoiceRecording,
                        onSendVoiceRecording = { replyId -> vm.finishAndSendVoiceRecording(chatId, replyId) },
                        onToggleVoicePlay = vm::toggleVoicePlayback,
                        onStartCall = { callType ->
                            runWithCallPermissions(callType) {
                                vm.startCall(
                                    chatId = chatId,
                                    title = chatUi.displayTitle,
                                    avatarEmoji = chatUi.displayEmoji,
                                    avatarColorHex = chatUi.displayColorHex,
                                    callType = callType
                                )
                            }
                        },
                        onTypingChange = { isTyping ->
                            vm.updateLivePresence(
                                chatId = chatId,
                                status = if (isTyping) PresenceStatus.TYPING else PresenceStatus.ONLINE,
                                forceClear = !isTyping
                            )
                        },
                        onToggleTheme = vm::toggleTheme,
                        onGoToHomePanel = {
                            vm.closeChat()
                            if (!navController.popBackStack(Routes.HOME, inclusive = false)) {
                                navController.navigate(Routes.HOME) { launchSingleTop = true }
                            }
                        },
                        onSwitchProfile = {
                            if (member.isAdmin) {
                                vm.closeChat()
                                vm.logoutToSelector()
                                navController.navigate(Routes.SELECTOR) {
                                    popUpTo(0) { inclusive = true }
                                }
                            } else {
                                showSwitchKeyDialog = true
                            }
                        }
                    )
                }
            }

            composable(Routes.ADMIN) {
                AdminPanelScreen(
                    members = members,
                    chats = rawChats,
                    currentMemberId = activeMember?.id,
                    familyName = activeFamily?.name.orEmpty(),
                    inviteCode = activeFamily?.inviteCode.orEmpty(),
                    currentMasterPin = masterPin,
                    currentSwitchPin = switchPin,
                    currentUpdateRepo = updateRepoSlug,
                    onBack = { safePopBack(Routes.ADMIN) },
                    onSaveMember = vm::saveFamilyMember,
                    onDeleteMember = vm::deleteFamilyMember,
                    onSaveGroup = vm::saveGroup,
                    onDeleteGroup = vm::deleteGroup,
                    onUpdateMasterPin = vm::updateMasterPin,
                    onUpdateSwitchPin = vm::updateSwitchPin,
                    onUpdateRepoSlug = { vm.saveUpdateRepoSlug(it, triggerCheck = false) },
                    onChangeFamilySpace = vm::clearActiveFamilySpace
                )
            }
        }

        pendingProtectedMember?.let { protectedMember ->
            PinVerificationDialog(
                title = "Contraseña de ${protectedMember.name}",
                subtitle = "Ingresa el PIN de ${masterPin.length} dígitos para entrar",
                pinLength = masterPin.length,
                onVerify = vm::verifyPin,
                onSuccess = {
                    pendingProtectedMember = null
                    vm.selectProfile(protectedMember.id)
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.SELECTOR) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onDismiss = {
                    pendingProtectedMember = null
                }
            )
        }

        if (showSwitchKeyDialog) {
            PinVerificationDialog(
                title = "Cambiar de perfil (Switch)",
                subtitle = "Ingresa la clave de ${switchPin.length} dígitos autorizada por el administrador",
                pinLength = switchPin.length,
                onVerify = vm::verifySwitchKey,
                onSuccess = {
                    showSwitchKeyDialog = false
                    vm.closeChat()
                    vm.logoutToSelector()
                    navController.navigate(Routes.SELECTOR) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onDismiss = {
                    showSwitchKeyDialog = false
                }
            )
        }

        activeCall?.let { call ->
            CallOverlayScreen(
                callState = call,
                webRtcCallManager = vm.webRtcCallManager,
                onToggleMute = vm::toggleCallMute,
                onToggleSpeaker = vm::toggleCallSpeaker,
                onToggleCamera = vm::toggleCallCamera,
                onSwitchCamera = vm::switchCallCamera,
                onEndCall = vm::endActiveCall
            )
        }

        incomingCall?.let { call ->
            val caller = members.find { it.id == call.callerId }
            IncomingCallOverlay(
                call = call,
                callerMember = caller,
                onAccept = {
                    runWithCallPermissions(call.callType) {
                        vm.acceptIncomingCall(call)
                    }
                },
                onDecline = { vm.declineIncomingCall(call) }
            )
        }
    }
}
