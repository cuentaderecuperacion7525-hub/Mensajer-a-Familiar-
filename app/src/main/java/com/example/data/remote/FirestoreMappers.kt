package com.example.data.remote

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
            masterPin = doc.getString("masterPin") ?: "7525",
            switchPin = doc.getString("switchPin") ?: "2580",
            updateRepoSlug = doc.getString("updateRepoSlug") ?: ""
        )
    }

    fun toMember(doc: DocumentSnapshot): FamilyMemberEntity? {
        if (!doc.exists()) return null
        val liveStr = doc.getString("liveStatus") ?: "LAST_SEEN"
        val liveStatus = runCatching { PresenceStatus.valueOf(liveStr) }.getOrDefault(PresenceStatus.LAST_SEEN)
        val fcmTokens = (doc.get("fcmTokens") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        val serverActiveMs = doc.getTimestamp("lastActiveAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?.toDate()?.time ?: 0L
        val clientLiveAt = doc.getLong("liveStatusAt") ?: 0L
        val effectiveLiveStatusAt = if (serverActiveMs > 0L) serverActiveMs else clientLiveAt
        return FamilyMemberEntity(
            id = doc.id,
            name = doc.getString("name") ?: return null,
            roleLabel = doc.getString("roleLabel") ?: "Familiar",
            isAdmin = doc.getBoolean("isAdmin") ?: false,
            avatarColorHex = doc.getString("avatarColorHex") ?: "#1A73E8",
            avatarEmoji = doc.getString("avatarEmoji") ?: "👤",
            avatarPhotoData = doc.getString("avatarPhotoData") ?: "",
            bioText = doc.getString("bioText") ?: "",
            isOnline = doc.getBoolean("isOnline") ?: false,
            showLastSeen = doc.getBoolean("showLastSeen") ?: true,
            lastSeenText = doc.getString("lastSeenText") ?: "",
            createdAt = doc.getLong("createdAtMs") ?: 0L,
            phoneNumber = doc.getString("phoneNumber") ?: "",
            liveStatus = liveStatus,
            liveChatId = doc.getString("liveChatId"),
            liveStatusAt = effectiveLiveStatusAt,
            linkedAuthUid = doc.getString("linkedAuthUid") ?: "",
            fcmTokens = fcmTokens,
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
            "avatarPhotoData" to member.avatarPhotoData.take(750_000),
            "bioText" to member.bioText.take(120),
            "phoneNumber" to member.phoneNumber.take(30),
            "isOnline" to member.isOnline,
            "showLastSeen" to member.showLastSeen,
            "lastSeenText" to member.lastSeenText.take(80),
            "liveStatus" to member.liveStatus.name,
            "liveChatId" to member.liveChatId?.take(128),
            "liveStatusAt" to member.liveStatusAt,
            "linkedAuthUid" to member.linkedAuthUid.take(128),
            "createdAtMs" to member.createdAt,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (isCreate) {
            map["fcmTokens"] = member.fcmTokens.take(10)
            map["createdAt"] = FieldValue.serverTimestamp()
        }
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
        val readByIds = (doc.get("readByMemberIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        val playedByIds = (doc.get("playedByMemberIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
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
            readByMemberIdsJson = JsonHelpers.encodeStringList(readByIds),
            playedByMemberIdsJson = JsonHelpers.encodeStringList(playedByIds),
            forwardedFromName = doc.getString("forwardedFromName"),
            mediaUri = doc.getString("mediaData"),
            videoThumbnailData = doc.getString("videoThumbnailData"),
            fileName = doc.getString("fileName"),
            fileMimeType = doc.getString("fileMimeType"),
            fileSizeBytes = doc.getLong("fileSizeBytes") ?: 0L,
            chunkCount = (doc.getLong("chunkCount") ?: 0L).toInt(),
            voiceDurationSec = (doc.getLong("voiceDurationSec") ?: 0L).toInt(),
            voiceWaveformJson = JsonHelpers.encodeFloatList(waveform),
            locationName = doc.getString("locationName"),
            locationAddress = doc.getString("locationAddress"),
            locationLat = doc.getDouble("locationLat"),
            locationLng = doc.getDouble("locationLng"),
            locationLiveUntil = doc.getLong("locationLiveUntil"),
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
        val readBy = JsonHelpers.decodeStringList(msg.readByMemberIdsJson).take(25)
        val playedBy = JsonHelpers.decodeStringList(msg.playedByMemberIdsJson).take(25)
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
            "readByMemberIds" to readBy,
            "playedByMemberIds" to playedBy,
            "forwardedFromName" to msg.forwardedFromName?.take(60),
            "mediaData" to msg.mediaUri?.take(750_000),
            "videoThumbnailData" to msg.videoThumbnailData?.take(150_000),
            "fileName" to msg.fileName?.take(160),
            "fileMimeType" to msg.fileMimeType?.take(120),
            "fileSizeBytes" to if (msg.fileSizeBytes > 0L) msg.fileSizeBytes else null,
            "chunkCount" to if (msg.chunkCount > 0) msg.chunkCount else null,
            "voiceDurationSec" to msg.voiceDurationSec,
            "voiceWaveform" to waveform,
            "locationName" to msg.locationName?.take(120),
            "locationAddress" to msg.locationAddress?.take(240),
            "locationLat" to msg.locationLat,
            "locationLng" to msg.locationLng,
            "locationLiveUntil" to msg.locationLiveUntil,
            "pollQuestion" to msg.pollQuestion?.take(200),
            "pollOptions" to pollOpts,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (isCreate) map["createdAt"] = FieldValue.serverTimestamp()
        @Suppress("UNCHECKED_CAST")
        return map.filterValues { it != null } as Map<String, Any>
    }

    fun toMediaChunk(doc: DocumentSnapshot): MediaChunkEntity? {
        if (!doc.exists()) return null
        val data = doc.getString("data") ?: return null
        return MediaChunkEntity(
            id = doc.id,
            messageId = doc.getString("messageId") ?: return null,
            chunkIndex = (doc.getLong("chunkIndex") ?: 0L).toInt(),
            totalChunks = (doc.getLong("totalChunks") ?: 1L).toInt(),
            data = data,
            authorUid = doc.getString("authorUid") ?: ""
        )
    }

    fun mediaChunkToMap(
        chunk: MediaChunkEntity,
        memberUids: List<String>,
        authorUid: String
    ): Map<String, Any> {
        return mapOf(
            "memberUids" to memberUids,
            "authorUid" to authorUid,
            "messageId" to chunk.messageId,
            "chunkIndex" to chunk.chunkIndex.coerceIn(0, 25),
            "totalChunks" to chunk.totalChunks.coerceIn(1, 25),
            "data" to chunk.data.take(700_000),
            "createdAt" to FieldValue.serverTimestamp()
        )
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
            offerSdp = doc.getString("offerSdp"),
            answerSdp = doc.getString("answerSdp"),
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
            "offerSdp" to call.offerSdp?.take(32_000),
            "answerSdp" to call.answerSdp?.take(32_000),
            "timestamp" to call.timestamp,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (isCreate) map["createdAt"] = FieldValue.serverTimestamp()
        @Suppress("UNCHECKED_CAST")
        return map.filterValues { it != null } as Map<String, Any>
    }

    fun toIceCandidate(doc: DocumentSnapshot): CallIceCandidateEntity? {
        if (!doc.exists()) return null
        val candidate = doc.getString("candidate") ?: return null
        return CallIceCandidateEntity(
            id = doc.id,
            senderMemberId = doc.getString("senderMemberId") ?: "",
            sdpMid = doc.getString("sdpMid") ?: "",
            sdpMLineIndex = (doc.getLong("sdpMLineIndex") ?: 0L).toInt(),
            candidate = candidate,
            createdAtMs = doc.getLong("createdAtMs") ?: 0L,
            authorUid = doc.getString("authorUid") ?: ""
        )
    }

    fun iceCandidateToMap(
        cand: CallIceCandidateEntity,
        memberUids: List<String>,
        authorUid: String
    ): Map<String, Any> {
        return mapOf(
            "memberUids" to memberUids,
            "authorUid" to authorUid,
            "senderMemberId" to cand.senderMemberId,
            "sdpMid" to cand.sdpMid.take(64),
            "sdpMLineIndex" to cand.sdpMLineIndex.coerceIn(0, 32),
            "candidate" to cand.candidate.take(2048),
            "createdAtMs" to cand.createdAtMs,
            "createdAt" to FieldValue.serverTimestamp()
        )
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

    fun toFamilyStatus(doc: DocumentSnapshot): FamilyStatusEntity? {
        if (!doc.exists()) return null
        val viewed = (doc.get("viewedByMemberIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        val rawMediaData = doc.getString("mediaData")
        val rawThumbData = doc.getString("videoThumbnailData")
        val unpacked = com.example.util.StatusMusicCodec.unpackFullStatusFields(rawMediaData, rawThumbData)
        val mediaData = unpacked.mediaData
        val cleanThumbData = unpacked.videoThumbnailData
        val musicTrack = unpacked.musicTrack
        val overlayBundle = unpacked.overlayBundle
        val rawType = doc.getString("mediaType")
        val resolvedType = when {
            !rawType.isNullOrBlank() -> rawType
            mediaData?.startsWith("data:video") == true || mediaData?.startsWith("chunked:video") == true -> "VIDEO"
            !mediaData.isNullOrBlank() -> "IMAGE"
            else -> "TEXT"
        }
        return FamilyStatusEntity(
            id = doc.id,
            memberId = doc.getString("memberId") ?: return null,
            text = doc.getString("text") ?: "",
            mediaUri = mediaData,
            mediaType = resolvedType,
            videoThumbnailData = cleanThumbData,
            videoDurationSec = (doc.getLong("videoDurationSec") ?: 0L).toInt(),
            chunkCount = (doc.getLong("chunkCount") ?: 0L).toInt(),
            backgroundHex = doc.getString("backgroundHex") ?: "#128C7E",
            createdAt = doc.getLong("timestamp") ?: 0L,
            expiresAt = doc.getLong("expiresAt") ?: 0L,
            viewedByMemberIdsJson = JsonHelpers.encodeStringList(viewed),
            authorUid = doc.getString("authorUid") ?: "",
            musicTrack = musicTrack,
            overlayBundle = overlayBundle,
            visibleToMemberIds = overlayBundle?.audienceMemberIds
        )
    }

    fun statusToMap(
        status: FamilyStatusEntity,
        memberUids: List<String>,
        authorUid: String,
        isCreate: Boolean
    ): Map<String, Any> {
        val viewed = JsonHelpers.decodeStringList(status.viewedByMemberIdsJson).take(25)
        val effectiveBundle = when {
            status.overlayBundle != null -> status.overlayBundle.copy(
                audienceMemberIds = status.visibleToMemberIds ?: status.overlayBundle.audienceMemberIds
            )
            status.visibleToMemberIds != null -> com.example.util.StatusOverlayBundle(
                audienceMemberIds = status.visibleToMemberIds
            )
            else -> null
        }
        val (packedMediaData, packedThumbData) = com.example.util.StatusMusicCodec.packFullStatusFields(
            mediaType = status.mediaType,
            rawMediaData = status.mediaUri,
            rawVideoThumbnailData = status.videoThumbnailData,
            musicTrack = status.musicTrack,
            overlayBundle = effectiveBundle
        )
        val map = mutableMapOf<String, Any?>(
            "memberUids" to memberUids,
            "authorUid" to authorUid,
            "memberId" to status.memberId,
            "text" to status.text.take(400),
            "mediaData" to packedMediaData?.take(750_000),
            "mediaType" to status.mediaType.take(16).ifBlank { null },
            "videoThumbnailData" to packedThumbData?.take(150_000),
            "videoDurationSec" to if (status.videoDurationSec > 0) status.videoDurationSec else null,
            "chunkCount" to if (status.chunkCount > 0) status.chunkCount else null,
            "backgroundHex" to status.backgroundHex.take(16),
            "timestamp" to status.createdAt,
            "expiresAt" to status.expiresAt,
            "viewedByMemberIds" to viewed,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (isCreate) map["createdAt"] = FieldValue.serverTimestamp()
        @Suppress("UNCHECKED_CAST")
        return map.filterValues { it != null } as Map<String, Any>
    }
}
