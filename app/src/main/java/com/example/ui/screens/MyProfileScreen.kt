package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import com.example.data.model.FamilyMemberEntity
import com.example.ui.components.extractAvatarInitials
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.LocalDarkTheme
import com.example.ui.theme.ProfileAvatarColors
import com.example.util.CameraCaptureHelper
import com.example.util.ImageCompressionHelper
import com.example.util.parseHexColor

private val ProfileWhiteBg = Color(0xFFFFFFFF)
private val ProfileCardBg = Color(0xFFF5F7FB)
private val ProfileTextPrimary = Color(0xFF1B1F2A)
private val ProfileTextSecondary = Color(0xFF5B6270)
private val ProfileAccentBlue = Color(0xFF1A5FD4)
private val ProfileAdminPillBg = Color(0xFFE4EDFC)
private val ProfileDividerGray = Color(0xFFE3E6EC)
private val ProfileBorderGray = Color(0xFFC9CED8)
private val ProfileDangerRed = Color(0xFFB3261E)

private val ProfileQuickStatuses = listOf(
    "En línea",
    "En casa",
    "Ocupado",
    "En el trabajo",
    "En la escuela"
)

/**
 * Full-screen flat white "Mi perfil" page matching the approved clean design.
 * Preserves all existing profile fields and saves only when tapping "Guardar cambios".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MyProfileScreen(
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
    val view = LocalView.current
    val isDarkTheme = LocalDarkTheme.current

    // Keep status bar icons legible against the pure white full-screen background
    if (!view.isInEditMode) {
        DisposableEffect(view, isDarkTheme) {
            val window = (view.context as? Activity)?.window
            val controller = window?.let { WindowCompat.getInsetsController(it, view) }
            controller?.isAppearanceLightStatusBars = true
            controller?.isAppearanceLightNavigationBars = true
            onDispose {
                controller?.isAppearanceLightStatusBars = !isDarkTheme
                controller?.isAppearanceLightNavigationBars = !isDarkTheme
            }
        }
    }

    val initialNickname = remember(member.id, member.name) { member.name.take(40) }
    val initialColorHex = remember(member.id, member.avatarColorHex) {
        val raw = member.avatarColorHex.trim()
        when {
            raw.isBlank() ||
                raw.equals("#1A73E8", ignoreCase = true) ||
                raw.equals("#2563EB", ignoreCase = true) ||
                raw.equals("#2F6FED", ignoreCase = true) -> ProfileAvatarColors.first()
            else -> raw
        }
    }
    val initialShowLastSeen = remember(member.id, member.showLastSeen) { member.showLastSeen }
    val initialStatus = remember(member.id, member.lastSeenText) {
        val raw = member.lastSeenText.trim()
        ProfileQuickStatuses.firstOrNull { preset ->
            raw.equals(preset, ignoreCase = true) || raw.startsWith("$preset ·", ignoreCase = true)
        }.orEmpty()
    }
    val initialBio = remember(member.id, member.bioText) { member.bioText.take(80) }

    var nicknameInput by remember(member.id, member.name) { mutableStateOf(initialNickname) }
    var selectedColorHex by remember(member.id, member.avatarColorHex) { mutableStateOf(initialColorHex) }
    var showLastSeenInput by remember(member.id, member.showLastSeen) { mutableStateOf(initialShowLastSeen) }
    var statusInput by remember(member.id, member.lastSeenText) { mutableStateOf(initialStatus) }
    var bioInput by remember(member.id, member.bioText) { mutableStateOf(initialBio) }
    var pendingPhotoUri by remember(member.id) { mutableStateOf<Uri?>(null) }
    var pendingCameraPhotoUri by remember(member.id) { mutableStateOf<Uri?>(null) }
    var removeExistingPhoto by remember(member.id) { mutableStateOf(false) }

    var showPhotoMenu by remember { mutableStateOf(false) }
    var showRemovePhotoConfirm by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }

    val nicknameFocusRequester = remember { FocusRequester() }
    val bioFocusRequester = remember { FocusRequester() }

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

    val hasUnsavedChanges = nicknameInput.trim() != initialNickname.trim() ||
        !selectedColorHex.equals(initialColorHex, ignoreCase = true) ||
        showLastSeenInput != initialShowLastSeen ||
        statusInput != initialStatus ||
        bioInput.trim() != initialBio.trim() ||
        pendingPhotoUri != null ||
        (removeExistingPhoto && member.avatarPhotoData.isNotBlank())

    fun attemptClose() {
        if (hasUnsavedChanges) {
            showDiscardConfirm = true
        } else {
            onDismiss()
        }
    }

    BackHandler(enabled = true) {
        when {
            showPhotoMenu -> showPhotoMenu = false
            showRemovePhotoConfirm -> showRemovePhotoConfirm = false
            showDiscardConfirm -> showDiscardConfirm = false
            else -> attemptClose()
        }
    }

    val liveDisplayName = nicknameInput.trim().ifBlank { member.name }
    val liveInitials = remember(liveDisplayName, member.avatarEmoji) {
        extractAvatarInitials(liveDisplayName, member.avatarEmoji)
    }
    val squircleColor = parseHexColor(selectedColorHex)

    Surface(
        color = ProfileWhiteBg,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxSize()
            .testTag("profile_options_dialog")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ProfileWhiteBg)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Encabezado fijo arriba: "Mi perfil" + X
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Mi perfil",
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 20.sp,
                    color = ProfileTextPrimary
                )
                IconButton(
                    onClick = { attemptClose() },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("profile_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Cerrar",
                        tint = ProfileTextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Contenido desplazable
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp)
            ) {
                // IDENTIDAD
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(82.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClickLabel = "Editar foto de perfil"
                            ) { showPhotoMenu = true }
                            .testTag("profile_avatar_photo_menu_trigger")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .align(Alignment.TopStart)
                                .clip(RoundedCornerShape(24.dp))
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
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 26.sp,
                                        color = Color.White,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                else -> {
                                    Text(
                                        text = member.avatarEmoji.ifBlank { "👤" },
                                        color = Color.White,
                                        fontSize = 28.sp
                                    )
                                }
                            }
                        }

                        // Insignia circular oscura (#1B1F2A, 26dp, borde blanco de 3dp) con cámara blanca
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(ProfileWhiteBg)
                                .padding(3.dp)
                                .clip(CircleShape)
                                .background(ProfileTextPrimary)
                                .testTag("profile_camera_badge"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PhotoCamera,
                                contentDescription = "Cambiar foto",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showPhotoMenu,
                            onDismissRequest = { showPhotoMenu = false },
                            containerColor = ProfileWhiteBg,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.testTag("profile_photo_menu")
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Tomar foto",
                                        fontFamily = InterFontFamily,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp,
                                        color = ProfileTextPrimary
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.PhotoCamera,
                                        contentDescription = null,
                                        tint = ProfileTextPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showPhotoMenu = false
                                    launchCameraForPhoto()
                                },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("profile_take_photo_button")
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Elegir de galería",
                                        fontFamily = InterFontFamily,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp,
                                        color = ProfileTextPrimary
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.PhotoLibrary,
                                        contentDescription = null,
                                        tint = ProfileTextPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showPhotoMenu = false
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("profile_change_photo_button")
                            )
                            if (previewPhotoData.isNotBlank()) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Quitar foto",
                                            fontFamily = InterFontFamily,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 14.sp,
                                            color = ProfileDangerRed
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.DeleteOutline,
                                            contentDescription = null,
                                            tint = ProfileDangerRed,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    onClick = {
                                        showPhotoMenu = false
                                        showRemovePhotoConfirm = true
                                    },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("profile_remove_photo_button")
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = liveDisplayName,
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp,
                            lineHeight = 20.sp,
                            color = ProfileTextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (member.isAdmin) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = ProfileAdminPillBg,
                                tonalElevation = 0.dp,
                                shadowElevation = 0.dp,
                                modifier = Modifier.testTag("profile_admin_badge")
                            ) {
                                Text(
                                    text = "Administrador",
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp,
                                    color = ProfileAccentBlue,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Text(
                            text = "Toca la cámara para editar",
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Normal,
                            fontSize = 11.sp,
                            color = ProfileTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // COLOR
                Text(
                    text = "Color de tu perfil",
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = ProfileTextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProfileAvatarColors.forEachIndexed { idx, hex ->
                        val isSelected = selectedColorHex.equals(hex, ignoreCase = true)
                        val swatchColor = parseHexColor(hex)
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .semantics {
                                    selected = isSelected
                                    contentDescription = if (isSelected) "Color seleccionado" else "Seleccionar color"
                                }
                                .clickable(role = Role.RadioButton) { selectedColorHex = hex }
                                .testTag("profile_color_swatch_$idx"),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = if (isSelected) {
                                    Modifier
                                        .size(41.dp)
                                        .border(
                                            width = 2.5.dp,
                                            color = ProfileTextPrimary,
                                            shape = CircleShape
                                        )
                                        .padding(4.5.dp)
                                        .clip(CircleShape)
                                        .background(swatchColor)
                                } else {
                                    Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(swatchColor)
                                },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Se usa en tus iniciales y en los detalles de tu perfil.",
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 11.sp,
                    color = ProfileTextSecondary
                )

                Spacer(modifier = Modifier.height(16.dp))

                // TARJETA DE DATOS (#F5F7FB, radio 16dp, padding 16dp)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ProfileCardBg,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Fila Apodo
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                                .pointerInput(Unit) {
                                    detectTapGestures { nicknameFocusRequester.requestFocus() }
                                },
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Apodo · así te ve tu familia",
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.Normal,
                                fontSize = 11.sp,
                                color = ProfileTextSecondary
                            )
                            Box(modifier = Modifier.fillMaxWidth()) {
                                if (nicknameInput.isEmpty()) {
                                    Text(
                                        text = "Tu nombre o apodo",
                                        fontFamily = InterFontFamily,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp,
                                        color = ProfileTextSecondary
                                    )
                                }
                                BasicTextField(
                                    value = nicknameInput,
                                    onValueChange = { if (it.length <= 40) nicknameInput = it },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        fontFamily = InterFontFamily,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp,
                                        color = ProfileTextPrimary
                                    ),
                                    cursorBrush = SolidColor(ProfileAccentBlue),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(nicknameFocusRequester)
                                        .testTag("profile_nickname_input")
                                )
                            }
                        }

                        HorizontalDivider(
                            thickness = 1.dp,
                            color = ProfileDividerGray,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )

                        // Fila Frase personal + contador 0/80
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                                .pointerInput(Unit) {
                                    detectTapGestures { bioFocusRequester.requestFocus() }
                                },
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Frase personal",
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 11.sp,
                                    color = ProfileTextSecondary
                                )
                                Text(
                                    text = "${bioInput.length}/80",
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 11.sp,
                                    color = ProfileTextSecondary,
                                    modifier = Modifier.testTag("profile_bio_counter")
                                )
                            }
                            Box(modifier = Modifier.fillMaxWidth()) {
                                if (bioInput.isEmpty()) {
                                    Text(
                                        text = "Escribe algo que te represente",
                                        fontFamily = InterFontFamily,
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 13.sp,
                                        color = ProfileTextSecondary
                                    )
                                }
                                BasicTextField(
                                    value = bioInput,
                                    onValueChange = { if (it.length <= 80) bioInput = it },
                                    singleLine = false,
                                    maxLines = 3,
                                    textStyle = TextStyle(
                                        fontFamily = InterFontFamily,
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 13.sp,
                                        color = ProfileTextPrimary
                                    ),
                                    cursorBrush = SolidColor(ProfileAccentBlue),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(bioFocusRequester)
                                        .testTag("profile_bio_input")
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // ESTADO
                Text(
                    text = "Estado",
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = ProfileTextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    ProfileQuickStatuses.forEach { preset ->
                        val isSelected = statusInput.equals(preset, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .semantics { selected = isSelected }
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    role = Role.RadioButton
                                ) {
                                    statusInput = preset
                                    if (preset == "En línea") {
                                        showLastSeenInput = true
                                    }
                                }
                                .testTag("profile_status_chip_$preset"),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (isSelected) ProfileAccentBlue else ProfileWhiteBg,
                                border = if (isSelected) null else BorderStroke(1.5.dp, ProfileBorderGray),
                                tonalElevation = 0.dp,
                                shadowElevation = 0.dp,
                                modifier = Modifier.height(34.dp)
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = preset,
                                        fontFamily = InterFontFamily,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.sp,
                                        color = if (isSelected) Color.White else ProfileTextPrimary,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // PRIVACIDAD (#F5F7FB, radio 16dp)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ProfileCardBg,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "Mostrar mi última vez",
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                color = ProfileTextPrimary
                            )
                            Text(
                                text = if (showLastSeenInput) {
                                    "La familia verá cuándo te conectaste."
                                } else {
                                    "Nadie verá cuándo te conectaste."
                                },
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.Normal,
                                fontSize = 11.sp,
                                color = ProfileTextSecondary,
                                modifier = Modifier.testTag("profile_last_seen_subtitle")
                            )
                        }
                        CompactFlatSwitch(
                            checked = showLastSeenInput,
                            onCheckedChange = { checked ->
                                showLastSeenInput = checked
                                if (!checked && statusInput == "En línea") {
                                    statusInput = ""
                                }
                            },
                            modifier = Modifier.testTag("profile_online_switch")
                        )
                    }
                }
            }

            // BOTÓN GUARDAR fijo abajo (alto 48dp, píldora, #1A5FD4 activo / #C9CED8 deshabilitado)
            Surface(
                color = ProfileWhiteBg,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                val canSave = hasUnsavedChanges && nicknameInput.trim().isNotEmpty()
                Button(
                    onClick = {
                        val effectiveStatus = when {
                            statusInput.isNotBlank() -> statusInput
                            !showLastSeenInput && member.lastSeenText.equals("En línea", ignoreCase = true) -> "Desconectado"
                            else -> member.lastSeenText
                        }
                        onUpdateProfile(
                            nicknameInput.trim(),
                            selectedColorHex,
                            showLastSeenInput,
                            effectiveStatus,
                            bioInput,
                            pendingPhotoUri,
                            removeExistingPhoto
                        )
                    },
                    enabled = canSave,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ProfileAccentBlue,
                        contentColor = Color.White,
                        disabledContainerColor = ProfileBorderGray,
                        disabledContentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        disabledElevation = 0.dp
                    ),
                    contentPadding = PaddingValues(vertical = 10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 8.dp, bottom = 16.dp)
                        .height(48.dp)
                        .testTag("profile_save_status_button")
                ) {
                    Text(
                        text = "Guardar cambios",
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }

    if (showRemovePhotoConfirm) {
        AlertDialog(
            onDismissRequest = { showRemovePhotoConfirm = false },
            containerColor = ProfileWhiteBg,
            title = {
                Text(
                    text = "¿Quitar foto de perfil?",
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Medium,
                    color = ProfileTextPrimary
                )
            },
            text = {
                Text(
                    text = "Tu avatar volverá a mostrar tus iniciales. El cambio se aplicará cuando toques Guardar cambios.",
                    fontFamily = InterFontFamily,
                    color = ProfileTextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingPhotoUri = null
                        removeExistingPhoto = true
                        showRemovePhotoConfirm = false
                    },
                    modifier = Modifier.testTag("profile_remove_photo_confirm_button")
                ) {
                    Text(
                        text = "Quitar foto",
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Medium,
                        color = ProfileDangerRed
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRemovePhotoConfirm = false },
                    modifier = Modifier.testTag("profile_remove_photo_cancel_button")
                ) {
                    Text(
                        text = "Cancelar",
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Medium,
                        color = ProfileTextPrimary
                    )
                }
            }
        )
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            containerColor = ProfileWhiteBg,
            title = {
                Text(
                    text = "¿Salir sin guardar?",
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Medium,
                    color = ProfileTextPrimary
                )
            },
            text = {
                Text(
                    text = "Tienes cambios sin guardar en tu perfil. Si cierras ahora, se perderán.",
                    fontFamily = InterFontFamily,
                    color = ProfileTextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirm = false
                        onDismiss()
                    },
                    modifier = Modifier.testTag("profile_discard_confirm_button")
                ) {
                    Text(
                        text = "Salir",
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Medium,
                        color = ProfileDangerRed
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDiscardConfirm = false },
                    modifier = Modifier.testTag("profile_discard_cancel_button")
                ) {
                    Text(
                        text = "Seguir editando",
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Medium,
                        color = ProfileAccentBlue
                    )
                }
            }
        )
    }
}

@Composable
private fun CompactFlatSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) ProfileAccentBlue else ProfileBorderGray,
        animationSpec = tween(durationMillis = 150),
        label = "switchTrackColor"
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 16.dp else 2.dp,
        animationSpec = tween(durationMillis = 150),
        label = "switchThumbOffset"
    )
    Box(
        modifier = modifier
            .size(width = 48.dp, height = 44.dp)
            .toggleable(
                value = checked,
                role = Role.Switch,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onValueChange = onCheckedChange
            ),
        contentAlignment = Alignment.CenterEnd
    ) {
        Box(
            modifier = Modifier
                .size(width = 34.dp, height = 20.dp)
                .clip(RoundedCornerShape(50))
                .background(trackColor),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}
