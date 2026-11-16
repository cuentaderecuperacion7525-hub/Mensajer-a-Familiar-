package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.util.AppUpdateChecker
import com.example.util.MessageFormatter
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
    assertEquals("NidoFamiliar", appName)
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
    val (name, code) = AppUpdateChecker.parseReleaseVersion("v1.2-c3", "NidoFamiliar v1.2 (Build 3)")
    assertEquals("1.2", name)
    assertEquals(3, code)
    assertTrue(AppUpdateChecker.isRemoteNewer(2, "1.1", 3, "1.2"))
    assertFalse(AppUpdateChecker.isRemoteNewer(2, "1.1", 2, "1.1"))
    val sampleHtml = """
      <a href="/miusuario/Mensajer-a-Familiar-/releases/download/v2.0.1-c6/NidoFamiliar-v2.0.1-c6-debug.apk">APK</a>
    """.trimIndent()
    assertEquals(
      "https://github.com/miusuario/Mensajer-a-Familiar-/releases/download/v2.0.1-c6/NidoFamiliar-v2.0.1-c6-debug.apk",
      AppUpdateChecker.extractApkUrlFromHtml(sampleHtml, "miusuario/Mensajer-a-Familiar-")
    )
  }

  @Test
  fun `app update checker returns NotConfigured when repo slug is blank`() = kotlinx.coroutines.test.runTest {
    val result = AppUpdateChecker.checkLatestRelease("", "2.0.5", 10)
    assertTrue(result is com.example.util.AppUpdateUiState.NotConfigured)
  }
}
