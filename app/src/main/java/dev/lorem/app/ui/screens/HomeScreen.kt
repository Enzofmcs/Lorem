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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.lorem.app.domain.model.LocalProfile
import dev.lorem.app.domain.model.ProblemHistory
import dev.lorem.app.ui.HistorySyncUiState
import dev.lorem.app.ui.CatalogSyncUiState
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
    onNavigate: (String) -> Unit,
) {
    val attemptedCount = problemHistory.count(ProblemHistory::attempted)
    val solvedCount = problemHistory.count(ProblemHistory::hasAcceptedSubmission)
    val hasSynchronized = profile.lastSyncEpochMillis > 0L
    val latestCatalogUpdate = (catalogSyncState as? CatalogSyncUiState.Success)
        ?.updatedAtEpochMillis ?: catalogUpdatedAtEpochMillis
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Início", style = MaterialTheme.typography.headlineMedium)
            Text("${profile.displayName} (@${profile.handle})")
            Text("Rating oficial: ${profile.officialRating ?: "não disponível"}")
            Text("Rating Lorem: ${profile.loremRating}")
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onNavigate(LoremDestination.Configuration.route) },
            ) {
                Text("Abrir ${LoremDestination.Configuration.title}")
            }

            Text("Histórico do Codeforces", style = MaterialTheme.typography.titleMedium)
            if (hasSynchronized) {
                Text("Histórico salvo neste dispositivo.")
                Text("Problemas tentados: $attemptedCount")
                Text("Problemas resolvidos: $solvedCount")
                Text("Última sincronização: ${formatSyncTime(profile.lastSyncEpochMillis)}")
            }

            when (syncState) {
                HistorySyncUiState.Idle -> if (!hasSynchronized) {
                    Text("Sincronize para importar seu histórico do Codeforces.")
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
                Text(if (syncState is HistorySyncUiState.Error) "Tentar novamente" else "Sincronizar histórico")
            }

            Text("Catálogo de problemas", style = MaterialTheme.typography.titleMedium)
            if (latestCatalogUpdate != null) {
                Text("$problemCatalogCount problemas com rating disponíveis localmente.")
                Text("Última atualização: ${formatSyncTime(latestCatalogUpdate)}")
            }
            when (catalogSyncState) {
                CatalogSyncUiState.Idle -> if (latestCatalogUpdate == null) {
                    Text("Carregue o catálogo para habilitar recomendações locais.")
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
                Text(if (catalogSyncState is CatalogSyncUiState.Error) "Tentar atualizar catálogo" else "Atualizar catálogo")
            }

            LoremDestination.all.filterNot {
                it == LoremDestination.Home || it == LoremDestination.Configuration
            }.forEach { destination ->
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onNavigate(destination.route) },
                ) {
                    Text("Abrir ${destination.title}")
                }
            }
        }
    }
}

private fun catalogSyncUiMessage(state: CatalogSyncUiState.Error): String =
    if (state.savedProblemCount > 0) {
        "${state.message} Usando ${state.savedProblemCount} problemas salvos."
    } else {
        state.message
    }

private fun formatSyncTime(epochMillis: Long): String = java.time.Instant.ofEpochMilli(epochMillis)
    .atZone(java.time.ZoneId.systemDefault())
    .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
