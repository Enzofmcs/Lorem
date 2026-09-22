package dev.lorem.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.lorem.app.domain.model.LocalProfile
import dev.lorem.app.domain.repository.LoremRepository
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class DataStoreLoremRepository(
    private val dataStore: DataStore<Preferences>,
) : LoremRepository {
    override val profile: Flow<LocalProfile?> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            preferences[DISPLAY_NAME]
                ?.takeIf(String::isNotBlank)
                ?.let(::LocalProfile)
        }

    override suspend fun saveProfile(profile: LocalProfile) {
        require(profile.displayName.isNotBlank()) { "displayName must not be blank" }
        dataStore.edit { it[DISPLAY_NAME] = profile.displayName.trim() }
    }

    override suspend fun clearProfile() {
        dataStore.edit { it.remove(DISPLAY_NAME) }
    }

    private companion object {
        val DISPLAY_NAME = stringPreferencesKey("display_name")
    }
}
