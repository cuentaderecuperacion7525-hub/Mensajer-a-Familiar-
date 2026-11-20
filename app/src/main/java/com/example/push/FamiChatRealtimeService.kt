package com.example.push

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Shared in-process state so background real-time listeners never duplicate
 * notifications already handled while the app is open in the foreground.
 */
object FamiChatAppRuntimeState {
    @Volatile
    var isAppForeground: Boolean = false

    @Volatile
    var activeChatId: String? = null

    @Volatile
    var isRealtimeServiceRunning: Boolean = false
}

/**
 * Lightweight persistent foreground service (`remoteMessaging`) that keeps
 * Firestore real-time listeners active even when the screen is off or on devices
 * without Google Play Services, delivering incoming messages and calls in ~1 second.
 */
class FamiChatRealtimeService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sessionWatcherJob: Job? = null
    private var messageListenerJob: Job? = null
    private var callListenerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = FamiChatNotificationHelper.buildRealtimeServiceNotification(this)
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
        } else {
            0
        }
        val promoted = runCatching {
            ServiceCompat.startForeground(
                this,
                FamiChatNotificationHelper.NOTIFICATION_ID_REALTIME_SYNC,
                notification,
                fgsType
            )
            true
        }.getOrDefault(false)

        if (!promoted) {
            stopSelf()
            return START_NOT_STICKY
        }

        FamiChatAppRuntimeState.isRealtimeServiceRunning = true
        ensureSessionWatcherStarted()
        return START_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf(startId)
    }

    override fun onDestroy() {
        FamiChatAppRuntimeState.isRealtimeServiceRunning = false
        messageListenerJob?.cancel()
        callListenerJob?.cancel()
        sessionWatcherJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun ensureSessionWatcherStarted() {
        if (sessionWatcherJob?.isActive == true) return
        val prefs = PreferencesManager(applicationContext)
        val repo = FamilyCloudRepository(applicationContext)

        sessionWatcherJob = serviceScope.launch {
            combine(
                prefs.activeFamilyIdFlow,
                prefs.activeMemberIdFlow,
                prefs.notificationsEnabledFlow
            ) { famId, memberId, enabled ->
                Triple(famId, memberId, enabled)
            }
                .distinctUntilChanged()
                .collectLatest { (famId, meId, notifEnabled) ->
                    messageListenerJob?.cancel()
                    callListenerJob?.cancel()

                    val uid = runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
                    if (!notifEnabled || famId.isNullOrBlank() || meId.isNullOrBlank() || uid.isNullOrBlank()) {
                        stopSelf()
                        return@collectLatest
                    }

                    startMessageListener(repo, prefs, famId, meId, uid)
                    startCallListener(repo, famId, meId, uid)
                }
        }
    }

    private fun startMessageListener(
        repo: FamilyCloudRepository,
        prefs: PreferencesManager,
        famId: String,
        meId: String,
        uid: String
    ) {
        messageListenerJob = serviceScope.launch {
            val seenMessageIds = mutableSetOf<String>()
            val deliveredAttemptedIds = mutableSetOf<String>()
            var initialized = false
            val initialLastNotifiedTs = prefs.lastNotifiedTimestampFlow.first()
            val startedAt = System.currentTimeMillis()
            val baselineTs = if (initialLastNotifiedTs > 0L) initialLastNotifiedTs else startedAt

            combine(
                repo.observeMessages(famId, uid),
                repo.observeChats(famId, uid),
                repo.observeMembers(famId, uid),
                prefs.mutedChatIdsFlow
            ) { messages, chats, members, mutedChatIds ->
                Quad(messages, chats, members, mutedChatIds)
            }
                .catch { err ->
                    if (err is CancellationException) throw err
                }
                .collect { (messages, chats, members, mutedChatIds) ->
                    val chatMap = chats.associateBy { it.id }
                    val memberMap = members.associateBy { it.id }

                    // Immediately mark incoming SENT messages as DELIVERED (✓✓) even with screen locked
                    messages.forEach { msg ->
                        val chat = chatMap[msg.chatId] ?: return@forEach
                        val participants = JsonHelpers.decodeStringList(chat.participantIdsJson)
                        if (participants.contains(meId) &&
                            msg.senderId != meId &&
                            msg.type != MessageType.DELETED &&
                            msg.status == MessageStatus.SENT &&
                            deliveredAttemptedIds.add(msg.id)
                        ) {
                            launch {
                                repo.markMessageDeliveredToRecipient(famId, msg.id)
                            }
                        }
                    }

                    if (!initialized) {
                        initialized = true
                        messages.forEach { msg ->
                            if (msg.timestamp <= baselineTs) {
                                seenMessageIds.add(msg.id)
                            }
                        }
                        if (initialLastNotifiedTs == 0L) {
                            prefs.updateLastNotifiedTimestampIfNewer(startedAt)
                        }
                    }

                    val latestPersistedTs = prefs.lastNotifiedTimestampFlow.first()
                    var maxNotifiedTs = latestPersistedTs

                    messages.forEach { msg ->
                        if (!seenMessageIds.add(msg.id)) return@forEach
                        if (msg.timestamp <= latestPersistedTs) return@forEach

                        val chat = chatMap[msg.chatId] ?: return@forEach
                        val participants = JsonHelpers.decodeStringList(chat.participantIdsJson)
                        val alreadyRead = msg.status == MessageStatus.READ ||
                            JsonHelpers.decodeStringList(msg.readByMemberIdsJson).contains(meId)

                        if (!participants.contains(meId) ||
                            msg.senderId == meId ||
                            msg.type == MessageType.DELETED ||
                            alreadyRead ||
                            mutedChatIds.contains(msg.chatId)
                        ) {
                            return@forEach
                        }

                        // When app is open in foreground, FamilyViewModel already handles in-app alerts
                        if (FamiChatAppRuntimeState.isAppForeground) {
                            if (msg.timestamp > maxNotifiedTs) maxNotifiedTs = msg.timestamp
                            return@forEach
                        }

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
                            FamiChatNotificationHelper.notifyNewMessage(
                                context = applicationContext,
                                chatId = msg.chatId,
                                chatTitle = chatTitle,
                                senderName = senderName,
                                body = body,
                                isGroup = chat.type == ChatType.GROUP
                            )
                            if (msg.timestamp > maxNotifiedTs) {
                                maxNotifiedTs = msg.timestamp
                            }
                        }
                    }

                    if (maxNotifiedTs > latestPersistedTs) {
                        prefs.updateLastNotifiedTimestampIfNewer(maxNotifiedTs)
                    }
                }
        }
    }

    private fun startCallListener(
        repo: FamilyCloudRepository,
        famId: String,
        meId: String,
        uid: String
    ) {
        callListenerJob = serviceScope.launch {
            var notifiedCallId: String? = null
            combine(
                repo.observeCalls(famId, uid),
                repo.observeMembers(famId, uid)
            ) { calls, members -> calls to members }
                .catch { err ->
                    if (err is CancellationException) throw err
                }
                .collect { (calls, members) ->
                    val now = System.currentTimeMillis()
                    val ringing = calls.firstOrNull { call ->
                        val targets = JsonHelpers.decodeStringList(call.targetMemberIdsJson)
                        call.callStatus == CallSignalStatus.RINGING &&
                            call.callerId != meId &&
                            targets.contains(meId) &&
                            (now - call.timestamp) < 60_000L
                    }
                    if (ringing != null && !FamiChatAppRuntimeState.isAppForeground) {
                        if (notifiedCallId != ringing.id) {
                            notifiedCallId = ringing.id
                            val callerName = members.find { it.id == ringing.callerId }?.name ?: ringing.title
                            FamiChatNotificationHelper.notifyIncomingCall(
                                context = applicationContext,
                                callId = ringing.id,
                                chatId = ringing.chatId,
                                callerName = callerName,
                                isVideo = ringing.callType == CallType.VIDEO
                            )
                        }
                    } else if (ringing == null && notifiedCallId != null) {
                        FamiChatNotificationHelper.cancelCallNotification(applicationContext, notifiedCallId!!)
                        notifiedCallId = null
                    }
                }
        }
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    companion object {
        private const val ACTION_START = "com.example.push.ACTION_START_REALTIME"
        private const val ACTION_STOP = "com.example.push.ACTION_STOP_REALTIME"

        fun start(context: Context) {
            val appCtx = context.applicationContext
            val intent = Intent(appCtx, FamiChatRealtimeService::class.java).apply {
                action = ACTION_START
            }
            runCatching {
                ContextCompat.startForegroundService(appCtx, intent)
            }
        }

        fun stop(context: Context) {
            val appCtx = context.applicationContext
            runCatching {
                appCtx.stopService(Intent(appCtx, FamiChatRealtimeService::class.java))
            }
        }
    }
}
