package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.FamilyMemberEntity
import com.example.ui.components.MemberAvatar
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.LocalDarkTheme
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.ProfileAvatarColors

private enum class SelectorEntryMode { EXISTING, REGISTER }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileSelectorScreen(
    members: List<FamilyMemberEntity>,
    familyName: String,
    currentInviteCode: String,
    isMembersLoading: Boolean,
    isRegisterBusy: Boolean,
    snackbarMessage: String?,
    onClearSnackbar: () -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onSelectMember: (FamilyMemberEntity) -> Unit,
    onRegisterMember: (name: String, roleLabel: String, colorHex: String, inviteCode: String) -> Unit,
    onChangeFamily: () -> Unit
) {
    val accentBlue = Color(0xFF1A5FD4)
    val labelGray = if (isDarkTheme) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF5B6270)
    val cardBorderColor = if (isDarkTheme) MaterialTheme.colorScheme.outline else Color(0xFFD8DEE8)
    val cardSurface = if (isDarkTheme) MaterialTheme.colorScheme.surface else Color(0xFFFFFFFF)
    val containerSurface = if (isDarkTheme) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFF0F2F8)
    val placeholderGray = if (isDarkTheme) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f) else Color(0xFF9AA3B2)

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            onClearSnackbar()
        }
    }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    var entryMode by rememberSaveable {
        mutableStateOf(
            if (members.isEmpty() && !isMembersLoading) SelectorEntryMode.REGISTER
            else SelectorEntryMode.EXISTING
        )
    }
    var userSwitchedTab by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(isMembersLoading, members.isEmpty()) {
        if (!userSwitchedTab) {
            entryMode = if (!isMembersLoading && members.isEmpty()) {
                SelectorEntryMode.REGISTER
            } else {
                SelectorEntryMode.EXISTING
            }
        }
    }

    var nicknameInput by rememberSaveable { mutableStateOf("") }
    var selectedColorHex by rememberSaveable { mutableStateOf(ProfileAvatarColors.first()) }
    var selectedRolePreset by rememberSaveable { mutableStateOf("Familiar") }
    var customRoleInput by rememberSaveable { mutableStateOf("") }
    var showInviteCodeField by rememberSaveable { mutableStateOf(false) }
    var customInviteCode by rememberSaveable { mutableStateOf("") }

    val rolePresets = remember { listOf("Mamá", "Papá", "Hijo/a", "Abuelo/a", "Familiar", "Otro") }
    val resolvedRoleLabel = remember(selectedRolePreset, customRoleInput) {
        if (selectedRolePreset == "Otro") {
            customRoleInput.trim().ifEmpty { "Familiar" }
        } else {
            selectedRolePreset
        }
    }
    val matchingExistingMember = remember(members, nicknameInput) {
        val clean = nicknameInput.trim()
        if (clean.length < 2) null
        else members.firstOrNull { it.name.trim().equals(clean, ignoreCase = true) }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 580.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_famichat_logo),
                        contentDescription = "Logo FamiChat",
                        modifier = Modifier.size(34.dp)
                    )
                    Text(
                        text = "FamiChat",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .minimumInteractiveComponentSize()
                        .clickable { onToggleTheme() }
                        .testTag("selector_theme_toggle")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = "Cambiar tema",
                            tint = accentBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (isDarkTheme) "Modo blanco" else "Modo oscuro",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            Text(
                text = if (entryMode == SelectorEntryMode.REGISTER) {
                    "Crea tu perfil familiar"
                } else {
                    "¿Quién está ingresando ahora?"
                },
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            val famDisplay = familyName.ifBlank { "Mi Familia" }
            Text(
                text = if (entryMode == SelectorEntryMode.REGISTER) {
                    "Únete a $famDisplay desde este teléfono en segundos"
                } else {
                    "$famDisplay · Toca tu perfil o registra uno nuevo"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = labelGray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 2-Mode Segmented Pill Switcher
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = containerSurface,
                border = BorderStroke(1.dp, cardBorderColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 500.dp)
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SelectorModeTabButton(
                        label = if (members.isNotEmpty()) "Ya tengo perfil (${members.size})" else "Ya tengo perfil",
                        icon = Icons.Filled.Group,
                        selected = entryMode == SelectorEntryMode.EXISTING,
                        accentBlue = accentBlue,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("selector_mode_existing"),
                        onClick = {
                            userSwitchedTab = true
                            entryMode = SelectorEntryMode.EXISTING
                        }
                    )
                    SelectorModeTabButton(
                        label = "Registrar mi perfil",
                        icon = Icons.Filled.PersonAdd,
                        selected = entryMode == SelectorEntryMode.REGISTER,
                        accentBlue = accentBlue,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("selector_mode_register"),
                        onClick = {
                            userSwitchedTab = true
                            entryMode = SelectorEntryMode.REGISTER
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            if (entryMode == SelectorEntryMode.REGISTER) {
                // Path A: Self-Registration Card
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = cardSurface,
                    border = BorderStroke(1.dp, cardBorderColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 500.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Live squircle preview
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            MemberAvatar(
                                emoji = "🧑🏽",
                                colorHex = selectedColorHex,
                                size = 64.dp,
                                isOnline = true,
                                showOnlineDot = true,
                                nameForInitials = nicknameInput.trim().ifEmpty { "Tú" },
                                cornerRadius = 18.dp
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = nicknameInput.trim().ifEmpty { "Tu perfil en FamiChat" },
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = accentBlue.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = resolvedRoleLabel,
                                        fontFamily = InterFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        color = accentBlue,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        // Color swatches
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Color del avatar",
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = labelGray
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ProfileAvatarColors.forEachIndexed { index, hex ->
                                    val swatchColor = Color(android.graphics.Color.parseColor(hex))
                                    val selected = selectedColorHex.equals(hex, ignoreCase = true)
                                    val ringColor = if (isDarkTheme) Color.White else Color(0xFF1B1F2A)
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .then(
                                                if (selected) Modifier.border(2.5.dp, ringColor, CircleShape)
                                                else Modifier
                                            )
                                            .padding(if (selected) 3.dp else 0.dp)
                                            .clip(CircleShape)
                                            .background(swatchColor)
                                            .clickable { selectedColorHex = hex }
                                            .testTag("register_color_swatch_$index"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (selected) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = "Color seleccionado",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Nickname input
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Nombre o apodo",
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = labelGray
                                )
                                Text(
                                    text = "${nicknameInput.length}/40",
                                    fontFamily = InterFontFamily,
                                    fontSize = 12.sp,
                                    color = labelGray
                                )
                            }
                            SelectorRoundedInputField(
                                value = nicknameInput,
                                onValueChange = { if (it.length <= 40) nicknameInput = it },
                                placeholder = "Ej. Sofía, Tío Carlos, Mamá",
                                singleLine = true,
                                isError = false,
                                accentColor = accentBlue,
                                defaultBorderColor = cardBorderColor,
                                placeholderColor = placeholderGray,
                                capitalization = KeyboardCapitalization.Words,
                                testTag = "register_name_input"
                            )
                        }

                        // Soft duplicate warning if exact name already exists
                        if (matchingExistingMember != null) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = accentBlue.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, accentBlue.copy(alpha = 0.25f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Info,
                                        contentDescription = null,
                                        tint = accentBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Ya existe \"${matchingExistingMember.name}\" en esta familia.",
                                            fontFamily = InterFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.5.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Si reinstalaste la app, entra en \"Ya tengo perfil\".",
                                            fontFamily = InterFontFamily,
                                            fontSize = 11.5.sp,
                                            color = labelGray
                                        )
                                    }
                                    TextButton(
                                        onClick = {
                                            userSwitchedTab = true
                                            entryMode = SelectorEntryMode.EXISTING
                                        }
                                    ) {
                                        Text(
                                            text = "Ver",
                                            fontFamily = InterFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = accentBlue
                                        )
                                    }
                                }
                            }
                        }

                        // Role presets
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Parentesco o rol",
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = labelGray
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rolePresets.forEach { preset ->
                                    val selected = selectedRolePreset == preset
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = if (selected) accentBlue else cardSurface,
                                        border = if (selected) null else BorderStroke(1.25.dp, cardBorderColor),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .clickable { selectedRolePreset = preset }
                                            .testTag("register_role_chip_$preset")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
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
                                                fontSize = 13.sp,
                                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                            if (selectedRolePreset == "Otro") {
                                SelectorRoundedInputField(
                                    value = customRoleInput,
                                    onValueChange = { if (it.length <= 30) customRoleInput = it },
                                    placeholder = "Escribe tu parentesco (ej. Tía, Primo)",
                                    singleLine = true,
                                    isError = false,
                                    accentColor = accentBlue,
                                    defaultBorderColor = cardBorderColor,
                                    placeholderColor = placeholderGray,
                                    capitalization = KeyboardCapitalization.Sentences,
                                    testTag = "register_custom_role_input"
                                )
                            }
                        }

                        // Optional family invite code section
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Key,
                                        contentDescription = null,
                                        tint = labelGray,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "Espacio: ${currentInviteCode.ifBlank { "NIDO01" }}",
                                        fontFamily = InterFontFamily,
                                        fontSize = 12.5.sp,
                                        color = labelGray
                                    )
                                }
                                Text(
                                    text = if (showInviteCodeField) "Ocultar código" else "Tengo otro código",
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.5.sp,
                                    color = accentBlue,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            showInviteCodeField = !showInviteCodeField
                                            if (!showInviteCodeField) customInviteCode = ""
                                        }
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                        .testTag("register_toggle_invite_code")
                                )
                            }
                            if (showInviteCodeField) {
                                SelectorRoundedInputField(
                                    value = customInviteCode,
                                    onValueChange = {
                                        val clean = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(12)
                                        customInviteCode = clean
                                    },
                                    placeholder = "Código familiar opcional (ej. FAM123)",
                                    singleLine = true,
                                    isError = false,
                                    accentColor = accentBlue,
                                    defaultBorderColor = cardBorderColor,
                                    placeholderColor = placeholderGray,
                                    capitalization = KeyboardCapitalization.Characters,
                                    testTag = "register_invite_code_input"
                                )
                            }
                        }

                        // Submit CTA
                        Button(
                            onClick = {
                                onRegisterMember(
                                    nicknameInput.trim(),
                                    resolvedRoleLabel,
                                    selectedColorHex,
                                    if (showInviteCodeField) customInviteCode.trim() else ""
                                )
                            },
                            enabled = nicknameInput.trim().isNotEmpty() && !isRegisterBusy,
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentBlue,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(vertical = 10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("register_submit_button")
                        ) {
                            if (isRegisterBusy) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Creando perfil...",
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            } else {
                                Text(
                                    text = "Crear mi perfil y entrar",
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            } else {
                // Path B: Existing Members Grid / Sync Loading / Friendly Empty State
                when {
                    isMembersLoading && members.isEmpty() -> {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = cardSurface,
                            border = BorderStroke(1.dp, cardBorderColor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 500.dp)
                                .testTag("selector_loading_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = accentBlue,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(34.dp)
                                )
                                Text(
                                    text = "Cargando perfiles familiares...",
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Sincronizando integrantes desde la nube. Si eres nuevo, puedes registrar tu perfil de inmediato.",
                                    fontFamily = InterFontFamily,
                                    fontSize = 13.sp,
                                    color = labelGray,
                                    textAlign = TextAlign.Center
                                )
                                OutlinedButton(
                                    onClick = {
                                        userSwitchedTab = true
                                        entryMode = SelectorEntryMode.REGISTER
                                    },
                                    shape = RoundedCornerShape(50),
                                    border = BorderStroke(1.5.dp, accentBlue),
                                    modifier = Modifier
                                        .height(44.dp)
                                        .testTag("selector_loading_register_cta")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PersonAdd,
                                        contentDescription = null,
                                        tint = accentBlue,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Soy nuevo · Registrar mi perfil",
                                        fontFamily = InterFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        color = accentBlue
                                    )
                                }
                            }
                        }
                    }

                    members.isEmpty() -> {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = cardSurface,
                            border = BorderStroke(1.dp, cardBorderColor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 500.dp)
                                .testTag("selector_empty_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(accentBlue.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PersonAdd,
                                        contentDescription = null,
                                        tint = accentBlue,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Text(
                                    text = "Aún no hay perfiles en este teléfono",
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Crea tu perfil en segundos para aparecer en la lista de tu familia y abrir el chat grupal.",
                                    fontFamily = InterFontFamily,
                                    fontSize = 13.sp,
                                    color = labelGray,
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = {
                                        userSwitchedTab = true
                                        entryMode = SelectorEntryMode.REGISTER
                                    },
                                    shape = RoundedCornerShape(50),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = accentBlue,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                        .testTag("selector_empty_register_cta")
                                ) {
                                    Text(
                                        text = "Registrar mi perfil ahora",
                                        fontFamily = InterFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.5.sp
                                    )
                                }
                            }
                        }
                    }

                    else -> {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 520.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(22.dp),
                            maxItemsInEachRow = 2
                        ) {
                            members.forEach { member ->
                                AnimatedVisibility(
                                    visible = visible,
                                    enter = fadeIn() + scaleIn(initialScale = 0.92f)
                                ) {
                                    NetflixProfileTile(
                                        member = member,
                                        onClick = { onSelectMember(member) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))
            TextButton(
                onClick = onChangeFamily,
                modifier = Modifier.testTag("selector_change_family")
            ) {
                Text(
                    text = "Cambiar o crear otro espacio familiar",
                    fontFamily = InterFontFamily,
                    color = labelGray
                )
            }
        }
    }
}

