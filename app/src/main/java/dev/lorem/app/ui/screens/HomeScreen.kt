package dev.lorem.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.lorem.app.domain.ConsolidationProgress
import dev.lorem.app.domain.model.LocalProfile
import dev.lorem.app.domain.model.ProblemHistory
import dev.lorem.app.ui.CatalogSyncUiState
import dev.lorem.app.ui.HistorySyncUiState
import dev.lorem.app.ui.StartIpsumUiState
import dev.lorem.app.ui.components.LoremErrorState
import dev.lorem.app.ui.components.LoremMetricCard
import dev.lorem.app.ui.components.LoremPrimaryAction
import dev.lorem.app.ui.navigation.LoremDestination

@Composable
fun HomeScreen(
    profile: LocalProfile,
    syncState: HistorySyncUiState,
    problemHistory: List<ProblemHistory>,
    onSynchronize: () -> Unit,
    problemCatalogCount: Int,
    catalogUpdatedAtEpochMillis: Long?,
    catalogSyncState: CatalogSyncUiState,
    onSynchronizeCatalog: () -> Unit,
    hasActiveIpsum: Boolean = false,
    latestFinishedIpsumId: Long? = null,
    startIpsumState: StartIpsumUiState = StartIpsumUiState.Idle,
    onStartIpsum: () -> Unit = {},
    onNavigate: (String) -> Unit,
    consolidation: ConsolidationProgress? = null,
    preparation: dev.lorem.app.ui.PreparationUiState? = null,
    dataSourceNotice: String? = null,
) {
    val attemptedCount = problemHistory.count(ProblemHistory::attempted)
    val solvedCount = problemHistory.count(ProblemHistory::hasAcceptedSubmission)
    val hasSynchronized = profile.lastSyncEpochMillis > 0L
    val latestCatalogUpdate = (catalogSyncState as? CatalogSyncUiState.Success)
        ?.updatedAtEpochMillis ?: catalogUpdatedAtEpochMillis
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Início", style = MaterialTheme.typography.headlineMedium)
        Text("${profile.displayName} (@${profile.handle})")
        Text("Rating oficial: ${profile.officialRating ?: "não disponível"}")
        LoremMetricCard("Rating Lorem", profile.loremRating.toString())
        LoremPrimaryAction(
            label = if (hasActiveIpsum) "Voltar para o Ipsum ativo" else "Iniciar novo Ipsum",
            onClick = { if (hasActiveIpsum) onNavigate(LoremDestination.Ipsum.route) else onStartIpsum() },
            busy = startIpsumState == StartIpsumUiState.Loading,
        )
        if (latestFinishedIpsumId != null) {
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onNavigate("${LoremDestination.Result.route}/$latestFinishedIpsumId") },
            ) { Text("Abrir Resultado") }
        }
        when (startIpsumState) {
            StartIpsumUiState.Idle, is StartIpsumUiState.Success -> Unit
            StartIpsumUiState.Loading -> Text("Escolhendo e salvando um problema…")
            is StartIpsumUiState.Error -> LoremErrorState(startIpsumState.message)
        }
        consolidation?.let { ConsolidationCard(it, compact = true) }

        preparation?.let { PreparationStatus(it) }
        dataSourceNotice?.let { Text(it) }

        Text("Histórico do Codeforces", style = MaterialTheme.typography.titleMedium)
        if (hasSynchronized) {
            Text("Histórico salvo neste dispositivo.")
            Text("Problemas tentados: $attemptedCount")
            Text("Problemas resolvidos: $solvedCount")
            Text("Última sincronização: ${formatSyncTime(profile.lastSyncEpochMillis)}")
        }

        when (syncState) {
            HistorySyncUiState.Idle -> if (!hasSynchronized) {
                Text("O histórico será importado automaticamente quando houver conexão.")
            }
            HistorySyncUiState.Loading -> {
                CircularProgressIndicator()
                Text("Sincronizando histórico…")
            }
            is HistorySyncUiState.Success -> Text(
                "Sincronização concluída: ${syncState.problemCount} problemas importados.",
            )
            is HistorySyncUiState.Error -> Text(
                syncState.message,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onSynchronize,
            enabled = syncState != HistorySyncUiState.Loading,
        ) {
            Text(if (syncState is HistorySyncUiState.Error) "Tentar novamente" else "Atualizar histórico agora")
        }

        Text("Catálogo de problemas", style = MaterialTheme.typography.titleMedium)
        if (latestCatalogUpdate != null) {
            Text("$problemCatalogCount problemas com rating disponíveis localmente.")
            Text("Última atualização: ${formatSyncTime(latestCatalogUpdate)}")
        }
        when (catalogSyncState) {
            CatalogSyncUiState.Idle -> if (latestCatalogUpdate == null) {
                Text("O catálogo será preparado automaticamente quando houver conexão.")
            }
            CatalogSyncUiState.Loading -> {
                CircularProgressIndicator()
                Text("Atualizando catálogo…")
            }
            is CatalogSyncUiState.Success -> Text(
                "Catálogo atualizado: ${catalogSyncState.problemCount} problemas elegíveis.",
            )
            is CatalogSyncUiState.Error -> {
                Text(catalogSyncUiMessage(catalogSyncState), color = MaterialTheme.colorScheme.error)
            }
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onSynchronizeCatalog,
            enabled = catalogSyncState != CatalogSyncUiState.Loading,
        ) {
            Text(if (catalogSyncState is CatalogSyncUiState.Error) "Tentar atualizar catálogo" else "Atualizar catálogo agora")
        }

    }
}

private fun catalogSyncUiMessage(state: CatalogSyncUiState.Error): String =
    if (state.savedProblemCount > 0) {
        "${state.message} Usando ${state.savedProblemCount} problemas salvos."
    } else {
        state.message
    }

internal fun formatSyncTime(epochMillis: Long): String = java.time.Instant.ofEpochMilli(epochMillis)
    .atZone(java.time.ZoneId.systemDefault())
    .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
