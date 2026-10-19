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
        var loads = 0
        var verifies = 0
        override suspend fun load(uid: String, selectedKey: String?): CloudSnapshot { loads++; return snapshot }
        override suspend fun selectedHandle(uid: String) = "tourist"
        override suspend fun addHandle(uid: String, handle: String) = snapshot.handles.first()
        override suspend fun selectHandle(uid: String, key: String) = Unit
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
}
