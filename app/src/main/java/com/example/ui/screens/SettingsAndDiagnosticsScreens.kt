package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.BuildConfig
import com.example.data.local.AppFontScale
import com.example.data.local.AppThemeMode
import com.example.data.local.PreferencesManager
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.OnlineGreen
import com.example.util.AppReleaseInfo
import com.example.util.AppUpdateUiState
import com.example.util.FamiChatNotificationHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isAdmin: Boolean,
    isDarkTheme: Boolean,
    notificationsEnabled: Boolean,
    updateRepoSlug: String,
    updateState: AppUpdateUiState,
    onBack: () -> Unit,
    onOpenNotifications: () -> Unit,
    onToggleTheme: () -> Unit,
    onCheckForUpdate: () -> Unit,
    onSaveUpdateRepoSlug: (String) -> Unit,
    onDownloadUpdate: (AppReleaseInfo) -> Unit,
    onResetUpdateState: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onOpenUpdates: () -> Unit = {},
    themeMode: AppThemeMode = if (isDarkTheme) AppThemeMode.DARK else AppThemeMode.SYSTEM,
    fontScale: AppFontScale = AppFontScale.NORMAL,
    onOpenAppearance: () -> Unit = onToggleTheme,
    onOpenFontSize: () -> Unit = {},
    onOpenWhatsNew: () -> Unit = {},
    onOpenAdminPanel: () -> Unit = onOpenAdvanced,
    onOpenDiagnostics: () -> Unit = onOpenAdvanced,
    onOpenLicenses: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var systemNotifGranted by remember {
        mutableStateOf(FamiChatNotificationHelper.canPostNotifications(context))
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                systemNotifGranted = FamiChatNotificationHelper.canPostNotifications(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationsActive = systemNotifGranted && notificationsEnabled
    val hasNewUpdate = updateState is AppUpdateUiState.UpdateAvailable ||
        updateState is AppUpdateUiState.ReadyToInstall

    val surfaceBg = if (isDarkTheme) MaterialTheme.colorScheme.background else Color.White
    val sectionBlue = if (isDarkTheme) MaterialTheme.colorScheme.primary else Color(0xFF1A5FD4)
    val inkPrimary = if (isDarkTheme) MaterialTheme.colorScheme.onSurface else Color(0xFF1B1F2A)
    val inkSecondary = if (isDarkTheme) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF5B6270)
    val iconTint = if (isDarkTheme) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF5B6270)
    val chevronTint = if (isDarkTheme) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f) else Color(0xFF8E95A2)
    val dividerColor = if (isDarkTheme) MaterialTheme.colorScheme.outline.copy(alpha = 0.18f) else Color(0xFFECEFF4)
    val activeGreen = Color(0xFF1E8E4E)
    val inactiveAmber = Color(0xFFA45F08)
    val updateDotRed = Color(0xFFD93025)
    val updateTextRed = Color(0xFFB3261E)

    Scaffold(
        containerColor = surfaceBg,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Ajustes",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 20.sp
                        ),
                        color = inkPrimary
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back_button")
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
                    containerColor = surfaceBg,
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
                    .widthIn(max = 650.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Section: PREFERENCIAS
                SettingsSectionHeader(
                    text = "PREFERENCIAS",
                    color = sectionBlue,
                    topPadding = 10.dp
                )
                SettingsFlatRow(
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    title = "Notificaciones",
                    titleColor = inkPrimary,
                    trailingContent = {
                        Text(
                            text = if (notificationsActive) "Activadas" else "Desactivadas",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = InterFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = if (notificationsActive) activeGreen else inactiveAmber,
                            maxLines = 1
                        )
                    },
                    showChevron = true,
                    chevronTint = chevronTint,
                    testTag = "settings_notifications_row",
                    onClick = onOpenNotifications
                )
                SettingsInsetDivider(color = dividerColor)
                SettingsFlatRow(
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.DarkMode,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    title = "Apariencia",
                    titleColor = inkPrimary,
                    trailingContent = {
                        Text(
                            text = themeMode.label,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = InterFontFamily,
                                fontSize = 12.sp
                            ),
                            color = inkSecondary,
                            maxLines = 1,
                            modifier = Modifier.testTag("settings_appearance_value")
                        )
                    },
                    showChevron = true,
                    chevronTint = chevronTint,
                    testTag = "settings_appearance_row",
                    onClick = onOpenAppearance
                )
                SettingsInsetDivider(color = dividerColor)
                SettingsFlatRow(
                    leadingIcon = {
                        Text(
                            text = "Aa",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = InterFontFamily,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = iconTint,
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    title = "Tamaño de letra",
                    titleColor = inkPrimary,
                    trailingContent = {
                        Text(
                            text = fontScale.label,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = InterFontFamily,
                                fontSize = 12.sp
                            ),
                            color = inkSecondary,
                            maxLines = 1,
                            modifier = Modifier.testTag("settings_font_size_value")
                        )
                    },
                    showChevron = true,
                    chevronTint = chevronTint,
                    testTag = "settings_font_size_row",
                    onClick = onOpenFontSize
                )

                // Section: APLICACIÓN
                SettingsSectionHeader(
                    text = "APLICACIÓN",
                    color = sectionBlue
                )
                SettingsFlatRow(
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.FileDownload,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    title = "Actualizaciones",
                    titleColor = inkPrimary,
                    trailingContent = {
                        when {
                            hasNewUpdate -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(updateDotRed)
                                            .testTag("settings_update_row_badge_dot")
                                    )
                                    Text(
                                        text = "Nueva",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = InterFontFamily,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = updateTextRed,
                                        maxLines = 1
                                    )
                                }
                            }
                            updateState is AppUpdateUiState.Downloading -> {
                                Text(
                                    text = "${updateState.progressPercent.coerceAtLeast(0)}%",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = InterFontFamily,
                                        fontSize = 12.sp
                                    ),
                                    color = inkSecondary,
                                    maxLines = 1
                                )
                            }
                            else -> {
                                Text(
                                    text = "Al día",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = InterFontFamily,
                                        fontSize = 12.sp
                                    ),
                                    color = inkSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    },
                    showChevron = true,
                    chevronTint = chevronTint,
                    testTag = "settings_update_row",
                    onClick = onOpenUpdates
                )
                SettingsInsetDivider(color = dividerColor)
                SettingsFlatRow(
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Description,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    title = "Novedades de esta versión",
                    titleColor = inkPrimary,
                    showChevron = true,
                    chevronTint = chevronTint,
                    testTag = "settings_whats_new_row",
                    onClick = onOpenWhatsNew
                )

                // Section: ADMINISTRADOR (admin only)
                if (isAdmin) {
                    SettingsSectionHeader(
                        text = "ADMINISTRADOR",
                        color = sectionBlue
                    )
                    SettingsFlatRow(
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Shield,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        title = "Administración",
                        titleColor = inkPrimary,
                        showChevron = true,
                        chevronTint = chevronTint,
                        testTag = "settings_admin_row",
                        onClick = onOpenAdminPanel
                    )
                    SettingsInsetDivider(color = dividerColor)
                    SettingsFlatRow(
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.MonitorHeart,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        title = "Diagnóstico",
                        titleColor = inkPrimary,
                        showChevron = true,
                        chevronTint = chevronTint,
                        testTag = "settings_diagnostics_row",
                        onClick = onOpenDiagnostics
                    )
                }

                // Section: ACERCA DE
                SettingsSectionHeader(
                    text = "ACERCA DE",
                    color = sectionBlue
                )
                SettingsFlatRow(
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    title = "Versión",
                    titleColor = inkPrimary,
                    trailingContent = {
                        Text(
                            text = BuildConfig.VERSION_NAME,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = InterFontFamily,
                                fontSize = 12.sp
                            ),
                            color = inkSecondary,
                            maxLines = 1,
                            modifier = Modifier.testTag("settings_version_value")
                        )
                    },
                    showChevron = false,
                    chevronTint = chevronTint,
                    testTag = "settings_version_row",
                    onClick = null
                )
                SettingsInsetDivider(color = dividerColor)
                SettingsFlatRow(
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Description,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    title = "Licencias",
                    titleColor = inkPrimary,
                    showChevron = true,
                    chevronTint = chevronTint,
                    testTag = "settings_licenses_row",
                    onClick = onOpenLicenses
                )

                // Generous bottom breathing room so the last row never sticks to the bottom edge
                Spacer(modifier = Modifier.height(44.dp))
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(
    text: String,
    color: Color,
    topPadding: androidx.compose.ui.unit.Dp = 22.dp
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = InterFontFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp
        ),
        color = color,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = topPadding, bottom = 6.dp)
    )
}

