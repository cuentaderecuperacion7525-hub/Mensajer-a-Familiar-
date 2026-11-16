package com.example.util

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
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
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
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

        // 1) Primary: GitHub Releases /latest API
        try {
            val apiRequest = Request.Builder()
                .url("https://api.github.com/repos/$slug/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("Cache-Control", "no-cache")
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
                        return@withContext evaluateRelease(parsed, installedVersionName, installedVersionCode)
                    } else if (parsed != null) {
                        // Try web expanded_assets in case API omitted or user wants fallback before scanning list
                        val webApk = resolveApkFromWebPages(slug, parsed.tagName)
                        if (!webApk.isNullOrBlank()) {
                            return@withContext evaluateRelease(
                                parsed.copy(downloadUrl = webApk),
                                installedVersionName,
                                installedVersionCode
                            )
                        }
                        sawLatestWithoutApk = true
                    }
                }
            }
        } catch (e: Exception) {
            lastException = e
        }

        // 2) Secondary API: Releases list (handles prereleases or /latest lacking an APK asset)
        try {
            val listRequest = Request.Builder()
                .url("https://api.github.com/repos/$slug/releases?per_page=10")
                .header("Accept", "application/vnd.github+json")
                .header("Cache-Control", "no-cache")
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
                    } else if (apiReturnedNotFound) {
                        return@withContext AppUpdateUiState.Error(
                            message = "No se encontró ningún Release publicado en \"$slug\" o el repositorio es privado. Verifica en GitHub → Releases que exista una versión con archivo .apk."
                        )
                    }
                }
            }
        } catch (e: Exception) {
            lastException = e
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
                        releaseNotes = "Nueva compilación disponible en GitHub ($tagName).",
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
                                releaseNotes = "Nueva compilación disponible en GitHub.",
                                downloadUrl = htmlApk,
                                releasePageUrl = repoWebUrl
                            )
                            return@withContext evaluateRelease(rel, installedVersionName, installedVersionCode)
                        }
                        return@withContext AppUpdateUiState.Error(
                            message = "No se encontró un Release publicado en \"$slug\" o el repositorio es privado. Verifica que el repositorio sea público y tenga un Release con el archivo .apk."
                        )
                    }
                }
            }
        } catch (e: Exception) {
            lastException = e
        }

        if (sawLatestWithoutApk) {
            return@withContext AppUpdateUiState.Error(
                message = "El último Release en \"$slug\" aún no tiene archivo .apk adjunto."
            )
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

        // Clean legacy cache updates folder if any
        File(context.cacheDir, "updates").listFiles()?.forEach { old ->
            if (old.isFile) old.delete()
        }

        val updatesDir = File(context.filesDir, "updates").apply { mkdirs() }
        updatesDir.listFiles()?.forEach { old ->
            if (old.isFile) old.delete()
        }
        val partFile = File(updatesDir, "FamiChat-update.apk.part")
        val targetFile = File(updatesDir, "FamiChat-update.apk")

        return@withContext runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "FamiChat-Android")
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
                        val buffer = ByteArray(16 * 1024)
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
                if (totalBytes > 0L && downloaded < totalBytes) {
                    error("La descarga se interrumpió ($downloaded de $totalBytes bytes). Intenta de nuevo.")
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
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        }
    }

    fun launchApkInstaller(context: Context, apkFile: File): Result<Unit> = runCatching {
        require(apkFile.exists() && apkFile.length() > 0L) {
            "No se encontró el archivo de actualización descargado."
        }
        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, apkFile)
        val clip = ClipData.newRawUri("FamiChat APK", contentUri)

        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            clipData = clip
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
        }

        if (viewIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(viewIntent)
            return@runCatching
        }

        @Suppress("DEPRECATION")
        val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            clipData = clip
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }
        if (installIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(installIntent)
            return@runCatching
        }

        context.startActivity(viewIntent)
    }
}
