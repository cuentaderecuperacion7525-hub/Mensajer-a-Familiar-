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
  fun `app update checker parses versions and normalizes repo url`() {
    assertEquals(
      "miusuario/Mensajer-a-Familiar-",
      AppUpdateChecker.normalizeRepoSlug("https://github.com/miusuario/Mensajer-a-Familiar-/releases")
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
}
