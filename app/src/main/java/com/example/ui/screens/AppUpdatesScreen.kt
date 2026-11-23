package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
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
import com.example.util.ApkInspectionResult
import com.example.util.AppReleaseInfo
import com.example.util.AppUpdateChecker
import com.example.util.AppUpdateUiState
import com.example.util.VersionHistoryUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// Exact design palette for Light Theme (Dark Theme automatically uses MaterialTheme.colorScheme)
private val UpdatesBrandBlue = Color(0xFF1A5FD4)
private val UpdatesBrandBluePressed = Color(0xFF154FB3)
private val UpdatesIconBoxBlueLight = Color(0xFFE4EDFC)
private val UpdatesCardBgLight = Color(0xFFF5F7FB)
private val UpdatesDividerLight = Color(0xFFE3E6EC)
private val UpdatesInkPrimaryLight = Color(0xFF1B1F2A)
private val UpdatesInkSecondaryLight = Color(0xFF5B6270)
private val UpdatesChevronLight = Color(0xFF5B6270)
private val UpdatesProgressTrackLight = Color(0xFFE4E8F0)
private val UpdatesGreenCircle = Color(0xFF22C55E)
private val UpdatesGreenHaloLight = Color(0xFFE1F5EE)
private val UpdatesRedDot = Color(0xFFD93025)
private val UpdatesCancelRed = Color(0xFFB3261E)
private val UpdatesWarningAmber = Color(0xFFE0A030)

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
    onOpenWhatsNew: () -> Unit = {},
    onRetryCheckForUpdate: () -> Unit = onCheckForUpdate,
    versionHistoryState: VersionHistoryUiState = VersionHistoryUiState.Idle,
    onLoadVersionHistory: () -> Unit = {},
    onPostponeUpdate: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val hasUnifiedSigning = remember(context) { AppUpdateChecker.isConsistentDebugSigning(context) }
    var canInstallPackages by remember { mutableStateOf(AppUpdateChecker.canRequestPackageInstalls(context)) }
    var waitingPermissionReturn by rememberSaveable { mutableStateOf(false) }
    var showTechnicalDetails by rememberSaveable { mutableStateOf(false) }
    var isDialogVisible by rememberSaveable {
        mutableStateOf(
            updateState is AppUpdateUiState.Downloading ||
                updateState is AppUpdateUiState.ReadyToInstall
        )
    }
    var showPermissionDialog by rememberSaveable { mutableStateOf(false) }
    var installErrorMsg by remember { mutableStateOf<String?>(null) }
    var apkInspection by remember { mutableStateOf<ApkInspectionResult?>(null) }
    val latestUpdateState by rememberUpdatedState(updateState)

    val isChecking = updateState is AppUpdateUiState.Checking
    val hasNewUpdate = updateState is AppUpdateUiState.UpdateAvailable ||
        updateState is AppUpdateUiState.ReadyToInstall

    // Theme-aware tokens matching the exact light reference images and existing dark theme
    val screenBg = if (isDarkTheme) MaterialTheme.colorScheme.background else Color.White
    val cardBg = if (isDarkTheme) MaterialTheme.colorScheme.surface else UpdatesCardBgLight
    val dialogBg = if (isDarkTheme) MaterialTheme.colorScheme.surface else Color.White
    val dividerColor = if (isDarkTheme) MaterialTheme.colorScheme.outline.copy(alpha = 0.28f) else UpdatesDividerLight
    val inkPrimary = if (isDarkTheme) MaterialTheme.colorScheme.onSurface else UpdatesInkPrimaryLight
    val inkSecondary = if (isDarkTheme) MaterialTheme.colorScheme.onSurfaceVariant else UpdatesInkSecondaryLight
    val chevronTint = if (isDarkTheme) MaterialTheme.colorScheme.onSurfaceVariant else UpdatesChevronLight
    val accentBlue = if (isDarkTheme) MaterialTheme.colorScheme.primary else UpdatesBrandBlue
    val iconBoxBg = if (isDarkTheme) MaterialTheme.colorScheme.primaryContainer else UpdatesIconBoxBlueLight
    val iconBoxTint = if (isDarkTheme) MaterialTheme.colorScheme.onPrimaryContainer else UpdatesBrandBlue
    val greenHalo = if (isDarkTheme) UpdatesGreenCircle.copy(alpha = 0.18f) else UpdatesGreenHaloLight
    val blueHalo = if (isDarkTheme) accentBlue.copy(alpha = 0.20f) else UpdatesIconBoxBlueLight
    val amberHalo = UpdatesWarningAmber.copy(alpha = if (isDarkTheme) 0.20f else 0.15f)
    val progressTrack = if (isDarkTheme) MaterialTheme.colorScheme.outline.copy(alpha = 0.28f) else UpdatesProgressTrackLight
    val cancelRedColor = if (isDarkTheme) Color(0xFFFF7B73) else UpdatesCancelRed

    val lastCheckedLabel = remember(lastCheckedAtMs) {
        AppUpdateChecker.formatLastCheckedLabel(lastCheckedAtMs)
    }

    // Automatically surface active download / ready-to-install progress
    LaunchedEffect(updateState) {
        if (updateState is AppUpdateUiState.Downloading ||
            updateState is AppUpdateUiState.ReadyToInstall
        ) {
            isDialogVisible = true
        }
        if (updateState !is AppUpdateUiState.ReadyToInstall) {
            showPermissionDialog = false
            installErrorMsg = null
            apkInspection = null
        }
    }

    // Inspect APK safety/signature when ReadyToInstall
    LaunchedEffect(updateState) {
        if (updateState is AppUpdateUiState.ReadyToInstall) {
            val apkFile = File(updateState.apkFilePath)
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
        }
    }

    // Re-check install permission when returning from Android Settings
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val allowed = AppUpdateChecker.canRequestPackageInstalls(context)
                canInstallPackages = allowed
                val currentState = latestUpdateState
                if (allowed && waitingPermissionReturn && currentState is AppUpdateUiState.ReadyToInstall) {
                    waitingPermissionReturn = false
                    showPermissionDialog = false
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

    // Back press closes any open dialog without leaving the Actualizaciones screen
    // and never cancels an active download. Checking runs inline in Row 1 (no modal overlay).
    val anyOverlayOpen = showPermissionDialog || (isDialogVisible && !isChecking)
    BackHandler(enabled = anyOverlayOpen) {
        if (showPermissionDialog) {
            showPermissionDialog = false
        } else {
            isDialogVisible = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // PANTALLA 1: ACTUALIZACIONES (Imagen 1)
        Scaffold(
            containerColor = screenBg,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Actualizaciones",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 20.sp
                            ),
                            color = inkPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
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
                                tint = inkPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = screenBg,
                        titleContentColor = inkPrimary,
                        navigationIconContentColor = inkPrimary
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
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .testTag("app_updates_screen")
                ) {
                    // Main grouped card (#F5F7FB, 18dp corners)
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = cardBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Row 1: Buscar actualizaciones (con estado de búsqueda integrado en la misma fila)
                            val syncRotation = if (isChecking) {
                                val infiniteTransition = rememberInfiniteTransition(label = "updates_sync_spin")
                                val angle by infiniteTransition.animateFloat(
                                    initialValue = 0f,
                                    targetValue = 360f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(durationMillis = 1100, easing = LinearEasing),
                                        repeatMode = RepeatMode.Restart
                                    ),
                                    label = "updates_sync_angle"
                                )
                                angle
                            } else {
                                0f
                            }
                            val checkingIconBorder by animateColorAsState(
                                targetValue = if (isChecking) accentBlue.copy(alpha = 0.55f) else Color.Transparent,
                                animationSpec = tween(durationMillis = 220),
                                label = "updates_icon_border"
                            )
                            val subtitleColor by animateColorAsState(
                                targetValue = if (isChecking) accentBlue else inkSecondary,
                                animationSpec = tween(durationMillis = 220),
                                label = "updates_subtitle_color"
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (isChecking) Modifier.testTag("updates_state_checking") else Modifier
                                    )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 72.dp)
                                        .clickable(
                                            enabled = !isChecking,
                                            role = Role.Button,
                                            onClick = {
                                                isDialogVisible = true
                                                onCheckForUpdate()
                                            }
                                        )
                                        .padding(horizontal = 16.dp, vertical = 16.dp)
                                        .testTag("updates_check_button"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(13.dp))
                                            .background(iconBoxBg)
                                            .border(
                                                width = if (isChecking) 1.dp else 0.dp,
                                                color = checkingIconBorder,
                                                shape = RoundedCornerShape(13.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Sync,
                                            contentDescription = null,
                                            tint = if (isChecking) accentBlue else iconBoxTint,
                                            modifier = Modifier
                                                .size(22.dp)
                                                .rotate(syncRotation)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "Buscar actualizaciones",
                                                style = MaterialTheme.typography.titleSmall.copy(
                                                    fontFamily = InterFontFamily,
                                                    fontWeight = FontWeight.Medium,
                                                    fontSize = 15.sp
                                                ),
                                                color = inkPrimary,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (hasNewUpdate && !isChecking) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(UpdatesRedDot)
                                                        .testTag("updates_row_new_badge_dot")
                                                )
                                            }
                                        }
                                        Text(
                                            text = if (isChecking) {
                                                "Buscando actualizaciones…"
                                            } else {
                                                "Comprueba si hay una versión nueva de FamiChat"
                                            },
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = InterFontFamily,
                                                fontWeight = if (isChecking) FontWeight.Medium else FontWeight.Normal,
                                                fontSize = 13.sp,
                                                lineHeight = 18.sp
                                            ),
                                            color = subtitleColor,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    if (isChecking) {
                                        CircularProgressIndicator(
                                            color = accentBlue,
                                            trackColor = progressTrack,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier
                                                .size(18.dp)
                                                .testTag("updates_checking_indicator")
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = null,
                                            tint = chevronTint,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                AnimatedVisibility(visible = isChecking) {
                                    LinearProgressIndicator(
                                        color = accentBlue,
                                        trackColor = progressTrack.copy(alpha = 0.45f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp)
                                            .height(2.dp)
                                            .clip(CircleShape)
                                            .testTag("updates_checking_progress_bar")
                                    )
                                }
                            }

                            if (!isChecking) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    thickness = 1.dp,
                                    color = dividerColor
                                )
                            }

                            // Row 2: Novedades de esta versión
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 72.dp)
                                    .clickable(
                                        role = Role.Button,
                                        onClick = onOpenWhatsNew
                                    )
                                    .padding(horizontal = 16.dp, vertical = 16.dp)
                                    .testTag("updates_whats_new_row"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(13.dp))
                                        .background(iconBoxBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Description,
                                        contentDescription = null,
                                        tint = iconBoxTint,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = "Novedades de esta versión",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontFamily = InterFontFamily,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 15.sp
                                        ),
                                        color = inkPrimary
                                    )
                                    Text(
                                        text = "Mira lo nuevo que trae",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = InterFontFamily,
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp
                                        ),
                                        color = inkSecondary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = chevronTint,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    // Admin-only collapsible Technical Details card with "Admin" pill badge
                    if (isAdmin) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = cardBg,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 52.dp)
                                        .clickable(
                                            role = Role.Button,
                                            onClick = { showTechnicalDetails = !showTechnicalDetails }
                                        )
                                        .padding(horizontal = 16.dp, vertical = 14.dp)
                                        .testTag("updates_technical_details_toggle"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Detalles técnicos",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = InterFontFamily,
                                            fontWeight = FontWeight.Normal,
                                            fontSize = 15.sp
                                        ),
                                        color = inkPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = iconBoxBg,
                                        modifier = Modifier.testTag("updates_admin_badge")
                                    ) {
                                        Text(
                                            text = "Admin",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = InterFontFamily,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 12.sp
                                            ),
                                            color = iconBoxTint,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Icon(
                                        imageVector = if (showTechnicalDetails) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                        contentDescription = if (showTechnicalDetails) "Ocultar detalles técnicos" else "Mostrar detalles técnicos",
                                        tint = chevronTint,
                                        modifier = Modifier.size(22.dp)
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
                                            text = "Versión instalada: ${BuildConfig.VERSION_NAME}",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = InterFontFamily,
                                                fontSize = 12.sp
                                            ),
                                            color = inkSecondary
                                        )
                                        Text(
                                            text = "Compilación: ${BuildConfig.VERSION_CODE}",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = InterFontFamily,
                                                fontSize = 12.sp
                                            ),
                                            color = inkSecondary
                                        )
                                        Text(
                                            text = "Canal de actualización: ${if (currentRepoSlug.isNotBlank()) "Configurado" else "No configurado"}",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = InterFontFamily,
                                                fontSize = 12.sp
                                            ),
                                            color = inkSecondary
                                        )
                                        Text(
                                            text = "Descarga segura: HTTPS verificado y comprobación de firma",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = InterFontFamily,
                                                fontSize = 12.sp
                                            ),
                                            color = inkSecondary
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

        // OVERLAY CUADROS (Imágenes 2, 3 y 4) sobre capa negra al 55% de opacidad
        if (anyOverlayOpen) {
            val backdropInteraction = remember { MutableInteractionSource() }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("updates_dialog_backdrop"),
                contentAlignment = Alignment.Center
            ) {
                // Capa oscura atenuada detrás del cuadro; tocar fuera cierra el cuadro sin cancelar descargas
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable(
                            interactionSource = backdropInteraction,
                            indication = null,
                            onClick = {
                                if (showPermissionDialog) {
                                    showPermissionDialog = false
                                } else {
                                    // Dismissing the dialog never cancels a running download
                                    isDialogVisible = false
                                }
                            }
                        )
                )

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = dialogBg,
                    tonalElevation = 0.dp,
                    shadowElevation = 8.dp,
                    border = if (isDarkTheme) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 24.dp)
                        .widthIn(min = 260.dp, max = 312.dp)
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectTapGestures { /* consume taps inside card so scrim does not dismiss */ }
                        }
                        .testTag("updates_dialog_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 22.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        when {
                            // CUADRO DE PERMISO NECESARIO (solo si Android pide permiso de apps desconocidas)
                            showPermissionDialog -> {
                                PermissionRequiredDialogContent(
                                    inkPrimary = inkPrimary,
                                    inkSecondary = inkSecondary,
                                    onOpenSettings = {
                                        waitingPermissionReturn = true
                                        AppUpdateChecker.openInstallPermissionSettings(context)
                                    },
                                    onCancel = {
                                        showPermissionDialog = false
                                    }
                                )
                            }

                            // CUADRO 2: ESTÁS AL DÍA (Imagen 2)
                            updateState is AppUpdateUiState.Idle ||
                                updateState is AppUpdateUiState.UpToDate -> {
                                UpToDateDialogContent(
                                    installedVersionName = BuildConfig.VERSION_NAME,
                                    lastCheckedLabel = lastCheckedLabel,
                                    inkPrimary = inkPrimary,
                                    inkSecondary = inkSecondary,
                                    greenHalo = greenHalo,
                                    onAccept = { isDialogVisible = false }
                                )
                            }

                            // CUADRO 3: HAY UNA VERSIÓN NUEVA (Imagen 3)
                            updateState is AppUpdateUiState.UpdateAvailable -> {
                                UpdateAvailableDialogContent(
                                    release = updateState.release,
                                    isDarkTheme = isDarkTheme,
                                    hasUnifiedSigning = hasUnifiedSigning,
                                    inkPrimary = inkPrimary,
                                    inkSecondary = inkSecondary,
                                    blueHalo = blueHalo,
                                    onUpdateNow = { onDownloadUpdate(updateState.release) },
                                    onLater = {
                                        onPostponeUpdate()
                                        isDialogVisible = false
                                    }
                                )
                            }

                            // CUADRO 4B: DESCARGANDO (Imagen 4 centro)
                            updateState is AppUpdateUiState.Downloading -> {
                                DownloadingDialogContent(
                                    state = updateState,
                                    inkPrimary = inkPrimary,
                                    inkSecondary = inkSecondary,
                                    accentBlue = accentBlue,
                                    progressTrack = progressTrack,
                                    cancelRedColor = cancelRedColor,
                                    onCancelDownload = {
                                        onCancelDownload()
                                        isDialogVisible = false
                                    }
                                )
                            }

                            // CUADRO 4C: LISTA PARA INSTALAR (Imagen 4 abajo)
                            updateState is AppUpdateUiState.ReadyToInstall -> {
                                val apkFile = remember(updateState.apkFilePath) { File(updateState.apkFilePath) }
                                ReadyToInstallDialogContent(
                                    inspection = apkInspection,
                                    installErrorMsg = installErrorMsg,
                                    inkPrimary = inkPrimary,
                                    inkSecondary = inkSecondary,
                                    greenHalo = greenHalo,
                                    amberHalo = amberHalo,
                                    onInstall = {
                                        installErrorMsg = null
                                        if (AppUpdateChecker.canRequestPackageInstalls(context)) {
                                            AppUpdateChecker.launchApkInstaller(context, apkFile)
                                                .onFailure { installErrorMsg = it.localizedMessage }
                                        } else {
                                            showPermissionDialog = true
                                        }
                                    },
                                    onClose = { isDialogVisible = false }
                                )
                            }

                            // CUADRO 4E: ERROR (sin conexión o fallo de descarga)
                            updateState is AppUpdateUiState.Error -> {
                                val failedRelease = updateState.release
                                val briefReason = remember(updateState.message, failedRelease) {
                                    AppUpdateChecker.simplifyUpdateErrorMessage(
                                        rawMessage = updateState.message,
                                        isDownloadError = failedRelease != null
                                    )
                                }
                                ErrorDialogContent(
                                    reason = briefReason,
                                    inkPrimary = inkPrimary,
                                    inkSecondary = inkSecondary,
                                    amberHalo = amberHalo,
                                    onRetry = {
                                        if (failedRelease != null && failedRelease.downloadUrl.isNotBlank()) {
                                            onDownloadUpdate(failedRelease)
                                        } else {
                                            onRetryCheckForUpdate()
                                        }
                                    },
                                    onClose = { isDialogVisible = false }
                                )
                            }

                            // No configurado
                            updateState is AppUpdateUiState.NotConfigured -> {
                                ErrorDialogContent(
                                    reason = "No se pudo comprobar ahora",
                                    inkPrimary = inkPrimary,
                                    inkSecondary = inkSecondary,
                                    amberHalo = amberHalo,
                                    onRetry = onRetryCheckForUpdate,
                                    onClose = { isDialogVisible = false }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpToDateDialogContent(
    installedVersionName: String,
    lastCheckedLabel: String,
    inkPrimary: Color,
    inkSecondary: Color,
    greenHalo: Color,
    onAccept: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("updates_state_up_to_date"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(greenHalo),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(UpdatesGreenCircle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Estás al día",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Estás al día",
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 17.sp
            ),
            color = inkPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Tienes la última versión: $installedVersionName",
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = InterFontFamily,
                fontSize = 13.sp,
                lineHeight = 18.sp
            ),
            color = inkSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("updates_installed_version_text")
        )

        if (lastCheckedLabel.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = lastCheckedLabel,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = InterFontFamily,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                ),
                color = inkSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("updates_last_checked_text")
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        DialogPrimaryPillButton(
            text = "Aceptar",
            onClick = onAccept,
            testTag = "updates_accept_button"
        )
    }
}

@Composable
private fun UpdateAvailableDialogContent(
    release: AppReleaseInfo,
    isDarkTheme: Boolean,
    hasUnifiedSigning: Boolean?,
    inkPrimary: Color,
    inkSecondary: Color,
    blueHalo: Color,
    onUpdateNow: () -> Unit,
    onLater: () -> Unit
) {
    val releaseBullets = remember(release.releaseNotes) {
        AppUpdateChecker.parseReleaseNotesBullets(release.releaseNotes, maxItems = 5)
    }
    val bulletBlue = if (isDarkTheme) Color(0xFF4C8DFF) else UpdatesBrandBlue
    val bulletGreen = UpdatesGreenCircle
    val bulletAmber = UpdatesWarningAmber

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("updates_state_available"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(blueHalo),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(UpdatesBrandBlue),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowDownward,
                    contentDescription = "Hay una versión nueva",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Hay una versión nueva",
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 17.sp
            ),
            color = inkPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Versión ${release.versionName}",
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = InterFontFamily,
                fontSize = 12.sp
            ),
            color = inkSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("updates_new_version_text")
        )

        if (releaseBullets.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("updates_whats_included_card"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Qué incluye",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    ),
                    color = inkPrimary
                )
                releaseBullets.forEachIndexed { index, item ->
                    val dotColor = when (index % 3) {
                        0 -> bulletBlue
                        1 -> bulletGreen
                        else -> bulletAmber
                    }
                    ReleaseNoteBulletRow(
                        text = item,
                        dotColor = dotColor,
                        textColor = inkPrimary.copy(alpha = 0.88f)
                    )
                }
            }
        }

        if (hasUnifiedSigning == false) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Si Android muestra conflicto por una firma anterior, desinstala una vez e instala esta versión.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = InterFontFamily,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    ),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        DialogPrimaryPillButton(
            text = "Actualizar ahora",
            onClick = onUpdateNow,
            testTag = "updates_download_button"
        )

        Spacer(modifier = Modifier.height(4.dp))

        TextButton(
            onClick = onLater,
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
                color = inkSecondary
            )
        }
    }
}

