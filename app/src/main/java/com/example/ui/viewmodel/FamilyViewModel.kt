package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.calls.WebRtcCallManager
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
import com.example.util.TimeFormatters
import com.example.util.VideoMediaHelper
import com.example.util.VoiceAudioHelper
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class MainTab { CHATS, GROUPS, CALLS, BOARD }

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
    val isMuted: Boolean = false
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
        val msg = fsErr?.localizedMessage ?: err.localizedMessage
        if (!msg.isNullOrBlank()) {
            _snackbarMessage.value = msg
        }
        emit(fallback)
    }

    val myFamilies: StateFlow<List<FamilySpace>> = cloudRepo.observeMyFamilies(currentUserId)
        .safeFirestoreFlow(emptyList())
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _activeFamilyId = MutableStateFlow<String?>(null)
    val activeFamilyId: StateFlow<String?> = _activeFamilyId.asStateFlow()

    val activeFamily: StateFlow<FamilySpace?> = _activeFamilyId
        .flatMapLatest { fid ->
            if (fid.isNullOrBlank()) flowOf(null) else cloudRepo.observeFamily(fid).safeFirestoreFlow(null)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _activeMemberId = MutableStateFlow<String?>(null)
    private val _isAppForeground = MutableStateFlow(false)
    private val _manualOffline = MutableStateFlow(false)

    private val presenceClock = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(PresenceResolver.CLOCK_TICK_MS)
        }
    }

    private val rawMembers: StateFlow<List<FamilyMemberEntity>> = _activeFamilyId
        .flatMapLatest { fid ->
            if (fid.isNullOrBlank()) flowOf(emptyList())
            else cloudRepo.observeMembers(fid, currentUserId).safeFirestoreFlow(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val members: StateFlow<List<FamilyMemberEntity>> = combine(
        rawMembers,
        _activeMemberId,
        _isAppForeground,
        _manualOffline,
        presenceClock
    ) { list, currentId, inForeground, manualOff, now ->
        list.map { member ->
            val isLocalCurrent = member.id == currentId
            val resolved = PresenceResolver.resolve(
                member = member,
                nowMs = now,
                isLocalActiveInForeground = isLocalCurrent && inForeground,
                manualInvisible = isLocalCurrent && manualOff
            )
            member.copy(
                isOnline = resolved.isOnline,
                lastSeenText = resolved.statusText
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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

    val calls: StateFlow<List<CallRecordEntity>> = _activeFamilyId
        .flatMapLatest { fid ->
            if (fid.isNullOrBlank()) flowOf(emptyList())
            else cloudRepo.observeCalls(fid, currentUserId).safeFirestoreFlow(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val rawStatuses: StateFlow<List<FamilyStatusEntity>> = _activeFamilyId
        .flatMapLatest { fid ->
            if (fid.isNullOrBlank()) flowOf(emptyList())
            else cloudRepo.observeStatuses(fid, currentUserId).safeFirestoreFlow(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val statuses: StateFlow<List<FamilyStatusEntity>> = combine(
        rawStatuses,
        presenceClock
    ) { list, now ->
        list.filter { it.expiresAt > now }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val shoppingItems: StateFlow<List<ShoppingItemEntity>> = _activeFamilyId
        .flatMapLatest { fid ->
            if (fid.isNullOrBlank()) flowOf(emptyList())
            else cloudRepo.observeShoppingItems(fid, currentUserId).safeFirestoreFlow(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val events: StateFlow<List<FamilyEventEntity>> = _activeFamilyId
        .flatMapLatest { fid ->
            if (fid.isNullOrBlank()) flowOf(emptyList())
            else cloudRepo.observeEvents(fid, currentUserId).safeFirestoreFlow(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notes: StateFlow<List<FamilyNoteEntity>> = _activeFamilyId
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

    val notificationsEnabled: StateFlow<Boolean> = prefs.notificationsEnabledFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val chatWallpapers: StateFlow<Map<String, String>> = prefs.chatWallpapersFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val pinnedChatIds: StateFlow<Set<String>> = prefs.pinnedChatIdsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val mutedChatIds: StateFlow<Set<String>> = prefs.mutedChatIdsFlow
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
    private var updateCheckJob: Job? = null
    private var updateDownloadJob: Job? = null

    val activeMember: StateFlow<FamilyMemberEntity?> = combine(members, _activeMemberId) { list, id ->
        list.find { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _isSessionReady = MutableStateFlow(false)
    val isSessionReady: StateFlow<Boolean> = _isSessionReady.asStateFlow()

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
        _activeMemberId
    ) { chatsList, msgsList, membersList, currentId ->
        if (currentId == null) return@combine emptyList()
        val membersMap = membersList.associateBy { it.id }
        val now = System.currentTimeMillis()

        chatsList.mapNotNull { chat ->
            val participantIds = JsonHelpers.decodeStringList(chat.participantIdsJson)
            if (!participantIds.contains(currentId)) return@mapNotNull null

            val participants = participantIds.mapNotNull { membersMap[it] }
            val visibleMsgs = msgsList.filter { msg ->
                msg.chatId == chat.id &&
                    !JsonHelpers.decodeStringList(msg.hiddenForMemberIdsJson).contains(currentId)
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

            val displayTitle = if (chat.type == ChatType.DIRECT) {
                otherMember?.name ?: chat.title
            } else chat.title

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
                        otherMember?.lastSeenText ?: "No se ha conectado"
                    } else {
                        val onlineOthers = otherMembers.count { it.isOnline }
                        val names = participants.joinToString(", ") { it.name }
                        if (onlineOthers > 0) "$onlineOthers en línea · $names"
                        else "${participants.size} miembros · $names"
                    }
                }
            }

            val preview = buildLastMessagePreview(lastMsg, membersMap, currentId, chat.type)
            val timeLabel = lastMsg?.let { TimeFormatters.formatHourMinute(it.timestamp) } ?: ""

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
                participants = participants
            )
        }
    }

    val chatPreviews: StateFlow<List<ChatThreadUi>> = combine(
        baseChatPreviews,
        pinnedChatIds,
        mutedChatIds
    ) { list, pinned, muted ->
        list.map { item ->
            item.copy(
                isPinned = pinned.contains(item.chat.id),
                isMuted = muted.contains(item.chat.id)
            )
        }.sortedWith(
            compareByDescending<ChatThreadUi> { it.isPinned }
                .thenByDescending { it.chat.updatedAt }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        FamiChatNotificationHelper.ensureChannels(appContext)
        viewModelScope.launch {
            val savedFamilyId = prefs.activeFamilyIdFlow.first()
            val fams = runCatching {
                cloudRepo.observeMyFamilies(currentUserId).first()
            }.getOrDefault(emptyList())
            var resolvedFamilyId = when {
                savedFamilyId != null && fams.any { it.id == savedFamilyId } -> savedFamilyId
                fams.any { it.inviteCode == "NIDO01" } -> fams.first { it.inviteCode == "NIDO01" }.id
                savedFamilyId != null -> savedFamilyId
                else -> null
            }
            if (resolvedFamilyId == null) {
                resolvedFamilyId = cloudRepo.ensureFixedFamily(currentUserId).getOrNull()
                    ?: fams.firstOrNull()?.id
            }
            if (resolvedFamilyId != null) {
                _activeFamilyId.value = resolvedFamilyId
                prefs.setActiveFamilyId(resolvedFamilyId)
                val savedMemberId = prefs.activeMemberIdFlow.first()
                if (savedMemberId != null) {
                    // Wait briefly for initial member list so locked session restores straight to HOME
                    val loadedMembers = runCatching {
                        cloudRepo.observeMembers(resolvedFamilyId, currentUserId).first()
                    }.getOrDefault(emptyList())
                    if (loadedMembers.any { it.id == savedMemberId }) {
                        _activeMemberId.value = savedMemberId
                        syncPushAndBackgroundWork(resolvedFamilyId, savedMemberId)
                        if (_isAppForeground.value && !_manualOffline.value) {
                            startPresenceHeartbeat()
                        }
                    } else {
                        prefs.setActiveMemberId(null)
                    }
                }
            }
            _isSessionReady.value = true
        }

        // Automatically backfill new family UIDs onto existing docs when a new family phone joins
        viewModelScope.launch {
            activeFamily.collect { fam ->
                if (fam != null) {
                    val uids = fam.memberUids.toSet()
                    if (uids.size > 1 && uids != lastSyncedMemberUids) {
                        lastSyncedMemberUids = uids
                        cloudRepo.syncMemberUidsAcrossExistingDocs(fam.id, fam.memberUids, currentUserId)
                    }
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
                            msg.status == MessageStatus.SENT
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

    fun selectFamilySpace(familyId: String) {
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
                adminColorHex = adminColorHex
            )
            _isFamilyActionBusy.value = false
            res.onSuccess { (famId, memberId) ->
                _activeFamilyId.value = famId
                prefs.setActiveFamilyId(famId)
                _activeMemberId.value = memberId
                prefs.setActiveMemberId(memberId)
                syncPushAndBackgroundWork(famId, memberId)
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

    fun toggleTheme() {
        viewModelScope.launch {
            val current = prefs.isDarkThemeFlow.first()
            prefs.setDarkTheme(!current)
        }
    }

    fun setAppForeground(inForeground: Boolean) {
        if (_isAppForeground.value == inForeground) return
        _isAppForeground.value = inForeground
        if (inForeground) {
            if (_activeMemberId.value != null && !_manualOffline.value) {
                startPresenceHeartbeat()
            }
        } else {
            heartbeatJob?.cancel()
            heartbeatJob = null
            val famId = _activeFamilyId.value ?: return
            val memberId = _activeMemberId.value ?: return
            if (notificationsEnabled.value) {
                PushRegistrationManager.schedulePeriodicBackgroundSync(appContext)
                PushRegistrationManager.scheduleBackgroundCheckOnStop(appContext)
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
                        liveStatusAt = System.currentTimeMillis()
                    )
                }
            }
        }
    }

    private fun syncPushAndBackgroundWork(familyId: String, memberId: String) {
        viewModelScope.launch {
            if (notificationsEnabled.value) {
                PushRegistrationManager.syncFcmTokenAndTopic(appContext, cloudRepo, prefs, familyId, memberId)
                PushRegistrationManager.schedulePeriodicBackgroundSync(appContext)
            }
        }
    }

    private fun startPresenceHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launch {
            while (_isAppForeground.value && !_manualOffline.value) {
                val famId = _activeFamilyId.value
                val memberId = _activeMemberId.value
                if (famId != null && memberId != null) {
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
                        linkedAuthUid = currentUserId
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
        _manualOffline.value = false
        _activeMemberId.value = memberId
        _activeChatId.value = null
        _searchQuery.value = ""
        viewModelScope.launch {
            prefs.setActiveMemberId(memberId)
            val famId = _activeFamilyId.value ?: return@launch
            val now = System.currentTimeMillis()
            val raw = rawMembers.value.find { it.id == memberId }
            val nextLabel = onlineStatusLabelFor(raw?.lastSeenText.orEmpty())
            cloudRepo.updateMemberPresence(
                familyId = famId,
                memberId = memberId,
                isOnline = true,
                lastSeenText = nextLabel,
                liveStatus = PresenceStatus.ONLINE,
                liveChatId = null,
                liveStatusAt = now,
                linkedAuthUid = currentUserId
            )
            if (_isAppForeground.value) {
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
        _manualOffline.value = false
        _activeMemberId.value = null
        _activeChatId.value = null
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
                        liveStatusAt = System.currentTimeMillis()
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

    fun setChatWallpaper(chatId: String, wallpaperKey: String) {
        viewModelScope.launch {
            prefs.setChatWallpaper(chatId, wallpaperKey)
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
        FamiChatNotificationHelper.cancelChatNotifications(appContext, chatId)
        markChatMessagesAsRead(chatId)
    }

    fun closeChat() {
        val prevChatId = _activeChatId.value
        _activeChatId.value = null
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
        if (_manualOffline.value) return
        val now = System.currentTimeMillis()
        val key = "$chatId:${status.name}"

        if (!forceClear && status != PresenceStatus.ONLINE &&
            key == lastSentPresenceKey && (now - lastSentPresenceAt) < 2500L
        ) {
            return
        }
        lastSentPresenceKey = key
        lastSentPresenceAt = now

        val raw = rawMembers.value.find { it.id == memberId }
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
                linkedAuthUid = currentUserId
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
        val toMark = allMessages.value.filter {
            it.chatId == chatId &&
                it.senderId != currentId &&
                it.type != MessageType.DELETED &&
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
        entered == switchPin.value || entered == masterPin.value

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
        isOnline: Boolean,
        statusText: String,
        bioText: String,
        newPhotoUri: Uri?,
        removePhoto: Boolean
    ) {
        val famId = _activeFamilyId.value ?: return
        val memberId = _activeMemberId.value ?: return
        _manualOffline.value = !isOnline
        val now = System.currentTimeMillis()
        val cleanStatus = statusText.trim().ifEmpty { if (isOnline) "En línea" else "Desconectado" }
        val finalLabel = if (isOnline && (cleanStatus == "Desconectado" || cleanStatus == "No se ha conectado")) {
            "En línea"
        } else if (!isOnline && (cleanStatus == "En línea" || cleanStatus == "No se ha conectado")) {
            "Desconectado"
        } else {
            cleanStatus
        }
        viewModelScope.launch {
            val existingPhoto = rawMembers.value.find { it.id == memberId }?.avatarPhotoData.orEmpty()
            val resolvedPhoto = when {
                removePhoto -> ""
                newPhotoUri != null -> ImageCompressionHelper.compressToDataUri(appContext, newPhotoUri) ?: existingPhoto
                else -> existingPhoto
            }
            cloudRepo.updateMemberPresence(
                familyId = famId,
                memberId = memberId,
                isOnline = isOnline,
                lastSeenText = finalLabel,
                liveStatus = if (isOnline) PresenceStatus.ONLINE else PresenceStatus.LAST_SEEN,
                liveChatId = null,
                liveStatusAt = now,
                linkedAuthUid = currentUserId
            )
            cloudRepo.updateMemberProfileDetails(
                familyId = famId,
                memberId = memberId,
                fields = mapOf(
                    "bioText" to bioText.trim().take(120),
                    "avatarPhotoData" to resolvedPhoto.take(750_000)
                )
            )
            if (isOnline && _isAppForeground.value) {
                startPresenceHeartbeat()
            } else {
                heartbeatJob?.cancel()
                heartbeatJob = null
            }
            showMessage("Perfil actualizado")
        }
    }

    fun checkForAppUpdate() {
        updateCheckJob?.cancel()
        updateCheckJob = viewModelScope.launch {
            _updateState.value = AppUpdateUiState.Checking
            val savedSlug = prefs.updateRepoSlugFlow.first()
            val effectiveSlug = savedSlug
                .ifBlank { activeFamily.value?.updateRepoSlug.orEmpty() }
                .ifBlank { BuildConfig.GITHUB_REPO }
            _updateState.value = AppUpdateChecker.checkLatestRelease(
                rawRepoSlug = effectiveSlug,
                installedVersionName = BuildConfig.VERSION_NAME,
                installedVersionCode = BuildConfig.VERSION_CODE
            )
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
            if (triggerCheck) checkForAppUpdate()
        }
    }

    fun downloadAndInstallUpdate(release: AppReleaseInfo) {
        if (updateDownloadJob?.isActive == true) return
        updateDownloadJob = viewModelScope.launch {
            _updateState.value = AppUpdateUiState.Downloading(
                release = release,
                progressPercent = 0,
                downloadedBytes = 0L,
                totalBytes = -1L
            )
            val result = AppUpdateChecker.downloadApkToCache(
                context = appContext,
                release = release
            ) { pct, downloaded, total ->
                _updateState.value = AppUpdateUiState.Downloading(
                    release = release,
                    progressPercent = pct,
                    downloadedBytes = downloaded,
                    totalBytes = total
                )
            }
            result.fold(
                onSuccess = { apkFile ->
                    _updateState.value = AppUpdateUiState.ReadyToInstall(
                        release = release,
                        apkFilePath = apkFile.absolutePath
                    )
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    _updateState.value = AppUpdateUiState.Error(
                        message = error.localizedMessage ?: "No se pudo descargar la actualización."
                    )
                }
            )
        }
    }

    fun resetUpdateState() {
        updateCheckJob?.cancel()
        updateCheckJob = null
        updateDownloadJob?.cancel()
        updateDownloadJob = null
        _updateState.value = AppUpdateUiState.Idle
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
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return
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
                roleLabel = roleLabel.trim().ifEmpty { "Familiar" },
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
                                id = "${msgId}_$idx",
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
                                id = "${msgId}_$idx",
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
                }
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
            val cached = VideoMediaHelper.getCachedVideoFile(appContext, message.id, message.fileName)
            if (cached.exists() && cached.length() > 0L) {
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
                }
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

    fun deleteMessagesForMe(messageIds: List<String>) {
        val famId = _activeFamilyId.value ?: return
        val currentId = _activeMemberId.value ?: return
        viewModelScope.launch {
            messageIds.forEach { id ->
                val msg = allMessages.value.find { it.id == id } ?: return@forEach
                val hidden = JsonHelpers.decodeStringList(msg.hiddenForMemberIdsJson).toMutableSet()
                hidden.add(currentId)
                cloudRepo.updateMessageFields(famId, id, mapOf("hiddenForMemberIds" to hidden.toList()))
            }
            showMessage("Eliminado para ti")
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
            var answeredOffer = false
            fun tryAnswerOffer(offerSdp: String?) {
                if (answeredOffer || offerSdp.isNullOrBlank()) return
                answeredOffer = true
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

    fun publishTextStatus(text: String, backgroundHex: String) {
        val famId = _activeFamilyId.value ?: return
        val currentId = _activeMemberId.value ?: return
        val clean = text.trim()
        if (clean.isEmpty()) return
        val now = System.currentTimeMillis()
        val status = FamilyStatusEntity(
            id = "st_${UUID.randomUUID().toString().replace("-", "").take(10)}",
            memberId = currentId,
            text = clean.take(400),
            mediaUri = null,
            backgroundHex = backgroundHex,
            createdAt = now,
            expiresAt = now + FamilyStatusEntity.STATUS_TTL_MS,
            viewedByMemberIdsJson = JsonHelpers.encodeStringList(listOf(currentId)),
            authorUid = currentUserId
        )
        viewModelScope.launch {
            val res = cloudRepo.createStatus(famId, status, currentMemberUids(), currentUserId)
            showMessage(if (res.isSuccess) "Estado publicado por 24 horas" else "No se pudo publicar el estado")
        }
    }

    fun publishPhotoStatus(pickedUri: Uri, caption: String) {
        val famId = _activeFamilyId.value ?: return
        val currentId = _activeMemberId.value ?: return
        viewModelScope.launch {
            val dataUri = ImageCompressionHelper.compressToDataUri(appContext, pickedUri)
            if (dataUri == null) {
                showMessage("No se pudo procesar la foto del estado")
                return@launch
            }
            val now = System.currentTimeMillis()
            val status = FamilyStatusEntity(
                id = "st_${UUID.randomUUID().toString().replace("-", "").take(10)}",
                memberId = currentId,
                text = caption.trim().take(200),
                mediaUri = dataUri,
                backgroundHex = "#111827",
                createdAt = now,
                expiresAt = now + FamilyStatusEntity.STATUS_TTL_MS,
                viewedByMemberIdsJson = JsonHelpers.encodeStringList(listOf(currentId)),
                authorUid = currentUserId
            )
            val res = cloudRepo.createStatus(famId, status, currentMemberUids(), currentUserId)
            showMessage(if (res.isSuccess) "Foto de estado publicada por 24h" else "No se pudo subir el estado")
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
            cloudRepo.deleteStatus(famId, statusId)
            showMessage("Estado eliminado")
        }
    }

    fun replyToStatus(status: FamilyStatusEntity, replyText: String, onOpenChat: (String) -> Unit) {
        val clean = replyText.trim()
        if (clean.isEmpty()) return
        val quoteLabel = status.text.trim().ifBlank { "Foto de estado" }.take(60)
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
                val chunksRes = cloudRepo.fetchMediaChunks(famId, source.id, source.chunkCount)
                chunksRes.onSuccess { fullData ->
                    val newChunks = DocumentMediaHelper.splitIntoChunks(fullData)
                    val uids = currentMemberUids()
                    newChunks.forEachIndexed { idx, chunkStr ->
                        cloudRepo.uploadMediaChunk(
                            familyId = famId,
                            chunk = MediaChunkEntity(
                                id = "${msgId}_$idx",
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
}
