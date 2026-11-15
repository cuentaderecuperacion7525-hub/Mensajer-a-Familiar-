package com.example.data.local

import android.content.Context
import android.net.Uri
import com.example.data.model.CallRecordEntity
import com.example.data.model.ChatMessageEntity
import com.example.data.model.ChatThreadEntity
import com.example.data.model.FamilyEventEntity
import com.example.data.model.FamilyMemberEntity
import com.example.data.model.FamilyNoteEntity
import com.example.data.model.ShoppingItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class FamilyRepository(
    private val context: Context,
    private val db: FamilyDatabase,
    private val prefs: PreferencesManager
) {
    val members: Flow<List<FamilyMemberEntity>> = db.familyMemberDao().observeAll()
    val chats: Flow<List<ChatThreadEntity>> = db.chatThreadDao().observeAll()
    val allMessages: Flow<List<ChatMessageEntity>> = db.chatMessageDao().observeAll()
    val calls: Flow<List<CallRecordEntity>> = db.callRecordDao().observeAll()
    val shoppingItems: Flow<List<ShoppingItemEntity>> = db.shoppingItemDao().observeAll()
    val events: Flow<List<FamilyEventEntity>> = db.familyEventDao().observeAll()
    val notes: Flow<List<FamilyNoteEntity>> = db.familyNoteDao().observeAll()
    val masterPin: Flow<String> = prefs.masterPinFlow

    suspend fun ensureSeeded() {
        SeedData.populateIfEmpty(db)
    }

    suspend fun updateMasterPin(newPin: String) {
        prefs.updateMasterPin(newPin)
    }

    // Members
    suspend fun upsertMember(member: FamilyMemberEntity) {
        db.familyMemberDao().upsert(member)
    }

    suspend fun deleteMember(memberId: String) {
        db.familyMemberDao().deleteById(memberId)
    }

    // Chats
    suspend fun upsertChat(chat: ChatThreadEntity) {
        db.chatThreadDao().upsert(chat)
    }

    suspend fun deleteChat(chatId: String) {
        db.chatMessageDao().deleteAllForChat(chatId)
        db.chatThreadDao().deleteById(chatId)
    }

    suspend fun updatePinnedMessage(chatId: String, messageId: String?) {
        db.chatThreadDao().updatePinnedMessage(chatId, messageId)
    }

    suspend fun getChatById(chatId: String): ChatThreadEntity? = db.chatThreadDao().getById(chatId)

    // Messages
    fun observeMessages(chatId: String): Flow<List<ChatMessageEntity>> =
        db.chatMessageDao().observeForChat(chatId)

    suspend fun getMessagesOnce(chatId: String): List<ChatMessageEntity> =
        db.chatMessageDao().getForChatOnce(chatId)

    suspend fun getMessageById(messageId: String): ChatMessageEntity? =
        db.chatMessageDao().getById(messageId)

    suspend fun upsertMessage(message: ChatMessageEntity) {
        db.chatMessageDao().upsert(message)
        db.chatThreadDao().updateTimestamp(message.chatId, message.timestamp)
    }

    suspend fun updateMessageOnly(message: ChatMessageEntity) {
        db.chatMessageDao().update(message)
    }

    // Calls
    suspend fun insertCall(call: CallRecordEntity) {
        db.callRecordDao().insert(call)
    }

    suspend fun deleteCall(callId: String) {
        db.callRecordDao().deleteById(callId)
    }

    // Board
    suspend fun upsertShoppingItem(item: ShoppingItemEntity) {
        db.shoppingItemDao().upsert(item)
    }

    suspend fun deleteShoppingItem(id: String) {
        db.shoppingItemDao().deleteById(id)
    }

    suspend fun clearCheckedShoppingItems() {
        db.shoppingItemDao().deleteChecked()
    }

    suspend fun upsertEvent(event: FamilyEventEntity) {
        db.familyEventDao().upsert(event)
    }

    suspend fun deleteEvent(id: String) {
        db.familyEventDao().deleteById(id)
    }

    suspend fun upsertNote(note: FamilyNoteEntity) {
        db.familyNoteDao().upsert(note)
    }

    suspend fun deleteNote(id: String) {
        db.familyNoteDao().deleteById(id)
    }

    suspend fun persistPickedImage(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val mediaDir = File(context.filesDir, "chat_media").apply { mkdirs() }
            val outFile = File(mediaDir, "img_${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null
            Uri.fromFile(outFile).toString()
        } catch (_: Exception) {
            null
        }
    }
}
