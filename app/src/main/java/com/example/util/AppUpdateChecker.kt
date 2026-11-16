package com.example.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.HttpURLConnection
import java.util.concurrent.TimeUnit

data class AppReleaseInfo(
    val tagName: String,
    val versionName: String,
    val versionCode: Int?,
    val releaseNotes: String,
    val downloadUrl: String,
    val releasePageUrl: String
)

sealed interface AppUpdateUiState {
    data object Idle : AppUpdateUiState
    data object Checking : AppUpdateUiState
    data class NotConfigured(val installedVersionName: String, val installedVersionCode: Int) : AppUpdateUiState
    data class UpToDate(
        val installedVersionName: String,
        val installedVersionCode: Int,
        val releasePageUrl: String
    ) : AppUpdateUiState
    data class UpdateAvailable(
        val installedVersionName: String,
        val installedVersionCode: Int,
        val release: AppReleaseInfo
    ) : AppUpdateUiState
    data class Error(
        val message: String,
        val repoUrl: String? = null
    ) : AppUpdateUiState
}

object AppUpdateChecker {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val noRedirectClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    fun normalizeRepoSlug(raw: String): String {
        val trimmed = raw.trim().removeSuffix("/")
        if (trimmed.isEmpty()) return ""
        val githubPrefix = "github.com/"
        val idx = trimmed.indexOf(githubPrefix, ignoreCase = true)
        val path = if (idx >= 0) {
            trimmed.substring(idx + githubPrefix.length)
        } else {
            trimmed
        }
        val parts = path.split("/").filter { it.isNotBlank() }
        return if (parts.size >= 2) "${parts[0]}/${parts[1]}" else ""
    }

