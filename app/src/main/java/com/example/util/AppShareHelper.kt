package com.example.util

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.BuildConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shares the currently installed FamiChat APK using a clean user-facing filename
 * (e.g. "FamiChat-2.8.40.apk") without exposing the internal Android package ID.
 */
object AppShareHelper {
    private const val SHARED_DIR_NAME = "shared_media"
    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    private const val FILE_PREFIX = "FamiChat-"
    private const val FALLBACK_FILE_NAME = "FamiChat.apk"

    /**
     * Builds a clean filename for sharing, e.g. "FamiChat-2.8.40.apk".
     */
    fun buildShareApkFileName(versionName: String): String {
        val cleanVersion = versionName.trim()
            .removePrefix("v")
            .removePrefix("V")
            .replace(Regex("[^A-Za-z0-9._-]"), "")
            .trim('.', '-', '_')
        return if (cleanVersion.isBlank()) {
            FALLBACK_FILE_NAME
        } else {
            "$FILE_PREFIX$cleanVersion.apk"
        }
    }

    /**
     * Copies the installed APK into the FileProvider-backed cache directory with a clean filename.
     * Reuses an existing cached copy when the file length and timestamp already match.
     */
    suspend fun prepareInstalledApkForSharing(
        context: Context,
        versionName: String = BuildConfig.VERSION_NAME
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val sourcePath = context.applicationInfo.publicSourceDir
                ?.takeIf { it.isNotBlank() }
                ?: context.applicationInfo.sourceDir
                ?: error("No se encontró el archivo de instalación de FamiChat.")
            val sourceFile = File(sourcePath)
            if (!sourceFile.isFile || !sourceFile.canRead() || sourceFile.length() <= 0L) {
                error("No se pudo leer el archivo de instalación de FamiChat.")
            }

            val targetDir = File(context.cacheDir, SHARED_DIR_NAME).apply { mkdirs() }
            val targetName = buildShareApkFileName(versionName)
            val targetFile = File(targetDir, targetName)

            // Clean older cached FamiChat APKs so cache does not grow across updates
            targetDir.listFiles()?.forEach { existing ->
                val name = existing.name
                val isOldShareApk = (name == FALLBACK_FILE_NAME || name.startsWith(FILE_PREFIX)) &&
                    (name.endsWith(".apk") || name.endsWith(".apk.tmp"))
                if (isOldShareApk && name != targetName) {
                    runCatching { existing.delete() }
                }
            }

            if (targetFile.isFile &&
                targetFile.length() == sourceFile.length() &&
                targetFile.lastModified() >= sourceFile.lastModified()
            ) {
                return@runCatching targetFile
            }

            val tempFile = File(targetDir, "$targetName.tmp")
            if (tempFile.exists()) runCatching { tempFile.delete() }
            sourceFile.inputStream().use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                    output.flush()
                }
            }
            if (targetFile.exists()) runCatching { targetFile.delete() }
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                runCatching { tempFile.delete() }
            }
            targetFile
        }
    }

    /**
     * Opens the Android system share chooser for the prepared APK file.
     */
    fun launchShareApkChooser(context: Context, apkFile: File): Result<Unit> = runCatching {
        require(apkFile.isFile && apkFile.length() > 0L) {
            "El archivo de FamiChat no está disponible para compartir."
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = APK_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, apkFile.nameWithoutExtension)
            clipData = ClipData.newUri(context.contentResolver, apkFile.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(sendIntent, "Compartir FamiChat").apply {
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(chooser)
    }
}
