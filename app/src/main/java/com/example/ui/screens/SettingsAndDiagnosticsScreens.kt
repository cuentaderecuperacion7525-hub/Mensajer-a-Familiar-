package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Notifications
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
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
import com.example.data.local.PreferencesManager
import com.example.ui.components.AppUpdateDialog
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
    onOpenAdvanced: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var systemNotifGranted by remember {
        mutableStateOf(FamiChatNotificationHelper.canPostNotifications(context))
    }
    var showUpdateDialog by rememberSaveable { mutableStateOf(false) }

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
    val updateSubtitle = when (updateState) {
        is AppUpdateUiState.UpdateAvailable -> "Nueva versión v${updateState.release.versionName}"
        is AppUpdateUiState.Downloading -> "Descargando ${updateState.progressPercent.coerceAtLeast(0)}%"
        is AppUpdateUiState.ReadyToInstall -> "Lista para instalar"
        else -> "Estás al día"
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Ajustes", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_button")) {
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
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "PREFERENCIAS",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SettingsActionRow(
                            icon = Icons.Outlined.Notifications,
                            title = "Notificaciones",
                            subtitle = if (notificationsActive) "Activadas" else "Desactivadas",
                            subtitleColor = if (notificationsActive) OnlineGreen else MaterialTheme.colorScheme.error,
                            testTag = "settings_notifications_row",
                            onClick = onOpenNotifications
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleTheme() }
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                                .testTag("settings_theme_row"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DarkMode,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Tema oscuro",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isDarkTheme) "Activado" else "Desactivado",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isDarkTheme,
                                onCheckedChange = { onToggleTheme() }
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                        SettingsActionRow(
                            icon = Icons.Outlined.FileDownload,
                            title = "Buscar actualización",
                            subtitle = updateSubtitle,
                            testTag = "settings_update_row",
                            onClick = {
                                showUpdateDialog = true
                                onCheckForUpdate()
                            }
                        )
                    }
                }

                if (isAdmin) {
                    Text(
                        text = "ADMINISTRADOR",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    ) {
                        SettingsActionRow(
                            icon = Icons.Outlined.AdminPanelSettings,
                            title = "Avanzado",
                            subtitle = "Diagnóstico de alertas y llamadas",
                            testTag = "settings_advanced_row",
                            onClick = onOpenAdvanced
                        )
                    }
                }
            }
        }
    }

    if (showUpdateDialog) {
        AppUpdateDialog(
            isAdmin = isAdmin,
            currentRepoSlug = updateRepoSlug,
            updateState = updateState,
            onCheckAgain = onCheckForUpdate,
            onSaveRepoSlug = onSaveUpdateRepoSlug,
            onDownloadUpdate = onDownloadUpdate,
            onDismiss = {
                showUpdateDialog = false
                onResetUpdateState()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    notificationsEnabled: Boolean,
    snackbarMessage: String?,
    onClearSnackbar: () -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
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

@OptIn(ExperimentalMaterial3Api::class)
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
