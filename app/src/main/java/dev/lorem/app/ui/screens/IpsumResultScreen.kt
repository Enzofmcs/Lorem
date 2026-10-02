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
import kotlin.math.absoluteValue

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
                val delta = ipsum.ratingDelta
                val after = ipsum.finalLoremRating
                if (delta != null && after != null) {
                    Text("${ipsum.initialLoremRating} ${signed(delta)} ${delta.absoluteValue} = $after")
                    Text(difficultyExplanation(ipsum.selectedRating, ipsum.initialLoremRating, ipsum.status))
                    if (ipsum.status == IpsumStatus.COMPLETED) {
                        val expected = ipsum.expectedTimeMillis
                        val elapsed = ipsum.totalTimeMillis()
                        if (expected != null && elapsed != null) {
                            Text(timeExplanation(elapsed, expected))
                        }
                        Text(
                            if (ipsum.hintRevealedAtEpochMillis == null) {
                                "Você resolveu sem revelar os tópicos, sem penalidade de dica."
                            } else {
                                "Os tópicos foram revelados, reduzindo o ganho em cerca de 25% (no mínimo 2 pontos)."
                            },
                        )
                    } else {
                        Text("Sem AC, a tentativa aplica a perda completa; desistir cedo não reduz essa perda.")
                    }
                    ipsum.expectedTimeMillis?.let { Text("Tempo esperado: ${formatElapsed(it)}") }
                } else {
                    Text("Este resultado antigo não possui cálculo de rating salvo.")
                }
            }
        }
        Button(modifier = Modifier.fillMaxWidth(), onClick = onClose) { Text("Fechar resultado") }
    }
}

private fun signed(delta: Int) = if (delta >= 0) "+" else "−"

private fun difficultyExplanation(problem: Int, user: Int, status: IpsumStatus): String {
    val comparison = when {
        problem > user -> "mais difícil que seu nível anterior"
        problem < user -> "mais fácil que seu nível anterior"
        else -> "compatível com seu nível anterior"
    }
    return if (status == IpsumStatus.COMPLETED) {
        "O problema era $comparison; problemas mais difíceis oferecem ganho maior."
    } else {
        "O problema era $comparison; falhar em problemas mais fáceis causa uma perda maior."
    }
}

private fun timeExplanation(elapsed: Long, expected: Long): String = when {
    elapsed <= expected -> "Você resolveu dentro do tempo esperado e recebeu o ganho integral de tempo."
    elapsed >= expected * 2 -> "O tempo passou do dobro do esperado, limitando o ganho de tempo a 50%."
    else -> "Você passou do tempo esperado, por isso o ganho foi reduzido gradualmente."
}

private fun categoryLabel(value: String) = when (value) {
    "FLUENCY" -> "Fluência"
    "CURRENT_LEVEL" -> "Nível atual"
    else -> "Desafio"
}
