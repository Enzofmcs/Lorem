package dev.lorem.app.ui

import dev.lorem.app.domain.model.CodeforcesProblem
import dev.lorem.app.domain.model.LocalProfile
import dev.lorem.app.domain.repository.CodeforcesRepository
import dev.lorem.app.domain.repository.CodeforcesUser
import dev.lorem.app.domain.repository.LoremRepository
import dev.lorem.app.domain.repository.UserLookupResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoremViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `repeated confirmations are ignored while lookup is active`() = runTest {
        val remote = CountingRepository()
        val viewModel = LoremViewModel(MemoryRepository(), remote)
        viewModel.updateHandle("tourist")

        viewModel.confirmHandle()
        viewModel.confirmHandle()
        advanceUntilIdle()

        assertEquals(1, remote.calls)
        assertFalse(viewModel.configurationState.value.isLoading)
    }

    @Test
    fun `unrated valid user starts at decided rating`() = runTest {
        val local = MemoryRepository()
        val remote = CountingRepository(
            UserLookupResult.Success(CodeforcesUser("newbie", "New User", null)),
        )
        val viewModel = LoremViewModel(local, remote, nowMillis = { 42L })
        viewModel.updateHandle("newbie")
        viewModel.confirmHandle()
        advanceUntilIdle()

        assertEquals(800, local.profile.value?.loremRating)
        assertEquals(42L, local.profile.value?.lastSyncEpochMillis)
    }
}

private class MemoryRepository : LoremRepository {
    override val profile = MutableStateFlow<LocalProfile?>(null)
    override suspend fun saveProfile(profile: LocalProfile) { this.profile.value = profile }
    override suspend fun clearProfile() { profile.value = null }
}

private class CountingRepository(
    private val result: UserLookupResult = UserLookupResult.NetworkFailure,
) : CodeforcesRepository {
    var calls = 0
    override suspend fun user(handle: String): UserLookupResult { calls++; return result }
    override suspend fun problems(): List<CodeforcesProblem> = emptyList()
}
