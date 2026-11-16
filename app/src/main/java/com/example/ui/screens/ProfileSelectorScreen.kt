package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FamilyMemberEntity
import com.example.ui.theme.LocalDarkTheme
import com.example.ui.theme.OnlineGreen
import com.example.util.parseHexColor

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileSelectorScreen(
    members: List<FamilyMemberEntity>,
    familyName: String,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onSelectMember: (FamilyMemberEntity) -> Unit,
    onChangeFamily: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = "Logo NidoFamiliar",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "NidoFamiliar",
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
                            tint = MaterialTheme.colorScheme.primary,
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

            Spacer(modifier = Modifier.height(42.dp))

            Text(
                text = "¿Quién está ingresando ahora?",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (familyName.isNotBlank()) "$familyName · Toca tu perfil" else "Toca tu perfil para abrir tus chats familiares",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(34.dp))

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                maxItemsInEachRow = 2
            ) {
                members.forEach { member ->
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn() + scaleIn(initialScale = 0.9f)
                    ) {
                        NetflixProfileTile(
                            member = member,
                            onClick = { onSelectMember(member) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            TextButton(
                onClick = onChangeFamily,
                modifier = Modifier.testTag("selector_change_family")
            ) {
                Text(
                    text = "Cambiar espacio familiar",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun NetflixProfileTile(
    member: FamilyMemberEntity,
    onClick: () -> Unit
) {
    val accent = parseHexColor(member.avatarColorHex)
    val dark = LocalDarkTheme.current
    val tileShape = RoundedCornerShape(20.dp)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .widthIn(min = 124.dp, max = 136.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(6.dp)
            .testTag("profile_card_${member.id}")
    ) {
        Box(
            modifier = Modifier
                .size(116.dp)
                .clip(tileShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            accent.copy(alpha = if (dark) 0.92f else 0.88f),
                            accent
                        )
                    )
                )
                .border(
                    width = 1.5.dp,
                    color = if (dark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.08f),
                    shape = tileShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = member.avatarEmoji.ifBlank { "👤" },
                fontSize = 52.sp
            )

            if (member.isAdmin) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.34f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "Perfil protegido con contraseña",
                        tint = Color.White,
                        modifier = Modifier
                            .padding(5.dp)
                            .size(14.dp)
                    )
                }
            }

            if (member.isOnline) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(OnlineGreen)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = member.name,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = member.roleLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
