package com.example.util

import android.app.Activity
import android.app.DownloadManager
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
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

data class ApkInspectionResult(
    val isValidArchive: Boolean,
    val packageName: String? = null,
    val versionCode: Long? = null,
    val versionName: String? = null,
    val apkSha256: String? = null,
    val installedSha256: String? = null,
    val hasSignatureConflict: Boolean = false,
    val isDowngrade: Boolean = false,
    val blockingError: String? = null,
    val signatureWarning: String? = null
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
        val totalBytes: Long,
        val statusLabel: String = ""
    ) : AppUpdateUiState
    data class ReadyToInstall(
        val release: AppReleaseInfo,
        val apkFilePath: String
    ) : AppUpdateUiState
    data class Error(
        val message: String,
        val release: AppReleaseInfo? = null
    ) : AppUpdateUiState
}

object AppUpdateChecker {

    const val DAILY_CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L

    private const val EXPECTED_DEBUG_SHA256 =
        "33D2C0F213FED29E496863488A303962B27C80F5C8C17F10C59B47DFFA15385C"

    private val TRUSTED_DOWNLOAD_HOSTS = setOf(
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
        "github-releases.githubusercontent.com"
    )

    fun isTrustedDownloadUrl(rawUrl: String): Boolean {
        val url = rawUrl.trim()
        if (!url.startsWith("https://", ignoreCase = true)) return false
        val host = runCatching { java.net.URI(url).host?.lowercase().orEmpty() }.getOrDefault("")
        if (host.isBlank()) return false
        return host in TRUSTED_DOWNLOAD_HOSTS || host.endsWith(".githubusercontent.com")
    }

    fun shouldAutoCheckForUpdate(
        lastCheckedAtMs: Long,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (lastCheckedAtMs <= 0L) return true
        return (nowMs - lastCheckedAtMs) >= DAILY_CHECK_INTERVAL_MS
    }

    fun formatLastCheckedLabel(
        lastCheckedAtMs: Long,
        nowMs: Long = System.currentTimeMillis()
    ): String {
        if (lastCheckedAtMs <= 0L) return ""
        val calNow = java.util.Calendar.getInstance().apply { timeInMillis = nowMs }
        val calThen = java.util.Calendar.getInstance().apply { timeInMillis = lastCheckedAtMs }
        val timeFmt = java.text.SimpleDateFormat("H:mm", java.util.Locale("es", "MX"))
        val timeStr = timeFmt.format(java.util.Date(lastCheckedAtMs))
        val sameYear = calNow.get(java.util.Calendar.YEAR) == calThen.get(java.util.Calendar.YEAR)
        val dayDiff = calNow.get(java.util.Calendar.DAY_OF_YEAR) - calThen.get(java.util.Calendar.DAY_OF_YEAR)
        return when {
            sameYear && dayDiff == 0 -> "Última búsqueda: hoy $timeStr"
            sameYear && dayDiff == 1 -> "Última búsqueda: ayer $timeStr"
            else -> {
                val dateFmt = java.text.SimpleDateFormat("d MMM H:mm", java.util.Locale("es", "MX"))
                "Última búsqueda: ${dateFmt.format(java.util.Date(lastCheckedAtMs)).lowercase(java.util.Locale("es", "MX"))}"
            }
        }
    }