@Composable
private fun DownloadingDialogContent(
    state: AppUpdateUiState.Downloading,
    inkPrimary: Color,
    inkSecondary: Color,
    accentBlue: Color,
    progressTrack: Color,
    cancelRedColor: Color,
    onCancelDownload: () -> Unit
) {
    val pct = state.progressPercent
    val hasDeterminateProgress = pct >= 0 && (state.downloadedBytes > 0L || pct > 0)
    val animatedProgress by animateFloatAsState(
        targetValue = (pct / 100f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = if (pct >= 100) 120 else 150),
        label = "updateDownloadProgress"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("updates_state_downloading")
    ) {
        Text(
            text = "Descargando versión ${state.release.versionName}",
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp
            ),
            color = inkPrimary
        )

        Spacer(modifier = Modifier.height(14.dp))

        if (hasDeterminateProgress) {
            LinearProgressIndicator(
                progress = { animatedProgress },
                color = accentBlue,
                trackColor = progressTrack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .testTag("updates_progress_bar")
            )
        } else {
            LinearProgressIndicator(
                color = accentBlue,
                trackColor = progressTrack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .testTag("updates_progress_bar")
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (pct >= 0) "$pct %" else "Preparando…",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = InterFontFamily,
                    fontSize = 13.sp
                ),
                color = inkSecondary,
                modifier = Modifier.testTag("updates_progress_percent")
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "No cierres la app",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = InterFontFamily,
                    fontSize = 13.sp
                ),
                color = inkSecondary,
                textAlign = TextAlign.End
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            TextButton(
                onClick = onCancelDownload,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .testTag("updates_cancel_download_button")
            ) {
                Text(
                    text = "Cancelar",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    ),
                    color = cancelRedColor
                )
            }
        }
    }
}

