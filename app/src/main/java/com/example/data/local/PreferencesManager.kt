package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
}
