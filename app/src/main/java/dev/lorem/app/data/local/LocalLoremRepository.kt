package dev.lorem.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.lorem.app.domain.model.LocalProfile
import dev.lorem.app.domain.model.ProblemHistory
import dev.lorem.app.domain.repository.LoremRepository
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LocalLoremRepository(
    private val dataStore: DataStore<Preferences>,
    private val problemHistoryDao: ProblemHistoryDao,
) : LoremRepository {
    override val profile: Flow<LocalProfile?> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map(::readProfile)

    override val problemHistory: Flow<List<ProblemHistory>> = profile
        .flatMapLatest { profile ->
            profile?.let { problemHistoryDao.observeForOwner(normalizeHandle(it.handle)) }
                ?: flowOf(emptyList())
        }
        .map { history -> history.map { it.toDomain() } }

    override suspend fun saveProfile(profile: LocalProfile) {
        require(profile.handle.isNotBlank()) { "handle must not be blank" }
        require(profile.displayName.isNotBlank()) { "displayName must not be blank" }
        dataStore.edit { preferences ->
            preferences.remove(LEGACY_DISPLAY_NAME)
            preferences[HANDLE] = profile.handle.trim()
            preferences[DISPLAY_NAME] = profile.displayName.trim()
            profile.officialRating?.let { preferences[OFFICIAL_RATING] = it }
                ?: preferences.remove(OFFICIAL_RATING)
            preferences[LOREM_RATING] = profile.loremRating
            profile.consolidatedRating?.let { preferences[CONSOLIDATED_RATING] = it }
                ?: preferences.remove(CONSOLIDATED_RATING)
            preferences[LAST_SYNC] = profile.lastSyncEpochMillis
        }
    }

    override suspend fun clearProfile() {
        dataStore.edit { preferences ->
            preferences.remove(LEGACY_DISPLAY_NAME)
            preferences.remove(HANDLE)
            preferences.remove(DISPLAY_NAME)
            preferences.remove(OFFICIAL_RATING)
            preferences.remove(LOREM_RATING)
            preferences.remove(CONSOLIDATED_RATING)
            preferences.remove(LAST_SYNC)
        }
    }

    override suspend fun saveProblemHistory(ownerHandle: String, history: List<ProblemHistory>) {
        val normalizedOwner = normalizeHandle(ownerHandle)
        require(normalizedOwner.isNotBlank()) { "ownerHandle must not be blank" }
        problemHistoryDao.upsertAll(history.map { it.toEntity(normalizedOwner) })
    }

    private fun readProfile(preferences: Preferences): LocalProfile? {
        val handle = preferences[HANDLE]?.takeIf(String::isNotBlank) ?: return null
        val displayName = preferences[DISPLAY_NAME]?.takeIf(String::isNotBlank) ?: return null
        val loremRating = preferences[LOREM_RATING] ?: return null
        val lastSync = preferences[LAST_SYNC] ?: return null
        return LocalProfile(
            handle = handle,
            displayName = displayName,
            officialRating = preferences[OFFICIAL_RATING],
            loremRating = loremRating,
            consolidatedRating = preferences[CONSOLIDATED_RATING],
            lastSyncEpochMillis = lastSync,
        )
    }

    private companion object {
        fun normalizeHandle(handle: String): String = handle.trim().lowercase(Locale.ROOT)
        val LEGACY_DISPLAY_NAME = stringPreferencesKey("display_name")
        val HANDLE = stringPreferencesKey("profile_handle")
        val DISPLAY_NAME = stringPreferencesKey("profile_display_name")
        val OFFICIAL_RATING = intPreferencesKey("profile_official_rating")
        val LOREM_RATING = intPreferencesKey("profile_lorem_rating")
        val CONSOLIDATED_RATING = intPreferencesKey("profile_consolidated_rating")
        val LAST_SYNC = longPreferencesKey("profile_last_sync")
    }
}
