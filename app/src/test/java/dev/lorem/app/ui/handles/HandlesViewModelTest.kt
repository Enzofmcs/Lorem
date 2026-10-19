package dev.lorem.app.ui.handles

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import dev.lorem.app.domain.handles.*
import dev.lorem.app.domain.sync.ProfileLink
import dev.lorem.app.ui.MainDispatcherRule
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HandlesViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val profile = AccountHandle("a", HandleIdentity.parse("Tourist"), 1, 800, null, 0, 1234)
    private inner class Repository : HandlesRepository {
        val snapshots = MutableStateFlow(HandlesSnapshot(serverConfirmed = true))
        var additions = 0; var selections = 0
        var failure: HandleFailure? = null
        var beforeAdd: suspend () -> Unit = {}
        override fun observe(uid: String) = snapshots
        override suspend fun add(uid: String, input: String): AccountHandle {
            additions++; beforeAdd(); failure?.let { throw HandleException(it) }; return profile
        }
        override suspend fun select(uid: String, handleKey: String, bindingVersion: Long) {
            selections++
            snapshots.value = HandlesSnapshot(listOf(profile), ProfileLink.forAccount(uid, "Tourist", 1, 1), serverConfirmed = true)
        }
    }
    private fun model(store: ViewModelStore, repo: HandlesRepository) =
        ViewModelProvider(store, HandlesViewModel.factory("a", repo))[HandlesViewModel::class.java]

    @Test fun `double add sends one request and first binding is selected only after confirmation`() = runTest {
        val repo = Repository(); val store = ViewModelStore(); val model = model(store, repo)
        try {
            val reply = CompletableDeferred<Unit>(); repo.beforeAdd = { reply.await() }
            runCurrent(); model.input("tourist"); model.add(); model.add(); runCurrent()
            assertEquals(1, repo.additions); assertTrue(model.state.value.busy)
            assertNull(model.state.value.snapshot.selection)
            reply.complete(Unit); runCurrent()
            assertEquals(1, repo.selections); assertEquals("tourist", model.state.value.snapshot.selection!!.handle)
            assertEquals("", model.state.value.input); assertFalse(model.state.value.busy)
        } finally { store.clear() }
    }

    @Test fun `offline and active Ipsum prevent selection without optimistically changing profile`() = runTest {
        val repo = Repository(); val store = ViewModelStore(); val model = model(store, repo)
        try {
            repo.snapshots.value = HandlesSnapshot(listOf(profile), serverConfirmed = false)
            runCurrent(); model.input("tourist"); model.add(); runCurrent()
            assertEquals(0, repo.additions); assertEquals(HandleFailure.NETWORK, model.state.value.failure)
            repo.snapshots.value = HandlesSnapshot(listOf(profile), locked = true, serverConfirmed = true)
            runCurrent(); model.select(profile); runCurrent()
            assertEquals(0, repo.selections); assertEquals(HandleFailure.IPSUM_ACTIVE, model.state.value.failure)
        } finally { store.clear() }
    }

    @Test fun `lookup and quota failures preserve input and permit retry without a false selection`() = runTest {
        val repo = Repository(); val store = ViewModelStore(); val model = model(store, repo)
        try {
            runCurrent()
            for (failure in listOf(HandleFailure.NOT_FOUND, HandleFailure.QUOTA, HandleFailure.NETWORK)) {
                repo.failure = failure; model.input("tourist"); model.add(); runCurrent()
                assertEquals(failure, model.state.value.failure); assertEquals("tourist", model.state.value.input)
                assertNull(model.state.value.snapshot.selection); assertFalse(model.state.value.busy)
            }
            repo.failure = null; model.add(); runCurrent()
            assertNotNull(model.state.value.snapshot.selected); assertNull(model.state.value.failure)
        } finally { store.clear() }
    }

    @Test fun `refresh invalidates a late uncancellable reply and disposal releases the observer`() = runTest {
        val repo = Repository(); val store = ViewModelStore(); val model = model(store, repo)
        val reply = CompletableDeferred<Unit>()
        repo.beforeAdd = { withContext(NonCancellable) { reply.await() } }
        runCurrent(); model.input("tourist"); model.add(); runCurrent()
        model.refresh(); model.input("Petr"); runCurrent()
        reply.complete(Unit); runCurrent()
        assertEquals(0, repo.selections); assertEquals("Petr", model.state.value.input)
        store.clear(); runCurrent(); assertEquals(0, repo.snapshots.subscriptionCount.value)
    }

    @Test fun `a snapshot belonging to another uid is rejected before it reaches UI`() = runTest {
        val repo = Repository(); val store = ViewModelStore(); val model = model(store, repo)
        try {
            repo.snapshots.value = HandlesSnapshot(listOf(profile.copy(uid = "b")), serverConfirmed = true)
            runCurrent()
            assertEquals(HandleFailure.INVALID_SESSION, model.state.value.failure)
            assertTrue(model.state.value.snapshot.handles.isEmpty())
        } finally { store.clear() }
    }
}
