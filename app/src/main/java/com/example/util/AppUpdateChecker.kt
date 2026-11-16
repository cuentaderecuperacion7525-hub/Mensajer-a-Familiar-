package com.example.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

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
            return@withContext AppUpdateUiState.NotConfigured(
                installedVersionName = installedVersionName,
                installedVersionCode = installedVersionCode
            )
        }

        val repoWebUrl = "https://github.com/$slug/releases"
        val apiUrl = "https://api.github.com/repos/$slug/releases/latest"
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "NidoFamiliar-Android")
            }

            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                return@withContext AppUpdateUiState.Error(
                    message = "No se encontraron publicaciones (Releases) en $slug. Verifica el repositorio o compila una nueva versión en GitHub.",
                    repoUrl = repoWebUrl
                )
            }
            if (code !in 200..299) {
                return@withContext AppUpdateUiState.Error(
                    message = "No se pudo consultar actualizaciones (código $code).",
                    repoUrl = repoWebUrl
                )
            }

            val bodyText = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(bodyText)
            val tagName = json.optString("tag_name", "")
            val title = json.optString("name", tagName)
            val notes = json.optString("body", "").ifBlank { "Mejoras y correcciones de NidoFamiliar." }
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

            if (isRemoteNewer(installedVersionCode, installedVersionName, remoteVerCode, remoteVerName)) {
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
        } catch (e: Exception) {
            AppUpdateUiState.Error(
                message = "Sin conexión o no se pudo conectar con GitHub. Revisa tu internet e intenta nuevamente.",
                repoUrl = repoWebUrl
            )
        } finally {
            connection?.disconnect()
        }
    }
}
