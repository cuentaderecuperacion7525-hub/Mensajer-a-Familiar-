package com.example.ui.components

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.FamilyMemberEntity
import com.example.ui.theme.InterFontFamily
import com.example.util.StatusMusicTrack
import com.example.util.StatusStickerCatalog
import com.example.util.StatusStickerKind
import com.example.util.TimeFormatters
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

private val StudioSheetDark = Color(0xFF0E1219)
private val StudioSheetCard = Color(0xFF151B21)
private val StudioBlue = Color(0xFF2F7FE0)
private val StudioGreen = Color(0xFF25D366)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatusStickerPickerSheet(
    onPickSticker: (kind: StatusStickerKind, value: String) -> Unit,
    onDismiss: () -> Unit
) {
    var activeTab by remember { mutableIntStateOf(0) } // 0 = Emojis, 1 = Útiles
    var searchQuery by remember { mutableStateOf("") }

    BackHandler(enabled = true) { onDismiss() }

    val filteredCategories = remember(searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            StatusStickerCatalog.categories
        } else {
            StatusStickerCatalog.categories.mapNotNull { cat ->
                val hits = cat.emojis.filter { (emoji, keywords) ->
                    emoji.contains(q) || keywords.contains(q) || cat.title.lowercase().contains(q)
                }
                if (hits.isEmpty()) null else cat.copy(emojis = hits)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(55f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.58f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
        )

        Surface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = StudioSheetDark,
            contentColor = Color.White,
            shadowElevation = 18.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.76f)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .pointerInput(Unit) { detectTapGestures { } }
                .testTag("status_sticker_picker_sheet")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                // Header with handle & close
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Stickers",
                        fontFamily = InterFontFamily,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("sticker_sheet_close")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Cerrar stickers",
                            tint = Color.White
                        )
                    }
                }

                // Tabs: Emojis / Útiles
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val tabs = listOf("Emojis" to "sticker_tab_emojis", "Útiles" to "sticker_tab_useful")
                    tabs.forEachIndexed { idx, (label, tag) ->
                        val selected = activeTab == idx
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (selected) StudioBlue.copy(alpha = 0.22f) else StudioSheetCard,
                            border = BorderStroke(
                                1.dp,
                                if (selected) StudioBlue else Color.White.copy(alpha = 0.14f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 44.dp)
                                .clip(RoundedCornerShape(50))
                                .clickable { activeTab = idx }
                                .testTag(tag)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = label,
                                    fontFamily = InterFontFamily,
                                    fontSize = 14.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (selected) Color(0xFF93C5FD) else Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }

                if (activeTab == 0) {
                    // Search bar for Emojis
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = StudioSheetCard,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .heightIn(min = 46.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "Buscar emoji",
                                tint = Color.White.copy(alpha = 0.65f),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Buscar emoji…",
                                        fontFamily = InterFontFamily,
                                        fontSize = 14.5.sp,
                                        color = Color.White.copy(alpha = 0.50f)
                                    )
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        fontFamily = InterFontFamily,
                                        fontSize = 14.5.sp,
                                        color = Color.White
                                    ),
                                    cursorBrush = SolidColor(StudioBlue),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("sticker_emoji_search_input")
                                )
                            }
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Limpiar búsqueda",
                                        tint = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (filteredCategories.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No encontramos emojis para \"$searchQuery\".",
                                fontFamily = InterFontFamily,
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.65f)
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(6),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            filteredCategories.forEach { cat ->
                                item(key = "hdr_${cat.id}", span = { GridItemSpan(6) }) {
                                    Text(
                                        text = cat.title,
                                        fontFamily = InterFontFamily,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White.copy(alpha = 0.65f),
                                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                                    )
                                }
                                items(cat.emojis, key = { "${cat.id}_${it.first}" }) { (emoji, _) ->
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(StudioSheetCard)
                                            .clickable { onPickSticker(StatusStickerKind.EMOJI, emoji) }
                                            .semantics { contentDescription = "Sticker $emoji" }
                                            .testTag("sticker_emoji_$emoji"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = emoji, fontSize = 28.sp)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Tab 1: Útiles (Hora actual, Fecha de hoy, "Familia")
                    val currentTime = remember { StatusStickerCatalog.currentTimeBadgeText() }
                    val currentDate = remember { StatusStickerCatalog.currentDateBadgeText() }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Stickers dinámicos",
                            fontFamily = InterFontFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.68f)
                        )

                        UsefulStickerOptionCard(
                            icon = Icons.Filled.AccessTime,
                            title = "Hora actual",
                            badgePreview = "🕒 $currentTime",
                            accentColor = StudioSheetCard,
                            testTag = "sticker_useful_time",
                            onClick = { onPickSticker(StatusStickerKind.TIME_BADGE, currentTime) }
                        )

                        UsefulStickerOptionCard(
                            icon = Icons.Filled.CalendarToday,
                            title = "Fecha de hoy",
                            badgePreview = "📅 $currentDate",
                            accentColor = StudioSheetCard,
                            testTag = "sticker_useful_date",
                            onClick = { onPickSticker(StatusStickerKind.DATE_BADGE, currentDate) }
                        )

                        UsefulStickerOptionCard(
                            icon = Icons.Filled.FamilyRestroom,
                            title = "Etiqueta Familia",
                            badgePreview = "👨‍👩‍👧‍👦 Familia",
                            accentColor = StudioBlue,
                            testTag = "sticker_useful_family",
                            onClick = { onPickSticker(StatusStickerKind.FAMILY_BADGE, "Familia") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UsefulStickerOptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    badgePreview: String,
    accentColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = StudioSheetCard,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = title,
                    fontFamily = InterFontFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }

            Surface(
                shape = RoundedCornerShape(50),
                color = accentColor,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
            ) {
                Text(
                    text = badgePreview,
                    fontFamily = InterFontFamily,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
fun StatusMusicTrimmerSheet(
    track: StatusMusicTrack,
    onConfirm: (StatusMusicTrack) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val totalDurationSec = track.durationSec.coerceIn(10, 90)
    val maxWindowSec = totalDurationSec.coerceAtMost(30)
    var trimStartSec by remember(track.trackId) {
        mutableIntStateOf(track.trimStartSec.coerceIn(0, (totalDurationSec - 5).coerceAtLeast(0)))
    }
    var trimLenSec by remember(track.trackId) {
        mutableIntStateOf(track.trimDurationSec.coerceIn(5, maxWindowSec))
    }

    // Clamp window inside total duration
    LaunchedEffect(trimStartSec, trimLenSec) {
        if (trimStartSec + trimLenSec > totalDurationSec) {
            trimStartSec = (totalDurationSec - trimLenSec).coerceAtLeast(0)
        }
    }

    var mediaPlayer by remember(track.previewUrl) { mutableStateOf<MediaPlayer?>(null) }
    var isPrepared by remember(track.previewUrl) { mutableStateOf(false) }
    var isPlaying by remember(track.previewUrl) { mutableStateOf(false) }
    var currentPosSec by remember(track.trackId) { mutableFloatStateOf(trimStartSec.toFloat()) }
    var waveWidthPx by remember { mutableFloatStateOf(1f) }

    val barHeights = remember(track.trackId) {
        val seed = abs(track.trackId.hashCode()) % 100
        List(44) { idx ->
            val v = sin((idx + seed) * 0.48) * 0.32 + sin((idx * 1.7 + seed) * 0.25) * 0.24
            (0.46f + v.toFloat()).coerceIn(0.18f, 1.0f)
        }
    }

    DisposableEffect(track.previewUrl) {
        val mp = MediaPlayer()
        mediaPlayer = mp
        runCatching {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            mp.setDataSource(track.previewUrl)
            mp.isLooping = false
            mp.setOnPreparedListener { ready ->
                if (mediaPlayer === ready) {
                    isPrepared = true
                    runCatching { ready.seekTo(trimStartSec * 1000) }
                }
            }
            mp.setOnErrorListener { _, _, _ -> true }
            mp.prepareAsync()
        }.onFailure {
            runCatching { mp.release() }
            mediaPlayer = null
        }
        onDispose {
            val current = mediaPlayer
            mediaPlayer = null
            if (current != null) {
                runCatching { if (current.isPlaying) current.stop() }
                runCatching { current.reset() }
                runCatching { current.release() }
            }
        }
    }

    // Keep preview looping inside [trimStartSec, trimStartSec + trimLenSec]
    LaunchedEffect(isPlaying, isPrepared, trimStartSec, trimLenSec) {
        if (!isPlaying || !isPrepared) return@LaunchedEffect
        val mp = mediaPlayer ?: return@LaunchedEffect
        val startMs = trimStartSec * 1000
        val endMs = (trimStartSec + trimLenSec).coerceAtMost(totalDurationSec) * 1000
        runCatching {
            if (mp.currentPosition < startMs || mp.currentPosition >= endMs) {
                mp.seekTo(startMs)
            }
            if (!mp.isPlaying) mp.start()
        }
        while (isPlaying) {
            runCatching {
                val posMs = mp.currentPosition
                if (posMs >= endMs) {
                    mp.seekTo(startMs)
                    currentPosSec = trimStartSec.toFloat()
                } else {
                    currentPosSec = posMs / 1000f
                }
            }
            delay(80L)
        }
    }

    BackHandler(enabled = true) { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(58f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
        )

        Surface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = StudioSheetDark,
            contentColor = Color.White,
            shadowElevation = 20.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .pointerInput(Unit) { detectTapGestures { } }
                .testTag("status_music_trimmer_sheet")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("music_trimmer_cancel")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Cancelar recorte",
                            tint = Color.White
                        )
                    }
                    Text(
                        text = "Recortar fragmento",
                        fontFamily = InterFontFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Button(
                        onClick = {
                            onConfirm(
                                track.copy(
                                    trimStartSec = trimStartSec,
                                    trimDurationSec = trimLenSec
                                )
                            )
                        },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StudioBlue,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 44.dp)
                            .testTag("music_trimmer_confirm")
                    ) {
                        Text(
                            text = "Listo",
                            fontFamily = InterFontFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Track header + preview Play button
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = StudioSheetCard,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1E293B)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (track.artworkUrl.isNotBlank()) {
                                val req = remember(track.artworkUrl) {
                                    ImageRequest.Builder(context)
                                        .data(track.artworkUrl)
                                        .crossfade(false)
                                        .build()
                                }
                                AsyncImage(
                                    model = req,
                                    contentDescription = "Portada",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.MusicNote,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                fontFamily = InterFontFamily,
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track.artist,
                                fontFamily = InterFontFamily,
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.68f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = {
                                val mp = mediaPlayer
                                if (isPlaying) {
                                    isPlaying = false
                                    runCatching { if (mp?.isPlaying == true) mp.pause() }
                                } else {
                                    isPlaying = true
                                    if (isPrepared && mp != null) {
                                        runCatching {
                                            mp.seekTo(trimStartSec * 1000)
                                            mp.start()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(StudioGreen)
                                .testTag("music_trimmer_play_button")
                        ) {
                            if (!isPrepared && isPlaying) {
                                CircularProgressIndicator(
                                    color = StudioSheetDark,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (isPlaying) "Pausar fragmento" else "Probar fragmento",
                                    tint = StudioSheetDark,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Waveform & selectable window (drag horizontally to move fragment start)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Inicio: ${TimeFormatters.formatDuration(trimStartSec)}",
                        fontFamily = InterFontFamily,
                        fontSize = 12.5.sp,
                        color = Color.White.copy(alpha = 0.80f)
                    )
                    Text(
                        text = "Fragmento: ${trimLenSec} s (máx. 30 s)",
                        fontFamily = InterFontFamily,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = StudioGreen
                    )
                    Text(
                        text = "Fin: ${TimeFormatters.formatDuration((trimStartSec + trimLenSec).coerceAtMost(totalDurationSec))}",
                        fontFamily = InterFontFamily,
                        fontSize = 12.5.sp,
                        color = Color.White.copy(alpha = 0.80f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(78.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(StudioSheetCard)
                        .onSizeChanged { waveWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                        .pointerInput(totalDurationSec, trimLenSec) {
                            detectHorizontalDragGestures { _, dragAmount ->
                                val maxStart = (totalDurationSec - trimLenSec).coerceAtLeast(0)
                                if (maxStart > 0) {
                                    val deltaSec = (dragAmount / waveWidthPx) * totalDurationSec.toFloat()
                                    val nextStart = (trimStartSec + deltaSec.roundToInt()).coerceIn(0, maxStart)
                                    trimStartSec = nextStart
                                    if (isPrepared) {
                                        runCatching { mediaPlayer?.seekTo(nextStart * 1000) }
                                    }
                                }
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val startFrac = trimStartSec.toFloat() / totalDurationSec.toFloat()
                        val endFrac = (trimStartSec + trimLenSec).coerceAtMost(totalDurationSec).toFloat() / totalDurationSec.toFloat()
                        val winLeft = startFrac * w
                        val winRight = endFrac * w

                        // Highlight selected window
                        drawRoundRect(
                            color = StudioGreen.copy(alpha = 0.22f),
                            topLeft = Offset(winLeft, 0f),
                            size = Size((winRight - winLeft).coerceAtLeast(8f), h),
                            cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                        )

                        // Waveform bars
                        val count = barHeights.size
                        val slotW = w / count.toFloat()
                        val barW = (slotW * 0.56f).coerceAtLeast(3f)
                        barHeights.forEachIndexed { idx, amp ->
                            val frac = (idx + 0.5f) / count.toFloat()
                            val inSelection = frac in startFrac..endFrac
                            val bh = (h * amp).coerceAtLeast(8f)
                            val top = (h - bh) / 2f
                            drawRoundRect(
                                color = if (inSelection) StudioGreen else Color.White.copy(alpha = 0.26f),
                                topLeft = Offset(idx * slotW + (slotW - barW) / 2f, top),
                                size = Size(barW, bh),
                                cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                            )
                        }

                        // Playback cursor while previewing
                        if (isPlaying) {
                            val playFrac = (currentPosSec / totalDurationSec.toFloat()).coerceIn(0f, 1f)
                            val px = playFrac * w
                            drawLine(
                                color = Color.White,
                                start = Offset(px, 0f),
                                end = Offset(px, h),
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Fine range controls: Start offset + Duration (5..30s)
                if (totalDurationSec > trimLenSec) {
                    Text(
                        text = "Desplazar inicio",
                        fontFamily = InterFontFamily,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.72f)
                    )
                    Slider(
                        value = trimStartSec.toFloat(),
                        onValueChange = { next ->
                            val clamped = next.roundToInt().coerceIn(0, (totalDurationSec - trimLenSec).coerceAtLeast(0))
                            trimStartSec = clamped
                            if (isPrepared) {
                                runCatching { mediaPlayer?.seekTo(clamped * 1000) }
                            }
                        },
                        valueRange = 0f..(totalDurationSec - trimLenSec).coerceAtLeast(1).toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = StudioBlue,
                            inactiveTrackColor = Color.White.copy(alpha = 0.20f)
                        ),
                        modifier = Modifier.testTag("music_trimmer_start_slider")
                    )
                }

                Text(
                    text = "Duración del fragmento (${trimLenSec} s)",
                    fontFamily = InterFontFamily,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.72f)
                )
                Slider(
                    value = trimLenSec.toFloat(),
                    onValueChange = { next ->
                        val nextLen = next.roundToInt().coerceIn(5, maxWindowSec)
                        trimLenSec = nextLen
                        val maxStart = (totalDurationSec - nextLen).coerceAtLeast(0)
                        if (trimStartSec > maxStart) trimStartSec = maxStart
                    },
                    valueRange = 5f..maxWindowSec.toFloat().coerceAtLeast(6f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = StudioGreen,
                        inactiveTrackColor = Color.White.copy(alpha = 0.20f)
                    ),
                    modifier = Modifier.testTag("music_trimmer_duration_slider")
                )
            }
        }
    }
}

@Composable
fun StatusAudiencePickerSheet(
    allMembers: List<FamilyMemberEntity>,
    activeMemberId: String,
    selectedAudienceIds: List<String>?,
    onApplyAudience: (List<String>?) -> Unit,
    onDismiss: () -> Unit
) {
    val otherMembers = remember(allMembers, activeMemberId) {
        allMembers.filter { it.id != activeMemberId }
    }
    var useSpecificPeople by remember(selectedAudienceIds) {
        mutableStateOf(selectedAudienceIds != null)
    }
    var checkedIds by remember(selectedAudienceIds, otherMembers) {
        mutableStateOf(
            selectedAudienceIds?.toSet() ?: otherMembers.map { it.id }.toSet()
        )
    }

    BackHandler(enabled = true) { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(56f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.60f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
        )

        Surface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = StudioSheetDark,
            contentColor = Color.White,
            shadowElevation = 20.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.68f)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .pointerInput(Unit) { detectTapGestures { } }
                .testTag("status_audience_picker_sheet")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "¿Quién puede ver este estado?",
                        fontFamily = InterFontFamily,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Cerrar audiencia",
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Option 1: Toda la familia
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (!useSpecificPeople) StudioBlue.copy(alpha = 0.16f) else StudioSheetCard,
                    border = BorderStroke(
                        1.dp,
                        if (!useSpecificPeople) StudioBlue else Color.White.copy(alpha = 0.10f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { useSpecificPeople = false }
                        .testTag("audience_option_all_family")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !useSpecificPeople,
                            onClick = { useSpecificPeople = false },
                            colors = RadioButtonDefaults.colors(selectedColor = StudioBlue)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Filled.Group,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Toda la familia",
                                fontFamily = InterFontFamily,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                            Text(
                                text = "Todos los integrantes de tu familia podrán verlo",
                                fontFamily = InterFontFamily,
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.65f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Option 2: Personas concretas
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (useSpecificPeople) StudioBlue.copy(alpha = 0.16f) else StudioSheetCard,
                    border = BorderStroke(
                        1.dp,
                        if (useSpecificPeople) StudioBlue else Color.White.copy(alpha = 0.10f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { useSpecificPeople = true }
                        .testTag("audience_option_specific")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = useSpecificPeople,
                            onClick = { useSpecificPeople = true },
                            colors = RadioButtonDefaults.colors(selectedColor = StudioBlue)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Personas concretas",
                                fontFamily = InterFontFamily,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                            Text(
                                text = "Elige exactamente quién verá este estado",
                                fontFamily = InterFontFamily,
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.65f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (otherMembers.isEmpty()) {
                        item {
                            Text(
                                text = "Aún no hay otros familiares registrados en tu grupo.",
                                fontFamily = InterFontFamily,
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.60f),
                                modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp)
                            )
                        }
                    } else if (useSpecificPeople) {
                        items(otherMembers, key = { it.id }) { member ->
                            val checked = checkedIds.contains(member.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(StudioSheetCard)
                                    .clickable {
                                        checkedIds = if (checked) checkedIds - member.id else checkedIds + member.id
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .testTag("audience_member_row_${member.id}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MemberAvatar(
                                    emoji = member.avatarEmoji,
                                    colorHex = member.avatarColorHex,
                                    photoData = member.avatarPhotoData,
                                    nameForInitials = member.name,
                                    size = 36.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = member.name,
                                        fontFamily = InterFontFamily,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                    )
                                    if (member.roleLabel.isNotBlank()) {
                                        Text(
                                            text = member.roleLabel,
                                            fontFamily = InterFontFamily,
                                            fontSize = 12.sp,
                                            color = Color.White.copy(alpha = 0.62f)
                                        )
                                    }
                                }
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { isChecked ->
                                        checkedIds = if (isChecked) checkedIds + member.id else checkedIds - member.id
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = StudioBlue,
                                        uncheckedColor = Color.White.copy(alpha = 0.55f),
                                        checkmarkColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val canConfirm = !useSpecificPeople || checkedIds.isNotEmpty() || otherMembers.isEmpty()
                Button(
                    onClick = {
                        if (!useSpecificPeople) {
                            onApplyAudience(null)
                        } else {
                            onApplyAudience(checkedIds.toList())
                        }
                    },
                    enabled = canConfirm,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StudioBlue,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("audience_apply_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (useSpecificPeople) {
                            "Aplicar (${checkedIds.size} selec.)"
                        } else {
                            "Listo · Toda la familia"
                        },
                        fontFamily = InterFontFamily,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