@Composable
private fun ReadyToInstallDialogContent(
    inspection: ApkInspectionResult?,
    installErrorMsg: String?,
    inkPrimary: Color,
    inkSecondary: Color,
    greenHalo: Color,
    amberHalo: Color,
    onInstall: () -> Unit,
    onClose: () -> Unit
) {
    val blockingMsg = inspection?.blockingError
    val sigWarning = inspection?.signatureWarning

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("updates_state_ready"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(if (blockingMsg != null) amberHalo else greenHalo),
            contentAlignment = Alignment.Center
        ) {
            if (blockingMsg != null) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = "Advertencia",
                    tint = UpdatesWarningAmber,
                    modifier = Modifier.size(34.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(UpdatesGreenCircle),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Lista para instalar",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = if (blockingMsg != null) "No se pudo completar" else "Lista para instalar",
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 17.sp
            ),
            color = inkPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = blockingMsg ?: "Android te pedirá confirmar la instalación.",
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = InterFontFamily,
                fontSize = 13.sp,
                lineHeight = 18.sp
            ),
            color = inkSecondary,
            textAlign = TextAlign.Center
        )

        if (!sigWarning.isNullOrBlank() && blockingMsg == null) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = sigWarning,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = InterFontFamily,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                ),
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }

        if (!installErrorMsg.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = installErrorMsg,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = InterFontFamily,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                ),
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        if (blockingMsg == null) {
            DialogPrimaryPillButton(
                text = "Instalar",
                onClick = onInstall,
                testTag = "updates_install_button"
            )
        } else {
            DialogPrimaryPillButton(
                text = "Cerrar",
                onClick = onClose,
                testTag = "updates_close_error_button"
            )
        }
    }
}

