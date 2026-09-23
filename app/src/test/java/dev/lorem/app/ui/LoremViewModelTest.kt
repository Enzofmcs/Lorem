package dev.lorem.app.ui

import dev.lorem.app.domain.model.CodeforcesProblem
import dev.lorem.app.domain.model.CodeforcesSubmission
import dev.lorem.app.domain.model.LocalProfile
import dev.lorem.app.domain.model.ProblemHistory
import dev.lorem.app.domain.model.ProblemId
import dev.lorem.app.domain.repository.CodeforcesRepository
import dev.lorem.app.domain.repository.ProblemCatalogResult
import dev.lorem.app.domain.repository.SubmissionHistoryResult
import dev.lorem.app.domain.repository.CodeforcesUser
import dev.lorem.app.domain.repository.LoremRepository
import dev.lorem.app.domain.repository.UserLookupResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        assertEquals(0L, local.profile.value?.lastSyncEpochMillis)
    }

    @Test
    fun `successful profile change selects empty history owned by the new user`() = runTest {
        val existing = ProblemHistory(ProblemId(7L, "B"), attempted = true, hasAcceptedSubmission = true)
        val local = MemoryRepository(profile(), listOf(existing))
        val viewModel = LoremViewModel(
            local,
            CountingRepository(UserLookupResult.Success(CodeforcesUser("other", "Other", 1200))),
        )

        viewModel.updateHandle("other")
        viewModel.confirmHandle()
        advanceUntilIdle()

        assertEquals("other", local.profile.value?.handle)
        assertEquals(emptyList<ProblemHistory>(), local.problemHistory.value)
        assertEquals(listOf(existing), local.historyFor("tourist"))
    }

    @Test
    fun `failed profile validation preserves current profile and history`() = runTest {
        val existing = ProblemHistory(ProblemId(8L, "C"), attempted = true, hasAcceptedSubmission = false)
        val original = profile(lastSync = 55L)
        val local = MemoryRepository(original, listOf(existing))
        val viewModel = LoremViewModel(local, CountingRepository(UserLookupResult.UserNotFound))

        viewModel.updateHandle("missing")
        viewModel.confirmHandle()
        advanceUntilIdle()

        assertEquals(original, local.profile.value)
        assertEquals(listOf(existing), local.problemHistory.value)
    }

    @Test
    fun `canonical handle differing only by case retains owned history and training data`() = runTest {
        val existing = ProblemHistory(ProblemId(9L, "D"), attempted = true, hasAcceptedSubmission = true)
        val original = profile(lastSync = 66L)
        val local = MemoryRepository(original, listOf(existing))
        val viewModel = LoremViewModel(
            local,
            CountingRepository(UserLookupResult.Success(CodeforcesUser("Tourist", "Canonical", 3900))),
        )

        viewModel.updateHandle("TOURIST")
        viewModel.confirmHandle()
        advanceUntilIdle()

        assertEquals("Tourist", local.profile.value?.handle)
        assertEquals(original.loremRating, local.profile.value?.loremRating)
        assertEquals(66L, local.profile.value?.lastSyncEpochMillis)
        assertEquals(listOf(existing), local.problemHistory.value)
    }

    @Test
    fun `successful synchronization persists deduplicated history before updating timestamp`() = runTest {
        val original = profile(lastSync = 10L)
        val local = MemoryRepository(original)
        val remote = CountingRepository(historyResults = listOf(SubmissionHistoryResult.Success(
            listOf(
                submission(1L, "WRONG_ANSWER"),
                submission(2L, "OK"),
            ),
        )))
        val viewModel = LoremViewModel(local, remote, nowMillis = { 99L })

        viewModel.synchronizeHistory()
        advanceUntilIdle()

        assertEquals(HistorySyncUiState.Success(problemCount = 1), viewModel.historySyncState.value)
        assertEquals(1, local.problemHistory.value.size)
        assertTrue(local.problemHistory.value.single().hasAcceptedSubmission)
        assertEquals(99L, local.profile.value?.lastSyncEpochMillis)
        assertEquals(listOf("history", "profile"), local.savedOperations)
    }

    @Test
    fun `all remote failures become recoverable errors without changing valid local data`() = runTest {
        val failures = listOf(
            SubmissionHistoryResult.Failed("maintenance") to "maintenance",
            SubmissionHistoryResult.RateLimited to "Limite",
            SubmissionHistoryResult.NetworkFailure to "Sem conexão",
            SubmissionHistoryResult.HttpFailure(503) to "503",
            SubmissionHistoryResult.InvalidResponse to "resposta inválida",
        )

        failures.forEach { (failure, expectedMessage) ->
            val existing = ProblemHistory(ProblemId(7L, "B"), attempted = true, hasAcceptedSubmission = false)
            val local = MemoryRepository(profile(lastSync = 10L), listOf(existing))
            val viewModel = LoremViewModel(
                local,
                CountingRepository(historyResults = listOf(failure)),
                nowMillis = { 99L },
            )

            viewModel.synchronizeHistory()
            advanceUntilIdle()

            val state = viewModel.historySyncState.value
            assertTrue(state is HistorySyncUiState.Error)
            assertTrue((state as HistorySyncUiState.Error).message.contains(expectedMessage))
            assertEquals(listOf(existing), local.problemHistory.value)
            assertEquals(10L, local.profile.value?.lastSyncEpochMillis)
        }
    }

    @Test
    fun `synchronization can be retried after failure`() = runTest {
        val local = MemoryRepository(profile(lastSync = 10L))
        val remote = CountingRepository(historyResults = listOf(
            SubmissionHistoryResult.NetworkFailure,
            SubmissionHistoryResult.Success(listOf(submission(1L, "OK"))),
        ))
        val viewModel = LoremViewModel(local, remote, nowMillis = { 77L })

        viewModel.synchronizeHistory()
        advanceUntilIdle()
        assertTrue(viewModel.historySyncState.value is HistorySyncUiState.Error)

        viewModel.synchronizeHistory()
        advanceUntilIdle()

        assertEquals(2, remote.historyCalls)
        assertEquals(HistorySyncUiState.Success(1), viewModel.historySyncState.value)
        assertEquals(77L, local.profile.value?.lastSyncEpochMillis)
    }

    @Test
    fun `repeated synchronization requests are ignored while loading`() = runTest {
        val remote = CountingRepository(historyResults = listOf(SubmissionHistoryResult.Success(emptyList())))
        val viewModel = LoremViewModel(MemoryRepository(profile()), remote)

        viewModel.synchronizeHistory()
        viewModel.synchronizeHistory()
        advanceUntilIdle()

        assertEquals(1, remote.historyCalls)
    }
}

