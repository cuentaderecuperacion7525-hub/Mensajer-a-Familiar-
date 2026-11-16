package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.data.model.CallDirection
import com.example.data.model.CallRecordEntity
import com.example.data.model.CallType
import com.example.data.model.ChatMessageEntity
import com.example.data.model.ChatThreadEntity
import com.example.data.model.ChatType
import com.example.data.model.FamilyEventEntity
import com.example.data.model.FamilyMemberEntity
import com.example.data.model.FamilyNoteEntity
import com.example.data.model.JsonHelpers
import com.example.data.model.MessageStatus
import com.example.data.model.MessageType
import com.example.data.model.PollOption
import com.example.data.model.ShoppingItemEntity

class RoomEnumConverters {
    @TypeConverter fun fromChatType(v: ChatType): String = v.name
    @TypeConverter fun toChatType(v: String): ChatType = ChatType.valueOf(v)

    @TypeConverter fun fromMessageType(v: MessageType): String = v.name
    @TypeConverter fun toMessageType(v: String): MessageType = MessageType.valueOf(v)

    @TypeConverter fun fromMessageStatus(v: MessageStatus): String = v.name
    @TypeConverter fun toMessageStatus(v: String): MessageStatus = MessageStatus.valueOf(v)

    @TypeConverter fun fromCallType(v: CallType): String = v.name
    @TypeConverter fun toCallType(v: String): CallType = CallType.valueOf(v)

    @TypeConverter fun fromCallDirection(v: CallDirection): String = v.name
    @TypeConverter fun toCallDirection(v: String): CallDirection = CallDirection.valueOf(v)
}

