package com.example.ui.components

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

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
    var textStudioDraft by remember {
        mutableStateOf(
            StatusStudioDraftItem(
                id = "text_studio_draft",
                isVideo = false,
                isTextStatus = true,
                backgroundHex = StatusComposerTextColors.first()
            )
        )
    }

    var mediaDrafts by remember { mutableStateOf<List<StatusStudioDraftItem>>(emptyList()) }
    var activeDraftIndex by remember { mutableIntStateOf(0) }
    var appendMediaOnReturn by remember { mutableStateOf(false) }

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
    var hadCameraRationaleBeforeRequest by remember { mutableStateOf(false) }
    var galleryRefreshTick by remember { mutableIntStateOf(0) }

    var showDiscardDialog by remember { mutableStateOf(false) }
    var inlineWarningMsg by remember { mutableStateOf<String?>(null) }
    var inlineWarningOpensSettings by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                photoAccessGranted = hasPhotoGalleryAccess(context)
                videoAccessGranted = hasVideoGalleryAccess(context)
                partialPhotoAccess = hasPartialMediaAccess(context, isVideo = false)
                partialVideoAccess = hasPartialMediaAccess(context, isVideo = true)
                if (inlineWarningOpensSettings && isPermissionGranted(context, Manifest.permission.CAMERA)) {
                    inlineWarningMsg = null
                    inlineWarningOpensSettings = false
                }
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

    LaunchedEffect(inlineWarningMsg, inlineWarningOpensSettings) {
        if (inlineWarningMsg != null) {
            delay(if (inlineWarningOpensSettings) 5500L else 2800L)
            inlineWarningMsg = null
            inlineWarningOpensSettings = false
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
        val fromTextStudio = mediaDrafts.isEmpty() && append
        val sharedAudience = mediaDrafts.firstOrNull()?.overlays?.audienceMemberIds
            ?: textStudioDraft.overlays.audienceMemberIds
        val entry = StatusStudioDraftItem(
            id = "draft_${UUID.randomUUID().toString().take(8)}",
            uri = uri,
            isVideo = false,
            caption = if (fromTextStudio) textStudioDraft.caption else "",
            musicTrack = if (fromTextStudio) textStudioDraft.musicTrack else null,
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
            val fromTextStudio = mediaDrafts.isEmpty() && append
            val sharedAudience = mediaDrafts.firstOrNull()?.overlays?.audienceMemberIds
                ?: textStudioDraft.overlays.audienceMemberIds
            val draft = StatusStudioDraftItem(
                id = "draft_${UUID.randomUUID().toString().take(8)}",
                uri = uri,
                isVideo = true,
                videoPreviewInfo = info,
                caption = if (fromTextStudio) textStudioDraft.caption else "",
                musicTrack = if (fromTextStudio) textStudioDraft.musicTrack else null,
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
                inlineWarningMsg = null
                inlineWarningOpensSettings = false
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
                val activity = context as? Activity
                val showRationaleNow = activity != null &&
                    ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
                val permanentlyDenied = activity != null && !hadCameraRationaleBeforeRequest && !showRationaleNow
                if (permanentlyDenied) {
                    inlineWarningOpensSettings = true
                    inlineWarningMsg = "Permiso de cámara desactivado en Ajustes."
                } else {
                    inlineWarningOpensSettings = false
                    inlineWarningMsg = "Permiso de cámara requerido para capturar."
                }
            }
        }
    )

    fun launchSystemPhotoCamera() {
        pendingCameraType = 1
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            inlineWarningMsg = null
            inlineWarningOpensSettings = false
            val uri = CameraCaptureHelper.createTempPhotoUri(context)
            if (uri != null) {
                pendingCameraPhotoUri = uri
                takePictureLauncher.launch(uri)
            } else {
                appendMediaOnReturn = false
            }
        } else {
            val activity = context as? Activity
            hadCameraRationaleBeforeRequest = activity != null &&
                ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun launchSystemVideoCamera() {
        pendingCameraType = 2
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            inlineWarningMsg = null
            inlineWarningOpensSettings = false
            val uri = CameraCaptureHelper.createTempVideoUri(context)
            if (uri != null) {
                pendingCameraVideoUri = uri
                captureVideoLauncher.launch(uri)
            } else {
                appendMediaOnReturn = false
            }
        } else {
            val activity = context as? Activity
            hadCameraRationaleBeforeRequest = activity != null &&
                ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val isInMediaStudio = mediaDrafts.isNotEmpty()
    val isInTextStudio = !isInMediaStudio && currentMode == StatusComposerMode.TEXT
    val isStudioShown = isInMediaStudio || isInTextStudio
    val isPublishing = publishState is StatusPublishUiState.Publishing

    fun hasUnsavedContent(): Boolean =
        textStudioDraft.hasEdits() ||
            mediaDrafts.any { it.hasEdits() } ||
            mediaDrafts.size > 1

    fun triggerPublishTextStudio() {
        if (isPublishing) return
        val hasContent = textStudioDraft.caption.trim().isNotEmpty() || !textStudioDraft.overlays.isEmptyEdits()
        if (!hasContent) return
        keyboardController?.hide()
        focusManager.clearFocus()
        onPublish(
            StatusPublishRequest(
                mode = StatusComposerMode.TEXT,
                text = textStudioDraft.caption.trim(),
                backgroundHex = textStudioDraft.backgroundHex,
                musicTrack = textStudioDraft.musicTrack,
                overlayBundle = textStudioDraft.overlays,
                visibleToMemberIds = textStudioDraft.overlays.audienceMemberIds,
                draftId = textStudioDraft.id
            )
        )
    }

    fun triggerPublishMediaDrafts() {
        if (isPublishing || mediaDrafts.isEmpty()) return
        keyboardController?.hide()
        focusManager.clearFocus()
        val requests = mediaDrafts.map { draft ->
            if (draft.isVideo) {
                StatusPublishRequest(
                    mode = StatusComposerMode.VIDEO,
                    videoUri = draft.uri,
                    videoCaption = draft.caption,
                    musicTrack = draft.musicTrack,
                    overlayBundle = draft.overlays,
                    visibleToMemberIds = draft.overlays.audienceMemberIds,
                    draftId = draft.id
                )
            } else {
                StatusPublishRequest(
                    mode = StatusComposerMode.PHOTO,
                    photoUri = draft.uri,
                    photoCaption = draft.caption,
                    musicTrack = draft.musicTrack,
                    overlayBundle = draft.overlays,
                    visibleToMemberIds = draft.overlays.audienceMemberIds,
                    draftId = draft.id,
                    viewportAspectRatio = draft.viewportAspectRatio,
                    useContainWithBlur = draft.useContainWithBlur
                )
            }
        }
        if (onPublishBatch != null) {
            onPublishBatch(requests)
        } else if (requests.isNotEmpty()) {
            onPublish(requests.first())
        }
    }

    fun handleBackOrClose() {
        if (isPublishing) return
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

    BackHandler(enabled = !isStudioShown) {
        when {
            showDiscardDialog -> showDiscardDialog = false
            isPublishing -> Unit
            else -> handleBackOrClose()
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
                    onRequestClose = { handleBackOrClose() },
                    onDismissPublishError = onDismissPublishError,
                    onPublishDrafts = ::triggerPublishMediaDrafts
                )
            }

            currentMode == StatusComposerMode.TEXT -> {
                StatusMediaStudioScreen(
                    drafts = listOf(textStudioDraft),
                    activeIndex = 0,
                    activeDraft = textStudioDraft,
                    publishState = publishState,
                    allMembers = allMembers,
                    activeMemberId = activeMemberId,
                    favoriteMusicTracks = favoriteMusicTracks,
                    onToggleFavoriteMusicTrack = onToggleFavoriteMusicTrack,
                    musicSearchCatalog = musicSearchCatalog,
                    onSelectDraftIndex = {},
                    onRemoveDraftAt = {},
                    onUpdateActiveDraft = { transform ->
                        val prevAudience = textStudioDraft.overlays.audienceMemberIds
                        val updated = transform(textStudioDraft)
                        textStudioDraft = updated
                        val nextAudience = updated.overlays.audienceMemberIds
                        if (prevAudience != nextAudience && mediaDrafts.isNotEmpty()) {
                            mediaDrafts = mediaDrafts.map { item ->
                                item.copy(overlays = item.overlays.copy(audienceMemberIds = nextAudience))
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
                    onRequestClose = { handleBackOrClose() },
                    onDismissPublishError = onDismissPublishError,
                    onPublishDrafts = ::triggerPublishTextStudio,
                    onSelectComposerMode = { next ->
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        currentMode = next
                    }
                )
            }

            else -> {
                val isVideoGallery = currentMode == StatusComposerMode.VIDEO
                MediaGalleryComposerPage(
                    isVideo = isVideoGallery,
                    items = if (isVideoGallery) combinedVideos else combinedPhotos,
                    hasGalleryPermission = if (isVideoGallery) videoAccessGranted else photoAccessGranted,
                    hasPartialAccess = if (isVideoGallery) partialVideoAccess else partialPhotoAccess,
                    onCloseClick = { handleBackOrClose() },
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

        // Brief floating notice for size limit or camera permission (with Settings button when permanently denied)
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(
                        horizontal = 16.dp,
                        vertical = if (inlineWarningOpensSettings) 6.dp else 10.dp
                    )
                ) {
                    Text(
                        text = warning,
                        color = Color.White,
                        fontFamily = InterFontFamily,
                        fontSize = 13.sp,
                        textAlign = if (inlineWarningOpensSettings) TextAlign.Start else TextAlign.Center,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (inlineWarningOpensSettings) {
                        TextButton(
                            onClick = {
                                inlineWarningMsg = null
                                inlineWarningOpensSettings = false
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.fromParts("package", context.packageName, null)
                                        ).apply {
                                            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 40.dp)
                                .testTag("status_camera_open_settings_button")
                        ) {
                            Text(
                                text = "Abrir Ajustes",
                                fontFamily = InterFontFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF8AB4F8)
                            )
                        }
                    }
                }
            }
        }

        // Centered Discard Dialog (shared across Text, Photo, and Video Studio)
        if (showDiscardDialog) {
            BackHandler(enabled = true) { showDiscardDialog = false }
            StatusDiscardDialog(
                title = "¿Descartar el estado?",
                subtitle = "Se perderán los cambios sin publicar.",
                onKeepEditing = { showDiscardDialog = false },
                onDiscard = {
                    showDiscardDialog = false
                    mediaDrafts = emptyList()
                    activeDraftIndex = 0
                    onClose()
                }
            )
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
internal fun StatusComposerModePill(
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
