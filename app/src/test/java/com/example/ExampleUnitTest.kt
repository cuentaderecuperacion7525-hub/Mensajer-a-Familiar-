package com.example

import com.example.data.model.CallIceCandidateEntity
import com.example.data.model.FamilyMemberEntity
import com.example.data.model.PresenceStatus
import com.example.push.PushRegistrationManager
import com.example.ui.components.extractAvatarInitials
import com.example.ui.screens.applyFormatToSelection
import com.example.ui.screens.formatShortProfileName
import com.example.ui.screens.resolveProfileSelectorColors
import com.example.ui.viewmodel.isDuplicateLegacyPapaProfile
import com.example.ui.viewmodel.validateMemberBeforeSave
import com.example.util.AppUpdateChecker
import com.example.util.AppUpdateUiState
import com.example.util.DocumentMediaHelper
import com.example.util.EmojiMessageHelper
import com.example.util.LinkPreviewHelper
import com.example.util.MessageFormatter
import com.example.util.NotoAnimatedEmojiSession
import com.example.util.PresenceResolver
import com.example.util.TimeFormatters
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Local pure-JVM unit tests (do not require Robolectric SDK artifacts).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
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
    val bolded = applyFormatToSelection(initial, "*")
    assertEquals("Hola *familia*", bolded.text)
    assertEquals(androidx.compose.ui.text.TextRange(6, 13), bolded.selection)

    val unbolded = applyFormatToSelection(bolded, "*")
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
  fun `app update checker returns NotConfigured when repo slug is blank`() = runTest {
    val result = AppUpdateChecker.checkLatestRelease("", "2.1.0", 11)
    assertTrue(result is AppUpdateUiState.NotConfigured)
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
  fun `presence resolver hides online and last seen when showLastSeen is false`() {
    val now = 1_700_000_000_000L
    val hiddenDefault = FamilyMemberEntity(
      id = "m_priv",
      name = "Mamá",
      roleLabel = "Mamá",
      isAdmin = false,
      avatarColorHex = "#B0306A",
      avatarEmoji = "👩🏽",
      isOnline = true,
      showLastSeen = false,
      lastSeenText = "En línea",
      liveStatus = PresenceStatus.ONLINE,
      liveStatusAt = now - 5_000L,
      linkedAuthUid = "uid_mama"
    )
    val resolvedHidden = PresenceResolver.resolve(hiddenDefault, nowMs = now)
    assertFalse(resolvedHidden.isOnline)
    assertEquals("", resolvedHidden.statusText)

    val hiddenWithCustomStatus = hiddenDefault.copy(lastSeenText = "En el trabajo")
    val resolvedCustom = PresenceResolver.resolve(hiddenWithCustomStatus, nowMs = now)
    assertFalse(resolvedCustom.isOnline)
    assertEquals("En el trabajo", resolvedCustom.statusText)

    // Local active user in foreground with showLastSeen=true is online even if cached isOnline was false
    val localReopened = hiddenDefault.copy(isOnline = false, showLastSeen = true, lastSeenText = "Desconectado")
    val resolvedLocal = PresenceResolver.resolve(localReopened, nowMs = now, isLocalActiveInForeground = true)
    assertTrue(resolvedLocal.isOnline)
    assertEquals("En línea", resolvedLocal.statusText)
  }

  @Test
  fun `push registration manager builds valid FCM topic and member stores fcmTokens`() {
    val topic = PushRegistrationManager.topicForFamily("fam-123/abc")
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
    val url = LinkPreviewHelper.extractFirstUrl("Mira esta receta https://cocina.ejemplo.com/receta?id=42!).")
    assertEquals("https://cocina.ejemplo.com/receta?id=42", url)
    assertNull(LinkPreviewHelper.extractFirstUrl("Mensaje sin enlace web"))

    assertEquals("512 B", DocumentMediaHelper.formatFileSize(512L))
    assertEquals("2.0 KB", DocumentMediaHelper.formatFileSize(2048L))

    val candidate = CallIceCandidateEntity(
      id = "cand_1",
      senderMemberId = "m_papa",
      sdpMid = "0",
      sdpMLineIndex = 0,
      candidate = "candidate:1 1 UDP 2122252543 192.168.1.10 54321 typ host"
    )
    assertEquals("m_papa", candidate.senderMemberId)
  }

  @Test
  fun `extractAvatarInitials computes expected initials for Spanish family names`() {
    assertEquals("JM", extractAvatarInitials("José Manuel", "👨🏽"))
    assertEquals("TC", extractAvatarInitials("Tila del Carmen", "👩🏽"))
    assertEquals("CF", extractAvatarInitials("Cristian Fernando", "🧑🏽"))
    assertEquals("C", extractAvatarInitials("Cristopher", "🧑🏽"))
    assertEquals("AJ", extractAvatarInitials("Ángel de Jesús", "🧑🏽"))
  }

  @Test
  fun `profile selector formats short names and assigns non repeating colors`() {
    assertEquals("José Manuel", formatShortProfileName("José Manuel Martínez"))
    assertEquals("Tila del Carmen", formatShortProfileName("Tila del Carmen"))
    assertEquals("Ángel de Jesús", formatShortProfileName("Ángel de Jesús"))
    assertEquals("Cristian Fernando", formatShortProfileName("Cristian Fernando"))
    assertEquals("Cristopher", formatShortProfileName("Cristopher"))

    val members = listOf(
      FamilyMemberEntity("m1", "José Manuel Martínez", "Papá", true, "#1A73E8", "👨🏽"),
      FamilyMemberEntity("m2", "Mama", "Mamá", false, "#1A73E8", "👩🏽"),
      FamilyMemberEntity("m3", "Cristopher", "Hijo", false, "", "🧑🏽"),
      FamilyMemberEntity("m4", "Cristian Fernando", "Hijo", false, "#1A73E8", "🧑🏽")
    )
    val resolved = resolveProfileSelectorColors(members)
    assertEquals(4, resolved.values.toSet().size)
    assertEquals("#6B4FD8", resolved["m2"])
    assertEquals("#C94521", resolved["m3"])
    assertEquals("#B0306A", resolved["m4"])
  }

  @Test
  fun `duplicate legacy papa detector and member validator enforce rules accurately`() {
    val admin = FamilyMemberEntity("m_admin", "José Manuel Martínez", "Papá", true, "#1A5FD4", "👨🏽")
    val dupPapa = FamilyMemberEntity("m_dup", "Papa", "Familiar", false, "#1A73E8", "👨🏽")
    val mama = FamilyMemberEntity("m_mama", "Mama", "Mamá", false, "#6B4FD8", "👩🏽")
    val cristopher = FamilyMemberEntity("m_cris1", "Cristopher", "Hijo", false, "#C94521", "🧑🏽")
    val cristian = FamilyMemberEntity("m_cris2", "Cristian Fernando", "Hijo mayor", false, "#B0306A", "🧑🏽")
    val all = listOf(admin, dupPapa, mama, cristopher, cristian)

    assertTrue(isDuplicateLegacyPapaProfile(dupPapa, all))
    assertFalse(isDuplicateLegacyPapaProfile(admin, all))
    assertFalse(isDuplicateLegacyPapaProfile(mama, all))
    assertFalse(isDuplicateLegacyPapaProfile(cristopher, all))
    assertFalse(isDuplicateLegacyPapaProfile(cristian, all))

    // Non-admin blocked
    assertTrue(validateMemberBeforeSave(all, null, "Abuela", "Abuela", isCallerAdmin = false) != null)
    // Blank role blocked
    assertTrue(validateMemberBeforeSave(all, null, "Abuela", "  ", isCallerAdmin = true) != null)
    // Duplicate name blocked (accent/case insensitive)
    assertTrue(validateMemberBeforeSave(all, null, "Mamá", "Tía", isCallerAdmin = true) != null)
    // Repeated role (e.g. multiple profiles with "Hijo") is allowed when name is unique
    assertNull(validateMemberBeforeSave(all, null, "Carlos", "Hijo", isCallerAdmin = true))
    assertNull(validateMemberBeforeSave(all, null, "Ángel de Jesús", "Hijo", isCallerAdmin = true))
    // Editing same member keeps passing
    assertTrue(validateMemberBeforeSave(all, "m_mama", "Mamá", "Mamá", isCallerAdmin = true) == null)
  }

  @Test
  fun `formatLastSeen shows short Spanish day and month when older than yesterday`() {
    val calNow = java.util.Calendar.getInstance().apply {
      set(2026, java.util.Calendar.NOVEMBER, 18, 15, 10, 0)
      set(java.util.Calendar.MILLISECOND, 0)
    }
    val calSameYearOlder = java.util.Calendar.getInstance().apply {
      set(2026, java.util.Calendar.NOVEMBER, 15, 14, 30, 0)
      set(java.util.Calendar.MILLISECOND, 0)
    }
    val calPrevYear = java.util.Calendar.getInstance().apply {
      set(2025, java.util.Calendar.OCTOBER, 9, 8, 5, 0)
      set(java.util.Calendar.MILLISECOND, 0)
    }

    assertEquals(
      "Últ. vez 15 nov 14:30",
      TimeFormatters.formatLastSeen(calSameYearOlder.timeInMillis, calNow.timeInMillis)
    )
    assertEquals(
      "Últ. vez 9 oct 2025",
      TimeFormatters.formatLastSeen(calPrevYear.timeInMillis, calNow.timeInMillis)
    )
    assertEquals(
      "No se ha conectado",
      TimeFormatters.formatLastSeen(0L, calNow.timeInMillis)
    )
  }

  @Test
  fun `contact info formatters and status resolvers match Spanish specs`() {
    val calNow = java.util.Calendar.getInstance().apply {
      set(2026, java.util.Calendar.NOVEMBER, 18, 18, 0, 0)
      set(java.util.Calendar.MILLISECOND, 0)
    }
    val calMemberSince = java.util.Calendar.getInstance().apply {
      set(2026, java.util.Calendar.NOVEMBER, 10, 9, 0, 0)
      set(java.util.Calendar.MILLISECOND, 0)
    }
    val calYesterdayCall = java.util.Calendar.getInstance().apply {
      set(2026, java.util.Calendar.NOVEMBER, 17, 17, 40, 0)
      set(java.util.Calendar.MILLISECOND, 0)
    }
    assertEquals("10 nov 2026", TimeFormatters.formatDayMonthYear(calMemberSince.timeInMillis))
    assertEquals("Ayer 5:40 pm", TimeFormatters.formatCallDateTime(calYesterdayCall.timeInMillis, calNow.timeInMillis))
    assertEquals("12 min", TimeFormatters.formatCallDurationShort(720))
    assertEquals("45 seg", TimeFormatters.formatCallDurationShort(45))
    assertEquals("", TimeFormatters.formatCallDurationShort(0))

    val atHomeMember = FamilyMemberEntity(
      id = "m_1",
      name = "Tila del Carmen",
      roleLabel = "Mamá",
      isAdmin = false,
      avatarColorHex = "#6B4FD8",
      avatarEmoji = "👩🏽",
      isOnline = true,
      lastSeenText = "En casa",
      liveStatusAt = calNow.timeInMillis
    )
    assertEquals("En casa", com.example.ui.screens.resolveContactStatusPill(atHomeMember))

    val offlineNoStatus = atHomeMember.copy(
      isOnline = false,
      lastSeenText = "Últ. vez 16 nov 18:52",
      liveStatusAt = calYesterdayCall.timeInMillis
    )
    assertNull(com.example.ui.screens.resolveContactStatusPill(offlineNoStatus))
  }

  @Test
  fun `emoji message helper detects 1 to 3 emojis including compound sequences and rejects text or 4+`() {
    // 1 emoji cases (single, VS16, skin tone, flag, ZWJ family, ZWJ heart on fire, ZWJ woman technologist, keycap, rainbow flag)
    assertEquals(listOf("😎"), EmojiMessageHelper.parseEmojiOnlyMessage("😎"))
    assertEquals(listOf("❤️"), EmojiMessageHelper.parseEmojiOnlyMessage("  ❤️  "))
    assertEquals(listOf("👍🏽"), EmojiMessageHelper.parseEmojiOnlyMessage("👍🏽"))
    assertEquals(listOf("🇲🇽"), EmojiMessageHelper.parseEmojiOnlyMessage("🇲🇽"))
    assertEquals(listOf("👨‍👩‍👧‍👦"), EmojiMessageHelper.parseEmojiOnlyMessage("👨‍👩‍👧‍👦"))
    assertEquals(listOf("❤️‍🔥"), EmojiMessageHelper.parseEmojiOnlyMessage("❤️‍🔥"))
    assertEquals(listOf("👩🏽‍💻"), EmojiMessageHelper.parseEmojiOnlyMessage("👩🏽‍💻"))
    assertEquals(listOf("1️⃣"), EmojiMessageHelper.parseEmojiOnlyMessage("1️⃣"))
    assertEquals(listOf("🏳️‍🌈"), EmojiMessageHelper.parseEmojiOnlyMessage("🏳️‍🌈"))

    // 2 and 3 emoji cases (with and without spaces)
    assertEquals(listOf("😎", "😎"), EmojiMessageHelper.parseEmojiOnlyMessage("😎😎"))
    assertEquals(listOf("👍🏽", "🇲🇽"), EmojiMessageHelper.parseEmojiOnlyMessage("👍🏽  🇲🇽"))
    assertEquals(listOf("😎", "❤️", "🔥"), EmojiMessageHelper.parseEmojiOnlyMessage("😎❤️🔥"))
    assertEquals(listOf("👨‍👩‍👧‍👦", "👍🏽", "🇲🇽"), EmojiMessageHelper.parseEmojiOnlyMessage("👨‍👩‍👧‍👦 👍🏽 🇲🇽"))

    // 4+ emojis or mixed text / plain digits / blank must return empty (normal bubble)
    assertTrue(EmojiMessageHelper.parseEmojiOnlyMessage("😎😎😎😎").isEmpty())
    assertTrue(EmojiMessageHelper.parseEmojiOnlyMessage("hola 😎").isEmpty())
    assertTrue(EmojiMessageHelper.parseEmojiOnlyMessage("😎!").isEmpty())
    assertTrue(EmojiMessageHelper.parseEmojiOnlyMessage("123").isEmpty())
    assertTrue(EmojiMessageHelper.parseEmojiOnlyMessage("#*").isEmpty())
    assertTrue(EmojiMessageHelper.parseEmojiOnlyMessage("").isEmpty())
    assertTrue(EmojiMessageHelper.parseEmojiOnlyMessage("   ").isEmpty())

    // Display sizing
    assertEquals(64, EmojiMessageHelper.emojiDisplaySizeDp(1))
    assertEquals(48, EmojiMessageHelper.emojiDisplaySizeDp(2))
    assertEquals(40, EmojiMessageHelper.emojiDisplaySizeDp(3))

    // Noto Animated Emoji CDN URL formatting
    assertEquals("1f60e", EmojiMessageHelper.emojiToNotoCodepointSequence("😎"))
    assertEquals(
      "https://fonts.gstatic.com/s/e/notoemoji/latest/1f60e/512.webp",
      EmojiMessageHelper.buildNotoAnimatedWebpUrl("😎")
    )
    assertEquals("2764", EmojiMessageHelper.emojiToNotoCodepointSequence("❤️"))
    assertEquals("1f44d_1f3fd", EmojiMessageHelper.emojiToNotoCodepointSequence("👍🏽"))
    assertEquals("0031_20e3", EmojiMessageHelper.emojiToNotoCodepointSequence("1️⃣"))

    // Session play-once tracker
    NotoAnimatedEmojiSession.clearForTest()
    assertTrue(NotoAnimatedEmojiSession.consumeShouldAutoPlay("msg_emoji_1"))
    assertFalse(NotoAnimatedEmojiSession.consumeShouldAutoPlay("msg_emoji_1"))
    assertTrue(NotoAnimatedEmojiSession.consumeShouldAutoPlay("msg_emoji_2"))
  }

  @Test
  fun `status time formatter matches Novedades spec`() {
    val calNow = java.util.Calendar.getInstance().apply {
      set(2026, java.util.Calendar.NOVEMBER, 18, 18, 0, 0)
      set(java.util.Calendar.MILLISECOND, 0)
    }
    val calTodayPm = java.util.Calendar.getInstance().apply {
      set(2026, java.util.Calendar.NOVEMBER, 18, 16, 51, 0)
      set(java.util.Calendar.MILLISECOND, 0)
    }
    val calYesterdayPm = java.util.Calendar.getInstance().apply {
      set(2026, java.util.Calendar.NOVEMBER, 17, 17, 58, 0)
      set(java.util.Calendar.MILLISECOND, 0)
    }

    assertEquals("4:51 p. m.", TimeFormatters.formatStatusTime(calTodayPm.timeInMillis, calNow.timeInMillis))
    assertEquals("Ayer 5:58 p. m.", TimeFormatters.formatStatusTime(calYesterdayPm.timeInMillis, calNow.timeInMillis))
  }

  @Test
  fun `webrtc hd call tuning picks 720p with graceful fallback and enables opus fec`() {
    val hdProfile = com.example.calls.WebRtcCallManager.selectOptimalCaptureProfile(
      listOf(320 to 240, 640 to 480, 1280 to 720, 1920 to 1080)
    )
    assertEquals(1280, hdProfile.width)
    assertEquals(720, hdProfile.height)
    assertEquals(30, hdProfile.fps)

    val fallbackProfile = com.example.calls.WebRtcCallManager.selectOptimalCaptureProfile(
      listOf(320 to 240, 640 to 480, 960 to 540)
    )
    assertEquals(960, fallbackProfile.width)
    assertEquals(540, fallbackProfile.height)

    val rawSdp = listOf(
      "v=0",
      "m=audio 9 UDP/TLS/RTP/SAVPF 111",
      "a=rtpmap:111 opus/48000/2",
      "a=fmtp:111 minptime=10;useinbandfec=0"
    ).joinToString("\r\n")
    val tuned = com.example.calls.WebRtcCallManager.tuneSdpForHdQuality(rawSdp)
    assertTrue(tuned.contains("useinbandfec=1"))
    assertTrue(tuned.contains("usedtx=1"))
    assertTrue(tuned.contains("maxaveragebitrate=48000"))

    val noFmtpSdp = listOf(
      "v=0",
      "a=rtpmap:111 opus/48000/2"
    ).joinToString("\r\n")
    val tunedInserted = com.example.calls.WebRtcCallManager.tuneSdpForHdQuality(noFmtpSdp)
    assertTrue(tunedInserted.contains("a=fmtp:111 "))
    assertTrue(tunedInserted.contains("useinbandfec=1"))
  }

  @Test
  fun `app update checker enforces trusted HTTPS GitHub URLs daily throttling and friendly last checked labels`() {
    assertTrue(
      AppUpdateChecker.isTrustedDownloadUrl(
        "https://github.com/owner/repo/releases/download/v2.8.23/FamiChat.apk"
      )
    )
    assertTrue(
      AppUpdateChecker.isTrustedDownloadUrl(
        "https://objects.githubusercontent.com/github-production-release-asset/123/FamiChat.apk"
      )
    )
    assertFalse(
      AppUpdateChecker.isTrustedDownloadUrl(
        "http://github.com/owner/repo/releases/download/v2.8.23/FamiChat.apk"
      )
    )
    assertFalse(AppUpdateChecker.isTrustedDownloadUrl("https://evil.example.com/FamiChat.apk"))
    assertFalse(AppUpdateChecker.isTrustedDownloadUrl("https://github.com.evil.com/FamiChat.apk"))
    assertFalse(AppUpdateChecker.isTrustedDownloadUrl(""))

    val calNow = java.util.Calendar.getInstance().apply {
      set(2026, java.util.Calendar.NOVEMBER, 20, 14, 0, 0)
      set(java.util.Calendar.MILLISECOND, 0)
    }
    val calToday913 = java.util.Calendar.getInstance().apply {
      set(2026, java.util.Calendar.NOVEMBER, 20, 9, 13, 0)
      set(java.util.Calendar.MILLISECOND, 0)
    }
    val calYesterday1805 = java.util.Calendar.getInstance().apply {
      set(2026, java.util.Calendar.NOVEMBER, 19, 18, 5, 0)
      set(java.util.Calendar.MILLISECOND, 0)
    }

    assertTrue(AppUpdateChecker.shouldAutoCheckForUpdate(0L, calNow.timeInMillis))
    assertFalse(AppUpdateChecker.shouldAutoCheckForUpdate(calToday913.timeInMillis, calNow.timeInMillis))
    assertTrue(
      AppUpdateChecker.shouldAutoCheckForUpdate(
        calNow.timeInMillis - AppUpdateChecker.DAILY_CHECK_INTERVAL_MS - 1_000L,
        calNow.timeInMillis
      )
    )

    assertEquals("", AppUpdateChecker.formatLastCheckedLabel(0L, calNow.timeInMillis))
    assertEquals(
      "Última búsqueda: hoy 9:13",
      AppUpdateChecker.formatLastCheckedLabel(calToday913.timeInMillis, calNow.timeInMillis)
    )
    assertEquals(
      "Última búsqueda: ayer 18:05",
      AppUpdateChecker.formatLastCheckedLabel(calYesterday1805.timeInMillis, calNow.timeInMillis)
    )
  }

  @Test
  fun `release notes bullet parser extracts up to 5 plain text items strips links images code and preserves empty notes`() {
    val markdownNotes = """
      ## Novedades
      - Audio más claro en llamadas ![foto](https://example.com/a.png)
      * Menor uso de batería [detalles](https://github.com/owner/repo)
      • Corrección en el envío de fotos `sendPhoto()`
      - Mejora de inicio https://example.com/info
      - Punto cinco limpio
      - Punto seis que no debe salir
      ```kotlin
      val secret = "ignore me"
      ```
    """.trimIndent()

    val bullets = AppUpdateChecker.parseReleaseNotesBullets(markdownNotes)
    assertEquals(5, bullets.size)
    assertEquals("Audio más claro en llamadas", bullets[0])
    assertEquals("Menor uso de batería", bullets[1])
    assertEquals("Corrección en el envío de fotos", bullets[2])
    assertEquals("Mejora de inicio", bullets[3])
    assertEquals("Punto cinco limpio", bullets[4])
    assertTrue(bullets.none { it.contains("https://") || it.contains("sendPhoto") || it.contains("secret") })

    val plainFallback = """
      Primera línea de mejora
      Segunda línea útil
      https://solo-enlace.example.com
      ![solo imagen](https://example.com/x.png)
      Tercera línea útil
    """.trimIndent()
    assertEquals(
      listOf("Primera línea de mejora", "Segunda línea útil", "Tercera línea útil"),
      AppUpdateChecker.parseReleaseNotesBullets(plainFallback)
    )

    assertTrue(AppUpdateChecker.parseReleaseNotesBullets("").isEmpty())
    assertTrue(AppUpdateChecker.parseReleaseNotesBullets("   \n![img](https://a.com/b.png)\n```code```").isEmpty())

    assertEquals("Sin conexión", AppUpdateChecker.simplifyUpdateErrorMessage("Sin conexión a internet.", false))
    assertEquals("No se pudo descargar", AppUpdateChecker.simplifyUpdateErrorMessage("HTTP 502 error", true))

    val now = 1_700_000_000_000L
    assertTrue(AppUpdateChecker.isUpdatePromptSnoozed(now + AppUpdateChecker.DAILY_CHECK_INTERVAL_MS, now))
    assertFalse(AppUpdateChecker.isUpdatePromptSnoozed(now - 1_000L, now))

    // 10-minute manual check cache reuse
    val upToDate = com.example.util.AppUpdateUiState.UpToDate("2.8.41", 83)
    assertTrue(AppUpdateChecker.shouldReuseRecentManualCheck(now - 5 * 60_000L, upToDate, now))
    assertFalse(AppUpdateChecker.shouldReuseRecentManualCheck(now - 10 * 60_000L, upToDate, now))
    assertFalse(AppUpdateChecker.shouldReuseRecentManualCheck(now - 1_000L, com.example.util.AppUpdateUiState.Idle, now))
    assertFalse(AppUpdateChecker.shouldReuseRecentManualCheck(now - 1_000L, com.example.util.AppUpdateUiState.Error("Sin conexión"), now))
  }

  @Test
  fun `app share helper builds clean FamiChat apk filename without package id`() {
    assertEquals("FamiChat-2.8.40.apk", com.example.util.AppShareHelper.buildShareApkFileName("2.8.40"))
    assertEquals("FamiChat-2.8.40.apk", com.example.util.AppShareHelper.buildShareApkFileName("v2.8.40"))
    assertEquals("FamiChat.apk", com.example.util.AppShareHelper.buildShareApkFileName("   "))
    val generated = com.example.util.AppShareHelper.buildShareApkFileName("2.8.40")
    assertFalse(generated.contains("aistudio", ignoreCase = true))
    assertFalse(generated.contains("nidofamiliar", ignoreCase = true))
  }

  @Test
  fun `switch key verification accepts only switchPin and rejects admin masterPin`() {
    assertTrue(com.example.ui.viewmodel.FamilyViewModel.matchesSwitchKey(entered = "2802", activeSwitchPin = "2802"))
    assertFalse(com.example.ui.viewmodel.FamilyViewModel.matchesSwitchKey(entered = "7525", activeSwitchPin = "2802"))
    assertFalse(com.example.ui.viewmodel.FamilyViewModel.matchesSwitchKey(entered = "", activeSwitchPin = "2802"))
    assertFalse(com.example.ui.viewmodel.FamilyViewModel.matchesSwitchKey(entered = "2802", activeSwitchPin = ""))
  }
}

