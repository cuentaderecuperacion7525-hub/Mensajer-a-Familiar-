package com.example

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.model.ChatType
import com.example.data.model.FamilyMemberEntity
import com.example.ui.components.PinVerificationDialog
import com.example.ui.screens.AdminPanelScreen
import com.example.ui.screens.CallOverlayScreen
import com.example.ui.screens.ChatRoomScreen
import com.example.ui.screens.MainHomeScreen
import com.example.ui.screens.ProfileSelectorScreen
import com.example.ui.theme.NidoFamiliarTheme
import com.example.ui.viewmodel.FamilyViewModel

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
            val vm: FamilyViewModel = viewModel()
            val isDarkTheme by vm.isDarkTheme.collectAsStateWithLifecycle()

            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as? Activity)?.window ?: return@SideEffect
                    val controller = WindowCompat.getInsetsController(window, view)
                    controller.isAppearanceLightStatusBars = !isDarkTheme
                    controller.isAppearanceLightNavigationBars = !isDarkTheme
                }
            }

            NidoFamiliarTheme(darkTheme = isDarkTheme) {
                NidoFamiliarApp(vm = vm, isDarkTheme = isDarkTheme)
            }
        }
    }
}

@Composable
fun NidoFamiliarApp(
    vm: FamilyViewModel,
    isDarkTheme: Boolean
) {
    val navController = rememberNavController()
    val members by vm.members.collectAsStateWithLifecycle()
    val activeMember by vm.activeMember.collectAsStateWithLifecycle()
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
    val activeCall by vm.activeCall.collectAsStateWithLifecycle()
    val voiceRecording by vm.voiceRecording.collectAsStateWithLifecycle()
    val playingVoiceId by vm.playingVoiceMessageId.collectAsStateWithLifecycle()
    val playingVoiceProgress by vm.playingVoiceProgress.collectAsStateWithLifecycle()
    val snackbarMessage by vm.snackbarMessage.collectAsStateWithLifecycle()

    var pendingProtectedMember by remember { mutableStateOf<FamilyMemberEntity?>(null) }
    var showSwitchKeyDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.SELECTOR
        ) {
            composable(Routes.SELECTOR) {
                ProfileSelectorScreen(
                    members = members,
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
                    }
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
                                    popUpTo(Routes.HOME) { inclusive = true }
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
                        onStartCall = vm::startSimulatedCall,
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
                        onSendLocation = { name, addr, replyId -> vm.sendLocationMessage(chatId, name, addr, replyId) },
                        onSendPoll = { q, opts -> vm.sendPollMessage(chatId, q, opts) },
                        onVotePoll = vm::voteOnPoll,
                        onEditMessage = vm::editMessage,
                        onToggleReaction = vm::toggleReaction,
                        onTogglePin = { msgId -> vm.togglePinMessage(chatId, msgId) },
                        onDeleteForMe = vm::deleteMessagesForMe,
                        onDeleteForEveryone = vm::deleteMessagesForEveryone,
                        onStartVoiceRecording = vm::startVoiceRecording,
                        onCancelVoiceRecording = vm::cancelVoiceRecording,
                        onSendVoiceRecording = { replyId -> vm.finishAndSendVoiceRecording(chatId, replyId) },
                        onToggleVoicePlay = vm::toggleVoicePlayback,
                        onStartCall = { callType ->
                            vm.startSimulatedCall(
                                chatId = chatId,
                                title = chatUi.displayTitle,
                                avatarEmoji = chatUi.displayEmoji,
                                avatarColorHex = chatUi.displayColorHex,
                                callType = callType
                            )
                        },
                        onSimulatePresence = { status -> vm.triggerBriefPresenceDemo(chatId, status) }
                    )
                }
            }

            composable(Routes.ADMIN) {
                AdminPanelScreen(
                    members = members,
                    chats = rawChats,
                    currentMemberId = activeMember?.id,
                    currentMasterPin = masterPin,
                    currentSwitchPin = switchPin,
                    onBack = { navController.popBackStack() },
                    onSaveMember = vm::saveFamilyMember,
                    onDeleteMember = vm::deleteFamilyMember,
                    onSaveGroup = vm::saveGroup,
                    onDeleteGroup = vm::deleteGroup,
                    onUpdateMasterPin = vm::updateMasterPin,
                    onUpdateSwitchPin = vm::updateSwitchPin
                )
            }
        }

        pendingProtectedMember?.let { protectedMember ->
            PinVerificationDialog(
                title = "Contraseña de ${protectedMember.name}",
                subtitle = "Ingresa la contraseña de ${masterPin.length} dígitos para entrar",
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
                subtitle = "Ingresa la clave autorizada por el administrador (${switchPin.length} dígitos)",
                pinLength = switchPin.length,
                onVerify = vm::verifySwitchKey,
                onSuccess = {
                    showSwitchKeyDialog = false
                    vm.logoutToSelector()
                    navController.navigate(Routes.SELECTOR) {
                        popUpTo(Routes.HOME) { inclusive = true }
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
                onEndCall = vm::endSimulatedCall
            )
        }
    }
}
