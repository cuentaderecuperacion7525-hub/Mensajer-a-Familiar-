package com.example.ui.components

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.FamilyMemberEntity
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.LocalDarkTheme
import com.example.ui.viewmodel.StatusComposerMode
import com.example.ui.viewmodel.StatusPublishRequest
import com.example.ui.viewmodel.StatusPublishUiState
import com.example.util.CameraCaptureHelper
import com.example.util.StatusMusicCatalog
import com.example.util.StatusMusicTrack
import com.example.util.StatusOverlayBundle
import com.example.util.StatusStudioDraftItem
import com.example.util.TimeFormatters
import com.example.util.VideoMediaHelper
import com.example.util.parseHexColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private const val MAX_TEXT_STATUS_CHARS = 280
private const val MAX_MEDIA_CAPTION_CHARS = 200

val StatusComposerTextColors = listOf(
    "#0E7C66",
    "#1A5FD4",
    "#6B4FD8",
    "#B0306A",
    "#C94521",
    "#3A4150"
)

private val GalleryDarkBg = Color(0xFF0E1219)
private val GalleryTileBg = Color(0xFF171C26)
private val DialogBluePrimary = Color(0xFF1A73E8)
private val SoftRedDark = Color(0xFFF28B82)
private val SoftRedLight = Color(0xFFD93025)
private val AmberWarnColor = Color(0xFFE0A030)

private data class LocalGalleryMediaItem(
    val uri: Uri,
    val dateAddedSec: Long,
    val durationSec: Int = 0,
    val sizeBytes: Long = 0L,
    val isOverLimit: Boolean = false,
    val bucketName: String = "Recientes"
)

private fun isPermissionGranted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun hasFullPhotoAccess(context: Context): Boolean = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
        isPermissionGranted(context, Manifest.permission.READ_MEDIA_IMAGES)
    else ->
        isPermissionGranted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun hasFullVideoAccess(context: Context): Boolean = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
        isPermissionGranted(context, Manifest.permission.READ_MEDIA_VIDEO)
    else ->
        isPermissionGranted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun hasPhotoGalleryAccess(context: Context): Boolean = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
        hasFullPhotoAccess(context) ||
            isPermissionGranted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    else ->
        hasFullPhotoAccess(context)
}

private fun hasVideoGalleryAccess(context: Context): Boolean = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
        hasFullVideoAccess(context) ||
            isPermissionGranted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    else ->
        hasFullVideoAccess(context)
}

private fun hasPartialMediaAccess(context: Context, isVideo: Boolean): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
    val hasFull = if (isVideo) hasFullVideoAccess(context) else hasFullPhotoAccess(context)
    return !hasFull &&
        isPermissionGranted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
}

