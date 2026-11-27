package com.example.ui.viewmodel

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.calls.WebRtcCallManager
import com.example.data.local.AppFontScale
import com.example.data.local.AppThemeMode
import com.example.data.local.InitialSessionSnapshot
import com.example.data.local.PreferencesManager
import com.example.data.model.CallDirection
import com.example.data.model.CallIceCandidateEntity
import com.example.data.model.CallRecordEntity
import com.example.data.model.CallSignalStatus
import com.example.data.model.CallType
import com.example.data.model.ChatMessageEntity
import com.example.data.model.ChatThreadEntity
import com.example.data.model.ChatType
import com.example.data.model.FamilyEventEntity
import com.example.data.model.FamilyMemberEntity
import com.example.data.model.FamilyNoteEntity
import com.example.data.model.FamilySpace
import com.example.data.model.FamilyStatusEntity
import com.example.data.model.JsonHelpers
import com.example.data.model.MediaChunkEntity
import com.example.data.model.MessageStatus
import com.example.data.model.MessageType
import com.example.data.model.PollOption
import com.example.data.model.PresenceStatus
import com.example.data.model.ShoppingItemEntity
import com.example.data.remote.FamilyCloudRepository
import com.example.data.remote.unwrapFirestoreException
import com.example.push.PushRegistrationManager
import com.example.util.AppReleaseInfo
import com.example.util.AppUpdateChecker
import com.example.util.AppUpdateUiState
import com.example.util.DocumentMediaHelper
import com.example.util.FamiChatNotificationHelper
import com.example.util.ImageCompressionHelper
import com.example.util.LocationHelper
import com.example.util.MessageFormatter
import com.example.util.PresenceResolver
import com.example.util.StatusMusicTrack
import com.example.util.TimeFormatters
import com.example.util.VideoMediaHelper
import com.example.util.VoiceAudioHelper
import java.io.File
import java.text.Normalizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

enum class MainTab { CHATS, GROUPS, CALLS, UPDATES }

enum class StatusComposerMode { TEXT, PHOTO, VIDEO }

data class StatusPublishRequest(
    val mode: StatusComposerMode,
    val text: String = "",
    val backgroundHex: String = "#0E7C66",
    val photoUri: Uri? = null,
    val photoCaption: String = "",
    val videoUri: Uri? = null,
    val videoCaption: String = "",
    val musicTrack: StatusMusicTrack? = null,
    val overlayBundle: com.example.util.StatusOverlayBundle? = null,
    val visibleToMemberIds: List<String>? = null,
    val draftId: String = "",
    val viewportAspectRatio: Float = 0f,
    val useContainWithBlur: Boolean = false
)

sealed interface StatusPublishUiState {
    data object Idle : StatusPublishUiState
    data class Publishing(val mode: StatusComposerMode, val progress: Float = 0f) : StatusPublishUiState
    data class Error(val message: String) : StatusPublishUiState
}

data class ChatThreadUi(
    val chat: ChatThreadEntity,
    val displayTitle: String,
    val displayEmoji: String,
    val displayColorHex: String,
    val displayPhotoData: String = "",
    val partnerBioText: String = "",
    val isOnline: Boolean,
    val subtitleStatus: String,
    val lastMessagePreview: String,
    val lastMessageTime: String,
    val unreadCount: Int,
    val lastMessageStatus: MessageStatus?,
    val isLastMessageFromMe: Boolean,
    val participants: List<FamilyMemberEntity>,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val rightStatusText: String = ""
)

data class ActiveCallUiState(
    val callId: String,
    val chatId: String,
    val title: String,
    val avatarEmoji: String,
    val avatarColorHex: String,
    val callType: CallType,
    val isOutgoing: Boolean = true,
    val isConnected: Boolean = false,
    val durationSec: Int = 0,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isCameraOn: Boolean = true,
    val targetPhoneNumber: String = ""
)

data class VoiceRecordingState(
    val isRecording: Boolean = false,
    val elapsedSec: Int = 0,
    val amplitudes: List<Float> = emptyList()
)

internal fun normalizeProfileKey(value: String): String {
    val nfd = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
    return nfd.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .replace(Regex("\\s+"), " ")
        .lowercase()
}

internal fun isDuplicateLegacyPapaProfile(
    candidate: FamilyMemberEntity,
    allMembers: List<FamilyMemberEntity>
): Boolean {
    if (candidate.isAdmin) return false
    val hasPrimaryAdmin = allMembers.any { it.isAdmin && it.id != candidate.id }
    if (!hasPrimaryAdmin) return false
    val normName = normalizeProfileKey(candidate.name)
    val normRole = normalizeProfileKey(candidate.roleLabel)
    return normName == "papa" && (normRole == "familiar" || normRole.isEmpty())
}

internal fun validateMemberBeforeSave(
    existingMembers: List<FamilyMemberEntity>,
    editingId: String?,
    name: String,
    roleLabel: String,
    isCallerAdmin: Boolean
): String? {
    if (!isCallerAdmin) {
        return "Solo el administrador puede crear o modificar perfiles."
    }
    val trimmedName = name.trim()
    val trimmedRole = roleLabel.trim()
    if (trimmedName.isEmpty()) {
        return "Ingresa un nombre para el perfil."
    }
    if (trimmedRole.isEmpty()) {
        return "Ingresa el parentesco o rol familiar."
    }
    val normName = normalizeProfileKey(trimmedName)
    val others = existingMembers.filter { it.id != editingId }
    if (others.any { normalizeProfileKey(it.name) == normName }) {
        return "Ya existe un perfil con el nombre \"$trimmedName\"."
    }
    return null
}

