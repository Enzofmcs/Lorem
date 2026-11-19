package dev.lorem.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.lorem.app.domain.DUEL_SCALE
import dev.lorem.app.domain.model.Ipsum
import dev.lorem.app.domain.model.IpsumStatus
import dev.lorem.app.ui.components.formatDolorRating
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RatingComparison(history: List<Ipsum>, simulatedAbility: Int? = null) {
    val points = remember(history) {
        history.filter { it.status != IpsumStatus.ACTIVE && it.endedAtEpochMillis != null }
            .sortedWith(compareBy<Ipsum> { it.endedAtEpochMillis }.thenBy { it.id })
    }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selectedIndex = points.indexOfFirst { it.id == selectedId }.takeIf { it >= 0 } ?: points.lastIndex
    val loremColor = MaterialTheme.colorScheme.primary
    val dolorColor = MaterialTheme.colorScheme.tertiary
    val referenceColor = MaterialTheme.colorScheme.onSurfaceVariant
    val reference = simulatedAbility?.times(DUEL_SCALE)
    val balances = remember(points, reference) {
        points.flatMap { listOfNotNull(it.finalLoremRating?.times(DUEL_SCALE), it.virtualDuelResult?.afterMicros) } +
            listOfNotNull(reference)
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Evolução dos ratings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        if (points.isEmpty()) {
            Text("Conclua um Ipsum para ver a evolução.")
            return@Column
        }
        Text("Todos os Ipsums", style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("●", color = loremColor, modifier = Modifier.clearAndSetSemantics {})
                Text("Rating Lorem")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("◆", color = dolorColor, modifier = Modifier.clearAndSetSemantics {})
                Text("Rating Dolor")
            }
            if (simulatedAbility != null) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("– –", color = referenceColor, modifier = Modifier.clearAndSetSemantics {})
                Text("Habilidade simulada = $simulatedAbility")
            }
        }
        if (points.none { it.virtualDuelResult != null }) Text("Ainda sem avaliações Dolor no histórico.")
        if (balances.isNotEmpty()) {
            val low = Math.floorDiv(balances.min(), DUEL_SCALE)
            val high = (-Math.floorDiv(-balances.max(), DUEL_SCALE)).coerceAtLeast(low + 1)
            Text("Pontos", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Column(Modifier.height(152.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Text(high.toString(), style = MaterialTheme.typography.bodySmall)
                    Text(low.toString(), style = MaterialTheme.typography.bodySmall)
                }
                RatingComparisonPlot(points, selectedIndex, low, high, loremColor, dolorColor, reference, referenceColor,
                    select = { selectedId = points[it].id }, modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Mais antigo", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Text("Mais recente", style = MaterialTheme.typography.bodySmall)
            }
        }
        val point = points[selectedIndex]
        Card(Modifier.fillMaxWidth().testTag("rating-point-details").semantics { liveRegion = LiveRegionMode.Polite }) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (simulatedAbility != null) Text("Ipsum ${selectedIndex + 1}")
                Text("${point.problem.id} · ${point.problem.name}", style = MaterialTheme.typography.titleMedium)
                Text(formatSyncTime(requireNotNull(point.endedAtEpochMillis)))
                val lorem = point.finalLoremRating
                Text(if (lorem == null) "Rating Lorem: sem avaliação" else
                    "Rating Lorem: $lorem${point.ratingDelta?.let { " (${if (it >= 0) "+" else "−"}${kotlin.math.abs(it.toLong())})" }.orEmpty()}")
                val dolor = point.virtualDuelResult
                Text(if (dolor == null) "Rating Dolor: sem avaliação" else
                    "Rating Dolor: ${formatDolorRating(dolor.afterMicros)} (${formatDolorRating(dolor.deltaMicros, signed = true)})")
            }
        }
        if (points.size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { selectedId = points[selectedIndex - 1].id }, enabled = selectedIndex > 0,
                modifier = Modifier.heightIn(min = 48.dp)) { Text("Ipsum anterior") }
            TextButton(onClick = { selectedId = points[selectedIndex + 1].id }, enabled = selectedIndex < points.lastIndex,
                modifier = Modifier.heightIn(min = 48.dp)) { Text("Próximo Ipsum") }
        }
    }
}

@Composable
private fun RatingComparisonPlot(
    points: List<Ipsum>, selectedIndex: Int, low: Long, high: Long,
    loremColor: Color, dolorColor: Color, reference: Long?, referenceColor: Color,
    select: (Int) -> Unit, modifier: Modifier = Modifier,
) {
    val axis = MaterialTheme.colorScheme.outline
    val surface = MaterialTheme.colorScheme.background
    val onSelect by rememberUpdatedState(select)
    val inset = 8.dp
    Canvas(modifier.fillMaxWidth().height(152.dp).testTag("statistics-evolution-chart")
        .pointerInput(points.size) {
            detectTapGestures { tap ->
                val pad = inset.toPx()
                val fraction = (tap.x - pad) / (size.width - pad * 2).coerceAtLeast(1f)
                onSelect((fraction * points.lastIndex).roundToInt().coerceIn(0, points.lastIndex))
            }
        }
        // The values and point navigation have their own accessible controls below the plot.
        .clearAndSetSemantics {}) {
        val pad = inset.toPx()
        val right = (size.width - pad).coerceAtLeast(pad)
        val bottom = size.height - pad
        fun x(index: Int) = if (points.size == 1) size.width / 2
            else pad + (right - pad) * index / points.lastIndex
        fun position(index: Int, micros: Long) = Offset(
            x(index),
            bottom - (bottom - pad) * ((micros - low * DUEL_SCALE).toDouble() / ((high - low) * DUEL_SCALE)).toFloat(),
        )
        drawLine(axis, Offset(pad, pad), Offset(pad, bottom), 1.dp.toPx())
        drawLine(axis, Offset(pad, bottom), Offset(right, bottom), 1.dp.toPx())
        if (reference != null) {
            val y = position(0, reference).y
            drawLine(referenceColor, Offset(pad, y), Offset(right, y), 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
        }
        val selectedX = x(selectedIndex)
        drawLine(axis, Offset(selectedX, pad), Offset(selectedX, bottom), 1.dp.toPx())

        fun drawSeries(color: Color, diamond: Boolean, balance: (Ipsum) -> Long?) {
            val dash = if (diamond) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())) else null
            var previous: Offset? = null
            points.forEachIndexed { index, point ->
                val next = balance(point)?.let { position(index, it) }
                val prior = previous
                if (prior != null && next != null) drawLine(color, prior, next, 2.dp.toPx(), pathEffect = dash)
                previous = next
            }
            points.forEachIndexed { index, point ->
                val value = balance(point) ?: return@forEachIndexed
                val center = position(index, value)
                val radius = if (index == selectedIndex) 5.dp.toPx() else 3.dp.toPx()
                if (diamond) {
                    drawPath(Path().apply {
                        moveTo(center.x, center.y - radius)
                        lineTo(center.x + radius, center.y)
                        lineTo(center.x, center.y + radius)
                        lineTo(center.x - radius, center.y)
                        close()
                    }, color)
                } else {
                    drawCircle(color, radius, center)
                    drawCircle(surface, 1.dp.toPx(), center)
                }
            }
        }
        drawSeries(loremColor, diamond = false) { it.finalLoremRating?.times(DUEL_SCALE) }
        drawSeries(dolorColor, diamond = true) { it.virtualDuelResult?.afterMicros }
    }
}
