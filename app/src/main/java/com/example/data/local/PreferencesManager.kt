package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.FamilyMemberEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nidofamiliar_prefs")

enum class AppThemeMode(val key: String, val label: String) {
    LIGHT("light", "Claro"),
    DARK("dark", "Oscuro"),
    SYSTEM("system", "Automático");

    companion object {
        fun fromKey(raw: String?): AppThemeMode? = entries.find { it.key == raw }
    }
}

enum class AppFontScale(val key: String, val label: String, val multiplier: Float) {
    SMALL("small", "Pequeño", 0.90f),
    NORMAL("normal", "Normal", 1.00f),
    LARGE("large", "Grande", 1.15f);

    companion object {
        fun fromKey(raw: String?): AppFontScale = entries.find { it.key == raw } ?: NORMAL
        fun fromStep(step: Int): AppFontScale = when (step.coerceIn(0, 2)) {
            0 -> SMALL
            2 -> LARGE
            else -> NORMAL
        }
    }

    val stepIndex: Int
        get() = when (this) {
            SMALL -> 0
            NORMAL -> 1
            LARGE -> 2
        }
}

data class BootDisplayPrefs(
    val themeMode: AppThemeMode,
    val fontScale: AppFontScale,
    val fromFastCache: Boolean
)

data class InitialSessionSnapshot(
    val activeFamilyId: String?,
    val activeMemberId: String?,
    val cachedFamilyId: String?,
    val cachedMembers: List<FamilyMemberEntity>
)

class PreferencesManager(private val context: Context) {
    companion object {
        private const val BOOT_DISPLAY_PREFS_NAME = "famichat_boot_display_prefs"
        private const val BOOT_KEY_THEME_MODE = "theme_mode"
        private const val BOOT_KEY_FONT_SCALE = "font_scale"

        private const val BOOT_SESSION_PREFS_NAME = "famichat_boot_session_prefs"
        private const val BOOT_SESSION_KEY_INITIALIZED = "initialized"
        private const val BOOT_SESSION_KEY_FAMILY_ID = "active_family_id"
        private const val BOOT_SESSION_KEY_MEMBER_ID = "active_member_id"
        private const val BOOT_SESSION_KEY_CACHED_MEMBERS_JSON = "cached_members_json"

        private val KEY_MASTER_PIN = stringPreferencesKey("master_pin")
        private val KEY_SWITCH_PIN = stringPreferencesKey("switch_pin")
        private val KEY_ACTIVE_MEMBER_ID = stringPreferencesKey("active_member_id")
        private val KEY_ACTIVE_FAMILY_ID = stringPreferencesKey("active_family_id")
        private val KEY_IS_DARK_THEME = booleanPreferencesKey("is_dark_theme")
        private val KEY_UPDATE_REPO_SLUG = stringPreferencesKey("update_repo_slug")
        private val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        private val KEY_CHAT_WALLPAPERS_JSON = stringPreferencesKey("chat_wallpapers_json")
        private val KEY_FCM_TOKEN = stringPreferencesKey("fcm_token")
        private val KEY_LAST_NOTIFIED_MSG_TS = longPreferencesKey("last_notified_msg_ts")
        private val KEY_PINNED_CHAT_IDS_JSON = stringPreferencesKey("pinned_chat_ids_json")
        private val KEY_MUTED_CHAT_IDS_JSON = stringPreferencesKey("muted_chat_ids_json")
        private val KEY_CHAT_NOTIFICATION_TONES_JSON = stringPreferencesKey("chat_notification_tones_json")
        private val KEY_TURN_URL = stringPreferencesKey("turn_server_url")
        private val KEY_TURN_USERNAME = stringPreferencesKey("turn_server_username")
        private val KEY_TURN_CREDENTIAL = stringPreferencesKey("turn_server_credential")
        private val KEY_LAST_SYNCED_UIDS_HASH = stringPreferencesKey("last_synced_uids_hash")
        private val KEY_CHAT_CLEARED_AT_JSON = stringPreferencesKey("chat_cleared_at_json")
        private val KEY_LOCALLY_HIDDEN_MESSAGE_KEYS_JSON = stringPreferencesKey("locally_hidden_message_keys_json")
        private val KEY_CACHED_MEMBERS_JSON = stringPreferencesKey("cached_members_json")
        private val KEY_SHOW_LAST_SEEN_MAP_JSON = stringPreferencesKey("show_last_seen_map_json")
        private val KEY_LAST_UPDATE_CHECK_AT = longPreferencesKey("last_update_check_at")
        private val KEY_UPDATE_SNOOZED_UNTIL = longPreferencesKey("update_snoozed_until")
        private val KEY_VERSION_HISTORY_CACHE_JSON = stringPreferencesKey("version_history_cache_json")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_FONT_SCALE = stringPreferencesKey("font_scale")
        private val KEY_REALTIME_BACKGROUND_SERVICE = booleanPreferencesKey("realtime_background_service_enabled")
        const val DEFAULT_PIN = "7525"
        const val DEFAULT_SWITCH_PIN = "2580"
        const val DEFAULT_TURN_URL = "turn:openrelay.metered.ca:80"
        const val DEFAULT_TURN_USERNAME = "openrelayproject"
        const val DEFAULT_TURN_CREDENTIAL = "openrelayproject"

        fun chatClearedAtKey(memberId: String, chatId: String): String = "$memberId::$chatId"
        fun hiddenMessageKey(memberId: String, messageId: String): String = "$memberId::$messageId"
    }