@Composable
private fun SelectorModeTabButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    accentBlue: Color,
    unselectedTextColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) accentBlue else Color.Transparent,
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Color.White else unselectedTextColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontFamily = InterFontFamily,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 12.5.sp,
                color = if (selected) Color.White else unselectedTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SelectorRoundedInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean,
    isError: Boolean,
    accentColor: Color,
    defaultBorderColor: Color,
    placeholderColor: Color,
    capitalization: KeyboardCapitalization,
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
            .heightIn(min = 46.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
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
                keyboardOptions = KeyboardOptions(capitalization = capitalization),
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
private fun NetflixProfileTile(
    member: FamilyMemberEntity,
    onClick: () -> Unit
) {
    val dark = LocalDarkTheme.current
    val tileShape = RoundedCornerShape(24.dp)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .widthIn(min = 124.dp, max = 140.dp)
            .clip(tileShape)
            .clickable(onClick = onClick)
            .padding(8.dp)
            .testTag("profile_card_${member.id}")
    ) {
        Box(modifier = Modifier.size(108.dp)) {
            MemberAvatar(
                emoji = member.avatarEmoji,
                colorHex = member.avatarColorHex,
                size = 108.dp,
                isOnline = member.isOnline,
                showOnlineDot = member.isOnline,
                photoData = member.avatarPhotoData,
                nameForInitials = member.name,
                cornerRadius = 24.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .border(
                        width = 1.dp,
                        color = if (dark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.08f),
                        shape = tileShape
                    )
            )

            if (member.isAdmin) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.42f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(7.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "Perfil protegido con contraseña",
                        tint = Color.White,
                        modifier = Modifier
                            .padding(5.dp)
                            .size(13.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = member.name,
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = member.roleLabel,
            fontFamily = InterFontFamily,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = member.lastSeenText,
            fontFamily = InterFontFamily,
            fontSize = 11.5.sp,
            color = if (member.isOnline) OnlineGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
