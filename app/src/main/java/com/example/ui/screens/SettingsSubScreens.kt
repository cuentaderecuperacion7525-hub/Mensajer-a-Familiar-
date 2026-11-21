package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.local.AppFontScale
import com.example.data.local.AppThemeMode
import com.example.ui.theme.InterFontFamily
import kotlin.math.roundToInt

private val SettingsAccentBlue = Color(0xFF1A5FD4)
private val SettingsInkPrimary = Color(0xFF1B1F2A)
private val SettingsInkSecondary = Color(0xFF5B6270)
private val SettingsHairlineLight = Color(0xFFECEFF4)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSubPageScaffold(
    title: String,
    isDarkTheme: Boolean,
    backTestTag: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    val surfaceBg = if (isDarkTheme) MaterialTheme.colorScheme.background else Color.White
    val textPrimary = if (isDarkTheme) MaterialTheme.colorScheme.onSurface else SettingsInkPrimary

    Scaffold(
        containerColor = surfaceBg,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 20.sp
                        ),
                        color = textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag(backTestTag)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Volver",
                            tint = textPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = surfaceBg,
                    titleContentColor = textPrimary,
                    navigationIconContentColor = textPrimary
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
            ) {
                content()
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun AppearanceSettingsScreen(
    selectedMode: AppThemeMode,
    isDarkTheme: Boolean,
    onSelectMode: (AppThemeMode) -> Unit,
    onBack: () -> Unit
) {
    val accentBlue = if (isDarkTheme) MaterialTheme.colorScheme.primary else SettingsAccentBlue
    val textPrimary = if (isDarkTheme) MaterialTheme.colorScheme.onSurface else SettingsInkPrimary
    val dividerColor = if (isDarkTheme) MaterialTheme.colorScheme.outline.copy(alpha = 0.18f) else SettingsHairlineLight

    val options = listOf(
        Triple(AppThemeMode.LIGHT, "Claro", "appearance_option_light"),
        Triple(AppThemeMode.DARK, "Oscuro", "appearance_option_dark"),
        Triple(AppThemeMode.SYSTEM, "Automático (igual que el teléfono)", "appearance_option_system")
    )

    SettingsSubPageScaffold(
        title = "Apariencia",
        isDarkTheme = isDarkTheme,
        backTestTag = "appearance_back_button",
        onBack = onBack
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        options.forEachIndexed { index, (mode, label, tag) ->
            val isSelected = selectedMode == mode
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clickable { onSelectMode(mode) }
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .testTag(tag),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = InterFontFamily,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                    ),
                    color = if (isSelected) accentBlue else textPrimary,
                    modifier = Modifier.weight(1f)
                )
                if (isSelected) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Seleccionado",
                        tint = accentBlue,
                        modifier = Modifier
                            .size(20.dp)
                            .testTag("${tag}_selected")
                    )
                }
            }
            if (index < options.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    thickness = 0.75.dp,
                    color = dividerColor
                )
            }
        }
    }
}