    private fun extractPackageSha256(info: PackageInfo?): String? {
        info ?: return null
        @Suppress("DEPRECATION")
        val certBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo
            val sigs = if (signingInfo?.hasMultipleSigners() == true) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo?.signingCertificateHistory ?: signingInfo?.apkContentsSigners
            }
            sigs?.firstOrNull()?.toByteArray()
        } else {
            info.signatures?.firstOrNull()?.toByteArray()
        } ?: return null

        return MessageDigest.getInstance("SHA-256")
            .digest(certBytes)
            .joinToString("") { "%02X".format(it) }
    }

    fun getInstalledSigningSha256(context: Context): String? = runCatching {
        val pm = context.packageManager
        val pkgName = context.packageName
        @Suppress("DEPRECATION")
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(pkgName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkgName, flags)
        }
        extractPackageSha256(info)
    }.getOrNull()

    fun isConsistentDebugSigning(context: Context): Boolean? {
        val sha = getInstalledSigningSha256(context) ?: return null
        return sha.equals(EXPECTED_DEBUG_SHA256, ignoreCase = true)
    }

    fun inspectApkFile(
        context: Context,
        apkFile: File,
        installedVersionCode: Int
    ): ApkInspectionResult {
        if (!apkFile.exists() || apkFile.length() <= 0L) {
            return ApkInspectionResult(
                isValidArchive = false,
                blockingError = "El archivo APK descargado no existe o está vacío."
            )
        }
        return runCatching {
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                PackageManager.GET_SIGNATURES
            }
            val archiveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
            }

            if (archiveInfo == null) {
                return@runCatching ApkInspectionResult(
                    isValidArchive = false,
                    blockingError = "El archivo descargado no se pudo leer como un paquete APK válido."
                )
            }

            val apkPkg = archiveInfo.packageName
            val apkVerCode = PackageInfoCompat.getLongVersionCode(archiveInfo)
            val apkVerName = archiveInfo.versionName
            val apkSha = extractPackageSha256(archiveInfo)
            val installedSha = getInstalledSigningSha256(context)

            val wrongPackage = !apkPkg.isNullOrBlank() && apkPkg != context.packageName
            val isDowngrade = apkVerCode < installedVersionCode.toLong()
            val sigConflict = !apkSha.isNullOrBlank() &&
                !installedSha.isNullOrBlank() &&
                !apkSha.equals(installedSha, ignoreCase = true)

            val blockingError = when {
                wrongPackage ->
                    "El APK descargado pertenece a otro paquete ($apkPkg) y no puede actualizar a FamiChat."
                isDowngrade ->
                    "El APK descargado (comp. $apkVerCode) es anterior a la versión instalada (comp. $installedVersionCode). Android no permite instalar una versión menor encima."
                else -> null
            }

            val signatureWarning = if (sigConflict) {
                "Conflicto de firma detectado: la app instalada usa firma ${installedSha?.take(8)}… y el APK de GitHub usa ${apkSha?.take(8)}…. Android rechazará actualizar encima; desinstala FamiChat una sola vez e instala esta versión (las siguientes ya actualizarán automáticamente)."
            } else null

            ApkInspectionResult(
                isValidArchive = true,
                packageName = apkPkg,
                versionCode = apkVerCode,
                versionName = apkVerName,
                apkSha256 = apkSha,
                installedSha256 = installedSha,
                hasSignatureConflict = sigConflict,
                isDowngrade = isDowngrade,
                blockingError = blockingError,
                signatureWarning = signatureWarning
            )
        }.getOrElse {
            ApkInspectionResult(isValidArchive = true)
        }
    }

    fun extractApkUrlFromHtml(html: String, slug: String): String? {
        val escapedSlug = Regex.escape(slug)
        val regex = Regex(
            """href=["'](?:https?://github\.com)?(/$escapedSlug/releases/download/[^"'\s]+\.apk(?:\?[^"'\s]*)?)["']""",
            RegexOption.IGNORE_CASE
        )
        val path = regex.find(html)?.groupValues?.getOrNull(1) ?: return null
        return "https://github.com$path"
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val noRedirectClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    private val downloadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun normalizeRepoSlug(raw: String): String {
        val trimmed = raw.trim()
            .substringBefore("?")
            .substringBefore("#")
            .removeSuffix("/")
        if (trimmed.isEmpty()) return ""
        val githubPrefix = "github.com/"
        val idx = trimmed.indexOf(githubPrefix, ignoreCase = true)
        val path = if (idx >= 0) {
            trimmed.substring(idx + githubPrefix.length)
        } else {
            trimmed
        }
        val parts = path.split("/")
            .filter { it.isNotBlank() }
            .toMutableList()
        if (parts.size < 2) return ""
        val owner = parts[0]
        val repo = parts[1].removeSuffix(".git")
        return if (owner.isNotBlank() && repo.isNotBlank()) "$owner/$repo" else ""
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

    internal fun parseReleaseJsonObject(json: JSONObject, repoWebUrl: String): AppReleaseInfo? {
        if (json.optBoolean("draft", false)) return null
        val tagName = json.optString("tag_name", "").trim()
        if (tagName.isEmpty()) return null
        val title = json.optString("name", tagName)
        val notes = json.optString("body", "")
            .ifBlank { "Mejoras y correcciones de FamiChat." }
        val htmlUrl = json.optString("html_url", repoWebUrl)

        var apkUrl = ""
        val assets = json.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val assetName = asset.optString("name", "")
                val download = asset.optString("browser_download_url", "")
                if (assetName.endsWith(".apk", ignoreCase = true) && download.startsWith("http")) {
                    apkUrl = download
                    break
                }
            }
        }

        val (remoteVerName, remoteVerCode) = parseReleaseVersion(tagName, title)
        return AppReleaseInfo(
            tagName = tagName,
            versionName = remoteVerName,
            versionCode = remoteVerCode,
            releaseNotes = notes,
            downloadUrl = apkUrl,
            releasePageUrl = htmlUrl
        )
    }

    internal fun selectBestReleaseFromApiJsonArray(
        releasesArray: JSONArray,
        repoWebUrl: String
    ): AppReleaseInfo? {
        val parsed = buildList {
            for (i in 0 until releasesArray.length()) {
                val obj = releasesArray.optJSONObject(i) ?: continue
                parseReleaseJsonObject(obj, repoWebUrl)?.let { add(it) }
            }
        }
        val withApk = parsed.filter { it.downloadUrl.isNotBlank() }
        if (withApk.isNotEmpty()) {
            return withApk.maxWithOrNull { a, b ->
                when {
                    a.versionCode != null && b.versionCode != null ->
                        a.versionCode.compareTo(b.versionCode)
                    else -> compareVersionNames(a.versionName, b.versionName)
                }
            }
        }
        return parsed.firstOrNull()
    }

    private fun evaluateRelease(
        releaseInfo: AppReleaseInfo,
        installedVersionName: String,
        installedVersionCode: Int
    ): AppUpdateUiState {
        val newer = isRemoteNewer(
            installedVersionCode = installedVersionCode,
            installedVersionName = installedVersionName,
            remoteVersionCode = releaseInfo.versionCode,
            remoteVersionName = releaseInfo.versionName
        )
        if (!newer) {
            return AppUpdateUiState.UpToDate(
                installedVersionName = installedVersionName,
                installedVersionCode = installedVersionCode
            )
        }
        if (releaseInfo.downloadUrl.isBlank() ||
            (!releaseInfo.downloadUrl.contains(".apk", ignoreCase = true) &&
                !releaseInfo.downloadUrl.contains("/releases/download/", ignoreCase = true))
        ) {
            return AppUpdateUiState.Error(
                message = "Se encontró la versión ${releaseInfo.tagName} en GitHub, pero no tiene un archivo .apk adjunto todavía. Revisa que GitHub Actions haya terminado de compilar."
            )
        }
        return AppUpdateUiState.UpdateAvailable(
            installedVersionName = installedVersionName,
            installedVersionCode = installedVersionCode,
            release = releaseInfo
        )
    }

    private fun resolveApkFromWebPages(slug: String, tagName: String): String? {
        val urlsToTry = listOf(
            "https://github.com/$slug/releases/expanded_assets/$tagName",
            "https://github.com/$slug/releases/tag/$tagName"
        )
        for (url in urlsToTry) {
            val found = runCatching {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "FamiChat-Android")
                    .header("Accept", "text/html,application/xhtml+xml")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        extractApkUrlFromHtml(resp.body?.string().orEmpty(), slug)
                    } else null
                }
            }.getOrNull()
            if (!found.isNullOrBlank()) return found
        }
        return null
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
        var sawLatestWithoutApk = false
        var apiReturnedNotFound = false

        var latestEvaluatedState: AppUpdateUiState? = null

        // 1) Primary: GitHub Releases /latest API
        try {
            val apiRequest = Request.Builder()
                .url("https://api.github.com/repos/$slug/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("Cache-Control", "no-cache, no-store")
                .header("Pragma", "no-cache")
                .header("User-Agent", "FamiChat-Android")
                .build()

            client.newCall(apiRequest).execute().use { response ->
                val code = response.code
                if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                    apiReturnedNotFound = true
                } else if (code in 200..299) {
                    val bodyText = response.body?.string().orEmpty()
                    val json = JSONObject(bodyText)
                    val parsed = parseReleaseJsonObject(json, repoWebUrl)
                    if (parsed != null && parsed.downloadUrl.isNotBlank()) {
                        val evalState = evaluateRelease(parsed, installedVersionName, installedVersionCode)
                        if (evalState is AppUpdateUiState.UpdateAvailable) {
                            return@withContext evalState
                        }
                        latestEvaluatedState = evalState
                    } else if (parsed != null) {
                        // Try web expanded_assets in case API omitted or user wants fallback before scanning list
                        val webApk = resolveApkFromWebPages(slug, parsed.tagName)
                        if (!webApk.isNullOrBlank()) {
                            val evalState = evaluateRelease(
                                parsed.copy(downloadUrl = webApk),
                                installedVersionName,
                                installedVersionCode
                            )
                            if (evalState is AppUpdateUiState.UpdateAvailable) {
                                return@withContext evalState
                            }
                            latestEvaluatedState = evalState
                        } else {
                            sawLatestWithoutApk = true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            lastException = e
        }

        // 2) Secondary API: Releases list (handles prereleases, newer releases, or /latest lacking an APK asset)
        try {
            val listRequest = Request.Builder()
                .url("https://api.github.com/repos/$slug/releases?per_page=10")
                .header("Accept", "application/vnd.github+json")
                .header("Cache-Control", "no-cache, no-store")
                .header("Pragma", "no-cache")
                .header("User-Agent", "FamiChat-Android")
                .build()

            client.newCall(listRequest).execute().use { response ->
                if (response.code in 200..299) {
                    val arr = JSONArray(response.body?.string().orEmpty())
                    val best = selectBestReleaseFromApiJsonArray(arr, repoWebUrl)
                    if (best != null) {
                        val withResolvedUrl = if (best.downloadUrl.isBlank()) {
                            best.copy(downloadUrl = resolveApkFromWebPages(slug, best.tagName).orEmpty())
                        } else {
                            best
                        }
                        return@withContext evaluateRelease(
                            withResolvedUrl,
                            installedVersionName,
                            installedVersionCode
                        )
                    } else if (latestEvaluatedState != null) {
                        return@withContext latestEvaluatedState!!
                    } else if (apiReturnedNotFound) {
                        return@withContext AppUpdateUiState.Error(
                            message = "No se encontraron actualizaciones disponibles por el momento. Intenta de nuevo más tarde."
                        )
                    }
                }
            }
        } catch (e: Exception) {
            lastException = e
        }

        if (latestEvaluatedState != null) {
            return@withContext latestEvaluatedState!!
        }

        // 3) Fallback: Direct GitHub web redirect + HTML asset scrape (avoids api.github.com rate limits)
        try {
            val webRequest = Request.Builder()
                .url("https://github.com/$slug/releases/latest")
                .header("Cache-Control", "no-cache")
                .header("User-Agent", "FamiChat-Android")
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
                    val resolvedApkUrl = resolveApkFromWebPages(slug, tagName).orEmpty()
                    val releaseInfo = AppReleaseInfo(
                        tagName = tagName,
                        versionName = remoteVerName,
                        versionCode = remoteVerCode,
                        releaseNotes = "Mejoras de estabilidad y rendimiento.",
                        downloadUrl = resolvedApkUrl,
                        releasePageUrl = releasePage
                    )
                    return@withContext evaluateRelease(
                        releaseInfo,
                        installedVersionName,
                        installedVersionCode
                    )
                } else {
                    val cleanLoc = location.substringBefore("?").trimEnd('/')
                    if (response.code == HttpURLConnection.HTTP_NOT_FOUND || cleanLoc.endsWith("/releases")) {
                        // Last check: maybe only prereleases exist on /releases HTML page
                        val htmlApk = runCatching {
                            val req = Request.Builder()
                                .url(repoWebUrl)
                                .header("User-Agent", "FamiChat-Android")
                                .build()
                            client.newCall(req).execute().use { r ->
                                if (r.isSuccessful) extractApkUrlFromHtml(r.body?.string().orEmpty(), slug) else null
                            }
                        }.getOrNull()
                        if (!htmlApk.isNullOrBlank()) {
                            val tagFromUrl = htmlApk.substringAfter("/releases/download/", "")
                                .substringBefore("/")
                            val (rName, rCode) = parseReleaseVersion(tagFromUrl, tagFromUrl)
                            val rel = AppReleaseInfo(
                                tagName = tagFromUrl.ifBlank { "latest" },
                                versionName = rName,
                                versionCode = rCode,
                                releaseNotes = "Mejoras de estabilidad y rendimiento.",
                                downloadUrl = htmlApk,
                                releasePageUrl = repoWebUrl
                            )
                            return@withContext evaluateRelease(rel, installedVersionName, installedVersionCode)
                        }
                        return@withContext AppUpdateUiState.Error(
                            message = "No se encontraron actualizaciones disponibles por el momento. Intenta de nuevo más tarde."
                        )
                    }
                }
            }
        } catch (e: Exception) {
            lastException = e
        }

        if (sawLatestWithoutApk) {
            return@withContext AppUpdateUiState.Error(
                message = "La nueva versión aún se está preparando. Intenta de nuevo en unos minutos."
            )
        }

        val friendlyError = when (lastException) {
            is java.net.UnknownHostException,
            is java.net.ConnectException,
            is java.net.NoRouteToHostException ->
                "Sin conexión a internet. Revisa tu Wi-Fi o datos móviles e intenta de nuevo."
            is java.net.SocketTimeoutException ->
                "La conexión tardó demasiado. Intenta de nuevo en un momento."
            else ->
                "No se pudo buscar actualizaciones ahora. Revisa tu conexión e intenta nuevamente."
        }
        AppUpdateUiState.Error(message = friendlyError)
    }

    private fun parseTotalBytesFromContentRange(contentRange: String?): Long {
        if (contentRange.isNullOrBlank()) return -1L
        val slashIdx = contentRange.lastIndexOf('/')
        if (slashIdx < 0 || slashIdx + 1 >= contentRange.length) return -1L
        return contentRange.substring(slashIdx + 1).trim().toLongOrNull() ?: -1L
    }

    private fun hasValidZipMagic(file: File): Boolean {
        if (!file.exists() || file.length() < 2L) return false
        return runCatching {
            file.inputStream().use { ins ->
                val b0 = ins.read()
                val b1 = ins.read()
                b0 == 0x50 && b1 == 0x4B
            }
        }.getOrDefault(false)
    }

    private fun hasZipEocd(file: File): Boolean {
        if (!file.exists() || file.length() < 22L) return false
        return runCatching {
            val len = file.length()
            val tailSize = minOf(len, 66_000L).toInt()
            val buf = ByteArray(tailSize)
            java.io.RandomAccessFile(file, "r").use { raf ->
                raf.seek(len - tailSize)
                raf.readFully(buf)
            }
            for (i in tailSize - 22 downTo 0) {
                if (buf[i] == 0x50.toByte() &&
                    buf[i + 1] == 0x4B.toByte() &&
                    buf[i + 2] == 0x05.toByte() &&
                    buf[i + 3] == 0x06.toByte()
                ) {
                    return@runCatching true
                }
            }
            false
        }.getOrDefault(false)
    }

    suspend fun downloadApkToCache(
        context: Context,
        release: AppReleaseInfo,
        onProgress: (progressPercent: Int, downloadedBytes: Long, totalBytes: Long, statusLabel: String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val url = release.downloadUrl.trim()
        if (!isTrustedDownloadUrl(url)) {
            return@withContext Result.failure(
                SecurityException("La descarga fue bloqueada porque el enlace no es un origen HTTPS verificado.")
            )
        }
        val isLikelyApk = url.contains(".apk", ignoreCase = true) ||
            url.contains("/releases/download/", ignoreCase = true)
        if (!isLikelyApk) {
            return@withContext Result.failure(
                IllegalStateException("Esta versión no tiene un archivo APK disponible para descargar.")
            )
        }

        // Clean legacy cache updates folder if any
        File(context.cacheDir, "updates").listFiles()?.forEach { old ->
            if (old.isFile) old.delete()
        }

        val updatesDir = File(context.filesDir, "updates").apply { mkdirs() }
        val targetFile = File(updatesDir, "FamiChat-update.apk")

        // 1) Primary engine: Android native DownloadManager (handles redirects, mobile/Wi-Fi handoff & background OEM throttling)
        val dmResult = downloadApkWithSystemManager(
            context = context,
            release = release,
            url = url,
            targetFile = targetFile,
            onProgress = onProgress
        )
        if (dmResult.isSuccess) {
            return@withContext dmResult
        }
        val dmErr = dmResult.exceptionOrNull()
        if (dmErr is CancellationException) throw dmErr

        // 2) Fallback engine: Hardened HTTP/1.1 OkHttp stream with strict zero-byte stall guard
        return@withContext downloadApkWithHttpFallback(
            release = release,
            url = url,
            updatesDir = updatesDir,
            targetFile = targetFile,
            priorReason = dmErr?.localizedMessage,
            onProgress = onProgress
        )
    }

    private suspend fun downloadApkWithSystemManager(
        context: Context,
        release: AppReleaseInfo,
        url: String,
        targetFile: File,
        onProgress: (progressPercent: Int, downloadedBytes: Long, totalBytes: Long, statusLabel: String) -> Unit
    ): Result<File> {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            ?: return Result.failure(IllegalStateException("Gestor de descargas no disponible"))
        val extDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: return Result.failure(IllegalStateException("Carpeta de descargas privada no disponible"))

        runCatching {
            extDir.listFiles()?.forEach { old ->
                if (old.isFile && old.name.startsWith("FamiChat")) {
                    old.delete()
                }
            }
        }

        val safeTag = release.tagName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "latest" }
        val destFileName = "FamiChat-$safeTag-${System.currentTimeMillis()}.apk"
        val stagedFile = File(extDir, destFileName)

        var downloadId = -1L
        var finishedCleanly = false
        return try {
            onProgress(0, 0L, -1L, "Iniciando gestor de descargas Android...")
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("FamiChat v${release.versionName}")
                .setDescription("Descargando actualización de FamiChat")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, destFileName)
                .addRequestHeader("User-Agent", "FamiChat-Android")
                .addRequestHeader("Accept", "application/vnd.android.package-archive, application/octet-stream, */*")

            downloadId = dm.enqueue(request)

            val query = DownloadManager.Query().setFilterById(downloadId)
            val startedAt = System.currentTimeMillis()
            var maxBytesSeen = 0L
            var lastAdvanceAt = startedAt

            while (true) {
                currentCoroutineContext().ensureActive()
                val now = System.currentTimeMillis()
                var status = DownloadManager.STATUS_PENDING
                var reason = 0
                var downloaded = 0L
                var total = -1L
                var rowFound = false

                dm.query(query)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        rowFound = true
                        val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                        val downIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        val totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        if (statusIdx >= 0) status = cursor.getInt(statusIdx)
                        if (reasonIdx >= 0) reason = cursor.getInt(reasonIdx)
                        if (downIdx >= 0) downloaded = cursor.getLong(downIdx).coerceAtLeast(0L)
                        if (totalIdx >= 0) total = cursor.getLong(totalIdx)
                    }
                }

                if (!rowFound) {
                    error("La descarga fue cancelada por el sistema.")
                }

                if (downloaded > maxBytesSeen) {
                    maxBytesSeen = downloaded
                    lastAdvanceAt = now
                }

                val pct = if (total > 0L) {
                    ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
                } else {
                    if (downloaded > 0L) -1 else 0
                }

                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        val copied = copyCompletedDmFile(dm, downloadId, stagedFile, targetFile)
                        if (!copied || !hasValidZipMagic(targetFile)) {
                            error("El paquete descargado está incompleto o dañado.")
                        }
                        finishedCleanly = true
                        runCatching { dm.remove(downloadId) }
                        runCatching { if (stagedFile.exists()) stagedFile.delete() }
                        val finalTotal = if (total > 0L) total else targetFile.length()
                        onProgress(100, targetFile.length(), finalTotal, "Descarga completa")
                        return Result.success(targetFile)
                    }

                    DownloadManager.STATUS_FAILED -> {
                        error(describeDmFailureReason(reason))
                    }

                    DownloadManager.STATUS_PAUSED -> {
                        val pauseLabel = when (reason) {
                            DownloadManager.PAUSED_WAITING_TO_RETRY -> "Reconectando descarga..."
                            DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "Esperando conexión a internet..."
                            DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "Esperando red Wi-Fi o datos..."
                            else -> "Descarga en pausa por el sistema..."
                        }
                        onProgress(pct, downloaded, total, pauseLabel)
                        if (maxBytesSeen == 0L && (now - startedAt) > 16_000L) {
                            error("El gestor del sistema quedó esperando red en 0%.")
                        }
                        if (maxBytesSeen > 0L && (now - lastAdvanceAt) > 25_000L) {
                            error("La descarga se detuvo sin conexión.")
                        }
                    }

                    DownloadManager.STATUS_PENDING -> {
                        onProgress(pct, downloaded, total, "Conectando con gestor Android...")
                        if ((now - startedAt) > 15_000L) {
                            error("El gestor del sistema tardó demasiado en iniciar.")
                        }
                    }

                    DownloadManager.STATUS_RUNNING -> {
                        val runningLabel = if (downloaded <= 0L) {
                            "Conectando con el servidor..."
                        } else {
                            "Descargando con gestor Android..."
                        }
                        onProgress(pct, downloaded, total, runningLabel)
                        if (maxBytesSeen == 0L && (now - startedAt) > 18_000L) {
                            error("No se recibieron datos después de 18 s.")
                        }
                        if (maxBytesSeen > 0L && (now - lastAdvanceAt) > 25_000L) {
                            error("La transferencia se detuvo a mitad de descarga.")
                        }
                    }
                }

                delay(350L)
            }
            @Suppress("UNREACHABLE_CODE")
            Result.failure(IllegalStateException("Descarga interrumpida"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        } finally {
            if (!finishedCleanly && downloadId >= 0L) {
                withContext(NonCancellable) {
                    runCatching { dm.remove(downloadId) }
                    runCatching { if (stagedFile.exists()) stagedFile.delete() }
                }
            }
        }
    }

    private fun copyCompletedDmFile(
        dm: DownloadManager,
        downloadId: Long,
        stagedFile: File,
        targetFile: File
    ): Boolean {
        if (targetFile.exists()) targetFile.delete()
        if (stagedFile.exists() && stagedFile.length() > 0L) {
            if (stagedFile.renameTo(targetFile)) {
                return true
            }
            stagedFile.copyTo(targetFile, overwrite = true)
            stagedFile.delete()
            return targetFile.exists() && targetFile.length() > 0L
        }
        return runCatching {
            dm.openDownloadedFile(downloadId)?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).use { input ->
                    BufferedOutputStream(FileOutputStream(targetFile), 64 * 1024).use { output ->
                        input.copyTo(output, bufferSize = 64 * 1024)
                        output.flush()
                    }
                }
                true
            } ?: false
        }.getOrDefault(false)
    }

    private fun describeDmFailureReason(reason: Int): String = when (reason) {
        DownloadManager.ERROR_CANNOT_RESUME -> "No se pudo reanudar la descarga."
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Espacio insuficiente en el dispositivo."
        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Demasiadas redirecciones del servidor."
        DownloadManager.ERROR_HTTP_DATA_ERROR -> "Error de transferencia HTTP."
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "El servidor rechazó la descarga."
        else -> "El gestor de descargas detuvo la transferencia (código $reason)."
    }

    private suspend fun downloadApkWithHttpFallback(
        release: AppReleaseInfo,
        url: String,
        updatesDir: File,
        targetFile: File,
        priorReason: String?,
        onProgress: (progressPercent: Int, downloadedBytes: Long, totalBytes: Long, statusLabel: String) -> Unit
    ): Result<File> {
        val safeTag = release.tagName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "latest" }
        val partFileName = "FamiChat-$safeTag.apk.part"
        val partFile = File(updatesDir, partFileName)

        updatesDir.listFiles()?.forEach { old ->
            if (old.isFile && old.name != partFileName) {
                old.delete()
            }
        }
        if (partFile.exists() && !hasValidZipMagic(partFile)) {
            partFile.delete()
        }

        return runCatching {
            val maxAttempts = 2
            var expectedTotalBytes = -1L
            var lastError: Throwable? = null

            for (attempt in 1..maxAttempts) {
                currentCoroutineContext().ensureActive()
                val resumeOffset = if (partFile.exists()) partFile.length().coerceAtLeast(0L) else 0L

                if (expectedTotalBytes > 0L && resumeOffset >= expectedTotalBytes && hasValidZipMagic(partFile)) {
                    break
                }

                try {
                    onProgress(
                        if (expectedTotalBytes > 0L) ((resumeOffset * 100L) / expectedTotalBytes).toInt().coerceIn(0, 100) else 0,
                        resumeOffset,
                        expectedTotalBytes,
                        "Probando conexión directa (intento $attempt/$maxAttempts)..."
                    )

                    val reqBuilder = Request.Builder()
                        .url(url)
                        .header("User-Agent", "FamiChat-Android")
                        .header("Accept", "application/vnd.android.package-archive, application/octet-stream, */*")
                        .header("Accept-Encoding", "identity")
                        .header("Connection", "close")

                    if (resumeOffset > 0L) {
                        reqBuilder.header("Range", "bytes=$resumeOffset-")
                    }

                    downloadClient.newCall(reqBuilder.build()).execute().use { response ->
                        val code = response.code
                        if (code == 416) {
                            if (expectedTotalBytes > 0L && resumeOffset >= expectedTotalBytes && hasValidZipMagic(partFile)) {
                                return@use
                            }
                            partFile.delete()
                            continue
                        }
                        if (!response.isSuccessful) {
                            error("Error al descargar (HTTP $code)")
                        }

                        val body = response.body ?: error("Respuesta vacía al descargar el APK")
                        val isPartial = code == HttpURLConnection.HTTP_PARTIAL && resumeOffset > 0L
                        val bodyLen = body.contentLength()
                        val totalFromRange = parseTotalBytesFromContentRange(response.header("Content-Range"))

                        val totalBytes = when {
                            totalFromRange > 0L -> totalFromRange
                            isPartial && bodyLen > 0L -> resumeOffset + bodyLen
                            !isPartial && bodyLen > 0L -> bodyLen
                            expectedTotalBytes > 0L -> expectedTotalBytes
                            else -> -1L
                        }
                        if (totalBytes > 0L) {
                            expectedTotalBytes = totalBytes
                        }

                        var downloaded = if (isPartial) resumeOffset else 0L
                        var lastPercent = -1
                        var lastEmitTime = 0L
                        var checkedMagic = isPartial && hasValidZipMagic(partFile)

                        val initialPct = if (totalBytes > 0L) {
                            ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                        } else -1
                        onProgress(initialPct, downloaded, totalBytes, "Descarga directa en curso...")

                        body.byteStream().use { input ->
                            BufferedOutputStream(FileOutputStream(partFile, isPartial), 64 * 1024).use { output ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val read = input.read(buffer)
                                    if (read == -1) break
                                    if (read == 0) continue
                                    output.write(buffer, 0, read)
                                    downloaded += read
                                    if (!checkedMagic && downloaded >= 2L) {
                                        checkedMagic = true
                                        output.flush()
                                        if (!hasValidZipMagic(partFile)) {
                                            partFile.delete()
                                            error("El archivo descargado no es un paquete APK válido.")
                                        }
                                    }

                                    val now = System.currentTimeMillis()
                                    val pct = if (totalBytes > 0L) {
                                        ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                                    } else {
                                        -1
                                    }
                                    if ((pct != lastPercent && pct >= 0) || now - lastEmitTime >= 150L) {
                                        lastPercent = pct
                                        lastEmitTime = now
                                        onProgress(pct, downloaded, totalBytes, "Descarga directa en curso...")
                                    }
                                }
                                output.flush()
                            }
                        }

                        if (totalBytes <= 0L && downloaded > 0L && hasZipEocd(partFile)) {
                            expectedTotalBytes = downloaded
                        }
                    }

                    val finalSize = if (partFile.exists()) partFile.length() else 0L
                    val isComplete = finalSize > 0L && (
                        (expectedTotalBytes > 0L && finalSize >= expectedTotalBytes) ||
                            (expectedTotalBytes <= 0L && hasZipEocd(partFile))
                        )
                    if (isComplete) {
                        break
                    }
                } catch (e: Throwable) {
                    currentCoroutineContext().ensureActive()
                    lastError = e
                    if (attempt < maxAttempts) {
                        delay(500L)
                    }
                }
            }

            val completedSize = if (partFile.exists()) partFile.length() else 0L
            if (completedSize <= 0L || !hasValidZipMagic(partFile)) {
                val hint = lastError?.localizedMessage ?: priorReason
                throw IllegalStateException(
                    if (!hint.isNullOrBlank()) {
                        "No se pudo completar la descarga ($hint). Usa 'Descargar en navegador' para instalarla de inmediato."
                    } else {
                        "La descarga se detuvo en 0%. Usa 'Descargar en navegador' para instalarla de inmediato."
                    }
                )
            }
            if (expectedTotalBytes > 0L && completedSize < expectedTotalBytes) {
                val mbDown = String.format("%.1f", completedSize / (1024f * 1024f))
                val mbTot = String.format("%.1f", expectedTotalBytes / (1024f * 1024f))
                throw IllegalStateException(
                    "Conexión interrumpida ($mbDown MB de $mbTot MB). Reintenta o usa 'Descargar en navegador'."
                )
            }

            if (targetFile.exists()) targetFile.delete()
            if (!partFile.renameTo(targetFile)) {
                partFile.copyTo(targetFile, overwrite = true)
                partFile.delete()
            }
            onProgress(100, completedSize, if (expectedTotalBytes > 0L) expectedTotalBytes else completedSize, "Descarga completa")
            targetFile
        }
    }

    fun openDownloadInBrowser(context: Context, release: AppReleaseInfo?): Result<Unit> = runCatching {
        val targetUrl = release?.downloadUrl?.takeIf { it.startsWith("http") }
            ?: release?.releasePageUrl?.takeIf { it.startsWith("http") }
            ?: error("No hay enlace disponible para abrir en el navegador.")
        val hostActivity = findActivity(context)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
            if (hostActivity == null) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        (hostActivity ?: context).startActivity(intent)
    }

    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    private tailrec fun findActivity(context: Context): Activity? = when (context) {
        is Activity -> context
        is ContextWrapper -> findActivity(context.baseContext)
        else -> null
    }

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val hostActivity = findActivity(context)
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                if (hostActivity == null) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            (hostActivity ?: context).startActivity(intent)
        }
    }

    fun launchApkInstaller(context: Context, apkFile: File): Result<Unit> = runCatching {
        require(apkFile.exists() && apkFile.length() > 0L) {
            "No se encontró el archivo de actualización descargado."
        }
        val hostActivity = findActivity(context)
        val launchCtx = hostActivity ?: context
        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, apkFile)
        val clip = ClipData.newRawUri("FamiChat APK", contentUri)

        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            clipData = clip
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (hostActivity == null) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
        }

        if (viewIntent.resolveActivity(context.packageManager) != null) {
            launchCtx.startActivity(viewIntent)
            return@runCatching
        }

        @Suppress("DEPRECATION")
        val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            clipData = clip
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (hostActivity == null) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }
        if (installIntent.resolveActivity(context.packageManager) != null) {
            launchCtx.startActivity(installIntent)
            return@runCatching
        }

        launchCtx.startActivity(viewIntent)
    }
}