@OptIn(ExperimentalCoroutinesApi::class)
class FamilyViewModel(
    private val appContext: Context,
    private val cloudRepo: FamilyCloudRepository,
    private val prefs: PreferencesManager,
    val currentUserId: String
) : ViewModel() {

    private val voiceAudio = VoiceAudioHelper(appContext)
    val webRtcCallManager = WebRtcCallManager(appContext)
    private val liveLocationJobs = mutableMapOf<String, Job>()

    private fun <T> Flow<T>.safeFirestoreFlow(fallback: T): Flow<T> = catch { err ->
        val fsErr = err.unwrapFirestoreException()
        if (fsErr == null && err is CancellationException) throw err
        val msg = if (fsErr?.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED) {
            "Límite diario de lecturas en la nube alcanzado; se restablecerá mañana."
        } else {
            fsErr?.localizedMessage ?: err.localizedMessage
        }
        if (!msg.isNullOrBlank()) {
            _snackbarMessage.value = msg
        }
        emit(fallback)
    }

    private val bootSessionSnapshot: InitialSessionSnapshot? = prefs.readInitialSessionSnapshotSync()
    private val bootActiveFamilyId: String? = bootSessionSnapshot?.activeFamilyId?.takeIf { it.isNotBlank() }
    private val bootActiveMemberId: String? = bootSessionSnapshot?.activeMemberId?.takeIf { it.isNotBlank() }
    private val bootInstantMembers: List<FamilyMemberEntity> = run {
        val snap = bootSessionSnapshot ?: return@run emptyList()
        val fid = bootActiveFamilyId ?: return@run emptyList()
        if (snap.cachedFamilyId == fid) snap.cachedMembers else emptyList()
    }
    private val hasBootFastActiveProfile: Boolean =
        bootActiveFamilyId != null &&
            bootActiveMemberId != null &&
            bootInstantMembers.any { it.id == bootActiveMemberId }
    private val hasBootFastSelector: Boolean =
        bootActiveFamilyId != null &&
            bootActiveMemberId == null &&
            bootInstantMembers.isNotEmpty()
    private val bootActiveMember: FamilyMemberEntity? =
        if (hasBootFastActiveProfile) bootInstantMembers.find { it.id == bootActiveMemberId } else null

    // Defer non-Chats Firestore listeners briefly during fast boot so Frame 1 renders unblocked.
    private val _secondaryStreamsReady = MutableStateFlow(!hasBootFastActiveProfile)

    val myFamilies: StateFlow<List<FamilySpace>> = _secondaryStreamsReady
        .flatMapLatest { ready ->
            if (!ready) flowOf(emptyList())
            else cloudRepo.observeMyFamilies(currentUserId).safeFirestoreFlow(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _activeFamilyId = MutableStateFlow<String?>(
        if (hasBootFastActiveProfile || hasBootFastSelector) bootActiveFamilyId else null
    )
    val activeFamilyId: StateFlow<String?> = _activeFamilyId.asStateFlow()

    val activeFamily: StateFlow<FamilySpace?> = _activeFamilyId
        .flatMapLatest { fid ->
            if (fid.isNullOrBlank()) flowOf(null) else cloudRepo.observeFamily(fid).safeFirestoreFlow(null)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _activeMemberId = MutableStateFlow<String?>(
        if (hasBootFastActiveProfile) bootActiveMemberId else null
    )
    val activeMemberId: StateFlow<String?> = _activeMemberId.asStateFlow()
    private val _isAppForeground = MutableStateFlow(false)
    private val _pendingShowLastSeenOverride = MutableStateFlow<Pair<String, Boolean>?>(null)

    private val presenceClock = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(PresenceResolver.CLOCK_TICK_MS)
        }
    }

    private val _isMembersLoading = MutableStateFlow(!(hasBootFastActiveProfile || hasBootFastSelector))
    val isMembersLoading: StateFlow<Boolean> = _isMembersLoading.asStateFlow()

    private val _profileLoadError = MutableStateFlow<String?>(null)
    val profileLoadError: StateFlow<String?> = _profileLoadError.asStateFlow()

    private val _isResolvingFamilySpace = MutableStateFlow(!(hasBootFastActiveProfile || hasBootFastSelector))
    private val _membersRetryTick = MutableStateFlow(0)
    private val _optimisticRegisteredMember = MutableStateFlow<Pair<String, FamilyMemberEntity>?>(null)
    private val _bootstrapFallbackMembers = MutableStateFlow<Pair<String, List<FamilyMemberEntity>>?>(
        if ((hasBootFastActiveProfile || hasBootFastSelector) && bootActiveFamilyId != null) {
            bootActiveFamilyId to bootInstantMembers
        } else {
            null
        }
    )
    private var membersLoadingTimeoutJob: Job? = null

    private fun isNonRetryableFirestoreError(err: Throwable?): Boolean {
        val code = err?.unwrapFirestoreException()?.code ?: return false
        return code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED ||
            code == com.google.firebase.firestore.FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED ||
            code == com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAUTHENTICATED ||
            code == com.google.firebase.firestore.FirebaseFirestoreException.Code.INVALID_ARGUMENT ||
            code == com.google.firebase.firestore.FirebaseFirestoreException.Code.NOT_FOUND
    }

    private fun formatProfileLoadError(err: Throwable?): String {
        val fsErr = err?.unwrapFirestoreException()
        return when (fsErr?.code) {
            com.google.firebase.firestore.FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED -> {
                val rawDetail = fsErr.localizedMessage.orEmpty()
                if (rawDetail.contains("free tier database", ignoreCase = true) ||
                    rawDetail.contains("cannot exceed free quota limits", ignoreCase = true)
                ) {
                    "La base de datos Firestore sigue limitada a cuota gratuita (20,000 escrituras/día) aunque el proyecto tenga Blaze (RESOURCE_EXHAUSTED). Se restablece mañana o al quitar el modo Free Tier de la base en Google Cloud Console."
                } else {
                    "Se alcanzó el límite diario de operaciones en la nube (RESOURCE_EXHAUSTED). Se restablecerá mañana."
                }
            }
            com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "Sin permisos para leer o unirse a la familia en la nube (PERMISSION_DENIED)."
            com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE ->
                "No se pudo conectar con el servidor. Verifica tu conexión a internet (UNAVAILABLE)."
            com.google.firebase.firestore.FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                "La conexión tardó demasiado al descargar los perfiles (DEADLINE_EXCEEDED)."
            com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                "No se pudo validar la sesión con el servidor (UNAUTHENTICATED). Intenta de nuevo."
            com.google.firebase.firestore.FirebaseFirestoreException.Code.ABORTED,
            com.google.firebase.firestore.FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
                "El servidor rechazó temporalmente la unión a la familia (${fsErr.code.name}). Intenta de nuevo."
            else -> {
                val codeName = fsErr?.code?.name
                val codeSuffix = codeName?.let { " ($it)" }.orEmpty()
                val raw = fsErr?.localizedMessage ?: err?.localizedMessage
                if (!raw.isNullOrBlank()) {
                    if (codeName != null && !raw.contains(codeName)) "$raw$codeSuffix" else raw
                } else {
                    "No se pudieron descargar los perfiles$codeSuffix. Verifica tu internet e intenta de nuevo."
                }
            }
        }
    }

    private val rawMembers: StateFlow<List<FamilyMemberEntity>> = combine(
        _activeFamilyId,
        _isResolvingFamilySpace,
        _membersRetryTick
    ) { fid, resolving, retryTick ->
        Triple(fid, if (fid.isNullOrBlank()) resolving else false, retryTick)
    }
        .distinctUntilChanged()
        .flatMapLatest { (fid, resolving, _) ->
            membersLoadingTimeoutJob?.cancel()
            if (fid.isNullOrBlank()) {
                _isMembersLoading.value = resolving
                if (resolving) {
                    membersLoadingTimeoutJob = viewModelScope.launch {
                        delay(16_000L)
                        if (_activeFamilyId.value.isNullOrBlank()) {
                            _isResolvingFamilySpace.value = false
                            _isMembersLoading.value = false
                            if (_profileLoadError.value.isNullOrBlank()) {
                                _profileLoadError.value =
                                    "La conexión tardó demasiado al buscar los perfiles. Verifica tu internet e intenta de nuevo."
                            }
                        }
                    }
                }
                flowOf(emptyList())
            } else {
                val hasFallback = _bootstrapFallbackMembers.value?.let { it.first == fid && it.second.isNotEmpty() } == true
                _isMembersLoading.value = !hasFallback
                if (!hasFallback) {
                    membersLoadingTimeoutJob = viewModelScope.launch {
                        delay(10_000L)
                        if (_activeFamilyId.value == fid) {
                            _isMembersLoading.value = false
                        }
                    }
                }
                var handledInitialEmpty = false
                cloudRepo.observeMembers(fid, currentUserId)
                    .onEach { list ->
                        if (list.isEmpty()) {
                            // On a fresh install, Firestore's local cache immediately emits emptyList()
                            // before server data or newly-synced memberUids arrive. Fetch directly
                            // from server + meta/bootstrap before ever showing the empty state.
                            var fallback = cloudRepo.fetchMembersOnceFromServer(fid, currentUserId)
                            if (fallback.isEmpty()) {
                                fallback = cloudRepo.fetchBootstrapMembersById(fid)
                            }
                            if (fallback.isEmpty() && !handledInitialEmpty) {
                                handledInitialEmpty = true
                                delay(1_000L)
                                fallback = cloudRepo.fetchMembersOnceFromServer(fid, currentUserId)
                                if (fallback.isEmpty()) {
                                    fallback = cloudRepo.fetchBootstrapMembersById(fid)
                                }
                            }
                            if (fallback.isNotEmpty()) {
                                _bootstrapFallbackMembers.value = fid to fallback
                                viewModelScope.launch {
                                    prefs.saveCachedFamilyMembers(fid, fallback)
                                }
                            }
                        } else {
                            membersLoadingTimeoutJob?.cancel()
                            val activeId = _activeMemberId.value
                            if (activeId.isNullOrBlank() || list.any { it.id == activeId }) {
                                if (_bootstrapFallbackMembers.value != null) {
                                    _bootstrapFallbackMembers.value = null
                                }
                            } else {
                                // Keep the currently active member from fallback so a partial
                                // Firestore snapshot never drops activeMember to null and bounces
                                // the user back to the profile selector.
                                val preserved = _bootstrapFallbackMembers.value
                                    ?.takeIf { it.first == fid }
                                    ?.second
                                    ?.find { it.id == activeId }
                                if (preserved != null) {
                                    _bootstrapFallbackMembers.value = fid to listOf(preserved)
                                }
                                viewModelScope.launch {
                                    cloudRepo.syncSingleMemberUid(fid, activeId, currentUserId)
                                }
                            }
                            viewModelScope.launch {
                                prefs.saveCachedFamilyMembers(fid, list, preserveMemberId = activeId)
                            }
                        }
                        _isMembersLoading.value = false
                        val opt = _optimisticRegisteredMember.value
                        if (opt != null && opt.first == fid && list.any { it.id == opt.second.id }) {
                            _optimisticRegisteredMember.value = null
                        }
                        val pendingShow = _pendingShowLastSeenOverride.value
                        if (pendingShow != null && list.any { it.id == pendingShow.first && it.showLastSeen == pendingShow.second }) {
                            _pendingShowLastSeenOverride.value = null
                        }
                    }
                    .catch { err ->
                        val fallback = cloudRepo.fetchBootstrapMembersById(fid)
                        if (fallback.isNotEmpty()) {
                            _bootstrapFallbackMembers.value = fid to fallback
                        }
                        _isMembersLoading.value = false
                        val fsErr = err.unwrapFirestoreException()
                        if (fsErr == null && err is CancellationException) throw err
                        val msg = if (fsErr?.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED) {
                            "Límite diario de lecturas en la nube alcanzado; se restablecerá mañana."
                        } else {
                            fsErr?.localizedMessage ?: err.localizedMessage
                        }
                        if (!msg.isNullOrBlank()) {
                            _snackbarMessage.value = msg
                        }
                        emit(emptyList())
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val effectiveRawMembers: Flow<List<FamilyMemberEntity>> = combine(
        rawMembers,
        _bootstrapFallbackMembers,
        _optimisticRegisteredMember,
        _activeFamilyId
    ) { list, fallbackPair, optimistic, famId ->
        val fallbackList = if (fallbackPair != null && fallbackPair.first == famId) fallbackPair.second else emptyList()
        val existingIds = list.map { it.id }.toSet()
        val merged = if (fallbackList.isNotEmpty()) {
            list + fallbackList.filter { it.id !in existingIds }
        } else {
            list
        }
        if (optimistic != null && optimistic.first == famId && merged.none { it.id == optimistic.second.id }) {
            merged + optimistic.second
        } else {
            merged
        }
    }

    val members: StateFlow<List<FamilyMemberEntity>> = combine(
        effectiveRawMembers,
        _activeMemberId,
        _isAppForeground,
        _pendingShowLastSeenOverride,
        presenceClock
    ) { list, currentId, inForeground, pendingShow, now ->
        list.map { rawMember ->
            val member = if (pendingShow != null && rawMember.id == pendingShow.first) {
                rawMember.copy(showLastSeen = pendingShow.second)
            } else {
                rawMember
            }
            val isLocalCurrent = member.id == currentId
            val resolved = PresenceResolver.resolve(
                member = member,
                nowMs = now,
                isLocalActiveInForeground = isLocalCurrent && inForeground,
                manualInvisible = !member.showLastSeen
            )
            member.copy(
                isOnline = resolved.isOnline,
                lastSeenText = resolved.statusText
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        if (hasBootFastActiveProfile || hasBootFastSelector) bootInstantMembers else emptyList()
    )

    val rawChats: StateFlow<List<ChatThreadEntity>> = _activeFamilyId
        .flatMapLatest { fid ->
            if (fid.isNullOrBlank()) flowOf(emptyList())
            else cloudRepo.observeChats(fid, currentUserId).safeFirestoreFlow(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allMessages: StateFlow<List<ChatMessageEntity>> = _activeFamilyId
        .flatMapLatest { fid ->
            if (fid.isNullOrBlank()) flowOf(emptyList())
            else cloudRepo.observeMessages(fid, currentUserId).safeFirestoreFlow(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val secondaryFamilyIdFlow: Flow<String?> = combine(
        _activeFamilyId,
        _secondaryStreamsReady
    ) { fid, ready ->
        if (ready) fid else null
    }.distinctUntilChanged()

    val calls: StateFlow<List<CallRecordEntity>> = secondaryFamilyIdFlow
        .flatMapLatest { fid ->
            if (fid.isNullOrBlank()) flowOf(emptyList())
            else cloudRepo.observeCalls(fid, currentUserId).safeFirestoreFlow(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val rawStatuses: StateFlow<List<FamilyStatusEntity>> = secondaryFamilyIdFlow
        .flatMapLatest { fid ->
            if (fid.isNullOrBlank()) flowOf(emptyList())
            else cloudRepo.observeStatuses(fid, currentUserId).safeFirestoreFlow(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val statuses: StateFlow<List<FamilyStatusEntity>> = combine(
        rawStatuses,
        presenceClock,
        _activeMemberId
    ) { list, now, currentId ->
        val memberId = currentId.orEmpty()
        list.filter { st ->
            st.expiresAt > now &&
                (memberId.isEmpty() || com.example.util.StatusEditorCodec.isStatusVisibleToMember(st, memberId))
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val shoppingItems: StateFlow<List<ShoppingItemEntity>> = secondaryFamilyIdFlow
        .flatMapLatest { fid ->
            if (fid.isNullOrBlank()) flowOf(emptyList())
            else cloudRepo.observeShoppingItems(fid, currentUserId).safeFirestoreFlow(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val events: StateFlow<List<FamilyEventEntity>> = secondaryFamilyIdFlow
        .flatMapLatest { fid ->
            if (fid.isNullOrBlank()) flowOf(emptyList())
            else cloudRepo.observeEvents(fid, currentUserId).safeFirestoreFlow(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notes: StateFlow<List<FamilyNoteEntity>> = secondaryFamilyIdFlow
        .flatMapLatest { fid ->
            if (fid.isNullOrBlank()) flowOf(emptyList())
            else cloudRepo.observeNotes(fid, currentUserId).safeFirestoreFlow(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val masterPin: StateFlow<String> = combine(activeFamily, prefs.masterPinFlow) { fam, localPin ->
        fam?.masterPin?.takeIf { it.length == 4 && it.all(Char::isDigit) } ?: localPin
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PreferencesManager.DEFAULT_PIN)

    val switchPin: StateFlow<String> = combine(activeFamily, prefs.switchPinFlow) { fam, localPin ->
        fam?.switchPin?.takeIf { it.length == 4 && it.all(Char::isDigit) } ?: localPin
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PreferencesManager.DEFAULT_SWITCH_PIN)

    val isDarkTheme: StateFlow<Boolean> = prefs.isDarkThemeFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val themeMode: StateFlow<AppThemeMode> = prefs.themeModeFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppThemeMode.SYSTEM)

    val fontScale: StateFlow<AppFontScale> = prefs.fontScaleFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppFontScale.NORMAL)

    val notificationsEnabled: StateFlow<Boolean> = prefs.notificationsEnabledFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val realtimeBackgroundServiceEnabled: StateFlow<Boolean> = prefs.realtimeBackgroundServiceEnabledFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val chatWallpapers: StateFlow<Map<String, String>> = prefs.chatWallpapersFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val chatNotificationTones: StateFlow<Map<String, String>> = prefs.chatNotificationTonesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val favoriteMusicTracks: StateFlow<List<StatusMusicTrack>> = prefs.favoriteMusicTracksFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun toggleFavoriteMusicTrack(track: StatusMusicTrack) {
        viewModelScope.launch {
            prefs.toggleFavoriteMusicTrack(track)
        }
    }

    val pinnedChatIds: StateFlow<Set<String>> = prefs.pinnedChatIdsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val mutedChatIds: StateFlow<Set<String>> = prefs.mutedChatIdsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val chatClearedAtMap: StateFlow<Map<String, Long>> = prefs.chatClearedAtFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val locallyHiddenMessageKeys: StateFlow<Set<String>> = prefs.locallyHiddenMessageKeysFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val fcmToken: StateFlow<String?> = prefs.fcmTokenFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val turnUrl: StateFlow<String> = prefs.turnUrlFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, PreferencesManager.DEFAULT_TURN_URL)

    val turnUsername: StateFlow<String> = prefs.turnUsernameFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, PreferencesManager.DEFAULT_TURN_USERNAME)

    val turnCredential: StateFlow<String> = prefs.turnCredentialFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, PreferencesManager.DEFAULT_TURN_CREDENTIAL)

    private val _pendingNotificationChatId = MutableStateFlow<String?>(null)
    val pendingNotificationChatId: StateFlow<String?> = _pendingNotificationChatId.asStateFlow()

    val updateRepoSlug: StateFlow<String> = combine(activeFamily, prefs.updateRepoSlugFlow) { fam, localSlug ->
        localSlug.ifBlank { null }
            ?: fam?.updateRepoSlug?.ifBlank { null }
            ?: BuildConfig.GITHUB_REPO
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BuildConfig.GITHUB_REPO)

    private val _updateState = MutableStateFlow<AppUpdateUiState>(AppUpdateUiState.Idle)
    val updateState: StateFlow<AppUpdateUiState> = _updateState.asStateFlow()
    val lastUpdateCheckAt: StateFlow<Long> = prefs.lastUpdateCheckAtFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)
    val updateSnoozedUntil: StateFlow<Long> = prefs.updateSnoozedUntilFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)
    private val _versionHistoryState =
        MutableStateFlow<com.example.util.VersionHistoryUiState>(com.example.util.VersionHistoryUiState.Idle)
    val versionHistoryState: StateFlow<com.example.util.VersionHistoryUiState> =
        _versionHistoryState.asStateFlow()
    private var updateCheckJob: Job? = null
    private var updateDownloadJob: Job? = null
    private var versionHistoryJob: Job? = null

    val activeMember: StateFlow<FamilyMemberEntity?> = combine(members, _activeMemberId) { list, id ->
        list.find { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, bootActiveMember)

    private val _isSessionReady = MutableStateFlow(hasBootFastActiveProfile || hasBootFastSelector)
    val isSessionReady: StateFlow<Boolean> = _isSessionReady.asStateFlow()

    private val _showFamilySpacesSetup = MutableStateFlow(false)
    val showFamilySpacesSetup: StateFlow<Boolean> = _showFamilySpacesSetup.asStateFlow()

    private val _isFamilyActionBusy = MutableStateFlow(false)
    val isFamilyActionBusy: StateFlow<Boolean> = _isFamilyActionBusy.asStateFlow()

    private val _selectedTab = MutableStateFlow(MainTab.CHATS)
    val selectedTab: StateFlow<MainTab> = _selectedTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _activeChatId = MutableStateFlow<String?>(null)
    val activeChatId: StateFlow<String?> = _activeChatId.asStateFlow()

    private val _activeCall = MutableStateFlow<ActiveCallUiState?>(null)
    val activeCall: StateFlow<ActiveCallUiState?> = _activeCall.asStateFlow()

    val incomingCall: StateFlow<CallRecordEntity?> = combine(calls, _activeMemberId, _activeCall) { callList, meId, curActive ->
        if (meId == null || curActive != null) return@combine null
        val now = System.currentTimeMillis()
        callList.firstOrNull { call ->
            val targets = JsonHelpers.decodeStringList(call.targetMemberIdsJson)
            call.callStatus == CallSignalStatus.RINGING &&
                call.callerId != meId &&
                targets.contains(meId) &&
                (now - call.timestamp) < 60_000L
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _voiceRecording = MutableStateFlow(VoiceRecordingState())
    val voiceRecording: StateFlow<VoiceRecordingState> = _voiceRecording.asStateFlow()

    private val _playingVoiceMessageId = MutableStateFlow<String?>(null)
    val playingVoiceMessageId: StateFlow<String?> = _playingVoiceMessageId.asStateFlow()

    private val _playingVoiceProgress = MutableStateFlow(0f)
    val playingVoiceProgress: StateFlow<Float> = _playingVoiceProgress.asStateFlow()

    private val _voicePlaybackSpeed = MutableStateFlow(1.0f)
    val voicePlaybackSpeed: StateFlow<Float> = _voicePlaybackSpeed.asStateFlow()

    private val _mediaTransferProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val mediaTransferProgress: StateFlow<Map<String, Float>> = _mediaTransferProgress.asStateFlow()

    private val _activeUploadLabel = MutableStateFlow<String?>(null)
    val activeUploadLabel: StateFlow<String?> = _activeUploadLabel.asStateFlow()

    private val _activeUploadProgress = MutableStateFlow<Float?>(null)
    val activeUploadProgress: StateFlow<Float?> = _activeUploadProgress.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private var callMonitorJob: Job? = null
    private var recordingJob: Job? = null
    private var playbackJob: Job? = null
    private var presenceClearJob: Job? = null
    private var heartbeatJob: Job? = null
    private var lastSentPresenceKey: String = ""
    private var lastSentPresenceAt: Long = 0L
    private var lastSyncedMemberUids: Set<String> = emptySet()

    private val baseChatPreviews: Flow<List<ChatThreadUi>> = combine(
        rawChats,
        allMessages,
        members,
        _activeMemberId,
        combine(chatClearedAtMap, locallyHiddenMessageKeys) { cleared, localHidden -> cleared to localHidden }
    ) { chatsList, msgsList, membersList, currentId, (clearedMap, localHiddenKeys) ->
        if (currentId == null) return@combine emptyList()
        val membersMap = membersList.associateBy { it.id }
        val now = System.currentTimeMillis()
        val existingDirectPartnerIds = mutableSetOf<String>()

        val existingItems = chatsList.mapNotNull { chat ->
            val participantIds = JsonHelpers.decodeStringList(chat.participantIdsJson)
            if (!participantIds.contains(currentId)) return@mapNotNull null

            val participants = participantIds.mapNotNull { membersMap[it] }
            val visibleMsgs = msgsList.filter { msg ->
                msg.chatId == chat.id &&
                    isMessageVisibleForMember(
                        msg = msg,
                        memberId = currentId,
                        clearedAtMap = clearedMap,
                        locallyHiddenKeys = localHiddenKeys
                    )
            }
            val lastMsg = visibleMsgs.lastOrNull()
            val unreadCount = visibleMsgs.count { msg ->
                msg.senderId != currentId &&
                    msg.type != MessageType.DELETED &&
                    msg.status != MessageStatus.READ &&
                    !JsonHelpers.decodeStringList(msg.readByMemberIdsJson).contains(currentId)
            }

            val otherMembers = participants.filter { it.id != currentId }
            val otherMember = if (chat.type == ChatType.DIRECT) otherMembers.firstOrNull() else null
            if (otherMember != null) {
                existingDirectPartnerIds.add(otherMember.id)
            }

            val displayTitle = when {
                chat.type == ChatType.DIRECT -> otherMember?.name ?: chat.title
                chat.id == "chat_general" && chat.title == "Familia General" -> "Mi Familia"
                else -> chat.title
            }

            val displayEmoji = if (chat.type == ChatType.DIRECT) {
                otherMember?.avatarEmoji ?: chat.avatarEmoji
            } else chat.avatarEmoji

            val displayColor = if (chat.type == ChatType.DIRECT) {
                otherMember?.avatarColorHex ?: chat.avatarColorHex
            } else chat.avatarColorHex

            val displayPhotoData = if (chat.type == ChatType.DIRECT) {
                otherMember?.avatarPhotoData.orEmpty()
            } else ""

            val partnerBioText = if (chat.type == ChatType.DIRECT) {
                otherMember?.bioText.orEmpty()
            } else ""

            val isOnline = if (chat.type == ChatType.DIRECT) {
                otherMember?.isOnline == true
            } else {
                otherMembers.any { it.isOnline }
            }

            val liveActiveOther = otherMembers.firstOrNull { m ->
                PresenceResolver.isLiveChatAction(m, chat.id, now)
            }

            val subtitleStatus = when (liveActiveOther?.liveStatus) {
                PresenceStatus.TYPING -> if (chat.type == ChatType.GROUP) "${liveActiveOther.name} escribiendo..." else "Escribiendo..."
                PresenceStatus.RECORDING -> if (chat.type == ChatType.GROUP) "${liveActiveOther.name} grabando audio..." else "Grabando audio..."
                else -> {
                    if (chat.type == ChatType.DIRECT) {
                        when {
                            otherMember == null -> "No se ha conectado"
                            !otherMember.showLastSeen -> otherMember.lastSeenText.trim()
                            else -> otherMember.lastSeenText.trim().ifBlank { "No se ha conectado" }
                        }
                    } else {
                        val onlineOthers = otherMembers.count { it.isOnline }
                        val names = participants.joinToString(", ") { it.name }
                        if (onlineOthers > 0) "$onlineOthers en línea · $names"
                        else "${participants.size} miembros · $names"
                    }
                }
            }

            val preview = buildLastMessagePreview(lastMsg, membersMap, currentId, chat.type)
            val timeLabel = if (lastMsg != null) {
                TimeFormatters.formatHourMinute(lastMsg.timestamp)
            } else {
                ""
            }
            val directRightPresence = if (chat.type == ChatType.DIRECT) {
                when {
                    otherMember == null -> "No se ha conectado"
                    !otherMember.showLastSeen -> otherMember.lastSeenText.trim()
                    else -> otherMember.lastSeenText.trim().ifBlank { "No se ha conectado" }
                }
            } else {
                ""
            }

            ChatThreadUi(
                chat = chat,
                displayTitle = displayTitle,
                displayEmoji = displayEmoji,
                displayColorHex = displayColor,
                displayPhotoData = displayPhotoData,
                partnerBioText = partnerBioText,
                isOnline = isOnline,
                subtitleStatus = subtitleStatus,
                lastMessagePreview = preview,
                lastMessageTime = timeLabel,
                unreadCount = unreadCount,
                lastMessageStatus = lastMsg?.status,
                isLastMessageFromMe = lastMsg?.senderId == currentId,
                participants = participants,
                rightStatusText = directRightPresence
            )
        }

        val meMember = membersMap[currentId]
        val virtualDirectItems = membersList
            .filter { it.id != currentId && !existingDirectPartnerIds.contains(it.id) }
            .map { other ->
                val virtualChat = ChatThreadEntity(
                    id = "virtual_direct_${other.id}",
                    type = ChatType.DIRECT,
                    title = other.name,
                    avatarColorHex = other.avatarColorHex,
                    avatarEmoji = other.avatarEmoji,
                    participantIdsJson = JsonHelpers.encodeStringList(listOf(currentId, other.id)),
                    updatedAt = other.liveStatusAt.coerceAtLeast(1L)
                )
                val resolvedPresenceText = if (!other.showLastSeen) {
                    other.lastSeenText.trim()
                } else {
                    other.lastSeenText.trim().ifBlank { "No se ha conectado" }
                }
                ChatThreadUi(
                    chat = virtualChat,
                    displayTitle = other.name,
                    displayEmoji = other.avatarEmoji,
                    displayColorHex = other.avatarColorHex,
                    displayPhotoData = other.avatarPhotoData,
                    partnerBioText = other.bioText,
                    isOnline = other.isOnline,
                    subtitleStatus = resolvedPresenceText,
                    lastMessagePreview = "Sin mensajes aún",
                    lastMessageTime = "",
                    unreadCount = 0,
                    lastMessageStatus = null,
                    isLastMessageFromMe = false,
                    participants = listOfNotNull(meMember, other),
                    rightStatusText = resolvedPresenceText
                )
            }

        existingItems + virtualDirectItems
    }.flowOn(Dispatchers.Default)

    val chatPreviews: StateFlow<List<ChatThreadUi>> = combine(
        baseChatPreviews,
        pinnedChatIds,
        mutedChatIds
    ) { list, pinned, muted ->
        list.map { item ->
            val isDefaultGroupPinned = item.chat.id == "chat_general" && !pinned.contains("unpinned_chat_general")
            item.copy(
                isPinned = pinned.contains(item.chat.id) || isDefaultGroupPinned,
                isMuted = muted.contains(item.chat.id)
            )
        }.sortedWith(
            compareByDescending<ChatThreadUi> { it.isPinned }
                .thenByDescending { it.chat.type == ChatType.GROUP }
                .thenByDescending { it.isOnline }
                .thenByDescending { it.chat.updatedAt }
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            FamiChatNotificationHelper.ensureChannels(appContext)
            VideoMediaHelper.cleanupExpiredStatusVideos(appContext)
        }
        if (!_secondaryStreamsReady.value) {
            viewModelScope.launch {
                delay(400L)
                _secondaryStreamsReady.value = true
            }
        }
        // Silent automatic update check at most once every 24 hours after session is ready
        viewModelScope.launch {
            isSessionReady.first { it }
            kotlinx.coroutines.delay(4_000L)
            checkForAppUpdate(isAutoCheck = true)
        }
        // Race-free presence controller: always starts heartbeat once family, profile, and foreground are active
        viewModelScope.launch {
            combine(_activeFamilyId, _activeMemberId, _isAppForeground) { famId, memberId, inFg ->
                Triple(famId, memberId, inFg)
            }.distinctUntilChanged().collect { (famId, memberId, inFg) ->
                if (famId != null && memberId != null && inFg) {
                    if (isShowLastSeenEnabledFor(memberId)) {
                        startPresenceHeartbeat()
                    } else {
                        heartbeatJob?.cancel()
                        heartbeatJob = null
                    }
                } else if (!inFg) {
                    heartbeatJob?.cancel()
                    heartbeatJob = null
                }
            }
        }
        viewModelScope.launch {
            try {
                // Ultra-Fast Path 0: Already hydrated synchronously from SharedPreferences session mirror
                // on Frame 1 -> skip blocking startup and run background sync right after Chats is visible.
                if (hasBootFastActiveProfile && bootActiveFamilyId != null && bootActiveMemberId != null) {
                    val savedFamilyId = bootActiveFamilyId
                    val savedMemberId = bootActiveMemberId
                    delay(350L)
                    syncPushAndBackgroundWork(savedFamilyId, savedMemberId)
                    if (_isAppForeground.value && isShowLastSeenEnabledFor(savedMemberId)) {
                        startPresenceHeartbeat()
                    }
                    val serverMembers = cloudRepo.fetchMembersOnceFromServer(savedFamilyId, currentUserId)
                    if (serverMembers.isNotEmpty()) {
                        if (serverMembers.any { it.id == savedMemberId }) {
                            prefs.saveCachedFamilyMembers(
                                savedFamilyId,
                                serverMembers,
                                preserveMemberId = savedMemberId
                            )
                        } else {
                            cloudRepo.syncJoiningMembers(savedFamilyId, currentUserId)
                            cloudRepo.syncSingleMemberUid(savedFamilyId, savedMemberId, currentUserId)
                            cloudRepo.syncJoiningChats(savedFamilyId, currentUserId)
                            val confirmedDeleted = cloudRepo.isMemberConfirmedDeletedOnServer(
                                familyId = savedFamilyId,
                                memberId = savedMemberId,
                                uid = currentUserId
                            )
                            if (confirmedDeleted) {
                                _bootstrapFallbackMembers.value = null
                                _activeMemberId.value = null
                                prefs.setActiveMemberId(null)
                                prefs.saveCachedFamilyMembers(savedFamilyId, serverMembers)
                            } else {
                                prefs.saveCachedFamilyMembers(
                                    savedFamilyId,
                                    serverMembers,
                                    preserveMemberId = savedMemberId
                                )
                            }
                        }
                    } else {
                        val rejoined = resolveInitialFamilySpace(savedFamilyId)
                        val targetFamId = rejoined?.takeIf { it.isNotBlank() } ?: savedFamilyId
                        if (targetFamId != savedFamilyId) {
                            _activeFamilyId.value = targetFamId
                            prefs.setActiveFamilyId(targetFamId)
                        }
                        cloudRepo.syncJoiningMembers(targetFamId, currentUserId)
                        cloudRepo.syncSingleMemberUid(targetFamId, savedMemberId, currentUserId)
                        cloudRepo.syncJoiningChats(targetFamId, currentUserId)
                    }
                    return@launch
                }

                if (hasBootFastSelector && bootActiveFamilyId != null) {
                    val savedFamilyId = bootActiveFamilyId
                    delay(350L)
                    val serverMembers = cloudRepo.fetchMembersOnceFromServer(savedFamilyId, currentUserId)
                    if (serverMembers.isEmpty()) {
                        cloudRepo.syncJoiningMembers(savedFamilyId, currentUserId)
                        cloudRepo.syncJoiningChats(savedFamilyId, currentUserId)
                    } else {
                        prefs.saveCachedFamilyMembers(savedFamilyId, serverMembers)
                    }
                    return@launch
                }

                val snapshot = prefs.readInitialSessionSnapshot()
                val savedFamilyId = snapshot.activeFamilyId
                val savedMemberId = snapshot.activeMemberId
                val cachedForSavedFamily = if (!savedFamilyId.isNullOrBlank() && snapshot.cachedFamilyId == savedFamilyId) {
                    snapshot.cachedMembers
                } else {
                    emptyList()
                }

                // Fast Path 1: Returning device with a saved family & profile -> open Chats immediately
                // from local DataStore / Firestore disk cache (zero network blocking, no 3s white spinner).
                if (!savedFamilyId.isNullOrBlank() && !savedMemberId.isNullOrBlank()) {
                    val instantMembers = cachedForSavedFamily.ifEmpty {
                        cloudRepo.fetchMembersFromCache(savedFamilyId, currentUserId)
                    }
                    if (instantMembers.any { it.id == savedMemberId }) {
                        _bootstrapFallbackMembers.value = savedFamilyId to instantMembers
                        _activeFamilyId.value = savedFamilyId
                        _isResolvingFamilySpace.value = false
                        _isMembersLoading.value = false
                        _activeMemberId.value = savedMemberId
                        withTimeoutOrNull(2_000L) {
                            activeMember.first { it?.id == savedMemberId }
                        }
                        _isSessionReady.value = true
                        if (cachedForSavedFamily.isEmpty()) {
                            viewModelScope.launch {
                                prefs.saveCachedFamilyMembers(
                                    savedFamilyId,
                                    instantMembers,
                                    preserveMemberId = savedMemberId
                                )
                            }
                        }
                        viewModelScope.launch {
                            syncPushAndBackgroundWork(savedFamilyId, savedMemberId)
                            if (_isAppForeground.value && isShowLastSeenEnabledFor(savedMemberId)) {
                                startPresenceHeartbeat()
                            }
                            val serverMembers = cloudRepo.fetchMembersOnceFromServer(savedFamilyId, currentUserId)
                            if (serverMembers.isNotEmpty()) {
                                if (serverMembers.any { it.id == savedMemberId }) {
                                    prefs.saveCachedFamilyMembers(
                                        savedFamilyId,
                                        serverMembers,
                                        preserveMemberId = savedMemberId
                                    )
                                } else {
                                    cloudRepo.syncJoiningMembers(savedFamilyId, currentUserId)
                                    cloudRepo.syncSingleMemberUid(savedFamilyId, savedMemberId, currentUserId)
                                    cloudRepo.syncJoiningChats(savedFamilyId, currentUserId)
                                    // Verify positively on server before ever wiping the saved profile
                                    val confirmedDeleted = cloudRepo.isMemberConfirmedDeletedOnServer(
                                        familyId = savedFamilyId,
                                        memberId = savedMemberId,
                                        uid = currentUserId
                                    )
                                    if (confirmedDeleted) {
                                        _bootstrapFallbackMembers.value = null
                                        _activeMemberId.value = null
                                        prefs.setActiveMemberId(null)
                                        prefs.saveCachedFamilyMembers(savedFamilyId, serverMembers)
                                    } else {
                                        prefs.saveCachedFamilyMembers(
                                            savedFamilyId,
                                            serverMembers,
                                            preserveMemberId = savedMemberId
                                        )
                                    }
                                }
                            } else {
                                // Device UID may need re-syncing (e.g. after reinstall with Auto Backup)
                                val rejoined = resolveInitialFamilySpace(savedFamilyId)
                                val targetFamId = rejoined?.takeIf { it.isNotBlank() } ?: savedFamilyId
                                if (targetFamId != savedFamilyId) {
                                    _activeFamilyId.value = targetFamId
                                    prefs.setActiveFamilyId(targetFamId)
                                }
                                cloudRepo.syncJoiningMembers(targetFamId, currentUserId)
                                cloudRepo.syncSingleMemberUid(targetFamId, savedMemberId, currentUserId)
                                cloudRepo.syncJoiningChats(targetFamId, currentUserId)
                            }
                        }
                        return@launch
                    }
                }

                // Fast Path 2: Selector screen with cached family profiles -> show profiles immediately
                if (savedMemberId == null && !savedFamilyId.isNullOrBlank() && cachedForSavedFamily.isNotEmpty()) {
                    _bootstrapFallbackMembers.value = savedFamilyId to cachedForSavedFamily
                    _activeFamilyId.value = savedFamilyId
                    _isResolvingFamilySpace.value = false
                    _isMembersLoading.value = false
                    _isSessionReady.value = true
                    viewModelScope.launch {
                        val serverMembers = cloudRepo.fetchMembersOnceFromServer(savedFamilyId, currentUserId)
                        if (serverMembers.isEmpty()) {
                            cloudRepo.syncJoiningMembers(savedFamilyId, currentUserId)
                            cloudRepo.syncJoiningChats(savedFamilyId, currentUserId)
                        } else {
                            prefs.saveCachedFamilyMembers(savedFamilyId, serverMembers)
                        }
                    }
                    return@launch
                }

                if (savedMemberId == null) {
                    // Show ProfileSelectorScreen immediately in "Cargando perfiles…" state on fresh device
                    _profileLoadError.value = null
                    _isMembersLoading.value = true
                    _isSessionReady.value = true
                }
                var resolveResult = resolveInitialFamilySpaceDetailed(savedFamilyId)
                var resolvedFamilyId = resolveResult.first
                var lastResolveError = resolveResult.second
                // At most 1 quick retry for transient network/gRPC cold-start hiccups; stop immediately on permanent errors.
                if (resolvedFamilyId == null && !isNonRetryableFirestoreError(lastResolveError)) {
                    delay(800L)
                    resolveResult = resolveInitialFamilySpaceDetailed(savedFamilyId)
                    resolvedFamilyId = resolveResult.first
                    if (resolveResult.second != null) {
                        lastResolveError = resolveResult.second
                    }
                }
                if (resolvedFamilyId != null) {
                    _profileLoadError.value = null
                    // On fresh installs, bootstrap member reads succeed immediately via isFamilyMemberByDoc(familyId)
                    // without waiting for per-member memberUids writes to finish first.
                    val prefetchedMembers = if (savedFamilyId.isNullOrBlank()) {
                        cloudRepo.fetchBootstrapMembersById(resolvedFamilyId)
                            .ifEmpty { cloudRepo.fetchMembersOnceFromServer(resolvedFamilyId, currentUserId) }
                    } else {
                        cloudRepo.fetchMembersOnceFromServer(resolvedFamilyId, currentUserId)
                            .ifEmpty { cloudRepo.fetchBootstrapMembersById(resolvedFamilyId) }
                    }
                    if (prefetchedMembers.isNotEmpty()) {
                        _bootstrapFallbackMembers.value = resolvedFamilyId to prefetchedMembers
                        prefs.saveCachedFamilyMembers(
                            resolvedFamilyId,
                            prefetchedMembers,
                            preserveMemberId = savedMemberId
                        )
                    }
                    // Set _activeFamilyId BEFORE clearing _isResolvingFamilySpace so combine() never sees (null, false)
                    _activeFamilyId.value = resolvedFamilyId
                    _isResolvingFamilySpace.value = false
                    if (prefetchedMembers.isNotEmpty()) {
                        _isMembersLoading.value = false
                    }
                    prefs.setActiveFamilyId(resolvedFamilyId)
                    // Sync members & chats in the background so the profile selector never waits on docs
                    viewModelScope.launch {
                        cloudRepo.syncJoiningMembers(resolvedFamilyId, currentUserId)
                        if (!savedMemberId.isNullOrBlank()) {
                            cloudRepo.syncSingleMemberUid(resolvedFamilyId, savedMemberId, currentUserId)
                        }
                        cloudRepo.syncJoiningChats(resolvedFamilyId, currentUserId)
                    }
                    if (savedMemberId != null) {
                        // Wait for non-empty member list or fetch the saved member directly by ID
                        var loadedMembers = prefetchedMembers
                        if (loadedMembers.none { it.id == savedMemberId }) {
                            val directMember = cloudRepo.fetchMemberByIdFromServer(resolvedFamilyId, savedMemberId)
                            if (directMember != null) {
                                loadedMembers = loadedMembers + directMember
                            }
                        }
                        if (loadedMembers.none { it.id == savedMemberId }) {
                            val observed = runCatching {
                                withTimeoutOrNull(4_000L) {
                                    cloudRepo.observeMembers(resolvedFamilyId, currentUserId)
                                        .first { list -> list.any { it.id == savedMemberId } }
                                }
                            }.getOrNull().orEmpty()
                            if (observed.isNotEmpty()) {
                                loadedMembers = observed
                            }
                        }
                        if (loadedMembers.any { it.id == savedMemberId }) {
                            _bootstrapFallbackMembers.value = resolvedFamilyId to loadedMembers
                            prefs.saveSelectedSession(resolvedFamilyId, savedMemberId, loadedMembers)
                            _activeMemberId.value = savedMemberId
                            withTimeoutOrNull(2_000L) {
                                activeMember.first { it?.id == savedMemberId }
                            }
                            syncPushAndBackgroundWork(resolvedFamilyId, savedMemberId)
                            if (_isAppForeground.value && isShowLastSeenEnabledFor(savedMemberId)) {
                                startPresenceHeartbeat()
                            }
                        } else if (cloudRepo.isMemberConfirmedDeletedOnServer(
                                familyId = resolvedFamilyId,
                                memberId = savedMemberId,
                                uid = currentUserId
                            )
                        ) {
                            prefs.setActiveMemberId(null)
                        }
                    }
                } else {
                    _profileLoadError.value = formatProfileLoadError(lastResolveError)
                    _isResolvingFamilySpace.value = false
                    _isMembersLoading.value = false
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val errMsg = formatProfileLoadError(e)
                _profileLoadError.value = errMsg
                showMessage(errMsg)
                _isMembersLoading.value = false
            } finally {
                _isResolvingFamilySpace.value = false
                if (_activeFamilyId.value.isNullOrBlank()) {
                    _isMembersLoading.value = false
                }
                _isSessionReady.value = true
            }
        }

        // Automatically backfill new family UIDs onto existing docs only when a new phone joins (owner device only)
        viewModelScope.launch {
            activeFamily.collect { fam ->
                if (fam != null && fam.ownerUid == currentUserId) {
                    val uids = fam.memberUids.toSet()
                    val uidsHash = "${fam.id}:${uids.sorted().joinToString(",")}"
                    val savedHash = prefs.lastSyncedUidsHashFlow.first()
                    if (uids.size > 1 && uids != lastSyncedMemberUids && uidsHash != savedHash) {
                        lastSyncedMemberUids = uids
                        cloudRepo.syncMemberUidsAcrossExistingDocs(fam.id, fam.memberUids, currentUserId)
                        prefs.setLastSyncedUidsHash(uidsHash)
                    } else if (uidsHash == savedHash) {
                        lastSyncedMemberUids = uids
                    }
                }
            }
        }

        // One-time admin cleanup: migrate any data from duplicate "Papa" ("Familiar") to primary admin and delete duplicate
        viewModelScope.launch {
            val cleanedDuplicateIds = mutableSetOf<String>()
            var cleanupInProgress = false
            combine(activeFamily, activeMember, rawMembers) { fam, active, membersList ->
                Triple(fam, active, membersList)
            }.collect { (fam, active, membersList) ->
                if (cleanupInProgress || fam == null || active == null || !active.isAdmin) return@collect
                val duplicate = membersList.firstOrNull {
                    it.id !in cleanedDuplicateIds && isDuplicateLegacyPapaProfile(it, membersList)
                } ?: return@collect
                val primaryAdmin = membersList.firstOrNull { it.isAdmin && it.id == active.id }
                    ?: membersList.firstOrNull { it.isAdmin }
                    ?: return@collect

                cleanedDuplicateIds.add(duplicate.id)
                cleanupInProgress = true
                try {
                    migrateAndRemoveDuplicateProfile(
                        famId = fam.id,
                        duplicate = duplicate,
                        targetAdmin = primaryAdmin
                    )
                } finally {
                    cleanupInProgress = false
                }
            }
        }

        // Keep WebRTC TURN relay credentials synced with local preferences
        viewModelScope.launch {
            combine(turnUrl, turnUsername, turnCredential) { url, user, cred ->
                Triple(url, user, cred)
            }.collect { (url, user, cred) ->
                webRtcCallManager.setCustomTurnConfig(url, user, cred)
            }
        }

        // Real-time Android notifications & automatic DELIVERED receipt for incoming messages
        viewModelScope.launch {
            val sessionStartedAt = System.currentTimeMillis()
            val seenMessageIds = mutableSetOf<String>()
            val deliveredAttemptedIds = mutableSetOf<String>()
            var seededForMemberId: String? = null
            combine(
                allMessages,
                rawChats,
                members,
                _activeMemberId,
                notificationsEnabled
            ) { msgs, chats, membersList, meId, notifEnabled ->
                Tuple5(msgs, chats, membersList, meId, notifEnabled)
            }.collect { (msgs, chats, membersList, meId, notifEnabled) ->
                if (meId == null) {
                    seededForMemberId = null
                    seenMessageIds.clear()
                    deliveredAttemptedIds.clear()
                    return@collect
                }
                val chatMap = chats.associateBy { it.id }
                val famId = _activeFamilyId.value
                if (!famId.isNullOrBlank()) {
                    msgs.forEach { msg ->
                        val chat = chatMap[msg.chatId] ?: return@forEach
                        val participants = JsonHelpers.decodeStringList(chat.participantIdsJson)
                        if (participants.contains(meId) &&
                            msg.senderId != meId &&
                            msg.type != MessageType.DELETED &&
                            msg.status == MessageStatus.SENT &&
                            deliveredAttemptedIds.add(msg.id)
                        ) {
                            viewModelScope.launch {
                                cloudRepo.markMessageDeliveredToRecipient(famId, msg.id)
                            }
                        }
                    }
                }
                if (seededForMemberId != meId) {
                    seededForMemberId = meId
                    seenMessageIds.clear()
                    msgs.forEach { seenMessageIds.add(it.id) }
                    return@collect
                }
                val memberMap = membersList.associateBy { it.id }
                msgs.forEach { msg ->
                    if (seenMessageIds.add(msg.id)) {
                        val chat = chatMap[msg.chatId] ?: return@forEach
                        val participants = JsonHelpers.decodeStringList(chat.participantIdsJson)
                        if (notifEnabled &&
                            !mutedChatIds.value.contains(msg.chatId) &&
                            participants.contains(meId) &&
                            msg.senderId != meId &&
                            msg.type != MessageType.DELETED &&
                            msg.timestamp >= sessionStartedAt - 15_000L &&
                            (!_isAppForeground.value || _activeChatId.value != msg.chatId)
                        ) {
                            val senderName = memberMap[msg.senderId]?.name ?: "Familiar"
                            val chatTitle = if (chat.type == ChatType.GROUP) chat.title else senderName
                            val body = when (msg.type) {
                                MessageType.IMAGE -> if (msg.text.isNotBlank()) "📷 ${msg.text}" else "📷 Foto"
                                MessageType.VIDEO -> if (msg.text.isNotBlank()) "🎬 ${msg.text}" else "🎬 Video"
                                MessageType.VOICE -> "🎤 Nota de voz (${TimeFormatters.formatDuration(msg.voiceDurationSec)})"
                                MessageType.LOCATION -> "📍 ${msg.locationName ?: "Ubicación"}"
                                MessageType.DOCUMENT -> "📄 ${msg.fileName ?: "Documento"}"
                                MessageType.POLL -> "📊 ${msg.pollQuestion ?: "Encuesta"}"
                                MessageType.TEXT -> MessageFormatter.stripMarkdownForPreview(msg.text)
                                MessageType.DELETED -> ""
                            }
                            if (body.isNotBlank()) {
                                viewModelScope.launch {
                                    prefs.updateLastNotifiedTimestampIfNewer(msg.timestamp)
                                }
                                FamiChatNotificationHelper.notifyNewMessage(
                                    context = appContext,
                                    chatId = msg.chatId,
                                    chatTitle = chatTitle,
                                    senderName = senderName,
                                    body = body,
                                    isGroup = chat.type == ChatType.GROUP
                                )
                            }
                        }
                    }
                }
            }
        }

        // Ringtone + system notification on incoming call
        viewModelScope.launch {
            var activeIncomingNotifCallId: String? = null
            incomingCall.collect { incoming ->
                if (incoming != null) {
                    voiceAudio.startRingtone(isIncoming = true)
                    if (notificationsEnabled.value && !_isAppForeground.value) {
                        val callerName = members.value.find { it.id == incoming.callerId }?.name ?: incoming.title
                        activeIncomingNotifCallId = incoming.id
                        FamiChatNotificationHelper.notifyIncomingCall(
                            context = appContext,
                            callId = incoming.id,
                            chatId = incoming.chatId,
                            callerName = callerName,
                            isVideo = incoming.callType == CallType.VIDEO
                        )
                    }
                } else {
                    activeIncomingNotifCallId?.let { FamiChatNotificationHelper.cancelCallNotification(appContext, it) }
                    activeIncomingNotifCallId = null
                    if (_activeCall.value == null) {
                        voiceAudio.stopRingtone()
                    }
                }
            }
        }
    }

    private fun currentMemberUids(): List<String> =
        activeFamily.value?.memberUids?.ifEmpty { listOf(currentUserId) } ?: listOf(currentUserId)

    private fun buildLastMessagePreview(
        msg: ChatMessageEntity?,
        membersMap: Map<String, FamilyMemberEntity>,
        currentId: String,
        chatType: ChatType
    ): String {
        if (msg == null) return "Sin mensajes aún"
        val prefix = if (chatType == ChatType.GROUP && msg.senderId != currentId) {
            val senderName = membersMap[msg.senderId]?.name ?: "Alguien"
            "$senderName: "
        } else if (msg.senderId == currentId) {
            "Tú: "
        } else ""

        val body = when (msg.type) {
            MessageType.DELETED -> "🚫 Mensaje eliminado"
            MessageType.IMAGE -> if (msg.text.isNotBlank()) "📷 ${MessageFormatter.stripMarkdownForPreview(msg.text)}" else "📷 Foto"
            MessageType.VIDEO -> if (msg.text.isNotBlank()) "🎬 ${MessageFormatter.stripMarkdownForPreview(msg.text)}" else "🎬 Video"
            MessageType.VOICE -> "🎤 Nota de voz (${TimeFormatters.formatDuration(msg.voiceDurationSec)})"
            MessageType.LOCATION -> {
                val isLive = msg.locationLiveUntil != null && msg.locationLiveUntil > System.currentTimeMillis()
                if (isLive) "📍 Ubicación en tiempo real" else "📍 ${msg.locationName ?: "Ubicación compartida"}"
            }
            MessageType.DOCUMENT -> "📄 ${msg.fileName ?: "Documento"}"
            MessageType.POLL -> "📊 Encuesta: ${msg.pollQuestion.orEmpty()}"
            MessageType.TEXT -> MessageFormatter.stripMarkdownForPreview(msg.text)
        }
        return "$prefix$body"
    }

    fun showMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    // --- Family Setup / Join ---

    private suspend fun resolveInitialFamilySpaceDetailed(savedFamilyId: String?): Pair<String?, Throwable?> {
        if (savedFamilyId.isNullOrBlank()) {
            // Fresh install: join fixed family NIDO01 directly without wasting a families query on a brand-new UID.
            val ensuredResult = withTimeoutOrNull(14_000L) {
                cloudRepo.ensureFixedFamily(currentUserId, syncMembersInline = false)
            } ?: Result.failure(IllegalStateException("Tiempo de espera agotado al conectar con la familia."))
            val ensuredId = ensuredResult.getOrNull()
            if (!ensuredId.isNullOrBlank()) {
                return ensuredId to null
            }
            val ensureErr = ensuredResult.exceptionOrNull()
            val fallbackFams = withTimeoutOrNull(5_000L) {
                cloudRepo.fetchMyFamiliesOnce(currentUserId)
            } ?: emptyList()
            val fallbackId = fallbackFams.firstOrNull { it.inviteCode == "NIDO01" }?.id
                ?: fallbackFams.firstOrNull()?.id
            return fallbackId to (if (fallbackId == null) ensureErr else null)
        }

        val fams = withTimeoutOrNull(6_000L) {
            cloudRepo.fetchMyFamiliesOnce(currentUserId)
        } ?: emptyList()
        val verifiedSavedFamilyId = savedFamilyId.takeIf { id -> fams.any { it.id == id } }
        val directFound = verifiedSavedFamilyId ?: fams.firstOrNull { it.inviteCode == "NIDO01" }?.id
        if (!directFound.isNullOrBlank()) {
            return directFound to null
        }
        val ensuredResult = withTimeoutOrNull(14_000L) {
            cloudRepo.ensureFixedFamily(currentUserId, syncMembersInline = false)
        } ?: Result.failure(IllegalStateException("Tiempo de espera agotado al conectar con la familia."))
        val resolved = ensuredResult.getOrNull()
            ?: fams.firstOrNull()?.id
            ?: savedFamilyId
        return resolved to (if (resolved == null) ensuredResult.exceptionOrNull() else null)
    }

    private suspend fun resolveInitialFamilySpace(savedFamilyId: String?): String? =
        resolveInitialFamilySpaceDetailed(savedFamilyId).first

    fun retryLoadProfiles() {
        viewModelScope.launch {
            _profileLoadError.value = null
            _isResolvingFamilySpace.value = true
            _isMembersLoading.value = true
            try {
                val currentFamId = _activeFamilyId.value
                var retryError: Throwable? = null
                val targetFamId = if (currentFamId.isNullOrBlank()) {
                    val savedFamId = prefs.activeFamilyIdFlow.first()
                    val (fid, err) = resolveInitialFamilySpaceDetailed(savedFamId)
                    retryError = err
                    fid?.also {
                        _activeFamilyId.value = it
                        prefs.setActiveFamilyId(it)
                    }
                } else {
                    val code = activeFamily.value?.inviteCode?.ifBlank { "NIDO01" } ?: "NIDO01"
                    val joinRes = withTimeoutOrNull(10_000L) {
                        cloudRepo.joinFamilyByInviteCode(currentUserId, code, syncMembersInline = false)
                    }
                    retryError = joinRes?.exceptionOrNull()
                    joinRes?.getOrNull() ?: currentFamId
                }
                _isResolvingFamilySpace.value = false
                if (!targetFamId.isNullOrBlank()) {
                    val fallback = cloudRepo.fetchBootstrapMembersById(targetFamId)
                        .ifEmpty { cloudRepo.fetchMembersOnceFromServer(targetFamId, currentUserId) }
                    if (fallback.isNotEmpty()) {
                        _profileLoadError.value = null
                        _bootstrapFallbackMembers.value = targetFamId to fallback
                        _isMembersLoading.value = false
                    } else if (retryError != null) {
                        _profileLoadError.value = formatProfileLoadError(retryError)
                    }
                    _membersRetryTick.value += 1
                    viewModelScope.launch {
                        cloudRepo.syncJoiningMembers(targetFamId, currentUserId)
                        cloudRepo.syncJoiningChats(targetFamId, currentUserId)
                    }
                } else {
                    _profileLoadError.value = formatProfileLoadError(retryError)
                    _isMembersLoading.value = false
                }
            } finally {
                _isResolvingFamilySpace.value = false
                if (_activeFamilyId.value.isNullOrBlank()) {
                    _isMembersLoading.value = false
                }
            }
        }
    }

    fun selectFamilySpace(familyId: String) {
        _showFamilySpacesSetup.value = false
        _activeFamilyId.value = familyId
        _activeMemberId.value = null
        viewModelScope.launch {
            prefs.setActiveFamilyId(familyId)
            prefs.setActiveMemberId(null)
        }
    }

    fun clearActiveFamilySpace() {
        val prevFamId = _activeFamilyId.value
        val prevMemberId = _activeMemberId.value
        _showFamilySpacesSetup.value = true
        _activeFamilyId.value = null
        _activeMemberId.value = null
        _activeChatId.value = null
        stopPlayback()
        cancelVoiceRecording()
        PushRegistrationManager.cancelBackgroundSync(appContext)
        viewModelScope.launch {
            withContext(NonCancellable) {
                if (prevFamId != null && prevMemberId != null) {
                    PushRegistrationManager.unregisterMemberToken(cloudRepo, prefs, prevFamId, prevMemberId)
                }
                if (prevFamId != null) {
                    PushRegistrationManager.unsubscribeFromFamilyTopic(prevFamId)
                }
                prefs.setActiveFamilyId(null)
                prefs.setActiveMemberId(null)
            }
        }
    }

    fun createNewFamily(familyName: String, adminName: String, adminEmoji: String, adminColorHex: String) {
        if (_isFamilyActionBusy.value) return
        viewModelScope.launch {
            _isFamilyActionBusy.value = true
            val res = cloudRepo.createFamily(
                uid = currentUserId,
                familyName = familyName,
                adminName = adminName,
                adminEmoji = adminEmoji,
                adminColorHex = adminColorHex,
                autoCreateAdminMember = false
            )
            _isFamilyActionBusy.value = false
            res.onSuccess { (famId, _) ->
                _showFamilySpacesSetup.value = false
                _activeFamilyId.value = famId
                prefs.setActiveFamilyId(famId)
                _activeMemberId.value = null
                prefs.setActiveMemberId(null)
                showMessage("Familia creada y sincronizada en la nube")
            }.onFailure { err ->
                showMessage(err.localizedMessage ?: "No se pudo crear la familia")
            }
        }
    }

    fun joinFamilyWithCode(code: String) {
        if (_isFamilyActionBusy.value) return
        viewModelScope.launch {
            _isFamilyActionBusy.value = true
            val res = cloudRepo.joinFamilyByInviteCode(currentUserId, code)
            _isFamilyActionBusy.value = false
            res.onSuccess { famId ->
                _showFamilySpacesSetup.value = false
                _activeFamilyId.value = famId
                prefs.setActiveFamilyId(famId)
                _activeMemberId.value = null
                prefs.setActiveMemberId(null)
                showMessage("Te uniste a la familia. Selecciona tu perfil.")
            }.onFailure { err ->
                showMessage(err.localizedMessage ?: "No se pudo unir con ese código")
            }
        }
    }

    fun registerSelfProfile(
        name: String,
        roleLabel: String,
        colorHex: String,
        inviteCodeInput: String = "",
        onSuccess: () -> Unit
    ) {
        showMessage("Solo el administrador puede crear perfiles en esta familia.")
        return
    }

    fun toggleTheme() {
        viewModelScope.launch {
            val currentMode = prefs.themeModeFlow.first()
            val currentlyDark = when (currentMode) {
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
                AppThemeMode.SYSTEM -> {
                    val nightMask = appContext.resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK
                    nightMask == android.content.res.Configuration.UI_MODE_NIGHT_YES
                }
            }
            prefs.setThemeMode(if (currentlyDark) AppThemeMode.LIGHT else AppThemeMode.DARK)
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            prefs.setThemeMode(mode)
        }
    }

    fun setFontScale(scale: AppFontScale) {
        viewModelScope.launch {
            prefs.setFontScale(scale)
        }
    }

    private fun isShowLastSeenEnabledFor(memberId: String): Boolean {
        val pending = _pendingShowLastSeenOverride.value
        if (pending != null && pending.first == memberId) return pending.second
        val raw = rawMembers.value.find { it.id == memberId }
            ?: _bootstrapFallbackMembers.value?.second?.find { it.id == memberId }
        return raw?.showLastSeen ?: true
    }

    fun setAppForeground(inForeground: Boolean) {
        if (_isAppForeground.value == inForeground) return
        _isAppForeground.value = inForeground
        com.example.push.FamiChatAppRuntimeState.isAppForeground = inForeground
        if (inForeground) {
            val memberId = _activeMemberId.value
            if (memberId != null && isShowLastSeenEnabledFor(memberId)) {
                startPresenceHeartbeat()
            }
            if (_activeFamilyId.value != null && memberId != null && notificationsEnabled.value && realtimeBackgroundServiceEnabled.value) {
                PushRegistrationManager.startRealtimeService(appContext)
            } else {
                PushRegistrationManager.stopRealtimeService(appContext)
            }
        } else {
            heartbeatJob?.cancel()
            heartbeatJob = null
            val famId = _activeFamilyId.value ?: return
            val memberId = _activeMemberId.value ?: return
            if (notificationsEnabled.value) {
                if (realtimeBackgroundServiceEnabled.value) {
                    PushRegistrationManager.startRealtimeService(appContext)
                    PushRegistrationManager.scheduleBackgroundCheckOnStop(appContext)
                } else {
                    PushRegistrationManager.stopRealtimeService(appContext)
                }
                PushRegistrationManager.schedulePeriodicBackgroundSync(appContext)
            }
            val raw = rawMembers.value.find { it.id == memberId }
            val awayLabel = offlineStatusLabelFor(raw?.lastSeenText.orEmpty())
            viewModelScope.launch {
                withContext(NonCancellable) {
                    cloudRepo.updateMemberPresence(
                        familyId = famId,
                        memberId = memberId,
                        isOnline = false,
                        lastSeenText = awayLabel,
                        liveStatus = PresenceStatus.LAST_SEEN,
                        liveChatId = null,
                        liveStatusAt = System.currentTimeMillis(),
                        callerUid = currentUserId
                    )
                }
            }
        }
    }

    private fun syncPushAndBackgroundWork(familyId: String, memberId: String) {
        viewModelScope.launch {
            if (notificationsEnabled.value) {
                if (realtimeBackgroundServiceEnabled.value) {
                    PushRegistrationManager.startRealtimeService(appContext)
                } else {
                    PushRegistrationManager.stopRealtimeService(appContext)
                }
                PushRegistrationManager.syncFcmTokenAndTopic(appContext, cloudRepo, prefs, familyId, memberId)
                PushRegistrationManager.schedulePeriodicBackgroundSync(appContext)
            }
        }
    }

    private fun startPresenceHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launch {
            while (_isAppForeground.value) {
                val famId = _activeFamilyId.value
                val memberId = _activeMemberId.value
                if (famId != null && memberId != null) {
                    if (!isShowLastSeenEnabledFor(memberId)) {
                        break
                    }
                    val now = System.currentTimeMillis()
                    val raw = rawMembers.value.find { it.id == memberId }
                    val keepingLiveAction = raw != null &&
                        (raw.liveStatus == PresenceStatus.TYPING || raw.liveStatus == PresenceStatus.RECORDING) &&
                        !raw.liveChatId.isNullOrBlank() &&
                        (now - raw.liveStatusAt) < PresenceResolver.LIVE_ACTION_TTL_MS
                    val nextStatus = if (keepingLiveAction) raw!!.liveStatus else PresenceStatus.ONLINE
                    val nextChatId = if (keepingLiveAction) raw?.liveChatId else null
                    val onlineLabel = onlineStatusLabelFor(raw?.lastSeenText.orEmpty())
                    cloudRepo.updateMemberPresence(
                        familyId = famId,
                        memberId = memberId,
                        isOnline = true,
                        lastSeenText = onlineLabel,
                        liveStatus = nextStatus,
                        liveChatId = nextChatId,
                        liveStatusAt = now,
                        linkedAuthUid = currentUserId,
                        callerUid = currentUserId
                    )
                }
                delay(PresenceResolver.HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    private fun onlineStatusLabelFor(currentLabel: String): String {
        val trimmed = currentLabel.trim()
        return if (trimmed.isBlank() ||
            trimmed == "Desconectado" ||
            trimmed == "No se ha conectado" ||
            trimmed.startsWith("Últ.")
        ) {
            "En línea"
        } else {
            trimmed
        }
    }

    private fun offlineStatusLabelFor(currentLabel: String): String {
        val trimmed = currentLabel.trim()
        return if (trimmed.isBlank() ||
            trimmed == "En línea" ||
            trimmed == "En casa" ||
            trimmed == "No se ha conectado" ||
            trimmed.startsWith("Últ.")
        ) {
            "Desconectado"
        } else {
            trimmed
        }
    }

    fun selectProfile(memberId: String) {
        _activeMemberId.value = memberId
        _activeChatId.value = null
        _searchQuery.value = ""
        val famIdSnapshot = _activeFamilyId.value
        val currentListSnapshot = members.value
        viewModelScope.launch {
            withContext(NonCancellable) {
                val famId = famIdSnapshot ?: _activeFamilyId.value
                val listToCache = currentListSnapshot.ifEmpty { members.value }
                if (!famId.isNullOrBlank()) {
                    prefs.saveSelectedSession(famId, memberId, listToCache)
                } else {
                    prefs.setActiveMemberId(memberId)
                }
            }
            val famId = _activeFamilyId.value ?: return@launch
            cloudRepo.syncSingleMemberUid(famId, memberId, currentUserId)
            val now = System.currentTimeMillis()
            val raw = rawMembers.value.find { it.id == memberId }
            val allowPresence = isShowLastSeenEnabledFor(memberId)
            val nextLabel = if (allowPresence) {
                onlineStatusLabelFor(raw?.lastSeenText.orEmpty())
            } else {
                offlineStatusLabelFor(raw?.lastSeenText.orEmpty())
            }
            cloudRepo.updateMemberPresence(
                familyId = famId,
                memberId = memberId,
                isOnline = allowPresence,
                lastSeenText = nextLabel,
                liveStatus = if (allowPresence) PresenceStatus.ONLINE else PresenceStatus.LAST_SEEN,
                liveChatId = null,
                liveStatusAt = now,
                linkedAuthUid = currentUserId,
                callerUid = currentUserId
            )
            if (_isAppForeground.value && allowPresence) {
                startPresenceHeartbeat()
            }
            syncPushAndBackgroundWork(famId, memberId)
        }
    }

    fun logoutToSelector() {
        val famId = _activeFamilyId.value
        val prevMemberId = _activeMemberId.value
        val prevRaw = prevMemberId?.let { id -> rawMembers.value.find { it.id == id } }
        heartbeatJob?.cancel()
        heartbeatJob = null
        _activeMemberId.value = null
        _activeChatId.value = null
        com.example.push.FamiChatAppRuntimeState.activeChatId = null
        stopPlayback()
        cancelVoiceRecording()
        PushRegistrationManager.cancelBackgroundSync(appContext)
        viewModelScope.launch {
            prefs.setActiveMemberId(null)
            if (famId != null && prevMemberId != null) {
                withContext(NonCancellable) {
                    PushRegistrationManager.unregisterMemberToken(cloudRepo, prefs, famId, prevMemberId)
                    cloudRepo.updateMemberPresence(
                        familyId = famId,
                        memberId = prevMemberId,
                        isOnline = false,
                        lastSeenText = offlineStatusLabelFor(prevRaw?.lastSeenText.orEmpty()),
                        liveStatus = PresenceStatus.LAST_SEEN,
                        liveChatId = null,
                        liveStatusAt = System.currentTimeMillis(),
                        callerUid = currentUserId
                    )
                }
            }
        }
    }

    fun selectTab(tab: MainTab) {
        _selectedTab.value = tab
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        val famId = _activeFamilyId.value
        val memberId = _activeMemberId.value
        viewModelScope.launch {
            prefs.setNotificationsEnabled(enabled)
            if (enabled) {
                if (famId != null && memberId != null) {
                    if (realtimeBackgroundServiceEnabled.value) {
                        PushRegistrationManager.startRealtimeService(appContext)
                    } else {
                        PushRegistrationManager.stopRealtimeService(appContext)
                    }
                    PushRegistrationManager.syncFcmTokenAndTopic(appContext, cloudRepo, prefs, famId, memberId)
                    PushRegistrationManager.schedulePeriodicBackgroundSync(appContext)
                }
            } else {
                PushRegistrationManager.cancelBackgroundSync(appContext)
                if (famId != null && memberId != null) {
                    PushRegistrationManager.unregisterMemberToken(cloudRepo, prefs, famId, memberId)
                }
            }
        }
    }

    fun setRealtimeBackgroundServiceEnabled(enabled: Boolean) {
        val famId = _activeFamilyId.value
        val memberId = _activeMemberId.value
        viewModelScope.launch {
            prefs.setRealtimeBackgroundServiceEnabled(enabled)
            if (enabled && notificationsEnabled.value && famId != null && memberId != null) {
                PushRegistrationManager.startRealtimeService(appContext)
                _snackbarMessage.value = "Conexión permanente activada en este teléfono"
            } else {
                PushRegistrationManager.stopRealtimeService(appContext)
                _snackbarMessage.value = "Ahorro de batería activado (alertas desde el servidor)"
            }
        }
    }

    fun setChatWallpaper(chatId: String, wallpaperKey: String) {
        viewModelScope.launch {
            prefs.setChatWallpaper(chatId, wallpaperKey)
        }
    }

    fun setChatNotificationTone(chatId: String, toneUri: String) {
        viewModelScope.launch {
            prefs.setChatNotificationTone(chatId, toneUri)
        }
    }

    fun togglePinChat(chatId: String) {
        viewModelScope.launch {
            val nowPinned = prefs.togglePinnedChat(chatId)
            showMessage(if (nowPinned) "Chat fijado en la parte superior" else "Chat desfijado")
        }
    }

    fun toggleMuteChat(chatId: String) {
        viewModelScope.launch {
            val nowMuted = prefs.toggleMutedChat(chatId)
            showMessage(if (nowMuted) "Notificaciones silenciadas en este chat" else "Notificaciones activadas en este chat")
        }
    }

    fun requestOpenChatFromNotification(chatId: String?) {
        if (!chatId.isNullOrBlank()) {
            _pendingNotificationChatId.value = chatId
        }
    }

    fun consumePendingNotificationChatId() {
        _pendingNotificationChatId.value = null
    }

    fun openChat(chatId: String) {
        _activeChatId.value = chatId
        com.example.push.FamiChatAppRuntimeState.activeChatId = chatId
        FamiChatNotificationHelper.cancelChatNotifications(appContext, chatId)
        markChatMessagesAsRead(chatId)
    }

    fun closeChat() {
        val prevChatId = _activeChatId.value
        _activeChatId.value = null
        com.example.push.FamiChatAppRuntimeState.activeChatId = null
        stopPlayback()
        cancelVoiceRecording()
        if (prevChatId != null) {
            updateLivePresence(prevChatId, PresenceStatus.ONLINE, forceClear = true)
        }
    }

    // --- Real Live Presence (Typing / Recording) ---

    fun updateLivePresence(chatId: String, status: PresenceStatus, forceClear: Boolean = false) {
        val famId = _activeFamilyId.value ?: return
        val memberId = _activeMemberId.value ?: return
        if (!isShowLastSeenEnabledFor(memberId)) return
        val now = System.currentTimeMillis()
        val key = "$chatId:${status.name}"

        if (!forceClear && status != PresenceStatus.ONLINE &&
            key == lastSentPresenceKey && (now - lastSentPresenceAt) < 4_000L
        ) {
            return
        }
        val raw = rawMembers.value.find { it.id == memberId }
        if (status == PresenceStatus.ONLINE &&
            raw != null &&
            raw.isOnline &&
            raw.liveStatus == PresenceStatus.ONLINE &&
            raw.liveChatId.isNullOrBlank()
        ) {
            return
        }
        lastSentPresenceKey = key
        lastSentPresenceAt = now

        val onlineLabel = onlineStatusLabelFor(raw?.lastSeenText.orEmpty())
        presenceClearJob?.cancel()
        viewModelScope.launch {
            cloudRepo.updateMemberPresence(
                familyId = famId,
                memberId = memberId,
                isOnline = status != PresenceStatus.LAST_SEEN,
                lastSeenText = if (status == PresenceStatus.LAST_SEEN) "Desconectado" else onlineLabel,
                liveStatus = status,
                liveChatId = if (status == PresenceStatus.ONLINE || status == PresenceStatus.LAST_SEEN) null else chatId,
                liveStatusAt = now,
                linkedAuthUid = currentUserId,
                callerUid = currentUserId
            )
        }
        if (status == PresenceStatus.TYPING || status == PresenceStatus.RECORDING) {
            presenceClearJob = viewModelScope.launch {
                delay(4500)
                updateLivePresence(chatId, PresenceStatus.ONLINE, forceClear = true)
            }
        }
    }

    fun markChatMessagesAsRead(chatId: String) {
        val famId = _activeFamilyId.value ?: return
        val currentId = _activeMemberId.value ?: return
        val clearedMap = chatClearedAtMap.value
        val localHiddenKeys = locallyHiddenMessageKeys.value
        val toMark = allMessages.value.filter {
            it.chatId == chatId &&
                it.senderId != currentId &&
                it.type != MessageType.DELETED &&
                isMessageVisibleForMember(it, currentId, clearedMap, localHiddenKeys) &&
                (it.status != MessageStatus.READ ||
                    !JsonHelpers.decodeStringList(it.readByMemberIdsJson).contains(currentId))
        }
        if (toMark.isEmpty()) return
        viewModelScope.launch {
            toMark.forEach { msg ->
                cloudRepo.markMessageReadByMember(famId, msg.id, currentId)
            }
        }
    }

    fun verifyPin(entered: String): Boolean = entered == masterPin.value

    fun verifySwitchKey(entered: String): Boolean =
        matchesSwitchKey(entered = entered, activeSwitchPin = switchPin.value)

    fun updateMasterPin(newPin: String) {
        if (newPin.length != 4 || !newPin.all(Char::isDigit)) {
            showMessage("El PIN debe tener exactamente 4 dígitos")
            return
        }
        val fam = activeFamily.value
        val famId = _activeFamilyId.value ?: return
        viewModelScope.launch {
            prefs.updateMasterPin(newPin)
            if (fam != null && fam.ownerUid == currentUserId) {
                val updates = mutableMapOf<String, Any>("masterPin" to newPin)
                val currentFamSwitch = fam.switchPin
                if (currentFamSwitch.length != 4 || !currentFamSwitch.all(Char::isDigit)) {
                    updates["switchPin"] = switchPin.value
                }
                val res = cloudRepo.updateFamilySettings(famId, updates)
                if (res.isSuccess) showMessage("Contraseña de Administrador actualizada")
                else showMessage("Contraseña guardada localmente")
            } else {
                showMessage("Contraseña actualizada en este dispositivo")
            }
        }
    }

    fun updateSwitchPin(newPin: String) {
        if (newPin.length != 4 || !newPin.all(Char::isDigit)) {
            showMessage("La clave de Switch debe tener exactamente 4 dígitos")
            return
        }
        val fam = activeFamily.value
        val famId = _activeFamilyId.value ?: return
        viewModelScope.launch {
            prefs.updateSwitchPin(newPin)
            if (fam != null && fam.ownerUid == currentUserId) {
                val updates = mutableMapOf<String, Any>("switchPin" to newPin)
                val currentFamMaster = fam.masterPin
                if (currentFamMaster.length != 4 || !currentFamMaster.all(Char::isDigit)) {
                    updates["masterPin"] = masterPin.value
                }
                val res = cloudRepo.updateFamilySettings(famId, updates)
                if (res.isSuccess) showMessage("Clave de cambio de perfil actualizada")
                else showMessage("Clave de Switch guardada localmente")
            } else {
                showMessage("Clave de cambio de perfil actualizada en este dispositivo")
            }
        }
    }

    fun updateOwnProfileDetails(
        nickname: String,
        avatarColorHex: String,
        showLastSeen: Boolean,
        statusText: String,
        bioText: String,
        newPhotoUri: Uri?,
        removePhoto: Boolean
    ) {
        val famId = _activeFamilyId.value ?: return
        val memberId = _activeMemberId.value ?: return
        _pendingShowLastSeenOverride.value = memberId to showLastSeen
        val now = System.currentTimeMillis()
        val currentMember = rawMembers.value.find { it.id == memberId }
            ?: _bootstrapFallbackMembers.value?.second?.find { it.id == memberId }
        val cleanNickname = nickname.trim().take(40).ifBlank { currentMember?.name.orEmpty().ifBlank { "Familiar" } }
        val cleanColor = avatarColorHex.trim().takeIf { it.startsWith("#") && it.length in 4..9 }
            ?: currentMember?.avatarColorHex?.ifBlank { "#1A73E8" }
            ?: "#1A73E8"
        val cleanStatus = statusText.trim().ifEmpty { if (showLastSeen) "En línea" else "Desconectado" }
        val finalLabel = if (showLastSeen && (cleanStatus == "Desconectado" || cleanStatus == "No se ha conectado")) {
            "En línea"
        } else if (!showLastSeen && (cleanStatus == "En línea" || cleanStatus == "No se ha conectado")) {
            "Desconectado"
        } else {
            cleanStatus
        }
        val effectiveOnline = showLastSeen && _isAppForeground.value
        // Also update local fallback cache immediately so reopening Mi perfil reflects saved preference right away
        _bootstrapFallbackMembers.value?.let { (cachedFam, cachedList) ->
            if (cachedFam == famId) {
                _bootstrapFallbackMembers.value = cachedFam to cachedList.map { m ->
                    if (m.id == memberId) {
                        m.copy(
                            name = cleanNickname,
                            avatarColorHex = cleanColor,
                            bioText = bioText.trim().take(80),
                            showLastSeen = showLastSeen,
                            isOnline = effectiveOnline,
                            lastSeenText = finalLabel,
                            liveStatus = if (effectiveOnline) PresenceStatus.ONLINE else PresenceStatus.LAST_SEEN,
                            liveStatusAt = now
                        )
                    } else m
                }
            }
        }
        viewModelScope.launch {
            prefs.setMemberShowLastSeen(memberId, showLastSeen)
            val existingPhoto = currentMember?.avatarPhotoData.orEmpty()
            val resolvedPhoto = when {
                removePhoto -> ""
                newPhotoUri != null -> ImageCompressionHelper.compressToDataUri(appContext, newPhotoUri) ?: existingPhoto
                else -> existingPhoto
            }
            val presenceResult = cloudRepo.updateMemberPresence(
                familyId = famId,
                memberId = memberId,
                isOnline = effectiveOnline,
                lastSeenText = finalLabel,
                liveStatus = if (effectiveOnline) PresenceStatus.ONLINE else PresenceStatus.LAST_SEEN,
                liveChatId = null,
                liveStatusAt = now,
                linkedAuthUid = currentUserId,
                showLastSeen = showLastSeen,
                callerUid = currentUserId
            )
            val detailsResult = cloudRepo.updateMemberProfileDetails(
                familyId = famId,
                memberId = memberId,
                fields = mapOf(
                    "name" to cleanNickname,
                    "avatarColorHex" to cleanColor,
                    "bioText" to bioText.trim().take(80),
                    "avatarPhotoData" to resolvedPhoto.take(750_000),
                    "showLastSeen" to showLastSeen
                )
            )
            if (effectiveOnline) {
                startPresenceHeartbeat()
            } else {
                heartbeatJob?.cancel()
                heartbeatJob = null
            }
            if (presenceResult.isFailure && detailsResult.isFailure) {
                showMessage("No se pudo guardar el perfil. Verifica tu conexión.")
            } else {
                showMessage("Perfil actualizado")
            }
        }
    }

    fun checkForAppUpdate(isAutoCheck: Boolean = false, forceRefresh: Boolean = false) {
        val currentState = _updateState.value
        if (currentState is AppUpdateUiState.Downloading ||
            currentState is AppUpdateUiState.ReadyToInstall
        ) {
            return
        }
        if (isAutoCheck && currentState is AppUpdateUiState.Checking) {
            return
        }
        updateCheckJob?.cancel()
        updateCheckJob = viewModelScope.launch {
            val lastChecked = prefs.lastUpdateCheckAtFlow.first()
            if (isAutoCheck) {
                if (!AppUpdateChecker.shouldAutoCheckForUpdate(lastChecked)) {
                    return@launch
                }
            } else {
                if (!forceRefresh &&
                    AppUpdateChecker.shouldReuseRecentManualCheck(
                        lastCheckedAtMs = lastChecked,
                        currentState = currentState
                    )
                ) {
                    return@launch
                }
                _updateState.value = AppUpdateUiState.Checking
            }
            val savedSlug = prefs.updateRepoSlugFlow.first()
            val effectiveSlug = savedSlug
                .ifBlank { activeFamily.value?.updateRepoSlug.orEmpty() }
                .ifBlank { BuildConfig.GITHUB_REPO }
            val result = withTimeoutOrNull(AppUpdateChecker.UPDATE_CHECK_TIMEOUT_MS) {
                AppUpdateChecker.checkLatestRelease(
                    rawRepoSlug = effectiveSlug,
                    installedVersionName = BuildConfig.VERSION_NAME,
                    installedVersionCode = BuildConfig.VERSION_CODE
                )
            } ?: AppUpdateUiState.Error("La conexión tardó demasiado")
            when (result) {
                is AppUpdateUiState.UpToDate,
                is AppUpdateUiState.UpdateAvailable -> {
                    prefs.setLastUpdateCheckAt(System.currentTimeMillis())
                    _updateState.value = result
                }
                else -> {
                    if (!isAutoCheck) {
                        _updateState.value = result
                    }
                }
            }
        }
    }

    fun saveUpdateRepoSlug(rawInput: String, triggerCheck: Boolean = false) {
        val trimmed = rawInput.trim()
        val normalized = if (trimmed.isEmpty()) "" else AppUpdateChecker.normalizeRepoSlug(trimmed)
        if (trimmed.isNotEmpty() && normalized.isEmpty()) {
            showMessage("Ingresa el formato usuario/repositorio o URL de GitHub")
            return
        }
        viewModelScope.launch {
            prefs.setUpdateRepoSlug(normalized)
            val fam = activeFamily.value
            if (fam != null && fam.ownerUid == currentUserId) {
                cloudRepo.updateFamilySettings(fam.id, mapOf("updateRepoSlug" to normalized))
            }
            showMessage(
                if (normalized.isEmpty()) "Repositorio de actualizaciones borrado"
                else "Repositorio de actualizaciones guardado"
            )
            if (triggerCheck) checkForAppUpdate(isAutoCheck = false, forceRefresh = true)
        }
    }

    fun downloadAndInstallUpdate(release: AppReleaseInfo) {
        if (updateDownloadJob?.isActive == true) return
        updateDownloadJob = viewModelScope.launch {
            _updateState.value = AppUpdateUiState.Downloading(
                release = release,
                progressPercent = 0,
                downloadedBytes = 0L,
                totalBytes = release.apkSizeBytes,
                statusLabel = "Conectando con el servidor..."
            )
            val result = AppUpdateChecker.downloadApkToCache(
                context = appContext,
                release = release
            ) { pct, downloaded, total, statusLabel ->
                _updateState.value = AppUpdateUiState.Downloading(
                    release = release,
                    progressPercent = pct,
                    downloadedBytes = downloaded,
                    totalBytes = total,
                    statusLabel = statusLabel
                )
            }
            result.fold(
                onSuccess = { apkFile ->
                    val finalSize = apkFile.length().takeIf { it > 0L } ?: release.apkSizeBytes
                    _updateState.value = AppUpdateUiState.Downloading(
                        release = release,
                        progressPercent = 100,
                        downloadedBytes = finalSize.coerceAtLeast(0L),
                        totalBytes = finalSize,
                        statusLabel = "Descarga completa"
                    )
                    delay(400L)
                    _updateState.value = AppUpdateUiState.ReadyToInstall(
                        release = release,
                        apkFilePath = apkFile.absolutePath
                    )
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    _updateState.value = AppUpdateUiState.Error(
                        message = error.localizedMessage ?: "No se pudo descargar la actualización.",
                        release = release
                    )
                }
            )
        }
    }

    fun cancelUpdateDownload() {
        updateDownloadJob?.cancel()
        updateDownloadJob = null
        val current = _updateState.value
        _updateState.value = if (current is AppUpdateUiState.Downloading) {
            AppUpdateUiState.UpdateAvailable(
                installedVersionName = BuildConfig.VERSION_NAME,
                installedVersionCode = BuildConfig.VERSION_CODE,
                release = current.release
            )
        } else {
            AppUpdateUiState.Idle
        }
    }

    fun resetUpdateState() {
        updateCheckJob?.cancel()
        updateCheckJob = null
        updateDownloadJob?.cancel()
        updateDownloadJob = null
        _updateState.value = AppUpdateUiState.Idle
    }

    fun postponeUpdateFor24Hours() {
        val untilMs = System.currentTimeMillis() + AppUpdateChecker.DAILY_CHECK_INTERVAL_MS
        viewModelScope.launch {
            prefs.setUpdateSnoozedUntil(untilMs)
        }
    }

    fun loadVersionHistoryOnDemand() {
        val current = _versionHistoryState.value
        if (current is com.example.util.VersionHistoryUiState.Loaded && current.entries.isNotEmpty()) {
            return
        }
        if (versionHistoryJob?.isActive == true) return
        versionHistoryJob = viewModelScope.launch {
            val cachedJson = prefs.getCachedVersionHistoryJson()
            val cachedEntries = AppUpdateChecker.decodeVersionHistoryJson(cachedJson)
            if (cachedEntries.isNotEmpty()) {
                _versionHistoryState.value = com.example.util.VersionHistoryUiState.Loaded(cachedEntries)
                return@launch
            }
            _versionHistoryState.value = com.example.util.VersionHistoryUiState.Loading
            val savedSlug = prefs.updateRepoSlugFlow.first()
            val effectiveSlug = savedSlug
                .ifBlank { activeFamily.value?.updateRepoSlug.orEmpty() }
                .ifBlank { BuildConfig.GITHUB_REPO }
            val result = AppUpdateChecker.fetchVersionHistory(effectiveSlug)
            result.fold(
                onSuccess = { entries ->
                    prefs.setCachedVersionHistoryJson(AppUpdateChecker.encodeVersionHistoryJson(entries))
                    _versionHistoryState.value = com.example.util.VersionHistoryUiState.Loaded(entries)
                },
                onFailure = {
                    _versionHistoryState.value = com.example.util.VersionHistoryUiState.Unavailable
                }
            )
        }
    }

    // --- Admin Member & Group Management ---

    fun saveFamilyMember(
        existingId: String?,
        name: String,
        roleLabel: String,
        isAdmin: Boolean,
        colorHex: String,
        emoji: String,
        phoneNumber: String = "",
        bioText: String? = null,
        newPhotoUri: Uri? = null,
        removePhoto: Boolean = false
    ) {
        val famId = _activeFamilyId.value ?: return
        val callerIsAdmin = activeMember.value?.isAdmin == true
        val validationError = validateMemberBeforeSave(
            existingMembers = rawMembers.value,
            editingId = existingId,
            name = name,
            roleLabel = roleLabel,
            isCallerAdmin = callerIsAdmin
        )
        if (validationError != null) {
            showMessage(validationError)
            return
        }
        val trimmedName = name.trim()
        val trimmedRole = roleLabel.trim()
        val uids = currentMemberUids()
        viewModelScope.launch {
            val isCreate = existingId == null
            val id = existingId ?: "member_${UUID.randomUUID().toString().replace("-", "").take(8)}"
            val existing = rawMembers.value.find { it.id == id }
            val resolvedPhoto = when {
                removePhoto -> ""
                newPhotoUri != null -> ImageCompressionHelper.compressToDataUri(appContext, newPhotoUri)
                    ?: existing?.avatarPhotoData.orEmpty()
                else -> existing?.avatarPhotoData.orEmpty()
            }
            val member = FamilyMemberEntity(
                id = id,
                name = trimmedName,
                roleLabel = trimmedRole,
                isAdmin = isAdmin,
                avatarColorHex = colorHex,
                avatarEmoji = emoji,
                avatarPhotoData = resolvedPhoto,
                bioText = bioText?.trim()?.take(120) ?: existing?.bioText.orEmpty(),
                isOnline = existing?.isOnline ?: false,
                lastSeenText = existing?.lastSeenText ?: "No se ha conectado",
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                phoneNumber = phoneNumber.trim(),
                liveStatus = existing?.liveStatus ?: PresenceStatus.LAST_SEEN,
                liveChatId = existing?.liveChatId,
                liveStatusAt = existing?.liveStatusAt ?: 0L,
                linkedAuthUid = existing?.linkedAuthUid ?: "",
                authorUid = existing?.authorUid ?: currentUserId
            )
            cloudRepo.upsertMember(famId, member, uids, currentUserId, isCreate)

            if (isCreate) {
                val mainGroup = rawChats.value.firstOrNull { it.type == ChatType.GROUP }
                if (mainGroup != null) {
                    val pIds = JsonHelpers.decodeStringList(mainGroup.participantIdsJson).toMutableList()
                    if (!pIds.contains(id)) {
                        pIds.add(id)
                        cloudRepo.upsertChat(
                            famId,
                            mainGroup.copy(participantIdsJson = JsonHelpers.encodeStringList(pIds)),
                            uids,
                            currentUserId,
                            isCreate = false
                        )
                    }
                }
                val currentAdminId = _activeMemberId.value
                if (currentAdminId != null && currentAdminId != id) {
                    createOrOpenDirectChat(id, openAfter = false)
                }
                showMessage("$trimmedName añadido a la familia")
            } else {
                showMessage("Perfil de $trimmedName actualizado")
            }
        }
    }

    private suspend fun migrateAndRemoveDuplicateProfile(
        famId: String,
        duplicate: FamilyMemberEntity,
        targetAdmin: FamilyMemberEntity
    ) {
        val uids = currentMemberUids()
        val oldId = duplicate.id
        val newId = targetAdmin.id
        var migratedCount = 0

        val chatsSnapshot = rawChats.value
        val directChatRemap = mutableMapOf<String, String>()
        chatsSnapshot.filter { it.type == ChatType.DIRECT }.forEach { chat ->
            val pIds = JsonHelpers.decodeStringList(chat.participantIdsJson)
            if (oldId in pIds) {
                val otherId = pIds.firstOrNull { it != oldId }
                val existingAdminDirect = if (otherId != null) {
                    chatsSnapshot.firstOrNull { c ->
                        c.id != chat.id && c.type == ChatType.DIRECT &&
                            JsonHelpers.decodeStringList(c.participantIdsJson).let { newId in it && otherId in it }
                    }
                } else null
                if (existingAdminDirect != null) {
                    directChatRemap[chat.id] = existingAdminDirect.id
                }
            }
        }

        // 1. Migrate any messages referencing duplicate profile
        allMessages.value.forEach { msg ->
            val remappedChatId = directChatRemap[msg.chatId] ?: msg.chatId
            val needsIdentityMigration = msg.senderId == oldId || msg.chatId in directChatRemap
            val readIds = JsonHelpers.decodeStringList(msg.readByMemberIdsJson)
            val playedIds = JsonHelpers.decodeStringList(msg.playedByMemberIdsJson)
            val hiddenIds = JsonHelpers.decodeStringList(msg.hiddenForMemberIdsJson)
            val reactions = JsonHelpers.decodeReactions(msg.reactionsJson)
            val hasArrayRef = oldId in readIds || oldId in playedIds || oldId in hiddenIds || reactions.containsKey(oldId)

            if (needsIdentityMigration) {
                val newRead = readIds.map { if (it == oldId) newId else it }.distinct()
                val newPlayed = playedIds.map { if (it == oldId) newId else it }.distinct()
                val newHidden = hiddenIds.map { if (it == oldId) newId else it }.distinct()
                val newReactions = reactions.toMutableMap().also { map ->
                    val oldReaction = map.remove(oldId)
                    if (oldReaction != null && !map.containsKey(newId)) {
                        map[newId] = oldReaction
                    }
                }
                val migratedMsg = msg.copy(
                    id = "msg_${UUID.randomUUID().toString().replace("-", "").take(10)}",
                    chatId = remappedChatId,
                    senderId = if (msg.senderId == oldId) newId else msg.senderId,
                    readByMemberIdsJson = JsonHelpers.encodeStringList(newRead),
                    playedByMemberIdsJson = JsonHelpers.encodeStringList(newPlayed),
                    hiddenForMemberIdsJson = JsonHelpers.encodeStringList(newHidden),
                    reactionsJson = JsonHelpers.encodeReactions(newReactions),
                    authorUid = currentUserId
                )
                if (cloudRepo.createMessage(famId, migratedMsg, uids, currentUserId).isSuccess) {
                    cloudRepo.deleteMessage(famId, msg.id)
                    migratedCount++
                }
            } else if (hasArrayRef) {
                val fields = mutableMapOf<String, Any>()
                if (oldId in readIds) {
                    fields["readByMemberIds"] = readIds.map { if (it == oldId) newId else it }.distinct()
                }
                if (oldId in playedIds) {
                    fields["playedByMemberIds"] = playedIds.map { if (it == oldId) newId else it }.distinct()
                }
                if (oldId in hiddenIds) {
                    fields["hiddenForMemberIds"] = hiddenIds.map { if (it == oldId) newId else it }.distinct()
                }
                if (reactions.containsKey(oldId)) {
                    val newReactions = reactions.toMutableMap().also { map ->
                        val r = map.remove(oldId)
                        if (r != null && !map.containsKey(newId)) map[newId] = r
                    }
                    fields["reactions"] = newReactions
                }
                if (fields.isNotEmpty()) {
                    cloudRepo.updateMessageFields(famId, msg.id, fields)
                    migratedCount++
                }
            }
        }

        // 2. Migrate or clean chats
        chatsSnapshot.forEach { chat ->
            val pIds = JsonHelpers.decodeStringList(chat.participantIdsJson)
            if (oldId in pIds) {
                if (chat.id in directChatRemap) {
                    cloudRepo.deleteChat(famId, chat.id)
                } else {
                    val updatedPIds = pIds.map { if (it == oldId) newId else it }.distinct()
                    cloudRepo.upsertChat(
                        famId,
                        chat.copy(participantIdsJson = JsonHelpers.encodeStringList(updatedPIds)),
                        uids,
                        currentUserId,
                        isCreate = false
                    )
                    migratedCount++
                }
            }
        }

        // 3. Migrate statuses / stories
        rawStatuses.value.forEach { st ->
            if (st.memberId == oldId) {
                val migratedStatus = st.copy(
                    id = "st_${UUID.randomUUID().toString().replace("-", "").take(8)}",
                    memberId = newId,
                    authorUid = currentUserId
                )
                if (cloudRepo.createStatus(famId, migratedStatus, uids, currentUserId).isSuccess) {
                    cloudRepo.deleteStatus(famId, st.id)
                    migratedCount++
                }
            } else if (oldId in JsonHelpers.decodeStringList(st.viewedByMemberIdsJson)) {
                cloudRepo.markStatusViewed(famId, st.id, newId)
            }
        }

        // 4. Migrate calls
        calls.value.forEach { call ->
            if (call.callerId == oldId) {
                val migratedCall = call.copy(
                    id = "call_${UUID.randomUUID().toString().replace("-", "").take(8)}",
                    callerId = newId,
                    authorUid = currentUserId
                )
                if (cloudRepo.createCall(famId, migratedCall, uids, currentUserId).isSuccess) {
                    cloudRepo.deleteCall(famId, call.id)
                    migratedCount++
                }
            }
        }

        // 5. Migrate events, notes, shopping items
        events.value.forEach { ev ->
            if (ev.assignedMemberId == oldId) {
                if (cloudRepo.updateEventAssignedMember(famId, ev.id, newId).isSuccess) {
                    migratedCount++
                }
            }
        }
        notes.value.forEach { note ->
            if (note.authorId == oldId) {
                val migratedNote = note.copy(
                    id = "note_${UUID.randomUUID().toString().replace("-", "").take(8)}",
                    authorId = newId,
                    authorUid = currentUserId
                )
                if (cloudRepo.upsertNote(famId, migratedNote, uids, currentUserId, isCreate = true).isSuccess) {
                    cloudRepo.deleteNote(famId, note.id)
                    migratedCount++
                }
            }
        }
        shoppingItems.value.forEach { item ->
            if (item.addedById == oldId) {
                val migratedItem = item.copy(
                    id = "shop_${UUID.randomUUID().toString().replace("-", "").take(8)}",
                    addedById = newId,
                    authorUid = currentUserId
                )
                if (cloudRepo.upsertShoppingItem(famId, migratedItem, uids, currentUserId, isCreate = true).isSuccess) {
                    cloudRepo.deleteShoppingItem(famId, item.id)
                    migratedCount++
                }
            }
        }

        // 6. Delete duplicate member document
        val deleteRes = cloudRepo.deleteMember(famId, oldId)
        if (deleteRes.isSuccess) {
            if (prefs.activeMemberIdFlow.first() == oldId) {
                prefs.setActiveMemberId(newId)
            }
            if (migratedCount > 0) {
                showMessage("Datos de \"${duplicate.name}\" transferidos a ${targetAdmin.name} y perfil duplicado eliminado.")
            } else {
                showMessage("Perfil duplicado \"${duplicate.name}\" eliminado. Solo ${targetAdmin.name} permanece como administrador.")
            }
        }
    }

    fun deleteFamilyMember(memberId: String) {
        val famId = _activeFamilyId.value ?: return
        val uids = currentMemberUids()
        viewModelScope.launch {
            val adminsCount = members.value.count { it.isAdmin }
            val target = members.value.find { it.id == memberId } ?: return@launch
            if (target.isAdmin && adminsCount <= 1) {
                showMessage("Debe haber al menos un administrador")
                return@launch
            }
            if (memberId == _activeMemberId.value) {
                showMessage("No puedes eliminar el perfil con sesión activa")
                return@launch
            }
            cloudRepo.deleteMember(famId, memberId)
            rawChats.value.forEach { chat ->
                val ids = JsonHelpers.decodeStringList(chat.participantIdsJson)
                if (ids.contains(memberId)) {
                    if (chat.type == ChatType.DIRECT) {
                        cloudRepo.deleteChat(famId, chat.id)
                    } else {
                        val updated = ids.filterNot { it == memberId }
                        cloudRepo.upsertChat(
                            famId,
                            chat.copy(participantIdsJson = JsonHelpers.encodeStringList(updated)),
                            uids,
                            currentUserId,
                            isCreate = false
                        )
                    }
                }
            }
            showMessage("${target.name} eliminado")
        }
    }

    fun saveGroup(
        existingGroupId: String?,
        title: String,
        colorHex: String,
        emoji: String,
        participantIds: List<String>
    ) {
        val famId = _activeFamilyId.value ?: return
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty() || participantIds.size < 2) {
            showMessage("El grupo necesita nombre y al menos 2 miembros")
            return
        }
        val uids = currentMemberUids()
        viewModelScope.launch {
            val isCreate = existingGroupId == null
            val id = existingGroupId ?: "group_${UUID.randomUUID().toString().replace("-", "").take(8)}"
            val existing = rawChats.value.find { it.id == id }
            val group = ChatThreadEntity(
                id = id,
                type = ChatType.GROUP,
                title = cleanTitle,
                avatarColorHex = colorHex,
                avatarEmoji = emoji,
                participantIdsJson = JsonHelpers.encodeStringList(participantIds.distinct()),
                pinnedMessageId = existing?.pinnedMessageId,
                updatedAt = System.currentTimeMillis(),
                authorUid = existing?.authorUid ?: currentUserId
            )
            cloudRepo.upsertChat(famId, group, uids, currentUserId, isCreate)
            showMessage(if (isCreate) "Grupo \"$cleanTitle\" creado" else "Grupo actualizado")
        }
    }

    fun deleteGroup(groupId: String) {
        val famId = _activeFamilyId.value ?: return
        viewModelScope.launch {
            cloudRepo.deleteChat(famId, groupId)
            showMessage("Grupo eliminado")
        }
    }

    fun createOrOpenDirectChat(otherMemberId: String, openAfter: Boolean = true, onOpened: (String) -> Unit = {}) {
        val famId = _activeFamilyId.value ?: return
        val currentId = _activeMemberId.value ?: return
        if (currentId == otherMemberId) return
        val uids = currentMemberUids()
        viewModelScope.launch {
            val existing = rawChats.value.firstOrNull { chat ->
                chat.type == ChatType.DIRECT &&
                    JsonHelpers.decodeStringList(chat.participantIdsJson).toSet() == setOf(currentId, otherMemberId)
            }
            val chatId = if (existing != null) {
                existing.id
            } else {
                val other = members.value.find { it.id == otherMemberId }
                val newId = "chat_${UUID.randomUUID().toString().replace("-", "").take(8)}"
                cloudRepo.upsertChat(
                    familyId = famId,
                    chat = ChatThreadEntity(
                        id = newId,
                        type = ChatType.DIRECT,
                        title = "Directo",
                        avatarColorHex = other?.avatarColorHex ?: "#1A73E8",
                        avatarEmoji = other?.avatarEmoji ?: "💬",
                        participantIdsJson = JsonHelpers.encodeStringList(listOf(currentId, otherMemberId)),
                        updatedAt = System.currentTimeMillis(),
                        authorUid = currentUserId
                    ),
                    memberUids = uids,
                    callerUid = currentUserId,
                    isCreate = true
                )
                newId
            }
            if (openAfter) {
                openChat(chatId)
                onOpened(chatId)
            }
        }
    }

    // --- Real Cloud Messaging Actions ---

    private suspend fun markDeliveredIfRecipientOnline(
        famId: String,
        chatId: String,
        msgId: String,
        senderId: String
    ) {
        val chat = rawChats.value.find { it.id == chatId }
        val participants = chat?.let { JsonHelpers.decodeStringList(it.participantIdsJson) }.orEmpty()
        val anyOtherOnline = members.value.any { m ->
            m.id != senderId && participants.contains(m.id) && m.isOnline
        }
        if (anyOtherOnline) {
            cloudRepo.markMessageDeliveredToRecipient(famId, msgId)
        }
    }

    fun sendTextMessage(chatId: String, text: String, replyToId: String? = null) {
        val famId = _activeFamilyId.value ?: return
        val currentId = _activeMemberId.value ?: return
        val clean = text.trim()
        if (clean.isEmpty()) return
        val msgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(10)}"
        val msg = ChatMessageEntity(
            id = msgId,
            chatId = chatId,
            senderId = currentId,
            text = clean,
            type = MessageType.TEXT,
            status = MessageStatus.SENT,
            timestamp = System.currentTimeMillis(),
            replyToMessageId = replyToId,
            authorUid = currentUserId
        )
        viewModelScope.launch {
            val res = cloudRepo.createMessage(famId, msg, currentMemberUids(), currentUserId)
            if (res.isSuccess) {
                markDeliveredIfRecipientOnline(famId, chatId, msgId, currentId)
            }
            updateLivePresence(chatId, PresenceStatus.ONLINE, forceClear = true)
        }
    }

    fun sendImageMessage(chatId: String, pickedUri: Uri, caption: String, replyToId: String? = null) {
        val famId = _activeFamilyId.value ?: return
        val currentId = _activeMemberId.value ?: return
        viewModelScope.launch {
            val dataUri = ImageCompressionHelper.compressToDataUri(appContext, pickedUri)
            if (dataUri == null) {
                showMessage("No se pudo procesar la imagen seleccionada")
                return@launch
            }
            val msgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(10)}"
            val msg = ChatMessageEntity(
                id = msgId,
                chatId = chatId,
                senderId = currentId,
                text = caption.trim(),
                type = MessageType.IMAGE,
                status = MessageStatus.SENT,
                timestamp = System.currentTimeMillis(),
                replyToMessageId = replyToId,
                mediaUri = dataUri,
                authorUid = currentUserId
            )
            val res = cloudRepo.createMessage(famId, msg, currentMemberUids(), currentUserId)
            if (res.isSuccess) {
                markDeliveredIfRecipientOnline(famId, chatId, msgId, currentId)
            } else {
                showMessage("La imagen no se pudo subir a la nube")
            }
        }
    }

    fun sendDocumentMessage(chatId: String, documentUri: Uri, replyToId: String? = null) {
        val famId = _activeFamilyId.value ?: return
        val currentId = _activeMemberId.value ?: return
        viewModelScope.launch {
            _activeUploadLabel.value = "Preparando archivo..."
            _activeUploadProgress.value = 0.05f
            val encodedRes = DocumentMediaHelper.readAndEncodeDocument(appContext, documentUri)
            encodedRes.fold(
                onSuccess = { payload ->
                    val msgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(10)}"
                    val uids = currentMemberUids()
                    val isChunked = payload.dataUri.length > DocumentMediaHelper.INLINE_MAX_CHARS
                    val chunks = if (isChunked) DocumentMediaHelper.splitIntoChunks(payload.dataUri) else emptyList()

                    if (isChunked) {
                        _activeUploadLabel.value = "Subiendo ${payload.fileName} (${DocumentMediaHelper.formatFileSize(payload.sizeBytes)})..."
                        for ((idx, chunkStr) in chunks.withIndex()) {
                            val chunkEntity = MediaChunkEntity(
                                id = "${msgId}_c$idx",
                                messageId = msgId,
                                chunkIndex = idx,
                                totalChunks = chunks.size,
                                data = chunkStr,
                                authorUid = currentUserId
                            )
                            val chunkRes = cloudRepo.uploadMediaChunk(famId, chunkEntity, uids, currentUserId)
                            if (chunkRes.isFailure) {
                                _activeUploadLabel.value = null
                                _activeUploadProgress.value = null
                                showMessage("Error al subir fragmento ${idx + 1}/${chunks.size} del archivo")
                                return@fold
                            }
                            _activeUploadProgress.value = ((idx + 1).toFloat() / chunks.size.toFloat()).coerceIn(0.05f, 0.98f)
                        }
                        // Cache locally so sender can open immediately
                        runCatching {
                            val commaIdx = payload.dataUri.indexOf(',')
                            val b64 = if (commaIdx > 0) payload.dataUri.substring(commaIdx + 1) else payload.dataUri
                            val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                            DocumentMediaHelper.getCachedDocumentFile(appContext, msgId, payload.fileName).writeBytes(bytes)
                        }
                    }

                    val msg = ChatMessageEntity(
                        id = msgId,
                        chatId = chatId,
                        senderId = currentId,
                        text = payload.fileName,
                        type = MessageType.DOCUMENT,
                        status = MessageStatus.SENT,
                        timestamp = System.currentTimeMillis(),
                        replyToMessageId = replyToId,
                        mediaUri = if (isChunked) null else payload.dataUri,
                        fileName = payload.fileName,
                        fileMimeType = payload.mimeType,
                        fileSizeBytes = payload.sizeBytes,
                        chunkCount = chunks.size,
                        authorUid = currentUserId
                    )
                    val res = cloudRepo.createMessage(famId, msg, uids, currentUserId)
                    _activeUploadLabel.value = null
                    _activeUploadProgress.value = null
                    if (res.isSuccess) {
                        markDeliveredIfRecipientOnline(famId, chatId, msgId, currentId)
                        showMessage("Documento enviado: ${payload.fileName}")
                    } else {
                        showMessage("No se pudo enviar el documento")
                    }
                },
                onFailure = { err ->
                    _activeUploadLabel.value = null
                    _activeUploadProgress.value = null
                    showMessage(err.localizedMessage ?: "No se pudo adjuntar el documento")
                }
            )
        }
    }

    fun sendVideoMessage(chatId: String, videoUri: Uri, caption: String, replyToId: String? = null) {
        val famId = _activeFamilyId.value ?: return
        val currentId = _activeMemberId.value ?: return
        viewModelScope.launch {
            _activeUploadLabel.value = "Procesando video..."
            _activeUploadProgress.value = 0.05f
            val encodedRes = VideoMediaHelper.readAndEncodeVideo(appContext, videoUri)
            encodedRes.fold(
                onSuccess = { payload ->
                    val msgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(10)}"
                    val uids = currentMemberUids()
                    val chunks = payload.chunks
                    if (chunks.isNotEmpty()) {
                        _activeUploadLabel.value = "Subiendo video (${DocumentMediaHelper.formatFileSize(payload.sizeBytes)})..."
                        for ((idx, chunkStr) in chunks.withIndex()) {
                            val chunkEntity = MediaChunkEntity(
                                id = "${msgId}_c$idx",
                                messageId = msgId,
                                chunkIndex = idx,
                                totalChunks = chunks.size,
                                data = chunkStr,
                                authorUid = currentUserId
                            )
                            val chunkRes = cloudRepo.uploadMediaChunk(famId, chunkEntity, uids, currentUserId)
                            if (chunkRes.isFailure) {
                                _activeUploadLabel.value = null
                                _activeUploadProgress.value = null
                                showMessage("No se pudo subir el video (${idx + 1}/${chunks.size})")
                                return@fold
                            }
                            _activeUploadProgress.value = ((idx + 1).toFloat() / chunks.size.toFloat()).coerceIn(0.05f, 0.98f)
                        }
                    }
                    // Write assembled video to local cache so sender plays it immediately
                    VideoMediaHelper.writeAssembledVideoToCache(
                        context = appContext,
                        messageId = msgId,
                        fileName = payload.fileName,
                        fullDataUri = payload.dataUri
                    )
                    val msg = ChatMessageEntity(
                        id = msgId,
                        chatId = chatId,
                        senderId = currentId,
                        text = caption.trim(),
                        type = MessageType.VIDEO,
                        status = MessageStatus.SENT,
                        timestamp = System.currentTimeMillis(),
                        replyToMessageId = replyToId,
                        mediaUri = if (chunks.isEmpty()) payload.dataUri else null,
                        fileName = payload.fileName,
                        fileMimeType = payload.mimeType,
                        fileSizeBytes = payload.sizeBytes,
                        voiceDurationSec = payload.durationSec,
                        videoThumbnailData = payload.thumbnailDataUri,
                        chunkCount = chunks.size,
                        authorUid = currentUserId
                    )
                    val res = cloudRepo.createMessage(famId, msg, uids, currentUserId)
                    _activeUploadLabel.value = null
                    _activeUploadProgress.value = null
                    if (res.isSuccess) {
                        markDeliveredIfRecipientOnline(famId, chatId, msgId, currentId)
                        showMessage("Video enviado")
                    } else {
                        showMessage("No se pudo enviar el video")
                    }
                },
                onFailure = { err ->
                    _activeUploadLabel.value = null
                    _activeUploadProgress.value = null
                    showMessage(err.localizedMessage ?: "No se pudo procesar el video")
                }
            )
        }
    }

    fun openOrDownloadDocument(message: ChatMessageEntity) {
        val famId = _activeFamilyId.value ?: return
        viewModelScope.launch {
            val cached = DocumentMediaHelper.getCachedDocumentFile(appContext, message.id, message.fileName)
            if (cached.exists() && cached.length() > 0L) {
                DocumentMediaHelper.launchDocumentFileViewer(appContext, cached, message.fileMimeType)
                return@launch
            }
            if (!message.mediaUri.isNullOrBlank()) {
                val res = DocumentMediaHelper.openDocumentExternally(
                    context = appContext,
                    messageId = message.id,
                    fileName = message.fileName,
                    mimeType = message.fileMimeType,
                    mediaData = message.mediaUri
                )
                if (res.isFailure) {
                    showMessage("No se pudo abrir el archivo")
                }
                return@launch
            }
            if (message.chunkCount <= 0) {
                showMessage("El archivo no está disponible")
                return@launch
            }
            if (_mediaTransferProgress.value.containsKey(message.id)) return@launch
            _mediaTransferProgress.value = _mediaTransferProgress.value + (message.id to 0.05f)
            val fetched = cloudRepo.fetchMediaChunks(
                familyId = famId,
                messageId = message.id,
                totalChunks = message.chunkCount,
                onProgress = { prog ->
                    _mediaTransferProgress.value = _mediaTransferProgress.value + (message.id to prog)
                },
                callerUid = currentUserId
            )
            _mediaTransferProgress.value = _mediaTransferProgress.value - message.id
            fetched.fold(
                onSuccess = { fullDataUri ->
                    val res = DocumentMediaHelper.openDocumentExternally(
                        context = appContext,
                        messageId = message.id,
                        fileName = message.fileName,
                        mimeType = message.fileMimeType,
                        mediaData = fullDataUri
                    )
                    if (res.isFailure) {
                        showMessage("No se pudo abrir el documento descargado")
                    }
                },
                onFailure = { err ->
                    showMessage(err.localizedMessage ?: "No se pudo descargar el documento")
                }
            )
        }
    }

    fun openOrDownloadVideo(message: ChatMessageEntity, onReady: (File) -> Unit) {
        val famId = _activeFamilyId.value ?: return
        viewModelScope.launch {
            val cached = VideoMediaHelper.findExistingCachedVideoFile(appContext, message.id, message.fileName)
            if (cached != null && cached.exists() && cached.length() > 0L) {
                onReady(cached)
                return@launch
            }
            if (!message.mediaUri.isNullOrBlank()) {
                val res = VideoMediaHelper.writeAssembledVideoToCache(
                    context = appContext,
                    messageId = message.id,
                    fileName = message.fileName,
                    fullDataUri = message.mediaUri
                )
                res.onSuccess(onReady).onFailure {
                    showMessage("No se pudo preparar el video")
                }
                return@launch
            }
            if (message.chunkCount <= 0) {
                showMessage("El video no está disponible")
                return@launch
            }
            if (_mediaTransferProgress.value.containsKey(message.id)) return@launch
            _mediaTransferProgress.value = _mediaTransferProgress.value + (message.id to 0.05f)
            val fetched = cloudRepo.fetchMediaChunks(
                familyId = famId,
                messageId = message.id,
                totalChunks = message.chunkCount,
                onProgress = { prog ->
                    _mediaTransferProgress.value = _mediaTransferProgress.value + (message.id to prog)
                },
                callerUid = currentUserId
            )
            _mediaTransferProgress.value = _mediaTransferProgress.value - message.id
            fetched.fold(
                onSuccess = { fullDataUri ->
                    val res = VideoMediaHelper.writeAssembledVideoToCache(
                        context = appContext,
                        messageId = message.id,
                        fileName = message.fileName,
                        fullDataUri = fullDataUri
                    )
                    res.onSuccess(onReady).onFailure {
                        showMessage("Error al reconstruir el video")
                    }
                },
                onFailure = { err ->
                    showMessage(err.localizedMessage ?: "No se pudo descargar el video")
                }
            )
        }
    }

    fun sendLocationMessage(
        chatId: String,
        name: String,
        address: String,
        latitude: Double? = null,
        longitude: Double? = null,
        replyToId: String? = null,
        liveDurationMinutes: Int = 0
    ) {
        val famId = _activeFamilyId.value ?: return
        val currentId = _activeMemberId.value ?: return
        val now = System.currentTimeMillis()
        val liveUntil = if (liveDurationMinutes > 0) now + liveDurationMinutes * 60_000L else null
        val msgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(10)}"
        val msg = ChatMessageEntity(
            id = msgId,
            chatId = chatId,
            senderId = currentId,
            text = "",
            type = MessageType.LOCATION,
            status = MessageStatus.SENT,
            timestamp = now,
            replyToMessageId = replyToId,
            locationName = if (liveDurationMinutes > 0) "Ubicación en tiempo real" else name.trim().ifEmpty { "Ubicación GPS" },
            locationAddress = address.trim(),
            locationLat = latitude,
            locationLng = longitude,
            locationLiveUntil = liveUntil,
            authorUid = currentUserId
        )
        viewModelScope.launch {
            val res = cloudRepo.createMessage(famId, msg, currentMemberUids(), currentUserId)
            if (res.isSuccess) {
                cloudRepo.updateMessageFields(famId, msgId, mapOf("status" to MessageStatus.DELIVERED.name))
                if (liveUntil != null) {
                    startLiveLocationTracker(famId, msgId, liveUntil)
                }
            }
        }
    }

    private fun startLiveLocationTracker(familyId: String, messageId: String, liveUntilMs: Long) {
        liveLocationJobs[messageId]?.cancel()
        liveLocationJobs[messageId] = viewModelScope.launch {
            while (System.currentTimeMillis() < liveUntilMs) {
                delay(20_000L)
                if (System.currentTimeMillis() >= liveUntilMs) break
                val gps = LocationHelper.fetchCurrentGpsLocation(appContext).getOrNull() ?: continue
                cloudRepo.updateMessageFields(
                    familyId = familyId,
                    messageId = messageId,
                    fields = mapOf(
                        "locationLat" to gps.latitude,
                        "locationLng" to gps.longitude,
                        "locationAddress" to gps.address.take(240)
                    )
                )
            }
            liveLocationJobs.remove(messageId)
        }
    }

    fun stopLiveLocationSharing(messageId: String) {
        liveLocationJobs.remove(messageId)?.cancel()
        val famId = _activeFamilyId.value ?: return
        viewModelScope.launch {
            cloudRepo.updateMessageFields(
                familyId = famId,
                messageId = messageId,
                fields = mapOf("locationLiveUntil" to 0L)
            )
            showMessage("Ubicación en tiempo real detenida")
        }
    }

    fun sendPollMessage(chatId: String, question: String, optionTexts: List<String>) {
        val famId = _activeFamilyId.value ?: return
        val currentId = _activeMemberId.value ?: return
        val cleanQ = question.trim()
        val cleanOpts = optionTexts.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleanQ.isEmpty() || cleanOpts.size < 2) {
            showMessage("La encuesta necesita pregunta y al menos 2 opciones")
            return
        }
        val pollOptions = cleanOpts.mapIndexed { index, opt ->
            PollOption(id = "opt_${index}_${UUID.randomUUID().toString().take(4)}", text = opt)
        }
        val msgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(10)}"
        val msg = ChatMessageEntity(
            id = msgId,
            chatId = chatId,
            senderId = currentId,
            text = "",
            type = MessageType.POLL,
            status = MessageStatus.SENT,
            timestamp = System.currentTimeMillis(),
            pollQuestion = cleanQ,
            pollOptionsJson = JsonHelpers.encodePollOptions(pollOptions),
            authorUid = currentUserId
        )
        viewModelScope.launch {
            val res = cloudRepo.createMessage(famId, msg, currentMemberUids(), currentUserId)
            if (res.isSuccess) {
                cloudRepo.updateMessageFields(famId, msgId, mapOf("status" to MessageStatus.DELIVERED.name))
            }
        }
    }

    fun voteOnPoll(messageId: String, optionId: String) {
        val famId = _activeFamilyId.value ?: return
        val currentId = _activeMemberId.value ?: return
        val msg = allMessages.value.find { it.id == messageId } ?: return
        val options = JsonHelpers.decodePollOptions(msg.pollOptionsJson)
        val updated = options.map { opt ->
            val cleanedVoters = opt.voterIds.filterNot { it == currentId }
            if (opt.id == optionId) {
                val alreadyVoted = opt.voterIds.contains(currentId)
                opt.copy(voterIds = if (alreadyVoted) cleanedVoters else cleanedVoters + currentId)
            } else {
                opt.copy(voterIds = cleanedVoters)
            }
        }
        val pollMaps = updated.map { mapOf("id" to it.id, "text" to it.text, "voterIds" to it.voterIds) }
        viewModelScope.launch {
            cloudRepo.updateMessageFields(famId, messageId, mapOf("pollOptions" to pollMaps))
        }
    }

    fun editMessage(messageId: String, newText: String) {
        val famId = _activeFamilyId.value ?: return
        val clean = newText.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            cloudRepo.updateMessageFields(famId, messageId, mapOf("text" to clean, "isEdited" to true))
        }
    }

    fun toggleReaction(messageId: String, emoji: String) {
        val famId = _activeFamilyId.value ?: return
        val currentId = _activeMemberId.value ?: return
        val msg = allMessages.value.find { it.id == messageId } ?: return
        val reactions = JsonHelpers.decodeReactions(msg.reactionsJson).toMutableMap()
        if (reactions[currentId] == emoji) reactions.remove(currentId) else reactions[currentId] = emoji
        viewModelScope.launch {
            cloudRepo.updateMessageFields(famId, messageId, mapOf("reactions" to reactions))
        }
    }

    fun togglePinMessage(chatId: String, messageId: String) {
        val famId = _activeFamilyId.value ?: return
        val chat = rawChats.value.find { it.id == chatId } ?: return
        val nextPinned = if (chat.pinnedMessageId == messageId) null else messageId
        viewModelScope.launch {
            cloudRepo.updatePinnedMessage(famId, chatId, nextPinned)
            showMessage(if (nextPinned != null) "Mensaje fijado arriba" else "Mensaje desfijado")
        }
    }

    fun clearChatForMe(chatId: String) {
        val famId = _activeFamilyId.value ?: return
        val currentId = _activeMemberId.value ?: return
        val clearedMap = chatClearedAtMap.value
        val localHiddenKeys = locallyHiddenMessageKeys.value
        val chatMsgs = allMessages.value.filter { msg ->
            msg.chatId == chatId &&
                isMessageVisibleForMember(msg, currentId, clearedMap, localHiddenKeys)
        }
        val targetIds = chatMsgs.map { it.id }
        val clearTs = System.currentTimeMillis()
        val skewedIds = chatMsgs.filter { it.timestamp > clearTs }.map { it.id }
        viewModelScope.launch {
            prefs.setChatClearedAt(
                memberId = currentId,
                chatId = chatId,
                clearedAtMs = clearTs,
                extraHiddenMessageIds = skewedIds
            )
            showMessage("Chat vaciado")
            if (targetIds.isNotEmpty()) {
                cloudRepo.hideMessagesForMember(famId, targetIds, currentId)
            }
        }
    }

    fun deleteMessagesForMe(messageIds: List<String>) {
        val famId = _activeFamilyId.value ?: return
        val currentId = _activeMemberId.value ?: return
        val targetIds = messageIds.distinct().filter { it.isNotBlank() }
        if (targetIds.isEmpty()) return
        viewModelScope.launch {
            prefs.addLocallyHiddenMessages(currentId, targetIds)
            showMessage("Eliminado para ti")
            cloudRepo.hideMessagesForMember(famId, targetIds, currentId)
        }
    }

    fun deleteMessagesForEveryone(messageIds: List<String>) {
        val famId = _activeFamilyId.value ?: return
        val currentId = _activeMemberId.value ?: return
        viewModelScope.launch {
            messageIds.forEach { id ->
                val msg = allMessages.value.find { it.id == id } ?: return@forEach
                if (msg.senderId == currentId || activeMember.value?.isAdmin == true) {
                    cloudRepo.updateMessageFields(
                        famId,
                        id,
                        mapOf(
                            "text" to "",
                            "type" to MessageType.DELETED.name,
                            "reactions" to emptyMap<String, String>(),
                            "pollOptions" to emptyList<Map<String, Any>>()
                        )
                    )
                    val chat = rawChats.value.find { it.id == msg.chatId }
                    if (chat?.pinnedMessageId == msg.id) {
                        cloudRepo.updatePinnedMessage(famId, msg.chatId, null)
                    }
                } else {
                    val hidden = JsonHelpers.decodeStringList(msg.hiddenForMemberIdsJson).toMutableSet()
                    hidden.add(currentId)
                    cloudRepo.updateMessageFields(famId, id, mapOf("hiddenForMemberIds" to hidden.toList()))
                }
            }
            showMessage("Mensaje eliminado")
        }
    }

    // --- Real Hardware Voice Recording (MediaRecorder) & Playback (MediaPlayer) ---

    fun startVoiceRecording(chatId: String? = _activeChatId.value) {
        recordingJob?.cancel()
        val started = voiceAudio.startRecording()
        if (!started) {
            showMessage("No se pudo iniciar la grabación de audio")
            return
        }
        if (chatId != null) {
            updateLivePresence(chatId, PresenceStatus.RECORDING)
        }
        _voiceRecording.value = VoiceRecordingState(
            isRecording = true,
            elapsedSec = 0,
            amplitudes = listOf(0.25f)
        )
        recordingJob = viewModelScope.launch {
            var ticks = 0
            while (true) {
                delay(150)
                ticks++
                val current = _voiceRecording.value
                val realAmp = voiceAudio.readNormalizedAmplitude()
                val updatedAmps = (current.amplitudes + realAmp).takeLast(28)
                val sec = (ticks * 150) / 1000
                _voiceRecording.value = current.copy(
                    elapsedSec = sec,
                    amplitudes = updatedAmps
                )
                if (sec >= 90 && chatId != null) {
                    finishAndSendVoiceRecording(chatId, null)
                    break
                }
            }
        }
    }

    fun cancelVoiceRecording() {
        recordingJob?.cancel()
        voiceAudio.cancelRecording()
        _voiceRecording.value = VoiceRecordingState()
        _activeChatId.value?.let { updateLivePresence(it, PresenceStatus.ONLINE, forceClear = true) }
    }

    fun finishAndSendVoiceRecording(chatId: String, replyToId: String? = null) {
        val state = _voiceRecording.value
        recordingJob?.cancel()
        _voiceRecording.value = VoiceRecordingState()
        val famId = _activeFamilyId.value ?: return
        val currentId = _activeMemberId.value ?: return
        val waveform = state.amplitudes.ifEmpty { listOf(0.3f, 0.5f, 0.4f, 0.7f, 0.5f) }

        viewModelScope.launch {
            val encoded = voiceAudio.stopAndEncodeToDataUri()
            updateLivePresence(chatId, PresenceStatus.ONLINE, forceClear = true)
            if (encoded == null) {
                showMessage("Audio demasiado largo o vacío")
                return@launch
            }
            val (durationSec, dataUri) = encoded
            val msgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(10)}"
            val msg = ChatMessageEntity(
                id = msgId,
                chatId = chatId,
                senderId = currentId,
                text = "",
                type = MessageType.VOICE,
                status = MessageStatus.SENT,
                timestamp = System.currentTimeMillis(),
                replyToMessageId = replyToId,
                mediaUri = dataUri,
                voiceDurationSec = durationSec,
                voiceWaveformJson = JsonHelpers.encodeFloatList(waveform),
                authorUid = currentUserId
            )
            val res = cloudRepo.createMessage(famId, msg, currentMemberUids(), currentUserId)
            if (res.isSuccess) {
                markDeliveredIfRecipientOnline(famId, chatId, msgId, currentId)
            }
        }
    }

    fun cycleVoicePlaybackSpeed() {
        val next = when (_voicePlaybackSpeed.value) {
            1.0f -> 1.5f
            1.5f -> 2.0f
            else -> 1.0f
        }
        _voicePlaybackSpeed.value = next
        voiceAudio.setPlaybackSpeed(next)
    }

    fun toggleVoicePlayback(messageId: String) {
        if (_playingVoiceMessageId.value == messageId) {
            stopPlayback()
            return
        }
        val msg = allMessages.value.find { it.id == messageId }
        val mediaData = msg?.mediaUri
        if (mediaData.isNullOrBlank()) {
            showMessage("Este mensaje no tiene audio disponible")
            return
        }
        stopPlayback()
        _playingVoiceMessageId.value = messageId
        _playingVoiceProgress.value = 0f

        val famId = _activeFamilyId.value
        val currentId = _activeMemberId.value
        if (famId != null && currentId != null && msg.senderId != currentId &&
            !JsonHelpers.decodeStringList(msg.playedByMemberIdsJson).contains(currentId)
        ) {
            viewModelScope.launch {
                cloudRepo.markMessageVoicePlayedByMember(famId, messageId, currentId)
            }
        }

        playbackJob = viewModelScope.launch {
            voiceAudio.setPlaybackSpeed(_voicePlaybackSpeed.value)
            val started = voiceAudio.prepareAndStartPlayback(
                messageId = messageId,
                mediaData = mediaData,
                onPrepared = {},
                onCompleted = {
                    _playingVoiceMessageId.value = null
                    _playingVoiceProgress.value = 0f
                },
                onError = { errMsg ->
                    _playingVoiceMessageId.value = null
                    _playingVoiceProgress.value = 0f
                    showMessage(errMsg)
                }
            )
            if (started) {
                while (_playingVoiceMessageId.value == messageId) {
                    _playingVoiceProgress.value = voiceAudio.playbackProgress()
                    delay(100)
                }
            }
        }
    }

    private fun stopPlayback() {
        playbackJob?.cancel()
        voiceAudio.stopPlayback()
        _playingVoiceMessageId.value = null
        _playingVoiceProgress.value = 0f
    }

    fun saveTurnConfig(url: String, username: String, credential: String) {
        viewModelScope.launch {
            prefs.saveTurnConfig(url, username, credential)
            webRtcCallManager.setCustomTurnConfig(url, username, credential)
            showMessage("Servidores TURN para datos móviles actualizados")
        }
    }

    fun sendTestPushNotification() {
        val famId = _activeFamilyId.value
        val memberId = _activeMemberId.value
        if (famId != null && memberId != null) {
            syncPushAndBackgroundWork(famId, memberId)
        }
        FamiChatNotificationHelper.notifyNewMessage(
            context = appContext,
            chatId = _activeChatId.value ?: "group_main",
            chatTitle = "Diagnóstico FamiChat",
            senderName = activeMember.value?.name ?: "FamiChat",
            body = "✅ Las alertas en tiempo real y en segundo plano están funcionando correctamente.",
            isGroup = false
        )
        showMessage("Notificación de prueba enviada al sistema")
    }

    // --- Real Multi-Device Calls (Firestore Signaling + Ringtone + AudioManager) ---

    fun startCall(
        chatId: String,
        title: String,
        avatarEmoji: String,
        avatarColorHex: String,
        callType: CallType
    ) {
        val famId = _activeFamilyId.value ?: return
        val meId = _activeMemberId.value ?: return
        val chat = rawChats.value.find { it.id == chatId }
        val participants = chat?.let { JsonHelpers.decodeStringList(it.participantIdsJson) } ?: emptyList()
        val targets = participants.filterNot { it == meId }.ifEmpty {
            members.value.map { it.id }.filterNot { it == meId }
        }
        val otherPhone = if (chat?.type == ChatType.DIRECT) {
            members.value.find { it.id == targets.firstOrNull() }?.phoneNumber ?: ""
        } else ""

        val callId = "call_${UUID.randomUUID().toString().replace("-", "").take(10)}"
        val record = CallRecordEntity(
            id = callId,
            chatId = chatId,
            title = title,
            callerId = meId,
            avatarColorHex = avatarColorHex,
            avatarEmoji = avatarEmoji,
            callType = callType,
            direction = CallDirection.OUTGOING,
            durationSec = 0,
            timestamp = System.currentTimeMillis(),
            callStatus = CallSignalStatus.RINGING,
            targetMemberIdsJson = JsonHelpers.encodeStringList(targets.ifEmpty { listOf(meId) }),
            connectedAt = 0L,
            authorUid = currentUserId
        )

        callMonitorJob?.cancel()
        _activeCall.value = ActiveCallUiState(
            callId = callId,
            chatId = chatId,
            title = title,
            avatarEmoji = avatarEmoji,
            avatarColorHex = avatarColorHex,
            callType = callType,
            isOutgoing = true,
            isConnected = false,
            durationSec = 0,
            isSpeakerOn = callType == CallType.VIDEO,
            isCameraOn = callType == CallType.VIDEO,
            targetPhoneNumber = otherPhone
        )
        voiceAudio.startRingtone(isIncoming = false)
        voiceAudio.applyAudioCallHardware(isMuted = false, isSpeakerOn = callType == CallType.VIDEO)

        viewModelScope.launch {
            cloudRepo.createCall(famId, record, currentMemberUids(), currentUserId)
            webRtcCallManager.startCallerSession(
                isVideoCall = callType == CallType.VIDEO,
                onOfferCreated = { offerSdp ->
                    viewModelScope.launch {
                        cloudRepo.updateCallSdpOffer(famId, callId, offerSdp)
                    }
                },
                onLocalIceCandidate = { cand ->
                    viewModelScope.launch {
                        cloudRepo.addCallIceCandidate(
                            familyId = famId,
                            callId = callId,
                            candidate = CallIceCandidateEntity(
                                id = "cand_${UUID.randomUUID().toString().replace("-", "").take(10)}",
                                senderMemberId = meId,
                                sdpMid = cand.sdpMid ?: "0",
                                sdpMLineIndex = cand.sdpMLineIndex,
                                candidate = cand.sdp,
                                createdAtMs = System.currentTimeMillis(),
                                authorUid = currentUserId
                            ),
                            memberUids = currentMemberUids(),
                            callerUid = currentUserId
                        )
                    }
                }
            )
            monitorCallLifecycle(famId, callId, isOutgoing = true)
        }
    }

    fun acceptIncomingCall(call: CallRecordEntity) {
        val famId = _activeFamilyId.value ?: return
        val meId = _activeMemberId.value ?: return
        voiceAudio.stopRingtone()
        val caller = members.value.find { it.id == call.callerId }
        callMonitorJob?.cancel()
        _activeCall.value = ActiveCallUiState(
            callId = call.id,
            chatId = call.chatId,
            title = caller?.name ?: call.title,
            avatarEmoji = caller?.avatarEmoji ?: call.avatarEmoji,
            avatarColorHex = caller?.avatarColorHex ?: call.avatarColorHex,
            callType = call.callType,
            isOutgoing = false,
            isConnected = true,
            durationSec = 0,
            isSpeakerOn = call.callType == CallType.VIDEO,
            isCameraOn = call.callType == CallType.VIDEO,
            targetPhoneNumber = caller?.phoneNumber ?: ""
        )
        voiceAudio.applyAudioCallHardware(isMuted = false, isSpeakerOn = call.callType == CallType.VIDEO)
        viewModelScope.launch {
            cloudRepo.updateCallStatus(
                familyId = famId,
                callId = call.id,
                signalStatus = CallSignalStatus.CONNECTED,
                connectedAt = System.currentTimeMillis(),
                directionName = CallDirection.INCOMING.name
            )
            var lastHandledOfferSdp: String? = null
            fun tryAnswerOffer(offerSdp: String?) {
                if (offerSdp.isNullOrBlank() || offerSdp == lastHandledOfferSdp) return
                val isFirstOffer = lastHandledOfferSdp == null
                lastHandledOfferSdp = offerSdp
                if (isFirstOffer) {
                    webRtcCallManager.startCalleeSession(
                        isVideoCall = call.callType == CallType.VIDEO,
                        offerSdp = offerSdp,
                        onAnswerCreated = { answerSdp ->
                            viewModelScope.launch {
                                cloudRepo.updateCallSdpAnswer(famId, call.id, answerSdp)
                            }
                        },
                        onLocalIceCandidate = { cand ->
                            viewModelScope.launch {
                                cloudRepo.addCallIceCandidate(
                                    familyId = famId,
                                    callId = call.id,
                                    candidate = CallIceCandidateEntity(
                                        id = "cand_${UUID.randomUUID().toString().replace("-", "").take(10)}",
                                        senderMemberId = meId,
                                        sdpMid = cand.sdpMid ?: "0",
                                        sdpMLineIndex = cand.sdpMLineIndex,
                                        candidate = cand.sdp,
                                        createdAtMs = System.currentTimeMillis(),
                                        authorUid = currentUserId
                                    ),
                                    memberUids = currentMemberUids(),
                                    callerUid = currentUserId
                                )
                            }
                        }
                    )
                } else {
                    webRtcCallManager.handleRemoteIceRestartOffer(
                        offerSdp = offerSdp,
                        onAnswerCreated = { answerSdp ->
                            viewModelScope.launch {
                                cloudRepo.updateCallSdpAnswer(famId, call.id, answerSdp)
                            }
                        }
                    )
                }
            }
            tryAnswerOffer(call.offerSdp)
            monitorCallLifecycle(famId, call.id, isOutgoing = false, onLateOffer = ::tryAnswerOffer)
        }
    }

    fun declineIncomingCall(call: CallRecordEntity) {
        val famId = _activeFamilyId.value ?: return
        voiceAudio.stopRingtone()
        webRtcCallManager.closeSession()
        viewModelScope.launch {
            cloudRepo.updateCallStatus(
                familyId = famId,
                callId = call.id,
                signalStatus = CallSignalStatus.DECLINED,
                directionName = CallDirection.MISSED.name
            )
        }
    }

    private fun monitorCallLifecycle(
        famId: String,
        callId: String,
        isOutgoing: Boolean,
        onLateOffer: (String?) -> Unit = {}
    ) {
        val meId = _activeMemberId.value.orEmpty()
        callMonitorJob = viewModelScope.launch {
            val seenCandidateIds = mutableSetOf<String>()
            val candidateWatcher = launch {
                cloudRepo.observeCallIceCandidates(famId, callId, currentUserId)
                    .safeFirestoreFlow(emptyList())
                    .collect { candidates ->
                        candidates.forEach { cand ->
                            if (cand.senderMemberId != meId && seenCandidateIds.add(cand.id)) {
                                webRtcCallManager.addRemoteIceCandidate(
                                    sdpMid = cand.sdpMid,
                                    sdpMLineIndex = cand.sdpMLineIndex,
                                    candidateSdp = cand.candidate
                                )
                            }
                        }
                    }
            }

            // Observe remote state transitions & SDP offer/answer
            val watcher = launch {
                calls.collect { list ->
                    val remote = list.find { it.id == callId } ?: return@collect
                    val local = _activeCall.value ?: return@collect
                    if (!isOutgoing && !remote.offerSdp.isNullOrBlank()) {
                        onLateOffer(remote.offerSdp)
                    }
                    if (isOutgoing && !remote.answerSdp.isNullOrBlank()) {
                        webRtcCallManager.applyRemoteAnswerIfNeeded(remote.answerSdp)
                    }
                    when (remote.callStatus) {
                        CallSignalStatus.CONNECTED -> {
                            if (!local.isConnected) {
                                voiceAudio.stopRingtone()
                                _activeCall.value = local.copy(isConnected = true)
                            }
                        }
                        CallSignalStatus.DECLINED -> {
                            candidateWatcher.cancel()
                            voiceAudio.stopRingtone()
                            voiceAudio.applyAudioCallHardware(isMuted = false, isSpeakerOn = false)
                            webRtcCallManager.closeSession()
                            _activeCall.value = null
                            showMessage("Llamada rechazada")
                            callMonitorJob?.cancel()
                        }
                        CallSignalStatus.ENDED, CallSignalStatus.MISSED -> {
                            candidateWatcher.cancel()
                            voiceAudio.stopRingtone()
                            voiceAudio.applyAudioCallHardware(isMuted = false, isSpeakerOn = false)
                            webRtcCallManager.closeSession()
                            _activeCall.value = null
                            callMonitorJob?.cancel()
                        }
                        CallSignalStatus.RINGING -> Unit
                    }
                }
            }

            // Duration & 45s ring timeout loop
            var ringElapsed = 0
            while (_activeCall.value != null) {
                delay(1000)
                val cur = _activeCall.value ?: break
                if (cur.isConnected) {
                    _activeCall.value = cur.copy(durationSec = cur.durationSec + 1)
                } else if (isOutgoing) {
                    ringElapsed++
                    if (ringElapsed >= 45) {
                        watcher.cancel()
                        candidateWatcher.cancel()
                        voiceAudio.stopRingtone()
                        voiceAudio.applyAudioCallHardware(isMuted = false, isSpeakerOn = false)
                        webRtcCallManager.closeSession()
                        _activeCall.value = null
                        cloudRepo.updateCallStatus(
                            familyId = famId,
                            callId = callId,
                            signalStatus = CallSignalStatus.MISSED,
                            durationSec = 0,
                            directionName = CallDirection.MISSED.name
                        )
                        showMessage("Sin respuesta")
                        break
                    }
                }
            }
        }
    }

    fun toggleCallMute() {
        val next = _activeCall.value?.let { it.copy(isMuted = !it.isMuted) } ?: return
        _activeCall.value = next
        voiceAudio.applyAudioCallHardware(isMuted = next.isMuted, isSpeakerOn = next.isSpeakerOn)
        webRtcCallManager.setMuted(next.isMuted)
    }

    fun toggleCallSpeaker() {
        val next = _activeCall.value?.let { it.copy(isSpeakerOn = !it.isSpeakerOn) } ?: return
        _activeCall.value = next
        voiceAudio.applyAudioCallHardware(isMuted = next.isMuted, isSpeakerOn = next.isSpeakerOn)
        webRtcCallManager.setSpeakerphone(next.isSpeakerOn)
    }

    fun toggleCallCamera() {
        val next = _activeCall.value?.let { it.copy(isCameraOn = !it.isCameraOn) } ?: return
        _activeCall.value = next
        webRtcCallManager.setCameraEnabled(next.isCameraOn)
    }

    fun switchCallCamera() {
        webRtcCallManager.switchCamera()
    }

    fun endActiveCall() {
        callMonitorJob?.cancel()
        voiceAudio.stopRingtone()
        voiceAudio.applyAudioCallHardware(isMuted = false, isSpeakerOn = false)
        webRtcCallManager.closeSession()
        val callState = _activeCall.value ?: return
        _activeCall.value = null
        val famId = _activeFamilyId.value ?: return
        viewModelScope.launch {
            val finalStatus = if (callState.isConnected) CallSignalStatus.ENDED else CallSignalStatus.MISSED
            val finalDir = if (callState.isConnected) {
                if (callState.isOutgoing) CallDirection.OUTGOING else CallDirection.INCOMING
            } else {
                CallDirection.MISSED
            }
            cloudRepo.updateCallStatus(
                familyId = famId,
                callId = callState.callId,
                signalStatus = finalStatus,
                durationSec = callState.durationSec,
                directionName = finalDir.name
            )
        }
    }

    fun deleteCallRecord(id: String) {
        val famId = _activeFamilyId.value ?: return
        viewModelScope.launch { cloudRepo.deleteCall(famId, id) }
    }

    // --- Tablón Familiar ---

    fun addShoppingItem(name: String, quantity: String, category: String) {
        val famId = _activeFamilyId.value ?: return
        val currentId = _activeMemberId.value ?: return
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            cloudRepo.upsertShoppingItem(
                familyId = famId,
                item = ShoppingItemEntity(
                    id = "shop_${UUID.randomUUID().toString().replace("-", "").take(8)}",
                    name = clean,
                    quantity = quantity.trim().ifEmpty { "1 u." },
                    category = category.trim().ifEmpty { "General" },
                    isChecked = false,
                    addedById = currentId,
                    createdAt = System.currentTimeMillis(),
                    authorUid = currentUserId
                ),
                memberUids = currentMemberUids(),
                callerUid = currentUserId,
                isCreate = true
            )
        }
    }

    fun toggleShoppingItem(item: ShoppingItemEntity) {
        val famId = _activeFamilyId.value ?: return
        viewModelScope.launch {
            cloudRepo.upsertShoppingItem(
                familyId = famId,
                item = item.copy(isChecked = !item.isChecked),
                memberUids = currentMemberUids(),
                callerUid = currentUserId,
                isCreate = false
            )
        }
    }

    fun deleteShoppingItem(id: String) {
        val famId = _activeFamilyId.value ?: return
        viewModelScope.launch { cloudRepo.deleteShoppingItem(famId, id) }
    }

    fun clearCheckedShoppingItems() {
        val famId = _activeFamilyId.value ?: return
        val checkedIds = shoppingItems.value.filter { it.isChecked }.map { it.id }
        if (checkedIds.isEmpty()) return
        viewModelScope.launch {
            checkedIds.forEach { cloudRepo.deleteShoppingItem(famId, it) }
            showMessage("Productos marcados limpiados")
        }
    }

    fun addFamilyEvent(
        title: String,
        dateLabel: String,
        timeLabel: String,
        description: String,
        assignedMemberId: String,
        colorHex: String
    ) {
        val famId = _activeFamilyId.value ?: return
        val clean = title.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            cloudRepo.createEvent(
                familyId = famId,
                event = FamilyEventEntity(
                    id = "evt_${UUID.randomUUID().toString().replace("-", "").take(8)}",
                    title = clean,
                    dateLabel = dateLabel.trim().ifEmpty { "Esta semana" },
                    timeLabel = timeLabel.trim().ifEmpty { "Todo el día" },
                    description = description.trim(),
                    assignedMemberId = assignedMemberId,
                    colorHex = colorHex,
                    createdAt = System.currentTimeMillis(),
                    authorUid = currentUserId
                ),
                memberUids = currentMemberUids(),
                callerUid = currentUserId
            )
        }
    }

    fun deleteFamilyEvent(id: String) {
        val famId = _activeFamilyId.value ?: return
        viewModelScope.launch { cloudRepo.deleteEvent(famId, id) }
    }

    fun saveFamilyNote(existingId: String?, title: String, content: String, colorHex: String, isPinned: Boolean) {
        val famId = _activeFamilyId.value ?: return
        val currentId = _activeMemberId.value ?: return
        val cleanTitle = title.trim()
        val cleanContent = content.trim()
        if (cleanTitle.isEmpty() || cleanContent.isEmpty()) return
        viewModelScope.launch {
            val isCreate = existingId == null
            cloudRepo.upsertNote(
                familyId = famId,
                note = FamilyNoteEntity(
                    id = existingId ?: "note_${UUID.randomUUID().toString().replace("-", "").take(8)}",
                    title = cleanTitle,
                    content = cleanContent,
                    authorId = currentId,
                    colorHex = colorHex,
                    isPinned = isPinned,
                    createdAt = System.currentTimeMillis(),
                    authorUid = currentUserId
                ),
                memberUids = currentMemberUids(),
                callerUid = currentUserId,
                isCreate = isCreate
            )
        }
    }

    fun deleteFamilyNote(id: String) {
        val famId = _activeFamilyId.value ?: return
        viewModelScope.launch { cloudRepo.deleteNote(famId, id) }
    }

    // --- 24h Family Statuses / Stories & Forwarding ---

    private val _statusPublishState = MutableStateFlow<StatusPublishUiState>(StatusPublishUiState.Idle)
    val statusPublishState: StateFlow<StatusPublishUiState> = _statusPublishState.asStateFlow()
    private var activeStatusPublishJob: Job? = null
    private var activeStatusPublishId: String? = null
    private val publishedDraftIdsInSession = mutableSetOf<String>()

    private fun isNetworkConnected(): Boolean {
        return try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true
        }
    }

    private fun friendlyPublishError(err: Throwable?, isTimeout: Boolean): String {
        if (isTimeout || !isNetworkConnected()) {
            return "Sin conexión. Tu estado no se perdió."
        }
        val fsCode = err?.unwrapFirestoreException()?.code
        if (fsCode == com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE ||
            fsCode == com.google.firebase.firestore.FirebaseFirestoreException.Code.DEADLINE_EXCEEDED
        ) {
            return "Sin conexión. Tu estado no se perdió."
        }
        val raw = err?.localizedMessage?.trim().orEmpty()
        return if (raw.contains("network", ignoreCase = true) ||
            raw.contains("offline", ignoreCase = true) ||
            raw.contains("unable to resolve", ignoreCase = true)
        ) {
            "Sin conexión. Tu estado no se perdió."
        } else {
            raw.takeIf { it.isNotBlank() && it.length <= 120 }
                ?: "No se pudo subir. Tu estado no se perdió."
        }
    }

    fun dismissStatusPublishError() {
        if (_statusPublishState.value is StatusPublishUiState.Error) {
            _statusPublishState.value = StatusPublishUiState.Idle
        }
    }

    fun cancelStatusPublish() {
        val idToClear = activeStatusPublishId
        activeStatusPublishJob?.cancel()
        activeStatusPublishJob = null
        activeStatusPublishId = null
        if (idToClear != null) {
            _mediaTransferProgress.value = _mediaTransferProgress.value - idToClear
        }
        _statusPublishState.value = StatusPublishUiState.Idle
    }

    fun resetStatusPublishSession() {
        cancelStatusPublish()
        publishedDraftIdsInSession.clear()
    }

    fun publishStatus(request: StatusPublishRequest, onSuccess: () -> Unit) {
        publishStatusBatch(listOf(request), onSuccess)
    }

    fun publishStatusBatch(requests: List<StatusPublishRequest>, onSuccess: () -> Unit) {
        if (activeStatusPublishJob?.isActive == true) return
        val nonEmpty = requests.filter {
            when (it.mode) {
                StatusComposerMode.TEXT ->
                    it.text.trim().isNotEmpty() || it.overlayBundle?.isEmptyEdits() == false
                StatusComposerMode.PHOTO -> it.photoUri != null
                StatusComposerMode.VIDEO -> it.videoUri != null
            }
        }
        if (nonEmpty.isEmpty()) {
            _statusPublishState.value = StatusPublishUiState.Idle
            return
        }
        val pendingRequests = filterPendingStatusBatchRequests(nonEmpty, publishedDraftIdsInSession)
        if (pendingRequests.isEmpty()) {
            publishedDraftIdsInSession.clear()
            _statusPublishState.value = StatusPublishUiState.Idle
            showMessage("Estado publicado")
            onSuccess()
            return
        }
        val famId = _activeFamilyId.value
        val currentId = _activeMemberId.value
        if (famId.isNullOrBlank() || currentId.isNullOrBlank()) {
            _statusPublishState.value = StatusPublishUiState.Error("No hay un perfil activo.")
            return
        }
        activeStatusPublishJob = viewModelScope.launch {
            try {
                for (req in pendingRequests) {
                    coroutineContext.ensureActive()
                    val ok = publishSingleStatusInternal(req, famId, currentId)
                    if (!ok) return@launch
                    if (req.draftId.isNotBlank()) {
                        publishedDraftIdsInSession.add(req.draftId)
                    }
                }
                publishedDraftIdsInSession.clear()
                _statusPublishState.value = StatusPublishUiState.Idle
                showMessage("Estado publicado")
                onSuccess()
            } catch (e: CancellationException) {
                _statusPublishState.value = StatusPublishUiState.Idle
                throw e
            } finally {
                activeStatusPublishJob = null
                activeStatusPublishId = null
            }
        }
    }

    private suspend fun publishSingleStatusInternal(
        request: StatusPublishRequest,
        famId: String,
        currentId: String
    ): Boolean {
        val statusId = "st_${UUID.randomUUID().toString().replace("-", "").take(10)}"
        activeStatusPublishId = statusId
        try {
            when (request.mode) {
                StatusComposerMode.TEXT -> {
                    val clean = request.text.trim()
                    val hasOverlayEdits = request.overlayBundle?.isEmptyEdits() == false
                    if (clean.isEmpty() && !hasOverlayEdits) {
                        _statusPublishState.value = StatusPublishUiState.Idle
                        return false
                    }
                    _statusPublishState.value = StatusPublishUiState.Publishing(StatusComposerMode.TEXT, 0f)
                    if (!isNetworkConnected()) {
                        _statusPublishState.value = StatusPublishUiState.Error("Sin conexión. Tu estado no se perdió.")
                        return false
                    }
                    val now = System.currentTimeMillis()
                    val status = FamilyStatusEntity(
                        id = statusId,
                        memberId = currentId,
                        text = clean.take(400),
                        mediaUri = null,
                        mediaType = "TEXT",
                        backgroundHex = request.backgroundHex,
                        createdAt = now,
                        expiresAt = now + FamilyStatusEntity.STATUS_TTL_MS,
                        viewedByMemberIdsJson = JsonHelpers.encodeStringList(listOf(currentId)),
                        authorUid = currentUserId,
                        musicTrack = request.musicTrack,
                        overlayBundle = request.overlayBundle?.takeIf { !it.isEmptyEdits() },
                        visibleToMemberIds = request.visibleToMemberIds
                    )
                    val res = withTimeoutOrNull(18_000L) {
                        cloudRepo.createStatus(famId, status, currentMemberUids(), currentUserId)
                    }
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    return if (res != null && res.isSuccess) {
                        true
                    } else {
                        _statusPublishState.value = StatusPublishUiState.Error(
                            friendlyPublishError(res?.exceptionOrNull(), isTimeout = res == null)
                        )
                        false
                    }
                }

                StatusComposerMode.PHOTO -> {
                    val pickedUri = request.photoUri ?: run {
                        _statusPublishState.value = StatusPublishUiState.Idle
                        return false
                    }
                    _statusPublishState.value = StatusPublishUiState.Publishing(StatusComposerMode.PHOTO, 0.14f)
                    val dataUri = com.example.util.StatusPhotoCanvasRenderer.renderAndCompressPhoto(
                        context = appContext,
                        photoUri = pickedUri,
                        overlayBundle = request.overlayBundle,
                        viewportAspectRatio = request.viewportAspectRatio,
                        useContainWithBlur = request.useContainWithBlur
                    )
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    if (dataUri == null) {
                        _statusPublishState.value = StatusPublishUiState.Error("No se pudo procesar la foto elegida.")
                        return false
                    }
                    _statusPublishState.value = StatusPublishUiState.Publishing(StatusComposerMode.PHOTO, 0.48f)
                    if (!isNetworkConnected()) {
                        _statusPublishState.value = StatusPublishUiState.Error("Sin conexión. Tu estado no se perdió.")
                        return false
                    }
                    val now = System.currentTimeMillis()
                    val status = FamilyStatusEntity(
                        id = statusId,
                        memberId = currentId,
                        text = request.photoCaption.trim().take(200),
                        mediaUri = dataUri,
                        mediaType = "IMAGE",
                        backgroundHex = "#0B141A",
                        createdAt = now,
                        expiresAt = now + FamilyStatusEntity.STATUS_TTL_MS,
                        viewedByMemberIdsJson = JsonHelpers.encodeStringList(listOf(currentId)),
                        authorUid = currentUserId,
                        musicTrack = request.musicTrack,
                        visibleToMemberIds = request.visibleToMemberIds
                    )
                    _statusPublishState.value = StatusPublishUiState.Publishing(StatusComposerMode.PHOTO, 0.72f)
                    val res = withTimeoutOrNull(22_000L) {
                        cloudRepo.createStatus(famId, status, currentMemberUids(), currentUserId)
                    }
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    return if (res != null && res.isSuccess) {
                        _statusPublishState.value = StatusPublishUiState.Publishing(StatusComposerMode.PHOTO, 1f)
                        true
                    } else {
                        _statusPublishState.value = StatusPublishUiState.Error(
                            friendlyPublishError(res?.exceptionOrNull(), isTimeout = res == null)
                        )
                        false
                    }
                }

                StatusComposerMode.VIDEO -> {
                    val pickedUri = request.videoUri ?: run {
                        _statusPublishState.value = StatusPublishUiState.Idle
                        return false
                    }
                    _statusPublishState.value = StatusPublishUiState.Publishing(StatusComposerMode.VIDEO, 0.06f)
                    _mediaTransferProgress.value = _mediaTransferProgress.value + (statusId to 0.06f)
                    val encodedRes = VideoMediaHelper.readAndEncodeVideo(
                        context = appContext,
                        uri = pickedUri,
                        maxDurationSec = VideoMediaHelper.MAX_STATUS_VIDEO_DURATION_SEC
                    )
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    val payload = encodedRes.getOrElse { err ->
                        _mediaTransferProgress.value = _mediaTransferProgress.value - statusId
                        _statusPublishState.value = StatusPublishUiState.Error(
                            err.localizedMessage?.takeIf { it.isNotBlank() }
                                ?: "No se pudo procesar el video del estado."
                        )
                        return false
                    }

                    VideoMediaHelper.writeAssembledVideoToCache(
                        context = appContext,
                        messageId = statusId,
                        fileName = "estado_${statusId}.mp4",
                        fullDataUri = payload.dataUri
                    )

                    _statusPublishState.value = StatusPublishUiState.Publishing(StatusComposerMode.VIDEO, 0.14f)
                    _mediaTransferProgress.value = _mediaTransferProgress.value + (statusId to 0.14f)
                    if (!isNetworkConnected()) {
                        _mediaTransferProgress.value = _mediaTransferProgress.value - statusId
                        _statusPublishState.value = StatusPublishUiState.Error("Sin conexión. Tu estado no se perdió.")
                        return false
                    }

                    val uids = currentMemberUids()
                    val chunks = payload.chunks
                    if (chunks.isNotEmpty()) {
                        for ((idx, chunkData) in chunks.withIndex()) {
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            val pct = 0.15f + (idx + 1).toFloat() / chunks.size.toFloat() * 0.75f
                            _mediaTransferProgress.value = _mediaTransferProgress.value + (statusId to pct)
                            _statusPublishState.value = StatusPublishUiState.Publishing(StatusComposerMode.VIDEO, pct)
                            val chunkEntity = MediaChunkEntity(
                                id = "${statusId}_c$idx",
                                messageId = statusId,
                                chunkIndex = idx,
                                totalChunks = chunks.size,
                                data = chunkData,
                                authorUid = currentUserId
                            )
                            val chunkRes = withTimeoutOrNull(25_000L) {
                                cloudRepo.uploadMediaChunk(famId, chunkEntity, uids, currentUserId)
                            }
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            if (chunkRes == null || chunkRes.isFailure) {
                                _mediaTransferProgress.value = _mediaTransferProgress.value - statusId
                                _statusPublishState.value = StatusPublishUiState.Error(
                                    friendlyPublishError(chunkRes?.exceptionOrNull(), isTimeout = chunkRes == null)
                                )
                                return false
                            }
                        }
                    } else {
                        _mediaTransferProgress.value = _mediaTransferProgress.value + (statusId to 0.85f)
                        _statusPublishState.value = StatusPublishUiState.Publishing(StatusComposerMode.VIDEO, 0.85f)
                    }

                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    _statusPublishState.value = StatusPublishUiState.Publishing(StatusComposerMode.VIDEO, 0.92f)
                    val now = System.currentTimeMillis()
                    val status = FamilyStatusEntity(
                        id = statusId,
                        memberId = currentId,
                        text = request.videoCaption.trim().take(200),
                        mediaUri = if (chunks.isEmpty()) payload.dataUri else "chunked:${payload.mimeType}",
                        mediaType = "VIDEO",
                        videoThumbnailData = payload.thumbnailDataUri,
                        videoDurationSec = payload.durationSec,
                        chunkCount = chunks.size,
                        backgroundHex = "#0B141A",
                        createdAt = now,
                        expiresAt = now + FamilyStatusEntity.STATUS_TTL_MS,
                        viewedByMemberIdsJson = JsonHelpers.encodeStringList(listOf(currentId)),
                        authorUid = currentUserId,
                        musicTrack = request.musicTrack,
                        overlayBundle = request.overlayBundle?.takeIf { !it.isEmptyEdits() },
                        visibleToMemberIds = request.visibleToMemberIds
                    )
                    val res = withTimeoutOrNull(22_000L) {
                        cloudRepo.createStatus(famId, status, uids, currentUserId)
                    }
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    _mediaTransferProgress.value = _mediaTransferProgress.value - statusId
                    return if (res != null && res.isSuccess) {
                        _statusPublishState.value = StatusPublishUiState.Publishing(StatusComposerMode.VIDEO, 1f)
                        true
                    } else {
                        _statusPublishState.value = StatusPublishUiState.Error(
                            friendlyPublishError(res?.exceptionOrNull(), isTimeout = res == null)
                        )
                        false
                    }
                }
            }
        } catch (e: CancellationException) {
            _mediaTransferProgress.value = _mediaTransferProgress.value - statusId
            _statusPublishState.value = StatusPublishUiState.Idle
            throw e
        } finally {
            _mediaTransferProgress.value = _mediaTransferProgress.value - statusId
        }
    }

    fun publishTextStatus(text: String, backgroundHex: String) {
        publishStatus(
            StatusPublishRequest(
                mode = StatusComposerMode.TEXT,
                text = text,
                backgroundHex = backgroundHex
            ),
            onSuccess = {}
        )
    }

    fun publishPhotoStatus(pickedUri: Uri, caption: String) {
        publishStatus(
            StatusPublishRequest(
                mode = StatusComposerMode.PHOTO,
                photoUri = pickedUri,
                photoCaption = caption
            ),
            onSuccess = {}
        )
    }

    fun publishVideoStatus(pickedUri: Uri, caption: String) {
        publishStatus(
            StatusPublishRequest(
                mode = StatusComposerMode.VIDEO,
                videoUri = pickedUri,
                videoCaption = caption
            ),
            onSuccess = {}
        )
    }

    fun prepareStatusVideo(
        status: FamilyStatusEntity,
        onReady: (java.io.File) -> Unit,
        onError: (String) -> Unit = {}
    ) {
        val famId = _activeFamilyId.value
        if (famId.isNullOrBlank()) {
            onError("No hay una familia activa para cargar el video.")
            return
        }
        viewModelScope.launch {
            val fileName = "estado_${status.id}.mp4"
            val cached = VideoMediaHelper.findExistingCachedVideoFile(appContext, status.id, fileName)
            if (cached != null && cached.exists() && cached.length() > 0L) {
                onReady(cached)
                return@launch
            }
            val isChunkedMedia = status.chunkCount > 0 || status.mediaUri?.startsWith("chunked:") == true
            if (isChunkedMedia) {
                _mediaTransferProgress.value = _mediaTransferProgress.value + (status.id to 0.08f)
                val chunksRes = cloudRepo.fetchMediaChunks(
                    familyId = famId,
                    messageId = status.id,
                    totalChunks = status.chunkCount,
                    onProgress = { prog ->
                        val scaled = (0.10f + prog * 0.80f).coerceIn(0.10f, 0.92f)
                        _mediaTransferProgress.value = _mediaTransferProgress.value + (status.id to scaled)
                    },
                    callerUid = currentUserId
                )
                chunksRes.onSuccess { fullData ->
                    _mediaTransferProgress.value = _mediaTransferProgress.value + (status.id to 0.95f)
                    val writeRes = VideoMediaHelper.writeAssembledVideoToCache(
                        context = appContext,
                        messageId = status.id,
                        fileName = fileName,
                        fullDataUri = fullData
                    )
                    _mediaTransferProgress.value = _mediaTransferProgress.value - status.id
                    writeRes.onSuccess(onReady).onFailure {
                        val msg = "No se pudo preparar el video del estado."
                        showMessage(msg)
                        onError(msg)
                    }
                }.onFailure { err ->
                    _mediaTransferProgress.value = _mediaTransferProgress.value - status.id
                    val msg = err.localizedMessage?.takeIf { it.isNotBlank() }
                        ?: "No se pudo descargar el video del estado."
                    showMessage(msg)
                    onError(msg)
                }
            } else if (!status.mediaUri.isNullOrBlank() && status.mediaUri.startsWith("data:video")) {
                _mediaTransferProgress.value = _mediaTransferProgress.value + (status.id to 0.50f)
                val writeRes = VideoMediaHelper.writeAssembledVideoToCache(
                    context = appContext,
                    messageId = status.id,
                    fileName = fileName,
                    fullDataUri = status.mediaUri
                )
                _mediaTransferProgress.value = _mediaTransferProgress.value - status.id
                writeRes.onSuccess(onReady).onFailure {
                    val msg = "No se pudo reproducir el video del estado."
                    showMessage(msg)
                    onError(msg)
                }
            } else {
                val msg = "Este video ya no está disponible en la nube."
                showMessage(msg)
                onError(msg)
            }
        }
    }

    fun saveStatusMediaToGallery(status: FamilyStatusEntity) {
        if (status.isVideo) {
            prepareStatusVideo(
                status = status,
                onReady = { file ->
                    viewModelScope.launch {
                        val res = VideoMediaHelper.saveVideoToGallery(appContext, file)
                        showMessage(res.getOrElse { it.localizedMessage ?: "No se pudo guardar el video" })
                    }
                },
                onError = { msg -> showMessage(msg) }
            )
        } else if (!status.mediaUri.isNullOrBlank()) {
            viewModelScope.launch {
                val res = com.example.util.ImageGalleryAndShareHelper.saveImageToGallery(appContext, status.mediaUri)
                showMessage(res.getOrElse { it.localizedMessage ?: "No se pudo guardar la foto" })
            }
        }
    }

    fun markStatusViewed(statusId: String) {
        val famId = _activeFamilyId.value ?: return
        val currentId = _activeMemberId.value ?: return
        val st = statuses.value.find { it.id == statusId } ?: return
        if (JsonHelpers.decodeStringList(st.viewedByMemberIdsJson).contains(currentId)) return
        viewModelScope.launch {
            cloudRepo.markStatusViewed(famId, statusId, currentId)
        }
    }

    fun deleteStatus(statusId: String) {
        val famId = _activeFamilyId.value ?: return
        viewModelScope.launch {
            VideoMediaHelper.deleteCachedVideoFile(appContext, statusId, "estado_${statusId}.mp4")
            cloudRepo.deleteStatus(famId, statusId)
            showMessage("Estado eliminado")
        }
    }

    fun replyToStatus(status: FamilyStatusEntity, replyText: String, onOpenChat: (String) -> Unit) {
        val clean = replyText.trim()
        if (clean.isEmpty()) return
        val fallbackLabel = if (status.isVideo) "🎬 Video de estado" else "📷 Foto de estado"
        val quoteLabel = status.text.trim().ifBlank { fallbackLabel }.take(60)
        createOrOpenDirectChat(status.memberId, openAfter = true) { chatId ->
            sendTextMessage(chatId, "_Sobre tu estado \"$quoteLabel\":_\n$clean")
            onOpenChat(chatId)
        }
    }

    fun forwardMessage(source: ChatMessageEntity, targetChatId: String) {
        val famId = _activeFamilyId.value ?: return
        val currentId = _activeMemberId.value ?: return
        if (source.type == MessageType.DELETED) return
        val originalSender = members.value.find { it.id == source.senderId }?.name ?: "Familiar"
        val cleanPollJson = if (source.type == MessageType.POLL) {
            val resetOpts = JsonHelpers.decodePollOptions(source.pollOptionsJson)
                .map { it.copy(voterIds = emptyList()) }
            JsonHelpers.encodePollOptions(resetOpts)
        } else {
            source.pollOptionsJson
        }
        val msgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(10)}"
        val forwarded = ChatMessageEntity(
            id = msgId,
            chatId = targetChatId,
            senderId = currentId,
            text = source.text,
            type = source.type,
            status = MessageStatus.SENT,
            timestamp = System.currentTimeMillis(),
            forwardedFromName = source.forwardedFromName?.ifBlank { originalSender } ?: originalSender,
            mediaUri = source.mediaUri,
            fileName = source.fileName,
            fileMimeType = source.fileMimeType,
            fileSizeBytes = source.fileSizeBytes,
            voiceDurationSec = source.voiceDurationSec,
            voiceWaveformJson = source.voiceWaveformJson,
            videoThumbnailData = source.videoThumbnailData,
            chunkCount = source.chunkCount,
            locationName = source.locationName,
            locationAddress = source.locationAddress,
            locationLat = source.locationLat,
            locationLng = source.locationLng,
            pollQuestion = source.pollQuestion,
            pollOptionsJson = cleanPollJson,
            authorUid = currentUserId
        )
        viewModelScope.launch {
            if (source.chunkCount > 0) {
                val chunksRes = cloudRepo.fetchMediaChunks(
                    familyId = famId,
                    messageId = source.id,
                    totalChunks = source.chunkCount,
                    callerUid = currentUserId
                )
                chunksRes.onSuccess { fullData ->
                    val newChunks = DocumentMediaHelper.splitIntoChunks(fullData)
                    val uids = currentMemberUids()
                    newChunks.forEachIndexed { idx, chunkStr ->
                        cloudRepo.uploadMediaChunk(
                            familyId = famId,
                            chunk = MediaChunkEntity(
                                id = "${msgId}_c$idx",
                                messageId = msgId,
                                chunkIndex = idx,
                                totalChunks = newChunks.size,
                                data = chunkStr,
                                authorUid = currentUserId
                            ),
                            memberUids = uids,
                            callerUid = currentUserId
                        )
                    }
                }
            }
            val res = cloudRepo.createMessage(famId, forwarded, currentMemberUids(), currentUserId)
            if (res.isSuccess) {
                markDeliveredIfRecipientOnline(famId, targetChatId, msgId, currentId)
                showMessage("Mensaje reenviado")
            } else {
                showMessage("No se pudo reenviar el mensaje")
            }
        }
    }

    private data class Tuple5<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)

    override fun onCleared() {
        super.onCleared()
        liveLocationJobs.values.forEach { it.cancel() }
        liveLocationJobs.clear()
        webRtcCallManager.closeSession()
        voiceAudio.releaseAll()
    }

    companion object {
        fun matchesSwitchKey(entered: String, activeSwitchPin: String): Boolean =
            activeSwitchPin.length == 4 && entered == activeSwitchPin

        fun filterPendingStatusBatchRequests(
            requests: List<StatusPublishRequest>,
            alreadyPublishedDraftIds: Set<String>
        ): List<StatusPublishRequest> =
            requests.filter { req ->
                req.draftId.isBlank() || req.draftId !in alreadyPublishedDraftIds
            }

        fun isMessageVisibleForMember(
            msg: ChatMessageEntity,
            memberId: String,
            clearedAtMap: Map<String, Long>,
            locallyHiddenKeys: Set<String>
        ): Boolean {
            if (JsonHelpers.decodeStringList(msg.hiddenForMemberIdsJson).contains(memberId)) return false
            if (locallyHiddenKeys.contains(PreferencesManager.hiddenMessageKey(memberId, msg.id))) return false
            val clearedAt = clearedAtMap[PreferencesManager.chatClearedAtKey(memberId, msg.chatId)] ?: 0L
            if (clearedAt > 0L && msg.timestamp <= clearedAt) return false
            return true
        }
    }
}