private fun requiredGalleryPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    else ->
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun primaryGalleryPermission(isVideo: Boolean): String = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
        if (isVideo) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_MEDIA_IMAGES
    else ->
        Manifest.permission.READ_EXTERNAL_STORAGE
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatusComposerScreen(
    publishState: StatusPublishUiState,
    onPublish: (StatusPublishRequest) -> Unit,
    onCancelPublish: () -> Unit,
    onDismissPublishError: () -> Unit,
    onClose: () -> Unit,
    favoriteMusicTracks: List<StatusMusicTrack> = emptyList(),
    onToggleFavoriteMusicTrack: (StatusMusicTrack) -> Unit = {},
    musicSearchCatalog: suspend (String) -> Result<List<StatusMusicTrack>> = StatusMusicCatalog::searchTracks,
    allMembers: List<FamilyMemberEntity> = emptyList(),
    activeMemberId: String = "",
    onPublishBatch: ((List<StatusPublishRequest>) -> Unit)? = null
) {
    val context = LocalContext.current
    val view = LocalView.current
    val parentDarkTheme = LocalDarkTheme.current
    val isImeVisible = WindowInsets.isImeVisible
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    // Ensure status/nav bar icons remain crisp white over colored & dark fullscreen backgrounds
    if (!view.isInEditMode) {
        DisposableEffect(parentDarkTheme) {
            val window = (view.context as? Activity)?.window
            val controller = window?.let { WindowCompat.getInsetsController(it, view) }
            controller?.isAppearanceLightStatusBars = false
            controller?.isAppearanceLightNavigationBars = false
            onDispose {
                controller?.isAppearanceLightStatusBars = !parentDarkTheme
                controller?.isAppearanceLightNavigationBars = !parentDarkTheme
            }
        }
    }

    var currentMode by rememberSaveable { mutableStateOf(StatusComposerMode.TEXT) }
    var statusText by rememberSaveable { mutableStateOf("") }
    var selectedHex by rememberSaveable { mutableStateOf(StatusComposerTextColors.first()) }

    var mediaDrafts by remember { mutableStateOf<List<StatusStudioDraftItem>>(emptyList()) }
    var activeDraftIndex by remember { mutableIntStateOf(0) }
    var appendMediaOnReturn by remember { mutableStateOf(false) }

    var selectedMusicTrack by remember { mutableStateOf<StatusMusicTrack?>(null) }
    var showMusicPickerSheet by remember { mutableStateOf(false) }
    var isNonVideoMusicPlaying by remember { mutableStateOf(true) }

    var pendingCameraPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraVideoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraType by remember { mutableIntStateOf(1) }

    var sessionPhotoItems by remember { mutableStateOf<List<LocalGalleryMediaItem>>(emptyList()) }
    var sessionVideoItems by remember { mutableStateOf<List<LocalGalleryMediaItem>>(emptyList()) }
    var mediaStorePhotos by remember { mutableStateOf<List<LocalGalleryMediaItem>>(emptyList()) }
    var mediaStoreVideos by remember { mutableStateOf<List<LocalGalleryMediaItem>>(emptyList()) }

    var photoAccessGranted by remember { mutableStateOf(hasPhotoGalleryAccess(context)) }
    var videoAccessGranted by remember { mutableStateOf(hasVideoGalleryAccess(context)) }
    var partialPhotoAccess by remember { mutableStateOf(hasPartialMediaAccess(context, isVideo = false)) }
    var partialVideoAccess by remember { mutableStateOf(hasPartialMediaAccess(context, isVideo = true)) }
    var hasPromptedPhotoPermission by rememberSaveable { mutableStateOf(false) }
    var hasPromptedVideoPermission by rememberSaveable { mutableStateOf(false) }
    var manualPermissionIsVideo by remember { mutableStateOf<Boolean?>(null) }
    var hadRationaleBeforeManualRequest by remember { mutableStateOf(false) }
    var galleryRefreshTick by remember { mutableIntStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                photoAccessGranted = hasPhotoGalleryAccess(context)
                videoAccessGranted = hasVideoGalleryAccess(context)
                partialPhotoAccess = hasPartialMediaAccess(context, isVideo = false)
                partialVideoAccess = hasPartialMediaAccess(context, isVideo = true)
                galleryRefreshTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val galleryPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val prevIsVideo = manualPermissionIsVideo
        manualPermissionIsVideo = null
        photoAccessGranted = hasPhotoGalleryAccess(context)
        videoAccessGranted = hasVideoGalleryAccess(context)
        partialPhotoAccess = hasPartialMediaAccess(context, isVideo = false)
        partialVideoAccess = hasPartialMediaAccess(context, isVideo = true)
        galleryRefreshTick++

        if (prevIsVideo != null) {
            val grantedNow = if (prevIsVideo) videoAccessGranted else photoAccessGranted
            if (!grantedNow) {
                val activity = context as? Activity
                val primaryPerm = primaryGalleryPermission(prevIsVideo)
                val showRationaleNow = activity != null &&
                    ActivityCompat.shouldShowRequestPermissionRationale(activity, primaryPerm)
                if (activity != null && !hadRationaleBeforeManualRequest && !showRationaleNow) {
                    runCatching {
                        activity.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            )
                        )
                    }
                }
            }
        }
    }

    fun requestGalleryAccess(isVideo: Boolean, userInitiated: Boolean) {
        if (userInitiated) {
            val activity = context as? Activity
            val primaryPerm = primaryGalleryPermission(isVideo)
            hadRationaleBeforeManualRequest = activity != null &&
                ActivityCompat.shouldShowRequestPermissionRationale(activity, primaryPerm)
            manualPermissionIsVideo = isVideo
        } else {
            manualPermissionIsVideo = null
        }
        galleryPermissionLauncher.launch(requiredGalleryPermissions())
    }

    var showDiscardDialog by remember { mutableStateOf(false) }
    var inlineWarningMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(inlineWarningMsg) {
        if (inlineWarningMsg != null) {
            delay(2800L)
            inlineWarningMsg = null
        }
    }

    // Automatically request combined photo+video gallery permission & load device camera roll
    LaunchedEffect(currentMode, photoAccessGranted, videoAccessGranted, galleryRefreshTick) {
        photoAccessGranted = hasPhotoGalleryAccess(context)
        videoAccessGranted = hasVideoGalleryAccess(context)
        partialPhotoAccess = hasPartialMediaAccess(context, isVideo = false)
        partialVideoAccess = hasPartialMediaAccess(context, isVideo = true)

        when (currentMode) {
            StatusComposerMode.PHOTO -> {
                val loadedPhotos = loadAppAccessiblePhotos(context)
                mediaStorePhotos = loadedPhotos
                val needsPrompt = !photoAccessGranted ||
                    (!hasFullPhotoAccess(context) && loadedPhotos.isEmpty())
                if (needsPrompt && !hasPromptedPhotoPermission) {
                    hasPromptedPhotoPermission = true
                    requestGalleryAccess(isVideo = false, userInitiated = false)
                }
            }
            StatusComposerMode.VIDEO -> {
                val loadedVideos = loadAppAccessibleVideos(context)
                mediaStoreVideos = loadedVideos
                val needsPrompt = !videoAccessGranted ||
                    (!hasFullVideoAccess(context) && loadedVideos.isEmpty())
                if (needsPrompt && !hasPromptedVideoPermission) {
                    hasPromptedVideoPermission = true
                    requestGalleryAccess(isVideo = true, userInitiated = false)
                }
            }
            StatusComposerMode.TEXT -> Unit
        }
    }

    fun addOrOpenPhotoDraft(uri: Uri, append: Boolean) {
        val sharedAudience = mediaDrafts.firstOrNull()?.overlays?.audienceMemberIds
        val entry = StatusStudioDraftItem(
            id = "draft_${UUID.randomUUID().toString().take(8)}",
            uri = uri,
            isVideo = false,
            overlays = StatusOverlayBundle(audienceMemberIds = sharedAudience)
        )
        if (append && mediaDrafts.isNotEmpty()) {
            val nextList = mediaDrafts + entry
            mediaDrafts = nextList
            activeDraftIndex = nextList.lastIndex
        } else {
            mediaDrafts = listOf(entry)
            activeDraftIndex = 0
        }
    }

    fun selectVideoUriWithCheck(uri: Uri, knownSizeBytes: Long = -1L, append: Boolean = false) {
        coroutineScope.launch {
            val info = VideoMediaHelper.extractVideoPreview(context, uri)
            val effectiveSize = if (info.sizeBytes > 0L) info.sizeBytes else knownSizeBytes
            if (effectiveSize > VideoMediaHelper.MAX_VIDEO_BYTES) {
                inlineWarningMsg = "El video supera el límite de 5 MB. Elige uno más corto."
                return@launch
            }
            val entry = LocalGalleryMediaItem(
                uri = uri,
                dateAddedSec = System.currentTimeMillis() / 1000L,
                durationSec = info.durationSec,
                sizeBytes = effectiveSize,
                isOverLimit = false
            )
            sessionVideoItems = (listOf(entry) + sessionVideoItems.filterNot { it.uri == uri })
            val sharedAudience = mediaDrafts.firstOrNull()?.overlays?.audienceMemberIds
            val draft = StatusStudioDraftItem(
                id = "draft_${UUID.randomUUID().toString().take(8)}",
                uri = uri,
                isVideo = true,
                videoPreviewInfo = info,
                overlays = StatusOverlayBundle(audienceMemberIds = sharedAudience)
            )
            if (append && mediaDrafts.isNotEmpty()) {
                val nextList = mediaDrafts + draft
                mediaDrafts = nextList
                activeDraftIndex = nextList.lastIndex
            } else {
                mediaDrafts = listOf(draft)
                activeDraftIndex = 0
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            val shouldAppend = appendMediaOnReturn
            appendMediaOnReturn = false
            if (uri != null) {
                val entry = LocalGalleryMediaItem(uri, System.currentTimeMillis() / 1000L)
                sessionPhotoItems = (listOf(entry) + sessionPhotoItems.filterNot { it.uri == uri })
                addOrOpenPhotoDraft(uri, append = shouldAppend)
            }
        }
    )

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            val shouldAppend = appendMediaOnReturn
            appendMediaOnReturn = false
            if (uri != null) {
                selectVideoUriWithCheck(uri, append = shouldAppend)
            }
        }
    )

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            val shouldAppend = appendMediaOnReturn
            appendMediaOnReturn = false
            val captured = pendingCameraPhotoUri
            if (success && captured != null) {
                val entry = LocalGalleryMediaItem(captured, System.currentTimeMillis() / 1000L)
                sessionPhotoItems = (listOf(entry) + sessionPhotoItems.filterNot { it.uri == captured })
                addOrOpenPhotoDraft(captured, append = shouldAppend)
            }
        }
    )

    val captureVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo(),
        onResult = { success ->
            val shouldAppend = appendMediaOnReturn
            appendMediaOnReturn = false
            val captured = pendingCameraVideoUri
            if (success && captured != null) {
                selectVideoUriWithCheck(captured, append = shouldAppend)
            }
        }
    )

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                if (pendingCameraType == 2) {
                    val uri = CameraCaptureHelper.createTempVideoUri(context)
                    if (uri != null) {
                        pendingCameraVideoUri = uri
                        captureVideoLauncher.launch(uri)
                    } else {
                        appendMediaOnReturn = false
                    }
                } else {
                    val uri = CameraCaptureHelper.createTempPhotoUri(context)
                    if (uri != null) {
                        pendingCameraPhotoUri = uri
                        takePictureLauncher.launch(uri)
                    } else {
                        appendMediaOnReturn = false
                    }
                }
            } else {
                appendMediaOnReturn = false
                inlineWarningMsg = "Permiso de cámara requerido para capturar."
            }
        }
    )

    fun launchSystemPhotoCamera() {
        pendingCameraType = 1
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            val uri = CameraCaptureHelper.createTempPhotoUri(context)
            if (uri != null) {
                pendingCameraPhotoUri = uri
                takePictureLauncher.launch(uri)
            } else {
                appendMediaOnReturn = false
            }
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun launchSystemVideoCamera() {
        pendingCameraType = 2
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            val uri = CameraCaptureHelper.createTempVideoUri(context)
            if (uri != null) {
                pendingCameraVideoUri = uri
                captureVideoLauncher.launch(uri)
            } else {
                appendMediaOnReturn = false
            }
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val isInMediaStudio = mediaDrafts.isNotEmpty()
    val isPublishing = publishState is StatusPublishUiState.Publishing
    val isErrorDialog = publishState is StatusPublishUiState.Error

    // Preview attached music automatically for Text composer (Media Studio manages its own trimmed player)
    var nonVideoMusicPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    var nonVideoPreparedUrl by remember { mutableStateOf<String?>(null) }
    val activeNonVideoMusicUrl = if (
        !isInMediaStudio &&
        currentMode == StatusComposerMode.TEXT &&
        !showMusicPickerSheet &&
        !isPublishing
    ) {
        selectedMusicTrack?.previewUrl
    } else {
        null
    }

    LaunchedEffect(activeNonVideoMusicUrl) {
        val prev = nonVideoMusicPlayer
        if (activeNonVideoMusicUrl.isNullOrBlank()) {
            nonVideoMusicPlayer = null
            nonVideoPreparedUrl = null
            if (prev != null) {
                runCatching { if (prev.isPlaying) prev.stop() }
                runCatching { prev.reset() }
                runCatching { prev.release() }
            }
            return@LaunchedEffect
        }
        if (nonVideoPreparedUrl == activeNonVideoMusicUrl && prev != null) {
            return@LaunchedEffect
        }
        if (prev != null) {
            runCatching { if (prev.isPlaying) prev.stop() }
            runCatching { prev.reset() }
            runCatching { prev.release() }
        }
        nonVideoPreparedUrl = activeNonVideoMusicUrl
        val mp = android.media.MediaPlayer()
        nonVideoMusicPlayer = mp
        runCatching {
            mp.setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            mp.setDataSource(activeNonVideoMusicUrl)
            mp.isLooping = true
            mp.setOnPreparedListener { readyMp ->
                if (nonVideoMusicPlayer === readyMp && isNonVideoMusicPlaying) {
                    runCatching { readyMp.start() }
                }
            }
            mp.setOnErrorListener { _, _, _ -> true }
            mp.prepareAsync()
        }.onFailure {
            runCatching { mp.release() }
            if (nonVideoMusicPlayer === mp) nonVideoMusicPlayer = null
        }
    }

    LaunchedEffect(isNonVideoMusicPlaying, nonVideoMusicPlayer, activeNonVideoMusicUrl) {
        val mp = nonVideoMusicPlayer ?: return@LaunchedEffect
        if (activeNonVideoMusicUrl.isNullOrBlank()) return@LaunchedEffect
        runCatching {
            if (isNonVideoMusicPlaying) {
                if (!mp.isPlaying) mp.start()
            } else {
                if (mp.isPlaying) mp.pause()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val mp = nonVideoMusicPlayer
            nonVideoMusicPlayer = null
            if (mp != null) {
                runCatching { if (mp.isPlaying) mp.stop() }
                runCatching { mp.reset() }
                runCatching { mp.release() }
            }
        }
    }

    fun hasUnsavedContent(): Boolean =
        statusText.isNotBlank() ||
            mediaDrafts.any { it.hasEdits() } ||
            mediaDrafts.size > 1 ||
            selectedMusicTrack != null

    fun triggerPublishTextCurrent() {
        if (isPublishing) return
        keyboardController?.hide()
        focusManager.clearFocus()
        isNonVideoMusicPlaying = false
        if (currentMode == StatusComposerMode.TEXT && statusText.isNotBlank()) {
            onPublish(
                StatusPublishRequest(
                    mode = StatusComposerMode.TEXT,
                    text = statusText,
                    backgroundHex = selectedHex,
                    musicTrack = selectedMusicTrack
                )
            )
        }
    }

    fun triggerPublishMediaDrafts() {
        if (isPublishing || mediaDrafts.isEmpty()) return
        keyboardController?.hide()
        focusManager.clearFocus()
        isNonVideoMusicPlaying = false
        val requests = mediaDrafts.map { draft ->
            if (draft.isVideo) {
                StatusPublishRequest(
                    mode = StatusComposerMode.VIDEO,
                    videoUri = draft.uri,
                    videoCaption = draft.caption,
                    musicTrack = draft.musicTrack,
                    overlayBundle = draft.overlays,
                    visibleToMemberIds = draft.overlays.audienceMemberIds
                )
            } else {
                StatusPublishRequest(
                    mode = StatusComposerMode.PHOTO,
                    photoUri = draft.uri,
                    photoCaption = draft.caption,
                    musicTrack = draft.musicTrack,
                    overlayBundle = draft.overlays,
                    visibleToMemberIds = draft.overlays.audienceMemberIds
                )
            }
        }
        if (onPublishBatch != null) {
            onPublishBatch(requests)
        } else if (requests.isNotEmpty()) {
            onPublish(requests.first())
        }
    }

    fun handleBackOrClose(fromCloseButton: Boolean) {
        if (isPublishing) return
        if (isErrorDialog) {
            if (isInMediaStudio) {
                onDismissPublishError()
            } else {
                return
            }
        }
        if (isImeVisible && !fromCloseButton) {
            keyboardController?.hide()
            focusManager.clearFocus()
            return
        }
        keyboardController?.hide()
        focusManager.clearFocus()
        when {
            isInMediaStudio -> {
                if (mediaDrafts.any { it.hasEdits() } || mediaDrafts.size > 1) {
                    showDiscardDialog = true
                } else {
                    mediaDrafts = emptyList()
                    activeDraftIndex = 0
                }
            }
            hasUnsavedContent() -> showDiscardDialog = true
            else -> onClose()
        }
    }

    BackHandler(enabled = !showMusicPickerSheet && !isInMediaStudio) {
        when {
            showDiscardDialog -> showDiscardDialog = false
            isPublishing -> Unit
            isErrorDialog -> onDismissPublishError()
            else -> handleBackOrClose(fromCloseButton = false)
        }
    }

    val combinedPhotos = remember(sessionPhotoItems, mediaStorePhotos) {
        (sessionPhotoItems + mediaStorePhotos)
            .distinctBy { it.uri.toString() }
            .sortedByDescending { it.dateAddedSec }
    }
    val combinedVideos = remember(sessionVideoItems, mediaStoreVideos) {
        (sessionVideoItems + mediaStoreVideos)
            .distinctBy { it.uri.toString() }
            .sortedByDescending { it.dateAddedSec }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(35f)
            .pointerInput(Unit) { detectTapGestures { } }
            .testTag("status_composer_screen")
    ) {
        when {
            isInMediaStudio -> {
                val safeIdx = activeDraftIndex.coerceIn(0, mediaDrafts.lastIndex)
                StatusMediaStudioScreen(
                    drafts = mediaDrafts,
                    activeIndex = safeIdx,
                    activeDraft = mediaDrafts[safeIdx],
                    publishState = publishState,
                    allMembers = allMembers,
                    activeMemberId = activeMemberId,
                    favoriteMusicTracks = favoriteMusicTracks,
                    onToggleFavoriteMusicTrack = onToggleFavoriteMusicTrack,
                    musicSearchCatalog = musicSearchCatalog,
                    onSelectDraftIndex = { idx ->
                        if (idx in mediaDrafts.indices) activeDraftIndex = idx
                    },
                    onRemoveDraftAt = { idx ->
                        val nextList = mediaDrafts.filterIndexed { i, _ -> i != idx }
                        mediaDrafts = nextList
                        activeDraftIndex = if (nextList.isEmpty()) 0 else safeIdx.coerceIn(0, nextList.lastIndex)
                    },
                    onUpdateActiveDraft = { transform ->
                        val targetIdx = activeDraftIndex.coerceIn(0, mediaDrafts.lastIndex)
                        val prevAudience = mediaDrafts[targetIdx].overlays.audienceMemberIds
                        val updatedItem = transform(mediaDrafts[targetIdx])
                        val nextAudience = updatedItem.overlays.audienceMemberIds
                        mediaDrafts = mediaDrafts.mapIndexed { i, item ->
                            val base = if (i == targetIdx) updatedItem else item
                            if (prevAudience != nextAudience) {
                                base.copy(overlays = base.overlays.copy(audienceMemberIds = nextAudience))
                            } else {
                                base
                            }
                        }
                    },
                    onRequestAddMedia = { isVideo, fromCamera ->
                        appendMediaOnReturn = true
                        when {
                            isVideo && fromCamera -> launchSystemVideoCamera()
                            isVideo && !fromCamera -> videoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                            !isVideo && fromCamera -> launchSystemPhotoCamera()
                            else -> photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    },
                    onRequestClose = { handleBackOrClose(fromCloseButton = true) },
                    onDismissPublishError = onDismissPublishError,
                    onPublishDrafts = ::triggerPublishMediaDrafts
                )
            }

            currentMode == StatusComposerMode.TEXT -> {
                TextStatusComposerPage(
                    text = statusText,
                    selectedHex = selectedHex,
                    isImeVisible = isImeVisible,
                    isPublishing = isPublishing,
                    selectedMusic = selectedMusicTrack,
                    isMusicPlaying = isNonVideoMusicPlaying,
                    onToggleMusicPlayback = { isNonVideoMusicPlaying = !isNonVideoMusicPlaying },
                    onOpenMusicPicker = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        showMusicPickerSheet = true
                    },
                    onRemoveMusic = { selectedMusicTrack = null },
                    onTextChange = { if (it.length <= MAX_TEXT_STATUS_CHARS) statusText = it },
                    onSelectColor = { selectedHex = it },
                    onSelectTab = { next ->
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        currentMode = next
                    },
                    onCloseClick = { handleBackOrClose(fromCloseButton = true) },
                    onPublishClick = ::triggerPublishTextCurrent
                )
            }

            else -> {
                val isVideoGallery = currentMode == StatusComposerMode.VIDEO
                MediaGalleryComposerPage(
                    isVideo = isVideoGallery,
                    items = if (isVideoGallery) combinedVideos else combinedPhotos,
                    hasGalleryPermission = if (isVideoGallery) videoAccessGranted else photoAccessGranted,
                    hasPartialAccess = if (isVideoGallery) partialVideoAccess else partialPhotoAccess,
                    onCloseClick = { handleBackOrClose(fromCloseButton = true) },
                    onRequestPermission = { requestGalleryAccess(isVideo = isVideoGallery, userInitiated = true) },
                    onOpenPicker = {
                        appendMediaOnReturn = false
                        if (isVideoGallery) {
                            videoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        } else {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    },
                    onOpenCamera = {
                        appendMediaOnReturn = false
                        if (isVideoGallery) launchSystemVideoCamera() else launchSystemPhotoCamera()
                    },
                    onItemClick = { item ->
                        if (isVideoGallery) {
                            if (item.isOverLimit) {
                                inlineWarningMsg = "El video supera el límite de 5 MB y no se puede elegir."
                            } else {
                                selectVideoUriWithCheck(item.uri, item.sizeBytes, append = false)
                            }
                        } else {
                            addOrOpenPhotoDraft(item.uri, append = false)
                        }
                    },
                    onSelectTab = { next -> currentMode = next }
                )
            }
        }

        // Brief floating notice for size limit or camera permission
        val warning = inlineWarningMsg
        if (!warning.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color(0xFF1E2532).copy(alpha = 0.94f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 92.dp)
                    .testTag("status_composer_warning_pill")
            ) {
                Text(
                    text = warning,
                    color = Color.White,
                    fontFamily = InterFontFamily,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }

        // Centered Dialogs: Discard (both modes) / Uploading & Error (Text mode only; Studio handles its own Send spinner & inline banner)
        if (showDiscardDialog) {
            BackHandler(enabled = true) { showDiscardDialog = false }
            val dialogTitle = if (isInMediaStudio) "¿Descartar el estado?" else "¿Descartar este estado?"
            val subtitle = when {
                isInMediaStudio -> "Se perderán los cambios sin publicar."
                else -> "Se perderá lo que escribiste."
            }
            StatusDiscardDialog(
                title = dialogTitle,
                subtitle = subtitle,
                onKeepEditing = { showDiscardDialog = false },
                onDiscard = {
                    showDiscardDialog = false
                    mediaDrafts = emptyList()
                    activeDraftIndex = 0
                    onClose()
                }
            )
        } else if (!isInMediaStudio && publishState is StatusPublishUiState.Publishing) {
            StatusUploadingDialog(
                mode = publishState.mode,
                progress = publishState.progress,
                onCancel = onCancelPublish
            )
        } else if (!isInMediaStudio && publishState is StatusPublishUiState.Error) {
            StatusPublishErrorDialog(
                message = publishState.message,
                onRetry = ::triggerPublishTextCurrent,
                onDismiss = onDismissPublishError
            )
        }

        if (showMusicPickerSheet && !isInMediaStudio) {
            StatusMusicPickerSheet(
                selectedTrack = selectedMusicTrack,
                favoriteTracks = favoriteMusicTracks,
                onToggleFavorite = onToggleFavoriteMusicTrack,
                onSelectTrack = { chosen ->
                    selectedMusicTrack = chosen.copy(muteOriginalVideo = false)
                    isNonVideoMusicPlaying = true
                    showMusicPickerSheet = false
                },
                onDismiss = { showMusicPickerSheet = false },
                searchCatalog = musicSearchCatalog
            )
        }
    }
}

@Composable
private fun TextStatusComposerPage(
    text: String,
    selectedHex: String,
    isImeVisible: Boolean,
    isPublishing: Boolean,
    selectedMusic: StatusMusicTrack?,
    isMusicPlaying: Boolean,
    onToggleMusicPlayback: () -> Unit,
    onOpenMusicPicker: () -> Unit,
    onRemoveMusic: () -> Unit,
    onTextChange: (String) -> Unit,
    onSelectColor: (String) -> Unit,
    onSelectTab: (StatusComposerMode) -> Unit,
    onCloseClick: () -> Unit,
    onPublishClick: () -> Unit
) {
    val bgColor = parseHexColor(selectedHex, fallback = Color(0xFF0E7C66))
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val hasText = text.isNotBlank()

    // Base font size smoothly adapts from 26sp down to 16sp as length grows
    var overflowStep by remember { mutableIntStateOf(0) }
    LaunchedEffect(text.length) {
        if (overflowStep > 0 && text.length < 40) {
            overflowStep = 0
        }
    }
    val lengthBasedSp = when {
        text.length <= 45 -> 26f
        text.length <= 90 -> 23f
        text.length <= 145 -> 20f
        text.length <= 210 -> 18f
        else -> 16f
    }
    val effectiveSp = (lengthBasedSp - overflowStep * 2f).coerceIn(16f, 26f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .imePadding()
            .navigationBarsPadding()
    ) {
        // Top Bar: X (left) + Publicar pill (right)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onCloseClick,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("status_close_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Cerrar",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatusMusicToolbarButton(
                    hasSelectedMusic = selectedMusic != null,
                    enabled = !isPublishing,
                    onClick = onOpenMusicPicker
                )

                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (hasText && !isPublishing) Color.White else Color.White.copy(alpha = 0.22f),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 44.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable(enabled = hasText && !isPublishing) { onPublishClick() }
                        .testTag("publish_status_button")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "Publicar",
                            fontFamily = InterFontFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (hasText && !isPublishing) Color(0xFF0B3D33) else Color.White.copy(alpha = 0.70f),
                            maxLines = 1
                        )
                    }
                }
            }
        }

        if (selectedMusic != null) {
            SelectedStatusMusicPill(
                track = selectedMusic,
                isVideoMode = false,
                isMusicPlaying = isMusicPlaying,
                onTogglePlayback = onToggleMusicPlayback,
                onChangeMusic = onOpenMusicPicker,
                onToggleMuteOriginalVideo = {},
                onRemoveMusic = onRemoveMusic,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // Editable centered text directly on colored background
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                }
                .padding(horizontal = 28.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            val maxLinesEstimate = ((maxHeight.value / (effectiveSp * 1.45f)).toInt()).coerceAtLeast(3)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { next ->
                        if (next.length <= MAX_TEXT_STATUS_CHARS) {
                            if (next.length < text.length && overflowStep > 0) {
                                overflowStep = (overflowStep - 1).coerceAtLeast(0)
                            }
                            onTextChange(next)
                        }
                    },
                    textStyle = TextStyle(
                        fontFamily = InterFontFamily,
                        fontSize = effectiveSp.sp,
                        lineHeight = (effectiveSp * 1.32f).sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    ),
                    cursorBrush = SolidColor(Color.White),
                    onTextLayout = { layoutResult ->
                        if ((layoutResult.hasVisualOverflow || layoutResult.lineCount > maxLinesEstimate) &&
                            effectiveSp > 16f
                        ) {
                            overflowStep += 1
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .testTag("status_text_input"),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (text.isEmpty()) {
                                Text(
                                    text = "Escribe tu estado…",
                                    fontFamily = InterFontFamily,
                                    fontSize = 26.sp,
                                    lineHeight = 34.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = 0.65f),
                                    textAlign = TextAlign.Center
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }
        }

        // Character counter N/280 above color row, right-aligned
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 4.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(
                text = "${text.length}/$MAX_TEXT_STATUS_CHARS",
                fontFamily = InterFontFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.testTag("status_text_counter")
            )
        }

        // 6 color circles
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusComposerTextColors.forEachIndexed { idx, hex ->
                val isSelected = selectedHex.equals(hex, ignoreCase = true)
                val swatchColor = parseHexColor(hex)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable { onSelectColor(hex) }
                        .testTag("status_color_option_$idx"),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(39.dp)
                                .border(2.5.dp, Color.White, CircleShape)
                                .padding(4.5.dp)
                                .clip(CircleShape)
                                .background(swatchColor)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(swatchColor)
                        )
                    }
                }
            }
        }

        // Bottom selector pill (hidden while keyboard is open)
        if (!isImeVisible) {
            Spacer(modifier = Modifier.height(6.dp))
            StatusComposerModePill(
                currentMode = StatusComposerMode.TEXT,
                containerColor = Color.Black.copy(alpha = 0.24f),
                activeTextColor = Color(0xFF0B3D33),
                onSelectTab = onSelectTab
            )
            Spacer(modifier = Modifier.height(14.dp))
        } else {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun MediaGalleryComposerPage(
    isVideo: Boolean,
    items: List<LocalGalleryMediaItem>,
    hasGalleryPermission: Boolean,
    hasPartialAccess: Boolean,
    onCloseClick: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenPicker: () -> Unit,
    onOpenCamera: () -> Unit,
    onItemClick: (LocalGalleryMediaItem) -> Unit,
    onSelectTab: (StatusComposerMode) -> Unit
) {
    var selectedBucket by rememberSaveable(isVideo) { mutableStateOf("Recientes") }
    var showAlbumMenu by remember { mutableStateOf(false) }

    val albums = remember(items, isVideo) {
        if (isVideo) {
            emptyList()
        } else {
            items
                .groupBy { it.bucketName.ifBlank { "Cámara" } }
                .map { (name, list) -> name to list.size }
                .sortedByDescending { it.second }
        }
    }

    LaunchedEffect(albums, selectedBucket) {
        if (selectedBucket != "Recientes" && albums.none { it.first == selectedBucket }) {
            selectedBucket = "Recientes"
        }
    }

    val displayedItems = remember(items, isVideo, selectedBucket) {
        if (!isVideo && selectedBucket != "Recientes") {
            items.filter { it.bucketName == selectedBucket }
        } else {
            items
        }
    }

    val showDirectCameraRoll = hasGalleryPermission && displayedItems.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GalleryDarkBg)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .navigationBarsPadding()
    ) {
        // Header: X on left, centered title ("Recientes v" / "Videos")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .heightIn(min = 48.dp)
        ) {
            IconButton(
                onClick = onCloseClick,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(48.dp)
                    .testTag("status_close_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Cerrar",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            Box(modifier = Modifier.align(Alignment.Center)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            if (!isVideo) {
                                if (items.isNotEmpty() || hasGalleryPermission) {
                                    showAlbumMenu = true
                                } else {
                                    onOpenPicker()
                                }
                            } else if (!hasGalleryPermission) {
                                onOpenPicker()
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag(if (isVideo) "status_gallery_title_videos" else "status_gallery_title_recents")
                ) {
                    Text(
                        text = if (isVideo) "Videos" else selectedBucket,
                        fontFamily = InterFontFamily,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!isVideo) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Cambiar álbum o galería",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                if (!isVideo) {
                    DropdownMenu(
                        expanded = showAlbumMenu,
                        onDismissRequest = { showAlbumMenu = false },
                        containerColor = Color(0xFF1B2230),
                        modifier = Modifier.testTag("status_album_dropdown")
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Recientes (${items.size})",
                                    fontFamily = InterFontFamily,
                                    fontSize = 14.sp,
                                    fontWeight = if (selectedBucket == "Recientes") FontWeight.SemiBold else FontWeight.Normal,
                                    color = Color.White
                                )
                            },
                            trailingIcon = {
                                if (selectedBucket == "Recientes") {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = Color(0xFF5A9BFF),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            onClick = {
                                selectedBucket = "Recientes"
                                showAlbumMenu = false
                            }
                        )

                        albums.forEach { (albumName, count) ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "$albumName ($count)",
                                        fontFamily = InterFontFamily,
                                        fontSize = 14.sp,
                                        fontWeight = if (selectedBucket == albumName) FontWeight.SemiBold else FontWeight.Normal,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                trailingIcon = {
                                    if (selectedBucket == albumName) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = Color(0xFF5A9BFF),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                onClick = {
                                    selectedBucket = albumName
                                    showAlbumMenu = false
                                }
                            )
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

                        if (hasPartialAccess) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Permitir más fotos…",
                                        fontFamily = InterFontFamily,
                                        fontSize = 14.sp,
                                        color = Color(0xFF8AB4F8)
                                    )
                                },
                                onClick = {
                                    showAlbumMenu = false
                                    onRequestPermission()
                                }
                            )
                        }

                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Selector del sistema",
                                    fontFamily = InterFontFamily,
                                    fontSize = 14.sp,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            },
                            onClick = {
                                showAlbumMenu = false
                                onOpenPicker()
                            }
                        )
                    }
                }
            }
        }

        // 3-column grid with 5dp gap & 12dp corners
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag(if (isVideo) "status_video_gallery_grid" else "status_photo_gallery_grid")
        ) {
            if (hasPartialAccess && displayedItems.isNotEmpty()) {
                item(key = "partial_access_banner", span = { GridItemSpan(3) }) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = GalleryTileBg,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                            .clickable { onRequestPermission() }
                            .testTag("status_partial_gallery_banner")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isVideo) {
                                    "Mostrando videos seleccionados"
                                } else {
                                    "Mostrando fotos seleccionadas"
                                },
                                fontFamily = InterFontFamily,
                                fontSize = 12.5.sp,
                                color = Color.White.copy(alpha = 0.78f)
                            )
                            Text(
                                text = "Permitir más",
                                fontFamily = InterFontFamily,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF8AB4F8)
                            )
                        }
                    }
                }
            }

            // Cell 0: Camera / Record
            item(key = "tile_camera") {
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(GalleryTileBg)
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                        .clickable { onOpenCamera() }
                        .testTag(if (isVideo) "status_record_video_button" else "status_take_photo_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isVideo) Icons.Outlined.Videocam else Icons.Outlined.PhotoCamera,
                            contentDescription = if (isVideo) "Grabar video" else "Tomar foto",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                        Text(
                            text = if (isVideo) "Grabar" else "Cámara",
                            fontFamily = InterFontFamily,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.72f)
                        )
                    }
                }
            }

            // Fallback System Photo Picker tile only when gallery access isn't granted or camera roll is empty
            if (!showDirectCameraRoll) {
                item(key = "tile_system_picker") {
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(GalleryTileBg)
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                            .clickable { onOpenPicker() }
                            .testTag(if (isVideo) "status_pick_video_button" else "status_pick_gallery_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PhotoLibrary,
                                contentDescription = "Elegir de galería",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = if (isVideo) "Elegir video" else "Galería",
                                fontFamily = InterFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.72f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Real thumbnails (newest first) directly alongside Cámara / Grabar
            items(displayedItems, key = { it.uri.toString() }) { item ->
                GalleryRealMediaTile(
                    item = item,
                    isVideo = isVideo,
                    onClick = { onItemClick(item) }
                )
            }

            if (!hasGalleryPermission || (hasPartialAccess && displayedItems.isEmpty())) {
                val partialEmpty = hasGalleryPermission && hasPartialAccess && displayedItems.isEmpty()
                item(key = "gallery_permission_prompt", span = { GridItemSpan(3) }) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = GalleryTileBg,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = when {
                                    partialEmpty && isVideo ->
                                        "No hay videos en la selección actual. Permite acceso completo o selecciona videos del teléfono."
                                    partialEmpty ->
                                        "No hay fotos en la selección actual. Permite acceso completo o selecciona más fotos."
                                    isVideo ->
                                        "Permite el acceso para ver los videos del teléfono directamente aquí."
                                    else ->
                                        "Permite el acceso para ver el carrete de fotos del teléfono directamente aquí."
                                },
                                fontFamily = InterFontFamily,
                                fontSize = 13.5.sp,
                                lineHeight = 19.sp,
                                color = Color.White.copy(alpha = 0.76f),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = onRequestPermission,
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DialogBluePrimary,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 44.dp)
                                    .testTag("status_request_gallery_permission_button")
                            ) {
                                Text(
                                    text = if (isVideo) "Permitir acceso a videos" else "Permitir acceso al carrete",
                                    fontFamily = InterFontFamily,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            } else if (displayedItems.isEmpty()) {
                item(key = "picker_hint", span = { GridItemSpan(3) }) {
                    Text(
                        text = if (isVideo) {
                            "No hay videos recientes. Toca «Elegir video» o «Grabar»."
                        } else {
                            "No hay fotos recientes en esta carpeta. Toca «Galería» o «Cámara»."
                        },
                        fontFamily = InterFontFamily,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = Color.White.copy(alpha = 0.56f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 28.dp)
                    )
                }
            }
        }

        // Bottom pill selector
        Spacer(modifier = Modifier.height(6.dp))
        StatusComposerModePill(
            currentMode = if (isVideo) StatusComposerMode.VIDEO else StatusComposerMode.PHOTO,
            containerColor = GalleryTileBg,
            activeTextColor = GalleryDarkBg,
            onSelectTab = onSelectTab
        )
        Spacer(modifier = Modifier.height(14.dp))
    }
}

