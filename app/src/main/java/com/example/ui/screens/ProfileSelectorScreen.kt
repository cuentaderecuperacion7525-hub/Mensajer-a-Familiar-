package com.example.ui.screens

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import com.example.data.model.FamilyMemberEntity
import com.example.ui.components.extractAvatarInitials
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.LocalDarkTheme
import com.example.util.ImageCompressionHelper
import com.example.util.parseHexColor

private val SelectorScreenBg = Color(0xFFFFFFFF)
private val SelectorTitleInk = Color(0xFF1B1F2A)
private val SelectorSubtitleGray = Color(0xFF5B6270)
private val SelectorCardBg = Color(0xFFF5F7FB)
private val SelectorCardBorder = Color(0xFFE3E6EC)
private val SelectorAdminPillBg = Color(0xFFE4EDFC)
private val SelectorAdminBlue = Color(0xFF1A5FD4)

private val SelectorFallbackPalette = listOf(
    "#1A5FD4",
    "#6B4FD8",
    "#C94521",
    "#B0306A",
    "#0E7C66",
    "#A45F08"
)

internal fun formatShortProfileName(fullName: String): String {
    val parts = fullName.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (parts.size <= 2) return parts.joinToString(" ")
    val connectors = setOf("de", "del", "la", "las", "los", "y")
    return if (parts[1].lowercase() in connectors) {
        parts.take(3).joinToString(" ")
    } else {
        parts.take(2).joinToString(" ")
    }
}

internal fun resolveProfileSelectorColors(orderedMembers: List<FamilyMemberEntity>): Map<String, String> {
    val hexRegex = Regex("^#[0-9A-F]{6}$")
    val defaultBlueAliases = setOf("#1A73E8", "#1A5FD4")
    val usedColors = mutableSetOf<String>()
    val assigned = linkedMapOf<String, String>()

    // Pass 1: claim each member's first distinct valid saved color
    orderedMembers.forEach { member ->
        val rawHex = member.avatarColorHex.trim().uppercase()
        val isValid = hexRegex.matches(rawHex)
        val isCollision = !isValid ||
            rawHex in usedColors ||
            (rawHex in defaultBlueAliases && usedColors.any { it in defaultBlueAliases })
        if (!isCollision) {
            assigned[member.id] = rawHex
            usedColors.add(rawHex)
            if (rawHex in defaultBlueAliases) {
                usedColors.addAll(defaultBlueAliases)
            }
        }
    }

    // Pass 2: assign non-repeating fallback palette colors in list order for missing/duplicated colors
    var fallbackIdx = 0
    orderedMembers.forEach { member ->
        if (!assigned.containsKey(member.id)) {
            val nextColor = SelectorFallbackPalette.firstOrNull { candidate ->
                val upper = candidate.uppercase()
                upper !in usedColors &&
                    !(upper in defaultBlueAliases && usedColors.any { it in defaultBlueAliases })
            } ?: SelectorFallbackPalette[fallbackIdx % SelectorFallbackPalette.size]
            fallbackIdx++
            assigned[member.id] = nextColor
            usedColors.add(nextColor.uppercase())
            if (nextColor.uppercase() in defaultBlueAliases) {
                usedColors.addAll(defaultBlueAliases)
            }
        }
    }
    return assigned
}

