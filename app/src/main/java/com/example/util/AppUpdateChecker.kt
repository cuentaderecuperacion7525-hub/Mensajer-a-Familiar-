package com.example.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.security.MessageDigest
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
        val installedVersionCode: Int
    ) : AppUpdateUiState
    data class UpdateAvailable(
        val installedVersionName: String,
        val installedVersionCode: Int,
        val release: AppReleaseInfo
    ) : AppUpdateUiState
    data class Downloading(
        val release: AppReleaseInfo,
        val progressPercent: Int,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : AppUpdateUiState
    data class ReadyToInstall(
        val release: AppReleaseInfo,
        val apkFilePath: String
    ) : AppUpdateUiState
    data class Error(
        val message: String
    ) : AppUpdateUiState
}

object AppUpdateChecker {

    private const val EXPECTED_DEBUG_SHA256 =
        "33D2C0F213FED29E496863488A303962B27C80F5C8C17F10C59B47DFFA15385C"

    fun getInstalledSigningSha256(context: Context): String? = runCatching {
        val pm = context.packageManager
        val pkgName = context.packageName
        @Suppress("DEPRECATION")
        val certBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(pkgName, PackageManager.GET_SIGNING_CERTIFICATES)
            val signingInfo = info.signingInfo
            val sigs = if (signingInfo?.hasMultipleSigners() == true) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo?.signingCertificateHistory
            }
            sigs?.firstOrNull()?.toByteArray()
        } else {
            val info = pm.getPackageInfo(pkgName, PackageManager.GET_SIGNATURES)
            info.signatures?.firstOrNull()?.toByteArray()
        } ?: return@runCatching null

        MessageDigest.getInstance("SHA-256")
            .digest(certBytes)
            .joinToString("") { "%02X".format(it) }
    }.getOrNull()

    fun isConsistentDebugSigning(context: Context): Boolean? {
        val sha = getInstalledSigningSha256(context) ?: return null
        return sha.equals(EXPECTED_DEBUG_SHA256, ignoreCase = true)
    }

    fun extractApkUrlFromHtml(html: String, slug: String): String? {
        val escapedSlug = Regex.escape(slug)
        val regex = Regex(
            """href="(/$escapedSlug/releases/download/[^"]+\.apk)"""",
            RegexOption.IGNORE_CASE
        )
        val path = regex.find(html)?.groupValues?.getOrNull(1) ?: return null
        return "https://github.com$path"
    }

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

    private val downloadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
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
            return@withContext AppUpdateUiState.NotConfigured(
                installedVersionName = installedVersionName,
                installedVersionCode = installedVersionCode
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
                    return@withContext AppUpdateUiState.Error(
                        message = "No se encontró un Release publicado en \"$slug\" o el repositorio es privado. Verifica que el repositorio sea público y tenga un Release con el archivo .apk."
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
                            installedVersionCode = installedVersionCode
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
                    val expandedAssetsUrl = "https://github.com/$slug/releases/expanded_assets/$tagName"
                    val resolvedApkUrl = runCatching {
                        val assetsReq = Request.Builder()
                            .url(expandedAssetsUrl)
                            .header("User-Agent", "NidoFamiliar-Android")
                            .build()
                        client.newCall(assetsReq).execute().use { assetsResp ->
                            if (assetsResp.isSuccessful) {
                                extractApkUrlFromHtml(assetsResp.body?.string().orEmpty(), slug)
                            } else {
                                null
                            }
                        }
                    }.getOrNull() ?: releasePage
                    val releaseInfo = AppReleaseInfo(
                        tagName = tagName,
                        versionName = remoteVerName,
                        versionCode = remoteVerCode,
                        releaseNotes = "Nueva compilación disponible en GitHub ($tagName).",
                        downloadUrl = resolvedApkUrl,
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
                            installedVersionCode = installedVersionCode
                        )
                    }
                } else if (response.code == HttpURLConnection.HTTP_NOT_FOUND || location.endsWith("/releases")) {
                    return@withContext AppUpdateUiState.Error(
                        message = "No se encontró un Release publicado en \"$slug\" o el repositorio es privado. Verifica que el repositorio sea público y tenga un Release con el archivo .apk."
                    )
                }
            }
        } catch (e: Exception) {
            lastException = e
        }

        val detail = lastException?.let { "${it.javaClass.simpleName}: ${it.localizedMessage.orEmpty()}".trim() }
            ?: "sin respuesta"
        AppUpdateUiState.Error(
            message = "No se pudo verificar la actualización ($detail). Revisa tu internet e intenta nuevamente."
        )
    }

    suspend fun downloadApkToCache(
        context: Context,
        release: AppReleaseInfo,
        onProgress: (progressPercent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val url = release.downloadUrl.trim()
        val isLikelyApk = url.contains(".apk", ignoreCase = true) ||
            url.contains("/releases/download/", ignoreCase = true)
        if (!isLikelyApk) {
            return@withContext Result.failure(
                IllegalStateException("Esta versión no tiene un archivo APK disponible para descargar.")
            )
        }

        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        updatesDir.listFiles()?.forEach { old ->
            if (old.isFile) old.delete()
        }
        val partFile = File(updatesDir, "NidoFamiliar-update.apk.part")
        val targetFile = File(updatesDir, "NidoFamiliar-update.apk")

        return@withContext runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "NidoFamiliar-Android")
                .header("Accept", "application/vnd.android.package-archive, application/octet-stream, */*")
                .build()

            downloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Error al descargar (HTTP ${response.code})")
                }
                val body = response.body ?: error("Respuesta vacía al descargar el APK")
                val totalBytes = body.contentLength()
                var downloaded = 0L
                var lastPercent = -1
                var lastEmitTime = 0L
                var checkedMagic = false

                onProgress(if (totalBytes > 0L) 0 else -1, 0L, totalBytes)

                body.byteStream().use { input ->
                    FileOutputStream(partFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read <= 0) break
                            if (!checkedMagic) {
                                checkedMagic = true
                                val isZipHeader = read >= 2 && buffer[0] == 0x50.toByte() && buffer[1] == 0x4B.toByte()
                                if (!isZipHeader) {
                                    error("El archivo descargado no es un APK válido.")
                                }
                            }
                            output.write(buffer, 0, read)
                            downloaded += read

                            val now = System.currentTimeMillis()
                            val pct = if (totalBytes > 0L) {
                                ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                            } else {
                                -1
                            }
                            if ((pct != lastPercent && pct >= 0) || now - lastEmitTime >= 150L) {
                                lastPercent = pct
                                lastEmitTime = now
                                onProgress(pct, downloaded, totalBytes)
                            }
                        }
                        output.flush()
                    }
                }

                if (downloaded <= 0L) {
                    error("El archivo APK descargado está vacío.")
                }
                if (targetFile.exists()) targetFile.delete()
                if (!partFile.renameTo(targetFile)) {
                    partFile.copyTo(targetFile, overwrite = true)
                    partFile.delete()
                }
                onProgress(100, downloaded, if (totalBytes > 0L) totalBytes else downloaded)
                targetFile
            }
        }.onFailure {
            partFile.delete()
        }
    }

    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun launchApkInstaller(context: Context, apkFile: File): Result<Unit> = runCatching {
        require(apkFile.exists()) { "No se encontró el archivo de actualización descargado." }
        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}