    val masterPinFlow: Flow<String> = context.dataStore.data.map { prefs ->
        val saved = prefs[KEY_MASTER_PIN]
        if (saved != null && saved.length == 4 && saved.all(Char::isDigit)) saved else DEFAULT_PIN
    }

    val switchPinFlow: Flow<String> = context.dataStore.data.map { prefs ->
        val saved = prefs[KEY_SWITCH_PIN]
        if (saved != null && saved.length == 4 && saved.all(Char::isDigit)) saved else DEFAULT_SWITCH_PIN
    }

    val activeMemberIdFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_ACTIVE_MEMBER_ID]
    }

    val activeFamilyIdFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_ACTIVE_FAMILY_ID]
    }

    val isDarkThemeFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        when (AppThemeMode.fromKey(prefs[KEY_THEME_MODE])) {
            AppThemeMode.DARK -> true
            AppThemeMode.LIGHT -> false
            else -> prefs[KEY_IS_DARK_THEME] ?: false
        }
    }

    val themeModeFlow: Flow<AppThemeMode> = context.dataStore.data.map { prefs ->
        AppThemeMode.fromKey(prefs[KEY_THEME_MODE])
            ?: when (prefs[KEY_IS_DARK_THEME]) {
                true -> AppThemeMode.DARK
                false -> AppThemeMode.LIGHT
                null -> AppThemeMode.SYSTEM
            }
    }

    val fontScaleFlow: Flow<AppFontScale> = context.dataStore.data.map { prefs ->
        AppFontScale.fromKey(prefs[KEY_FONT_SCALE])
    }

    private fun bootDisplayPrefs() =
        context.applicationContext.getSharedPreferences(BOOT_DISPLAY_PREFS_NAME, Context.MODE_PRIVATE)

    private fun bootSessionPrefs() =
        context.applicationContext.getSharedPreferences(BOOT_SESSION_PREFS_NAME, Context.MODE_PRIVATE)

    private fun mirrorBootDisplayPrefs(
        themeMode: AppThemeMode? = null,
        fontScale: AppFontScale? = null
    ) {
        val editor = bootDisplayPrefs().edit()
        if (themeMode != null) editor.putString(BOOT_KEY_THEME_MODE, themeMode.key)
        if (fontScale != null) editor.putString(BOOT_KEY_FONT_SCALE, fontScale.key)
        editor.apply()
    }

    /**
     * Reads activeFamilyId, activeMemberId, and cached family members synchronously from a
     * lightweight SharedPreferences mirror so FamilyViewModel can open Chats on Frame 1 without
     * waiting for DataStore disk parsing. Returns null on upgrading devices until the mirror
     * has been populated once.
     */
    fun readInitialSessionSnapshotSync(): InitialSessionSnapshot? {
        val sp = bootSessionPrefs()
        if (!sp.getBoolean(BOOT_SESSION_KEY_INITIALIZED, false)) {
            return null
        }
        val cachedPair = decodeCachedMembers(sp.getString(BOOT_SESSION_KEY_CACHED_MEMBERS_JSON, null))
        return InitialSessionSnapshot(
            activeFamilyId = sp.getString(BOOT_SESSION_KEY_FAMILY_ID, null),
            activeMemberId = sp.getString(BOOT_SESSION_KEY_MEMBER_ID, null),
            cachedFamilyId = cachedPair?.first,
            cachedMembers = cachedPair?.second.orEmpty()
        )
    }

    fun mirrorSessionSnapshot(snapshot: InitialSessionSnapshot) {
        val editor = bootSessionPrefs().edit()
            .putBoolean(BOOT_SESSION_KEY_INITIALIZED, true)
        if (snapshot.activeFamilyId.isNullOrBlank()) {
            editor.remove(BOOT_SESSION_KEY_FAMILY_ID)
        } else {
            editor.putString(BOOT_SESSION_KEY_FAMILY_ID, snapshot.activeFamilyId)
        }
        if (snapshot.activeMemberId.isNullOrBlank()) {
            editor.remove(BOOT_SESSION_KEY_MEMBER_ID)
        } else {
            editor.putString(BOOT_SESSION_KEY_MEMBER_ID, snapshot.activeMemberId)
        }
        val famId = snapshot.cachedFamilyId
        if (!famId.isNullOrBlank() && snapshot.cachedMembers.isNotEmpty()) {
            editor.putString(
                BOOT_SESSION_KEY_CACHED_MEMBERS_JSON,
                encodeCachedMembers(famId, snapshot.cachedMembers)
            )
        } else {
            editor.remove(BOOT_SESSION_KEY_CACHED_MEMBERS_JSON)
        }
        editor.apply()
    }

    /**
     * Reads themeMode and fontScale synchronously before MainActivity draws its first frame.
     * Uses the lightweight SharedPreferences mirror when available, falling back once to
     * DataStore (and backfilling the mirror) on devices upgrading from older versions.
     */
    fun readBootDisplayPrefsSync(): BootDisplayPrefs {
        val sp = bootDisplayPrefs()
        val cachedTheme = AppThemeMode.fromKey(sp.getString(BOOT_KEY_THEME_MODE, null))
        val cachedScaleRaw = sp.getString(BOOT_KEY_FONT_SCALE, null)
        if (cachedTheme != null && cachedScaleRaw != null) {
            return BootDisplayPrefs(
                themeMode = cachedTheme,
                fontScale = AppFontScale.fromKey(cachedScaleRaw),
                fromFastCache = true
            )
        }
        val fallback = runCatching {
            runBlocking(Dispatchers.IO) {
                withTimeoutOrNull(500L) {
                    val prefs = context.dataStore.data.first()
                    val resolvedTheme = AppThemeMode.fromKey(prefs[KEY_THEME_MODE])
                        ?: when (prefs[KEY_IS_DARK_THEME]) {
                            true -> AppThemeMode.DARK
                            false -> AppThemeMode.LIGHT
                            null -> AppThemeMode.SYSTEM
                        }
                    val resolvedScale = AppFontScale.fromKey(prefs[KEY_FONT_SCALE])
                    resolvedTheme to resolvedScale
                }
            }
        }.getOrNull()
        val finalTheme = cachedTheme ?: fallback?.first ?: AppThemeMode.SYSTEM
        val finalScale = if (cachedScaleRaw != null) {
            AppFontScale.fromKey(cachedScaleRaw)
        } else {
            fallback?.second ?: AppFontScale.NORMAL
        }
        mirrorBootDisplayPrefs(themeMode = finalTheme, fontScale = finalScale)
        return BootDisplayPrefs(
            themeMode = finalTheme,
            fontScale = finalScale,
            fromFastCache = false
        )
    }

    val updateRepoSlugFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_UPDATE_REPO_SLUG] ?: ""
    }

    val lastUpdateCheckAtFlow: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_UPDATE_CHECK_AT] ?: 0L
    }

    val updateSnoozedUntilFlow: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[KEY_UPDATE_SNOOZED_UNTIL] ?: 0L
    }

    val notificationsEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_NOTIFICATIONS_ENABLED] ?: true
    }

    val realtimeBackgroundServiceEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_REALTIME_BACKGROUND_SERVICE] ?: false
    }

    val chatWallpapersFlow: Flow<Map<String, String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_CHAT_WALLPAPERS_JSON] ?: "{}"
        com.example.data.model.JsonHelpers.decodeReactions(raw)
    }

    val chatNotificationTonesFlow: Flow<Map<String, String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_CHAT_NOTIFICATION_TONES_JSON] ?: "{}"
        com.example.data.model.JsonHelpers.decodeReactions(raw)
    }

    val fcmTokenFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_FCM_TOKEN]
    }

    val lastNotifiedTimestampFlow: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_NOTIFIED_MSG_TS] ?: 0L
    }

    val pinnedChatIdsFlow: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_PINNED_CHAT_IDS_JSON] ?: "[]"
        com.example.data.model.JsonHelpers.decodeStringList(raw).toSet()
    }

    val mutedChatIdsFlow: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_MUTED_CHAT_IDS_JSON] ?: "[]"
        com.example.data.model.JsonHelpers.decodeStringList(raw).toSet()
    }

    val turnUrlFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_TURN_URL]?.ifBlank { DEFAULT_TURN_URL } ?: DEFAULT_TURN_URL
    }

    val turnUsernameFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_TURN_USERNAME]?.ifBlank { DEFAULT_TURN_USERNAME } ?: DEFAULT_TURN_USERNAME
    }

    val turnCredentialFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_TURN_CREDENTIAL]?.ifBlank { DEFAULT_TURN_CREDENTIAL } ?: DEFAULT_TURN_CREDENTIAL
    }

    val lastSyncedUidsHashFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_SYNCED_UIDS_HASH]
    }

    val memberShowLastSeenMapFlow: Flow<Map<String, Boolean>> = context.dataStore.data.map { prefs ->
        decodeBooleanMap(prefs[KEY_SHOW_LAST_SEEN_MAP_JSON] ?: "{}")
    }

    suspend fun setMemberShowLastSeen(memberId: String, showLastSeen: Boolean) {
        if (memberId.isBlank()) return
        context.dataStore.edit { prefs ->
            val current = decodeBooleanMap(prefs[KEY_SHOW_LAST_SEEN_MAP_JSON] ?: "{}").toMutableMap()
            current[memberId] = showLastSeen
            prefs[KEY_SHOW_LAST_SEEN_MAP_JSON] = encodeBooleanMap(current)
        }
    }

    val chatClearedAtFlow: Flow<Map<String, Long>> = context.dataStore.data.map { prefs ->
        decodeLongMap(prefs[KEY_CHAT_CLEARED_AT_JSON] ?: "{}")
    }

    val locallyHiddenMessageKeysFlow: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_LOCALLY_HIDDEN_MESSAGE_KEYS_JSON] ?: "[]"
        com.example.data.model.JsonHelpers.decodeStringList(raw).toSet()
    }

    suspend fun updateMasterPin(newPin: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_MASTER_PIN] = newPin
        }
    }

    suspend fun updateSwitchPin(newPin: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SWITCH_PIN] = newPin
        }
    }

    suspend fun setActiveMemberId(memberId: String?) {
        bootSessionPrefs().edit().apply {
            putBoolean(BOOT_SESSION_KEY_INITIALIZED, true)
            if (memberId == null) remove(BOOT_SESSION_KEY_MEMBER_ID) else putString(BOOT_SESSION_KEY_MEMBER_ID, memberId)
        }.apply()
        context.dataStore.edit { prefs ->
            if (memberId == null) {
                prefs.remove(KEY_ACTIVE_MEMBER_ID)
            } else {
                prefs[KEY_ACTIVE_MEMBER_ID] = memberId
            }
        }
    }

    suspend fun setActiveFamilyId(familyId: String?) {
        bootSessionPrefs().edit().apply {
            putBoolean(BOOT_SESSION_KEY_INITIALIZED, true)
            if (familyId == null) remove(BOOT_SESSION_KEY_FAMILY_ID) else putString(BOOT_SESSION_KEY_FAMILY_ID, familyId)
        }.apply()
        context.dataStore.edit { prefs ->
            if (familyId == null) {
                prefs.remove(KEY_ACTIVE_FAMILY_ID)
            } else {
                prefs[KEY_ACTIVE_FAMILY_ID] = familyId
            }
        }
    }

    suspend fun readInitialSessionSnapshot(): InitialSessionSnapshot {
        val prefs = context.dataStore.data.first()
        val cachedPair = decodeCachedMembers(prefs[KEY_CACHED_MEMBERS_JSON])
        val snapshot = InitialSessionSnapshot(
            activeFamilyId = prefs[KEY_ACTIVE_FAMILY_ID],
            activeMemberId = prefs[KEY_ACTIVE_MEMBER_ID],
            cachedFamilyId = cachedPair?.first,
            cachedMembers = cachedPair?.second.orEmpty()
        )
        mirrorSessionSnapshot(snapshot)
        return snapshot
    }

    suspend fun saveSelectedSession(
        familyId: String,
        memberId: String,
        members: List<FamilyMemberEntity>
    ) {
        if (memberId.isBlank()) return
        var mirroredEncodedMembers: String? = null
        context.dataStore.edit { prefs ->
            if (familyId.isNotBlank()) {
                prefs[KEY_ACTIVE_FAMILY_ID] = familyId
            }
            prefs[KEY_ACTIVE_MEMBER_ID] = memberId
            if (familyId.isNotBlank() && members.isNotEmpty()) {
                val merged = mergePreservingMember(
                    existingJson = prefs[KEY_CACHED_MEMBERS_JSON],
                    familyId = familyId,
                    incoming = members,
                    preserveMemberId = memberId
                )
                val encoded = encodeCachedMembers(familyId, merged)
                mirroredEncodedMembers = encoded
                if (prefs[KEY_CACHED_MEMBERS_JSON] != encoded) {
                    prefs[KEY_CACHED_MEMBERS_JSON] = encoded
                }
            }
        }
        bootSessionPrefs().edit().apply {
            putBoolean(BOOT_SESSION_KEY_INITIALIZED, true)
            if (familyId.isNotBlank()) {
                putString(BOOT_SESSION_KEY_FAMILY_ID, familyId)
            }
            putString(BOOT_SESSION_KEY_MEMBER_ID, memberId)
            if (mirroredEncodedMembers != null) {
                putString(BOOT_SESSION_KEY_CACHED_MEMBERS_JSON, mirroredEncodedMembers)
            }
        }.apply()
    }

    suspend fun saveCachedFamilyMembers(
        familyId: String,
        members: List<FamilyMemberEntity>,
        preserveMemberId: String? = null
    ) {
        if (familyId.isBlank() || members.isEmpty()) return
        var mirroredEncodedMembers: String? = null
        context.dataStore.edit { prefs ->
            val merged = mergePreservingMember(
                existingJson = prefs[KEY_CACHED_MEMBERS_JSON],
                familyId = familyId,
                incoming = members,
                preserveMemberId = preserveMemberId
            )
            val encoded = encodeCachedMembers(familyId, merged)
            mirroredEncodedMembers = encoded
            if (prefs[KEY_CACHED_MEMBERS_JSON] != encoded) {
                prefs[KEY_CACHED_MEMBERS_JSON] = encoded
            }
        }
        if (mirroredEncodedMembers != null) {
            bootSessionPrefs().edit()
                .putBoolean(BOOT_SESSION_KEY_INITIALIZED, true)
                .putString(BOOT_SESSION_KEY_CACHED_MEMBERS_JSON, mirroredEncodedMembers)
                .apply()
        }
    }

    private fun mergePreservingMember(
        existingJson: String?,
        familyId: String,
        incoming: List<FamilyMemberEntity>,
        preserveMemberId: String?
    ): List<FamilyMemberEntity> {
        if (preserveMemberId.isNullOrBlank() || incoming.any { it.id == preserveMemberId }) {
            return incoming
        }
        val prev = decodeCachedMembers(existingJson)
        val preserved = if (prev != null && prev.first == familyId) {
            prev.second.find { it.id == preserveMemberId }
        } else {
            null
        }
        return if (preserved != null) incoming + preserved else incoming
    }

    suspend fun clearCachedFamilyMembers() {
        bootSessionPrefs().edit()
            .remove(BOOT_SESSION_KEY_CACHED_MEMBERS_JSON)
            .apply()
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_CACHED_MEMBERS_JSON)
        }
    }

    suspend fun setDarkTheme(isDark: Boolean) {
        val mode = if (isDark) AppThemeMode.DARK else AppThemeMode.LIGHT
        mirrorBootDisplayPrefs(themeMode = mode)
        context.dataStore.edit { prefs ->
            prefs[KEY_IS_DARK_THEME] = isDark
            prefs[KEY_THEME_MODE] = mode.key
        }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        mirrorBootDisplayPrefs(themeMode = mode)
        context.dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode.key
            when (mode) {
                AppThemeMode.DARK -> prefs[KEY_IS_DARK_THEME] = true
                AppThemeMode.LIGHT -> prefs[KEY_IS_DARK_THEME] = false
                AppThemeMode.SYSTEM -> prefs.remove(KEY_IS_DARK_THEME)
            }
        }
    }

    suspend fun setFontScale(scale: AppFontScale) {
        mirrorBootDisplayPrefs(fontScale = scale)
        context.dataStore.edit { prefs ->
            prefs[KEY_FONT_SCALE] = scale.key
        }
    }

    suspend fun setUpdateRepoSlug(slug: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_UPDATE_REPO_SLUG] = slug
        }
    }

    suspend fun setLastUpdateCheckAt(timestampMs: Long) {
        if (timestampMs <= 0L) return
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_UPDATE_CHECK_AT] = timestampMs
        }
    }

    suspend fun setUpdateSnoozedUntil(untilMs: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_UPDATE_SNOOZED_UNTIL] = untilMs.coerceAtLeast(0L)
        }
    }

    suspend fun getCachedVersionHistoryJson(): String {
        return context.dataStore.data.first()[KEY_VERSION_HISTORY_CACHE_JSON] ?: ""
    }

    suspend fun setCachedVersionHistoryJson(json: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_VERSION_HISTORY_CACHE_JSON] = json
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun setRealtimeBackgroundServiceEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_REALTIME_BACKGROUND_SERVICE] = enabled
        }
    }

    suspend fun setChatWallpaper(chatId: String, wallpaperKey: String) {
        context.dataStore.edit { prefs ->
            val current = com.example.data.model.JsonHelpers.decodeReactions(
                prefs[KEY_CHAT_WALLPAPERS_JSON] ?: "{}"
            ).toMutableMap()
            if (wallpaperKey == "default" || wallpaperKey.isBlank()) {
                current.remove(chatId)
            } else {
                current[chatId] = wallpaperKey
            }
            prefs[KEY_CHAT_WALLPAPERS_JSON] = com.example.data.model.JsonHelpers.encodeReactions(current)
        }
    }

    suspend fun setChatNotificationTone(chatId: String, toneUri: String) {
        context.dataStore.edit { prefs ->
            val current = com.example.data.model.JsonHelpers.decodeReactions(
                prefs[KEY_CHAT_NOTIFICATION_TONES_JSON] ?: "{}"
            ).toMutableMap()
            if (toneUri.isBlank()) {
                current.remove(chatId)
            } else {
                current[chatId] = toneUri
            }
            prefs[KEY_CHAT_NOTIFICATION_TONES_JSON] = com.example.data.model.JsonHelpers.encodeReactions(current)
        }
    }

    suspend fun setFcmToken(token: String?) {
        context.dataStore.edit { prefs ->
            if (token.isNullOrBlank()) {
                prefs.remove(KEY_FCM_TOKEN)
            } else {
                prefs[KEY_FCM_TOKEN] = token
            }
        }
    }

    suspend fun updateLastNotifiedTimestampIfNewer(ts: Long) {
        if (ts <= 0L) return
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_LAST_NOTIFIED_MSG_TS] ?: 0L
            if (ts > current) {
                prefs[KEY_LAST_NOTIFIED_MSG_TS] = ts
            }
        }
    }

    suspend fun togglePinnedChat(chatId: String): Boolean {
        var nowPinned = false
        context.dataStore.edit { prefs ->
            val current = com.example.data.model.JsonHelpers.decodeStringList(
                prefs[KEY_PINNED_CHAT_IDS_JSON] ?: "[]"
            ).toMutableList()
            if (current.contains(chatId)) {
                current.remove(chatId)
                nowPinned = false
            } else {
                current.add(chatId)
                nowPinned = true
            }
            prefs[KEY_PINNED_CHAT_IDS_JSON] = com.example.data.model.JsonHelpers.encodeStringList(current)
        }
        return nowPinned
    }

    suspend fun toggleMutedChat(chatId: String): Boolean {
        var nowMuted = false
        context.dataStore.edit { prefs ->
            val current = com.example.data.model.JsonHelpers.decodeStringList(
                prefs[KEY_MUTED_CHAT_IDS_JSON] ?: "[]"
            ).toMutableList()
            if (current.contains(chatId)) {
                current.remove(chatId)
                nowMuted = false
            } else {
                current.add(chatId)
                nowMuted = true
            }
            prefs[KEY_MUTED_CHAT_IDS_JSON] = com.example.data.model.JsonHelpers.encodeStringList(current)
        }
        return nowMuted
    }

    suspend fun saveTurnConfig(url: String, username: String, credential: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TURN_URL] = url.trim().ifBlank { DEFAULT_TURN_URL }
            prefs[KEY_TURN_USERNAME] = username.trim().ifBlank { DEFAULT_TURN_USERNAME }
            prefs[KEY_TURN_CREDENTIAL] = credential.trim().ifBlank { DEFAULT_TURN_CREDENTIAL }
        }
    }

    suspend fun setLastSyncedUidsHash(hash: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_SYNCED_UIDS_HASH] = hash
        }
    }

    suspend fun setChatClearedAt(
        memberId: String,
        chatId: String,
        clearedAtMs: Long,
        extraHiddenMessageIds: Collection<String> = emptyList()
    ) {
        if (memberId.isBlank() || chatId.isBlank() || clearedAtMs <= 0L) return
        context.dataStore.edit { prefs ->
            val currentMap = decodeLongMap(prefs[KEY_CHAT_CLEARED_AT_JSON] ?: "{}").toMutableMap()
            val key = chatClearedAtKey(memberId, chatId)
            val prev = currentMap[key] ?: 0L
            currentMap[key] = maxOf(prev, clearedAtMs)
            prefs[KEY_CHAT_CLEARED_AT_JSON] = encodeLongMap(currentMap)

            if (extraHiddenMessageIds.isNotEmpty()) {
                val hiddenList = com.example.data.model.JsonHelpers.decodeStringList(
                    prefs[KEY_LOCALLY_HIDDEN_MESSAGE_KEYS_JSON] ?: "[]"
                ).toMutableList()
                val hiddenSet = hiddenList.toMutableSet()
                extraHiddenMessageIds.forEach { msgId ->
                    if (msgId.isNotBlank()) {
                        val hKey = hiddenMessageKey(memberId, msgId)
                        if (hiddenSet.add(hKey)) hiddenList.add(hKey)
                    }
                }
                val trimmed = if (hiddenList.size > 2000) hiddenList.takeLast(2000) else hiddenList
                prefs[KEY_LOCALLY_HIDDEN_MESSAGE_KEYS_JSON] =
                    com.example.data.model.JsonHelpers.encodeStringList(trimmed)
            }
        }
    }

    suspend fun addLocallyHiddenMessages(memberId: String, messageIds: Collection<String>) {
        if (memberId.isBlank() || messageIds.isEmpty()) return
        context.dataStore.edit { prefs ->
            val hiddenList = com.example.data.model.JsonHelpers.decodeStringList(
                prefs[KEY_LOCALLY_HIDDEN_MESSAGE_KEYS_JSON] ?: "[]"
            ).toMutableList()
            val hiddenSet = hiddenList.toMutableSet()
            messageIds.forEach { msgId ->
                if (msgId.isNotBlank()) {
                    val hKey = hiddenMessageKey(memberId, msgId)
                    if (hiddenSet.add(hKey)) hiddenList.add(hKey)
                }
            }
            val trimmed = if (hiddenList.size > 2000) hiddenList.takeLast(2000) else hiddenList
            prefs[KEY_LOCALLY_HIDDEN_MESSAGE_KEYS_JSON] =
                com.example.data.model.JsonHelpers.encodeStringList(trimmed)
        }
    }

    private fun decodeLongMap(json: String): Map<String, Long> = runCatching {
        val obj = org.json.JSONObject(json)
        val result = mutableMapOf<String, Long>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            result[k] = obj.optLong(k, 0L)
        }
        result
    }.getOrDefault(emptyMap())

    private fun encodeLongMap(map: Map<String, Long>): String {
        val obj = org.json.JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }

    private fun decodeBooleanMap(json: String): Map<String, Boolean> = runCatching {
        val obj = org.json.JSONObject(json)
        val result = mutableMapOf<String, Boolean>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            result[k] = obj.optBoolean(k, true)
        }
        result
    }.getOrDefault(emptyMap())

    private fun encodeBooleanMap(map: Map<String, Boolean>): String {
        val obj = org.json.JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }

    private fun encodeCachedMembers(familyId: String, members: List<FamilyMemberEntity>): String {
        val root = org.json.JSONObject()
        root.put("familyId", familyId)
        val arr = org.json.JSONArray()
        members.forEach { m ->
            val obj = org.json.JSONObject()
            obj.put("id", m.id)
            obj.put("name", m.name)
            obj.put("roleLabel", m.roleLabel)
            obj.put("isAdmin", m.isAdmin)
            obj.put("avatarColorHex", m.avatarColorHex)
            obj.put("avatarEmoji", m.avatarEmoji)
            obj.put("bioText", m.bioText)
            obj.put("phoneNumber", m.phoneNumber)
            obj.put("showLastSeen", m.showLastSeen)
            obj.put("lastSeenText", m.lastSeenText)
            obj.put("liveStatusAt", m.liveStatusAt)
            obj.put("linkedAuthUid", m.linkedAuthUid)
            obj.put("createdAt", m.createdAt)
            obj.put("authorUid", m.authorUid)
            arr.put(obj)
        }
        root.put("members", arr)
        return root.toString()
    }

    private fun decodeCachedMembers(json: String?): Pair<String, List<FamilyMemberEntity>>? {
        if (json.isNullOrBlank()) return null
        return runCatching {
            val root = org.json.JSONObject(json)
            val familyId = root.optString("familyId", "").trim()
            if (familyId.isBlank()) return null
            val arr = root.optJSONArray("members") ?: return null
            val list = buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id", "").trim()
                    val name = obj.optString("name", "").trim()
                    if (id.isBlank() || name.isBlank()) continue
                    add(
                        FamilyMemberEntity(
                            id = id,
                            name = name,
                            roleLabel = obj.optString("roleLabel", "Familiar"),
                            isAdmin = obj.optBoolean("isAdmin", false),
                            avatarColorHex = obj.optString("avatarColorHex", "#1A73E8"),
                            avatarEmoji = obj.optString("avatarEmoji", "👤"),
                            bioText = obj.optString("bioText", ""),
                            phoneNumber = obj.optString("phoneNumber", ""),
                            showLastSeen = obj.optBoolean("showLastSeen", true),
                            lastSeenText = obj.optString("lastSeenText", "No se ha conectado"),
                            liveStatusAt = obj.optLong("liveStatusAt", 0L),
                            linkedAuthUid = obj.optString("linkedAuthUid", ""),
                            createdAt = obj.optLong("createdAt", 0L),
                            authorUid = obj.optString("authorUid", "")
                        )
                    )
                }
            }
            familyId to list
        }.getOrNull()
    }
}