@Composable
fun FontSizeSettingsScreen(
    selectedScale: AppFontScale,
    isDarkTheme: Boolean,
    onSelectScale: (AppFontScale) -> Unit,
    onBack: () -> Unit
) {
    val accentBlue = if (isDarkTheme) MaterialTheme.colorScheme.primary else SettingsAccentBlue
    val textPrimary = if (isDarkTheme) MaterialTheme.colorScheme.onSurface else SettingsInkPrimary
    val textSecondary = if (isDarkTheme) MaterialTheme.colorScheme.onSurfaceVariant else SettingsInkSecondary
    val previewCardBg = if (isDarkTheme) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f) else Color(0xFFF5F7FB)
    val bubbleBg = if (isDarkTheme) MaterialTheme.colorScheme.primaryContainer else Color(0xFFE4EDFC)

    SettingsSubPageScaffold(
        title = "Tamaño de letra",
        isDarkTheme = isDarkTheme,
        backTestTag = "font_size_back_button",
        onBack = onBack
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Live preview card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = previewCardBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("font_size_preview_card")
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "VISTA PREVIA",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = InterFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.7.sp
                        ),
                        color = accentBlue
                    )
                    Surface(
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = 4.dp,
                            bottomEnd = 16.dp
                        ),
                        color = bubbleBg
                    ) {
                        Text(
                            text = "Así se verá el texto de tus mensajes.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = InterFontFamily,
                                fontSize = 15.sp,
                                lineHeight = 22.sp
                            ),
                            color = textPrimary,
                            modifier = Modifier
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                .testTag("font_size_preview_text")
                        )
                    }
                }
            }

            // 3-step Slider with small "A" and large "A"
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "A",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = InterFontFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = textSecondary
                    )
                    Slider(
                        value = selectedScale.stepIndex.toFloat(),
                        onValueChange = { raw ->
                            val snapped = AppFontScale.fromStep(raw.roundToInt())
                            if (snapped != selectedScale) {
                                onSelectScale(snapped)
                            }
                        },
                        valueRange = 0f..2f,
                        steps = 1,
                        colors = SliderDefaults.colors(
                            thumbColor = accentBlue,
                            activeTrackColor = accentBlue,
                            inactiveTrackColor = accentBlue.copy(alpha = 0.22f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("font_size_slider")
                    )
                    Text(
                        text = "A",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = InterFontFamily,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = textPrimary
                    )
                }

                // Tappable step labels aligned under the 3 steps
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        AppFontScale.SMALL to "font_size_option_small",
                        AppFontScale.NORMAL to "font_size_option_normal",
                        AppFontScale.LARGE to "font_size_option_large"
                    ).forEach { (scale, tag) ->
                        val isSelected = selectedScale == scale
                        Box(
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clickable { onSelectScale(scale) }
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                                .testTag(tag),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = scale.label,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = InterFontFamily,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                ),
                                color = if (isSelected) accentBlue else textSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WhatsNewScreen(
    installedVersionName: String = BuildConfig.VERSION_NAME,
    versionReleaseNotes: String = "",
    isDarkTheme: Boolean,
    onBack: () -> Unit
) {
    val accentBlue = if (isDarkTheme) MaterialTheme.colorScheme.primary else SettingsAccentBlue
    val textPrimary = if (isDarkTheme) MaterialTheme.colorScheme.onSurface else SettingsInkPrimary
    val textSecondary = if (isDarkTheme) MaterialTheme.colorScheme.onSurfaceVariant else SettingsInkSecondary
    val cardBg = if (isDarkTheme) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f) else Color(0xFFF5F7FB)

    val changeItems = versionReleaseNotes
        .lines()
        .map { line ->
            line.trim()
                .removePrefix("-")
                .removePrefix("•")
                .removePrefix("*")
                .trim()
        }
        .filter { it.isNotBlank() }

    SettingsSubPageScaffold(
        title = "Novedades de esta versión",
        isDarkTheme = isDarkTheme,
        backTestTag = "whats_new_back_button",
        onBack = onBack
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .testTag("whats_new_screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Versión $installedVersionName",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = InterFontFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = accentBlue,
                modifier = Modifier.testTag("whats_new_version_text")
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = cardBg,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (changeItems.isEmpty()) {
                    Text(
                        text = "No hay novedades para esta versión.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = InterFontFamily,
                            fontSize = 14.sp
                        ),
                        color = textSecondary,
                        modifier = Modifier
                            .padding(18.dp)
                            .testTag("whats_new_empty_text")
                    )
                } else {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        changeItems.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = accentBlue
                                )
                                Text(
                                    text = item,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = InterFontFamily,
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp
                                    ),
                                    color = textPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class LicenseEntry(
    val title: String,
    val detail: String,
    val testTag: String? = null
)

@Composable
fun LicensesScreen(
    isDarkTheme: Boolean,
    onBack: () -> Unit
) {
    val textPrimary = if (isDarkTheme) MaterialTheme.colorScheme.onSurface else SettingsInkPrimary
    val textSecondary = if (isDarkTheme) MaterialTheme.colorScheme.onSurfaceVariant else SettingsInkSecondary
    val dividerColor = if (isDarkTheme) MaterialTheme.colorScheme.outline.copy(alpha = 0.18f) else SettingsHairlineLight

    val entries = listOf(
        LicenseEntry(
            title = "Emojis animados: Noto Emoji Animation de Google, licencia CC BY 4.0",
            detail = "Creative Commons Attribution 4.0 International (CC BY 4.0)",
            testTag = "license_noto_emoji"
        ),
        LicenseEntry(
            title = "Material Icons (Core y Extended) — Google / AndroidX",
            detail = "Licencia Apache 2.0",
            testTag = "license_material_icons"
        ),
        LicenseEntry(
            title = "Tipografía Inter — Rasmus Andersson",
            detail = "SIL Open Font License 1.1"
        ),
        LicenseEntry(
            title = "Tipografía Plus Jakarta Sans — Tokotype",
            detail = "SIL Open Font License 1.1"
        ),
        LicenseEntry(
            title = "Jetpack Compose y librerías AndroidX (Core, Activity, Lifecycle, Navigation, DataStore, WorkManager, Credentials)",
            detail = "Licencia Apache 2.0"
        ),
        LicenseEntry(
            title = "Kotlin Coroutines — JetBrains",
            detail = "Licencia Apache 2.0"
        ),
        LicenseEntry(
            title = "Firebase Android SDK (Authentication, Cloud Firestore, Cloud Messaging) — Google",
            detail = "Licencia Apache 2.0"
        ),
        LicenseEntry(
            title = "Google Play Services (Location e Identity)",
            detail = "Google APIs Terms of Service"
        ),
        LicenseEntry(
            title = "Coil (Carga de imágenes)",
            detail = "Licencia Apache 2.0"
        ),
        LicenseEntry(
            title = "OkHttp — Square",
            detail = "Licencia Apache 2.0"
        ),
        LicenseEntry(
            title = "Stream WebRTC Android",
            detail = "Licencia Apache 2.0 y BSD-3-Clause (WebRTC)"
        )
    )

    SettingsSubPageScaffold(
        title = "Licencias",
        isDarkTheme = isDarkTheme,
        backTestTag = "licenses_back_button",
        onBack = onBack
    ) {
        Spacer(modifier = Modifier.height(6.dp))
        entries.forEachIndexed { index, entry ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .then(if (entry.testTag != null) Modifier.testTag(entry.testTag) else Modifier),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = InterFontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 20.sp
                    ),
                    color = textPrimary
                )
                Text(
                    text = entry.detail,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = InterFontFamily,
                        fontSize = 12.sp
                    ),
                    color = textSecondary
                )
            }
            if (index < entries.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    thickness = 0.75.dp,
                    color = dividerColor
                )
            }
        }
    }
}
