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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.testTag
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
import com.example.data.local.AppFontScale
import com.example.data.local.AppThemeMode
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
import com.example.ui.screens.AppUpdatesHubScreen
import com.example.ui.screens.AppUpdatesScreen
import com.example.ui.screens.AppearanceSettingsScreen
import com.example.ui.screens.CallOverlayScreen
import com.example.ui.screens.ChatRoomScreen
import com.example.ui.screens.FamilySetupScreen
import com.example.ui.screens.FontSizeSettingsScreen
import com.example.ui.screens.IncomingCallOverlay
import com.example.ui.screens.LicensesScreen
import com.example.ui.screens.MainHomeScreen
import com.example.ui.screens.NotificationSettingsScreen
import com.example.ui.screens.ProfileSelectorScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WhatsNewScreen
import com.example.ui.theme.FamiChatTheme
import com.example.ui.viewmodel.FamilyViewModel
import com.example.ui.viewmodel.MainTab
import com.example.util.FamiChatNotificationHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

object Routes {
    const val SELECTOR = "selector"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val SETTINGS_APPEARANCE = "settings_appearance"
    const val SETTINGS_FONT_SIZE = "settings_font_size"
    const val SETTINGS_WHATS_NEW = "settings_whats_new"
    const val SETTINGS_LICENSES = "settings_licenses"
    const val UPDATES = "updates"
    const val UPDATES_CHECK = "updates_check"
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
            val themeMode by prefs.themeModeFlow.collectAsStateWithLifecycle(initialValue = AppThemeMode.SYSTEM)
            val fontScalePref by prefs.fontScaleFlow.collectAsStateWithLifecycle(initialValue = AppFontScale.NORMAL)
            val systemDark = isSystemInDarkTheme()
            val isDarkTheme = when (themeMode) {
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
                AppThemeMode.SYSTEM -> systemDark
            }

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
                fontScaleMultiplier = fontScalePref.multiplier,
                onToggleTheme = {
                    scope.launch {
                        prefs.setThemeMode(if (isDarkTheme) AppThemeMode.LIGHT else AppThemeMode.DARK)
                    }
                }
            ) {
                var signedInOverride by remember { mutableStateOf<FirebaseUser?>(null) }
                val signedInUser = currentUser ?: signedInOverride
                if (signedInUser == null) {
                    var isAuthLoading by remember { mutableStateOf(true) }
                    var authError by remember { mutableStateOf<String?>(null) }
                    var authRetryTick by remember { mutableStateOf(0) }

                    LaunchedEffect(authRetryTick) {
                        isAuthLoading = true
                        authError = null
                        try {
                            withTimeout(12_000L) {
                                Firebase.auth.signInAnonymously().await()
                            }
                            signedInOverride = Firebase.auth.currentUser
                        } catch (e: Exception) {
                            authError = e.localizedMessage ?: "No se pudo conectar. Verifica tu internet."
                        } finally {
                            isAuthLoading = false
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (isAuthLoading) {
                                CircularProgressIndicator()
                                Text(
                                    text = "Conectando a FamiChat...",
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            } else {
                                Text(
                                    text = authError ?: "No se pudo conectar",
                                    color = MaterialTheme.colorScheme.error
                                )
                                Button(onClick = { authRetryTick++ }) {
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
                    FamiChatApp(
                        vm = vm,
                        userEmail = signedInUser.email.orEmpty(),
                        userName = signedInUser.displayName.orEmpty(),
                        isDarkTheme = isDarkTheme,
                        externalOpenChatId = pendingNotifChatId,
                        onConsumeExternalOpenChatId = { pendingNotifChatId = null },
                        onSignOutGoogle = {
                            scope.launch {
                                signedInOverride = null
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
    val activeMemberId by vm.activeMemberId.collectAsStateWithLifecycle()

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
    val initialRoute = remember {
        if (vm.activeMember.value != null || !vm.activeMemberId.value.isNullOrBlank()) {
            Routes.HOME
        } else {
            Routes.SELECTOR
        }
    }
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
    val chatClearedAtMap by vm.chatClearedAtMap.collectAsStateWithLifecycle()
    val locallyHiddenMessageKeys by vm.locallyHiddenMessageKeys.collectAsStateWithLifecycle()
    val pendingNotificationChatId by vm.pendingNotificationChatId.collectAsStateWithLifecycle()
    val updateRepoSlug by vm.updateRepoSlug.collectAsStateWithLifecycle()
    val updateState by vm.updateState.collectAsStateWithLifecycle()
    val lastUpdateCheckAt by vm.lastUpdateCheckAt.collectAsStateWithLifecycle()
    val versionHistoryState by vm.versionHistoryState.collectAsStateWithLifecycle()
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    val fontScale by vm.fontScale.collectAsStateWithLifecycle()
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
    val realtimeBackgroundEnabled by vm.realtimeBackgroundServiceEnabled.collectAsStateWithLifecycle()
    val turnUrl by vm.turnUrl.collectAsStateWithLifecycle()
    val turnUsername by vm.turnUsername.collectAsStateWithLifecycle()
    val turnCredential by vm.turnCredential.collectAsStateWithLifecycle()
    val snackbarMessage by vm.snackbarMessage.collectAsStateWithLifecycle()
    val globalSnackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage ?: return@LaunchedEffect
        try {
            globalSnackbarHostState.currentSnackbarData?.dismiss()
            globalSnackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
        } finally {
            if (vm.snackbarMessage.value == msg) {
                vm.clearSnackbar()
            }
        }
    }

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
                LaunchedEffect(activeMember?.id) {
                    if (activeMember != null &&
                        navController.currentBackStackEntry?.destination?.route == Routes.SELECTOR
                    ) {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.SELECTOR) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
                ProfileSelectorScreen(
                    members = members,
                    isMembersLoading = isMembersLoading,
                    snackbarMessage = null,
                    onClearSnackbar = {},
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
                val pendingMemberId = activeMemberId
                if (member == null) {
                    if (pendingMemberId.isNullOrBlank()) {
                        LaunchedEffect(Unit) {
                            navController.navigate(Routes.SELECTOR) {
                                popUpTo(Routes.HOME) { inclusive = true }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
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
                        snackbarMessage = null,
                        onClearSnackbar = {},
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
                        onCheckForUpdate = { vm.checkForAppUpdate() },
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
                        onOpenNotifications = { navigateSingleTop(Routes.NOTIFICATIONS) },
                        isSwitchPinActive = switchPin.isNotBlank()
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
                        onCheckForUpdate = { vm.checkForAppUpdate() },
                        onSaveUpdateRepoSlug = { vm.saveUpdateRepoSlug(it, triggerCheck = true) },
                        onDownloadUpdate = vm::downloadAndInstallUpdate,
                        onResetUpdateState = vm::resetUpdateState,
                        onOpenAdvanced = {
                            if (member.isAdmin) {
                                navigateSingleTop(Routes.ADMIN_ADVANCED)
                            }
                        },
                        onOpenUpdates = { navigateSingleTop(Routes.UPDATES) },
                        themeMode = themeMode,
                        fontScale = fontScale,
                        onOpenAppearance = { navigateSingleTop(Routes.SETTINGS_APPEARANCE) },
                        onOpenFontSize = { navigateSingleTop(Routes.SETTINGS_FONT_SIZE) },
                        onOpenWhatsNew = { navigateSingleTop(Routes.SETTINGS_WHATS_NEW) },
                        onOpenAdminPanel = {
                            if (member.isAdmin) {
                                navigateSingleTop(Routes.ADMIN)
                            }
                        },
                        onOpenDiagnostics = {
                            if (member.isAdmin) {
                                navigateSingleTop(Routes.ADMIN_DIAGNOSTICS)
                            }
                        },
                        onOpenLicenses = { navigateSingleTop(Routes.SETTINGS_LICENSES) }
                    )
                }
            }

            composable(Routes.SETTINGS_APPEARANCE) {
                AppearanceSettingsScreen(
                    selectedMode = themeMode,
                    isDarkTheme = isDarkTheme,
                    onSelectMode = vm::setThemeMode,
                    onBack = { safePopBack(Routes.SETTINGS_APPEARANCE) }
                )
            }

            composable(Routes.SETTINGS_FONT_SIZE) {
                FontSizeSettingsScreen(
                    selectedScale = fontScale,
                    isDarkTheme = isDarkTheme,
                    onSelectScale = vm::setFontScale,
                    onBack = { safePopBack(Routes.SETTINGS_FONT_SIZE) }
                )
            }

            composable(Routes.SETTINGS_WHATS_NEW) {
                val bundledNotes = BuildConfig.VERSION_RELEASE_NOTES.trim()
                val matchingNotes = bundledNotes.ifBlank {
                    when (val st = updateState) {
                        is com.example.util.AppUpdateUiState.UpdateAvailable ->
                            if (st.release.versionName == BuildConfig.VERSION_NAME) st.release.releaseNotes else ""
                        is com.example.util.AppUpdateUiState.Downloading ->
                            if (st.release.versionName == BuildConfig.VERSION_NAME) st.release.releaseNotes else ""
                        is com.example.util.AppUpdateUiState.ReadyToInstall ->
                            if (st.release.versionName == BuildConfig.VERSION_NAME) st.release.releaseNotes else ""
                        else -> ""
                    }
                }
                WhatsNewScreen(
                    installedVersionName = BuildConfig.VERSION_NAME,
                    versionReleaseNotes = matchingNotes,
                    isDarkTheme = isDarkTheme,
                    onBack = { safePopBack(Routes.SETTINGS_WHATS_NEW) }
                )
            }

            composable(Routes.SETTINGS_LICENSES) {
                LicensesScreen(
                    isDarkTheme = isDarkTheme,
                    onBack = { safePopBack(Routes.SETTINGS_LICENSES) }
                )
            }

            composable(Routes.UPDATES) {
                val member = activeMember
                if (member == null) {
                    LaunchedEffect(Unit) { safePopBack(Routes.UPDATES) }
                } else {
                    AppUpdatesHubScreen(
                        isDarkTheme = isDarkTheme,
                        updateState = updateState,
                        onOpenCheckUpdates = { navigateSingleTop(Routes.UPDATES_CHECK) },
                        onOpenWhatsNew = { navigateSingleTop(Routes.SETTINGS_WHATS_NEW) },
                        onBack = { safePopBack(Routes.UPDATES) }
                    )
                }
            }

            composable(Routes.UPDATES_CHECK) {
                val member = activeMember
                if (member == null) {
                    LaunchedEffect(Unit) { safePopBack(Routes.UPDATES_CHECK) }
                } else {
                    AppUpdatesScreen(
                        isAdmin = member.isAdmin,
                        isDarkTheme = isDarkTheme,
                        currentRepoSlug = updateRepoSlug,
                        updateState = updateState,
                        lastCheckedAtMs = lastUpdateCheckAt,
                        versionHistoryState = versionHistoryState,
                        onCheckForUpdate = { vm.checkForAppUpdate(isAutoCheck = false) },
                        onDownloadUpdate = vm::downloadAndInstallUpdate,
                        onCancelDownload = vm::cancelUpdateDownload,
                        onLoadVersionHistory = vm::loadVersionHistoryOnDemand,
                        onPostponeUpdate = {
                            vm.postponeUpdateFor24Hours()
                            safePopBack(Routes.UPDATES_CHECK)
                        },
                        onBack = { safePopBack(Routes.UPDATES_CHECK) }
                    )
                }
            }

            composable(Routes.NOTIFICATIONS) {
                NotificationSettingsScreen(
                    notificationsEnabled = notificationsEnabled,
                    realtimeBackgroundEnabled = realtimeBackgroundEnabled,
                    snackbarMessage = null,
                    onClearSnackbar = {},
                    onToggleNotifications = vm::setNotificationsEnabled,
                    onToggleRealtimeBackground = vm::setRealtimeBackgroundServiceEnabled,
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
                    snackbarMessage = null,
                    onClearSnackbar = {},
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
                    val chatMessages = remember(
                        allMessages,
                        chatId,
                        member.id,
                        chatClearedAtMap,
                        locallyHiddenMessageKeys
                    ) {
                        allMessages.filter { msg ->
                            msg.chatId == chatId &&
                                FamilyViewModel.isMessageVisibleForMember(
                                    msg = msg,
                                    memberId = member.id,
                                    clearedAtMap = chatClearedAtMap,
                                    locallyHiddenKeys = locallyHiddenMessageKeys
                                )
                        }
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
                        snackbarMessage = null,
                        onClearSnackbar = {},
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
                        onClearChat = { vm.clearChatForMe(chatId) },
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

        SnackbarHost(
            hostState = globalSnackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .testTag("global_snackbar_host")
        ) { data ->
            Snackbar(
                snackbarData = data,
                shape = RoundedCornerShape(14.dp)
            )
        }
    }
}
