package dev.lorem.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.lorem.app.domain.AcRate
import dev.lorem.app.domain.AttemptStatistics
import dev.lorem.app.domain.IpsumStatistics
import dev.lorem.app.domain.TagStatistics
import dev.lorem.app.ui.StatisticsUiState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class StatisticsSection(val label: String) {
    SUMMARY("Resumo"), RATING("Por rating"), TAGS("Por tag"),
}

@Composable
fun StatisticsScreen(state: StatisticsUiState, onNavigateHome: () -> Unit = {}) {
    Scaffold { padding ->
        when (state) {
            StatisticsUiState.Loading -> Column(Modifier.padding(padding).padding(16.dp)) {
                CircularProgressIndicator()
                Text("Carregando estatísticas…")
            }
            StatisticsUiState.NoProfile -> Column(Modifier.padding(padding).padding(16.dp)) {
                Text("Vincule um handle para visualizar suas estatísticas.")
                Button(onClick = onNavigateHome) { Text("Voltar ao Início") }
            }
            is StatisticsUiState.Ready -> StatisticsContent(state.statistics, padding, onNavigateHome)
        }
    }
}

@Composable
private fun StatisticsContent(stats: IpsumStatistics, padding: PaddingValues, onNavigateHome: () -> Unit) {
    var sectionName by rememberSaveable(stats.ownerHandle) { mutableStateOf(StatisticsSection.SUMMARY.name) }
    val section = StatisticsSection.valueOf(sectionName)
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).testTag("statistics-list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Estatísticas", style = MaterialTheme.typography.headlineMedium)
            Text("Treino de @${stats.ownerHandle}")
            Text("Rating Lorem atual: ${stats.currentRating}", modifier = Modifier.testTag("statistics-current-rating"))
            Button(onClick = onNavigateHome) { Text("Voltar ao Início") }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatisticsSection.entries.forEach { option ->
                    FilterChip(
                        modifier = Modifier.testTag("statistics-section-${option.name}"),
                        selected = section == option,
                        onClick = { sectionName = option.name },
                        label = { Text(option.label) },
                    )
                }
            }
        }
        item {
            Text("Somente Ipsums encerrados. AC posterior não conta como AC no Ipsum.")
            if (stats.overall.total == 0) Text("Nenhum Ipsum encerrado. Conclua um treino para ver seu desempenho.")
        }
        when (section) {
            StatisticsSection.SUMMARY -> {
                item { AttemptCard("Seu desempenho", stats.overall, "statistics-overall") }
                item {
                    Text("Evolução do Rating Lorem", style = MaterialTheme.typography.titleLarge)
                    Text("Valores registrados ao encerrar cada Ipsum, do mais antigo ao mais recente.")
                    if (stats.ratingEvolution.isEmpty()) Text("Sem avaliações de rating registradas.")
                    if (stats.unratedAttempts > 0) {
                        Text("${stats.unratedAttempts} encerramento(s) sem avaliação completa; não entram na evolução.")
                    }
                }
                items(stats.ratingEvolution, key = { "evolution-${it.ipsumId}" }) { point ->
                    Card(Modifier.fillMaxWidth().testTag("statistics-evolution-${point.ipsumId}")) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Ipsum #${point.ipsumId} • ${formatStatisticsDate(point.endedAtEpochMillis)}")
                            val delta = if (point.delta >= 0) "+${point.delta}" else point.delta.toString()
                            Text("${point.before} → ${point.after} ($delta)")
                        }
                    }
                }
            }
            StatisticsSection.RATING -> {
                item {
                    Text("Desempenho por rating do problema", style = MaterialTheme.typography.titleLarge)
                    if (stats.byRating.isEmpty()) Text("Sem dados por rating.")
                }
                items(stats.byRating, key = { "rating-${it.problemRating}" }) { group ->
                    AttemptCard("Rating ${group.problemRating}", group.attempts, "statistics-rating-${group.problemRating}")
                }
            }
            StatisticsSection.TAGS -> {
                item {
                    Text("Desempenho por tag", style = MaterialTheme.typography.titleLarge)
                    Text("Um problema conta para cada tag oficial, uma vez no total geral. Taxas com poucas tentativas ainda têm pouca evidência.")
                    Text("Tags sem prática vêm do catálogo salvo e não indicam falha.")
                }
                item { TagRanking("Mais praticadas", stats.mostPracticed) }
                item { TagRanking("Menos praticadas", stats.leastPracticed) }
                item { TagRanking("Maior taxa de falha no Ipsum", stats.highestFailure, showFailure = true) }
                if (stats.byTag.isEmpty()) item { Text("Sem tags no histórico ou no catálogo salvo.") }
                items(stats.byTag, key = { "tag-${it.tag}" }) { group ->
                    AttemptCard(group.tag, group.attempts, "statistics-tag-${group.tag}")
                }
            }
        }
    }
}

@Composable
private fun AttemptCard(title: String, stats: AttemptStatistics, testTag: String) {
    Card(Modifier.fillMaxWidth().testTag(testTag)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text("Ipsums encerrados: ${stats.total}")
            Text("AC no Ipsum: ${stats.accepted}")
            Text("Taxa de AC no Ipsum: ${formatRate(stats.acRate)}")
            Text("AC sem dica: ${formatRate(stats.withoutHint)}")
            Text("AC com dica: ${formatRate(stats.withHint)}")
            Text("Uso de dica: ${stats.hintCount}/${stats.total}")
            Text("Falhas no Ipsum: ${stats.failures} (${formatPercent(stats.failurePercent)})")
            Text("Pendentes atuais: ${stats.pending}")
            Text("AC posterior: ${stats.solvedOutside}")
            Text("Tempo médio: ${stats.averageTimeMillis?.let { "${formatNumber(it / 60_000)} min" } ?: "Sem dados"} (${stats.timedAttempts} tentativa(s) com tempo)")
            Text("Erros antes do AC: ${stats.errorsBeforeAc}")
            Text("Média de erros antes do AC: ${stats.averageErrorsBeforeAc?.let(::formatNumber) ?: "Sem dados"} (${stats.accepted} AC no Ipsum)")
        }
    }
}

@Composable
private fun TagRanking(title: String, tags: List<TagStatistics>, showFailure: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (tags.isEmpty()) Text("Sem dados")
        tags.take(3).forEach { tag ->
            Text(if (showFailure) {
                "${tag.tag}: ${formatPercent(tag.attempts.failurePercent)} (${tag.attempts.failures}/${tag.attempts.total})"
            } else "${tag.tag}: ${tag.attempts.total} tentativa(s)")
        }
        if (tags.size > 3) Text("Exibindo as 3 primeiras; os dados de todas as tags estão abaixo.")
    }
}

private fun formatRate(rate: AcRate): String = "${formatPercent(rate.percent)} (${rate.accepted}/${rate.attempts})"
private fun formatPercent(value: Double?): String = value?.let { "${formatNumber(it)}%" } ?: "Sem dados"
private fun formatNumber(value: Double): String = String.format(Locale.forLanguageTag("pt-BR"), "%.1f", value)
private fun formatStatisticsDate(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis)
    .atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
