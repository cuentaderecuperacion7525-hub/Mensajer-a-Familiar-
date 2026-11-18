package com.example.push

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.PreferencesManager
import com.example.data.model.CallSignalStatus
import com.example.data.model.CallType
import com.example.data.model.ChatType
import com.example.data.model.JsonHelpers
import com.example.data.model.MessageStatus
import com.example.data.model.MessageType
import com.example.data.remote.FamilyCloudRepository
import com.example.util.FamiChatNotificationHelper
import com.example.util.MessageFormatter
import com.example.util.TimeFormatters
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.first

class FamiChatBackgroundSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_IS_IMMEDIATE_CHECK = "is_immediate_check"
    }

    override suspend fun doWork(): Result {
        val appContext = applicationContext
        val uid = Firebase.auth.currentUser?.uid ?: return Result.success()
        val prefs = PreferencesManager(appContext)
        val notifEnabled = prefs.notificationsEnabledFlow.first()
        if (!notifEnabled || !FamiChatNotificationHelper.canPostNotifications(appContext)) {
            return Result.success()
        }

        val famId = prefs.activeFamilyIdFlow.first() ?: return Result.success()
        val meId = prefs.activeMemberIdFlow.first() ?: return Result.success()

        val repo = FamilyCloudRepository(appContext)
        if (prefs.fcmTokenFlow.first().isNullOrBlank()) {
            PushRegistrationManager.syncFcmTokenAndTopic(appContext, repo, prefs, famId, meId)
        }

        val isImmediateCheck = inputData.getBoolean(KEY_IS_IMMEDIATE_CHECK, false)
        val now = System.currentTimeMillis()
        val savedLastTs = prefs.lastNotifiedTimestampFlow.first()
        val mutedChatIds = prefs.mutedChatIdsFlow.first()
        val cutoffTs = if (savedLastTs > 0L) savedLastTs else (now - 120_000L)

        val chats = repo.fetchChatsOnce(famId, uid)
        val chatMap = chats.associateBy { it.id }

        val hasNewChatActivity = chats.any { chat ->
            val participants = JsonHelpers.decodeStringList(chat.participantIdsJson)
            participants.contains(meId) &&
                !mutedChatIds.contains(chat.id) &&
                chat.updatedAt > cutoffTs
        }

        val messages = if (hasNewChatActivity) repo.fetchMessagesOnce(famId, uid) else emptyList()
        val calls = if (isImmediateCheck) repo.fetchCallsOnce(famId, uid) else emptyList()

        val activeRingingCall = calls.firstOrNull { call ->
            val targets = JsonHelpers.decodeStringList(call.targetMemberIdsJson)
            call.callStatus == CallSignalStatus.RINGING &&
                call.callerId != meId &&
                targets.contains(meId) &&
                (now - call.timestamp) < 60_000L
        }

        val unreadNewMessages = messages.filter { msg ->
            if (mutedChatIds.contains(msg.chatId)) return@filter false
            val chat = chatMap[msg.chatId] ?: return@filter false
            val participants = JsonHelpers.decodeStringList(chat.participantIdsJson)
            participants.contains(meId) &&
                msg.senderId != meId &&
                msg.type != MessageType.DELETED &&
                msg.status != MessageStatus.READ &&
                !JsonHelpers.decodeStringList(msg.readByMemberIdsJson).contains(meId) &&
                msg.timestamp > cutoffTs
        }

        val memberMap = if (unreadNewMessages.isNotEmpty() || activeRingingCall != null) {
            repo.fetchMembersOnce(famId, uid).associateBy { it.id }
        } else {
            emptyMap()
        }

        // Automatically mark incoming SENT messages as DELIVERED (double gray check ✓✓) in background
        messages.forEach { msg ->
            val chat = chatMap[msg.chatId] ?: return@forEach
            val participants = JsonHelpers.decodeStringList(chat.participantIdsJson)
            if (participants.contains(meId) &&
                msg.senderId != meId &&
                msg.type != MessageType.DELETED &&
                msg.status == MessageStatus.SENT
            ) {
                repo.markMessageDeliveredToRecipient(famId, msg.id)
            }
        }

        var maxNotifiedTs = savedLastTs
        unreadNewMessages
            .groupBy { it.chatId }
            .forEach { (chatId, chatMsgs) ->
                val latest = chatMsgs.maxByOrNull { it.timestamp } ?: return@forEach
                val chat = chatMap[chatId] ?: return@forEach
                val senderName = memberMap[latest.senderId]?.name ?: "Familiar"
                val chatTitle = if (chat.type == ChatType.GROUP) chat.title else senderName
                val baseBody = when (latest.type) {
                    MessageType.IMAGE -> if (latest.text.isNotBlank()) "📷 ${latest.text}" else "📷 Foto"
                    MessageType.VIDEO -> if (latest.text.isNotBlank()) "🎬 ${latest.text}" else "🎬 Video"
                    MessageType.VOICE -> "🎤 Nota de voz (${TimeFormatters.formatDuration(latest.voiceDurationSec)})"
                    MessageType.LOCATION -> "📍 ${latest.locationName ?: "Ubicación"}"
                    MessageType.DOCUMENT -> "📄 ${latest.fileName ?: "Documento"}"
                    MessageType.POLL -> "📊 ${latest.pollQuestion ?: "Encuesta"}"
                    MessageType.TEXT -> MessageFormatter.stripMarkdownForPreview(latest.text)
                    MessageType.DELETED -> ""
                }
                val body = if (chatMsgs.size > 1) {
                    "$baseBody (+${chatMsgs.size - 1} más)"
                } else {
                    baseBody
                }
                if (body.isNotBlank()) {
                    FamiChatNotificationHelper.notifyNewMessage(
                        context = appContext,
                        chatId = chatId,
                        chatTitle = chatTitle,
                        senderName = senderName,
                        body = body,
                        isGroup = chat.type == ChatType.GROUP
                    )
                    if (latest.timestamp > maxNotifiedTs) {
                        maxNotifiedTs = latest.timestamp
                    }
                }
            }

        if (maxNotifiedTs > savedLastTs) {
            prefs.updateLastNotifiedTimestampIfNewer(maxNotifiedTs)
        } else if (savedLastTs == 0L) {
            prefs.updateLastNotifiedTimestampIfNewer(now)
        }

        if (activeRingingCall != null) {
            val callerName = memberMap[activeRingingCall.callerId]?.name ?: activeRingingCall.title
            FamiChatNotificationHelper.notifyIncomingCall(
                context = appContext,
                callId = activeRingingCall.id,
                chatId = activeRingingCall.chatId,
                callerName = callerName,
                isVideo = activeRingingCall.callType == CallType.VIDEO
            )
        }

        return Result.success()
    }
}
