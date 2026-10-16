package dev.lorem.app.ui.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.lorem.app.domain.sync.SyncFailureKind
import dev.lorem.app.domain.sync.SynchronizationState
import dev.lorem.app.ui.PreparationUiState
import dev.lorem.app.ui.components.LoremSyncStatus
import dev.lorem.app.ui.components.LoremTone

@Composable
internal fun PreparationStatus(state: PreparationUiState) {
    val stages = listOf(state.history, state.catalog, state.pending, state.metadata)
    val summary = when {
        state.history.lastSuccessEpochMillis == null || state.catalog.lastSuccessEpochMillis == null -> "Preparando seu treino"
        stages.any { it.running } -> "Atualizando dados…"
        stages.any { it.failureKind == SyncFailureKind.NETWORK } -> "Sem conexão · dados salvos"
        stages.any { it.failure != null } -> "Atualização incompleta · dados salvos"
        state.hasExpiredData -> "Dados salvos · atualização pendente"
        stages.all { it.lastSuccessEpochMillis != null } -> "Dados atualizados"
        else -> "Preparação em andamento"
    }
    LoremSyncStatus(summary, when {
        stages.any { it.failureKind == SyncFailureKind.NETWORK } || state.hasExpiredData -> LoremTone.ATTENTION
        stages.any { it.failure != null } -> LoremTone.ERROR
        stages.all { it.lastSuccessEpochMillis != null && !it.running } -> LoremTone.SUCCESS
        else -> LoremTone.NEUTRAL
    })
    SynchronizationStage("Histórico", state.history)
    SynchronizationStage("Catálogo", state.catalog)
    SynchronizationStage("Pendências", state.pending)
    SynchronizationStage("Perfil", state.metadata)
}

@Composable
internal fun SynchronizationStage(label: String, state: SynchronizationState) {
    val status = when {
        state.running && state.importedCount > 0 -> "${state.importedCount} registros processados; importação em andamento"
        state.running -> "atualizando…"
        state.failureKind == SyncFailureKind.NETWORK -> "sem conexão · dados salvos. Tentaremos novamente."
        state.failureKind == SyncFailureKind.RATE_LIMIT -> "aguardando o Codeforces permitir nova consulta"
        state.failureKind == SyncFailureKind.INTERRUPTED -> "atualização interrompida; retomada automática disponível"
        state.failure != null -> "falha na atualização; tente novamente. Dados salvos preservados."
        state.lastSuccessEpochMillis != null -> "atualizado"
        else -> "aguardando conexão e execução automática"
    }
    Text("$label: $status")
    Text("$label · último sucesso: ${state.lastSuccessEpochMillis?.let(::formatSyncTime) ?: "nenhuma consulta concluída"}")
}
