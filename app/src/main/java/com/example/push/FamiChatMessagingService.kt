package com.example.push

import com.example.data.local.PreferencesManager
import com.example.data.remote.FamilyCloudRepository
import com.example.util.FamiChatNotificationHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class FamiChatMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        if (token.isBlank()) return
        val appContext = applicationContext
        val prefs = PreferencesManager(appContext)
        serviceScope.launch {
            prefs.setFcmToken(token)
            val currentUid = Firebase.auth.currentUser?.uid ?: return@launch
            val famId = prefs.activeFamilyIdFlow.first() ?: return@launch
            val memberId = prefs.activeMemberIdFlow.first() ?: return@launch
            if (currentUid.isNotBlank()) {
                val repo = FamilyCloudRepository(appContext)
                repo.registerMemberFcmToken(famId, memberId, token)
                PushRegistrationManager.syncFcmTokenAndTopic(appContext, repo, prefs, famId, memberId)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val appContext = applicationContext
        val prefs = PreferencesManager(appContext)

        val (notifEnabled, activeMemberId) = runBlocking {
            val enabled = prefs.notificationsEnabledFlow.first()
            val memberId = prefs.activeMemberIdFlow.first()
            enabled to memberId
        }
        if (!notifEnabled) return

        val data = message.data
        val payloadType = data["type"]?.lowercase() ?: "message"

        if (payloadType == "call") {
            val callerMemberId = data["callerMemberId"].orEmpty()
            if (!activeMemberId.isNullOrBlank() && callerMemberId == activeMemberId) return

            val callId = data["callId"].orEmpty().ifBlank { "fcm_call_${System.currentTimeMillis()}" }
            val chatId = data["chatId"].orEmpty()
            val callerName = data["callerName"].orEmpty()
                .ifBlank { message.notification?.title ?: "Familiar" }
            val isVideo = data["isVideo"] == "true"

            FamiChatNotificationHelper.notifyIncomingCall(
                context = appContext,
                callId = callId,
                chatId = chatId,
                callerName = callerName,
                isVideo = isVideo
            )
            return
        }

        val senderId = data["senderId"].orEmpty()
        if (!activeMemberId.isNullOrBlank() && senderId == activeMemberId) return

        val targetMemberIds = data["targetMemberIds"]
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        if (!activeMemberId.isNullOrBlank() &&
            targetMemberIds.isNotEmpty() &&
            !targetMemberIds.contains(activeMemberId)
        ) {
            return
        }

        val chatId = data["chatId"].orEmpty().ifBlank { "general" }
        val senderName = data["senderName"].orEmpty()
            .ifBlank { message.notification?.title ?: "FamiChat" }
        val chatTitle = data["chatTitle"].orEmpty().ifBlank { senderName }
        val body = data["body"].orEmpty()
            .ifBlank { message.notification?.body ?: "Nuevo mensaje en FamiChat" }
        val isGroup = data["isGroup"] == "true"
        val msgTimestamp = data["timestamp"]?.toLongOrNull() ?: System.currentTimeMillis()

        runBlocking {
            prefs.updateLastNotifiedTimestampIfNewer(msgTimestamp)
        }

        FamiChatNotificationHelper.notifyNewMessage(
            context = appContext,
            chatId = chatId,
            chatTitle = chatTitle,
            senderName = senderName,
            body = body,
            isGroup = isGroup
        )
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
