package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.BuildConfig
import com.example.data.model.FamilyMemberEntity
import com.example.data.model.MessageStatus
import com.example.ui.theme.LocalDarkTheme
import com.example.ui.theme.LocalThemeController
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.ReadBlue
import com.example.util.AppReleaseInfo
import com.example.util.AppUpdateChecker
import com.example.util.AppUpdateUiState
import com.example.util.parseHexColor
import java.io.File

@Composable
fun MemberAvatar(
    emoji: String,
    colorHex: String,
    size: Dp = 48.dp,
    isOnline: Boolean = false,
    showOnlineDot: Boolean = false,
    modifier: Modifier = Modifier
) {
    val bgColor = parseHexColor(colorHex)
    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(CircleShape)
                .background(bgColor.copy(alpha = 0.18f))
                .border(1.5.dp, bgColor.copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = emoji.ifBlank { "👤" },
                fontSize = (size.value * 0.48f).sp
            )
        }
        if (showOnlineDot && isOnline) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size((size.value * 0.28f).coerceAtLeast(11f).dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(OnlineGreen)
            )
        }
    }
}

@Composable
fun MessageStatusIcon(
    status: MessageStatus,
    tintDefault: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier: Modifier = Modifier
) {
    val (icon, tint, desc) = when (status) {
        MessageStatus.SENDING -> Triple(Icons.Filled.Schedule, tintDefault, "Enviando")
        MessageStatus.SENT -> Triple(Icons.Filled.Check, tintDefault, "Enviado")
        MessageStatus.DELIVERED -> Triple(Icons.Filled.DoneAll, tintDefault, "Entregado")
        MessageStatus.READ -> Triple(Icons.Filled.DoneAll, ReadBlue, "Leído")
    }
    Icon(
        imageVector = icon,
        contentDescription = desc,
        tint = tint,
        modifier = modifier.size(15.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UserProfileOptionsDialog(
    member: FamilyMemberEntity,
    userGroupsCount: Int,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onUpdateStatus: (isOnline: Boolean, statusText: String) -> Unit,
    onOpenAdminPanel: () -> Unit,
    onRequestSwitchProfile: () -> Unit,
    onDismiss: () -> Unit
) {
    val effectiveDarkTheme = LocalDarkTheme.current
    var isOnlineInput by remember(member.id, member.isOnline) { mutableStateOf(member.isOnline) }
    var statusInput by remember(member.id, member.lastSeenText) { mutableStateOf(member.lastSeenText) }
    val quickStatuses = listOf("En línea", "En casa", "Ocupado", "En el trabajo", "En la escuela")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .testTag("profile_options_dialog")
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MemberAvatar(
                        emoji = member.avatarEmoji,
                        colorHex = member.avatarColorHex,
                        size = 56.dp,
                        isOnline = isOnlineInput,
                        showOnlineDot = true
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = member.name,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            if (member.isAdmin) {
                                Icon(
                                    imageVector = Icons.Filled.Shield,
                                    contentDescription = "Administrador",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Text(
                            text = if (member.isAdmin) "${member.roleLabel} · Administrador" else member.roleLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("profile_close_button")
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                    }
                }

                // Características del usuario
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Características del perfil",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Rol familiar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(member.roleLabel, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Filled.Groups, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Grupos familiares", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("$userGroupsCount activos", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Acceso", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = if (member.isAdmin) "Protegido con contraseña" else "Acceso directo",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                            )
                        }
                    }
                }

                // Estado y disponibilidad
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Mostrarme en línea", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = if (isOnlineInput) "Visible para la familia" else "Apareces como desconectado",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isOnlineInput,
                            onCheckedChange = { checked ->
                                isOnlineInput = checked
                                if (checked && statusInput == "Desconectado") {
                                    statusInput = "En línea"
                                } else if (!checked && statusInput == "En línea") {
                                    statusInput = "Desconectado"
                                }
                            },
                            modifier = Modifier.testTag("profile_online_switch")
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        quickStatuses.forEach { preset ->
                            FilterChip(
                                selected = statusInput == preset,
                                onClick = {
                                    statusInput = preset
                                    isOnlineInput = preset == "En línea" || preset == "En casa"
                                },
                                label = { Text(preset, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = statusInput,
                        onValueChange = { statusInput = it },
                        label = { Text("Estado personalizado") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_status_input")
                    )

                    Button(
                        onClick = { onUpdateStatus(isOnlineInput, statusInput) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_save_status_button")
                    ) {
                        Text("Guardar estado")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                // Apariencia
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onToggleTheme() }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(
                            imageVector = if (effectiveDarkTheme) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                            contentDescription = "Tema de la aplicación",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text("Tema oscuro", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = if (effectiveDarkTheme) "Activo (oscuro)" else "Desactivado (blanco)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = effectiveDarkTheme,
                        onCheckedChange = { onToggleTheme() },
                        modifier = Modifier.testTag("profile_theme_switch")
                    )
                }

                // Switch de perfil con clave del administrador
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            onDismiss()
                            onRequestSwitchProfile()
                        }
                        .testTag("profile_switch_row")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SwapHoriz,
                                contentDescription = "Switch de perfil",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text("Switch de perfil", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = if (member.isAdmin) {
                                        "Salir al selector de perfiles"
                                    } else {
                                        "Salir con clave del administrador"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = false,
                            onCheckedChange = {
                                onDismiss()
                                onRequestSwitchProfile()
                            },
                            modifier = Modifier.testTag("profile_switch_control")
                        )
                    }
                }

                if (member.isAdmin) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onOpenAdminPanel()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_admin_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AdminPanelSettings,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Abrir Panel de Administración")
                    }
                }
            }
        }
    }
}

@Composable
fun PinVerificationDialog(
    title: String = "Contraseña de Papá",
    subtitle: String = "Ingresa el PIN de 4 dígitos para entrar",
    pinLength: Int = 4,
    onVerify: (String) -> Boolean,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    var entered by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.widthIn(max = 360.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "Seguridad de perfil",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))

                // PIN Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(pinLength) { idx ->
                        val filled = idx < entered.length
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isError -> MaterialTheme.colorScheme.error
                                        filled -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                if (isError) {
                    Text(
                        text = "Clave incorrecta. Intenta de nuevo.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Spacer(modifier = Modifier.height(16.dp))
                }
                Spacer(modifier = Modifier.height(12.dp))

                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("", "0", "DEL")
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    rows.forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            row.forEach { key ->
                                when (key) {
                                    "" -> Spacer(modifier = Modifier.size(58.dp))
                                    "DEL" -> Box(
                                        modifier = Modifier
                                            .size(58.dp)
                                            .clip(CircleShape)
                                            .minimumInteractiveComponentSize()
                                            .clickable {
                                                if (entered.isNotEmpty()) {
                                                    entered = entered.dropLast(1)
                                                    isError = false
                                                }
                                            }
                                            .testTag("pin_backspace"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                                            contentDescription = "Borrar dígito",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    else -> Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier
                                            .size(58.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                if (entered.length < pinLength) {
                                                    isError = false
                                                    val next = entered + key
                                                    entered = next
                                                    if (next.length == pinLength) {
                                                        if (onVerify(next)) {
                                                            onSuccess()
                                                        } else {
                                                            isError = true
                                                            entered = ""
                                                        }
                                                    }
                                                }
                                            }
                                            .testTag("pin_key_$key")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = key,
                                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("pin_cancel_button")
                    ) {
                        Text("Cancelar")
                    }
                }
            }
        }
    }
}

