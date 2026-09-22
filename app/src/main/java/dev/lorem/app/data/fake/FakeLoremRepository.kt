package dev.lorem.app.data.fake

import dev.lorem.app.domain.model.LocalProfile
import dev.lorem.app.domain.repository.LoremRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeLoremRepository(initialProfile: LocalProfile? = null) : LoremRepository {
    private val storedProfile = MutableStateFlow(initialProfile)

    override val profile: Flow<LocalProfile?> = storedProfile

    override suspend fun saveProfile(profile: LocalProfile) {
        storedProfile.value = profile
    }

    override suspend fun clearProfile() {
        storedProfile.value = null
    }
}
