package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.FamilyMemberEntity
import com.example.data.model.PresenceStatus
import com.example.util.AppUpdateChecker
import com.example.util.MessageFormatter
import com.example.util.PresenceResolver
import com.example.util.TimeFormatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("FamiChat", appName)
  }

  @Test
  fun `message formatter strips markdown delimiters`() {
    val formatted = MessageFormatter.formatRichText("*Hola* _familia_ ~ayer~")
    assertEquals("Hola familia ayer", formatted.text)
  }

  @Test
  fun `chat format helper wraps and toggles selected text only`() {
    val initial = androidx.compose.ui.text.input.TextFieldValue(
      text = "Hola familia",
      selection = androidx.compose.ui.text.TextRange(5, 12)
    )
    val bolded = com.example.ui.screens.applyFormatToSelection(initial, "*")
    assertEquals("Hola *familia*", bolded.text)
    assertEquals(androidx.compose.ui.text.TextRange(6, 13), bolded.selection)

    val unbolded = com.example.ui.screens.applyFormatToSelection(bolded, "*")
    assertEquals("Hola familia", unbolded.text)
    assertEquals(androidx.compose.ui.text.TextRange(5, 12), unbolded.selection)
  }

  @Test
  fun `app update checker parses versions and normalizes repo url`() {
    assertEquals(
      "miusuario/Mensajer-a-Familiar-",
      AppUpdateChecker.normalizeRepoSlug("https://github.com/miusuario/Mensajer-a-Familiar-/releases")
    )
    assertEquals(
      "miusuario/FamiChat",
      AppUpdateChecker.normalizeRepoSlug("https://github.com/miusuario/FamiChat.git")
    )
    val (name, code) = AppUpdateChecker.parseReleaseVersion("v1.2-c3", "FamiChat v1.2 (Build 3)")
    assertEquals("1.2", name)
    assertEquals(3, code)
    assertTrue(AppUpdateChecker.isRemoteNewer(2, "1.1", 3, "1.2"))
    assertFalse(AppUpdateChecker.isRemoteNewer(2, "1.1", 2, "1.1"))
    val sampleHtml = """
      <a href="/miusuario/Mensajer-a-Familiar-/releases/download/v2.0.1-c6/FamiChat-v2.0.1-c6-debug.apk">APK</a>
    """.trimIndent()
    assertEquals(
      "https://github.com/miusuario/Mensajer-a-Familiar-/releases/download/v2.0.1-c6/FamiChat-v2.0.1-c6-debug.apk",
      AppUpdateChecker.extractApkUrlFromHtml(sampleHtml, "miusuario/Mensajer-a-Familiar-")
    )
    val fullUrlHtml = """
      <a href='https://github.com/miusuario/FamiChat/releases/download/v2.1.1-c12/FamiChat-v2.1.1-c12-debug.apk?raw=1'>APK</a>
    """.trimIndent()
    assertEquals(
      "https://github.com/miusuario/FamiChat/releases/download/v2.1.1-c12/FamiChat-v2.1.1-c12-debug.apk?raw=1",
      AppUpdateChecker.extractApkUrlFromHtml(fullUrlHtml, "miusuario/FamiChat")
    )
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
  fun `app update checker returns NotConfigured when repo slug is blank`() = kotlinx.coroutines.test.runTest {
    val result = AppUpdateChecker.checkLatestRelease("", "2.1.0", 11)
    assertTrue(result is com.example.util.AppUpdateUiState.NotConfigured)
  }

  @Test
  fun `presence resolver reports never connected for members without device session`() {
    val neverConnectedLegacy = FamilyMemberEntity(
      id = "m_mama",
      name = "Mamá",
      roleLabel = "Mamá",
      isAdmin = false,
      avatarColorHex = "#1A73E8",
      avatarEmoji = "👩🏽",
      isOnline = true,
      lastSeenText = "En línea",
      liveStatus = PresenceStatus.ONLINE,
      liveStatusAt = 0L,
      linkedAuthUid = ""
    )

    val resolved = PresenceResolver.resolve(neverConnectedLegacy, nowMs = 1_700_000_000_000L)
    assertFalse(resolved.isOnline)
    assertEquals("No se ha conectado", resolved.statusText)
  }

  @Test
  fun `presence resolver reflects active heartbeat and expires stale heartbeat to last seen`() {
    val now = 1_700_000_000_000L
    val connectedMember = FamilyMemberEntity(
      id = "m_hijo",
      name = "Hijo",
      roleLabel = "Hijo",
      isAdmin = false,
      avatarColorHex = "#1A73E8",
      avatarEmoji = "🧑🏽",
      isOnline = true,
      lastSeenText = "En línea",
      liveStatus = PresenceStatus.ONLINE,
      liveStatusAt = now - 20_000L,
      linkedAuthUid = "uid_hijo"
    )

    val active = PresenceResolver.resolve(connectedMember, nowMs = now)
    assertTrue(active.isOnline)
    assertEquals("En línea", active.statusText)

    val stale = PresenceResolver.resolve(
      connectedMember,
      nowMs = now + PresenceResolver.ONLINE_TTL_MS + 10_000L
    )
    assertFalse(stale.isOnline)
    assertTrue(stale.statusText.startsWith("Últ. vez"))

    val explicitOffline = connectedMember.copy(
      isOnline = false,
      liveStatus = PresenceStatus.LAST_SEEN,
      lastSeenText = "Ocupado",
      liveStatusAt = now - 120_000L
    )
    val away = PresenceResolver.resolve(explicitOffline, nowMs = now)
    assertFalse(away.isOnline)
    assertTrue(away.statusText.startsWith("Ocupado · Últ. vez"))
    assertEquals("Últ. vez hace 2 min", TimeFormatters.formatLastSeen(now - 120_000L, now))
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
  fun `push registration manager builds valid FCM topic and member stores fcmTokens`() {
    val topic = com.example.push.PushRegistrationManager.topicForFamily("fam-123/abc")
    assertEquals("famichat_fam-123_abc", topic)
    val member = FamilyMemberEntity(
      id = "m1",
      name = "Mamá",
      roleLabel = "Mamá",
      isAdmin = false,
      avatarColorHex = "#1A73E8",
      avatarEmoji = "👩🏽",
      fcmTokens = listOf("token_1", "token_2")
    )
    assertEquals(2, member.fcmTokens.size)
  }

  @Test
  fun `link preview helper extracts urls and document media helper formats file sizes`() {
    val url = com.example.util.LinkPreviewHelper.extractFirstUrl("Mira esta receta https://cocina.ejemplo.com/receta?id=42!).")
    assertEquals("https://cocina.ejemplo.com/receta?id=42", url)
    org.junit.Assert.assertNull(com.example.util.LinkPreviewHelper.extractFirstUrl("Mensaje sin enlace web"))

    assertEquals("512 B", com.example.util.DocumentMediaHelper.formatFileSize(512L))
    assertEquals("2.0 KB", com.example.util.DocumentMediaHelper.formatFileSize(2048L))

    val candidate = com.example.data.model.CallIceCandidateEntity(
      id = "cand_1",
      senderMemberId = "m_papa",
      sdpMid = "0",
      sdpMLineIndex = 0,
      candidate = "candidate:1 1 UDP 2122252543 192.168.1.10 54321 typ host"
    )
    assertEquals("m_papa", candidate.senderMemberId)
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
    org.junit.Assert.assertSame(model1, model2)
  }
}

