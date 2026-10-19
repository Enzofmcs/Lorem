package dev.lorem.app.ui.cloud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.lorem.app.domain.cloud.*
import dev.lorem.app.domain.model.IpsumFailureReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class CloudTrainingUiState(
    val snapshot: CloudSnapshot? = null,
    val selectedKey: String? = null,
    val busy: Boolean = false,
    val error: String? = null,
)

class CloudTrainingViewModel(private val uid: String, private val repository: CloudTrainingRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(CloudTrainingUiState())
    val state = mutableState.asStateFlow()
    private var pendingStart: Pair<String, String>? = null
    init { refresh() }

    private fun action(block: suspend () -> Unit) {
        if (mutableState.value.busy) return
        mutableState.value = mutableState.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try { block() }
            catch (e: Exception) {
                if (e is CancellationException) throw e
                mutableState.value = mutableState.value.copy(error = e.message ?: "Não foi possível atualizar. Tente novamente.")
            } finally { mutableState.value = mutableState.value.copy(busy = false) }
        }
    }
    private suspend fun reload() {
        val selected = repository.selectedHandle(uid)
        val snapshot = repository.load(uid, selected)
        val key = snapshot.active?.handleKey ?: selected?.takeIf { k -> snapshot.handles.any { it.key == k } }
            ?: snapshot.handles.firstOrNull()?.key
        val resolved = if (snapshot.active == null && key != selected) repository.load(uid, key) else snapshot
        mutableState.value = mutableState.value.copy(snapshot = resolved, selectedKey = key)
    }
    fun refresh() = action {
        reload()
        val active = mutableState.value.snapshot?.active
        if (active != null) { repository.reconcile(uid, active); reload() }
    }
    fun add(handle: String) = action {
        val added = repository.addHandle(uid, handle)
        // An existing active attempt continues to own the selected destination.
        if (mutableState.value.snapshot?.active == null) repository.selectHandle(uid, added.key)
        reload()
    }
    fun select(key: String) = action { repository.selectHandle(uid, key); pendingStart = null; reload() }
    fun start() = action {
        val key = requireNotNull(mutableState.value.selectedKey)
        val command = pendingStart?.takeIf { it.first == key }?.second ?: UUID.randomUUID().toString().also {
            pendingStart = key to it
        }
        repository.start(uid, key, command)
        pendingStart = null
        reload()
    }
    fun hint() = action { repository.hint(uid, requireNotNull(mutableState.value.snapshot?.active)); reload() }
    fun close(reason: IpsumFailureReason) = action {
        repository.close(uid, requireNotNull(mutableState.value.snapshot?.active), reason)
        reload()
    }
    fun upsolve(attempt: CloudIpsum) = action { repository.reconcile(uid, attempt); reload() }
    fun now(): Long? = repository.deadline.now(uid)

    companion object {
        fun factory(uid: String, repository: CloudTrainingRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = CloudTrainingViewModel(uid, repository) as T
        }
    }
}
