package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.FamilyDatabase
import com.example.data.local.FamilyRepository
import com.example.data.local.PreferencesManager
import com.example.data.model.CallDirection
import com.example.data.model.CallRecordEntity
import com.example.data.model.CallType
import com.example.data.model.ChatMessageEntity
import com.example.data.model.ChatThreadEntity
import com.example.data.model.ChatType
import com.example.data.model.FamilyEventEntity
import com.example.data.model.FamilyMemberEntity
import com.example.data.model.FamilyNoteEntity
import com.example.data.model.JsonHelpers
import com.example.data.model.MessageStatus
import com.example.data.model.MessageType
import com.example.data.model.PollOption
import com.example.data.model.PresenceStatus
import com.example.data.model.ShoppingItemEntity
import com.example.util.MessageFormatter
import com.example.util.TimeFormatters
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.random.Random

enum class MainTab { CHATS, GROUPS, CALLS, BOARD }

data class ChatThreadUi(
    val chat: ChatThreadEntity,
    val displayTitle: String,
    val displayEmoji: String,
    val displayColorHex: String,
    val isOnline: Boolean,
    val subtitleStatus: String,
    val lastMessagePreview: String,
    val lastMessageTime: String,
    val unreadCount: Int,
    val lastMessageStatus: MessageStatus?,
    val isLastMessageFromMe: Boolean,
    val participants: List<FamilyMemberEntity>
)

data class ActiveCallUiState(
    val chatId: String,
    val title: String,
    val avatarEmoji: String,
    val avatarColorHex: String,
    val callType: CallType,
    val isConnected: Boolean = false,
    val durationSec: Int = 0,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isCameraOn: Boolean = true
)

data class VoiceRecordingState(
    val isRecording: Boolean = false,
    val elapsedSec: Int = 0,
    val amplitudes: List<Float> = emptyList()
)

class FamilyViewModel(application: Application) : AndroidViewModel(application) {
    private val db = FamilyDatabase.getInstance(application)
    private val prefs = PreferencesManager(application)
    private val repo = FamilyRepository(application, db, prefs)

