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
import dev.lorem.app.domain.model.IpsumStatus
import dev.lorem.app.domain.model.totalTimeMillis
import dev.lorem.app.ui.IpsumResultUiState

@Composable
fun IpsumResultScreen(state: IpsumResultUiState, onClose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Resultado do Ipsum", style = MaterialTheme.typography.headlineMedium)
        when (state) {
            IpsumResultUiState.Idle, IpsumResultUiState.Loading -> CircularProgressIndicator()
            is IpsumResultUiState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
            is IpsumResultUiState.Ready -> {
                val result = state.result
                val ipsum = result.ipsum
                Text(if (ipsum.status == IpsumStatus.COMPLETED) "Resolvido no Ipsum" else "Pendente sem AC")
                Text(ipsum.problem.name, style = MaterialTheme.typography.titleLarge)
                Text("Rating do problema: ${ipsum.selectedRating}")
                Text("Categoria: ${categoryLabel(ipsum.category.name)}")
                Text("Tópicos: ${ipsum.problem.tags.sorted().joinToString()}")
                ipsum.totalTimeMillis()?.let { Text("Tempo total: ${formatElapsed(it)}") }
                    ?: Text("Tempo total indisponível", color = MaterialTheme.colorScheme.error)
                Text("Dica usada: ${if (ipsum.hintRevealedAtEpochMillis != null) "sim" else "não"}")
                Text("Erros antes do primeiro AC: ${ipsum.errorCount}")
                Text("Tipos de erro: ${result.errorVerdicts.ifEmpty { listOf("nenhum") }.groupingBy { it }.eachCount().entries.joinToString { "${it.key} (${it.value})" }}")
                ipsum.failureReason?.let { Text("Motivo: ${it.label}") }
                Text("Alteração do Rating Lorem", style = MaterialTheme.typography.titleMedium)
                Text("O cálculo e a explicação da variação serão adicionados na História 08.")
            }
        }
        Button(modifier = Modifier.fillMaxWidth(), onClick = onClose) { Text("Fechar resultado") }
    }
}

private fun categoryLabel(value: String) = when (value) {
    "FLUENCY" -> "Fluência"
    "CURRENT_LEVEL" -> "Nível atual"
    else -> "Desafio"
}
