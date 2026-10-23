package dev.lorem.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.key
import dev.lorem.app.domain.RatingEvolutionPoint
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.CardDefaults
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
import dev.lorem.app.domain.ConsolidationProgress
import dev.lorem.app.domain.IpsumStatistics
import dev.lorem.app.domain.TagStatistics
import dev.lorem.app.ui.StatisticsUiState
import dev.lorem.app.ui.components.LoremEmptyState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class StatisticsSection(val label: String) {
    SUMMARY("Resumo"), RATING("Por rating"), TAGS("Por tag"),
}

@Composable
fun StatisticsScreen(state: StatisticsUiState, onNavigateHome: () -> Unit = {}, consolidation: ConsolidationProgress? = null,
    showDolorRating: Boolean = false, dolorRatingMicros: Long? = null) {
    val padding = PaddingValues(0.dp)
    when (state) {
        StatisticsUiState.Loading -> Column(Modifier.padding(padding).padding(16.dp)) {
            CircularProgressIndicator()
            Text("Carregando estatísticas…")
        }
        StatisticsUiState.NoProfile -> Column(Modifier.padding(padding).padding(16.dp)) {
            LoremEmptyState("Vincule um handle para visualizar suas estatísticas.")
            TextButton(onClick = onNavigateHome, modifier = Modifier.heightIn(min = 48.dp)) { Text("Voltar ao Início") }
        }
        is StatisticsUiState.Ready -> key(state.statistics.ownerHandle.trim().lowercase(Locale.ROOT)) {
            StatisticsContent(state.statistics, padding, onNavigateHome, consolidation, showDolorRating, dolorRatingMicros)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatisticsContent(stats: IpsumStatistics, padding: PaddingValues, onNavigateHome: () -> Unit, consolidation: ConsolidationProgress?,
    showDolorRating: Boolean, dolorRatingMicros: Long?) {
    var sectionName by rememberSaveable(stats.ownerHandle) { mutableStateOf(StatisticsSection.SUMMARY.name) }
    val section = StatisticsSection.valueOf(sectionName)
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).testTag("statistics-list"),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Estatísticas", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                Text("Seu progresso · @${stats.ownerHandle}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Rating Lorem atual: ${stats.currentRating}", style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.testTag("statistics-current-rating"))
                        Text("Acompanhe seus resultados e consulte as avaliações salvas abaixo.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (showDolorRating) dev.lorem.app.ui.components.DolorRatingCard(dolorRatingMicros)
                TextButton(onClick = onNavigateHome, modifier = Modifier.heightIn(min = 48.dp)) { Text("Voltar ao Início") }
            }
        }
        item(key = "sections") {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatisticsSection.entries.forEach { option ->
                    FilterChip(
                        modifier = Modifier.testTag("statistics-section-${option.name}").heightIn(min = 48.dp),
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
                if (consolidation != null) item { ConsolidationCard(consolidation) }
                item { AttemptCard("Seu desempenho", stats.overall, "statistics-overall") }
                item {
                    Text("Evolução do Rating Lorem", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    Text("Valores salvos, em ordem de encerramento. Cada ponto representa uma avaliação; os valores completos estão abaixo.")
                    if (stats.ratingEvolution.isEmpty()) Text("Sem avaliações de rating registradas.")
                    if (stats.unratedAttempts > 0) {
                        Text("${stats.unratedAttempts} encerramento(s) sem avaliação completa; não entram na evolução.")
                    }
                }
                if (stats.ratingEvolution.isNotEmpty()) item { RatingEvolutionChart(stats.ratingEvolution) }
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
                    Text("Desempenho por rating do problema", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    Text("As barras mostram a taxa de AC no Ipsum; cada grupo informa seus ACs e sua amostra.")
                    if (stats.byRating.isEmpty()) Text("Sem dados por rating.")
                }
                items(stats.byRating, key = { "rating-${it.problemRating}" }) { group ->
                    AttemptCard("Rating ${group.problemRating}", group.attempts, "statistics-rating-${group.problemRating}")
                }
            }
            StatisticsSection.TAGS -> {
                item {
                    Text("Desempenho por tag", style = MaterialTheme.typography.titleLarge)
                    Text("Uma tentativa pode contribuir para várias tags oficiais. A soma das tags não equivale ao total de treinos. Taxas com poucas tentativas ainda têm pouca evidência.")
                    Text("Tags sem prática vêm do catálogo salvo e não indicam falha. As barras mostram a taxa de AC no Ipsum; texto e frações permitem conferir cada grupo.")
                }
                item { TagRanking("Mais praticadas", stats.mostPracticed) }
                item { TagRanking("Menos praticadas", stats.leastPracticed) }
                item { TagRanking("Maior taxa de falha no Ipsum", stats.highestFailure, showFailure = true,
                    emptyLabel = if (stats.byTag.any { it.attempts.total > 0 }) "Nenhuma falha nas tentativas com amostra." else "Sem amostra") }
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
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text("Ipsums encerrados: ${stats.total}", style = MaterialTheme.typography.titleLarge)
            Text("AC no Ipsum: ${stats.accepted}")
            Text("Taxa de AC no Ipsum: ${formatRate(stats.acRate)}", style = MaterialTheme.typography.titleMedium)
            SampleBar(stats.acRate)
            Text("AC sem dica: ${formatRate(stats.withoutHint)}")
            Text("AC com dica: ${formatRate(stats.withHint)}")
            Text("Uso de dica: ${stats.hintCount}/${stats.total}${if (stats.total == 0) " · Sem amostra" else ""}")
            Text("Falhas no Ipsum: ${stats.failures} (${formatPercent(stats.failurePercent)}; ${stats.failures}/${stats.total})")
            Text("Pendentes atuais: ${stats.pending}")
            Text("AC posterior: ${stats.solvedOutside}")
            Text("Tempo médio: ${stats.averageTimeMillis?.let { "${formatNumber(it / 60_000)} min" } ?: "Sem amostra"} (${stats.timedAttempts} tentativa(s) com tempo)")
            Text("Erros antes do AC: ${stats.errorsBeforeAc}")
            Text("Média de erros antes do AC: ${stats.averageErrorsBeforeAc?.let(::formatNumber) ?: "Sem amostra"} (${stats.accepted} AC no Ipsum)")
        }
    }
}

@Composable
private fun TagRanking(title: String, tags: List<TagStatistics>, showFailure: Boolean = false, emptyLabel: String = "Sem amostra") {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (tags.isEmpty()) Text(emptyLabel)
        tags.take(3).forEach { tag ->
            Text(if (showFailure) {
                "${tag.tag}: ${formatPercent(tag.attempts.failurePercent)} (${tag.attempts.failures}/${tag.attempts.total})"
            } else "${tag.tag}: ${tag.attempts.total} tentativa(s)")
        }
        if (tags.size > 3) Text("Exibindo as 3 primeiras; os dados de todas as tags estão abaixo.")
    }
}

private fun formatRate(rate: AcRate): String = "${formatPercent(rate.percent)} (${rate.accepted}/${rate.attempts})"
private fun formatPercent(value: Double?): String = value?.let { "${formatNumber(it)}%" } ?: "Sem amostra"
private fun formatNumber(value: Double): String = String.format(Locale.forLanguageTag("pt-BR"), "%.1f", value)
private fun formatStatisticsDate(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis)
    .atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))