@Composable
private fun GalleryRealMediaTile(
    item: LocalGalleryMediaItem,
    isVideo: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var videoThumbBitmap by remember(item.uri, isVideo) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(item.uri, isVideo) {
        if (isVideo) {
            videoThumbBitmap = loadVideoThumbnailBitmap(context, item.uri)
        }
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(GalleryTileBg)
            .alpha(if (item.isOverLimit) 0.42f else 1f)
            .clickable { onClick() }
    ) {
        if (isVideo) {
            val bmp = videoThumbBitmap
            if (bmp != null && !bmp.isRecycled) {
                val imgBmp = remember(bmp) { bmp.asImageBitmap() }
                Image(
                    bitmap = imgBmp,
                    contentDescription = "Miniatura de video",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Videocam,
                        contentDescription = "Video",
                        tint = Color.White.copy(alpha = 0.45f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            if (item.durationSec > 0) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.72f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                ) {
                    Text(
                        text = TimeFormatters.formatDuration(item.durationSec),
                        fontFamily = InterFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        } else {
            val photoReq = remember(item.uri) {
                ImageRequest.Builder(context)
                    .data(item.uri)
                    .size(360)
                    .crossfade(false)
                    .build()
            }
            AsyncImage(
                model = photoReq,
                contentDescription = "Miniatura de foto",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}


@Composable
private fun StatusMusicToolbarButton(
    hasSelectedMusic: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = if (hasSelectedMusic) Color(0xFF143D2B).copy(alpha = 0.92f) else Color.Black.copy(alpha = 0.34f),
        border = BorderStroke(
            width = 1.dp,
            color = if (hasSelectedMusic) Color(0xFF25D366) else Color.White.copy(alpha = 0.34f)
        ),
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled) { onClick() }
            .testTag("status_music_button")
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = if (hasSelectedMusic) "Cambiar música" else "Añadir música",
                tint = if (hasSelectedMusic) Color(0xFF4ADE80) else Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun SelectedStatusMusicPill(
    track: StatusMusicTrack,
    isVideoMode: Boolean,
    isMusicPlaying: Boolean,
    onTogglePlayback: () -> Unit,
    onChangeMusic: () -> Unit,
    onToggleMuteOriginalVideo: () -> Unit,
    onRemoveMusic: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = Color.Black.copy(alpha = 0.56f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(50))
                .clickable { onChangeMusic() }
                .testTag("status_selected_music_pill")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 5.dp, bottom = 5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E293B))
                        .clickable { onTogglePlayback() }
                        .testTag("status_selected_music_play_toggle"),
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
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isMusicPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isMusicPlaying) "Pausar música" else "Escuchar música",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = "${track.title} · ${track.artist}",
                        fontFamily = InterFontFamily,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable { onRemoveMusic() }
                        .testTag("status_music_remove_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Quitar música",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        if (isVideoMode) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.52f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { onToggleMuteOriginalVideo() }
                    .testTag("status_video_mute_toggle")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Icon(
                        imageVector = if (track.muteOriginalVideo) Icons.AutoMirrored.Filled.VolumeOff
                        else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = if (track.muteOriginalVideo) Color(0xFFFCA5A5) else Color(0xFF4ADE80),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (track.muteOriginalVideo) "Audio original silenciado" else "Audio original activo",
                        fontFamily = InterFontFamily,
                        fontSize = 11.5.sp,
                        color = Color.White.copy(alpha = 0.90f)
                    )
                }
            }
        }
    }
}


@Composable
private fun StatusComposerModePill(
    currentMode: StatusComposerMode,
    containerColor: Color,
    activeTextColor: Color,
    onSelectTab: (StatusComposerMode) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = containerColor,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 360.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tabs = listOf(
                    Triple(StatusComposerMode.TEXT, "Texto", "status_tab_text"),
                    Triple(StatusComposerMode.PHOTO, "Foto", "status_tab_photo"),
                    Triple(StatusComposerMode.VIDEO, "Video", "status_tab_video")
                )
                tabs.forEach { (mode, label, tag) ->
                    val active = currentMode == mode
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (active) Color.White else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 44.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable { onSelectTab(mode) }
                            .testTag(tag)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = label,
                                fontFamily = InterFontFamily,
                                fontSize = 14.sp,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (active) activeTextColor else Color.White.copy(alpha = 0.86f),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusDiscardDialog(
    title: String = "¿Descartar este estado?",
    subtitle: String,
    onKeepEditing: () -> Unit,
    onDiscard: () -> Unit
) {
    val isDark = LocalDarkTheme.current
    val cardBg = if (isDark) Color(0xFF1E2430) else MaterialTheme.colorScheme.surface
    val titleColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
    val bodyColor = if (isDark) Color(0xFFB6C0D1) else MaterialTheme.colorScheme.onSurfaceVariant
    val discardColor = if (isDark) SoftRedDark else SoftRedLight
    val buttonBlue = if (isDark) DialogBluePrimary else MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.58f))
            .pointerInput(Unit) { detectTapGestures { } }
            .testTag("status_discard_dialog"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = cardBg,
            tonalElevation = 0.dp,
            shadowElevation = 12.dp,
            modifier = Modifier
                .widthIn(min = 260.dp, max = 280.dp)
                .padding(vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    fontFamily = InterFontFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = subtitle,
                    fontFamily = InterFontFamily,
                    fontSize = 14.sp,
                    color = bodyColor,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = onKeepEditing,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonBlue,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 46.dp)
                        .testTag("status_discard_keep_editing")
                ) {
                    Text(
                        text = "Seguir editando",
                        fontFamily = InterFontFamily,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                TextButton(
                    onClick = onDiscard,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 44.dp)
                        .testTag("status_discard_confirm")
                ) {
                    Text(
                        text = "Descartar",
                        fontFamily = InterFontFamily,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = discardColor
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusUploadingDialog(
    mode: StatusComposerMode,
    progress: Float,
    onCancel: () -> Unit
) {
    val isDark = LocalDarkTheme.current
    val cardBg = if (isDark) Color(0xFF1E2430) else MaterialTheme.colorScheme.surface
    val titleColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
    val bodyColor = if (isDark) Color(0xFFB6C0D1) else MaterialTheme.colorScheme.onSurfaceVariant
    val cancelColor = if (isDark) SoftRedDark else SoftRedLight
    val trackColor = if (isDark) Color(0xFF2D3648) else Color(0xFFE3E6EC)
    val barColor = if (isDark) Color(0xFF5A9BFF) else DialogBluePrimary

    val title = when (mode) {
        StatusComposerMode.VIDEO -> "Subiendo video…"
        StatusComposerMode.PHOTO -> "Subiendo foto…"
        StatusComposerMode.TEXT -> "Publicando…"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.58f))
            .pointerInput(Unit) { detectTapGestures { } }
            .testTag("status_uploading_dialog"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = cardBg,
            tonalElevation = 0.dp,
            shadowElevation = 12.dp,
            modifier = Modifier
                .widthIn(min = 260.dp, max = 280.dp)
                .padding(vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    fontFamily = InterFontFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (mode == StatusComposerMode.TEXT) {
                    CircularProgressIndicator(
                        color = barColor,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 44.dp)
                            .testTag("status_upload_cancel_button")
                    ) {
                        Text(
                            text = "Cancelar",
                            fontFamily = InterFontFamily,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = cancelColor
                        )
                    }
                } else {
                    val pct = (progress * 100f).toInt().coerceIn(0, 100)
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0.02f, 1f) },
                        color = barColor,
                        trackColor = trackColor,
                        strokeCap = StrokeCap.Round,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "$pct %",
                            fontFamily = InterFontFamily,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = bodyColor
                        )
                        TextButton(
                            onClick = onCancel,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 44.dp)
                                .testTag("status_upload_cancel_button")
                        ) {
                            Text(
                                text = "Cancelar",
                                fontFamily = InterFontFamily,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = cancelColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPublishErrorDialog(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val isDark = LocalDarkTheme.current
    val cardBg = if (isDark) Color(0xFF1E2430) else MaterialTheme.colorScheme.surface
    val titleColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
    val bodyColor = if (isDark) Color(0xFFB6C0D1) else MaterialTheme.colorScheme.onSurfaceVariant
    val buttonBlue = if (isDark) DialogBluePrimary else MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.58f))
            .pointerInput(Unit) { detectTapGestures { } }
            .testTag("status_publish_error_dialog"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = cardBg,
            tonalElevation = 0.dp,
            shadowElevation = 12.dp,
            modifier = Modifier
                .widthIn(min = 260.dp, max = 280.dp)
                .padding(vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(AmberWarnColor.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(AmberWarnColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PriorityHigh,
                            contentDescription = "Advertencia",
                            tint = Color(0xFF1A1D24),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "No se pudo publicar",
                    fontFamily = InterFontFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    fontFamily = InterFontFamily,
                    fontSize = 14.sp,
                    color = bodyColor,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = onRetry,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonBlue,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 46.dp)
                        .testTag("status_publish_retry_button")
                ) {
                    Text(
                        text = "Reintentar",
                        fontFamily = InterFontFamily,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 44.dp)
                        .testTag("status_publish_error_close_button")
                ) {
                    Text(
                        text = "Cerrar",
                        fontFamily = InterFontFamily,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = bodyColor
                    )
                }
            }
        }
    }
}

private suspend fun loadAppAccessiblePhotos(context: Context): List<LocalGalleryMediaItem> =
    withContext(Dispatchers.IO) {
        val result = mutableListOf<LocalGalleryMediaItem>()
        val seen = HashSet<String>()

        // 1. Device MediaStore photos (newest first, up to 240 items, grouped by album bucket)
        runCatching {
            val imageCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.DATE_MODIFIED,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME
            )
            context.contentResolver.query(
                imageCollection,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_MODIFIED} DESC, ${MediaStore.Images.Media._ID} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dateCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
                val modCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)
                val sizeCol = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                val bucketCol = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                while (cursor.moveToNext() && result.size < 240) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(imageCollection, id)
                    val dateAdded = if (dateCol >= 0) cursor.getLong(dateCol) else 0L
                    val dateModified = if (modCol >= 0) cursor.getLong(modCol) else 0L
                    val effectiveDate = maxOf(dateAdded, dateModified)
                    val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                    val rawBucket = if (bucketCol >= 0) cursor.getString(bucketCol) else null
                    val bucketName = rawBucket?.trim().orEmpty().ifBlank { "Cámara" }
                    if (seen.add(uri.toString())) {
                        result.add(
                            LocalGalleryMediaItem(
                                uri = uri,
                                dateAddedSec = effectiveDate,
                                sizeBytes = size,
                                bucketName = bucketName
                            )
                        )
                    }
                }
            }
        }

        // 2. App's camera photo cache (recently captured photos)
        runCatching {
            val dir = File(context.cacheDir, "camera_photos")
            if (dir.exists()) {
                dir.listFiles()
                    ?.filter { it.isFile && it.length() > 0L }
                    ?.sortedByDescending { it.lastModified() }
                    ?.take(20)
                    ?.forEach { file ->
                        val uri = Uri.fromFile(file)
                        if (seen.add(uri.toString())) {
                            result.add(
                                LocalGalleryMediaItem(
                                    uri = uri,
                                    dateAddedSec = file.lastModified() / 1000L,
                                    sizeBytes = file.length(),
                                    bucketName = "Cámara"
                                )
                            )
                        }
                    }
            }
        }

        result.sortedByDescending { it.dateAddedSec }
    }