    fun parseReleaseVersion(tagName: String, title: String): Pair<String, Int?> {
        val codeRegex = Regex("""(?:-c|build\s*)(\d+)""", RegexOption.IGNORE_CASE)
        val codeMatch = codeRegex.find(tagName) ?: codeRegex.find(title)
        val parsedCode = codeMatch?.groupValues?.getOrNull(1)?.toIntOrNull()

        val cleanVersion = tagName
            .removePrefix("v")
            .removePrefix("V")
            .replace(Regex("""-c\d+$""", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifBlank { tagName }

        return cleanVersion to parsedCode
    }

    fun isRemoteNewer(
        installedVersionCode: Int,
        installedVersionName: String,
        remoteVersionCode: Int?,
        remoteVersionName: String
    ): Boolean {
        if (remoteVersionCode != null) {
            return remoteVersionCode > installedVersionCode
        }
        return compareVersionNames(remoteVersionName, installedVersionName) > 0
    }

    fun compareVersionNames(remote: String, local: String): Int {
        val rParts = remote.split(".").mapNotNull { it.filter(Char::isDigit).toIntOrNull() }
        val lParts = local.split(".").mapNotNull { it.filter(Char::isDigit).toIntOrNull() }
        val maxLen = maxOf(rParts.size, lParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val l = lParts.getOrElse(i) { 0 }
            if (r != l) return r.compareTo(l)
        }
        return 0
    }

    suspend fun checkLatestRelease(
        rawRepoSlug: String,
        installedVersionName: String,
        installedVersionCode: Int
    ): AppUpdateUiState = withContext(Dispatchers.IO) {
        val slug = normalizeRepoSlug(rawRepoSlug)
        if (slug.isEmpty()) {
            return@withContext AppUpdateUiState.UpToDate(
                installedVersionName = installedVersionName,
                installedVersionCode = installedVersionCode,
                releasePageUrl = ""
            )
        }

        val repoWebUrl = "https://github.com/$slug/releases"
        var lastException: Exception? = null

        // 1) Primary: GitHub Releases API
        try {
            val apiRequest = Request.Builder()
                .url("https://api.github.com/repos/$slug/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "NidoFamiliar-Android")
                .build()

            client.newCall(apiRequest).execute().use { response ->
                val code = response.code
                if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                    return@withContext AppUpdateUiState.UpToDate(
                        installedVersionName = installedVersionName,
                        installedVersionCode = installedVersionCode,
                        releasePageUrl = repoWebUrl
                    )
                }
                if (code in 200..299) {
                    val bodyText = response.body?.string().orEmpty()
                    val json = JSONObject(bodyText)
                    val tagName = json.optString("tag_name", "")
                    val title = json.optString("name", tagName)
                    val notes = json.optString("body", "")
                        .ifBlank { "Mejoras y correcciones de NidoFamiliar." }
                    val htmlUrl = json.optString("html_url", repoWebUrl)

                    var apkUrl = htmlUrl
                    val assets = json.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.optJSONObject(i) ?: continue
                            val assetName = asset.optString("name", "")
                            if (assetName.endsWith(".apk", ignoreCase = true)) {
                                apkUrl = asset.optString("browser_download_url", htmlUrl)
                                break
                            }
                        }
                    }

                    val (remoteVerName, remoteVerCode) = parseReleaseVersion(tagName, title)
                    val releaseInfo = AppReleaseInfo(
                        tagName = tagName,
                        versionName = remoteVerName,
                        versionCode = remoteVerCode,
                        releaseNotes = notes,
                        downloadUrl = apkUrl,
                        releasePageUrl = htmlUrl
                    )

                    return@withContext if (isRemoteNewer(
                            installedVersionCode,
                            installedVersionName,
                            remoteVerCode,
                            remoteVerName
                        )
                    ) {
                        AppUpdateUiState.UpdateAvailable(
                            installedVersionName = installedVersionName,
                            installedVersionCode = installedVersionCode,
                            release = releaseInfo
                        )
                    } else {
                        AppUpdateUiState.UpToDate(
                            installedVersionName = installedVersionName,
                            installedVersionCode = installedVersionCode,
                            releasePageUrl = htmlUrl
                        )
                    }
                }
            }
        } catch (e: Exception) {
            lastException = e
        }

        // 2) Fallback: Direct GitHub web redirect (avoids api.github.com rate limits)
        try {
            val webRequest = Request.Builder()
                .url("https://github.com/$slug/releases/latest")
                .header("User-Agent", "NidoFamiliar-Android")
                .build()

            noRedirectClient.newCall(webRequest).execute().use { response ->
                val location = response.header("Location").orEmpty()
                val tagMarker = "/releases/tag/"
                val tagIdx = location.indexOf(tagMarker)
                if (tagIdx >= 0) {
                    val tagName = location.substring(tagIdx + tagMarker.length)
                        .substringBefore("?")
                        .substringBefore("#")
                        .trim()
                    val (remoteVerName, remoteVerCode) = parseReleaseVersion(tagName, tagName)
                    val releasePage = "https://github.com/$slug/releases/tag/$tagName"
                    val apkUrl = "https://github.com/$slug/releases/download/$tagName/app-debug.apk"
                    val releaseInfo = AppReleaseInfo(
                        tagName = tagName,
                        versionName = remoteVerName,
                        versionCode = remoteVerCode,
                        releaseNotes = "Nueva compilación disponible en GitHub ($tagName).",
                        downloadUrl = apkUrl,
                        releasePageUrl = releasePage
                    )
                    return@withContext if (isRemoteNewer(
                            installedVersionCode,
                            installedVersionName,
                            remoteVerCode,
                            remoteVerName
                        )
                    ) {
                        AppUpdateUiState.UpdateAvailable(
                            installedVersionName = installedVersionName,
                            installedVersionCode = installedVersionCode,
                            release = releaseInfo
                        )
                    } else {
                        AppUpdateUiState.UpToDate(
                            installedVersionName = installedVersionName,
                            installedVersionCode = installedVersionCode,
                            releasePageUrl = releasePage
                        )
                    }
                } else if (response.code == HttpURLConnection.HTTP_NOT_FOUND || location.endsWith("/releases")) {
                    return@withContext AppUpdateUiState.UpToDate(
                        installedVersionName = installedVersionName,
                        installedVersionCode = installedVersionCode,
                        releasePageUrl = repoWebUrl
                    )
                }
            }
        } catch (e: Exception) {
            lastException = e
        }

        val detail = lastException?.let { "${it.javaClass.simpleName}: ${it.localizedMessage.orEmpty()}".trim() }
            ?: "sin respuesta"
        AppUpdateUiState.Error(
            message = "No se pudo conectar con GitHub ($detail). Revisa tu internet e intenta nuevamente.",
            repoUrl = repoWebUrl
        )
    }
}
