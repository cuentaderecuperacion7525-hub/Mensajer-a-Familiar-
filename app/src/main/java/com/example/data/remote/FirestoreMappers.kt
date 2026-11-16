package com.example.data.remote

import com.example.data.model.CallDirection
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
import com.example.data.model.JsonHelpers
import com.example.data.model.MessageStatus
import com.example.data.model.MessageType
import com.example.data.model.PollOption
import com.example.data.model.PresenceStatus
import com.example.data.model.ShoppingItemEntity
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue

internal object FirestoreMappers {

    fun toFamilySpace(doc: DocumentSnapshot): FamilySpace? {
        if (!doc.exists()) return null
        val uids = (doc.get("memberUids") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        return FamilySpace(
            id = doc.id,
            ownerUid = doc.getString("ownerUid") ?: "",
            name = doc.getString("name") ?: "Mi Familia",
            inviteCode = doc.getString("inviteCode") ?: "",
            memberUids = uids,
            masterPin = doc.getString("masterPin") ?: "752581",
            switchPin = doc.getString("switchPin") ?: "258046",
            updateRepoSlug = doc.getString("updateRepoSlug") ?: ""
        )
    }

    fun toMember(doc: DocumentSnapshot): FamilyMemberEntity? {
        if (!doc.exists()) return null
        val liveStr = doc.getString("liveStatus") ?: "ONLINE"
        val liveStatus = runCatching { PresenceStatus.valueOf(liveStr) }.getOrDefault(PresenceStatus.ONLINE)
        return FamilyMemberEntity(
            id = doc.id,
            name = doc.getString("name") ?: return null,
            roleLabel = doc.getString("roleLabel") ?: "Familiar",
            isAdmin = doc.getBoolean("isAdmin") ?: false,
            avatarColorHex = doc.getString("avatarColorHex") ?: "#1A73E8",
            avatarEmoji = doc.getString("avatarEmoji") ?: "👤",
            isOnline = doc.getBoolean("isOnline") ?: false,
            lastSeenText = doc.getString("lastSeenText") ?: "",
            createdAt = doc.getLong("createdAtMs") ?: 0L,
            phoneNumber = doc.getString("phoneNumber") ?: "",
            liveStatus = liveStatus,
            liveChatId = doc.getString("liveChatId"),
            liveStatusAt = doc.getLong("liveStatusAt") ?: 0L,
            linkedAuthUid = doc.getString("linkedAuthUid") ?: "",
            authorUid = doc.getString("authorUid") ?: ""
        )
    }

    fun memberToMap(
        member: FamilyMemberEntity,
        memberUids: List<String>,
        authorUid: String,
        isCreate: Boolean
    ): Map<String, Any> {
        val map = mutableMapOf<String, Any?>(
            "memberUids" to memberUids,
            "authorUid" to authorUid,
            "name" to member.name.take(40),
            "roleLabel" to member.roleLabel.take(40),
            "isAdmin" to member.isAdmin,
            "avatarColorHex" to member.avatarColorHex.take(16),
            "avatarEmoji" to member.avatarEmoji.take(16),
            "phoneNumber" to member.phoneNumber.take(30),
            "isOnline" to member.isOnline,
            "lastSeenText" to member.lastSeenText.take(80),
            "liveStatus" to member.liveStatus.name,
            "liveChatId" to member.liveChatId?.take(128),
            "liveStatusAt" to member.liveStatusAt,
            "linkedAuthUid" to member.linkedAuthUid.take(128),
            "createdAtMs" to member.createdAt,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (isCreate) map["createdAt"] = FieldValue.serverTimestamp()
        @Suppress("UNCHECKED_CAST")
        return map.filterValues { it != null } as Map<String, Any>
    }

    fun toChatThread(doc: DocumentSnapshot): ChatThreadEntity? {
        if (!doc.exists()) return null
        val type = runCatching { ChatType.valueOf(doc.getString("type") ?: "DIRECT") }.getOrDefault(ChatType.DIRECT)
        val pIds = (doc.get("participantIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        return ChatThreadEntity(
            id = doc.id,
            type = type,
            title = doc.getString("title") ?: "Chat",
            avatarColorHex = doc.getString("avatarColorHex") ?: "#1A73E8",
            avatarEmoji = doc.getString("avatarEmoji") ?: "💬",
            participantIdsJson = JsonHelpers.encodeStringList(pIds),
            pinnedMessageId = doc.getString("pinnedMessageId"),
            updatedAt = doc.getLong("lastMessageAt") ?: 0L,
            authorUid = doc.getString("authorUid") ?: ""
        )
    }

    fun chatToMap(
        chat: ChatThreadEntity,
        memberUids: List<String>,
        authorUid: String,
        isCreate: Boolean
    ): Map<String, Any> {
        val pIds = JsonHelpers.decodeStringList(chat.participantIdsJson).take(25)
        val map = mutableMapOf<String, Any?>(
            "memberUids" to memberUids,
            "authorUid" to authorUid,
            "type" to chat.type.name,
            "title" to chat.title.take(60),
            "avatarColorHex" to chat.avatarColorHex.take(16),
            "avatarEmoji" to chat.avatarEmoji.take(16),
            "participantIds" to pIds,
            "pinnedMessageId" to chat.pinnedMessageId,
            "lastMessageAt" to chat.updatedAt,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (isCreate) map["createdAt"] = FieldValue.serverTimestamp()
        @Suppress("UNCHECKED_CAST")
        return map.filterValues { it != null } as Map<String, Any>
    }

    fun toChatMessage(doc: DocumentSnapshot): ChatMessageEntity? {
        if (!doc.exists()) return null
        val type = runCatching { MessageType.valueOf(doc.getString("type") ?: "TEXT") }.getOrDefault(MessageType.TEXT)
        val status = runCatching { MessageStatus.valueOf(doc.getString("status") ?: "SENT") }.getOrDefault(MessageStatus.SENT)
        val rawReactions = (doc.get("reactions") as? Map<*, *>)
            ?.entries
            ?.mapNotNull { (k, v) -> if (k is String && v is String) k to v else null }
            ?.toMap() ?: emptyMap()
        val hiddenIds = (doc.get("hiddenForMemberIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        val waveform = (doc.get("voiceWaveform") as? List<*>)?.mapNotNull { (it as? Number)?.toFloat() } ?: emptyList()
        val rawPollOpts = (doc.get("pollOptions") as? List<*>)?.mapNotNull { raw ->
            val m = raw as? Map<*, *> ?: return@mapNotNull null
            val optId = m["id"] as? String ?: return@mapNotNull null
            val optText = m["text"] as? String ?: ""
            val voters = (m["voterIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            PollOption(id = optId, text = optText, voterIds = voters)
        } ?: emptyList()

        return ChatMessageEntity(
            id = doc.id,
            chatId = doc.getString("chatId") ?: return null,
            senderId = doc.getString("senderId") ?: return null,
            text = doc.getString("text") ?: "",
            type = type,
            status = status,
            timestamp = doc.getLong("timestamp") ?: 0L,
            replyToMessageId = doc.getString("replyToMessageId"),
            isEdited = doc.getBoolean("isEdited") ?: false,
            reactionsJson = JsonHelpers.encodeReactions(rawReactions),
            hiddenForMemberIdsJson = JsonHelpers.encodeStringList(hiddenIds),
            mediaUri = doc.getString("mediaData"),
            voiceDurationSec = (doc.getLong("voiceDurationSec") ?: 0L).toInt(),
            voiceWaveformJson = JsonHelpers.encodeFloatList(waveform),
            locationName = doc.getString("locationName"),
            locationAddress = doc.getString("locationAddress"),
            locationLat = doc.getDouble("locationLat"),
            locationLng = doc.getDouble("locationLng"),
            pollQuestion = doc.getString("pollQuestion"),
            pollOptionsJson = JsonHelpers.encodePollOptions(rawPollOpts),
            authorUid = doc.getString("authorUid") ?: ""
        )
    }

    fun messageToMap(
        msg: ChatMessageEntity,
        memberUids: List<String>,
        authorUid: String,
        isCreate: Boolean
    ): Map<String, Any> {
        val reactions = JsonHelpers.decodeReactions(msg.reactionsJson)
        val hidden = JsonHelpers.decodeStringList(msg.hiddenForMemberIdsJson).take(25)
        val waveform = JsonHelpers.decodeFloatList(msg.voiceWaveformJson).take(60)
        val pollOpts = JsonHelpers.decodePollOptions(msg.pollOptionsJson).take(10).map { opt ->
            mapOf("id" to opt.id, "text" to opt.text, "voterIds" to opt.voterIds)
        }
        val map = mutableMapOf<String, Any?>(
            "memberUids" to memberUids,
            "authorUid" to authorUid,
            "chatId" to msg.chatId,
            "senderId" to msg.senderId,
            "text" to msg.text.take(4000),
            "type" to msg.type.name,
            "status" to msg.status.name,
            "timestamp" to msg.timestamp,
            "replyToMessageId" to msg.replyToMessageId,
            "isEdited" to msg.isEdited,
            "reactions" to reactions,
            "hiddenForMemberIds" to hidden,
            "mediaData" to msg.mediaUri?.take(750_000),
            "voiceDurationSec" to msg.voiceDurationSec,
            "voiceWaveform" to waveform,
            "locationName" to msg.locationName?.take(120),
            "locationAddress" to msg.locationAddress?.take(240),
            "locationLat" to msg.locationLat,
            "locationLng" to msg.locationLng,
            "pollQuestion" to msg.pollQuestion?.take(200),
            "pollOptions" to pollOpts,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (isCreate) map["createdAt"] = FieldValue.serverTimestamp()
        @Suppress("UNCHECKED_CAST")
        return map.filterValues { it != null } as Map<String, Any>
    }

    fun toCallRecord(doc: DocumentSnapshot): CallRecordEntity? {
        if (!doc.exists()) return null
        val type = runCatching { CallType.valueOf(doc.getString("callType") ?: "VOICE") }.getOrDefault(CallType.VOICE)
        val signal = runCatching { CallSignalStatus.valueOf(doc.getString("callStatus") ?: "ENDED") }.getOrDefault(CallSignalStatus.ENDED)
        val dir = runCatching { CallDirection.valueOf(doc.getString("direction") ?: "OUTGOING") }.getOrDefault(CallDirection.OUTGOING)
        val targets = (doc.get("targetMemberIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        return CallRecordEntity(
            id = doc.id,
            chatId = doc.getString("chatId") ?: "",
            title = doc.getString("title") ?: "Llamada",
            callerId = doc.getString("callerMemberId") ?: "",
            avatarColorHex = doc.getString("avatarColorHex") ?: "#1A73E8",
            avatarEmoji = doc.getString("avatarEmoji") ?: "📞",
            callType = type,
            direction = dir,
            durationSec = (doc.getLong("durationSec") ?: 0L).toInt(),
            timestamp = doc.getLong("timestamp") ?: 0L,
            callStatus = signal,
            targetMemberIdsJson = JsonHelpers.encodeStringList(targets),
            connectedAt = doc.getLong("connectedAt") ?: 0L,
            authorUid = doc.getString("authorUid") ?: ""
        )
    }

    fun callToMap(
        call: CallRecordEntity,
        memberUids: List<String>,
        authorUid: String,
        isCreate: Boolean
    ): Map<String, Any> {
        val targets = JsonHelpers.decodeStringList(call.targetMemberIdsJson).take(25)
        val map = mutableMapOf<String, Any?>(
            "memberUids" to memberUids,
            "authorUid" to authorUid,
            "chatId" to call.chatId,
            "title" to call.title.take(60),
            "callerMemberId" to call.callerId,
            "targetMemberIds" to targets,
            "avatarColorHex" to call.avatarColorHex.take(16),
            "avatarEmoji" to call.avatarEmoji.take(16),
            "callType" to call.callType.name,
            "callStatus" to call.callStatus.name,
            "direction" to call.direction.name,
            "durationSec" to call.durationSec,
            "connectedAt" to call.connectedAt,
            "timestamp" to call.timestamp,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (isCreate) map["createdAt"] = FieldValue.serverTimestamp()
        @Suppress("UNCHECKED_CAST")
        return map.filterValues { it != null } as Map<String, Any>
    }

    fun toShoppingItem(doc: DocumentSnapshot): ShoppingItemEntity? {
        if (!doc.exists()) return null
        return ShoppingItemEntity(
            id = doc.id,
            name = doc.getString("name") ?: return null,
            quantity = doc.getString("quantity") ?: "1 u.",
            category = doc.getString("category") ?: "General",
            isChecked = doc.getBoolean("isChecked") ?: false,
            addedById = doc.getString("addedById") ?: "",
            createdAt = doc.getLong("timestamp") ?: 0L,
            authorUid = doc.getString("authorUid") ?: ""
        )
    }

    fun toFamilyEvent(doc: DocumentSnapshot): FamilyEventEntity? {
        if (!doc.exists()) return null
        return FamilyEventEntity(
            id = doc.id,
            title = doc.getString("title") ?: return null,
            dateLabel = doc.getString("dateLabel") ?: "",
            timeLabel = doc.getString("timeLabel") ?: "",
            description = doc.getString("description") ?: "",
            assignedMemberId = doc.getString("assignedMemberId") ?: "",
            colorHex = doc.getString("colorHex") ?: "#1A73E8",
            createdAt = doc.getLong("timestamp") ?: 0L,
            authorUid = doc.getString("authorUid") ?: ""
        )
    }

    fun toFamilyNote(doc: DocumentSnapshot): FamilyNoteEntity? {
        if (!doc.exists()) return null
        return FamilyNoteEntity(
            id = doc.id,
            title = doc.getString("title") ?: return null,
            content = doc.getString("content") ?: "",
            authorId = doc.getString("authorId") ?: "",
            colorHex = doc.getString("colorHex") ?: "#FFF4CC",
            isPinned = doc.getBoolean("isPinned") ?: true,
            createdAt = doc.getLong("timestamp") ?: 0L,
            authorUid = doc.getString("authorUid") ?: ""
        )
    }
}
