package dev.lorem.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.lorem.app.domain.IpsumHistoryFilters
import dev.lorem.app.domain.IpsumHistoryItem
import dev.lorem.app.domain.IpsumHistorySituation
import dev.lorem.app.domain.buildIpsumHistory
import dev.lorem.app.domain.filterIpsumHistory
import dev.lorem.app.domain.model.Ipsum
import dev.lorem.app.domain.model.problemUrl
import dev.lorem.app.domain.model.totalTimeMillis
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HistoryScreen(
    ipsums: List<Ipsum>,
    onOpenProblem: ((String) -> Unit)? = null,
) {
    val uriHandler = LocalUriHandler.current
    val openProblem = onOpenProblem ?: uriHandler::openUri
    val history = buildIpsumHistory(ipsums)
    val ratings = history.map { it.ipsum.selectedRating }.distinct().sorted()
    val tags = history.flatMap { it.ipsum.problem.tags }.distinct().sorted()
    var situationName by rememberSaveable { mutableStateOf(IpsumHistorySituation.SOLVED_IN_IPSUM.name) }
    var rating by rememberSaveable { mutableStateOf<Int?>(null) }
    var tag by rememberSaveable { mutableStateOf<String?>(null) }
    val situation = IpsumHistorySituation.valueOf(situationName)
    val visible = filterIpsumHistory(history, situation, IpsumHistoryFilters(rating, tag))

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Histórico e pendências", style = MaterialTheme.typography.headlineMedium)
        Text("Situação atual", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), Arrangement.spacedBy(8.dp)) {
            IpsumHistorySituation.entries.forEach { option ->
                FilterChip(
                    modifier = Modifier.testTag("situation-${option.name}"),
                    selected = situation == option,
                    onClick = { situationName = option.name },
                    label = { Text(option.label) },
                )
            }
        }
        Text("Rating", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), Arrangement.spacedBy(8.dp)) {
            ratings.forEach { option ->
                FilterChip(
                    modifier = Modifier.testTag("rating-$option"),
                    selected = rating == option,
                    onClick = { rating = option.takeUnless { rating == option } },
                    label = { Text(option.toString()) },
                )
            }
        }
        Text("Tag", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), Arrangement.spacedBy(8.dp)) {
            tags.forEach { option ->
                FilterChip(
                    modifier = Modifier.testTag("tag-$option"),
                    selected = tag == option,
                    onClick = { tag = option.takeUnless { tag == option } },
                    label = { Text(option) },
                )
            }
        }
        Button(
            onClick = {
                situationName = IpsumHistorySituation.SOLVED_IN_IPSUM.name
                rating = null
                tag = null
            },
        ) { Text("Limpar filtros") }

        if (visible.isEmpty()) {
            Text("Nenhum item nesta categoria com os filtros atuais.")
        } else {
            visible.forEach { item -> HistoryItem(item, openProblem) }
        }
    }
}

@Composable
private fun HistoryItem(item: IpsumHistoryItem, onOpenProblem: (String) -> Unit) {
    val ipsum = item.ipsum
    Card(Modifier.fillMaxWidth().testTag("history-${ipsum.problem.id.contestId}-${ipsum.problem.id.index}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(ipsum.problem.name, style = MaterialTheme.typography.titleLarge)
            Text("Problema: ${ipsum.problem.id.contestId}${ipsum.problem.id.index}")
            Text("Rating: ${ipsum.selectedRating}")
            Text("Tags: ${ipsum.problem.tags.sorted().joinToString().ifEmpty { "nenhuma" }}")
            Text("Tempo: ${ipsum.totalTimeMillis()?.let(::formatElapsed) ?: "indisponível"}")
            Text("Dica utilizada: ${if (ipsum.hintRevealedAtEpochMillis != null) "sim" else "não"}")
            Text("Erros: ${ipsum.errorCount}")
            Text("Data do Ipsum: ${formatHistoryDate(ipsum.startedAtEpochMillis)}")
            Text("Resultado atual: ${item.situation.label}")
            Text("Variação do Rating Lorem: ${ipsum.ratingDelta?.let(::formatDelta) ?: "indisponível"}")
            Button(onClick = { onOpenProblem(ipsum.problemUrl()) }) { Text("Abrir no Codeforces") }
        }
    }
}

private fun formatDelta(delta: Int): String = if (delta >= 0) "+$delta" else delta.toString()

private fun formatHistoryDate(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
