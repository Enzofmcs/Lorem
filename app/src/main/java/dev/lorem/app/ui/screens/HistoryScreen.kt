package dev.lorem.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.lorem.app.domain.*
import dev.lorem.app.domain.model.Ipsum
import dev.lorem.app.domain.model.problemUrl
import dev.lorem.app.domain.model.totalTimeMillis
import dev.lorem.app.ui.PendingUpdateUiState
import dev.lorem.app.ui.components.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HistoryScreen(
    ipsums: List<Ipsum>,
    onOpenProblem: ((String) -> Unit)? = null,
    updateState: PendingUpdateUiState = PendingUpdateUiState.Idle,
    onUpdatePending: () -> Unit = {},
    onNavigateHome: () -> Unit = {},
    ownerHandle: String? = null,
    onOpenResult: ((Ipsum) -> Unit)? = null,
) {
    // Reset filters and scroll on account changes, while tab navigation saves them.
    key(ownerHandle?.trim()?.lowercase(Locale.ROOT)) {
        val owned = if (ownerHandle == null) ipsums else ipsums.filter {
            it.ownerHandle.trim().equals(ownerHandle.trim(), ignoreCase = true)
        }
        HistoryContent(owned, onOpenProblem, updateState, onUpdatePending, onNavigateHome, onOpenResult)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryContent(
    ipsums: List<Ipsum>,
    onOpenProblem: ((String) -> Unit)?,
    updateState: PendingUpdateUiState,
    onUpdatePending: () -> Unit,
    onNavigateHome: () -> Unit,
    onOpenResult: ((Ipsum) -> Unit)?,
) {
    val uriHandler = LocalUriHandler.current
    val openProblem = onOpenProblem ?: uriHandler::openUri
    val history = remember(ipsums) { buildIpsumHistory(ipsums) }
    val ratings = history.map { it.ipsum.selectedRating }.distinct().sorted()
    val tags = history.flatMap { it.ipsum.problem.tags }.distinct().sorted()
    var situationName by rememberSaveable { mutableStateOf(IpsumHistorySituation.SOLVED_IN_IPSUM.name) }
    var rating by rememberSaveable { mutableStateOf<Int?>(null) }
    var tag by rememberSaveable { mutableStateOf<String?>(null) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    val situation = IpsumHistorySituation.valueOf(situationName)
    val visible = filterIpsumHistory(history, situation, IpsumHistoryFilters(rating, tag))

    LazyColumn(
        modifier = Modifier.testTag("history-list").fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "filters") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Histórico e pendências", style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() })
                Text("Reveja seus treinos e retome os problemas pendentes.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onNavigateHome, modifier = Modifier.heightIn(min = 48.dp)) { Text("Voltar ao Início") }
                OutlinedButton(
                    modifier = Modifier.testTag("update-pending").heightIn(min = 48.dp),
                    enabled = updateState != PendingUpdateUiState.Loading,
                    onClick = onUpdatePending,
                ) {
                    if (updateState == PendingUpdateUiState.Loading) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Atualizando pendências…")
                    } else Text(if (updateState is PendingUpdateUiState.Error) "Tentar novamente" else "Atualizar agora")
                }
                when (updateState) {
                    is PendingUpdateUiState.Success -> Text(
                        if (updateState.movedCount == 0) "Nenhuma pendência foi alterada." else "${updateState.movedCount} pendência(s) movida(s).",
                        modifier = Modifier.testTag("update-pending-result"),
                    )
                    is PendingUpdateUiState.Error -> LoremErrorState(updateState.message)
                    else -> Unit
                }
                Text("Situação atual", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IpsumHistorySituation.entries.forEach { option ->
                        FilterChip(
                            modifier = Modifier.testTag("situation-${option.name}").heightIn(min = 48.dp),
                            selected = situation == option,
                            onClick = { situationName = option.name },
                            label = { Text(historySituationLabel(option)) },
                        )
                    }
                }
                TextButton(onClick = { advanced = !advanced }, modifier = Modifier.heightIn(min = 48.dp).testTag("history-advanced")) {
                    Text(if (advanced) "Recolher filtros avançados" else "Filtros avançados")
                }
                if (advanced) {
                    Text("Rating do problema", style = MaterialTheme.typography.titleMedium)
                    if (ratings.isEmpty()) Text("Sem ratings no histórico.")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ratings.forEach { option ->
                            FilterChip(modifier = Modifier.testTag("rating-$option").heightIn(min = 48.dp),
                                selected = rating == option, onClick = { rating = option.takeUnless { rating == option } },
                                label = { Text(option.toString()) })
                        }
                    }
                    Text("Tag oficial", style = MaterialTheme.typography.titleMedium)
                    if (tags.isEmpty()) Text("Sem tags no histórico.")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        tags.forEach { option ->
                            FilterChip(modifier = Modifier.testTag("tag-$option").heightIn(min = 48.dp),
                                selected = tag == option, onClick = { tag = option.takeUnless { tag == option } },
                                label = { Text(option) })
                        }
                    }
                }
                Text(buildList {
                    add("Filtros ativos: ${historySituationLabel(situation)}")
                    rating?.let { add("rating $it") }
                    tag?.let { add("tag $it") }
                }.joinToString(" · "), modifier = Modifier.testTag("history-active-filters"))
                TextButton(onClick = {
                    situationName = IpsumHistorySituation.SOLVED_IN_IPSUM.name
                    rating = null
                    tag = null
                }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Limpar filtros") }
                Text("${visible.size} de ${history.size} treino(s) encerrado(s)", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (visible.isEmpty()) item(key = "empty") {
            if (history.isEmpty()) LoremEmptyState("Seu histórico começa no primeiro Ipsum.", "Conclua um treino para ver sua tentativa aqui. Seus dados salvos ficam disponíveis offline.")
            else LoremEmptyState("Nenhum resultado para estes filtros.", "Altere a situação ou use Limpar filtros para voltar a AC no Ipsum, sem restrição de rating ou tag.")
        }
        items(visible, key = { "attempt-${it.ipsum.id}" }) { item -> HistoryItem(item, openProblem, onOpenResult) }
    }
}

internal fun historySituationLabel(situation: IpsumHistorySituation): String = when (situation) {
    IpsumHistorySituation.SOLVED_IN_IPSUM -> "AC no Ipsum"
    IpsumHistorySituation.PENDING -> "Pendente"
    IpsumHistorySituation.SOLVED_OUTSIDE_IPSUM -> "AC posterior"
}

@Composable
private fun HistoryItem(item: IpsumHistoryItem, onOpenProblem: (String) -> Unit, onOpenResult: ((Ipsum) -> Unit)?) {
    val ipsum = item.ipsum
    Card(Modifier.fillMaxWidth().testTag("history-${ipsum.problem.id.contestId}-${ipsum.problem.id.index}")) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LoremBadge(historySituationLabel(item.situation), when (item.situation) {
                IpsumHistorySituation.SOLVED_IN_IPSUM -> LoremTone.SUCCESS
                IpsumHistorySituation.SOLVED_OUTSIDE_IPSUM -> LoremTone.NEUTRAL
                IpsumHistorySituation.PENDING -> LoremTone.ATTENTION
            })
            Text(ipsum.problem.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text("Problema: ${ipsum.problem.id.contestId}${ipsum.problem.id.index}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Data do Ipsum: ${formatHistoryDate(ipsum.startedAtEpochMillis)}")
            Text("Tempo: ${ipsum.totalTimeMillis()?.let(::formatElapsed) ?: "indisponível"}")
            Text("Dica utilizada: ${if (ipsum.hintRevealedAtEpochMillis != null) "sim" else "não"}")
            Text("Erros: ${ipsum.errorCount}")
            HorizontalDivider()
            Text("Avaliação original: ${if (item.situation == IpsumHistorySituation.SOLVED_IN_IPSUM) "AC no Ipsum" else "encerrado sem AC"}")
            Text("Encerramento: ${ipsum.endedAtEpochMillis?.let(::formatHistoryDate) ?: "indisponível"}")
            Text("Variação do Rating Lorem: ${ipsum.ratingDelta?.let { if (it >= 0) "+$it" else it.toString() } ?: "indisponível"}")
            ipsum.virtualDuelResult?.let { dolor ->
                Text("Rating Dolor: ${dev.lorem.app.ui.components.formatDolorRating(dolor.afterMicros)}", style = MaterialTheme.typography.titleMedium)
                Text("Variação do Rating Dolor: ${dev.lorem.app.ui.components.formatDolorRating(dolor.deltaMicros, signed = true)}")
            }
            if (ipsum.studyRecordingVersion == "spark-2h-v1" && ipsum.virtualDuelResult == null) {
                Text("Sem avaliação Dolor neste Ipsum.", style = MaterialTheme.typography.bodySmall)
            }
            if (item.situation == IpsumHistorySituation.SOLVED_OUTSIDE_IPSUM) {
                Text("AC posterior: ${formatHistoryDate(requireNotNull(ipsum.solvedOutsideAtEpochMillis))}")
                Text("O AC posterior não altera os ratings nem a taxa original de AC.", style = MaterialTheme.typography.bodySmall)
            }
            Text("Rating do problema: ${ipsum.selectedRating}")
            Text("Tags: ${ipsum.problem.tags.sorted().joinToString().ifEmpty { "nenhuma" }}")
            onOpenResult?.let { open -> TextButton(onClick = { open(ipsum) }) { Text("Ver resultado") } }
            OutlinedButton(onClick = { onOpenProblem(ipsum.problemUrl()) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Abrir no Codeforces") }
        }
    }
}

private fun formatHistoryDate(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis)
    .atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
