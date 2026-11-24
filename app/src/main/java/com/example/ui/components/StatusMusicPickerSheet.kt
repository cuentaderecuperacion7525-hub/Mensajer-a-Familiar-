package com.example.ui.components

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.InterFontFamily
import com.example.util.MusicBrowseCategory
import com.example.util.StatusMusicCatalog
import com.example.util.StatusMusicTrack
import kotlinx.coroutines.delay

private val SheetBgDark = Color(0xFF0E1219)
private val SheetCardDark = Color(0xFF171C26)
private val SheetHighlightCardBg = Color(0xFF2B3D50)
private val ActiveChipBg = Color(0xFF143D2B)
private val ActiveChipBorder = Color(0xFF25D366)
private val ActiveChipText = Color(0xFF4ADE80)
private val FavoriteGold = Color(0xFFFBBF24)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusMusicPickerSheet(
    selectedTrack: StatusMusicTrack?,
    favoriteTracks: List<StatusMusicTrack>,
    onToggleFavorite: (StatusMusicTrack) -> Unit,
    onSelectTrack: (StatusMusicTrack) -> Unit,
    onDismiss: () -> Unit,
    searchCatalog: suspend (String) -> Result<List<StatusMusicTrack>> = StatusMusicCatalog::searchTracks
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val keyboardController = LocalSoftwareKeyboardController.current

    var searchQuery by remember { mutableStateOf("") }
    var activeCategory by remember { mutableStateOf(MusicBrowseCategory.SUGGESTIONS) }
    var tracks by remember { mutableStateOf<List<StatusMusicTrack>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var retryNonce by remember { mutableIntStateOf(0) }

    // Audio preview state
    var previewPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var previewingTrackId by remember { mutableStateOf<String?>(null) }
    var isBufferingPreview by remember { mutableStateOf(false) }
    var previewError by remember { mutableStateOf<String?>(null) }

    fun stopAndReleasePreview() {
        val current = previewPlayer
        previewPlayer = null
        previewingTrackId = null
        isBufferingPreview = false
        if (current != null) {
            runCatching {
                if (current.isPlaying) current.stop()
            }
            runCatching { current.reset() }
            runCatching { current.release() }
        }
    }

    fun toggleTrackPreview(track: StatusMusicTrack) {
        previewError = null
        if (previewingTrackId == track.trackId) {
            stopAndReleasePreview()
            return
        }
        stopAndReleasePreview()
        previewingTrackId = track.trackId
        isBufferingPreview = true
        val mp = MediaPlayer()
        previewPlayer = mp
        runCatching {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            mp.setDataSource(track.previewUrl)
            mp.isLooping = true
            mp.setOnPreparedListener { readyMp ->
                if (previewPlayer === readyMp && previewingTrackId == track.trackId) {
                    isBufferingPreview = false
                    runCatching { readyMp.start() }
                } else {
                    runCatching { readyMp.release() }
                }
            }
            mp.setOnErrorListener { failedMp, _, _ ->
                if (previewPlayer === failedMp) {
                    stopAndReleasePreview()
                    previewError = "No se pudo reproducir la vista previa de esta canción."
                }
                true
            }
            mp.prepareAsync()
        }.onFailure {
            stopAndReleasePreview()
            previewError = "Error al conectar con el audio en línea."
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            stopAndReleasePreview()
        }
    }

    LaunchedEffect(previewError) {
        if (previewError != null) {
            delay(2800L)
            previewError = null
        }
    }

    // Load online catalog or local favorites
    LaunchedEffect(searchQuery, activeCategory, retryNonce, favoriteTracks) {
        val trimmed = searchQuery.trim()
        if (activeCategory == MusicBrowseCategory.FAVORITES && trimmed.isEmpty()) {
            tracks = favoriteTracks
            isLoading = false
            loadError = null
            return@LaunchedEffect
        }

        if (trimmed.isNotEmpty()) {
            delay(360L)
        }
        val queryToRun = trimmed.ifBlank { activeCategory.defaultQuery }
        isLoading = true
        loadError = null
        val res = searchCatalog(queryToRun)
        res.onSuccess { list ->
            tracks = list
            isLoading = false
            loadError = if (list.isEmpty()) "No encontramos canciones para \"$queryToRun\"." else null
        }.onFailure { err ->
            isLoading = false
            tracks = emptyList()
            loadError = err.localizedMessage?.takeIf { it.isNotBlank() }
                ?: "No se pudo cargar música de internet. Revisa tu conexión."
        }
    }

    val favoriteIds = remember(favoriteTracks) { favoriteTracks.map { it.trackId }.toSet() }
    val showFeaturedCarousel = searchQuery.isBlank() &&
        activeCategory == MusicBrowseCategory.SUGGESTIONS &&
        tracks.size >= 4
    val featuredTracks = remember(tracks, showFeaturedCarousel) {
        if (showFeaturedCarousel) tracks.take(4) else emptyList()
    }
    val listTracks = remember(tracks, showFeaturedCarousel) {
        if (showFeaturedCarousel) tracks.drop(4) else tracks
    }

    ModalBottomSheet(
        onDismissRequest = {
            stopAndReleasePreview()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = SheetBgDark,
        contentColor = Color.White,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 8.dp)
                    .width(38.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.28f))
            )
        },
        modifier = Modifier.testTag("status_music_picker_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .navigationBarsPadding()
        ) {
            // Search Bar Pill ("Buscar canciones")
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = SheetCardDark,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .heightIn(min = 48.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Buscar",
                        tint = Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.size(21.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Buscar canciones",
                                fontFamily = InterFontFamily,
                                fontSize = 15.5.sp,
                                color = Color.White.copy(alpha = 0.56f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontFamily = InterFontFamily,
                                fontSize = 15.5.sp,
                                color = Color.White
                            ),
                            cursorBrush = SolidColor(ActiveChipText),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                keyboardController?.hide()
                                retryNonce++
                            }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("status_music_search_input")
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("status_music_search_clear")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Limpiar búsqueda",
                                tint = Color.White.copy(alpha = 0.72f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Category Chips Row
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(MusicBrowseCategory.entries, key = { it.name }) { category ->
                    val isSelected = activeCategory == category && searchQuery.isBlank()
                    val label = if (category == MusicBrowseCategory.FAVORITES && favoriteTracks.isNotEmpty()) {
                        "${category.label} (${favoriteTracks.size})"
                    } else {
                        category.label
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isSelected) ActiveChipBg else SheetCardDark,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isSelected) ActiveChipBorder.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.14f)
                        ),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 36.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable {
                                searchQuery = ""
                                activeCategory = category
                                keyboardController?.hide()
                            }
                            .testTag("music_category_${category.name.lowercase()}")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = label,
                                fontFamily = InterFontFamily,
                                fontSize = 13.5.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (isSelected) ActiveChipText else Color.White.copy(alpha = 0.88f),
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            previewError?.let { err ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF3E1F24),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = err,
                        fontFamily = InterFontFamily,
                        fontSize = 12.5.sp,
                        color = Color(0xFFFCA5A5),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            when {
                isLoading && tracks.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                color = ActiveChipText,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(34.dp)
                            )
                            Text(
                                text = "Buscando canciones en línea…",
                                fontFamily = InterFontFamily,
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.72f)
                            )
                        }
                    }
                }

                activeCategory == MusicBrowseCategory.FAVORITES && searchQuery.isBlank() && favoriteTracks.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.StarBorder,
                                contentDescription = null,
                                tint = FavoriteGold.copy(alpha = 0.8f),
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "Sin canciones favoritas",
                                fontFamily = InterFontFamily,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = "Toca la estrella ⭐ en cualquier canción para guardarla aquí y usarla rápido.",
                                fontFamily = InterFontFamily,
                                fontSize = 13.5.sp,
                                lineHeight = 19.sp,
                                color = Color.White.copy(alpha = 0.65f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                loadError != null && tracks.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MusicNote,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(38.dp)
                            )
                            Text(
                                text = loadError ?: "No se pudo cargar el catálogo.",
                                fontFamily = InterFontFamily,
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.78f),
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = { retryNonce++ },
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ActiveChipBg,
                                    contentColor = ActiveChipText
                                ),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 44.dp)
                                    .testTag("music_retry_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Reintentar",
                                    fontFamily = InterFontFamily,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("status_music_results_list")
                    ) {
                        if (featuredTracks.isNotEmpty()) {
                            item(key = "featured_carousel") {
                                FeaturedMusicCarousel(
                                    tracks = featuredTracks,
                                    favoriteIds = favoriteIds,
                                    selectedTrackId = selectedTrack?.trackId,
                                    previewingTrackId = previewingTrackId,
                                    isBufferingPreview = isBufferingPreview,
                                    onTogglePreview = ::toggleTrackPreview,
                                    onToggleFavorite = onToggleFavorite,
                                    onSelectTrack = { chosen ->
                                        stopAndReleasePreview()
                                        onSelectTrack(chosen)
                                    }
                                )
                            }
                        }

                        items(listTracks, key = { it.trackId }) { track ->
                            MusicTrackRowItem(
                                track = track,
                                isFavorite = favoriteIds.contains(track.trackId),
                                isSelected = selectedTrack?.trackId == track.trackId,
                                isPreviewing = previewingTrackId == track.trackId,
                                isBuffering = previewingTrackId == track.trackId && isBufferingPreview,
                                onTogglePreview = { toggleTrackPreview(track) },
                                onToggleFavorite = { onToggleFavorite(track) },
                                onSelect = {
                                    stopAndReleasePreview()
                                    onSelectTrack(track)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeaturedMusicCarousel(
    tracks: List<StatusMusicTrack>,
    favoriteIds: Set<String>,
    selectedTrackId: String?,
    previewingTrackId: String?,
    isBufferingPreview: Boolean,
    onTogglePreview: (StatusMusicTrack) -> Unit,
    onToggleFavorite: (StatusMusicTrack) -> Unit,
    onSelectTrack: (StatusMusicTrack) -> Unit
) {
    val rowState = rememberLazyListState()
    val activeDot by remember {
        derivedStateOf { rowState.firstVisibleItemIndex.coerceIn(0, (tracks.size - 1).coerceAtLeast(0)) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 10.dp)
    ) {
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(tracks, key = { "feat_${it.trackId}" }) { track ->
                val isPreviewing = previewingTrackId == track.trackId
                val isBuffering = isPreviewing && isBufferingPreview
                val isFavorite = favoriteIds.contains(track.trackId)
                val isSelected = selectedTrackId == track.trackId

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = SheetHighlightCardBg,
                    border = if (isSelected) {
                        BorderStroke(1.5.dp, ActiveChipBorder)
                    } else {
                        BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                    },
                    modifier = Modifier
                        .width(296.dp)
                        .clickable { onTogglePreview(track) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MusicArtworkPlayThumb(
                            artworkUrl = track.artworkUrl,
                            isPreviewing = isPreviewing,
                            isBuffering = isBuffering,
                            sizeDp = 56.dp,
                            onClick = { onTogglePreview(track) }
                        )
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
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = track.artist,
                                fontFamily = InterFontFamily,
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.72f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { onToggleFavorite(track) },
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("music_favorite_${track.trackId}")
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = if (isFavorite) "Quitar de favoritas" else "Guardar en favoritas",
                                tint = if (isFavorite) FavoriteGold else Color.White.copy(alpha = 0.86f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) ActiveChipBorder else Color(0xFF1F2D3B))
                                .clickable { onSelectTrack(track) }
                                .testTag("music_select_${track.trackId}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Usar canción",
                                tint = if (isSelected) SheetBgDark else Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }
        }

        if (tracks.size > 1) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tracks.indices.forEach { idx ->
                    val active = idx == activeDot
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (active) 7.dp else 5.dp)
                            .clip(CircleShape)
                            .background(
                                if (active) ActiveChipText else Color.White.copy(alpha = 0.28f)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun MusicTrackRowItem(
    track: StatusMusicTrack,
    isFavorite: Boolean,
    isSelected: Boolean,
    isPreviewing: Boolean,
    isBuffering: Boolean,
    onTogglePreview: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTogglePreview() }
            .padding(horizontal = 16.dp, vertical = 9.dp)
            .testTag("music_row_${track.trackId}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MusicArtworkPlayThumb(
            artworkUrl = track.artworkUrl,
            isPreviewing = isPreviewing,
            isBuffering = isBuffering,
            sizeDp = 52.dp,
            onClick = onTogglePreview
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                fontFamily = InterFontFamily,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Medium,
                color = if (isPreviewing) ActiveChipText else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            val durationLabel = "0:${track.durationSec.coerceIn(5, 59).toString().padStart(2, '0')}"
            Text(
                text = "${track.artist} · $durationLabel",
                fontFamily = InterFontFamily,
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.65f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier
                .size(44.dp)
                .testTag("music_favorite_${track.trackId}")
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = if (isFavorite) "Quitar de favoritas" else "Guardar en favoritas",
                tint = if (isFavorite) FavoriteGold else Color.White.copy(alpha = 0.78f),
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (isSelected) ActiveChipBorder else SheetCardDark)
                .border(
                    width = 1.dp,
                    color = if (isSelected) ActiveChipBorder else Color.White.copy(alpha = 0.08f),
                    shape = CircleShape
                )
                .clickable { onSelect() }
                .testTag("music_select_${track.trackId}"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Usar canción",
                tint = if (isSelected) SheetBgDark else Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun MusicArtworkPlayThumb(
    artworkUrl: String,
    isPreviewing: Boolean,
    isBuffering: Boolean,
    sizeDp: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .size(sizeDp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF1F3A52), Color(0xFF132233))
                )
            )
            .border(
                width = if (isPreviewing) 1.5.dp else 0.dp,
                color = if (isPreviewing) ActiveChipBorder else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (artworkUrl.isNotBlank()) {
            val req = remember(artworkUrl) {
                ImageRequest.Builder(context)
                    .data(artworkUrl)
                    .crossfade(true)
                    .build()
            }
            AsyncImage(
                model = req,
                contentDescription = "Portada de canción",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.65f),
                modifier = Modifier.size(22.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (isPreviewing) 0.45f else 0.30f)),
            contentAlignment = Alignment.Center
        ) {
            if (isBuffering) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Icon(
                    imageVector = if (isPreviewing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPreviewing) "Pausar vista previa" else "Escuchar vista previa",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
