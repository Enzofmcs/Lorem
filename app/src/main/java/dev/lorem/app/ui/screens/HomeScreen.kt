package dev.lorem.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.lorem.app.domain.model.LocalProfile
import dev.lorem.app.ui.HistorySyncUiState

@Composable
fun HomeScreen(
    profile: LocalProfile,
    syncState: HistorySyncUiState,
    onSynchronize: () -> Unit,
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Home", style = MaterialTheme.typography.headlineMedium)
            Text("${profile.displayName} (@${profile.handle})")
            Text("Rating oficial: ${profile.officialRating ?: "não disponível"}")
            Text("Rating Lorem: ${profile.loremRating}")

            when (syncState) {
                HistorySyncUiState.Idle -> Text("Sincronize para importar seu histórico do Codeforces.")
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
        }
    }
}