    val members: StateFlow<List<FamilyMemberEntity>> = repo.members
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val masterPin: StateFlow<String> = repo.masterPin
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PreferencesManager.DEFAULT_PIN)

    val switchPin: StateFlow<String> = prefs.switchPinFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PreferencesManager.DEFAULT_SWITCH_PIN)

    val isDarkTheme: StateFlow<Boolean> = prefs.isDarkThemeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun toggleTheme() {
        val next = !isDarkTheme.value
        viewModelScope.launch {
            prefs.setDarkTheme(next)
        }
    }

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setDarkTheme(enabled)
        }
    }

    val calls: StateFlow<List<CallRecordEntity>> = repo.calls
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val shoppingItems: StateFlow<List<ShoppingItemEntity>> = repo.shoppingItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val events: StateFlow<List<FamilyEventEntity>> = repo.events
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notes: StateFlow<List<FamilyNoteEntity>> = repo.notes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeMemberId = MutableStateFlow<String?>(null)
    val activeMember: StateFlow<FamilyMemberEntity?> = combine(members, _activeMemberId) { list, id ->
        list.find { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _selectedTab = MutableStateFlow(MainTab.CHATS)
    val selectedTab: StateFlow<MainTab> = _selectedTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _activeChatId = MutableStateFlow<String?>(null)
    val activeChatId: StateFlow<String?> = _activeChatId.asStateFlow()

    private val _simulatedPresence = MutableStateFlow<Map<String, PresenceStatus>>(emptyMap())
    val simulatedPresence: StateFlow<Map<String, PresenceStatus>> = _simulatedPresence.asStateFlow()

    private val _activeCall = MutableStateFlow<ActiveCallUiState?>(null)
    val activeCall: StateFlow<ActiveCallUiState?> = _activeCall.asStateFlow()

    private val _voiceRecording = MutableStateFlow(VoiceRecordingState())
    val voiceRecording: StateFlow<VoiceRecordingState> = _voiceRecording.asStateFlow()

    private val _playingVoiceMessageId = MutableStateFlow<String?>(null)
    val playingVoiceMessageId: StateFlow<String?> = _playingVoiceMessageId.asStateFlow()

    private val _playingVoiceProgress = MutableStateFlow(0f)
    val playingVoiceProgress: StateFlow<Float> = _playingVoiceProgress.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private var callTimerJob: Job? = null
    private var recordingJob: Job? = null
    private var playbackJob: Job? = null
    private var presenceJob: Job? = null

    val rawChats: StateFlow<List<ChatThreadEntity>> = repo.chats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allMessages: StateFlow<List<ChatMessageEntity>> = repo.allMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val chatPreviews: StateFlow<List<ChatThreadUi>> = combine(
        rawChats,
        allMessages,
        members,
        _activeMemberId,
        _simulatedPresence
    ) { chatsList, msgsList, membersList, currentId, presenceMap ->
        if (currentId == null) return@combine emptyList()
        val membersMap = membersList.associateBy { it.id }

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
                    msg.status != MessageStatus.READ &&
                    msg.type != MessageType.DELETED
            }

            val otherMember = if (chat.type == ChatType.DIRECT) {
                participants.firstOrNull { it.id != currentId }
            } else null

            val displayTitle = if (chat.type == ChatType.DIRECT) {
                otherMember?.name ?: chat.title
            } else chat.title

            val displayEmoji = if (chat.type == ChatType.DIRECT) {
                otherMember?.avatarEmoji ?: chat.avatarEmoji
            } else chat.avatarEmoji

            val displayColor = if (chat.type == ChatType.DIRECT) {
                otherMember?.avatarColorHex ?: chat.avatarColorHex
            } else chat.avatarColorHex

            val isOnline = if (chat.type == ChatType.DIRECT) {
                otherMember?.isOnline == true
            } else {
                participants.any { it.id != currentId && it.isOnline }
            }

            val livePresence = presenceMap[chat.id]
            val subtitleStatus = when (livePresence) {
                PresenceStatus.TYPING -> "Escribiendo..."
                PresenceStatus.RECORDING -> "Grabando audio..."
                PresenceStatus.ONLINE -> "En línea"
                PresenceStatus.LAST_SEEN, null -> {
                    if (chat.type == ChatType.DIRECT) {
                        if (otherMember?.isOnline == true) "En línea"
                        else otherMember?.lastSeenText ?: "Últ. vez reciente"
                    } else {
                        val names = participants.joinToString(", ") { it.name }
                        "${participants.size} miembros · $names"
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repo.ensureSeeded()
        }
    }

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
            MessageType.VOICE -> "🎤 Nota de voz (${TimeFormatters.formatDuration(msg.voiceDurationSec)})"
            MessageType.LOCATION -> "📍 ${msg.locationName ?: "Ubicación compartida"}"
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

    fun selectProfile(memberId: String) {
        _activeMemberId.value = memberId
        _activeChatId.value = null
        _searchQuery.value = ""
        viewModelScope.launch {
            prefs.setActiveMemberId(memberId)
            val m = members.value.find { it.id == memberId }
            if (m != null && !m.isOnline) {
                repo.upsertMember(m.copy(isOnline = true, lastSeenText = "En línea"))
            }
        }
    }

    fun logoutToSelector() {
        _activeMemberId.value = null
        _activeChatId.value = null
        stopPlayback()
        cancelVoiceRecording()
    }

    fun selectTab(tab: MainTab) {
        _selectedTab.value = tab
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun openChat(chatId: String) {
        _activeChatId.value = chatId
        markChatMessagesAsRead(chatId)
        triggerBriefPresenceDemo(chatId)
    }

    fun closeChat() {
        _activeChatId.value = null
        stopPlayback()
        cancelVoiceRecording()
        presenceJob?.cancel()
    }

    fun triggerBriefPresenceDemo(chatId: String, forcedStatus: PresenceStatus? = null) {
        presenceJob?.cancel()
        presenceJob = viewModelScope.launch {
            val preview = chatPreviews.value.find { it.chat.id == chatId }
            if (forcedStatus != null) {
                _simulatedPresence.value = _simulatedPresence.value + (chatId to forcedStatus)
                delay(4500)
                _simulatedPresence.value = _simulatedPresence.value - chatId
            } else if (preview?.isOnline == true) {
                delay(900)
                _simulatedPresence.value = _simulatedPresence.value + (chatId to PresenceStatus.TYPING)
                delay(2400)
                _simulatedPresence.value = _simulatedPresence.value - chatId
            }
        }
    }

    fun markChatMessagesAsRead(chatId: String) {
        val currentId = _activeMemberId.value ?: return
        viewModelScope.launch {
            val msgs = repo.getMessagesOnce(chatId)
            msgs.filter {
                it.senderId != currentId &&
                    it.status != MessageStatus.READ &&
                    it.type != MessageType.DELETED
            }.forEach { msg ->
                repo.updateMessageOnly(msg.copy(status = MessageStatus.READ))
            }
        }
    }

    fun verifyPin(entered: String): Boolean = entered == masterPin.value

    fun verifySwitchKey(entered: String): Boolean =
        entered == switchPin.value || entered == masterPin.value

    fun updateMasterPin(newPin: String) {
        viewModelScope.launch {
            repo.updateMasterPin(newPin)
            showMessage("Contraseña de Papá actualizada correctamente")
        }
    }

    fun updateSwitchPin(newPin: String) {
        viewModelScope.launch {
            prefs.updateSwitchPin(newPin)
            showMessage("Clave de cambio de perfil actualizada")
        }
    }

    fun updateOwnProfileStatus(isOnline: Boolean, statusText: String) {
        val current = activeMember.value ?: return
        val cleanStatus = statusText.trim().ifEmpty { if (isOnline) "En línea" else "Desconectado" }
        viewModelScope.launch {
            repo.upsertMember(
                current.copy(
                    isOnline = isOnline,
                    lastSeenText = if (isOnline && cleanStatus == "Desconectado") "En línea" else cleanStatus
                )
            )
            showMessage("Estado del perfil actualizado")
        }
    }

    // --- Admin Member & Group Management ---

    fun saveFamilyMember(
        existingId: String?,
        name: String,
        roleLabel: String,
        isAdmin: Boolean,
        colorHex: String,
        emoji: String
    ) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return
        viewModelScope.launch {
            val id = existingId ?: "member_${UUID.randomUUID().toString().take(8)}"
            val existing = members.value.find { it.id == id }
            val member = FamilyMemberEntity(
                id = id,
                name = trimmedName,
                roleLabel = roleLabel.trim().ifEmpty { "Familiar" },
                isAdmin = isAdmin,
                avatarColorHex = colorHex,
                avatarEmoji = emoji,
                isOnline = existing?.isOnline ?: true,
                lastSeenText = existing?.lastSeenText ?: "En línea",
                createdAt = existing?.createdAt ?: System.currentTimeMillis()
            )
            repo.upsertMember(member)

            // Ensure new member is added to main family group if present and create 1:1 chats
            if (existingId == null) {
                val allExistingMembers = members.value
                val mainGroup = rawChats.value.find { it.id == "chat_familia" }
                if (mainGroup != null) {
                    val pIds = JsonHelpers.decodeStringList(mainGroup.participantIdsJson).toMutableList()
                    if (!pIds.contains(id)) {
                        pIds.add(id)
                        repo.upsertChat(mainGroup.copy(participantIdsJson = JsonHelpers.encodeStringList(pIds)))
                    }
                }
                val currentAdminId = _activeMemberId.value
                if (currentAdminId != null && currentAdminId != id) {
                    createOrOpenDirectChat(id, openAfter = false)
                } else if (allExistingMembers.isNotEmpty()) {
                    val firstId = allExistingMembers.first().id
                    val chat = ChatThreadEntity(
                        id = "chat_${UUID.randomUUID().toString().take(8)}",
                        type = ChatType.DIRECT,
                        title = "Directo",
                        avatarColorHex = colorHex,
                        avatarEmoji = emoji,
                        participantIdsJson = JsonHelpers.encodeStringList(listOf(firstId, id)),
                        updatedAt = System.currentTimeMillis()
                    )
                    repo.upsertChat(chat)
                }
                showMessage("$trimmedName añadido a la familia")
            } else {
                showMessage("Perfil de $trimmedName actualizado")
            }
        }
    }

    fun deleteFamilyMember(memberId: String) {
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
            repo.deleteMember(memberId)
            // Remove from groups
            rawChats.value.forEach { chat ->
                val ids = JsonHelpers.decodeStringList(chat.participantIdsJson)
                if (ids.contains(memberId)) {
                    if (chat.type == ChatType.DIRECT) {
                        repo.deleteChat(chat.id)
                    } else {
                        val updated = ids.filterNot { it == memberId }
                        repo.upsertChat(chat.copy(participantIdsJson = JsonHelpers.encodeStringList(updated)))
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
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty() || participantIds.size < 2) {
            showMessage("El grupo necesita nombre y al menos 2 miembros")
            return
        }
        viewModelScope.launch {
            val id = existingGroupId ?: "group_${UUID.randomUUID().toString().take(8)}"
            val existing = repo.getChatById(id)
            val group = ChatThreadEntity(
                id = id,
                type = ChatType.GROUP,
                title = cleanTitle,
                avatarColorHex = colorHex,
                avatarEmoji = emoji,
                participantIdsJson = JsonHelpers.encodeStringList(participantIds.distinct()),
                pinnedMessageId = existing?.pinnedMessageId,
                updatedAt = System.currentTimeMillis()
            )
            repo.upsertChat(group)
            showMessage(if (existingGroupId == null) "Grupo \"$cleanTitle\" creado" else "Grupo actualizado")
        }
    }

    fun deleteGroup(groupId: String) {
        viewModelScope.launch {
            repo.deleteChat(groupId)
            showMessage("Grupo eliminado")
        }
    }

    fun createOrOpenDirectChat(otherMemberId: String, openAfter: Boolean = true, onOpened: (String) -> Unit = {}) {
        val currentId = _activeMemberId.value ?: return
        if (currentId == otherMemberId) return
        viewModelScope.launch {
            val existing = rawChats.value.firstOrNull { chat ->
                chat.type == ChatType.DIRECT &&
                    JsonHelpers.decodeStringList(chat.participantIdsJson).toSet() == setOf(currentId, otherMemberId)
            }
            val chatId = if (existing != null) {
                existing.id
            } else {
                val other = members.value.find { it.id == otherMemberId }
                val newId = "chat_${UUID.randomUUID().toString().take(8)}"
                repo.upsertChat(
                    ChatThreadEntity(
                        id = newId,
                        type = ChatType.DIRECT,
                        title = "Directo",
                        avatarColorHex = other?.avatarColorHex ?: "#1A73E8",
                        avatarEmoji = other?.avatarEmoji ?: "💬",
                        participantIdsJson = JsonHelpers.encodeStringList(listOf(currentId, otherMemberId)),
                        updatedAt = System.currentTimeMillis()
                    )
                )
                newId
            }
            if (openAfter) {
                openChat(chatId)
                onOpened(chatId)
            }
        }
    }

    // --- Messaging Actions ---

    private fun progressMessageStatus(messageId: String) {
        viewModelScope.launch {
            delay(350)
            repo.getMessageById(messageId)?.let { msg ->
                if (msg.status == MessageStatus.SENDING) {
                    repo.updateMessageOnly(msg.copy(status = MessageStatus.SENT))
                }
            }
            delay(650)
            repo.getMessageById(messageId)?.let { msg ->
                if (msg.status == MessageStatus.SENT) {
                    repo.updateMessageOnly(msg.copy(status = MessageStatus.DELIVERED))
                }
            }
        }
    }

    fun sendTextMessage(chatId: String, text: String, replyToId: String? = null) {
        val currentId = _activeMemberId.value ?: return
        val clean = text.trim()
        if (clean.isEmpty()) return
        val msgId = "msg_${UUID.randomUUID().toString().take(10)}"
        val msg = ChatMessageEntity(
            id = msgId,
            chatId = chatId,
            senderId = currentId,
            text = clean,
            type = MessageType.TEXT,
            status = MessageStatus.SENDING,
            timestamp = System.currentTimeMillis(),
            replyToMessageId = replyToId
        )
        viewModelScope.launch {
            repo.upsertMessage(msg)
            progressMessageStatus(msgId)
        }
    }

    fun sendImageMessage(chatId: String, pickedUri: Uri, caption: String, replyToId: String? = null) {
        val currentId = _activeMemberId.value ?: return
        viewModelScope.launch {
            val savedUri = repo.persistPickedImage(pickedUri) ?: pickedUri.toString()
            val msgId = "msg_${UUID.randomUUID().toString().take(10)}"
            val msg = ChatMessageEntity(
                id = msgId,
                chatId = chatId,
                senderId = currentId,
                text = caption.trim(),
                type = MessageType.IMAGE,
                status = MessageStatus.SENDING,
                timestamp = System.currentTimeMillis(),
                replyToMessageId = replyToId,
                mediaUri = savedUri
            )
            repo.upsertMessage(msg)
            progressMessageStatus(msgId)
        }
    }

    fun sendLocationMessage(chatId: String, name: String, address: String, replyToId: String? = null) {
        val currentId = _activeMemberId.value ?: return
        val msgId = "msg_${UUID.randomUUID().toString().take(10)}"
        val msg = ChatMessageEntity(
            id = msgId,
            chatId = chatId,
            senderId = currentId,
            text = "",
            type = MessageType.LOCATION,
            status = MessageStatus.SENDING,
            timestamp = System.currentTimeMillis(),
            replyToMessageId = replyToId,
            locationName = name.trim(),
            locationAddress = address.trim()
        )
        viewModelScope.launch {
            repo.upsertMessage(msg)
            progressMessageStatus(msgId)
        }
    }

    fun sendPollMessage(chatId: String, question: String, optionTexts: List<String>) {
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
        val msgId = "msg_${UUID.randomUUID().toString().take(10)}"
        val msg = ChatMessageEntity(
            id = msgId,
            chatId = chatId,
            senderId = currentId,
            text = "",
            type = MessageType.POLL,
            status = MessageStatus.SENDING,
            timestamp = System.currentTimeMillis(),
            pollQuestion = cleanQ,
            pollOptionsJson = JsonHelpers.encodePollOptions(pollOptions)
        )
        viewModelScope.launch {
            repo.upsertMessage(msg)
            progressMessageStatus(msgId)
        }
    }

    fun voteOnPoll(messageId: String, optionId: String) {
        val currentId = _activeMemberId.value ?: return
        viewModelScope.launch {
            val msg = repo.getMessageById(messageId) ?: return@launch
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
            repo.updateMessageOnly(msg.copy(pollOptionsJson = JsonHelpers.encodePollOptions(updated)))
        }
    }

    fun editMessage(messageId: String, newText: String) {
        val clean = newText.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val msg = repo.getMessageById(messageId) ?: return@launch
            repo.updateMessageOnly(msg.copy(text = clean, isEdited = true))
        }
    }

    fun toggleReaction(messageId: String, emoji: String) {
        val currentId = _activeMemberId.value ?: return
        viewModelScope.launch {
            val msg = repo.getMessageById(messageId) ?: return@launch
            val reactions = JsonHelpers.decodeReactions(msg.reactionsJson).toMutableMap()
            if (reactions[currentId] == emoji) {
                reactions.remove(currentId)
            } else {
                reactions[currentId] = emoji
            }
            repo.updateMessageOnly(msg.copy(reactionsJson = JsonHelpers.encodeReactions(reactions)))
        }
    }

    fun togglePinMessage(chatId: String, messageId: String) {
        viewModelScope.launch {
            val chat = repo.getChatById(chatId) ?: return@launch
            val nextPinned = if (chat.pinnedMessageId == messageId) null else messageId
            repo.updatePinnedMessage(chatId, nextPinned)
            showMessage(if (nextPinned != null) "Mensaje fijado arriba" else "Mensaje desfijado")
        }
    }

    fun deleteMessagesForMe(messageIds: List<String>) {
        val currentId = _activeMemberId.value ?: return
        viewModelScope.launch {
            messageIds.forEach { id ->
                val msg = repo.getMessageById(id) ?: return@forEach
                val hidden = JsonHelpers.decodeStringList(msg.hiddenForMemberIdsJson).toMutableSet()
                hidden.add(currentId)
                repo.updateMessageOnly(msg.copy(hiddenForMemberIdsJson = JsonHelpers.encodeStringList(hidden.toList())))
            }
            showMessage("Eliminado para ti")
        }
    }

    fun deleteMessagesForEveryone(messageIds: List<String>) {
        val currentId = _activeMemberId.value ?: return
        viewModelScope.launch {
            messageIds.forEach { id ->
                val msg = repo.getMessageById(id) ?: return@forEach
                if (msg.senderId == currentId || activeMember.value?.isAdmin == true) {
                    repo.updateMessageOnly(
                        msg.copy(
                            text = "",
                            type = MessageType.DELETED,
                            mediaUri = null,
                            reactionsJson = "{}",
                            pollQuestion = null,
                            pollOptionsJson = "[]"
                        )
                    )
                    val chat = repo.getChatById(msg.chatId)
                    if (chat?.pinnedMessageId == msg.id) {
                        repo.updatePinnedMessage(msg.chatId, null)
                    }
                } else {
                    // If not sender/admin, hide locally
                    val hidden = JsonHelpers.decodeStringList(msg.hiddenForMemberIdsJson).toMutableSet()
                    hidden.add(currentId)
                    repo.updateMessageOnly(msg.copy(hiddenForMemberIdsJson = JsonHelpers.encodeStringList(hidden.toList())))
                }
            }
            showMessage("Mensaje eliminado")
        }
    }

    // --- Simulated Voice Recording & Playback ---

    fun startVoiceRecording() {
        recordingJob?.cancel()
        _voiceRecording.value = VoiceRecordingState(
            isRecording = true,
            elapsedSec = 0,
            amplitudes = listOf(0.35f)
        )
        recordingJob = viewModelScope.launch {
            var ticks = 0
            while (true) {
                delay(250)
                ticks++
                val current = _voiceRecording.value
                val nextAmp = Random.nextFloat() * 0.72f + 0.22f
                val updatedAmps = (current.amplitudes + nextAmp).takeLast(28)
                _voiceRecording.value = current.copy(
                    elapsedSec = ticks / 4,
                    amplitudes = updatedAmps
                )
            }
        }
    }

    fun cancelVoiceRecording() {
        recordingJob?.cancel()
        _voiceRecording.value = VoiceRecordingState()
    }

    fun finishAndSendVoiceRecording(chatId: String, replyToId: String? = null) {
        val state = _voiceRecording.value
        recordingJob?.cancel()
        _voiceRecording.value = VoiceRecordingState()
        val currentId = _activeMemberId.value ?: return
        val duration = state.elapsedSec.coerceAtLeast(1)
        val waveform = state.amplitudes.ifEmpty {
            listOf(0.3f, 0.7f, 0.5f, 0.85f, 0.4f, 0.65f, 0.9f, 0.5f, 0.35f, 0.75f, 0.6f, 0.45f)
        }
        val msgId = "msg_${UUID.randomUUID().toString().take(10)}"
        val msg = ChatMessageEntity(
            id = msgId,
            chatId = chatId,
            senderId = currentId,
            text = "",
            type = MessageType.VOICE,
            status = MessageStatus.SENDING,
            timestamp = System.currentTimeMillis(),
            replyToMessageId = replyToId,
            voiceDurationSec = duration,
            voiceWaveformJson = JsonHelpers.encodeFloatList(waveform)
        )
        viewModelScope.launch {
            repo.upsertMessage(msg)
            progressMessageStatus(msgId)
        }
    }

    fun toggleVoicePlayback(messageId: String, durationSec: Int) {
        if (_playingVoiceMessageId.value == messageId) {
            stopPlayback()
            return
        }
        stopPlayback()
        _playingVoiceMessageId.value = messageId
        _playingVoiceProgress.value = 0f
        val totalMs = (durationSec.coerceAtLeast(2) * 1000L)
        playbackJob = viewModelScope.launch {
            val steps = 40
            val stepDelay = totalMs / steps
            for (i in 1..steps) {
                delay(stepDelay)
                _playingVoiceProgress.value = i / steps.toFloat()
            }
            _playingVoiceMessageId.value = null
            _playingVoiceProgress.value = 0f
        }
    }

    private fun stopPlayback() {
        playbackJob?.cancel()
        _playingVoiceMessageId.value = null
        _playingVoiceProgress.value = 0f
    }

    // --- Simulated Calls ---

    fun startSimulatedCall(
        chatId: String,
        title: String,
        avatarEmoji: String,
        avatarColorHex: String,
        callType: CallType
    ) {
        callTimerJob?.cancel()
        _activeCall.value = ActiveCallUiState(
            chatId = chatId,
            title = title,
            avatarEmoji = avatarEmoji,
            avatarColorHex = avatarColorHex,
            callType = callType,
            isConnected = false,
            durationSec = 0,
            isCameraOn = callType == CallType.VIDEO
        )
        callTimerJob = viewModelScope.launch {
            delay(2000)
            _activeCall.value = _activeCall.value?.copy(isConnected = true)
            while (true) {
                delay(1000)
                val current = _activeCall.value ?: break
                _activeCall.value = current.copy(durationSec = current.durationSec + 1)
            }
        }
    }

    fun toggleCallMute() {
        _activeCall.value = _activeCall.value?.let { it.copy(isMuted = !it.isMuted) }
    }

    fun toggleCallSpeaker() {
        _activeCall.value = _activeCall.value?.let { it.copy(isSpeakerOn = !it.isSpeakerOn) }
    }

    fun toggleCallCamera() {
        _activeCall.value = _activeCall.value?.let { it.copy(isCameraOn = !it.isCameraOn) }
    }

    fun endSimulatedCall() {
        callTimerJob?.cancel()
        val callState = _activeCall.value
        _activeCall.value = null
        val currentId = _activeMemberId.value ?: return
        if (callState != null) {
            viewModelScope.launch {
                repo.insertCall(
                    CallRecordEntity(
                        id = "call_${UUID.randomUUID().toString().take(8)}",
                        chatId = callState.chatId,
                        title = callState.title,
                        callerId = currentId,
                        avatarColorHex = callState.avatarColorHex,
                        avatarEmoji = callState.avatarEmoji,
                        callType = callState.callType,
                        direction = if (callState.isConnected) CallDirection.OUTGOING else CallDirection.MISSED,
                        durationSec = callState.durationSec,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun deleteCallRecord(id: String) {
        viewModelScope.launch { repo.deleteCall(id) }
    }

    // --- Tablón Familiar ---

    fun addShoppingItem(name: String, quantity: String, category: String) {
        val currentId = _activeMemberId.value ?: return
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            repo.upsertShoppingItem(
                ShoppingItemEntity(
                    id = "shop_${UUID.randomUUID().toString().take(8)}",
                    name = clean,
                    quantity = quantity.trim().ifEmpty { "1 u." },
                    category = category.trim().ifEmpty { "General" },
                    isChecked = false,
                    addedById = currentId,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun toggleShoppingItem(item: ShoppingItemEntity) {
        viewModelScope.launch {
            repo.upsertShoppingItem(item.copy(isChecked = !item.isChecked))
        }
    }

    fun deleteShoppingItem(id: String) {
        viewModelScope.launch { repo.deleteShoppingItem(id) }
    }

    fun clearCheckedShoppingItems() {
        viewModelScope.launch {
            repo.clearCheckedShoppingItems()
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
        val clean = title.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            repo.upsertEvent(
                FamilyEventEntity(
                    id = "evt_${UUID.randomUUID().toString().take(8)}",
                    title = clean,
                    dateLabel = dateLabel.trim().ifEmpty { "Esta semana" },
                    timeLabel = timeLabel.trim().ifEmpty { "Todo el día" },
                    description = description.trim(),
                    assignedMemberId = assignedMemberId,
                    colorHex = colorHex,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun deleteFamilyEvent(id: String) {
        viewModelScope.launch { repo.deleteEvent(id) }
    }

    fun saveFamilyNote(existingId: String?, title: String, content: String, colorHex: String, isPinned: Boolean) {
        val currentId = _activeMemberId.value ?: return
        val cleanTitle = title.trim()
        val cleanContent = content.trim()
        if (cleanTitle.isEmpty() || cleanContent.isEmpty()) return
        viewModelScope.launch {
            repo.upsertNote(
                FamilyNoteEntity(
                    id = existingId ?: "note_${UUID.randomUUID().toString().take(8)}",
                    title = cleanTitle,
                    content = cleanContent,
                    authorId = currentId,
                    colorHex = colorHex,
                    isPinned = isPinned,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun deleteFamilyNote(id: String) {
        viewModelScope.launch { repo.deleteNote(id) }
    }
}
