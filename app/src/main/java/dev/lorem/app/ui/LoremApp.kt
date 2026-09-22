package dev.lorem.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.lorem.app.domain.repository.LoremRepository
import dev.lorem.app.ui.navigation.LoremNavHost

@Composable
fun LoremApp(
    repository: LoremRepository,
    viewModel: LoremViewModel = viewModel(factory = LoremViewModel.factory(repository)),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    when (val state = uiState) {
        LoremUiState.Loading -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }

        is LoremUiState.Ready -> LoremNavHost(
            initialProfile = state.profile,
            onSaveTestProfile = viewModel::saveTestProfile,
            onClearTestProfile = viewModel::clearTestProfile,
        )
    }
}
