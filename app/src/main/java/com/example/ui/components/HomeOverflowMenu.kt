package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.InterFontFamily
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun HomeOverflowMenuPopup(
    isAdmin: Boolean,
    isDarkTheme: Boolean,
    isSwitchPinActive: Boolean,
    onDismiss: () -> Unit,
    onOpenProfile: () -> Unit,
    onSwitchProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAdminPanel: () -> Unit
) {
    val cardBg = if (isDarkTheme) MaterialTheme.colorScheme.surface else Color.White
    val cardBorder = if (isDarkTheme) {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
    } else {
        Color(0xFFE3E6EC)
    }
    val titleColor = if (isDarkTheme) MaterialTheme.colorScheme.onSurface else Color(0xFF1B1F2A)
    val subtitleColor = if (isDarkTheme) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF5B6270)
    val pressedBg = if (isDarkTheme) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    } else {
        Color(0xFFF5F7FB)
    }

    val profileIconBg = if (isDarkTheme) Color(0xFF1A5FD4).copy(alpha = 0.22f) else Color(0xFFE4EDFC)
    val profileIconTint = if (isDarkTheme) MaterialTheme.colorScheme.primary else Color(0xFF1A5FD4)

    val switchIconBg = if (isDarkTheme) Color(0xFFA45F08).copy(alpha = 0.24f) else Color(0xFFFAEEDA)
    val switchIconTint = if (isDarkTheme) Color(0xFFF5B759) else Color(0xFFA45F08)

    val settingsIconBg = if (isDarkTheme) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFF0F2F8)
    val settingsIconTint = if (isDarkTheme) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF5B6270)

    val adminIconBg = if (isDarkTheme) Color(0xFF6B4FD8).copy(alpha = 0.24f) else Color(0xFFEEE9FB)
    val adminIconTint = if (isDarkTheme) Color(0xFFB39DF5) else Color(0xFF6B4FD8)

    // Invisible click-outside layer (no dark backdrop scrim)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            .testTag("home_more_menu_backdrop")
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = cardBg,
            border = BorderStroke(1.dp, cardBorder),
            tonalElevation = 0.dp,
            shadowElevation = if (isDarkTheme) 10.dp else 8.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 6.dp, end = 8.dp)
                .width(280.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .testTag("home_more_menu")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                HomeOverflowMenuRow(
                    title = "Mi perfil",
                    subtitle = "Foto, estado y frase",
                    titleColor = titleColor,
                    subtitleColor = subtitleColor,
                    pressedBg = pressedBg,
                    iconBoxBg = profileIconBg,
                    testTag = "menu_profile_options",
                    onClick = {
                        onDismiss()
                        onOpenProfile()
                    },
                    leadingIcon = {
                        HomeMenuPersonIcon(
                            tint = profileIconTint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                )

                HomeOverflowMenuRow(
                    title = "Cambiar de perfil",
                    subtitle = "Pide la clave de seguridad",
                    titleColor = titleColor,
                    subtitleColor = subtitleColor,
                    pressedBg = pressedBg,
                    iconBoxBg = switchIconBg,
                    testTag = "menu_switch_profile",
                    onClick = {
                        onDismiss()
                        onSwitchProfile()
                    },
                    leadingIcon = {
                        HomeMenuSwapArrowsIcon(
                            tint = switchIconTint,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    trailingContent = if (isSwitchPinActive) {
                        {
                            HomeMenuLockIcon(
                                tint = subtitleColor,
                                modifier = Modifier
                                    .size(18.dp)
                                    .testTag("menu_switch_profile_lock")
                            )
                        }
                    } else {
                        null
                    }
                )

                HomeOverflowMenuRow(
                    title = "Ajustes",
                    subtitle = "Notificaciones, tema y más",
                    titleColor = titleColor,
                    subtitleColor = subtitleColor,
                    pressedBg = pressedBg,
                    iconBoxBg = settingsIconBg,
                    testTag = "menu_settings",
                    onClick = {
                        onDismiss()
                        onOpenSettings()
                    },
                    leadingIcon = {
                        HomeMenuGearIcon(
                            tint = settingsIconTint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                )

                if (isAdmin) {
                    HomeOverflowMenuRow(
                        title = "Administración",
                        subtitle = "Solo para el administrador",
                        titleColor = titleColor,
                        subtitleColor = subtitleColor,
                        pressedBg = pressedBg,
                        iconBoxBg = adminIconBg,
                        testTag = "menu_admin_panel",
                        onClick = {
                            onDismiss()
                            onOpenAdminPanel()
                        },
                        leadingIcon = {
                            HomeMenuShieldIcon(
                                tint = adminIconTint,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeOverflowMenuRow(
    title: String,
    subtitle: String,
    titleColor: Color,
    subtitleColor: Color,
    pressedBg: Color,
    iconBoxBg: Color,
    testTag: String,
    onClick: () -> Unit,
    leadingIcon: @Composable () -> Unit,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isPressed) pressedBg else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(iconBoxBg),
            contentAlignment = Alignment.Center
        ) {
            leadingIcon()
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = InterFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 19.sp
                ),
                color = titleColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = InterFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    lineHeight = 16.sp
                ),
                color = subtitleColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (trailingContent != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailingContent()
        }
    }
}

@Composable
private fun HomeMenuPersonIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(
            width = 1.8.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        val w = size.width
        val h = size.height

        // Head circle
        drawCircle(
            color = tint,
            radius = w * 0.17f,
            center = Offset(w * 0.5f, h * 0.31f),
            style = stroke
        )

        // Shoulders arc
        val shoulders = Path().apply {
            moveTo(w * 0.18f, h * 0.84f)
            cubicTo(
                w * 0.18f, h * 0.62f,
                w * 0.34f, h * 0.56f,
                w * 0.50f, h * 0.56f
            )
            cubicTo(
                w * 0.66f, h * 0.56f,
                w * 0.82f, h * 0.62f,
                w * 0.82f, h * 0.84f
            )
        }
        drawPath(path = shoulders, color = tint, style = stroke)
    }
}

@Composable
private fun HomeMenuSwapArrowsIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(
            width = 1.8.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        val w = size.width
        val h = size.height

        // Top arrow pointing right
        val topArrow = Path().apply {
            moveTo(w * 0.18f, h * 0.36f)
            lineTo(w * 0.82f, h * 0.36f)
            moveTo(w * 0.64f, h * 0.19f)
            lineTo(w * 0.82f, h * 0.36f)
            lineTo(w * 0.64f, h * 0.53f)
        }
        drawPath(path = topArrow, color = tint, style = stroke)

        // Bottom arrow pointing left
        val bottomArrow = Path().apply {
            moveTo(w * 0.82f, h * 0.68f)
            lineTo(w * 0.18f, h * 0.68f)
            moveTo(w * 0.36f, h * 0.51f)
            lineTo(w * 0.18f, h * 0.68f)
            lineTo(w * 0.36f, h * 0.85f)
        }
        drawPath(path = bottomArrow, color = tint, style = stroke)
    }
}

@Composable
private fun HomeMenuGearIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(
            width = 1.8.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        // Inner circle
        drawCircle(
            color = tint,
            radius = w * 0.16f,
            center = Offset(cx, cy),
            style = stroke
        )

        // Clean 8-segment outer ring with rounded caps (matches reference gear silhouette)
        val outerRadius = w * 0.35f
        val arcRect = Rect(
            left = cx - outerRadius,
            top = cy - outerRadius,
            right = cx + outerRadius,
            bottom = cy + outerRadius
        )
        for (i in 0 until 8) {
            val startDeg = i * 45f - 10f
            drawArc(
                color = tint,
                startAngle = startDeg,
                sweepAngle = 22f,
                useCenter = false,
                topLeft = arcRect.topLeft,
                size = arcRect.size,
                style = stroke
            )
        }

        // Subtle cardinal/diagonal ticks for gear definition
        val innerTick = w * 0.34f
        val outerTick = w * 0.41f
        for (i in 0 until 8) {
            val rad = (i * 45.0) * PI / 180.0
            val cosA = cos(rad).toFloat()
            val sinA = sin(rad).toFloat()
            drawLine(
                color = tint,
                start = Offset(cx + cosA * innerTick, cy + sinA * innerTick),
                end = Offset(cx + cosA * outerTick, cy + sinA * outerTick),
                strokeWidth = 1.8.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun HomeMenuShieldIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(
            width = 1.8.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        val w = size.width
        val h = size.height

        val shield = Path().apply {
            moveTo(w * 0.50f, h * 0.14f)
            lineTo(w * 0.81f, h * 0.26f)
            lineTo(w * 0.81f, h * 0.51f)
            cubicTo(
                w * 0.81f, h * 0.69f,
                w * 0.67f, h * 0.81f,
                w * 0.50f, h * 0.88f
            )
            cubicTo(
                w * 0.33f, h * 0.81f,
                w * 0.19f, h * 0.69f,
                w * 0.19f, h * 0.51f
            )
            lineTo(w * 0.19f, h * 0.26f)
            close()
        }
        drawPath(path = shield, color = tint, style = stroke)
    }
}

@Composable
private fun HomeMenuLockIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(
            width = 1.65.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        val w = size.width
        val h = size.height

        // Lock body
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.18f, h * 0.46f),
            size = Size(w * 0.64f, h * 0.42f),
            cornerRadius = CornerRadius(w * 0.12f, w * 0.12f),
            style = stroke
        )

        // Lock shackle
        val shackle = Path().apply {
            moveTo(w * 0.32f, h * 0.46f)
            lineTo(w * 0.32f, h * 0.32f)
            cubicTo(
                w * 0.32f, h * 0.17f,
                w * 0.68f, h * 0.17f,
                w * 0.68f, h * 0.32f
            )
            lineTo(w * 0.68f, h * 0.46f)
        }
        drawPath(path = shackle, color = tint, style = stroke)
    }
}
