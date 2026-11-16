package com.example.util

import com.example.data.model.FamilyMemberEntity
import com.example.data.model.PresenceStatus

data class ResolvedPresence(
    val isOnline: Boolean,
    val statusText: String
)

object PresenceResolver {
    const val ONLINE_TTL_MS = 75_000L
    const val HEARTBEAT_INTERVAL_MS = 25_000L
    const val CLOCK_TICK_MS = 15_000L
    const val LIVE_ACTION_TTL_MS = 7_000L

    private val defaultOnlineLabels = setOf(
        "",
        "En línea",
        "Desconectado",
        "No se ha conectado"
    )

    private val awayStatusLabels = setOf(
        "Ocupado",
        "En el trabajo",
        "En la escuela"
    )

    fun hasEverConnected(member: FamilyMemberEntity): Boolean =
        member.linkedAuthUid.isNotBlank() || member.liveStatusAt > 0L

    fun isLiveChatAction(member: FamilyMemberEntity, chatId: String, nowMs: Long): Boolean {
        if (member.liveChatId != chatId) return false
        if (member.liveStatus != PresenceStatus.TYPING && member.liveStatus != PresenceStatus.RECORDING) {
            return false
        }
        val age = nowMs - member.liveStatusAt
        return age in -10_000L..LIVE_ACTION_TTL_MS
    }

    fun resolve(
        member: FamilyMemberEntity,
        nowMs: Long = System.currentTimeMillis(),
        isLocalActiveInForeground: Boolean = false,
        manualInvisible: Boolean = false
    ): ResolvedPresence {
        val everConnected = hasEverConnected(member)
        if (!everConnected && !isLocalActiveInForeground) {
            return ResolvedPresence(isOnline = false, statusText = "No se ha conectado")
        }

        val freshRemoteHeartbeat = member.isOnline &&
            member.liveStatus != PresenceStatus.LAST_SEEN &&
            member.liveStatusAt > 0L &&
            (nowMs - member.liveStatusAt) in -60_000L..ONLINE_TTL_MS

        val effectivelyOnline = if (isLocalActiveInForeground) {
            !manualInvisible && member.isOnline
        } else {
            everConnected && freshRemoteHeartbeat
        }

        val rawLabel = member.lastSeenText.trim()
        val hasCustomLabel = rawLabel !in defaultOnlineLabels && !rawLabel.startsWith("Últ.")

        if (effectivelyOnline) {
            val onlineLabel = if (hasCustomLabel && rawLabel !in awayStatusLabels) rawLabel else "En línea"
            return ResolvedPresence(isOnline = true, statusText = onlineLabel)
        }

        // Offline / last seen state
        if (!everConnected) {
            return ResolvedPresence(isOnline = false, statusText = "No se ha conectado")
        }

        val lastSeenLabel = if (member.liveStatusAt > 0L) {
            TimeFormatters.formatLastSeen(member.liveStatusAt, nowMs)
        } else {
            "Desconectado"
        }

        val offlineText = if (hasCustomLabel && rawLabel != "En casa") {
            if (member.liveStatusAt > 0L) "$rawLabel · $lastSeenLabel" else rawLabel
        } else {
            lastSeenLabel
        }

        return ResolvedPresence(isOnline = false, statusText = offlineText)
    }
}
