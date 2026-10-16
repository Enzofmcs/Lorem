package dev.lorem.app.domain.repository

import dev.lorem.app.domain.model.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

interface AppearanceRepository {
    val themePreference: Flow<ThemePreference>
    suspend fun setThemePreference(preference: ThemePreference)
}

class MemoryAppearanceRepository : AppearanceRepository {
    override val themePreference = MutableStateFlow(ThemePreference.SYSTEM)
    override suspend fun setThemePreference(preference: ThemePreference) { themePreference.value = preference }
}
