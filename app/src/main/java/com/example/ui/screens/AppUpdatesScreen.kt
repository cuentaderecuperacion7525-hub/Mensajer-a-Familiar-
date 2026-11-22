package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.util.VersionHistoryUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val MagicPillBlue = Color(0xFF246BFD)
private val MagicPillBluePressed = Color(0xFF1D58D8)
private val WarningAmber = Color(0xFFF59E0B)

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
    onBack: () -> Unit,
    versionHistoryState: VersionHistoryUiState = VersionHistoryUiState.Idle,
    onLoadVersionHistory: () -> Unit = {},
    onPostponeUpdate: () -> Unit = onBack
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val hasUnifiedSigning = remember(context) { AppUpdateChecker.isConsistentDebugSigning(context) }
    var canInstallPackages by remember { mutableStateOf(AppUpdateChecker.canRequestPackageInstalls(context)) }
    var waitingPermissionReturn by rememberSaveable { mutableStateOf(false) }
    var installErrorMsg by remember { mutableStateOf<String?>(null) }
    var apkInspection by remember { mutableStateOf<ApkInspectionResult?>(null) }
    var showTechnicalDetails by rememberSaveable { mutableStateOf(false) }
    var showHistorySheet by rememberSaveable { mutableStateOf(false) }
    val latestUpdateState by rememberUpdatedState(updateState)

    val surfaceBg = MaterialTheme.colorScheme.background
    val cardBg = if (isDarkTheme) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val accentBlue = MaterialTheme.colorScheme.primary
    val heroBadgeBg = MaterialTheme.colorScheme.primaryContainer
    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = if (isDarkTheme) 0.35f else 0.45f)
    val bulletBlue = if (isDarkTheme) Color(0xFF4C8DFF) else MagicPillBlue
    val bulletGreen = OnlineGreen
    val bulletAmber = WarningAmber

    val lastCheckedLabel = remember(lastCheckedAtMs) {
        AppUpdateChecker.formatLastCheckedLabel(lastCheckedAtMs)
    }

    LaunchedEffect(versionHistoryState) {
        if (versionHistoryState is VersionHistoryUiState.Unavailable) {
            showHistorySheet = false
        }
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
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("updates_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Volver",
                            tint = textPrimary,
                            modifier = Modifier.size(30.dp)
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
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .testTag("app_updates_screen"),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Consistent Hero Header across all states
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(heroBadgeBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.SystemUpdate,
                        contentDescription = null,
                        tint = accentBlue,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Actualizaciones",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 22.sp
                    ),
                    color = textPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Instalada: ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = InterFontFamily,
                        fontSize = 13.sp
                    ),
                    color = textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("updates_installed_version_text")
                )

                Spacer(modifier = Modifier.height(22.dp))

                // Main State Body
                when (updateState) {
                    AppUpdateUiState.Checking -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                                .testTag("updates_state_checking"),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(MagicPillBlue.copy(alpha = if (isDarkTheme) 0.16f else 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = MagicPillBlue,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("updates_checking_indicator")
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Buscando actualizaciones…",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 18.sp
                                ),
                                color = textPrimary,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(28.dp))

                            MagicCheckUpdatesPillButton(
                                isChecking = true,
                                onClick = onCheckForUpdate
                            )
                        }
                    }

                    AppUpdateUiState.Idle,
                    is AppUpdateUiState.UpToDate -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                                .testTag("updates_state_up_to_date"),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(OnlineGreen.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = "Tienes la última versión",
                                    tint = OnlineGreen,
                                    modifier = Modifier.size(44.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Tienes la última versión",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.Medium,
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

                            MagicCheckUpdatesPillButton(
                                isChecking = false,
                                onClick = onCheckForUpdate
                            )
                        }
                    }

                    is AppUpdateUiState.UpdateAvailable -> {
                        val rel = updateState.release
                        val releaseBullets = remember(rel.releaseNotes) {
                            AppUpdateChecker.parseReleaseNotesBullets(rel.releaseNotes, maxItems = 5)
                        }
                        val showHistoryLink = versionHistoryState !is VersionHistoryUiState.Unavailable &&
                            (currentRepoSlug.isNotBlank() || versionHistoryState is VersionHistoryUiState.Loaded)

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("updates_state_available"),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Double-ring blue circle with downward arrow
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(MagicPillBlue.copy(alpha = if (isDarkTheme) 0.18f else 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(MagicPillBlue),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ArrowDownward,
                                        contentDescription = "Hay una versión nueva",
                                        tint = Color.White,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "Hay una versión nueva",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 18.sp
                                ),
                                color = textPrimary,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Versión ${rel.versionName}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = InterFontFamily,
                                    fontSize = 12.sp
                                ),
                                color = textSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.testTag("updates_new_version_text")
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            // "Qué incluye" card — shown ONLY when real release notes exist
                            if (releaseBullets.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = cardBg,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("updates_whats_included_card")
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp)
                                    ) {
                                        Text(
                                            text = "Qué incluye",
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontFamily = InterFontFamily,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 14.sp
                                            ),
                                            color = textPrimary
                                        )

                                        Spacer(modifier = Modifier.height(12.dp))

                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            releaseBullets.forEachIndexed { index, item ->
                                                val dotColor = when (index % 3) {
                                                    0 -> bulletBlue
                                                    1 -> bulletGreen
                                                    else -> bulletAmber
                                                }
                                                ReleaseNoteBulletRow(
                                                    text = item,
                                                    dotColor = dotColor,
                                                    textColor = textPrimary.copy(alpha = 0.88f)
                                                )
                                            }
                                        }

                                        if (showHistoryLink) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            VersionHistoryLinkRow(
                                                accentColor = accentBlue,
                                                onClick = {
                                                    showHistorySheet = true
                                                    onLoadVersionHistory()
                                                }
                                            )
                                        }
                                    }
                                }
                            } else if (showHistoryLink) {
                                VersionHistoryLinkRow(
                                    accentColor = accentBlue,
                                    centered = true,
                                    onClick = {
                                        showHistorySheet = true
                                        onLoadVersionHistory()
                                    }
                                )
                            }

                            if (hasUnifiedSigning == false) {
                                Spacer(modifier = Modifier.height(12.dp))
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

                            Spacer(modifier = Modifier.height(22.dp))

                            PrimaryPillActionButton(
                                text = "Actualizar ahora",
                                onClick = { onDownloadUpdate(rel) },
                                testTag = "updates_download_button"
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            TextButton(
                                onClick = onPostponeUpdate,
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .heightIn(min = 44.dp)
                                    .testTag("updates_later_button")
                            ) {
                                Text(
                                    text = "Más tarde",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = InterFontFamily,
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 13.sp
                                    ),
                                    color = textSecondary
                                )
                            }
                        }
                    }

                    is AppUpdateUiState.Downloading -> {
                        val rel = updateState.release
                        val pct = updateState.progressPercent
                        val downloadedMb = updateState.downloadedBytes / (1024f * 1024f)
                        val totalMb = updateState.totalBytes / (1024f * 1024f)
                        val hasDeterminateProgress = pct >= 0 && (updateState.downloadedBytes > 0L || pct > 0)
                        val animatedProgress by animateFloatAsState(
                            targetValue = (pct / 100f).coerceIn(0f, 1f),
                            animationSpec = tween(durationMillis = if (pct >= 100) 120 else 150),
                            label = "updateDownloadProgress"
                        )
                        val trackColor = MagicPillBlue.copy(alpha = 0.16f)

                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = cardBg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("updates_state_downloading")
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (pct >= 100) {
                                            "Descarga completa"
                                        } else {
                                            "Descargando versión ${rel.versionName}…"
                                        },
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontFamily = InterFontFamily,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 14.sp
                                        ),
                                        color = textPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (pct >= 0) "$pct%" else String.format("%.1f MB", downloadedMb),
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontFamily = InterFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp
                                        ),
                                        color = accentBlue,
                                        modifier = Modifier.testTag("updates_progress_percent")
                                    )
                                }

                                if (hasDeterminateProgress) {
                                    LinearProgressIndicator(
                                        progress = { animatedProgress },
                                        color = MagicPillBlue,
                                        trackColor = trackColor,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .testTag("updates_progress_bar")
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        color = MagicPillBlue,
                                        trackColor = trackColor,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .testTag("updates_progress_bar")
                                    )
                                }

                                val sizeLabel = when {
                                    pct >= 100 && updateState.totalBytes > 0L ->
                                        String.format("%.1f MB de %.1f MB · Preparando instalación…", downloadedMb, totalMb)
                                    updateState.totalBytes > 0L && updateState.downloadedBytes > 0L ->
                                        String.format("%.1f MB de %.1f MB", downloadedMb, totalMb)
                                    updateState.downloadedBytes > 0L ->
                                        String.format("%.1f MB descargados", downloadedMb)
                                    updateState.statusLabel.isNotBlank() ->
                                        updateState.statusLabel
                                    else ->
                                        "Conectando con el servidor…"
                                }
                                Text(
                                    text = sizeLabel,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = InterFontFamily,
                                        fontSize = 12.sp
                                    ),
                                    color = textSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        OutlinedButton(
                            onClick = onCancelDownload,
                            enabled = pct < 100,
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 46.dp)
                                .testTag("updates_cancel_download_button")
                        ) {
                            Text(
                                text = "Cancelar",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp
                                ),
                                color = if (pct < 100) textPrimary else textSecondary
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
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (blockingMsg != null) WarningAmber.copy(alpha = 0.16f)
                                        else OnlineGreen.copy(alpha = 0.14f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (blockingMsg != null) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = if (blockingMsg != null) WarningAmber else OnlineGreen,
                                    modifier = Modifier.size(44.dp)
                                )
                            }

                            Text(
                                text = if (blockingMsg != null) "No se puede instalar este archivo" else "Lista para instalar",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 18.sp
                                ),
                                color = textPrimary,
                                textAlign = TextAlign.Center
                            )

                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = cardBg,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = blockingMsg ?: "Android te pedirá confirmar la instalación.",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = InterFontFamily,
                                            fontSize = 13.sp,
                                            lineHeight = 19.sp
                                        ),
                                        color = textSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                    if (blockingMsg == null && !canInstallPackages) {
                                        Text(
                                            text = "Activa el permiso para FamiChat y vuelve a esta pantalla.",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = InterFontFamily,
                                                fontSize = 12.sp,
                                                lineHeight = 17.sp
                                            ),
                                            color = accentBlue,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.testTag("updates_install_permission_hint")
                                        )
                                    }
                                }
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
                                Spacer(modifier = Modifier.height(4.dp))
                                PrimaryPillActionButton(
                                    text = "Instalar",
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
                                    testTag = "updates_install_button"
                                )
                            }
                        }
                    }

                    is AppUpdateUiState.Error -> {
                        val failedRelease = updateState.release
                        val briefReason = remember(updateState.message, failedRelease) {
                            AppUpdateChecker.simplifyUpdateErrorMessage(
                                rawMessage = updateState.message,
                                isDownloadError = failedRelease != null
                            )
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("updates_state_error"),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(WarningAmber.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = "Advertencia",
                                    tint = WarningAmber,
                                    modifier = Modifier.size(38.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "No se pudo completar",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 18.sp
                                ),
                                color = textPrimary,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = briefReason,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = InterFontFamily,
                                    fontSize = 13.sp
                                ),
                                color = textSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.testTag("updates_error_reason_text")
                            )

                            Spacer(modifier = Modifier.height(22.dp))

                            PrimaryPillActionButton(
                                text = "Reintentar",
                                onClick = {
                                    if (failedRelease != null && failedRelease.downloadUrl.isNotBlank()) {
                                        onDownloadUpdate(failedRelease)
                                    } else {
                                        onCheckForUpdate()
                                    }
                                },
                                testTag = "updates_retry_button"
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            TextButton(
                                onClick = onBack,
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .heightIn(min = 44.dp)
                                    .testTag("updates_close_error_button")
                            ) {
                                Text(
                                    text = "Cerrar",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = InterFontFamily,
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 13.sp
                                    ),
                                    color = textSecondary
                                )
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
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Actualizaciones no activadas",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 18.sp
                                ),
                                color = textPrimary,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Esta instalación aún no tiene un canal de actualizaciones vinculado.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = InterFontFamily,
                                    fontSize = 13.sp
                                ),
                                color = textSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Admin-only collapsible Technical Details (never exposes repo slug, account, URLs, or tokens)
                if (isAdmin) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = cardBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
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
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 14.sp
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
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = InterFontFamily,
                                            fontSize = 12.sp
                                        ),
                                        color = textSecondary
                                    )
                                    Text(
                                        text = "Canal de actualización: ${if (currentRepoSlug.isNotBlank()) "Configurado" else "No configurado"}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = InterFontFamily,
                                            fontSize = 12.sp
                                        ),
                                        color = textSecondary
                                    )
                                    Text(
                                        text = "Descarga segura: HTTPS verificado y comprobación de firma",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = InterFontFamily,
                                            fontSize = 12.sp
                                        ),
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

    if (showHistorySheet && versionHistoryState !is VersionHistoryUiState.Unavailable) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showHistorySheet = false },
            sheetState = sheetState,
            containerColor = if (isDarkTheme) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp)
                    .testTag("updates_version_history_sheet")
            ) {
                Text(
                    text = "Historial de versiones",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp
                    ),
                    color = textPrimary
                )
                Spacer(modifier = Modifier.height(14.dp))

                when (versionHistoryState) {
                    VersionHistoryUiState.Idle,
                    VersionHistoryUiState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = MagicPillBlue,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("updates_history_loading")
                            )
                        }
                    }

                    is VersionHistoryUiState.Loaded -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp)
                        ) {
                            items(versionHistoryState.entries) { entry ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = cardBg,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Versión ${entry.versionName}",
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontFamily = InterFontFamily,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 14.sp
                                            ),
                                            color = textPrimary
                                        )
                                        if (entry.bullets.isEmpty()) {
                                            Text(
                                                text = "Sin notas registradas",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontFamily = InterFontFamily,
                                                    fontSize = 12.sp
                                                ),
                                                color = textSecondary
                                            )
                                        } else {
                                            entry.bullets.forEachIndexed { idx, bullet ->
                                                val dotColor = when (idx % 3) {
                                                    0 -> bulletBlue
                                                    1 -> bulletGreen
                                                    else -> bulletAmber
                                                }
                                                ReleaseNoteBulletRow(
                                                    text = bullet,
                                                    dotColor = dotColor,
                                                    textColor = textPrimary.copy(alpha = 0.88f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    VersionHistoryUiState.Unavailable -> Unit
                }
            }
        }
    }
}

