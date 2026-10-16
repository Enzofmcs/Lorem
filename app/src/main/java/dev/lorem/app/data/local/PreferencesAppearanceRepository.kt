package dev.lorem.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.lorem.app.domain.model.ThemePreference
import dev.lorem.app.domain.repository.AppearanceRepository
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** App preference, independent of the currently linked Codeforces account. */
class PreferencesAppearanceRepository(private val store: DataStore<Preferences>) : AppearanceRepository {
    override val themePreference = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map { preferences ->
        ThemePreference.entries.firstOrNull { it.name == preferences[THEME] } ?: ThemePreference.SYSTEM
    }.distinctUntilChanged()

    override suspend fun setThemePreference(preference: ThemePreference) {
        store.edit { it[THEME] = preference.name }
    }

    private companion object { val THEME = stringPreferencesKey("appearance_theme") }
}
