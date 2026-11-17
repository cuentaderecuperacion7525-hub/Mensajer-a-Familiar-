package com.example.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Login
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.CallRecordEntity
import com.example.data.model.CallType
import com.example.data.model.FamilyMemberEntity
import com.example.data.model.FamilySpace
import com.example.data.remote.GoogleAuthHelper
import com.example.data.remote.GoogleAuthResult
import com.example.ui.theme.AvatarColors
import com.example.ui.theme.DangerRed
import com.example.ui.theme.OnlineGreen
import com.example.util.parseHexColor
import kotlinx.coroutines.launch

@Composable
fun GoogleSignInScreen(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onSignedIn: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isBusy by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val silentUser = GoogleAuthHelper.attemptSilentSignIn(context)
        isBusy = false
        if (silentUser != null) {
            onSignedIn()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Surface(
                    shape = RoundedCornerShape(50.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .minimumInteractiveComponentSize()
                        .clickable { onToggleTheme() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = "Cambiar tema",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (isDarkTheme) "Modo blanco" else "Modo oscuro",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            Image(
                painter = painterResource(id = R.drawable.ic_famichat_logo),
                contentDescription = "Logo FamiChat",
                modifier = Modifier.size(84.dp)
            )

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = "Bienvenido a FamiChat",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Conecta tu cuenta de Google para sincronizar mensajes, notas de voz, ubicación GPS, llamadas y el tablón familiar en tiempo real entre dispositivos.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 420.dp)
            )

            Spacer(modifier = Modifier.height(34.dp))

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp)
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (isBusy) {
                        CircularProgressIndicator()
                        Text(
                            text = "Verificando sesión de Google...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Button(
                            onClick = {
                                scope.launch {
                                    isBusy = true
                                    errorMessage = null
                                    when (val res = GoogleAuthHelper.signInWithGoogle(context)) {
                                        is GoogleAuthResult.Success -> onSignedIn()
                                        GoogleAuthResult.Cancelled -> Unit
                                        is GoogleAuthResult.Error -> errorMessage = res.message
                                    }
                                    isBusy = false
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("google_signin_button")
                        ) {
                            Icon(Icons.Filled.Login, contentDescription = null)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Continuar con Google", style = MaterialTheme.typography.titleSmall)
                        }
                    }

                    if (!errorMessage.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = errorMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FamilySetupScreen(
    userEmail: String,
    userName: String,
    families: List<FamilySpace>,
    isBusy: Boolean,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onSelectFamily: (String) -> Unit,
    onCreateFamily: (familyName: String, adminName: String, emoji: String, colorHex: String) -> Unit,
    onJoinFamily: (String) -> Unit,
    onSignOutGoogle: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var familyName by remember { mutableStateOf("Mi Familia") }
    var adminName by remember(userName) {
        mutableStateOf(userName.substringBefore(" ").ifBlank { "Papá" })
    }
    var adminEmoji by remember { mutableStateOf("👨🏽") }
    var adminColor by remember { mutableStateOf(AvatarColors.first()) }
    var inviteCode by remember { mutableStateOf("") }

    val emojiOptions = listOf("👨🏽", "👩🏽", "🧑🏽", "👴🏽", "👵🏽", "🏡")

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_famichat_logo),
                        contentDescription = "Logo FamiChat",
                        modifier = Modifier.size(32.dp)
                    )
                    Column {
                        Text(
                            text = "FamiChat",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        if (userEmail.isNotBlank()) {
                            Text(
                                text = userEmail,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = "Cambiar tema"
                        )
                    }
                    TextButton(
                        onClick = onSignOutGoogle,
                        modifier = Modifier.testTag("setup_signout_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Salir")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (families.isNotEmpty()) {
                    Text(
                        text = "Tus espacios familiares",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    families.forEach { fam ->
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .clickable(enabled = !isBusy) { onSelectFamily(fam.id) }
                                .testTag("family_card_${fam.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.FamilyRestroom,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(fam.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "Código de invitación: ${fam.inviteCode}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "Entrar",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        TabRow(selectedTabIndex = selectedTab) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = { Text("Crear familia") },
                                icon = { Icon(Icons.Filled.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = { Text("Unirme con código") },
                                icon = { Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                        }

                        if (selectedTab == 0) {
                            Text(
                                text = "Crea un nuevo espacio privado y comparte el código de invitación con tu familia.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = familyName,
                                onValueChange = { familyName = it },
                                label = { Text("Nombre de la familia") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("family_name_input")
                            )
                            OutlinedTextField(
                                value = adminName,
                                onValueChange = { adminName = it },
                                label = { Text("Tu nombre de administrador") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("admin_name_input")
                            )
                            Text("Elige tu avatar", style = MaterialTheme.typography.labelMedium)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(emojiOptions) { emoji ->
                                    Surface(
                                        shape = CircleShape,
                                        color = if (adminEmoji == emoji) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .clickable { adminEmoji = emoji }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(emoji, fontSize = 20.sp)
                                        }
                                    }
                                }
                            }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(AvatarColors) { hex ->
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(parseHexColor(hex))
                                            .border(
                                                width = if (adminColor == hex) 3.dp else 0.dp,
                                                color = if (adminColor == hex) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                                shape = CircleShape
                                            )
                                            .clickable { adminColor = hex }
                                    )
                                }
                            }
                            Button(
                                onClick = { onCreateFamily(familyName, adminName, adminEmoji, adminColor) },
                                enabled = !isBusy && familyName.isNotBlank() && adminName.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().testTag("create_family_button")
                            ) {
                                if (isBusy) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text("Crear espacio familiar")
                            }
                        } else {
                            Text(
                                text = "Ingresa el código de 6 caracteres que generó el administrador de tu familia.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = inviteCode,
                                onValueChange = { raw ->
                                    val filtered = raw.uppercase().filter { it.isLetterOrDigit() }.take(6)
                                    inviteCode = filtered
                                },
                                label = { Text("Código familiar (ej. A7K9P2)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("invite_code_input")
                            )
                            Button(
                                onClick = { onJoinFamily(inviteCode) },
                                enabled = !isBusy && inviteCode.length == 6,
                                modifier = Modifier.fillMaxWidth().testTag("join_family_button")
                            ) {
                                if (isBusy) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text("Unirme a la familia")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IncomingCallOverlay(
    call: CallRecordEntity,
    callerMember: FamilyMemberEntity?,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val pulse = rememberInfiniteTransition(label = "incoming_pulse")
    val ringScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.24f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "incoming_ring"
    )
    val displayName = callerMember?.name ?: call.title
    val emoji = callerMember?.avatarEmoji ?: call.avatarEmoji
    val accent = parseHexColor(callerMember?.avatarColorHex ?: call.avatarColorHex)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF090E1A), Color(0xFF17253B), Color(0xFF090E1A))
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(28.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    color = Color.White.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(50.dp)
                ) {
                    Text(
                        text = if (call.callType == CallType.VIDEO) "VIDEOLLAMADA ENTRANTE" else "LLAMADA ENTRANTE",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Llamada familiar en FamiChat",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF93C5FD)
                )
            }

            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(164.dp)
                        .scale(ringScale)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.22f))
                )
                Box(
                    modifier = Modifier
                        .size(132.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = emoji, fontSize = 58.sp)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onDecline,
                        modifier = Modifier
                            .size(66.dp)
                            .clip(CircleShape)
                            .background(DangerRed)
                            .testTag("incoming_call_decline")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CallEnd,
                            contentDescription = "Rechazar llamada",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Rechazar", color = Color.White, style = MaterialTheme.typography.labelMedium)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onAccept,
                        modifier = Modifier
                            .size(66.dp)
                            .clip(CircleShape)
                            .background(OnlineGreen)
                            .testTag("incoming_call_accept")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Call,
                            contentDescription = "Contestar llamada",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Contestar", color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