@Composable
private fun PermissionRequiredDialogContent(
    inkPrimary: Color,
    inkSecondary: Color,
    onOpenSettings: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("updates_permission_dialog"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Permiso necesario",
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 17.sp
            ),
            color = inkPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Activa el permiso para FamiChat y vuelve a esta pantalla.",
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = InterFontFamily,
                fontSize = 13.sp,
                lineHeight = 18.sp
            ),
            color = inkSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("updates_install_permission_hint")
        )

        Spacer(modifier = Modifier.height(18.dp))

        DialogPrimaryPillButton(
            text = "Abrir ajustes",
            onClick = onOpenSettings,
            testTag = "updates_open_permission_settings_button"
        )

        Spacer(modifier = Modifier.height(4.dp))

        TextButton(
            onClick = onCancel,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            modifier = Modifier
                .heightIn(min = 44.dp)
                .testTag("updates_permission_cancel_button")
        ) {
            Text(
                text = "Cancelar",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = InterFontFamily,
                    fontSize = 13.sp
                ),
                color = inkSecondary
            )
        }
    }
}

@Composable
private fun ErrorDialogContent(
    reason: String,
    inkPrimary: Color,
    inkSecondary: Color,
    amberHalo: Color,
    onRetry: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("updates_state_error"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(amberHalo),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = "Advertencia",
                tint = UpdatesWarningAmber,
                modifier = Modifier.size(34.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "No se pudo completar",
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 17.sp
            ),
            color = inkPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = reason,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = InterFontFamily,
                fontSize = 13.sp,
                lineHeight = 18.sp
            ),
            color = inkSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("updates_error_reason_text")
        )

        Spacer(modifier = Modifier.height(18.dp))

        DialogPrimaryPillButton(
            text = "Reintentar",
            onClick = onRetry,
            testTag = "updates_retry_button"
        )

        Spacer(modifier = Modifier.height(4.dp))

        TextButton(
            onClick = onClose,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            modifier = Modifier
                .heightIn(min = 44.dp)
                .testTag("updates_close_error_button")
        ) {
            Text(
                text = "Cerrar",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = InterFontFamily,
                    fontSize = 13.sp
                ),
                color = inkSecondary
            )
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
private fun DialogPrimaryPillButton(
    text: String,
    onClick: () -> Unit,
    testTag: String
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val backgroundColor by animateColorAsState(
        targetValue = if (isPressed || isHovered) UpdatesBrandBluePressed else UpdatesBrandBlue,
        animationSpec = tween(durationMillis = 120),
        label = "updatesDialogPillBg"
    )

    Button(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
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
            .heightIn(min = 44.dp)
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
