package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.BuildConfig
import com.example.data.model.FamilyMemberEntity
import com.example.data.model.MessageStatus
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.LocalDarkTheme
import com.example.ui.theme.LocalThemeController
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.ProfileAvatarColors
import com.example.ui.theme.ReadBlue
import com.example.util.ApkInspectionResult
import com.example.util.AppReleaseInfo
import com.example.util.AppUpdateChecker
import com.example.util.AppUpdateUiState
import com.example.util.CameraCaptureHelper
import com.example.util.ImageCompressionHelper
import com.example.util.parseHexColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

fun extractAvatarInitials(name: String?, fallbackEmoji: String): String {
    val cleanName = name?.trim().orEmpty()
    if (cleanName.isNotEmpty()) {
        val parts = cleanName.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.size >= 2) {
            val first = parts[0].firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()
            val second = parts[1].firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()
            if (first != null && second != null) return "$first$second"
        }
        val single = parts.firstOrNull()?.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()
        if (single != null) return single.toString()
    }
    val cleanFallback = fallbackEmoji.trim()
    if (cleanFallback.isNotEmpty() && cleanFallback.all { it.isLetterOrDigit() }) {
        return cleanFallback.take(2).uppercase()
    }
    return ""
}

@Composable
fun MemberAvatar(
    emoji: String,
    colorHex: String,
    size: Dp = 48.dp,
    isOnline: Boolean = false,
    showOnlineDot: Boolean = false,
    showOfflineDot: Boolean = false,
    statusDotColor: Color? = null,
    photoData: String? = null,
    nameForInitials: String? = null,
    isGroupAvatar: Boolean = false,
    useHomeGroupIcon: Boolean = false,
    cornerRadius: Dp? = null,
    modifier: Modifier = Modifier
) {
    val bgColor = parseHexColor(colorHex)
    val initials = remember(nameForInitials, emoji) {
        extractAvatarInitials(nameForInitials, emoji)
    }
    val treatAsGroup = isGroupAvatar || useHomeGroupIcon || emoji == "🏠" || emoji == "👨‍👩‍👧‍👦"
    val resolvedCorner = cornerRadius ?: (size.value * 0.28f).coerceIn(10f, 22f).dp
    val avatarShape = RoundedCornerShape(resolvedCorner)

    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(avatarShape)
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            when {
                !photoData.isNullOrBlank() -> {
                    AsyncImage(
                        model = ImageCompressionHelper.resolveCoilModel(photoData),
                        contentDescription = "Foto de perfil",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                useHomeGroupIcon || (treatAsGroup && emoji == "🏠") -> {
                    Icon(
                        imageVector = Icons.Filled.Home,
                        contentDescription = "Mi Familia",
                        tint = Color.White,
                        modifier = Modifier.size((size.value * 0.52f).dp)
                    )
                }
                treatAsGroup -> {
                    Icon(
                        imageVector = Icons.Filled.Groups,
                        contentDescription = "Grupo familiar",
                        tint = Color.White,
                        modifier = Modifier.size((size.value * 0.52f).dp)
                    )
                }
                initials.isNotEmpty() -> {
                    Text(
                        text = initials,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = (size.value * if (initials.length > 1) 0.36f else 0.42f).sp,
                        textAlign = TextAlign.Center
                    )
                }
                else -> {
                    Text(
                        text = emoji.ifBlank { "👤" },
                        color = Color.White,
                        fontSize = (size.value * 0.44f).sp
                    )
                }
            }
        }
        val effectiveDotColor = when {
            statusDotColor != null && (showOnlineDot || showOfflineDot) -> statusDotColor
            showOnlineDot && isOnline -> OnlineGreen
            showOfflineDot && !isOnline -> Color(0xFF9AA0A6)
            else -> null
        }
        if (effectiveDotColor != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size((size.value * 0.30f).coerceIn(11f, 15f).dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(effectiveDotColor)
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun UserProfileOptionsDialog(
    member: FamilyMemberEntity,
    onUpdateProfile: (
        nickname: String,
        avatarColorHex: String,
        isOnline: Boolean,
        statusText: String,
        bioText: String,
        newPhotoUri: Uri?,
        removePhoto: Boolean
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isDarkTheme = LocalDarkTheme.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val accentBlue = if (isDarkTheme) MaterialTheme.colorScheme.primary else Color(0xFF1A5FD4)
    val badgeBg = if (isDarkTheme) MaterialTheme.colorScheme.primaryContainer else Color(0xFFE8F0FE)
    val sheetSurface = MaterialTheme.colorScheme.surface
    val borderLightGray = if (isDarkTheme) MaterialTheme.colorScheme.outline else Color(0xFFDDE3EC)
    val labelGray = if (isDarkTheme) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF5A6577)
    val handleColor = if (isDarkTheme) MaterialTheme.colorScheme.outline else Color(0xFFD5DCE6)
    val scrimColor = if (isDarkTheme) {
        Color(0xFF0A0F1A).copy(alpha = 0.72f)
    } else {
        Color(0xFF475569).copy(alpha = 0.48f)
    }

    var nicknameInput by remember(member.id, member.name) {
        mutableStateOf(member.name.take(40))
    }
    var selectedColorHex by remember(member.id, member.avatarColorHex) {
        val initial = member.avatarColorHex.trim()
        val mapped = when {
            initial.isBlank() ||
                initial.equals("#1A73E8", ignoreCase = true) ||
                initial.equals("#2563EB", ignoreCase = true) ||
                initial.equals("#2F6FED", ignoreCase = true) -> ProfileAvatarColors.first()
            else -> initial
        }
        mutableStateOf(mapped)
    }
    var isOnlineInput by remember(member.id, member.isOnline) { mutableStateOf(member.isOnline) }
    var statusInput by remember(member.id, member.lastSeenText) { mutableStateOf(member.lastSeenText) }
    var bioInput by remember(member.id, member.bioText) { mutableStateOf(member.bioText.take(80)) }
    var pendingPhotoUri by remember(member.id) { mutableStateOf<Uri?>(null) }
    var pendingCameraPhotoUri by remember(member.id) { mutableStateOf<Uri?>(null) }
    var removeExistingPhoto by remember(member.id) { mutableStateOf(false) }

    val colorSwatches = ProfileAvatarColors

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                pendingPhotoUri = uri
                removeExistingPhoto = false
            }
        }
    )

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success && pendingCameraPhotoUri != null) {
                pendingPhotoUri = pendingCameraPhotoUri
                removeExistingPhoto = false
            }
        }
    )

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                val uri = CameraCaptureHelper.createTempPhotoUri(context)
                if (uri != null) {
                    pendingCameraPhotoUri = uri
                    takePictureLauncher.launch(uri)
                }
            }
        }
    )

    fun launchCameraForPhoto() {
        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            val uri = CameraCaptureHelper.createTempPhotoUri(context)
            if (uri != null) {
                pendingCameraPhotoUri = uri
                takePictureLauncher.launch(uri)
            }
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val previewPhotoData = when {
        pendingPhotoUri != null -> pendingPhotoUri.toString()
        removeExistingPhoto -> ""
        else -> member.avatarPhotoData
    }

    val liveDisplayName = nicknameInput.trim().ifBlank { member.name }
    val liveInitials = remember(liveDisplayName, member.avatarEmoji) {
        extractAvatarInitials(liveDisplayName, member.avatarEmoji)
    }
    val squircleColor = parseHexColor(selectedColorHex)
    val quickStatuses = listOf("En línea", "En casa", "Ocupado", "En el trabajo", "En la escuela")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetMaxWidth = 420.dp,
        containerColor = sheetSurface,
        scrimColor = scrimColor,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                width = 40.dp,
                height = 4.dp,
                color = handleColor
            )
        },
        modifier = Modifier.testTag("profile_options_dialog")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // 2. Encabezado fijo arriba: título + X
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Mi perfil",
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("profile_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Cerrar",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 3. Bloque de identidad: avatar squircle 60dp (rx 16dp) + Nombre + píldora rol
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(squircleColor),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            previewPhotoData.isNotBlank() -> {
                                AsyncImage(
                                    model = ImageCompressionHelper.resolveCoilModel(previewPhotoData),
                                    contentDescription = "Foto de perfil",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            liveInitials.isNotEmpty() -> {
                                Text(
                                    text = liveInitials,
                                    fontFamily = InterFontFamily,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = if (liveInitials.length > 1) 22.sp else 25.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                            else -> {
                                Text(
                                    text = member.avatarEmoji.ifBlank { "👤" },
                                    color = Color.White,
                                    fontSize = 26.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = liveDisplayName,
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            lineHeight = 21.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = badgeBg
                        ) {
                            Text(
                                text = if (member.isAdmin) "Administrador" else member.roleLabel,
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.5.sp,
                                color = accentBlue,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // 4. Dos botones de contorno compactos en fila: Tomar foto | Galería
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { launchCameraForPhoto() },
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.5.dp, accentBlue),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .testTag("profile_take_photo_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PhotoCamera,
                                contentDescription = null,
                                tint = accentBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Tomar foto",
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = accentBlue,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.5.dp, accentBlue),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .testTag("profile_change_photo_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PhotoLibrary,
                                contentDescription = null,
                                tint = accentBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Galería",
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = accentBlue,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    AnimatedVisibility(visible = previewPhotoData.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable {
                                        pendingPhotoUri = null
                                        removeExistingPhoto = true
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("profile_remove_photo_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Quitar foto",
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.5.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                // 5. Color del avatar (6 círculos distintos)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Color del avatar",
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp,
                        color = labelGray
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        colorSwatches.forEachIndexed { idx, hex ->
                            val isSelected = selectedColorHex.equals(hex, ignoreCase = true)
                            val swatchColor = parseHexColor(hex)
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .clickable { selectedColorHex = hex }
                                    .testTag("profile_color_swatch_$idx"),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(if (isSelected) 36.dp else 31.dp)
                                        .then(
                                            if (isSelected) {
                                                Modifier
                                                    .border(
                                                        width = 2.dp,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        shape = CircleShape
                                                    )
                                                    .padding(3.dp)
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .clip(CircleShape)
                                        .background(swatchColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = "Color seleccionado",
                                            tint = Color.White,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 6. Campo Apodo
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Apodo",
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp,
                        color = labelGray
                    )
                    ProfileRoundedInputField(
                        value = nicknameInput,
                        onValueChange = { if (it.length <= 40) nicknameInput = it },
                        placeholder = "Tu nombre o apodo",
                        singleLine = true,
                        isError = nicknameInput.isBlank(),
                        accentColor = accentBlue,
                        defaultBorderColor = borderLightGray,
                        placeholderColor = labelGray,
                        testTag = "profile_nickname_input"
                    )
                }

                // 7. Campo Bio con contador 0/80
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Bio",
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp,
                            color = labelGray
                        )
                        Text(
                            text = "${bioInput.length}/80",
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            color = labelGray
                        )
                    }
                    ProfileRoundedInputField(
                        value = bioInput,
                        onValueChange = { if (it.length <= 80) bioInput = it },
                        placeholder = "Frase personal (opcional)",
                        singleLine = false,
                        maxLines = 2,
                        accentColor = accentBlue,
                        defaultBorderColor = borderLightGray,
                        placeholderColor = labelGray,
                        testTag = "profile_bio_input"
                    )
                }

                // 8. Sección Estado: chips compactos en FlowRow
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Estado",
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp,
                        color = labelGray
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        quickStatuses.forEach { preset ->
                            val selected = statusInput == preset
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (selected) accentBlue else sheetSurface,
                                border = if (selected) null else BorderStroke(1.25.dp, borderLightGray),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable {
                                        statusInput = preset
                                        if (preset == "En línea") {
                                            isOnlineInput = true
                                        }
                                    }
                                    .testTag("profile_status_chip_$preset")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    if (selected) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Text(
                                        text = preset,
                                        fontFamily = InterFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.5.sp,
                                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // 9. Fila con interruptor: Mostrar mi última vez
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Mostrar mi última vez",
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isOnlineInput) "Visible para la familia" else "Apareces como desconectado",
                            fontFamily = InterFontFamily,
                            fontSize = 12.sp,
                            color = labelGray
                        )
                    }
                    Switch(
                        checked = isOnlineInput,
                        onCheckedChange = { checked ->
                            isOnlineInput = checked
                            if (checked && (statusInput == "Desconectado" || statusInput == "No se ha conectado")) {
                                statusInput = "En línea"
                            } else if (!checked && statusInput == "En línea") {
                                statusInput = "Desconectado"
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = accentBlue
                        ),
                        modifier = Modifier.testTag("profile_online_switch")
                    )
                }
            }

            // 10. Botón principal fijo al fondo, compacto y estilizado
            Surface(
                color = sheetSurface,
                tonalElevation = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = {
                        onUpdateProfile(
                            nicknameInput.trim(),
                            selectedColorHex,
                            isOnlineInput,
                            statusInput,
                            bioInput,
                            pendingPhotoUri,
                            removeExistingPhoto
                        )
                    },
                    enabled = nicknameInput.trim().isNotEmpty(),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentBlue,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 6.dp, bottom = 14.dp)
                        .height(44.dp)
                        .testTag("profile_save_status_button")
                ) {
                    Text(
                        text = "Guardar cambios",
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileRoundedInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean,
    maxLines: Int = if (singleLine) 1 else 2,
    isError: Boolean = false,
    accentColor: Color,
    defaultBorderColor: Color,
    placeholderColor: Color,
    testTag: String
) {
    var isFocused by remember { mutableStateOf(false) }
    val borderColor = when {
        isError -> MaterialTheme.colorScheme.error
        isFocused -> accentColor
        else -> defaultBorderColor
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.5.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    fontFamily = InterFontFamily,
                    fontSize = 14.sp,
                    color = placeholderColor
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                maxLines = maxLines,
                textStyle = TextStyle(
                    fontFamily = InterFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(accentColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isFocused = it.isFocused }
                    .testTag(testTag)
            )
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
    var showRepoEditor by rememberSaveable { mutableStateOf(false) }
    var canInstallPackages by remember { mutableStateOf(AppUpdateChecker.canRequestPackageInstalls(context)) }
    var waitingPermissionReturn by rememberSaveable { mutableStateOf(false) }
    var installErrorMsg by remember { mutableStateOf<String?>(null) }
    var apkInspection by remember { mutableStateOf<ApkInspectionResult?>(null) }
    val latestUpdateState by rememberUpdatedState(updateState)

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
                        if (currentRepoSlug.isNotBlank()) {
                            Text(
                                text = "Origen: $currentRepoSlug",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
                        TextButton(
                            onClick = { AppUpdateChecker.openDownloadInBrowser(context, rel) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("update_available_browser_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Descargar en navegador")
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
                            val statusInfo = updateState.statusLabel.ifBlank {
                                "Descarga gestionada por el sistema Android."
                            }
                            Text(
                                text = "$sizeLabel · $statusInfo",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = { AppUpdateChecker.openDownloadInBrowser(context, rel) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("update_browser_download_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Descargar en navegador")
                            }
                            TextButton(
                                onClick = onDismiss,
                                modifier = Modifier.align(Alignment.End).testTag("update_cancel_download_button")
                            ) {
                                Text("Cancelar / Pausar")
                            }
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

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (blockingMsg != null) {
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = if (blockingMsg != null) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                                    contentDescription = if (blockingMsg != null) "Aviso de paquete" else "Descarga completa",
                                    tint = if (blockingMsg != null) MaterialTheme.colorScheme.error else OnlineGreen
                                )
                                Column {
                                    Text(
                                        text = if (blockingMsg != null) "Paquete incompatible" else "Descarga completa (100%)",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = when {
                                            blockingMsg != null -> blockingMsg
                                            canInstallPackages ->
                                                "Versión v${updateState.release.versionName} lista para instalar."
                                            else ->
                                                "Activa el permiso \"Instalar aplicaciones desconocidas\" para FamiChat y volverás directo al instalador."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (!sigWarning.isNullOrBlank() && blockingMsg == null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Warning,
                                        contentDescription = "Conflicto de firma",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = sigWarning,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        if (!installErrorMsg.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = installErrorMsg.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("update_install_button")
                            ) {
                                Icon(Icons.Filled.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    when {
                                        !canInstallPackages -> "Permitir e instalar"
                                        inspection?.hasSignatureConflict == true -> "Intentar instalar ahora"
                                        else -> "Instalar ahora"
                                    }
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onCheckAgain,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Volver a verificar")
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
                        val failedRelease = updateState.release
                        Button(
                            onClick = {
                                if (failedRelease != null && failedRelease.downloadUrl.isNotBlank()) {
                                    onDownloadUpdate(failedRelease)
                                } else {
                                    onCheckAgain()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("update_retry_button")
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reintentar descarga")
                        }
                        if (failedRelease != null) {
                            OutlinedButton(
                                onClick = { AppUpdateChecker.openDownloadInBrowser(context, failedRelease) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("update_error_browser_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Descargar en navegador")
                            }
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
