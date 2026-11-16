package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.data.model.CallRecordEntity
import com.example.data.model.CallSignalStatus
import com.example.data.model.ChatMessageEntity
import com.example.data.model.ChatThreadEntity
import com.example.data.model.ChatType
import com.example.data.model.FamilyEventEntity
import com.example.data.model.FamilyMemberEntity
import com.example.data.model.FamilyNoteEntity
import com.example.data.model.FamilySpace
import com.example.data.model.JsonHelpers
import com.example.data.model.ShoppingItemEntity
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FamilyCloudRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun familyRef(familyId: String) = db.collection("families").document(familyId)

    // --- Real-Time Observation Streams ---

    fun observeMyFamilies(uid: String): Flow<List<FamilySpace>> =
        db.collection("families")
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { snap -> snap.documents.mapNotNull(FirestoreMappers::toFamilySpace) }

    fun observeFamily(familyId: String): Flow<FamilySpace?> =
        familyRef(familyId)
            .snapshots()
            .map { FirestoreMappers.toFamilySpace(it) }

    fun observeMembers(familyId: String, uid: String): Flow<List<FamilyMemberEntity>> =
        familyRef(familyId).collection("members")
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull(FirestoreMappers::toMember).sortedBy { it.createdAt }
            }

    fun observeChats(familyId: String, uid: String): Flow<List<ChatThreadEntity>> =
        familyRef(familyId).collection("chats")
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull(FirestoreMappers::toChatThread).sortedByDescending { it.updatedAt }
            }

    fun observeMessages(familyId: String, uid: String): Flow<List<ChatMessageEntity>> =
        familyRef(familyId).collection("messages")
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull(FirestoreMappers::toChatMessage).sortedBy { it.timestamp }
            }

    fun observeCalls(familyId: String, uid: String): Flow<List<CallRecordEntity>> =
        familyRef(familyId).collection("calls")
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull(FirestoreMappers::toCallRecord).sortedByDescending { it.timestamp }
            }

    fun observeShoppingItems(familyId: String, uid: String): Flow<List<ShoppingItemEntity>> =
        familyRef(familyId).collection("shoppingItems")
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull(FirestoreMappers::toShoppingItem)
                    .sortedWith(compareBy<ShoppingItemEntity> { it.isChecked }.thenByDescending { it.createdAt })
            }

    fun observeEvents(familyId: String, uid: String): Flow<List<FamilyEventEntity>> =
        familyRef(familyId).collection("events")
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull(FirestoreMappers::toFamilyEvent).sortedByDescending { it.createdAt }
            }

    fun observeNotes(familyId: String, uid: String): Flow<List<FamilyNoteEntity>> =
        familyRef(familyId).collection("notes")
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull(FirestoreMappers::toFamilyNote)
                    .sortedWith(compareByDescending<FamilyNoteEntity> { it.isPinned }.thenByDescending { it.createdAt })
            }

    // --- Family Lifecycle (Create / Join / Backfill) ---

    suspend fun createFamily(
        uid: String,
        familyName: String,
        adminName: String,
        adminEmoji: String = "👨🏽",
        adminColorHex: String = "#1A73E8",
        defaultMasterPin: String = "752581",
        defaultSwitchPin: String = "258046"
    ): Result<Pair<String, String>> {
        val cleanFamName = familyName.trim().take(50).ifEmpty { "Mi Familia" }
        val cleanAdminName = adminName.trim().take(40).ifEmpty { "Papá" }
        val familyId = "fam_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val inviteCode = generateInviteCode()
        val adminMemberId = "member_${UUID.randomUUID().toString().replace("-", "").take(8)}"
        val mainChatId = "chat_familia_${UUID.randomUUID().toString().replace("-", "").take(6)}"
        val memberUids = listOf(uid)
        val now = System.currentTimeMillis()

        val batch = db.batch()
        val fRef = familyRef(familyId)
        val invRef = db.collection("familyInvites").document(inviteCode)

        batch.set(
            fRef,
            mapOf(
                "ownerUid" to uid,
                "name" to cleanFamName,
                "inviteCode" to inviteCode,
                "memberUids" to memberUids,
                "masterPin" to defaultMasterPin,
                "switchPin" to defaultSwitchPin,
                "updateRepoSlug" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.set(
            invRef,
            mapOf(
                "inviteCode" to inviteCode,
                "familyId" to familyId,
                "familyName" to cleanFamName,
                "ownerUid" to uid,
                "active" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        return try {
            batch.commit().await()

            val adminMember = FamilyMemberEntity(
                id = adminMemberId,
                name = cleanAdminName,
                roleLabel = "Administrador",
                isAdmin = true,
                avatarColorHex = adminColorHex,
                avatarEmoji = adminEmoji,
                isOnline = true,
                lastSeenText = "En línea",
                createdAt = now,
                linkedAuthUid = uid,
                authorUid = uid
            )
            fRef.collection("members").document(adminMemberId)
                .set(FirestoreMappers.memberToMap(adminMember, memberUids, uid, isCreate = true))
                .await()

            val mainChat = ChatThreadEntity(
                id = mainChatId,
                type = ChatType.GROUP,
                title = cleanFamName,
                avatarColorHex = "#1A73E8",
                avatarEmoji = "🏡",
                participantIdsJson = JsonHelpers.encodeStringList(listOf(adminMemberId)),
                updatedAt = now,
                authorUid = uid
            )
            fRef.collection("chats").document(mainChatId)
                .set(FirestoreMappers.chatToMap(mainChat, memberUids, uid, isCreate = true))
                .await()

            fRef.collection("meta").document("bootstrap").set(
                mapOf(
                    "ownerUid" to uid,
                    "memberIds" to listOf(adminMemberId),
                    "chatIds" to listOf(mainChatId),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(familyId to adminMemberId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, fRef.path)
            Result.failure(e)
        }
    }

    suspend fun joinFamilyByInviteCode(uid: String, rawCode: String): Result<String> {
        val code = rawCode.trim().uppercase()
        if (code.length < 6 || !code.all { it.isLetterOrDigit() }) {
            return Result.failure(IllegalArgumentException("Ingresa un código familiar válido (6 caracteres)."))
        }
        val invRef = db.collection("familyInvites").document(code)
        return try {
            val invSnap = invRef.get().await()
            if (!invSnap.exists() || invSnap.getBoolean("active") != true) {
                return Result.failure(IllegalArgumentException("Código familiar no encontrado o inactivo."))
            }
            val familyId = invSnap.getString("familyId")
                ?: return Result.failure(IllegalStateException("Invitación inválida."))
            val fRef = familyRef(familyId)

            fRef.update(
                mapOf(
                    "memberUids" to FieldValue.arrayUnion(uid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            // Immediately sync joining UID onto existing members & chats listed in bootstrap
            val bootstrap = fRef.collection("meta").document("bootstrap").get().await()
            val memberIds = (bootstrap.get("memberIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val chatIds = (bootstrap.get("chatIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

            memberIds.forEach { mId ->
                runCatching {
                    fRef.collection("members").document(mId).update(
                        mapOf(
                            "memberUids" to FieldValue.arrayUnion(uid),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            chatIds.forEach { cId ->
                runCatching {
                    fRef.collection("chats").document(cId).update(
                        mapOf(
                            "memberUids" to FieldValue.arrayUnion(uid),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            Result.success(familyId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, invRef.path)
            Result.failure(e)
        }
    }

    suspend fun syncMemberUidsAcrossExistingDocs(familyId: String, allFamilyUids: List<String>, myUid: String) {
        if (allFamilyUids.isEmpty()) return
        val fRef = familyRef(familyId)
        val collections = listOf("members", "chats", "messages", "calls", "shoppingItems", "events", "notes")
        collections.forEach { colName ->
            try {
                val docs = fRef.collection(colName).whereArrayContains("memberUids", myUid).get().await()
                for (doc: DocumentSnapshot in docs.documents) {
                    val existingUids = (doc.get("memberUids") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                    val missing = allFamilyUids.filter { it !in existingUids }
                    if (missing.isNotEmpty()) {
                        doc.reference.update(
                            mapOf(
                                "memberUids" to (existingUids + missing).distinct().take(25),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    // --- Family Settings ---

    suspend fun updateFamilySettings(
        familyId: String,
        fields: Map<String, Any>
    ): Result<Unit> {
        val docRef = familyRef(familyId)
        return try {
            val payload = fields.toMutableMap().apply {
                this["updatedAt"] = FieldValue.serverTimestamp()
            }
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    // --- Members ---

    suspend fun upsertMember(
        familyId: String,
        member: FamilyMemberEntity,
        memberUids: List<String>,
        callerUid: String,
        isCreate: Boolean
    ): Result<Unit> {
        val docRef = familyRef(familyId).collection("members").document(member.id)
        return try {
            if (isCreate) {
                docRef.set(FirestoreMappers.memberToMap(member, memberUids, callerUid, true)).await()
                runCatching {
                    familyRef(familyId).collection("meta").document("bootstrap").update(
                        mapOf(
                            "memberIds" to FieldValue.arrayUnion(member.id),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            } else {
                val updates = FirestoreMappers.memberToMap(member, memberUids, member.authorUid.ifBlank { callerUid }, false)
                    .filterKeys { it != "authorUid" && it != "createdAtMs" }
                docRef.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteMember(familyId: String, memberId: String): Result<Unit> {
        val docRef = familyRef(familyId).collection("members").document(memberId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    // --- Chats ---

    suspend fun upsertChat(
        familyId: String,
        chat: ChatThreadEntity,
        memberUids: List<String>,
        callerUid: String,
        isCreate: Boolean
    ): Result<Unit> {
        val docRef = familyRef(familyId).collection("chats").document(chat.id)
        return try {
            if (isCreate) {
                docRef.set(FirestoreMappers.chatToMap(chat, memberUids, callerUid, true)).await()
                runCatching {
                    familyRef(familyId).collection("meta").document("bootstrap").update(
                        mapOf(
                            "chatIds" to FieldValue.arrayUnion(chat.id),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            } else {
                val updates = FirestoreMappers.chatToMap(chat, memberUids, chat.authorUid.ifBlank { callerUid }, false)
                    .filterKeys { it != "authorUid" && it != "type" }
                docRef.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteChat(familyId: String, chatId: String): Result<Unit> {
        val docRef = familyRef(familyId).collection("chats").document(chatId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updatePinnedMessage(familyId: String, chatId: String, messageId: String?) {
        val docRef = familyRef(familyId).collection("chats").document(chatId)
        try {
            val valOrDelete: Any = messageId ?: FieldValue.delete()
            docRef.update(
                mapOf(
                    "pinnedMessageId" to valOrDelete,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
        }
    }

    // --- Messages ---

    suspend fun createMessage(
        familyId: String,
        message: ChatMessageEntity,
        memberUids: List<String>,
        callerUid: String
    ): Result<Unit> {
        val docRef = familyRef(familyId).collection("messages").document(message.id)
        return try {
            docRef.set(FirestoreMappers.messageToMap(message, memberUids, callerUid, true)).await()
            runCatching {
                familyRef(familyId).collection("chats").document(message.chatId).update(
                    mapOf(
                        "lastMessageAt" to message.timestamp,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateMessageFields(
        familyId: String,
        messageId: String,
        fields: Map<String, Any>
    ): Result<Unit> {
        val docRef = familyRef(familyId).collection("messages").document(messageId)
        return try {
            val payload = fields.toMutableMap().apply {
                this["updatedAt"] = FieldValue.serverTimestamp()
            }
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    // --- Calls ---

    suspend fun createCall(
        familyId: String,
        call: CallRecordEntity,
        memberUids: List<String>,
        callerUid: String
    ): Result<Unit> {
        val docRef = familyRef(familyId).collection("calls").document(call.id)
        return try {
            docRef.set(FirestoreMappers.callToMap(call, memberUids, callerUid, true)).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateCallStatus(
        familyId: String,
        callId: String,
        signalStatus: CallSignalStatus,
        durationSec: Int? = null,
        connectedAt: Long? = null,
        directionName: String? = null
    ): Result<Unit> {
        val docRef = familyRef(familyId).collection("calls").document(callId)
        return try {
            val payload = mutableMapOf<String, Any>(
                "callStatus" to signalStatus.name,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (durationSec != null) payload["durationSec"] = durationSec
            if (connectedAt != null) payload["connectedAt"] = connectedAt
            if (directionName != null) payload["direction"] = directionName
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteCall(familyId: String, callId: String): Result<Unit> {
        val docRef = familyRef(familyId).collection("calls").document(callId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    // --- Board (Shopping / Events / Notes) ---

    suspend fun upsertShoppingItem(
        familyId: String,
        item: ShoppingItemEntity,
        memberUids: List<String>,
        callerUid: String,
        isCreate: Boolean
    ): Result<Unit> {
        val docRef = familyRef(familyId).collection("shoppingItems").document(item.id)
        return try {
            if (isCreate) {
                docRef.set(
                    mapOf(
                        "memberUids" to memberUids,
                        "authorUid" to callerUid,
                        "name" to item.name.take(80),
                        "quantity" to item.quantity.take(40),
                        "category" to item.category.take(40),
                        "isChecked" to item.isChecked,
                        "addedById" to item.addedById,
                        "timestamp" to item.createdAt,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.update(
                    mapOf(
                        "isChecked" to item.isChecked,
                        "name" to item.name.take(80),
                        "quantity" to item.quantity.take(40),
                        "category" to item.category.take(40),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteShoppingItem(familyId: String, itemId: String) {
        val docRef = familyRef(familyId).collection("shoppingItems").document(itemId)
        try {
            docRef.delete().await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
        }
    }

    suspend fun createEvent(
        familyId: String,
        event: FamilyEventEntity,
        memberUids: List<String>,
        callerUid: String
    ): Result<Unit> {
        val docRef = familyRef(familyId).collection("events").document(event.id)
        return try {
            docRef.set(
                mapOf(
                    "memberUids" to memberUids,
                    "authorUid" to callerUid,
                    "title" to event.title.take(80),
                    "dateLabel" to event.dateLabel.take(40),
                    "timeLabel" to event.timeLabel.take(40),
                    "description" to event.description.take(300),
                    "assignedMemberId" to event.assignedMemberId,
                    "colorHex" to event.colorHex.take(16),
                    "timestamp" to event.createdAt,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteEvent(familyId: String, eventId: String) {
        val docRef = familyRef(familyId).collection("events").document(eventId)
        try {
            docRef.delete().await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
        }
    }

    suspend fun upsertNote(
        familyId: String,
        note: FamilyNoteEntity,
        memberUids: List<String>,
        callerUid: String,
        isCreate: Boolean
    ): Result<Unit> {
        val docRef = familyRef(familyId).collection("notes").document(note.id)
        return try {
            if (isCreate) {
                docRef.set(
                    mapOf(
                        "memberUids" to memberUids,
                        "authorUid" to callerUid,
                        "title" to note.title.take(80),
                        "content" to note.content.take(1000),
                        "authorId" to note.authorId,
                        "colorHex" to note.colorHex.take(16),
                        "isPinned" to note.isPinned,
                        "timestamp" to note.createdAt,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.update(
                    mapOf(
                        "title" to note.title.take(80),
                        "content" to note.content.take(1000),
                        "colorHex" to note.colorHex.take(16),
                        "isPinned" to note.isPinned,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteNote(familyId: String, noteId: String) {
        val docRef = familyRef(familyId).collection("notes").document(noteId)
        try {
            docRef.delete().await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
        }
    }

    private fun generateInviteCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { alphabet.random() }.joinToString("")
    }
}