@Composable
private fun SettingsInsetDivider(color: Color) {
    HorizontalDivider(
        modifier = Modifier.padding(start = 60.dp, end = 20.dp),
        thickness = 0.75.dp,
        color = color
    )
}

@Composable
private fun SettingsFlatRow(
    leadingIcon: @Composable () -> Unit,
    title: String,
    titleColor: Color,
    trailingContent: (@Composable () -> Unit)? = null,
    showChevron: Boolean,
    chevronTint: Color,
    testTag: String,
    onClick: (() -> Unit)?
) {
    val rowModifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 48.dp)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 20.dp, vertical = 12.dp)
        .testTag(testTag)

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .widthIn(min = 24.dp)
                .heightIn(min = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            leadingIcon()
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = InterFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal
            ),
            color = titleColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (trailingContent != null) {
            Spacer(modifier = Modifier.width(10.dp))
            trailingContent()
        }
        if (showChevron) {
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = chevronTint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun FourPointSparkleOutlineIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val outer = minOf(w, h) * 0.42f
        val ctrl = minOf(w, h) * 0.08f
        val path = Path().apply {
            moveTo(cx, cy - outer)
            quadraticTo(cx + ctrl, cy - ctrl, cx + outer, cy)
            quadraticTo(cx + ctrl, cy + ctrl, cx, cy + outer)
            quadraticTo(cx - ctrl, cy + ctrl, cx - outer, cy)
            quadraticTo(cx - ctrl, cy - ctrl, cx, cy - outer)
            close()
        }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(
                width = 1.7.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    notificationsEnabled: Boolean,
    realtimeBackgroundEnabled: Boolean = false,
    snackbarMessage: String?,
    onClearSnackbar: () -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onToggleRealtimeBackground: (Boolean) -> Unit = {},
    onSendTestNotification: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    var systemNotifGranted by remember {
        mutableStateOf(FamiChatNotificationHelper.canPostNotifications(context))
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                systemNotifGranted = FamiChatNotificationHelper.canPostNotifications(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(snackbarMessage) {
        if (snackbarMessage != null) {
            snackbarHostState.showSnackbar(snackbarMessage)
            onClearSnackbar()
        }
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            systemNotifGranted = granted || FamiChatNotificationHelper.canPostNotifications(context)
            if (systemNotifGranted) {
                onToggleNotifications(true)
            } else {
                val activity = context.findActivity()
                val canShowRationale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && activity != null) {
                    ActivityCompat.shouldShowRequestPermissionRationale(
                        activity,
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                } else {
                    false
                }
                if (!canShowRationale) {
                    openSystemNotificationSettings(context)
                }
            }
        }
    )

    val activateNotifications = {
        if (!systemNotifGranted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                openSystemNotificationSettings(context)
            }
        } else if (!notificationsEnabled) {
            onToggleNotifications(true)
        }
    }

    val areNotificationsActive = systemNotifGranted && notificationsEnabled

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Notificaciones", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("notifications_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
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
                    .widthIn(max = 650.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (areNotificationsActive) {
                            OnlineGreen.copy(alpha = 0.12f)
                        } else {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("notifications_status_card")
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = if (areNotificationsActive) {
                                    Icons.Filled.CheckCircle
                                } else {
                                    Icons.Filled.NotificationsOff
                                },
                                contentDescription = if (areNotificationsActive) {
                                    "Notificaciones activadas"
                                } else {
                                    "Notificaciones desactivadas"
                                },
                                tint = if (areNotificationsActive) OnlineGreen else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(30.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (areNotificationsActive) {
                                        "Notificaciones activadas"
                                    } else {
                                        "Notificaciones desactivadas"
                                    },
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (areNotificationsActive) {
                                        "Recibirás alertas cuando te envíen mensajes o llamadas familiares."
                                    } else {
                                        "Activa los avisos para enterarte al instante cuando tu familia te escriba o te llame."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (!areNotificationsActive) {
                            Button(
                                onClick = activateNotifications,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("notifications_activate_button")
                            ) {
                                Text("Activar")
                            }
                        }
                    }
                }

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (!notificationsEnabled) {
                                    activateNotifications()
                                } else {
                                    onToggleNotifications(false)
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Avisos en este dispositivo",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (notificationsEnabled) "Recibiendo avisos" else "Silenciados en la app",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = notificationsEnabled,
                            onCheckedChange = { wantEnable ->
                                if (wantEnable) {
                                    activateNotifications()
                                } else {
                                    onToggleNotifications(false)
                                }
                            },
                            modifier = Modifier.testTag("notifications_enable_switch")
                        )
                    }
                }

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = notificationsEnabled) {
                                onToggleRealtimeBackground(!realtimeBackgroundEnabled)
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.BatteryChargingFull,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Conexión permanente (sin Google)",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (realtimeBackgroundEnabled) {
                                    "Mantiene el aviso \"FamiChat activo\" para teléfonos sin servicios de Google."
                                } else {
                                    "Desactivada para ahorrar batería. Las alertas llegan al instante desde el servidor."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = realtimeBackgroundEnabled && notificationsEnabled,
                            enabled = notificationsEnabled,
                            onCheckedChange = onToggleRealtimeBackground,
                            modifier = Modifier.testTag("notifications_realtime_bg_switch")
                        )
                    }
                }

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Comprobar alertas",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Envía una notificación de prueba a este teléfono para verificar que los avisos suenan y se muestran correctamente.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onSendTestNotification,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("notifications_test_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.NotificationsActive,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Probar notificación")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAdvancedSettingsScreen(
    isAdmin: Boolean,
    onBack: () -> Unit,
    onOpenDiagnostics: () -> Unit
) {
    if (!isAdmin) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Avanzado", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("advanced_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
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
                    .widthIn(max = 650.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    )
                ) {
                    SettingsActionRow(
                        icon = Icons.Outlined.MonitorHeart,
                        title = "Diagnóstico",
                        subtitle = "Estado de entregas, FCM y servidores TURN 4G/5G",
                        testTag = "advanced_diagnostics_row",
                        onClick = onOpenDiagnostics
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdminDiagnosticsScreen(
    isAdmin: Boolean,
    familyId: String,
    memberName: String,
    notificationsEnabled: Boolean,
    fcmToken: String,
    turnUrl: String,
    turnUsername: String,
    turnCredential: String,
    snackbarMessage: String?,
    onClearSnackbar: () -> Unit,
    onSendTestNotification: () -> Unit,
    onSaveTurnConfig: (url: String, username: String, credential: String) -> Unit,
    onBack: () -> Unit
) {
    if (!isAdmin) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val isImeVisible = WindowInsets.isImeVisible
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler(enabled = isImeVisible) {
        keyboardController?.hide()
        focusManager.clearFocus()
    }

    var systemNotifGranted by remember {
        mutableStateOf(FamiChatNotificationHelper.canPostNotifications(context))
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                systemNotifGranted = FamiChatNotificationHelper.canPostNotifications(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(snackbarMessage) {
        if (snackbarMessage != null) {
            snackbarHostState.showSnackbar(snackbarMessage)
            onClearSnackbar()
        }
    }

    val sanitizedTopic = remember(familyId) {
        val clean = familyId.replace(Regex("[^a-zA-Z0-9-_.~%]"), "_").take(48)
        "famichat_${clean.ifBlank { "default" }}"
    }

    var showTechnicalDetails by rememberSaveable { mutableStateOf(false) }
    var editTurnUrl by remember(turnUrl) { mutableStateOf(turnUrl) }
    var editTurnUser by remember(turnUsername) { mutableStateOf(turnUsername) }
    var editTurnCred by remember(turnCredential) { mutableStateOf(turnCredential) }
    var copiedBanner by remember { mutableStateOf<String?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Diagnóstico", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Estado de entregas ✓✓ y servidores TURN 4G/5G",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("diagnostics_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("push_call_diagnostics_dialog"),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 650.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DiagnosticStackedRow(
                            label = "Permiso de notificaciones del sistema",
                            ok = systemNotifGranted && notificationsEnabled,
                            detail = if (systemNotifGranted && notificationsEnabled) "Activo" else "Desactivado"
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                        DiagnosticStackedRow(
                            label = "Doble palomita gris (✓✓) en 2º plano",
                            ok = true,
                            detail = "Automática (FCM + WorkManager)"
                        )
                    }
                }

                Button(
                    onClick = onSendTestNotification,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("test_push_notification_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.NotificationsActive,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Probar alerta")
                }

                OutlinedButton(
                    onClick = { showTechnicalDetails = !showTechnicalDetails },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("toggle_technical_details_button")
                ) {
                    Icon(
                        imageVector = if (showTechnicalDetails) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (showTechnicalDetails) "Ocultar detalles técnicos" else "Ver detalles técnicos"
                    )
                }

                AnimatedVisibility(visible = showTechnicalDetails) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("diagnostics_technical_details_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            DiagnosticStackedRow(
                                label = "Canal Push FCM familiar",
                                ok = familyId.isNotBlank(),
                                detail = sanitizedTopic
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                            DiagnosticStackedRow(
                                label = "Token FCM del dispositivo ($memberName)",
                                ok = fcmToken.isNotBlank(),
                                detail = if (fcmToken.isNotBlank()) fcmToken else "Pendiente de Google Play Services",
                                detailMaxLines = 3
                            )
                            if (fcmToken.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(fcmToken))
                                        copiedBanner = "Token FCM copiado al portapapeles"
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("copy_fcm_token_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Copiar token FCM")
                                }
                            }
                            if (copiedBanner != null) {
                                Text(
                                    text = copiedBanner!!,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OnlineGreen
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Router,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Servidores TURN de respaldo (Datos móviles 4G/5G)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "Permite que las llamadas de voz y video crucen redes móviles con NAT estricto.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedTextField(
                    value = editTurnUrl,
                    onValueChange = { editTurnUrl = it },
                    label = { Text("Servidores TURN (separados por coma)") },
                    singleLine = false,
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("turn_url_input")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = editTurnUser,
                        onValueChange = { editTurnUser = it },
                        label = { Text("Usuario TURN") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("turn_user_input")
                    )
                    OutlinedTextField(
                        value = editTurnCred,
                        onValueChange = { editTurnCred = it },
                        label = { Text("Clave TURN") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("turn_cred_input")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            editTurnUrl = PreferencesManager.DEFAULT_TURN_URL
                            editTurnUser = PreferencesManager.DEFAULT_TURN_USERNAME
                            editTurnCred = PreferencesManager.DEFAULT_TURN_CREDENTIAL
                            onSaveTurnConfig(editTurnUrl, editTurnUser, editTurnCred)
                        }
                    ) {
                        Text("Restaurar OpenRelay")
                    }
                    Button(
                        onClick = { onSaveTurnConfig(editTurnUrl, editTurnUser, editTurnCred) },
                        modifier = Modifier.testTag("save_turn_config_button")
                    ) {
                        Text("Guardar TURN")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    showBadgeDot: Boolean = false,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = subtitleColor
            )
        }
        if (showBadgeDot) {
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .testTag("${testTag}_badge_dot")
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DiagnosticStackedRow(
    label: String,
    ok: Boolean,
    detail: String,
    detailMaxLines: Int = 2
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (ok) Icons.Filled.CheckCircle else Icons.Filled.Error,
                contentDescription = null,
                tint = if (ok) OnlineGreen else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            maxLines = detailMaxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 24.dp)
        )
    }
}

private fun openSystemNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
    } else {
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