@Composable
fun ProfileSelectorScreen(
    members: List<FamilyMemberEntity>,
    isMembersLoading: Boolean,
    snackbarMessage: String?,
    onClearSnackbar: () -> Unit,
    onSelectMember: (FamilyMemberEntity) -> Unit,
    onRetryLoad: () -> Unit,
    errorMessage: String? = null,
    isDarkTheme: Boolean = LocalDarkTheme.current
) {
    val screenBg = if (isDarkTheme) MaterialTheme.colorScheme.background else SelectorScreenBg
    val titleInk = if (isDarkTheme) MaterialTheme.colorScheme.onBackground else SelectorTitleInk
    val subtitleInk = if (isDarkTheme) MaterialTheme.colorScheme.onSurfaceVariant else SelectorSubtitleGray
    val cardBg = if (isDarkTheme) MaterialTheme.colorScheme.surface else SelectorCardBg
    val cardBorder = if (isDarkTheme) MaterialTheme.colorScheme.outline else SelectorCardBorder
    val adminPillBg = if (isDarkTheme) MaterialTheme.colorScheme.primaryContainer else SelectorAdminPillBg
    val adminBlue = if (isDarkTheme) MaterialTheme.colorScheme.primary else SelectorAdminBlue
    val retryContentColor = if (isDarkTheme) MaterialTheme.colorScheme.onPrimary else Color.White

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !isDarkTheme
            controller.isAppearanceLightNavigationBars = !isDarkTheme
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            onClearSnackbar()
        }
    }

    val orderedMembers = remember(members) {
        members.sortedWith(
            compareByDescending<FamilyMemberEntity> { it.isAdmin }
                .thenBy { it.createdAt }
        )
    }
    val resolvedColors = remember(orderedMembers) {
        resolveProfileSelectorColors(orderedMembers)
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = screenBg,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(screenBg)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 32.dp)
                .testTag("profile_selector_root"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 390.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "¿Quién eres?",
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    color = titleInk,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Toca tu perfil para entrar a FamiChat",
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = subtitleInk,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(28.dp))

                when {
                    isMembersLoading && orderedMembers.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp)
                                .testTag("selector_loading_state"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            CircularProgressIndicator(
                                color = adminBlue,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = "Cargando perfiles…",
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                color = subtitleInk,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    orderedMembers.isEmpty() -> {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = cardBg,
                            border = BorderStroke(1.dp, cardBorder),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("selector_empty_state")
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = if (!errorMessage.isNullOrBlank()) {
                                        "No se pudieron cargar los perfiles"
                                    } else {
                                        "Aún no hay perfiles. Pídele al administrador que cree la familia."
                                    },
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    color = titleInk,
                                    textAlign = TextAlign.Center
                                )
                                if (!errorMessage.isNullOrBlank()) {
                                    Text(
                                        text = errorMessage,
                                        fontFamily = InterFontFamily,
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp,
                                        color = subtitleInk,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.testTag("selector_error_detail")
                                    )
                                }
                                Button(
                                    onClick = onRetryLoad,
                                    shape = RoundedCornerShape(50),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = adminBlue,
                                        contentColor = retryContentColor
                                    ),
                                    elevation = ButtonDefaults.buttonElevation(
                                        defaultElevation = 0.dp,
                                        pressedElevation = 0.dp
                                    ),
                                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("selector_retry_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Reintentar",
                                        fontFamily = InterFontFamily,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }

                    else -> {
                        val rows = remember(orderedMembers) { orderedMembers.chunked(2) }
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val cardWidth = ((maxWidth - 12.dp) / 2f).coerceAtLeast(120.dp)
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                rows.forEach { rowMembers ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        rowMembers.forEach { member ->
                                            ProfileSelectionCard(
                                                member = member,
                                                resolvedColorHex = resolvedColors[member.id] ?: member.avatarColorHex,
                                                cardWidth = cardWidth,
                                                isDarkTheme = isDarkTheme,
                                                cardBg = cardBg,
                                                cardBorder = cardBorder,
                                                titleInk = titleInk,
                                                subtitleInk = subtitleInk,
                                                adminPillBg = adminPillBg,
                                                adminBlue = adminBlue,
                                                onClick = { onSelectMember(member) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileSelectionCard(
    member: FamilyMemberEntity,
    resolvedColorHex: String,
    cardWidth: androidx.compose.ui.unit.Dp,
    isDarkTheme: Boolean,
    cardBg: Color,
    cardBorder: Color,
    titleInk: Color,
    subtitleInk: Color,
    adminPillBg: Color,
    adminBlue: Color,
    onClick: () -> Unit
) {
    val cardShape = RoundedCornerShape(18.dp)
    val avatarShape = RoundedCornerShape(18.dp)
    val hasPhoto = member.avatarPhotoData.isNotBlank()
    val photoSurfaceBg = if (isDarkTheme) MaterialTheme.colorScheme.surfaceVariant else Color.White
    val avatarBg = if (hasPhoto) photoSurfaceBg else parseHexColor(resolvedColorHex)
    val lockBadgeBg = if (isDarkTheme) MaterialTheme.colorScheme.surfaceVariant else SelectorTitleInk
    val displayName = remember(member.name) { formatShortProfileName(member.name) }
    val initials = remember(displayName, member.name, member.avatarEmoji) {
        extractAvatarInitials(displayName.ifBlank { member.name }, member.avatarEmoji)
    }

    Surface(
        shape = cardShape,
        color = cardBg,
        border = BorderStroke(1.dp, cardBorder),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .width(cardWidth)
            .height(124.dp)
            .clip(cardShape)
            .clickable(onClick = onClick)
            .testTag("profile_card_${member.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(modifier = Modifier.size(56.dp)) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(avatarShape)
                        .background(avatarBg),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasPhoto) {
                        AsyncImage(
                            model = ImageCompressionHelper.resolveCoilModel(member.avatarPhotoData),
                            contentDescription = "Foto de $displayName",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = initials.ifBlank { member.avatarEmoji.ifBlank { "U" } },
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 20.sp,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                if (member.isAdmin) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 3.dp, y = 3.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(lockBadgeBg)
                            .border(2.dp, cardBg, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Perfil de administrador con PIN",
                            tint = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = displayName,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                color = titleInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (member.isAdmin) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = adminPillBg,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Text(
                        text = "Papá · con PIN",
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        color = adminBlue,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            } else {
                Text(
                    text = member.roleLabel,
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = subtitleInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
