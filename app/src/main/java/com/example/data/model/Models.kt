package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject

enum class ChatType { DIRECT, GROUP }

enum class MessageType { TEXT, IMAGE, VOICE, LOCATION, POLL, DELETED }

enum class MessageStatus { SENDING, SENT, DELIVERED, READ }

enum class CallType { VOICE, VIDEO }

enum class CallDirection { OUTGOING, INCOMING, MISSED }

enum class PresenceStatus { ONLINE, TYPING, RECORDING, LAST_SEEN }

data class PollOption(
    val id: String,
    val text: String,
    val voterIds: List<String> = emptyList()
)

@Entity(tableName = "family_members")
data class FamilyMemberEntity(
    @PrimaryKey val id: String,
    val name: String,
    val roleLabel: String,
    val isAdmin: Boolean,
    val avatarColorHex: String,
    val avatarEmoji: String,
    val isOnline: Boolean = true,
    val lastSeenText: String = "En línea",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_threads")
data class ChatThreadEntity(
    @PrimaryKey val id: String,
    val type: ChatType,
    val title: String,
    val avatarColorHex: String,
    val avatarEmoji: String,
    val participantIdsJson: String,
    val pinnedMessageId: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val senderId: String,
    val text: String,
    val type: MessageType = MessageType.TEXT,
    val status: MessageStatus = MessageStatus.SENT,
    val timestamp: Long = System.currentTimeMillis(),
    val replyToMessageId: String? = null,
    val isEdited: Boolean = false,
    val reactionsJson: String = "{}",
    val hiddenForMemberIdsJson: String = "[]",
    val mediaUri: String? = null,
    val voiceDurationSec: Int = 0,
    val voiceWaveformJson: String = "[]",
    val locationName: String? = null,
    val locationAddress: String? = null,
    val pollQuestion: String? = null,
    val pollOptionsJson: String = "[]"
)

@Entity(tableName = "call_records")
data class CallRecordEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val title: String,
    val callerId: String,
    val avatarColorHex: String,
    val avatarEmoji: String,
    val callType: CallType,
    val direction: CallDirection,
    val durationSec: Int,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "shopping_items")
data class ShoppingItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val quantity: String,
    val category: String,
    val isChecked: Boolean = false,
    val addedById: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "family_events")
data class FamilyEventEntity(
    @PrimaryKey val id: String,
    val title: String,
    val dateLabel: String,
    val timeLabel: String,
    val description: String,
    val assignedMemberId: String,
    val colorHex: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "family_notes")
data class FamilyNoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    val authorId: String,
    val colorHex: String,
    val isPinned: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

object JsonHelpers {
    fun encodeStringList(items: List<String>): String {
        val arr = JSONArray()
        items.forEach { arr.put(it) }
        return arr.toString()
    }

    fun decodeStringList(json: String): List<String> {
        return try {
            val arr = JSONArray(json)
            List(arr.length()) { idx -> arr.getString(idx) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun encodeFloatList(items: List<Float>): String {
        val arr = JSONArray()
        items.forEach { arr.put(it.toDouble()) }
        return arr.toString()
    }

    fun decodeFloatList(json: String): List<Float> {
        return try {
            val arr = JSONArray(json)
            List(arr.length()) { idx -> arr.getDouble(idx).toFloat() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun encodeReactions(map: Map<String, String>): String {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }

    fun decodeReactions(json: String): Map<String, String> {
        return try {
            val obj = JSONObject(json)
            val result = linkedMapOf<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                result[key] = obj.getString(key)
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun encodePollOptions(options: List<PollOption>): String {
        val arr = JSONArray()
        options.forEach { opt ->
            val obj = JSONObject().apply {
                put("id", opt.id)
                put("text", opt.text)
                put("voterIds", JSONArray(opt.voterIds))
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    fun decodePollOptions(json: String): List<PollOption> {
        return try {
            val arr = JSONArray(json)
            List(arr.length()) { idx ->
                val obj = arr.getJSONObject(idx)
                val votersArr = obj.optJSONArray("voterIds") ?: JSONArray()
                val voters = List(votersArr.length()) { vIdx -> votersArr.getString(vIdx) }
                PollOption(
                    id = obj.getString("id"),
                    text = obj.getString("text"),
                    voterIds = voters
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
