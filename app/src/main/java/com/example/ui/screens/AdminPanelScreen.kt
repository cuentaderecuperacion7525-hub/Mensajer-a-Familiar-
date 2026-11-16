package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.model.ChatThreadEntity
import com.example.data.model.ChatType
import com.example.data.model.FamilyMemberEntity
import com.example.data.model.JsonHelpers
import com.example.ui.components.MemberAvatar
import com.example.ui.theme.AvatarColors
import com.example.util.parseHexColor

private val MemberEmojis = listOf("👨🏽", "👩🏽", "🧑🏽‍🎓", "👧🏽", "👴🏽", "👵🏽", "🧑🏽", "🐶")
private val GroupEmojis = listOf("🏡", "⚽", "🛒", "🎉", "✈️", "📚", "🍽️", "❤️")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    members: List<FamilyMemberEntity>,
    chats: List<ChatThreadEntity>,
    currentMemberId: String?,
    familyName: String,
    inviteCode: String,
    currentMasterPin: String,
    currentSwitchPin: String,
    currentUpdateRepo: String,
    onBack: () -> Unit,
    onSaveMember: (id: String?, name: String, role: String, isAdmin: Boolean, color: String, emoji: String, phoneNumber: String, bioText: String, newPhotoUri: Uri?, removePhoto: Boolean) -> Unit,
    onDeleteMember: (String) -> Unit,
    onSaveGroup: (id: String?, title: String, color: String, emoji: String, participantIds: List<String>) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onUpdateMasterPin: (String) -> Unit,
    onUpdateSwitchPin: (String) -> Unit,
    onUpdateRepoSlug: (String) -> Unit,
    onChangeFamilySpace: () -> Unit
) {
    var selectedSection by remember { mutableIntStateOf(0) }
    var editingMember by remember { mutableStateOf<FamilyMemberEntity?>(null) }
    var showMemberDialog by remember { mutableStateOf(false) }

    var editingGroup by remember { mutableStateOf<ChatThreadEntity?>(null) }
    var showGroupDialog by remember { mutableStateOf(false) }

    val groups = remember(chats) { chats.filter { it.type == ChatType.GROUP } }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Panel de Administración", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = familyName.ifBlank { "Control familiar" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("admin_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            if (selectedSection == 0) {
                FloatingActionButton(
                    onClick = {
                        editingMember = null
                        showMemberDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("admin_add_member_fab")
                ) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = "Crear miembro")
                }
            } else if (selectedSection == 1) {
                FloatingActionButton(
                    onClick = {
                        editingGroup = null
                        showGroupDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("admin_add_group_fab")
                ) {
                    Icon(Icons.Filled.GroupAdd, contentDescription = "Crear grupo")
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(modifier = Modifier.fillMaxSize().widthIn(max = 650.dp)) {
                TabRow(selectedTabIndex = selectedSection) {
                    Tab(
                        selected = selectedSection == 0,
                        onClick = { selectedSection = 0 },
                        text = { Text("Miembros (${members.size})") },
                        modifier = Modifier.testTag("admin_tab_members")
                    )
                    Tab(
                        selected = selectedSection == 1,
                        onClick = { selectedSection = 1 },
                        text = { Text("Grupos (${groups.size})") },
                        modifier = Modifier.testTag("admin_tab_groups")
                    )
                    Tab(
                        selected = selectedSection == 2,
                        onClick = { selectedSection = 2 },
                        text = { Text("Seguridad") },
                        modifier = Modifier.testTag("admin_tab_pin")
                    )
                }

                when (selectedSection) {
                    0 -> AdminMembersList(
                        members = members,
                        currentMemberId = currentMemberId,
                        onEdit = {
                            editingMember = it
                            showMemberDialog = true
                        },
                        onDelete = onDeleteMember
                    )
                    1 -> AdminGroupsList(
                        groups = groups,
                        members = members,
                        onEdit = {
                            editingGroup = it
                            showGroupDialog = true
                        },
                        onDelete = onDeleteGroup
                    )
                    2 -> AdminPinSettings(
                        familyName = familyName,
                        inviteCode = inviteCode,
                        currentMasterPin = currentMasterPin,
                        currentSwitchPin = currentSwitchPin,
                        currentUpdateRepo = currentUpdateRepo,
                        onUpdateMasterPin = onUpdateMasterPin,
                        onUpdateSwitchPin = onUpdateSwitchPin,
                        onUpdateRepoSlug = onUpdateRepoSlug,
                        onChangeFamilySpace = onChangeFamilySpace
                    )
                }
            }
        }
    }

    if (showMemberDialog) {
        MemberEditorDialog(
            initial = editingMember,
            onDismiss = { showMemberDialog = false },
            onSave = { name, role, isAdmin, color, emoji, phoneNumber, bio, newPhotoUri, removePhoto ->
                onSaveMember(editingMember?.id, name, role, isAdmin, color, emoji, phoneNumber, bio, newPhotoUri, removePhoto)
                showMemberDialog = false
            }
        )
    }

    if (showGroupDialog) {
        GroupEditorDialog(
            initial = editingGroup,
            members = members,
            onDismiss = { showGroupDialog = false },
            onSave = { title, color, emoji, participantIds ->
                onSaveGroup(editingGroup?.id, title, color, emoji, participantIds)
                showGroupDialog = false
            }
        )
    }
}

