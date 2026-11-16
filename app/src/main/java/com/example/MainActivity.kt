package com.example

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.local.PreferencesManager
import com.example.data.model.ChatType
import com.example.data.model.FamilyMemberEntity
import com.example.data.model.PresenceStatus
import com.example.data.remote.FamilyCloudRepository
import com.example.data.remote.GoogleAuthHelper
import com.example.data.remote.authStateFlow
import com.example.ui.components.PinVerificationDialog
import com.example.ui.screens.AdminPanelScreen
import com.example.ui.screens.CallOverlayScreen
import com.example.ui.screens.ChatRoomScreen
import com.example.ui.screens.FamilySetupScreen
import com.example.ui.screens.IncomingCallOverlay
import com.example.ui.screens.MainHomeScreen
import com.example.ui.screens.ProfileSelectorScreen
import com.example.ui.theme.NidoFamiliarTheme
import com.example.ui.viewmodel.FamilyViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

object Routes {
    const val SELECTOR = "selector"
    const val HOME = "home"
    const val ADMIN = "admin"
    const val CHAT = "chat/{chatId}"
    fun chat(chatId: String) = "chat/$chatId"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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

            NidoFamiliarTheme(
                darkTheme = isDarkTheme,
                onToggleTheme = {
                    scope.launch {
                        val current = prefs.isDarkThemeFlow.first()
                        prefs.setDarkTheme(!current)
                    }
                }
            ) {
                val signedInUser = currentUser
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
                    NidoFamiliarApp(
                        vm = vm,
                        userEmail = signedInUser.email.orEmpty(),
                        userName = signedInUser.displayName.orEmpty(),
                        isDarkTheme = isDarkTheme,
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
fun NidoFamiliarApp(
    vm: FamilyViewModel,
    userEmail: String,
    userName: String,
    isDarkTheme: Boolean,
    onSignOutGoogle: () -> Unit
) {
    val isSessionReady by vm.isSessionReady.collectAsStateWithLifecycle()
    val myFamilies by vm.myFamilies.collectAsStateWithLifecycle()
    val activeFamily by vm.activeFamily.collectAsStateWithLifecycle()
    val activeFamilyId by vm.activeFamilyId.collectAsStateWithLifecycle()
    val isFamilyActionBusy by vm.isFamilyActionBusy.collectAsStateWithLifecycle()
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

    if (activeFamilyId == null) {
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
    val initialRoute = remember(activeFamilyId) { if (activeMember != null) Routes.HOME else Routes.SELECTOR }
    val selectedTab by vm.selectedTab.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val chatPreviews by vm.chatPreviews.collectAsStateWithLifecycle()
    val rawChats by vm.rawChats.collectAsStateWithLifecycle()
    val allMessages by vm.allMessages.collectAsStateWithLifecycle()
    val calls by vm.calls.collectAsStateWithLifecycle()
    val shoppingItems by vm.shoppingItems.collectAsStateWithLifecycle()
    val events by vm.events.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    val masterPin by vm.masterPin.collectAsStateWithLifecycle()
    val switchPin by vm.switchPin.collectAsStateWithLifecycle()
    val updateRepoSlug by vm.updateRepoSlug.collectAsStateWithLifecycle()
    val updateState by vm.updateState.collectAsStateWithLifecycle()
    val activeCall by vm.activeCall.collectAsStateWithLifecycle()
    val incomingCall by vm.incomingCall.collectAsStateWithLifecycle()
    val voiceRecording by vm.voiceRecording.collectAsStateWithLifecycle()
    val playingVoiceId by vm.playingVoiceMessageId.collectAsStateWithLifecycle()
    val playingVoiceProgress by vm.playingVoiceProgress.collectAsStateWithLifecycle()
    val snackbarMessage by vm.snackbarMessage.collectAsStateWithLifecycle()

    var pendingProtectedMember by remember { mutableStateOf<FamilyMemberEntity?>(null) }
    var showSwitchKeyDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = initialRoute
        ) {
            composable(Routes.SELECTOR) {
                ProfileSelectorScreen(
                    members = members,
                    familyName = activeFamily?.name.orEmpty(),
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = vm::toggleTheme,
                    onSelectMember = { member ->
                        if (member.isAdmin) {
                            pendingProtectedMember = member
                        } else {
                            vm.selectProfile(member.id)
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.SELECTOR) { inclusive = true }
                            }
                        }
                    },
                    onChangeFamily = vm::clearActiveFamilySpace
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
                        updateRepoSlug = updateRepoSlug,
                        updateState = updateState,
                        selectedTab = selectedTab,
                        searchQuery = searchQuery,
                        chatPreviews = chatPreviews,
                        calls = calls,
                        shoppingItems = shoppingItems,
                        events = events,
                        notes = notes,
                        snackbarMessage = snackbarMessage,
                        onClearSnackbar = vm::clearSnackbar,
                        onToggleTheme = vm::toggleTheme,
                        onUpdateOwnStatus = vm::updateOwnProfileStatus,
                        onCheckForUpdate = vm::checkForAppUpdate,
                        onSaveUpdateRepoSlug = { vm.saveUpdateRepoSlug(it, triggerCheck = true) },
                        onDownloadUpdate = vm::downloadAndInstallUpdate,
                        onResetUpdateState = vm::resetUpdateState,
                        onSelectTab = vm::selectTab,
                        onSearchChange = vm::updateSearchQuery,
                        onOpenChat = { chatId ->
                            vm.openChat(chatId)
                            navController.navigate(Routes.chat(chatId))
                        },
                        onStartDirectChat = { otherId ->
                            vm.createOrOpenDirectChat(otherId) { chatId ->
                                navController.navigate(Routes.chat(chatId))
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
                                navController.navigate(Routes.ADMIN)
                            }
                        },
                        onStartCall = vm::startCall,
                        onAddShoppingItem = vm::addShoppingItem,
                        onToggleShoppingItem = vm::toggleShoppingItem,
                        onDeleteShoppingItem = vm::deleteShoppingItem,
                        onClearCheckedShopping = vm::clearCheckedShoppingItems,
                        onAddEvent = vm::addFamilyEvent,
                        onDeleteEvent = vm::deleteFamilyEvent,
                        onSaveNote = vm::saveFamilyNote,
                        onDeleteNote = vm::deleteFamilyNote
                    )
                }
            }

            composable(
                route = Routes.CHAT,
                arguments = listOf(navArgument("chatId") { type = NavType.StringType })
            ) { backStackEntry ->
                val chatId = backStackEntry.arguments?.getString("chatId") ?: return@composable
                val member = activeMember
                val chatUi = chatPreviews.find { it.chat.id == chatId }
                if (member == null || chatUi == null) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
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
                        messages = chatMessages,
                        voiceRecording = voiceRecording,
                        playingVoiceMessageId = playingVoiceId,
                        playingVoiceProgress = playingVoiceProgress,
                        snackbarMessage = snackbarMessage,
                        onClearSnackbar = vm::clearSnackbar,
                        onBack = {
                            vm.closeChat()
                            navController.popBackStack()
                        },
                        onSendText = { text, replyId -> vm.sendTextMessage(chatId, text, replyId) },
                        onSendImage = { uri, caption, replyId -> vm.sendImageMessage(chatId, uri, caption, replyId) },
                        onSendLocation = { name, addr, lat, lng, replyId ->
                            vm.sendLocationMessage(chatId, name, addr, lat, lng, replyId)
                        },
                        onSendPoll = { q, opts -> vm.sendPollMessage(chatId, q, opts) },
                        onVotePoll = vm::voteOnPoll,
                        onEditMessage = vm::editMessage,
                        onToggleReaction = vm::toggleReaction,
                        onTogglePin = { msgId -> vm.togglePinMessage(chatId, msgId) },
                        onDeleteForMe = vm::deleteMessagesForMe,
                        onDeleteForEveryone = vm::deleteMessagesForEveryone,
                        onStartVoiceRecording = { vm.startVoiceRecording(chatId) },
                        onCancelVoiceRecording = vm::cancelVoiceRecording,
                        onSendVoiceRecording = { replyId -> vm.finishAndSendVoiceRecording(chatId, replyId) },
                        onToggleVoicePlay = vm::toggleVoicePlayback,
                        onStartCall = { callType ->
                            vm.startCall(
                                chatId = chatId,
                                title = chatUi.displayTitle,
                                avatarEmoji = chatUi.displayEmoji,
                                avatarColorHex = chatUi.displayColorHex,
                                callType = callType
                            )
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
                    onBack = { navController.popBackStack() },
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
                onToggleMute = vm::toggleCallMute,
                onToggleSpeaker = vm::toggleCallSpeaker,
                onToggleCamera = vm::toggleCallCamera,
                onEndCall = vm::endActiveCall
            )
        }

        incomingCall?.let { call ->
            val caller = members.find { it.id == call.callerId }
            IncomingCallOverlay(
                call = call,
                callerMember = caller,
                onAccept = { vm.acceptIncomingCall(call) },
                onDecline = { vm.declineIncomingCall(call) }
            )
        }
    }
}
