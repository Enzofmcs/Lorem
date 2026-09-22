package dev.lorem.app.domain.repository

import dev.lorem.app.domain.model.LocalProfile
import kotlinx.coroutines.flow.Flow

interface LoremRepository {
    val profile: Flow<LocalProfile?>

    suspend fun saveProfile(profile: LocalProfile)

    suspend fun clearProfile()
}
