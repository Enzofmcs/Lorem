package dev.lorem.app.ui

import dev.lorem.app.domain.model.CodeforcesProblem
import dev.lorem.app.StatisticsFixtures
import dev.lorem.app.domain.model.CodeforcesSubmission
import dev.lorem.app.domain.model.LocalProfile
import dev.lorem.app.domain.model.ProblemHistory
import dev.lorem.app.domain.model.ProblemId
import dev.lorem.app.domain.model.Ipsum
import dev.lorem.app.domain.model.IpsumFailureReason
import dev.lorem.app.domain.model.IpsumResult
import dev.lorem.app.domain.model.IpsumStatus
import dev.lorem.app.domain.repository.ActiveIpsumAlreadyExistsException
import dev.lorem.app.domain.RandomSource
import dev.lorem.app.domain.repository.CodeforcesRepository
import dev.lorem.app.domain.repository.ProblemCatalogResult
import dev.lorem.app.domain.repository.SubmissionHistoryResult
import dev.lorem.app.domain.repository.CodeforcesUser
import dev.lorem.app.domain.repository.LoremRepository
import dev.lorem.app.domain.repository.UserLookupResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
    fun `statistics react to local closure rating catalog and upsolve without remote calls`() = runTest {
        val local = MemoryRepository(StatisticsFixtures.profile)
        val remote = CountingRepository()
        val viewModel = LoremViewModel(local, remote, statisticsDispatcher = UnconfinedTestDispatcher(testScheduler))
        assertEquals(StatisticsUiState.Loading, viewModel.statisticsState.value)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.statisticsState.collect {} }
        advanceUntilIdle()
        assertEquals(0, (viewModel.statisticsState.value as StatisticsUiState.Ready).statistics.overall.total)

        val pending = StatisticsFixtures.finished.map {
            if (it.status == IpsumStatus.SOLVED_OUTSIDE) it.copy(status = IpsumStatus.PENDING,
                solvedOutsideAtEpochMillis = null) else it
        }
        local.ipsums.value = pending
        local.problemCatalog.value = StatisticsFixtures.catalog
        local.saveProfile(StatisticsFixtures.profile.copy(loremRating = 1300))
        advanceUntilIdle()
        val before = (viewModel.statisticsState.value as StatisticsUiState.Ready).statistics
        assertEquals(4, before.overall.total)
        assertEquals(1300, before.currentRating)
        assertEquals(0, before.byTag.single { it.tag == "greedy" }.attempts.total)

        local.ipsums.value = StatisticsFixtures.finished
        advanceUntilIdle()
        val after = (viewModel.statisticsState.value as StatisticsUiState.Ready).statistics
        assertEquals(1, after.overall.solvedOutside)
        assertEquals(1, after.overall.pending)
        assertEquals(before.overall.acRate, after.overall.acRate)
        assertEquals(before.ratingEvolution, after.ratingEvolution)
        assertEquals(before.overall.averageTimeMillis, after.overall.averageTimeMillis)
        assertEquals(0, remote.calls)
        assertEquals(0, remote.historyCalls)
    }

    @Test
    fun `statistics exclude cached attempts immediately on owner switch and clear on unlink`() = runTest {
        val local = MemoryRepository(StatisticsFixtures.profile)
        local.ipsums.value = StatisticsFixtures.finished
        val viewModel = LoremViewModel(local, CountingRepository(),
            statisticsDispatcher = UnconfinedTestDispatcher(testScheduler))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.statisticsState.collect {} }
        advanceUntilIdle()
        assertEquals(4, (viewModel.statisticsState.value as StatisticsUiState.Ready).statistics.overall.total)

        local.saveProfile(StatisticsFixtures.profile.copy(handle = "other", loremRating = 800))
        advanceUntilIdle()
        val switched = (viewModel.statisticsState.value as StatisticsUiState.Ready).statistics
        assertEquals("other", switched.ownerHandle)
        assertEquals(800, switched.currentRating)
        assertEquals(0, switched.overall.total)
        assertTrue(switched.ratingEvolution.isEmpty())
        assertTrue(switched.byTag.isEmpty())

        local.ipsums.value = listOf(StatisticsFixtures.finished.first().copy(ownerHandle = "other"))
        advanceUntilIdle()
        assertEquals(1, (viewModel.statisticsState.value as StatisticsUiState.Ready).statistics.overall.total)
        local.clearProfile()
        advanceUntilIdle()
        assertEquals(StatisticsUiState.NoProfile, viewModel.statisticsState.value)
    }

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

    @Test
    fun `refresh before selection excludes a problem attempted since the last import`() = runTest {
        val local = MemoryRepository(profile(lastSync = 1L).copy(loremRating = 1200))
        local.problemCatalog.value = listOf(problem(10, 1200), problem(11, 1200))
        val remote = CountingRepository(historyResults = listOf(SubmissionHistoryResult.Success(listOf(
            CodeforcesSubmission(99, ProblemId(10, "A"), "WRONG_ANSWER", Instant.ofEpochSecond(10)),
        ))))
        val model = LoremViewModel(local, remote, nowMillis = { 20_000 }, randomSource = RandomSource { 0 })
        model.startNewIpsum()
        model.startNewIpsum()
        advanceUntilIdle()
        assertEquals(1, remote.historyCalls)
        assertEquals(ProblemId(11, "A"), local.activeIpsum.value!!.problem.id)
        assertEquals(1, local.ipsums.value.size)
        assertEquals(null, model.dataSourceNotice.value)
    }

    @Test
    fun `offline cache remains usable after the first successful import and reports saved data`() = runTest {
        val local = MemoryRepository(profile(lastSync = 1L).copy(loremRating = 1200))
        local.problemCatalog.value = listOf(problem(10, 1200))
        val remote = CountingRepository(historyResults = listOf(SubmissionHistoryResult.NetworkFailure))
        val model = LoremViewModel(local, remote, randomSource = RandomSource { 0 })
        model.startNewIpsum()
        advanceUntilIdle()
        assertTrue(model.startIpsumState.value is StartIpsumUiState.Success)
        assertTrue(model.dataSourceNotice.value!!.contains("Usando dados salvos"))
        assertEquals(1L, local.profile.value!!.lastSyncEpochMillis)
    }

    @Test
    fun `slow preflight times out and cancels unused synchronization before using cache`() = runTest {
        val local = MemoryRepository(profile(lastSync = 1L).copy(loremRating = 1200))
        local.problemCatalog.value = listOf(problem(10, 1200))
        var cancelled = false
        val remote = object : CodeforcesRepository {
            override suspend fun user(handle: String) = UserLookupResult.NetworkFailure
            override suspend fun problems() = ProblemCatalogResult.NetworkFailure
            override suspend fun submissionHistory(handle: String): SubmissionHistoryResult {
                try { kotlinx.coroutines.awaitCancellation() } finally { cancelled = true }
            }
        }
        val model = LoremViewModel(local, remote, randomSource = RandomSource { 0 })
        model.startNewIpsum()
        advanceUntilIdle()
        assertTrue(cancelled)
        assertTrue(model.startIpsumState.value is StartIpsumUiState.Success)
        assertTrue(model.dataSourceNotice.value!!.contains("Usando dados salvos"))
        assertEquals(1L, local.profile.value!!.lastSyncEpochMillis)
    }

    @Test
    fun `profile changed during preflight cannot start a recommendation for either owner`() = runTest {
        val local = MemoryRepository(profile(lastSync = 1L).copy(loremRating = 1200))
        local.problemCatalog.value = listOf(problem(10, 1200))
        val response = kotlinx.coroutines.CompletableDeferred<SubmissionHistoryResult>()
        val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
        val remote = object : CodeforcesRepository {
            override suspend fun user(handle: String) = UserLookupResult.NetworkFailure
            override suspend fun problems() = ProblemCatalogResult.NetworkFailure
            override suspend fun submissionHistory(handle: String): SubmissionHistoryResult {
                entered.complete(Unit)
                return response.await()
            }
        }
        val model = LoremViewModel(local, remote)
        model.startNewIpsum()
        entered.await()
        local.saveProfile(profile(lastSync = 1L).copy(handle = "other"))
        response.complete(SubmissionHistoryResult.Success(emptyList()))
        advanceUntilIdle()
        assertEquals(null, local.activeIpsum.value)
        assertTrue((model.startIpsumState.value as StartIpsumUiState.Error).message.contains("perfil mudou"))
    }

    @Test
    fun `start persists timestamp and recommendation before reporting success`() = runTest {
        val local = MemoryRepository(profile(lastSync = 1L).copy(loremRating = 1200))
        local.problemCatalog.value = listOf(problem(10, 1300))
        val viewModel = LoremViewModel(
            local,
            CountingRepository(),
            nowMillis = { 9876L },
            randomSource = RandomSource { 0 },
        )

        viewModel.startNewIpsum()
        advanceUntilIdle()

        assertTrue(viewModel.startIpsumState.value is StartIpsumUiState.Success)
        assertEquals(9876L, local.activeIpsum.value?.startedAtEpochMillis)
        assertEquals(1300, local.activeIpsum.value?.selectedRating)
    }

    @Test
    fun `start without candidate is recoverable and persists nothing`() = runTest {
        val local = MemoryRepository(profile(lastSync = 1L).copy(loremRating = 1200))
        local.problemCatalog.value = listOf(problem(10, 1700))
        val viewModel = LoremViewModel(local, CountingRepository(), randomSource = sequenceRandom(1))

        viewModel.startNewIpsum()
        advanceUntilIdle()

        assertTrue((viewModel.startIpsumState.value as StartIpsumUiState.Error).message.contains("Nenhum problema"))
        assertEquals(null, local.activeIpsum.value)
    }

    @Test
    fun `second start reports existing active ipsum without replacing it`() = runTest {
        val local = MemoryRepository(profile(lastSync = 1L).copy(loremRating = 1200))
        local.problemCatalog.value = listOf(problem(10, 1200), problem(11, 1200))
        val viewModel = LoremViewModel(local, CountingRepository(), randomSource = sequenceRandom(1, 0))
        viewModel.startNewIpsum()
        advanceUntilIdle()
        val first = local.activeIpsum.value

        viewModel.startNewIpsum()
        advanceUntilIdle()

        assertEquals(first, local.activeIpsum.value)
        assertTrue((viewModel.startIpsumState.value as StartIpsumUiState.Error).message.contains("Já existe"))
    }

    @Test
    fun `repeated hint requests retain the first reveal time`() = runTest {
        val local = MemoryRepository(profile(lastSync = 1L).copy(loremRating = 1200))
        local.problemCatalog.value = listOf(problem(10, 1200))
        var now = 1_000L
        val viewModel = LoremViewModel(local, CountingRepository(), nowMillis = { now })
        viewModel.startNewIpsum()
        advanceUntilIdle()

        viewModel.revealActiveIpsumHint()
        advanceUntilIdle()
        now = 2_000L
        viewModel.revealActiveIpsumHint()
        advanceUntilIdle()

        assertEquals(1_000L, local.activeIpsum.value?.hintRevealedAtEpochMillis)
    }

    @Test
    fun `manual ending requires a reason and exposes the persisted result`() = runTest {
        val local = MemoryRepository(profile(lastSync = 1L).copy(loremRating = 1200))
        local.problemCatalog.value = listOf(problem(10, 1200))
        val viewModel = LoremViewModel(local, CountingRepository(), nowMillis = { 5_000L })
        viewModel.startNewIpsum()
        advanceUntilIdle()

        viewModel.endActiveIpsum(null)
        advanceUntilIdle()
        assertTrue(viewModel.ipsumResultState.value is IpsumResultUiState.Error)
        assertTrue(local.activeIpsum.value != null)

        viewModel.endActiveIpsum(IpsumFailureReason.TIME_EXPIRED)
        advanceUntilIdle()
        val result = (viewModel.ipsumResultState.value as IpsumResultUiState.Ready).result.ipsum
        assertEquals(IpsumFailureReason.TIME_EXPIRED, result.failureReason)
        assertEquals(5_000L, result.endedAtEpochMillis)
        assertEquals(IpsumStatus.PENDING, result.status)
    }

    @Test
    fun `manual ending reports a recoverable error when persistence fails`() = runTest {
        val local = MemoryRepository(profile(lastSync = 1L).copy(loremRating = 1200)).apply {
            problemCatalog.value = listOf(problem(10, 1200))
            failManualEnding = true
        }
        val viewModel = LoremViewModel(local, CountingRepository(), nowMillis = { 5_000L })
        viewModel.startNewIpsum()
        advanceUntilIdle()

        viewModel.endActiveIpsum(IpsumFailureReason.TIME_EXPIRED)
        advanceUntilIdle()

        val state = viewModel.ipsumResultState.value as IpsumResultUiState.Error
        assertEquals("Não foi possível salvar o resultado. Tente novamente.", state.message)
        assertTrue(local.activeIpsum.value != null)
    }

    @Test
    fun `first recommendation requires imported history instead of treating missing history as empty`() = runTest {
        val local = MemoryRepository(profile().copy(loremRating = 1200))
        local.problemCatalog.value = listOf(problem(10, 1200))
        val viewModel = LoremViewModel(local, CountingRepository())
        viewModel.startNewIpsum()
        advanceUntilIdle()
        assertTrue((viewModel.startIpsumState.value as StartIpsumUiState.Error).message.contains("Sincronize"))
        assertEquals(null, local.activeIpsum.value)
    }

    @Test
    fun `old history response cannot replace synchronization state after switching accounts`() = runTest {
        val local = MemoryRepository(profile(lastSync = 1L))
        val response = kotlinx.coroutines.CompletableDeferred<SubmissionHistoryResult>()
        val remote = object : CodeforcesRepository {
            override suspend fun user(handle: String) = UserLookupResult.Success(CodeforcesUser("other", "Other", 1200))
            override suspend fun submissionHistory(handle: String) = response.await()
            override suspend fun problems() = ProblemCatalogResult.Success(emptyList())
        }
        val viewModel = LoremViewModel(local, remote)
        viewModel.synchronizeHistory()
        advanceUntilIdle()
        viewModel.updateHandle("other")
        viewModel.confirmHandle()
        advanceUntilIdle()
        response.complete(SubmissionHistoryResult.Success(listOf(submission(1, "OK"))))
        advanceUntilIdle()
        assertEquals("other", local.profile.value?.handle)
        assertEquals(HistorySyncUiState.Idle, viewModel.historySyncState.value)
        assertEquals(emptyList<ProblemHistory>(), local.problemHistory.value)
    }

    @Test
    fun `late submission response cannot replace status of a newly started Ipsum`() = runTest {
        val local = MemoryRepository(profile(lastSync = 1L).copy(loremRating = 1200))
        local.problemCatalog.value = listOf(problem(10, 1200), problem(11, 1200))
        val response = kotlinx.coroutines.CompletableDeferred<SubmissionHistoryResult>()
        val remote = object : CodeforcesRepository {
            override suspend fun user(handle: String) = UserLookupResult.NetworkFailure
            override suspend fun submissionHistory(handle: String) = response.await()
            override suspend fun problems() = ProblemCatalogResult.Success(emptyList())
        }
        val model = LoremViewModel(local, remote, nowMillis = { 5_000L }, randomSource = sequenceRandom(1, 0))
        model.startNewIpsum()
        advanceUntilIdle()
        val first = local.activeIpsum.value!!
        model.verifyActiveIpsumSubmissions()
        advanceUntilIdle()
        model.endActiveIpsum(IpsumFailureReason.TIME_EXPIRED)
        advanceUntilIdle()
        model.startNewIpsum()
        advanceUntilIdle()
        val next = local.activeIpsum.value!!
        assertTrue(first.id != next.id)
        assertEquals(SubmissionCheckUiState.Idle, model.submissionCheckState.value)
        response.complete(SubmissionHistoryResult.Success(emptyList()))
        advanceUntilIdle()
        assertEquals(next, local.activeIpsum.value)
        assertEquals(SubmissionCheckUiState.Idle, model.submissionCheckState.value)
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
    override val ipsums = MutableStateFlow<List<Ipsum>>(emptyList())
    override val activeIpsum = MutableStateFlow<Ipsum?>(null)
    private val histories = mutableMapOf<String, List<ProblemHistory>>()
    val savedOperations = mutableListOf<String>()
    var failManualEnding = false
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
    override suspend fun createActiveIpsum(ipsum: Ipsum): Ipsum {
        if (activeIpsum.value != null) throw ActiveIpsumAlreadyExistsException()
        val saved = ipsum.copy(id = (ipsums.value.maxOfOrNull { it.id } ?: 0L) + 1L)
        ipsums.value += saved
        activeIpsum.value = saved
        return saved
    }
    override suspend fun revealIpsumHint(ipsumId: Long, revealedAtEpochMillis: Long) {
        val current = activeIpsum.value ?: return
        if (current.id == ipsumId && current.hintRevealedAtEpochMillis == null) {
            val updated = current.copy(hintRevealedAtEpochMillis = revealedAtEpochMillis)
            activeIpsum.value = updated
            ipsums.value = ipsums.value.map { if (it.id == ipsumId) updated else it }
        }
    }
    override suspend fun endIpsumWithoutAc(
        ipsumId: Long,
        reason: IpsumFailureReason,
        endedAtEpochMillis: Long,
    ): Boolean {
        if (failManualEnding) error("persistence failed")
        val current = activeIpsum.value?.takeIf { it.id == ipsumId } ?: return false
        val ended = current.copy(
            status = IpsumStatus.PENDING,
            endedAtEpochMillis = endedAtEpochMillis,
            failureReason = reason,
        )
        activeIpsum.value = null
        ipsums.value = ipsums.value.map { if (it.id == ipsumId) ended else it }
        return true
    }
    override suspend fun getIpsumResult(ipsumId: Long): IpsumResult? = ipsums.value
        .firstOrNull { it.id == ipsumId && it.status != IpsumStatus.ACTIVE }
        ?.let { IpsumResult(it, emptyList()) }
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

private fun problem(id: Long, rating: Int) = CodeforcesProblem(
    id = ProblemId(id, "A"),
    name = "Problem $id",
    rating = rating,
    tags = setOf("implementation"),
)

private fun sequenceRandom(vararg values: Int): RandomSource {
    var index = 0
    return RandomSource { bound -> values[index++.coerceAtMost(values.lastIndex)].mod(bound) }
}
