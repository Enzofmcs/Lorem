package dev.lorem.app.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.lorem.app.domain.sync.SynchronizationState
import dev.lorem.app.ui.PreparationUiState

@Composable
internal fun PreparationStatus(state: PreparationUiState) {
    if (state.history.lastSuccessEpochMillis == null || state.catalog.lastSuccessEpochMillis == null) {
        Text("Preparando seu treino", style = MaterialTheme.typography.titleMedium)
    }
    PreparationStage("Histórico", state.history)
    PreparationStage("Catálogo", state.catalog)
}

@Composable
private fun PreparationStage(label: String, state: SynchronizationState) {
    val status = when {
        state.running && state.importedCount > 0 -> "${state.importedCount} registros processados; importação em andamento"
        state.running -> "atualizando…"
        state.failure != null -> "falha na atualização; tente novamente. Dados salvos preservados."
        state.lastSuccessEpochMillis != null -> "atualizado em ${formatSyncTime(state.lastSuccessEpochMillis)}"
        else -> "aguardando conexão e execução automática"
    }
    Text("$label: $status")
}
