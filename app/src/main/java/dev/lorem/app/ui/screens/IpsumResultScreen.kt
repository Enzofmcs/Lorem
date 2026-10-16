package dev.lorem.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.lorem.app.domain.model.*
import dev.lorem.app.ui.IpsumResultUiState
import dev.lorem.app.ui.components.*
import kotlin.math.absoluteValue

@Composable
fun IpsumResultScreen(state: IpsumResultUiState, onClose: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Resultado do Ipsum", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        when (state) {
            IpsumResultUiState.Idle, IpsumResultUiState.Loading -> {
                CircularProgressIndicator()
                Text("Carregando o resultado salvo…")
            }
            is IpsumResultUiState.Error -> LoremErrorState(state.message)
            is IpsumResultUiState.Ready -> {
                val result = state.result
                val ipsum = result.ipsum
                if (ipsum.status == IpsumStatus.ACTIVE) {
                    LoremErrorState("Este Ipsum ainda está ativo. O resultado estará disponível ao encerrar.")
                } else {
                    LoremBadge(if (ipsum.status == IpsumStatus.COMPLETED) "AC no Ipsum" else "Encerrado sem AC",
                        if (ipsum.status == IpsumStatus.COMPLETED) LoremTone.SUCCESS else LoremTone.ATTENTION)
                    Text(ipsum.problem.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    Text("Problema ${ipsum.problem.id}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ipsum.solvedOutsideAtEpochMillis?.let {
                        LoremSyncStatus("Resolvido depois do Ipsum", LoremTone.SUCCESS,
                            "AC posterior: ${formatSyncTime(it)}. A tentativa original terminou sem AC; seu rating foi preservado.")
                    }
                    RatingSummary(ipsum)
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Seu treino", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                            Text(ipsum.totalTimeMillis()?.let { "Tempo total: ${formatElapsed(it)}" } ?: "Tempo total indisponível")
                            Text("Dica usada: ${if (ipsum.hintRevealedAtEpochMillis != null) "sim" else "não"}")
                            Text("Erros definitivos: ${ipsum.errorCount}")
                            val errors = result.errorVerdicts.groupingBy { it }.eachCount().entries
                                .joinToString { "${it.key} (${it.value})" }.ifEmpty { "nenhum" }
                            Text("Tipos de erro: $errors", style = MaterialTheme.typography.bodySmall)
                            ipsum.failureReason?.let { Text("Motivo: ${it.label}") }
                        }
                    }
                    LoremPrimaryAction("Voltar ao início", onClose)
                    OutlinedButton(onClick = { uriHandler.openUri(ipsum.problemUrl()) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Abrir no Codeforces") }
                    ProblemDetails(ipsum)
                    RatingExplanation(ipsum)
                }
            }
        }
        if (state !is IpsumResultUiState.Ready || state.result.ipsum.status == IpsumStatus.ACTIVE) {
            LoremPrimaryAction("Voltar ao início", onClose)
        }
    }
}

@Composable
private fun RatingSummary(ipsum: Ipsum) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Alteração do Rating Lorem", style = MaterialTheme.typography.titleMedium)
            val delta = ipsum.ratingDelta
            val after = ipsum.finalLoremRating
            if (delta != null && after != null) {
                Text("${ipsum.initialLoremRating} → $after", style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.semantics {
                        contentDescription = "Rating anterior ${ipsum.initialLoremRating}, rating após o Ipsum $after"
                    })
                Text("Variação: ${if (delta >= 0) "+" else "−"}${delta.toLong().absoluteValue} pontos",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (delta >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            } else {
                Text("Rating anterior: ${ipsum.initialLoremRating}")
                Text("Este resultado antigo não possui cálculo de rating salvo.")
            }
        }
    }
}

@Composable
private fun ProblemDetails(ipsum: Ipsum) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Sobre o problema", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text("Rating do problema: ${ipsum.selectedRating}")
            Text("Categoria: ${categoryLabel(ipsum.category.name)}")
            Text("Tópicos: ${ipsum.problem.tags.sorted().joinToString()}")
            ipsum.recommendationFactors?.let { factors ->
                Text("Por que este problema?", style = MaterialTheme.typography.titleMedium)
                Text("A dificuldade foi escolhida para seu nível; os tópicos favorecem variedade, prática e autonomia.")
                val unseen = factors.tags.filter { it.exposure == 0 }.map { it.tag }
                if (unseen.isNotEmpty()) Text("Tópicos novos na sua prática: ${unseen.joinToString()}")
                if (factors.recentOverlap == 0) Text("Os tópicos variam em relação ao último Ipsum encerrado.")
            }
        }
    }
}

@Composable
private fun RatingExplanation(ipsum: Ipsum) {
    if (ipsum.ratingDelta == null || ipsum.finalLoremRating == null) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Entenda a variação", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(difficultyExplanation(ipsum.selectedRating, ipsum.initialLoremRating, ipsum.status))
            if (ipsum.status == IpsumStatus.COMPLETED) {
                val expected = ipsum.expectedTimeMillis
                val elapsed = ipsum.totalTimeMillis()
                if (expected != null && elapsed != null) Text(timeExplanation(elapsed, expected))
                Text(if (ipsum.hintRevealedAtEpochMillis == null) {
                    "Você resolveu sem revelar os tópicos, sem penalidade de dica."
                } else {
                    "Os tópicos foram revelados, reduzindo o ganho em cerca de 25% (no mínimo 2 pontos)."
                })
            } else {
                Text("Sem AC, a tentativa aplica a perda completa; desistir cedo não reduz essa perda.")
            }
            ipsum.expectedTimeMillis?.let { Text("Tempo esperado: ${formatElapsed(it)}") }
        }
    }
}

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