@Composable
fun AppUpdateDialog(
    isAdmin: Boolean,
    currentRepoSlug: String,
    updateState: AppUpdateUiState,
    onCheckAgain: () -> Unit,
    onSaveRepoSlug: (String) -> Unit,
    onDownloadUpdate: (AppReleaseInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val hasUnifiedSigning = remember(context) { AppUpdateChecker.isConsistentDebugSigning(context) }
    var repoInput by remember(currentRepoSlug) { mutableStateOf(currentRepoSlug) }
    var showRepoEditor by remember { mutableStateOf(false) }
    var canInstallPackages by remember { mutableStateOf(AppUpdateChecker.canRequestPackageInstalls(context)) }
    var waitingPermissionReturn by remember { mutableStateOf(false) }
    var installErrorMsg by remember { mutableStateOf<String?>(null) }
    val latestUpdateState by rememberUpdatedState(updateState)

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val allowed = AppUpdateChecker.canRequestPackageInstalls(context)
                canInstallPackages = allowed
                val currentState = latestUpdateState
                if (allowed && waitingPermissionReturn && currentState is AppUpdateUiState.ReadyToInstall) {
                    waitingPermissionReturn = false
                    AppUpdateChecker.launchApkInstaller(context, File(currentState.apkFilePath))
                        .onFailure { installErrorMsg = it.localizedMessage }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 410.dp)
                .testTag("app_update_dialog")
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SystemUpdate,
                            contentDescription = "Actualizar aplicación",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Actualizar aplicación",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Versión instalada: v${BuildConfig.VERSION_NAME} (comp. ${BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("update_close_button")
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))

                when (updateState) {
                    AppUpdateUiState.Idle,
                    AppUpdateUiState.Checking -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = "Buscando actualizaciones disponibles...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    is AppUpdateUiState.NotConfigured -> {
                        Text(
                            text = if (isAdmin) {
                                "Configura el repositorio de actualizaciones para descargar e instalar nuevas versiones directamente aquí."
                            } else {
                                "El administrador (Papá) aún no ha vinculado el origen de actualizaciones en Panel de Administración → Seguridad."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isAdmin) {
                            OutlinedTextField(
                                value = repoInput,
                                onValueChange = { repoInput = it },
                                label = { Text("Repositorio (usuario/repositorio)") },
                                placeholder = { Text("ej. usuario/Mensajer-a-Familiar-") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("update_repo_input")
                            )
                            Button(
                                onClick = { onSaveRepoSlug(repoInput) },
                                enabled = repoInput.isNotBlank(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("update_save_repo_button")
                            ) {
                                Text("Guardar y buscar actualización")
                            }
                        }
                    }

                    is AppUpdateUiState.UpToDate -> {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = "Actualizada",
                                    tint = OnlineGreen
                                )
                                Column {
                                    Text(
                                        text = "Tienes la última versión",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = "Ya tienes la versión más reciente instalada (v${updateState.installedVersionName}).",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Button(
                            onClick = onCheckAgain,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("update_recheck_button")
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Buscar de nuevo")
                        }
                        if (isAdmin) {
                            TextButton(
                                onClick = { showRepoEditor = !showRepoEditor },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (showRepoEditor) "Ocultar repositorio" else "Cambiar repositorio")
                            }
                            if (showRepoEditor) {
                                OutlinedTextField(
                                    value = repoInput,
                                    onValueChange = { repoInput = it },
                                    label = { Text("Repositorio (usuario/repositorio)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("update_repo_input")
                                )
                                OutlinedButton(
                                    onClick = {
                                        showRepoEditor = false
                                        onSaveRepoSlug(repoInput)
                                    },
                                    enabled = repoInput.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth().testTag("update_save_repo_button")
                                ) {
                                    Text("Guardar y buscar")
                                }
                            }
                        }
                    }

                    is AppUpdateUiState.UpdateAvailable -> {
                        val rel = updateState.release
                        val remoteLabel = if (rel.versionCode != null) {
                            "v${rel.versionName} (comp. ${rel.versionCode})"
                        } else {
                            "v${rel.versionName}"
                        }
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "¡Nueva versión disponible: $remoteLabel!",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = rel.releaseNotes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        Button(
                            onClick = { onDownloadUpdate(rel) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("update_download_button")
                        ) {
                            Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Descargar e instalar")
                        }
                        if (hasUnifiedSigning == false) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Aviso: la versión instalada actualmente usa una firma anterior distinta. Si aparece conflicto de paquete, desinstala una sola vez antes de instalar esta versión; las siguientes ya actualizarán encima automáticamente.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }

                    is AppUpdateUiState.Downloading -> {
                        val rel = updateState.release
                        val pct = updateState.progressPercent
                        val downloadedMb = updateState.downloadedBytes / (1024f * 1024f)
                        val totalMb = updateState.totalBytes / (1024f * 1024f)

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Descargando v${rel.versionName}...",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = if (pct >= 0) "$pct%" else String.format("%.1f MB", downloadedMb),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.testTag("update_progress_percent")
                                )
                            }
                            if (pct >= 0) {
                                LinearProgressIndicator(
                                    progress = { (pct / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .testTag("update_progress_bar")
                                )
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .testTag("update_progress_bar")
                                )
                            }
                            val sizeLabel = if (updateState.totalBytes > 0L) {
                                String.format("%.1f MB de %.1f MB", downloadedMb, totalMb)
                            } else {
                                String.format("%.1f MB descargados", downloadedMb)
                            }
                            Text(
                                text = "$sizeLabel · No salgas de esta ventana; el instalador se abrirá automáticamente.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    is AppUpdateUiState.ReadyToInstall -> {
                        val apkFile = remember(updateState.apkFilePath) { File(updateState.apkFilePath) }

                        LaunchedEffect(updateState.apkFilePath) {
                            installErrorMsg = null
                            canInstallPackages = AppUpdateChecker.canRequestPackageInstalls(context)
                            if (canInstallPackages) {
                                AppUpdateChecker.launchApkInstaller(context, apkFile)
                                    .onFailure { installErrorMsg = it.localizedMessage }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = "Descarga completa",
                                    tint = OnlineGreen
                                )
                                Column {
                                    Text(
                                        text = "Descarga completa (100%)",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = if (canInstallPackages) {
                                            "Versión v${updateState.release.versionName} lista para instalar."
                                        } else {
                                            "Activa el permiso \"Instalar aplicaciones desconocidas\" para NidoFamiliar y volverás directo al instalador."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (!installErrorMsg.isNullOrBlank()) {
                            Text(
                                text = installErrorMsg.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

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
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("update_install_button")
                        ) {
                            Icon(Icons.Filled.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (canInstallPackages) "Instalar ahora" else "Permitir e instalar")
                        }
                    }

                    is AppUpdateUiState.Error -> {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = "Aviso",
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = updateState.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        Button(
                            onClick = onCheckAgain,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("update_retry_button")
                        ) {
                            Text("Reintentar")
                        }
                        if (isAdmin) {
                            TextButton(
                                onClick = { showRepoEditor = !showRepoEditor },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (showRepoEditor) "Ocultar repositorio" else "Cambiar repositorio")
                            }
                            if (showRepoEditor) {
                                OutlinedTextField(
                                    value = repoInput,
                                    onValueChange = { repoInput = it },
                                    label = { Text("Repositorio (usuario/repositorio)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("update_repo_input")
                                )
                                OutlinedButton(
                                    onClick = {
                                        showRepoEditor = false
                                        onSaveRepoSlug(repoInput)
                                    },
                                    enabled = repoInput.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth().testTag("update_save_repo_button")
                                ) {
                                    Text("Guardar y buscar")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
