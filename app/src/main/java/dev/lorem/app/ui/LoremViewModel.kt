package dev.lorem.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.lorem.app.domain.model.LocalProfile
import dev.lorem.app.domain.repository.LoremRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface LoremUiState {
    data object Loading : LoremUiState
    data class Ready(val profile: LocalProfile?) : LoremUiState
}

class LoremViewModel(
    private val repository: LoremRepository,
) : ViewModel() {
    val uiState: StateFlow<LoremUiState> = repository.profile
        .map<LocalProfile?, LoremUiState>(LoremUiState::Ready)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LoremUiState.Loading,
        )

    fun saveTestProfile(displayName: String) {
        if (displayName.isBlank()) return
        viewModelScope.launch { repository.saveProfile(LocalProfile(displayName.trim())) }
    }

    fun clearTestProfile() {
        viewModelScope.launch { repository.clearProfile() }
    }

    companion object {
        fun factory(repository: LoremRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(LoremViewModel::class.java))
                    return LoremViewModel(repository) as T
                }
            }
    }
}