/** Decorative bars: the exact rate, numerator and denominator are in the adjacent text. */
@Composable
private fun SampleBar(rate: AcRate) {
    if (rate.attempts > 0) LinearProgressIndicator(
        progress = { (rate.accepted.toFloat() / rate.attempts).coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().height(8.dp).clearAndSetSemantics {},
    )
}

/** Only persisted `after` values are plotted; no missing evaluation is reconstructed. */
@Composable
private fun RatingEvolutionChart(points: List<RatingEvolutionPoint>) {
    val minimum = points.minOf { it.after }
    val maximum = points.maxOf { it.after }
    val color = MaterialTheme.colorScheme.primary
    val axis = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${points.size} avaliação(ões) registrada(s) · rating de $minimum a $maximum")
        Canvas(Modifier.fillMaxWidth().height(160.dp).testTag("statistics-evolution-chart").clearAndSetSemantics {}) {
            val inset = 8.dp.toPx()
            val width = (size.width - inset * 2).coerceAtLeast(0f)
            val height = (size.height - inset * 2).coerceAtLeast(0f)
            fun position(index: Int, value: Int): Offset = Offset(
                if (points.size == 1) size.width / 2 else inset + width * index / (points.size - 1),
                if (minimum == maximum) size.height / 2 else inset + height * (1f - (value.toFloat() - minimum) / (maximum.toFloat() - minimum)),
            )
            drawLine(axis, Offset(inset, size.height - inset), Offset(size.width - inset, size.height - inset), 1.dp.toPx())
            val path = Path()
            points.forEachIndexed { index, point ->
                val offset = position(index, point.after)
                if (index == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
            }
            drawPath(path, color, style = Stroke(2.dp.toPx()))
            // Dense series remain linear to draw and do not fill the curve with overlapping dots.
            if (points.size <= 40) points.forEachIndexed { index, point -> drawCircle(color, 3.dp.toPx(), position(index, point.after)) }
        }
        Text("Mais antigo → mais recente. Dados por Ipsum a seguir.", style = MaterialTheme.typography.bodySmall)
    }
}
