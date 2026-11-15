package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.CallRecordEntity
import com.example.data.model.ChatMessageEntity
import com.example.data.model.ChatThreadEntity
import com.example.data.model.FamilyEventEntity
import com.example.data.model.FamilyMemberEntity
import com.example.data.model.FamilyNoteEntity
import com.example.data.model.ShoppingItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FamilyMemberDao {
    @Query("SELECT * FROM family_members ORDER BY isAdmin DESC, createdAt ASC")
    fun observeAll(): Flow<List<FamilyMemberEntity>>

    @Query("SELECT * FROM family_members")
    suspend fun getAllOnce(): List<FamilyMemberEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(member: FamilyMemberEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(members: List<FamilyMemberEntity>)

    @Query("DELETE FROM family_members WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ChatThreadDao {
    @Query("SELECT * FROM chat_threads ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ChatThreadEntity>>

    @Query("SELECT * FROM chat_threads WHERE id = :chatId LIMIT 1")
    suspend fun getById(chatId: String): ChatThreadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(chat: ChatThreadEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(chats: List<ChatThreadEntity>)

    @Query("UPDATE chat_threads SET pinnedMessageId = :messageId WHERE id = :chatId")
    suspend fun updatePinnedMessage(chatId: String, messageId: String?)

    @Query("UPDATE chat_threads SET updatedAt = :updatedAt WHERE id = :chatId")
    suspend fun updateTimestamp(chatId: String, updatedAt: Long)

    @Query("DELETE FROM chat_threads WHERE id = :chatId")
    suspend fun deleteById(chatId: String)
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun observeAll(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun observeForChat(chatId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE id = :messageId LIMIT 1")
    suspend fun getById(messageId: String): ChatMessageEntity?

    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId")
    suspend fun getForChatOnce(chatId: String): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: ChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(messages: List<ChatMessageEntity>)

    @Update
    suspend fun update(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE chatId = :chatId")
    suspend fun deleteAllForChat(chatId: String)
}

@Dao
interface CallRecordDao {
    @Query("SELECT * FROM call_records ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<CallRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(call: CallRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(calls: List<CallRecordEntity>)

    @Query("DELETE FROM call_records WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ShoppingItemDao {
    @Query("SELECT * FROM shopping_items ORDER BY isChecked ASC, createdAt DESC")
    fun observeAll(): Flow<List<ShoppingItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ShoppingItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ShoppingItemEntity>)

    @Query("DELETE FROM shopping_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM shopping_items WHERE isChecked = 1")
    suspend fun deleteChecked()
}

@Dao
interface FamilyEventDao {
    @Query("SELECT * FROM family_events ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<FamilyEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(event: FamilyEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(events: List<FamilyEventEntity>)

    @Query("DELETE FROM family_events WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface FamilyNoteDao {
    @Query("SELECT * FROM family_notes ORDER BY isPinned DESC, createdAt DESC")
    fun observeAll(): Flow<List<FamilyNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: FamilyNoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(notes: List<FamilyNoteEntity>)

    @Query("DELETE FROM family_notes WHERE id = :id")
    suspend fun deleteById(id: String)
}
