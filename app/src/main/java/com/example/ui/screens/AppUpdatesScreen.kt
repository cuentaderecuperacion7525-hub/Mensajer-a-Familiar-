package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.BuildConfig
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.OnlineGreen
import com.example.util.ApkInspectionResult
import com.example.util.AppReleaseInfo
import com.example.util.AppUpdateChecker
import com.example.util.AppUpdateUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUpdatesScreen(
    isAdmin: Boolean,
    isDarkTheme: Boolean = false,
    currentRepoSlug: String,
    updateState: AppUpdateUiState,
    lastCheckedAtMs: Long,
    onCheckForUpdate: () -> Unit,
    onDownloadUpdate: (AppReleaseInfo) -> Unit,
    onCancelDownload: () -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val hasUnifiedSigning = remember(context) { AppUpdateChecker.isConsistentDebugSigning(context) }
    var canInstallPackages by remember { mutableStateOf(AppUpdateChecker.canRequestPackageInstalls(context)) }
    var waitingPermissionReturn by rememberSaveable { mutableStateOf(false) }
    var installErrorMsg by remember { mutableStateOf<String?>(null) }
    var apkInspection by remember { mutableStateOf<ApkInspectionResult?>(null) }
    var showTechnicalDetails by rememberSaveable { mutableStateOf(false) }
    val latestUpdateState by rememberUpdatedState(updateState)

    val brandBlue = if (isDarkTheme) MaterialTheme.colorScheme.primary else Color(0xFF1A5FD4)
    val surfaceBg = if (isDarkTheme) MaterialTheme.colorScheme.background else Color.White
    val cardBg = if (isDarkTheme) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f) else Color(0xFFF5F7FB)
    val textPrimary = if (isDarkTheme) MaterialTheme.colorScheme.onSurface else Color(0xFF1B1F2A)
    val textSecondary = if (isDarkTheme) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF5B6270)
    val dividerColor = if (isDarkTheme) MaterialTheme.colorScheme.outline.copy(alpha = 0.22f) else Color(0xFFE3E6EC)

    val lastCheckedLabel = remember(lastCheckedAtMs) {
        AppUpdateChecker.formatLastCheckedLabel(lastCheckedAtMs)
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val allowed = AppUpdateChecker.canRequestPackageInstalls(context)
                canInstallPackages = allowed
                val currentState = latestUpdateState
                if (allowed && waitingPermissionReturn && currentState is AppUpdateUiState.ReadyToInstall) {
                    waitingPermissionReturn = false
                    if (apkInspection?.blockingError == null) {
                        AppUpdateChecker.launchApkInstaller(context, File(currentState.apkFilePath))
                            .onFailure { installErrorMsg = it.localizedMessage }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = surfaceBg,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("updates_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = surfaceBg,
                    navigationIconContentColor = textPrimary
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .testTag("app_updates_screen"),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Centered Hero Header
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isDarkTheme) MaterialTheme.colorScheme.primaryContainer
                            else Color(0xFFE4EDFC)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.SystemUpdate,
                        contentDescription = null,
                        tint = brandBlue,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Actualizaciones",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 22.sp
                    ),
                    color = textPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Versión ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = InterFontFamily,
                        fontSize = 14.sp
                    ),
                    color = textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("updates_installed_version_text")
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Main Status Content
                when (updateState) {
                    AppUpdateUiState.Checking -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp)
                                .testTag("updates_state_checking"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                color = brandBlue,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = "Buscando actualizaciones...",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = textSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    AppUpdateUiState.Idle,
                    is AppUpdateUiState.UpToDate -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                                .testTag("updates_state_up_to_date"),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(CircleShape)
                                    .background(OnlineGreen.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = "Tienes la última versión",
                                    tint = OnlineGreen,
                                    modifier = Modifier.size(48.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            Text(
                                text = "Tienes la última versión",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 18.sp
                                ),
                                color = textPrimary,
                                textAlign = TextAlign.Center
                            )

                            if (lastCheckedLabel.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = lastCheckedLabel,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = InterFontFamily,
                                        fontSize = 13.sp
                                    ),
                                    color = textSecondary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.testTag("updates_last_checked_text")
                                )
                            }

                            Spacer(modifier = Modifier.height(28.dp))

                            Button(
                                onClick = onCheckForUpdate,
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = brandBlue,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("updates_check_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Buscar actualizaciones",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontFamily = InterFontFamily,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                )
                            }
                        }
                    }

                    is AppUpdateUiState.UpdateAvailable -> {
                        val rel = updateState.release
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("updates_state_available"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = cardBg,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Nueva versión ${rel.versionName}",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = InterFontFamily,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = brandBlue
                                    )
                                    Text(
                                        text = "Novedades de esta versión",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontFamily = InterFontFamily,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = textPrimary
                                    )
                                    Text(
                                        text = rel.releaseNotes.ifBlank { "Mejoras de estabilidad y rendimiento." },
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = InterFontFamily
                                        ),
                                        color = textSecondary
                                    )
                                }
                            }

                            if (hasUnifiedSigning == false) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Nota: si Android muestra conflicto de paquete por una firma anterior, desinstala una sola vez e instala esta versión.",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = InterFontFamily),
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }

                            Button(
                                onClick = { onDownloadUpdate(rel) },
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = brandBlue,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("updates_download_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CloudDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Actualizar ahora",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontFamily = InterFontFamily,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                )
                            }

                            OutlinedButton(
                                onClick = onBack,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("updates_later_button")
                            ) {
                                Text(
                                    text = "Más tarde",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontFamily = InterFontFamily,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = textPrimary
                                )
                            }
                        }
                    }

                    is AppUpdateUiState.Downloading -> {
                        val rel = updateState.release
                        val pct = updateState.progressPercent
                        val downloadedMb = updateState.downloadedBytes / (1024f * 1024f)
                        val totalMb = updateState.totalBytes / (1024f * 1024f)

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = cardBg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("updates_state_downloading")
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Descargando versión ${rel.versionName}...",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontFamily = InterFontFamily,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = textPrimary
                                    )
                                    Text(
                                        text = if (pct >= 0) "$pct%" else String.format("%.1f MB", downloadedMb),
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontFamily = InterFontFamily,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = brandBlue,
                                        modifier = Modifier.testTag("updates_progress_percent")
                                    )
                                }

                                if (pct >= 0) {
                                    LinearProgressIndicator(
                                        progress = { (pct / 100f).coerceIn(0f, 1f) },
                                        color = brandBlue,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .testTag("updates_progress_bar")
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        color = brandBlue,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .testTag("updates_progress_bar")
                                    )
                                }

                                val sizeLabel = if (updateState.totalBytes > 0L) {
                                    String.format("%.1f MB de %.1f MB", downloadedMb, totalMb)
                                } else if (updateState.downloadedBytes > 0L) {
                                    String.format("%.1f MB descargados", downloadedMb)
                                } else {
                                    "Preparando descarga segura..."
                                }
                                Text(
                                    text = sizeLabel,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = InterFontFamily),
                                    color = textSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedButton(
                            onClick = onCancelDownload,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("updates_cancel_download_button")
                        ) {
                            Text(
                                text = "Cancelar descarga",
                                style = MaterialTheme.typography.labelLarge.copy(fontFamily = InterFontFamily),
                                color = textPrimary
                            )
                        }
                    }

                    is AppUpdateUiState.ReadyToInstall -> {
                        val apkFile = remember(updateState.apkFilePath) { File(updateState.apkFilePath) }

                        LaunchedEffect(updateState.apkFilePath) {
                            installErrorMsg = null
                            apkInspection = null
                            canInstallPackages = AppUpdateChecker.canRequestPackageInstalls(context)
                            val inspected = withContext(Dispatchers.IO) {
                                AppUpdateChecker.inspectApkFile(
                                    context = context,
                                    apkFile = apkFile,
                                    installedVersionCode = BuildConfig.VERSION_CODE
                                )
                            }
                            apkInspection = inspected
                            if (canInstallPackages && inspected.blockingError == null && !inspected.hasSignatureConflict) {
                                AppUpdateChecker.launchApkInstaller(context, apkFile)
                                    .onFailure { installErrorMsg = it.localizedMessage }
                            }
                        }

                        val inspection = apkInspection
                        val blockingMsg = inspection?.blockingError
                        val sigWarning = inspection?.signatureWarning

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("updates_state_ready"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (blockingMsg != null) MaterialTheme.colorScheme.errorContainer
                                        else OnlineGreen.copy(alpha = 0.14f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (blockingMsg != null) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = if (blockingMsg != null) MaterialTheme.colorScheme.error else OnlineGreen,
                                    modifier = Modifier.size(44.dp)
                                )
                            }

                            Text(
                                text = if (blockingMsg != null) "No se puede instalar este archivo" else "Listo para instalar",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 18.sp
                                ),
                                color = textPrimary,
                                textAlign = TextAlign.Center
                            )

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = cardBg,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = when {
                                        blockingMsg != null -> blockingMsg
                                        canInstallPackages ->
                                            "Android te pedirá confirmar la instalación de la versión ${updateState.release.versionName}."
                                        else ->
                                            "Android te pedirá confirmar la instalación. Como es la primera vez, también puede pedirte permiso para \"instalar apps desconocidas\"."
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = InterFontFamily,
                                        lineHeight = 19.sp
                                    ),
                                    color = textSecondary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }

                            if (!sigWarning.isNullOrBlank() && blockingMsg == null) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = sigWarning,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = InterFontFamily),
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }

                            if (!installErrorMsg.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = installErrorMsg.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = InterFontFamily),
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }

                            if (blockingMsg == null) {
                                Button(
                                    onClick = {
                                        installErrorMsg = null
                                        if (AppUpdateChecker.canRequestPackageInstalls(context)) {
                                            AppUpdateChecker.launchApkInstaller(context, apkFile)
                                                .onFailure { installErrorMsg = it.localizedMessage }
                                        } else {
                                            waitingPermissionReturn = true
                                            AppUpdateChecker.openInstallPermissionSettings(context)
                                        }
                                    },
                                    shape = RoundedCornerShape(50),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = brandBlue,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("updates_install_button")
                                ) {
                                    Icon(Icons.Filled.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (!canInstallPackages) "Permitir e instalar" else "Instalar ahora",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontFamily = InterFontFamily,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                }
                            }
                        }
                    }

                    is AppUpdateUiState.Error -> {
                        val failedRelease = updateState.release
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("updates_state_error"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = "Error",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(40.dp)
                                )
                            }

                            Text(
                                text = "No se pudo completar",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 18.sp
                                ),
                                color = textPrimary,
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = updateState.message,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = InterFontFamily),
                                color = textSecondary,
                                textAlign = TextAlign.Center
                            )

                            Button(
                                onClick = {
                                    if (failedRelease != null && failedRelease.downloadUrl.isNotBlank()) {
                                        onDownloadUpdate(failedRelease)
                                    } else {
                                        onCheckForUpdate()
                                    }
                                },
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = brandBlue,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("updates_retry_button")
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Reintentar",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontFamily = InterFontFamily,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }

                            if (failedRelease != null && AppUpdateChecker.isTrustedDownloadUrl(failedRelease.downloadUrl)) {
                                TextButton(
                                    onClick = { AppUpdateChecker.openDownloadInBrowser(context, failedRelease) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("updates_browser_fallback_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Descargar en navegador")
                                }
                            }
                        }
                    }

                    is AppUpdateUiState.NotConfigured -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp)
                                .testTag("updates_state_not_configured"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Actualizaciones no activadas",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = textPrimary,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Esta instalación aún no tiene un canal de actualizaciones vinculado.",
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = InterFontFamily),
                                color = textSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Admin-only read-only Technical Details (collapsed by default, no repo editor)
                if (isAdmin) {
                    Spacer(modifier = Modifier.height(32.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = cardBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showTechnicalDetails = !showTechnicalDetails }
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                                    .testTag("updates_technical_details_toggle"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Detalles técnicos",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = InterFontFamily,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = textPrimary
                                )
                                Icon(
                                    imageVector = if (showTechnicalDetails) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = if (showTechnicalDetails) "Ocultar detalles técnicos" else "Mostrar detalles técnicos",
                                    tint = textSecondary
                                )
                            }
                            AnimatedVisibility(visible = showTechnicalDetails) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                                        .testTag("updates_technical_details_content"),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    HorizontalDivider(color = dividerColor)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Compilación: ${BuildConfig.VERSION_CODE}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = InterFontFamily),
                                        color = textSecondary
                                    )
                                    Text(
                                        text = "Origen: ${currentRepoSlug.ifBlank { "No configurado" }}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = InterFontFamily),
                                        color = textSecondary
                                    )
                                    Text(
                                        text = "Descarga segura: HTTPS verificado y comprobación de firma",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = InterFontFamily),
                                        color = textSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
