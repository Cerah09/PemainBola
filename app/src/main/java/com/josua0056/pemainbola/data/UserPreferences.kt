package com.josua0056.pemainbola.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_global_prefs")

class UserPreferences(private val context: Context) {
    companion object {
        private val USER_ID = stringPreferencesKey("prefs_user_id")
        private val USER_NAME = stringPreferencesKey("prefs_user_name")
        private val USER_EMAIL = stringPreferencesKey("prefs_user_email")
        private val USER_PHOTO = stringPreferencesKey("prefs_user_photo")
    }

    val userFlow: Flow<User?> = context.dataStore.data.map { preferences ->
        val id = preferences[USER_ID]
        if (id != null) {
            User(
                id = id,
                name = preferences[USER_NAME] ?: "",
                email = preferences[USER_EMAIL] ?: "",
                photoUrl = preferences[USER_PHOTO] ?: ""
            )
        } else null
    }

    suspend fun saveUser(user: User) {
        context.dataStore.edit { preferences ->
            preferences[USER_ID] = user.id
            preferences[USER_NAME] = user.name
            preferences[USER_EMAIL] = user.email
            preferences[USER_PHOTO] = user.photoUrl
        }
    }

    suspend fun clearUser() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}