@Composable
private fun AdminMembersList(
    members: List<FamilyMemberEntity>,
    currentMemberId: String?,
    onEdit: (FamilyMemberEntity) -> Unit,
    onDelete: (String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(members, key = { it.id }) { member ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MemberAvatar(
                        emoji = member.avatarEmoji,
                        colorHex = member.avatarColorHex,
                        photoData = member.avatarPhotoData,
                        size = 48.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(member.name, style = MaterialTheme.typography.titleMedium)
                            if (member.isAdmin) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    Icons.Filled.Shield,
                                    contentDescription = "Administrador",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Text(
                            member.roleLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { onEdit(member) },
                        modifier = Modifier.testTag("edit_member_${member.id}")
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = "Editar miembro")
                    }
                    if (member.id != currentMemberId) {
                        IconButton(
                            onClick = { onDelete(member.id) },
                            modifier = Modifier.testTag("delete_member_${member.id}")
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Eliminar miembro",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminGroupsList(
    groups: List<ChatThreadEntity>,
    members: List<FamilyMemberEntity>,
    onEdit: (ChatThreadEntity) -> Unit,
    onDelete: (String) -> Unit
) {
    val membersMap = remember(members) { members.associateBy { it.id } }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(groups, key = { it.id }) { group ->
            val pIds = JsonHelpers.decodeStringList(group.participantIdsJson)
            val participantNames = pIds.mapNotNull { membersMap[it]?.name }.joinToString(", ")
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MemberAvatar(
                        emoji = group.avatarEmoji,
                        colorHex = group.avatarColorHex,
                        size = 48.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(group.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${pIds.size} miembros: $participantNames",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { onEdit(group) },
                        modifier = Modifier.testTag("edit_group_${group.id}")
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = "Editar grupo")
                    }
                    IconButton(
                        onClick = { onDelete(group.id) },
                        modifier = Modifier.testTag("delete_group_${group.id}")
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Eliminar grupo",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminPinSettings(
    familyName: String,
    inviteCode: String,
    currentMasterPin: String,
    currentSwitchPin: String,
    currentUpdateRepo: String,
    onUpdateMasterPin: (String) -> Unit,
    onUpdateSwitchPin: (String) -> Unit,
    onUpdateRepoSlug: (String) -> Unit,
    onChangeFamilySpace: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var copiedInvite by remember { mutableStateOf(false) }

    var currentInput by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf<String?>(null) }

    var newSwitchPin by remember { mutableStateOf("") }
    var switchFeedback by remember { mutableStateOf<String?>(null) }

    var repoInput by remember(currentUpdateRepo) {
        mutableStateOf(currentUpdateRepo.ifBlank { BuildConfig.GITHUB_REPO })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (inviteCode.isNotBlank()) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.FamilyRestroom,
                            contentDescription = "Invitación familiar",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Código de invitación familiar",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(
                        "Comparte este código para que otros teléfonos de la familia se unan a \"$familyName\" desde sus cuentas de Google.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = inviteCode,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 4.sp
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("admin_invite_code")
                            )
                            IconButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(inviteCode))
                                    copiedInvite = true
                                }
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copiar código")
                            }
                        }
                    }
                    if (copiedInvite) {
                        Text(
                            "Código copiado al portapapeles",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "Únete a nuestra familia \"$familyName\" en FamiChat con el código: $inviteCode"
                                    )
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Compartir código familiar"))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Compartir")
                        }
                        OutlinedButton(
                            onClick = onChangeFamilySpace,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cambiar familia")
                        }
                    }
                }
            }
        }

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.SwapHoriz,
                        contentDescription = "Clave de Switch",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Clave de Switch (Salir de perfil)",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Text(
                    "Si alguien entra por error a otro perfil, necesitará esta clave de 4 dígitos autorizada por Papá para usar la opción Switch y volver al selector.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Clave activa actual:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = currentSwitchPin,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 4.sp
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("admin_active_switch_pin")
                        )
                    }
                }

                OutlinedTextField(
                    value = newSwitchPin,
                    onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) newSwitchPin = it },
                    label = { Text("Nueva clave de Switch (4 dígitos)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_new_switch_pin_input")
                )

                if (switchFeedback != null) {
                    Text(
                        text = switchFeedback!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Button(
                    onClick = {
                        if (newSwitchPin.length != 4) {
                            switchFeedback = "La clave de Switch debe tener exactamente 4 dígitos."
                        } else {
                            switchFeedback = null
                            onUpdateSwitchPin(newSwitchPin)
                            newSwitchPin = ""
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_save_switch_pin_button")
                ) {
                    Text("Guardar clave de Switch")
                }
            }
        }

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Key,
                        contentDescription = "Contraseña de Papá",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Contraseña de acceso de Papá",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Text(
                    "Protege el ingreso al perfil administrador de Papá y el control familiar (PIN de 4 dígitos).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = currentInput,
                    onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) currentInput = it },
                    label = { Text("PIN actual (4 dígitos)") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("admin_current_pin_input")
                )
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) newPin = it },
                    label = { Text("Nuevo PIN (4 dígitos)") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("admin_new_pin_input")
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) confirmPin = it },
                    label = { Text("Confirmar nuevo PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("admin_confirm_pin_input")
                )

                if (feedback != null) {
                    Text(
                        text = feedback!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Button(
                    onClick = {
                        when {
                            currentInput != currentMasterPin -> feedback = "El PIN actual no coincide."
                            newPin.length != 4 -> feedback = "El nuevo PIN debe tener exactamente 4 dígitos."
                            newPin != confirmPin -> feedback = "Los nuevos PIN no coinciden."
                            else -> {
                                feedback = null
                                onUpdateMasterPin(newPin)
                                currentInput = ""
                                newPin = ""
                                confirmPin = ""
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("admin_save_pin_button")
                ) {
                    Text("Guardar nuevo PIN")
                }
            }
        }

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.SystemUpdate,
                        contentDescription = "Actualizaciones de la app",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Actualizaciones desde GitHub",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Text(
                    "Versión actual: v${BuildConfig.VERSION_NAME} (compilación ${BuildConfig.VERSION_CODE}). Configura tu repositorio de GitHub para que toda la familia pueda buscar actualizaciones desde el menú.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = repoInput,
                    onValueChange = { repoInput = it },
                    label = { Text("Repositorio (usuario/repositorio)") },
                    placeholder = { Text("ej. usuario/Mensajer-a-Familiar-") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_update_repo_input")
                )

                Button(
                    onClick = { onUpdateRepoSlug(repoInput) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_save_update_repo_button")
                ) {
                    Text("Guardar repositorio")
                }
            }
        }
    }
}

@Composable
private fun MemberEditorDialog(
    initial: FamilyMemberEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, role: String, isAdmin: Boolean, color: String, emoji: String, phoneNumber: String, bioText: String, newPhotoUri: Uri?, removePhoto: Boolean) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var role by remember { mutableStateOf(initial?.roleLabel ?: "") }
    var phone by remember { mutableStateOf(initial?.phoneNumber ?: "") }
    var bioText by remember { mutableStateOf(initial?.bioText ?: "") }
    var isAdmin by remember { mutableStateOf(initial?.isAdmin ?: false) }
    var selectedColor by remember { mutableStateOf(initial?.avatarColorHex ?: AvatarColors.first()) }
    var selectedEmoji by remember { mutableStateOf(initial?.avatarEmoji ?: MemberEmojis.first()) }
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var removePhoto by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                pendingPhotoUri = uri
                removePhoto = false
            }
        }
    )

    val previewPhoto = when {
        pendingPhotoUri != null -> pendingPhotoUri.toString()
        removePhoto -> ""
        else -> initial?.avatarPhotoData.orEmpty()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nuevo miembro" else "Editar miembro") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MemberAvatar(
                        emoji = selectedEmoji,
                        colorHex = selectedColor,
                        photoData = previewPhoto,
                        size = 54.dp
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(
                            onClick = {
                                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (previewPhoto.isBlank()) "Foto de galería" else "Cambiar foto")
                        }
                        if (previewPhoto.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    pendingPhotoUri = null
                                    removePhoto = true
                                }
                            ) {
                                Text("Usar solo emoji", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre (ej. Abuela, Hermana)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("member_name_input")
                )
                OutlinedTextField(
                    value = role,
                    onValueChange = { role = it },
                    label = { Text("Rol familiar") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("member_role_input")
                )
                OutlinedTextField(
                    value = bioText,
                    onValueChange = { if (it.length <= 120) bioText = it },
                    label = { Text("Bio o frase personal (opcional)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("member_bio_input")
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Teléfono (opcional para marcar)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("member_phone_input")
                )
                Text("Avatar", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(MemberEmojis) { emoji ->
                        Surface(
                            shape = CircleShape,
                            color = if (selectedEmoji == emoji) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .clickable { selectedEmoji = emoji }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(emoji, fontSize = 20.sp)
                            }
                        }
                    }
                }
                Text("Color de perfil", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AvatarColors) { hex ->
                        val color = parseHexColor(hex)
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (selectedColor == hex) 3.dp else 0.dp,
                                    color = if (selectedColor == hex) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = hex }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Permisos de Administrador", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = isAdmin, onCheckedChange = { isAdmin = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(name, role, isAdmin, selectedColor, selectedEmoji, phone, bioText, pendingPhotoUri, removePhoto)
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("save_member_button")
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun GroupEditorDialog(
    initial: ChatThreadEntity?,
    members: List<FamilyMemberEntity>,
    onDismiss: () -> Unit,
    onSave: (title: String, color: String, emoji: String, participantIds: List<String>) -> Unit
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var selectedColor by remember { mutableStateOf(initial?.avatarColorHex ?: AvatarColors.first()) }
    var selectedEmoji by remember { mutableStateOf(initial?.avatarEmoji ?: GroupEmojis.first()) }
    val selectedParticipants = remember {
        mutableStateListOf<String>().apply {
            if (initial != null) {
                addAll(JsonHelpers.decodeStringList(initial.participantIdsJson))
            } else {
                addAll(members.map { it.id })
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nuevo grupo familiar" else "Editar grupo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Nombre del grupo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("group_title_input")
                )
                Text("Ícono del grupo", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(GroupEmojis) { emoji ->
                        Surface(
                            shape = CircleShape,
                            color = if (selectedEmoji == emoji) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable { selectedEmoji = emoji }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(emoji, fontSize = 18.sp)
                            }
                        }
                    }
                }
                Text("Color", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AvatarColors) { hex ->
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(parseHexColor(hex))
                                .border(
                                    width = if (selectedColor == hex) 3.dp else 0.dp,
                                    color = if (selectedColor == hex) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = hex }
                        )
                    }
                }
                Text("Participantes (${selectedParticipants.size})", style = MaterialTheme.typography.labelMedium)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    members.forEach { member ->
                        val checked = selectedParticipants.contains(member.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    if (checked) selectedParticipants.remove(member.id)
                                    else selectedParticipants.add(member.id)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    if (isChecked) {
                                        if (!selectedParticipants.contains(member.id)) selectedParticipants.add(member.id)
                                    } else {
                                        selectedParticipants.remove(member.id)
                                    }
                                }
                            )
                            Text("${member.avatarEmoji} ${member.name}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, selectedColor, selectedEmoji, selectedParticipants.toList()) },
                enabled = title.isNotBlank() && selectedParticipants.size >= 2,
                modifier = Modifier.testTag("save_group_button")
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