@Database(
    entities = [
        FamilyMemberEntity::class,
        ChatThreadEntity::class,
        ChatMessageEntity::class,
        CallRecordEntity::class,
        ShoppingItemEntity::class,
        FamilyEventEntity::class,
        FamilyNoteEntity::class,
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(RoomEnumConverters::class)
abstract class FamilyDatabase : RoomDatabase() {
    abstract fun familyMemberDao(): FamilyMemberDao
    abstract fun chatThreadDao(): ChatThreadDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun callRecordDao(): CallRecordDao
    abstract fun shoppingItemDao(): ShoppingItemDao
    abstract fun familyEventDao(): FamilyEventDao
    abstract fun familyNoteDao(): FamilyNoteDao

    companion object {
        @Volatile
        private var INSTANCE: FamilyDatabase? = null

        fun getInstance(context: Context): FamilyDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FamilyDatabase::class.java,
                    "nidofamiliar_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

object SeedData {
    suspend fun populateIfEmpty(db: FamilyDatabase) {
        if (db.familyMemberDao().getAllOnce().isNotEmpty()) return

        val now = System.currentTimeMillis()
        val min = 60_000L

        val papa = FamilyMemberEntity(
            id = "member_papa",
            name = "Papá",
            roleLabel = "Administrador",
            isAdmin = true,
            avatarColorHex = "#1A73E8",
            avatarEmoji = "👨🏽",
            isOnline = true,
            lastSeenText = "En línea",
            createdAt = now - 300 * min
        )
        val mama = FamilyMemberEntity(
            id = "member_mama",
            name = "Mamá",
            roleLabel = "Madre",
            isAdmin = false,
            avatarColorHex = "#DB2777",
            avatarEmoji = "👩🏽",
            isOnline = true,
            lastSeenText = "En línea",
            createdAt = now - 290 * min
        )
        val hijo = FamilyMemberEntity(
            id = "member_hijo",
            name = "Hijo",
            roleLabel = "Hijo",
            isAdmin = false,
            avatarColorHex = "#2563EB",
            avatarEmoji = "🧑🏽‍🎓",
            isOnline = false,
            lastSeenText = "Últ. vez 14:20",
            createdAt = now - 280 * min
        )
        db.familyMemberDao().upsertAll(listOf(papa, mama, hijo))

        val familyGroup = ChatThreadEntity(
            id = "chat_familia",
            type = ChatType.GROUP,
            title = "Familia Unida 🏡",
            avatarColorHex = "#1A73E8",
            avatarEmoji = "🏡",
            participantIdsJson = JsonHelpers.encodeStringList(listOf(papa.id, mama.id, hijo.id)),
            pinnedMessageId = "msg_g_1",
            updatedAt = now - 4 * min
        )
        val chatPapaMama = ChatThreadEntity(
            id = "chat_papa_mama",
            type = ChatType.DIRECT,
            title = "Directo",
            avatarColorHex = "#DB2777",
            avatarEmoji = "💬",
            participantIdsJson = JsonHelpers.encodeStringList(listOf(papa.id, mama.id)),
            pinnedMessageId = null,
            updatedAt = now - 15 * min
        )
        val chatPapaHijo = ChatThreadEntity(
            id = "chat_papa_hijo",
            type = ChatType.DIRECT,
            title = "Directo",
            avatarColorHex = "#2563EB",
            avatarEmoji = "💬",
            participantIdsJson = JsonHelpers.encodeStringList(listOf(papa.id, hijo.id)),
            pinnedMessageId = null,
            updatedAt = now - 45 * min
        )
        val chatMamaHijo = ChatThreadEntity(
            id = "chat_mama_hijo",
            type = ChatType.DIRECT,
            title = "Directo",
            avatarColorHex = "#7C3AED",
            avatarEmoji = "💬",
            participantIdsJson = JsonHelpers.encodeStringList(listOf(mama.id, hijo.id)),
            pinnedMessageId = null,
            updatedAt = now - 90 * min
        )
        db.chatThreadDao().upsertAll(listOf(familyGroup, chatPapaMama, chatPapaHijo, chatMamaHijo))

        val messages = listOf(
            ChatMessageEntity(
                id = "msg_g_1",
                chatId = familyGroup.id,
                senderId = papa.id,
                text = "*¡Bienvenidos al chat familiar!* Aquí coordinamos la semana, compras y salidas. ~Nada de olvidar las llaves~ _Cuiden el grupo._",
                type = MessageType.TEXT,
                status = MessageStatus.READ,
                timestamp = now - 120 * min,
                reactionsJson = JsonHelpers.encodeReactions(mapOf(mama.id to "❤️", hijo.id to "👍"))
            ),
            ChatMessageEntity(
                id = "msg_g_2",
                chatId = familyGroup.id,
                senderId = mama.id,
                text = "Les dejé la lista del súper actualizada en el *Tablón Familiar*.",
                type = MessageType.TEXT,
                status = MessageStatus.READ,
                timestamp = now - 85 * min,
                replyToMessageId = "msg_g_1"
            ),
            ChatMessageEntity(
                id = "msg_g_3",
                chatId = familyGroup.id,
                senderId = hijo.id,
                text = "",
                type = MessageType.VOICE,
                status = MessageStatus.READ,
                timestamp = now - 40 * min,
                voiceDurationSec = 14,
                voiceWaveformJson = JsonHelpers.encodeFloatList(
                    listOf(0.25f, 0.55f, 0.85f, 0.45f, 0.9f, 0.7f, 0.35f, 0.8f, 0.65f, 0.4f, 0.75f, 0.5f, 0.3f, 0.6f)
                ),
                reactionsJson = JsonHelpers.encodeReactions(mapOf(papa.id to "😂"))
            ),
            ChatMessageEntity(
                id = "msg_g_4",
                chatId = familyGroup.id,
                senderId = papa.id,
                text = "",
                type = MessageType.POLL,
                status = MessageStatus.DELIVERED,
                timestamp = now - 12 * min,
                pollQuestion = "¿Qué cenamos este viernes en familia?",
                pollOptionsJson = JsonHelpers.encodePollOptions(
                    listOf(
                        PollOption("opt_1", "Tacos al pastor 🌮", listOf(papa.id, hijo.id)),
                        PollOption("opt_2", "Pizza casera 🍕", listOf(mama.id)),
                        PollOption("opt_3", "Pasta al pesto 🍝", emptyList())
                    )
                )
            ),
            ChatMessageEntity(
                id = "msg_g_5",
                chatId = familyGroup.id,
                senderId = mama.id,
                text = "Yo paso por el parque a las *18:30*, ¿alguien necesita algo de camino?",
                type = MessageType.TEXT,
                status = MessageStatus.DELIVERED,
                timestamp = now - 4 * min
            ),
            // Papá <-> Mamá
            ChatMessageEntity(
                id = "msg_pm_1",
                chatId = chatPapaMama.id,
                senderId = mama.id,
                text = "Amor, ¿llevamos el coche al taller mañana temprano?",
                type = MessageType.TEXT,
                status = MessageStatus.READ,
                timestamp = now - 30 * min
            ),
            ChatMessageEntity(
                id = "msg_pm_2",
                chatId = chatPapaMama.id,
                senderId = papa.id,
                text = "Sí, a las *08:30 AM*. Te comparto la ubicación exacta del taller.",
                type = MessageType.TEXT,
                status = MessageStatus.READ,
                timestamp = now - 18 * min
            ),
            ChatMessageEntity(
                id = "msg_pm_3",
                chatId = chatPapaMama.id,
                senderId = papa.id,
                text = "",
                type = MessageType.LOCATION,
                status = MessageStatus.DELIVERED,
                timestamp = now - 15 * min,
                locationName = "Taller Central Esmeralda",
                locationAddress = "Av. Los Robles 412, Col. Jardines"
            ),
            // Papá <-> Hijo
            ChatMessageEntity(
                id = "msg_ph_1",
                chatId = chatPapaHijo.id,
                senderId = hijo.id,
                text = "Pa, ya terminé la tarea de matemáticas ✅",
                type = MessageType.TEXT,
                status = MessageStatus.READ,
                timestamp = now - 50 * min,
                reactionsJson = JsonHelpers.encodeReactions(mapOf(papa.id to "🎉"))
            ),
            ChatMessageEntity(
                id = "msg_ph_2",
                chatId = chatPapaHijo.id,
                senderId = papa.id,
                text = "¡Excelente campeón! Cuando llegue revisamos el proyecto de ciencias.",
                type = MessageType.TEXT,
                status = MessageStatus.DELIVERED,
                timestamp = now - 45 * min
            ),
            // Mamá <-> Hijo
            ChatMessageEntity(
                id = "msg_mh_1",
                chatId = chatMamaHijo.id,
                senderId = mama.id,
                text = "No olvides meter tu suéter en la mochila antes de salir.",
                type = MessageType.TEXT,
                status = MessageStatus.READ,
                timestamp = now - 90 * min
            )
        )
        db.chatMessageDao().upsertAll(messages)

        val calls = listOf(
            CallRecordEntity(
                id = "call_1",
                chatId = chatPapaMama.id,
                title = "Mamá",
                callerId = papa.id,
                avatarColorHex = mama.avatarColorHex,
                avatarEmoji = mama.avatarEmoji,
                callType = CallType.VIDEO,
                direction = CallDirection.OUTGOING,
                durationSec = 245,
                timestamp = now - 180 * min
            ),
            CallRecordEntity(
                id = "call_2",
                chatId = familyGroup.id,
                title = "Familia Unida 🏡",
                callerId = mama.id,
                avatarColorHex = familyGroup.avatarColorHex,
                avatarEmoji = familyGroup.avatarEmoji,
                callType = CallType.VOICE,
                direction = CallDirection.INCOMING,
                durationSec = 412,
                timestamp = now - 600 * min
            ),
            CallRecordEntity(
                id = "call_3",
                chatId = chatPapaHijo.id,
                title = "Hijo",
                callerId = hijo.id,
                avatarColorHex = hijo.avatarColorHex,
                avatarEmoji = hijo.avatarEmoji,
                callType = CallType.VOICE,
                direction = CallDirection.MISSED,
                durationSec = 0,
                timestamp = now - 1200 * min
            )
        )
        db.callRecordDao().insertAll(calls)

        val shopping = listOf(
            ShoppingItemEntity(
                id = "shop_1",
                name = "Leche deslactosada",
                quantity = "2 litros",
                category = "Lácteos",
                isChecked = false,
                addedById = mama.id,
                createdAt = now - 100 * min
            ),
            ShoppingItemEntity(
                id = "shop_2",
                name = "Aguacates maduros",
                quantity = "4 piezas",
                category = "Frutas y Verduras",
                isChecked = false,
                addedById = papa.id,
                createdAt = now - 95 * min
            ),
            ShoppingItemEntity(
                id = "shop_3",
                name = "Pan integral",
                quantity = "1 paquete",
                category = "Panadería",
                isChecked = true,
                addedById = hijo.id,
                createdAt = now - 140 * min
            )
        )
        db.shoppingItemDao().upsertAll(shopping)

        val events = listOf(
            FamilyEventEntity(
                id = "evt_1",
                title = "Partido de fútbol",
                dateLabel = "Sábado 16 Nov",
                timeLabel = "10:00 AM",
                description = "Cancha Municipal Norte — llevar hidratación",
                assignedMemberId = hijo.id,
                colorHex = "#2563EB",
                createdAt = now - 200 * min
            ),
            FamilyEventEntity(
                id = "evt_2",
                title = "Cena con los abuelos",
                dateLabel = "Domingo 17 Nov",
                timeLabel = "19:30 PM",
                description = "En casa de los abuelos, llevamos el postre",
                assignedMemberId = mama.id,
                colorHex = "#1A73E8",
                createdAt = now - 150 * min
            )
        )
        db.familyEventDao().upsertAll(events)

        val notes = listOf(
            FamilyNoteEntity(
                id = "note_1",
                title = "Clave del Wi-Fi de casa",
                content = "Red: Nido_5G  |  Clave: Familia2026!",
                authorId = papa.id,
                colorHex = "#FFF4CC",
                isPinned = true,
                createdAt = now - 500 * min
            ),
            FamilyNoteEntity(
                id = "note_2",
                title = "Horarios de recolección",
                content = "Recordatorio: sacar los envases reciclables los martes y jueves antes de las 20:00.",
                authorId = mama.id,
                colorHex = "#E8F1FD",
                isPinned = true,
                createdAt = now - 400 * min
            )
        )
        db.familyNoteDao().upsertAll(notes)
    }
}