@Composable
private fun ReleaseNoteBulletRow(
    text: String,
    dotColor: Color,
    textColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = InterFontFamily,
                fontSize = 12.sp,
                lineHeight = 17.sp
            ),
            color = textColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun VersionHistoryLinkRow(
    accentColor: Color,
    centered: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .then(if (centered) Modifier else Modifier.fillMaxWidth())
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
            .testTag("updates_version_history_link"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (centered) Arrangement.Center else Arrangement.Start
    ) {
        Text(
            text = "Ver historial de versiones",
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp
            ),
            color = accentColor
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun PrimaryPillActionButton(
    text: String,
    onClick: () -> Unit,
    testTag: String
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val backgroundColor by animateColorAsState(
        targetValue = if (isPressed || isHovered) MagicPillBluePressed else MagicPillBlue,
        animationSpec = tween(durationMillis = 120),
        label = "updatesPrimaryPillBg"
    )

    Button(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            hoveredElevation = 0.dp,
            disabledElevation = 0.dp
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = Color.White
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .testTag(testTag)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                letterSpacing = 0.sp
            ),
            color = Color.White,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MagicCheckUpdatesPillButton(
    isChecking: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isChecking -> MagicPillBlue.copy(alpha = 0.85f)
            isPressed || isHovered -> MagicPillBluePressed
            else -> MagicPillBlue
        },
        animationSpec = tween(durationMillis = 120),
        label = "magicUpdatePillBackground"
    )

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        val targetWidth = maxOf(240.dp, maxWidth * 0.65f).coerceAtMost(maxWidth)
        Button(
            onClick = onClick,
            enabled = !isChecking,
            interactionSource = interactionSource,
            shape = CircleShape,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                hoveredElevation = 0.dp,
                disabledElevation = 0.dp
            ),
            colors = ButtonDefaults.buttonColors(
                containerColor = backgroundColor,
                contentColor = Color.White,
                disabledContainerColor = backgroundColor,
                disabledContentColor = Color.White
            ),
            modifier = Modifier
                .width(targetWidth)
                .heightIn(min = 46.dp)
                .testTag("updates_check_button")
        ) {
            if (isChecking) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .size(16.dp)
                        .testTag("updates_check_spinner")
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = if (isChecking) "Buscando..." else "Buscar actualizaciones",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    letterSpacing = 0.sp
                ),
                color = Color.White,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

