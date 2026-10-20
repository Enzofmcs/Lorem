package dev.lorem.app.ui.cloud

import dev.lorem.app.domain.cloud.*
import dev.lorem.app.domain.model.*
import dev.lorem.app.ui.MainDispatcherRule
import dev.lorem.app.data.cloud.reconcileCloudWork
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CloudTrainingViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private class Repository : CloudTrainingRepository {
        override val deadline = CloudDeadline({ 0 }) { 1 }
        val attempt = CloudIpsum("id", "tourist", 0, CloudIpsumState.ACTIVE,
            Ipsum(1, "tourist", CodeforcesProblem(ProblemId(2000, "A"), "P", 800, setOf("math")),
                800, IpsumCategory.FLUENCY, 800, 800, 800, 0, 1, status = IpsumStatus.ACTIVE))
        var snapshot = CloudSnapshot(listOf(CloudHandle("tourist", "tourist", 800, null, 0)), null, emptyList(), true)
        val commands = mutableListOf<String>()
        var lostResponse = true
        var block: CompletableDeferred<Unit>? = null
        var selected = "tourist"
        var loadBlock: CompletableDeferred<Unit>? = null
        var loads = 0
        var verifies = 0
        var pendingCalls = 0
        override suspend fun refreshPending(uid: String, key: String, force: Boolean): Int { pendingCalls++; return 0 }
        override suspend fun load(uid: String, selectedKey: String?): CloudSnapshot { loads++; loadBlock?.await(); return snapshot }
        override suspend fun selectedHandle(uid: String) = selected
        override suspend fun addHandle(uid: String, handle: String) = snapshot.handles.firstOrNull { it.handle == handle } ?: snapshot.handles.first()
        override suspend fun selectHandle(uid: String, key: String) { selected = key }
        override suspend fun start(uid: String, key: String, commandId: String): CloudIpsum {
            commands += commandId
            block?.await()
            if (lostResponse) { lostResponse = false; error("Resposta perdida") }
            snapshot = snapshot.copy(active = attempt)
            return attempt
        }
        override suspend fun hint(uid: String, attempt: CloudIpsum) = Unit
        override suspend fun close(uid: String, attempt: CloudIpsum, reason: IpsumFailureReason) = Unit
        override suspend fun reconcile(uid: String, attempt: CloudIpsum): Boolean { verifies++; return true }
    }
    @Test fun `lost response retries same id and double tap never sends parallel start`() = runTest {
        val repository = Repository()
        val model = CloudTrainingViewModel("A", repository)
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        repository.block = gate
        model.start()
        model.start()
        runCurrent()
        assertEquals(1, repository.commands.size)
        gate.complete(Unit)
        runCurrent()
        assertEquals("Resposta perdida", model.state.value.error)
        model.start()
        runCurrent()
        assertEquals(2, repository.commands.size)
        assertEquals(repository.commands.first(), repository.commands.last())
        assertEquals(repository.attempt, model.state.value.snapshot?.active)
    }
    @Test fun `workers retry wrong session ignore stale version and verify only original attempt`() = runTest {
        val repository = Repository().apply { snapshot = snapshot.copy(active = attempt) }
        assertFalse(reconcileCloudWork("A", "tourist", "id", 0, { "B" }, repository))
        assertEquals(0, repository.loads)
        assertTrue(reconcileCloudWork("A", "tourist", "id", 1, { "A" }, repository))
        assertEquals(0, repository.verifies)
        assertTrue(reconcileCloudWork("A", "tourist", "old-id", 0, { "A" }, repository))
        assertEquals(0, repository.verifies)
        assertTrue(reconcileCloudWork("A", "tourist", "id", 0, { "A" }, repository))
        assertEquals(1, repository.verifies)
        repository.snapshot = repository.snapshot.copy(active = null, online = false)
        assertFalse(reconcileCloudWork("A", "tourist", "id", 0, { "A" }, repository))
        assertEquals(1, repository.verifies)
    }
    @Test fun `pending maintenance honors session offline and prioritizes active`() = runTest {
        val repository = Repository()
        assertFalse(dev.lorem.app.data.cloud.maintainCloudWork("A", { "B" }, repository))
        assertEquals(0, repository.loads)
        repository.snapshot = repository.snapshot.copy(online = false)
        assertFalse(dev.lorem.app.data.cloud.maintainCloudWork("A", { "A" }, repository))
        repository.snapshot = repository.snapshot.copy(online = true, active = repository.attempt)
        assertTrue(dev.lorem.app.data.cloud.maintainCloudWork("A", { "A" }, repository))
        assertEquals(1, repository.verifies)
    }
    @Test fun `closed adapter rejects other handles bindings and nonfinal data`() {
        val r = Repository()
        val final = r.attempt.copy(state = CloudIpsumState.FINAL)
        val state = CloudTrainingUiState(r.snapshot.copy(attempts = listOf(final, r.attempt,
            final.copy(handleKey = "other"), final.copy(bindingVersion = 2))), "tourist")
        assertEquals(listOf(final), state.closedAttempts())
        assertNull(state.copy(snapshot = state.snapshot!!.copy(active = r.attempt.copy(handleKey = "other"))).activeAttempt())
        assertNull(state.copy(snapshot = state.snapshot!!.copy(active = r.attempt.copy(bindingVersion = 2))).activeAttempt())
    }

    @Test fun `idle ticks do not reread Firestore or Codeforces inside pending cooldown and manual retry bypasses it`() = runTest {
        val repository = Repository()
        var elapsed = 0L
        val model = CloudTrainingViewModel("A", repository) { elapsed }
        runCurrent()
        model.updatePending(); runCurrent()
        val loads = repository.loads
        repeat(10) { elapsed += 15_000; model.updatePending(); runCurrent() }
        assertEquals(1, repository.pendingCalls)
        assertEquals(loads, repository.loads)
        model.updatePending(true); runCurrent()
        assertEquals(2, repository.pendingCalls)
        elapsed += 900_000
        model.updatePending(); runCurrent()
        assertEquals(3, repository.pendingCalls)
    }

    @Test fun `adding and selecting another handle clears old data while the response is delayed`() = runTest {
        val repository = Repository().apply {
            snapshot = snapshot.copy(handles = snapshot.handles + CloudHandle("petr", "petr", 800, null, 0))
        }
        val model = CloudTrainingViewModel("A", repository)
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        repository.loadBlock = gate
        model.add("petr"); runCurrent()
        assertNull(model.state.value.snapshot)
        assertEquals("petr", model.state.value.selectedKey)
        assertEquals(dev.lorem.app.ui.PendingUpdateUiState.Idle, model.state.value.pendingUpdate)
        gate.complete(Unit); runCurrent()
        assertEquals("petr", model.state.value.selectedKey)
        assertNotNull(model.state.value.snapshot)
    }

}
