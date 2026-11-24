package com.example

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.CallType
import com.example.data.model.FamilyMemberEntity
import com.example.ui.screens.AdminDiagnosticsScreen
import com.example.ui.screens.CallOverlayScreen
import com.example.ui.screens.MainHomeScreen
import com.example.ui.screens.NotificationSettingsScreen
import com.example.ui.screens.ProfileSelectorScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.viewmodel.ActiveCallUiState
import com.example.ui.viewmodel.MainTab
import com.example.util.AppUpdateChecker
import com.example.util.AppUpdateUiState
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("FamiChat", appName)
  }

  @Test
  fun `app update checker selects newest release containing an apk asset`() {
    val json = org.json.JSONArray(
      """
      [
        {
          "tag_name": "v2.1.2-c13",
          "name": "Draft tag without apk",
          "draft": false,
          "assets": []
        },
        {
          "tag_name": "v2.1.1-c12",
          "name": "FamiChat v2.1.1 (Build 12)",
          "draft": false,
          "assets": [
            {
              "name": "FamiChat-v2.1.1-c12-debug.apk",
              "size": 24576000,
              "browser_download_url": "https://github.com/u/r/releases/download/v2.1.1-c12/FamiChat-v2.1.1-c12-debug.apk"
            }
          ]
        }
      ]
      """.trimIndent()
    )
    val selected = AppUpdateChecker.selectBestReleaseFromApiJsonArray(json, "https://github.com/u/r/releases")
    assertEquals("v2.1.1-c12", selected?.tagName)
    assertEquals(12, selected?.versionCode)
    assertEquals(24_576_000L, selected?.apkSizeBytes)
    assertTrue(selected?.downloadUrl?.endsWith(".apk") == true)
  }

  @Test
  fun `notification helper creates message and call channels`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    com.example.util.FamiChatNotificationHelper.ensureChannels(context)
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
      val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
      val msgCh = mgr.getNotificationChannel(com.example.util.FamiChatNotificationHelper.CHANNEL_MESSAGES)
      val callCh = mgr.getNotificationChannel(com.example.util.FamiChatNotificationHelper.CHANNEL_CALLS)
      org.junit.Assert.assertNotNull(msgCh)
      org.junit.Assert.assertNotNull(callCh)
    }
  }

  @Test
  fun `family status 24h TTL and chat wallpaper palette work correctly`() {
    assertEquals(24 * 60 * 60 * 1000L, com.example.data.model.FamilyStatusEntity.STATUS_TTL_MS)
    val defaultLight = com.example.ui.components.ChatWallpaperPalette.resolveColor("default", isDark = false)
    val mintLight = com.example.ui.components.ChatWallpaperPalette.resolveColor("mint", isDark = false)
    val unknownLight = com.example.ui.components.ChatWallpaperPalette.resolveColor("non_existent", isDark = false)
    assertEquals(defaultLight, unknownLight)
    org.junit.Assert.assertNotEquals(defaultLight, mintLight)

    val ids = listOf("m_papa", "m_mama")
    val encoded = com.example.data.model.JsonHelpers.encodeStringList(ids)
    assertEquals(ids, com.example.data.model.JsonHelpers.decodeStringList(encoded))
  }

  @Test
  fun `chunked media splitting video message and voice played status work correctly`() {
    val samplePayload = "A".repeat(400_000) + "B".repeat(120_000)
    val chunks = com.example.util.DocumentMediaHelper.splitIntoChunks(samplePayload)
    assertEquals(2, chunks.size)
    assertEquals(380_000, chunks[0].length)
    assertEquals(140_000, chunks[1].length)
    assertEquals(samplePayload, chunks.joinToString(""))

    val videoMsg = com.example.data.model.ChatMessageEntity(
      id = "msg_vid_1",
      chatId = "group_main",
      senderId = "m_papa",
      text = "Video familiar",
      type = com.example.data.model.MessageType.VIDEO,
      status = com.example.data.model.MessageStatus.DELIVERED,
      timestamp = 1_700_000_000_000L,
      voiceDurationSec = 12,
      videoThumbnailData = "data:image/jpeg;base64,thumb",
      chunkCount = 2,
      playedByMemberIdsJson = com.example.data.model.JsonHelpers.encodeStringList(listOf("m_mama"))
    )
    assertEquals(com.example.data.model.MessageType.VIDEO, videoMsg.type)
    assertEquals(2, videoMsg.chunkCount)
    assertTrue(com.example.data.model.JsonHelpers.decodeStringList(videoMsg.playedByMemberIdsJson).contains("m_mama"))

    val chunkEntity = com.example.data.model.MediaChunkEntity(
      id = "msg_vid_1_0",
      messageId = "msg_vid_1",
      chunkIndex = 0,
      totalChunks = 2,
      data = "chunk_payload",
      authorUid = "uid_1"
    )
    assertEquals(0, chunkEntity.chunkIndex)
    assertTrue(com.example.data.local.PreferencesManager.DEFAULT_TURN_URL.contains("turn:openrelay.metered.ca"))

    val videoStatus = com.example.data.model.FamilyStatusEntity(
      id = "st_vid_1",
      memberId = "m_papa",
      text = "Video de estado",
      mediaUri = "chunked:video/mp4",
      mediaType = "VIDEO",
      videoThumbnailData = "data:image/jpeg;base64,/9j/4AAQ=",
      videoDurationSec = 9,
      chunkCount = 2
    )
    assertTrue(videoStatus.isVideo)
    assertEquals(9, videoStatus.videoDurationSec)
    assertEquals(2, videoStatus.chunkCount)

    val model1 = com.example.util.ImageCompressionHelper.resolveCoilModel("data:image/jpeg;base64,/9j/4AAQ=")
    val model2 = com.example.util.ImageCompressionHelper.resolveCoilModel("data:image/jpeg;base64,/9j/4AAQ=")
    assertSame(model1, model2)
  }

  @Test
  fun `video media helper caches atomically finds legacy cache and cleans expired status videos`() = kotlinx.coroutines.test.runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val sampleB64 = android.util.Base64.encodeToString(byteArrayOf(0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70), android.util.Base64.NO_WRAP)
    val writeRes = com.example.util.VideoMediaHelper.writeAssembledVideoToCache(
      context = context,
      messageId = "st_test123",
      fileName = "estado_st_test123.mp4",
      fullDataUri = "data:video/mp4;base64,$sampleB64"
    )
    assertTrue(writeRes.isSuccess)
    val cachedFile = writeRes.getOrNull()!!
    assertTrue(cachedFile.exists())
    assertEquals(cachedFile.absolutePath, com.example.util.VideoMediaHelper.findExistingCachedVideoFile(context, "st_test123", "estado_st_test123.mp4")?.absolutePath)

    // Simulate expired status file > 26h old
    cachedFile.setLastModified(System.currentTimeMillis() - (27L * 60L * 60L * 1000L))
    com.example.util.VideoMediaHelper.cleanupExpiredStatusVideos(context)
    assertFalse(cachedFile.exists())
  }

  @Test
  fun `settings hides Administrador section for non admin and shows Administracion and Diagnostico for admin`() {
    val isAdminState = androidx.compose.runtime.mutableStateOf(false)
    composeTestRule.setContent {
      SettingsScreen(
        isAdmin = isAdminState.value,
        isDarkTheme = false,
        notificationsEnabled = true,
        updateRepoSlug = "",
        updateState = AppUpdateUiState.Idle,
        onBack = {},
        onOpenNotifications = {},
        onToggleTheme = {},
        onCheckForUpdate = {},
        onSaveUpdateRepoSlug = {},
        onDownloadUpdate = {},
        onResetUpdateState = {},
        onOpenAdvanced = {}
      )
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_notifications_row").assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_admin_row").assertDoesNotExist()
    composeTestRule.onNodeWithTag("settings_diagnostics_row").assertDoesNotExist()
    assertEquals(0, composeTestRule.onAllNodesWithText("ADMINISTRADOR").fetchSemanticsNodes().size)

    composeTestRule.runOnUiThread { isAdminState.value = true }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("ADMINISTRADOR").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_admin_row").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_diagnostics_row").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun `notifications screen avoids technical terms and triggers test alert`() {
    var tested = false
    composeTestRule.setContent {
      NotificationSettingsScreen(
        notificationsEnabled = false,
        snackbarMessage = null,
        onClearSnackbar = {},
        onToggleNotifications = {},
        onSendTestNotification = { tested = true },
        onBack = {}
      )
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Notificaciones desactivadas").assertIsDisplayed()
    composeTestRule.onNodeWithTag("notifications_activate_button").assertIsDisplayed()
    assertEquals(0, composeTestRule.onAllNodesWithText("FCM", substring = true, ignoreCase = true).fetchSemanticsNodes().size)
    assertEquals(0, composeTestRule.onAllNodesWithText("WorkManager", substring = true, ignoreCase = true).fetchSemanticsNodes().size)
    assertEquals(0, composeTestRule.onAllNodesWithText("token", substring = true, ignoreCase = true).fetchSemanticsNodes().size)
    assertEquals(0, composeTestRule.onAllNodesWithText("tópico", substring = true, ignoreCase = true).fetchSemanticsNodes().size)

    composeTestRule.onNodeWithTag("notifications_test_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertTrue(tested)
  }

  @Test
  fun `admin diagnostics removes quick guide and hides token and channel until expanded`() {
    val fakeToken = "fcm_test_token_1234567890"
    composeTestRule.setContent {
      AdminDiagnosticsScreen(
        isAdmin = true,
        familyId = "fam_123",
        memberName = "José Manuel Martínez",
        notificationsEnabled = true,
        fcmToken = fakeToken,
        turnUrl = "turn:openrelay.metered.ca:80",
        turnUsername = "openrelayproject",
        turnCredential = "openrelayproject",
        snackbarMessage = null,
        onClearSnackbar = {},
        onSendTestNotification = {},
        onSaveTurnConfig = { _, _, _ -> },
        onBack = {}
      )
    }
    composeTestRule.waitForIdle()

    // No Guía rápida anywhere
    assertEquals(0, composeTestRule.onAllNodesWithText("Guía rápida", substring = true, ignoreCase = true).fetchSemanticsNodes().size)
    // Token and channel hidden initially
    assertEquals(0, composeTestRule.onAllNodesWithText("famichat_fam_123").fetchSemanticsNodes().size)
    assertEquals(0, composeTestRule.onAllNodesWithText(fakeToken).fetchSemanticsNodes().size)

    // Tap "Ver detalles técnicos" -> reveals channel and token
    composeTestRule.onNodeWithTag("toggle_technical_details_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("famichat_fam_123").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText(fakeToken).performScrollTo().assertIsDisplayed()
  }

  private fun dispatchSystemBack() {
    composeTestRule.runOnUiThread {
      val latestDialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
      if (latestDialog != null && latestDialog.isShowing) {
        if (latestDialog is androidx.activity.ComponentDialog) {
          latestDialog.onBackPressedDispatcher.onBackPressed()
        } else {
          @Suppress("DEPRECATION")
          latestDialog.onBackPressed()
        }
      } else {
        composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
      }
    }
    composeTestRule.waitForIdle()
  }

  private fun sampleMember(id: String = "m_1", name: String = "Cristopher", isAdmin: Boolean = false) =
    FamilyMemberEntity(
      id = id,
      name = name,
      roleLabel = if (isAdmin) "Papá" else "Hijo",
      isAdmin = isAdmin,
      avatarColorHex = "#1A5FD4",
      avatarEmoji = "🧑🏽"
    )

  @Test
  fun `back navigation double press within 3s exits app from Chats and resets silently after 3s`() {
    val me = sampleMember()
    composeTestRule.setContent {
      MainHomeScreen(
        activeMember = me,
        allMembers = listOf(me),
        userGroupsCount = 1,
        isDarkTheme = false,
        notificationsEnabled = true,
        updateRepoSlug = "",
        updateState = AppUpdateUiState.Idle,
        selectedTab = MainTab.CHATS,
        searchQuery = "",
        chatPreviews = emptyList(),
        statuses = emptyList(),
        calls = emptyList(),
        shoppingItems = emptyList(),
        events = emptyList(),
        notes = emptyList(),
        snackbarMessage = null,
        onClearSnackbar = {},
        onToggleTheme = {},
        onToggleNotifications = {},
        onUpdateOwnProfile = { _, _, _, _, _, _, _ -> },
        onPublishTextStatus = { _, _ -> },
        onPublishPhotoStatus = { _, _ -> },
        onMarkStatusViewed = {},
        onDeleteStatus = {},
        onReplyToStatus = { _, _ -> },
        onCheckForUpdate = {},
        onSaveUpdateRepoSlug = {},
        onDownloadUpdate = {},
        onResetUpdateState = {},
        onSelectTab = {},
        onSearchChange = {},
        onOpenChat = {},
        onStartDirectChat = {},
        onSwitchProfile = {},
        onOpenAdminPanel = {},
        onStartCall = { _, _, _, _, _ -> },
        onAddShoppingItem = { _, _, _ -> },
        onToggleShoppingItem = {},
        onDeleteShoppingItem = {},
        onClearCheckedShopping = {},
        onAddEvent = { _, _, _, _, _, _ -> },
        onDeleteEvent = {},
        onSaveNote = { _, _, _, _, _ -> },
        onDeleteNote = {}
      )
    }
    composeTestRule.waitForIdle()

    // 1st back press on Chats -> arms silently, does NOT finish
    composeTestRule.runOnUiThread { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
    composeTestRule.waitForIdle()
    assertFalse(composeTestRule.activity.isFinishing)

    // Wait > 3s -> disarms silently, next back still does NOT finish
    composeTestRule.mainClock.advanceTimeBy(3_500L)
    composeTestRule.waitForIdle()
    composeTestRule.runOnUiThread { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
    composeTestRule.waitForIdle()
    assertFalse(composeTestRule.activity.isFinishing)

    // Press again within 3s -> exits app cleanly
    composeTestRule.mainClock.advanceTimeBy(500L)
    composeTestRule.waitForIdle()
    composeTestRule.runOnUiThread { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
    composeTestRule.waitForIdle()
    assertTrue(composeTestRule.activity.isFinishing)
  }

  @Test
  fun `back navigation returns from Calls tab to Chats and closes overflow menu and profile sheet first`() {
    val me = sampleMember()
    val tabState = androidx.compose.runtime.mutableStateOf(MainTab.CHATS)
    composeTestRule.setContent {
      MainHomeScreen(
        activeMember = me,
        allMembers = listOf(me),
        userGroupsCount = 1,
        isDarkTheme = false,
        notificationsEnabled = true,
        updateRepoSlug = "",
        updateState = AppUpdateUiState.Idle,
        selectedTab = tabState.value,
        searchQuery = "",
        chatPreviews = emptyList(),
        statuses = emptyList(),
        calls = emptyList(),
        shoppingItems = emptyList(),
        events = emptyList(),
        notes = emptyList(),
        snackbarMessage = null,
        onClearSnackbar = {},
        onToggleTheme = {},
        onToggleNotifications = {},
        onUpdateOwnProfile = { _, _, _, _, _, _, _ -> },
        onPublishTextStatus = { _, _ -> },
        onPublishPhotoStatus = { _, _ -> },
        onMarkStatusViewed = {},
        onDeleteStatus = {},
        onReplyToStatus = { _, _ -> },
        onCheckForUpdate = {},
        onSaveUpdateRepoSlug = {},
        onDownloadUpdate = {},
        onResetUpdateState = {},
        onSelectTab = { tabState.value = it },
        onSearchChange = {},
        onOpenChat = {},
        onStartDirectChat = {},
        onSwitchProfile = {},
        onOpenAdminPanel = {},
        onStartCall = { _, _, _, _, _ -> },
        onAddShoppingItem = { _, _, _ -> },
        onToggleShoppingItem = {},
        onDeleteShoppingItem = {},
        onClearCheckedShopping = {},
        onAddEvent = { _, _, _, _, _, _ -> },
        onDeleteEvent = {},
        onSaveNote = { _, _, _, _, _ -> },
        onDeleteNote = {}
      )
    }
    composeTestRule.waitForIdle()

    // Overflow menu open -> back closes menu without exiting
    composeTestRule.onNodeWithTag("home_more_menu_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("home_more_menu").assertIsDisplayed()
    dispatchSystemBack()
    composeTestRule.onNodeWithTag("home_more_menu").assertDoesNotExist()
    assertFalse(composeTestRule.activity.isFinishing)

    // Profile options sheet open -> back closes sheet and stays on Home
    composeTestRule.onNodeWithTag("home_more_menu_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("menu_profile_options").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("profile_options_dialog").assertIsDisplayed()
    dispatchSystemBack()
    composeTestRule.onNodeWithTag("profile_options_dialog").assertDoesNotExist()
    assertFalse(composeTestRule.activity.isFinishing)

    // Switch to Llamadas tab -> back returns to Chats tab
    composeTestRule.runOnUiThread { tabState.value = MainTab.CALLS }
    composeTestRule.waitForIdle()
    dispatchSystemBack()
    assertEquals(MainTab.CHATS, tabState.value)
    assertFalse(composeTestRule.activity.isFinishing)
  }

  @Test
  fun `back navigation during active call asks confirmation and does not hang up without confirm`() {
    var ended = false
    composeTestRule.setContent {
      CallOverlayScreen(
        callState = ActiveCallUiState(
          callId = "call_1",
          chatId = "chat_1",
          title = "Mamá",
          avatarEmoji = "👩🏽",
          avatarColorHex = "#6B4FD8",
          callType = CallType.VOICE,
          isOutgoing = true,
          isConnected = true,
          durationSec = 15
        ),
        onToggleMute = {},
        onToggleSpeaker = {},
        onToggleCamera = {},
        onEndCall = { ended = true }
      )
    }
    composeTestRule.waitForIdle()

    // 1st back -> shows confirmation dialog, does NOT end call
    dispatchSystemBack()
    composeTestRule.onNodeWithText("¿Salir de la llamada?").assertIsDisplayed()
    assertFalse(ended)

    // 2nd back while dialog is open -> closes dialog without ending call
    dispatchSystemBack()
    assertEquals(0, composeTestRule.onAllNodesWithText("¿Salir de la llamada?").fetchSemanticsNodes().size)
    assertFalse(ended)

    // Trigger back again and confirm "Colgar" -> ends call
    dispatchSystemBack()
    composeTestRule.onNodeWithTag("confirm_end_call_button").performClick()
    composeTestRule.waitForIdle()
    assertTrue(ended)
  }

  @Test
  fun `back navigation across NavHost routes, profile selection, and notification deep link works cleanly`() {
    val me = sampleMember()
    lateinit var navRef: androidx.navigation.NavHostController
    composeTestRule.setContent {
      val navController = rememberNavController()
      navRef = navController
      NavHost(navController = navController, startDestination = Routes.SELECTOR) {
        composable(Routes.SELECTOR) {
          ProfileSelectorScreen(
            members = listOf(me),
            isMembersLoading = false,
            snackbarMessage = null,
            onClearSnackbar = {},
            onSelectMember = {
              navController.navigate(Routes.HOME) {
                popUpTo(Routes.SELECTOR) { inclusive = true }
                launchSingleTop = true
              }
            },
            onRetryLoad = {}
          )
        }
        composable(Routes.HOME) {
          MainHomeScreen(
            activeMember = me,
            allMembers = listOf(me),
            userGroupsCount = 1,
            isDarkTheme = false,
            notificationsEnabled = true,
            updateRepoSlug = "",
            updateState = AppUpdateUiState.Idle,
            selectedTab = MainTab.CHATS,
            searchQuery = "",
            chatPreviews = emptyList(),
            statuses = emptyList(),
            calls = emptyList(),
            shoppingItems = emptyList(),
            events = emptyList(),
            notes = emptyList(),
            snackbarMessage = null,
            onClearSnackbar = {},
            onToggleTheme = {},
            onToggleNotifications = {},
            onUpdateOwnProfile = { _, _, _, _, _, _, _ -> },
            onPublishTextStatus = { _, _ -> },
            onPublishPhotoStatus = { _, _ -> },
            onMarkStatusViewed = {},
            onDeleteStatus = {},
            onReplyToStatus = { _, _ -> },
            onCheckForUpdate = {},
            onSaveUpdateRepoSlug = {},
            onDownloadUpdate = {},
            onResetUpdateState = {},
            onSelectTab = {},
            onSearchChange = {},
            onOpenChat = {},
            onStartDirectChat = {},
            onSwitchProfile = {},
            onOpenAdminPanel = {},
            onStartCall = { _, _, _, _, _ -> },
            onAddShoppingItem = { _, _, _ -> },
            onToggleShoppingItem = {},
            onDeleteShoppingItem = {},
            onClearCheckedShopping = {},
            onAddEvent = { _, _, _, _, _, _ -> },
            onDeleteEvent = {},
            onSaveNote = { _, _, _, _, _ -> },
            onDeleteNote = {},
            onOpenSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } }
          )
        }
        composable(Routes.SETTINGS) {
          SettingsScreen(
            isAdmin = false,
            isDarkTheme = false,
            notificationsEnabled = true,
            updateRepoSlug = "",
            updateState = AppUpdateUiState.Idle,
            onBack = { navController.popBackStack() },
            onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) { launchSingleTop = true } },
            onToggleTheme = {},
            onCheckForUpdate = {},
            onSaveUpdateRepoSlug = {},
            onDownloadUpdate = {},
            onResetUpdateState = {},
            onOpenAdvanced = {}
          )
        }
        composable(Routes.NOTIFICATIONS) {
          NotificationSettingsScreen(
            notificationsEnabled = true,
            snackbarMessage = null,
            onClearSnackbar = {},
            onToggleNotifications = {},
            onSendTestNotification = {},
            onBack = { navController.popBackStack() }
          )
        }
        composable(Routes.CHAT) {
          androidx.compose.material3.Text("MockChatRoom")
        }
      }
    }
    composeTestRule.waitForIdle()

    // Pick profile -> replaces SELECTOR with HOME
    composeTestRule.onNodeWithTag("profile_card_${me.id}").performClick()
    composeTestRule.waitForIdle()
    assertEquals(Routes.HOME, navRef.currentBackStackEntry?.destination?.route)

    // Back from HOME must NOT go back to SELECTOR ("¿Quién eres?")
    composeTestRule.runOnUiThread { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
    composeTestRule.waitForIdle()
    assertEquals(Routes.HOME, navRef.currentBackStackEntry?.destination?.route)
    assertEquals(0, composeTestRule.onAllNodesWithText("¿Quién eres?").fetchSemanticsNodes().size)

    // Wait >3s to disarm double-back exit, then navigate HOME -> SETTINGS -> NOTIFICATIONS
    composeTestRule.mainClock.advanceTimeBy(3_500L)
    composeTestRule.waitForIdle()
    composeTestRule.runOnUiThread {
      navRef.navigate(Routes.SETTINGS) { launchSingleTop = true }
      navRef.navigate(Routes.NOTIFICATIONS) { launchSingleTop = true }
    }
    composeTestRule.waitForIdle()
    assertEquals(Routes.NOTIFICATIONS, navRef.currentBackStackEntry?.destination?.route)

    // Back -> SETTINGS -> Back -> HOME
    composeTestRule.runOnUiThread { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
    composeTestRule.waitForIdle()
    assertEquals(Routes.SETTINGS, navRef.currentBackStackEntry?.destination?.route)
    composeTestRule.runOnUiThread { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
    composeTestRule.waitForIdle()
    assertEquals(Routes.HOME, navRef.currentBackStackEntry?.destination?.route)

    // Simulate notification deep link anchored directly over HOME even if opened from SETTINGS
    composeTestRule.runOnUiThread {
      navRef.navigate(Routes.SETTINGS) { launchSingleTop = true }
      navRef.navigate(Routes.chat("chat_notif")) {
        popUpTo(Routes.HOME) { inclusive = false }
        launchSingleTop = true
      }
    }
    composeTestRule.waitForIdle()
    assertEquals(Routes.CHAT, navRef.currentBackStackEntry?.destination?.route)

    // Back from notification chat -> returns directly to HOME (Chats) and does NOT exit app
    composeTestRule.runOnUiThread { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
    composeTestRule.waitForIdle()
    assertEquals(Routes.HOME, navRef.currentBackStackEntry?.destination?.route)
    assertFalse(composeTestRule.activity.isFinishing)
  }

  @Test
  fun `chat room back closes menu, search, and confirmation dialog before leaving chat`() {
    val me = sampleMember()
    val chatUi = com.example.ui.viewmodel.ChatThreadUi(
      chat = com.example.data.model.ChatThreadEntity(
        id = "chat_fam",
        type = com.example.data.model.ChatType.GROUP,
        title = "Familia",
        avatarColorHex = "#1A5FD4",
        avatarEmoji = "🏡",
        participantIdsJson = "[\"${me.id}\"]"
      ),
      displayTitle = "Familia",
      displayEmoji = "🏡",
      displayColorHex = "#1A5FD4",
      isOnline = true,
      subtitleStatus = "En línea",
      lastMessagePreview = "Hola familia",
      lastMessageTime = "12:00",
      unreadCount = 0,
      lastMessageStatus = com.example.data.model.MessageStatus.READ,
      isLastMessageFromMe = true,
      participants = listOf(me)
    )
    var leftChat = false
    var deletedMessages = false
    composeTestRule.setContent {
      com.example.ui.screens.ChatRoomScreen(
        chatUi = chatUi,
        activeMember = me,
        allMembers = listOf(me),
        messages = listOf(
          com.example.data.model.ChatMessageEntity(
            id = "msg_1",
            chatId = "chat_fam",
            senderId = me.id,
            text = "Hola familia",
            timestamp = 1_700_000_000_000L
          )
        ),
        voiceRecording = com.example.ui.viewmodel.VoiceRecordingState(),
        playingVoiceMessageId = null,
        playingVoiceProgress = 0f,
        snackbarMessage = null,
        onClearSnackbar = {},
        onBack = { leftChat = true },
        onSendText = { _, _ -> },
        onSendImage = { _, _, _ -> },
        onSendLocation = { _, _, _, _, _, _ -> },
        onSendPoll = { _, _ -> },
        onVotePoll = { _, _ -> },
        onEditMessage = { _, _ -> },
        onToggleReaction = { _, _ -> },
        onTogglePin = {},
        onDeleteForMe = { deletedMessages = true },
        onDeleteForEveryone = {},
        onStartVoiceRecording = {},
        onCancelVoiceRecording = {},
        onSendVoiceRecording = {},
        onToggleVoicePlay = {},
        onStartCall = {},
        onTypingChange = {},
        onToggleTheme = {},
        onGoToHomePanel = {},
        onSwitchProfile = {}
      )
    }
    composeTestRule.waitForIdle()

    // 1. Open header overflow menu -> back closes menu only
    composeTestRule.onNodeWithTag("chat_more_menu_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("chat_more_menu").assertIsDisplayed()
    dispatchSystemBack()
    composeTestRule.onNodeWithTag("chat_more_menu").assertDoesNotExist()
    assertFalse(leftChat)

    // 2. Open "Vaciar chat" confirmation dialog -> back closes dialog without deleting messages
    composeTestRule.onNodeWithTag("chat_more_menu_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("chat_menu_clear_chat").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("¿Deseas vaciar todos los mensajes de este chat para ti?").assertIsDisplayed()
    dispatchSystemBack()
    assertEquals(0, composeTestRule.onAllNodesWithText("¿Deseas vaciar todos los mensajes de este chat para ti?").fetchSemanticsNodes().size)
    assertFalse(deletedMessages)
    assertFalse(leftChat)

    // 3. Back directly from chat -> calls onBack()
    dispatchSystemBack()
    assertTrue(leftChat)
  }

  @Test
  @Config(qualifiers = "w411dp-h891dp")
  fun `chats list displays online, short-date last seen, and never connected on the right side of direct rows`() {
    val me = sampleMember(id = "m_me", name = "José Manuel", isAdmin = true)
    val mama = sampleMember(id = "m_mama", name = "Mamá")
    val hijo1 = sampleMember(id = "m_h1", name = "Cristian Fernando")
    val hijo2 = sampleMember(id = "m_h2", name = "Cristopher")

    val groupPreview = com.example.ui.viewmodel.ChatThreadUi(
      chat = com.example.data.model.ChatThreadEntity(
        id = "chat_general",
        type = com.example.data.model.ChatType.GROUP,
        title = "Mi Familia",
        avatarColorHex = "#1A5FD4",
        avatarEmoji = "🏠",
        participantIdsJson = "[\"${me.id}\",\"${mama.id}\"]"
      ),
      displayTitle = "Mi Familia",
      displayEmoji = "🏠",
      displayColorHex = "#1A5FD4",
      isOnline = false,
      subtitleStatus = "2 miembros",
      lastMessagePreview = "Sin mensajes aún",
      lastMessageTime = "",
      unreadCount = 0,
      lastMessageStatus = null,
      isLastMessageFromMe = false,
      participants = listOf(me, mama),
      rightStatusText = ""
    )

    val onlineWithMessage = com.example.ui.viewmodel.ChatThreadUi(
      chat = com.example.data.model.ChatThreadEntity(
        id = "direct_mama",
        type = com.example.data.model.ChatType.DIRECT,
        title = "Mamá",
        avatarColorHex = "#6B4FD8",
        avatarEmoji = "👩🏽",
        participantIdsJson = "[\"${me.id}\",\"${mama.id}\"]"
      ),
      displayTitle = "Mamá",
      displayEmoji = "👩🏽",
      displayColorHex = "#6B4FD8",
      isOnline = true,
      subtitleStatus = "En línea",
      lastMessagePreview = "Ya llegué a la casa",
      lastMessageTime = "12:04",
      unreadCount = 1,
      lastMessageStatus = null,
      isLastMessageFromMe = false,
      participants = listOf(me, mama),
      rightStatusText = "En línea"
    )

    val oldLastSeenEmpty = com.example.ui.viewmodel.ChatThreadUi(
      chat = com.example.data.model.ChatThreadEntity(
        id = "virtual_direct_${hijo1.id}",
        type = com.example.data.model.ChatType.DIRECT,
        title = hijo1.name,
        avatarColorHex = "#C94521",
        avatarEmoji = "🧑🏽",
        participantIdsJson = "[\"${me.id}\",\"${hijo1.id}\"]"
      ),
      displayTitle = hijo1.name,
      displayEmoji = "🧑🏽",
      displayColorHex = "#C94521",
      isOnline = false,
      subtitleStatus = "Últ. vez 15 nov 14:30",
      lastMessagePreview = "Sin mensajes aún",
      lastMessageTime = "",
      unreadCount = 0,
      lastMessageStatus = null,
      isLastMessageFromMe = false,
      participants = listOf(me, hijo1),
      rightStatusText = "Últ. vez 15 nov 14:30"
    )

    val neverConnectedEmpty = com.example.ui.viewmodel.ChatThreadUi(
      chat = com.example.data.model.ChatThreadEntity(
        id = "virtual_direct_${hijo2.id}",
        type = com.example.data.model.ChatType.DIRECT,
        title = hijo2.name,
        avatarColorHex = "#B0306A",
        avatarEmoji = "🧑🏽",
        participantIdsJson = "[\"${me.id}\",\"${hijo2.id}\"]"
      ),
      displayTitle = hijo2.name,
      displayEmoji = "🧑🏽",
      displayColorHex = "#B0306A",
      isOnline = false,
      subtitleStatus = "No se ha conectado",
      lastMessagePreview = "Sin mensajes aún",
      lastMessageTime = "",
      unreadCount = 0,
      lastMessageStatus = null,
      isLastMessageFromMe = false,
      participants = listOf(me, hijo2),
      rightStatusText = "No se ha conectado"
    )

    composeTestRule.setContent {
      MainHomeScreen(
        activeMember = me,
        allMembers = listOf(me, mama, hijo1, hijo2),
        userGroupsCount = 1,
        isDarkTheme = false,
        notificationsEnabled = true,
        updateRepoSlug = "",
        updateState = AppUpdateUiState.Idle,
        selectedTab = MainTab.CHATS,
        searchQuery = "",
        chatPreviews = listOf(groupPreview, onlineWithMessage, oldLastSeenEmpty, neverConnectedEmpty),
        statuses = emptyList(),
        calls = emptyList(),
        shoppingItems = emptyList(),
        events = emptyList(),
        notes = emptyList(),
        snackbarMessage = null,
        onClearSnackbar = {},
        onToggleTheme = {},
        onToggleNotifications = {},
        onUpdateOwnProfile = { _, _, _, _, _, _, _ -> },
        onPublishTextStatus = { _, _ -> },
        onPublishPhotoStatus = { _, _ -> },
        onMarkStatusViewed = {},
        onDeleteStatus = {},
        onReplyToStatus = { _, _ -> },
        onCheckForUpdate = {},
        onSaveUpdateRepoSlug = {},
        onDownloadUpdate = {},
        onResetUpdateState = {},
        onSelectTab = {},
        onSearchChange = {},
        onOpenChat = {},
        onStartDirectChat = {},
        onSwitchProfile = {},
        onOpenAdminPanel = {},
        onStartCall = { _, _, _, _, _ -> },
        onAddShoppingItem = { _, _, _ -> },
        onToggleShoppingItem = {},
        onDeleteShoppingItem = {},
        onClearCheckedShopping = {},
        onAddEvent = { _, _, _, _, _, _ -> },
        onDeleteEvent = {},
        onSaveNote = { _, _, _, _, _ -> },
        onDeleteNote = {}
      )
    }
    composeTestRule.waitForIdle()

    // Group row should not render direct presence badge
    composeTestRule.onNodeWithTag("chat_presence_chat_general", useUnmergedTree = true).assertDoesNotExist()
    // Direct with messages shows both message time, message preview, and right-aligned "En línea"
    composeTestRule.onNodeWithTag("chats_lazy_column").performScrollToIndex(1)
    composeTestRule.waitForIdle()
    val nameBounds = composeTestRule.onNodeWithText("Mamá", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    val timeBounds = composeTestRule.onNodeWithText("12:04", useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
    val previewBounds = composeTestRule.onNodeWithText("Ya llegué a la casa", useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
    val presenceBounds = composeTestRule.onNodeWithTag("chat_presence_direct_mama", useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
    composeTestRule.onNodeWithText("En línea", useUnmergedTree = true).assertIsDisplayed()
    assertTrue("La hora debe estar alineada verticalmente con el nombre", kotlin.math.abs(timeBounds.top - nameBounds.top) < 12f)
    assertTrue("La conexión debe mostrarse debajo de la hora", presenceBounds.top >= timeBounds.bottom - 2f)
    assertTrue("La conexión debe quedar arriba del avance de mensaje", presenceBounds.bottom <= previewBounds.top + 2f)
    assertTrue("La conexión debe quedar al lado derecho", presenceBounds.right >= timeBounds.right - 8f)
    // Older last-seen date formatted with short month
    composeTestRule.onNodeWithTag("chats_lazy_column").performScrollToIndex(2)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("chat_presence_virtual_direct_${hijo1.id}", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("Últ. vez 15 nov 14:30", useUnmergedTree = true).assertIsDisplayed()
    // Never connected contact shows discrete label on right while keeping "Toca para saludar"
    composeTestRule.onNodeWithTag("chats_lazy_column").performScrollToIndex(3)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("chat_presence_virtual_direct_${hijo2.id}", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("No se ha conectado", useUnmergedTree = true).assertIsDisplayed()
  }

  @Test
  @Config(qualifiers = "w411dp-h891dp")
  fun `empty chat displays privacy notice with learn more sheet, closes sheet on back, and hides once messages exist`() {
    val me = sampleMember(id = "m_me", name = "José Manuel", isAdmin = true)
    val mama = sampleMember(id = "m_mama", name = "Mamá")
    val chatUi = com.example.ui.viewmodel.ChatThreadUi(
      chat = com.example.data.model.ChatThreadEntity(
        id = "direct_mama",
        type = com.example.data.model.ChatType.DIRECT,
        title = "Mamá",
        avatarColorHex = "#6B4FD8",
        avatarEmoji = "👩🏽",
        participantIdsJson = "[\"${me.id}\",\"${mama.id}\"]"
      ),
      displayTitle = "Mamá",
      displayEmoji = "👩🏽",
      displayColorHex = "#6B4FD8",
      isOnline = true,
      subtitleStatus = "En línea",
      lastMessagePreview = "Sin mensajes aún",
      lastMessageTime = "",
      unreadCount = 0,
      lastMessageStatus = null,
      isLastMessageFromMe = false,
      participants = listOf(me, mama),
      rightStatusText = "En línea"
    )
    val messagesState = androidx.compose.runtime.mutableStateOf<List<com.example.data.model.ChatMessageEntity>>(emptyList())
    var leftChat = false

    composeTestRule.setContent {
      com.example.ui.screens.ChatRoomScreen(
        chatUi = chatUi,
        activeMember = me,
        allMembers = listOf(me, mama),
        messages = messagesState.value,
        voiceRecording = com.example.ui.viewmodel.VoiceRecordingState(),
        playingVoiceMessageId = null,
        playingVoiceProgress = 0f,
        snackbarMessage = null,
        onClearSnackbar = {},
        onBack = { leftChat = true },
        onSendText = { _, _ -> },
        onSendImage = { _, _, _ -> },
        onSendLocation = { _, _, _, _, _, _ -> },
        onSendPoll = { _, _ -> },
        onVotePoll = { _, _ -> },
        onEditMessage = { _, _ -> },
        onToggleReaction = { _, _ -> },
        onTogglePin = {},
        onDeleteForMe = {},
        onDeleteForEveryone = {},
        onStartVoiceRecording = {},
        onCancelVoiceRecording = {},
        onSendVoiceRecording = {},
        onToggleVoicePlay = {},
        onStartCall = {},
        onTypingChange = {},
        onToggleTheme = {},
        onGoToHomePanel = {},
        onSwitchProfile = {}
      )
    }
    composeTestRule.waitForIdle()

    // 1. Empty chat shows notice and exact copy (without prohibited wording or old greeting elements)
    composeTestRule.onNodeWithTag("empty_chat_privacy_notice").assertIsDisplayed()
    composeTestRule.onNodeWithText("Aún no hay mensajes con Mamá").assertDoesNotExist()
    composeTestRule.onNodeWithText("Envíale un saludo para empezar.").assertDoesNotExist()
    composeTestRule.onNodeWithText("¡Hola!").assertDoesNotExist()
    composeTestRule.onNodeWithText("¿Cómo estás?").assertDoesNotExist()
    composeTestRule.onNodeWithText("Te quiero").assertDoesNotExist()
    composeTestRule.onNodeWithText(
      "Los mensajes y las llamadas de este chat son privados. Solo las personas de tu familia en este chat pueden acceder a ellos."
    ).assertIsDisplayed()
    assertEquals(
      0,
      composeTestRule.onAllNodesWithText("extremo a extremo", substring = true, ignoreCase = true)
        .fetchSemanticsNodes().size
    )

    // 2. Tap "Más información" -> opens bottom sheet
    composeTestRule.onNodeWithTag("empty_chat_privacy_learn_more").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("empty_chat_privacy_sheet").assertIsDisplayed()
    composeTestRule.onNodeWithText(
      "FamiChat es un espacio solo para tu familia. Los mensajes y las llamadas están protegidos mientras viajan por internet y solo pueden verlos las personas que forman parte de este chat."
    ).assertIsDisplayed()
    assertEquals(
      0,
      composeTestRule.onAllNodesWithText("extremo a extremo", substring = true, ignoreCase = true)
        .fetchSemanticsNodes().size
    )

    // 3. System back closes the bottom sheet without leaving the chat
    dispatchSystemBack()
    composeTestRule.onNodeWithTag("empty_chat_privacy_sheet").assertDoesNotExist()
    assertFalse(leftChat)

    // 4. First message arrives/is sent -> privacy notice disappears immediately
    composeTestRule.runOnUiThread {
      messagesState.value = listOf(
        com.example.data.model.ChatMessageEntity(
          id = "msg_first",
          chatId = "direct_mama",
          senderId = mama.id,
          text = "Hola!",
          timestamp = 1_700_000_000_000L
        )
      )
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("empty_chat_privacy_notice").assertDoesNotExist()

    // 5. Message is deleted/hidden for current member -> chat is empty again and privacy notice reappears
    composeTestRule.runOnUiThread {
      messagesState.value = listOf(
        com.example.data.model.ChatMessageEntity(
          id = "msg_first",
          chatId = "direct_mama",
          senderId = mama.id,
          text = "Hola!",
          timestamp = 1_700_000_000_000L,
          hiddenForMemberIdsJson = "[\"${me.id}\"]"
        )
      )
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("empty_chat_privacy_notice").assertIsDisplayed()
  }

  @Test
  @Config(qualifiers = "w411dp-h891dp")
  fun `contact info opens full screen from chat header without kinship or birthday, supports actions, calls, media, wallpaper sheet, and back returns to chat`() {
    val calCreated = java.util.Calendar.getInstance().apply {
      set(2026, java.util.Calendar.NOVEMBER, 10, 9, 0, 0)
      set(java.util.Calendar.MILLISECOND, 0)
    }
    val calCall1 = java.util.Calendar.getInstance().apply {
      set(2026, java.util.Calendar.NOVEMBER, 17, 17, 40, 0)
      set(java.util.Calendar.MILLISECOND, 0)
    }
    val calCall2 = java.util.Calendar.getInstance().apply {
      set(2026, java.util.Calendar.NOVEMBER, 16, 10, 15, 0)
      set(java.util.Calendar.MILLISECOND, 0)
    }
    val me = sampleMember(id = "m_me", name = "José Manuel", isAdmin = true)
    val contact = sampleMember(id = "m_mama", name = "Tila del Carmen").copy(
      roleLabel = "Mamá Especial",
      avatarColorHex = "#6B4FD8",
      bioText = "Siempre aquí para todos",
      isOnline = true,
      lastSeenText = "En casa",
      createdAt = calCreated.timeInMillis,
      liveStatusAt = calCall1.timeInMillis
    )
    val chatUiState = androidx.compose.runtime.mutableStateOf(
      com.example.ui.viewmodel.ChatThreadUi(
        chat = com.example.data.model.ChatThreadEntity(
          id = "direct_mama",
          type = com.example.data.model.ChatType.DIRECT,
          title = contact.name,
          avatarColorHex = contact.avatarColorHex,
          avatarEmoji = contact.avatarEmoji,
          participantIdsJson = "[\"${me.id}\",\"${contact.id}\"]"
        ),
        displayTitle = contact.name,
        displayEmoji = contact.avatarEmoji,
        displayColorHex = contact.avatarColorHex,
        partnerBioText = contact.bioText,
        isOnline = true,
        subtitleStatus = "En casa",
        lastMessagePreview = "Mira este enlace",
        lastMessageTime = "18:00",
        unreadCount = 0,
        lastMessageStatus = null,
        isLastMessageFromMe = false,
        participants = listOf(me, contact),
        isPinned = false,
        isMuted = false,
        rightStatusText = "En casa"
      )
    )
    val selectedWallpaper = androidx.compose.runtime.mutableStateOf("default")
    val selectedTone = androidx.compose.runtime.mutableStateOf("")
    val startedCalls = mutableListOf<com.example.data.model.CallType>()
    var leftChat = false

    val sampleMessages = listOf(
      com.example.data.model.ChatMessageEntity(
        id = "img_1", chatId = "direct_mama", senderId = contact.id, text = "Foto 1",
        type = com.example.data.model.MessageType.IMAGE, mediaUri = "https://example.com/1.jpg", timestamp = 1_700_000_001L
      ),
      com.example.data.model.ChatMessageEntity(
        id = "img_2", chatId = "direct_mama", senderId = contact.id, text = "Foto 2",
        type = com.example.data.model.MessageType.IMAGE, mediaUri = "https://example.com/2.jpg", timestamp = 1_700_000_002L
      ),
      com.example.data.model.ChatMessageEntity(
        id = "img_3", chatId = "direct_mama", senderId = contact.id, text = "Foto 3",
        type = com.example.data.model.MessageType.IMAGE, mediaUri = "https://example.com/3.jpg", timestamp = 1_700_000_003L
      ),
      com.example.data.model.ChatMessageEntity(
        id = "img_4", chatId = "direct_mama", senderId = contact.id, text = "Foto 4",
        type = com.example.data.model.MessageType.IMAGE, mediaUri = "https://example.com/4.jpg", timestamp = 1_700_000_004L
      ),
      com.example.data.model.ChatMessageEntity(
        id = "img_5", chatId = "direct_mama", senderId = contact.id, text = "Foto 5",
        type = com.example.data.model.MessageType.IMAGE, mediaUri = "https://example.com/5.jpg", timestamp = 1_700_000_005L
      ),
      com.example.data.model.ChatMessageEntity(
        id = "doc_1", chatId = "direct_mama", senderId = me.id, text = "",
        type = com.example.data.model.MessageType.DOCUMENT, fileName = "Receta.pdf", fileSizeBytes = 2048L, timestamp = 1_700_000_006L
      ),
      com.example.data.model.ChatMessageEntity(
        id = "link_1", chatId = "direct_mama", senderId = contact.id, text = "Checa https://famichat.example.com/hola",
        type = com.example.data.model.MessageType.TEXT, timestamp = 1_700_000_007L
      )
    )
    val sampleCalls = listOf(
      com.example.data.model.CallRecordEntity(
        id = "c1", chatId = "direct_mama", title = contact.name, callerId = contact.id,
        avatarColorHex = contact.avatarColorHex, avatarEmoji = contact.avatarEmoji,
        callType = com.example.data.model.CallType.VOICE, direction = com.example.data.model.CallDirection.INCOMING,
        durationSec = 720, timestamp = calCall1.timeInMillis, callStatus = com.example.data.model.CallSignalStatus.ENDED
      ),
      com.example.data.model.CallRecordEntity(
        id = "c2", chatId = "direct_mama", title = contact.name, callerId = contact.id,
        avatarColorHex = contact.avatarColorHex, avatarEmoji = contact.avatarEmoji,
        callType = com.example.data.model.CallType.VOICE, direction = com.example.data.model.CallDirection.MISSED,
        durationSec = 0, timestamp = calCall2.timeInMillis, callStatus = com.example.data.model.CallSignalStatus.MISSED
      )
    )

    composeTestRule.setContent {
      com.example.ui.screens.ChatRoomScreen(
        chatUi = chatUiState.value,
        activeMember = me,
        allMembers = listOf(me, contact),
        calls = sampleCalls,
        selectedWallpaperKey = selectedWallpaper.value,
        selectedNotificationToneUri = selectedTone.value,
        messages = sampleMessages,
        voiceRecording = com.example.ui.viewmodel.VoiceRecordingState(),
        playingVoiceMessageId = null,
        playingVoiceProgress = 0f,
        snackbarMessage = null,
        onClearSnackbar = {},
        onBack = { leftChat = true },
        onSendText = { _, _ -> },
        onSendImage = { _, _, _ -> },
        onSendLocation = { _, _, _, _, _, _ -> },
        onSendPoll = { _, _ -> },
        onVotePoll = { _, _ -> },
        onEditMessage = { _, _ -> },
        onSelectWallpaper = { selectedWallpaper.value = it },
        onSelectNotificationTone = { selectedTone.value = it },
        onToggleReaction = { _, _ -> },
        onTogglePin = {},
        onTogglePinChat = { chatUiState.value = chatUiState.value.copy(isPinned = !chatUiState.value.isPinned) },
        onToggleMuteChat = { chatUiState.value = chatUiState.value.copy(isMuted = !chatUiState.value.isMuted) },
        onDeleteForMe = {},
        onDeleteForEveryone = {},
        onStartVoiceRecording = {},
        onCancelVoiceRecording = {},
        onSendVoiceRecording = {},
        onToggleVoicePlay = {},
        onStartCall = { startedCalls.add(it) },
        onTypingChange = {},
        onToggleTheme = {},
        onGoToHomePanel = {},
        onSwitchProfile = {}
      )
    }
    composeTestRule.waitForIdle()

    // Tap chat header to open full-screen ContactInfoScreen
    composeTestRule.onNodeWithTag("chat_header_info_button").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("contact_info_screen").assertIsDisplayed()
    composeTestRule.onNodeWithText("Tila del Carmen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("contact_info_status_pill").assertIsDisplayed()
    composeTestRule.onNodeWithText("En casa").assertIsDisplayed()
    composeTestRule.onNodeWithText("Siempre aquí para todos").assertIsDisplayed()
    composeTestRule.onNodeWithText("Miembro desde 10 nov 2026").assertIsDisplayed()

    // Kinship (roleLabel), birthday, and "Información" card MUST NOT appear
    composeTestRule.onNodeWithText("Mamá Especial", substring = true).assertDoesNotExist()
    composeTestRule.onNodeWithText("Cumpleaños", substring = true, ignoreCase = true).assertDoesNotExist()
    composeTestRule.onNodeWithText("Información", ignoreCase = true).assertDoesNotExist()

    // Recent calls section is intentionally omitted in ContactInfoScreen (already exists in Llamadas tab)
    composeTestRule.onNodeWithTag("contact_info_recent_calls_card").assertDoesNotExist()
    composeTestRule.onNodeWithText("LLAMADAS RECIENTES").assertDoesNotExist()

    // Shared counts (5 photos with +1 overflow, 1 file, 1 link)
    composeTestRule.onNodeWithTag("contact_info_shared_tabs").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Fotos 5").assertIsDisplayed()
    composeTestRule.onNodeWithText("Archivos 1").assertIsDisplayed()
    composeTestRule.onNodeWithText("Enlaces 1").assertIsDisplayed()
    composeTestRule.onNodeWithTag("contact_shared_photo_3").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("contact_shared_photos_overflow", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("+1", useUnmergedTree = true).assertIsDisplayed()

    // Chat settings card: wallpaper sheet, notification tone default, pin & mute switches
    composeTestRule.onNodeWithTag("contact_setting_wallpaper").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("contact_wallpaper_sheet").assertIsDisplayed()
    composeTestRule.onNodeWithTag("contact_wallpaper_option_mint").performClick()
    composeTestRule.waitForIdle()
    assertEquals("mint", selectedWallpaper.value)
    composeTestRule.onNodeWithTag("contact_setting_wallpaper_value", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("Menta", useUnmergedTree = true).assertIsDisplayed()

    composeTestRule.onNodeWithTag("contact_setting_notification_tone").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Predeterminado", useUnmergedTree = true).assertIsDisplayed()

    composeTestRule.onNodeWithTag("contact_setting_pin_chat").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("contact_setting_mute_chat").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertTrue(chatUiState.value.isPinned)
    assertTrue(chatUiState.value.isMuted)

    // Voice & video actions trigger existing call callbacks
    composeTestRule.onNodeWithTag("contact_action_voice_call").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("contact_action_video_call").performClick()
    assertEquals(listOf(com.example.data.model.CallType.VOICE, com.example.data.model.CallType.VIDEO), startedCalls)

    // System back closes ContactInfoScreen and returns to the chat without leaving chat
    dispatchSystemBack()
    composeTestRule.onNodeWithTag("contact_info_screen").assertDoesNotExist()
    composeTestRule.onNodeWithTag("chat_header_info_button").assertIsDisplayed()
    assertFalse(leftChat)

    // Reopen ContactInfoScreen and tap "Buscar" -> returns to chat with in-chat search open
    composeTestRule.onNodeWithTag("chat_header_info_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("contact_action_search").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("contact_info_screen").assertDoesNotExist()
    composeTestRule.onNodeWithTag("in_chat_search_input").assertIsDisplayed()
  }

  @Test
  @Config(qualifiers = "w411dp-h891dp")
  fun `contact info hides status pill, recent calls, and member since when data is absent`() {
    val me = sampleMember(id = "m_me", name = "José Manuel", isAdmin = true)
    val contact = sampleMember(id = "m_hijo", name = "Cristopher").copy(
      roleLabel = "Hijo 2",
      bioText = "",
      isOnline = false,
      lastSeenText = "No se ha conectado",
      createdAt = 0L,
      liveStatusAt = 0L,
      linkedAuthUid = ""
    )
    val chatUi = com.example.ui.viewmodel.ChatThreadUi(
      chat = com.example.data.model.ChatThreadEntity(
        id = "direct_cris",
        type = com.example.data.model.ChatType.DIRECT,
        title = contact.name,
        avatarColorHex = contact.avatarColorHex,
        avatarEmoji = contact.avatarEmoji,
        participantIdsJson = "[\"${me.id}\",\"${contact.id}\"]"
      ),
      displayTitle = contact.name,
      displayEmoji = contact.avatarEmoji,
      displayColorHex = contact.avatarColorHex,
      partnerBioText = "",
      isOnline = false,
      subtitleStatus = "No se ha conectado",
      lastMessagePreview = "Sin mensajes aún",
      lastMessageTime = "",
      unreadCount = 0,
      lastMessageStatus = null,
      isLastMessageFromMe = false,
      participants = listOf(me, contact),
      rightStatusText = "No se ha conectado"
    )

    composeTestRule.setContent {
      com.example.ui.screens.ContactInfoScreen(
        chatUi = chatUi,
        activeMember = me,
        allMembers = listOf(me, contact),
        visibleMessages = emptyList(),
        calls = emptyList(),
        selectedWallpaperKey = "default",
        selectedNotificationToneUri = "",
        isContactBlocked = false,
        onSelectWallpaper = {},
        onSelectNotificationTone = {},
        onTogglePinChat = {},
        onToggleMuteChat = {},
        onStartCall = {},
        onSearchInChat = {},
        onOpenImage = {},
        onOpenDocument = {},
        onRequestClearChat = {},
        onRequestToggleBlockContact = {},
        onBack = {}
      )
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("contact_info_screen").assertIsDisplayed()
    composeTestRule.onNodeWithText("Cristopher").assertIsDisplayed()
    composeTestRule.onNodeWithText("No se ha conectado").assertIsDisplayed()
    composeTestRule.onNodeWithTag("contact_info_status_pill").assertDoesNotExist()
    composeTestRule.onNodeWithTag("contact_info_member_since").assertDoesNotExist()
    composeTestRule.onNodeWithTag("contact_info_recent_calls_card").assertDoesNotExist()
    composeTestRule.onNodeWithText("Hijo 2").assertDoesNotExist()
    composeTestRule.onNodeWithTag("contact_shared_photos_empty").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Aún no se han compartido fotos en este chat.").assertIsDisplayed()
  }

  @Test
  @Config(qualifiers = "w411dp-h891dp")
  fun `emoji only messages render without bubble at 64 48 40 dp while 4+ and mixed text keep normal bubble`() {
    com.example.util.NotoAnimatedEmojiSession.clearForTest()
    val longPressedIds = mutableListOf<String>()
    val now = 1_763_480_000_000L

    val msg1Mine = com.example.data.model.ChatMessageEntity(
      id = "e1", chatId = "c1", senderId = "me",
      type = com.example.data.model.MessageType.TEXT, text = "😎",
      status = com.example.data.model.MessageStatus.READ, timestamp = now
    )
    val msg2Recv = com.example.data.model.ChatMessageEntity(
      id = "e2", chatId = "c1", senderId = "other",
      type = com.example.data.model.MessageType.TEXT, text = "😎😎",
      status = com.example.data.model.MessageStatus.DELIVERED, timestamp = now + 1000
    )
    val msg3Mine = com.example.data.model.ChatMessageEntity(
      id = "e3", chatId = "c1", senderId = "me",
      type = com.example.data.model.MessageType.TEXT, text = "👍🏽 🇲🇽 👨‍👩‍👧‍👦",
      status = com.example.data.model.MessageStatus.SENT, timestamp = now + 2000
    )
    val msg4Normal = com.example.data.model.ChatMessageEntity(
      id = "e4", chatId = "c1", senderId = "me",
      type = com.example.data.model.MessageType.TEXT, text = "😎😎😎😎",
      status = com.example.data.model.MessageStatus.SENT, timestamp = now + 3000
    )
    val msgMixedNormal = com.example.data.model.ChatMessageEntity(
      id = "e5", chatId = "c1", senderId = "other",
      type = com.example.data.model.MessageType.TEXT, text = "hola 😎",
      status = com.example.data.model.MessageStatus.READ, timestamp = now + 4000
    )

    composeTestRule.setContent {
      androidx.compose.runtime.CompositionLocalProvider(
        com.example.ui.components.LocalNotoEmojiLoaderOverride provides { _, _ ->
          com.example.util.NotoEmojiLoadResult.NotAnimated
        }
      ) {
        androidx.compose.foundation.layout.Column {
          listOf(
            msg1Mine to true,
            msg2Recv to false,
            msg3Mine to true,
            msg4Normal to true,
            msgMixedNormal to false
          ).forEach { (msg, isMine) ->
            com.example.ui.components.ChatMessageBubble(
              message = msg,
              isMine = isMine,
              isGroupChat = false,
              sender = null,
              repliedMessage = null,
              repliedSenderName = null,
              activeMemberId = "me",
              isSelected = false,
              isMultiSelectMode = false,
              isPlayingVoice = false,
              voicePlaybackProgress = 0f,
              searchQuery = "",
              onToggleSelect = {},
              onLongPress = { longPressedIds.add(msg.id) },
              onReplyClick = {},
              onImageClick = {},
              onToggleVoicePlay = {},
              onVotePoll = {},
              onToggleReaction = {}
            )
          }
        }
      }
    }
    composeTestRule.waitForIdle()

    // 1, 2, 3 emojis render without message_bubble_* and with emoji_only_message_* + exact sizes
    composeTestRule.onNodeWithTag("emoji_only_message_e1").assertIsDisplayed()
    composeTestRule.onNodeWithTag("message_bubble_e1").assertDoesNotExist()
    composeTestRule.onNodeWithTag("emoji_only_glyph_e1_0", useUnmergedTree = true).assertWidthIsEqualTo(64.dp)
    composeTestRule.onNodeWithTag("emoji_only_meta_e1", useUnmergedTree = true).assertIsDisplayed()

    composeTestRule.onNodeWithTag("emoji_only_message_e2").assertIsDisplayed()
    composeTestRule.onNodeWithTag("message_bubble_e2").assertDoesNotExist()
    composeTestRule.onNodeWithTag("emoji_only_glyph_e2_0", useUnmergedTree = true).assertWidthIsEqualTo(48.dp)
    composeTestRule.onNodeWithTag("emoji_only_glyph_e2_1", useUnmergedTree = true).assertWidthIsEqualTo(48.dp)

    composeTestRule.onNodeWithTag("emoji_only_message_e3").assertIsDisplayed()
    composeTestRule.onNodeWithTag("message_bubble_e3").assertDoesNotExist()
    composeTestRule.onNodeWithTag("emoji_only_glyph_e3_0", useUnmergedTree = true).assertWidthIsEqualTo(40.dp)
    composeTestRule.onNodeWithTag("emoji_only_glyph_e3_2", useUnmergedTree = true).assertWidthIsEqualTo(40.dp)

    // Right alignment for mine vs left alignment for received
    val mineBounds = composeTestRule.onNodeWithTag("emoji_only_message_e1").getUnclippedBoundsInRoot()
    val recvBounds = composeTestRule.onNodeWithTag("emoji_only_message_e2").getUnclippedBoundsInRoot()
    assertTrue("Mine emoji should be aligned to the right", mineBounds.left > recvBounds.left)

    // 4+ emojis and mixed text keep normal chat bubble
    composeTestRule.onNodeWithTag("message_bubble_e4").assertIsDisplayed()
    composeTestRule.onNodeWithTag("emoji_only_message_e4").assertDoesNotExist()
    composeTestRule.onNodeWithTag("message_bubble_e5").assertIsDisplayed()
    composeTestRule.onNodeWithTag("emoji_only_message_e5").assertDoesNotExist()

    // Tap replays without opening menu; long-press opens message actions
    composeTestRule.onNodeWithTag("emoji_only_message_e1").performClick()
    composeTestRule.waitForIdle()
    assertTrue(longPressedIds.isEmpty())

    composeTestRule.onNodeWithTag("emoji_only_message_e1").performTouchInput { longClick() }
    composeTestRule.waitForIdle()
    assertEquals(listOf("e1"), longPressedIds)
  }

  @Test
  @Config(qualifiers = "w411dp-h891dp")
  fun `emoji only messages render statically on offline error and reduce motion across all wallpapers`() {
    com.example.util.NotoAnimatedEmojiSession.clearForTest()
    val msgOffline = com.example.data.model.ChatMessageEntity(
      id = "e_off", chatId = "c1", senderId = "me",
      type = com.example.data.model.MessageType.TEXT, text = "❤️",
      status = com.example.data.model.MessageStatus.DELIVERED, timestamp = 1_763_480_000_000L
    )

    composeTestRule.setContent {
      androidx.compose.runtime.CompositionLocalProvider(
        com.example.ui.components.LocalReduceMotionOverride provides true,
        com.example.ui.components.LocalNotoEmojiLoaderOverride provides { _, _ ->
          com.example.util.NotoEmojiLoadResult.NetworkError
        }
      ) {
        androidx.compose.foundation.layout.Column {
          com.example.ui.components.ChatWallpaperPalette.options.forEach { wallpaper ->
            androidx.compose.foundation.layout.Box {
              com.example.ui.components.ChatMessageBubble(
                message = msgOffline.copy(id = "e_${wallpaper.key}"),
                isMine = true,
                isGroupChat = false,
                sender = null,
                repliedMessage = null,
                repliedSenderName = null,
                activeMemberId = "me",
                isSelected = false,
                isMultiSelectMode = false,
                isPlayingVoice = false,
                voicePlaybackProgress = 0f,
                searchQuery = "",
                onToggleSelect = {},
                onLongPress = {},
                onReplyClick = {},
                onImageClick = {},
                onToggleVoicePlay = {},
                onVotePoll = {},
                onToggleReaction = {}
              )
            }
          }
        }
      }
    }
    composeTestRule.waitForIdle()

    com.example.ui.components.ChatWallpaperPalette.options.forEach { wallpaper ->
      composeTestRule.onNodeWithTag("emoji_only_message_e_${wallpaper.key}").assertIsDisplayed()
      composeTestRule.onNodeWithTag("emoji_only_meta_e_${wallpaper.key}", useUnmergedTree = true).assertIsDisplayed()
    }
  }

  @Test
  @Config(qualifiers = "w411dp-h891dp")
  fun `settings screen shows Acerca de with Version and Licencias and LicensesScreen shows Noto Emoji credit`() {
    val showLicenses = androidx.compose.runtime.mutableStateOf(false)
    composeTestRule.setContent {
      if (!showLicenses.value) {
        SettingsScreen(
          isAdmin = false,
          isDarkTheme = false,
          notificationsEnabled = true,
          updateRepoSlug = "",
          updateState = AppUpdateUiState.Idle,
          onBack = {},
          onOpenNotifications = {},
          onToggleTheme = {},
          onCheckForUpdate = {},
          onSaveUpdateRepoSlug = {},
          onDownloadUpdate = {},
          onResetUpdateState = {},
          onOpenAdvanced = {},
          onOpenLicenses = { showLicenses.value = true }
        )
      } else {
        com.example.ui.screens.LicensesScreen(
          isDarkTheme = false,
          onBack = { showLicenses.value = false }
        )
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("ACERCA DE").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_version_row").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText(BuildConfig.VERSION_NAME, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_licenses_row").performScrollTo().assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()

    composeTestRule
      .onNodeWithText("Emojis animados: Noto Emoji Animation de Google, licencia CC BY 4.0")
      .performScrollTo()
      .assertIsDisplayed()
    composeTestRule.onNodeWithTag("license_material_icons").performScrollTo().assertIsDisplayed()
  }

  @Test
  @Config(qualifiers = "w411dp-h891dp")
  fun `novedades tab replaces avisos, shows red dot, recientes and vistos, opens viewer, creates status, and backs to chats`() {
    val me = sampleMember(id = "m_me", name = "José Manuel", isAdmin = true)
    val mama = sampleMember(id = "m_mama", name = "Tila del Carmen")
    val hijo = sampleMember(id = "m_hijo", name = "Cristian Fernando")
    val now = System.currentTimeMillis()

    val tabState = androidx.compose.runtime.mutableStateOf(MainTab.CHATS)
    val statusesState = androidx.compose.runtime.mutableStateOf(
      listOf(
        com.example.data.model.FamilyStatusEntity(
          id = "st_mama_1",
          memberId = mama.id,
          text = "Buenos días familia",
          mediaType = "VIDEO",
          createdAt = now - 60_000L,
          expiresAt = now + com.example.data.model.FamilyStatusEntity.STATUS_TTL_MS,
          viewedByMemberIdsJson = "[]"
        ),
        com.example.data.model.FamilyStatusEntity(
          id = "st_hijo_1",
          memberId = hijo.id,
          text = "En camino",
          createdAt = now - 120_000L,
          expiresAt = now + com.example.data.model.FamilyStatusEntity.STATUS_TTL_MS,
          viewedByMemberIdsJson = "[\"${me.id}\"]"
        )
      )
    )
    val notesState = androidx.compose.runtime.mutableStateOf(
      listOf(
        com.example.data.model.FamilyNoteEntity(
          id = "n1",
          title = "Cena familiar",
          content = "Hoy a las 8 pm en casa",
          authorId = mama.id,
          colorHex = "#FFF4CC",
          isPinned = true
        )
      )
    )

    composeTestRule.setContent {
      MainHomeScreen(
        activeMember = me,
        allMembers = listOf(me, mama, hijo),
        userGroupsCount = 1,
        isDarkTheme = false,
        notificationsEnabled = true,
        updateRepoSlug = "",
        updateState = AppUpdateUiState.Idle,
        selectedTab = tabState.value,
        searchQuery = "",
        chatPreviews = emptyList(),
        statuses = statusesState.value,
        calls = emptyList(),
        shoppingItems = emptyList(),
        events = emptyList(),
        notes = notesState.value,
        snackbarMessage = null,
        onClearSnackbar = {},
        onToggleTheme = {},
        onToggleNotifications = {},
        onUpdateOwnProfile = { _, _, _, _, _, _, _ -> },
        onPublishTextStatus = { _, _ -> },
        onPublishPhotoStatus = { _, _ -> },
        onMarkStatusViewed = { statusId ->
          statusesState.value = statusesState.value.map { st ->
            if (st.id == statusId) st.copy(viewedByMemberIdsJson = "[\"${me.id}\"]") else st
          }
        },
        onDeleteStatus = {},
        onReplyToStatus = { _, _ -> },
        onCheckForUpdate = {},
        onSaveUpdateRepoSlug = {},
        onDownloadUpdate = {},
        onResetUpdateState = {},
        onSelectTab = { tabState.value = it },
        onSearchChange = {},
        onOpenChat = {},
        onStartDirectChat = {},
        onSwitchProfile = {},
        onOpenAdminPanel = {},
        onStartCall = { _, _, _, _, _ -> },
        onAddShoppingItem = { _, _, _ -> },
        onToggleShoppingItem = {},
        onDeleteShoppingItem = {},
        onClearCheckedShopping = {},
        onAddEvent = { _, _, _, _, _, _ -> },
        onDeleteEvent = {},
        onSaveNote = { _, _, _, _, _ -> },
        onDeleteNote = {}
      )
    }
    composeTestRule.waitForIdle()

    // Bottom bar has Chats, Grupos, Llamadas, Novedades — no Avisos; Chats stories strip preserved
    composeTestRule.onNodeWithTag("nav_chats").assertIsDisplayed()
    composeTestRule.onNodeWithTag("nav_groups").assertIsDisplayed()
    composeTestRule.onNodeWithTag("nav_calls").assertIsDisplayed()
    composeTestRule.onNodeWithTag("nav_updates").assertIsDisplayed()
    composeTestRule.onNodeWithText("Avisos").assertDoesNotExist()
    composeTestRule.onNodeWithTag("family_status_strip").assertIsDisplayed()

    // Red dot appears on Novedades icon while mama's status is unseen
    composeTestRule.onNodeWithTag("nav_updates_unread_dot", useUnmergedTree = true).assertIsDisplayed()

    // Switch to Novedades tab
    composeTestRule.onNodeWithTag("nav_updates").performClick()
    composeTestRule.waitForIdle()
    assertEquals(MainTab.UPDATES, tabState.value)

    // Header is "Novedades" with no overflow icon; camera FAB is shown
    composeTestRule.onNodeWithTag("updates_top_title").assertIsDisplayed()
    composeTestRule.onNodeWithTag("home_more_menu_button").assertDoesNotExist()
    composeTestRule.onNodeWithTag("fab_new_status").assertIsDisplayed()

    // Pinned family notices card is visible because notes is non-empty
    composeTestRule.onNodeWithTag("updates_family_notices_card").assertIsDisplayed()
    composeTestRule.onNodeWithText("Avisos de la familia").assertIsDisplayed()
    composeTestRule.onNodeWithText("Cena familiar").assertIsDisplayed()

    // Estados section with "Añadir estado" / "Tu día en una foto"
    composeTestRule.onNodeWithText("Estados").assertIsDisplayed()
    composeTestRule.onNodeWithText("Añadir estado").assertIsDisplayed()
    composeTestRule.onNodeWithText("Tu día en una foto").assertIsDisplayed()

    // Recientes (mama with video badge) and Vistos (hijo)
    composeTestRule.onNodeWithTag("updates_section_recent").assertIsDisplayed()
    composeTestRule.onNodeWithTag("updates_recent_row_${mama.id}").assertIsDisplayed()
    composeTestRule.onNodeWithTag("updates_video_play_badge_st_mama_1", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithTag("updates_section_viewed").assertIsDisplayed()
    composeTestRule.onNodeWithTag("updates_viewed_row_${hijo.id}").assertIsDisplayed()

    // Tap mama's status -> opens viewer & marks viewed -> moves from Recientes to Vistos and clears red dot
    composeTestRule.onNodeWithTag("updates_recent_row_${mama.id}").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("status_reply_input").assertIsDisplayed()
    dispatchSystemBack()
    composeTestRule.onNodeWithTag("status_reply_input").assertDoesNotExist()
    composeTestRule.onNodeWithTag("updates_recent_row_${mama.id}").assertDoesNotExist()
    composeTestRule.onNodeWithTag("updates_viewed_row_${mama.id}").assertIsDisplayed()
    composeTestRule.onNodeWithTag("nav_updates_unread_dot", useUnmergedTree = true).assertDoesNotExist()

    // Tap camera FAB -> opens existing CreateStatusDialog
    composeTestRule.onNodeWithTag("fab_new_status").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("publish_status_button").assertIsDisplayed()
    dispatchSystemBack()
    composeTestRule.onNodeWithTag("publish_status_button").assertDoesNotExist()

    // Back gesture from Novedades returns to Chats
    dispatchSystemBack()
    assertEquals(MainTab.CHATS, tabState.value)
  }

  @Test
  @Config(qualifiers = "w411dp-h891dp")
  fun `novedades empty state hides notices card when empty and shows my status when active`() {
    val me = sampleMember(id = "m_me", name = "José Manuel", isAdmin = true)
    val now = System.currentTimeMillis()
    var addClicked = 0
    var openedMine = false

    composeTestRule.setContent {
      com.example.ui.screens.UpdatesTabScreen(
        activeMember = me,
        allMembers = listOf(me),
        statuses = listOf(
          com.example.data.model.FamilyStatusEntity(
            id = "st_me_1",
            memberId = me.id,
            text = "Listo para hoy",
            createdAt = now - 30_000L,
            expiresAt = now + com.example.data.model.FamilyStatusEntity.STATUS_TTL_MS
          )
        ),
        notes = emptyList(),
        isDarkTheme = false,
        onAddStatus = { addClicked++ },
        onOpenMemberStatuses = { if (it.member.id == me.id) openedMine = true }
      )
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("updates_family_notices_card").assertDoesNotExist()
    composeTestRule.onNodeWithText("Mi estado").assertIsDisplayed()
    composeTestRule.onNodeWithTag("updates_empty_message").assertIsDisplayed()
    composeTestRule.onNodeWithText("Aún no hay estados. Toca la cámara para compartir tu día.").assertIsDisplayed()

    composeTestRule.onNodeWithTag("updates_my_status_row").performClick()
    assertTrue(openedMine)

    composeTestRule.onNodeWithTag("updates_add_another_status_button").performClick()
    assertEquals(1, addClicked)

    // Verify unseen detector ignores own statuses and flips to false once viewed
    val own = com.example.data.model.FamilyStatusEntity(id = "s1", memberId = me.id, viewedByMemberIdsJson = "[]")
    val unseen = com.example.data.model.FamilyStatusEntity(id = "s2", memberId = "m_other", viewedByMemberIdsJson = "[]")
    val seen = unseen.copy(viewedByMemberIdsJson = "[\"${me.id}\"]")
    assertFalse(com.example.ui.screens.hasUnviewedFamilyStatuses(listOf(own), me.id))
    assertTrue(com.example.ui.screens.hasUnviewedFamilyStatuses(listOf(own, unseen), me.id))
    assertFalse(com.example.ui.screens.hasUnviewedFamilyStatuses(listOf(own, seen), me.id))
  }

  @Test
  @Config(qualifiers = "w411dp-h891dp")
  fun `vaciar chat desde menu de tres puntos limpia toda la conversacion y permite mensajes nuevos`() {
    val me = sampleMember(id = "m_me", name = "José Manuel", isAdmin = true)
    val other = sampleMember(id = "m_other", name = "Cristian Fernando")
    val chatId = "chat_direct_clear_test"
    val chatUi = com.example.ui.viewmodel.ChatThreadUi(
      chat = com.example.data.model.ChatThreadEntity(
        id = chatId,
        type = com.example.data.model.ChatType.DIRECT,
        title = other.name,
        avatarColorHex = other.avatarColorHex,
        avatarEmoji = other.avatarEmoji,
        participantIdsJson = "[\"${me.id}\",\"${other.id}\"]"
      ),
      displayTitle = other.name,
      displayEmoji = other.avatarEmoji,
      displayColorHex = other.avatarColorHex,
      isOnline = true,
      subtitleStatus = "En línea",
      lastMessagePreview = "Cuarto mensaje",
      lastMessageTime = "18:00",
      unreadCount = 0,
      lastMessageStatus = null,
      isLastMessageFromMe = false,
      participants = listOf(me, other)
    )
    val baseTs = 1_700_000_000_000L

    val rawMessages = androidx.compose.runtime.mutableStateOf(
      listOf(
        com.example.data.model.ChatMessageEntity(id = "msg_1", chatId = chatId, senderId = me.id, text = "Primer mensaje", timestamp = baseTs + 1_000L),
        com.example.data.model.ChatMessageEntity(id = "msg_2", chatId = chatId, senderId = other.id, text = "Segundo mensaje", timestamp = baseTs + 2_000L),
        com.example.data.model.ChatMessageEntity(id = "msg_3", chatId = chatId, senderId = me.id, text = "Tercer mensaje", timestamp = baseTs + 3_000L),
        com.example.data.model.ChatMessageEntity(id = "msg_4", chatId = chatId, senderId = other.id, text = "Cuarto mensaje", timestamp = baseTs + 4_000L),
        // Clock-skewed message slightly ahead of clear timestamp
        com.example.data.model.ChatMessageEntity(id = "msg_skew", chatId = chatId, senderId = other.id, text = "Mensaje adelantado", timestamp = baseTs + 15_000L)
      )
    )
    val clearedAtMap = androidx.compose.runtime.mutableStateOf<Map<String, Long>>(emptyMap())
    val locallyHidden = androidx.compose.runtime.mutableStateOf<Set<String>>(emptySet())
    var clearInvocations = 0

    composeTestRule.setContent {
      val visibleForMe = rawMessages.value.filter { msg ->
        com.example.ui.viewmodel.FamilyViewModel.isMessageVisibleForMember(
          msg = msg,
          memberId = me.id,
          clearedAtMap = clearedAtMap.value,
          locallyHiddenKeys = locallyHidden.value
        )
      }
      com.example.ui.screens.ChatRoomScreen(
        chatUi = chatUi,
        activeMember = me,
        allMembers = listOf(me, other),
        messages = visibleForMe,
        voiceRecording = com.example.ui.viewmodel.VoiceRecordingState(),
        playingVoiceMessageId = null,
        playingVoiceProgress = 0f,
        snackbarMessage = null,
        onClearSnackbar = {},
        onBack = {},
        onSendText = { _, _ -> },
        onSendImage = { _, _, _ -> },
        onSendLocation = { _, _, _, _, _, _ -> },
        onSendPoll = { _, _ -> },
        onVotePoll = { _, _ -> },
        onEditMessage = { _, _ -> },
        onToggleReaction = { _, _ -> },
        onTogglePin = {},
        onDeleteForMe = {},
        onClearChat = {
          clearInvocations++
          val clearTs = baseTs + 10_000L
          val skewed = rawMessages.value.filter { it.timestamp > clearTs }.map {
            com.example.data.local.PreferencesManager.hiddenMessageKey(me.id, it.id)
          }
          clearedAtMap.value = clearedAtMap.value + (
            com.example.data.local.PreferencesManager.chatClearedAtKey(me.id, chatId) to clearTs
            )
          locallyHidden.value = locallyHidden.value + skewed
        },
        onDeleteForEveryone = {},
        onStartVoiceRecording = {},
        onCancelVoiceRecording = {},
        onSendVoiceRecording = {},
        onToggleVoicePlay = {},
        onStartCall = {},
        onTypingChange = {},
        onToggleTheme = {},
        onGoToHomePanel = {},
        onSwitchProfile = {}
      )
    }
    composeTestRule.waitForIdle()

    // All 5 messages initially visible; privacy notice hidden
    composeTestRule.onNodeWithText("Primer mensaje").assertIsDisplayed()
    composeTestRule.onNodeWithText("Cuarto mensaje").assertIsDisplayed()
    composeTestRule.onNodeWithText("Mensaje adelantado").assertIsDisplayed()
    composeTestRule.onNodeWithTag("empty_chat_privacy_notice").assertDoesNotExist()

    // Open 3-dots menu -> tap "Vaciar chat" -> confirm
    composeTestRule.onNodeWithTag("chat_more_menu_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("chat_menu_clear_chat").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("clear_chat_confirm_button").performClick()
    composeTestRule.waitForIdle()

    // ALL messages are cleared at once (not just one) and empty-chat notice reappears
    assertEquals(1, clearInvocations)
    composeTestRule.onNodeWithText("Primer mensaje").assertDoesNotExist()
    composeTestRule.onNodeWithText("Segundo mensaje").assertDoesNotExist()
    composeTestRule.onNodeWithText("Tercer mensaje").assertDoesNotExist()
    composeTestRule.onNodeWithText("Cuarto mensaje").assertDoesNotExist()
    composeTestRule.onNodeWithText("Mensaje adelantado").assertDoesNotExist()
    composeTestRule.onNodeWithTag("empty_chat_privacy_notice").assertIsDisplayed()

    // Other participant still sees all messages unaffected
    assertTrue(
      rawMessages.value.all { msg ->
        com.example.ui.viewmodel.FamilyViewModel.isMessageVisibleForMember(
          msg = msg,
          memberId = other.id,
          clearedAtMap = clearedAtMap.value,
          locallyHiddenKeys = locallyHidden.value
        )
      }
    )

    // New message arriving after clear displays normally and hides empty notice again
    rawMessages.value = rawMessages.value + com.example.data.model.ChatMessageEntity(
      id = "msg_after",
      chatId = chatId,
      senderId = other.id,
      text = "Mensaje nuevo después de vaciar",
      timestamp = baseTs + 20_000L
    )
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Mensaje nuevo después de vaciar").assertIsDisplayed()
    composeTestRule.onNodeWithTag("empty_chat_privacy_notice").assertDoesNotExist()
  }

  @Test
  fun `preferences manager persists chatClearedAt and locallyHiddenMessageKeys`() = kotlinx.coroutines.runBlocking {
    val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    val prefs = com.example.data.local.PreferencesManager(context)
    val ts = 1_750_000_000_000L
    prefs.setChatClearedAt(
      memberId = "m_me",
      chatId = "chat_persisted",
      clearedAtMs = ts,
      extraHiddenMessageIds = listOf("m_skew_1")
    )
    prefs.addLocallyHiddenMessages("m_me", listOf("m_single_1", "m_single_2"))

    val clearedMap = prefs.chatClearedAtFlow.first()
    val hiddenKeys = prefs.locallyHiddenMessageKeysFlow.first()
    assertEquals(ts, clearedMap[com.example.data.local.PreferencesManager.chatClearedAtKey("m_me", "chat_persisted")])
    assertTrue(hiddenKeys.contains(com.example.data.local.PreferencesManager.hiddenMessageKey("m_me", "m_skew_1")))
    assertTrue(hiddenKeys.contains(com.example.data.local.PreferencesManager.hiddenMessageKey("m_me", "m_single_1")))
    assertTrue(hiddenKeys.contains(com.example.data.local.PreferencesManager.hiddenMessageKey("m_me", "m_single_2")))
  }

  @Test
  @Config(qualifiers = "w411dp-h891dp")
  fun `profile selector transitions cleanly from loading to retry or loaded profiles without sticking on spinner`() {
    val isLoading = androidx.compose.runtime.mutableStateOf(true)
    val membersState = androidx.compose.runtime.mutableStateOf<List<FamilyMemberEntity>>(emptyList())
    val snackbar = androidx.compose.runtime.mutableStateOf<String?>(null)
    var retryCount = 0

    composeTestRule.setContent {
      ProfileSelectorScreen(
        members = membersState.value,
        isMembersLoading = isLoading.value,
        snackbarMessage = snackbar.value,
        onClearSnackbar = { snackbar.value = null },
        onSelectMember = {},
        onRetryLoad = {
          retryCount++
          membersState.value = listOf(
            sampleMember(id = "m_admin", name = "José Manuel", isAdmin = true),
            sampleMember(id = "m_hijo", name = "Cristian Fernando", isAdmin = false)
          )
        }
      )
    }
    composeTestRule.waitForIdle()

    // Initial loading state shows spinner
    composeTestRule.onNodeWithTag("selector_loading_state").assertIsDisplayed()

    // Once loading finishes with empty list, spinner disappears and retry card stays visible even after snackbar clears
    isLoading.value = false
    snackbar.value = "Error temporal de red"
    composeTestRule.waitForIdle()
    snackbar.value = null
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("selector_loading_state").assertDoesNotExist()
    composeTestRule.onNodeWithTag("selector_empty_state").assertIsDisplayed()
    composeTestRule.onNodeWithTag("selector_retry_button").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()

    assertEquals(1, retryCount)
    composeTestRule.onNodeWithText("José Manuel").assertIsDisplayed()
    composeTestRule.onNodeWithText("Cristian Fernando").assertIsDisplayed()
  }

  @Test
  @Config(qualifiers = "w411dp-h891dp")
  fun `profile selector respects dark theme palette when dark mode is applied`() {
    val members = listOf(
      sampleMember(id = "m_admin", name = "José Manuel", isAdmin = true),
      sampleMember(id = "m_hijo", name = "Cristian Fernando", isAdmin = false)
    )
    composeTestRule.setContent {
      com.example.ui.theme.FamiChatTheme(darkTheme = true) {
        ProfileSelectorScreen(
          members = members,
          isMembersLoading = false,
          snackbarMessage = null,
          onClearSnackbar = {},
          onSelectMember = {},
          onRetryLoad = {}
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("profile_selector_root").assertIsDisplayed()
    composeTestRule.onNodeWithTag("profile_card_m_admin").assertIsDisplayed()
    composeTestRule.onNodeWithTag("profile_card_m_hijo").assertIsDisplayed()
    composeTestRule.onNodeWithText("Papá · con PIN").assertIsDisplayed()
  }

  @Test
  @Config(qualifiers = "w411dp-h891dp")
  fun `google sign in screen shows browser fallback option for devices without google play services`() {
    composeTestRule.setContent {
      com.example.ui.screens.GoogleSignInScreen(
        isDarkTheme = false,
        onToggleTheme = {},
        onSignedIn = {}
      )
    }
    composeTestRule.waitUntil(timeoutMillis = 6_000L) {
      composeTestRule.onAllNodesWithText("Continuar con Google").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("google_signin_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("google_browser_signin_button").assertIsDisplayed()
    composeTestRule.onNodeWithText("Entrar desde el navegador (sin Servicios de Google)").assertIsDisplayed()
  }

  @Test
  fun `preferences manager persists and restores initial session snapshot with cached family members`() = kotlinx.coroutines.runBlocking {
    val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    val prefs = com.example.data.local.PreferencesManager(context)
    val members = listOf(
      sampleMember(id = "m_admin", name = "José Manuel", isAdmin = true).copy(roleLabel = "Papá", avatarEmoji = "👨🏽"),
      sampleMember(id = "m_hijo_1", name = "Cristian", isAdmin = false).copy(roleLabel = "Hijo", avatarEmoji = "👦🏽"),
      sampleMember(id = "m_hijo_2", name = "Mateo", isAdmin = false).copy(roleLabel = "Hijo", avatarEmoji = "🧒🏽")
    )
    prefs.setActiveFamilyId("fam_fast_boot")
    prefs.setActiveMemberId("m_hijo_1")
    prefs.saveCachedFamilyMembers("fam_fast_boot", members)

    val snapshot = prefs.readInitialSessionSnapshot()
    assertEquals("fam_fast_boot", snapshot.activeFamilyId)
    assertEquals("m_hijo_1", snapshot.activeMemberId)
    assertEquals("fam_fast_boot", snapshot.cachedFamilyId)
    assertEquals(3, snapshot.cachedMembers.size)
    assertEquals("Cristian", snapshot.cachedMembers.first { it.id == "m_hijo_1" }.name)
    assertTrue(snapshot.cachedMembers.first { it.id == "m_admin" }.isAdmin)

    prefs.clearCachedFamilyMembers()
    val cleared = prefs.readInitialSessionSnapshot()
    assertTrue(cleared.cachedMembers.isEmpty())
  }

  @Test
  fun `realtime service notification channel is silent minimal and builds ongoing notification`() {
    val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    com.example.util.FamiChatNotificationHelper.ensureChannels(context)
    val manager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
    val realtimeChannel = manager.getNotificationChannel(com.example.util.FamiChatNotificationHelper.CHANNEL_REALTIME_SYNC)
    org.junit.Assert.assertNotNull(realtimeChannel)
    assertEquals(android.app.NotificationManager.IMPORTANCE_MIN, realtimeChannel.importance)
    assertFalse(realtimeChannel.canShowBadge())

    val notification = com.example.util.FamiChatNotificationHelper.buildRealtimeServiceNotification(context)
    assertTrue((notification.flags and android.app.Notification.FLAG_ONGOING_EVENT) != 0)
    assertEquals(com.example.util.FamiChatNotificationHelper.CHANNEL_REALTIME_SYNC, notification.channelId)
  }

  @Test
  @Config(qualifiers = "w411dp-h891dp")
  fun `my profile screen matches redesigned layout and saves changes only on button tap`() {
    var savedNickname: String? = null
    var savedColor: String? = null
    var savedOnline: Boolean? = null
    var savedStatus: String? = null
    var savedBio: String? = null
    var savedRemovePhoto: Boolean? = null
    var dismissed = false

    val adminMember = sampleMember(
      id = "m_admin",
      name = "José Manuel Martínez",
      isAdmin = true
    ).copy(
      avatarColorHex = "#1A5FD4",
      bioText = "",
      isOnline = true,
      lastSeenText = "Desconectado",
      avatarPhotoData = "data:image/jpeg;base64,/9j/4AAQSkZJRg=="
    )

    composeTestRule.setContent {
      com.example.ui.screens.MyProfileScreen(
        member = adminMember,
        onUpdateProfile = { nickname, colorHex, isOnline, statusText, bioText, _, removePhoto ->
          savedNickname = nickname
          savedColor = colorHex
          savedOnline = isOnline
          savedStatus = statusText
          savedBio = bioText
          savedRemovePhoto = removePhoto
        },
        onDismiss = { dismissed = true }
      )
    }
    composeTestRule.waitForIdle()

    // Full-screen header, admin pill, camera hint, and no old standalone photo buttons
    composeTestRule.onNodeWithTag("profile_options_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithText("Mi perfil").assertIsDisplayed()
    composeTestRule.onNodeWithTag("profile_admin_badge").assertIsDisplayed()
    composeTestRule.onNodeWithText("Administrador").assertIsDisplayed()
    composeTestRule.onNodeWithText("Toca la cámara para editar").assertIsDisplayed()
    composeTestRule.onNodeWithText("Tomar foto").assertDoesNotExist()
    composeTestRule.onNodeWithText("Elegir de galería").assertDoesNotExist()
    composeTestRule.onNodeWithText("Quitar foto").assertDoesNotExist()

    // 6 color swatches present and Save disabled while unchanged
    for (idx in 0 until 6) {
      composeTestRule.onNodeWithTag("profile_color_swatch_$idx").assertExists()
    }
    composeTestRule.onNodeWithTag("profile_save_status_button").assertIsNotEnabled()

    // No status chip selected initially when lastSeenText is Desconectado
    listOf("En línea", "En casa", "Ocupado", "En el trabajo", "En la escuela").forEach { chip ->
      composeTestRule.onNodeWithTag("profile_status_chip_$chip").assertIsNotSelected()
    }

    // Bio placeholder & 0/80 counter
    composeTestRule.onNodeWithText("Escribe algo que te represente").assertIsDisplayed()
    composeTestRule.onNodeWithTag("profile_bio_counter").assertTextEquals("0/80")

    // Photo menu opens on avatar tap with 3 options; Quitar foto asks confirmation first
    composeTestRule.onNodeWithTag("profile_avatar_photo_menu_trigger").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("profile_take_photo_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("profile_change_photo_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("profile_remove_photo_button").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("¿Quitar foto de perfil?").assertIsDisplayed()
    composeTestRule.onNodeWithTag("profile_remove_photo_confirm_button").performClick()
    composeTestRule.waitForIdle()

    // Select 3rd color, enter bio, select "En casa", toggle last seen switch OFF
    composeTestRule.onNodeWithTag("profile_color_swatch_2").performClick()
    composeTestRule.onNodeWithTag("profile_bio_input").performTextInput("Siempre con la familia")
    composeTestRule.onNodeWithTag("profile_status_chip_En casa").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("profile_bio_counter").assertTextEquals("22/80")
    composeTestRule.onNodeWithTag("profile_status_chip_En casa").assertIsSelected()
    composeTestRule.onNodeWithTag("profile_status_chip_En línea").assertIsNotSelected()

    composeTestRule.onNodeWithTag("profile_last_seen_subtitle")
      .assertTextEquals("La familia verá cuándo te conectaste.")
    composeTestRule.onNodeWithTag("profile_online_switch").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("profile_last_seen_subtitle")
      .assertTextEquals("Nadie verá cuándo te conectaste.")

    // Nothing saved until tapping Guardar cambios
    assertEquals(null, savedStatus)
    composeTestRule.onNodeWithTag("profile_save_status_button").assertIsEnabled().performClick()
    composeTestRule.waitForIdle()

    assertEquals("José Manuel Martínez", savedNickname)
    assertEquals("#0E7C66", savedColor)
    assertEquals(false, savedOnline)
    assertEquals("En casa", savedStatus)
    assertEquals("Siempre con la familia", savedBio)
    assertEquals(true, savedRemovePhoto)
    assertFalse(dismissed)
  }

  @Test
  @Config(qualifiers = "w411dp-h891dp")
  fun `my profile screen asks confirmation before closing with unsaved changes and hides admin badge for non-admin`() {
    var dismissCount = 0
    val regularMember = sampleMember(
      id = "m_member",
      name = "Sofía",
      isAdmin = false
    ).copy(
      avatarColorHex = "#6B4FD8",
      avatarPhotoData = "",
      lastSeenText = "Ocupado"
    )

    composeTestRule.setContent {
      com.example.ui.screens.MyProfileScreen(
        member = regularMember,
        onUpdateProfile = { _, _, _, _, _, _, _ -> },
        onDismiss = { dismissCount++ }
      )
    }
    composeTestRule.waitForIdle()

    // Non-admin does not show Administrador pill; saved status is pre-selected
    composeTestRule.onNodeWithTag("profile_admin_badge").assertDoesNotExist()
    composeTestRule.onNodeWithTag("profile_status_chip_Ocupado").assertIsSelected()

    // Without photo, photo menu omits "Quitar foto"
    composeTestRule.onNodeWithTag("profile_avatar_photo_menu_trigger").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("profile_take_photo_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("profile_change_photo_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("profile_remove_photo_button").assertDoesNotExist()
    // Dismiss menu by clicking avatar/closing
    composeTestRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
    composeTestRule.waitForIdle()

    // Make an edit, then tap X -> discard confirmation appears instead of closing directly
    composeTestRule.onNodeWithTag("profile_status_chip_En el trabajo").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("profile_close_button").performClick()
    composeTestRule.waitForIdle()

    assertEquals(0, dismissCount)
    composeTestRule.onNodeWithText("¿Salir sin guardar?").assertIsDisplayed()
    composeTestRule.onNodeWithTag("profile_discard_cancel_button").performClick()
    composeTestRule.waitForIdle()
    assertEquals(0, dismissCount)

    // Tap X again and confirm Salir -> dismisses
    composeTestRule.onNodeWithTag("profile_close_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("profile_discard_confirm_button").performClick()
    composeTestRule.waitForIdle()
    assertEquals(1, dismissCount)
  }

  @Test
  @Config(qualifiers = "w411dp-h891dp")
  fun `my profile last seen switch tracks showLastSeen independently of transient isOnline and hides contact timestamp when off`() {
    val memberWithShowEnabledWhileOffline = sampleMember(
      id = "m_priv_check",
      name = "Carlos",
      isAdmin = false
    ).copy(
      isOnline = false,
      showLastSeen = true,
      liveStatusAt = System.currentTimeMillis() - 180_000L,
      lastSeenText = "Últ. vez hace 3 min"
    )

    // Even when transient isOnline is false, showLastSeen=true keeps switch ON
    composeTestRule.setContent {
      com.example.ui.screens.MyProfileScreen(
        member = memberWithShowEnabledWhileOffline,
        onUpdateProfile = { _, _, _, _, _, _, _ -> },
        onDismiss = {}
      )
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("profile_last_seen_subtitle")
      .assertTextEquals("La familia verá cuándo te conectaste.")

    // When showLastSeen=false, contact info hides last seen timestamp completely
    val hiddenMember = memberWithShowEnabledWhileOffline.copy(showLastSeen = false)
    assertEquals(
      "",
      com.example.ui.screens.resolveContactLastSeen(hiddenMember, "Últ. vez hace 3 min")
    )
    assertTrue(
      com.example.ui.screens.resolveContactLastSeen(memberWithShowEnabledWhileOffline, "")
        .startsWith("Últ. vez")
    )
  }

  @Test
  fun `selected profile session persists atomically and partial member cache updates never drop active member`() = kotlinx.coroutines.test.runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = com.example.data.local.PreferencesManager(context)

    val admin = sampleMember(id = "m_admin_persist", name = "José Manuel", isAdmin = true)
    val sister = sampleMember(id = "m_sister_persist", name = "Ana", isAdmin = false)

    // Atomically save selected session as done in FamilyViewModel.selectProfile
    prefs.saveSelectedSession(
      familyId = "fam_persist_1",
      memberId = sister.id,
      members = listOf(admin, sister)
    )

    val snapshot1 = prefs.readInitialSessionSnapshot()
    assertEquals("fam_persist_1", snapshot1.activeFamilyId)
    assertEquals(sister.id, snapshot1.activeMemberId)
    assertEquals("fam_persist_1", snapshot1.cachedFamilyId)
    assertTrue(snapshot1.cachedMembers.any { it.id == sister.id })

    // Simulate a partial Firestore members snapshot that temporarily omits the active member
    prefs.saveCachedFamilyMembers(
      familyId = "fam_persist_1",
      members = listOf(admin),
      preserveMemberId = sister.id
    )

    val snapshot2 = prefs.readInitialSessionSnapshot()
    assertEquals("fam_persist_1", snapshot2.activeFamilyId)
    assertEquals(sister.id, snapshot2.activeMemberId)
    assertTrue(
      "Active member must remain in cachedMembers after partial snapshot so cold restart enters HOME directly",
      snapshot2.cachedMembers.any { it.id == sister.id }
    )
    assertTrue(snapshot2.cachedMembers.any { it.id == admin.id })
  }

  @Test
  fun `app updates screen hides repo editor hides technical details for non admin and expands read only details for admin`() {
    val isAdminState = androidx.compose.runtime.mutableStateOf(false)
    val updateState = androidx.compose.runtime.mutableStateOf<AppUpdateUiState>(
      AppUpdateUiState.UpToDate(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
    )
    var checkClicks = 0
    var cancelClicks = 0
    val repoSlug = "privado-owner/FamiChat-Privado"

    composeTestRule.setContent {
      com.example.ui.screens.AppUpdatesScreen(
        isAdmin = isAdminState.value,
        isDarkTheme = false,
        currentRepoSlug = repoSlug,
        updateState = updateState.value,
        lastCheckedAtMs = System.currentTimeMillis(),
        onCheckForUpdate = { checkClicks++ },
        onDownloadUpdate = {},
        onCancelDownload = { cancelClicks++ },
        onOpenWhatsNew = {},
        onBack = {}
      )
    }
    composeTestRule.waitForIdle()

    // Pantalla 1: Clean layout matching Image 1 (NO big status circle or "Estás al día" outside)
    composeTestRule.onNodeWithText("Actualizaciones").assertIsDisplayed()
    composeTestRule.onNodeWithText("Buscar actualizaciones").assertIsDisplayed()
    composeTestRule.onNodeWithText("Comprueba si hay una versión nueva de FamiChat").assertIsDisplayed()
    composeTestRule.onNodeWithText("Novedades de esta versión").assertIsDisplayed()
    composeTestRule.onNodeWithText("Mira lo nuevo que trae").assertIsDisplayed()
    composeTestRule.onNodeWithTag("updates_dialog_card").assertDoesNotExist()
    composeTestRule.onNodeWithTag("updates_row_new_badge_dot", useUnmergedTree = true).assertDoesNotExist()

    // "Cambiar repositorio", "Detalles técnicos", and repo slug must NOT appear for non-admin
    assertEquals(0, composeTestRule.onAllNodesWithText("Cambiar repositorio", substring = true, ignoreCase = true).fetchSemanticsNodes().size)
    assertEquals(0, composeTestRule.onAllNodesWithText("Detalles técnicos", substring = true, ignoreCase = true).fetchSemanticsNodes().size)
    assertEquals(0, composeTestRule.onAllNodesWithText(repoSlug, substring = true).fetchSemanticsNodes().size)

    // Switch to admin: "Detalles técnicos" with "Admin" pill appears collapsed; expanding reveals build & secure channel WITHOUT repo slug
    composeTestRule.runOnUiThread { isAdminState.value = true }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("updates_technical_details_toggle").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Admin").assertIsDisplayed()
    assertEquals(0, composeTestRule.onAllNodesWithText(repoSlug, substring = true).fetchSemanticsNodes().size)

    composeTestRule.onNodeWithTag("updates_technical_details_toggle").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Compilación: ${BuildConfig.VERSION_CODE}").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Canal de actualización: Configurado").performScrollTo().assertIsDisplayed()
    assertEquals(0, composeTestRule.onAllNodesWithText(repoSlug, substring = true).fetchSemanticsNodes().size)
    assertEquals(0, composeTestRule.onAllNodesWithText("Cambiar repositorio", substring = true, ignoreCase = true).fetchSemanticsNodes().size)

    // Cuadro 2: Tap "Buscar actualizaciones" when UpToDate -> opens "Estás al día" dialog
    composeTestRule.onNodeWithTag("updates_check_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertEquals(1, checkClicks)
    composeTestRule.onNodeWithTag("updates_state_up_to_date").assertIsDisplayed()
    composeTestRule.onNodeWithText("Estás al día").assertIsDisplayed()
    composeTestRule.onNodeWithText("Tienes la última versión: ${BuildConfig.VERSION_NAME}").assertIsDisplayed()
    composeTestRule.onNodeWithTag("updates_last_checked_text").assertIsDisplayed()
    composeTestRule.onNodeWithTag("updates_accept_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("updates_dialog_card").assertDoesNotExist()

    // Cuadro 3: UpdateAvailable -> red dot appears next to title; tapping opens dialog with Qué incluye, Actualizar ahora, Más tarde
    val release = com.example.util.AppReleaseInfo(
      tagName = "v2.9.0",
      versionName = "2.9.0",
      versionCode = BuildConfig.VERSION_CODE + 1,
      releaseNotes = "- Mejoras de llamadas y seguridad.\n- Ahorro de batería.",
      downloadUrl = "https://github.com/privado-owner/FamiChat-Privado/releases/download/v2.9.0/FamiChat.apk",
      releasePageUrl = "https://github.com/privado-owner/FamiChat-Privado/releases/tag/v2.9.0"
    )
    composeTestRule.runOnUiThread {
      updateState.value = AppUpdateUiState.UpdateAvailable(
        installedVersionName = BuildConfig.VERSION_NAME,
        installedVersionCode = BuildConfig.VERSION_CODE,
        release = release
      )
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("updates_row_new_badge_dot", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithTag("updates_check_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Hay una versión nueva").assertIsDisplayed()
    composeTestRule.onNodeWithText("Versión 2.9.0").assertIsDisplayed()
    composeTestRule.onNodeWithText("Qué incluye").assertIsDisplayed()
    composeTestRule.onNodeWithText("Mejoras de llamadas y seguridad.").assertIsDisplayed()
    composeTestRule.onNodeWithText("Actualizar ahora").assertIsDisplayed()
    composeTestRule.onNodeWithText("Más tarde").assertIsDisplayed()

    // Cuadro 4B: Downloading -> shows "Descargando versión 2.9.0", "42 %", "No cierres la app", and red "Cancelar"
    composeTestRule.runOnUiThread {
      updateState.value = AppUpdateUiState.Downloading(
        release = release,
        progressPercent = 42,
        downloadedBytes = 4L * 1024L * 1024L,
        totalBytes = 10L * 1024L * 1024L
      )
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Descargando versión 2.9.0").assertIsDisplayed()
    composeTestRule.onNodeWithText("42 %").assertIsDisplayed()
    composeTestRule.onNodeWithText("No cierres la app").assertIsDisplayed()
    composeTestRule.onNodeWithTag("updates_cancel_download_button").performClick()
    composeTestRule.waitForIdle()
    assertEquals(1, cancelClicks)
    composeTestRule.onNodeWithTag("updates_dialog_card").assertDoesNotExist()

    // Cuadro 4E: Error -> shows "No se pudo completar", "Sin conexión", "Reintentar", "Cerrar"
    composeTestRule.runOnUiThread {
      updateState.value = AppUpdateUiState.Error("Sin conexión a internet. Verifica tu red e inténtalo de nuevo.")
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("updates_check_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("No se pudo completar").assertIsDisplayed()
    composeTestRule.onNodeWithText("Sin conexión").assertIsDisplayed()
    composeTestRule.onNodeWithText("Reintentar").assertIsDisplayed()
    composeTestRule.onNodeWithText("Cerrar").assertIsDisplayed()
  }

  @Test
  fun `settings shows Actualizaciones row with update dot and home menu omits Buscar actualizacion`() {
    var openedUpdates = false
    val release = com.example.util.AppReleaseInfo(
      tagName = "v2.9.0",
      versionName = "2.9.0",
      versionCode = BuildConfig.VERSION_CODE + 1,
      releaseNotes = "Novedades",
      downloadUrl = "https://github.com/owner/repo/releases/download/v2.9.0/app.apk",
      releasePageUrl = "https://github.com/owner/repo/releases/tag/v2.9.0"
    )
    val availableState = AppUpdateUiState.UpdateAvailable(
      installedVersionName = BuildConfig.VERSION_NAME,
      installedVersionCode = BuildConfig.VERSION_CODE,
      release = release
    )

    val showHome = androidx.compose.runtime.mutableStateOf(false)
    val member = sampleMember(id = "m_u1", name = "José Manuel", isAdmin = true)

    composeTestRule.setContent {
      if (!showHome.value) {
        SettingsScreen(
          isAdmin = true,
          isDarkTheme = false,
          notificationsEnabled = true,
          updateRepoSlug = "owner/repo",
          updateState = availableState,
          onBack = {},
          onOpenNotifications = {},
          onToggleTheme = {},
          onCheckForUpdate = {},
          onSaveUpdateRepoSlug = {},
          onDownloadUpdate = {},
          onResetUpdateState = {},
          onOpenAdvanced = {},
          onOpenUpdates = { openedUpdates = true }
        )
      } else {
        MainHomeScreen(
          activeMember = member,
          allMembers = listOf(member),
          userGroupsCount = 1,
          isDarkTheme = false,
          notificationsEnabled = true,
          updateRepoSlug = "owner/repo",
          updateState = availableState,
          selectedTab = MainTab.CHATS,
          searchQuery = "",
          chatPreviews = emptyList(),
          statuses = emptyList(),
          calls = emptyList(),
          shoppingItems = emptyList(),
          events = emptyList(),
          notes = emptyList(),
          snackbarMessage = null,
          onClearSnackbar = {},
          onToggleTheme = {},
          onToggleNotifications = {},
          onUpdateOwnProfile = { _, _, _, _, _, _, _ -> },
          onPublishTextStatus = { _, _ -> },
          onPublishPhotoStatus = { _, _ -> },
          onMarkStatusViewed = {},
          onDeleteStatus = {},
          onReplyToStatus = { _, _ -> },
          onCheckForUpdate = {},
          onSaveUpdateRepoSlug = {},
          onDownloadUpdate = {},
          onResetUpdateState = {},
          onSelectTab = {},
          onSearchChange = {},
          onOpenChat = {},
          onStartDirectChat = {},
          onSwitchProfile = {},
          onOpenAdminPanel = {},
          onStartCall = { _, _, _, _, _ -> },
          onAddShoppingItem = { _, _, _ -> },
          onToggleShoppingItem = {},
          onDeleteShoppingItem = {},
          onClearCheckedShopping = {},
          onAddEvent = { _, _, _, _, _, _ -> },
          onDeleteEvent = {},
          onSaveNote = { _, _, _, _, _ -> },
          onDeleteNote = {}
        )
      }
    }
    composeTestRule.waitForIdle()

    // In Settings: row is "Actualizaciones", shows badge dot, and opens dedicated screen on tap
    composeTestRule.onNodeWithText("Actualizaciones").assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_update_row_badge_dot", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_update_row").performClick()
    assertTrue(openedUpdates)

    // In MainHomeScreen: 3-dot icon shows subtle blue dot, overflow menu shows clean Ajustes row and NO Buscar actualización
    composeTestRule.runOnUiThread { showHome.value = true }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("home_more_menu_update_dot", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithTag("home_more_menu_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("menu_settings").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("menu_check_update").assertDoesNotExist()
    assertEquals(0, composeTestRule.onAllNodesWithText("Buscar actualización", substring = true).fetchSemanticsNodes().size)
  }

  @Test
  @Config(qualifiers = "w411dp-h891dp")
  fun `settings shows Al dia without red dot when up to date and supports Appearance, FontSize, and WhatsNew subscreens`() {
    var selectedMode = com.example.data.local.AppThemeMode.SYSTEM
    var selectedScale = com.example.data.local.AppFontScale.NORMAL
    val screenState = androidx.compose.runtime.mutableStateOf("settings")
    val notesState = androidx.compose.runtime.mutableStateOf("")

    composeTestRule.setContent {
      when (screenState.value) {
        "settings" -> SettingsScreen(
          isAdmin = false,
          isDarkTheme = false,
          notificationsEnabled = false,
          updateRepoSlug = "",
          updateState = AppUpdateUiState.UpToDate(
            installedVersionName = BuildConfig.VERSION_NAME,
            installedVersionCode = BuildConfig.VERSION_CODE
          ),
          themeMode = selectedMode,
          fontScale = selectedScale,
          onBack = {},
          onOpenNotifications = {},
          onToggleTheme = {},
          onCheckForUpdate = {},
          onSaveUpdateRepoSlug = {},
          onDownloadUpdate = {},
          onResetUpdateState = {},
          onOpenAdvanced = {},
          onOpenUpdates = { screenState.value = "updates" },
          onOpenAppearance = { screenState.value = "appearance" },
          onOpenFontSize = { screenState.value = "font_size" },
          onOpenWhatsNew = { screenState.value = "whats_new" }
        )
        "updates" -> com.example.ui.screens.AppUpdatesScreen(
          isAdmin = false,
          isDarkTheme = false,
          currentRepoSlug = "",
          updateState = AppUpdateUiState.UpToDate(
            installedVersionName = BuildConfig.VERSION_NAME,
            installedVersionCode = BuildConfig.VERSION_CODE
          ),
          lastCheckedAtMs = System.currentTimeMillis(),
          onCheckForUpdate = {},
          onDownloadUpdate = {},
          onCancelDownload = {},
          onBack = { screenState.value = "settings" }
        )
        "appearance" -> com.example.ui.screens.AppearanceSettingsScreen(
          selectedMode = selectedMode,
          isDarkTheme = false,
          onSelectMode = { selectedMode = it },
          onBack = { screenState.value = "settings" }
        )
        "font_size" -> com.example.ui.screens.FontSizeSettingsScreen(
          selectedScale = selectedScale,
          isDarkTheme = false,
          onSelectScale = { selectedScale = it },
          onBack = { screenState.value = "settings" }
        )
        "whats_new" -> com.example.ui.screens.WhatsNewScreen(
          installedVersionName = BuildConfig.VERSION_NAME,
          versionReleaseNotes = notesState.value,
          isDarkTheme = false,
          onBack = { screenState.value = "settings" }
        )
      }
    }
    composeTestRule.waitForIdle()

    // Up to date shows "Al día" and NO red dot or "Nueva", and Novedades lives inside Actualizaciones
    composeTestRule.onNodeWithText("PREFERENCIAS").assertIsDisplayed()
    composeTestRule.onNodeWithText("APLICACIÓN").assertIsDisplayed()
    composeTestRule.onNodeWithText("Desactivadas", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("Automático", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("Normal", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("Al día", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_update_row_badge_dot", useUnmergedTree = true).assertDoesNotExist()
    composeTestRule.onNodeWithTag("settings_whats_new_row").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_share_app_row").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Compartir FamiChat").performScrollTo().assertIsDisplayed()
    assertEquals(0, composeTestRule.onAllNodesWithText("Nueva", useUnmergedTree = true).fetchSemanticsNodes().size)

    // Appearance sub-screen
    composeTestRule.onNodeWithTag("settings_appearance_row").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Claro").assertIsDisplayed()
    composeTestRule.onNodeWithText("Oscuro").assertIsDisplayed()
    composeTestRule.onNodeWithText("Automático (igual que el teléfono)").assertIsDisplayed()
    composeTestRule.onNodeWithTag("appearance_option_dark").performClick()
    assertEquals(com.example.data.local.AppThemeMode.DARK, selectedMode)
    composeTestRule.onNodeWithTag("appearance_back_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Oscuro", useUnmergedTree = true).assertIsDisplayed()

    // Font size sub-screen
    composeTestRule.onNodeWithTag("settings_font_size_row").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Así se verá el texto de tus mensajes.").assertIsDisplayed()
    composeTestRule.onNodeWithTag("font_size_slider").assertIsDisplayed()
    composeTestRule.onNodeWithTag("font_size_option_large").performClick()
    assertEquals(com.example.data.local.AppFontScale.LARGE, selectedScale)
    composeTestRule.onNodeWithTag("font_size_back_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Grande", useUnmergedTree = true).assertIsDisplayed()

    // Ajustes -> Actualizaciones opens clean Pantalla 1, and tapping Buscar actualizaciones shows Estás al día dialog
    composeTestRule.onNodeWithTag("settings_update_row").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("updates_check_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("updates_whats_new_row").assertIsDisplayed()
    composeTestRule.onNodeWithTag("updates_check_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Estás al día").assertIsDisplayed()
    composeTestRule.onNodeWithText("Tienes la última versión: ${BuildConfig.VERSION_NAME}").assertIsDisplayed()
    composeTestRule.onNodeWithTag("updates_accept_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("updates_back_button").performClick()
    composeTestRule.waitForIdle()

    // Ajustes -> Novedades de esta versión sub-screen: empty fallback and populated list
    composeTestRule.onNodeWithTag("settings_whats_new_row").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Versión ${BuildConfig.VERSION_NAME}").assertIsDisplayed()
    composeTestRule.onNodeWithText("No hay novedades para esta versión.").assertIsDisplayed()

    composeTestRule.runOnUiThread {
      notesState.value = "- Mejoras en Ajustes\n- Nuevo tamaño de letra"
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Mejoras en Ajustes").assertIsDisplayed()
    composeTestRule.onNodeWithText("Nuevo tamaño de letra").assertIsDisplayed()
  }

  @Test
  fun `preferences manager persists themeMode and fontScale locally`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = com.example.data.local.PreferencesManager(context)

    prefs.setThemeMode(com.example.data.local.AppThemeMode.DARK)
    prefs.setFontScale(com.example.data.local.AppFontScale.LARGE)

    assertEquals(com.example.data.local.AppThemeMode.DARK, prefs.themeModeFlow.first())
    assertEquals(com.example.data.local.AppFontScale.LARGE, prefs.fontScaleFlow.first())

    prefs.setThemeMode(com.example.data.local.AppThemeMode.SYSTEM)
    prefs.setFontScale(com.example.data.local.AppFontScale.SMALL)

    assertEquals(com.example.data.local.AppThemeMode.SYSTEM, prefs.themeModeFlow.first())
    assertEquals(com.example.data.local.AppFontScale.SMALL, prefs.fontScaleFlow.first())
  }

  @Test
  @Config(qualifiers = "w411dp-h891dp")
  fun `home overflow menu matches redesigned 4 row layout for admin, 3 rows for non admin, and conditional switch lock`() {
    val isAdminState = androidx.compose.runtime.mutableStateOf(true)
    val switchPinActiveState = androidx.compose.runtime.mutableStateOf(true)
    var switched = false
    var openedSettings = false
    var openedAdmin = false

    composeTestRule.setContent {
      val member = sampleMember(
        id = if (isAdminState.value) "m_admin" else "m_member",
        name = if (isAdminState.value) "José Manuel" else "Tila del Carmen",
        isAdmin = isAdminState.value
      )
      MainHomeScreen(
        activeMember = member,
        allMembers = listOf(member),
        userGroupsCount = 1,
        isDarkTheme = false,
        notificationsEnabled = true,
        updateRepoSlug = "",
        updateState = AppUpdateUiState.Idle,
        selectedTab = MainTab.CHATS,
        searchQuery = "",
        chatPreviews = emptyList(),
        statuses = emptyList(),
        calls = emptyList(),
        shoppingItems = emptyList(),
        events = emptyList(),
        notes = emptyList(),
        snackbarMessage = null,
        onClearSnackbar = {},
        onToggleTheme = {},
        onToggleNotifications = {},
        onUpdateOwnProfile = { _, _, _, _, _, _, _ -> },
        onPublishTextStatus = { _, _ -> },
        onPublishPhotoStatus = { _, _ -> },
        onMarkStatusViewed = {},
        onDeleteStatus = {},
        onReplyToStatus = { _, _ -> },
        onCheckForUpdate = {},
        onSaveUpdateRepoSlug = {},
        onDownloadUpdate = {},
        onResetUpdateState = {},
        onSelectTab = {},
        onSearchChange = {},
        onOpenChat = {},
        onStartDirectChat = {},
        onSwitchProfile = { switched = true },
        onOpenAdminPanel = { openedAdmin = true },
        onStartCall = { _, _, _, _, _ -> },
        onAddShoppingItem = { _, _, _ -> },
        onToggleShoppingItem = {},
        onDeleteShoppingItem = {},
        onClearCheckedShopping = {},
        onAddEvent = { _, _, _, _, _, _ -> },
        onDeleteEvent = {},
        onSaveNote = { _, _, _, _, _ -> },
        onDeleteNote = {},
        onOpenSettings = { openedSettings = true },
        isSwitchPinActive = switchPinActiveState.value
      )
    }
    composeTestRule.waitForIdle()

    // Open menu as Admin with Switch PIN active -> 4 rows + lock icon
    composeTestRule.onNodeWithTag("home_more_menu_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("home_more_menu").assertIsDisplayed()
    composeTestRule.onNodeWithText("Mi perfil", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("Foto, estado y frase", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("Cambiar de perfil", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("Pide la clave de seguridad", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithTag("menu_switch_profile_lock", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("Ajustes", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("Notificaciones, tema y más", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("Administración", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("Solo para el administrador", useUnmergedTree = true).assertIsDisplayed()

    // No separate Notificaciones or Tema oscuro rows inside the 3-dot menu
    composeTestRule.onNodeWithTag("menu_toggle_push_notifications").assertDoesNotExist()
    composeTestRule.onNodeWithTag("menu_toggle_theme").assertDoesNotExist()

    // Tapping Administración fires callback and dismisses menu
    composeTestRule.onNodeWithTag("menu_admin_panel").performClick()
    composeTestRule.waitForIdle()
    assertTrue(openedAdmin)
    composeTestRule.onNodeWithTag("home_more_menu").assertDoesNotExist()

    // Switch to non-admin & inactive PIN -> only 3 rows, no Administración, no lock icon
    composeTestRule.runOnUiThread {
      isAdminState.value = false
      switchPinActiveState.value = false
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("home_more_menu_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("menu_profile_options").assertIsDisplayed()
    composeTestRule.onNodeWithTag("menu_switch_profile").assertIsDisplayed()
    composeTestRule.onNodeWithTag("menu_settings").assertIsDisplayed()
    composeTestRule.onNodeWithTag("menu_admin_panel").assertDoesNotExist()
    composeTestRule.onNodeWithTag("menu_switch_profile_lock", useUnmergedTree = true).assertDoesNotExist()

    // Tapping Cambiar de perfil and Ajustes trigger their callbacks
    composeTestRule.onNodeWithTag("menu_switch_profile").performClick()
    composeTestRule.waitForIdle()
    assertTrue(switched)

    composeTestRule.onNodeWithTag("home_more_menu_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("menu_settings").performClick()
    composeTestRule.waitForIdle()
    assertTrue(openedSettings)
  }

  @Test
  @Config(qualifiers = "w411dp-h891dp")
  fun `my profile screen adapts cleanly to dark theme without losing controls or save flow`() {
    val member = sampleMember(id = "m_dark", name = "José Manuel Martínez", isAdmin = true)
    var savedNickname = ""
    composeTestRule.setContent {
      com.example.ui.theme.FamiChatTheme(darkTheme = true) {
        com.example.ui.screens.MyProfileScreen(
          member = member,
          onUpdateProfile = { nick, _, _, _, _, _, _ -> savedNickname = nick },
          onDismiss = {}
        )
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("profile_options_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithText("Mi perfil").assertIsDisplayed()
    composeTestRule.onNodeWithTag("profile_admin_badge").assertIsDisplayed()
    composeTestRule.onNodeWithTag("profile_online_switch").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("profile_save_status_button").assertIsNotEnabled()

    composeTestRule.onNodeWithTag("profile_status_chip_En casa").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("profile_save_status_button").assertIsEnabled().performClick()
    assertEquals("José Manuel Martínez", savedNickname)
  }

  @Test
  @Config(qualifiers = "w411dp-h891dp")
  fun `global snackbar host displays confirmation immediately on AdminPanelScreen`() {
    val snackbarMessageState = androidx.compose.runtime.mutableStateOf<String?>(null)
    composeTestRule.setContent {
      com.example.ui.theme.FamiChatTheme(darkTheme = true) {
        val hostState = androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() }
        androidx.compose.runtime.LaunchedEffect(snackbarMessageState.value) {
          val msg = snackbarMessageState.value ?: return@LaunchedEffect
          hostState.showSnackbar(msg)
        }
        androidx.compose.foundation.layout.Box(
          modifier = androidx.compose.ui.Modifier.fillMaxSize()
        ) {
          com.example.ui.screens.AdminPanelScreen(
            members = listOf(sampleMember(id = "m_admin", name = "José Manuel", isAdmin = true)),
            chats = emptyList(),
            currentMemberId = "m_admin",
            familyName = "Mi Familia",
            inviteCode = "NIDO01",
            currentMasterPin = "1234",
            currentSwitchPin = "2580",
            currentUpdateRepo = "",
            onBack = {},
            onSaveMember = { _, _, _, _, _, _, _, _, _, _ -> },
            onDeleteMember = {},
            onSaveGroup = { _, _, _, _, _ -> },
            onDeleteGroup = {},
            onUpdateMasterPin = { snackbarMessageState.value = "Contraseña actualizada en este dispositivo" },
            onUpdateSwitchPin = {},
            onUpdateRepoSlug = {},
            onChangeFamilySpace = {}
          )
          androidx.compose.material3.SnackbarHost(
            hostState = hostState,
            modifier = androidx.compose.ui.Modifier
              .align(androidx.compose.ui.Alignment.BottomCenter)
              .testTag("global_snackbar_host")
          )
        }
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.runOnUiThread {
      snackbarMessageState.value = "Contraseña actualizada en este dispositivo"
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("Panel de Administración").assertIsDisplayed()
    composeTestRule.onNodeWithText("Contraseña actualizada en este dispositivo").assertIsDisplayed()
  }

  @Test
  @Config(qualifiers = "w411dp-h891dp")
  fun `whats new screen displays bundled release notes for current version offline`() {
    val bundled = BuildConfig.VERSION_RELEASE_NOTES.trim()
    assertTrue("BuildConfig.VERSION_RELEASE_NOTES should not be blank for current version", bundled.isNotBlank())
    val firstBullet = bundled.lines()
      .map { it.trim().removePrefix("-").removePrefix("•").removePrefix("*").trim() }
      .firstOrNull { it.isNotBlank() }
      .orEmpty()
    assertTrue("Bundled notes should contain at least one non-empty bullet", firstBullet.isNotBlank())
    assertFalse("Bundled notes should not contain raw unicode escape sequences", bundled.contains("\\u"))

    composeTestRule.setContent {
      com.example.ui.theme.FamiChatTheme(darkTheme = true) {
        com.example.ui.screens.WhatsNewScreen(
          installedVersionName = BuildConfig.VERSION_NAME,
          versionReleaseNotes = bundled,
          isDarkTheme = true,
          onBack = {}
        )
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("whats_new_screen").assertIsDisplayed()
    composeTestRule.onNodeWithText("Versión ${BuildConfig.VERSION_NAME}").assertIsDisplayed()
    composeTestRule.onNodeWithTag("whats_new_empty_text").assertDoesNotExist()
    composeTestRule.onNodeWithText(firstBullet).assertIsDisplayed()
  }

  @Test
  @Config(qualifiers = "w411dp-h891dp")
  fun `magic pill check updates button switches to Buscando with white spinner and disables while checking`() {
    val updateState = androidx.compose.runtime.mutableStateOf<AppUpdateUiState>(
      AppUpdateUiState.UpToDate(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
    )
    var clicks = 0

    composeTestRule.setContent {
      com.example.ui.theme.FamiChatTheme(darkTheme = true) {
        com.example.ui.screens.AppUpdatesScreen(
          isAdmin = false,
          isDarkTheme = true,
          currentRepoSlug = "privado-owner/FamiChat-Privado",
          updateState = updateState.value,
          lastCheckedAtMs = System.currentTimeMillis(),
          onCheckForUpdate = {
            clicks++
            updateState.value = AppUpdateUiState.Checking
          },
          onDownloadUpdate = {},
          onCancelDownload = {},
          onBack = {}
        )
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("updates_check_button").assertIsDisplayed().assertIsEnabled()
    composeTestRule.onNodeWithText("Buscar actualizaciones").assertIsDisplayed()
    composeTestRule.onNodeWithTag("updates_checking_indicator").assertDoesNotExist()

    composeTestRule.onNodeWithTag("updates_check_button").performClick()
    composeTestRule.waitForIdle()

    assertEquals(1, clicks)
    composeTestRule.onNodeWithTag("updates_dialog_card").assertDoesNotExist()
    composeTestRule.onNodeWithTag("updates_check_button").assertIsNotEnabled()
    composeTestRule.onNodeWithTag("updates_state_checking").assertIsDisplayed()
    composeTestRule.onNodeWithText("Buscando actualizaciones…", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithTag("updates_checking_indicator", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithTag("updates_checking_progress_bar").assertIsDisplayed()
  }

  @Test
  @Config(qualifiers = "w411dp-h891dp")
  fun `realtime background service defaults to false and notifications screen toggles it cleanly`() = kotlinx.coroutines.test.runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = com.example.data.local.PreferencesManager(context)
    assertFalse("Persistent FamiChat activo service should be OFF by default when server push is active", prefs.realtimeBackgroundServiceEnabledFlow.first())
    prefs.setRealtimeBackgroundServiceEnabled(true)
    assertTrue(prefs.realtimeBackgroundServiceEnabledFlow.first())
    prefs.setRealtimeBackgroundServiceEnabled(false)
    assertFalse(prefs.realtimeBackgroundServiceEnabledFlow.first())

    val realtimeEnabled = androidx.compose.runtime.mutableStateOf(false)
    composeTestRule.setContent {
      NotificationSettingsScreen(
        notificationsEnabled = true,
        realtimeBackgroundEnabled = realtimeEnabled.value,
        snackbarMessage = null,
        onClearSnackbar = {},
        onToggleNotifications = {},
        onToggleRealtimeBackground = { realtimeEnabled.value = it },
        onSendTestNotification = {},
        onBack = {}
      )
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("Conexión permanente (sin Google)").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("notifications_realtime_bg_switch").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertTrue(realtimeEnabled.value)
  }

  @Test
  @Config(qualifiers = "w411dp-h891dp")
  fun `app updates screen hides whats included card when release notes are empty handles version history and postpones for 24h`() = kotlinx.coroutines.test.runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = com.example.data.local.PreferencesManager(context)

    val emptyRelease = com.example.util.AppReleaseInfo(
      tagName = "v2.9.1",
      versionName = "2.9.1",
      versionCode = BuildConfig.VERSION_CODE + 1,
      releaseNotes = "",
      downloadUrl = "https://github.com/owner/repo/releases/download/v2.9.1/FamiChat.apk",
      releasePageUrl = "https://github.com/owner/repo/releases/tag/v2.9.1"
    )
    val updateState = androidx.compose.runtime.mutableStateOf<AppUpdateUiState>(
      AppUpdateUiState.UpdateAvailable(
        installedVersionName = BuildConfig.VERSION_NAME,
        installedVersionCode = BuildConfig.VERSION_CODE,
        release = emptyRelease
      )
    )
    val historyState = androidx.compose.runtime.mutableStateOf<com.example.util.VersionHistoryUiState>(
      com.example.util.VersionHistoryUiState.Idle
    )
    var historyRequested = 0
    var postponeClicks = 0

    composeTestRule.setContent {
      com.example.ui.theme.FamiChatTheme(
        darkTheme = true,
        fontScaleMultiplier = com.example.data.local.AppFontScale.LARGE.multiplier
      ) {
        com.example.ui.screens.AppUpdatesScreen(
          isAdmin = false,
          isDarkTheme = true,
          currentRepoSlug = "owner/repo",
          updateState = updateState.value,
          lastCheckedAtMs = System.currentTimeMillis(),
          versionHistoryState = historyState.value,
          onCheckForUpdate = {},
          onDownloadUpdate = {},
          onCancelDownload = {},
          onLoadVersionHistory = { historyRequested++ },
          onPostponeUpdate = { postponeClicks++ },
          onBack = {}
        )
      }
    }
    composeTestRule.waitForIdle()

    // Red dot is visible next to Buscar actualizaciones; tap to open UpdateAvailable dialog
    composeTestRule.onNodeWithTag("updates_row_new_badge_dot", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithTag("updates_check_button").performClick()
    composeTestRule.waitForIdle()

    // Empty release notes -> NO "Qué incluye" section shown (works cleanly with LARGE font scale)
    composeTestRule.onNodeWithText("Hay una versión nueva").assertIsDisplayed()
    composeTestRule.onNodeWithText("Versión 2.9.1").assertIsDisplayed()
    composeTestRule.onNodeWithTag("updates_whats_included_card").assertDoesNotExist()
    assertEquals(0, composeTestRule.onAllNodesWithText("Qué incluye").fetchSemanticsNodes().size)

    // Tap "Más tarde" -> invokes postpone callback, closes dialog, and keeps red dot on Pantalla 1
    composeTestRule.onNodeWithTag("updates_later_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertEquals(1, postponeClicks)
    composeTestRule.onNodeWithTag("updates_dialog_card").assertDoesNotExist()
    composeTestRule.onNodeWithTag("updates_row_new_badge_dot", useUnmergedTree = true).assertIsDisplayed()

    val snoozedUntil = System.currentTimeMillis() + com.example.util.AppUpdateChecker.DAILY_CHECK_INTERVAL_MS
    prefs.setUpdateSnoozedUntil(snoozedUntil)
    assertTrue(com.example.util.AppUpdateChecker.isUpdatePromptSnoozed(prefs.updateSnoozedUntilFlow.first()))

    val history = listOf(
      com.example.util.VersionHistoryEntry("2.8.33", listOf("Ahorro de batería", "Sin duplicados")),
      com.example.util.VersionHistoryEntry("2.8.32", emptyList())
    )
    val encoded = com.example.util.AppUpdateChecker.encodeVersionHistoryJson(history)
    prefs.setCachedVersionHistoryJson(encoded)
    assertEquals(history, com.example.util.AppUpdateChecker.decodeVersionHistoryJson(prefs.getCachedVersionHistoryJson()))
  }

  @Test
  @Config(qualifiers = "w411dp-h891dp")
  fun `boot display prefs mirror and local startup timeline display in admin diagnostics only`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = com.example.data.local.PreferencesManager(context)

    kotlinx.coroutines.runBlocking {
      // 1. Saving theme & fontScale mirrors immediately into SharedPreferences for synchronous pre-frame read
      prefs.setThemeMode(com.example.data.local.AppThemeMode.DARK)
      prefs.setFontScale(com.example.data.local.AppFontScale.LARGE)
    }
    val fastBoot = prefs.readBootDisplayPrefsSync()
    assertTrue(fastBoot.fromFastCache)
    assertEquals(com.example.data.local.AppThemeMode.DARK, fastBoot.themeMode)
    assertEquals(com.example.data.local.AppFontScale.LARGE, fastBoot.fontScale)

    // 2. Clearing SharedPreferences mirror falls back once to DataStore and backfills the mirror
    context.getSharedPreferences("famichat_boot_display_prefs", Context.MODE_PRIVATE)
      .edit()
      .clear()
      .commit()
    val fallbackBoot = prefs.readBootDisplayPrefsSync()
    assertFalse(fallbackBoot.fromFastCache)
    assertEquals(com.example.data.local.AppThemeMode.DARK, fallbackBoot.themeMode)
    assertEquals(com.example.data.local.AppFontScale.LARGE, fallbackBoot.fontScale)
    assertTrue(prefs.readBootDisplayPrefsSync().fromFastCache)

    // 3. StartupTimelineTracker records onCreate counts, ViewModel counts, UID change, and all startup events locally
    com.example.util.StartupTimelineTracker.resetForTest(context)
    com.example.util.StartupTimelineTracker.recordOnCreate(context, hasSavedInstanceState = false)
    com.example.util.StartupTimelineTracker.recordThemeApplied(context, "Oscuro", "Grande", fromFastCache = true)
    com.example.util.StartupTimelineTracker.recordFirebaseSessionReady(context, "uid_alpha_001", alreadySignedIn = true)
    com.example.util.StartupTimelineTracker.recordViewModelCreated(context, "uid_alpha_001")
    com.example.util.StartupTimelineTracker.recordSessionRestored(context, hasActiveProfile = true)
    com.example.util.StartupTimelineTracker.recordFirstScreenVisible(context, "Chats")

    val cleanSnap = com.example.util.StartupTimelineTracker.readSnapshot(context)
    assertEquals(1, cleanSnap.onCreateCount)
    assertEquals(1, cleanSnap.viewModelCreateCount)
    assertFalse(cleanSnap.uidChanged)
    assertEquals(6, cleanSnap.events.size)

    // Simulate a second onCreate & ViewModel creation with a different UID
    com.example.util.StartupTimelineTracker.recordOnCreate(context, hasSavedInstanceState = true)
    com.example.util.StartupTimelineTracker.recordViewModelCreated(context, "uid_beta_999")
    val recreatedSnap = com.example.util.StartupTimelineTracker.readSnapshot(context)
    assertEquals(2, recreatedSnap.onCreateCount)
    assertEquals(2, recreatedSnap.viewModelCreateCount)
    assertTrue(recreatedSnap.uidChanged)

    // 4. AdminDiagnosticsScreen displays the startup timeline card for admin
    composeTestRule.setContent {
      AdminDiagnosticsScreen(
        isAdmin = true,
        familyId = "fam_123",
        memberName = "José Manuel Martínez",
        notificationsEnabled = true,
        fcmToken = "fcm_tok",
        turnUrl = "turn:openrelay.metered.ca:80",
        turnUsername = "openrelayproject",
        turnCredential = "openrelayproject",
        snackbarMessage = null,
        onClearSnackbar = {},
        onSendTestNotification = {},
        onSaveTurnConfig = { _, _, _ -> },
        onBack = {}
      )
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("diagnostics_startup_timeline_card").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Línea de tiempo del último arranque").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("onCreate (#1)").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Primera pantalla visible").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("copy_startup_timeline_button").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun `boot session mirror restores active profile synchronously and stays in sync on logout and backfill`() {
    val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<Context>()
    val prefs = com.example.data.local.PreferencesManager(context)
    val members = listOf(
      FamilyMemberEntity(
        id = "m_admin",
        name = "José Manuel Martínez",
        roleLabel = "Papá",
        isAdmin = true,
        avatarColorHex = "#1A73E8",
        avatarEmoji = "👨🏻"
      ),
      FamilyMemberEntity(
        id = "m_mom",
        name = "Mamá",
        roleLabel = "Mamá",
        isAdmin = false,
        avatarColorHex = "#34A853",
        avatarEmoji = "👩🏻"
      )
    )

    // 1. Fresh device with empty DataStore & empty mirror returns empty snapshot and marks mirror initialized
    kotlinx.coroutines.runBlocking {
      prefs.setActiveFamilyId(null)
      prefs.setActiveMemberId(null)
      prefs.clearCachedFamilyMembers()
    }
    context.getSharedPreferences("famichat_boot_session_prefs", Context.MODE_PRIVATE)
      .edit()
      .clear()
      .commit()
    val emptySnap = prefs.readInitialSessionSnapshotSync()
    assertTrue(emptySnap != null)
    assertEquals(null, emptySnap?.activeMemberId)

    // 2. Saving selected session populates the synchronous mirror immediately
    kotlinx.coroutines.runBlocking {
      prefs.saveSelectedSession("fam_fast_01", "m_admin", members)
    }
    val fastSnap = prefs.readInitialSessionSnapshotSync()
    assertTrue(fastSnap != null)
    assertEquals("fam_fast_01", fastSnap!!.activeFamilyId)
    assertEquals("m_admin", fastSnap.activeMemberId)
    assertEquals("fam_fast_01", fastSnap.cachedFamilyId)
    assertEquals(2, fastSnap.cachedMembers.size)

    // 3. Clearing mirror (simulating first open after update) falls back once to DataStore synchronously
    // and backfills the mirror immediately so no second loading screen flashes
    context.getSharedPreferences("famichat_boot_session_prefs", Context.MODE_PRIVATE)
      .edit()
      .clear()
      .commit()
    val upgradedSnap = prefs.readInitialSessionSnapshotSync()
    assertEquals("fam_fast_01", upgradedSnap?.activeFamilyId)
    assertEquals("m_admin", upgradedSnap?.activeMemberId)
    assertEquals(2, upgradedSnap?.cachedMembers?.size)
    // Mirror is now initialized for instant subsequent reads
    assertTrue(
      context.getSharedPreferences("famichat_boot_session_prefs", Context.MODE_PRIVATE)
        .getBoolean("initialized", false)
    )

    // 4. Logging out to selector clears activeMemberId in mirror while keeping family & cached members
    kotlinx.coroutines.runBlocking {
      prefs.setActiveMemberId(null)
    }
    val selectorSnap = prefs.readInitialSessionSnapshotSync()
    assertEquals("fam_fast_01", selectorSnap?.activeFamilyId)
    assertEquals(null, selectorSnap?.activeMemberId)
    assertEquals(2, selectorSnap?.cachedMembers?.size)

    // 5. Clearing cached members removes cached members from mirror
    kotlinx.coroutines.runBlocking {
      prefs.clearCachedFamilyMembers()
    }
    val clearedSnap = prefs.readInitialSessionSnapshotSync()
    assertTrue(clearedSnap?.cachedMembers.orEmpty().isEmpty())
  }

  @Test
  @Config(qualifiers = "w411dp-h891dp")
  fun `contact info screen adapts cleanly when dark theme is applied`() {
    val me = sampleMember(id = "m_me", name = "José Manuel", isAdmin = true)
    val contact = sampleMember(id = "m_mom", name = "Tila del Carmen").copy(
      bioText = "Siempre aquí",
      isOnline = true,
      lastSeenText = "En casa",
      liveStatusAt = System.currentTimeMillis()
    )
    val chatUi = com.example.ui.viewmodel.ChatThreadUi(
      chat = com.example.data.model.ChatThreadEntity(
        id = "direct_tila",
        type = com.example.data.model.ChatType.DIRECT,
        title = contact.name,
        avatarColorHex = contact.avatarColorHex,
        avatarEmoji = contact.avatarEmoji,
        participantIdsJson = "[\"${me.id}\",\"${contact.id}\"]"
      ),
      displayTitle = contact.name,
      displayEmoji = contact.avatarEmoji,
      displayColorHex = contact.avatarColorHex,
      partnerBioText = contact.bioText,
      isOnline = true,
      subtitleStatus = "En línea",
      lastMessagePreview = "Hola",
      lastMessageTime = "10:00",
      unreadCount = 0,
      lastMessageStatus = null,
      isLastMessageFromMe = false,
      participants = listOf(me, contact),
      rightStatusText = "En línea"
    )

    composeTestRule.setContent {
      com.example.ui.theme.FamiChatTheme(darkTheme = true) {
        com.example.ui.screens.ContactInfoScreen(
          chatUi = chatUi,
          activeMember = me,
          allMembers = listOf(me, contact),
          visibleMessages = emptyList(),
          calls = emptyList(),
          selectedWallpaperKey = "default",
          selectedNotificationToneUri = "",
          isContactBlocked = false,
          onSelectWallpaper = {},
          onSelectNotificationTone = {},
          onTogglePinChat = {},
          onToggleMuteChat = {},
          onStartCall = {},
          onSearchInChat = {},
          onOpenImage = {},
          onOpenDocument = {},
          onRequestClearChat = {},
          onRequestToggleBlockContact = {},
          onBack = {}
        )
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("contact_info_screen").assertIsDisplayed()
    composeTestRule.onNodeWithText("Tila del Carmen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("contact_info_status_pill").assertIsDisplayed()
  }

  @Test
  @Config(qualifiers = "w411dp-h891dp")
  fun `status composer opens on text tab preserves text across tabs asks discard only with content and respects photo video gallery`() {
    var closedCount = 0
    val publishCalls = mutableListOf<com.example.ui.viewmodel.StatusPublishRequest>()

    composeTestRule.setContent {
      com.example.ui.theme.FamiChatTheme(darkTheme = false) {
        com.example.ui.components.StatusComposerScreen(
          publishState = com.example.ui.viewmodel.StatusPublishUiState.Idle,
          onPublish = { publishCalls.add(it) },
          onCancelPublish = {},
          onDismissPublishError = {},
          onClose = { closedCount++ }
        )
      }
    }
    composeTestRule.waitForIdle()

    // Starts on Texto tab with empty prompt, 0/280, 24h badge, disabled Publicar
    composeTestRule.onNodeWithTag("status_composer_screen").assertIsDisplayed()
    composeTestRule.onNodeWithText("Se borra en 24 horas").assertIsDisplayed()
    composeTestRule.onNodeWithText("Escribe tu estado…").assertIsDisplayed()
    composeTestRule.onNodeWithText("0/280").assertIsDisplayed()
    composeTestRule.onNodeWithTag("publish_status_button").assertIsNotEnabled()

    // Empty close button closes immediately without discard confirmation
    composeTestRule.onNodeWithTag("status_close_button").performClick()
    composeTestRule.waitForIdle()
    assertEquals(1, closedCount)
    composeTestRule.onNodeWithTag("status_discard_dialog").assertDoesNotExist()

    // Select a different color, enter text, verify counter and enabled publish button
    composeTestRule.onNodeWithTag("status_color_option_1").performClick()
    composeTestRule.onNodeWithTag("status_text_input").performTextInput("Hoy cenamos en casa")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("19/280").assertIsDisplayed()
    composeTestRule.onNodeWithTag("publish_status_button").assertIsEnabled()

    // Switch to Foto and Video galleries and verify titles/tiles, then return to Texto with preserved text
    composeTestRule.onNodeWithTag("status_tab_photo").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Recientes").assertIsDisplayed()
    composeTestRule.onNodeWithTag("status_take_photo_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("status_pick_gallery_button").assertIsDisplayed()

    composeTestRule.onNodeWithTag("status_tab_video").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Videos").assertIsDisplayed()
    composeTestRule.onNodeWithTag("status_record_video_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("status_pick_video_button").assertIsDisplayed()

    composeTestRule.onNodeWithTag("status_tab_text").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Hoy cenamos en casa").assertIsDisplayed()

    // Closing with text shows discard confirmation; Keep Editing keeps state, Discard closes
    composeTestRule.onNodeWithTag("status_close_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("status_discard_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithText("¿Descartar este estado?").assertIsDisplayed()
    composeTestRule.onNodeWithText("Se perderá lo que escribiste.").assertIsDisplayed()
    composeTestRule.onNodeWithTag("status_discard_keep_editing").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("status_discard_dialog").assertDoesNotExist()

    // Publish invokes callback with entered text and chosen color (#1A5FD4)
    composeTestRule.onNodeWithTag("publish_status_button").performClick()
    composeTestRule.waitForIdle()
    assertEquals(1, publishCalls.size)
    assertEquals("Hoy cenamos en casa", publishCalls.first().text)
    assertEquals("#1A5FD4", publishCalls.first().backgroundHex)

    // Confirm Discard closes composer
    composeTestRule.onNodeWithTag("status_close_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("status_discard_confirm").performClick()
    composeTestRule.waitForIdle()
    assertEquals(2, closedCount)
  }

  @Test
  @Config(qualifiers = "w411dp-h891dp")
  fun `status composer uploading cancel and offline retry dialogs follow theme without losing text`() {
    val publishState = androidx.compose.runtime.mutableStateOf<com.example.ui.viewmodel.StatusPublishUiState>(
      com.example.ui.viewmodel.StatusPublishUiState.Publishing(
        mode = com.example.ui.viewmodel.StatusComposerMode.VIDEO,
        progress = 0.62f
      )
    )
    var cancelCalls = 0
    var dismissErrorCalls = 0
    var retryCount = 0

    composeTestRule.setContent {
      com.example.ui.theme.FamiChatTheme(darkTheme = true) {
        com.example.ui.components.StatusComposerScreen(
          publishState = publishState.value,
          onPublish = { retryCount++ },
          onCancelPublish = { cancelCalls++ },
          onDismissPublishError = { dismissErrorCalls++ },
          onClose = {}
        )
      }
    }
    composeTestRule.waitForIdle()

    // Uploading dialog displays title, percentage, and Cancel
    composeTestRule.onNodeWithTag("status_uploading_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithText("Subiendo video…").assertIsDisplayed()
    composeTestRule.onNodeWithText("62 %").assertIsDisplayed()
    composeTestRule.onNodeWithTag("status_upload_cancel_button").performClick()
    assertEquals(1, cancelCalls)

    // Switch to error state -> shows offline message, Retry & Close
    publishState.value = com.example.ui.viewmodel.StatusPublishUiState.Error("Sin conexión. Tu estado no se perdió.")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("status_publish_error_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithText("No se pudo publicar").assertIsDisplayed()
    composeTestRule.onNodeWithText("Sin conexión. Tu estado no se perdió.").assertIsDisplayed()
    composeTestRule.onNodeWithTag("status_publish_error_close_button").performClick()
    assertEquals(1, dismissErrorCalls)
  }

  @Test
  @Config(qualifiers = "w411dp-h891dp")
  fun `status composer shows gallery permission prompt when denied and opens camera roll dropdown when granted`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val cameraCacheDir = java.io.File(app.cacheDir, "camera_photos").apply { mkdirs() }
    val samplePhoto = java.io.File(cameraCacheDir, "test_status_roll.jpg").apply {
      writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte()))
    }
    try {
      org.robolectric.Shadows.shadowOf(app).denyPermissions(
        android.Manifest.permission.READ_MEDIA_IMAGES,
        android.Manifest.permission.READ_MEDIA_VIDEO,
        android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
      )

      composeTestRule.setContent {
        com.example.ui.theme.FamiChatTheme(darkTheme = false) {
          com.example.ui.components.StatusComposerScreen(
            publishState = com.example.ui.viewmodel.StatusPublishUiState.Idle,
            onPublish = {},
            onCancelPublish = {},
            onDismissPublishError = {},
            onClose = {}
          )
        }
      }
      composeTestRule.waitForIdle()

      // Denied state displays permission CTA alongside Cámara + Galería fallback
      composeTestRule.onNodeWithTag("status_tab_photo").performClick()
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithTag("status_take_photo_button").assertIsDisplayed()
      composeTestRule.onNodeWithTag("status_pick_gallery_button").assertIsDisplayed()
      composeTestRule.onNodeWithTag("status_request_gallery_permission_button").assertIsDisplayed()

      // Grant permissions and reopen composer -> direct camera roll hides permission CTA & Recientes opens dropdown
      org.robolectric.Shadows.shadowOf(app).grantPermissions(
        android.Manifest.permission.READ_MEDIA_IMAGES,
        android.Manifest.permission.READ_MEDIA_VIDEO
      )
      composeTestRule.onNodeWithTag("status_tab_video").performClick()
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithTag("status_tab_photo").performClick()
      composeTestRule.waitForIdle()

      composeTestRule.onNodeWithTag("status_request_gallery_permission_button").assertDoesNotExist()
      composeTestRule.onNodeWithTag("status_gallery_title_recents").performClick()
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithTag("status_album_dropdown").assertIsDisplayed()
      composeTestRule.onNodeWithText("Selector del sistema").assertIsDisplayed()
    } finally {
      samplePhoto.delete()
    }
  }

  @Test
  @Config(qualifiers = "w411dp-h891dp")
  fun `status composer video tab prompts for video access when only partial photo access was granted`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    org.robolectric.Shadows.shadowOf(app).denyPermissions(
      android.Manifest.permission.READ_MEDIA_IMAGES,
      android.Manifest.permission.READ_MEDIA_VIDEO
    )
    org.robolectric.Shadows.shadowOf(app).grantPermissions(
      android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    )

    composeTestRule.setContent {
      com.example.ui.theme.FamiChatTheme(darkTheme = false) {
        com.example.ui.components.StatusComposerScreen(
          publishState = com.example.ui.viewmodel.StatusPublishUiState.Idle,
          onPublish = {},
          onCancelPublish = {},
          onDismissPublishError = {},
          onClose = {}
        )
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("status_tab_video").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("status_record_video_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("status_pick_video_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("status_request_gallery_permission_button").assertIsDisplayed()
    composeTestRule.onNodeWithText("Permitir acceso a videos").assertIsDisplayed()
  }
}






