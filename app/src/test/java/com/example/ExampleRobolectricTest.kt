package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.ui.screens.AdminDiagnosticsScreen
import com.example.ui.screens.NotificationSettingsScreen
import com.example.ui.screens.SettingsScreen
import com.example.util.AppUpdateChecker
import com.example.util.AppUpdateUiState
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
  val composeTestRule = createComposeRule()

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
  fun `settings hides Avanzado for non admin and shows it for admin`() {
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
    composeTestRule.onNodeWithTag("settings_advanced_row").assertDoesNotExist()

    composeTestRule.runOnUiThread { isAdminState.value = true }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_advanced_row").performScrollTo().assertIsDisplayed()
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
}