private suspend fun loadAppAccessibleVideos(context: Context): List<LocalGalleryMediaItem> =
    withContext(Dispatchers.IO) {
        val result = mutableListOf<LocalGalleryMediaItem>()
        val seen = HashSet<String>()

        runCatching {
            val videoCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.DATE_MODIFIED,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE
            )
            context.contentResolver.query(
                videoCollection,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_MODIFIED} DESC, ${MediaStore.Video.Media._ID} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val dateCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)
                val modCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
                val durCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                while (cursor.moveToNext() && result.size < 120) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(videoCollection, id)
                    val dateAdded = if (dateCol >= 0) cursor.getLong(dateCol) else 0L
                    val dateModified = if (modCol >= 0) cursor.getLong(modCol) else 0L
                    val effectiveDate = maxOf(dateAdded, dateModified)
                    val durMs = if (durCol >= 0) cursor.getLong(durCol) else 0L
                    val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                    val durSec = if (durMs > 0L) (durMs / 1000L).toInt().coerceAtLeast(1) else 0
                    if (seen.add(uri.toString())) {
                        result.add(
                            LocalGalleryMediaItem(
                                uri = uri,
                                dateAddedSec = effectiveDate,
                                durationSec = durSec,
                                sizeBytes = size,
                                isOverLimit = size > VideoMediaHelper.MAX_VIDEO_BYTES
                            )
                        )
                    }
                }
            }
        }

        // App's recorded camera videos cache (excluding assembled playback cache entries)
        runCatching {
            val dir = File(context.cacheDir, "videos_cache")
            if (dir.exists()) {
                dir.listFiles()
                    ?.filter { it.isFile && it.length() > 0L && it.name.startsWith("vid_cam_") }
                    ?.sortedByDescending { it.lastModified() }
                    ?.take(15)
                    ?.forEach { file ->
                        val uri = Uri.fromFile(file)
                        if (seen.add(uri.toString())) {
                            val durSec = extractVideoDurationSec(context, uri)
                            result.add(
                                LocalGalleryMediaItem(
                                    uri = uri,
                                    dateAddedSec = file.lastModified() / 1000L,
                                    durationSec = durSec,
                                    sizeBytes = file.length(),
                                    isOverLimit = file.length() > VideoMediaHelper.MAX_VIDEO_BYTES
                                )
                            )
                        }
                    }
            }
        }

        result.sortedByDescending { it.dateAddedSec }
    }

private suspend fun extractVideoDurationSec(context: Context, uri: Uri): Int =
    withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 1000L
            (durMs / 1000L).toInt().coerceAtLeast(1)
        } catch (_: Exception) {
            1
        } finally {
            runCatching { retriever.release() }
        }
    }

private suspend fun loadVideoThumbnailBitmap(context: Context, uri: Uri): Bitmap? =
    withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri.scheme == "content") {
            val osThumb = runCatching {
                context.contentResolver.loadThumbnail(uri, Size(360, 360), null)
            }.getOrNull()
            if (osThumb != null && !osThumb.isRecycled) {
                return@withContext osThumb
            }
        }

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val frame = retriever.getFrameAtTime(200_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime()
            if (frame != null) {
                val maxSide = maxOf(frame.width, frame.height).coerceAtLeast(1)
                val scale = (320f / maxSide).coerceAtMost(1f)
                if (scale < 1f) {
                    Bitmap.createScaledBitmap(
                        frame,
                        (frame.width * scale).toInt().coerceAtLeast(1),
                        (frame.height * scale).toInt().coerceAtLeast(1),
                        true
                    ).also { if (it != frame) frame.recycle() }
                } else {
                    frame
                }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
