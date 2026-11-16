package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nidofamiliar_prefs")

class PreferencesManager(private val context: Context) {
    companion object {
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
        const val DEFAULT_PIN = "7525"
        const val DEFAULT_SWITCH_PIN = "2580"
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
        prefs[KEY_IS_DARK_THEME] ?: false
    }

    val updateRepoSlugFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_UPDATE_REPO_SLUG] ?: ""
    }

    val notificationsEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_NOTIFICATIONS_ENABLED] ?: true
    }

    val chatWallpapersFlow: Flow<Map<String, String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_CHAT_WALLPAPERS_JSON] ?: "{}"
        com.example.data.model.JsonHelpers.decodeReactions(raw)
    }

    val fcmTokenFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_FCM_TOKEN]
    }

    val lastNotifiedTimestampFlow: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_NOTIFIED_MSG_TS] ?: 0L
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
        context.dataStore.edit { prefs ->
            if (memberId == null) {
                prefs.remove(KEY_ACTIVE_MEMBER_ID)
            } else {
                prefs[KEY_ACTIVE_MEMBER_ID] = memberId
            }
        }
    }

    suspend fun setActiveFamilyId(familyId: String?) {
        context.dataStore.edit { prefs ->
            if (familyId == null) {
                prefs.remove(KEY_ACTIVE_FAMILY_ID)
            } else {
                prefs[KEY_ACTIVE_FAMILY_ID] = familyId
            }
        }
    }

    suspend fun setDarkTheme(isDark: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_IS_DARK_THEME] = isDark
        }
    }

    suspend fun setUpdateRepoSlug(slug: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_UPDATE_REPO_SLUG] = slug
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_NOTIFICATIONS_ENABLED] = enabled
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
}