private class MemoryRepository(
    initialProfile: LocalProfile? = null,
    initialHistory: List<ProblemHistory> = emptyList(),
) : LoremRepository {
    override val profile = MutableStateFlow(initialProfile)
    override val problemHistory = MutableStateFlow(initialHistory)
    override val problemCatalog = MutableStateFlow<List<CodeforcesProblem>>(emptyList())
    override val catalogLastUpdatedEpochMillis = MutableStateFlow<Long?>(null)
    private val histories = mutableMapOf<String, List<ProblemHistory>>()
    val savedOperations = mutableListOf<String>()
    init {
        initialProfile?.let { histories[normalize(it.handle)] = initialHistory }
    }
    override suspend fun saveProfile(profile: LocalProfile) {
        savedOperations += "profile"
        this.profile.value = profile
        problemHistory.value = histories[normalize(profile.handle)].orEmpty()
    }
    override suspend fun clearProfile() { profile.value = null }
    override suspend fun saveProblemHistory(ownerHandle: String, history: List<ProblemHistory>) {
        savedOperations += "history"
        val owner = normalize(ownerHandle)
        val merged = (histories[owner].orEmpty() + history)
            .associateBy(ProblemHistory::problemId)
            .values
            .toList()
        histories[owner] = merged
        if (profile.value?.handle?.let(::normalize) == owner) problemHistory.value = merged
    }
    fun historyFor(handle: String): List<ProblemHistory> = histories[normalize(handle)].orEmpty()
    override suspend fun replaceProblemCatalog(problems: List<CodeforcesProblem>, updatedAtEpochMillis: Long) {
        problemCatalog.value = problems
        catalogLastUpdatedEpochMillis.value = updatedAtEpochMillis
    }
    private fun normalize(handle: String) = handle.trim().lowercase()
}

private class CountingRepository(
    private val result: UserLookupResult = UserLookupResult.NetworkFailure,
    private val historyResults: List<SubmissionHistoryResult> =
        listOf(SubmissionHistoryResult.Success(emptyList())),
) : CodeforcesRepository {
    var calls = 0
    var historyCalls = 0
    override suspend fun user(handle: String): UserLookupResult { calls++; return result }
    override suspend fun submissionHistory(handle: String): SubmissionHistoryResult =
        historyResults[historyCalls.coerceAtMost(historyResults.lastIndex)].also { historyCalls++ }
    override suspend fun problems(): ProblemCatalogResult = ProblemCatalogResult.Success(emptyList())
}

private fun profile(lastSync: Long = 0L) = LocalProfile(
    handle = "tourist",
    displayName = "Tourist",
    officialRating = 3800,
    loremRating = 3800,
    consolidatedRating = null,
    lastSyncEpochMillis = lastSync,
)

private fun submission(id: Long, verdict: String?) = CodeforcesSubmission(
    id = id,
    problemId = ProblemId(100L, "A"),
    verdict = verdict,
    createdAt = Instant.EPOCH,
)
