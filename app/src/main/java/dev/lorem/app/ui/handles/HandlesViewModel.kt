package dev.lorem.app.ui.handles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.lorem.app.domain.handles.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HandlesUiState(
    val snapshot: HandlesSnapshot = HandlesSnapshot(),
    val input: String = "",
    val loading: Boolean = true,
    val busy: Boolean = false,
    val failure: HandleFailure? = null,
)

/** One instance/store per visible uid; disposal cancels its listeners and in-flight UI responses. */
class HandlesViewModel(private val uid: String, private val repository: HandlesRepository) : ViewModel() {
    private val mutable = MutableStateFlow(HandlesUiState())
    val state = mutable.asStateFlow()
    private var generation = 0L
    private var observation: Job? = null
    private var action: Job? = null

    init { refresh() }

    fun input(value: String) { mutable.update { it.copy(input = value, failure = null) } }

    fun refresh() {
        val request = ++generation
        observation?.cancel(); action?.cancel()
        mutable.update { it.copy(loading = true, busy = false, failure = null,
            snapshot = it.snapshot.copy(serverConfirmed = false)) }
        observation = viewModelScope.launch {
            try {
                repository.observe(uid).collect { snapshot ->
                    if (snapshot.handles.any { it.uid != uid } || (snapshot.selection != null && snapshot.selection.uid != uid)) {
                        throw HandleException(HandleFailure.INVALID_SESSION)
                    }
                    if (generation == request) mutable.update { it.copy(snapshot = snapshot, loading = false) }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (generation == request) mutable.update { it.copy(loading = false, failure = reason(error),
                    snapshot = it.snapshot.copy(serverConfirmed = false)) }
            }
        }
    }

    fun add() {
        val input = state.value.input
        runAction {
            val added = repository.add(uid, input)
            ensureActive()
            if (state.value.snapshot.selection == null && !state.value.snapshot.locked) {
                repository.select(uid, added.identity.key, added.bindingVersion)
            }
            ensureActive()
            mutable.update { it.copy(input = "") }
        }
    }

    fun select(profile: AccountHandle) {
        if (profile.uid != uid) return
        if (state.value.snapshot.locked) {
            mutable.update { it.copy(failure = HandleFailure.IPSUM_ACTIVE) }
            return
        }
        runAction { repository.select(uid, profile.identity.key, profile.bindingVersion) }
    }

    private fun runAction(block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) {
        if (state.value.busy) return
        if (!state.value.snapshot.serverConfirmed) {
            mutable.update { it.copy(failure = HandleFailure.NETWORK) }
            return
        }
        val request = generation
        mutable.update { it.copy(busy = true, failure = null) }
        action = viewModelScope.launch {
            try { block() } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (generation == request) mutable.update { it.copy(failure = reason(error)) }
            } finally {
                if (generation == request) mutable.update { it.copy(busy = false) }
            }
        }
    }

    override fun onCleared() { generation++; super.onCleared() }

    private fun reason(error: Exception) = (error as? HandleException)?.failure ?: HandleFailure.CONFIGURATION

    companion object {
        fun factory(uid: String, repository: HandlesRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = HandlesViewModel(uid, repository) as T
        }
    }
}
