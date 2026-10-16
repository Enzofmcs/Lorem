package dev.lorem.app.ui.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.lorem.app.domain.sync.SyncFailureKind
import dev.lorem.app.domain.sync.SynchronizationState
import dev.lorem.app.ui.PreparationUiState
import dev.lorem.app.ui.components.LoremSyncStatus
import dev.lorem.app.ui.components.LoremTone

@Composable
internal fun PreparationSummary(state: PreparationUiState) {
    val stages = listOf(state.history, state.catalog, state.pending, state.metadata)
    val complete = stages.all { it.lastSuccessEpochMillis != null }
    val offline = stages.any { it.failureKind == SyncFailureKind.NETWORK }
    val summary = when {
        stages.any { it.running } -> if (state.history.lastSuccessEpochMillis == null || state.catalog.lastSuccessEpochMillis == null)
            "Preparando seu treino" else "Atualizando dados…"
        offline -> if (state.history.lastSuccessEpochMillis != null && state.catalog.lastSuccessEpochMillis != null)
            "Sem conexão · dados salvos" else "Sem conexão · preparação pendente"
        stages.any { it.failure != null } -> "Atualização incompleta · tente novamente em Configurações"
        state.history.lastSuccessEpochMillis == null || state.catalog.lastSuccessEpochMillis == null -> "Preparando seu treino"
        state.hasExpiredData -> "Dados salvos · atualização pendente"
        complete -> "Dados atualizados"
        else -> "Preparação em andamento"
    }
    // Only a fully completed set can have a shared success time; a partial refresh cannot advance it.
    val lastCompleteSuccess = if (complete) stages.mapNotNull { it.lastSuccessEpochMillis }.minOrNull() else null
    LoremSyncStatus(summary, when {
        stages.any { it.running } -> LoremTone.NEUTRAL
        offline || state.hasExpiredData -> LoremTone.ATTENTION
        stages.any { it.failure != null } -> LoremTone.ERROR
        complete -> LoremTone.SUCCESS
        else -> LoremTone.NEUTRAL
    }, detail = lastCompleteSuccess?.let { "Último sucesso de todos os dados: ${formatSyncTime(it)}" })
}

@Composable
internal fun SynchronizationStage(label: String, state: SynchronizationState) {
    val saved = if (state.lastSuccessEpochMillis != null) "dados salvos" else "preparação pendente"
    val status = when {
        state.running && state.importedCount > 0 -> "${state.importedCount} registros processados; importação em andamento"
        state.running -> "atualizando…"
        state.failureKind == SyncFailureKind.NETWORK -> "sem conexão · $saved. Tentaremos novamente."
        state.failureKind == SyncFailureKind.RATE_LIMIT -> "aguardando o Codeforces permitir nova consulta"
        state.failureKind == SyncFailureKind.INTERRUPTED -> "atualização interrompida; retomada automática disponível"
        state.failure != null -> "falha na atualização; tente novamente. ${if (state.lastSuccessEpochMillis != null) "Dados salvos preservados." else "Nenhuma consulta concluída."}"
        state.lastSuccessEpochMillis != null -> "atualizado"
        else -> "aguardando conexão e execução automática"
    }
    Text("$label: $status")
    Text("$label · último sucesso: ${state.lastSuccessEpochMillis?.let(::formatSyncTime) ?: "nenhuma consulta concluída"}")
}
